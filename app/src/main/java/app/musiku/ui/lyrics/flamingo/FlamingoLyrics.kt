/** Adapted from Flamingo by Yos-X, GPL-3.0. See NOTICE. */
package app.musiku.ui.lyrics.flamingo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import app.musiku.model.LyricLine

private val Ws = Regex("\\S+")

/** One sung word of a line, in milliseconds. */
private class WordTiming(val text: String, val startMs: Float, val endMs: Float)

/** Share of a line's span the sweep uses; the rest is the breath before the next line. */
private const val LINE_SWEEP_SHARE = 0.94f

/** Convx's bloom mapping: longer held words raise the halo, capped at 0.55. */
private const val MAX_HELD_WORD_GLOW_EXPOSURE = 0.55f

/**
 * Musiku's adapter over the vendored Flamingo renderer.
 *
 * Convx gets word timings from TTML. Musiku only has the moment each line starts, so words are
 * spread across the gap to the next line weighted by their length: the sweep, the word swell and
 * the bright-pass bloom then run exactly as Convx's renderer runs them, with an estimated pace
 * inside the line instead of a per-syllable one. Lines with no text keep the countdown dots.
 */
@Composable
fun FlamingoLyrics(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.Default,
    fontSize: Float = 30f,
    lineSpacing: Float = 1.3f,
    isSynced: Boolean = true,
    blur: Boolean = false,
    /**
     * Flamingo's bright-pass bloom. Kept off by default: it is tuned for Convx's near-black
     * artwork field, and over a pale cover the 4dp + 16dp halos additively lift the whole screen.
     */
    glow: Boolean = false,
    isPlaying: Boolean = false,
) {
    val words = remember(lines) { syntheticWords(lines) }
    val entries = remember(lines, words) {
        lines.mapIndexed { index, line ->
            val at = line.timeSec * 1000f
            val timed = words[index]
            when {
                line.text.isBlank() -> listOf(at to "", at to "", at to "")
                else -> ArrayList<Pair<Float, String>>(timed.size + 2).apply {
                    // The renderer reads a pair as "sweep up to this time, then this text".
                    add(at to "")
                    timed.forEach { add(it.endMs to it.text) }
                    add(at to "")
                }
            }
        }
    }
    val ends = remember(lines) {
        lines.mapIndexed { index, line ->
            (lines.getOrNull(index + 1)?.timeSec ?: (line.timeSec + 5f)) * 1000f
        }
    }
    val sides = remember(lines) { List(lines.size) { false } }
    val backgrounds = remember(lines) { List(lines.size) { false } }

    val exposureTarget = remember { mutableFloatStateOf(0f) }
    val exposure by animateFloatAsState(exposureTarget.floatValue, tween(350), label = "lyric_exposure")
    val current = remember(lines, isSynced) { mutableIntStateOf(if (isSynced) primaryIndex(lines, positionMs()) else -1) }
    val playhead by rememberUpdatedState(positionMs)

    LaunchedEffect(lines, words, isSynced) {
        if (!isSynced) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val position = playhead()
            current.intValue = primaryIndex(lines, position)
            exposureTarget.floatValue = heldWordExposure(words.getOrNull(current.intValue), position)
        }
    }

    Box(modifier) {
        key(lines) {
            YosLyricView(
                lrcEntriesLambda = { entries },
                lineEndTimesLambda = { ends },
                isTtmlLyricsLambda = { false },
                liveTimeLambda = { positionMs().coerceIn(0, Int.MAX_VALUE.toLong()).toInt() },
                mediaEvent = remember(onSeek) {
                    object : YosMediaEvent {
                        override fun onSeek(position: Int) = onSeek(position / 1000f)
                    }
                },
                blurLambda = { blur },
                uiConfig = YosUIConfig(noLrcText = "Tidak ada lirik"),
                isSynced = isSynced,
                isPlaying = isPlaying,
                weightLambda = { false },
                otherSideForLines = sides,
                backgroundLines = backgrounds,
                glow = glow && isSynced,
                exposure = { exposure },
                currentLyricIndex = current,
                modifier = Modifier.fillMaxSize(),
                textStyle = MainTextStyle.copy(
                    fontFamily = fontFamily,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineSpacing).sp,
                ),
                onBackClick = {},
            )
        }
    }
}

/** Index of the line being sung, matching the panel's own lookup. */
private fun primaryIndex(lines: List<LyricLine>, positionMs: Long): Int {
    val seconds = positionMs / 1000f
    var index = -1
    for (i in lines.indices) {
        if (lines[i].timeSec <= seconds) index = i else break
    }
    return index
}

/**
 * Splits every line into words with estimated end times, weighted by word length. Words carry a
 * trailing space (except the last) because the renderer lays the line out from those strings.
 */
private fun syntheticWords(lines: List<LyricLine>): List<List<WordTiming>> = lines.mapIndexed { index, line ->
    val start = line.timeSec * 1000f
    val next = (lines.getOrNull(index + 1)?.timeSec ?: (line.timeSec + 5f)) * 1000f
    val matches = Ws.findAll(line.text).map { it.value }.toList()
    if (matches.isEmpty() || next <= start) return@mapIndexed emptyList()

    val span = (next - start) * LINE_SWEEP_SHARE
    val total = matches.sumOf { it.length }.coerceAtLeast(1)
    var consumed = 0
    matches.mapIndexed { wordIndex, word ->
        consumed += word.length
        val end = start + span * consumed / total
        val text = if (wordIndex < matches.lastIndex) "$word " else word
        WordTiming(text, start + span * (consumed - word.length) / total, end)
    }
}

private fun heldWordExposure(words: List<WordTiming>?, positionMs: Long): Float {
    val word = words?.firstOrNull { positionMs >= it.startMs && positionMs <= it.endMs } ?: return 0f
    val durationExposure = (((word.endMs - word.startMs) - 250f) / 1200f).coerceIn(0f, 1f)
    return durationExposure * MAX_HELD_WORD_GLOW_EXPOSURE
}
