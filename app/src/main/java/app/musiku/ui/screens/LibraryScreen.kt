package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.GridCard
import app.musiku.ui.components.LargeTitleList
import app.musiku.ui.components.Segmented
import app.musiku.ui.components.Tappable
import app.musiku.ui.components.TextPromptDialog
import app.musiku.ui.components.pressable
import app.musiku.ui.components.TrackRow
import app.musiku.ui.theme.Tok

private val SECTIONS = listOf("Playlist", "Artis", "Album", "Lagu")

private data class ArtistGroup(val name: String, val artistId: String?, val thumbs: String?, val count: Int)
private data class AlbumGroup(val name: String, val albumId: String?, val artist: String?, val thumbs: String?, val count: Int)

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val liked by app.library.liked.collectAsState()
    val playlists by app.library.playlists.collectAsState()
    var section by rememberSaveable { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    val artists = remember(liked) {
        liked.filter { !it.artist.isNullOrBlank() }.groupBy { it.artist!!.lowercase() }.map { (_, ts) ->
            ArtistGroup(ts.first().artist!!, ts.firstNotNullOfOrNull { it.artistId }, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }
    val albums = remember(liked) {
        liked.filter { !it.album.isNullOrBlank() }.groupBy { "${it.album!!.lowercase()}::${it.artist?.lowercase().orEmpty()}" }.map { (_, ts) ->
            AlbumGroup(ts.first().album!!, ts.firstNotNullOfOrNull { it.albumId }, ts.first().artist, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }

    // web: two-column grid with 16px gutter; cards fill the column
    val cardW = ((LocalConfiguration.current.screenWidthDp - 32 - 16) / 2).dp

    LargeTitleList(
        "Pustaka",
        header = {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    "${liked.size} lagu · ${playlists.size} playlist · tersimpan di perangkat ini",
                    color = Tok.Label2, fontSize = 15.sp, lineHeight = 19.sp,
                )
                Box(Modifier.padding(top = 20.dp)) { Segmented(SECTIONS, section, { section = it }) }
            }
        },
    ) {
        when (section) {
            0 -> {
                item {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NewPlaylistTile(cardW) { creating = true }
                        playlists.firstOrNull()?.let { pl ->
                            GridCard(pl.name, "${pl.tracks.size} lagu", pl.tracks.firstOrNull()?.thumbs, onClick = { ui.navigate(Routes.playlist(pl.id)) }, size = cardW)
                        }
                    }
                }
                playlists.drop(1).chunked(2).forEach { pair ->
                    item(key = "pl-" + pair.first().id) {
                        Row(Modifier.padding(horizontal = 16.dp).padding(top = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { pl -> GridCard(pl.name, "${pl.tracks.size} lagu", pl.tracks.firstOrNull()?.thumbs, onClick = { ui.navigate(Routes.playlist(pl.id)) }, size = cardW) }
                        }
                    }
                }
                if (playlists.isEmpty()) item {
                    Text("Belum ada playlist. Tekan “Playlist Baru” untuk membuat yang pertama.", color = Tok.Label2, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
                }
            }
            1 -> if (artists.isEmpty()) item { EmptyState("Belum ada artis", "Artis muncul di sini setelah kamu menyukai lagunya.") }
            else itemsIndexed(artists, key = { i, a -> "ar$i-${a.name}" }) { _, a ->
                Row(
                    Modifier.fillMaxWidth().pressable { a.artistId?.let { ui.navigate(Routes.artist(it)) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(a.thumbs, Modifier.size(52.dp).clip(CircleShape).background(Tok.Fill2))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.count} lagu", color = Tok.Label2, fontSize = 13.sp)
                    }
                    Icon(MIcons.ChevronRight, null, tint = Tok.Label3, modifier = Modifier.size(20.dp))
                }
            }
            2 -> if (albums.isEmpty()) item { EmptyState("Belum ada album", "Album muncul di sini setelah kamu menyukai lagu dari album tersebut.") }
            else albums.chunked(2).forEachIndexed { n, pair ->
                item(key = "alb$n") {
                    Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { a ->
                            GridCard(a.name, listOfNotNull(a.artist, "${a.count} lagu").joinToString(" · "), a.thumbs,
                                onClick = { a.albumId?.let { ui.navigate(Routes.album(it)) } ?: app.player.say("Album belum bisa dibuka") }, size = cardW)
                        }
                    }
                }
            }
            else -> if (liked.isEmpty()) item { EmptyState("Belum ada favorit", "Tekan ikon hati di lagu mana pun, nanti muncul di sini.") }
            else {
                itemsIndexed(liked, key = { i, t -> "lk${t.videoId}-$i" }) { i, t -> TrackRow(t, liked, i) }
            }
        }
    }

    if (creating) {
        TextPromptDialog("Playlist baru", "Buat", onDismiss = { creating = false }, onConfirm = {
            val pl = app.library.createPlaylist(it)
            creating = false
            ui.navigate(Routes.playlist(pl.id))
        })
    }
}

@Composable
private fun NewPlaylistTile(cardW: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Tappable(onClick, Modifier.size(width = cardW, height = cardW + 40.dp), pressScale = 0.97f, description = "Buat playlist baru") {
        Column {
            Box(
                Modifier.size(cardW).clip(RoundedCornerShape(14.dp)).background(Tok.Fill1).border(BorderStroke(1.dp, Tok.Hairline), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(MIcons.Plus, null, tint = Tok.Label2, modifier = Modifier.size(32.dp)) }
            Text("Playlist Baru", color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text("Buat koleksi lagu", color = Tok.Label2, fontSize = 13.sp)
        }
    }
}

/** Play / Shuffle pair used above any song list. */
@Composable
fun PlayShuffleRow(tracks: List<Track>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Putar", MIcons.Play, filled = true, modifier = Modifier.weight(1f)) {
            if (tracks.isNotEmpty()) app.player.playTracks(tracks, 0)
        }
        PillButton("Acak", MIcons.Shuffle, filled = false, modifier = Modifier.weight(1f)) {
            if (tracks.isNotEmpty()) {
                app.player.playTracks(tracks.shuffled(), 0)
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tappable(onClick, modifier.height(46.dp).clip(CircleShape).background(if (filled) Tok.AccentFill else Tok.Fill2), description = label) {
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
