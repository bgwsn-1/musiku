package app.musiku.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.musiku.MusikuApp
import app.musiku.R
import app.musiku.ui.LocalApp

/**
 * A looping, muted motion-video player that outlives the view showing it: hoisted above the
 * artist's lazy list, so scrolling the hero out of composition and back does not rebuffer.
 * [active] pauses decoding (off screen, app in background) without dropping the buffer.
 */
@Stable
class ArtistVideoPlayer internal constructor(context: Context, videoUrl: String) {
    private val app = context.applicationContext as MusikuApp

    internal val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(app.http)),
            ),
        )
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                false,
            )
            // The hero is screen-wide and partly washed out; higher resolution is wasted bytes.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1080, 1080)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(videoUrl.trim()))
            prepare()
            playWhenReady = true
        }

    internal var isReady by mutableStateOf(false)
        private set

    var active: Boolean
        get() = exoPlayer.playWhenReady
        set(value) {
            exoPlayer.playWhenReady = value
        }

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isReady = true
                }
            },
        )
    }

    internal fun release() = exoPlayer.release()
}

@Composable
fun rememberArtistVideoPlayer(url: String): ArtistVideoPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(url) { ArtistVideoPlayer(context, url) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

/** Muted, looping TextureView playback cropped to fill the hero artwork slot. */
@Composable
fun ArtistVideo(player: ArtistVideoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c ->
            (LayoutInflater.from(c).inflate(R.layout.view_canvas_player, null) as PlayerView).apply {
                useController = false
                this.player = player.exoPlayer
            }
        },
        update = { it.player = player.exoPlayer },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/**
 * Resolves the artist's motion artwork URL: Apple Music's catalog first, then the legacy
 * artwork-reference service as a fallback. Returns null when the feature is off, the active network
 * is metered, or neither source has anything for this artist. Failures are silent by design: the
 * caller keeps the static hero.
 */
@Composable
fun rememberArtistVideoUrl(
    artist: String,
    song: String?,
    album: String?,
    duration: String?,
    enabled: Boolean,
): String? {
    val app = LocalApp.current
    val context = LocalContext.current
    var url by remember(artist, song, album, duration, enabled) { mutableStateOf<String?>(null) }
    LaunchedEffect(artist, song, album, duration, enabled) {
        url = null
        if (!enabled || artist.isBlank()) return@LaunchedEffect
        if (isMetered(context)) return@LaunchedEffect
        val appleUrl = app.appleMotionVideo.lookup(artist)
        url = appleUrl ?: run {
            if (song.isNullOrBlank()) null
            else app.artistVideo.lookup(song, artist, album, durationSeconds(duration))?.url
        }
    }
    return url
}

private fun isMetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return cm.isActiveNetworkMetered
}

/** "3:45" → 225; "1:02:03" → 3723. Null when the label is missing or unparseable. */
private fun durationSeconds(label: String?): Int? {
    if (label.isNullOrBlank()) return null
    val parts = label.split(':').mapNotNull { it.trim().toIntOrNull() }
    if (parts.isEmpty()) return null
    var total = 0
    for (part in parts) total = total * 60 + part
    return total.takeIf { it > 0 }
}
