package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.LocalApp
import app.musiku.ui.Routes
import app.musiku.ui.theme.LocalHazeState
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glass

private val GlassShape = RoundedCornerShape(26.dp)

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val pos by app.player.position.collectAsState()
    val t = ps.current ?: return
    val artScale by animateFloatAsState(if (ps.playing) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "art")
    val haze = LocalHazeState.current

    Box(modifier.fillMaxWidth().height(64.dp).glass(haze, GlassShape, shadow = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.weight(1f).clip(CircleShape).tappable(onOpen, description = "Buka pemutar"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(t.thumbs, Modifier.size(44.dp).graphicsLayer { scaleX = artScale; scaleY = artScale }.clip(RoundedCornerShape(8.dp)).background(Tok.Fill2))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Tok.Label1, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist.orEmpty(), color = Tok.Label2, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).pressable(pressScale = 0.94f) { app.player.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (ps.playing) MIcons.Pause else MIcons.Play, if (ps.playing) "Jeda" else "Putar", tint = Tok.Label1, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(44.dp).clip(CircleShape).pressable(pressScale = 0.94f) { app.player.next() }, contentAlignment = Alignment.Center) {
                Icon(MIcons.Next, "Lagu berikutnya", tint = Tok.Label1, modifier = Modifier.size(20.dp))
            }
        }
        val frac = if (ps.durationSec > 0f) (pos / ps.durationSec).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Tok.Fill1)) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(Tok.Label1))
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.Home, "Beranda", MIcons.Home),
    Tab(Routes.Search, "Cari", MIcons.Search),
    Tab(Routes.Library, "Pustaka", MIcons.Library),
)

@Composable
fun BottomTabs(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haze = LocalHazeState.current
    // Web MobileTabs: the active pill is one element that slides behind the labels
    // (`.absolute .. rounded-full bg-fill2 transition duration-500 ease-[--ease-spring]`),
    // not a per-item fill — so the highlight travels when you switch tabs.
    val idx = Tabs.indexOfFirst { it.route == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(60.dp).glass(haze, CircleShape, shadow = 14.dp).padding(6.dp),
    ) {
        val gap = 4.dp
        val itemW = (maxWidth - gap * (Tabs.size - 1)) / Tabs.size
        // Web's ease-spring is cubic-bezier(0.34,1.4,0.64,1) — an overshooting spring, 500ms.
        val thumbX by animateDpAsState(
            itemW * idx + gap * idx,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "thumb",
        )
        Box(
            Modifier
                .offset(x = thumbX)
                .width(itemW)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Tok.Fill2),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tabs.forEach { tab ->
                val on = tab.route == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .pressable(pressScale = 0.95f) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = if (on) Tok.Accent else Tok.Label2, modifier = Modifier.size(24.dp))
                    Text(
                        tab.label,
                        color = if (on) Tok.Accent else Tok.Label2,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
