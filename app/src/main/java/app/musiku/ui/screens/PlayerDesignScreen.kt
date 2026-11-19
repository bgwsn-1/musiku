package app.musiku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.musiku.model.Track
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.pressable
import app.musiku.ui.nowplaying.NowPlayingBackground
import app.musiku.ui.nowplaying.PlayerArtworkStyle
import app.musiku.ui.nowplaying.PlayerBackgroundStyle
import app.musiku.ui.nowplaying.PlayerCoverSurface
import app.musiku.ui.nowplaying.PlayerLook
import app.musiku.ui.nowplaying.PlayerSlot
import app.musiku.ui.nowplaying.coverStyle
import app.musiku.ui.nowplaying.heroFootFade
import app.musiku.ui.nowplaying.heroOverlapPx
import app.musiku.ui.nowplaying.rememberHeroFadeBrush
import app.musiku.ui.nowplaying.rememberCoverRotation
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * One full preset of the player's look: what a phone in the carousel shows and what "Pakai"
 * writes to the three design preferences at once.
 */
private data class DesignSpec(
    val label: String,
    val look: PlayerLook,
    val artwork: PlayerArtworkStyle,
    val background: PlayerBackgroundStyle,
)

private val Designs = listOf(
    DesignSpec("Kotak", PlayerLook.Squared, PlayerArtworkStyle.Card, PlayerBackgroundStyle.Gradient),
    DesignSpec("Full bleed", PlayerLook.Hero, PlayerArtworkStyle.Card, PlayerBackgroundStyle.Gradient),
    DesignSpec("Vinyl", PlayerLook.Squared, PlayerArtworkStyle.Vinyl, PlayerBackgroundStyle.Gradient),
    DesignSpec("Clover", PlayerLook.Squared, PlayerArtworkStyle.Clover, PlayerBackgroundStyle.Glow),
    DesignSpec("Apple Music", PlayerLook.Hero, PlayerArtworkStyle.Card, PlayerBackgroundStyle.AppleMusic),
)

/** Logical phone the previews lay out at, then scale down — Convx's `ScreenWidth`/`ScreenHeight`. */
private val PhoneWidth = 390.dp
private val PhoneHeight = 844.dp

/**
 * Convx's player-design page: a carousel of phones showing the real player at real size, then
 * the three design axes as choice lists, then the drag-to-reorder layout builder. Every write
 * goes straight to [app.musiku.data.Library], so the Now Playing screen follows along live.
 */
