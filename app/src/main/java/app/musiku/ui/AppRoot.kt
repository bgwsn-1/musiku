package app.musiku.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.musiku.MusikuApp
import app.musiku.data.UpdateInfo
import app.musiku.data.checkForUpdate
import app.musiku.data.dismissUpdate
import app.musiku.data.dismissedUpdateVersion
import app.musiku.data.openInBrowser
import app.musiku.ui.components.AddToPlaylistSheet
import app.musiku.ui.components.BottomTabs
import app.musiku.ui.components.InfoDialog
import app.musiku.ui.components.MiniPlayer
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.TrackMenu
import app.musiku.ui.components.UpdatePrompt
import app.musiku.ui.nowplaying.NowPlayingActions
import app.musiku.ui.nowplaying.NowPlayingBackground
import app.musiku.ui.nowplaying.NowPlayingScreen
import app.musiku.ui.nowplaying.NowPlayingState
import app.musiku.ui.nowplaying.LandingEchoShare
import app.musiku.ui.nowplaying.LocalPlayerMorphProgress
import app.musiku.ui.nowplaying.PlayerMorph
import app.musiku.ui.nowplaying.PlayerMorphHost
import app.musiku.ui.nowplaying.playerBackgroundAlpha
import app.musiku.ui.nowplaying.playerContentAlpha
import app.musiku.ui.screens.AlbumScreen
import app.musiku.ui.screens.ArtistScreen
import app.musiku.ui.screens.GenreScreen
import app.musiku.ui.screens.HomeScreen
import app.musiku.ui.screens.LibraryScreen
import app.musiku.ui.screens.SettingsScreen
import app.musiku.ui.screens.LyricsPanel
import app.musiku.ui.screens.OnlinePlaylistScreen
import app.musiku.ui.screens.PlaylistScreen
import app.musiku.ui.screens.QueuePanel
import app.musiku.ui.screens.SearchScreen
import app.musiku.ui.screens.activeLyricIndex
import app.musiku.ui.screens.rememberLyrics
import app.musiku.ui.theme.LocalGlassBackdrop
import app.musiku.ui.theme.LocalHazeState
import app.musiku.ui.theme.Tok
import app.musiku.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import app.musiku.ui.glass.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TabRoutes = setOf(Routes.Home, Routes.Search, Routes.Library)

