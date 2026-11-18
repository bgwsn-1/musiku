package app.musiku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.data.SpotifyImportState
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.theme.Tok

/**
 * Paste-a-link import sheet. One dialog for the whole flow: link → progress → result, so the
 * share-sheet path can open it already working without an extra screen.
 */
@Composable
fun SpotifyImportDialog(open: Boolean, onClose: () -> Unit) {
    if (!open) return
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.importer.state.collectAsState()
    var link by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    fun close() {
        focus.clearFocus()
        app.importer.reset()
        link = ""
        onClose()
    }

    fun submit() {
        focus.clearFocus()
        if (link.isNotBlank()) app.importer.import(link)
    }

    AlertDialog(
        onDismissRequest = { close() },
        containerColor = Tok.GlassFillStrong,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                when (state) {
                    is SpotifyImportState.Done -> (state as SpotifyImportState.Done).name
                    else -> "Import dari Spotify"
                },
                color = Tok.Label1, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            when (val s = state) {
                SpotifyImportState.Idle -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tempel link playlist publik. Musiku mengambil daftar lagunya lalu mencocokkannya " +
                            "ke YouTube Music — tanpa login Spotify.",
                        color = Tok.Label2, fontSize = 14.sp,
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        placeholder = { Text("open.spotify.com/playlist/…", color = Tok.Label3, fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Tok.Label1, unfocusedTextColor = Tok.Label1,
                            focusedBorderColor = Tok.Accent, unfocusedBorderColor = Tok.Hairline,
                            cursorColor = Tok.Accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Maksimal 100 lagu per playlist. Hanya playlist publik.", color = Tok.Label3, fontSize = 12.sp)
                }

                is SpotifyImportState.Working -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        s.name ?: "Membaca playlist…",
                        color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.done.toFloat() / s.total },
                            modifier = Modifier.fillMaxWidth(),
                            color = Tok.Accent,
                            trackColor = Tok.Fill2,
                        )
                        Text("Mencocokkan ${s.done}/${s.total} lagu ke YouTube Music…", color = Tok.Label2, fontSize = 13.sp)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Tok.Accent, trackColor = Tok.Fill2,
                        )
                        Text("Mengambil daftar lagu…", color = Tok.Label2, fontSize = 13.sp)
                    }
                    Text(
                        if (s.syncing) "Playlist lama akan diperbarui setelah selesai."
                        else "Playlist muncul di Pustaka setelah selesai.",
                        color = Tok.Label3, fontSize = 12.sp,
                    )
                }

                is SpotifyImportState.Done -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${s.matched} dari ${s.total} lagu ketemu di YouTube Music.",
                        color = Tok.Label1, fontSize = 15.sp,
                    )
                    Text(
                        if (s.syncing) "Playlist sudah diperbarui." else "Playlist sudah masuk ke Pustaka.",
                        color = Tok.Label2, fontSize = 13.sp,
                    )
                    if (s.missing.isNotEmpty()) {
                        Text(
                            "Belum ketemu (${s.missing.size}):",
                            color = Tok.Label2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Column(
                            Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            s.missing.forEach { t ->
                                Text("• $t", color = Tok.Label3, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }

                is SpotifyImportState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import gagal", color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.message, color = Tok.Label2, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                SpotifyImportState.Idle -> TextButton(onClick = { submit() }, enabled = link.isNotBlank()) {
                    Text("Import", color = if (link.isNotBlank()) Tok.Accent else Tok.Label3, fontWeight = FontWeight.SemiBold)
                }
                is SpotifyImportState.Working -> TextButton(onClick = { close() }) {
                    Text("Batal", color = Tok.Label2)
                }
                is SpotifyImportState.Done -> TextButton(onClick = {
                    app.importer.reset()
                    onClose()
                    ui.navigate(Routes.playlist(s.playlistId))
                }) {
                    Text("Buka playlist", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                }
                is SpotifyImportState.Failed -> TextButton(onClick = { app.importer.reset() }) {
                    Text("Coba lagi", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            when (state) {
                SpotifyImportState.Idle, is SpotifyImportState.Failed -> TextButton(onClick = { close() }) {
                    Text("Tutup", color = Tok.Label2)
                }
                is SpotifyImportState.Working -> {}
                is SpotifyImportState.Done -> TextButton(onClick = { close() }) {
                    Text("Tutup", color = Tok.Label2)
                }
            }
        },
    )
}
