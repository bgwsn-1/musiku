package app.musiku.data

import app.musiku.model.Playlist
import app.musiku.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** One track as Spotify's public embed page exposes it. */
data class SpotifyTrack(
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
)

/** A public Spotify playlist scraped from the embed page — no login, no token. */
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val cover: String?,
    val tracks: List<SpotifyTrack>,
)

/**
 * Reads public Spotify playlists from the embed page (`open.spotify.com/embed/playlist/<id>`),
 * whose `__NEXT_DATA__` payload carries the full track list as plain JSON.
 *
 * This is deliberately not the Web API: no login, no Client ID and no token means nothing to
 * leak or refresh. The trade-offs are real and shown to the user — only public playlists work,
 * and the embed only paints the first ~100 tracks. If Spotify changes the embed markup this
 * parser stops working; [playlist] then throws a message the import sheet can display.
 */
class Spotify(private val http: OkHttpClient) {

    /** True when [input] plausibly contains a Spotify playlist (used to gate share intents). */
    fun looksLikePlaylist(input: String): Boolean =
        SPOTIFY_ID.containsMatchIn(input) || input.contains("spotify.link/", ignoreCase = true)

    /** Playlist id from a share URL, an `intl-xx` URL or a `spotify:playlist:` URI. */
    fun playlistId(input: String): String? =
        SPOTIFY_ID.find(input)?.groupValues?.get(1)

    /** Id from a plain URL, a bare playlist id, or by following a `spotify.link` short link. */
    suspend fun resolvePlaylistId(input: String): String? {
        playlistId(input)?.let { return it }
        // The Sync action already has the id, so it passes it without a URL around it.
        val bare = input.trim()
        if (RAW_ID.matches(bare)) return bare
        val short = SHORT_LINK.find(input)?.value ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(short).header("User-Agent", UA).build()
                http.newCall(req).execute().use { r -> playlistId(r.request.url.toString()) }
            }.getOrNull()
        }
    }

    suspend fun playlist(id: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$id")
            .header("User-Agent", UA)
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .build()
        val html = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException(
                if (r.code == 404) "Playlist tidak ditemukan. Pastikan link-nya benar."
                else "Spotify menolak permintaan (HTTP ${r.code})."
            )
            r.body?.string().orEmpty()
        }
        val raw = NEXT_DATA.find(html)?.groupValues?.get(1)
            ?: throw IOException("Format halaman Spotify berubah, import belum bisa dipakai.")
        val entity = JSONObject(raw)
            .optJSONObject("props")?.optJSONObject("pageProps")
            ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw IOException("Playlist ini tidak bisa dibuka. Mungkin privat atau tautannya salah.")

        val name = entity.optString("name").takeIf { it.isNotBlank() } ?: "Playlist Spotify"
        val cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")?.let { sources ->
            (sources.length() - 1 downTo 0).firstNotNullOfOrNull { i ->
                sources.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }
            }
        }
        val trackList = entity.optJSONArray("trackList") ?: JSONArray()
        val tracks = (0 until trackList.length()).mapNotNull { i ->
            val o = trackList.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri")
            // Podcast episodes can appear in playlists; they have no YouTube Music match.
            if (!uri.startsWith("spotify:track:")) return@mapNotNull null
            val title = o.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SpotifyTrack(
                uri = uri,
                title = title,
                artist = o.optString("subtitle").takeIf { it.isNotBlank() },
                durationMs = o.optLong("duration"),
            )
        }
        if (tracks.isEmpty()) throw IOException("Playlist ini kosong atau hanya berisi episode podcast.")
        SpotifyPlaylist(id, name, cover, tracks)
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val NEXT_DATA = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val SPOTIFY_ID = Regex(
            """(?:open\.spotify\.com/(?:intl-[a-z-]+/)?playlist/|spotify:playlist:)([A-Za-z0-9]{10,})""",
            RegexOption.IGNORE_CASE,
        )
        val SHORT_LINK = Regex("""https?://spotify\.link/[A-Za-z0-9]+""")
        val RAW_ID = Regex("""[A-Za-z0-9]{10,}""")
    }
}

