package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.theme.Tok

/** Song row used by album / artist / playlist / search / library. Long-press = track menu. */
@Composable
fun TrackRow(
    track: Track,
    queue: List<Track>,
    index: Int,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    number: Int? = null,
    onRemove: (() -> Unit)? = null,
) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.player.ui.collectAsState()
    val isCurrent = state.current?.videoId == track.videoId
    val playing = isCurrent && state.playing
    // Where the row sits, so the menu can grow out of it (and shrink back into it).
    var rowAnchor by remember { mutableStateOf(Rect.Zero) }
    var btnAnchor by remember { mutableStateOf(Rect.Zero) }
    val openMenu: (Rect) -> Unit = { a -> ui.openMenu(track, onRemove, a.takeIf { !it.isEmpty }) }

    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tappable(
            onClick = { if (isCurrent) app.player.toggle() else app.player.playTracks(queue, index) },
            onLongClick = { openMenu(rowAnchor) },
            pressScale = 0.985f,
            modifier = Modifier.weight(1f).onGloballyPositioned { rowAnchor = it.boundsInWindow() },
            description = "${if (playing) "Jeda" else "Putar"} ${track.title}",
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (number != null) {
                    Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                        if (playing) Eq(true, Tok.Accent) else Text("$number", color = Tok.Label3, fontSize = 15.sp)
                    }
                }
                if (showArt) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1)) {
                        Artwork(track.thumbs, Modifier.size(48.dp))
                        if (isCurrent && number == null) {
                            Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                Eq(playing)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = if (isCurrent) Tok.Accent else Tok.Label1,
                        fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = listOfNotNull(track.artist, track.album).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, color = Tok.Label2, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!track.duration.isNullOrBlank()) {
                    Text(track.duration, color = Tok.Label3, fontSize = 13.sp)
                }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .onGloballyPositioned { btnAnchor = it.boundsInWindow() }
                .clip(CircleShape)
                .pressable { openMenu(btnAnchor) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MIcons.Ellipsis, contentDescription = "Aksi untuk ${track.title}", tint = Tok.Label3, modifier = Modifier.size(20.dp))
        }
    }
}
