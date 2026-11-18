package app.musiku.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Convx's slider track (`ui/component/PlayerSlider.kt`), minus Automix.
 *
 * Round-capped played/unplayed lines on one axis, with the stepped tick dots sitting on top of
 * the played fill. The caps are inset by half the stroke so they land inside the node instead of
 * overshooting it, which matters because the player composites the whole control additively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 10.dp,
) {
    val valueRange = sliderState.valueRange
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            drawTrack(
                tickFractions = stepsToTickFractions(sliderState.steps),
                activeRangeStart = 0f,
                activeRangeEnd = calcFraction(
                    valueRange.start,
                    valueRange.endInclusive,
                    sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive),
                ),
                inactiveTrackColor = colors.inactiveTrackColor,
                activeTrackColor = colors.activeTrackColor,
                inactiveTickColor = colors.inactiveTickColor,
                activeTickColor = colors.activeTickColor,
                trackHeight = trackHeight,
            )
        }
    }
}

private fun DrawScope.drawTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()
    // Round caps bulge half the stroke width past each endpoint. Inset the line so the caps
    // land inside [0, size.width] instead of overshooting it.
    val capInset = trackStrokeWidth / 2f
    val sliderLeft = Offset(capInset, center.y)
    val sliderRight = Offset(size.width - capInset, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )
    val sliderValueEnd = Offset(
        sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRangeEnd,
        center.y,
    )
    val sliderValueStart = Offset(
        sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRangeStart,
        center.y,
    )
    drawLine(
        activeTrackColor,
        sliderValueStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f,
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray =
    if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)
