package app.musiku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.SfIcon
import app.musiku.ui.components.TrackRow
import app.musiku.ui.components.pressable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.SfDetailIcons
import app.musiku.ui.theme.SfNavIcons
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.launch

private val RadioCardMinWidth = 168.dp
private val RadioGutter = 16.dp

@Composable
fun RadioScreen() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val recent by app.library.recent.collectAsState()
    val scope = rememberCoroutineScope()

    // Seed cards from recently played tracks, one per artist, like a radio station history.
    val seeds = remember(recent) {
        recent.distinctBy { it.artistId ?: it.videoId }.take(12)
    }

    fun playRadio(seed: Track) {
        scope.launch {
            val related = runCatching { app.yt.related(seed.videoId) }.getOrDefault(emptyList())
            val queue = (listOf(seed) + related).distinctBy { it.videoId }
            app.player.playTracks(queue, 0)
        }
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = RadioCardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RadioGutter,
                top = 116.dp,
                end = RadioGutter,
                bottom = DockInset + 20.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "radio_title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Radio",
                    color = Tok.Label1,
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (seeds.isEmpty()) {
                item(key = "radio_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 40.dp),
                    ) {
                        SfIcon(SfNavIcons.DotRadiowavesLeftAndRight, 34.dp, Tok.Label2)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Play a few songs and your recent tracks will appear here.",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = Tok.Label2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Tok.Fill1)
                                .pressable { ui.navigate(Routes.Search) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Search", color = Tok.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                item(key = "radio_recent_title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Recently played",
                        color = Tok.Label1,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.26).sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                }
                items(seeds, key = { "radio_seed_${it.videoId}" }) { seed ->
                    RadioStationCard(
                        seed = seed,
                        onClick = {
                            ui.navigate(
                                Routes.radioStation(
                                    seedId = seed.videoId,
                                    title = seed.title,
                                    artist = seed.artist.orEmpty(),
                                    thumbnail = seed.thumbs.orEmpty(),
                                ),
                            )
                        },
                        onPlay = { playRadio(seed) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioStationCard(seed: Track, onClick: () -> Unit, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val artist = seed.artist?.ifBlank { null } ?: seed.title
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(Tok.Fill1)
            .clickable(onClick = onClick),
    ) {
        Artwork(seed.thumbs, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.08f),
                        0.45f to Color.Black.copy(alpha = 0.28f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(SfNavIcons.DotRadiowavesLeftAndRight, 17.dp, Color.White)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .pressable { onPlay() },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(SfDetailIcons.PlayFill, 16.dp, Color.White)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = "Radio for $artist",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = seed.title,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.4.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RadioStationScreen(
    seedId: String,
    seedTitle: String,
    seedArtist: String,
    seedThumbnail: String,
    onBack: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val load by rememberLoad(seedId) { app.yt.related(seedId).distinctBy { it.videoId }.take(24) }
    val title = seedTitle.ifBlank { "Radio" }
    val stationTitle = "Radio for $title"
    val seedTrack = remember(seedId, seedTitle, seedArtist, seedThumbnail) {
        Track(
            videoId = seedId,
            title = seedTitle,
            artist = seedArtist.ifBlank { null },
            thumbs = seedThumbnail.ifBlank { null },
        )
    }

    fun playRadio(seed: Track = seedTrack, queue: List<Track>? = null) {
        scope.launch {
            val tracks = queue ?: runCatching { app.yt.related(seed.videoId) }.getOrDefault(emptyList())
            val list = (listOf(seed) + tracks).distinctBy { it.videoId }
            val idx = list.indexOfFirst { it.videoId == seed.videoId }.coerceAtLeast(0)
            app.player.playTracks(list, idx)
        }
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = DockInset + 20.dp),
        ) {
            item(key = "radio_station_title") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                ) {
                    Text(
                        text = stationTitle,
                        color = Tok.Label1,
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item(key = "radio_station_header") {
                RadioStationHeader(
                    title = title,
                    artist = seedArtist,
                    thumbnail = seedThumbnail,
                    onPlay = { playRadio() },
                )
            }
            item(key = "radio_station_tracks_title") {
                Text(
                    text = "Up next",
                    color = Tok.Label1,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.26).sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                )
            }
            when (val l = load) {
                is Load.Loading -> item(key = "radio_station_loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tok.Accent)
                    }
                }

                is Load.Err -> item(key = "radio_station_error") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        Text(
                            "Could not load this station. Check your connection and try again.",
                            color = Tok.Label2,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Tok.Fill1)
                                .pressable { /* rememberLoad keyed by seedId; simple retry via recomposition not wired */ }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text("Retry", color = Tok.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is Load.Ok -> {
                    val tracks = l.value
                    if (tracks.isEmpty()) {
                        item(key = "radio_station_empty") {
                            Text(
                                "Could not load this station. Check your connection and try again.",
                                color = Tok.Label2,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            )
                        }
                    } else {
                        items(tracks, key = { "radio_track_${it.videoId}" }) { song ->
                            val queue = (listOf(seedTrack) + tracks).distinctBy { it.videoId }
                            val idx = queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
                            TrackRow(track = song, queue = queue, index = idx)
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

@Composable
private fun RadioStationHeader(title: String, artist: String, thumbnail: String, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Tok.Fill1)
            .clickable(onClick = onPlay)
            .padding(14.dp),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Tok.Fill2)) {
            if (thumbnail.isNotBlank()) Artwork(thumbnail, Modifier.size(88.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Song radio", color = Tok.Label2, fontSize = 13.sp)
            Text(
                title,
                color = Tok.Label1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Text(
                    artist,
                    color = Tok.Label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Tok.Fill2)
                .pressable { onPlay() },
            contentAlignment = Alignment.Center,
        ) {
            SfIcon(SfDetailIcons.PlayFill, 19.dp, Tok.Label1)
        }
    }
}
