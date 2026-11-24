package app.musiku.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.RepeatMode
import app.musiku.model.Track
import app.musiku.R
import app.musiku.ui.components.Artwork
import app.musiku.ui.theme.LocalLowDevice
import app.musiku.ui.theme.MIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Convx's Now Playing (`ui/player/Player_v2.kt`, `PlayerV2Frame` portrait branch):
 *
 *   handle (54×5 at 30%) → weighted pane → controls → 12dp-padded tab row on the navigation inset.
 *
 * Two looks, Convx's `PlayerLook`:
 *  • [PlayerLook.Squared] — a 32dp-gutter square card, 12dp corners, 8dp shadow, 0.729× while paused.
 *  • [PlayerLook.Hero] — full bleed: the sleeve runs edge to edge and to the true top of the
 *    screen, the handle floats over it, and its foot dissolves into the field through a
 *    `DstIn` smoothstep so the title row docks under the fade.
 *
 * The pane draws its [PlayerSlot]s in the stored [layoutOrder], skipping [hiddenSlots], so the
 * layout builder in settings really rearranges the player. Chrome below the meta row is composited
 * with a Plus pass (Convx's `flamingoChrome`): over a dark art background that is what keeps the
 * unplayed track and the time labels coloured instead of flat grey.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    look: PlayerLook = PlayerLook.Squared,
    artworkStyle: PlayerArtworkStyle = PlayerArtworkStyle.Card,
    background: PlayerBackgroundStyle = PlayerBackgroundStyle.Gradient,
    layoutOrder: List<PlayerSlot> = PlayerSlot.DefaultOrder,
    hiddenSlots: Set<PlayerSlot> = emptySet(),
    // Morph staging (Convx draws the background and the page as two recorded layers): the field
    // leads across the first fifth, the page follows from a tenth to six tenths. Lambdas so the
    // flight stays a draw-phase read and never recomposes the player.
    backgroundAlpha: () -> Float = { 1f },
    contentAlpha: () -> Float = { 1f },
) {
    var menuOpen by remember { mutableStateOf(false) }
    val track = state.track
    val noRipple = remember { MutableInteractionSource() }
    // The player ships its own field, so its menu samples *that* — PlayerMenu is a glass panel
    // over the player, not over the page behind it.
    val haze = remember { HazeState() }
    val lowDevice = LocalLowDevice.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Convx keeps the tab row 31dp off the screen edge, inset included.
    val bottomBarPad = (31.dp - navBottom).coerceAtLeast(4.dp)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // A canvas fills the bleed frame like the sleeve does: PlayerView crops a 16:9 video to the
    // slot (resize_mode=zoom), so the full-bleed look no longer has to fall back to the card.
    val hero = look == PlayerLook.Hero
    val order = remember(layoutOrder) { PlayerSlot.normalize(layoutOrder) }
    // Full bleed means "the art is the field", so it pins the gradient like Convx does.
    val fieldStyle = if (hero) PlayerBackgroundStyle.Gradient else background

    BackHandler(enabled = menuOpen) { menuOpen = false }

    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (lowDevice) Modifier else Modifier.haze(haze))) {
            // The black under the field fades with it so the card can hand off to the pill.
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = backgroundAlpha() }) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                NowPlayingBackground(
                    artworkUrl = track.thumbs,
                    modifier = Modifier.fillMaxSize(),
                    style = fieldStyle,
                    hero = hero,
                    isPlaying = state.playing,
                )
            }
        }

        var sheetDragTotal by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val threshold = 120.dp.toPx()
                    detectVerticalDragGestures(
                        onDragStart = { sheetDragTotal = 0f },
                        onDragEnd = {
                            if (sheetDragTotal > threshold) actions.onClose()
                            sheetDragTotal = 0f
                        },
                        onDragCancel = { sheetDragTotal = 0f },
                        onVerticalDrag = { _, dy -> sheetDragTotal += dy },
                    )
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // The bleed sleeve reaches the true top edge, so the status inset moves onto
                    // the floating handle instead (Convx's `overlayHandle` branch).
                    .padding(top = if (hero) 0.dp else statusTop)
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = contentAlpha() },
            ) {
                if (!hero) PlayerHandle(onClose = actions.onClose)

                order.forEach { slot ->
                    if (slot in hiddenSlots) return@forEach
                    when (slot) {
                        PlayerSlot.AlbumArt -> if (hero) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                PlayerCoverSlot(
                                    track = track,
                                    playing = state.playing,
                                    hero = true,
                                    style = artworkStyle,
                                )
                            }
                        } else {
                            // Convx centres the squared card between two weights: the first one,
                            // and the "leftover pane height" spacer right under the pane, which
                            // is what docks the meta row and the transport beneath the card.
                            Spacer(Modifier.weight(1f))
                            PlayerCoverSlot(
                                track = track,
                                playing = state.playing,
                                hero = false,
                                style = artworkStyle,
                            )
                            Spacer(Modifier.weight(1f))
                        }

                        PlayerSlot.TrackInfo -> PlayerMetaRow(
                            track = track,
                            liked = state.liked,
                            actions = actions,
                            onMore = { menuOpen = true },
                        )

                        PlayerSlot.SeekBar -> Box(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        ) { PlayerSeek(state = state, actions = actions) }

                        PlayerSlot.Controls -> {
                            Spacer(Modifier.height(30.dp)) // Space between timeline and transport.
                            PlayerTransport(
                                playing = state.playing,
                                actions = actions,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }

                        PlayerSlot.ActionRow -> {
                            Spacer(Modifier.height(23.dp)) // Room for the volume control's target.
                            PlayerVolume(
                                volume = state.volume,
                                onVolume = actions.onVolume,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }

                // Every weight in the pane belongs to the album slot, so hiding it would collapse
                // the column to its intrinsic height and strand the tab row mid-page. This is the
                // spare that keeps it at the foot; the slot itself owns the slack when on screen.
                if (PlayerSlot.AlbumArt in hiddenSlots) Spacer(Modifier.weight(1f))

                PlayerTabs(state = state, actions = actions, bottomPad = bottomBarPad)
            }

            if (hero) {
                // Floating handle: Convx keeps it 16dp under the status bar, over the artwork.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = statusTop),
                ) {
                    PlayerHandle(onClose = actions.onClose)
                }
            }
        }

        // menu: scrim + floating panel that grows from the bottom-right corner
        androidx.compose.animation.AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = noRipple, indication = null) { menuOpen = false },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = menuOpen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(160),
            ),
        ) {
            PlayerMenu(
                track = track,
                state = state,
                actions = actions,
                dismiss = { menuOpen = false },
                maxHeight = maxHeight - 120.dp,
                haze = haze,
            )
        }
    }
}

