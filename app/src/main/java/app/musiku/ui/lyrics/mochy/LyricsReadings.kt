package app.musiku.ui.lyrics.mochy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import app.musiku.lyrics.LyricsEntry
import app.musiku.lyrics.RubyToken

internal val LyricsHeaderArtX = 28.dp

internal data class ShownLyric(
    val entry: LyricsEntry,
    val subLine: String? = null,
    val ruby: List<RubyToken>? = null,
    val translation: String? = null,
)

@Composable
internal fun rememberShownLyric(entry: LyricsEntry): ShownLyric =
    remember(entry) { ShownLyric(entry) }
