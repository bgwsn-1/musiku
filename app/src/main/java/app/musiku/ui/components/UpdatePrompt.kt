package app.musiku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.data.UpdateDownload
import app.musiku.data.UpdateInfo
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glass
import dev.chrisbanes.haze.HazeState

/**
 * Convx's floating updater: a glass card that drops in at the top, version and release notes,
 * and two choices — download, or not now. It is not a dialog, so it never blocks the page.
 */
@Composable
fun UpdatePrompt(
    info: UpdateInfo,
    current: String,
    haze: HazeState,
    download: UpdateDownload,
    onDownload: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .glass(haze, shape, strong = true, shadow = 24.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Versi ${info.version} tersedia",
                    color = Tok.Label1,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    "Kamu masih di $current",
                    color = Tok.Label2,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).pressable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MIcons.Close, "Nanti saja", tint = Tok.Label2, modifier = Modifier.size(18.dp))
            }
        }

        if (!info.notes.isNullOrBlank()) {
            Text(
                info.notes.orEmpty(),
                color = Tok.Label2,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pressable { onDetails() },
            )
        }

        // In-app updater: download runs here, then the system installer is opened. Android asks
        // once per app for "install unknown apps"; until it is granted we show the way in.
        if (download.running) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Tok.Fill1),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(download.progress ?: 0.15f)
                        .fillMaxHeight()
                        .background(Tok.Accent),
                )
            }
        }
        if (download.needsPermission) {
            Text(
                "Izinkan Musiku memasang aplikasi di pengaturan, lalu kembali dan tekan Pasang.",
                color = Tok.Label2,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        download.error?.let { message ->
            Text(
                "Gagal mengunduh: $message",
                color = Tok.Accent,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (download.running) Tok.Fill1 else Tok.AccentFill)
                    .pressable { if (!download.running) onDownload() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        download.running -> download.progress
                            ?.let { "Mengunduh ${(it * 100).toInt()}%" }
                            ?: "Mengunduh…"
                        download.file != null -> "Pasang"
                        download.error != null -> "Coba lagi"
                        else -> "Unduh"
                    },
                    color = if (download.running) Tok.Label2 else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(Tok.Fill1)
                    .pressable { onDismiss() }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nanti", color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
