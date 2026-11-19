package app.musiku.ui.components

import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.SfIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.Routes
import app.musiku.ui.glass.backdrop.backdrops.layerBackdrop
import app.musiku.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import app.musiku.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import app.musiku.ui.glass.backdrop.drawBackdrop
import app.musiku.ui.glass.backdrop.effects.lens
import app.musiku.ui.glass.backdrop.highlight.Highlight
import app.musiku.ui.glass.backdrop.isRenderEffectSupported
import app.musiku.ui.glass.backdrop.shadow.InnerShadow
import app.musiku.ui.nowplaying.LandingEchoShare
import app.musiku.ui.nowplaying.LandingSquash
import app.musiku.ui.nowplaying.LandingSpread
import app.musiku.ui.nowplaying.MiniPlayerCornerRadius
import app.musiku.ui.nowplaying.PlayerMorph
import app.musiku.ui.theme.LocalGlassBackdrop
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.liquidGlass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private val GlassShape = RoundedCornerShape(MiniPlayerCornerRadius)

// Convx's dock metrics (`floatingtabbar` + `FloatingMiniPlayer`): the expanded bar sits under the
// player, the inline bar is one row of [selected tab circle][player][search circle] with 8dp gaps.
private val DockBarHeight = 60.dp
private val DockPlayerHeight = 64.dp
private val DockInlinePlayerHeight = 60.dp
private val DockGap = 8.dp
private val DockBarInset = 6.dp
private val DockItemGap = 4.dp
private val DockInlineCircle = 60.dp
private val DockExpandedHeight = DockPlayerHeight + DockGap + DockBarHeight

// Convx insets the selection puck by its bar content padding (4dp) on every side.
private val DockPuckInset = 4.dp

// Convx's morph clocks (`BarMotion`): collapse 560ms, expand 470ms, both landing on one frame.
private const val DockCollapseMorphMs = 560
private const val DockExpandMorphMs = 470

/**
 * An underdamped spring's step response squeezed into a fixed-duration [Easing], ported from
 * Convx's `SpringEasing` (`floatingtabbar/BarMotion.kt`). Crosses the target early and overshoots
 * by ~exp(-zeta*pi/sqrt(1-zeta^2)); [omegaT] = natural frequency x duration, 7.8 leaves <0.6%
 * residual that is removed linearly so `transform(1)` is exactly 1.
 */
