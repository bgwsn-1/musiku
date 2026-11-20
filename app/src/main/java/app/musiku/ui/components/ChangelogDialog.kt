package app.musiku.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.data.ReleaseNotes
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glassDialogColor

private val HeadingMarks = Regex("(?m)^#{1,6}\\s*")

/**
 * "Yang baru" after the installed version changes. The notes are the GitHub release bodies;
 * [entries] is oldest-first, so an update that skipped several versions still reads in order.
 */
@Composable
fun ChangelogDialog(
    version: String,
    entries: List<ReleaseNotes>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glassDialogColor(),
        shape = RoundedCornerShape(26.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Yang baru", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Musiku $version", color = Tok.Label2, fontSize = 13.sp)
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                entries.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entries.size > 1) {
                            Text(
                                "Versi ${entry.version}",
                                color = Tok.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            )
                        }
                        Text(
                            entry.notes.replace(HeadingMarks, "").trim(),
                            color = Tok.Label2,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
