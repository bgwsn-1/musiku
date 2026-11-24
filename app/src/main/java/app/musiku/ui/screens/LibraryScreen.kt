package app.musiku.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Playlist
import app.musiku.model.PlayHistoryEntry
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.LargeTitleList
import app.musiku.ui.components.Tappable
import app.musiku.ui.components.TextPromptDialog
import app.musiku.ui.components.TrackRow
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.SfLibraryIcons
import app.musiku.ui.theme.SfPro
import app.musiku.ui.theme.Tok
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** SF Pro at an explicit size — never the theme's font pick. */
private fun SfStyle(
    size: TextUnit,
    line: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = SfPro,
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    letterSpacing = tracking,
)

private enum class LibraryPage { Home, Playlists, Artists, Albums, Songs, Liked, Downloads }

private data class ArtistGroup(val name: String, val artistId: String?, val thumbs: String?, val count: Int)
private data class AlbumGroup(val name: String, val albumId: String?, val artist: String?, val thumbs: String?, val count: Int)
private data class RecentItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbs: String?,
    val route: String? = null,
    val page: LibraryPage? = null,
)

@Composable
fun LibraryScreen() {
    var page by rememberSaveable { mutableStateOf(LibraryPage.Home) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }

    fun openSection(next: LibraryPage) {
        forward = true
        page = next
    }

    fun openHistory() {
        forward = true
        historyOpen = true
    }

    fun goBack() {
        forward = false
        if (historyOpen) historyOpen = false else page = LibraryPage.Home
    }

    // A local section is part of the Library tab, so system back has to close it instead of
    // popping the tab itself and landing on Home.
    BackHandler(enabled = historyOpen || page != LibraryPage.Home) { goBack() }

    val spec = tween<androidx.compose.ui.unit.IntOffset>(340, easing = FastOutSlowInEasing)
    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        AnimatedContent(
            targetState = if (historyOpen) "history" else page.name,
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(spec) { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(spec) { -it / 4 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(spec) { it } + fadeOut(tween(200)))
                }
            },
        ) { target ->
            when (target) {
                "history" -> HistoryScreen(onBack = ::goBack)
                LibraryPage.Home.name -> LibraryHome(onOpen = ::openSection, onHistory = ::openHistory)
                else -> LibraryCollection(page = LibraryPage.valueOf(target), onBack = ::goBack)
            }
        }
    }
}

