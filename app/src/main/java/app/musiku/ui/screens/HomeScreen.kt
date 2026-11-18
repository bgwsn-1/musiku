package app.musiku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.HomeShelf
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.UiState
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.Eq
import app.musiku.ui.components.LargeTitleList
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.Tappable
import app.musiku.ui.components.pressable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import app.musiku.player.PlayerController

// Home's page rhythm, lifted from Convx: one 20dp gutter, 10dp between list items,
// 154dp cards with a 14dp gap, and song shelves drawn as four-deep list columns.
private val HomeGutter = 20.dp
private val HomeListItemSpacing = 10.dp
private val HomeCardWidth = 154.dp
private val HomeCardGap = 14.dp
private val HomeCardShape = RoundedCornerShape(10.dp)
private val HomeHeroShape = RoundedCornerShape(18.dp)
private val HomeListArt = 52.dp
private val HomeListPeek = 28.dp
private const val HomeListRows = 4
private const val HomeListMinimum = 8

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val ui = LocalUi.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by rememberLoad(attempt) { app.yt.home() }
    val recent by app.library.recent.collectAsState()

    LargeTitleList(
        "Beranda",
        titleBottom = 4.dp,
        itemSpacing = HomeListItemSpacing,
    ) {
        when (val l = load) {
            is Load.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().fillParentMaxHeight(0.6f), contentAlignment = Alignment.Center) {
                    MusikuLoading()
                }
            }
            is Load.Err -> item(key = "error") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Beranda belum bisa dimuat", "Cek koneksi, lalu coba lagi. Kamu tetap bisa mencari lagu.")
                    Text(
                        "Coba lagi", color = Tok.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { attempt++ }.padding(12.dp),
                    )
                }
            }
            is Load.Ok -> homeFeed(l.value, recent, app.player, ui)
        }
    }
}

/** One Home entry, already resolved into what a card or a list row needs to draw. */
private class HomeEntry(
    val title: String,
    val subtitle: String,
    val img: String?,
    val track: Track?,
    val centered: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: ((Rect) -> Unit)? = null,
)

/** The hero's song plus the queue the Play and Shuffle pills act on. */
private class HeroSource(val track: Track, val queue: List<Track>)

private fun HomeShelf.toEntries(app: PlayerController, ui: UiState): List<HomeEntry> {
    val queue = items.mapNotNull { it.toTrack() }
    return items.map { item ->
        val track = item.toTrack()
        HomeEntry(
            title = item.title,
            subtitle = item.artist.orEmpty(),
            img = item.thumbs,
            track = track,
            centered = item.isArtist,
            onClick = {
                when {
                    item.isArtist && item.browseId != null -> ui.navigate(Routes.artist(item.browseId))
                    item.isAlbum && item.browseId != null -> ui.navigate(Routes.album(item.browseId))
                    track != null -> app.playOne(track, queue)
                }
            },
            onLongClick = track?.let { t -> { rect: Rect -> ui.openMenu(t, anchor = rect) } },
        )
    }
}

private fun recentEntries(tracks: List<Track>, app: PlayerController, ui: UiState): List<HomeEntry> =
    tracks.map { t ->
        HomeEntry(
            title = t.title,
            subtitle = t.artist.orEmpty(),
            img = t.thumbs,
            track = t,
            onClick = { app.playOne(t, tracks) },
            onLongClick = { rect -> ui.openMenu(t, anchor = rect) },
        )
    }

/** Convx draws song shelves of eight or more as list columns, everything else as cards. */
private fun HomeShelf.isSongShelf(): Boolean {
    val songs = items.count { it.videoId != null }
    return songs >= HomeListMinimum && songs * 2 >= items.size
}

