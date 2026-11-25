package app.musiku.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.musiku.data.Library
import app.musiku.data.YtMusic
import app.musiku.model.RepeatMode
import app.musiku.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the UI renders. Playback position is a separate flow: it ticks 4×/s. */
data class PlayerUi(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationSec: Float = 0f,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val volume: Float = 0.8f,
    /** index of the first track that autoplay appended (null = none) */
    val autoplayFrom: Int? = null,
    val sleepLabel: String? = null,
)

/**
 * UI-side handle on the playback service. Port of lib/player.tsx: queue, shuffle/repeat,
 * autoplay-from-related, sleep timer, crossfade, recents. Commands issued before the
 * service connection is up are queued and replayed.
 */
class PlayerController(
    private val ctx: Context,
    private val scope: CoroutineScope,
    val library: Library,
    private val yt: YtMusic,
    private val onTrackError: (String) -> Unit = {},
) {
    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui
    private val _position = MutableStateFlow(0f)
    val position: StateFlow<Float> = _position
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** short toasts ("Ditambahkan ke antrian") for the UI to show */
    val messages: SharedFlow<String> = _messages

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var queueCache: List<Track> = emptyList()
    private var autoplayFrom: Int? = null
    private var fetchingRelated = false
    private var errorStreak = 0
    private var sleepJob: Job? = null
    private var lastRecentId: String? = null
    private var historyTrackId: String? = null
    private var historyMaxMs = 0L
    private var historyRecorded = false

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        connect()
        scope.launch { tick() }
        ContextCompat.registerReceiver(
            ctx,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) = syncVolume()
            },
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        syncVolume()
    }

    private fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            scope.launch {
                library.awaitReady()
                c.shuffleModeEnabled = library.shuffle.value
                c.repeatMode = when (library.repeat.value) { 1 -> Player.REPEAT_MODE_ALL; 2 -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
                syncUi()
            }
            rebuildQueue()
            syncUi()
            pending.toList().also { pending.clear() }.forEach { it(c) }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    // ── observing the player ─────────────────────────────────────────────────

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) rebuildQueue()
            syncUi()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED)) {
                noteRecent()
                maybeAutoplay()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) errorStreak = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("Musiku", "playback error ${error.errorCodeName}: ${error.message}", error)
            _ui.value.current?.videoId?.let(onTrackError)
            say("Gagal memutar lagu ini")
            val c = controller ?: return
            errorStreak++
            if (errorStreak <= 3 && c.hasNextMediaItem()) {
                scope.launch {
                    delay(600)
                    c.seekToNextMediaItem()
                    c.prepare()
                    c.play()
                }
            } else {
                c.pause()
            }
        }
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        queueCache = List(c.mediaItemCount) { c.getMediaItemAt(it).toTrack() }
        autoplayFrom = autoplayFrom?.takeIf { it < queueCache.size }
    }

    private fun syncUi() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val dur = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { it / 1000f } ?: 0f
        _position.value = (c.currentPosition / 1000f).coerceAtLeast(0f)
        _ui.value = _ui.value.copy(
            current = queueCache.getOrNull(idx),
            queue = queueCache,
            index = idx,
            playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            durationSec = dur,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) { Player.REPEAT_MODE_ALL -> RepeatMode.All; Player.REPEAT_MODE_ONE -> RepeatMode.One; else -> RepeatMode.Off },
            autoplayFrom = autoplayFrom,
        )
    }

    private fun noteRecent() {
        val t = _ui.value.current ?: return
        if (t.videoId != lastRecentId) {
            lastRecentId = t.videoId
            library.pushRecent(t)
        }
    }

    /**
     * A play counts once the song has actually run for 30 s or half its duration, whichever
     * comes first. A quick skip never reaches the threshold. Repeat-one wraps the playhead back
     * to zero, so that case starts a fresh measurement.
     */
    private fun noteHistory(c: MediaController) {
        if (!library.historyEnabled.value) return
        val t = _ui.value.current ?: return
        val pos = c.currentPosition.coerceAtLeast(0L)
        val dur = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val threshold = if (dur > 0) minOf(30_000L, dur / 2) else 30_000L
        if (t.videoId != historyTrackId) {
            historyTrackId = t.videoId
            historyMaxMs = 0L
            historyRecorded = false
        } else if (historyRecorded && pos < 1_000L && historyMaxMs >= threshold) {
            historyMaxMs = 0L
            historyRecorded = false
        }
        if (historyRecorded || !c.isPlaying || !c.playWhenReady) return
        if (pos > historyMaxMs) historyMaxMs = pos
        if (historyMaxMs >= threshold) {
            historyRecorded = true
            library.recordPlay(t, dur)
        }
    }

    /** Updates playback position and play history while playing (crossfade runs in PlaybackService). */
    private suspend fun tick() {
        ui.map { it.playing }
            .distinctUntilChanged()
            .collectLatest { isPlaying ->
                if (!isPlaying) return@collectLatest
                while (true) {
                    val c = controller
                    if (c != null) {
                        val posSec = c.currentPosition / 1000f
                        _position.value = posSec
                        noteHistory(c)
                        val dur = c.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { it / 1000f } ?: 0f
                        if (dur != _ui.value.durationSec) _ui.value = _ui.value.copy(durationSec = dur)
                    }
                    delay(250)
                }
            }
    }

    // ── system volume (the slider in the player drives the media stream) ─────

    private fun syncVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _ui.value = _ui.value.copy(volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat())
    }

    fun setVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).roundToInt(), 0)
        _ui.value = _ui.value.copy(volume = v.coerceIn(0f, 1f))
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val params = androidx.media3.common.PlaybackParameters(speed, pitch)
        val c = controller
        if (c != null) c.setPlaybackParameters(params) else pending.add { it.setPlaybackParameters(params) }
    }

    // ── queue ────────────────────────────────────────────────────────────────

    fun playTracks(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        autoplayFrom = null
        errorStreak = 0
        withController { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, start.coerceIn(0, tracks.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    /** Play [t]; if it sits inside [context] the whole list becomes the queue. */
    fun playOne(t: Track, context: List<Track> = emptyList()) {
        val i = context.indexOfFirst { it.videoId == t.videoId }
        if (i >= 0) playTracks(context, i) else playTracks(listOf(t))
    }

    fun enqueue(t: Track, next: Boolean) {
        withController { c ->
            if (c.mediaItemCount == 0) {
                playTracks(listOf(t)); return@withController
            }
            val item: MediaItem = t.toMediaItem()
            if (next) {
                val at = c.currentMediaItemIndex + 1
                c.addMediaItem(at, item)
                autoplayFrom = autoplayFrom?.let { if (at <= it) it + 1 else it }
            } else {
                val at = autoplayFrom ?: c.mediaItemCount
                c.addMediaItem(at, item)
                autoplayFrom = autoplayFrom?.plus(1)
            }
        }
        say(if (next) "Diputar berikutnya" else "Ditambahkan ke antrian")
    }

    fun removeFromQueue(i: Int) = withController { c ->
        if (i !in 0 until c.mediaItemCount || i == c.currentMediaItemIndex) return@withController
        c.removeMediaItem(i)
        autoplayFrom = autoplayFrom?.let { if (i < it) it - 1 else it }
    }

    fun jumpTo(i: Int) = withController { c ->
        if (i !in 0 until c.mediaItemCount) return@withController
        errorStreak = 0
        c.seekTo(i, 0L)
        c.play()
    }

    // ── transport ────────────────────────────────────────────────────────────

    fun toggle() = withController { c ->
        when {
            c.playbackState == Player.STATE_ENDED -> { errorStreak = 0; c.seekTo(0L); c.play() }
            c.playWhenReady -> c.pause()
            else -> { errorStreak = 0; if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
        }
    }

    fun next() = withController { c ->
        errorStreak = 0
        if (c.hasNextMediaItem()) c.seekToNextMediaItem() else say("Tidak ada lagu berikutnya")
    }
    fun prev() = withController {
        errorStreak = 0
        it.seekToPrevious()
    }
    fun seek(sec: Float) = withController { it.seekTo((sec * 1000).toLong()); _position.value = sec }

    /**
     * Playhead read straight from the session, for per-frame lyric rendering. The [position]
     * flow only ticks four times a second, which is too coarse for word sweeps.
     */
    fun currentPositionMs(): Long = controller?.currentPosition ?: (_position.value * 1000f).toLong()

    fun cycleRepeat() = withController { c ->
        val nextMode = when (_ui.value.repeat) { RepeatMode.Off -> RepeatMode.All; RepeatMode.All -> RepeatMode.One; RepeatMode.One -> RepeatMode.Off }
        c.repeatMode = when (nextMode) { RepeatMode.All -> Player.REPEAT_MODE_ALL; RepeatMode.One -> Player.REPEAT_MODE_ONE; RepeatMode.Off -> Player.REPEAT_MODE_OFF }
        library.setRepeat(nextMode.ordinal)
    }

    fun toggleShuffle() = withController { c ->
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        library.setShuffle(c.shuffleModeEnabled)
    }

    // ── autoplay ─────────────────────────────────────────────────────────────

    fun setAutoplay(on: Boolean) {
        library.setAutoplay(on)
        if (on) maybeAutoplay()
    }

    private fun maybeAutoplay() {
        val c = controller ?: return
        if (!library.autoplay.value || fetchingRelated) return
        if (c.mediaItemCount == 0 || c.repeatMode != Player.REPEAT_MODE_OFF) return
        if (c.mediaItemCount - c.currentMediaItemIndex - 1 > 0) return
        val seed = c.currentMediaItem?.mediaId ?: return
        fetchingRelated = true
        scope.launch {
            val related = runCatching { yt.related(seed) }.getOrDefault(emptyList())
            val live = controller
            if (live != null && live.currentMediaItem?.mediaId == seed) {
                val have = queueCache.map { it.videoId }.toSet()
                val add = related.filter { it.videoId !in have }.take(20)
                if (add.isNotEmpty()) {
                    if (autoplayFrom == null) autoplayFrom = live.mediaItemCount
                    live.addMediaItems(add.map { it.toMediaItem() })
                }
            }
            fetchingRelated = false
        }
    }

    // ── crossfade / sleep timer / likes ──────────────────────────────────────

    fun toggleCrossfade() {
        val on = !library.crossfade.value
        library.setCrossfade(on)
        say(if (on) "Crossfade aktif — ${library.cfSec.value} detik" else "Crossfade dimatikan")
    }

    fun setCfSec(n: Int) {
        library.setCfSec(n)
        say("Crossfade $n detik")
    }

    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _ui.value = _ui.value.copy(sleepLabel = null)
            say("Pewaktu tidur dimatikan")
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _ui.value = _ui.value.copy(sleepLabel = "Aktif sampai " + SimpleDateFormat("HH.mm", Locale("id", "ID")).format(Date(at)))
        say("Musik berhenti dalam $minutes menit")
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _ui.value = _ui.value.copy(sleepLabel = null)
        }
    }

    fun toggleLike(t: Track) {
        val was = library.isLiked(t.videoId)
        library.toggleLike(t)
        say(if (was) "Dihapus dari favorit" else "Ditambahkan ke favorit")
    }

    fun say(msg: String) {
        _messages.tryEmit(msg)
    }
}
