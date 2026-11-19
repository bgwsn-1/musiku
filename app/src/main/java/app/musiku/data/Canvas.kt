package app.musiku.data

import android.content.Context
import app.musiku.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Spotify Canvas — the short, silent, looping video an artist uploads per track and Spotify
 * plays over the artwork on the mobile player.
 *
 * There is no public API for it, so this mirrors what the web player does, anonymously:
 *
 *  1. mint an anonymous web-player token (TOTP from the rotating public secret dictionary),
 *  2. search Spotify for the track behind a YouTube Music video (title + artist scoring),
 *  3. resolve the video URL through the Canvas service,
 *  4. download the MP4 once into the cache directory.
 *
 * No account and no cookie: every step is anonymous. Failures (rotated secret, rotated query
 * hash, no match, no canvas, offline) all land on `null`, so the player just keeps the artwork.
 */
class Canvas(private val ctx: Context, private val http: OkHttpClient) {

    /** One failed lookup per track per session; a miss is not retried on every recomposition. */
    private val misses = ConcurrentHashMap.newKeySet<String>()

    private val spotifyIds = ConcurrentHashMap<String, String>()

    private var totpKey: ByteArray? = null
    private var totpVersion: String? = null
    private var secretsFetchedAt = 0L

    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L

    /** Human-readable outcome of the last attempt, shown in Settings so failures are diagnosable. */
    val status: StateFlow<String> get() = _status

    private val _status = MutableStateFlow("")

    private class NoMatch : Exception()
    private class NoCanvas : Exception()

    private sealed interface Lookup {
        data class Found(val url: String) : Lookup
        object Empty : Lookup
        data class Failed(val reason: String) : Lookup
    }

    private val dir: File
        get() = File(ctx.cacheDir, "canvas").apply { mkdirs() }

    private fun fileFor(track: Track) = File(dir, track.videoId + ".mp4")

    /** The already-downloaded video, if this track has one. Safe to call from composition. */
    fun cached(track: Track): File? = fileFor(track).takeIf { it.isFile && it.length() > 0L }

    /**
     * Resolve and download the canvas for [track], or null when anything is missing. Runs on IO;
     * the returned file is ready to hand to a player.
     */
    suspend fun video(track: Track): File? {
        cached(track)?.let {
            _status.value = "${track.title} — Canvas siap"
            return it
        }
        if (track.videoId in misses) return null

        return withContext(Dispatchers.IO) {
            try {
                val file = withToken { bearer ->
                    val id = spotifyIds[track.videoId] ?: search(bearer, track)?.also {
                        spotifyIds[track.videoId] = it
                    } ?: throw NoMatch()
                    when (val look = canvasUrl(bearer, id)) {
                        is Lookup.Found -> download(look.url, fileFor(track))
                        Lookup.Empty -> throw NoCanvas()
                        is Lookup.Failed -> throw IOException(look.reason)
                    }
                }
                _status.value = "${track.title} — Canvas siap"
                file
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoMatch) {
                misses += track.videoId
                _status.value = "${track.title} — nggak ketemu di Spotify"
                null
            } catch (e: NoCanvas) {
                misses += track.videoId
                _status.value = "${track.title} — lagu ini nggak punya Canvas"
                null
            } catch (e: Exception) {
                // Network hiccups are not a verdict: retry the next time the player opens.
                _status.value = "${track.title} — gagal: ${e.message?.take(70) ?: "unknown"}"
                null
            }
        }
    }

    /** Runs [block] with a token, minting a fresh one once if Spotify rejects the first. */
    private inline fun <T> withToken(block: (String) -> T): T {
        val first = accessToken() ?: accessToken(force = true) ?: error("no access token")
        return try {
            block(first)
        } catch (e: IOException) {
            val second = accessToken(force = true) ?: throw e
            block(second)
        }
    }

    // ── anonymous auth ───────────────────────────────────────────────────────

    /**
     * The web player signs its token request with a TOTP derived from a secret Spotify rotates.
     * The community dictionary tracks the current one; without a fresh secret the token endpoint
     * answers 400 and Canvas simply stays off.
     */
    private fun ensureSecrets(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && totpKey != null && now - secretsFetchedAt < SECRETS_TTL_MS) return

        val raw = get(SECRETS_URL)
        val o = JSONObject(raw)
        val newest = o.keys().asSequence()
            .maxByOrNull { it.toIntOrNull() ?: 0 }
            ?: throw IOException("empty secret dictionary")
        val data = o.optJSONArray(newest) ?: throw IOException("bad secret dictionary")

