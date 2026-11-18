package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Convx's `AccountAvatar` and `TopBarAccountButton` in one: a 28dp avatar inside a 36dp
 * tap target, filled with the same top-lit periwinkle gradient and a white person glyph for
 * a signed-out account. Tanpa akun, ini juga pintu masuk Pengaturan.
 */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val avatar = 28.dp
    val avatarPx = with(LocalDensity.current) { avatar.toPx() }
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .pressable(description = "Pengaturan", pressScale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(avatar)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFAABEDB), Color(0xFF7B80AE)),
                        center = Offset(avatarPx / 2f, 0f),
                        radius = avatarPx,
                    ),
                )
                .border(0.33.dp, Color.Black.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MIcons.UserFill, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