@Composable
fun AppRoot(app: MusikuApp) {
    val ui = remember { UiState() }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ps by app.player.ui.collectAsState()
    val scope = rememberCoroutineScope()

    ui.navigate = { r -> nav.navigate(r) { launchSingleTop = true } }

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        app.player.messages.collect { m ->
            toast = m
            delay(1900)
            if (toast == m) toast = null
        }
    }

    // Update prompt: one quiet GitHub check a few seconds after launch, never blocking, never
    // repeated for a version the user already waved away.
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateVisible by remember { mutableStateOf(false) }
    val versionName = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    LaunchedEffect(versionName) {
        delay(5_000)
        val info = runCatching { checkForUpdate(versionName) }.getOrNull() ?: return@LaunchedEffect
        if (dismissedUpdateVersion(ctx) == info.version) return@LaunchedEffect
        update = info
        updateVisible = true
    }
    // Settings asks on demand: ignore the earlier "Nanti", since this is an explicit tap.
    SideEffect {
        ui.checkUpdate = {
            scope.launch {
                val msg = "Memeriksa pembaruan…"
                toast = msg
                val info = runCatching { checkForUpdate(versionName) }.getOrNull()
                if (info != null) {
                    update = info
                    updateVisible = true
                    if (toast == msg) toast = null
                } else {
                    toast = "Kamu sudah di versi terbaru ($versionName)"
                    delay(1_900)
                    if (toast == "Kamu sudah di versi terbaru ($versionName)") toast = null
                }
            }
        }
    }
    // The web build's CSS px are the source of truth: 15px text, 28px large title, 16px gutters.
    // Compose sp would otherwise grow/shrink with the system font setting, so the layout would
    // drift away from the web on any phone where the user changed font size. Pin fontScale to 1
    // so the Compose tree measures exactly like the CSS.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalApp provides app,
        LocalUi provides ui,
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        // Liquid glass: the page content is drawn into one layer that every floating glass surface
        // (dock, tab bar, mini player) blurs behind itself — the Compose equivalent of the web's
        // `backdrop-filter`. The haze node must wrap the content, never a glass surface.
        val hazeState = remember { HazeState() }
        // The vendored Kyant backdrop records the routed page into its own layer; every glass
        // surface samples that layer (blur + AGSL lens) instead of approximating a backdrop.
        val glassBackdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().background(Tok.Window)) {
            CompositionLocalProvider(LocalHazeState provides hazeState, LocalGlassBackdrop provides glassBackdrop) {
                Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop).haze(hazeState)) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.Home,
                        enterTransition = { enter() },
                        exitTransition = { exit() },
                        popEnterTransition = { popEnter() },
                        popExitTransition = { popExit() },
                    ) {
                        composable(Routes.Home) { HomeScreen() }
                        composable(Routes.Search) { SearchScreen() }
                        composable(Routes.Library) { LibraryScreen() }
                        composable(Routes.Settings) {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            AlbumScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("artist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            ArtistScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("playlist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            PlaylistScreen(it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable(
                            "genre/{id}/{params}/{title}",
                            listOf(
                                navArgument("id") { type = NavType.StringType },
                                navArgument("params") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                            ),
                        ) {
                            GenreScreen(
                                browseId = it.arguments?.getString("id").orEmpty(),
                                params = android.net.Uri.decode(it.arguments?.getString("params").orEmpty()),
                                title = android.net.Uri.decode(it.arguments?.getString("title").orEmpty()),
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("oplaylist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                            OnlinePlaylistScreen(
                                id = android.net.Uri.decode(it.arguments?.getString("id").orEmpty()),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }

                // floating dock: mini player (when something is queued) + tab bar.
                // Settings is a page pushed over the tabs, the way Convx renders it.
                if (route != Routes.Settings) Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ps.current != null) MiniPlayer(onOpen = { ui.nowPlayingOpen = true })
                    BottomTabs(
                        selected = when {
                            route in TabRoutes -> route
                            else -> nav.previousBackStackEntry?.destination?.route?.takeIf { it in TabRoutes } ?: Routes.Home
                        },
                        onSelect = { selectTab(nav, it) },
                        // Convx echoes 40% of the pill's landing dip through the bar, so the dock
                        // lands as one piece instead of the pill bouncing alone.
                        modifier = Modifier.graphicsLayer {
                            translationY = PlayerMorph.landingDipPx * LandingEchoShare
                        },
                    )
                }
            }

            // full-screen player: Convx's container morph (pill <-> screen), not a sheet slide.
            PlayerMorphHost(
                visible = ui.nowPlayingOpen && ps.current != null,
                artworkUrl = ps.current?.thumbs,
            ) {
                NowPlayingHost(app, ui)
            }

            // update prompt
            AnimatedVisibility(
                visible = updateVisible && !ui.nowPlayingOpen,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { -it / 3 },
                exit = fadeOut(tween(150)),
            ) {
                update?.let { info ->
                    UpdatePrompt(
                        info = info,
                        current = versionName,
                        haze = hazeState,
                        onDownload = {
                            openInBrowser(ctx, info.apkUrl ?: info.pageUrl)
                            updateVisible = false
                        },
                        onDetails = { openInBrowser(ctx, info.pageUrl) },
                        onDismiss = {
                            scope.launch { dismissUpdate(ctx, info.version) }
                            updateVisible = false
                        },
                    )
                }
            }

            // toast
            AnimatedVisibility(
                visible = toast != null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { -it / 2 },
                exit = fadeOut(tween(160)),
            ) {
                Text(
                    toast.orEmpty(), color = Tok.Label1, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(CircleShape).background(Tok.Surface).border(1.dp, Tok.Hairline, CircleShape).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }

            ui.menuTrack?.let { t ->
                TrackMenu(t, ui.menuRemove, hazeState) {
                    ui.menuTrack = null
                    ui.menuRemove = null
                    ui.menuAnchor = null
                }
            }
            ui.addToPlaylist?.let { t -> AddToPlaylistSheet(t) { ui.addToPlaylist = null } }
            ui.infoTrack?.let { t -> InfoDialog(t) { ui.infoTrack = null } }
        }
    }
}

private fun selectTab(nav: androidx.navigation.NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val spec = tween<androidx.compose.ui.unit.IntOffset>(340, easing = FastOutSlowInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.bothTabs() =
    initialState.destination.route in TabRoutes && targetState.destination.route in TabRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (bothTabs()) fadeIn(tween(200)) else slideInHorizontally(spec) { it } + fadeIn(tween(200))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (bothTabs()) fadeOut(tween(120)) else slideOutHorizontally(spec) { -it / 4 } + fadeOut(tween(250))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    if (bothTabs()) fadeIn(tween(200)) else slideInHorizontally(spec) { -it / 4 } + fadeIn(tween(250))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    if (bothTabs()) fadeOut(tween(120)) else slideOutHorizontally(spec) { it } + fadeOut(tween(200))

/** Player + the lyrics / queue panels that slide over it. */
@Composable
private fun NowPlayingHost(app: MusikuApp, ui: UiState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ps by app.player.ui.collectAsState()
    val pos by app.player.position.collectAsState()
    val liked by app.library.liked.collectAsState()
    val cf by app.library.crossfade.collectAsState()
    val cfSec by app.library.cfSec.collectAsState()
    val track = ps.current ?: return
    val lyrics by rememberLyrics(track, ps.durationSec)

    BackHandler(enabled = ui.panel != null) { ui.panel = null }
    BackHandler(enabled = ui.panel == null) { ui.nowPlayingOpen = false }

    val line = when {
        lyrics == null -> "Memuat lirik…"
        lyrics!!.synced -> lyrics!!.lines.let { l -> activeLyricIndex(l, pos).let { i -> if (i >= 0) l[i].text.ifEmpty { "♪" } else "♪" } }
        else -> ""
    }

    val state = NowPlayingState(
        track = track,
        playing = ps.playing,
        positionSec = pos,
        durationSec = ps.durationSec,
        volume = ps.volume,
        shuffle = ps.shuffle,
        repeat = ps.repeat,
        liked = liked.any { it.videoId == track.videoId },
        lyricLine = line,
        crossfade = cf,
        cfSec = cfSec,
        sleepLabel = ps.sleepLabel,
        lyricsOpen = ui.panel == Panel.Lyrics,
        queueOpen = ui.panel == Panel.Queue,
    )

    val p = app.player
    val actions = NowPlayingActions(
        onClose = { ui.nowPlayingOpen = false },
        onPlayPause = p::toggle,
        onPrev = p::prev,
        onNext = p::next,
        onSeek = p::seek,
        onVolume = p::setVolume,
        onShuffle = p::toggleShuffle,
        onRepeat = p::cycleRepeat,
        onLyrics = { ui.panel = if (ui.panel == Panel.Lyrics) null else Panel.Lyrics },
        onQueue = { ui.panel = if (ui.panel == Panel.Queue) null else Panel.Queue },
        onShare = { scope.launch { runCatching { shareTrack(ctx, track, if (ps.durationSec > 0) pos / ps.durationSec else 0f) } } },
        onPlayNext = { p.enqueue(track, next = true) },
        onAddToQueue = { p.enqueue(track, next = false) },
        onLike = { p.toggleLike(track) },
        onAddToPlaylist = { ui.addToPlaylist = track },
        onOpenAlbum = track.albumId?.let { id -> { ui.nowPlayingOpen = false; ui.navigate(Routes.album(id)) } },
        onOpenArtist = track.artistId?.let { id -> { ui.nowPlayingOpen = false; ui.navigate(Routes.artist(id)) } },
        onInfo = { ui.infoTrack = track },
        onSleep = p::setSleep,
        onCrossfade = p::toggleCrossfade,
        onCfSec = p::setCfSec,
    )

    // Convx records the field and the page as two layers; the flight fades them on separate
    // schedules. The morph progress is read in draw phase, so the player never recomposes.
    val morphProgress: () -> Float = LocalPlayerMorphProgress.current

    Box(Modifier.fillMaxSize()) {
        NowPlayingScreen(
            state,
            actions,
            backgroundAlpha = { playerBackgroundAlpha(morphProgress()) },
            contentAlpha = { playerContentAlpha(morphProgress()) },
        )

        AnimatedVisibility(
            visible = ui.panel != null,
            // A panel is part of the same recorded page in Convx, so it fades with it.
            modifier = Modifier.graphicsLayer { alpha = playerContentAlpha(morphProgress()) },
            enter = slideInVertically(tween(360, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250)),
        ) {
            val shown = remember { mutableStateOf(ui.panel) }
            if (ui.panel != null) shown.value = ui.panel
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NowPlayingBackground(track.thumbs, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    when (shown.value) {
                        Panel.Lyrics -> LyricsPanel(track) { ui.panel = null }
                        Panel.Queue -> QueuePanel { ui.panel = null }
                        null -> Unit
                    }
                }
            }
        }
    }
}
