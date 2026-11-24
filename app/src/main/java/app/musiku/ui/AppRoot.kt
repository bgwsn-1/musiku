package app.musiku.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import app.musiku.data.ApkUpdater
import app.musiku.data.ReleaseNotes
import app.musiku.data.UpdateDownload
import app.musiku.data.UpdateInfo
import app.musiku.data.UpdateNotifier
import app.musiku.data.checkForUpdate
import app.musiku.data.dismissUpdate
import app.musiku.data.dismissedUpdateVersion
import app.musiku.data.isNewerVersion
import app.musiku.data.lastSeenVersion
import app.musiku.data.markUpdateNotified
import app.musiku.data.openInBrowser
import app.musiku.data.releaseNotesBetween
import app.musiku.data.setLastSeenVersion
import app.musiku.ui.components.AddToPlaylistSheet
import app.musiku.ui.components.ChangelogDialog
import app.musiku.ui.components.InfoDialog
import app.musiku.ui.components.MorphDock
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.rememberDockScrollConnection
import app.musiku.ui.components.SpotifyImportDialog
import app.musiku.ui.components.TrackMenu
import app.musiku.ui.components.UpdatePrompt
import app.musiku.ui.nowplaying.NowPlayingActions
import app.musiku.ui.nowplaying.NowPlayingBackground
import app.musiku.ui.nowplaying.NowPlayingScreen
import app.musiku.ui.nowplaying.NowPlayingState
import app.musiku.ui.nowplaying.LocalPlayerMorphProgress
import app.musiku.ui.nowplaying.PlayerMorph
import app.musiku.ui.nowplaying.PlayerMorphHost
import app.musiku.ui.nowplaying.PlayerLook
import app.musiku.ui.nowplaying.PlayerBackgroundStyle
import app.musiku.ui.nowplaying.playerBackgroundAlpha
import app.musiku.ui.nowplaying.playerContentAlpha
import app.musiku.ui.screens.AlbumScreen
import app.musiku.ui.screens.ArtistScreen
import app.musiku.ui.screens.EqualizerScreen
import app.musiku.ui.screens.ExploreScreen
import app.musiku.ui.screens.GenreScreen
import app.musiku.ui.screens.HomeScreen
import app.musiku.ui.screens.LibraryScreen
import app.musiku.ui.screens.SettingsScreen
import app.musiku.ui.screens.LyricsPanel
import app.musiku.ui.screens.OnlinePlaylistScreen
import app.musiku.ui.screens.PlaylistScreen
import app.musiku.ui.screens.PlayerDesignScreen
import app.musiku.ui.screens.QueuePanel
import app.musiku.ui.screens.RadioScreen
import app.musiku.ui.screens.RadioStationScreen
import app.musiku.ui.screens.SearchScreen
import app.musiku.ui.screens.SpotifyLibraryScreen
import app.musiku.ui.screens.SpotifyLoginScreen
import app.musiku.ui.screens.StatsScreen
import app.musiku.ui.screens.activeLyricIndex
import app.musiku.ui.screens.rememberLyrics
import app.musiku.ui.theme.LocalGlassBackdrop
import app.musiku.ui.theme.LocalGlassFrozen
import app.musiku.ui.theme.LocalHazeState
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.Tok
import app.musiku.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import app.musiku.ui.glass.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TabRoutes = setOf(Routes.Home, Routes.Search, Routes.Radio, Routes.Library, Routes.Explore)

