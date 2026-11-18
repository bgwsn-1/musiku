package app.musiku.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.theme.Tok

/** Tap target with the web build's `active:scale-95` press feedback. */
@Composable
fun Tappable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressScale: Float = 0.96f,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressScale else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val base = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics { if (description != null) contentDescription = description }
    Box(
        if (onLongClick != null) {
            base.combinedClickableCompat(source, onClick, onLongClick)
        } else {
            base.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        },
    ) { content() }
}

/**
 * Modifier form of [Tappable] for surfaces that need their own layout scope (a Box with
 * `contentAlignment`, a Row that must keep `weight`, ...). Same press feedback.
 */
@Composable
fun Modifier.pressable(
    pressScale: Float = 0.96f,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressScale else 1f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val base = this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics { if (description != null) contentDescription = description }
    return if (onLongClick != null) base.combinedClickableCompat(source, onClick, onLongClick)
    else base.clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
}

/** [Modifier.pressable] with the web's stronger `active:scale-90`. */
@Composable
fun Modifier.pressable90(onClick: () -> Unit, description: String? = null): Modifier =
    pressable(onClick = onClick, pressScale = 0.9f, description = description)

/** Alias kept for call sites that read better as a verb pair (`clickable`). */
@Composable
fun Modifier.tappable(onClick: () -> Unit, description: String? = null): Modifier =
    pressable(onClick = onClick, description = description)

@Composable
fun Eq(on: Boolean, color: Color = Color.White, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            val h by t.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(Modifier.width(3.dp).fillMaxHeight(if (on) h else 0.3f).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Tok.Label1,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        modifier = modifier,
    )
}

/** Artwork-first card. Text sits under the art without a box. */
@Composable
fun GridCard(
    title: String,
    subtitle: String?,
    img: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 148.dp,
) {
    Tappable(onClick, modifier.width(size), pressScale = 0.97f, description = title) {
        Column {
            Artwork(
                img,
                Modifier
                    .size(size)
                    .shadow(8.dp, if (round) CircleShape else RoundedCornerShape(14.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(if (round) CircleShape else RoundedCornerShape(14.dp))
                    .background(Tok.Fill1),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                title, color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (round) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, color = Tok.Label2, fontSize = 13.sp, lineHeight = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (round) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Shelf(title: String, modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier.padding(bottom = 40.dp)) {
        if (title.isNotBlank()) SectionTitle(title, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Tok.Label1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(body, color = Tok.Label2, fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.graphicsLayer { alpha = a }.clip(RoundedCornerShape(12.dp)).background(Tok.Fill1))
}

/** Pill-shaped segmented control (Library / Search filters). */
@Composable
fun Segmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(CircleShape).background(Tok.Fill1).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (on) Tok.Fill2 else Color.Transparent)
                    .pressable { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l, color = if (on) Tok.Label1 else Tok.Label2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
