package app.musiku.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import app.musiku.model.Track

enum class Panel { Lyrics, Queue }

/** Overlays that any screen can raise: track menu, "add to playlist", player, lyrics/queue. */
@Stable
class UiState {
    var menuTrack by mutableStateOf<Track?>(null)
    var menuRemove by mutableStateOf<(() -> Unit)?>(null)

    /**
     * Where the row/button that opened the menu sits, in window coordinates. The card grows out
     * of this point and, on dismiss, shrinks back into it — Convx's press-and-pop menu.
     */
    var menuAnchor by mutableStateOf<Rect?>(null)
    var addToPlaylist by mutableStateOf<Track?>(null)
    var infoTrack by mutableStateOf<Track?>(null)
    var nowPlayingOpen by mutableStateOf(false)
    var panel by mutableStateOf<Panel?>(null)

    /** wired by AppRoot to NavController.navigate */
    var navigate: (String) -> Unit = {}

    /** wired by AppRoot: asks GitHub now and raises the prompt (or a toast) with the answer. */
    var checkUpdate: () -> Unit = {}

    fun openMenu(t: Track, onRemove: (() -> Unit)? = null, anchor: Rect? = null) {
        menuTrack = t
        menuRemove = onRemove
        menuAnchor = anchor
    }
}

val LocalUi = compositionLocalOf<UiState> { error("UiState not provided") }

object Routes {
    const val Home = "home"
    const val Search = "search"
    const val Library = "library"
    const val Settings = "settings"
    fun album(id: String) = "album/$id"
    fun artist(id: String) = "artist/$id"
    fun playlist(id: String) = "playlist/$id"
    fun genre(id: String, params: String, title: String) =
        "genre/${android.net.Uri.encode(id)}/${android.net.Uri.encode(params)}/${android.net.Uri.encode(title)}"
    fun onlinePlaylist(id: String) = "oplaylist/${android.net.Uri.encode(id)}"
}
