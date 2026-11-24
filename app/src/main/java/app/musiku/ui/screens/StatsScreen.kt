package app.musiku.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.PlayEvent
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class StatPeriod(val label: String) {
    Week("7 Hari"),
    Month("30 Hari"),
    Year("Tahun Ini"),
    All("Semua"),
}

private data class SongStat(
    val track: Track,
    val count: Int,
    val totalMs: Long,
)

private data class ArtistStat(
    val name: String,
    val artistId: String?,
    val thumbs: String?,
    val count: Int,
    val totalMs: Long,
)

private data class DayStat(
    val label: String,
    val minutes: Float,
)

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val events by app.library.playEvents.collectAsState()
    var period by rememberSaveable { mutableStateOf(StatPeriod.Month) }

    val filtered = remember(events, period) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val cutoff = when (period) {
            StatPeriod.Week -> now - 7L * 24 * 60 * 60 * 1000
            StatPeriod.Month -> now - 30L * 24 * 60 * 60 * 1000
            StatPeriod.Year -> LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
            StatPeriod.All -> 0L
        }
        events.filter { it.playedAt >= cutoff }
    }

    val totalPlays = filtered.size
    val totalMs = remember(filtered) { filtered.sumOf { it.durationMs.coerceAtLeast(30_000L) } }
    val topSongs = remember(filtered) {
        filtered.groupBy { it.id }.values.map { list ->
            val first = list.first()
            SongStat(
                track = first.toTrack(),
                count = list.size,
                totalMs = list.sumOf { it.durationMs.coerceAtLeast(30_000L) },
            )
        }.sortedWith(compareByDescending<SongStat> { it.count }.thenByDescending { it.totalMs }).take(15)
    }
    val topArtists = remember(filtered) {
        filtered.filter { !it.artist.isNullOrBlank() }
            .groupBy { it.artist!!.trim().lowercase(Locale.ROOT) }
            .values
            .map { list ->
                val first = list.first()
                ArtistStat(
                    name = first.artist!!.trim(),
                    artistId = list.firstNotNullOfOrNull { it.artistId },
                    thumbs = list.firstNotNullOfOrNull { it.thumbnail },
                    count = list.size,
                    totalMs = list.sumOf { it.durationMs.coerceAtLeast(30_000L) },
                )
            }
            .sortedWith(compareByDescending<ArtistStat> { it.count }.thenByDescending { it.totalMs })
            .take(12)
    }
    val dailyBars = remember(events) { buildLast7Days(events) }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = DockInset + 24.dp),
        ) {
            item(key = "title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 16.dp, top = 52.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Musiku Replay",
                        color = Tok.Label1,
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                    if (totalPlays > 0) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Tok.Fill1)
                                .pressable {
                                    val topSong = topSongs.firstOrNull()?.track?.let { "${it.title} — ${it.artist.orEmpty()}" }
                                    val topArt = topArtists.firstOrNull()?.name
                                    val text = buildString {
                                        appendLine("🎵 Musiku Replay (${period.label})")
                                        appendLine("⏱️ Total dengar: ${formatDurationLabel(totalMs)}")
                                        appendLine("▶️ Total putar: $totalPlays lagu")
                                        if (topSong != null) appendLine("🔥 Lagu teratas: $topSong")
                                        if (topArt != null) appendLine("🎤 Artis teratas: $topArt")
                                    }
                                    ctx.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                            "Bagikan Musiku Replay",
                                        ),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MIcons.Share, "Bagikan Replay", tint = Tok.Label1, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item(key = "periods") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatPeriod.entries.forEach { p ->
                        val selected = p == period
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) Tok.Accent else Tok.Fill1)
                                .border(1.dp, if (selected) Tok.Accent else Tok.Fill2, CircleShape)
                                .pressable { period = p }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                p.label,
                                color = if (selected) Color.White else Tok.Label1,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            item(key = "hero") {
                Spacer(Modifier.height(16.dp))
                ReplayHeroCard(
                    totalMs = totalMs,
                    totalPlays = totalPlays,
                    artistCount = topArtists.size,
                    periodLabel = period.label,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (totalPlays == 0) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Belum ada statistik di periode ini", color = Tok.Label1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text("Putar beberapa lagu minimal 30 detik, nanti ringkasan Replay muncul di sini.", color = Tok.Label2, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                item(key = "weekly-chart") {
                    Spacer(Modifier.height(22.dp))
                    SectionTitle("Aktivitas 7 Hari Terakhir")
                    WeeklyBarChart(dailyBars, Modifier.padding(horizontal = 16.dp))
                }

                if (topArtists.isNotEmpty()) {
                    item(key = "top-artists") {
                        Spacer(Modifier.height(24.dp))
                        SectionTitle("Artis Teratas")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(topArtists, key = { it.name }) { a ->
                                Column(
                                    modifier = Modifier
                                        .width(92.dp)
                                        .pressable {
                                            a.artistId?.let { ui.navigate(Routes.artist(it)) }
                                                ?: app.player.say("${a.name} · ${a.count}× diputar")
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Artwork(
                                        a.thumbs,
                                        Modifier.size(84.dp).clip(CircleShape).background(Tok.Fill1),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(a.name, color = Tok.Label1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                    Text("${a.count}× putar", color = Tok.Label3, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (topSongs.isNotEmpty()) {
                    item(key = "top-songs-title") {
                        Spacer(Modifier.height(24.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Lagu Teratas", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Putar Semua",
                                color = Tok.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(CircleShape).pressable {
                                    app.player.playTracks(topSongs.map { it.track }, 0)
                                }.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    itemsIndexed(topSongs, key = { _, s -> s.track.videoId }) { index, stat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { app.player.playTracks(topSongs.map { it.track }, index) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                color = if (index < 3) Tok.Accent else Tok.Label3,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(24.dp),
                            )
                            Artwork(stat.track.thumbs, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Tok.Fill1))
                            Column(Modifier.weight(1f)) {
                                Text(stat.track.title, color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stat.track.artist.orEmpty(), color = Tok.Label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${stat.count}×", color = Tok.Label1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${(stat.totalMs / 60_000L).coerceAtLeast(1)} mnt", color = Tok.Label3, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Tok.Label1,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ReplayHeroCard(
    totalMs: Long,
    totalPlays: Int,
    artistCount: Int,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val accent = Tok.Accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), Tok.Surface),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column {
            Text("WAKTU MENDENGARKAN · ${periodLabel.uppercase()}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            Text(formatDurationLabel(totalMs), color = Tok.Label1, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("$totalPlays", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Total Putar", color = Tok.Label2, fontSize = 12.sp)
                }
                Column {
                    Text("$artistCount", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Artis Berbeda", color = Tok.Label2, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(days: List<DayStat>, modifier: Modifier = Modifier) {
    val maxMin = (days.maxOfOrNull { it.minutes } ?: 1f).coerceAtLeast(1f)
    val accent = Tok.Accent
    val track = Tok.Fill2

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Tok.Surface)
            .padding(16.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val count = days.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.44f).coerceIn(10.dp.toPx(), 24.dp.toPx())
            days.forEachIndexed { idx, day ->
                val x = idx * slotW + (slotW - barW) / 2f
                val r = CornerRadius(barW / 2f, barW / 2f)
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = r,
                )
                val h = ((day.minutes / maxMin) * size.height).coerceAtLeast(if (day.minutes > 0f) 6.dp.toPx() else 0f)
                if (h > 0f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = r,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                Text(
                    d.label,
                    color = Tok.Label3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun buildLast7Days(events: List<PlayEvent>): List<DayStat> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val byDate = events.groupBy {
        Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate()
    }
    val locale = Locale("id", "ID")
    return (6 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        val mins = (byDate[date]?.sumOf { it.durationMs.coerceAtLeast(30_000L) } ?: 0L) / 60_000f
        val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3)
        DayStat(label, mins)
    }
}

private fun formatDurationLabel(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return when {
        hours > 0 && mins > 0 -> "$hours jam $mins mnt"
        hours > 0 -> "$hours jam"
        else -> "$mins menit"
    }
}
