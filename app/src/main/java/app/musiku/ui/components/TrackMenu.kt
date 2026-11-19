package app.musiku.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.nowplaying.MenuRow
import app.musiku.ui.nowplaying.Sep
import app.musiku.ui.shareTrack
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.MMenuIcons
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * The three-dot track menu. Same floating card as [app.musiku.ui.nowplaying.PlayerMenu] — 268dp
 * wide, anchored bottom-right — instead of the full-width bottom sheet it used to be. A menu that
 * eats the whole width of the screen for eight short rows wastes the space and, because a
 * `ModalBottomSheet` renders in its own window, it could not blur the page behind it and just let
 * the list show through its 80% fill.
 *
 * Rendered inline (not in a dialog window) so `glass` can sample the page's haze layer.
 *
 * Motion is Convx's pop menu: the card stretches open from the corner of the row that raised it —
 * width first, then height (different springs, as on iOS) — and on dismiss it shrinks back into
 * that same point with a short `EaseIn` tween instead of vanishing. The page dims slightly while
 * it is up, and the dim rides the same fade so it never flashes.
 */
@Composable
fun TrackMenu(
    track: Track,
    onRemove: (() -> Unit)?,
    haze: HazeState,
    onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by app.player.ui.collectAsState()
    val liked by app.library.liked.collectAsState()
    val isLiked = liked.any { it.videoId == track.videoId }
    val isCurrent = state.current?.videoId == track.videoId
    val dark = Tok.IsDark
    val shape = RoundedCornerShape(28.dp)

    // `visible` stays true until the exit finishes, so the card can play its landing.
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    val t = rememberTransition(transition, label = "trackMenu")

    // Convx's menu axes: wider spring for X, softer for Y, both overshooting a touch.
    val sx by t.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.62f, stiffness = 560f) else tween(140, easing = EaseIn) },
        label = "menuX",
    ) { if (it) 1f else 0.25f }
    val sy by t.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.78f, stiffness = 330f) else tween(140, easing = EaseIn) },
        label = "menuY",
    ) { if (it) 1f else 0.08f }
    val fade by t.animateFloat(
        transitionSpec = { if (targetState) tween(110) else tween(150, easing = EaseIn) },
        label = "menuFade",
    ) { if (it) 1f else 0f }

    // Hold the composable until the shrink-back lands, then let the host drop it.
    LaunchedEffect(t.isRunning, closing) {
        if (closing && !t.isRunning) onDismiss()
    }

    val close: () -> Unit = {
        if (!closing) {
            closing = true
            transition.targetState = false
        }
    }
    val act: (() -> Unit) -> () -> Unit = { f -> { f(); close() } }

    BackHandler(onBack = close)

    // The anchor is where the row sat when it raised the menu; with none (a caller that only knows
    // the track) the card falls back to growing out of its own bottom-right corner.
    val anchor = ui.menuAnchor
    var card by remember { mutableStateOf(Rect.Zero) }
    val dim = if (dark) 0.26f else 0.14f
    // Sit above the dock instead of covering it: the tab bar stays where the thumb expects.
    val dock = 78.dp + if (state.current != null) 72.dp else 0.dp

    Box(Modifier.fillMaxSize()) {
        // Tap-outside scrim. No ripple: a flash across the whole screen reads as a bug.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = dim * fade.coerceIn(0f, 1f)) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = close,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = dock)
                .width(268.dp)
                .heightIn(max = 460.dp)
                .onGloballyPositioned { card = it.boundsInWindow() }
                .graphicsLayer {
                    val a = anchor
                    val ox = if (a != null && card.width > 0f) ((a.center.x - card.left) / card.width).coerceIn(0f, 1f) else 1f
                    val oy = if (a != null && card.height > 0f) ((a.center.y - card.top) / card.height).coerceIn(0f, 1f) else 1f
                    transformOrigin = TransformOrigin(ox, oy)
                    scaleX = sx
                    scaleY = sy
                    alpha = fade
                }
                // web: rounded-[28px] border-white/10 bg-[rgba(30,30,33,.86)] backdrop-blur-2xl
                .glass(haze, shape, strong = true, shadow = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().pressable { act { scope.launch { runCatching { shareTrack(ctx, track, 0f) } } }() }
                    .padding(top = 14.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MMenuIcons.Share, contentDescription = null, tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(28.dp))
                Text("Bagikan", color = if (dark) Color.White else Color.Black, fontSize = 14.sp)
            }
            Sep(dark)
            MenuRow(
                if (isCurrent && state.playing) MIcons.Pause else MIcons.Play,
                if (isCurrent && state.playing) "Jeda" else "Putar",
                dark = dark,
                onClick = act { if (isCurrent) app.player.toggle() else app.player.playTracks(listOf(track)) },
            )
            MenuRow(MMenuIcons.Next, "Putar berikutnya", dark = dark, onClick = act { app.player.enqueue(track, next = true) })
            MenuRow(MMenuIcons.Queue, "Tambah ke antrian", dark = dark, onClick = act { app.player.enqueue(track, next = false) })
            Sep(dark)
            MenuRow(
                if (isLiked) MMenuIcons.HeartFilled else MMenuIcons.Heart,
                if (isLiked) "Hapus dari favorit" else "Tambah ke favorit",
                dark = dark,
                onClick = act { app.player.toggleLike(track) },
            )
            MenuRow(MMenuIcons.Plus, "Tambah ke playlist…", dark = dark, onClick = act { ui.addToPlaylist = track })
            Sep(dark)
            if (track.albumId != null) {
                MenuRow(MMenuIcons.Album, "Buka album", subtitle = track.album, dark = dark, onClick = act { ui.navigate(Routes.album(track.albumId)) })
            }
            if (track.artistId != null) {
                MenuRow(MMenuIcons.Mic, "Buka artis", subtitle = track.artist, dark = dark, onClick = act { ui.navigate(Routes.artist(track.artistId)) })
            }
            MenuRow(MMenuIcons.Info, "Info lagu", dark = dark, onClick = act { ui.infoTrack = track })
            if (onRemove != null) {
                Sep(dark)
                MenuRow(MIcons.Trash, "Hapus dari playlist", dark = dark, danger = true, onClick = act(onRemove))
            }
        }
    }
}
