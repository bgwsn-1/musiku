package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import app.musiku.ui.theme.Tok

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
 * iOS "large title" page: a 34sp title that scrolls away while a small centred title and a
 * hairline fade into a translucent top bar (port of the web's large-title collapse).
 *
 * Metrics follow Convx's large title: the title's box starts 62dp from the top of the
 * window (status bar included), and the page list spaces its items by [itemSpacing].
 */
/** Compact bar height: Convx's `TopBarContentHeight`. */
private val BarHeight = 52.dp

/** Scroll distance over which the bar goes from clear to solid. */
private val BarFadeDistance = 40.dp

/** The touching target around the bar's trailing control; its avatar is 28dp inside it. */
private val ActionTarget = 48.dp

/** Half of [ActionTarget] minus the avatar, used to land the circle on the content gutter. */
private val ActionInset = 10.dp

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
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > threshold } }
    val bar by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "bar")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val window = Tok.Window
    val fadeDistance = with(LocalDensity.current) { BarFadeDistance.toPx() }
    // Solid before anything but the title can reach the bar, so text never lands on artwork.
    val barSolid = remember(state, fadeDistance) {
        derivedStateOf {
            val travelled = if (state.firstVisibleItemIndex > 0) fadeDistance
            else state.firstVisibleItemScrollOffset.toFloat()
            (travelled / fadeDistance).coerceIn(0f, 1f)
        }
    }
    // The status-bar scrim stays on at every offset: the clock and battery always have something
    // to sit on once the page is under them.
    val scrim = remember(window) {
        Brush.verticalGradient(
            listOf(window.copy(alpha = 0.88f), window.copy(alpha = 0.30f), Color.Transparent),
        )
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(bottom = DockInset),
            verticalArrangement = if (itemSpacing > 0.dp) Arrangement.spacedBy(itemSpacing) else Arrangement.Top,
            modifier = Modifier.fillMaxSize().iosBounce(),
        ) {
            item(key = "large-title") {
                Box(
                    Modifier
                        .statusBarsPadding()
                        .padding(
                            top = (62.dp - statusTop).coerceAtLeast(0.dp),
                            start = gutter,
                            end = gutter,
                            bottom = titleBottom,
                        ),
                ) {
                    Text(
                        title, color = Tok.Label1, fontSize = 34.sp, lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (header != null) item(key = "header") { header() }
            content()
        }

        // The bar is a sibling of the list, not part of it: it owns its background and its
        // insets, and the page scrolls underneath instead of dragging the header along.
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val solid = barSolid.value
                    if (solid > 0f) drawRect(window.copy(alpha = solid))
                    drawRect(scrim)
                }
                .statusBarsPadding()
                .height(BarHeight),
        ) {
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
                    .alpha(bar)
                    .graphicsLayer { translationY = (1f - bar) * 8.dp.toPx() },
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
