package app.musiku.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.theme.Tok

/**
 * iOS filter capsules: a plain system fill at rest, the accent fill with white text once
 * picked, always fully rounded — the shape the reference Home uses for its mood filters.
 */
private val ChipFill = Color(0x51787880) // iOS secondarySystemFill, dark variant.

/**
 * Horizontal row of single-choice filters. The row bleeds off the right edge and starts on
 * [gutter] so the first capsule lines up with the page title.
 */
@Composable
fun <T> ChipsRow(
    chips: List<Pair<T, String>>,
    currentValue: T,
    onValueUpdate: (T) -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = 20.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(gutter))
        chips.forEach { (value, label) ->
            Chip(
                label = label,
                selected = currentValue == value,
                onClick = { onValueUpdate(value) },
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Tok.Accent else ChipFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.24).sp,
            maxLines = 1,
        )
    }
}
