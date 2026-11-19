package app.musiku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok

// iOS kicker colours, dark variants — the same set Convx's settings rows tile their glyphs with.
internal val SysRed = Color(0xFFFF453A)
internal val SysOrange = Color(0xFFFF9F0A)
internal val SysPink = Color(0xFFFF375F)
internal val SysPurple = Color(0xFFBF5AF2)
internal val SysBlue = Color(0xFF0A84FF)
internal val SysTeal = Color(0xFF40C8E0)
internal val SysGreen = Color(0xFF30D158)
internal val SysGray = Color(0xFF8E8E93)
internal val SysIndigo = Color(0xFF5E5CE6)

// Convx's inset-grouped metrics (IosMetrics): 16dp page inset, 26dp card radius, 30dp glyph
// tiles on a 7dp radius, 52dp minimum row, dividers inset to the text column at 58dp.
internal val PageGutter = 16.dp
internal val CardShape = RoundedCornerShape(26.dp)
internal val TileShape = RoundedCornerShape(7.dp)
internal val TileSize = 30.dp
internal const val DividerInset = 58

/** One inset-grouped card: an optional footnote label, then rows split by hairlines. */
@Composable
internal fun SettingsGroup(label: String?, content: @Composable ColumnScope.() -> Unit) {
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
internal fun SettingsDivider() {
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
internal fun SettingsRow(
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
internal fun SettingsToggleRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SettingsTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Tok.Label1 else Tok.Label3,
                fontSize = 17.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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
            enabled = enabled,
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

/** A selectable row that shows its current value in the secondary colour. */
@Composable
internal fun SettingsChoiceRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressScale = 0.99f)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SettingsTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = Tok.Label1,
            fontSize = 17.sp, lineHeight = 22.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = Tok.Label3, fontSize = 15.sp, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(MIcons.ChevronRight, null, tint = Tok.Label3.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

@Composable
internal fun SettingsTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(TileSize).clip(TileShape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