private fun LazyListScope.homeFeed(
    shelves: List<HomeShelf>,
    recent: List<Track>,
    player: PlayerController,
    ui: UiState,
) {
    val hero = heroSource(recent, shelves)
    if (hero != null) {
        item(key = "hero") { HomeHeroCard(hero) }
    }

    if (recent.size >= 2) {
        val entries = recentEntries(recent.take(12), player, ui)
        item(key = "recent") {
            HomeCardRow(
                title = "Dengarkan lagi",
                entries = entries,
                chevron = true,
            )
        }
    }

    shelves.forEachIndexed { index, shelf ->
        val key = "shelf-$index"
        val queue = shelf.items.mapNotNull { it.toTrack() }
        if (shelf.isSongShelf()) {
            item(key = key) {
                HomeListColumns(
                    title = shelf.title.ifBlank { "Lagu pilihan" },
                    entries = shelf.toEntries(player, ui),
                    onPlayAll = queue.takeIf { it.isNotEmpty() }?.let { tracks ->
                        { player.playTracks(tracks) }
                    },
                )
            }
        } else {
            item(key = key) {
                HomeCardRow(
                    title = shelf.title.ifBlank { "Untukmu" },
                    entries = shelf.toEntries(player, ui),
                    chevron = true,
                )
            }
        }
    }

    item(key = "footer") {
        Text(
            "Metadata dari YouTube Music. Favorit dan playlist tersimpan di perangkat ini, tanpa akun.",
            color = Tok.Label3, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = HomeGutter, vertical = 8.dp),
        )
    }
}

/**
 * Convx's Home hero is the most recently played song. Musiku has no "quick picks" feed, so
 * fall back to the first playable song in the first shelf that has one.
 */
private fun heroSource(recent: List<Track>, shelves: List<HomeShelf>): HeroSource? {
    recent.firstOrNull()?.let { return HeroSource(it, recent) }
    shelves.forEach { shelf ->
        val tracks = shelf.items.mapNotNull { it.toTrack() }
        if (tracks.isNotEmpty()) return HeroSource(tracks.first(), tracks)
    }
    return null
}

/* ---------------------------------------------------------------- hero card -- */

@Composable
private fun HomeHeroCard(hero: HeroSource) {
    val app = LocalApp.current
    val track = hero.track
    // Convx's entrance: the cover settles from 112% while fading in.
    val scale = remember { Animatable(1.12f) }
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, tween(340)) }
            launch { contentAlpha.animateTo(1f, tween(340)) }
        }
    }

    Tappable(
        onClick = { app.player.playOne(track, hero.queue) },
        pressScale = 0.985f,
        description = "${track.title} — ${track.artist.orEmpty()}",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HomeGutter)
            .aspectRatio(4f / 3f)
            .shadow(20.dp, HomeHeroShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(HomeHeroShape)
            .background(Tok.Fill1),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val frostBlur = 24.dp * (screenWidth / maxWidth)

            Artwork(
                track.thumbs,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = contentAlpha.value
                    },
                contentDescription = null,
            )

            // The frosted band is the cover itself, blurred and faded in over the bottom
            // half — Convx bakes this ramp so the title sits in the cover's own colour.
            HeroFrost(track.thumbs, frostBlur, Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value })

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(HomeGutter)
                    .graphicsLayer { alpha = contentAlpha.value },
            ) {
                Text(
                    track.title, color = Color.White, fontSize = 20.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist.orEmpty(), color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 21.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroPill(MIcons.Play, "Putar", offsetIcon = true) { app.player.playOne(track, hero.queue) }
                    HeroPill(MIcons.Shuffle, "Acak") { app.player.playTracks(hero.queue.shuffled()) }
                }
            }
        }
    }
}

/** Blurred copy of the cover, unmasked by a vertical ramp so only the lower band shows. */
@Composable
private fun HeroFrost(url: String?, blurRadius: Dp, modifier: Modifier = Modifier) {
    if (url == null) return
    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.65f),
                        0.75f to Color.Black,
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Artwork(url, Modifier.fillMaxSize().clipToBounds().blur(blurRadius), contentDescription = null)
    }
}

@Composable
private fun HeroPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    offsetIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .pressable(onClick = onClick, pressScale = 0.94f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            icon, contentDescription = null, tint = Color.White,
            modifier = Modifier.size(17.dp).offset(x = if (offsetIcon) 0.5.dp else 0.dp),
        )
        Text(
            label, color = Color.White, fontSize = 17.sp, lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.43).sp, maxLines = 1,
        )
    }
}

/* ------------------------------------------------------------ section header -- */

/**
 * Section header on Convx's rhythm: 14dp above, 6dp below (plus the list's 10dp item
 * spacing), 22sp bold. A chevron marks a section that can page forward; song shelves
 * carry "Putar semua" instead, the way Convx's quick picks do.
 */
