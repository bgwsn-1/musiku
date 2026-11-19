package app.musiku.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Convx's `PlayerCoverStyle` (`ui/player/PlayerCoverStyle.kt`): one cover presentation. The big
 * cover and every preview read the style, so a new look is one implementation here.
 */
@Stable
interface PlayerCoverStyle {
    /** Turns while playing. */
    val spins: Boolean

    /** The art turns with the shape (a record's label); false keeps the art upright inside a turning shape. */
    val artSpinsWithShape: Boolean

    @Composable
    fun shape(cornerRadius: Dp): Shape

    /** Drawn over the art, e.g. a record's grooves. */
    @Composable
    fun Decoration(modifier: Modifier)
}

object CardCoverStyle : PlayerCoverStyle {
    override val spins = false
    override val artSpinsWithShape = true

    @Composable
    override fun shape(cornerRadius: Dp): Shape = RoundedCornerShape(cornerRadius)

    @Composable
    override fun Decoration(modifier: Modifier) = Unit
}

object VinylCoverStyle : PlayerCoverStyle {
    override val spins = true
    override val artSpinsWithShape = true

    @Composable
    override fun shape(cornerRadius: Dp): Shape = CircleShape

    @Composable
    override fun Decoration(modifier: Modifier) {
        val hairline = with(LocalDensity.current) { 1.dp.toPx() }
        Canvas(modifier) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Black record body drawn as a ring, so the art stays visible in the middle as the label.
            drawCircle(
                color = Color.Black.copy(alpha = 0.82f),
                radius = r * 0.67f,
                center = c,
                style = Stroke(width = r * 0.66f),
            )
            for (i in 1..7) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = r * (0.40f + i * 0.083f),
                    center = c,
                    style = Stroke(width = hairline),
                )
            }
            drawCircle(Color.Black, radius = r * 0.055f, center = c)
        }
    }
}

/**
 * Eight-lobed flower, hand-rolled: `MaterialShapes.Clover8Leaf` needs material3-expressive, which
 * this project does not carry. The radius wobbles as `0.80 + 0.20·cos(8θ)`, sampled finely enough
 * that the outline reads as curves at any cover size.
 */
private object CloverShape : Shape {
    private const val Steps = 240
    private const val Lobes = 8f
    private const val Mean = 0.80f
    private const val Swing = 0.20f

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy)
        val path = Path()
        for (i in 0..Steps) {
            val a = i.toFloat() / Steps * 2f * PI.toFloat()
            val k = Mean + Swing * cos(Lobes * a)
            val x = cx + cos(a) * radius * k
            val y = cy + sin(a) * radius * k
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

object CloverCoverStyle : PlayerCoverStyle {
    override val spins = true
    override val artSpinsWithShape = false

    @Composable
    override fun shape(cornerRadius: Dp): Shape = CloverShape

    @Composable
    override fun Decoration(modifier: Modifier) = Unit
}

fun PlayerArtworkStyle.coverStyle(): PlayerCoverStyle = when (this) {
    PlayerArtworkStyle.Card -> CardCoverStyle
    PlayerArtworkStyle.Vinyl -> VinylCoverStyle
    PlayerArtworkStyle.Clover -> CloverCoverStyle
}

private const val CoverRotationMillis = 20_000L

/**
 * Convx's `rememberCoverRotation`: the angle comes from the frame clock rather than a transition,
 * so the big cover and every preview sit at the same angle for the same track.
 */
@Composable
fun rememberCoverRotation(style: PlayerCoverStyle, isPlaying: Boolean): State<Float> {
    if (!isPlaying || !style.spins) return remember { mutableFloatStateOf(0f) }
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame ->
                angle.floatValue = (frame % CoverRotationMillis) / CoverRotationMillis.toFloat() * 360f
            }
        }
    }
    return angle
}

/**
 * Clips [content] to the style's shape and overlays its decoration. Styles whose art does not spin
 * counter-rotate the content inside the turning shape, so a clover's leaves turn while the sleeve
 * stays upright — exactly Convx's `artSpinsWithShape = false`.
 */
@Composable
fun PlayerCoverSurface(
    style: PlayerCoverStyle,
    rotation: Float,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = style.shape(cornerRadius)
    val turns = style.spins && rotation != 0f
    Box(
        modifier
            .graphicsLayer { rotationZ = if (turns) rotation else 0f }
            .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (turns && !style.artSpinsWithShape) -rotation else 0f },
            content = content,
        )
        style.Decoration(Modifier.fillMaxSize())
    }
}
