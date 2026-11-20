package app.musiku.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.ArtworkMeshBackground
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.ConvxCollectionHeader
import app.musiku.ui.components.ConvxGridRail
import app.musiku.ui.components.ConvxSectionHeader
import app.musiku.ui.components.ConvxSongRow
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.FeaturedReleaseCard
import app.musiku.ui.components.ArtistActionRow
import app.musiku.ui.components.ArtistHeroImage
import app.musiku.ui.components.ArtistVideo
import app.musiku.ui.components.ArtistWordmark
import app.musiku.ui.components.ArtistWordmarkFootInset
import app.musiku.ui.components.ArtistWordmarkHeight
import app.musiku.ui.components.HeroZoom
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.SfIcon
import app.musiku.ui.components.TextPromptDialog
import app.musiku.ui.components.TrackRow
import app.musiku.ui.components.artistPageColor
import app.musiku.ui.components.pressable
import app.musiku.ui.components.heroPullZoom
import app.musiku.ui.components.rememberHeroZoom
import app.musiku.ui.components.rememberArtistArtworkBg
import app.musiku.ui.components.rememberArtistVideoPlayer
import app.musiku.ui.components.rememberArtistVideoUrl
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.components.rememberPlaylistAppearance
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.SfDetailIcons
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glassDialogColor
import kotlinx.coroutines.launch

/** Art-tinted backdrop kept for the album page (not yet ported to the Convx collection header). */
@Composable
private fun Backdrop(img: String?, modifier: Modifier = Modifier) {
    val lowDevice = LocalLowDevice.current
    Box(modifier.fillMaxWidth().height(440.dp)) {
        Box(Modifier.fillMaxSize().then(if (lowDevice) Modifier else Modifier.blur(64.dp)).alpha(0.5f)) {
            Artwork(img, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Color.Transparent, 1f to Tok.Window)))
    }
}

@Composable
private fun MediaHeader(
    art: String?,
    kind: String,
    title: String,
    meta: String?,
    zoom: HeroZoom,
    round: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Backdrop(art)
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val shape = if (round) CircleShape else RoundedCornerShape(20.dp)
            Artwork(
                art,
                Modifier.size(208.dp)
                    .graphicsLayer { scaleX = zoom.scale; scaleY = zoom.scale }
                    .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(Tok.Fill1),
            )
            Text(kind.uppercase(), color = Tok.Label3, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.84.sp, modifier = Modifier.padding(top = 24.dp))
            Text(title, color = Tok.Label1, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.73).sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            if (!meta.isNullOrBlank()) Text(meta, color = Tok.Label2, fontSize = 15.sp, lineHeight = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.padding(top = 20.dp, bottom = 8.dp)) { actions() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailFrame(onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            content()
        }
        BackButton(onBack)
    }
}

@Composable
fun AlbumScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    var attempt by remember { mutableIntStateOf(0) }
    val load by rememberLoad(id to attempt) { app.yt.album(id) }
    val heroZoom = rememberHeroZoom()
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { MusikuLoading() }
            is Load.Err -> EmptyState("Album tidak ditemukan", "Mungkin sudah dihapus atau koneksi bermasalah.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val a = l.value
                LazyColumn(contentPadding = PaddingValues(bottom = DockInset), modifier = Modifier.heroPullZoom(heroZoom)) {
                    item {
                        MediaHeader(
                            a.thumbs, "Album", a.title,
                            listOfNotNull(a.artist, a.year, if (a.tracks.isNotEmpty()) "${a.tracks.size} lagu" else null, a.durationLabel).joinToString(" · "),
                            zoom = heroZoom,
                        ) { PlayShuffleRow(a.tracks) }
                    }
                    itemsIndexed(a.tracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                        TrackRow(t.copy(album = t.album ?: a.title, albumId = a.browseId), a.tracks, i, number = i + 1, showArt = false)
                    }
                }
            }
        }
    }
}

