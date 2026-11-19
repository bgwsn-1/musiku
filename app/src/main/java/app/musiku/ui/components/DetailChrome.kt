package app.musiku.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.SfDetailIcons

/** Convx `ArtistTextTiers` (white, then plain alpha steps). */
val ArtistTier1 = Color.White
val ArtistTier2 = Color.White.copy(alpha = 0.6f)
val ArtistTier3 = Color.White.copy(alpha = 0.35f)

@Composable
fun SfIcon(icon: ImageVector, size: Dp, tint: Color, description: String? = null) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}

/**
 * Convx `chromeShadow`: 24 dp soft drop, 4 dp down, black at 18%.
 * Compose 1.7 (pinned for Haze 1.1.0) predates `Modifier.dropShadow`, so the
 * same three values are drawn as a blurred, offset circle behind the button.
 */
@Composable
private fun ChromeShadow() {
    Box {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
                .blur(24.dp),
        )
    }
}

/** Convx `ContrastCircleButton` / `ArtistControlCircle` fill and floating chrome. */
@Composable
fun ContrastCircle(
    ink: Color,
    size: Dp = 44.dp,
    fillAlpha: Float = 0.16f,
    onClick: () -> Unit,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .pressable(onClick = onClick, description = description),
    ) {
        ChromeShadow()
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(ink.copy(alpha = fillAlpha)),
        )
        content()
    }
}

