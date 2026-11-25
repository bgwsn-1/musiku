package app.musiku.ui.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.MiniPlayerBody
import app.musiku.ui.components.miniExtras
import app.musiku.ui.theme.Tok
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Convx's player morph (`ui/player/PlayerContainerMorph.kt`, `PlayerMorphGeometry`).
 *
 * The full player is not a sheet that slides: it is one container that interpolates between the
 * mini pill's rect and the screen rect. The recorded player is scaled by the container's width and
 * clipped to it, the background hands off across the first fifth, the page content fades in from a
 * tenth to six tenths, and a close lands with speed — the pill dips below its slot and squashes
 * before settling.
 *
 * Convx records the player and the pill into `GraphicsLayer`s and draws the flight by hand. Musiku
 * composes the real player once and draws it through the same geometry, so there is no recording
 * to keep in sync and text stays crisp at rest.
 */

/** Close/open spring from [Convx MotionTokens]: `PlayerSheetStiffness`. */
private const val SheetStiffness = 360f

/** Convx lands the close underdamped so the pill can carry the arrival speed. */
private const val SheetLandingDamping = 0.72f
private const val LandingDamping = 0.5f
private const val LandingStiffness = 140f
private val LandingMaxDip = 22.dp

/** `PLAYER_MORPH_LAYER_HANDOFF_PROGRESS` — the background is gone by a fifth. */
private const val LayerHandoffProgress = 0.2f

/** `PLAYER_MORPH_CONTENT_APPEAR_START` / `_END`. */
private const val ContentAppearStart = 0.1f
private const val ContentAppearEnd = 0.6f

/** `PLAYER_MORPH_MINI_FADE_END` — the pill's own contents ride the card until 30%. */
private const val MiniContentFadeEnd = 0.3f

/** Same shape as the dock's mini pill, so the flight lands exactly on it. */
internal val MiniPlayerCornerRadius = 26.dp

/** `PlayerLandingSquash` / `PlayerLandingSpread` from Convx's motion tokens. */
internal const val LandingSquash = 0.14f
internal const val LandingSpread = 0.04f

/** How much of the pill's landing dip the tab bar echoes (Convx `LandingEchoShare`). */
internal const val LandingEchoShare = 0.4f

/**
 * Cross-tree morph state, the same reason Convx has this object: the mini pill lives in the dock,
 * the player lives in its own overlay, and the landing dip belongs to the pill after the card is
 * gone. Read from draw phase only.
 */
object PlayerMorph {
    /** Mini pill bounds in root coordinates, reported by the dock. */
    var miniBounds: Rect? by mutableStateOf(null)

    /** Mini cover (44dp square) bounds in root coordinates, reported by the dock. */
    var miniArtBounds: Rect? by mutableStateOf(null)

    /** Expanded cover bounds in root coordinates, reported by the player. */
    var fullArtBounds: Rect? by mutableStateOf(null)

    /** The squared player's live pause shrink (0.729 when paused), so the flight lands on it. */
    var coverScale: Float by mutableFloatStateOf(1f)

    /** True when the active player look is Hero (full-bleed), which has no square border/shadow. */
    var isHero: Boolean by mutableStateOf(false)

    /** The mini thumb's live pause shrink, same role at the pill end. */
    var miniArtScale: Float by mutableFloatStateOf(1f)

    /** Current landing dip below the pill's slot, px (0 at rest). */
    var landingDipPx: Float by mutableFloatStateOf(0f)

    /** -1..1 dip / max dip, used for the pill's liquid squash. */
    var landingSquash: Float by mutableFloatStateOf(0f)

    /** Progress 1 = expanded, 0 = collapsed. Read in draw phase only. */
    var progressProvider: () -> Float = { 1f }
}

/** Morph progress 1 = expanded, 0 = collapsed. Read in draw phase, never in composition. */
val LocalPlayerMorphProgress = staticCompositionLocalOf<() -> Float> { { 1f } }

private fun easeInCubic(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    return v * v * v
}

/** `PlayerMorphGeometry.backgroundAlpha`. */
fun playerBackgroundAlpha(progress: Float): Float =
    easeInCubic(progress.coerceIn(0f, 1f) / LayerHandoffProgress)

/** `PlayerMorphGeometry.pageAlpha`. */
fun playerContentAlpha(progress: Float): Float =
    easeInCubic(
        ((progress.coerceIn(0f, 1f) - ContentAppearStart) / (ContentAppearEnd - ContentAppearStart))
            .coerceIn(0f, 1f),
    )

