package app.musiku.ui.lyrics.mochy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.musiku.constants.LyricsMotionStyle
import app.musiku.lyrics.LyricsEntry
import app.musiku.lyrics.LyricsUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ActiveLineViewportFraction = 0.25f
private val MinPastLineVisibleHeight = 72.dp
private const val RecenterDurationMs = 950
internal const val LyricsRecenterDurationMs = RecenterDurationMs
internal val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
internal val LyricsSmoothRecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)
internal const val NoInterlude = Int.MIN_VALUE
private const val IdleRecenterDelayMs = 3_500L
private const val TopFadeFraction = 0.10f
private const val BottomFadeStart = 0.62f

internal fun recenterEasingFor(style: LyricsMotionStyle) =
    if (style.risesWords) LyricsSmoothRecenterEasing else LyricsRecenterEasing

private class LyricsListPadding : PaddingValues {
    var heightPx = 0
    var density = 1f
    private fun listHeight() = (heightPx / density).dp
    override fun calculateTopPadding() = maxOf(listHeight() * ActiveLineViewportFraction, MinPastLineVisibleHeight)
    override fun calculateBottomPadding() = listHeight() * 0.55f
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

internal sealed interface LyricRow {
    val key: Long
    data class Line(val index: Int, val entry: LyricsEntry) : LyricRow {
        override val key get() = index.toLong()
    }
    data class Interlude(val afterIndex: Int, val startMs: Long, val endMs: Long) : LyricRow {
        override val key get() = -1_000_000L - afterIndex
    }
}

internal fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)
    if (lines.first().time >= MinInterludeGapMs) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }
    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = LyricsUtils.lineEndMs(lines, index)
        if (next.time - gapStart >= MinInterludeGapMs) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

internal fun lyricAnchorIndex(lines: List<LyricsEntry>, positionMs: Long): Int {
    var i = LyricsUtils.findCurrentLineIndex(lines, positionMs)
    if (i <= 0 || i >= lines.size) return i
    while (i > 0) {
        val previous = i - 1
        val endMs = lines[previous].words
            ?.lastOrNull()
            ?.let { (it.endTime * 1000).toLong() }
            ?: return i
        if (endMs <= positionMs) return i
        i = previous
    }
    return i
}

internal fun activeInterludeOf(rows: List<LyricRow>, positionMs: Long): Int =
    rows.firstOrNull {
        it is LyricRow.Interlude && positionMs >= it.startMs &&
            positionMs + LyricsUtils.LineLeadInMs < it.endMs
    }?.let { (it as LyricRow.Interlude).afterIndex } ?: NoInterlude

internal fun usesAgentAlignment(lines: List<LyricsEntry>): Boolean =
    lines.filter { !it.isBackground }
        .map { it.agent ?: "v1" }
        .distinct()
        .size > 1

internal fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean, mirrored: Boolean = false): LyricsAlignment {
    val side = when {
        !useAgentAlignment -> LyricsAlignment.START
        entry.isBackground -> LyricsAlignment.CENTER
        entry.agent == "v2" -> LyricsAlignment.END
        entry.agent == "v1000" -> LyricsAlignment.CENTER
        else -> LyricsAlignment.START
    }
    if (!mirrored) return side
    return when (side) {
        LyricsAlignment.START -> LyricsAlignment.END
        LyricsAlignment.END -> LyricsAlignment.START
        LyricsAlignment.CENTER -> LyricsAlignment.CENTER
    }
}

@Composable
internal fun rememberLyricsMirrored(lines: List<LyricsEntry>): Boolean {
    val appRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lyricsRtl = remember(lines) { lyricsReadRtl(lines) }
    return lyricsRtl != appRtl
}

internal fun lyricsReadRtl(lines: List<LyricsEntry>): Boolean {
    var rtl = 0
    var ltr = 0
    for (line in lines) {
        for (ch in line.text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> { rtl++; break }
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> { ltr++; break }
            }
        }
    }
    return rtl > ltr
}

internal data class LineLook(val state: LyricsLineState, val blurStage: Int)

internal fun lineLook(index: Int, current: Int, anchor: Int, unsynced: Boolean, interlude: Boolean): LineLook {
    if (unsynced) return LineLook(LyricsLineState.ACTIVE, 0)
    val distance = when {
        current < 0 -> index - current
        index < anchor -> index - anchor
        index > current -> index - current
        else -> 0
    }
    val state = when {
        current >= 0 && index in anchor..current ->
            if (interlude) LyricsLineState.PAST else LyricsLineState.ACTIVE
        index == current + 1 -> LyricsLineState.UPCOMING
        index < current -> LyricsLineState.PAST
        else -> LyricsLineState.DEFAULT
    }
    val blurStage = when (abs(distance)) {
        0 -> 0
        1 -> 1
        else -> 2
    }
    return LineLook(state, blurStage)
}