/**
 * Convx's handle: a 54×5 grab pill at 30% of the primary ink, 16dp above and 8dp below. Dragging
 * it down still closes the screen (in Convx a bottom sheet owns that gesture; here the screen is
 * the sheet).
 */
@Composable
private fun PlayerHandle(onClose: () -> Unit) {
    val closeSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 5.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color.White.copy(alpha = 0.3f))
                .semantics { contentDescription = "Tutup pemutar" }
                .clickable(interactionSource = closeSource, indication = null, onClick = onClose),
        )
    }
}

/**
 * The album art slot in whichever look is active.
 *
 * Full bleed reproduces Convx's `Modifier.layout`: the sleeve wants 524/393 of the width, but its
 * laid-out box is 33/393 shorter, and [heroOverlapPx] hands that difference back to the caller so
 * the title row docks under the fade instead of under a visible gap. Squared keeps the 32dp
 * gutter, the shadow and the pause shrink.
 */
@Composable
private fun PlayerCoverSlot(
    track: Track,
    playing: Boolean,
    hero: Boolean,
    style: PlayerArtworkStyle,
) {
    val coverStyle = style.coverStyle()
    val rotation by rememberCoverRotation(coverStyle, playing)
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val cornerRadius = if (hero) 0.dp else 12.dp
    val shape = coverStyle.shape(cornerRadius)
    val scale by animateFloatAsState(
        // More damping on the way down so the card does not overshoot into the meta row.
        targetValue = if (playing) 1f else 0.729f,
        animationSpec = spring(
            dampingRatio = if (playing) 0.6f else 0.95f,
            stiffness = 160f,
        ),
        label = "coverScale",
    )
    SideEffect { PlayerMorph.coverScale = scale }

    val frame = if (hero) {
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val overlap = heroOverlapPx(constraints.maxWidth)
                // Reserve exactly the title row (8 + two 22pt lines + 16); larger text grows it
                // with fontScale, as Convx does.
                val artworkHeight = minOf(
                    (constraints.maxWidth * 524f / 393f).roundToInt(),
                    (constraints.maxHeight - (24.dp + 44.dp * fontScale).roundToPx() + overlap)
                        .coerceAtLeast(1),
                )
                val cover = measurable.measure(
                    constraints.copy(minHeight = artworkHeight, maxHeight = artworkHeight),
                )
                layout(cover.width, (artworkHeight - overlap).coerceAtLeast(0)) {
                    cover.placeRelative(0, 0)
                }
            }
            .heroFootFade(rememberHeroFadeBrush(wide = false))
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .aspectRatio(1f)
            // The morph's full end: the flight lands on this rect (not the pause-shrunk paint).
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // The flying cover owns the artwork while the card travels.
                val p = PlayerMorph.progressProvider()
                alpha = if (p > 0.0005f && p < 0.9995f &&
                    PlayerMorph.miniArtBounds != null && PlayerMorph.fullArtBounds != null
                ) 0f else 1f
            }
            .shadow(8.dp, shape)
    }

    PlayerCoverSurface(
        style = coverStyle,
        rotation = rotation,
        cornerRadius = cornerRadius,
        modifier = frame.onGloballyPositioned {
            if (it.isAttached) PlayerMorph.fullArtBounds = it.boundsInRoot()
        },
    ) {
        CanvasOrArtwork(track = track, playing = playing, modifier = Modifier.fillMaxSize())
    }
}

