package app.musiku.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.musiku.eq.EqualizerBand
import app.musiku.eq.EqualizerFilterType
import app.musiku.eq.EqualizerPresets
import app.musiku.eq.EqualizerProfile
import app.musiku.model.Playlist
import app.musiku.model.PlayHistoryEntry
import app.musiku.model.Track
import app.musiku.ui.nowplaying.PlayerArtworkStyle
import app.musiku.ui.nowplaying.PlayerBackgroundStyle
import app.musiku.ui.nowplaying.PlayerLook
import app.musiku.ui.nowplaying.PlayerSlot
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

private fun JSONObject.toHistoryEntryOrNull(): PlayHistoryEntry? {
    val id = optString("id")
    val title = optString("title")
    if (id.isEmpty() || title.isEmpty()) return null
    return PlayHistoryEntry(
        id = id,
        title = title,
        artist = optString("artist").ifEmpty { null },
        thumbnail = optString("thumbnail").ifEmpty { null },
        durationMs = optLong("durationMs"),
        playedAt = if (has("playedAt")) getLong("playedAt") else 0L,
    )
}

/** Corrupt or over-long history never takes the app down: bad rows are dropped, the rest is capped. */
private fun parseHistory(raw: String?): List<PlayHistoryEntry> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { runCatching { a.getJSONObject(it).toHistoryEntryOrNull() }.getOrNull() }
    }.getOrDefault(emptyList())
        .sortedByDescending { it.playedAt }
        .take(Library.HISTORY_LIMIT)
}

private fun List<PlayHistoryEntry>.toHistoryJson(): JSONArray = JSONArray().also { a ->
    forEach { e ->
        a.put(
            JSONObject().put("id", e.id).put("title", e.title).putOpt("artist", e.artist)
                .putOpt("thumbnail", e.thumbnail).put("durationMs", e.durationMs).put("playedAt", e.playedAt),
        )
    }
}

/**
 * Everything the web build keeps in localStorage: favourites, playlists, recents and the
 * player settings. Held in memory as StateFlows, mirrored to DataStore on every change.
 */
class Library(private val ctx: Context, private val scope: CoroutineScope) {
    private val kLiked = stringPreferencesKey("liked")
    private val kDownloadedTracks = stringPreferencesKey("downloadedTracks")
    private val kPlaylists = stringPreferencesKey("playlists")
    private val kRecent = stringPreferencesKey("recent")
    private val kPlayHistory = stringPreferencesKey("playHistory")
    private val kHistoryEnabled = booleanPreferencesKey("historyEnabled")
    private val kCrossfade = booleanPreferencesKey("crossfade")
    private val kCfSec = intPreferencesKey("crossfadeSec")
    private val kAutoplay = booleanPreferencesKey("autoplay")
    private val kShuffle = booleanPreferencesKey("shuffle")
    private val kRepeat = intPreferencesKey("repeat")
    private val kSpotifyMatches = stringPreferencesKey("spotifyMatches")
    private val kBookmarkedArtists = stringPreferencesKey("bookmarkedArtists")
    private val kLowDevice = booleanPreferencesKey("lowDevice")
    private val kCanvasEnabled = booleanPreferencesKey("canvasEnabled")
    private val kArtistVideo = booleanPreferencesKey("artistVideo")
    private val kPlayerLook = intPreferencesKey("playerLook")
    private val kPlayerArtworkStyle = intPreferencesKey("playerArtworkStyle")
    private val kPlayerBackgroundStyle = intPreferencesKey("playerBackgroundStyle")
    private val kPlayerLayoutOrder = stringPreferencesKey("playerLayoutOrder")
    private val kPlayerLayoutHidden = stringPreferencesKey("playerLayoutHidden")
    private val kEqEnabled = booleanPreferencesKey("eqEnabled")
    private val kEqPreset = stringPreferencesKey("eqPreset")
    private val kEqProfile = stringPreferencesKey("eqProfile")

