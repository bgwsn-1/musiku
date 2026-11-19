package app.musiku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * The one [HazeState] each layer shares: content behind → glass surfaces in front. Provided by
 * `AppRoot` so components can stay unaware of how the effect is wired.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Low-device mode: skip every backdrop blur and fall back to flat glass fills. */
val LocalLowDevice = compositionLocalOf { false }

/**
 * Liquid glass, the Musiku signature: a real backdrop blur (`RenderEffect`) under a translucent
 * fill, with the web's two edge highlights on top.
 *
 * Web source of truth (globals.css):
 *   .glass         backdrop-filter: blur(28px) saturate(1.7);  background: var(--glass-fill)
 *   .glass-lg      backdrop-filter: blur(40px) saturate(1.6);  background: var(--glass-fill-strong)
 *   .glass::after  inset 0 1px 0 var(--glass-edge-top) + inset 0 0 0 1px var(--glass-edge)
 *   --glass-shadow 0 14px 44px -12px rgba(0,0,0,.7), 0 2px 8px rgba(0,0,0,.3)   (dark)
 *   --glass-shadow 0 12px 40px -14px rgba(30,30,70,.28), 0 2px 8px rgba(30,30,70,.08) (light)
 *
 * Haze draws the blur *behind* the modified content, so the chain blurs first and then paints the
 * translucent fill and edges on top — the same stacking the browser does for backdrop-filter.
 * The composable using this must sit beside (never inside) a `Modifier.haze(state)` node.
 *
 * Note: Haze 1.1.0 has no hook to saturate the *sampled backdrop* (the web's `saturate(1.7)`), so
 * the blur comes through at source saturation. The fill colours below already carry the web's
 * alpha, and [glassBase] gives the blur an opaque floor so the result matches the web's
 * light/dark fills even where the sampled content is busy.
 */
@Composable
fun Modifier.glass(
    state: HazeState?,
    shape: Shape,
    strong: Boolean = false,
    shadow: Dp = 0.dp,
): Modifier {
    val lowDevice = LocalLowDevice.current
    var m = this
    if (shadow > 0.dp) {
        m = m.shadow(shadow, shape, clip = false, ambientColor = Tok.Shadow, spotColor = Tok.Shadow)
    }
    // Order matters: the clip must wrap hazeChild so the blurred backdrop is rounded, and the fill
    // + edges must come after it so they paint on top of the blur (web: backdrop-filter then bg).
    m = m.clip(shape)
    if (state != null && !lowDevice) {
        m = m.hazeChild(
            style = HazeStyle(
                // Drawn *behind* the blur; the translucent fill above is what the eye reads.
                // Matches the web's --surface-solid, which is what glass degrades to there.
                backgroundColor = Tok.GlassBase,
                tints = emptyList(),
                blurRadius = if (strong) 40.dp else 28.dp,
                noiseFactor = 0f,
            ),
            state = state,
        ) {
            // Haze disables blur on SDK 31 (RenderNode invalidation) and scrims instead. The web
            // look is a real blur, and 31 is our floor, so ask for it explicitly.
            blurEnabled = true
        }
    }
    val fill = if (lowDevice) Tok.SolidGlass else if (strong) Tok.GlassFillStrong else Tok.GlassFill
    return if (lowDevice) {
        m.background(fill).border(1.dp, Tok.Hairline, shape)
    } else {
        m.background(fill).border(1.dp, glassEdgeBrush(), shape)
    }
}

/**
 * `inset 0 1px 0 var(--glass-edge-top)` fading into `inset 0 0 0 1px var(--glass-edge)`.
 * Resolved per theme: the web's light edge is a white top highlight over a 6% dark hairline;
 * dark is the same shape with a 20% white top over a 7% white hairline.
 */
@Composable
fun glassEdgeBrush(): Brush = Brush.verticalGradient(
    0f to Tok.GlassEdgeTop,
    0.4f to Tok.GlassEdge,
    1f to Tok.GlassEdge,
)

/**
 * Glass for overlays that cannot sample the app content behind them (sheets/dialogs live in their
 * own window): the same fill, radii and edges, minus the backdrop blur. Uses the *strong* fill so
 * the surface stays legible without the blur underneath.
 */
@Composable
fun Modifier.glassFallback(shape: Shape, strong: Boolean = false): Modifier {
    val lowDevice = LocalLowDevice.current
    val fill = if (lowDevice) Tok.SolidGlass else if (strong) Tok.GlassFillStrong else Tok.GlassFill
    return if (lowDevice) {
        clip(shape).background(fill).border(1.dp, Tok.Hairline, shape)
    } else {
        clip(shape).background(fill).border(1.dp, glassEdgeBrush(), shape)
    }
}

/** Dialog container colour: flat opaque in low-device mode, the web's glass fill otherwise. */
@Composable
fun glassDialogColor(): Color = if (LocalLowDevice.current) Tok.SolidGlass else Tok.GlassFillStrong

/**
 * Just the edge highlights, no fill. For surfaces whose fill is already painted by something else
 * (a `ModalBottomSheet` with a non-transparent `containerColor`, say) — painting the fill twice
 * would stack the alpha and make the glass read as an opaque slab.
 */
@Composable
fun Modifier.glassEdges(shape: Shape): Modifier =
    border(1.dp, glassEdgeBrush(), shape)
