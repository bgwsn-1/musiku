package app.musiku.ui.nowplaying

import app.musiku.model.RepeatMode
import app.musiku.model.Track

data class NowPlayingState(
    val track: Track,
    val playing: Boolean = false,
    val positionSec: Float = 0f,
    val durationSec: Float = 0f,
    val volume: Float = 0.8f,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val liked: Boolean = false,
    val lyricLine: String = "",
    val crossfade: Boolean = false,
    val cfSec: Int = 4,
    val sleepLabel: String? = null,
    val lyricsOpen: Boolean = false,
    val queueOpen: Boolean = false,
)

/** Everything the screen can ask for; the host wires these to the real player later. */
class NowPlayingActions(
    val onClose: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onVolume: (Float) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onLyrics: () -> Unit = {},
    val onQueue: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onPlayNext: () -> Unit = {},
    val onAddToQueue: () -> Unit = {},
    val onLike: () -> Unit = {},
    val onAddToPlaylist: () -> Unit = {},
    val onOpenAlbum: (() -> Unit)? = null,
    val onOpenArtist: (() -> Unit)? = null,
    val onInfo: () -> Unit = {},
    val onSleep: (Int?) -> Unit = {},
    val onCrossfade: () -> Unit = {},
    val onCfSec: (Int) -> Unit = {},
)