        // Each value is XOR-scrambled with its position; the key is the decimal concatenation.
        val key = StringBuilder()
        for (i in 0 until data.length()) key.append(data.optInt(i) xor ((i % 33) + 9))
        totpKey = key.toString().toByteArray(Charsets.UTF_8)
        totpVersion = newest
        secretsFetchedAt = now
    }

    private fun totp(seconds: Long, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(key, "HmacSHA1"))
        }
        var counter = seconds / 30
        val message = ByteArray(8)
        for (i in 7 downTo 0) {
            message[i] = (counter and 0xff).toByte()
            counter = counter shr 8
        }
        val h = mac.doFinal(message)
        val offset = h[19].toInt() and 0x0f
        val code = ((h[offset].toInt() and 0x7f) shl 24) or
            ((h[offset + 1].toInt() and 0xff) shl 16) or
            ((h[offset + 2].toInt() and 0xff) shl 8) or
            (h[offset + 3].toInt() and 0xff)
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    /** Anonymous web-player bearer. No account, no cookie. */
    private fun accessToken(force: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!force) accessToken?.let { if (now < accessTokenExpiresAt - 60_000L) return it }

        ensureSecrets(force)
        val key = totpKey ?: return null
        val version = totpVersion ?: return null

        // The TOTP must be derived from Spotify's clock, not the phone's.
        val serverSeconds = runCatching {
            JSONObject(get("https://open.spotify.com/api/server-time")).optLong("serverTime")
        }.getOrNull()?.takeIf { it > 0L } ?: (now / 1000L)

        val url = "https://open.spotify.com/api/token".toHttpUrl().newBuilder()
            .addQueryParameter("reason", "init")
            .addQueryParameter("productType", "web_player")
            .addQueryParameter("totp", totp(now / 1000L, key))
            .addQueryParameter("totpVer", version)
            .addQueryParameter("totpServer", totp(serverSeconds, key))
            .build()

        val o = JSONObject(
            get(url.toString(), origin = "https://open.spotify.com/"),
        )
        val token = o.optString("accessToken").ifEmpty { return null }
        accessToken = token
        accessTokenExpiresAt = o.optLong("accessTokenExpirationTimestampMs", now + 25 * 60_000L)
        return token
    }

    // ── track matching ───────────────────────────────────────────────────────

    /**
     * YouTube Music carries a `videoId`, Spotify wants a track id. Search the web player's own
     * query and keep the best title/artist overlap instead of trusting the first hit — live takes
     * and remixes would otherwise pull a different song's canvas.
     */
    private fun search(bearer: String, track: Track): String? {
        val q = buildString {
            append(strip(track.title))
            track.artist?.let { if (it.isNotBlank()) append(" ").append(strip(it)) }
        }.trim()
        if (q.isEmpty()) return null

        val variables = JSONObject()
            .put("searchTerm", q)
            .put("offset", 0)
            .put("limit", 10)
            .put("numberOfTopResults", 5)
            .put("includeAudiobooks", false)
            .put("includePreReleases", true)
            .put("includeAlbumPreReleases", false)
            .put("includeAuthors", false)
            .put("includeEpisodeContentRatingsV2", false)
        val url = PATHFINDER.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchDesktop")
            .addQueryParameter("variables", variables.toString())
            .addQueryParameter("extensions", persistedQuery(SEARCH_HASH))
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify search HTTP ${r.code}")
            val items = JSONObject(r.body?.string() ?: "")
                .optJSONObject("data")
                ?.optJSONObject("searchV2")
                ?.optJSONObject("tracksV2")
                ?.optJSONArray("items")
                ?: return null

            val wantedTitle = words(track.title)
            val wantedArtist = words(track.artist.orEmpty())
            var best: String? = null
            var bestScore = 0f
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)
                    ?.optJSONObject("item")
                    ?.optJSONObject("data") ?: continue
                val uri = item.optString("uri")
                val id = uri.substringAfterLast(':')
                if (id.length != 22) continue

                val artists = item.optJSONObject("artists")?.optJSONArray("items")
                val names = if (artists == null) emptySet() else words(
                    (0 until artists.length()).joinToString(" ") {
                        artists.optJSONObject(it)?.optJSONObject("profile")?.optString("name").orEmpty()
                    },
                )
                val titleScore = overlap(wantedTitle, words(item.optString("name")))
                val artistScore = if (wantedArtist.isEmpty()) 0.5f else overlap(wantedArtist, names)
                val score = titleScore * 0.7f + artistScore * 0.3f
                if (score > bestScore) {
                    bestScore = score
                    best = id
                }
            }
            return if (bestScore >= 0.5f) best else null
        }
    }

    // ── canvas lookup ────────────────────────────────────────────────────────

    private fun canvasUrl(bearer: String, trackId: String): Lookup {
        // The community Canvas service signs its own (logged-in) queries server side, so it can
        // see canvases that an anonymous session is not served. It 500s while refreshing its own
        // token, so a failed attempt is worth one more try before falling back.
        var reason = "service tidak menjawab"
        repeat(2) { attempt ->
            val response = runCatching {
                JSONObject(get("https://spotify-canva.vercel.app/api/canvas?trackId=$trackId"))
            }
            response.getOrNull()?.optJSONArray("canvasesList")?.let { list ->
                if (list.length() == 0) return Lookup.Empty
                for (i in 0 until list.length()) {
                    val u = list.optJSONObject(i)?.optString("canvasUrl").orEmpty()
                    if (u.startsWith("http")) return Lookup.Found(u)
                }
                return Lookup.Empty
            }
            reason = response.exceptionOrNull()?.message?.take(70) ?: reason
            if (attempt == 0) Thread.sleep(500)
        }

        // Direct fallback: the same query the mobile client runs. Anonymous sessions are usually
        // not served a canvas, but if Spotify ever changes that this picks it up for free.
        directCanvas(bearer, trackId)?.let { return Lookup.Found(it) }
        return Lookup.Failed(reason)
    }

    private fun directCanvas(bearer: String, trackId: String): String? {
        val body = JSONObject()
            .put("operationName", "canvas")
            .put("variables", JSONObject().put("trackUri", "spotify:track:$trackId"))
            .put("extensions", JSONObject(persistedQuery(CANVAS_HASH)))
        val req = Request.Builder()
            .url(PATHFINDER_V2)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            return JSONObject(r.body?.string() ?: "")
                .optJSONObject("data")
                ?.optJSONObject("trackUnion")
                ?.optJSONObject("canvas")
                ?.optString("url")
                ?.takeIf { it.startsWith("http") }
        }
    }


    private fun download(url: String, target: File): File? {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("canvas download HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty canvas body")

            val tmp = File(target.parentFile, target.name + ".part")
            body.byteStream().use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            if (tmp.length() <= 0L || tmp.length() > MAX_BYTES) {
                tmp.delete()
                throw IOException("bad canvas file")
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("could not store canvas")
            }
            return target
        }
    }

    // ── transport ────────────────────────────────────────────────────────────

    private fun get(url: String, origin: String? = null): String {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .apply {
                if (origin != null) {
                    header("Origin", origin)
                    header("Referer", "$origin/")
                }
            }
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("empty body")
        }
    }

    private fun persistedQuery(hash: String): String = JSONObject()
        .put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash))
        .toString()

    // ── text matching helpers ────────────────────────────────────────────────

    private fun strip(s: String): String = s
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("(?i)\\b(official|video|audio|lyrics?|visualizer|remaster(ed)?|hd|4k)\\b"), " ")

    private fun words(s: String): Set<String> =
        strip(s).lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()

    private fun overlap(wanted: Set<String>, found: Set<String>): Float {
        if (wanted.isEmpty() || found.isEmpty()) return 0f
        return wanted.intersect(found).size.toFloat() / wanted.size
    }

    private companion object {
        const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
        const val PATHFINDER = "https://api-partner.spotify.com/pathfinder/v1/query"
        const val PATHFINDER_V2 = "https://api-partner.spotify.com/pathfinder/v2/query"
        const val SECRETS_URL =
            "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json"

        const val SEARCH_HASH = "eff59fa0a3d026b88b56fddbcf4bdfa16a186b8175a5c1a358c072e053c2e5b0"
        const val CANVAS_HASH = "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"

        const val MAX_BYTES = 24L * 1024L * 1024L
        const val SECRETS_TTL_MS = 6L * 60L * 60L * 1000L

        val JSON = "application/json".toMediaType()

        val STOP_WORDS = setOf("the", "and", "feat", "ft", "with", "from", "official", "lyric", "video")
    }
}