@Composable
fun AppRoot(app: MusikuApp, lowDevice: Boolean) {
    val ui = remember { UiState() }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ps by app.player.ui.collectAsState()
    val scope = rememberCoroutineScope()
    // Convx's scroll-driven dock: lists anywhere in the NavHost collapse it to the inline row.
    val dockScroll = rememberDockScrollConnection()
    // Convx's rememberStickySelectedRoute: the inline circle keeps the last content tab.
    var lastContentTab by remember { mutableStateOf(Routes.Home) }
    LaunchedEffect(route) {
        if (route == Routes.Home || route == Routes.Radio || route == Routes.Library || route == Routes.Explore) {
            lastContentTab = route
        }
    }

    ui.navigate = { r -> nav.navigate(r) { launchSingleTop = true } }

    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        app.player.messages.collect { m ->
            toast = m
            delay(1900)
            if (toast == m) toast = null
        }
    }

    // Spotify share-sheet / VIEW intent: open the import sheet already working on the link.
    LaunchedEffect(Unit) {
        app.pendingSpotify.collect { link ->
            if (link != null) {
                ui.spotifyImportOpen = true
                app.importer.import(link)
                app.pendingSpotify.value = null
            }
        }
    }

    // Update prompt: one quiet GitHub check a few seconds after launch, never blocking, never
    // repeated for a version the user already waved away.
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateVisible by remember { mutableStateOf(false) }
    var updateDownload by remember { mutableStateOf(UpdateDownload()) }
    // Reuse an APK that a previous prompt already downloaded for this version.
    LaunchedEffect(update) {
        val info = update ?: return@LaunchedEffect
        updateDownload = ApkUpdater.cached(ctx, info.version)
            ?.let { UpdateDownload(file = it) }
            ?: UpdateDownload()
    }
    val versionName = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    // Changelog: the first launch of a new version reads the release bodies from GitHub. The
    // last-seen version only advances after GitHub answered, so an offline launch retries next
    // time instead of losing the notes for good.
    var changelog by remember { mutableStateOf<List<ReleaseNotes>>(emptyList()) }
    LaunchedEffect(versionName) {
        if (versionName.isBlank()) return@LaunchedEffect
        val last = lastSeenVersion(ctx)
        if (last == null) {
            // A fresh install has nothing to announce. The build that introduces this dialog is
            // still an update for existing installs, but there is no previous version to diff
            // against — show only the current release.
            if (!hasBeenUpdated(ctx)) {
                setLastSeenVersion(ctx, versionName)
                return@LaunchedEffect
            }
            val notes = runCatching { releaseNotesBetween(null, versionName) }
                .onFailure { Log.w("MusikuChangelog", "rollout notes fetch failed", it) }
                .getOrNull() ?: return@LaunchedEffect
            setLastSeenVersion(ctx, versionName)
            val current = notes.filter { it.version == versionName }
            if (current.isNotEmpty()) changelog = current
            return@LaunchedEffect
        }
        if (last == versionName) return@LaunchedEffect
        if (!isNewerVersion(versionName, last)) {
            setLastSeenVersion(ctx, versionName)
            return@LaunchedEffect
        }
        val notes = runCatching { releaseNotesBetween(last, versionName) }
            .onFailure { Log.w("MusikuChangelog", "notes fetch failed", it) }
            .getOrNull() ?: return@LaunchedEffect
        setLastSeenVersion(ctx, versionName)
        if (notes.isNotEmpty()) changelog = notes
    }
    LaunchedEffect(versionName) {
        delay(5_000)
        val info = runCatching { checkForUpdate(versionName) }.getOrNull() ?: return@LaunchedEffect
        if (dismissedUpdateVersion(ctx) == info.version) return@LaunchedEffect
        update = info
        updateVisible = true
    }
    // The card is visible again, so the status-bar copy is redundant — and this version counts as
    // announced, which keeps the background worker from posting a duplicate later.
    LaunchedEffect(updateVisible, update) {
        if (!updateVisible) return@LaunchedEffect
        UpdateNotifier.cancel(ctx)
        update?.let { markUpdateNotified(ctx, it.version) }
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
        LocalLowDevice provides lowDevice,
    ) {
        // Liquid glass: the page content is drawn into one layer that every floating glass surface
        // (dock, tab bar, mini player) blurs behind itself — the Compose equivalent of the web's
        // `backdrop-filter`. The haze node must wrap the content, never a glass surface.
        val hazeState = remember { HazeState() }
        // The vendored Kyant backdrop records the routed page into its own layer; every glass
        // surface samples that layer (blur + AGSL lens) instead of approximating a backdrop.
        val glassBackdrop = rememberLayerBackdrop()
        // Re-blurring that layer is the expensive half of the effect, and a transition makes the
        // page change on every frame. Glass replays its last capture for the length of the fade.
        val navFreeze = remember { NavTransitionFreeze() }
        LaunchedEffect(route) { navFreeze.mark() }
        Box(Modifier.fillMaxSize().background(Tok.Window)) {
            CompositionLocalProvider(
                LocalHazeState provides if (lowDevice) null else hazeState,
                LocalGlassBackdrop provides if (lowDevice) null else glassBackdrop,
                LocalGlassFrozen provides navFreeze.frozen,
            ) {
                val skipBackdrop = lowDevice || route == Routes.SpotifyLogin
                Box(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(dockScroll)
                        // Low-device mode or WebView routes record no backdrop at all — WebView
                        // renders black inside an offscreen GraphicsLayer capture.
                        .then(if (skipBackdrop) Modifier else Modifier.layerBackdrop(glassBackdrop))
                        // The Haze source only feeds the two glass surfaces that sample this state:
                        // the update prompt and the track context menu. Keeping it attached while
                        // neither is on screen made every scroll and every transition re-record the
                        // whole page for a surface nobody could see.
                        .then(
                            if (!skipBackdrop && (updateVisible || ui.menuTrack != null)) {
                                Modifier.haze(hazeState)
                            } else {
                                Modifier
                            },
                        ),
                ) {
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
                        composable(Routes.Radio) { RadioScreen() }
                        composable(Routes.Library) { LibraryScreen() }
                        composable(Routes.Explore) { ExploreScreen() }
                        composable(Routes.Settings) {
                            SettingsScreen(
                                onBack = { nav.popBackStack() },
                                onPlayerDesign = { nav.navigate(Routes.PlayerDesign) },
                                onEqualizer = { nav.navigate(Routes.Equalizer) },
                                onSpotifyAccount = {
                                    nav.navigate(if (app.spotifyAuth.isLoggedIn) Routes.SpotifyLibrary else Routes.SpotifyLogin)
                                },
                            )
                        }
                        composable(Routes.PlayerDesign) {
                            PlayerDesignScreen(onBack = { nav.popBackStack() })
                        }
                        composable(Routes.Equalizer) {
                            EqualizerScreen(onBack = { nav.popBackStack() })
                        }
                        composable(Routes.Stats) {
                            StatsScreen(onBack = { nav.popBackStack() })
                        }
                        composable(Routes.SpotifyLogin) {
                            SpotifyLoginScreen(
                                onBack = { nav.popBackStack() },
                                onLoggedIn = {
                                    nav.navigate(Routes.SpotifyLibrary) {
                                        popUpTo(Routes.SpotifyLogin) { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(Routes.SpotifyLibrary) {
                            SpotifyLibraryScreen(
                                onBack = { nav.popBackStack() },
                                onOpenLogin = { nav.navigate(Routes.SpotifyLogin) },
                            )
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
                        composable(
                            "radio_station/{seedId}?title={title}&artist={artist}&thumbnail={thumbnail}",
                            listOf(
                                navArgument("seedId") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("artist") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("thumbnail") { type = NavType.StringType; nullable = true; defaultValue = null },
                            ),
                        ) {
                            RadioStationScreen(
                                seedId = android.net.Uri.decode(it.arguments?.getString("seedId").orEmpty()),
                                seedTitle = android.net.Uri.decode(it.arguments?.getString("title").orEmpty()),
                                seedArtist = android.net.Uri.decode(it.arguments?.getString("artist").orEmpty()),
                                seedThumbnail = android.net.Uri.decode(it.arguments?.getString("thumbnail").orEmpty()),
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }

                // floating dock: mini player (when something is queued) + tab bar, morphing into
                // Convx's inline row on scroll-down. Settings is a page pushed over the tabs.
                if (route != Routes.Settings && route != Routes.PlayerDesign && route != Routes.SpotifyLogin) {
                    val selectedTab = when {
                        route in TabRoutes -> route
                        else -> nav.previousBackStackEntry?.destination?.route?.takeIf { it in TabRoutes } ?: Routes.Home
                    }
                    MorphDock(
                        selected = selectedTab,
                        // Convx keeps the inline circle on the last content tab while Search is open.
                        contentTab = when (selectedTab) {
                            Routes.Home, Routes.Radio, Routes.Library, Routes.Explore -> selectedTab
                            else -> lastContentTab
                        },
                        inline = dockScroll.inline,
                        onSelect = { selectTab(nav, it) },
                        onExpand = { dockScroll.expand() },
                        onOpenPlayer = { ui.nowPlayingOpen = true },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .padding(horizontal = 12.dp).padding(bottom = 10.dp),
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
                        download = updateDownload,
                        onDownload = {
                            val apkUrl = info.apkUrl
                            val ready = updateDownload.file
                            when {
                                updateDownload.running -> Unit
                                apkUrl == null -> {
                                    // Releases without an APK asset still need the browser.
                                    openInBrowser(ctx, info.pageUrl)
                                    updateVisible = false
                                }
                                ready != null && ready.exists() -> {
                                    if (ApkUpdater.canInstall(ctx)) {
                                        runCatching { ApkUpdater.install(ctx, ready) }
                                            .onFailure { toast = "Tidak bisa membuka pemasang" }
                                    } else {
                                        updateDownload = updateDownload.copy(needsPermission = true)
                                        runCatching { ApkUpdater.openInstallPermission(ctx) }
                                    }
                                }
                                else -> scope.launch {
                                    updateDownload = UpdateDownload(running = true, progress = 0f)
                                    val file = runCatching {
                                        ApkUpdater.download(ctx, apkUrl, info.version) { p ->
                                            updateDownload = UpdateDownload(running = true, progress = p)
                                        }
                                    }.getOrElse { e ->
                                        updateDownload = UpdateDownload(
                                            error = e.message ?: "koneksi terputus",
                                        )
                                        return@launch
                                    }
                                    if (ApkUpdater.canInstall(ctx)) {
                                        updateDownload = UpdateDownload(file = file)
                                        runCatching { ApkUpdater.install(ctx, file) }
                                            .onFailure {
                                                toast = "Tidak bisa membuka pemasang"
                                                updateDownload = UpdateDownload(
                                                    file = file,
                                                    error = "pemasang tidak tersedia",
                                                )
                                            }
                                    } else {
                                        updateDownload = UpdateDownload(
                                            file = file,
                                            needsPermission = true,
                                        )
                                        runCatching { ApkUpdater.openInstallPermission(ctx) }
                                    }
                                }
                            }
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
            SpotifyImportDialog(ui.spotifyImportOpen) { ui.spotifyImportOpen = false }
            if (changelog.isNotEmpty()) {
                ChangelogDialog(versionName, changelog) { changelog = emptyList() }
            }
        }
    }
}

/** True when this install has taken at least one update, not a fresh first install. */
private fun hasBeenUpdated(ctx: Context): Boolean = runCatching {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    info.lastUpdateTime - info.firstInstallTime > 60_000L
}.getOrDefault(false)

private fun selectTab(nav: androidx.navigation.NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// Section changes are a cross-dissolve: tabs and pushed pages share the same motion. Sliding a
// page that carries the glass backdrop moves its whole captured layer every frame, which is the
// last thing those pages need; a fade also reads calmer when tabs change in place.
private const val FadeMillis = 200

// Fade plus frame-delivery slop; glass replays its capture for this window.
private const val NavFreezeWindowNs = 280_000_000L

private val navFade = tween<Float>(FadeMillis, easing = FastOutSlowInEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    fadeIn(navFade)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    fadeOut(navFade)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    fadeIn(navFade)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    fadeOut(navFade)

/**
 * Marks the start of a screen transition. Draw-phase code asks [frozen] whether glass should
 * replay the capture it already has instead of re-blurring the backdrop. A plain array keeps that
 * read out of snapshot state, so checking it never invalidates the frame that is checking.
 */
private class NavTransitionFreeze {
    private val startedAtNs = longArrayOf(0L)

    val frozen: () -> Boolean = {
        val started = startedAtNs[0]
        started != 0L && System.nanoTime() - started < NavFreezeWindowNs
    }

    fun mark() {
        startedAtNs[0] = System.nanoTime()
    }
}

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
    val look by app.library.playerLook.collectAsState()
    val artworkStyle by app.library.playerArtworkStyle.collectAsState()
    val bgStyle by app.library.playerBackgroundStyle.collectAsState()
    val layoutOrder by app.library.playerLayoutOrder.collectAsState()
    val hiddenSlots by app.library.playerLayoutHidden.collectAsState()
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
            look = look,
            artworkStyle = artworkStyle,
            background = bgStyle,
            layoutOrder = layoutOrder,
            hiddenSlots = hiddenSlots,
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
                NowPlayingBackground(
                    track.thumbs,
                    Modifier.fillMaxSize(),
                    style = if (look == PlayerLook.Hero) PlayerBackgroundStyle.Gradient else bgStyle,
                    hero = look == PlayerLook.Hero,
                    isPlaying = ps.playing,
                )
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
