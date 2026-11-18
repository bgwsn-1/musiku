package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
@Composable
fun LargeTitleList(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    /** Trailing controls pinned to the title's baseline (a settings gear, an avatar…). */
    actions: (@Composable () -> Unit)? = null,
    titleBottom: androidx.compose.ui.unit.Dp = 12.dp,
    itemSpacing: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    val collapsed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > threshold } }
    val bar by animateFloatAsState(if (collapsed) 1f else 0f, tween(180), label = "bar")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = 0.dp, bottom = DockInset),
            verticalArrangement = if (itemSpacing > 0.dp) Arrangement.spacedBy(itemSpacing) else Arrangement.Top,
        ) {
            item(key = "large-title") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.statusBarsPadding()
                        .padding(top = (62.dp - statusTop).coerceAtLeast(0.dp), start = 16.dp, end = 16.dp, bottom = titleBottom),
                ) {
                    Text(
                        title, color = Tok.Label1, fontSize = 34.sp, lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (actions != null) {
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                }
            }
            if (header != null) item(key = "header") { header() }
            content()
        }
        // Web scroll-edge: an always-on gradient that fades content into the window colour and
        // blurs 10px under the floating toolbar; the centred title fades/slides in on collapse.
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Tok.Window.copy(alpha = 0.85f),
                        0.55f to Tok.Window.copy(alpha = 0.40f),
                        1f to Color.Transparent,
                    ),
                )
                .statusBarsPadding()
                .height(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                color = Tok.Label1,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.25).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .alpha(bar)
                    .graphicsLayer { translationY = (1f - bar) * 8.dp.toPx() },
            )
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
