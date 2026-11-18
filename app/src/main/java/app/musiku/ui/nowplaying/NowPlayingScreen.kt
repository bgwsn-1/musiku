package app.musiku.ui.nowplaying

import app.musiku.ui.components.LiquidMetalButton
import app.musiku.ui.theme.MIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import app.musiku.model.RepeatMode
import app.musiku.ui.components.AppleBackdrop
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.PillSlider
import app.musiku.ui.theme.Ink
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

/**
 * Phone Now Playing. Port of components/NowPlaying.tsx: the cover bleeds from the
 * top edge and fades into a near-black, art-tinted backdrop; controls sit straight
 * on it. Swipe the handle down (or tap it / press Back) to close.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val track = state.track
    val noRipple = remember { MutableInteractionSource() }
    // The player owns its own backdrop (blurred artwork), so its menu can blur *that* — the web's
    // PlayerMenu is a `backdrop-blur-2xl` panel over the cover, not the page behind it.
    val haze = remember { HazeState() }

    BackHandler(enabled = menuOpen) { menuOpen = false }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val artHeight = minOf(maxWidth, maxHeight - 430.dp).coerceAtLeast(260.dp)

        Box(Modifier.fillMaxSize().haze(haze)) {
            // animated backdrop: Apple's three counter-rotating artwork layers (see AppleBackdrop)
            AppleBackdrop(track.thumbs, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))),
                ),
            )

            // cover bleeds from the top edge and fades out (inside the haze node so the player
            // menu, which floats above it, has the artwork to blur — like the web's backdrop-blur)
            Artwork(
                track.thumbs,
                Modifier
                    .fillMaxWidth()
                    .height(artHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.58f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentDescription = "Sampul ${track.title}",
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // handle + source; drag down to close
            var dragTotal by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        val threshold = 120.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal > threshold) actions.onClose()
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f },
                            onVerticalDrag = { _, dy -> dragTotal += dy },
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .height(32.dp)
                        .width(112.dp)
                        .clickable(interactionSource = noRipple, indication = null, onClick = actions.onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.7f)),
                    )
                }
                val from = track.album ?: track.artist
                if (from != null) {
                    Text(
                        "Memutar dari $from",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)),
                        modifier = Modifier.padding(horizontal = 48.dp).padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                // title + more
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            color = Ink.Label1,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.015).em,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.artist != null) {
                            Text(
                                track.artist,
                                color = Ink.Label3,
                                fontSize = 19.sp,
                                lineHeight = 23.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Pressable(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                        description = "Opsi lainnya",
                    ) {
                        Icon(MIcons.Ellipsis, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                // current lyric line
                Text(
                    state.lyricLine,
                    color = Ink.Label2,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable(interactionSource = noRipple, indication = null, onClick = actions.onLyrics),
                )

                // progress
                val dur = state.durationSec
                PillSlider(
                    fraction = if (dur > 0f) state.positionSec / dur else 0f,
                    onChange = { actions.onSeek(it * dur) },
                    modifier = Modifier.padding(top = 6.dp),
                    commitOnRelease = true,
                    enabled = dur > 0f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val small = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink.Label2)
                    Text(fmt(state.positionSec), style = small)
                    Text(if (dur > 0f) "-" + fmt(dur - state.positionSec) else "--:--", style = small)
                }

                // transport: bare glyphs
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pressable(actions.onPrev, Modifier.size(width = 88.dp, height = 72.dp), "Lagu sebelumnya") {
                        Icon(MIcons.Rewind, null, tint = Color.White, modifier = Modifier.size(width = 48.dp, height = 27.dp))
                    }
                    Box(Modifier.size(width = 88.dp, height = 72.dp), contentAlignment = Alignment.Center) {
                        LiquidMetalButton(
                            onClick = actions.onPlayPause,
                            icon = if (state.playing) MIcons.Pause else MIcons.Play,
                            contentDescription = if (state.playing) "Jeda" else "Putar",
                        )
                    }
                    Pressable(actions.onNext, Modifier.size(width = 88.dp, height = 72.dp), "Lagu berikutnya") {
                        Icon(MIcons.FastForward, null, tint = Color.White, modifier = Modifier.size(width = 48.dp, height = 27.dp))
                    }
                }

                // volume
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(MIcons.VolumeMute, null, tint = Ink.Label2, modifier = Modifier.size(18.dp))
                    PillSlider(
                        fraction = state.volume,
                        onChange = actions.onVolume,
                        modifier = Modifier.weight(1f),
                        barHeight = 6.dp,
                        growWhenDragging = false,
                        trackColor = Ink.Track,
                    )
                    Icon(MIcons.Volume, null, tint = Ink.Label2, modifier = Modifier.size(18.dp))
                }

                // tools
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ToolButton(state.lyricsOpen, MIcons.LyricsQuote, "Lirik", actions.onLyrics)
                    ToolButton(state.shuffle, MIcons.Shuffle, "Acak", actions.onShuffle)
                    ToolButton(
                        state.repeat != RepeatMode.Off,
                        if (state.repeat == RepeatMode.One) MIcons.RepeatOne else MIcons.Repeat,
                        "Ulangi",
                        actions.onRepeat,
                    )
                    ToolButton(state.queueOpen, MIcons.List, "Antrian", actions.onQueue)
                }
            }
        }

        // menu: scrim + floating panel that grows from the bottom-right corner
        AnimatedVisibility(visible = menuOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = noRipple, indication = null) { menuOpen = false },
            )
        }
        AnimatedVisibility(
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

private fun fmt(sec: Float): String {
    val s = sec.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Tap target with the web build's `active:scale-90` press feedback. */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { if (description != null) contentDescription = description }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ToolButton(on: Boolean, icon: ImageVector, description: String, onClick: () -> Unit) {
    Pressable(
        onClick,
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (on) Color.White.copy(alpha = 0.20f) else Color.Transparent),
        description,
    ) {
        Icon(icon, contentDescription = description, tint = if (on) Color.White else Ink.Label2, modifier = Modifier.size(24.dp))
    }
}
