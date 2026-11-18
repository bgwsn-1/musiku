package app.musiku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port of the web build's `LiquidMetalButton.tsx` (Paper Shaders' liquid-metal fragment shader).
 *
 * The surface itself lives in `LiquidMetal.kt`; this file is the button chrome around it: press
 * scale/lift, the hover speed bump, and the click ripple.
 *
 * Layout mirrors the original DOM stack: the shader paints the whole pill, a black capsule inset by
 * 2dp covers it, and only that ring shows through — the metal is the *frame*, not the surface.
 */
@Composable
fun LiquidMetalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = label,
) {
    // 142x46 for the labelled pill, 46x46 for the icon-only one — same as the web's `dimensions`.
    val width: Dp = if (label == null) 46.dp else 142.dp
    val height: Dp = 46.dp

    var pressed by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    val speed = remember { mutableFloatStateOf(0.6f) }
    val scope = rememberCoroutineScope()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "liquidMetalScale",
    )
    val pressLift by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(150),
        label = "liquidMetalLift",
    )

    // The clock inside `rememberLiquidMetal` reads this through `rememberUpdatedState`, so only
    // the draw phase reacts to the speed changes.
    val liquid = rememberLiquidMetal(speed.floatValue)

    val ripples = remember { mutableStateListOf<Ripple>() }

    Box(
        modifier = modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = pressLift * 1.dp.toPx()
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription?.let { this.contentDescription = it }
                onClick {
                    onClick()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { at ->
                        val ripple = Ripple(Animatable(0f), at)
                        ripples += ripple
                        scope.launch {
                            ripple.progress.animateTo(1f, tween(600, easing = LinearEasing))
                            ripples -= ripple
                        }
                        // One speed burst per click, then settle back to the resting/hover speed.
                        scope.launch {
                            speed.floatValue = 2.4f
                            delay(300)
                            speed.floatValue = if (hovered) 1f else 0.6f
                        }
                        onClick()
                    },
                )
            }
            .pointerInput(Unit) {
                // Only reachable with a mouse or stylus; on touch the shader just runs at rest speed.
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> {
                                hovered = true
                                speed.floatValue = 1f
                            }
                            PointerEventType.Exit -> {
                                hovered = false
                                speed.floatValue = 0.6f
                            }
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .drawBehind { liquid.paint(this) },
        )

        // The black capsule from the original: 2dp inset, so the shader reads as a rim.
        Box(
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF202020), Color(0xFF000000))),
                ),
        )

        Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
            for (ripple in ripples) {
                val progress = ripple.progress.value
                val radius = 10.dp.toPx() * (1f + 3f * progress)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f * (1f - progress)),
                            Color.Transparent,
                        ),
                        center = ripple.at,
                        radius = radius,
                    ),
                    radius = radius,
                    center = ripple.at,
                )
            }
        }

        if (label != null) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.align(Alignment.Center).size(18.dp),
            )
        }
    }
}

private class Ripple(
    val progress: Animatable<Float, AnimationVector1D>,
    val at: Offset,
)
