package app.musiku.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.sin

/**
 * Port of the web build's `LiquidMetalButton.tsx` (Paper Shaders' liquid-metal fragment shader).
 *
 * The shader is a GLSL ES 3.00 file translated to AGSL, so it only exists from Android 13 (API 33)
 * up. On 12 the composable falls back to an animated metallic gradient that keeps the same rim.
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

    // Frame clock instead of a Compose animation: the value is only read inside the draw lambda,
    // so each tick invalidates the draw phase and never triggers recomposition.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) clock.floatValue += (now - last) / 1000f * speed.floatValue
                last = now
            }
        }
    }

    val ripples = remember { mutableStateListOf<Ripple>() }

    val runtimeShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // A shader that fails to compile throws here rather than at draw time, which is the
            // only place the failure can be caught without patching the dispatcher.
            runCatching { RuntimeShader(LIQUID_METAL_AGSL) }.getOrNull()
        } else {
            null
        }
    }
    val shaderBrush = remember(runtimeShader) { runtimeShader?.let { ShaderBrush(it) } }

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
                .drawBehind {
                    val time = clock.floatValue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        runtimeShader != null && shaderBrush != null
                    ) {
                        drawLiquidMetal(runtimeShader, shaderBrush, time)
                    } else {
                        drawRect(fallbackMetal(time))
                    }
                },
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawLiquidMetal(
    shader: RuntimeShader,
    brush: ShaderBrush,
    time: Float,
) {
    shader.setFloatUniform("u_resolution", size.width, size.height)
    shader.setFloatUniform("u_time", time)
    drawRect(brush)
}

/** Pre-Android-13 stand-in: a metallic sheen that drifts across the rim. */
private fun fallbackMetal(time: Float): Brush {
    val drift = (sin(time * 0.9f) + 1f) / 2f
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF2A2A31),
            Color(0xFFE9E9F2),
            Color(0xFF767681),
            Color(0xFF0E0E11),
            Color(0xFFD3D3DE),
        ),
        start = Offset(drift * 160f, 0f),
        end = Offset(drift * 160f + 140f, 120f),
    )
}

/**
 * `liquidMetalFragmentShader` from @paper-design/shaders 0.0.82, translated to AGSL.
 *
 * Changes from the GLSL original, all forced by the runtime:
 *  - the image variant (`u_image`, `u_isImage`, `textureGrad`, 3x3 edge blur) is gone; this button
 *    only ever uses the built-in shapes, and the web passes no image
 *  - `v_objectUV` was computed in the vertex shader, so the equivalent transform is inlined here
 *    from `fragCoord` (origin: top-left, so y is flipped once)
 *  - `u_colorBack`/`u_colorTint` are dropped: the web leaves them at their defaults, so the
 *    background contributes nothing and the tint blend is a no-op
 */
private const val LIQUID_METAL_AGSL = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_repetition;
uniform float u_softness;
uniform float u_shiftRed;
uniform float u_shiftBlue;
uniform float u_distortion;
uniform float u_contour;
uniform float u_angle;
uniform float u_scale;
uniform float u_offsetX;
uniform float u_offsetY;

float2 rot2(float2 v, float th) {
    return float2(cos(th) * v.x - sin(th) * v.y,
                  sin(th) * v.x + cos(th) * v.y);
}

float3 permute(float3 x) {
    return mod(((x * 34.0) + 1.0) * x, 289.0);
}

float snoise(float2 v) {
    float4 C = float4(0.211324865405187, 0.366025403784439,
                      -0.577350269189626, 0.024390243902439);
    float2 i = floor(v + dot(v, C.yy));
    float2 x0 = v - i + dot(i, C.xx);
    float2 i1 = x0.x > x0.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float4 x12 = x0.xyxy + C.xxzz;
    x12 = float4(x12.x - i1.x, x12.y - i1.y, x12.z, x12.w);
    i = mod(i, 289.0);
    float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
    float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)),
                   float3(0.0));
    m = m * m;
    m = m * m;
    float3 x = 2.0 * fract(p * C.www) - 1.0;
    float3 h = abs(x) - 0.5;
    float3 ox = floor(x + 0.5);
    float3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    float3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.y = a0.y * x12.x + h.y * x12.y;
    g.z = a0.z * x12.z + h.z * x12.w;
    return 130.0 * dot(m, g);
}

// One channel of the stripe pattern: two thin bright bands inside a wide dark band.
float stripe(float c1, float c2, float stripeP, float3 w, float blur, float bump) {
    float ch = mix(c2, c1, smoothstep(0.0, 2.0 * blur, stripeP));

    float border = w.x;
    ch = mix(ch, c2, smoothstep(border, border + 2.0 * blur, stripeP));

    border = w.x + 0.4 * (1.0 - bump) * w.y;
    ch = mix(ch, c1, smoothstep(border, border + 2.0 * blur, stripeP));

    border = w.x + 0.5 * (1.0 - bump) * w.y;
    ch = mix(ch, c2, smoothstep(border, border + 2.0 * blur, stripeP));

    border = w.x + w.y;
    ch = mix(ch, c1, smoothstep(border, border + 2.0 * blur, stripeP));

    float gradientT = (stripeP - w.x - w.y) / w.z;
    float gradient = mix(c1, c2, smoothstep(0.0, 1.0, gradientT));
    ch = mix(ch, gradient, smoothstep(border, border + 0.5 * blur, stripeP));
    return ch;
}