/** Convx-style overflow menu, kept in the same sheet chrome as the rest of Musiku. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionMenuSheet(title: String, actions: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tok.Surface,
        contentColor = Tok.Label1,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            Text(title, color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            actions.forEach { (label, action) ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .pressCompat { action() }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = if (label == "Hapus") Tok.Accent else Tok.Label1, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Modifier.pressCompat(onClick: () -> Unit): Modifier =
    pressable(onClick = onClick)

@Composable
fun ArtistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val load by rememberLoad(id) { app.yt.artist(id) }
    val heroZoom = rememberHeroZoom()
    val state by app.player.ui.collectAsState()
    val bookmarks by app.library.bookmarkedArtists.collectAsState()
    val videoEnabled by app.library.artistVideoEnabled.collectAsState()
    val lowDevice = LocalLowDevice.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { MusikuLoading() }
            is Load.Err -> EmptyState("Artis tidak ditemukan", "Mungkin sudah dihapus atau koneksi bermasalah.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val a = l.value
                val art = a.avatar ?: a.banner ?: a.thumbs
                val artworkBg = rememberArtistArtworkBg(art)
                val tint = artistPageColor(artworkBg)
                val tracks = a.topSongs
                val playingHere = state.playing && tracks.any { it.videoId == state.current?.videoId }
                val featured = a.albums.firstOrNull() ?: a.singles.firstOrNull()
                val topChrome = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
                val motionSong = a.topSongs.firstOrNull()
                val motionUrl = rememberArtistVideoUrl(
                    artist = a.name,
                    song = motionSong?.title,
                    album = motionSong?.album,
                    duration = motionSong?.duration,
                    enabled = videoEnabled && !lowDevice,
                )
                val motionPlayer = motionUrl?.let { rememberArtistVideoPlayer(it) }
                LaunchedEffect(motionPlayer, heroVisible, resumed) {
                    motionPlayer?.active = heroVisible && resumed
                }

                Box(Modifier.fillMaxSize().background(tint)) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = DockInset),
                        modifier = Modifier.heroPullZoom(heroZoom),
                    ) {
                        item(key = "artist_header") {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val side = maxWidth
                                ArtistHeroImage(
                                    artworkUrl = art,
                                    pageColor = tint,
                                    heroScale = { heroZoom.scale },
                                    overlay = {
                                        val player = motionPlayer
                                        if (player != null) {
                                            val videoAlpha by animateFloatAsState(
                                                targetValue = if (player.isReady) 1f else 0f,
                                                animationSpec = tween(400),
                                                label = "artistVideoAlpha",
                                            )
                                            ArtistVideo(player, Modifier.fillMaxSize().alpha(videoAlpha))
                                        }
                                    },
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = (side - ArtistWordmarkFootInset - ArtistWordmarkHeight - topChrome).coerceAtLeast(0.dp)),
                                ) {
                                    ArtistWordmark(a.name)
                                    Spacer(Modifier.height(18.dp))
                                    ArtistActionRow(
                                        isPlaying = playingHere,
                                        pageBgRgb = artworkBg,
                                        onInfo = {
                                            scope.launch {
                                                listState.animateScrollToItem(
                                                    (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                                                )
                                            }
                                        },
                                        onPlay = {
                                            if (playingHere || (tracks.isNotEmpty() && state.current?.videoId == tracks.first().videoId)) state.let { app.player.toggle() }
                                            else if (tracks.isNotEmpty()) app.player.playTracks(tracks, 0)
                                        },
                                        onSave = { app.library.toggleArtistBookmark(a.browseId) },
                                        saved = a.browseId in bookmarks,
                                    )
                                    if (featured != null) {
                                        Spacer(Modifier.height(15.dp))
                                        FeaturedReleaseCard(
                                            caption = featured.year ?: "Rilis",
                                            title = featured.title,
                                            subtitle = featured.artist,
                                            artwork = featured.thumbs,
                                            pageBgRgb = artworkBg,
                                            onClick = { ui.navigate(Routes.album(featured.browseId)) },
                                            modifier = Modifier.padding(horizontal = 20.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }

                        if (tracks.isNotEmpty()) {
                            item(key = "artist_songs_title") {
                                ConvxSectionHeader("Lagu Populer", modifier = Modifier.padding(top = 8.dp))
                            }
                            itemsIndexed(tracks, key = { i, t -> "artist_${t.videoId}_$i" }) { i, t ->
                                ConvxSongRow(
                                    title = t.title,
                                    subtitle = listOfNotNull(t.artist, t.duration).joinToString(" · ").ifEmpty { null },
                                    artwork = t.thumbs,
                                    leadingDivider = i == 0,
                                    divider = i != tracks.lastIndex,
                                    onClick = {
                                        if (state.current?.videoId == t.videoId) app.player.toggle()
                                        else app.player.playTracks(tracks, i)
                                    },
                                    onLongClick = { ui.openMenu(t) },
                                    trailing = {
                                        Box(Modifier.pressable(onClick = { ui.openMenu(t) })) {
                                            SfIcon(SfDetailIcons.Ellipsis, 22.dp, Color.White.copy(alpha = 0.6f), "Opsi")
                                        }
                                    },
                                )
                            }
                        }

                        if (a.albums.isNotEmpty()) {
                            item(key = "artist_albums") {
                                Column {
                                    ConvxSectionHeader("Album")
                                    ConvxGridRail(
                                        items = a.albums.map { Triple(it.title, it.year ?: it.artist, it.thumbs) },
                                        onOpen = { i -> ui.navigate(Routes.album(a.albums[i].browseId)) },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                        if (a.singles.isNotEmpty()) {
                            item(key = "artist_singles") {
                                Column {
                                    ConvxSectionHeader("Single & EP")
                                    ConvxGridRail(
                                        items = a.singles.map { Triple(it.title, it.year ?: it.artist, it.thumbs) },
                                        onOpen = { i -> ui.navigate(Routes.album(a.singles[i].browseId)) },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                        item(key = "artist_about") {
                            Column {
                                ConvxSectionHeader("Tentang")
                                val about = listOfNotNull(
                                    a.monthlyListeners?.let { "$it pendengar bulanan" },
                                    a.subscribers?.let { "$it pelanggan" },
                                ).joinToString(" · ")
                                if (about.isNotBlank()) {
                                    Text(about, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp))
                                } else {
                                    Text(a.name, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp,
                                        modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val playlists by app.library.playlists.collectAsState()
    val state by app.player.ui.collectAsState()
    val pl = playlists.firstOrNull { it.id == id }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val heroZoom = rememberHeroZoom()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val exitSearch = {
        focusManager.clearFocus()
        isSearching = false
        query = TextFieldValue()
    }
    val filteredTracks = remember(pl?.tracks, query) {
        val source = pl?.tracks.orEmpty()
        if (query.text.isEmpty()) source else source.filter { t ->
            t.title.contains(query.text, ignoreCase = true) ||
                t.artist.orEmpty().contains(query.text, ignoreCase = true)
        }
    }
    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }
    if (isSearching) BackHandler(onBack = exitSearch)

    DetailFrame(if (isSearching) exitSearch else onBack) {
        if (pl == null) {
            EmptyState("Playlist tidak ditemukan", "Mungkin sudah dihapus.", Modifier.statusBarsPadding().padding(top = 80.dp))
        } else {
            val art = pl.tracks.firstOrNull()?.thumbs
            val appearance = rememberPlaylistAppearance(art)
            val tracks = pl.tracks
            val visibleTracks = if (isSearching) filteredTracks else tracks
            val playingHere = state.playing && tracks.any { it.videoId == state.current?.videoId }

            Box(Modifier.fillMaxSize().background(appearance.background)) {
                ArtworkMeshBackground(art, Modifier.fillMaxSize(), appearance)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = DockInset),
                    modifier = Modifier.heroPullZoom(heroZoom),
                ) {
                    item(key = "playlist_header") {
                        Box(Modifier.statusBarsPadding().padding(top = 64.dp)) {
                            ConvxCollectionHeader(
                                title = pl.name,
                                artwork = art,
                                metadata = listOfNotNull(
                                    if (pl.source == "spotify") "Spotify" else null,
                                    "${pl.tracks.size} lagu",
                                ).joinToString(" · "),
                                foreground = appearance.foreground,
                                playing = playingHere,
                                enabled = tracks.isNotEmpty(),
                                onPlay = {
                                    if (playingHere || (tracks.isNotEmpty() && state.current?.videoId == tracks.first().videoId)) app.player.toggle()
                                    else if (tracks.isNotEmpty()) app.player.playTracks(tracks, 0)
                                },
                                onShuffle = { if (tracks.isNotEmpty()) app.player.playTracks(tracks.shuffled(), 0) },
                                rightIcon = SfDetailIcons.Ellipsis,
                                onRightAction = { menuOpen = true },
                                zoom = heroZoom.scale,
                                onSearch = { isSearching = true },
                                searchValue = query.takeIf { isSearching },
                                onSearchValueChange = { query = it },
                                onSearchImeAction = { focusManager.clearFocus() },
                                searchFocusRequester = focusRequester,
                            )
                        }
                    }
                    if (!isSearching && tracks.isEmpty()) {
                        item { EmptyState("Playlist masih kosong", "Buka menu ⋯ di lagu mana pun lalu pilih “Tambah ke playlist…”.") }
                    }
                    itemsIndexed(visibleTracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                        ConvxSongRow(
                            title = t.title,
                            subtitle = listOfNotNull(t.artist, t.album, t.duration).joinToString(" · ").ifEmpty { null },
                            artwork = t.thumbs,
                            ink = appearance.foreground,
                            isCurrent = state.current?.videoId == t.videoId,
                            leadingDivider = i == 0,
                            divider = i != visibleTracks.lastIndex,
                            onClick = {
                                if (state.current?.videoId == t.videoId) app.player.toggle()
                                else app.player.playTracks(visibleTracks, i)
                            },
                            onLongClick = { ui.openMenu(t, onRemove = { app.library.removeFromPlaylist(pl.id, i) }) },
                            trailing = {
                                Box(Modifier.pressable(onClick = { ui.openMenu(t, onRemove = { app.library.removeFromPlaylist(pl.id, i) }) })) {
                                    SfIcon(
                                        SfDetailIcons.Ellipsis, 22.dp,
                                        appearance.foreground.copy(alpha = 0.6f), "Opsi",
                                    )
                                }
                            },
                        )
                    }
                }
            }
            if (menuOpen) {
                val actions = buildList {
                    if (pl.sourceId != null) add("Sync dari Spotify" to {
                        app.importer.sync(pl)
                        ui.spotifyImportOpen = true
                    })
                    add("Ganti nama" to { renaming = true })
                    add("Hapus playlist" to { confirmDelete = true })
                }
                CollectionMenuSheet(pl.name, actions) { menuOpen = false }
            }
            if (renaming) TextPromptDialog("Ganti nama", "Simpan", initial = pl.name, onDismiss = { renaming = false }, onConfirm = {
                app.library.renamePlaylist(pl.id, it); renaming = false
            })
            if (confirmDelete) AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = glassDialogColor(),
                shape = RoundedCornerShape(26.dp),
                title = { Text("Hapus playlist?", color = Tok.Label1, fontWeight = FontWeight.Bold) },
                text = { Text("“${pl.name}” akan dihapus dari perangkat ini.", color = Tok.Label2) },
                confirmButton = {
                    TextButton(onClick = { app.library.deletePlaylist(pl.id); confirmDelete = false; onBack() }) {
                        Text("Hapus", color = Tok.Accent, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal", color = Tok.Label2) } },
            )
        }
    }
}

@Composable
fun OnlinePlaylistScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val load by rememberLoad(id) { app.yt.playlist(id) }
    val state by app.player.ui.collectAsState()
    val heroZoom = rememberHeroZoom()
    DetailFrame(onBack) {
        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { MusikuLoading() }
            is Load.Err -> EmptyState("Playlist tidak bisa dimuat", "Cek koneksi internet lalu coba lagi.", Modifier.statusBarsPadding().padding(top = 80.dp))
            is Load.Ok -> {
                val p = l.value
                val art = p.thumbs
                val appearance = rememberPlaylistAppearance(art)
                val tracks = p.tracks
                val playingHere = state.playing && tracks.any { it.videoId == state.current?.videoId }
                Box(Modifier.fillMaxSize().background(appearance.background)) {
                    ArtworkMeshBackground(art, Modifier.fillMaxSize(), appearance)
                    LazyColumn(contentPadding = PaddingValues(bottom = DockInset), modifier = Modifier.heroPullZoom(heroZoom)) {
                        item(key = "playlist_header") {
                            Box(Modifier.statusBarsPadding()) {
                                ConvxCollectionHeader(
                                    title = p.title,
                                    artwork = art,
                                    subtitle = p.subtitle,
                                    metadata = if (tracks.isNotEmpty()) "${tracks.size} lagu" else "",
                                    foreground = appearance.foreground,
                                    playing = playingHere,
                                    enabled = tracks.isNotEmpty(),
                                    onPlay = {
                                        if (playingHere || (tracks.isNotEmpty() && state.current?.videoId == tracks.first().videoId)) app.player.toggle()
                                        else if (tracks.isNotEmpty()) app.player.playTracks(tracks, 0)
                                    },
                                    onShuffle = { if (tracks.isNotEmpty()) app.player.playTracks(tracks.shuffled(), 0) },
                                    zoom = heroZoom.scale,
                                )
                            }
                        }
                        itemsIndexed(tracks, key = { i, t -> "${t.videoId}-$i" }) { i, t ->
                            ConvxSongRow(
                                title = t.title,
                                subtitle = listOfNotNull(t.artist, t.album, t.duration).joinToString(" · ").ifEmpty { null },
                                artwork = t.thumbs,
                                ink = appearance.foreground,
                                isCurrent = state.current?.videoId == t.videoId,
                                leadingDivider = i == 0,
                                divider = i != tracks.lastIndex,
                                onClick = {
                                    if (state.current?.videoId == t.videoId) app.player.toggle()
                                    else app.player.playTracks(tracks, i)
                                },
                                onLongClick = { ui.openMenu(t) },
                                trailing = {
                                    Box(Modifier.pressable(onClick = { ui.openMenu(t) })) {
                                        SfIcon(SfDetailIcons.Ellipsis, 22.dp, appearance.foreground.copy(alpha = 0.6f), "Opsi")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