@Composable
private fun LibraryHome(onOpen: (LibraryPage) -> Unit, onHistory: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val liked by app.library.liked.collectAsState()
    val playlists by app.library.playlists.collectAsState()
    val spotifyUser by app.spotifyAuth.user.collectAsState()
    val spotifyPlaylists by app.spotifyAuth.playlists.collectAsState()

    LaunchedEffect(spotifyUser?.id) {
        if (spotifyUser != null && spotifyPlaylists.isEmpty()) {
            runCatching { app.spotifyAuth.myPlaylists() }
        }
    }

    val pinned = remember(playlists) { playlists.sortedByDescending { it.createdAt }.take(6) }
    val artists = remember(liked) {
        liked.filter { !it.artist.isNullOrBlank() }.groupBy { it.artist!!.lowercase() }.map { (_, ts) ->
            ArtistGroup(ts.first().artist!!, ts.firstNotNullOfOrNull { it.artistId }, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }
    val albums = remember(liked) {
        liked.filter { !it.album.isNullOrBlank() }.groupBy { "${it.album!!.lowercase()}::${it.artist?.lowercase().orEmpty()}" }.map { (_, ts) ->
            AlbumGroup(ts.first().album!!, ts.firstNotNullOfOrNull { it.albumId }, ts.first().artist, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }
    val recent = remember(playlists, albums) {
        buildList {
            playlists.sortedByDescending { it.createdAt }.take(4).forEach { pl ->
                add(
                    RecentItem(
                        key = "pl-${pl.id}",
                        title = pl.name,
                        subtitle = songCount(pl.tracks.size),
                        thumbs = pl.tracks.firstOrNull()?.thumbs,
                        route = Routes.playlist(pl.id),
                    ),
                )
            }
            albums.take(4).forEach { al ->
                add(
                    RecentItem(
                        key = "al-${al.name}-${al.artist.orEmpty()}",
                        title = al.name,
                        subtitle = al.artist.orEmpty(),
                        thumbs = al.thumbs,
                        route = al.albumId?.let(Routes::album),
                    ),
                )
            }
        }.take(6)
    }

    LargeTitleList("Pustaka") {
        if (pinned.isNotEmpty()) {
            item(key = "pinned") { PinnedGrid(pinned) { pl -> ui.navigate(Routes.playlist(pl.id)) } }
        }

        item(key = "categories") {
            Column {
                LibraryCategory("Playlist", SfLibraryIcons.MusicNoteList) { onOpen(LibraryPage.Playlists) }
                LibraryCategory("Artis", SfLibraryIcons.Microphone) { onOpen(LibraryPage.Artists) }
                LibraryCategory("Album", SfLibraryIcons.SquareStack) { onOpen(LibraryPage.Albums) }
                LibraryCategory("Lagu", SfLibraryIcons.MusicNote) { onOpen(LibraryPage.Songs) }
                LibraryCategory("Disukai", SfLibraryIcons.Heart) { onOpen(LibraryPage.Liked) }
                LibraryCategory("Unduhan", MIcons.Download) { onOpen(LibraryPage.Downloads) }
                LibraryCategory("Statistik & Replay", MIcons.Music) { ui.navigate(Routes.Stats) }
                LibraryCategory("Riwayat", SfLibraryIcons.Clock) { onHistory() }
                LibraryCategory("Spotify Saya", SfLibraryIcons.Link) { ui.navigate(Routes.SpotifyLibrary) }
                LibraryCategory("Impor Link Spotify", MIcons.Download) { ui.spotifyImportOpen = true }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recent-title") {
                Text(
                    "Baru Ditambahkan",
                    style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.26).sp),
                    color = Tok.Label1,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                )
            }
            listItems(recent.chunked(2), key = { "recent-" + it.first().key }) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                ) {
                    pair.forEach { item ->
                        Box(Modifier.weight(1f)) {
                            RecentTile(item) {
                                if (item.route != null) ui.navigate(item.route)
                                else app.player.say("Belum bisa dibuka")
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (spotifyUser != null && spotifyPlaylists.isNotEmpty()) {
            item(key = "spotify-title") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dari Spotify",
                        style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.26).sp),
                        color = Tok.Label1,
                    )
                    Text(
                        "Lihat Semua",
                        style = SfStyle(14.sp, 18.sp, FontWeight.SemiBold),
                        color = Tok.Accent,
                        modifier = Modifier.clip(CircleShape).pressable { ui.navigate(Routes.SpotifyLibrary) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            listItems(spotifyPlaylists.take(6).chunked(2), key = { "sp-" + it.first().id }) { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                ) {
                    pair.forEach { sp ->
                        Box(Modifier.weight(1f)) {
                            RecentTile(
                                RecentItem(
                                    key = "sp-${sp.id}",
                                    title = sp.name,
                                    subtitle = songCount(sp.trackCount),
                                    thumbs = sp.cover,
                                ),
                            ) {
                                val existing = playlists.firstOrNull { it.source == "spotify" && it.sourceId == sp.id }
                                if (existing != null) {
                                    ui.navigate(Routes.playlist(existing.id))
                                } else {
                                    ui.spotifyImportOpen = true
                                    app.importer.import("https://open.spotify.com/playlist/${sp.id}")
                                }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else if (recent.isEmpty()) {
            item(key = "library-empty") {
                LibraryEmptyState(
                    icon = SfLibraryIcons.MusicNoteList,
                    title = "Belum ada musik",
                    body = "Tambahkan lagu lewat playlist atau impor dari Spotify, nanti muncul di sini.",
                    action = "Impor dari Spotify",
                    onAction = { ui.spotifyImportOpen = true },
                )
            }
        }
    }
}

/** Three-across pinned covers. The grid is measured, non-scrollable, and lives inside the page list. */
@Composable
private fun PinnedGrid(items: List<Playlist>, onClick: (Playlist) -> Unit) {
    val columns = 3
    val gap = 12.dp
    val rowGap = 16.dp
    val labelHeight = 24.dp
    BoxWithConstraints(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
        val tileW = (maxWidth - gap * (columns - 1)) / columns
        val rows = (items.size + columns - 1) / columns
        val height = (tileW + labelHeight) * rows + rowGap * (rows - 1).coerceAtLeast(0)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(rowGap),
            modifier = Modifier.fillMaxWidth().height(height),
        ) {
            gridItems(items, key = { it.id }) { pl ->
                Column {
                    Artwork(
                        pl.tracks.firstOrNull()?.thumbs,
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(Tok.Fill1).border(0.5.dp, Tok.Hairline, RoundedCornerShape(10.dp)),
                    )
                    Text(
                        pl.name,
                        style = SfStyle(13.sp, 18.sp, tracking = (-0.08).sp).copy(textAlign = TextAlign.Center),
                        color = Tok.Label1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCategory(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(52.dp).pressable { onClick() },
    ) {
        Box(Modifier.padding(start = 16.dp, end = 8.dp).width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Tok.Accent, modifier = Modifier.size(23.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp)) {
            Text(
                label,
                style = SfStyle(17.sp, 22.sp, tracking = (-0.43).sp),
                color = Tok.Label1,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Chevron(Modifier.align(Alignment.CenterEnd))
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.33.dp).background(Tok.Hairline),
            )
        }
    }
}

/** SF chevron.right, 17pt semibold — about 7x12 dp at the reference's metrics. */
@Composable
private fun Chevron(modifier: Modifier = Modifier) {
    val color = Tok.Label3
    Canvas(modifier.size(7.dp, 12.dp)) {
        val w = 2.dp.toPx()
        drawLine(color, Offset(w, 0f), Offset(size.width - w, size.height / 2), w, StrokeCap.Round)
        drawLine(color, Offset(size.width - w, size.height / 2), Offset(w, size.height), w, StrokeCap.Round)
    }
}

@Composable
private fun RecentTile(item: RecentItem, onClick: () -> Unit) {
    Tappable(onClick, pressScale = 0.97f, description = item.title) {
        Column {
            Artwork(
                item.thumbs,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                    .background(Tok.Fill1).border(0.5.dp, Tok.Hairline, RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp),
                color = Tok.Label1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.subtitle,
                style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp),
                color = Tok.Label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Tok.Accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Tok.Accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.26).sp), color = Tok.Label1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp), color = Tok.Label2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Tappable(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(Tok.Accent),
            description = action,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(action, style = SfStyle(17.sp, 22.sp, FontWeight.SemiBold, (-0.43).sp), color = Color.White)
            }
        }
    }
}

@Composable
private fun LibraryCollection(page: LibraryPage, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val liked by app.library.liked.collectAsState()
    val downloadedTracks by app.library.downloadedTracks.collectAsState()
    val downloadStates by app.downloads.states.collectAsState()
    val playlists by app.library.playlists.collectAsState()
    val spotifyPlaylists by app.spotifyAuth.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    val title = when (page) {
        LibraryPage.Playlists -> "Playlist"
        LibraryPage.Artists -> "Artis"
        LibraryPage.Albums -> "Album"
        LibraryPage.Songs -> "Lagu"
        LibraryPage.Liked -> "Disukai"
        LibraryPage.Downloads -> "Unduhan"
        else -> "Pustaka"
    }
    val artists = remember(liked) {
        liked.filter { !it.artist.isNullOrBlank() }.groupBy { it.artist!!.lowercase() }.map { (_, ts) ->
            ArtistGroup(ts.first().artist!!, ts.firstNotNullOfOrNull { it.artistId }, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }
    val albums = remember(liked) {
        liked.filter { !it.album.isNullOrBlank() }.groupBy { "${it.album!!.lowercase()}::${it.artist?.lowercase().orEmpty()}" }.map { (_, ts) ->
            AlbumGroup(ts.first().album!!, ts.firstNotNullOfOrNull { it.albumId }, ts.first().artist, ts.firstNotNullOfOrNull { it.thumbs }, ts.size)
        }.sortedBy { it.name.lowercase() }
    }
    val addAction: (@Composable () -> Unit)? = if (page == LibraryPage.Playlists) {
        {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Tok.Fill2).pressable { creating = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MIcons.Plus, null, tint = Tok.Label1, modifier = Modifier.size(22.dp))
            }
        }
    } else null

    LargeTitleList(
        title,
        titleBottom = 10.dp,
        leading = {
            Row(
                Modifier.pressable(onClick = onBack).padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MIcons.Back, null, tint = Tok.Label1, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
                Text("Pustaka", style = SfStyle(17.sp, 22.sp, FontWeight.Medium), color = Tok.Label1, maxLines = 1)
            }
        },
        actions = addAction,
    ) {
        when (page) {
            LibraryPage.Playlists -> {
                listItems(playlists.sortedByDescending { it.createdAt }, key = { it.id }) { pl ->
                    LibraryArtRow(
                        art = pl.tracks.firstOrNull()?.thumbs,
                        title = pl.name,
                        subtitle = songCount(pl.tracks.size),
                        artSize = 60.dp,
                        rowHeight = 76.dp,
                        onClick = { ui.navigate(Routes.playlist(pl.id)) },
                    )
                }
                val importedIds = playlists.mapNotNull { it.sourceId }.toSet()
                val remoteOnly = spotifyPlaylists.filter { it.id !in importedIds }
                if (remoteOnly.isNotEmpty()) {
                    item(key = "sp-pl-header") {
                        Text(
                            "Dari Spotify",
                            style = SfStyle(18.sp, 22.sp, FontWeight.Bold),
                            color = Tok.Label1,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                        )
                    }
                    listItems(remoteOnly, key = { "sp-pl-${it.id}" }) { sp ->
                        LibraryArtRow(
                            art = sp.cover,
                            title = sp.name,
                            subtitle = songCount(sp.trackCount),
                            artSize = 60.dp,
                            rowHeight = 76.dp,
                            onClick = {
                                ui.spotifyImportOpen = true
                                app.importer.import("https://open.spotify.com/playlist/${sp.id}")
                            },
                        )
                    }
                }
                if (playlists.isEmpty() && remoteOnly.isEmpty()) {
                    item(key = "no-playlists") {
                        LibraryEmptyState(
                            icon = SfLibraryIcons.MusicNoteList,
                            title = "Belum ada playlist",
                            body = "Buat playlist pertamamu untuk mulai mengumpulkan lagu.",
                            action = "Buat playlist",
                            onAction = { creating = true },
                        )
                    }
                }
            }
            LibraryPage.Artists -> {
                listItems(artists, key = { "ar-${it.name}" }) { a ->
                    LibraryArtRow(
                        art = a.thumbs,
                        title = a.name,
                        subtitle = null,
                        artSize = 48.dp,
                        rowHeight = 56.dp,
                        circle = true,
                        onClick = { a.artistId?.let { ui.navigate(Routes.artist(it)) } ?: app.player.say("Artis belum bisa dibuka") },
                    )
                }
            }
            LibraryPage.Albums -> {
                listItems(albums.chunked(2), key = { "al-${it.first().name}" }) { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                    ) {
                        pair.forEach { a ->
                            Box(Modifier.weight(1f)) {
                                RecentTile(
                                    RecentItem("al-${a.name}", a.name, listOfNotNull(a.artist, songCount(a.count)).joinToString(" · "), a.thumbs, a.albumId?.let(Routes::album)),
                                ) {
                                    if (a.albumId != null) ui.navigate(Routes.album(a.albumId!!))
                                    else app.player.say("Album belum bisa dibuka")
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            LibraryPage.Songs, LibraryPage.Liked -> {
                item(key = "song-actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        PlayButton(MIcons.Play, "Putar", Modifier.weight(1f)) { if (liked.isNotEmpty()) app.player.playTracks(liked, 0) }
                        PlayButton(MIcons.Shuffle, "Acak", Modifier.weight(1f)) {
                            if (liked.isNotEmpty()) app.player.playTracks(liked.shuffled(), 0)
                        }
                    }
                }
                listItemsIndexed(liked, key = { i, t -> "song-${t.videoId}-$i" }) { i, t ->
                    LibrarySongRow(t, liked, i)
                }
                if (liked.isEmpty()) {
                    item(key = "no-songs") {
                        LibraryEmptyState(
                            icon = SfLibraryIcons.Heart,
                            title = "Belum ada lagu",
                            body = "Tekan ikon hati di lagu mana pun, nanti muncul di sini.",
                            action = "Cari lagu",
                            onAction = { ui.navigate(Routes.Search) },
                        )
                    }
                }
            }
            LibraryPage.Downloads -> {
                val offline = downloadedTracks.filter { t ->
                    val s = downloadStates[t.videoId]
                    s != null && (s.isCompleted || s.isActive)
                }
                if (offline.isNotEmpty()) {
                    item(key = "dl-actions") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            PlayButton(MIcons.Play, "Putar", Modifier.weight(1f)) { app.player.playTracks(offline, 0) }
                            PlayButton(MIcons.Shuffle, "Acak", Modifier.weight(1f)) { app.player.playTracks(offline.shuffled(), 0) }
                        }
                    }
                }
                listItemsIndexed(offline, key = { i, t -> "dl-${t.videoId}-$i" }) { i, t ->
                    LibrarySongRow(t, offline, i)
                }
                if (offline.isEmpty()) {
                    item(key = "no-downloads") {
                        LibraryEmptyState(
                            icon = MIcons.Download,
                            title = "Belum ada unduhan",
                            body = "Buka menu ⋯ di lagu atau playlist lalu pilih “Unduh untuk offline”.",
                            action = "Cari lagu",
                            onAction = { ui.navigate(Routes.Search) },
                        )
                    }
                }
            }
            else -> Unit
        }
    }

    if (creating) {
        TextPromptDialog("Playlist baru", "Buat", onDismiss = { creating = false }, onConfirm = {
            val pl = app.library.createPlaylist(it)
            creating = false
            ui.navigate(Routes.playlist(pl.id))
        })
    }
}

@Composable
private fun LibraryArtRow(
    art: String?,
    title: String,
    subtitle: String?,
    artSize: Dp,
    rowHeight: Dp,
    circle: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(rowHeight).pressable { onClick() }.padding(start = 16.dp),
    ) {
        val shape = if (circle) CircleShape else RoundedCornerShape(5.dp)
        Artwork(art, Modifier.size(artSize).clip(shape).background(Tok.Fill1))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text(title, style = SfStyle(17.sp, 22.sp, tracking = (-0.43).sp), color = Tok.Label1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp), color = Tok.Label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Chevron(Modifier.align(Alignment.CenterEnd))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(0.33.dp).background(Tok.Hairline))
        }
    }
}

@Composable
private fun PlayButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tappable(
        onClick = onClick,
        modifier = modifier.height(50.dp).clip(CircleShape).background(Color(0xFF767680).copy(alpha = 0.12f)),
        description = label,
    ) {
        Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Tok.Accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = SfStyle(17.sp, 22.sp, tracking = (-0.43).sp), color = Tok.Accent)
        }
    }
}

@Composable
private fun LibrarySongRow(track: Track, queue: List<Track>, index: Int) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val state by app.player.ui.collectAsState()
    val isCurrent = state.current?.videoId == track.videoId
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(64.dp).pressable {
            if (isCurrent) app.player.toggle() else app.player.playTracks(queue, index)
        }.padding(start = 16.dp),
    ) {
        Artwork(track.thumbs, Modifier.size(48.dp).clip(RoundedCornerShape(5.dp)).background(Tok.Fill1))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = SfStyle(17.sp, 22.sp, tracking = (-0.43).sp), color = if (isCurrent) Tok.Accent else Tok.Label1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!track.artist.isNullOrBlank()) {
                Text(track.artist!!, style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp), color = Tok.Label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(36.dp).pressable { ui.openMenu(track) }, contentAlignment = Alignment.Center) {
            Icon(MIcons.Ellipsis, null, tint = Tok.Label1, modifier = Modifier.size(20.dp))
        }
    }
}

private fun songCount(n: Int): String = if (n == 1) "1 lagu" else "$n lagu"

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val history by app.library.playHistory.collectAsState()
    val enabled by app.library.historyEnabled.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    val sections = remember(history) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val format = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("id", "ID"))
        history.mapNotNull { entry ->
            val date = runCatching {
                Instant.ofEpochMilli(entry.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull() ?: return@mapNotNull null
            val label = when (date) {
                today -> "Hari ini"
                yesterday -> "Kemarin"
                else -> format.format(date)
            }
            label to entry
        }.groupBy({ it.first }, { it.second }).toList()
    }
    val queue = remember(history) { history.map { it.toTrack() } }
    val disabledHeader: (@Composable () -> Unit)? = if (!enabled) {
        {
            Text(
                "Penyimpanan riwayat sedang dimatikan. Lagu yang diputar sekarang tidak akan dicatat.",
                color = Tok.Label2,
                style = SfStyle(13.sp, 18.sp, tracking = (-0.08).sp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    } else null

    LargeTitleList(
        "Riwayat",
        titleBottom = 10.dp,
        header = disabledHeader,
        leading = {
            Row(
                Modifier.pressable(onClick = onBack).padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MIcons.Back, null, tint = Tok.Label1, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
                Text("Pustaka", style = SfStyle(17.sp, 22.sp, FontWeight.Medium), color = Tok.Label1, maxLines = 1)
            }
        },
        actions = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Tok.Fill2).pressable { if (history.isNotEmpty()) confirmClear = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MIcons.Trash, null, tint = Tok.Label1, modifier = Modifier.size(20.dp))
            }
        },
    ) {
        if (history.isEmpty()) {
            item(key = "history-empty") {
                LibraryEmptyState(
                    icon = SfLibraryIcons.Clock,
                    title = "Belum ada riwayat",
                    body = "Lagu yang kamu putar minimal 30 detik akan muncul di sini.",
                    action = "Cari lagu",
                    onAction = { ui.navigate(Routes.Search) },
                )
            }
        } else {
            var offset = 0
            sections.forEach { (label, entries) ->
                item(key = "day-$label") {
                    Text(
                        label,
                        style = SfStyle(20.sp, 25.sp, FontWeight.SemiBold, (-0.4).sp),
                        color = Tok.Label2,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                    )
                }
                listItemsIndexed(entries, key = { i, e -> "h-${e.id}-${e.playedAt}-$i" }) { i, entry ->
                    val index = offset + i
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { TrackRow(entry.toTrack(), queue, index) }
                        Box(
                            Modifier.size(44.dp).pressable { app.library.removeHistoryEntry(entry.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MIcons.Trash, null, tint = Tok.Label3, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                offset += entries.size
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Tok.Surface,
            shape = RoundedCornerShape(26.dp),
            title = { Text("Hapus semua riwayat?", color = Tok.Label1, fontWeight = FontWeight.Bold) },
            text = { Text("Semua lagu dalam riwayat akan dihapus dari perangkat ini.", color = Tok.Label2) },
            confirmButton = {
                TextButton(onClick = { app.library.clearHistory(); confirmClear = false }) {
                    Text("Hapus", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal", color = Tok.Label2) } },
        )
    }
}

/** Play / Shuffle pair used above any song list. */
@Composable
fun PlayShuffleRow(tracks: List<Track>, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Putar", MIcons.Play, filled = true, modifier = Modifier.weight(1f)) {
            if (tracks.isNotEmpty()) app.player.playTracks(tracks, 0)
        }
        PillButton("Acak", MIcons.Shuffle, filled = false, modifier = Modifier.weight(1f)) {
            if (tracks.isNotEmpty()) {
                app.player.playTracks(tracks.shuffled(), 0)
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tappable(onClick, modifier.height(46.dp).clip(CircleShape).background(if (filled) Tok.AccentFill else Tok.Fill2), description = label) {
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