@Composable
fun PlayerDesignScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val look by app.library.playerLook.collectAsState()
    val artwork by app.library.playerArtworkStyle.collectAsState()
    val background by app.library.playerBackgroundStyle.collectAsState()
    val order by app.library.playerLayoutOrder.collectAsState()
    val hidden by app.library.playerLayoutHidden.collectAsState()
    val ui by app.player.ui.collectAsState()
    val position by app.player.position.collectAsState()
    val recent by app.library.recent.collectAsState()

    val track = ui.current ?: recent.firstOrNull()
    val previewPos: () -> Float = {
        if (track != null && ui.current?.videoId == track.videoId) position else 0f
    }

    val current = Designs.firstOrNull {
        it.look == look && it.artwork == artwork && it.background == background
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "title") {
                Text(
                    "Desain pemutar",
                    color = Tok.Label1,
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 4.dp),
                )
            }

            item(key = "carousel") {
                DesignCarousel(
                    selected = current,
                    track = track,
                    positionSec = previewPos,
                    isPlaying = ui.playing,
                    onApply = { spec ->
                        app.library.setPlayerLook(spec.look)
                        app.library.setPlayerArtworkStyle(spec.artwork)
                        app.library.setPlayerBackgroundStyle(spec.background)
                    },
                )
            }

            item(key = "look") {
                SettingsGroup("Tata letak") {
                    PlayerLook.entries.forEachIndexed { i, value ->
                        if (i > 0) SettingsDivider()
                        ChoiceRow(
                            icon = MIcons.Expand,
                            tint = SysBlue,
                            title = value.label,
                            subtitle = if (value == PlayerLook.Hero) {
                                "Sampul menyentuh tepi atas layar, memudar ke latar"
                            } else {
                                "Kartu kotak dengan jarak 32dp dari tepi"
                            },
                            selected = look == value,
                            onClick = { app.library.setPlayerLook(value) },
                        )
                    }
                }
            }

            item(key = "artwork") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Gaya sampul") {
                    PlayerArtworkStyle.entries.forEachIndexed { i, value ->
                        if (i > 0) SettingsDivider()
                        ChoiceRow(
                            icon = MIcons.Disc,
                            tint = SysPink,
                            title = value.label,
                            subtitle = when (value) {
                                PlayerArtworkStyle.Card -> "Sampul kotak bersudut 12dp"
                                PlayerArtworkStyle.Vinyl -> "Piringan hitam yang berputar"
                                PlayerArtworkStyle.Clover -> "Daun empat pasang, gambar tetap tegak"
                            },
                            selected = artwork == value,
                            onClick = { app.library.setPlayerArtworkStyle(value) },
                        )
                    }
                }
            }

            item(key = "background") {
                Spacer(Modifier.height(28.dp))
                SettingsGroup("Latar") {
                    PlayerBackgroundStyle.entries.forEachIndexed { i, value ->
                        if (i > 0) SettingsDivider()
                        ChoiceRow(
                            icon = MIcons.Library,
                            tint = SysTeal,
                            title = value.label,
                            subtitle = when (value) {
                                PlayerBackgroundStyle.Default -> "Hitam polos"
                                PlayerBackgroundStyle.Gradient -> "Gradasi dari warna sampul"
                                PlayerBackgroundStyle.Blur -> "Sampul diburamkan"
                                PlayerBackgroundStyle.Glow -> "Enam blob warna yang bergerak"
                                PlayerBackgroundStyle.AppleMusic -> "Gradasi tepi bawah sampul, seperti Apple Music"
                            },
                            selected = background == value,
                            enabled = look == PlayerLook.Squared || value == PlayerBackgroundStyle.Gradient,
                            onClick = { app.library.setPlayerBackgroundStyle(value) },
                        )
                    }
                    if (look == PlayerLook.Hero) {
                        SettingsDivider()
                        Text(
                            "Full bleed selalu memakai Gradient: sampulnya sendiri yang jadi latar.",
                            color = Tok.Label3,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                        )
                    }
                }
            }

            item(key = "layout") {
                Spacer(Modifier.height(28.dp))
                LayoutBuilder(
                    order = order,
                    hidden = hidden,
                    artworkUrl = track?.thumbs,
                    onReordered = { app.library.setPlayerLayoutOrder(it) },
                    onVisibilityChange = { slot, visible ->
                        app.library.setPlayerLayoutHidden(
                            if (visible) hidden - slot else hidden + slot,
                        )
                    },
                )
                Text(
                    "Tahan dan geser untuk mengubah urutan. Kontrol tidak bisa disembunyikan.",
                    color = Tok.Label3,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                )
            }
        }

        BackButton(onBack)
    }
}

/** A choice row with an iOS checkmark instead of a value label. */
@Composable
private fun ChoiceRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressable(onClick = onClick, pressScale = 0.99f) else Modifier)
            .height(56.dp)
            .padding(horizontal = 14.dp),
    ) {
        SettingsTile(icon, if (enabled) tint else Tok.Label3.copy(alpha = 0.5f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Tok.Label1 else Tok.Label3,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Tok.Label3,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (selected) CheckMark(tint = if (enabled) Tok.Accent else Tok.Label3)
    }
}

@Composable
private fun CheckMark(tint: Color) {
    Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.16f, w * 0.55f)
            lineTo(w * 0.40f, w * 0.78f)
            lineTo(w * 0.86f, w * 0.24f)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.16f,
                cap = StrokeCap.Round,
            ),
        )
    }
}

/**
 * Phones along a gentle arc: the centred one full size, its neighbours smaller, lower and turned
 * away. Tapping a side phone brings it to the centre; tapping the centre one puts it in use.
 */
