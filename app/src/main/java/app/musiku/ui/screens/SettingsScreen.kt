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

// iOS kicker colours, dark variants — the same set Convx's settings rows tile their glyphs with.
private val SysRed = Color(0xFFFF453A)
private val SysOrange = Color(0xFFFF9F0A)
private val SysPink = Color(0xFFFF375F)
private val SysPurple = Color(0xFFBF5AF2)
private val SysBlue = Color(0xFF0A84FF)
private val SysTeal = Color(0xFF40C8E0)
private val SysGreen = Color(0xFF30D158)
private val SysGray = Color(0xFF8E8E93)
private val SysIndigo = Color(0xFF5E5CE6)

// Convx's inset-grouped metrics (IosMetrics): 16dp page inset, 26dp card radius, 30dp glyph
// tiles on a 7dp radius, 52dp minimum row, dividers inset to the text column at 58dp.
private val PageGutter = 16.dp
private val CardShape = RoundedCornerShape(26.dp)
private val TileShape = RoundedCornerShape(7.dp)
private val TileSize = 30.dp
private const val DividerInset = 58

/**
 * Convx's root Settings page: iOS inset-grouped cards on the grouped background, one coloured
 * glyph tile per row, a footnote label above each group, and the version pinned at the bottom.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val crossfade by app.library.crossfade.collectAsState()
    val cfSec by app.library.cfSec.collectAsState()
    val autoplay by app.library.autoplay.collectAsState()
    val lowDevice by app.library.lowDevice.collectAsState()
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
                }
            }

            item(key = "appearance") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Tampilan") {
                    SettingsToggleRow(
                        icon = MIcons.Settings,
                        tint = SysOrange,
                        title = "Mode perangkat rendah",
                        subtitle = "Matikan liquid glass & blur agar lebih ringan",
                        checked = lowDevice,
                        onCheckedChange = { app.library.setLowDevice(it) },
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
                        icon = MIcons.User,
                        tint = SysGreen,
                        title = "Profil GitHub",
                        subtitle = "github.com/${UpdateSource.Repo.substringBefore('/')}",
                        onClick = {
                            openInBrowser(ctx, "https://github.com/${UpdateSource.Repo.substringBefore('/')}")
                        },
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
                        "Desain, gerak dan metrik diadaptasi dari Convx (GPL-3.0) dan Flamingo milik Yos-X. " +
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

/** One inset-grouped card: an optional footnote label, then rows split by hairlines. */
@Composable
private fun SettingsGroup(label: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PageGutter)) {
        if (label != null) {
            Text(
                label,
                color = Tok.Label3,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.08).sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Tok.Surface),
            content = content,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = DividerInset.dp)
            .height(0.33.dp)
            .background(Tok.Hairline),
    )
}

/** Convx's `SettingsLinkRow`: glyph tile, title, one line of description, value, chevron. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    chevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, pressScale = 0.99f) else Modifier)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SettingsTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Tok.Label1, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, color = Tok.Label3, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(trailing, color = Tok.Label3, fontSize = 17.sp, maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        if (chevron) {
            Icon(MIcons.ChevronRight, null, tint = Tok.Label3.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SettingsTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Tok.Label1, fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, color = Tok.Label3, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SysGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SettingsTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(TileSize).clip(TileShape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
