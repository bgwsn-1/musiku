package app.musiku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlin.math.max

/**
 * Port of Aceternity's `LiquidGlass` (liquid-glass.tsx) for surfaces floating over the page.
 *
 * The web component bakes a displacement map (R/G = bend, B = specular, A = shape mask) and feeds it
 * to `backdrop-filter: url(#…)`, so the pixels *behind* the element are bent through a Snell's-law
 * rim. Android has no `backdrop-filter`, but Haze already captures the page into
 * [HazeState.contentLayer], which is the same thing the browser hands to the filter. So the rim
 * band is drawn straight from that layer: the bevel shows what sits under the edge *sharp*, while
 * the body only shows Haze's frosted blur of it. That contrast — a clear rim around a diffused
 * interior — is the part of the web look that survives without a displacement map.
 *
 * What does not port: the web bends the backdrop per pixel (a Snell's-law slope baked into the
 * displacement map), so its rim shows the world *compressed* into the bevel. Android's canvas can
 * only scale a whole layer, which moves the rim against the body instead of grading into it, so
 * [refraction] is left at 1 (no scaling) by default.
 *
 * The rest of the recipe maps over directly:
 *
 *  - **Frost** — the web blurs *after* refracting (`frost` px). Here Haze's backdrop blur is the
 *    frost, and it only covers the body: the band stays sharp, which is what makes the edge read as
 *    thick glass rather than a painted border.
 *  - **Tint** — `rgb(255 255 255 / tint)` is the body: a white veil over the backdrop, plus a faint
 *    vertical sheen that brightens the lit top edge.
 *  - **Chromatic aberration** — the web splits the displaced image into R/G/B passes at
 *    `scale ± aberration`. Without per-pixel displacement the split is approximated by pulling a red
 *    ring up-left and a blue ring down-right by [aberration] under the neutral rim, so only the
 *    fringe shows.
 *  - **Bevel + specular** — a lit top rim fading to a shaded bottom rim, running along the outline so
 *    it follows the corner arcs the way the light at -0.55/-0.85 does in the generated map.
 *
 * Like [glass], the node using this must sit beside a `Modifier.haze(state)` node, never inside it.
 *
 * @param refraction optional zoom on the band's backdrop. Below 1 the rim pulls in the content
 *   outside the edge, but it does not fall off towards the body, so a seam appears where the two
 *   meet. Kept at 1 by default; try 0.94 on the tab bar if you want the lens to read harder.
 */
