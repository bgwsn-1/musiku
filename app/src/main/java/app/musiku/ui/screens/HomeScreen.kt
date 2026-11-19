package app.musiku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.HomeShelf
import app.musiku.model.Track
import app.musiku.player.PlayerController
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.UiState
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.Eq
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.LargeTitleList
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.ProfileButton
import app.musiku.ui.components.Tappable
import app.musiku.ui.components.pressable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// Convx's Home rhythm (AppPage): a 20dp gutter, every list item 10dp apart, section headers 14dp
// above and 6dp below, 154dp square cards with a 14dp gap.
private val HomeGutter = 20.dp
private val HomeItemSpacing = 10.dp
private val HomeCardWidth = 154.dp
private val HomeCardGap = 14.dp
private val HomeCardShape = RoundedCornerShape(10.dp)

// Convx's Top Picks rail: 242x323 cards with a frosted 81dp footer, on a 16dp gutter and 10dp
// apart, with the header tucked 4dp above the cards instead of the usual 16dp.
private val HeroWidth = 242.dp
private val HeroHeight = 323.dp
private val HeroShape = RoundedCornerShape(20.dp)
private val HeroGutter = 16.dp
private val HeroGap = 10.dp
private val HeroBand = 81.dp

// Song shelves of eight or more draw as four-deep list columns, the way Convx's quick picks do.
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
        itemSpacing = HomeItemSpacing,
        gutter = HomeGutter,
        // Convx keeps the account avatar at the right end of the bar; here it is the way
        // into Settings.
        actions = { ProfileButton(onClick = { ui.navigate(Routes.Settings) }) },
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

/** One Home entry, already resolved into what a card, rail hero or list row needs to draw. */
private class HomeEntry(
    val title: String,
    val subtitle: String,
    /** Third line of a Top Picks card: the album, when the feed says which one. */
    val detail: String? = null,
    val img: String?,
    val track: Track?,
    val centered: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: ((Rect) -> Unit)? = null,
)

private fun HomeShelf.toEntries(app: PlayerController, ui: UiState): List<HomeEntry> {
    val queue = items.mapNotNull { it.toTrack() }
    return items.map { item ->
        val track = item.toTrack()
        HomeEntry(
            title = item.title,
            subtitle = item.artist.orEmpty(),
            detail = item.album,
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
            detail = t.album,
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
    // Convx's home opens on the Top Picks rail. Musiku's feed marks that shelf from the charts
    // page; without one, the recently played songs stand in so the rail is never empty.
    val heroShelf = shelves.firstOrNull { it.hero }
    val heroFromRecent = heroShelf == null
    val heroEntries = when {
        heroShelf != null -> heroShelf.toEntries(player, ui)
        else -> recentEntries(recent.take(12), player, ui)
    }
    if (heroEntries.isNotEmpty()) {
        item(key = "hero") {
            HomeHeroRail(
                title = heroShelf?.title?.ifBlank { null } ?: "Pilihan teratas untukmu",
                entries = heroEntries,
            )
        }
    }

    if (!heroFromRecent && recent.size >= 2) {
        item(key = "recent") {
            HomeCardRow(
                title = "Dengarkan lagi",
                entries = recentEntries(recent.take(12), player, ui),
                chevron = true,
            )
        }
    }

    shelves.forEachIndexed { index, shelf ->
        if (shelf.hero) return@forEachIndexed
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

/* ---------------------------------------------------------------- hero rail -- */

/**
 * Convx's Top Picks rail (`figmaRail(hero = true)`): one 22/28 bold header 4dp above a row of
 * tall cards. The header owns the 16dp gutter, cards keep their size and peek off the edge.
 */
@Composable
private fun HomeHeroRail(title: String, entries: List<HomeEntry>) {
    if (entries.isEmpty()) return
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .pressable {
                    scope.launch {
                        val target = (rowState.firstVisibleItemIndex + 1).coerceAtMost(entries.lastIndex)
                        rowState.animateScrollToItem(target)
                    }
                }
                .padding(start = HeroGutter, end = HeroGutter, bottom = 4.dp),
        ) {
            Text(
                title, color = Tok.Label1, fontSize = 22.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.22).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Chevron()
        }
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = HeroGutter),
            horizontalArrangement = Arrangement.spacedBy(HeroGap),
        ) {
            itemsIndexed(entries, key = { i, e -> "hero-$i-${e.title}" }) { _, entry ->
                HomeHeroCard(entry)
            }
        }
    }
}

/**
 * `FigmaTopPickCard`: a 242x323 cover, 20dp corners, a 20dp drop shadow, and the artwork
 * re-blurred into an 81dp footer so centred white text always has something to sit on.
 */
@Composable
private fun HomeHeroCard(entry: HomeEntry) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Tappable(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick?.let { f -> { f(bounds) } },
        pressScale = 0.97f,
        description = "${entry.title} — ${entry.subtitle}",
        modifier = Modifier
            .width(HeroWidth)
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .width(HeroWidth)
                .height(HeroHeight)
                .shadow(
                    elevation = 20.dp,
                    shape = HeroShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.16f),
                )
                .clip(HeroShape)
                .background(Tok.Fill1),
        ) {
            Artwork(entry.img, Modifier.fillMaxSize(), contentDescription = null)
            HeroFooter(entry)
        }
    }
}

@Composable
private fun BoxScope.HeroFooter(entry: HomeEntry) {
    val lowDevice = LocalLowDevice.current
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(HeroBand),
    ) {
        if (entry.img != null) {
            // The same cover, softened into a colour field. Scaling past the band keeps the
            // blur from eating its own edges the way a plain Gaussian pass would.
            Box(Modifier.fillMaxSize().then(if (lowDevice) Modifier else Modifier.blur(20.dp))) {
                AsyncImage(
                    model = entry.img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    },
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        } else {
            Box(Modifier.fillMaxSize().background(Tok.Fill1))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                entry.title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = (-0.23).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            if (entry.subtitle.isNotBlank()) {
                Text(
                    entry.subtitle, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                    letterSpacing = (-0.08).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    detail, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
                    letterSpacing = (-0.08).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/* ------------------------------------------------------------ section header -- */

/** Convx's `NavigationTitle` chevron: a heavy 9x15 stroke level with the title's cap height. */
@Composable
private fun Chevron() {
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
            if (onChevron != null) Chevron()
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
