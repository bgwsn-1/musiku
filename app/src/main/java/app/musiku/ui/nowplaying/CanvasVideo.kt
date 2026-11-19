package app.musiku.ui.nowplaying

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.musiku.R
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.theme.LocalLowDevice
import java.io.File

/**
 * True when the cover slot is currently drawing a canvas video instead of the artwork. Full bleed
 * lays out for an artwork, and a 16:9 video would be cropped by it, so Convx drops the bleed look
 * while a video is on screen (`!videoActive`) — this is the same gate for the cover frame.
 */
@Composable
fun rememberCanvasVideoActive(track: Track): Boolean {
    val app = LocalApp.current
    val lowDevice = LocalLowDevice.current
    val enabled by app.library.canvasEnabled.collectAsState()
    return enabled && !lowDevice && app.canvas.cached(track) != null
}

/**
 * The square cover slot: Spotify Canvas when the user turned it on, has a Spotify session cookie
 * and Spotify actually has a canvas for this track; the static artwork otherwise. Loading is
 * silent — the artwork stays up until the video file exists, then crossfades into it.
 */
@Composable
fun CanvasOrArtwork(track: Track, playing: Boolean, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val lowDevice = LocalLowDevice.current
    val enabled by app.library.canvasEnabled.collectAsState()
    val active = enabled && !lowDevice

    var video by remember(track.videoId, active) {
        mutableStateOf(if (active) app.canvas.cached(track) else null)
    }
    LaunchedEffect(track.videoId, active) {
        if (!active) {
            video = null
            return@LaunchedEffect
        }
        if (video == null) video = app.canvas.video(track)
    }

    val file = video
    AnimatedContent(
        targetState = file,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
        contentKey = { it?.path ?: track.videoId },
        label = "coverCanvas",
        modifier = modifier,
    ) { current ->
        if (current != null) {
            CanvasVideo(current, playing, Modifier.fillMaxSize())
        } else {
            AnimatedContent(
                targetState = track.thumbs,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                contentKey = { it },
                label = "coverArt",
                modifier = Modifier.fillMaxSize(),
            ) { url ->
                Artwork(url, Modifier.fillMaxSize(), contentDescription = "Sampul ${track.title}")
            }
        }
    }
}

/** Muted, looping TextureView playback — no controls, cropped to fill the square art slot. */
@Composable
fun CanvasVideo(file: File, playing: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(file.path) {
        ExoPlayer.Builder(ctx).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }

    LaunchedEffect(player, playing) {
        player.playWhenReady = playing
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { c ->
            (LayoutInflater.from(c).inflate(R.layout.view_canvas_player, null) as PlayerView).apply {
                useController = false
                this.player = player
            }
        },
        modifier = modifier,
        update = { it.player = player },
    )
}
