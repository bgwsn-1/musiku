package app.musiku.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dual-ExoPlayer ping-pong crossfade engine adapted from Convx's CrossfadeController.
 *
 * Pre-arms the standby ExoPlayer ~6 seconds before the fade window so the next track's
 * stream URL is already resolved and buffered before overlapping playback begins.
 */
@UnstableApi
class CrossfadeEngine(
    private val scope: CoroutineScope,
    private val active: () -> ExoPlayer,
    private val standby: () -> ExoPlayer,
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit,
    private val isEnabled: () -> Boolean,
    private val fadeDurationMs: () -> Long,
) {
    private enum class Phase { IDLE, ARMING, FADING }

    private var phase = Phase.IDLE
    private var armedForMediaId: String? = null
    private var armedNextIndex: Int = C.INDEX_UNSET
    private var outgoing: ExoPlayer? = null
    private var incoming: ExoPlayer? = null
    private var handedOff = false
    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                step()
                delay(if (phase == Phase.FADING) 40L else 250L)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        bail()
    }

    private fun step() {
        if (!isEnabled()) {
            if (phase != Phase.IDLE) bail()
            return
        }

        when (phase) {
            Phase.IDLE -> checkArming()
            Phase.ARMING -> checkFadeStart()
            Phase.FADING -> Unit // driven by its own fade coroutine
        }
    }

    private fun checkArming() {
        val act = active()
        if (!act.isPlaying || !act.playWhenReady) return
        val dur = act.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        val fadeMs = fadeDurationMs().coerceAtLeast(1_000L)
        if (dur <= fadeMs * 2L + 2_000L) return

        val nextIdx = act.nextMediaItemIndex
        if (nextIdx == C.INDEX_UNSET || nextIdx !in 0 until act.mediaItemCount) return
        // Avoid crossfading into the exact same item in Repeat One
        if (act.repeatMode == Player.REPEAT_MODE_ONE) return

        val remain = dur - act.currentPosition
        // Pre-arm 6s before the fade window so NewPipe stream resolution finishes in advance
        if (remain > fadeMs + PREARM_LEAD_MS || remain <= 400L) return

        val curId = act.currentMediaItem?.mediaId ?: return
        val items = List(act.mediaItemCount) { act.getMediaItemAt(it) }
        val st = standby()
        st.playWhenReady = false
        st.volume = 0f
        st.repeatMode = act.repeatMode
        st.shuffleModeEnabled = act.shuffleModeEnabled
        st.setMediaItems(items, nextIdx, 0L)
        st.prepare()

        armedForMediaId = curId
        armedNextIndex = nextIdx
        outgoing = act
        incoming = st
        handedOff = false
        phase = Phase.ARMING
        Log.d(TAG, "Armed standby player for index $nextIdx (remain=${remain}ms)")
    }

    private fun checkFadeStart() {
        val out = outgoing ?: return bail()
        val inc = incoming ?: return bail()
        if (out != active() || !out.isPlaying || !out.playWhenReady || out.currentMediaItem?.mediaId != armedForMediaId) {
            bail()
            return
        }
        val dur = out.duration
        if (dur == C.TIME_UNSET || dur <= 0L) {
            bail()
            return
        }
        val fadeMs = fadeDurationMs().coerceAtLeast(1_000L)
        val remain = dur - out.currentPosition
        if (remain > fadeMs + PREARM_LEAD_MS + 1_500L) {
            // User scrubbed backward
            bail()
            return
        }
        if (remain <= fadeMs && inc.playbackState != Player.STATE_IDLE) {
            beginFade(out, inc, fadeMs.coerceAtMost(remain.coerceAtLeast(300L)))
        }
    }

    private fun beginFade(out: ExoPlayer, inc: ExoPlayer, fadeMs: Long) {
        phase = Phase.FADING
        inc.volume = 0f
        inc.play()
        Log.d(TAG, "Starting dual-player crossfade (${fadeMs}ms)")

        scope.launch {
            val startAt = System.currentTimeMillis()
            while (isActive && phase == Phase.FADING) {
                val elapsed = System.currentTimeMillis() - startAt
                val t = (elapsed.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                // Equal-power cosine/sine curve
                out.volume = cos(t * PI.toFloat() / 2f)
                inc.volume = sin(t * PI.toFloat() / 2f)

                if (t >= 0.5f && !handedOff) {
                    handedOff = true
                    onHandoff(out, inc)
                }

                if (t >= 1f || out.playbackState == Player.STATE_ENDED) {
                    if (!handedOff) {
                        handedOff = true
                        onHandoff(out, inc)
                    }
                    out.pause()
                    out.stop()
                    out.clearMediaItems()
                    out.volume = 1f
                    inc.volume = 1f
                    resetFields()
                    break
                }
                delay(32L)
            }
        }
    }

    fun bail() {
        val out = outgoing
        val inc = incoming
        if (out != null && out == active()) {
            out.volume = 1f
        }
        if (inc != null && inc != active()) {
            runCatching {
                inc.pause()
                inc.stop()
                inc.clearMediaItems()
                inc.volume = 1f
            }
        }
        resetFields()
    }

    private fun resetFields() {
        phase = Phase.IDLE
        armedForMediaId = null
        armedNextIndex = C.INDEX_UNSET
        outgoing = null
        incoming = null
        handedOff = false
    }

    companion object {
        private const val TAG = "MusikuCrossfade"
        private const val PREARM_LEAD_MS = 6_000L
    }
}
