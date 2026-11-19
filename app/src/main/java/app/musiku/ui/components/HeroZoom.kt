package app.musiku.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * Pull-to-zoom state for a detail screen's hero artwork, ported from Convx
 * (`ui/utils/MotionModifiers.kt`).
 *
 * The list translates with the rubber band and the artwork grows up to +18% as the pull passes the
 * top edge; nothing grows while scrolling normally. Convx gates this on its iOS overscroll
 * preference, which Musiku does not have — the bounce is always on here.
 */
@Stable
class HeroZoom internal constructor(
    internal val rawPull: MutableFloatState,
    internal val returnPull: MutableFloatState,
    /** Viewport height in pixels. */
    internal val viewport: MutableFloatState,
    val maxPull: Float,
) {
    /** List translation in pixels. */
    val offset: Float
        get() = rubberBand(rawPull.floatValue + returnPull.floatValue, viewport.floatValue)

    /** Only pulling past the top grows the artwork. */
    val scale: Float
        get() = 1f + (offset / maxPull).coerceIn(0f, 1f) * 0.18f
}

@Composable
fun rememberHeroZoom(maxPull: Dp = 220.dp): HeroZoom {
    val rawPull = remember { mutableFloatStateOf(0f) }
    val returnPull = remember { mutableFloatStateOf(0f) }
    val viewport = remember { mutableFloatStateOf(0f) }
    val maxPullPx = with(LocalDensity.current) { maxPull.toPx() }
    return remember(rawPull, returnPull, viewport, maxPullPx) {
        HeroZoom(rawPull, returnPull, viewport, maxPullPx)
    }
}

/**
 * Translates the list with the pull and feeds [HeroZoom.scale] to the header.
 *
 * Convx disables the ambient overscroll for these lists (`heroZoom.listOverscroll()` returns null)
 * because this connection owns the top edge; on Musiku that means placing the list under
 * `LocalOverscrollConfiguration provides null`, the same way the large-title lists do.
 */
@Composable
fun Modifier.heroPullZoom(zoom: HeroZoom): Modifier {
    val scope = rememberCoroutineScope()
    val connection = remember(zoom, scope) { HeroPullConnection(zoom.rawPull, zoom.returnPull, scope) }
    DisposableEffect(connection) {
        onDispose { connection.dispose() }
    }
    return this
        .onSizeChanged { zoom.viewport.floatValue = it.height.toFloat() }
        .graphicsLayer { translationY = zoom.offset }
        .pointerInput(connection) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                connection.pauseReturnForTouch()
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                connection.finishTouch()
            }
        }
        .nestedScroll(connection)
}

private class HeroPullConnection(
    private val rawPull: MutableFloatState,
    private val returnPull: MutableFloatState,
    private val scope: CoroutineScope,
) : NestedScrollConnection {
    private var gestureGeneration = 0L
    private var releasedGesture = 0L
    private var releasedFling: Job? = null
    private var reboundGeneration = 0L
    private var reboundJob: Job? = null
    private var disposed = false

    fun pauseReturnForTouch() {
        gestureGeneration++
        releasedFling = null
        cancelRebound()
        releaseActivePull()
    }

    fun finishTouch() {
        // Release always springs back, even when no fling is dispatched after the touch.
        if (rawPull.floatValue != 0f) startRebound(0f) else resumeReturnIfNeeded()
    }

    fun dispose() {
        disposed = true
        gestureGeneration++
        releasedFling = null
        cancelRebound()
        rawPull.floatValue = 0f
        returnPull.floatValue = 0f
    }

    private fun releaseActivePull() {
        returnPull.floatValue += rawPull.floatValue
        rawPull.floatValue = 0f
    }

    private fun resumeReturnIfNeeded() {
        if (reboundJob == null && returnPull.floatValue != 0f) startRebound(0f)
    }

    private fun cancelRebound() {
        reboundGeneration++
        reboundJob?.cancel()
        reboundJob = null
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            gestureGeneration++
            resumeReturnIfNeeded()
        }
        val current = rawPull.floatValue
        if (current != 0f && available.y != 0f && sign(available.y) != sign(current)) {
            val target = current + available.y
            val settled = if (sign(target) != sign(current)) 0f else target
            rawPull.floatValue = settled
            return Offset(0f, settled - current)
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (available.y == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        // Top edge only: a bottom pull dragged the whole list and left it stuck.
        if (available.y < 0f && rawPull.floatValue <= 0f) return Offset.Zero
        rawPull.floatValue += available.y
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        releasedGesture = gestureGeneration
        releasedFling = currentCoroutineContext()[Job]
        if (rawPull.floatValue == 0f) return Velocity.Zero
        startRebound(available.y)
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // A cancelled fling can finish after the user has touched down again.
        if (disposed || releasedFling !== currentCoroutineContext()[Job] ||
            releasedGesture != gestureGeneration
        ) {
            return Velocity.Zero
        }
        releasedFling = null
        if (available.y == 0f) return Velocity.Zero
        startRebound(available.y)
        return Velocity(0f, available.y)
    }

    private fun startRebound(leftoverVelocity: Float) {
        if (disposed) return
        cancelRebound()
        releaseActivePull()
        val rebound = reboundGeneration
        reboundJob = scope.launch {
            try {
                val seedVelocity = bounceSeedVelocity(leftoverVelocity)
                animate(
                    initialValue = returnPull.floatValue,
                    targetValue = 0f,
                    initialVelocity = seedVelocity,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = bounceStiffnessFor(seedVelocity),
                    ),
                ) { value, _ -> returnPull.floatValue = value }
            } finally {
                if (rebound == reboundGeneration) {
                    returnPull.floatValue = 0f
                    reboundJob = null
                }
            }
        }
    }
}