/** Title (SemiBold) over artist (0.78 ink), with the like and options glyphs on the right. */
@Composable
private fun PlayerMetaRow(
    track: Track,
    liked: Boolean,
    actions: NowPlayingActions,
    onMore: () -> Unit,
) {
    val metaStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.43).sp,
    )
    val albumClick = actions.onOpenAlbum
    val artistClick = actions.onOpenArtist
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FadeSwapText(
                text = track.title,
                style = metaStyle,
                color = Color.White,
                weight = FontWeight.SemiBold,
                modifier = if (albumClick != null) Modifier.clickable(onClick = albumClick) else Modifier,
            )
            if (!track.artist.isNullOrEmpty()) {
                FadeSwapText(
                    text = track.artist,
                    style = metaStyle,
                    color = Color.White.copy(alpha = 0.78f),
                    weight = FontWeight.Normal,
                    modifier = if (artistClick != null) Modifier.clickable(onClick = artistClick) else Modifier,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.plusChrome()) {
            IconButton(onClick = actions.onLike, modifier = Modifier.size(40.dp)) {
                Crossfade(
                    targetState = if (liked) R.drawable.flamingo_favorited else R.drawable.flamingo_favorite,
                    animationSpec = tween(180),
                    label = "likeGlyph",
                ) { id ->
                    Glyph(
                        id = id,
                        contentDescription = if (id == R.drawable.flamingo_favorited) "Batal suka" else "Suka",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).likePop(liked, track.videoId),
                    )
                }
            }
            IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                Glyph(
                    id = R.drawable.flamingo_more,
                    contentDescription = "Opsi lainnya",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Timeline: 7dp track that grows to 12dp while touched, stepped tick dots, and 10sp time labels
 * reading elapsed / -remaining. Scrubbing is local until release, so the bar never fights the
 * position ticker while the finger is down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeek(state: NowPlayingState, actions: NowPlayingActions) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = dragging || pressed
    val trackHeight by animateDpAsState(
        targetValue = if (active) 12.dp else 7.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "trackHeight",
    )
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationSec.coerceAtLeast(0f)
    val value = (scrub ?: state.positionSec).coerceIn(0f, duration.coerceAtLeast(1f))
    val colors = SliderDefaults.colors(
        activeTrackColor = Color.White,
        activeTickColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.05f),
        inactiveTickColor = Color.White.copy(alpha = 0.05f),
        disabledActiveTrackColor = Color.White,
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.05f),
        thumbColor = Color.White,
        disabledThumbColor = Color.White,
    )

    // The labels are a sibling row, not a second child of the caller's Box: stacked in a Box they
    // painted on top of the bar instead of sitting under it.
    Column(Modifier.fillMaxWidth()) {
        // Keep a 23dp visual slot while the Slider retains its 48dp touch target.
        Box(Modifier.fillMaxWidth().height(23.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                valueRange = 0f..duration.coerceAtLeast(1f),
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let(actions.onSeek)
                    scrub = null
                },
                enabled = duration > 0f,
                colors = colors,
                interactionSource = interaction,
                thumb = { Spacer(Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = colors,
                        trackHeight = trackHeight,
                        modifier = Modifier
                            .plusChrome()
                            .graphicsLayer { alpha = 0.45f },
                    )
                },
                modifier = Modifier.fillMaxWidth().requiredHeight(48.dp),
            )
        }

        // Clear of the capsule, with the row's own 18dp line box centred under it.
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), // Align with internal slider padding
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RollingTime(
                seconds = state.positionSec.roundToLong(),
                prefix = "",
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.plusChrome(),
                animate = active,
            )
            RollingTime(
                seconds = if (duration > 0f) (duration - state.positionSec).roundToLong().coerceAtLeast(0) else 0,
                prefix = "-",
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.plusChrome(),
                animate = active,
            )
        }
    }
}

