package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glassEdges

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(track: Track, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.player.ui.collectAsState()
    val liked by app.library.liked.collectAsState()
    val isLiked = liked.any { it.videoId == track.videoId }
    val isCurrent = state.current?.videoId == track.videoId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Modal sheets render in their own window and cannot sample the page behind them, so they
        // get the glass fill + edges without the backdrop blur.
        containerColor = Tok.GlassFillStrong,
        contentColor = Tok.Label1,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassEdges(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(track.thumbs, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Tok.Fill1))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Tok.Label1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (track.artist != null) Text(track.artist, color = Tok.Label2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Tok.Hairline))
            fun go(f: () -> Unit) = { f(); onDismiss() }
            SheetRow(if (isCurrent && state.playing) MIcons.Pause else MIcons.Play, if (isCurrent && state.playing) "Jeda" else "Putar",
                go { if (isCurrent) app.player.toggle() else app.player.playTracks(listOf(track)) })
            SheetRow(MIcons.Next, "Putar berikutnya", go { app.player.enqueue(track, next = true) })
            SheetRow(MIcons.Queue, "Tambah ke antrian", go { app.player.enqueue(track, next = false) })
            SheetRow(if (isLiked) MIcons.Heart else MIcons.HeartOutline, if (isLiked) "Hapus dari favorit" else "Tambah ke favorit",
                go { app.player.toggleLike(track) })
            SheetRow(MIcons.Plus, "Tambah ke playlist…", go { ui.addToPlaylist = track })
            if (track.albumId != null) SheetRow(MIcons.Disc, "Lihat album", go { ui.nowPlayingOpen = false; ui.navigate(Routes.album(track.albumId)) })
            if (track.artistId != null) SheetRow(MIcons.Mic, "Lihat artis", go { ui.nowPlayingOpen = false; ui.navigate(Routes.artist(track.artistId)) })
            if (onRemove != null) SheetRow(MIcons.Trash, "Hapus dari playlist", go(onRemove), danger = true)
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).pressable { onClick() }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = if (danger) Tok.Accent else Tok.Label2, modifier = Modifier.size(24.dp))
        Text(label, color = if (danger) Tok.Accent else Tok.Label1, fontSize = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(track: Track, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val playlists by app.library.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tok.GlassFillStrong,
        contentColor = Tok.Label1,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glassEdges(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Tambah ke playlist", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(MIcons.Plus, "Playlist baru", { creating = true })
            playlists.forEach { pl ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .pressable {
                            val ok = app.library.addToPlaylist(pl.id, track)
                            app.player.say(if (ok) "Ditambahkan ke ${pl.name}" else "Sudah ada di ${pl.name}")
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Artwork(pl.tracks.firstOrNull()?.thumbs, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pl.tracks.size} lagu", color = Tok.Label2, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (creating) {
        TextPromptDialog(
            title = "Playlist baru",
            confirm = "Buat",
            onDismiss = { creating = false },
            onConfirm = { name ->
                val pl = app.library.createPlaylist(name, track)
                app.player.say("Ditambahkan ke ${pl.name}")
                creating = false
                onDismiss()
            },
        )
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    confirm: String,
    initial: String = "",
    placeholder: String = "Nama playlist",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val shape = RoundedCornerShape(26.dp)
    AlertDialog(
        onDismissRequest = onDismiss,
        // web Dialog: `glass glass-lg rounded-t-[28px] md:rounded-[26px]` — same glass fill, no blur
        // (dialogs are their own window). The scrim and edges come from the shared glass fallback.
        containerColor = Tok.GlassFillStrong,
        shape = shape,
        title = { Text(title, color = Tok.Label1, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder, color = Tok.Label3) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Tok.Label1, unfocusedTextColor = Tok.Label1,
                    focusedBorderColor = Tok.Accent, unfocusedBorderColor = Tok.Hairline, cursorColor = Tok.Accent,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm, color = Tok.Accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = Tok.Label2) } },
    )
}

@Composable
fun InfoDialog(track: Track, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tok.GlassFillStrong,
        shape = RoundedCornerShape(26.dp),
        title = { Text("Info lagu", color = Tok.Label1, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Judul" to track.title, "Artis" to track.artist, "Album" to track.album, "Durasi" to track.duration, "ID" to track.videoId)
                    .filter { !it.second.isNullOrBlank() }
                    .forEach { (k, v) ->
                        Column {
                            Text(k, color = Tok.Label3, fontSize = 12.sp)
                            Text(v!!, color = Tok.Label1, fontSize = 16.sp)
                        }
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Tok.Accent) } },
    )
}
