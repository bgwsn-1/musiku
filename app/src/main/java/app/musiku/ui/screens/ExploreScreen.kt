package app.musiku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.GenreItem
import app.musiku.model.HomeItem
import app.musiku.model.HomeShelf
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.iosBounce
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.Tok
import coil.compose.AsyncImage
import java.util.Locale

private sealed interface ExploreCardItem {
    val id: String
    val title: String
    val subtitle: String
    val thumbnail: String?
    val eyebrow: String
}

private data class SongCard(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val thumbnail: String?,
    override val eyebrow: String = "SONG",
    val track: Track,
) : ExploreCardItem

private data class AlbumCard(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val thumbnail: String?,
    override val eyebrow: String = "ALBUM",
    val browseId: String,
) : ExploreCardItem

private data class ArtistCard(
    override val id: String,
    override val title: String,
    override val subtitle: String = "",
    override val thumbnail: String?,
    override val eyebrow: String = "ARTIST",
    val browseId: String,
) : ExploreCardItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen() {
    val app = LocalApp.current
    val ui = LocalUi.current

    var home by remember { mutableStateOf<List<HomeShelf>?>(null) }
    var genres by remember { mutableStateOf<List<GenreItem>?>(null) }
    var recommended by remember { mutableStateOf<List<ExploreCardItem>>(emptyList()) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadFailed = false
        val h = runCatching { app.yt.home() }.getOrElse {
            loadFailed = true
            emptyList()
        }
        home = h
        genres = runCatching { app.yt.genres() }.getOrDefault(emptyList())

        val seed = app.library.recent.value.firstOrNull()?.videoId
        if (seed != null) {
            val rel = runCatching { app.yt.related(seed) }.getOrDefault(emptyList())
            recommended = rel.distinctBy { it.videoId }.take(15).map { t ->
                SongCard(
                    id = t.videoId,
                    title = t.title,
                    subtitle = t.artist.orEmpty(),
                    thumbnail = t.thumbs,
                    track = t,
                )
            }
        }
    }

    val albums = remember(home) {
        (home.orEmpty().firstOrNull { it.title == "Rilis Baru" }?.items ?: emptyList())
            .filter { it.isAlbum && it.browseId != null }
            .distinctBy { it.browseId }
            .map {
                AlbumCard(
                    id = it.browseId!!,
                    title = it.title,
                    subtitle = it.artist.orEmpty(),
                    thumbnail = it.thumbs,
                    browseId = it.browseId,
                )
            }
    }
    val hero = albums.take(6)
    val newReleases = albums.drop(6)

    val topSongs = remember(home) {
        (home.orEmpty().firstOrNull { it.title == "Pilihan teratas untukmu" }?.items ?: emptyList())
            .filter { it.videoId != null }
            .distinctBy { it.videoId }
            .mapNotNull { item ->
                val t = item.toTrack() ?: return@mapNotNull null
                SongCard(
                    id = t.videoId,
                    title = t.title,
                    subtitle = t.artist.orEmpty(),
                    thumbnail = t.thumbs,
                    track = t,
                )
            }
    }

    val moodArt = remember { mutableStateMapOf<String, String?>() }
    val moods = genres.orEmpty()

    val loading = home == null && !loadFailed
    val hasContent = hero.isNotEmpty() || recommended.isNotEmpty() || newReleases.isNotEmpty() || topSongs.isNotEmpty() || moods.isNotEmpty()

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        // Match the other pages: the platform's glow/stretch is off and the iOS rubber band owns
        // the overscroll.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MusikuLoading() }
            }

            loadFailed && !hasContent -> {
                LazyColumn(
                    modifier = Modifier.iosBounce(),
                    contentPadding = PaddingValues(top = 116.dp, bottom = DockInset),
                ) {
                    item { EmptyState("Jelajahi belum bisa dimuat", "Cek koneksi internet lalu coba lagi.") }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().iosBounce(),
                    contentPadding = PaddingValues(start = 0.dp, top = 116.dp, bottom = DockInset + 16.dp),
                ) {
                    item(key = "explore_title") {
                        Text(
                            text = "Explore",
                            color = Tok.Label1,
                            fontSize = 34.sp,
                            lineHeight = 41.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                        )
                    }

                    if (hero.isNotEmpty()) {
                        item(key = "hero", contentType = "hero") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                            ) {
                                items(hero, key = { "hero_${it.id}" }) { item ->
                                    EditorialCard(item) { openExploreItem(item, ui, app.player) }
                                }
                            }
                        }
                    }

                    exploreShelf(
                        key = "recommended",
                        title = "Recommended for You",
                        items = recommended,
                        onOpen = { openExploreItem(it, ui, app.player) },
                    )
                    exploreShelf(
                        key = "new",
                        title = "New release albums",
                        items = newReleases,
                        onOpen = { openExploreItem(it, ui, app.player) },
                    )
                    exploreShelf(
                        key = "top",
                        title = "Top Songs",
                        items = topSongs,
                        onOpen = { openExploreItem(it, ui, app.player) },
                    )

                    if (moods.isNotEmpty()) {
                        item(key = "moods_title", contentType = "shelf_title") {
                            ShelfTitle("Mood and Genres")
                        }
                        item(key = "moods_row", contentType = "mood_tiles") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                items(moods, key = { "mood_${it.browseId}_${it.params}" }) { mood ->
                                    val artKey = mood.browseId + "|" + mood.params
                                    LaunchedEffect(artKey) {
                                        if (!moodArt.containsKey(artKey)) {
                                            moodArt[artKey] = runCatching { app.yt.genreArt(mood) }.getOrNull()
                                        }
                                    }
                                    CategoryTile(
                                        title = mood.title,
                                        color = Color(mood.color),
                                        art = moodArt[artKey],
                                        modifier = Modifier.width(180.dp),
                                    ) { ui.navigate(Routes.genre(mood.browseId, mood.params, mood.title)) }
                                }
                            }
                        }
                    }

                    if (!hasContent) {
                        item { EmptyState("Belum ada rekomendasi", "Coba lagi beberapa saat lagi.") }
                    }
                }
            }
        }
        }
    }
}

