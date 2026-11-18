package app.musiku.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.delay

/**
 * Port of the web build's `TextRoll`: the string is split into letters, and each letter flips up
 * from its own bottom edge, one after another.
 *
 * The web version stacks two layers per letter (one rotating 0°→90° at a 25% origin, one rotating
 * 90°→0° at a 100% origin) inside a `perspective` wrapper. Only the second layer is ever seen —
 * the first one starts flat and turns away behind it — so this port draws the visible half: a
 * single glyph rotating in from edge-on, with the same stagger and easing.
 *
 * The rotation lives in [graphicsLayer], so the flip never recomposes; only the layer changes.
 *
 * @param restartKey change it to replay the roll from the start.
 */
@Composable
fun TextRoll(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    durationMillis: Int = 500,
    staggerMillis: Int = 40,
    initialDelayMillis: Int = 200,
    restartKey: Any? = Unit,
) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { index, ch ->
            val flip = remember { Animatable(90f) }
            LaunchedEffect(restartKey, index) {
                flip.snapTo(90f)
                delay((initialDelayMillis + index * staggerMillis).toLong())
                flip.animateTo(0f, tween(durationMillis, easing = LinearOutSlowInEasing))
            }
            Text(
                // A plain space collapses at the edges of a Text; NBSP keeps the gap.
                text = if (ch == ' ') "\u00A0" else ch.toString(),
                style = style,
                color = color,
                maxLines = 1,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        rotationX = flip.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        // The web uses perspective(10000px) — a weak, almost orthographic camera.
                        cameraDistance = 24f * density
                    },
            )
        }
    }
}

/**
 * The app's loading mark: "Musiku" rolls in, then re-rolls every few seconds so a slow feed never
 * looks like a frozen screen.
 *
 * @param fontSize size of the letters.
 * @param restartKey change it to replay from the first letter.
 */
@Composable
fun MusikuLoading(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 36.sp,
    restartKey: Any? = Unit,
) {
    var cycle by remember(restartKey) { mutableIntStateOf(0) }

    LaunchedEffect(restartKey, cycle) {
        delay(2_600)
        cycle++
    }

    TextRoll(
        text = "Musiku",
        modifier = modifier,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        // Half opacity on purpose: the mark is a whisper over the page, not a page title.
        color = Tok.Label1.copy(alpha = 0.5f),
        restartKey = restartKey to cycle,
    )
}