    private val _liked = MutableStateFlow<List<Track>>(emptyList())
    private val _downloadedTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _bookmarkedArtists = MutableStateFlow<Set<String>>(emptySet())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _recent = MutableStateFlow<List<Track>>(emptyList())
    private val _playHistory = MutableStateFlow<List<PlayHistoryEntry>>(emptyList())
    private val _historyEnabled = MutableStateFlow(true)
    private val _crossfade = MutableStateFlow(false)
    private val _cfSec = MutableStateFlow(DEFAULT_CF_SEC)
    private val _autoplay = MutableStateFlow(true)
    private val _shuffle = MutableStateFlow(false)
    private val _repeat = MutableStateFlow(0)
    private val _lowDevice = MutableStateFlow(false)
    private val _canvasEnabled = MutableStateFlow(false)
    private val _artistVideo = MutableStateFlow(false)
    private val _playerLook = MutableStateFlow(PlayerLook.Squared)
    private val _playerArtworkStyle = MutableStateFlow(PlayerArtworkStyle.Card)
    private val _playerBackgroundStyle = MutableStateFlow(PlayerBackgroundStyle.Gradient)
    private val _playerLayoutOrder = MutableStateFlow(PlayerSlot.DefaultOrder)
    private val _playerLayoutHidden = MutableStateFlow(emptySet<PlayerSlot>())
    private val _eqEnabled = MutableStateFlow(false)
    private val _eqPreset = MutableStateFlow("Flat")
    private val _eqProfile = MutableStateFlow(EqualizerProfile())

    /** Spotify track URI → the YouTube Music track it was matched to, so syncs skip old work. */
    private val spotifyCache = java.util.concurrent.ConcurrentHashMap<String, Track>()
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val pendingLock = Any()
    private val pendingMutations = mutableListOf<() -> Unit>()

    val liked: StateFlow<List<Track>> = _liked
    val downloadedTracks: StateFlow<List<Track>> = _downloadedTracks
    val bookmarkedArtists: StateFlow<Set<String>> = _bookmarkedArtists
    val playlists: StateFlow<List<Playlist>> = _playlists
    val recent: StateFlow<List<Track>> = _recent
    val playHistory: StateFlow<List<PlayHistoryEntry>> = _playHistory
    val historyEnabled: StateFlow<Boolean> = _historyEnabled
    val crossfade: StateFlow<Boolean> = _crossfade
    val cfSec: StateFlow<Int> = _cfSec
    val autoplay: StateFlow<Boolean> = _autoplay
    val shuffle: StateFlow<Boolean> = _shuffle
    val repeat: StateFlow<Int> = _repeat
    val lowDevice: StateFlow<Boolean> = _lowDevice
    /** Spotify Canvas behind the player artwork. Opt-in: it needs a Spotify session cookie. */
    val canvasEnabled: StateFlow<Boolean> = _canvasEnabled
    /** Motion artwork in the artist hero. Opt-in: it streams an mp4 from a third-party service. */
    val artistVideoEnabled: StateFlow<Boolean> = _artistVideo
    val playerLook: StateFlow<PlayerLook> = _playerLook
    val playerArtworkStyle: StateFlow<PlayerArtworkStyle> = _playerArtworkStyle
    val playerBackgroundStyle: StateFlow<PlayerBackgroundStyle> = _playerBackgroundStyle
    val playerLayoutOrder: StateFlow<List<PlayerSlot>> = _playerLayoutOrder
    val playerLayoutHidden: StateFlow<Set<PlayerSlot>> = _playerLayoutHidden
    val eqEnabled: StateFlow<Boolean> = _eqEnabled
    val eqPreset: StateFlow<String> = _eqPreset
    val eqProfile: StateFlow<EqualizerProfile> = _eqProfile

    @Volatile var loaded = false
        private set

