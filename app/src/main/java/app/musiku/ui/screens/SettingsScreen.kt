package app.musiku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.data.Library
import app.musiku.data.UpdateSource
import app.musiku.data.openInBrowser
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok

/**
 * Convx's root Settings page: iOS inset-grouped cards on the grouped background, one coloured
 * glyph tile per row, a footnote label above each group, and the version pinned at the bottom.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPlayerDesign: () -> Unit = {},
    onEqualizer: () -> Unit = {},
) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val crossfade by app.library.crossfade.collectAsState()
    val cfSec by app.library.cfSec.collectAsState()
    val autoplay by app.library.autoplay.collectAsState()
    val historyEnabled by app.library.historyEnabled.collectAsState()
    val lowDevice by app.library.lowDevice.collectAsState()
    val look by app.library.playerLook.collectAsState()
    val canvasEnabled by app.library.canvasEnabled.collectAsState()
    val eqEnabled by app.library.eqEnabled.collectAsState()
    val eqPreset by app.library.eqPreset.collectAsState()
    val artistVideoEnabled by app.library.artistVideoEnabled.collectAsState()
    val canvasStatus by app.canvas.status.collectAsState()
    var license by remember { mutableStateOf(false) }

    val versionName = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "title") {
                Text(
                    "Pengaturan",
                    color = Tok.Label1,
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 14.dp),
                )
            }

            item(key = "playback") {
                SettingsGroup("Pemutaran") {
                    SettingsToggleRow(
                        icon = MIcons.Repeat,
                        tint = SysOrange,
                        title = "Lanjut otomatis",
                        subtitle = "Setelah antrean habis, lanjut ke lagu yang mirip",
                        checked = autoplay,
                        onCheckedChange = { app.library.setAutoplay(it) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = MIcons.Crossfade,
                        tint = SysPink,
                        title = "Crossfade",
                        subtitle = "Lagu berikutnya masuk dengan fade, bukan potong",
                        checked = crossfade,
                        onCheckedChange = { app.library.setCrossfade(it) },
                    )
                    if (crossfade) {
                        SettingsDivider()
                        SettingsRow(
                            icon = MIcons.Volume,
                            tint = SysPurple,
                            title = "Durasi crossfade",
                            subtitle = "Ketuk untuk ganti",
                            trailing = "$cfSec detik",
                            chevron = false,
                            onClick = { app.library.setCfSec(nextCfSec(cfSec)) },
                        )
                    }
                    SettingsDivider()
                    SettingsChoiceRow(
                        icon = MIcons.Crossfade,
                        tint = SysBlue,
                        title = "Equalizer",
                        value = if (eqEnabled) eqPreset else "Mati",
                        onClick = onEqualizer,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = MIcons.Disc,
                        tint = SysPink,
                        title = "Sampul animasi",
                        subtitle = if (lowDevice) {
                            "Mati selama mode perangkat rendah aktif"
                        } else {
                            "Putar video Canvas Spotify di layar pemutar"
                        },
                        checked = canvasEnabled && !lowDevice,
                        onCheckedChange = { app.library.setCanvasEnabled(it) },
                        enabled = !lowDevice,
                    )
                }
            }

            item(key = "appearance") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Tampilan") {
                    SettingsChoiceRow(
                        icon = MIcons.Disc,
                        tint = SysPink,
                        title = "Desain pemutar",
                        value = look.label,
                        onClick = onPlayerDesign,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = MIcons.Settings,
                        tint = SysOrange,
                        title = "Mode perangkat rendah",
                        subtitle = "Matikan liquid glass & blur agar lebih ringan",
                        checked = lowDevice,
                        onCheckedChange = { app.library.setLowDevice(it) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = MIcons.Video,
                        tint = SysIndigo,
                        title = "Video artis",
                        subtitle = "Artwork bergerak di header artis. Dilewati di data seluler.",
                        checked = artistVideoEnabled && !lowDevice,
                        onCheckedChange = { app.library.setArtistVideoEnabled(it) },
                        enabled = !lowDevice,
                    )
                }
            }

            item(key = "history") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Riwayat") {
                    SettingsToggleRow(
                        icon = MIcons.List,
                        tint = SysTeal,
                        title = "Simpan riwayat",
                        subtitle = "Lagu dicatat setelah 30 detik atau setengah durasi",
                        checked = historyEnabled,
                        onCheckedChange = { app.library.setHistoryEnabled(it) },
                    )
                }
            }

            item(key = "about") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Tentang") {
                    SettingsRow(
                        icon = MIcons.Info,
                        tint = SysGray,
                        title = "Versi",
                        trailing = versionName.ifBlank { "—" },
                        chevron = false,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = MIcons.Download,
                        tint = SysBlue,
                        title = "Cek pembaruan",
                        subtitle = "Tanya GitHub Releases sekarang",
                        onClick = { ui.checkUpdate() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = MIcons.Link,
                        tint = SysTeal,
                        title = "Repositori",
                        subtitle = "github.com/${UpdateSource.Repo}",
                        onClick = { openInBrowser(ctx, "https://github.com/${UpdateSource.Repo}") },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = MIcons.Music,
                        tint = SysIndigo,
                        title = "Lisensi",
                        onClick = { license = true },
                    )
                }
            }

            item(key = "footer") {
                Text(
                    "Musiku $versionName — favorit, playlist dan riwayat tersimpan di perangkat ini.",
                    color = Tok.Label3,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                )
            }
        }
        BackButton(onBack)
    }

    if (license) {
        AlertDialog(
            onDismissRequest = { license = false },
            containerColor = Tok.Surface,
            shape = RoundedCornerShape(26.dp),
            title = { Text("Lisensi", color = Tok.Label1, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Musiku — GPL-3.0.", color = Tok.Label2, fontSize = 14.sp, lineHeight = 19.sp)
                    Text(
                            "(GPL-3.0) dan Flamingo milik Yos-X. " +
                            "Metadata dari YouTube Music, stream lewat NewPipeExtractor.",
                        color = Tok.Label2, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { license = false }) {
                    Text("Tutup", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

private fun nextCfSec(cur: Int): Int {
    val presets = Library.CF_PRESETS
    val index = presets.indexOf(cur).let { if (it >= 0) it else 0 }
    return presets[(index + 1) % presets.size]
}