/** What the import sheet should render. Kept flat so the UI never has to guess. */
sealed interface SpotifyImportState {
    object Idle : SpotifyImportState
    data class Working(val name: String?, val done: Int, val total: Int, val syncing: Boolean) : SpotifyImportState
    data class Done(
        val playlistId: String,
        val name: String,
        val matched: Int,
        val total: Int,
        val missing: List<String>,
        val syncing: Boolean,
    ) : SpotifyImportState
    data class Failed(val message: String) : SpotifyImportState
}

/**
 * Turns a Spotify playlist into a local playlist of YouTube Music tracks.
 *
 * Each Spotify track is searched on YouTube Music and scored on title, artist and duration; the
 * matches are cached by Spotify URI in [Library] so a second sync only searches new songs. The
 * work runs five tracks at a time and publishes progress, because a 100-track playlist means
 * ~200 requests and the user should see it moving.
 */
class SpotifyImporter(
    private val spotify: Spotify,
    private val yt: YtMusic,
    private val library: Library,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<SpotifyImportState>(SpotifyImportState.Idle)
    val state: StateFlow<SpotifyImportState> = _state
    private var job: Job? = null

    fun import(link: String) = start(link, null)

    /** Re-fetch a playlist already imported from Spotify, keeping its local id and name. */
    fun sync(playlist: Playlist) {
        val sourceId = playlist.sourceId ?: return
        start(sourceId, playlist.id)
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SpotifyImportState.Idle
    }

    fun reset() {
        job?.cancel()
        job = null
        _state.value = SpotifyImportState.Idle
    }

    private fun start(input: String, syncTargetId: String?) {
        job?.cancel()
        job = scope.launch {
            _state.value = SpotifyImportState.Working(null, 0, 0, syncTargetId != null)
            try {
                val id = spotify.resolvePlaylistId(input)
                if (id == null) {
                    _state.value = SpotifyImportState.Failed(
                        "Link Spotify tidak dikenali. Tempel link playlist seperti open.spotify.com/playlist/…"
                    )
                    return@launch
                }
                val remote = spotify.playlist(id)
                library.awaitReady()

                val total = remote.tracks.size
                _state.value = SpotifyImportState.Working(remote.name, 0, total, syncTargetId != null)

                val cached = ConcurrentHashMap(library.spotifyMatches())
                val results = arrayOfNulls<Track>(total)
                val missing = arrayOfNulls<String>(total)
                val done = AtomicInteger(0)
                val gate = Semaphore(5)

                coroutineScope {
                    remote.tracks.mapIndexed { index, st ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                val track = cached[st.uri] ?: runCatching { match(st) }.getOrNull()
                                if (track != null) {
                                    results[index] = track
                                    cached[st.uri] = track
                                } else {
                                    missing[index] = listOfNotNull(st.title, st.artist).joinToString(" — ")
                                }
                                _state.value = SpotifyImportState.Working(
                                    remote.name, done.incrementAndGet(), total, syncTargetId != null,
                                )
                            }
                        }
                    }.awaitAll()
                }

                val tracks = results.filterNotNull()
                if (tracks.isEmpty()) {
                    _state.value = SpotifyImportState.Failed(
                        "Tidak ada lagu yang ketemu di YouTube Music. Coba playlist lain."
                    )
                    return@launch
                }

                library.saveSpotifyMatches(cached)
                val playlist = if (syncTargetId != null) {
                    library.replaceTracks(syncTargetId, tracks)
                    library.playlists.value.firstOrNull { it.id == syncTargetId }
                } else {
                    library.importPlaylist(remote.name, tracks, remote.id)
                }
                if (playlist == null) {
                    _state.value = SpotifyImportState.Failed("Playlist sudah dihapus sebelum import selesai.")
                    return@launch
                }
                _state.value = SpotifyImportState.Done(
                    playlistId = playlist.id,
                    name = playlist.name,
                    matched = tracks.size,
                    total = total,
                    missing = missing.filterNotNull(),
                    syncing = syncTargetId != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpotifyImportState.Failed(
                    e.message ?: "Import gagal. Cek koneksi internet lalu coba lagi."
                )
            }
        }
    }

    /** Best YouTube Music match for one Spotify track, or null when nothing is close enough. */
    private suspend fun match(st: SpotifyTrack): Track? {
        val artist = primaryArtist(st.artist)
        val queries = listOfNotNull(
            listOfNotNull(st.title, artist).joinToString(" ").trim(),
            st.title,
        ).filter { it.isNotBlank() }.distinct()

        for (q in queries) {
            val candidates = runCatching { yt.searchTracks(q) }.getOrDefault(emptyList())
            var best: Track? = null
            var bestScore = 0.0
            for (candidate in candidates) {
                val score = score(st, candidate)
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            if (best != null) return best
        }
        return null
    }

    /** Positive when the candidate is acceptable, zero when it is not. */
    private fun score(st: SpotifyTrack, candidate: Track): Double {
        if (isNoise(st.title, candidate.title)) return 0.0
        val title = titleScore(st.title, candidate.title)
        if (title < 0.5) return 0.0
        val artist = artistScore(st.artist, candidate)
        if (title < 0.85 && artist < 0.5) return 0.0
        val diff = durationDiff(st.durationMs, candidate.duration)
        if (diff > 25 && title < 0.99) return 0.0
        if (diff > 60) return 0.0
        return title * 2.0 + artist * 0.8 - (diff / 30.0).coerceAtMost(2.0)
    }
}