/** Prev / play / next: 61dp press discs on 44dp glyphs, play 72dp; row capped at 302dp. */
@Composable
private fun PlayerTransport(
    playing: Boolean,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 302.dp) // 292pt glyph frame + 2x5: 61pt targets centred on 51pt glyphs
            .fillMaxWidth()
            .plusChrome(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val previousInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = actions.onPrev,
            interactionSource = previousInteraction,
            modifier = Modifier
                .size(61.dp)
                .transportPressDisc(previousInteraction, Color.White.copy(alpha = 0.16f)),
        ) {
            Glyph(
                id = R.drawable.flamingo_rewind,
                contentDescription = "Lagu sebelumnya",
                tint = Color.White,
                modifier = Modifier.size(44.dp).skipGlyphPress(previousInteraction, direction = -1f),
            )
        }

        Spacer(Modifier.weight(1f))

        val playInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = actions.onPlayPause,
            interactionSource = playInteraction,
            modifier = Modifier
                .size(72.dp)
                .transportPressDisc(playInteraction, Color.White.copy(alpha = 0.16f)),
        ) {
            AnimatedPlayPause(
                playing = playing,
                modifier = Modifier.playGlyphPress(playInteraction),
            )
        }

        Spacer(Modifier.weight(1f))

        val nextInteraction = remember { MutableInteractionSource() }
        IconButton(
            onClick = actions.onNext,
            interactionSource = nextInteraction,
            modifier = Modifier
                .size(61.dp)
                .transportPressDisc(nextInteraction, Color.White.copy(alpha = 0.16f)),
        ) {
            Glyph(
                id = R.drawable.flamingo_fforward,
                contentDescription = "Lagu berikutnya",
                tint = Color.White,
                modifier = Modifier.size(44.dp).skipGlyphPress(nextInteraction, direction = 1f),
            )
        }
    }
}

