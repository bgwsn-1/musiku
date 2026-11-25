package app.musiku

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.PlaybackParameters
import app.musiku.playback.audio.pass.PassBlocker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Bridge between Mochy's LocalPlayerConnection usage in AudioLab and Musiku's PlayerController. */
class PlayerConnectionShim(private val app: MusikuApp) {
    val service = ServiceBridge(app)
    val isPlaying: StateFlow<Boolean> = MutableStateFlow(app.player.ui.value.playing)
    val player = PlayerBridge(app)

    class ServiceBridge(private val app: MusikuApp) {
        val outputs = app.audioOutputs
        val audioOutputs = app.audioOutputs
        val audioPass = app.audioPass
        val passBlockers: StateFlow<Set<PassBlocker>> = MutableStateFlow(emptySet())
        val canRestorePassBlockers: StateFlow<Boolean> = MutableStateFlow(false)
        val playerVolume: StateFlow<Float> = MutableStateFlow(app.player.ui.value.volume)
        val audioSessionId: Int get() = 0
        val player = PlayerBridge(app)
        fun clearPassBlockers() = Unit
        fun restorePassBlockers() = Unit
    }

    class PlayerBridge(private val app: MusikuApp) {
        var volume: Float
            get() = app.player.ui.value.volume
            set(value) {
                app.player.setVolume(value)
            }
        var playbackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
            set(value) {
                field = value
                app.player.setPlaybackParameters(value.speed, value.pitch)
            }
    }
}

val LocalPlayerConnection = compositionLocalOf<PlayerConnectionShim?> { null }
val LocalPlayerAwareWindowInsets = compositionLocalOf { WindowInsets(0, 0, 0, 0) }
val LocalAppContentObscured = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }
