package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.LyricLine
import app.musiku.model.Lyrics
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.Skeleton
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** null = still loading */
@Composable
fun rememberLyrics(track: Track?, durationSec: Float): State<Lyrics?> {
    val app = LocalApp.current
    return produceState<Lyrics?>(null, track?.videoId) {
        value = null
        if (track != null) value = runCatching { app.lyrics.load(track, durationSec.takeIf { it > 0f }) }.getOrDefault(Lyrics())
    }
}

fun activeLyricIndex(lines: List<LyricLine>, position: Float): Int {
    var idx = -1
    for (i in lines.indices) if (lines[i].timeSec <= position) idx = i else break
    return idx
}

private val Ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Apple-Music-style synced lyrics. When the active line changes, the list jumps to the new
 * scroll target at once and every line then eases from its old spot to the new one, each
 * starting a beat after the one above it, so the lyrics ripple upward (LyricsPanel.tsx).
 */
@Composable
fun LyricsPanel(track: Track, onClose: () -> Unit) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val position by app.player.position.collectAsState()
    val lyricsState = rememberLyrics(track, ps.durationSec)
    val lyrics = lyricsState.value
    val lines = lyrics?.takeIf { it.synced }?.lines.orEmpty()
    val active = remember(lines, position) { activeLyricIndex(lines, position) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(track.thumbs, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        lyrics == null -> "Mencari lirik…"
                        lyrics.source == "lrclib" && lyrics.synced -> "Lirik sinkron"
                        lyrics.source == "youtube-music" -> "Lirik · YouTube Music"
                        lyrics.hasText -> "Lirik"
                        else -> "Tidak ada lirik"
                    },
                    color = Tok.Label2, fontSize = 13.sp,
                )
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Tok.Fill1).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(MIcons.Close, "Tutup lirik", tint = Tok.Label1, modifier = Modifier.size(18.dp))
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.06f to Color.Black, 0.9f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            when {
                lyrics == null -> Column(Modifier.padding(20.dp).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(32.dp)); Skeleton(Modifier.fillMaxWidth(0.6f).height(32.dp)); Skeleton(Modifier.fillMaxWidth(0.7f).height(32.dp))
                }
                lines.isNotEmpty() -> SyncedLines(lines, active, position) { app.player.seek(it) }
                !lyrics.plain.isNullOrBlank() -> Text(
                    lyrics.plain, color = Tok.Label1, fontSize = 18.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 120.dp),
                )
                else -> Text("Lirik tidak tersedia untuk lagu ini.", color = Tok.Label2, fontSize = 15.sp, modifier = Modifier.padding(top = 64.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SyncedLines(lines: List<LyricLine>, active: Int, position: Float, onSeek: (Float) -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val shifts = remember(lines) { List(lines.size) { Animatable(0f) } }
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    var programmatic by remember { mutableStateOf(false) }
    var lastIdx by remember(lines) { mutableIntStateOf(-2) }
    var browsing by remember { mutableStateOf(false) }

    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress && !programmatic) {
            lastUserScroll = System.currentTimeMillis()
            browsing = true
        } else if (!list.isScrollInProgress && browsing) {
            kotlinx.coroutines.delay(3500)
            if (System.currentTimeMillis() - lastUserScroll >= 3400) browsing = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }

        LaunchedEffect(active) {
            val prev = lastIdx
            lastIdx = active
            if (active < 0) return@LaunchedEffect
            if (System.currentTimeMillis() - lastUserScroll < 3500) return@LaunchedEffect
            val target = (viewportPx * 0.32f).toInt()
            val before = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == active }?.offset
            programmatic = true
            if (before == null || prev < 0) {
                list.scrollToItem(active, -target)
            } else {
                val delta = (before - target).toFloat()
                if (abs(delta) < 1f) { programmatic = false; return@LaunchedEffect }
                val lo = max(0, active - 6)
                val hi = min(lines.lastIndex, active + 14)
                for (i in lo..hi) shifts[i].snapTo(delta)
                list.scrollToItem(active, -target)
                for (i in lo..hi) {
                    val after = max(0, i - active)
                    scope.launch { shifts[i].animateTo(0f, tween(680, delayMillis = min(after, 8) * 48, easing = Ease)) }
                }
            }
            programmatic = false
        }

        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = maxHeight * 0.10f, bottom = maxHeight * 0.45f),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { i, l -> "$i-${l.timeSec}" }) { i, l ->
                val isActive = i == active
                val dist = if (active < 0) 3 else min(3, abs(i - active))
                val dur = max(0.6f, (lines.getOrNull(i + 1)?.timeSec ?: (l.timeSec + 5f)) - l.timeSec)
                val prog = if (isActive) (((position - l.timeSec) / dur).coerceIn(0f, 1f) / 0.85f).coerceIn(0f, 1f) else 0f
                val alpha by animateFloatAsState(
                    when { isActive -> 1f; browsing -> 0.78f; else -> listOf(0.52f, 0.4f, 0.3f)[dist - 1] }, tween(600), label = "a",
                )
                val blurPx by animateFloatAsState(if (isActive || browsing) 0f else listOf(0.5f, 1.2f, 2f)[dist - 1], tween(600), label = "b")
                val scale by animateFloatAsState(if (isActive) 1f else 0.92f, tween(700, easing = Ease), label = "s")

                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { translationY = shifts.getOrNull(i)?.value ?: 0f }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSeek(l.timeSec) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    val mod = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha; transformOrigin = TransformOrigin(0f, 0.5f) }
                        .then(if (blurPx > 0.05f) Modifier.blur(blurPx.dp) else Modifier)
                    if (l.text.isEmpty()) {
                        Row(mod.height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (d in 0 until 3) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = if (isActive) (if (prog * 3 > d) 1f else 0.35f) else 0.6f)))
                        }
                    } else if (isActive) {
                        val words = l.text.split(Regex("\\s+"))
                        val total = words.sumOf { it.length + 1 }.coerceAtLeast(1)
                        var acc = 0
                        Text(
                            buildAnnotatedString {
                                words.forEachIndexed { k, w ->
                                    val start = acc.toFloat() / total
                                    acc += w.length + 1
                                    withStyle(SpanStyle(color = Color.White.copy(alpha = if (prog >= start) 1f else 0.42f))) {
                                        append(w); if (k < words.lastIndex) append(" ")
                                    }
                                }
                            },
                            fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = mod,
                        )
                    } else {
                        Text(l.text, color = Tok.Label1, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = mod)
                    }
                }
            }
        }
    }
}