private class SpringEasing(private val zeta: Float, private val omegaT: Float = 7.8f) : Easing {
    private val root = sqrt(1f - zeta * zeta)
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wd = omegaT * root * t
        return 1f - exp(-zeta * omegaT * t) * (cos(wd) + zeta / root * sin(wd))
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

/** Critically damped step response on the same fixed-duration frame, from Convx's `CriticalEasing`. */
private class CriticalEasing(private val omegaT: Float = 7.8f) : Easing {
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wt = omegaT * t
        return 1f - (1f + wt) * exp(-wt)
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

private val DockTravel = CriticalEasing()
private val DockCollapseSurface = CriticalEasing()
private val DockExpandSurface = SpringEasing(zeta = 0.74f)
private val DockCollapseMiniX = SpringEasing(zeta = 0.56f, omegaT = 9f)
private val DockExpandMiniX = SpringEasing(zeta = 0.34f, omegaT = 9f)
private val DockCollapseMiniY = SpringEasing(zeta = 0.62f)
private val DockExpandMiniY = SpringEasing(zeta = 0.58f)

/**
 * Convx's `FloatingTabBarScrollConnection`: collapse to the inline row after 50dp of accumulated
 * down-scroll, expand after 8dp up, and reset the accumulator whenever the direction reverses so
 * a shaky finger cannot oscillate the dock. It never consumes the scroll.
 */
class DockScrollConnection(
    initialInline: Boolean = false,
    private val collapseThresholdPx: Float,
    private val expandThresholdPx: Float,
) : NestedScrollConnection {
    var inline by mutableStateOf(initialInline)
        private set

    private var accumulated = 0f

    fun expand() {
        inline = false
        accumulated = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        if (delta == 0f) return Offset.Zero
        if ((accumulated > 0f && delta < 0f) || (accumulated < 0f && delta > 0f)) {
            accumulated = 0f
        }
        accumulated += delta
        if (accumulated <= -collapseThresholdPx && !inline) {
            inline = true
            accumulated = 0f
        } else if (accumulated >= expandThresholdPx && inline) {
            inline = false
            accumulated = 0f
        }
        return Offset.Zero
    }
}

@Composable
fun rememberDockScrollConnection(
    collapseThreshold: Dp = 50.dp,
    expandThreshold: Dp = 8.dp,
): DockScrollConnection = with(LocalDensity.current) {
    val collapsePx = collapseThreshold.toPx()
    val expandPx = expandThreshold.toPx()
    remember(collapsePx, expandPx) {
        DockScrollConnection(collapseThresholdPx = collapsePx, expandThresholdPx = expandPx)
    }
}

@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockPlayerHeight,
) {
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

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
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
            height = height,
            extras = miniExtras(maxWidth),
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
 * Convx collapses the mini's extras by width (`FloatingMiniPlayer`): full at 320dp, gone at
 * 262dp. The inline row lands at ~266dp on a phone, so Next fades out with it.
 */
internal fun miniExtras(width: Dp): Float =
    ((width - 262.dp) / (320.dp - 262.dp)).coerceIn(0f, 1f)

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
    height: Dp = DockPlayerHeight,
    extras: Float = 1f,
    onOpen: () -> Unit = {},
    onToggle: () -> Unit = {},
    onNext: () -> Unit = {},
    artSlot: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Row(
            Modifier.fillMaxWidth().height(height).padding(start = 10.dp, end = 8.dp),
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
            val shown = extras.coerceIn(0f, 1f)
            // Convx's extras collapse by width; the gap before Next collapses with them so the
            // play button stays flush to the pill when inline.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp * shown),
            ) {
                PillControl(if (playing) MIcons.Pause else MIcons.Play, if (playing) "Jeda" else "Putar", interactive, onToggle)
                Box(
                    Modifier
                        .width(44.dp * shown)
                        .height(44.dp)
                        .graphicsLayer { alpha = shown }
                        .clip(CircleShape)
                        .then(
                            if (interactive && shown > 0.5f) {
                                Modifier.pressable(pressScale = 0.94f, onClick = onNext)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MIcons.Next, "Lagu berikutnya", tint = Tok.Label1, modifier = Modifier.size(20.dp))
                }
            }
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

private data class Tab(val route: String, val label: String, val icon: ImageVector, val glyph: Dp)

// Convx's floating bar draws SF Symbols at 32dp (28dp for Search); the glyphs live in SfIcons.
private val Tabs = listOf(
    Tab(Routes.Home, "Beranda", SfIcons.HouseFill, 32.dp),
    Tab(Routes.Search, "Cari", SfIcons.Magnifyingglass, 28.dp),
    Tab(Routes.Library, "Pustaka", SfIcons.MusicNoteSquareStackFill, 32.dp),
)

private val SearchTab = Tabs.first { it.route == Routes.Search }

/**
 * Convx's floating dock: the mini player above the full tab bar when expanded, one inline row of
 * `[selected tab circle][mini player][search circle]` once the page scrolls down.
 *
 * The geometry is one tree rather than two cross-faded bars: the tab pill shrinks into the left
 * circle (Convx's shared-bounds tab pill), the search item travels to its own circle, and the
 * player's bounds morph between its expanded slot and the middle of the inline row. [inline] is
 * the scroll connection's target; the four clocks mirror Convx's stagger so the surfaces bounce
 * while the centre travel never overshoots.
 *
 * The selection pill is Convx's drag puck (`ExpandedTabs`): pressing grows it into a lens over the
 * bar's own glass and icons, dragging moves it tab to tab with a damped spring and a velocity
 * stretch, and releasing snaps it to the nearest tab and navigates there.
 */
@Composable
fun MorphDock(
    selected: String?,
    contentTab: String,
    inline: Boolean,
    onSelect: (String) -> Unit,
    onExpand: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val hasPlayer = ps.current != null
    val dark = Tok.IsDark
    val lowDevice = LocalLowDevice.current
    val appBackdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val travel by animateFloatAsState(
        targetValue = if (inline) 1f else 0f,
        animationSpec = tween(
            if (inline) DockCollapseMorphMs else DockExpandMorphMs,
            easing = DockTravel,
        ),
        label = "dockTravel",
    )
    val surface by animateFloatAsState(
        targetValue = if (inline) 1f else 0f,
        animationSpec = tween(
            if (inline) DockCollapseMorphMs else DockExpandMorphMs,
            easing = if (inline) DockCollapseSurface else DockExpandSurface,
        ),
        label = "dockSurface",
    )
    val miniX by animateFloatAsState(
        targetValue = if (inline) 1f else 0f,
        animationSpec = tween(
            if (inline) DockCollapseMorphMs else DockExpandMorphMs,
            easing = if (inline) DockCollapseMiniX else DockExpandMiniX,
        ),
        label = "dockMiniX",
    )
    val miniY by animateFloatAsState(
        targetValue = if (inline) 1f else 0f,
        animationSpec = tween(
            if (inline) DockCollapseMorphMs else DockExpandMorphMs,
            easing = if (inline) DockCollapseMiniY else DockExpandMiniY,
        ),
        label = "dockMiniY",
    )

    val dockHeight = lerp(if (hasPlayer) DockExpandedHeight else DockBarHeight, DockBarHeight, travel)
    val labelAlpha = (1f - travel).coerceIn(0f, 1f)
    val thumbAlpha = (1f - travel * 2f).coerceIn(0f, 1f)
    val selectedIndex = Tabs.indexOfFirst { it.route == selected }.coerceAtLeast(0)
    val contentIndex = Tabs.indexOfFirst { it.route == contentTab }.coerceAtLeast(0)
    val otherContent = Tabs.indices.first { it != 1 && it != contentIndex }

    // Convx keeps the puck on the last user-chosen tab and only moves it when navigation or a
    // finished drag changes the selection.
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onSelectNow = rememberUpdatedState(onSelect)

    val onTab: (Tab) -> Unit = { tab ->
        // Convx's inline circle expands first; navigating is the same click's second half.
        if (inline) onExpand()
        onSelect(tab.route)
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(dockHeight)) {
        val width = maxWidth
        val barY = dockHeight - DockBarHeight
        val barWidth = lerp(width, DockInlineCircle, surface)
        val expandedItem = (width - DockBarInset * 2 - DockItemGap * (Tabs.size - 1)) / Tabs.size
        val step = expandedItem + DockItemGap
        fun center(index: Int): Dp = DockBarInset + step * index + expandedItem / 2

        // No player: Convx packs [tab][search] and centres the cluster instead of spanning.
        val packedX = (width - DockInlineCircle * 2 - DockGap) / 2
        val barX = if (hasPlayer) 0.dp else lerp(0.dp, packedX, travel)
        val leftCenter = barX + DockInlineCircle / 2
        val searchTarget = if (hasPlayer) {
            width - DockInlineCircle / 2
        } else {
            lerp(width - DockInlineCircle / 2, barX + DockInlineCircle + DockGap + DockInlineCircle / 2, travel)
        }
        val itemWidth = lerp(expandedItem, DockInlineCircle, travel)
        val searchCenter = lerp(center(1), searchTarget, travel)
        val contentCenter = lerp(center(contentIndex), leftCenter, travel)
        val otherCenter = lerp(center(otherContent), leftCenter, travel)
        val otherAlpha = (1f - travel * 1.6f).coerceIn(0f, 1f)

        // Convx nudges the whole pill a few dp in the drag direction, eased and clamped, so a drag
        // feels like tugging the bar instead of sliding a puck inside a static shell.
        val stepPx = with(density) { step.toPx() }
        val totalWidthPx = with(density) { width.toPx() }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset = remember(density, totalWidthPx) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        // Break the construction-time self-reference: canDrag needs the animation's current value.
        class DragHolder { var instance: DampedDragAnimation? = null }
        val holder = remember { DragHolder() }
        val drag = remember(scope, density, expandedItem, width) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = currentIndex.toFloat(),
                valueRange = 0f..(Tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                canDrag = { offset ->
                    val anim = holder.instance ?: return@DampedDragAnimation true
                    if (stepPx == 0f) return@DampedDragAnimation false
                    val indicatorX = anim.value * stepPx
                    val pad = with(density) { DockBarInset.toPx() }
                    (pad + indicatorX + offset.x) in 0f..totalWidthPx
                },
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().coerceIn(0, Tabs.size - 1)
                    if (currentIndex != target) {
                        currentIndex = target
                        onSelectNow.value(Tabs[target].route)
                    } else {
                        animateToValue(target.toFloat())
                    }
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    if (stepPx > 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / stepPx)
                                .coerceIn(0f, (Tabs.size - 1).toFloat())
                        )
                        scope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                    }
                },
            ).also { holder.instance = it }
        }
        // Selection changes also arrive from navigation. They only move the indicator; taps and
        // completed drags navigate explicitly.
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { drag.animateToValue(it.toFloat()) }
        }
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) currentIndex = selectedIndex
        }
        LaunchedEffect(inline) {
            if (inline) offsetAnimation.snapTo(0f)
        }

        // Convx's finger sheen. The puck owns the gesture; this draws on the bar so the glow can
        // sit under the puck's glass instead of on top of it.
        val interactiveHighlight = remember(scope, drag, density, expandedItem) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        with(density) { (DockBarInset + expandedItem * (drag.value + 0.5f)).toPx() } +
                            panelOffset.value,
                        size.height / 2f,
                    )
                },
            )
        }
        // The puck samples the bar's own pixels (glass + icons). Convx only records the bar while
        // pressed so an idle dock pays nothing for the lens.
        val barBackdrop = rememberLayerBackdrop()
        val pressed by remember(drag) { derivedStateOf { drag.pressProgress > 0f } }
        val lensBackdrop = if (appBackdrop != null) {
            rememberCombinedBackdrop(appBackdrop, barBackdrop)
        } else {
            null
        }

        // Press-only recording covers the whole row (tab pill + search circle), not just the pill:
        // the puck travels over all three tabs, so a drag onto Search has to refract the magnifier
        // instead of painting the page over it.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pressed && !lowDevice) Modifier.layerBackdrop(barBackdrop) else Modifier),
        ) {
            // Expanded tabs live inside the pill that shrinks into the left circle.
            Box(
                Modifier
                    .offset(x = barX, y = barY)
                    .width(barWidth)
                    .height(DockBarHeight)
                    .graphicsLayer {
                        translationX = panelOffset.value
                        translationY = PlayerMorph.landingDipPx * LandingEchoShare
                        shape = CircleShape
                        // Convx clips the pill only while it morphs; at rest the pressed puck is
                        // allowed to grow past the bar's bounds.
                        clip = travel > 0.001f || pressed
                    }
                    .liquidGlass(CircleShape)
                    .then(interactiveHighlight.modifier),
            ) {
                Tabs.forEachIndexed { index, tab ->
                    if (index == 1) return@forEachIndexed
                    val target = if (index == contentIndex) contentCenter else otherCenter
                    val alpha = if (index == contentIndex) 1f else otherAlpha
                    Box(
                        Modifier
                            .offset(x = target - barX - itemWidth / 2)
                            .width(itemWidth)
                            .fillMaxHeight(),
                    ) {
                        DockTabItem(
                            tab = tab,
                            selected = tab.route == selected,
                            labelAlpha = labelAlpha,
                            itemAlpha = alpha,
                            onClick = { onTab(tab) },
                        )
                    }
                }
            }

            // Search is its own circle in both states, like Convx's standalone tab.
            Box(
                Modifier
                    .offset(x = searchCenter - itemWidth / 2, y = barY)
                    .width(itemWidth)
                    .height(DockBarHeight)
                    .graphicsLayer {
                        translationX = panelOffset.value
                        translationY = PlayerMorph.landingDipPx * LandingEchoShare
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = travel.coerceIn(0f, 1f) }
                        .liquidGlass(CircleShape)
                        .clip(CircleShape),
                )
                DockTabItem(
                    tab = SearchTab,
                    selected = SearchTab.route == selected,
                    labelAlpha = labelAlpha,
                    itemAlpha = 1f,
                    // Convx's standalone search tab navigates without expanding the bar.
                    onClick = { onSelect(SearchTab.route) },
                )
            }
        }

        if (hasPlayer) {
            val playerWidth = lerp(width, width - (DockInlineCircle + DockGap) * 2, miniX)
            val playerHeight = lerp(DockPlayerHeight, DockInlinePlayerHeight, miniY)
            MiniPlayer(
                onOpen = onOpenPlayer,
                height = playerHeight,
                modifier = Modifier
                    .offset(x = (width - playerWidth) / 2)
                    .width(playerWidth),
            )
        }

        // The draggable puck: topmost pointer target in the dock. Taps still reach the tabs below
        // (the drag inspector never consumes), drags move it, and the lens only exists while held.
        Box(
            Modifier
                .offset(
                    x = lerp(barX + DockBarInset, leftCenter - DockInlineCircle / 2, travel),
                    y = barY + DockPuckInset,
                )
                .width(lerp(expandedItem, DockInlineCircle, travel))
                .height(DockBarHeight - DockPuckInset * 2)
                .graphicsLayer {
                    translationX = drag.value * step.toPx() * (1f - travel)
                    alpha = thumbAlpha
                }
                .then(if (inline) Modifier else interactiveHighlight.gestureModifier)
                .then(if (inline) Modifier else drag.modifier)
                .then(
                    if (pressed && !lowDevice && lensBackdrop != null && isRenderEffectSupported()) {
                        Modifier.drawBackdrop(
                            backdrop = lensBackdrop,
                            // The bar's own shape family: puck and bar concentric.
                            shape = { CircleShape },
                            effects = {
                                val progress = drag.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = true,
                                    chromaticAberrationAmount = 0.5f,
                                )
                            },
                            highlight = {
                                Highlight(width = 1.dp, alpha = drag.pressProgress)
                            },
                            shadow = null,
                            innerShadow = {
                                val progress = drag.pressProgress
                                if (progress > 0f) {
                                    InnerShadow(
                                        radius = 8.dp * progress,
                                        color = Color.Black.copy(alpha = 0.15f),
                                        alpha = progress,
                                    )
                                } else {
                                    null
                                }
                            },
                            layerBlock = {
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = drag.pressProgress
                                drawRect(
                                    if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                    } else {
                        Modifier
                            .graphicsLayer {
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                // Apply velocity-based stretch to the puck outline.
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                            }
                            // The puck overlaps tab icons, so its surface stays translucent.
                            .drawWithCache {
                                // Bar's shape family, drawn not clipped.
                                val outline = CircleShape.createOutline(size, layoutDirection, this)
                                val wash = if (dark) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.Black.copy(alpha = 0.1f)
                                }
                                onDrawBehind {
                                    val progress = drag.pressProgress
                                    drawOutline(outline, color = wash, alpha = 1f - progress)
                                    drawOutline(outline, color = Color.Black.copy(alpha = 0.03f * progress))
                                }
                            }
                    },
                ),
        )
    }
}

@Composable
private fun DockTabItem(
    tab: Tab,
    selected: Boolean,
    labelAlpha: Float,
    itemAlpha: Float,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = itemAlpha }
            .clip(CircleShape)
            // The fading tab still sits on the left circle while inline; it must not take the tap.
            .then(
                if (itemAlpha > 0.05f) Modifier.pressable(pressScale = 0.95f, onClick = onClick) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            tab.icon,
            null,
            tint = if (selected) Tok.Accent else Tok.Label2,
            modifier = Modifier
                .size(tab.glyph)
                .graphicsLayer { translationY = -6.dp.toPx() * (1f - labelAlpha) },
        )
        Text(
            tab.label,
            color = if (selected) Tok.Accent else Tok.Label2,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer {
                    alpha = labelAlpha
                    translationY = 18.dp.toPx()
                },
        )
    }
}