@Composable
private fun DesignCarousel(
    selected: DesignSpec?,
    track: Track?,
    positionSec: () -> Float,
    isPlaying: Boolean,
    onApply: (DesignSpec) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selected?.let(Designs::indexOf)?.coerceAtLeast(0) ?: 0,
    )
    val scope = rememberCoroutineScope()
    val centred by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) }?.index ?: 0
        }
    }

    LaunchedEffect(selected) {
        selected?.let { listState.animateScrollToItem(Designs.indexOf(it)) }
    }

    // Distances must be observed state, or scrolling would never re-scale the phones.
    val distances by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.associate {
                val size = it.size.coerceAtLeast(1)
                it.index to ((it.offset + size / 2f - mid) / size).coerceIn(-2f, 2f)
            }
        }
    }
    val phoneWidth = 176.dp
    BoxWithConstraints(
        Modifier
            // The side phones bleed past the page gutter instead of clipping at it.
            .layout { measurable, constraints ->
                val bleed = 32.dp.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.maxWidth + bleed,
                        maxWidth = constraints.maxWidth + bleed,
                    ),
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
            }
            .padding(top = 4.dp),
    ) {
        val side = ((maxWidth - phoneWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = side),
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            verticalAlignment = Alignment.Top,
            // The scaled phone is ~366dp tall at this width; the rest is shadow and the arc's
            // downward push, so nothing gets clipped by the row.
            modifier = Modifier.fillMaxWidth().height(440.dp),
        ) {
            itemsIndexed(Designs, key = { _, d -> d.label }) { index, design ->
                val distance = distances[index] ?: 2f
                val depth = abs(distance)
                Box(
                    Modifier
                        .width(phoneWidth)
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            val scale = 1f - (depth * 0.16f).coerceAtMost(0.3f)
                            scaleX = scale
                            scaleY = scale
                            translationY = depth * depth * 22.dp.toPx()
                            rotationZ = distance * 5f
                            alpha = 1f - (depth * 0.25f).coerceAtMost(0.5f)
                        }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = {
                                if (index == centred) onApply(design)
                                else scope.launch { listState.animateScrollToItem(index) }
                            },
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    PhonePreview(
                        spec = design,
                        track = track,
                        positionSec = positionSec,
                        // Only the centred phone animates; six spinning covers would be a waste.
                        isPlaying = isPlaying && index == centred,
                        // 12dp narrower than its slot, so the bezel and the shadow have room.
                        width = phoneWidth - 12.dp,
                    )
                }
            }
        }
    }

    val shown = Designs[centred.coerceIn(0, Designs.lastIndex)]
    val inUse = shown == selected
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown.label,
                color = Tok.Label1,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (inUse) "Dipakai" else "Pakai",
                color = if (inUse) SysGreen else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (inUse) SysGreen.copy(alpha = 0.18f) else Tok.Accent)
                    .then(if (inUse) Modifier else Modifier.pressable(onClick = { onApply(shown) }))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Ketuk ponsel di tengah untuk memakainya",
            color = Tok.Label3,
            fontSize = 12.sp,
        )
    }
}

/**
 * A phone showing the player exactly as [spec] draws it: laid out at 390×844dp and scaled down,
 * so type, spacing and the bleed cover land where the real screen puts them.
 */
@Composable
private fun PhonePreview(
    spec: DesignSpec,
    track: Track?,
    positionSec: () -> Float,
    isPlaying: Boolean,
    width: Dp,
) {
    val bezel = width * 0.035f
    val screenWidth = width - bezel * 2
    val radius = width * 0.13f
    Box(
        Modifier
            .width(width)
            .shadow(18.dp, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFF0B0B0C))
            .padding(bezel),
    ) {
        Box(Modifier.clip(RoundedCornerShape((radius - bezel).coerceAtLeast(4.dp)))) {
            ScaledScreen(screenWidth) {
                PhonePlayer(spec, track, positionSec, isPlaying)
            }
            // Camera cut-out, so the device reads as a phone at a glance.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screenWidth * 0.025f)
                    .size(width = screenWidth * 0.26f, height = screenWidth * 0.07f)
                    .clip(CircleShape)
                    .background(Color.Black),
            )
        }
    }
}