/** Volume: 20dp glyph slots, 8dp gaps, 10dp track that grows to 14dp while touched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerVolume(
    volume: Float,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = dragging || pressed
    var dragVolume by remember { mutableFloatStateOf(volume) }
    LaunchedEffect(volume) { if (!active) dragVolume = volume }
    val shown = if (active) dragVolume else volume
    val trackHeight by animateDpAsState(
        targetValue = if (active) 14.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "volumeTrackHeight",
    )
    val colors = SliderDefaults.colors(
        activeTrackColor = Color.White.copy(alpha = 0.7f),
        activeTickColor = Color.White.copy(alpha = 0.7f),
        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
        inactiveTickColor = Color.White.copy(alpha = 0.15f),
        thumbColor = Color.White,
    )

    Row(
        modifier = modifier
            .widthIn(max = 341.dp)
            .fillMaxWidth()
            // 28dp of speaker + gap on each side leaves a 285dp track matching
            // the visible edges of the previous/next transport glyphs.
            .plusChrome(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuietVolumeGlyph(level = shown, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = shown,
            onValueChange = {
                dragVolume = it
                onVolume(it)
            },
            modifier = Modifier.weight(1f),
            interactionSource = interaction,
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = { sliderState ->
                PlayerSliderTrack(
                    sliderState = sliderState,
                    colors = colors,
                    trackHeight = trackHeight,
                )
            },
        )
        Spacer(Modifier.width(8.dp))
        LoudVolumeGlyph(tint = Color.White)
    }
}

/** A plain speaker; struck through at the mute glyph's 13dp so the bodies match. */
@Composable
private fun QuietVolumeGlyph(level: Float, tint: Color) {
    val muted = level <= 0.01f
    Box(Modifier.size(20.dp), contentAlignment = Alignment.CenterStart) {
        Icon(
            imageVector = if (muted) MIcons.VolumeMute else MIcons.Speaker,
            contentDescription = if (muted) "Bisu" else "Volume rendah",
            tint = tint,
            modifier = Modifier.size(if (muted) 13.dp else 12.dp),
        )
    }
}

/** The loud end keeps one wave glyph at every level, so the speaker itself never resizes. */
@Composable
private fun LoudVolumeGlyph(tint: Color) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.CenterEnd) {
        Glyph(
            id = R.drawable.speaker_wave3,
            contentDescription = "Volume",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Convx's player glyph: a vector drawable tinted flat, drawn to fit its box. The `flamingo_*`
 * assets carry their own aspect ratio, so the box is filled without stretching.
 */
@Composable
private fun Glyph(
    @DrawableRes id: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/**
 * Bottom utility row: four equal thirds-of-the-screen slots (Convx's three, plus Musiku's shuffle
 * and repeat so they stay reachable here). The active slot is a 41dp translucent disc holding a
 * 28dp glyph; inactive slots are plain 24dp glyphs.
 */
@Composable
private fun PlayerTabs(state: NowPlayingState, actions: NowPlayingActions, bottomPad: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).padding(bottom = bottomPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabSlot(Modifier.weight(1f), state.lyricsOpen, onClick = actions.onLyrics) { m, c ->
            Glyph(R.drawable.flamingo_lyrics, tint = c, modifier = m, contentDescription = "Lirik")
        }
        TabSlot(Modifier.weight(1f), state.shuffle, onClick = actions.onShuffle) { m, c ->
            Glyph(R.drawable.shuffle, tint = c, modifier = m, contentDescription = "Acak")
        }
        TabSlot(Modifier.weight(1f), state.repeat != RepeatMode.Off, onClick = actions.onRepeat) { m, c ->
            Glyph(
                id = if (state.repeat == RepeatMode.One) R.drawable.repeat_one else R.drawable.repeat,
                tint = c,
                modifier = m,
                contentDescription = "Ulangi",
            )
        }
        TabSlot(Modifier.weight(1f), state.queueOpen, onClick = actions.onQueue) { m, c ->
            Glyph(R.drawable.flamingo_queue, tint = c, modifier = m, contentDescription = "Antrian")
        }
    }
}

/** One tab slot; tint and glyph size are decided by [TabGlyph]. */
@Composable
private fun TabSlot(
    modifier: Modifier,
    active: Boolean,
    tint: Color = Color.White.copy(alpha = 0.62f),
    onClick: () -> Unit,
    glyph: @Composable (Modifier, Color) -> Unit,
) {
    Box(modifier.plusChrome(), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            TabGlyph(active = active, tint = tint, glyph = glyph)
        }
    }
}

/** Convx's `V2TabGlyph`: plain glyph when idle, readable tinted disc when selected. */
@Composable
private fun TabGlyph(active: Boolean, tint: Color, glyph: @Composable (Modifier, Color) -> Unit) {
    if (!active) {
        glyph(Modifier.size(24.dp), tint)
        return
    }
    // A real translucent fill stays visible over very dark art, where color-dodge
    // on mid-grey can collapse back into the background.
    val lightInk = tint.luminance() > 0.5f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(41.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (lightInk) Color.White.copy(alpha = 0.34f) else tint.copy(alpha = 0.86f),
                    CircleShape,
                ),
        )
        glyph(Modifier.size(28.dp), if (lightInk) Color.Black.copy(alpha = 0.82f) else Color.White)
    }
}

