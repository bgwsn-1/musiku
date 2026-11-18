package app.musiku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * iOS-style rubber band for a scrollable that has already hit its edge.
 *
 * Compose 1.7's LazyColumn has no `overscrollEffect` parameter, so the leftover drag is caught by
 * a nested-scroll connection instead: the list consumes everything it can, [BounceBand] takes the
 * remainder, damps it as the pull grows, and springs it back on release. Because the remainder is
 * consumed, the platform stretch never fires and the two effects can't fight — pair this with
 * `LocalOverscrollConfiguration provides null` where the list is placed.
 *
 * The offset is applied with [graphicsLayer], so a drag only invalidates the draw phase, and the
 * whole effect lives in a [remember]ed band so recomposition never restarts it.
 */
@Composable
fun Modifier.iosBounce(): Modifier {
    val density = LocalDensity.current
    val limit = with(density) { IosBounceLimit.toPx() }
    val scope = rememberCoroutineScope()
    val band = remember(scope, limit) { BounceBand(scope, limit) }

    return this
        .graphicsLayer { translationY = band.offset.value }
        .nestedScroll(band.connection)
}

/**
 * One rubber band: the displacement, the spring that returns it to rest, and the nested-scroll
 * connection that feeds it.
 *
 * [pull] is the authoritative finger travel and is written synchronously from the drag callbacks;
 * [offset] is only the drawn value. They are kept apart because animating is suspend — writing
 * every drag delta through a coroutine loses deltas when several arrive in one frame — while
 * [offset] gives the settle animation and the draw phase a single [Animatable] to follow.
 *
 * Every path back to rest goes through [settle], which is launched in the composition's scope, not
 * in the gesture's: a spring living in the gesture coroutine dies the moment the next touch lands
 * and leaves the band stuck mid-pull, which is what made the old version stop working after a few
 * pulls.
 */
private class BounceBand(
    private val scope: CoroutineScope,
    private val limit: Float,
) {
    /** What the layer is translated by. Only ever read in the draw phase. */
    val offset = Animatable(0f)

    /** Signed distance the finger has travelled past the edge, before the resistance curve. */
    private var pull = 0f

    /** True while the release spring is in flight. */
    private var springing = false

    /**
     * True from a release at the edge until its fling ends. The fling keeps pushing `SideEffect`
     * deltas into [onPostScroll]; feeding those back into the band would re-stretch it and cancel
     * the spring that just started — the "wait for the fling, then bounce" delay.
     */
    private var flingFromBand = false

    /** Bumped whenever a settle is superseded, so a queued spring cannot run under a new touch. */
    private var settleId = 0

    private fun show() {
        val value = sign(pull) * rubberBand(abs(pull), limit)
        scope.launch { offset.snapTo(value) }
    }

    private fun pullBy(delta: Float) {
        pull = (pull + delta).coerceIn(-PullCap * limit, PullCap * limit)
        show()
    }

    /**
     * Springs back to rest from wherever the band currently is.
     *
     * [velocity] is the velocity the gesture was released with. Handing it to the spring means the
     * band starts moving on the same frame instead of accelerating from a standstill, and a flick
     * away from the edge carries a little further before turning around, the way iOS feels.
     */
    private fun settle(velocity: Float = 0f) {
        pull = 0f
        springing = true
        val id = ++settleId
        val launchVelocity = (velocity.coerceIn(-1500f, 1500f)) * FLING_TO_SPRING
        scope.launch {
            if (id != settleId) return@launch
            try {
                offset.stop()
                offset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = launchVelocity,
                )
            } finally {
                // Cancelled by a new touch or by another settle: either way it is no longer running.
                if (id == settleId) springing = false
            }
        }
    }

    /**
     * A finger landing while the band is still springing back picks it up where it is, instead of
     * letting the spring keep dragging it under the touch.
     */
    private fun interrupt() {
        if (!springing) return
        val value = offset.value
        // Walk the curve backwards so a resumed pull continues at the same resistance.
        pull = sign(value) * inverseRubberBand(abs(value), limit)
        springing = false
        settleId++
        scope.launch { offset.stop() }
    }

    private fun relaxing(delta: Float) = pull != 0f && (pull > 0f) != (delta > 0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (source == NestedScrollSource.UserInput) {
                flingFromBand = false
                interrupt()
            }
            if (!relaxing(delta)) return Offset.Zero
            // Dragging back towards the edge: the band gives way one to one, and any travel left
            // over after it reaches the edge belongs to the list again.
            val next = pull + delta
            if (next * pull <= 0f) {
                val taken = -pull
                pull = 0f
                show()
                return Offset(0f, taken)
            }
            pullBy(delta)
            return Offset(0f, delta)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            val delta = available.y
            if (delta == 0f) return Offset.Zero
            // After a release at the edge the fling keeps producing SideEffect deltas even though
            // the list cannot move. Stretching the band with them would cancel the spring that
            // just started on release, so the visible bounce would only begin once the fling ends.
            if (source != NestedScrollSource.UserInput && flingFromBand) return Offset.Zero
            // The list hit its edge; whatever it could not scroll stretches the band. Consuming it
            // keeps the platform effect out of the drag.
            if (pull == 0f || (pull > 0f) == (delta > 0f)) pullBy(delta)
            return Offset(0f, delta)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // Pointer up at the edge: start the spring right here, with the release velocity, before
            // the fling has a chance to run. The list keeps the whole velocity, so a flick back into
            // the content still flings normally and nothing is consumed twice.
            flingFromBand = pull != 0f
            if (pull != 0f) settle(available.y)
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // A fling that reached the edge from inside the content stretched the band on the way
            // out; put it back now that the fling is done.
            flingFromBand = false
            if (pull != 0f) settle(available.y)
            return Velocity.Zero
        }
    }
}

/**
 * iOS' rubber band curve: the band follows at [RubberBandCoefficient] of the finger's travel at
 * first and gets stiffer as the pull grows, approaching [limit] without ever passing it.
 */
private fun rubberBand(distance: Float, limit: Float): Float =
    (1f - 1f / (distance * RubberBandCoefficient / limit + 1f)) * limit

/** Inverse of [rubberBand]: how far the finger must be from the edge for a given displacement. */
private fun inverseRubberBand(offset: Float, limit: Float): Float {
    val y = offset.coerceIn(0f, limit * 0.99f)
    return y * limit / (RubberBandCoefficient * (limit - y))
}

private const val RubberBandCoefficient = 0.55f

/** How much of the release velocity the spring takes over; the rest is an over-eager overshoot. */
private const val FLING_TO_SPRING = 0.4f

/** Upper bound on the tracked finger travel; the curve is already near its asymptote there. */
private const val PullCap = 8f

/** How far the band can stretch before it stops following the finger. */
private val IosBounceLimit = 140.dp
