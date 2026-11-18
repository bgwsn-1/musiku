package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.LyricLine
import app.musiku.model.Lyrics
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.Skeleton
import app.musiku.ui.components.pressable
import app.musiku.ui.lyrics.flamingo.FlamingoLyrics
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
 * Synced lyrics, rendered by Convx's Flamingo view. The active line lights as a whole (Musiku
 * has line timings only), the lines around it dim in steps, and each line change pulls the list
 * to centre through the rubber-string scroll.
 */
@Composable
fun LyricsPanel(track: Track, onClose: () -> Unit) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val lyricsState = rememberLyrics(track, ps.durationSec)
    val lyrics = lyricsState.value
    val lines = lyrics?.takeIf { it.synced }?.lines.orEmpty()

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
                lines.isNotEmpty() -> FlamingoLyrics(
                    lines = lines,
                    positionMs = { app.player.currentPositionMs() },
                    onSeek = { app.player.seek(it) },
                    isPlaying = ps.playing,
                )
                !lyrics.plain.isNullOrBlank() -> Text(
                    lyrics.plain, color = Tok.Label1, fontSize = 18.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 120.dp),
                )
                else -> Text("Lirik tidak tersedia untuk lagu ini.", color = Tok.Label2, fontSize = 15.sp, modifier = Modifier.padding(top = 64.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
