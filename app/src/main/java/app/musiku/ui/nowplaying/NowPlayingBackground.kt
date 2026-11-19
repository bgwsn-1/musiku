package app.musiku.ui.nowplaying

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.musiku.ui.components.rememberArtistFoot
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/** Top and bottom of the Now Playing gradient, ARGB. */
data class NowPlayingGradient(val top: Int, val bottom: Int)

private val GradientFallback = NowPlayingGradient(0xFF2C2C2E.toInt(), 0xFF121214.toInt())

/** Everything one 48px decode of a cover yields: both gradients and a six-cell palette. */
private class ArtworkSample(
    val gradient: NowPlayingGradient,
    val heroGradient: NowPlayingGradient,
    val palette: List<Int>,
)

private val sampleCache = LruCache<String, ArtworkSample>(32)

/**
 * Convx's Now Playing background (`ui/player/NowPlayingBackground.kt`).
 *
 * The GRADIENT field is one vertical gradient drawn from the artwork's edges: the squared look
 * takes its top and bottom rows, the [hero] (full-bleed) look takes both from the bottom row, so
 * the colour under a cover that now reaches the top of the screen no longer changes abruptly.
 *
 * [pixels] is a small ARGB decode, [w] × [h].
 */
fun nowPlayingGradient(pixels: IntArray, w: Int, h: Int, hero: Boolean = false): NowPlayingGradient {
    require(w > 0 && h > 0 && w.toLong() * h <= pixels.size) { "Invalid artwork dimensions" }
    val band = (h * 0.15f).toInt().coerceAtLeast(1)
    val topEdge = averageRows(pixels, w, 0, band, 0, w)
    val bottomEdge = averageRows(pixels, w, h - band, h, 0, w)
    // Keep the gradient dark enough for white controls to stay readable on pale covers.
    return if (hero) {
        NowPlayingGradient(
            shade(bottomEdge, maxValue = 0.47f, scale = 0.85f),
            shade(bottomEdge, maxValue = 0.28f, scale = 0.45f),
        )
    } else {
        NowPlayingGradient(
            shade(topEdge, maxValue = 0.47f, scale = 0.80f),
            shade(bottomEdge, maxValue = 0.28f, scale = 0.50f),
        )
    }
}

private fun averageRows(pixels: IntArray, w: Int, from: Int, to: Int, x0: Int, x1: Int): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (y in from until to) for (x in x0 until x1) {
        val c = pixels[y * w + x]
        r += (c shr 16) and 0xFF
        g += (c shr 8) and 0xFF
        b += c and 0xFF
        n++
    }
    if (n == 0) return 0xFF000000.toInt()
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

/** Six cell averages (3 across, 2 down) — the GLOW field's palette, without pulling in androidx.palette. */
private fun paletteOf(pixels: IntArray, w: Int, h: Int): List<Int> {
    val out = ArrayList<Int>(6)
    for (row in 0 until 2) for (col in 0 until 3) {
        out += averageRows(
            pixels, w,
            from = h * row / 2,
            to = min(h, (h * (row + 1) / 2).coerceAtLeast(h * row / 2 + 1)),
            x0 = w * col / 3,
            x1 = min(w, (w * (col + 1) / 3).coerceAtLeast(w * col / 3 + 1)),
        )
    }
    return out
}

/** Value scaled by [scale] and capped at [maxValue]; saturation nudged up so dark ends stay coloured. */
private fun shade(argb: Int, maxValue: Float, scale: Float): Int {
    val channels = intArrayOf((argb shr 16) and 255, (argb shr 8) and 255, argb and 255)
    val high = channels.max().toFloat()
    val low = channels.min().toFloat()
    val saturation = if (high == 0f) 0f else ((high - low) / high * 1.15f).coerceAtMost(1f)
    val value = (high * scale).coerceAtMost(maxValue * 255f)
    fun channel(index: Int): Int {
        val hueComponent = if (high == low) 0f else (channels[index] - low) / (high - low)
        return round(value * (1f - saturation + saturation * hueComponent)).toInt().coerceIn(0, 255)
    }
    var red = channel(0)
    var green = channel(1)
    var blue = channel(2)
    fun linear(c: Int): Double {
        val normalized = c / 255.0
        return if (normalized <= 0.04045) normalized / 12.92
        else ((normalized + 0.055) / 1.055).pow(2.4)
    }
    // Preserve saturated reference colours; pale neutral artwork needs a slightly lower cap.
    while (0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue) > 0.183) {
        red = (red * 0.98f).toInt()
        green = (green * 0.98f).toInt()
        blue = (blue * 0.98f).toInt()
    }
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

/** Pushes a cell toward a saturated mid-tone so a glow blob reads as light rather than as a stain. */
private fun vivid(argb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsv)
    hsv[1] = (hsv[1] * 1.45f).coerceIn(0.35f, 1f)
    hsv[2] = (hsv[2] * 1.6f).coerceIn(0.55f, 0.92f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun rememberArtworkSample(artworkUrl: String?): ArtworkSample? {
    val context = LocalContext.current
    var sample by remember(artworkUrl) { mutableStateOf(artworkUrl?.let(sampleCache::get)) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            sample = null
            return@LaunchedEffect
        }
        sampleCache.get(artworkUrl)?.let {
            sample = it
            return@LaunchedEffect
        }
        // Decode a 48px raster once per track; the edge averages and the palette are all it needs.
        val computed = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(48)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .build()
                val decoded = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching null
                val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded
                else decoded.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                ArtworkSample(
                    gradient = nowPlayingGradient(pixels, bitmap.width, bitmap.height),
                    heroGradient = nowPlayingGradient(pixels, bitmap.width, bitmap.height, hero = true),
                    palette = paletteOf(pixels, bitmap.width, bitmap.height),
                )
            }.getOrNull()
        }
        computed?.let { sampleCache.put(artworkUrl, it) }
        sample = computed
    }
    return sample
}