    init {
        scope.launch {
            val p: Preferences = ctx.store.data.first()
            _liked.value = parseTracks(p[kLiked])
            _downloadedTracks.value = parseTracks(p[kDownloadedTracks])
            _bookmarkedArtists.value = runCatching {
                val a = JSONArray(p[kBookmarkedArtists] ?: "[]")
                (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet()
            }.getOrDefault(emptySet())
            _recent.value = parseTracks(p[kRecent])
            _playHistory.value = parseHistory(p[kPlayHistory])
            _historyEnabled.value = p[kHistoryEnabled] ?: true
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
            _lowDevice.value = p[kLowDevice] ?: false
            _canvasEnabled.value = p[kCanvasEnabled] ?: false
            _artistVideo.value = p[kArtistVideo] ?: false
            _playerLook.value = PlayerLook.entries.getOrElse(p[kPlayerLook] ?: 0) { PlayerLook.Squared }
            _playerArtworkStyle.value = PlayerArtworkStyle.from(p[kPlayerArtworkStyle] ?: 0)
            _playerBackgroundStyle.value = PlayerBackgroundStyle.from(p[kPlayerBackgroundStyle] ?: 1)
            _playerLayoutOrder.value = PlayerSlot.orderFrom(p[kPlayerLayoutOrder])
            _playerLayoutHidden.value = PlayerSlot.hiddenFrom(p[kPlayerLayoutHidden])
            _eqEnabled.value = p[kEqEnabled] ?: false
            _eqPreset.value = p[kEqPreset] ?: "Flat"
            _eqProfile.value = parseEqProfile(p[kEqProfile]) ?: EqualizerProfile()
            val toRun = synchronized(pendingLock) {
                loaded = true
                pendingMutations.toList().also { pendingMutations.clear() }
            }
            toRun.forEach { it() }
            ready.complete(Unit)
        }
    }

    /**
     * Runs [block] immediately once DataStore has loaded, or queues it to run right after initial
     * load completes so early mutations never overwrite persisted state with empty defaults.
     */
    private fun afterLoad(block: () -> Unit) {
        val runNow = synchronized(pendingLock) {
            if (loaded) true
            else {
                pendingMutations += block
                false
            }
        }
        if (runNow) block()
    }

    private fun save(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        // A full or corrupt store must never take down playback: the in-memory copy stays authoritative.
        scope.launch { runCatching { ctx.store.edit { block(it) } } }
    }

    private fun saveLiked() = save { it[kLiked] = _liked.value.toJsonArray().toString() }
    private fun saveDownloadedTracks() = save { it[kDownloadedTracks] = _downloadedTracks.value.toJsonArray().toString() }
    private fun saveBookmarkedArtists() = save {
        val a = JSONArray()
        _bookmarkedArtists.value.forEach(a::put)
        it[kBookmarkedArtists] = a.toString()
    }
    private fun saveRecent() = save { it[kRecent] = _recent.value.toJsonArray().toString() }
    private fun savePlayHistory() = save { p ->
        runCatching { p[kPlayHistory] = _playHistory.value.toHistoryJson().toString() }
    }
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

    fun toggleLike(t: Track) = afterLoad {
        _liked.value = if (isLiked(t.videoId)) _liked.value.filterNot { it.videoId == t.videoId } else listOf(t) + _liked.value
        saveLiked()
    }

    // ── offline downloads ────────────────────────────────────────────────────
    fun saveDownloadedTrack(t: Track) = afterLoad {
        _downloadedTracks.value = listOf(t) + _downloadedTracks.value.filterNot { it.videoId == t.videoId }
        saveDownloadedTracks()
    }

    fun saveDownloadedTracks(tracks: List<Track>) = afterLoad {
        val incomingIds = tracks.map { it.videoId }.toSet()
        _downloadedTracks.value = tracks.distinctBy { it.videoId } +
            _downloadedTracks.value.filterNot { it.videoId in incomingIds }
        saveDownloadedTracks()
    }

    fun removeDownloadedTrack(videoId: String) = afterLoad {
        _downloadedTracks.value = _downloadedTracks.value.filterNot { it.videoId == videoId }
        saveDownloadedTracks()
    }

    fun clearDownloadedTracks() = afterLoad {
        _downloadedTracks.value = emptyList()
        saveDownloadedTracks()
    }

    fun isArtistBookmarked(id: String) = id in _bookmarkedArtists.value

    fun toggleArtistBookmark(id: String) = afterLoad {
        _bookmarkedArtists.value = if (id in _bookmarkedArtists.value) _bookmarkedArtists.value - id else _bookmarkedArtists.value + id
        saveBookmarkedArtists()
    }

    // ── recents ──────────────────────────────────────────────────────────────
    fun pushRecent(t: Track) = afterLoad {
        _recent.value = (listOf(t) + _recent.value.filterNot { it.videoId == t.videoId }).take(30)
        saveRecent()
    }

    // ── play history ─────────────────────────────────────────────────────────
    /** Called once a track has actually been played, not on a quick skip. */
    fun recordPlay(t: Track, durationMs: Long) = afterLoad {
        if (!_historyEnabled.value || t.videoId.isBlank()) return@afterLoad
        val entry = PlayHistoryEntry(
            id = t.videoId,
            title = t.title,
            artist = t.artist?.ifEmpty { null },
            thumbnail = t.thumbs?.ifEmpty { null },
            durationMs = durationMs.coerceAtLeast(0L),
            playedAt = System.currentTimeMillis(),
        )
        _playHistory.value = (listOf(entry) + _playHistory.value.filterNot { it.id == entry.id }).take(HISTORY_LIMIT)
        savePlayHistory()
    }

    fun removeHistoryEntry(id: String) = afterLoad {
        _playHistory.value = _playHistory.value.filterNot { it.id == id }
        savePlayHistory()
    }

    fun clearHistory() = afterLoad {
        _playHistory.value = emptyList()
        savePlayHistory()
    }

    fun setHistoryEnabled(on: Boolean) = afterLoad {
        _historyEnabled.value = on
        save { it[kHistoryEnabled] = on }
    }

    // ── playlists ────────────────────────────────────────────────────────────
    fun createPlaylist(name: String, first: Track? = null): Playlist {
        val pl = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Playlist baru" }, System.currentTimeMillis(), listOfNotNull(first))
        afterLoad {
            _playlists.value = listOf(pl) + _playlists.value
            savePlaylists()
        }
        return pl
    }

    fun deletePlaylist(id: String) = afterLoad {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        savePlaylists()
    }

    fun renamePlaylist(id: String, name: String) = afterLoad {
        _playlists.value = _playlists.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        savePlaylists()
    }

    fun addToPlaylist(id: String, t: Track): Boolean {
        val pl = _playlists.value.firstOrNull { it.id == id } ?: return false
        if (pl.tracks.any { it.videoId == t.videoId }) return false
        afterLoad {
            val current = _playlists.value.firstOrNull { it.id == id } ?: return@afterLoad
            if (current.tracks.any { it.videoId == t.videoId }) return@afterLoad
            _playlists.value = _playlists.value.map { if (it.id == id) it.copy(tracks = it.tracks + t) else it }
            savePlaylists()
        }
        return true
    }

    fun removeFromPlaylist(id: String, index: Int) = afterLoad {
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
        afterLoad {
            _playlists.value = listOf(pl) + _playlists.value
            savePlaylists()
        }
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
    fun setCrossfade(on: Boolean) = afterLoad { _crossfade.value = on; save { it[kCrossfade] = on } }
    fun setCfSec(n: Int) = afterLoad { val v = if (n in CF_PRESETS) n else DEFAULT_CF_SEC; _cfSec.value = v; save { it[kCfSec] = v } }
    fun setAutoplay(on: Boolean) = afterLoad { _autoplay.value = on; save { it[kAutoplay] = on } }
    fun setShuffle(on: Boolean) = afterLoad { _shuffle.value = on; save { it[kShuffle] = on } }
    fun setRepeat(mode: Int) = afterLoad { _repeat.value = mode; save { it[kRepeat] = mode } }
    fun setLowDevice(on: Boolean) = afterLoad { _lowDevice.value = on; save { it[kLowDevice] = on } }
    fun setCanvasEnabled(on: Boolean) = afterLoad { _canvasEnabled.value = on; save { it[kCanvasEnabled] = on } }
    fun setArtistVideoEnabled(on: Boolean) = afterLoad { _artistVideo.value = on; save { it[kArtistVideo] = on } }

    // ── player design (Convx `PlayerLook` / `PlayerArtworkStyle` / `PlayerBackgroundStyle`) ──
    fun setPlayerLook(look: PlayerLook) = afterLoad {
        _playerLook.value = look
        save { it[kPlayerLook] = look.ordinal }
    }

    fun setPlayerArtworkStyle(style: PlayerArtworkStyle) = afterLoad {
        _playerArtworkStyle.value = style
        save { it[kPlayerArtworkStyle] = style.ordinal }
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundStyle) = afterLoad {
        _playerBackgroundStyle.value = style
        save { it[kPlayerBackgroundStyle] = style.ordinal }
    }

    fun setPlayerLayoutOrder(order: List<PlayerSlot>) = afterLoad {
        val v = PlayerSlot.normalize(order)
        _playerLayoutOrder.value = v
        save { it[kPlayerLayoutOrder] = PlayerSlot.serializeOrder(v) }
    }

    fun setPlayerLayoutHidden(hidden: Set<PlayerSlot>) = afterLoad {
        val v = hidden.filter { it.hideable }.toSet()
        _playerLayoutHidden.value = v
        save { it[kPlayerLayoutHidden] = PlayerSlot.serializeHidden(v) }
    }

    // ── equalizer ────────────────────────────────────────────────────────────
    fun setEqEnabled(on: Boolean) = afterLoad {
        _eqEnabled.value = on
        save { it[kEqEnabled] = on }
    }

    fun selectEqPreset(name: String) = afterLoad {
        val profile = EqualizerPresets.Presets[name] ?: return@afterLoad
        _eqPreset.value = name
        _eqProfile.value = profile
        saveEq(name, profile)
    }

    fun setEqBandGain(index: Int, gainDb: Double) = afterLoad {
        val cur = _eqProfile.value
        if (index !in cur.bands.indices) return@afterLoad
        val updated = cur.bands.toMutableList().also {
            it[index] = it[index].copy(gain = gainDb.coerceIn(-12.0, 12.0))
        }
        val next = cur.copy(bands = updated)
        _eqPreset.value = "Custom"
        _eqProfile.value = next
        saveEq("Custom", next)
    }

    fun setEqPreamp(preampDb: Double) = afterLoad {
        val next = _eqProfile.value.copy(preamp = preampDb.coerceIn(-12.0, 6.0))
        _eqProfile.value = next
        saveEq(_eqPreset.value, next)
    }

    fun importEqProfile(name: String, profile: EqualizerProfile) = afterLoad {
        _eqPreset.value = name
        _eqProfile.value = profile
        _eqEnabled.value = true
        save {
            it[kEqEnabled] = true
            it[kEqPreset] = name
            it[kEqProfile] = serializeEqProfile(profile)
        }
    }

    fun resetEq() = afterLoad {
        val flat = EqualizerProfile()
        _eqPreset.value = "Flat"
        _eqProfile.value = flat
        saveEq("Flat", flat)
    }

    private fun saveEq(preset: String, profile: EqualizerProfile) = save {
        it[kEqPreset] = preset
        it[kEqProfile] = serializeEqProfile(profile)
    }

    private fun serializeEqProfile(p: EqualizerProfile): String {
        val arr = JSONArray()
        p.bands.forEach { b ->
            arr.put(
                JSONObject()
                    .put("f", b.frequency)
                    .put("g", b.gain)
                    .put("q", b.q)
                    .put("t", b.filterType.name)
                    .put("e", b.enabled),
            )
        }
        return JSONObject().put("preamp", p.preamp).put("bands", arr).toString()
    }

    private fun parseEqProfile(raw: String?): EqualizerProfile? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val preamp = o.optDouble("preamp", 0.0)
            val arr = o.optJSONArray("bands") ?: return null
            val bands = (0 until arr.length()).mapNotNull { i ->
                val b = arr.optJSONObject(i) ?: return@mapNotNull null
                EqualizerBand(
                    frequency = b.optDouble("f", 1000.0),
                    gain = b.optDouble("g", 0.0),
                    q = b.optDouble("q", 1.41),
                    filterType = runCatching { EqualizerFilterType.valueOf(b.optString("t", "PK")) }
                        .getOrDefault(EqualizerFilterType.PK),
                    enabled = b.optBoolean("e", true),
                )
            }
            if (bands.isEmpty()) null else EqualizerProfile(preamp, bands)
        }.getOrNull()
    }

    companion object {
        /** JSON string in DataStore is capped well below the ~5 MB localStorage budget. */
        const val HISTORY_LIMIT = 300
        const val DEFAULT_CF_SEC = 4
        val CF_PRESETS = listOf(2, 4, 6, 8, 10, 12)
    }
}
