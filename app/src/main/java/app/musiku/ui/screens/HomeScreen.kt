package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.HomeItem
import app.musiku.model.HomeShelf
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.Eq
import app.musiku.ui.components.GridCard
import app.musiku.ui.components.LargeTitleList
import app.musiku.ui.components.Load
import app.musiku.ui.components.SectionTitle
import app.musiku.ui.components.Shelf
import app.musiku.ui.components.Skeleton
import app.musiku.ui.components.pressable
import app.musiku.ui.components.Tappable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.Tok

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by rememberLoad(attempt) { app.yt.home() }
    val recent by app.library.recent.collectAsState()

    // web: <h1> has mb-5 before the featured carousel
    LargeTitleList("Beranda", titleBottom = 20.dp) {
        when (val l = load) {
            is Load.Loading -> item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Skeleton(Modifier.fillMaxWidth().aspectRatio(1.4f))
                    repeat(3) { Skeleton(Modifier.fillMaxWidth().height(56.dp)) }
                }
            }
            is Load.Err -> item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState("Beranda belum bisa dimuat", "Cek koneksi, lalu coba lagi. Kamu tetap bisa mencari lagu.")
                    Text(
                        "Coba lagi", color = Tok.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { attempt++ }.padding(12.dp),
                    )
                }
            }
            is Load.Ok -> {
                val shelves = l.value
                val lead = shelves.firstOrNull()
                val songsIdx = shelves.indexOfFirst { s -> shelves.indexOf(s) > 0 && s.items.count { it.videoId != null } >= 3 }
                val songs = if (songsIdx > 0) shelves[songsIdx] else null
                val rest = shelves.filterIndexed { i, _ -> i != 0 && i != songsIdx }

                if (lead != null && lead.items.isNotEmpty()) item { FeaturedCarousel(lead.items.take(6)) }
                if (songs != null) item { SongColumns(songs.title.ifBlank { "Lagu Terbaru" }, songs.items.take(12)) }
                if (recent.isNotEmpty()) item { RecentShelf(recent.take(12)) }
                items(rest.size) { i -> ShelfRow(rest[i]) }
                item {
                    Text(
                        "Metadata dari YouTube Music. Favorit dan playlist tersimpan di perangkat ini, tanpa akun.",
                        color = Tok.Label3, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentShelf(tracks: List<Track>) {
    val app = LocalApp.current
    Shelf("Dengarkan lagi") {
        itemsIndexed(tracks, key = { i, t -> "${t.videoId}-$i" }) { _, t ->
            GridCard(t.title, t.artist, t.thumbs, onClick = { app.player.playOne(t, tracks) })
        }
    }
}

@Composable
private fun ShelfRow(sh: HomeShelf) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val queue = remember(sh) { sh.items.mapNotNull { it.toTrack() } }
    Shelf(sh.title) {
        itemsIndexed(sh.items, key = { i, it -> "$i-${it.videoId ?: it.browseId}" }) { _, it ->
            GridCard(
                it.title, it.artist, it.thumbs,
                onClick = {
                    val tr = it.toTrack()
                    if (it.isArtist && it.browseId != null) ui.navigate(Routes.artist(it.browseId))
                    else if (it.isAlbum && it.browseId != null) ui.navigate(Routes.album(it.browseId))
                    else if (tr != null) app.player.playOne(tr, queue)
                },
            )
        }
    }
}

/** Wide editorial cards, one snaps at a time and the next peeks in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(items: List<HomeItem>) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.player.ui.collectAsState()
    val listState = rememberLazyListState()
    val tracks = remember(items) { items.mapNotNull { it.toTrack() } }
    val cardW = (LocalConfiguration.current.screenWidthDp - 72).dp

    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 36.dp),
    ) {
        itemsIndexed(items, key = { i, it -> "$i-${it.videoId ?: it.browseId}" }) { _, it ->
            val track = it.toTrack()
            val isCurrent = track != null && state.current?.videoId == track.videoId
            val playing = isCurrent && state.playing
            Tappable(
                onClick = {
                    if (it.isArtist && it.browseId != null) ui.navigate(Routes.artist(it.browseId))
                    else if (it.isAlbum && it.browseId != null) ui.navigate(Routes.album(it.browseId))
                    else if (track != null) { if (isCurrent) app.player.toggle() else app.player.playOne(track, tracks) }
                },
                pressScale = 0.98f,
                modifier = Modifier.width(cardW),
            ) {
                Column {
                    Text(if (it.isAlbum) "ALBUM" else "LAGU", color = Tok.Label3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.84.sp)
                    Text(it.title, color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(it.artist ?: " ", color = Tok.Label2, fontSize = 20.sp, fontWeight = FontWeight.Light, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The web caption is a backdrop-blurred strip over the artwork: draw a blurred
                    // copy of the art, clipped to the strip, then tint it black/35.
                    val artH = cardW / 1.4f
                    val captionH = 41.dp
                    Box(
                        Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1.4f)
                            .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
                            .clip(RoundedCornerShape(12.dp)).background(Tok.Fill1),
                    ) {
                        Artwork(it.thumbs, Modifier.fillMaxWidth().aspectRatio(1.4f))
                        Box(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(captionH).clipToBounds(),
                        ) {
                            Artwork(
                                it.thumbs,
                                Modifier.fillMaxWidth().height(artH).offset(y = captionH - artH).blur(18.dp),
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    if (it.isAlbum) "Dengarkan albumnya sekarang" else "Putar lagu ini sekarang",
                                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (playing) MIcons.Pause else MIcons.Play,
                                        null, tint = Color.White,
                                        modifier = Modifier.size(14.dp).offset(x = if (playing) 0.dp else 0.5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Songs in pages of three rows; the next page peeks in from the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongColumns(title: String, items: List<HomeItem>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // web: each page is calc(100% - 40px) of the 16px-gutter content column
    val pageW = (LocalConfiguration.current.screenWidthDp - 32 - 40).dp
    val pages = remember(items) { items.chunked(3) }
    val queue = remember(items) { items.mapNotNull { it.toTrack() } }
    Column(Modifier.padding(bottom = 36.dp)) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                .pressable {
                    val next = (listState.firstVisibleItemIndex + 1).coerceAtMost((pages.size - 1).coerceAtLeast(0))
                    scope.launch { listState.animateScrollToItem(next) }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle(title)
            Icon(MIcons.ChevronRight, null, tint = Tok.Label3, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(pages) { _, page ->
                Column(Modifier.width(pageW)) {
                    page.forEachIndexed { i, it -> SongRowHome(it, queue, divider = i < page.lastIndex) }
                }
            }
        }
    }
}

@Composable
private fun SongRowHome(it: HomeItem, queue: List<Track>, divider: Boolean) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.player.ui.collectAsState()
    val track = it.toTrack()
    val isCurrent = track != null && state.current?.videoId == track.videoId
    val playing = isCurrent && state.playing
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tappable(
                onClick = {
                    if (track == null) it.browseId?.let { b -> ui.navigate(Routes.album(b)) }
                    else if (isCurrent) app.player.toggle() else app.player.playOne(track, queue)
                },
                onLongClick = { track?.let { t -> ui.openMenu(t) } },
                pressScale = 0.985f,
                modifier = Modifier.weight(1f),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill1)) {
                        Artwork(it.thumbs, Modifier.size(54.dp))
                        if (playing) Box(Modifier.size(54.dp).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Eq(true) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(it.title, color = if (isCurrent) Tok.Accent else Tok.Label1, fontSize = 16.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(it.artist.orEmpty(), color = Tok.Label2, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (track != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).pressable { ui.openMenu(track) }, contentAlignment = Alignment.Center) {
                    Icon(MIcons.Ellipsis, "Aksi untuk ${it.title}", tint = Tok.Label3, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Tok.Hairline))
    }
}
