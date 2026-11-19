package app.musiku.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS-style rubber band, ported from Convx (`ui/utils/IosOverscroll.kt`).
 *
 * Convx hands its effect to the newer `LocalOverscrollFactory`, which this project can't use:
 * Musiku is pinned to foundation 1.7.5 because Haze 1.1.0 is built against it, and 1.7.5 has no
 * `overscrollEffect` parameter and no factory local. So the same effect is driven through the
 * nested-scroll API instead — same rubber-band curve, same springs, same release handling — while
 * the constants below stay identical to Convx's.
 *
 * The pull is tracked raw and damped only when drawn. Compose 1.7.5 has no per-axis effect, and one
 * connection here wraps the whole screen, so only the vertical axis bounces; horizontal rows would
 * need their own effect to move independently.
 */
@Composable
fun Modifier.iosBounce(): Modifier {
    val scope = rememberCoroutineScope()
    val band = remember(scope) { ConvxBounce(scope) }

    return this
        .onSizeChanged { band.containerHeightPx = it.height.toFloat() }
        .graphicsLayer { translationY = band.drawOffset }
        .nestedScroll(band.connection)
        .pointerInput(band) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.previousPressed } &&
                            event.changes.any { it.changedToDownIgnoreConsumed() }
                        ) {
                            band.onTouchDown()
                        } else if (event.changes.none { it.pressed } &&
                            event.changes.any { it.previousPressed }
                        ) {
                            band.onTouchUp()
                        }
                    }
                }
            } finally {
                band.onCancel()
            }
        }
}

private class ConvxBounce(private val scope: CoroutineScope) {

    /** Set from layout; the rubber band scales with it exactly as on iOS. */
    var containerHeightPx = 0f

    /** Raw overscroll distance; drawing applies [rubberBand]. */
    private val rawPull = mutableFloatStateOf(0f)

    /** Pull handed over to the return spring, added to the raw distance while it settles. */
    private val returnPull = mutableFloatStateOf(0f)

    val drawOffset: Float
        get() = rubberBand(rawPull.floatValue + returnPull.floatValue, containerHeightPx)

    private var gestureGeneration = 0L
    private var flingGeneration = 0L
    private var reboundGeneration = 0L
    private var reboundJob: Job? = null

    private fun startRebound(velocity: Float) {
        cancelRebound()
        releaseActivePull()
        val rebound = reboundGeneration
        reboundJob = scope.launch {
            try {
                if (returnPull.floatValue != 0f || velocity != 0f) {
                    val seed = bounceSeedVelocity(velocity)
                    animate(
                        initialValue = returnPull.floatValue,
                        targetValue = 0f,
                        initialVelocity = seed,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = bounceStiffnessFor(seed),
                        ),
                    ) { value, _ -> returnPull.floatValue = value }
                }
            } finally {
                if (rebound == reboundGeneration) {
                    returnPull.floatValue = 0f
                    reboundJob = null
                }
            }
        }
    }

    private fun cancelRebound() {
        reboundGeneration++
        reboundJob?.cancel()
        reboundJob = null
    }

    private fun releaseActivePull() {
        returnPull.floatValue += rawPull.floatValue
        rawPull.floatValue = 0f
    }

    private fun pauseReturnForTouch() {
        gestureGeneration++
        cancelRebound()
        // Preserve the touched row's position until the gesture becomes a drag.
        releaseActivePull()
    }

    private fun resumeReturnIfNeeded() {
        if (reboundJob == null && returnPull.floatValue != 0f) startRebound(0f)
    }

    private fun reset() {
        gestureGeneration++
        cancelRebound()
        rawPull.floatValue = 0f
        returnPull.floatValue = 0f
    }

    fun onTouchDown() = pauseReturnForTouch()

    fun onTouchUp() {
        if (rawPull.floatValue == 0f) resumeReturnIfNeeded()
    }

    fun onCancel() = reset()

    /** Reduces an existing pull and returns the consumed delta to the scrollable. */
    private fun payDown(pull: MutableFloatState, delta: Float): Float {
        val current = pull.floatValue
        if (current == 0f || delta == 0f || sign(delta) == sign(current)) return 0f
        val target = current + delta
        val settled = if (sign(target) != sign(current)) 0f else target
        pull.floatValue = settled
        return settled - current
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                gestureGeneration++
                resumeReturnIfNeeded()
            }
            // Remove existing pull before scrolling the list, using the same raw-distance curve.
            val payDown = payDown(rawPull, available.y)
            return Offset(0f, payDown)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // Stretch during drags only; fling deltas must stay with the scrollable so it can hand
            // the remaining velocity to onPostFling.
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            rawPull.floatValue += available.y
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            flingGeneration = gestureGeneration
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // A cancelled fling can finish after a newer gesture has started.
            if (flingGeneration != gestureGeneration) return Velocity.Zero
            if (rawPull.floatValue == 0f && returnPull.floatValue == 0f && available.y == 0f) {
                return Velocity.Zero
            }
            // A fling that reached the edge has no pull, only leftover velocity: the return spring
            // still animates from zero, which is Convx's bounce when content is flung into an edge.
            startRebound(available.y)
            return Velocity.Zero
        }
    }
}

/**
 * UIKit's rubber band constant, and Convx's curve: the band follows at 0.55 of the finger's travel
 * at first and gets stiffer as the pull grows, approaching container / 0.55 without passing it.
 */
internal fun rubberBand(rawDistance: Float, containerPx: Float): Float {
    val dim = if (containerPx > 0f) containerPx else FallbackContainerPx
    val d = abs(rawDistance)
    val banded = (1f - 1f / (d * RubberBandConstant / dim + 1f)) * dim / RubberBandConstant
    return banded * sign(rawDistance)
}

/** Clamps the leftover fling velocity that seeds the return spring. */
internal fun bounceSeedVelocity(leftoverVelocity: Float): Float =
    (leftoverVelocity * FlingBounceScale).coerceIn(-MaxBounceVelocity, MaxBounceVelocity)

/** Convx's return springs: 247 while a drag is released, 130 for a fast fling into the edge. */
internal fun bounceStiffnessFor(seedVelocity: Float): Float =
    if (abs(seedVelocity) > HighVelocityThreshold) {
        HighVelocityBounceStiffness
    } else {
        BounceSpringStiffness
    }

private const val RubberBandConstant = 0.55f
private const val FallbackContainerPx = 2000f
private const val FlingBounceScale = 1f
private const val MaxBounceVelocity = 10_000f
private const val BounceSpringStiffness = 247f
private const val HighVelocityThreshold = 5000f
private const val HighVelocityBounceStiffness = 130f