/** Convx `CollectionDetailHeader` action row: 54 shuffle / 140×50 pill / 54 right. */
@Composable
fun CollectionActions(
    ink: Color,
    playing: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    rightIcon: ImageVector? = null,
    onRightAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.padding(top = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ContrastCircle(ink, 54.dp, onClick = onShuffle, description = "Acak") {
            SfIcon(SfDetailIcons.Shuffle, 26.dp, ink)
        }
        val inverse = if (ink == Color.White) Color.Black else Color.White
        val pill = Modifier
            .width(140.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(ink.copy(alpha = if (enabled) .82f else .4f))
        Row(
            modifier = if (enabled) pill.pressable(onClick = onPlay, description = if (playing) "Jeda" else "Putar") else pill,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SfIcon(if (playing) SfDetailIcons.PauseFill else SfDetailIcons.PlayFill, 19.dp, inverse)
            Spacer(Modifier.width(7.dp))
            Text(
                if (playing) "Jeda" else "Putar",
                fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = inverse,
            )
        }
        if (rightIcon != null && onRightAction != null) {
            ContrastCircle(ink, 54.dp, onClick = onRightAction, description = "Opsi lain") {
                SfIcon(rightIcon, 26.dp, ink)
            }
        }
    }
}

/** Convx `CollectionDetailHeader`: min(maxWidth*.68, 300) art, 24/8/7/17 rhythm. */
@Composable
fun ConvxCollectionHeader(
    title: String,
    artwork: String?,
    metadata: String,
    foreground: Color,
    playing: Boolean,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    rightIcon: ImageVector? = null,
    onRightAction: (() -> Unit)? = null,
    zoom: Float = 1f,
    onSearch: (() -> Unit)? = null,
    searchValue: TextFieldValue? = null,
    onSearchValueChange: ((TextFieldValue) -> Unit)? = null,
    onSearchImeAction: (() -> Unit)? = null,
    searchFocusRequester: FocusRequester? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val artworkSize = minOf(maxWidth * .68f, 300.dp)
        var failed by remember(artwork) { mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onSearch != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(foreground.copy(alpha = .12f))
                        .clickable {
                            if (searchValue == null) onSearch() else searchFocusRequester?.requestFocus()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfIcon(SfDetailIcons.Magnifyingglass, 18.dp, foreground.copy(alpha = .48f))
                    Spacer(Modifier.width(8.dp))
                    if (searchValue == null || onSearchValueChange == null) {
                        Text("Cari", fontSize = 17.sp, lineHeight = 22.sp, color = foreground.copy(alpha = .48f))
                    } else {
                        BasicTextField(
                            value = searchValue,
                            onValueChange = onSearchValueChange,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 17.sp, lineHeight = 22.sp, color = foreground,
                            ),
                            cursorBrush = SolidColor(foreground),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchImeAction?.invoke() }),
                            modifier = Modifier.weight(1f).then(
                                searchFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchValue.text.isEmpty()) {
                                        Text("Cari", fontSize = 17.sp, lineHeight = 22.sp, color = foreground.copy(alpha = .48f))
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }
            Box(
                Modifier
                    .size(artworkSize)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom }
                    .shadow(
                        18.dp, RoundedCornerShape(12.dp), clip = false,
                        ambientColor = Color.Black.copy(alpha = .18f),
                        spotColor = Color.Black.copy(alpha = .18f),
                    ),
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(foreground.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artwork.isNullOrBlank() || failed) {
                        SfIcon(SfDetailIcons.MusicNoteList, 72.dp, foreground.copy(alpha = .5f))
                    } else {
                        Artwork(artwork, Modifier.fillMaxSize(), onError = { failed = true })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold,
                color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle, fontSize = 20.sp, lineHeight = 24.sp, color = foreground,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            if (metadata.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    metadata, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium,
                    color = foreground.copy(alpha = .48f), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            CollectionActions(foreground, playing, enabled, onPlay, onShuffle, rightIcon, onRightAction)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    description, fontSize = 14.sp, lineHeight = 19.sp,
                    color = foreground.copy(alpha = .48f), maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/** Convx `ArtistSectionHeader`: 22/28 bold -0.26, gutter 20, top 24 / bottom 16. */
@Composable
fun ConvxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(
                start = 20.dp, end = 20.dp,
                top = if (compact) 8.dp else 24.dp,
                bottom = if (compact) 14.dp else 16.dp,
            ),
    ) {
        Text(
            title, color = Color.White,
            fontSize = if (compact) 20.sp else 22.sp,
            lineHeight = if (compact) 25.sp else 28.sp,
            letterSpacing = if (compact) (-0.45).sp else (-0.26).sp,
            fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            SfIcon(SfDetailIcons.ChevronRight, 16.dp, Color.White.copy(alpha = 0.6f))
        }
    }
}

/**
 * Convx flat `ListItem`: 58 dp row, 48 dp art (5 dp corners, 5 dp top/bottom, 4 dp after
 * art), 20 dp gutter, 15/20 semibold title, 13/18 metadata, 0.75 dp rule inset to the text.
 */
@Composable
fun ConvxSongRow(
    title: String,
    subtitle: String?,
    artwork: String?,
    ink: Color = Color.White,
    isCurrent: Boolean = false,
    leadingDivider: Boolean = false,
    divider: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        if (leadingDivider) {
            Box(Modifier.padding(horizontal = 20.dp).height(0.75.dp).background(ink.copy(alpha = 0.24f)))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(if (isCurrent) ink.copy(alpha = 0.15f) else Color.Transparent)
                .pressable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                Modifier
                    .padding(start = 20.dp, top = 5.dp, bottom = 5.dp, end = 4.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(ink.copy(alpha = 0.12f)),
            ) {
                if (artwork.isNullOrBlank()) SfIcon(SfDetailIcons.MusicNoteList, 24.dp, ink.copy(alpha = .6f))
                else Artwork(artwork, Modifier.fillMaxSize())
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.23).sp, color = ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle, fontSize = 13.sp, lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
            } else {
                Spacer(Modifier.width(20.dp))
            }
        }
        if (divider) {
            Box(
                Modifier
                    .padding(start = 20.dp + 48.dp + 12.dp, end = 20.dp)
                    .height(0.75.dp)
                    .background(ink.copy(alpha = 0.24f)),
            )
        }
    }
}

/** Convx grid tile: square art, 15 pt normal title, 15/20 metadata subtitle. */
@Composable
fun ConvxGridCard(
    title: String,
    subtitle: String?,
    artwork: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(132.dp).pressable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            if (artwork.isNullOrBlank()) SfIcon(SfDetailIcons.MusicNoteList, 32.dp, Color.White.copy(alpha = .5f))
            else Artwork(artwork, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title, fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.23).sp,
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle, fontSize = 15.sp, lineHeight = 20.sp,
                color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ConvxGridRail(items: List<Triple<String, String?, String?>>, onOpen: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items) { i, (title, sub, art) ->
            ConvxGridCard(title, sub, art, onClick = { onOpen(i) })
        }
    }
}

