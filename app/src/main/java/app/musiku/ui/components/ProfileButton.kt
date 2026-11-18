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
import androidx.compose.ui.unit.dp
import app.musiku.ui.theme.Tok

/**
 * Convx's `TopBarAccountButton`: a 28dp avatar inside a 36dp tap target, ringed with a
 * hairline so it never dissolves into the glass behind it. Signed out it shows the person
 * glyph on the filled circle, which is also the way into Settings.
 */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .pressable(description = "Pengaturan", pressScale = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Tok.Fill2)
                .border(0.5.dp, Tok.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MIcons.User, null, tint = Tok.Label2, modifier = Modifier.size(18.dp))
        }
    }
}