/** Play/pause pops between states: the outgoing glyph shrinks to 0.2× as the incoming springs up. */
@Composable
private fun AnimatedPlayPause(playing: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.2f) + fadeIn(tween(120)))
                .togetherWith(
                    scaleOut(tween(100, easing = FastOutSlowInEasing), targetScale = 0.2f) + fadeOut(tween(90)),
                )
                .using(SizeTransform(clip = false))
        },
        contentKey = { it },
        label = "playPause",
        modifier = modifier,
    ) { isPlaying ->
        Glyph(
            id = if (isPlaying) R.drawable.flamingo_pause else R.drawable.flamingo_play,
            contentDescription = if (isPlaying) "Jeda" else "Putar",
            tint = Color.White,
            modifier = Modifier.size(44.dp),
        )
    }
}

/** Title/artist line that crossfades on change and marquees when it does not fit. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FadeSwapText(
    text: String,
    style: TextStyle,
    color: Color,
    weight: FontWeight,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            // Old line out, a short blank beat, new line in -- eased so neither edge shows.
            fadeIn(tween(380, delayMillis = 120, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(180, easing = FastOutSlowInEasing))
        },
        contentAlignment = Alignment.CenterStart,
        label = "fadeSwapText",
        modifier = modifier,
    ) { value ->
        Text(
            value,
            style = style,
            color = color,
            fontWeight = weight,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200),
        )
    }
}

private data class TimeDigit(val character: Char, val seconds: Long)

/**
 * Convx's `RollingPlaybackTime`: 10sp SemiBold with tabular figures on a fixed digit cell, so the
 * label never jitters. Digits only roll while scrubbing; otherwise they change in place.
 */
@Composable
private fun RollingTime(
    seconds: Long,
    prefix: String,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
) {
    val rolling by rememberUpdatedState(animate)
    val style = remember {
        TextStyle(
            fontSize = 10.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.35.sp,
            fontFeatureSettings = "tnum",
        )
    }
    val formatted = formatTime(seconds)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(style, density) {
        with(density) { measurer.measure("0", style).size.width.toDp() }
    }
    val lineHeight = with(density) { style.lineHeight.toDp() }
    Row(modifier) {
        if (prefix.isNotEmpty()) {
            Text(prefix, color = color, style = style)
        }
        formatted.forEachIndexed { index, character ->
            key(formatted.length - index) {
                if (!character.isDigit()) {
                    Text(character.toString(), color = color, style = style)
                } else {
                    AnimatedContent(
                        targetState = TimeDigit(character, seconds),
                        contentKey = { it.character },
                        transitionSpec = {
                            if (!rolling) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState.seconds >= initialState.seconds) 1 else -1
                                (slideInVertically { it * direction } + fadeIn(tween(120))) togetherWith
                                    (slideOutVertically { -it * direction } + fadeOut(tween(100)))
                            }
                        },
                        modifier = Modifier.width(digitWidth).height(lineHeight).clipToBounds(),
                        label = "playbackTimeDigit",
                    ) { digit ->
                        Text(digit.character.toString(), color = color, style = style)
                    }
                }
            }
        }
    }
}

