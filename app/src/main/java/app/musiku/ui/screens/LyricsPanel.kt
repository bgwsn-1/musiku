package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.LyricLine
import app.musiku.model.Lyrics
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.pressable
import app.musiku.ui.lyrics.mochy.MochyLyricsLoadingDots
import app.musiku.ui.lyrics.mochy.MochyLyricsView
import app.musiku.ui.theme.Tok

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

/**
 * Synced lyrics rendered with Mochy Music's word-by-word glow, spring lift,
 * staggered recenter scroll, and instrumental interlude dots.
 */
@Composable
fun LyricsPanel(track: Track, onClose: () -> Unit) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val lyricsState = rememberLyrics(track, ps.durationSec)
    val lyrics = lyricsState.value
    val entries = lyrics?.takeIf { it.synced }?.entries.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(track.thumbs, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Tok.Label1,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        lyrics == null -> "Mencari lirik…"
                        lyrics.wordSynced && lyrics.source == "betterlyrics" -> "Lirik sinkron kata · BetterLyrics"
                        lyrics.wordSynced && lyrics.source == "lrcred" -> "Lirik sinkron kata · lrc.red"
                        lyrics.source == "lrclib" && lyrics.synced -> "Lirik sinkron · LRCLib"
                        lyrics.source == "youtube-music" -> "Lirik · YouTube Music"
                        lyrics.hasText -> "Lirik"
                        else -> "Tidak ada lirik"
                    },
                    color = Tok.Label2,
                    fontSize = 13.sp,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Tok.Fill1).pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MIcons.Close, "Tutup lirik", tint = Tok.Label1, modifier = Modifier.size(18.dp))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                lyrics == null -> MochyLyricsLoadingDots(Modifier.align(Alignment.Center))
                entries.isNotEmpty() -> MochyLyricsView(
                    lines = entries,
                    positionMs = { app.player.currentPositionMs() },
                    onSeek = { app.player.seek(it / 1000f) },
                    isPlaying = ps.playing,
                    modifier = Modifier.fillMaxSize(),
                )
                !lyrics.plain.isNullOrBlank() -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    Text(
                        lyrics.plain,
                        color = Tok.Label1.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tidak ada lirik untuk lagu ini.", color = Tok.Label2, fontSize = 16.sp)
                }
            }
        }
    }
}