@Composable
private fun ScaledScreen(width: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.layout { measurable, _ ->
            val w = PhoneWidth.roundToPx()
            val h = PhoneHeight.roundToPx()
            val scale = width.toPx() / w
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout((w * scale).roundToInt(), (h * scale).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
        content = content,
    )
}

@Composable
private fun PhonePlayer(
    spec: DesignSpec,
    track: Track?,
    positionSec: () -> Float,
    isPlaying: Boolean,
) {
    val hero = spec.look == PlayerLook.Hero
    // Full bleed means "the art is the field", so it pins the gradient just like the player does.
    val fieldStyle = if (hero) PlayerBackgroundStyle.Gradient else spec.background
    val duration = remember(track) {
        track?.duration?.split(':')?.mapNotNull { it.toIntOrNull() }
            ?.let { if (it.size == 2) it[0] * 60 + it[1] else 0 }
            ?: 0
    }
    val pos = positionSec().toInt().coerceAtLeast(0)
    // The bleed cover is 524/393 of the width; how much of it the title row takes back is
    // `heroOverlapPx`, applied in the layout above.
    val coverHeight = PhoneWidth * 524f / 393f

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NowPlayingBackground(
            artworkUrl = track?.thumbs,
            modifier = Modifier.fillMaxSize(),
            style = fieldStyle,
            hero = hero,
            isPlaying = isPlaying,
        )

        Column(Modifier.fillMaxSize()) {
            if (hero) {
                // Same order as the real player: report the shortened height but paint the full
                // cover, so the fade's mask still covers the part that overlaps the title row.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val overlapPx = heroOverlapPx(constraints.maxWidth)
                            val placeable = measurable.measure(constraints)
                            layout(
                                placeable.width,
                                (placeable.height - overlapPx).coerceAtLeast(0),
                            ) { placeable.place(0, 0) }
                        }
                        .heroFootFade(rememberHeroFadeBrush(wide = false))
                        .height(coverHeight),
                ) {
                    PhoneCover(
                        style = spec.artwork,
                        url = track?.thumbs,
                        isPlaying = isPlaying,
                        cornerRadius = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                MiniHandle(Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
                Spacer(Modifier.weight(1f))
                PhoneCover(
                    style = spec.artwork,
                    url = track?.thumbs,
                    isPlaying = isPlaying,
                    cornerRadius = 12.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).aspectRatio(1f),
                )
                Spacer(Modifier.weight(1f))
            }

            MiniTitle(track)
            Spacer(Modifier.height(14.dp))
            MiniSeek(if (duration > 0) pos.toFloat() / duration else 0.35f)
            Spacer(Modifier.height(6.dp))
            MiniTimes(pos, duration)
            Spacer(Modifier.height(30.dp))
            MiniTransport(isPlaying)
            Spacer(Modifier.height(23.dp))
            MiniVolume()
            Spacer(Modifier.weight(1f))
            MiniTabs()
            Spacer(Modifier.height(31.dp))
        }

        // The window chrome is only in the preview: the real device draws its own status bar, and
        // in the bleed look the handle floats over the art 16dp under it.
        MiniStatusBar()
        if (hero) {
            MiniHandle(Modifier.align(Alignment.TopCenter).padding(top = 54.dp))
        }
    }
}

@Composable
private fun MiniStatusBar() {
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("9:41", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    Modifier.size(width = 15.dp, height = 10.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.9f)),
                )
            }
        }
    }
}

@Composable
private fun MiniHandle(modifier: Modifier) {
    Box(
        modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .size(width = 54.dp, height = 5.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(Color.White.copy(alpha = 0.3f)),
    )
}

@Composable
private fun PhoneCover(
    style: PlayerArtworkStyle,
    url: String?,
    isPlaying: Boolean,
    cornerRadius: Dp,
    modifier: Modifier,
) {
    val cover = style.coverStyle()
    val rotation by rememberCoverRotation(cover, isPlaying)
    PlayerCoverSurface(
        style = cover,
        rotation = rotation,
        cornerRadius = cornerRadius,
        modifier = modifier,
    ) {
        Artwork(url, Modifier.fillMaxSize())
    }
}

@Composable
private fun MiniTitle(track: Track?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track?.title ?: "Belum ada lagu",
                color = Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artist ?: "Putar lagu untuk melihat preview",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(MIcons.HeartOutline, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Icon(MIcons.Ellipsis, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MiniSeek(fraction: Float) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(Color.White.copy(alpha = 0.24f)),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White.copy(alpha = 0.9f)))
    }
}

