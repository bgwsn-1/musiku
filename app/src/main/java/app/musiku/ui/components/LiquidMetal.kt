package app.musiku.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.sin

/**
 * The liquid-metal surface shared by the button, the search field, and the seek bar.
 *
 * The shader is Paper Shaders' `liquidMetalFragmentShader` (0.0.82) translated to AGSL. Whoever
 * hosts it supplies its own clock: the animated value is only read inside draw lambdas, so the
 * surface repaints without ever recomposing.
 */
internal class LiquidMetal(
    private val clock: () -> Float,
    private val shader: RuntimeShader?,
    private val brush: ShaderBrush?,
    private val scale: Float = 8f,
    private val masked: Boolean = true,
    private val lift: Float = 0f,
) {
    /** Fills the whole draw bounds. */
    fun paint(scope: DrawScope) {
        if (runtimeShaderUsable(shader, brush)) {
            paintShader(scope, shader!!, brush!!, clock())
        } else {
            scope.drawRect(fallbackMetal(clock()))
        }
    }

    /**
     * Paints the surface clipped to a rounded rect. The shader stays anchored to the whole canvas,
     * so a partially filled seek bar reveals the pattern instead of rescaling it.
     */
    fun paintRounded(scope: DrawScope, topLeft: Offset, rectSize: Size, cornerRadius: CornerRadius) {
        if (rectSize.width <= 0f || rectSize.height <= 0f) return
        val clip = Path().apply {
            addRoundRect(RoundRect(Rect(topLeft, rectSize), cornerRadius.x, cornerRadius.y))
        }
        scope.clipPath(clip) { paint(this) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun paintShader(scope: DrawScope, shader: RuntimeShader, brush: ShaderBrush, time: Float) {
        shader.setFloatUniform("u_resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("u_time", time)
        // Every other uniform has to be written explicitly: RuntimeShader starts them all at 0,
        // and u_scale = 0 would divide the UVs to infinity.
        shader.setFloatUniform("u_repetition", 4f)
        shader.setFloatUniform("u_softness", 0.5f)
        shader.setFloatUniform("u_shiftRed", 0.3f)
        shader.setFloatUniform("u_shiftBlue", 0.3f)
        shader.setFloatUniform("u_distortion", 0f)
        shader.setFloatUniform("u_contour", 0f)
        shader.setFloatUniform("u_angle", 45f)
        shader.setFloatUniform("u_scale", scale)
        shader.setFloatUniform("u_shape", if (masked) 1f else 0f)
        shader.setFloatUniform("u_lift", lift)
        shader.setFloatUniform("u_offsetX", 0.1f)
        shader.setFloatUniform("u_offsetY", -0.1f)
        scope.drawRect(brush)
    }
}

private fun runtimeShaderUsable(
    shader: RuntimeShader?,
    brush: ShaderBrush?,
): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null

/** One compiled program shared by every surface; uniforms are rewritten before each draw. */
private val sharedShader: RuntimeShader? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // A shader that fails to compile throws here rather than at draw time, which is the only
        // place the failure can be caught without patching the dispatcher.
        runCatching { RuntimeShader(LIQUID_METAL_AGSL) }.getOrNull()
    } else {
        null
    }
}

@Composable
internal fun rememberLiquidMetal(
    speed: Float = 0.6f,
    scale: Float = 8f,
    masked: Boolean = true,
    lift: Float = 0f,
): LiquidMetal {
    val clock = remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) clock.floatValue += (now - last) / 1000f * currentSpeed
                last = now
            }
        }
    }

    val shader = remember { sharedShader }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    return remember(shader, brush, scale, masked, lift) {
        LiquidMetal({ clock.floatValue }, shader, brush, scale, masked, lift)
    }
}

/** Pre-Android-13 stand-in: a metallic sheen that drifts across the surface. */
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
 *  - the image variant (`u_image`, `u_isImage`, `textureGrad`, 3x3 edge blur) is gone; the web
 *    passes no image and only ever uses the built-in circle shape
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
uniform float u_shape;
uniform float u_lift;
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

    // shape = 1 (circle) for the button's pill, shape = 0 (full bleed) for the wide bars, where a
    // circle mask would leave everything outside the centre transparent.
    float edge = 0.0;
    if (u_shape > 0.5) {
        float2 shapeUV = (uv - 0.5) * 0.67;
        edge = pow(clamp(3.0 * length(shapeUV), 0.0, 1.0), 18.0);
    }
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

    direction += diagTLtoBR;
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

    // u_lift raises the darks towards white. The button rim wants the full contrast, but a seek
    // bar filled with the raw pattern has long near-black stretches that read as unfilled.
    float3 rgb = mix(float3(r, g, b), float3(1.0), u_lift);
    return half4(half3(rgb * opacity), half(opacity));
}
"""