/** `m:ss`, growing to `h:mm:ss` (and `d:hh:mm:ss`) exactly like Convx's `makeTimeString`. */
private fun formatTime(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val day = total / 86_400
    var rem = total % 86_400
    val hour = rem / 3_600
    rem %= 3_600
    val minute = rem / 60
    val sec = rem % 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

/**
 * Convx's `flamingoChrome`: one Plus pass over the content. Over artwork-derived backgrounds the
 * player's chrome is white at partial alpha, and adding it keeps the tint of what is behind
 * instead of painting flat grey over it.
 */
private fun Modifier.plusChrome(enabled: Boolean = true, strength: Float = 1f): Modifier =
    if (!enabled) this else drawWithContent {
        drawContext.canvas.saveLayer(
            Rect(0f, 0f, size.width, size.height),
            Paint().apply {
                blendMode = BlendMode.Plus
                alpha = strength
            },
        )
        drawContent()
        drawContext.canvas.restore()
    }

/** Disc behind a transport button that grows in 60ms and eases away on a stiff spring. */
@Composable
private fun Modifier.transportPressDisc(
    interactionSource: MutableInteractionSource,
    color: Color,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val disc by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        // Apply press feedback immediately, then ease it away after release.
        animationSpec = if (pressed) tween(60) else spring(dampingRatio = 1f, stiffness = 260f),
        label = "transportDisc",
    )
    return drawBehind {
        if (disc > 0f) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2f * (0.85f + 0.15f * disc),
                alpha = disc,
            )
        }
    }
}

/** Play/pause glyph under the finger: shrinks to 0.2× over ~200ms while held. */
@Composable
private fun Modifier.playGlyphPress(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.2f else 1f,
        animationSpec = if (pressed) {
            tween(220, easing = FastOutSlowInEasing)
        } else {
            // A touch more give on the rebound: playful, still settles inside ~350ms.
            spring(dampingRatio = 0.6f, stiffness = 420f)
        },
        label = "playGlyphPress",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Skip glyph: dips slightly under the finger, then nudges in the skip direction and back. */
@Composable
private fun Modifier.skipGlyphPress(interactionSource: MutableInteractionSource, direction: Float): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "skipGlyphScale",
    )
    val nudge = remember { Animatable(0f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (wasPressed && !pressed) {
            nudge.animateTo(
                0f,
                keyframes {
                    durationMillis = 340
                    0f at 0
                    1f at 130 using FastOutSlowInEasing
                    0f at 340 using FastOutSlowInEasing
                },
            )
        }
        wasPressed = pressed
    }
    val nudgePx = with(LocalDensity.current) { 8.dp.toPx() }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = nudge.value * nudgePx * direction
    }
}

/**
 * Convx's heart pop: settle to a 1.4× peak in 90ms, then spring back (damping 0.35, stiffness
 * 380). Only a like on the same song plays it; the first composition and a song change stay still.
 */
@Composable
private fun Modifier.likePop(isLiked: Boolean, songId: String?): Modifier {
    val pop = remember { Animatable(1f) }
    // Re-keyed per song: skipping to a song with a different liked state is not a tap.
    var settledSong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isLiked, songId) {
        val sameSong = settledSong == songId
        settledSong = songId
        if (!sameSong) {
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        if (isLiked) {
            pop.animateTo(1.4f, tween(90))
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
        }
    }
    return graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
    }
}
