package app.musiku.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.musiku.model.Playlist
import app.musiku.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.store by preferencesDataStore(name = "musiku")

private fun Track.toJson() = JSONObject()
    .put("videoId", videoId).put("title", title)
    .putOpt("artist", artist).putOpt("album", album).putOpt("albumId", albumId)
    .putOpt("artistId", artistId).putOpt("thumbs", thumbs).putOpt("duration", duration)

private fun JSONObject.toTrack() = Track(
    videoId = getString("videoId"),
    title = optString("title"),
    artist = optString("artist").ifEmpty { null },
    album = optString("album").ifEmpty { null },
    albumId = optString("albumId").ifEmpty { null },
    artistId = optString("artistId").ifEmpty { null },
    thumbs = optString("thumbs").ifEmpty { null },
    duration = optString("duration").ifEmpty { null },
)

private fun List<Track>.toJsonArray() = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

private fun parseTracks(raw: String?): List<Track> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { runCatching { a.getJSONObject(it).toTrack() }.getOrNull() }
    }.getOrDefault(emptyList())
}

/**
 * Everything the web build keeps in localStorage: favourites, playlists, recents and the
 * player settings. Held in memory as StateFlows, mirrored to DataStore on every change.
 */
class Library(private val ctx: Context, private val scope: CoroutineScope) {
    private val kLiked = stringPreferencesKey("liked")
    private val kPlaylists = stringPreferencesKey("playlists")
    private val kRecent = stringPreferencesKey("recent")
    private val kCrossfade = booleanPreferencesKey("crossfade")
    private val kCfSec = intPreferencesKey("crossfadeSec")
    private val kAutoplay = booleanPreferencesKey("autoplay")
    private val kShuffle = booleanPreferencesKey("shuffle")
    private val kRepeat = intPreferencesKey("repeat")
    private val kSpotifyMatches = stringPreferencesKey("spotifyMatches")

    private val _liked = MutableStateFlow<List<Track>>(emptyList())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _recent = MutableStateFlow<List<Track>>(emptyList())
    private val _crossfade = MutableStateFlow(false)
    private val _cfSec = MutableStateFlow(DEFAULT_CF_SEC)
    private val _autoplay = MutableStateFlow(true)
    private val _shuffle = MutableStateFlow(false)
    private val _repeat = MutableStateFlow(0)

    /** Spotify track URI → the YouTube Music track it was matched to, so syncs skip old work. */
    private val spotifyCache = java.util.concurrent.ConcurrentHashMap<String, Track>()
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()

    val liked: StateFlow<List<Track>> = _liked
    val playlists: StateFlow<List<Playlist>> = _playlists
    val recent: StateFlow<List<Track>> = _recent
    val crossfade: StateFlow<Boolean> = _crossfade
    val cfSec: StateFlow<Int> = _cfSec
    val autoplay: StateFlow<Boolean> = _autoplay
    val shuffle: StateFlow<Boolean> = _shuffle
    val repeat: StateFlow<Int> = _repeat

    @Volatile var loaded = false
        private set

