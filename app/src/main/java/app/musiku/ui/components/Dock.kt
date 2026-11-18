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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.Routes
import app.musiku.ui.nowplaying.LandingSquash
import app.musiku.ui.nowplaying.LandingSpread
import app.musiku.ui.nowplaying.MiniPlayerCornerRadius
import app.musiku.ui.nowplaying.PlayerMorph
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.liquidGlass

private val GlassShape = RoundedCornerShape(MiniPlayerCornerRadius)

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val pos by app.player.position.collectAsState()
    val t = ps.current ?: return
    val artScale by animateFloatAsState(if (ps.playing) 1f else 0.9f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "art")
    SideEffect { PlayerMorph.miniArtScale = artScale }
    val owner = remember { Any() }
    // The morph host needs the pill's slot to fly the player in and out of it; release on dispose
    // so a player without a dock falls back to a plain fade instead of a stale rect.
    DisposableEffect(owner) {
        onDispose {
            if (PlayerMorph.miniBounds != null) PlayerMorph.miniBounds = null
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .onGloballyPositioned { if (it.isAttached) PlayerMorph.miniBounds = it.boundsInRoot() }
            // Close landing: Convx carries the sheet's arrival speed into the pill, which dips
            // below its slot and squashes like a liquid body before settling.
            .graphicsLayer {
                translationY = PlayerMorph.landingDipPx
                val squash = PlayerMorph.landingSquash
                scaleX = 1f + LandingSpread * squash
                scaleY = 1f - LandingSquash * squash
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .liquidGlass(GlassShape),
    ) {
        MiniPlayerBody(
            track = t,
            playing = ps.playing,
            fraction = if (ps.durationSec > 0f) (pos / ps.durationSec).coerceIn(0f, 1f) else 0f,
            interactive = true,
            onOpen = onOpen,
            onToggle = app.player::toggle,
            onNext = app.player::next,
            artSlot = {
                Artwork(
                    t.thumbs,
                    Modifier
                        .size(44.dp)
                        // The morph's pill end: the cover flies from exactly this square.
                        .onGloballyPositioned { if (it.isAttached) PlayerMorph.miniArtBounds = it.boundsInRoot() }
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                            val p = PlayerMorph.progressProvider()
                            alpha = if (p > 0.0005f && p < 0.9995f &&
                                PlayerMorph.fullArtBounds != null
                            ) 0f else 1f
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Tok.Fill2),
                )
            },
        )
    }
}

/**
 * The pill's contents, shared by the dock and the morph's card.
 *
 * Convx records the pill into a layer and draws that same recording on the growing card, so the
 * card's copy has to land on the dock's to the pixel — hence one composable for both. The card's
 * copy is [interactive] `false`: it is a picture, and its job ends where the real pill takes over.
 * [artSlot] is what differs between the two (the dock's cover vs. the hole the flying cover leaves).
 */
@Composable
internal fun MiniPlayerBody(
    track: Track,
    playing: Boolean,
    fraction: Float,
    interactive: Boolean,
    onOpen: () -> Unit = {},
    onToggle: () -> Unit = {},
    onNext: () -> Unit = {},
    artSlot: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .then(if (interactive) Modifier.tappable(onOpen, description = "Buka pemutar") else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                artSlot()
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Tok.Label1, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist.orEmpty(), color = Tok.Label2, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PillControl(if (playing) MIcons.Pause else MIcons.Play, if (playing) "Jeda" else "Putar", interactive, onToggle)
            PillControl(MIcons.Next, "Lagu berikutnya", interactive, onNext)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp).fillMaxWidth().height(2.dp).clip(CircleShape).background(Tok.Fill1)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Tok.Label1))
        }
    }
}

@Composable
private fun PillControl(icon: ImageVector, label: String, interactive: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (interactive) Modifier.pressable(pressScale = 0.94f, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Tok.Label1, modifier = Modifier.size(20.dp))
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
    // Web MobileTabs: the active pill is one element that slides behind the labels
    // (`.absolute .. rounded-full bg-fill2 transition duration-500 ease-[--ease-spring]`),
    // not a per-item fill — so the highlight travels when you switch tabs.
    val idx = Tabs.indexOfFirst { it.route == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(60.dp)
            // Same treatment as the mini player: Convx runs one glass material for both the nav
            // bar and the mini player, so the dock reads as a single system.
            .liquidGlass(CircleShape)
            .padding(6.dp),
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
