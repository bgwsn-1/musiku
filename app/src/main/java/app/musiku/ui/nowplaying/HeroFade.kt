package app.musiku.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

/**
 * Convx's `heroFadeOutStops` (`ui/component/ArtistHero.kt`): black up to [start], then a smoothstep
 * ramp to black at [end]. Used as a `DstIn` mask, so black means "keep" and transparent means "erase".
 *
 * The ramp is sampled in [steps] increments because a two-stop gradient leaves a visible seam on a
 * full-bleed cover.
 */
internal fun heroFadeOutStops(start: Float = 0.78f, end: Float = 1f, steps: Int = 24): List<Pair<Float, Color>> {
    require(steps > 0 && start >= 0f && end <= 1f && start < end) {
        "heroFadeOutStops needs 0 <= start < end <= 1 and steps > 0"
    }
    val stops = ArrayList<Pair<Float, Color>>(steps + 2)
    stops += 0f to Color.Black
    stops += start to Color.Black
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        // smoothstep: eases both ends, so the cover dissolves without a bright lip.
        val eased = t * t * (3f - 2f * t)
        stops += (start + (end - start) * t) to Color.Black.copy(alpha = 1f - eased)
    }
    return stops
}

/** Convx's `heroFadeOutBrush` default: portrait covers fade over their last fifth. */
@Composable
fun rememberHeroFadeBrush(wide: Boolean): Brush = remember(wide) {
    if (wide) {
        Brush.horizontalGradient(*heroFadeOutStops(0.6f, 1f).toTypedArray())
    } else {
        Brush.verticalGradient(*heroFadeOutStops().toTypedArray())
    }
}

/**
 * Erases the bottom of a full-bleed cover into the field behind it. `Offscreen` is required:
 * without a separate layer `DstIn` has nothing isolated to mask and the whole screen is affected.
 */
fun Modifier.heroFootFade(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }

/**
 * The hero cover's height and the overlap it hands back to the title row, from Convx
 * `Player_v2.kt`: the cover is 524/393 of the width, but its laid-out box is 33/393 shorter, so the
 * title row docks under the faded foot instead of leaving a visible gap.
 */
internal fun heroOverlapPx(widthPx: Int): Int = (widthPx * 33f / 393f).roundToInt()
