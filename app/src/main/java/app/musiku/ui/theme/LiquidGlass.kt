package app.musiku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.musiku.ui.glass.backdrop.backdrops.LayerBackdrop
import app.musiku.ui.glass.backdrop.drawBackdrop
import app.musiku.ui.glass.backdrop.effects.blur
import app.musiku.ui.glass.backdrop.effects.colorControls
import app.musiku.ui.glass.backdrop.effects.lens
import app.musiku.ui.glass.backdrop.highlight.Highlight
import app.musiku.ui.glass.backdrop.highlight.HighlightStyle
import app.musiku.ui.glass.backdrop.isRenderEffectSupported
import app.musiku.ui.glass.backdrop.shadow.Shadow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The page content that glass surfaces sample from. Fed by `Modifier.layerBackdrop` on the routed
 * content in [app.musiku.ui.AppRoot].
 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Liquid glass, exactly as the Convx client builds it.
 *
 * `drawBackdrop` (Kyant0/backdrop 2.0.x, vendored under `app.musiku.ui.glass.backdrop`) captures the
 * layer behind the surface, then runs one effect chain over it in this order:
 *
 *  1. `colorControls(saturation = 2f)` — the "vibrancy" pass. It is what keeps colours behind the
 *     glass alive instead of greying them out.
 *  2. `blur(3dp)` — only 3dp. Anything heavier smears the lens.
 *  3. `lens(28.8dp, 33.12dp)` — the AGSL rounded-rect refraction with dispersion, i.e. the bend and
 *     the rainbow fringe on the rim.
 *
 * On top: a specular rim (`Highlight`, 0.8dp, 45°, Plus blend) and the outer dark outline that
 * keeps the edge readable on bright content. The surface itself is the iOS-style white lift
 * (#171717 at 15% in dark, #F5F5F7 at 40% in light), not a dark smoke.
 *
 * The numbers are Convx's nav-bar/mini-player tokens (`GlassTintTokens` + `ConvxDesignTokens.Glass`)
 * so the dock matches that app's floating bar 1:1 rather than approximating it.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    /** Specular rim opacity. Convx ships 0.30 in dark, 0.40 in light. */
    highlightAlpha: Float = if (isSystemInDarkTheme()) 0.30f else 0.40f,
    /** Overrides the neutral surface tint (light mode only by default). */
    tint: Color? = null,
): Modifier {
    val dark = isSystemInDarkTheme()
    val backdrop = LocalGlassBackdrop.current

    // GlassTintTokens: dark is a white lift, light is a slightly darker veil.
    val base = if (dark) Color(0xFF171717) else Color(0xFFF5F5F7)
    val baseAlpha = if (dark) 0.15f else 0.40f
    val neutralTint = tint ?: if (dark) Color.Transparent else Color(0xFF3C3C43).copy(alpha = 0.05f)

    // API 31 has no shader inputs and no capture for us: a readable smoked panel instead.
    if (backdrop == null || !isRenderEffectSupported()) {
        return clip(shape)
            .background(base.copy(alpha = baseAlpha))
            .then(
                if (neutralTint.alpha > 0f) {
                    Modifier.background(neutralTint)
                } else {
                    Modifier
                },
            )
    }

    val density = LocalDensity.current
    // Pooled nav-bar glass samples at full resolution, so the effects run at full resolution too.
    val blurPx = with(density) { NavBlur.toPx() }
    val lensHeightPx = with(density) { (MaxLensDp * NavLensHeight).toPx() }
    val lensAmountPx = with(density) { (MaxLensDp * NavLensAmount).toPx() }

    val rim = Color.White.copy(alpha = highlightAlpha)
    // Convx's DarkRimAlpha is 0.32 at the specular rim's own 0.55 baseline.
    val darkRimAlpha = (DarkRimAlpha * (highlightAlpha / EdgeHighlightAlpha)).coerceIn(0f, 1f)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = NavSaturation)
            blur(blurPx)
            lens(lensHeightPx, lensAmountPx)
        },
        highlight = {
            Highlight(
                width = EdgeHighlightWidth,
                style = HighlightStyle.Default(color = rim, angle = HighlightAngle),
            )
        },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(base.copy(alpha = baseAlpha))
            if (neutralTint.alpha > 0f) drawRect(neutralTint)
        },
    ).outerDarkRim(
        shape = shape,
        width = DarkRimWidth,
        alpha = darkRimAlpha,
        angle = HighlightAngle,
    )
}

// --- Convx glass tokens, nav bar / mini player ------------------------------------------------

/** `ConvxDesignTokens.Glass.MaxLensDp` — lens values are fractions of this. */
private val MaxLensDp = 48.dp

/** `GlassTintTokens.NavBlurDp`. */
private val NavBlur = 3.dp

/** `GlassTintTokens.NavLensHeight` — 0.60 × 48dp = 28.8dp. */
private const val NavLensHeight = 0.60f

/** `GlassTintTokens.NavLensAmount` — 0.69 × 48dp = 33.12dp. */
private const val NavLensAmount = 0.69f

/** `GlassTintTokens.NavVibrancy` 2.0 through `glassSaturation`: 1 + 0.5 × 2. */
private const val NavSaturation = 2f

/** `GlassEffect.EdgeHighlightWidth`. */
private val EdgeHighlightWidth = 0.8.dp

/** `GlassEffect.EdgeHighlightAlpha`, the baseline the rim opacity is measured against. */
private const val EdgeHighlightAlpha = 0.55f

/** `GlassEffect.HighlightAngleFrozen`. */
private const val HighlightAngle = 45f

/** `GlassEffect.DarkRimWidth` / `DarkRimAlpha`. */
private val DarkRimWidth = 1.6.dp
private const val DarkRimAlpha = 0.32f

/**
 * Convx's paired dark outline: two diagonal strokes swollen on opposite flanks, so the edge darkens
 * where the light rim does not land. Copied from `Modifier.outerDarkRim`.
 */
private fun Modifier.outerDarkRim(
    shape: Shape,
    width: Dp = 1.6.dp,
    alpha: Float = 0.32f,
    angle: Float = 45f,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = width.toPx())
    val center = Offset(size.width / 2f, size.height / 2f)
    fun flankBrush(axisDeg: Float): Brush {
        val a = axisDeg * (PI / 180f).toFloat()
        val dir = Offset(cos(a), sin(a))
        val reach = dir * (abs(dir.x) * size.width + abs(dir.y) * size.height) / 2f
        return Brush.linearGradient(
            0f to Color.Black.copy(alpha = alpha),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = alpha),
            start = center - reach,
            end = center + reach,
        )
    }
    val firstBrush = flankBrush(angle)
    val secondBrush = flankBrush(angle + 90f)
    onDrawWithContent {
        drawContent()
        translate(0f, 0f) {
            drawOutline(outline = outline, brush = firstBrush, style = stroke)
            drawOutline(outline = outline, brush = secondBrush, style = stroke)
        }
    }
}
