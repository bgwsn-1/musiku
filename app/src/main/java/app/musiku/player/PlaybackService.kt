package app.musiku.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.musiku.MainActivity
import app.musiku.MusikuApp
import app.musiku.data.USER_AGENT
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException

/**
 * Owns the real ExoPlayer. Because it is a MediaSessionService the music keeps playing with
 * the screen off and shows up on the lock screen / notification shade / Bluetooth controls.
 * Queue items carry `musiku://track/<videoId>`; the URL is resolved lazily when ExoPlayer
 * actually opens the item, so a long queue never resolves hundreds of streams up front.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MusikuApp

        val http = DefaultHttpDataSource.Factory()
            // Same UA the extractor uses when it resolves the stream; googlevideo URLs are
            // minted for that client, so keep both sides identical.
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec: DataSpec ->
            if (spec.uri.scheme == STREAM_SCHEME) {
                val id = spec.uri.lastPathSegment ?: throw IOException("bad track uri")
                val url = try {
                    app.streams.resolveBlocking(id)
                } catch (e: Exception) {
                    throw IOException("resolve failed: ${e.message}", e)
                }
                spec.withUri(Uri.parse(url))
            } else {
                spec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
