package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.LocalApp
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.Tok
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Bottom inset reserved for the floating dock (mini player, when something is queued, plus the tab
 * bar). Content still scrolls under the dock; this only keeps the last row reachable.
 */
val DockInset: Dp
    @Composable
    get() {
        val ps by LocalApp.current.player.ui.collectAsState()
        return if (ps.current == null) 86.dp else 156.dp
    }

/**
 * iOS "large title" page: a 34sp title scrolls away under a frosted-glass bar while a compact
 * centred title and the trailing control stay fixed at the top.
 *
 * Metrics follow Convx's large title: the compact bar is 52dp of content under the status bar,
 * and the page list spaces its items by [itemSpacing]. The bar itself is Haze glass — it samples
 * the list behind it instead of painting a solid band — so [contentPadding] keeps the first item
 * out from under it at rest while everything else scrolls through the blur.
 */
private val BarHeight = 52.dp

/** Distance from the bar's foot over which the blur and its tint reach clear. */
private val GlassFade = 24.dp

/** Peak alpha of the dark tint over the glass. */
private const val GlassTint = 0.55f

/** Gap between the compact bar and the large title. */
private val TitleTop = 6.dp

/** The tapping target around the bar's trailing control; its avatar is 28dp inside it. */
private val ActionTarget = 48.dp

/** Half of [ActionTarget] minus the avatar, used to land the circle on the content gutter. */
private val ActionInset = 10.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LargeTitleList(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    /** Trailing controls pinned to the bar's right edge (an avatar, a gear…). */
    actions: (@Composable () -> Unit)? = null,
    titleBottom: Dp = 12.dp,
    itemSpacing: Dp = 0.dp,
    /** Horizontal inset shared by the title and [actions]; match the page's content gutter. */
    gutter: Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val lowDevice = LocalLowDevice.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val window = Tok.Window
    // The whole status-bar + bar strip is glass, and the list starts right below it.
    val barHeight = statusTop + BarHeight
    val fadeStartPx = with(density) { (barHeight - GlassFade).toPx() }
    val fadeEndPx = with(density) { barHeight.toPx() }
    // The large title lives inside the list, so it is "gone" once it has slid up behind the bar.
    val collapseAt = with(density) { (TitleTop + 46.dp).toPx() }
    val collapsed by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > collapseAt }
    }
    val compact by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "compact-title")

    // The bar samples the page behind it. This has to be its own state: the app-level one is held
    // by an ancestor of both the list and the bar, and a child of that state cannot sample the
    // sibling that paints it.
    val hazeState = remember { HazeState() }
    val blurRamp = remember(fadeStartPx, fadeEndPx) {
        HazeProgressive.verticalGradient(
            easing = EaseOutCubic,
            // Full strength over the bar, then out to nothing at its foot so content emerges from
            // the blur instead of being cut by a hard line.
            startY = fadeStartPx,
            startIntensity = 1f,
            endY = fadeEndPx,
            endIntensity = 0f,
        )
    }
    val style = remember(window) {
        HazeStyle(
            // Nothing is painted under the sampled backdrop: at rest the glass only tints the page
            // colour, and once content slides under it the blur has something to frost.
            backgroundColor = Color.Transparent,
            // The tint is drawn as its own gradient below so it can fade out with the blur.
            tints = emptyList(),
            blurRadius = 24.dp,
            noiseFactor = 0f,
            // Android 11 and older cannot blur; Haze falls back to a scrim instead.
            fallbackTint = HazeTint(window.copy(alpha = 0.85f)),
        )
    }
    val tint = remember(window, barHeight) {
        val solid = ((barHeight - GlassFade) / barHeight).coerceIn(0f, 1f)
        Brush.verticalGradient(
            0f to window.copy(alpha = GlassTint),
            solid to window.copy(alpha = GlassTint),
            1f to Color.Transparent,
        )
    }

    Box(modifier.fillMaxSize()) {
        // The platform's glow/stretch is switched off here: iosBounce() already owns the
        // overscroll, and two effects would fight over the same drag.
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(top = barHeight, bottom = DockInset),
                verticalArrangement = if (itemSpacing > 0.dp) Arrangement.spacedBy(itemSpacing) else Arrangement.Top,
                // The list is the Haze source: it keeps drawing edge to edge underneath the bar. The
                // page colour sits *inside* the source, so the blur samples a filled canvas — without
                // it the gaps around artwork are transparent and the sharp original bleeds through.
                modifier = Modifier.fillMaxSize()
                    .then(if (lowDevice) Modifier else Modifier.haze(hazeState))
                    .background(window).iosBounce(),
            ) {
                item(key = "large-title") {
                    Text(
                        title, color = Tok.Label1, fontSize = 34.sp, lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = gutter, end = gutter, top = TitleTop, bottom = titleBottom,
                        ),
                    )
                }
                if (header != null) item(key = "header") { header() }
                content()
            }
        }

        // The bar is a sibling of the list, not part of it, and it paints blur + tint instead of a
        // solid colour — the page keeps scrolling underneath the glass.
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .then(
                    if (lowDevice) {
                        Modifier.background(window)
                    } else {
                        Modifier
                            .hazeChild(hazeState, style = style) {
                                blurEnabled = true
                                progressive = blurRamp
                            }
                            .background(tint)
                    }
                ),
        ) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                Text(
                    title,
                    color = Tok.Label1,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.25).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = gutter + ActionTarget)
                        .alpha(compact)
                        .graphicsLayer { translationY = (1f - compact) * 8.dp.toPx() },
                )
                if (actions != null) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            // The target is wider than the avatar and centres it, so shave half the
                            // slack off the edge: the visible circle lands on the content gutter.
                            .padding(end = (gutter - ActionInset).coerceAtLeast(0.dp)),
                    ) {
                        actions()
                    }
                }
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.statusBarsPadding().padding(start = 12.dp, top = 6.dp)
            .size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).pressable90(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MIcons.Back, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(26.dp))
    }
}