/** `PlayerMorphGeometry.miniContentAlpha`: 1 at the pill, gone once the card is a third open. */
fun playerMiniContentAlpha(progress: Float): Float =
    1f - easeOutCubic((progress.coerceIn(0f, 1f) / MiniContentFadeEnd).coerceIn(0f, 1f))

/**
 * Hosts the player overlay and owns its expand/collapse flight.
 *
 * [visible] is the logical open flag: when it goes false the player keeps composing until the
 * close spring lands, then the pill's landing dip takes over and the overlay unmounts.
 */
@Composable
fun PlayerMorphHost(
    visible: Boolean,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    // The page itself is dropped the moment the sheet lands: at that point it is invisible, and
    // keeping it composed would let its full-screen (scaled-away) touch targets eat taps meant
    // for the pill while the landing dip plays.
    var contentMounted by remember { mutableStateOf(visible) }
    // Bounds matter: Convx pins `Animatable` to the collapsed anchor so the underdamped close
    // spring stops *on* the pill instead of ringing past it — that is what hands the arrival
    // speed to the landing in the same frame. Without a bound the card would sit invisibly
    // under the pill for another ~300 ms and the landing would start near rest.
    val progress = remember { Animatable(if (visible) 1f else 0f).apply { updateBounds(0f, 1f) } }
    val landing = remember { Animatable(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // The dock's live glass cannot be sampled inside a draw scope, so the handoff uses the app's
    // surface colour — the tint the pill reads as once the page behind it is blurred away.
    val plateColor = Tok.Surface
    val progressReader = remember { { progress.value } }

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            contentMounted = true
            PlayerMorph.landingDipPx = 0f
            PlayerMorph.landingSquash = 0f
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = SheetStiffness,
                ),
            )
        } else if (mounted) {
            val result = progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = SheetLandingDamping,
                    stiffness = SheetStiffness,
                ),
            )
            // The card reached the pill; the page is invisible from here on.
            contentMounted = false
            // The card reached the pill. Hand the arrival speed to the pill's landing so a fast
            // flick dips further than a tap, exactly like Convx's `playPillLanding`.
            val pill = PlayerMorph.miniBounds
            val velocity = result.endState.velocity
            if (progress.value <= 0.01f && pill != null && heightPx > 0f && velocity < 0f) {
                // Card centre per unit of progress near the pill end: screen centre -> pill centre.
                val travelPx = pill.center.y - heightPx / 2f
                val maxDip = with(density) { LandingMaxDip.toPx() }
                if (travelPx != 0f && maxDip > 0f) {
                    try {
                        landing.snapTo(0f)
                        landing.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = LandingDamping,
                                stiffness = LandingStiffness,
                            ),
                            initialVelocity = velocity,
                        ) {
                            // Soft-saturating dip: linear in velocity near the slot, capped at 22dp.
                            val dip = maxDip * tanh(-landing.value * travelPx / maxDip)
                            PlayerMorph.landingDipPx = dip
                            PlayerMorph.landingSquash = (dip / maxDip).coerceIn(-1f, 1f)
                        }
                    } finally {
                        PlayerMorph.landingDipPx = 0f
                        PlayerMorph.landingSquash = 0f
                    }
                }
            }
            mounted = false
        }
    }

    SideEffect { PlayerMorph.progressProvider = { progress.value } }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() },
    ) {
        if (mounted) {
            // The card: the player drawn once at screen size, scaled by the container's width and
            // clipped to the container's rounded rect — Convx's recorded-player flight.
            Box(
                Modifier
                    .fillMaxSize()
                    // The guard has to be a PARENT of the page, never a sibling on top of it: a
                    // full-screen sibling with pointer input wins hit-testing and would eat every
                    // tap even when it consumes nothing. As a parent it is only in the path, and
                    // once the page is gone it is removed entirely so the landed pill takes input.
                    .then(
                        if (contentMounted) {
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (progress.isRunning) event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .drawWithContent {
                        val p = progress.value
                        if (p >= 1f) {
                            drawContent()
                            return@drawWithContent
                        }
                        if (p <= 0f) return@drawWithContent
                        val pill = PlayerMorph.miniBounds
                        if (pill == null || pill.width <= 0f || pill.height <= 0f) {
                            drawContent()
                            return@drawWithContent
                        }

                        val full = Rect(0f, 0f, size.width, size.height)
                        val rect = sharedContainerRect(source = pill, target = full, progress = p)
                        // Convx derives the pill end from `mini.height / 2`; Musiku's dock pill is
                        // 26dp-rounded, so the flight has to land on that exact shape.
                        val radius = lerp(with(density) { MiniPlayerCornerRadius.toPx() }, 0f, p)
                        val scale = rect.width / size.width

                        val path = Path().apply {
                            reset()
                            addRoundRect(RoundRect(rect, CornerRadius(radius, radius)))
                        }
                        clipPath(path) {
                            // The pill's own surface, stretched over the card. Convx hands the mini
                            // recording to the card for the first/last fifth; a flat plate that
                            // matches the dock glass reads the same at this speed. It fades out on
                            // the last few percent so the real pill takes over without a pop.
                            val plateAlpha = (1f - playerBackgroundAlpha(p)) *
                                (p / 0.05f).coerceIn(0f, 1f)
                            if (plateAlpha > 0f) {
                                drawRect(
                                    color = plateColor.copy(alpha = 0.85f * plateAlpha),
                                    topLeft = rect.topLeft,
                                    size = rect.size,
                                )
                            }
                            withTransform({
                                translate(rect.left, rect.top)
                                scale(scale, scale, pivot = Offset.Zero)
                            }) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    },
            ) {
                if (contentMounted) {
                    CompositionLocalProvider(LocalPlayerMorphProgress provides progressReader) {
                        content()
                    }
                }
            }

            // Convx draws the recorded pill over the stretched plate until the background takes
            // over: the card and the pill are one body for the first third. Composed after the
            // card so it rides on that plate, and fades exactly where the real pill takes back.
            PillContentOnCard(progress)

            // The cover is a separate recorded layer in Convx: it flies between the mini thumb
            // and the player's cover, uncut by the shrinking card.
            FlyingArtwork(progress, artworkUrl)
        }
    }
}

/**
 * The pill's contents on the card (`PlayerContainerMorph`'s mini layer).
 *
 * Convx records the pill once and draws that recording into the card for the first 30% of the
 * flight, kept at its own size and glued to the card's top edge while the card grows up out of
 * the pill. Musiku composes the same contents a second time instead of recording them: the
 * artwork slot is left empty because the cover is already flying as its own layer.
 */
@Composable
private fun PillContentOnCard(progress: Animatable<Float, AnimationVector1D>) {
    val mini = PlayerMorph.miniBounds ?: return
    if (mini.width <= 0f || mini.height <= 0f) return
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val pos by app.player.position.collectAsState()
    val track = ps.current ?: return
    val density = LocalDensity.current
    val widthDp = with(density) { mini.width.toDp() }
    val heightDp = with(density) { mini.height.toDp() }

    Box(
        Modifier
            .offset { IntOffset(mini.left.roundToInt(), mini.top.roundToInt()) }
            .size(widthDp, heightDp)
            .graphicsLayer {
                val p = progress.value.coerceIn(0f, 1f)
                // The card's top edge is `mini.top * (1 - p)`; the content stays glued to it,
                // which for a layer already sitting on the pill is a slide of `-mini.top * p`.
                translationY = -mini.top * p
                // Convx stacks the gradient over the pill content, so only the share of the
                // content the background has not covered yet is left visible.
                alpha = if (p <= 0f) {
                    0f
                } else {
                    playerMiniContentAlpha(p) * (1f - playerBackgroundAlpha(p))
                }
            },
    ) {
        MiniPlayerBody(
            track = track,
            playing = ps.playing,
            fraction = if (ps.durationSec > 0f) (pos / ps.durationSec).coerceIn(0f, 1f) else 0f,
            interactive = false,
            // Match the slot the pill was captured from: inline it is 60dp with Next collapsed.
            height = heightDp,
            extras = miniExtras(widthDp),
            artSlot = { Box(Modifier.size(44.dp)) },
        )
    }
}

/** Convx `PlayerMorphGeometry.containerRect`: every edge lerps linearly with progress. */
private fun sharedContainerRect(source: Rect, target: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerp(source.left, target.left, p),
        top = lerp(source.top, target.top, p),
        right = lerp(source.right, target.right, p),
        bottom = lerp(source.bottom, target.bottom, p),
    )
}

/**
 * Convx `PlayerMorphGeometry.artworkWindow`: the clip window travels with the container's
 * horizontal easing but reaches the pill's height sooner, weighted 40% toward linear.
 */
private fun artworkWindow(source: Rect, target: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    val centerX = lerp(source.center.x, target.center.x, easeOutCubic(p))
    val centerY = lerp(
        source.center.y,
        target.center.y,
        lerp(easeInCubic(p), p, 0.4f),
    )
    val halfWidth = lerp(source.width, target.width, p).coerceAtLeast(1f) / 2f
    val halfHeight = lerp(source.height, target.height, p).coerceAtLeast(1f) / 2f
    return Rect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
}

/**
 * Convx `PlayerArtworkMorphOverlay`, simplified for the squared player: one square image at the
 * cover's own size, a rounded window interpolating from the mini thumb to the cover, the whole
 * layer scaled/translated by the window. Both ends are square, so the image never distorts.
 */
@Composable
private fun FlyingArtwork(progress: Animatable<Float, AnimationVector1D>, artworkUrl: String?) {
    val miniLayout = PlayerMorph.miniArtBounds ?: return
    val full = PlayerMorph.fullArtBounds ?: return
    if (artworkUrl == null) return
    if (miniLayout.width <= 0f || miniLayout.height <= 0f || full.width <= 0f || full.height <= 0f) return

    val hero = PlayerMorph.isHero
    val density = LocalDensity.current
    val sidePx = maxOf(full.width, full.height)
    val boxW = with(density) { (if (hero) full.width else sidePx).toDp() }
    val boxH = with(density) { (if (hero) full.height else sidePx).toDp() }
    val mini = miniLayout.scaledAboutCenter(PlayerMorph.miniArtScale.coerceIn(0.1f, 1f))
    val miniRadius = with(density) { 8.dp.toPx() } * PlayerMorph.miniArtScale.coerceIn(0.1f, 1f)
    val fullRadius = if (hero) 0f else with(density) { 12.dp.toPx() }
    val shadowPx = if (hero) 0f else with(density) { 8.dp.toPx() }

    Box(
        Modifier
            .offset { IntOffset(full.left.roundToInt(), full.top.roundToInt()) }
            .size(boxW, boxH)
            .graphicsLayer {
                val p = progress.value
                if (p <= 0f || p >= 1f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = if (hero) (1f - playerContentAlpha(p)).coerceIn(0f, 1f) else 1f
                if (alpha <= 0f) return@graphicsLayer
                val shrink = if (hero) 1f else PlayerMorph.coverScale.coerceIn(0.1f, 1f)
                val target = full.scaledAboutCenter(shrink)
                val window = artworkWindow(mini, target, p)
                val scale = if (hero) {
                    lerp(mini.width / full.width, 1f, p).coerceAtLeast(0.05f)
                } else {
                    lerp(maxOf(mini.width, mini.height), sidePx * shrink, p) / sidePx
                }
                transformOrigin = TransformOrigin.Center
                scaleX = scale
                scaleY = scale
                translationX = window.center.x - target.center.x
                translationY = window.center.y - target.center.y
                val radius = lerp(miniRadius, fullRadius * shrink, p)
                shape = CenteredWindowShape(
                    windowWidth = window.width / scale,
                    windowHeight = window.height / scale,
                    radius = radius / scale,
                )
                clip = true
                shadowElevation = shadowPx * p
            },
    ) {
        Artwork(artworkUrl, Modifier.fillMaxSize())
    }
}

/** Rounded window of the given size centred in the layer (layer-local px). */
private class CenteredWindowShape(
    private val windowWidth: Float,
    private val windowHeight: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (size.width - windowWidth) / 2f
        val top = (size.height - windowHeight) / 2f
        val r = radius.coerceIn(0f, minOf(windowWidth, windowHeight) / 2f)
        return Outline.Rounded(
            RoundRect(left, top, left + windowWidth, top + windowHeight, CornerRadius(r, r)),
        )
    }
}

private fun Rect.scaledAboutCenter(factor: Float): Rect =
    if (factor >= 1f) {
        this
    } else {
        Rect(
            left = center.x - width * factor / 2f,
            top = center.y - height * factor / 2f,
            right = center.x + width * factor / 2f,
            bottom = center.y + height * factor / 2f,
        )
    }

private fun easeOutCubic(value: Float): Float {
    val inverse = 1f - value.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