@Composable
fun MochyLyricsView(
    lines: List<LyricsEntry>,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    accentColor: Color = Color.White,
    motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val rows = remember(lines) { buildLyricRows(lines) }
    val stagger = remember(lines) { LyricsStagger(rows.size) }
    val rowOfLine = remember(lines, rows) {
        IntArray(lines.size).also { out ->
            for (r in rows.indices) {
                val row = rows[r]
                if (row is LyricRow.Line) out[row.index] = r
            }
        }
    }

    var smoothPosMs by remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(isPlaying, lines) {
        smoothPosMs = positionMs()
        if (!isPlaying) return@LaunchedEffect
        var lastAnchor = positionMs()
        var lastFrameMs = withFrameMillis { it }
        while (true) {
            val frameMs = withFrameMillis { it }
            val actual = positionMs()
            if (abs(actual - lastAnchor) > 150L) {
                smoothPosMs = actual
                lastAnchor = actual
            } else {
                smoothPosMs += (frameMs - lastFrameMs).coerceIn(0L, 64L)
                if (abs(smoothPosMs - actual) > 180L) {
                    smoothPosMs = actual
                    lastAnchor = actual
                }
            }
            lastFrameMs = frameMs
        }
    }

    val currentIndex by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, smoothPosMs) }
    }
    val anchorIndex by remember(lines) {
        derivedStateOf { lyricAnchorIndex(lines, smoothPosMs) }
    }
    val activeInterlude by remember(lines, rows) {
        derivedStateOf { activeInterludeOf(rows, smoothPosMs) }
    }

    var followPlayback by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            followPlayback = false
        } else if (!followPlayback) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    LaunchedEffect(followPlayback, lines, motionStyle) {
        if (!followPlayback) return@LaunchedEffect
        snapshotFlow { anchorIndex }.collectLatest { target ->
            if (target !in lines.indices) return@collectLatest
            val targetRow = rowOfLine[target]
            val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val minOffsetPx = with(density) { MinPastLineVisibleHeight.toPx() }
            val restingOffset = maxOf(viewportHeight * ActiveLineViewportFraction, minOffsetPx).roundToInt()

            val layoutInfo = listState.layoutInfo
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
            if (visible != null) {
                val current = visible.offset - layoutInfo.viewportStartOffset
                val collapsing = layoutInfo.visibleItemsInfo
                    .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                    .sumOf { it.size }
                val distance = (current - restingOffset - collapsing).toFloat()
                stagger.anchorRow = targetRow
                listState.scroll {
                    var previous = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = distance,
                        animationSpec = tween(durationMillis = RecenterDurationMs, easing = recenterEasingFor(motionStyle)),
                    ) { value, _ ->
                        val consumed = scrollBy(value - previous)
                        previous += consumed
                        if (motionStyle.risesWords && consumed != 0f) {
                            for (item in listState.layoutInfo.visibleItemsInfo) {
                                if (item.index > targetRow) stagger.holdBack(item.index, consumed)
                            }
                        }
                    }
                }
            } else {
                val firstVisible = listState.firstVisibleItemIndex
                if (abs(targetRow - firstVisible) > 8) {
                    val stagingRow = (targetRow + if (targetRow > firstVisible) -4 else 4)
                        .coerceIn(0, (rows.size - 1).coerceAtLeast(0))
                    listState.scrollToItem(stagingRow, 0)
                }
                listState.animateScrollToItem(targetRow, 0)
            }
        }
    }

    val useAgentAlignment = remember(lines) { usesAgentAlignment(lines) }
    val mirrored = rememberLyricsMirrored(lines)
    val listPadding = remember { LyricsListPadding() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPlaced {
                listPadding.heightPx = it.size.height
                listPadding.density = density.density
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            userScrollEnabled = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    val fade = Brush.verticalGradient(
                        0f to Color.Transparent,
                        TopFadeFraction to Color.Black,
                        BottomFadeStart to Color.Black,
                        1f to Color.Transparent,
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    }
                },
        ) {
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
                contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
            ) { listIndex, row ->
                when (row) {
                    is LyricRow.Interlude -> {
                        LyricsInterludeDots(
                            modifier = Modifier.graphicsLayer {
                                translationY = stagger.lagOf(listIndex)
                            },
                            startMs = row.startMs,
                            endMs = row.endMs,
                            positionProvider = { smoothPosMs },
                            bloom = motionStyle.shaded,
                            highBloom = true,
                            visible = activeInterlude == row.afterIndex,
                            collapseEasing = recenterEasingFor(motionStyle),
                            alignment = lines.getOrNull(row.afterIndex + 1)
                                ?.let { alignmentFor(it, useAgentAlignment, mirrored) }
                                ?: LyricsAlignment.CENTER,
                        )
                    }
                    is LyricRow.Line -> {
                        val index = row.index
                        val entry = row.entry
                        val look by remember(index, lines) {
                            derivedStateOf {
                                lineLook(index, currentIndex, anchorIndex, unsynced = false, interlude = activeInterlude != NoInterlude)
                            }
                        }
                        val shown = rememberShownLyric(entry)
                        val rowIndex = rowOfLine[index]
                        val lineAlignment = alignmentFor(entry, useAgentAlignment, mirrored)
                        LyricsGlowLine(
                            modifier = Modifier.graphicsLayer {
                                translationY = stagger.lagOf(rowIndex)
                            },
                            entry = shown.entry,
                            state = look.state,
                            blurStage = look.blurStage,
                            positionProvider = { smoothPosMs },
                            accentColor = accentColor,
                            alignment = lineAlignment,
                            waveEnabled = motionStyle.shaded,
                            motionStyle = motionStyle,
                            highBloom = true,
                            onClick = {
                                onSeek(entry.time)
                                followPlayback = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MochyLyricsLoadingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "lyricsLoading")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 900,
                        delayMillis = index * 150,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "lyricsLoadingDot$index",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * phase
                        val s = 0.75f + 0.25f * phase
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
