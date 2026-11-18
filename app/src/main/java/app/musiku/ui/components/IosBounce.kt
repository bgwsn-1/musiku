package app.musiku.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

/**
 * iOS-style rubber band for a scrollable that has already hit its edge.
 *
 * Compose 1.7's LazyColumn has no `overscrollEffect` parameter, so the leftover drag is caught
 * by a nested-scroll connection instead: the list consumes everything it can, we take the
 * remainder, damp it as the pull grows, and spring it back on release. Because the leftover is
 * consumed, the platform's stretch/glow never fires and the two effects can't fight.
 *
 * The offset is applied with [graphicsLayer], so a drag only invalidates the draw phase.
 */
@Composable
fun Modifier.iosBounce(): Modifier {
    val density = LocalDensity.current
    val limit = with(density) { IosBounceLimit.toPx() }
    val offset = remember { mutableFloatStateOf(0f) }

    val connection = remember(limit) {
        object : NestedScrollConnection {
            /** True when a drag is heading back towards the edge the band is stretched from. */
            fun relaxing(available: Float): Boolean {
                val current = offset.floatValue
                return (current > 0f && available < 0f) || (current < 0f && available > 0f)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (offset.floatValue == 0f || !relaxing(available.y)) return Offset.Zero
                val next = offset.floatValue + available.y
                offset.floatValue = if (next * offset.floatValue <= 0f) 0f else next
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y == 0f) return Offset.Zero
                // Resistance grows with the pull, so the first few dp feel loose and the end of
                // the band gets stiff — iOS' `rubberBandDistance`.
                val stretch = (abs(offset.floatValue) / limit).coerceIn(0f, 1f)
                val damped = available.y * (1f - stretch).coerceIn(0.12f, 1f)
                offset.floatValue = (offset.floatValue + damped).coerceIn(-limit, limit)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.floatValue == 0f) return Velocity.Zero
                animate(
                    initialValue = offset.floatValue,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ) { value, _ -> offset.floatValue = value }
                return available
            }
        }
    }

    return this
        .graphicsLayer { translationY = offset.floatValue }
        .nestedScroll(connection)
}

/** How far the band can stretch before it stops following the finger. */
private val IosBounceLimit = 140.dp