/**
 * The player's field. GRADIENT keeps the squared look's flat top for the first 60.6% of the screen
 * (62% when [hero]) then falls to [bottom]; BLUR bakes a progressive-blur tail of the cover on the
 * CPU, so the style works from minSdk 24 without AGSL; GLOW drifts six coloured blobs.
 */
@Composable
fun NowPlayingBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    style: PlayerBackgroundStyle = PlayerBackgroundStyle.Gradient,
    hero: Boolean = false,
    isPlaying: Boolean = false,
    transitionMillis: Int = 800,
) {
    // Above the fade knee the field is flat, so a hero cover needs no second colour at the top.
    val useHeroGradient = hero || style == PlayerBackgroundStyle.AppleMusic

    when {
        style == PlayerBackgroundStyle.Default -> Box(modifier.background(Color.Black))

        style == PlayerBackgroundStyle.Blur && artworkUrl != null -> {
            // Convx blurs live at 150dp (API 31+). The baked tail is the same look on every device.
            val foot = rememberArtistFoot(artworkUrl)
            Box(modifier.background(Color.Black)) {
                if (foot != null) {
                    Image(
                        bitmap = foot,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
        }

        style == PlayerBackgroundStyle.Glow -> {
            val sample = rememberArtworkSample(artworkUrl)
            val palette = sample?.palette?.map(::vivid).orEmpty()
            val progress = rememberGlowProgress(isPlaying)
            Box(
                modifier.drawBehind {
                    drawRect(Color(0xFF050505))
                    if (palette.isEmpty()) return@drawBehind
                    val p = progress()
                    fun colorAt(i: Int): Color {
                        val idx = i + p * palette.size
                        val a = floor(idx).toInt() % palette.size
                        return lerp(palette[a], palette[(a + 1) % palette.size], idx - floor(idx))
                    }
                    fun osc(min: Float, max: Float, phase: Float): Float =
                        min + (max - min) * ((sin(2f * Math.PI.toFloat() * (p + phase)) + 1f) / 2f)
                    // Convx's six blobs: same centres, radii and strengths as GLOW_ANIMATED.
                    val blobs = listOf(
                        floatArrayOf(osc(0f, 1f, 0f), osc(0f, .5f, .07f), osc(.8f, 1.6f, .12f), .85f),
                        floatArrayOf(osc(1f, 0f, .2f), osc(.5f, 1f, .25f), osc(.7f, 1.5f, .18f), .8f),
                        floatArrayOf(osc(.2f, .8f, .33f), osc(.8f, .2f, .36f), osc(.6f, 1.4f, .29f), .75f),
                        floatArrayOf(osc(.3f, .7f, .44f), osc(.2f, .8f, .41f), osc(.9f, 1.7f, .47f), .7f),
                        floatArrayOf(osc(.4f, .6f, .55f), osc(0f, 1f, .51f), osc(.7f, 1.5f, .58f), .65f),
                        floatArrayOf(osc(0f, 1f, .66f), osc(.5f, .7f, .62f), osc(.8f, 1.8f, .69f), .6f),
                    )
                    blobs.forEachIndexed { i, blob ->
                        val c = colorAt(i % palette.size)
                        drawRect(
                            Brush.radialGradient(
                                listOf(
                                    c.copy(alpha = blob[3]),
                                    c.copy(alpha = blob[3] * 0.6f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * blob[0], size.height * blob[1]),
                                radius = size.width * blob[2],
                            ),
                        )
                    }
                },
            )
        }

        else -> {
            // Gradient, Apple Music, and Blur before the artwork has decoded.
            val sample = rememberArtworkSample(artworkUrl)
            val gradient = if (useHeroGradient) sample?.heroGradient else sample?.gradient
            val top by animateColorAsState(
                Color(gradient?.top ?: GradientFallback.top),
                tween(transitionMillis),
                label = "nowPlayingTop",
            )
            val bottom by animateColorAsState(
                Color(gradient?.bottom ?: GradientFallback.bottom),
                tween(transitionMillis),
                label = "nowPlayingBottom",
            )
            val knee = if (useHeroGradient) 0.62f else 0.606f
            // Colours read in the draw phase: a track change redraws, never recomposes.
            Box(modifier.drawBehind { drawRect(Brush.verticalGradient(0f to top, knee to top, 1f to bottom)) })
        }
    }
}

/** One 20s turn shared by every glow read; a still player freezes at 0. */
@Composable
private fun rememberGlowProgress(isPlaying: Boolean): () -> Float {
    if (!isPlaying) {
        val zero = remember { mutableFloatStateOf(0f) }
        return { zero.floatValue }
    }
    val transition = rememberInfiniteTransition(label = "glowField")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "glowProgress",
    )
    return { progress }
}