@Composable
fun Modifier.liquidGlass(
    state: HazeState?,
    shape: Shape,
    /** White body tint, 0–1. The source's default is 0.12. */
    tint: Float = 0.12f,
    /** Visible thickness of the refracting rim. */
    bevelDepth: Dp = 3.dp,
    /** Offset between the red and blue fringes. */
    aberration: Dp = 0.8.dp,
    /** Backdrop blur behind the body — the web's `frost`. */
    frost: Dp = 24.dp,
    /** How much the edge compresses what is behind it. 1 = no zoom. */
    refraction: Float = 1f,
    shadow: Dp = 14.dp,
): Modifier {
    val dark = isSystemInDarkTheme()
    // The web body is `rgb(255 255 255 / tint)` — a white veil over the refracted backdrop, never a
    // dark scrim. Tok.GlassFill (56% near-black) is right for sheets that have to hide what is
    // behind them, but over this app's near-black window it just reads as a black slab, which is
    // all the tab bar ever looked like. Light mode needs a much stronger veil: white over a white
    // page vanishes, and there the bevel and the shadow have to carry the surface.
    val veil = Color.White.copy(alpha = if (dark) 0.18f else 0.55f)
    val topLight = Color.White.copy(alpha = if (dark) 0.62f else 0.85f)
    val midLight = Color.White.copy(alpha = if (dark) 0.10f else 0.22f)
    val bottomShade = Color.Black.copy(alpha = if (dark) 0.40f else 0.14f)
    val fringeRed = Color(0xFFFF4D63).copy(alpha = 0.22f)
    val fringeBlue = Color(0xFF4FA8FF).copy(alpha = 0.22f)

    // Where this surface sits in the window. The captured layer is in the page's coordinates, so the
    // band has to be translated by (page origin − our origin) before it is drawn.
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Until the first layout pass the origin is (0,0), which would sample the top of the page into
    // the band for a frame. Skip the band until the surface knows where it is.
    var positioned by remember { mutableStateOf(false) }

    var m = this
    if (shadow > 0.dp) {
        m = m.shadow(shadow, shape, clip = false, ambientColor = Tok.Shadow, spotColor = Tok.Shadow)
    }
    m = m.clip(shape)
    if (state != null) {
        m = m.hazeChild(
            style = HazeStyle(
                backgroundColor = Tok.GlassBase,
                tints = emptyList(),
                blurRadius = frost,
                noiseFactor = 0f,
            ),
            state = state,
        ) { blurEnabled = true }
    }
    return m
        .onGloballyPositioned {
            origin = it.positionInWindow()
            positioned = true
        }
        .background(veil)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rimPx = bevelDepth.toPx()
            val abPx = aberration.toPx()
            // The ring between the outer outline and the same outline inset by the bevel.
            val band = bandPath(outline, rimPx)

            val body = Brush.verticalGradient(
                0f to Color.White.copy(alpha = tint * 0.55f),
                0.45f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = tint * 0.25f),
            )
            val rim = Brush.verticalGradient(
                0f to topLight,
                0.45f to midLight,
                1f to bottomShade,
            )
            val specular = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.22f, 0f),
                radius = size.height * 1.1f,
            )

            onDrawBehind {
                // 1. The rim shows the captured backdrop sharp, where the body only shows its
                //    blurred copy. That contrast is what reads as a thick, bent edge.
                val layer = state?.contentLayer
                if (band != null && layer != null && positioned) {
                    val delta = state.positionOnScreen - origin
                    clipPath(band) {
                        // A zoom below 1 pulls the world outside the edge into the band, but a
                        // uniform scale is the wrong shape for a lens: it never falls off towards
                        // the body, so the two sides of the bevel seam. Left at 1 by default.
                        withTransform({
                            translate(delta.x, delta.y)
                            if (refraction < 1f) scale(refraction, refraction, size.center)
                        }) {
                            drawLayer(layer)
                        }
                    }
                    // The web's filter ends with brightness(1.02) + a specular channel, so the rim
                    // sits a touch lighter than the body. Without a shader the cheapest honest
                    // version is a faint white coat on the band alone.
                    drawPath(band, Color.White.copy(alpha = 0.05f))
                }
                // 2. Body tint over the whole surface, band included.
                drawOutline(outline, body)
                // 3. Chromatic split, tucked under the neutral rim so only the fringe reads.
                translate(-abPx, -abPx) { drawOutline(outline, fringeRed, style = Stroke(rimPx * 0.5f)) }
                translate(abPx, abPx) { drawOutline(outline, fringeBlue, style = Stroke(rimPx * 0.5f)) }
                // 4. Bevel: lit top arc, neutral middle, shaded bottom.
                drawOutline(outline, rim, style = Stroke(rimPx * 0.36f))
                // 5. Specular sheen along the lit edge.
                drawOutline(outline, specular, style = Stroke(rimPx * 0.5f))
            }
        }
}

/** Ring between [outline] and the same shape inset by [inset], as an even-odd path. */
private fun bandPath(outline: Outline, inset: Float): Path? {
    if (outline !is Outline.Rounded) return null
    val rr = outline.roundRect
    if (rr.width <= inset * 2 || rr.height <= inset * 2) return null
    val inner = RoundRect(
        left = rr.left + inset,
        top = rr.top + inset,
        right = rr.right - inset,
        bottom = rr.bottom - inset,
        topLeftCornerRadius = shrink(rr.topLeftCornerRadius, inset),
        topRightCornerRadius = shrink(rr.topRightCornerRadius, inset),
        bottomRightCornerRadius = shrink(rr.bottomRightCornerRadius, inset),
        bottomLeftCornerRadius = shrink(rr.bottomLeftCornerRadius, inset),
    )
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addOutline(outline)
        addOutline(Outline.Rounded(inner))
    }
}

private fun shrink(radius: CornerRadius, by: Float) =
    CornerRadius(max(radius.x - by, 0f), max(radius.y - by, 0f))
