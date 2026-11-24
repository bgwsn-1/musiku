package app.musiku.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.musiku.MainActivity
import app.musiku.MusikuApp
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Owns the real ExoPlayer(s) and dual-player ping-pong [CrossfadeEngine]. Because it is a
 * MediaSessionService the music keeps playing with the screen off and shows up on the lock screen
 * / notification shade / Bluetooth controls.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var activePlayer: ExoPlayer
    private var standbyPlayer: ExoPlayer? = null
    private var crossfade: CrossfadeEngine? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun createExoPlayer(handleAudioFocus: Boolean): ExoPlayer {
        val app = application as MusikuApp
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(app.eqProcessor))
                .build()
        }
        return ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(app.downloads.playbackDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MusikuApp
        activePlayer = createExoPlayer(handleAudioFocus = true)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, activePlayer)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Controllers only know the mediaId; the service decides what to actually stream.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val fixed = mediaItems.map {
                        it.buildUpon().setUri("$STREAM_SCHEME://track/${it.mediaId}").build()
                    }.toMutableList()
                    return Futures.immediateFuture(fixed)
                }
            })
            .build()

        crossfade = CrossfadeEngine(
            scope = serviceScope,
            active = { activePlayer },
            standby = { standbyPlayer ?: createExoPlayer(handleAudioFocus = false).also { standbyPlayer = it } },
            onHandoff = ::adoptPlayer,
            isEnabled = { app.library.crossfade.value },
            fadeDurationMs = { app.library.cfSec.value * 1000L },
        ).also { it.start() }
    }

    private fun adoptPlayer(outgoing: ExoPlayer, incoming: ExoPlayer) {
        activePlayer = incoming
        standbyPlayer = outgoing
        session?.player = incoming
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        crossfade?.stop()
        crossfade = null
        serviceScope.cancel()
        session?.run {
            player.release()
            release()
        }
        standbyPlayer?.release()
        standbyPlayer = null
        session = null
        super.onDestroy()
    }
}