/** Artist wordmark slot: 75 dp tall, max 286 dp wide, name bottom-aligned. */
@Composable
fun ArtistWordmark(name: String, ink: Color = Color.White) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxWidth().height(ArtistWordmarkHeight).padding(horizontal = 20.dp),
    ) {
        Text(
            name, color = ink, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = ArtistWordmarkMaxWidth),
        )
    }
}

/** Convx `ArtistActionRow`: 50 info / 73 play / 50 save, 24 dp apart. */
@Composable
fun ArtistActionRow(
    isPlaying: Boolean,
    pageBgRgb: Int,
    ink: Color = Color.White,
    onInfo: () -> Unit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    saved: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ContrastCircle(ink, 50.dp, ArtistControlFillAlpha, onInfo, "Tentang") {
            SfIcon(SfDetailIcons.Info, 22.dp, ink)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(73.dp).clip(CircleShape).background(ink).pressable(onClick = onPlay, description = "Putar"),
        ) {
            SfIcon(
                if (isPlaying) SfDetailIcons.PauseFill else SfDetailIcons.PlayFill,
                36.dp, artistPlayGlyph(pageBgRgb),
            )
        }
        ContrastCircle(ink, 50.dp, ArtistControlFillAlpha, onSave, "Simpan") {
            SfIcon(if (saved) SfDetailIcons.StarFill else SfDetailIcons.Star, 24.dp, ink)
        }
    }
}

/** Convx `FeaturedReleaseCard`: 114 tall, 22 corners, 90 art/10 corners, 12 padding. */
@Composable
fun FeaturedReleaseCard(
    caption: String,
    title: String,
    subtitle: String?,
    artwork: String?,
    pageBgRgb: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(114.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = artistCardFillAlpha(pageBgRgb)))
            .pressable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier.size(90.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)),
        ) {
            if (artwork.isNullOrBlank()) SfIcon(SfDetailIcons.MusicNoteList, 28.dp, Color.White.copy(alpha = .5f))
            else Artwork(artwork, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(caption, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Color.White, fontSize = 17.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, lineHeight = 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Hero/wordmark constants from Convx `ArtistHero.kt` and `ArtistScreen.kt`. */
const val ArtistHeroExtent = 495f / 393f
const val ArtistArtworkExtent = 405f / 393f
val ArtistWordmarkHeight = 75.dp
val ArtistWordmarkMaxWidth = 286.dp
val ArtistWordmarkFootInset = 4.dp

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun artistWash(fraction: Float): Float = smoothstep(307f / 393f, 1.21f, fraction)

/** 32-step eased foot wash, exactly Convx `artistWashStops`. */
private fun artistWashStops(pageColor: Color): Array<Pair<Float, Color>> {
    val steps = 32
    return Array(steps + 1) { i ->
        val position = i / steps.toFloat()
        position to pageColor.copy(alpha = artistWash(position * ArtistHeroExtent))
    }
}

/**
 * Convx square artist hero: art is `side * 405/393` tall in a `side * 495/393` box, the tail is
 * a blurred mirror, then the exact 32-step page-colour wash. Convx's API 33+ AGSL live blur is
 * replaced by this blurred tail; the geometry and wash ramp are the source values.
 */
@Composable
fun ArtistHeroImage(
    artworkUrl: String?,
    pageColor: Color,
    modifier: Modifier = Modifier,
    heroScale: () -> Float = { 1f },
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val side = maxWidth
        Box(
            Modifier
                .width(side)
                .height(side * ArtistHeroExtent)
                .graphicsLayer { scaleX = heroScale(); scaleY = heroScale() },
        ) {
            val foot = rememberArtistFoot(artworkUrl)
            if (foot != null) {
                Image(
                    bitmap = foot,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.fillMaxWidth().height(side * ArtistArtworkExtent).background(Color(0xFF3A3A3C))) {
                    if (!artworkUrl.isNullOrBlank()) Artwork(artworkUrl, Modifier.fillMaxSize())
                    else SfIcon(MIcons.Music, side * 0.18f, Color.White.copy(alpha = 0.3f))
                }
                if (!artworkUrl.isNullOrBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(side * (ArtistHeroExtent - ArtistArtworkExtent))
                            .align(Alignment.BottomCenter)
                            .blur(24.dp)
                            .alpha(0.7f),
                    ) {
                        Artwork(artworkUrl, Modifier.fillMaxSize())
                    }
                }
            }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(colorStops = artistWashStops(pageColor))))
        }
    }
}