private fun primaryArtist(raw: String?): String? =
    raw?.split(',', '&', ';')
        ?.firstOrNull()
        ?.split(" feat.", " ft.", " featuring ")
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun titleScore(a: String, b: String): Double {
    val x = normalizeTitle(a)
    val y = normalizeTitle(b)
    if (x.isEmpty() || y.isEmpty()) return 0.0
    if (x == y) return 1.0
    if ((" $x ") in (" $y ") || (" $y ") in (" $x ")) return 0.85
    val xt = x.split(' ').toSet()
    val yt = y.split(' ').toSet()
    return xt.intersect(yt).size.toDouble() / xt.union(yt).size.toDouble()
}

private fun artistScore(spotifyArtist: String?, candidate: Track): Double {
    if (spotifyArtist.isNullOrBlank()) return 0.5
    val haystack = " ${normalizeTitle(listOfNotNull(candidate.artist, candidate.album).joinToString(" "))} "
    val names = spotifyArtist.split(',', '&', ';')
        .flatMap { it.split(" feat.", " ft.", " featuring ") }
        .map { normalizeTitle(it) }
        .filter { it.isNotBlank() }
    if (names.isEmpty()) return 0.5
    return names.count { " $it " in haystack }.toDouble() / names.size
}

/** Reject karaoke/cover/sped-up uploads that the real song's title does not ask for. */
private fun isNoise(spotifyTitle: String, candidateTitle: String): Boolean {
    val c = " ${normalizeTitle(candidateTitle)} "
    val s = " ${normalizeTitle(spotifyTitle)} "
    return NOISE.any { " $it " in c && " $it " !in s }
}

private fun durationDiff(spotifyMs: Long, candidate: String?): Int {
    if (spotifyMs <= 0 || candidate.isNullOrBlank()) return 0
    val seconds = durationSeconds(candidate) ?: return 0
    return abs(seconds - (spotifyMs / 1000).toInt())
}

private fun durationSeconds(label: String): Int? {
    val parts = label.trim().split(':')
    if (parts.size !in 2..3) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    return nums.fold(0) { acc, v -> acc * 60 + v }
}

private fun normalizeTitle(raw: String): String {
    var s = raw.lowercase()
    s = s.replace(BRACKETS, " ")
    s = s.replace(FEATURING, " ")
    s = s.replace(NON_WORD, " ")
    return s.replace(SPACES, " ").trim()
}

private val BRACKETS = Regex("""[\(\[][^)\]]*[\)\]]""")
private val FEATURING = Regex("""\b(feat\.?|ft\.?|featuring)\b.*$""")
private val NON_WORD = Regex("""[^\p{L}\p{N} ]+""")
private val SPACES = Regex("""\s+""")
private val NOISE = listOf(
    "cover", "karaoke", "instrumental", "tribute", "nightcore",
    "sped up", "slowed", "8d audio", "reverb",
)