private fun openExploreItem(
    item: ExploreCardItem,
    ui: app.musiku.ui.UiState,
    player: app.musiku.player.PlayerController,
) {
    when (item) {
        is SongCard -> player.playOne(item.track)
        is AlbumCard -> ui.navigate(Routes.album(item.browseId))
        is ArtistCard -> ui.navigate(Routes.artist(item.browseId))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.exploreShelf(
    key: String,
    title: String,
    items: List<ExploreCardItem>,
    onOpen: (ExploreCardItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${key}_title", contentType = "shelf_title") { ShelfTitle(title) }
    item(key = "${key}_row", contentType = "shelf") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${key}_${it.id}" }) { item ->
                ShelfCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

@Composable
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.26).sp,
        color = Tok.Label1,
        maxLines = 1,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    )
}

@Composable
private fun EditorialCard(item: ExploreCardItem, onClick: () -> Unit) {
    val cardWidth = (LocalConfiguration.current.screenWidthDp * 0.88f).dp.coerceAtMost(360.dp)
    Column(
        Modifier
            .width(cardWidth)
            .pressable(onClick = onClick, pressScale = 0.985f),
    ) {
        Text(
            text = item.eyebrow.uppercase(Locale.ROOT),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.06.sp,
            color = Tok.Label2,
            maxLines = 1,
        )
        Text(
            text = item.title,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.45).sp,
            color = Tok.Label1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            letterSpacing = (-0.45).sp,
            color = Tok.Label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tok.Fill1),
        ) {
            if (item.thumbnail != null) {
                Artwork(item.thumbnail, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ShelfCard(item: ExploreCardItem, onClick: () -> Unit) {
    val shape = if (item is ArtistCard) CircleShape else RoundedCornerShape(10.dp)
    Column(
        Modifier
            .width(154.dp)
            .pressable(onClick = onClick, pressScale = 0.97f),
    ) {
        Box(Modifier.size(154.dp).clip(shape).background(Tok.Fill1)) {
            if (item.thumbnail != null) Artwork(item.thumbnail, Modifier.size(154.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.23).sp,
            color = Tok.Label1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.23).sp,
                color = Tok.Label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    color: Color,
    art: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ground = color.copy(alpha = 1f)
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .aspectRatio(181f / 105f)
            .clip(shape)
            .background(ground)
            .border(0.25.dp, Color.Black.copy(alpha = 0.1f), shape)
            .pressable(onClick = onClick, pressScale = 0.97f),
    ) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .drawWithContent {
                        val paint = Paint().apply { blendMode = BlendMode.Multiply }
                        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
                        drawContent()
                        drawContext.canvas.restore()
                    },
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(ground.copy(alpha = 0.25f), blendMode = BlendMode.Screen)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.28f),
                    ),
                ),
        )
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.23).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}
