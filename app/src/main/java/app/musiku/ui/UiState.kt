package app.musiku.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import app.musiku.model.Track

enum class Panel { Lyrics, Queue }

/** Overlays that any screen can raise: track menu, "add to playlist", player, lyrics/queue. */
@Stable
class UiState {
    var menuTrack by mutableStateOf<Track?>(null)
    var menuRemove by mutableStateOf<(() -> Unit)?>(null)
    var addToPlaylist by mutableStateOf<Track?>(null)
    var infoTrack by mutableStateOf<Track?>(null)
    var nowPlayingOpen by mutableStateOf(false)
    var panel by mutableStateOf<Panel?>(null)

    /** wired by AppRoot to NavController.navigate */
    var navigate: (String) -> Unit = {}

    fun openMenu(t: Track, onRemove: (() -> Unit)? = null) {
        menuTrack = t
        menuRemove = onRemove
    }
}

val LocalUi = compositionLocalOf<UiState> { error("UiState not provided") }

object Routes {
    const val Home = "home"
    const val Search = "search"
    const val Library = "library"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
}
