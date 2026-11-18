package app.musiku.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thick pill-shaped slider used for both the progress bar (12 dp, grows while
 * dragging, commits on release) and the volume bar (6 dp, live).
 */
@Composable
fun PillSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 12.dp,
    growWhenDragging: Boolean = true,
    commitOnRelease: Boolean = false,
    enabled: Boolean = true,
    trackColor: Color = Color.White.copy(alpha = 0.40f),
    fillColor: Color = Color.White,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = (drag ?: fraction).coerceIn(0f, 1f)
    val h by animateDpAsState(
        targetValue = if (drag != null && growWhenDragging) barHeight + 3.dp else barHeight,
        label = "pillHeight",
    )
    val latest by rememberUpdatedState(onChange)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> latest((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled, commitOnRelease) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        val f = drag
                        if (f != null && commitOnRelease) latest(f)
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ ->
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        drag = f
                        if (!commitOnRelease) latest(f)
                    },
                )
            },
    ) {
        val barH = h.toPx()
        val top = (size.height - barH) / 2f
        val radius = CornerRadius(barH / 2f, barH / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, barH),
            cornerRadius = radius,
        )
        if (shown > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, top),
                size = Size(size.width * shown, barH),
                cornerRadius = radius,
            )
        }
    }
}