half4 main(float2 fragCoord) {
    float2 res = max(u_resolution, float2(1.0));
    float t = 0.3 * (u_time + 2.8);

    // Inlined vertex stage: the layer is a single quad, so NDC == fragCoord remapped.
    float2 ndc = float2(fragCoord.x / res.x - 0.5, 0.5 - fragCoord.y / res.y);
    float2 objectUV = ndc * (res / min(res.x, res.y));
    objectUV += float2(-u_offsetX, u_offsetY);
    objectUV /= u_scale;

    float2 uv = objectUV + 0.5;
    uv.y = 1.0 - uv.y;

    float cycleWidth = u_repetition;

    // shape = 1 (circle) from the web's args; at u_scale 8 it fully contains the pill.
    float2 shapeUV = (uv - 0.5) * 0.67;
    float edge = pow(clamp(3.0 * length(shapeUV), 0.0, 1.0), 18.0);
    float opacity = 1.0 - smoothstep(0.9 - 2.0 * fwidth(edge), 0.9, edge);
    edge = 1.2 * edge;

    float2 rotatedUV = rot2(uv - 0.5, (-u_angle + 70.0) * 3.14159265358979323846 / 180.0) + 0.5;
    float diagBLtoTR = rotatedUV.x - rotatedUV.y;
    float diagTLtoBR = rotatedUV.x + rotatedUV.y;

    float3 color1 = float3(0.98, 0.98, 1.0);
    float3 color2 = float3(0.1, 0.1, 0.1 + 0.1 * smoothstep(0.7, 1.3, diagTLtoBR));

    float2 gradUV = uv - 0.5;
    float dist = length(gradUV + float2(0.0, 0.2 * diagBLtoTR));
    gradUV = rot2(gradUV, (0.25 - 0.2 * diagBLtoTR) * 3.14159265358979323846);
    float direction = gradUV.x;

    float bump = 1.0 - pow(1.8 * dist, 1.2);
    bump *= pow(uv.y, 0.3);

    float thinStrip1Ratio = 0.12 / cycleWidth * (1.0 - 0.4 * bump);
    float thinStrip2Ratio = 0.07 / cycleWidth * (1.0 + 0.4 * bump);
    float wideStripRatio = 1.0 - thinStrip1Ratio - thinStrip2Ratio;
    float thinStrip1Width = cycleWidth * thinStrip1Ratio;
    float thinStrip2Width = cycleWidth * thinStrip2Ratio;

    float noise = snoise(uv - t);
    edge += (1.0 - edge) * u_distortion * noise;

    direction += diagBLtoTR;
    float contour = 0.0;
    direction -= 2.0 * noise * diagBLtoTR *
        (smoothstep(0.0, 1.0, edge) * (1.0 - smoothstep(0.0, 1.0, edge)));
    direction *= mix(1.0, 1.0 - edge, smoothstep(0.5, 1.0, u_contour));
    direction -= 1.7 * edge * smoothstep(0.5, 1.0, u_contour);
    direction += 0.2 * pow(u_contour, 4.0) * (1.0 - smoothstep(0.0, 1.0, edge));

    bump *= clamp(pow(uv.y, 0.1), 0.3, 1.0);
    direction *= (0.1 + (1.1 - edge) * bump);
    direction *= (0.4 + 0.6 * (1.0 - smoothstep(0.5, 1.0, edge)));
    direction += 0.18 * (smoothstep(0.1, 0.2, uv.y) * (1.0 - smoothstep(0.2, 0.4, uv.y)));
    direction += 0.03 * (smoothstep(0.1, 0.2, 1.0 - uv.y) * (1.0 - smoothstep(0.2, 0.4, 1.0 - uv.y)));
    direction *= (0.5 + 0.5 * pow(uv.y, 2.0));
    direction *= cycleWidth;
    direction -= t;

    float colorDispersion = clamp(1.0 - bump, 0.0, 1.0);

    float dispersionRed = colorDispersion;
    dispersionRed += 0.03 * bump * noise;
    dispersionRed += 5.0 * (smoothstep(-0.1, 0.2, uv.y) * (1.0 - smoothstep(0.1, 0.5, uv.y))) *
        (smoothstep(0.4, 0.6, bump) * (1.0 - smoothstep(0.4, 1.0, bump)));
    dispersionRed -= diagBLtoTR;

    float dispersionBlue = colorDispersion * 1.3;
    dispersionBlue += (smoothstep(0.0, 0.4, uv.y) * (1.0 - smoothstep(0.1, 0.8, uv.y))) *
        (smoothstep(0.4, 0.6, bump) * (1.0 - smoothstep(0.4, 0.8, bump)));
    dispersionBlue -= 0.2 * edge;

    dispersionRed *= u_shiftRed / 20.0;
    dispersionBlue *= u_shiftBlue / 20.0;

    float blur = u_softness / 15.0 + 0.3 * contour;

    float3 w = float3(thinStrip1Width, thinStrip2Width, wideStripRatio);
    w.y -= 0.02 * smoothstep(0.0, 1.0, edge + bump);

    float stripeR = fract(direction + dispersionRed);
    float stripeG = fract(direction);
    float stripeB = fract(direction - dispersionBlue);

    float r = stripe(color1.r, color2.r, stripeR, w, blur + fwidth(stripeR), bump);
    float g = stripe(color1.g, color2.g, stripeG, w, blur + fwidth(stripeG), bump);
    float b = stripe(color1.b, color2.b, stripeB, w, blur + fwidth(stripeB), bump);

    return half4(half3(float3(r, g, b) * opacity), half(opacity));
}
"""