@Composable
private fun HomeSectionHeader(
    title: String,
    onChevron: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onChevron != null) Modifier.pressable(onClick = onChevron) else Modifier)
            .padding(start = HomeGutter, end = HomeGutter, top = 14.dp, bottom = 6.dp),
    ) {
        // Convx keeps the chevron tucked against the title, not at the row's right edge.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title, color = Tok.Label1, fontSize = 22.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.22).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onChevron != null) {
                val chevronColor = Tok.Label2
                Canvas(Modifier.padding(start = 6.dp, top = 1.dp).size(width = 9.dp, height = 15.dp)) {
                    val stroke = 2.6.dp.toPx()
                    val inset = stroke / 2
                    drawPath(
                        Path().apply {
                            moveTo(inset, inset)
                            lineTo(size.width - inset, size.height / 2)
                            lineTo(inset, size.height - inset)
                        },
                        color = chevronColor,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        if (onPlayAll != null) {
            Text(
                "Putar semua", color = Tok.Label2, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 1, modifier = Modifier.pressable(onClick = onPlayAll),
            )
        }
    }
}

/* ----------------------------------------------------------------- card row -- */

@Composable
private fun HomeCardRow(
    title: String,
    entries: List<HomeEntry>,
    chevron: Boolean,
) {
    if (entries.isEmpty()) return
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ps by LocalApp.current.player.ui.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title,
            onChevron = if (chevron) {
                {
                    scope.launch {
                        val target = (rowState.firstVisibleItemIndex + 3).coerceAtMost(entries.lastIndex)
                        rowState.animateScrollToItem(target)
                    }
                }
            } else null,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = HomeGutter),
            horizontalArrangement = Arrangement.spacedBy(HomeCardGap),
        ) {
            itemsIndexed(entries, key = { i, e -> "$i-${e.title}" }) { _, entry ->
                HomeShelfCard(
                    entry,
                    isCurrent = entry.track != null && ps.current?.videoId == entry.track.videoId,
                    isPlaying = entry.track != null && ps.current?.videoId == entry.track.videoId && ps.playing,
                )
            }
        }
    }
}

@Composable
private fun HomeShelfCard(entry: HomeEntry, isCurrent: Boolean, isPlaying: Boolean) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Tappable(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick?.let { f -> { f(bounds) } },
        pressScale = 0.97f,
        description = entry.title,
        modifier = Modifier
            .width(HomeCardWidth)
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Column {
            Box(
                Modifier
                    .size(HomeCardWidth)
                    .clip(HomeCardShape)
                    .background(Tok.Fill1)
                    .border(0.25.dp, Color.Black.copy(alpha = 0.1f), HomeCardShape),
            ) {
                Artwork(entry.img, Modifier.fillMaxSize(), contentDescription = null)
                if (isCurrent) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        if (isPlaying) Eq(true) else Icon(MIcons.Play, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.title, color = Tok.Label1, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.Normal, letterSpacing = (-0.23).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (entry.centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle, color = Tok.Label2, fontSize = 15.sp, lineHeight = 20.sp,
                    letterSpacing = (-0.23).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = if (entry.centered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/* ------------------------------------------------------------ list columns -- */

@Composable
private fun HomeListColumns(
    title: String,
    entries: List<HomeEntry>,
    onPlayAll: (() -> Unit)?,
) {
    if (entries.isEmpty()) return
    val rows = remember(entries) { entries.chunked(HomeListRows) }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxWidth()) {
        HomeSectionHeader(title, onPlayAll = onPlayAll)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Same arithmetic as Convx: the next column's 14dp gap plus 14dp of itself
            // show past the right edge, and a column is a full page minus gutter/peek.
            val columnWidth = maxWidth - HomeGutter - HomeListPeek
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(horizontal = HomeGutter),
                horizontalArrangement = Arrangement.spacedBy(HomeCardGap),
            ) {
                itemsIndexed(rows, key = { i, _ -> i }) { _, column ->
                    Column(Modifier.width(columnWidth)) {
                        column.forEach { entry -> HomeListRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeListRow(entry: HomeEntry) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pressable(
                onClick = entry.onClick,
                onLongClick = entry.onLongClick?.let { f -> { f(bounds) } },
                pressScale = 0.985f,
            )
            .padding(vertical = 5.dp),
    ) {
        Box(Modifier.size(HomeListArt).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1)) {
            Artwork(entry.img, Modifier.fillMaxSize(), contentDescription = null)
            Box(Modifier.fillMaxSize().border(0.25.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                entry.title, color = Tok.Label1, fontSize = 15.sp, lineHeight = 20.sp,
                letterSpacing = (-0.23).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle, color = Tok.Label2, fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
