package app.musiku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.GridCard
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.SectionTitle
import app.musiku.ui.components.Shelf
import app.musiku.ui.components.TextPromptDialog
import app.musiku.ui.components.pressable
import app.musiku.ui.components.TrackRow
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.Tok

/** Art-tinted backdrop that melts into the window colour, shared by album / artist / playlist. */
@Composable
private fun Backdrop(img: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(440.dp)) {
        Box(Modifier.fillMaxSize().blur(64.dp).alpha(0.5f)) {
            Artwork(img, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Transparent, 1f to Tok.Window)))
    }
}

@Composable
private fun MediaHeader(
    art: String?,
    kind: String,
    title: String,
    meta: String?,
    round: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Backdrop(art)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val shape = if (round) CircleShape else RoundedCornerShape(20.dp)
            Artwork(art, Modifier.size(208.dp).shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(Tok.Fill1))
            Text(kind.uppercase(), color = Tok.Label3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.84.sp, modifier = Modifier.padding(top = 24.dp))
            Text(title, color = Tok.Label1, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.73).sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (!meta.isNullOrBlank()) Text(meta, color = Tok.Label2, fontSize = 15.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.padding(top = 20.dp, bottom = 8.dp)) { actions() }
        }
    }
}

@Composable
private fun DetailFrame(onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        BackButton(onBack)
    }
}

@Composable
fun AlbumScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by rememberLoad(id to attempt) { app.yt.album(id) }
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                MusikuLoading()
            }
            is Load.Err -> EmptyState("Album tidak ditemukan", "Mungkin sudah dihapus atau koneksi bermasalah.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val a = l.value
                LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
                    item {
                        MediaHeader(
                            a.thumbs, "Album", a.title,
                            listOfNotNull(a.artist, a.year, if (a.tracks.isNotEmpty()) "${a.tracks.size} lagu" else null, a.durationLabel).joinToString(" · "),
                        ) {
                            PlayShuffleRow(a.tracks)
                        }
                    }
                    itemsIndexed(a.tracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                        TrackRow(t.copy(album = t.album ?: a.title, albumId = a.browseId), a.tracks, i, number = i + 1, showArt = false)
                    }
                }
            }
        }
    }
}


@Composable
fun ArtistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val load by rememberLoad(id) { app.yt.artist(id) }
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                MusikuLoading()
            }
            is Load.Err -> EmptyState("Artis tidak ditemukan", "Mungkin sudah dihapus atau koneksi bermasalah.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val a = l.value
                LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
                    item {
                        MediaHeader(
                            a.avatar ?: a.banner, "Artis", a.name,
                            listOfNotNull(a.monthlyListeners?.let { "$it pendengar bulanan" }, a.subscribers?.let { "$it pelanggan" }).joinToString(" · "),
                            round = true,
                        ) { PlayShuffleRow(a.topSongs) }
                    }
                    if (a.topSongs.isNotEmpty()) {
                        item { SectionTitle("Populer", Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 4.dp)) }
                        itemsIndexed(a.topSongs, key = { i, t -> "${t.videoId}-$i" }) { i, t -> TrackRow(t, a.topSongs, i) }
                    }
                    if (a.albums.isNotEmpty()) item {
                        Column(Modifier.padding(top = 32.dp)) {
                            Shelf("Album") { itemsIndexed(a.albums, key = { i, x -> "a$i-${x.browseId}" }) { _, x -> GridCard(x.title, x.year, x.thumbs, { ui.navigate(Routes.album(x.browseId)) }) } }
                        }
                    }
                    if (a.singles.isNotEmpty()) item {
                        Shelf("Single & EP") { itemsIndexed(a.singles, key = { i, x -> "s$i-${x.browseId}" }) { _, x -> GridCard(x.title, x.year, x.thumbs, { ui.navigate(Routes.album(x.browseId)) }) } }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val playlists by app.library.playlists.collectAsState()
    val pl = playlists.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DetailFrame(onBack) {
        if (pl == null) {
            EmptyState("Playlist tidak ditemukan", "Mungkin sudah dihapus.", Modifier.statusBarsPadding().padding(top = 80.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
                item {
                    MediaHeader(pl.tracks.firstOrNull()?.thumbs, "Playlist", pl.name, "${pl.tracks.size} lagu") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PlayShuffleRow(pl.tracks)
                            Row2(
                                onRename = { renaming = true },
                                onDelete = { confirmDelete = true },
                            )
                        }
                    }
                }
                if (pl.tracks.isEmpty()) item { EmptyState("Playlist masih kosong", "Buka menu ⋯ di lagu mana pun lalu pilih “Tambah ke playlist…”.") }
                itemsIndexed(pl.tracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                    TrackRow(t, pl.tracks, i, onRemove = { app.library.removeFromPlaylist(pl.id, i) })
                }
            }
            if (renaming) TextPromptDialog("Ganti nama", "Simpan", initial = pl.name, onDismiss = { renaming = false }, onConfirm = {
                app.library.renamePlaylist(pl.id, it); renaming = false
            })
            if (confirmDelete) androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Tok.GlassFillStrong,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                title = { Text("Hapus playlist?", color = Tok.Label1, fontWeight = FontWeight.Bold) },
                text = { Text("“${pl.name}” akan dihapus dari perangkat ini.", color = Tok.Label2) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { app.library.deletePlaylist(pl.id); confirmDelete = false; onBack() }) {
                        Text("Hapus", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Batal", color = Tok.Label2) } },
            )
        }
    }
}

@Composable
private fun Row2(onRename: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text("Ganti nama", color = Tok.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).androidxClickable(onRename).padding(8.dp))
        Text("Hapus", color = Tok.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(CircleShape).androidxClickable(onDelete).padding(8.dp))
    }
}

@Composable
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = this.then(Modifier.pressable { onClick() })

@Composable
fun OnlinePlaylistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val load by rememberLoad(id) { app.yt.playlist(id) }
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { MusikuLoading() }
            is Load.Err -> EmptyState("Playlist tidak bisa dimuat", "Cek koneksi internet lalu coba lagi.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val p = l.value
                LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
                    item {
                        MediaHeader(
                            p.thumbs, "Playlist", p.title,
                            listOfNotNull(p.subtitle, if (p.tracks.isNotEmpty()) "${p.tracks.size} lagu" else null).joinToString(" · "),
                        ) {
                            PlayShuffleRow(p.tracks)
                        }
                    }
                    itemsIndexed(p.tracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                        TrackRow(t, p.tracks, i, number = i + 1, showArt = false)
                    }
                }
            }
        }
    }
}