@Composable
private fun MiniTimes(posSec: Int, durationSec: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(miniTime(posSec), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(
            "-" + miniTime((durationSec - posSec).coerceAtLeast(0)),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
    }
}

private fun miniTime(seconds: Int): String =
    "%d:%02d".format(seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

@Composable
private fun MiniTransport(isPlaying: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(MIcons.Shuffle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Icon(MIcons.Prev, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) MIcons.Pause else MIcons.Play,
                null,
                tint = Color.Black,
                modifier = Modifier.size(30.dp),
            )
        }
        Icon(MIcons.Next, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Icon(MIcons.Repeat, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MiniVolume() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MIcons.VolumeMute, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
        Box(
            Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(Color.White.copy(alpha = 0.8f)))
        }
        Icon(MIcons.Volume, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun MiniTabs() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(MIcons.Lyrics, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        Icon(MIcons.Queue, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        Icon(MIcons.Share, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
    }
}

/**
 * Convx's `PlayerLayoutList`: a drag-to-reorder list where each row is a miniature of the real
 * band it stands for, so the builder shows the shape instead of naming it.
 */
@Composable
private fun LayoutBuilder(
    order: List<PlayerSlot>,
    hidden: Set<PlayerSlot>,
    artworkUrl: String?,
    onReordered: (List<PlayerSlot>) -> Unit,
    onVisibilityChange: (PlayerSlot, Boolean) -> Unit,
) {
    val slots = remember(order) { order.toMutableStateList() }
    val listState = rememberLazyListState()
    var hasDragged by remember { mutableStateOf(false) }
    val reorderableState: ReorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        slots.add(to.index, slots.removeAt(from.index))
        hasDragged = true
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasDragged) {
            onReordered(slots.toList())
            hasDragged = false
        }
    }

    SettingsGroup("Susunan pemutar") {
        // A LazyColumn nested in a LazyColumn item has no intrinsic height, so the card is given
        // one exact: 84dp per row plus the 10dp inner padding top and bottom.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height((slots.size * 84 + 20).dp),
        ) {
                itemsIndexed(slots, key = { _, slot -> slot.name }) { _, slot ->
                    ReorderableItem(state = reorderableState, key = slot.name) {
                        val isHidden = slot in hidden
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 76.dp)
                                .alpha(if (isHidden) 0.42f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Tok.Fill1)
                                .draggableHandle(onDragStopped = {})
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(Modifier.padding(end = 46.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        slot.label,
                                        color = Tok.Label3,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        MIcons.List,
                                        contentDescription = "Geser untuk mengubah urutan",
                                        tint = Tok.Label3.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                SlotPreview(slot, artworkUrl)
                            }
                            if (slot.hideable) {
                                Switch(
                                    checked = !isHidden,
                                    onCheckedChange = { visible -> onVisibilityChange(slot, visible) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = SysGreen,
                                        checkedBorderColor = Color.Transparent,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                                        uncheckedBorderColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.align(Alignment.TopEnd).scale(0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
}

/** One band's actual shape at reduced scale, standing in for the real Now Playing section. */
@Composable
private fun SlotPreview(slot: PlayerSlot, artworkUrl: String?) {
    when (slot) {
        PlayerSlot.AlbumArt -> Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
        ) { Artwork(artworkUrl, Modifier.fillMaxSize()) }

        PlayerSlot.TrackInfo -> Column {
            PreviewBar(0.55f, 8.dp, Tok.Label1.copy(alpha = 0.85f))
            Spacer(Modifier.height(5.dp))
            PreviewBar(0.35f, 6.dp, Tok.Label1.copy(alpha = 0.45f))
        }

        PlayerSlot.SeekBar -> Box(contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                    .background(Tok.Label1.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(0.35f).background(Tok.Label1.copy(alpha = 0.85f)),
                )
            }
        }

        PlayerSlot.Controls -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewDot(12.dp, 0.5f)
            Spacer(Modifier.width(14.dp))
            PreviewDot(24.dp, 1f)
            Spacer(Modifier.width(14.dp))
            PreviewDot(12.dp, 0.5f)
        }

        PlayerSlot.ActionRow -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { PreviewDot(14.dp, 0.35f) }
        }
    }
}

@Composable
private fun PreviewBar(widthFraction: Float, height: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color),
    )
}

@Composable
private fun PreviewDot(size: Dp, alpha: Float) {
    Box(Modifier.size(size).clip(CircleShape).background(Tok.Label1.copy(alpha = alpha)))
}