    init {
        scope.launch {
            val p: Preferences = ctx.store.data.first()
            _liked.value = parseTracks(p[kLiked])
            _recent.value = parseTracks(p[kRecent])
            _playlists.value = runCatching {
                val a = JSONArray(p[kPlaylists] ?: "[]")
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        createdAt = o.optLong("createdAt"),
                        tracks = parseTracks(o.optJSONArray("tracks")?.toString()),
                        source = o.optString("source").ifEmpty { null },
                        sourceId = o.optString("sourceId").ifEmpty { null },
                    )
                }
            }.getOrDefault(emptyList())
            runCatching {
                val o = JSONObject(p[kSpotifyMatches] ?: "{}")
                o.keys().forEach { key ->
                    runCatching { spotifyCache[key] = o.getJSONObject(key).toTrack() }
                }
            }
            _crossfade.value = p[kCrossfade] ?: false
            _cfSec.value = (p[kCfSec] ?: DEFAULT_CF_SEC).takeIf { it in CF_PRESETS } ?: DEFAULT_CF_SEC
            _autoplay.value = p[kAutoplay] ?: true
            _shuffle.value = p[kShuffle] ?: false
            _repeat.value = (p[kRepeat] ?: 0).coerceIn(0, 2)
            loaded = true
            ready.complete(Unit)
        }
    }

    private fun save(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { ctx.store.edit { block(it) } }
    }

    private fun saveLiked() = save { it[kLiked] = _liked.value.toJsonArray().toString() }
    private fun saveRecent() = save { it[kRecent] = _recent.value.toJsonArray().toString() }
    private fun savePlaylists() = save { p ->
        val a = JSONArray()
        _playlists.value.forEach { pl ->
            a.put(
                JSONObject().put("id", pl.id).put("name", pl.name).put("createdAt", pl.createdAt)
                    .put("tracks", pl.tracks.toJsonArray())
                    .putOpt("source", pl.source).putOpt("sourceId", pl.sourceId)
            )
        }
        p[kPlaylists] = a.toString()
    }

    // ── favourites ───────────────────────────────────────────────────────────
    fun isLiked(id: String) = _liked.value.any { it.videoId == id }

    fun toggleLike(t: Track) {
        _liked.value = if (isLiked(t.videoId)) _liked.value.filterNot { it.videoId == t.videoId } else listOf(t) + _liked.value
        saveLiked()
    }

    // ── recents ──────────────────────────────────────────────────────────────
    fun pushRecent(t: Track) {
        _recent.value = (listOf(t) + _recent.value.filterNot { it.videoId == t.videoId }).take(30)
        saveRecent()
    }

    // ── playlists ────────────────────────────────────────────────────────────
    fun createPlaylist(name: String, first: Track? = null): Playlist {
        val pl = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Playlist baru" }, System.currentTimeMillis(), listOfNotNull(first))
        _playlists.value = listOf(pl) + _playlists.value
        savePlaylists()
        return pl
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        savePlaylists()
    }

    fun renamePlaylist(id: String, name: String) {
        _playlists.value = _playlists.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        savePlaylists()
    }

    fun addToPlaylist(id: String, t: Track): Boolean {
        val pl = _playlists.value.firstOrNull { it.id == id } ?: return false
        if (pl.tracks.any { it.videoId == t.videoId }) return false
        _playlists.value = _playlists.value.map { if (it.id == id) it.copy(tracks = it.tracks + t) else it }
        savePlaylists()
        return true
    }

    fun removeFromPlaylist(id: String, index: Int) {
        _playlists.value = _playlists.value.map {
            if (it.id == id && index in it.tracks.indices) it.copy(tracks = it.tracks.toMutableList().also { l -> l.removeAt(index) }) else it
        }
        savePlaylists()
    }

    /** Insert a playlist mirrored from Spotify at the top of Pustaka. */
    fun importPlaylist(name: String, tracks: List<Track>, sourceId: String): Playlist {
        val pl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Playlist Spotify" },
            createdAt = System.currentTimeMillis(),
            tracks = tracks,
            source = "spotify",
            sourceId = sourceId,
        )
        _playlists.value = listOf(pl) + _playlists.value
        savePlaylists()
        return pl
    }

    /** Replace a mirrored playlist's tracks after a sync, keeping its id and any local rename. */
    fun replaceTracks(id: String, tracks: List<Track>): Playlist? {
        var updated: Playlist? = null
        _playlists.value = _playlists.value.map {
            if (it.id == id) it.copy(tracks = tracks).also { p -> updated = p } else it
        }
        if (updated != null) savePlaylists()
        return updated
    }

    // ── spotify match cache ──────────────────────────────────────────────────
    suspend fun awaitReady() = ready.await()

    fun spotifyMatches(): Map<String, Track> = HashMap(spotifyCache)

    fun saveSpotifyMatches(matches: Map<String, Track>) {
        spotifyCache.clear()
        spotifyCache.putAll(matches)
        save { p ->
            val o = JSONObject()
            matches.forEach { (uri, track) -> o.put(uri, track.toJson()) }
            p[kSpotifyMatches] = o.toString()
        }
    }

    // ── settings ─────────────────────────────────────────────────────────────
    fun setCrossfade(on: Boolean) { _crossfade.value = on; save { it[kCrossfade] = on } }
    fun setCfSec(n: Int) { val v = if (n in CF_PRESETS) n else DEFAULT_CF_SEC; _cfSec.value = v; save { it[kCfSec] = v } }
    fun setAutoplay(on: Boolean) { _autoplay.value = on; save { it[kAutoplay] = on } }
    fun setShuffle(on: Boolean) { _shuffle.value = on; save { it[kShuffle] = on } }
    fun setRepeat(mode: Int) { _repeat.value = mode; save { it[kRepeat] = mode } }

    companion object {
        const val DEFAULT_CF_SEC = 4
        val CF_PRESETS = listOf(2, 4, 6, 8, 10, 12)
    }
}
