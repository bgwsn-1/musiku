package app.musiku.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
 *
 * [metalFill] swaps the flat fill for the liquid-metal surface, clipped to the filled portion.
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
    metalFill: Boolean = false,
) {
    val liquid = if (metalFill) rememberLiquidMetal(speed = 0.5f, scale = 24f, masked = false, lift = 0.25f) else null
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
            .pointerInput(enabled, commitOnRelease) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                        dragged = true
                        val f = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        drag = f
                        if (!commitOnRelease) latest(f)
                    }
                    if (dragged && slop != null) {
                        val completed = horizontalDrag(slop.id) { change ->
                            change.consume()
                            val f = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            drag = f
                            if (!commitOnRelease) latest(f)
                        }
                        if (completed) {
                            val f = drag
                            if (f != null && commitOnRelease) latest(f)
                        }
                        drag = null
                    } else if (!dragged) {
                        val lastChange = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (lastChange != null && !lastChange.isConsumed && !lastChange.pressed) {
                            lastChange.consume()
                            latest((lastChange.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                        }
                    }
                }
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
            if (liquid != null) {
                liquid.paintRounded(this, Offset(0f, top), Size(size.width * shown, barH), radius)
            } else {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width * shown, barH),
                    cornerRadius = radius,
                )
            }
        }
    }
}
