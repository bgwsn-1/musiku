package app.musiku.ui.nowplaying

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.round

/** Top and bottom of the Now Playing gradient, ARGB. */
data class NowPlayingGradient(val top: Int, val bottom: Int)

private val GradientFallback = NowPlayingGradient(0xFF2C2C2E.toInt(), 0xFF121214.toInt())

private val gradientCache = LruCache<String, NowPlayingGradient>(64)

/**
 * Convx's Now Playing background (`ui/player/NowPlayingBackground.kt`), default GRADIENT style:
 * one vertical gradient from the artwork's top-edge colour to its bottom-edge colour, the bottom
 * pulled down toward black so the transport and time labels read. No blur, no mesh, no rotating
 * copies — the player sits on a flat field.
 *
 * [pixels] is a small ARGB decode, [w] × [h].
 */
fun nowPlayingGradient(pixels: IntArray, w: Int, h: Int): NowPlayingGradient {
    require(w > 0 && h > 0 && w.toLong() * h <= pixels.size) { "Invalid artwork dimensions" }
    val band = (h * 0.15f).toInt().coerceAtLeast(1)
    val topEdge = averageRows(pixels, w, 0, band)
    val bottomEdge = averageRows(pixels, w, h - band, h)
    // Keep the gradient dark enough for white controls to stay readable on pale covers.
    return NowPlayingGradient(
        shade(topEdge, maxValue = 0.47f, scale = 0.80f),
        shade(bottomEdge, maxValue = 0.28f, scale = 0.50f),
    )
}

private fun averageRows(pixels: IntArray, w: Int, from: Int, to: Int): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    for (y in from until to) for (x in 0 until w) {
        val c = pixels[y * w + x]
        r += (c shr 16) and 0xFF
        g += (c shr 8) and 0xFF
        b += c and 0xFF
    }
    val n = ((to - from) * w).coerceAtLeast(1)
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
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

@Composable
private fun rememberNowPlayingGradient(artworkUrl: String?): NowPlayingGradient? {
    val context = LocalContext.current
    var gradient by remember(artworkUrl) { mutableStateOf(artworkUrl?.let(gradientCache::get)) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            gradient = null
            return@LaunchedEffect
        }
        gradientCache.get(artworkUrl)?.let {
            gradient = it
            return@LaunchedEffect
        }
        // Decode a 48px raster once per track; the two edge averages are all the gradient needs.
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
                nowPlayingGradient(pixels, bitmap.width, bitmap.height)
            }.getOrNull()
        }
        computed?.let { gradientCache.put(artworkUrl, it) }
        gradient = computed
    }
    return gradient
}

/**
 * The squared player's field: flat [top] for the first 60.6% of the screen, then a fall to
 * [bottom]. Colours cross-fade over 800ms on a track change; without artwork it lands on Convx's
 * own fallback pair (#2C2C2E → #121214).
 */
@Composable
fun NowPlayingBackground(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    transitionMillis: Int = 800,
) {
    val gradient = rememberNowPlayingGradient(artworkUrl)
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
    // Colours read in the draw phase: a track change redraws, never recomposes.
    Box(modifier.drawBehind {
        drawRect(Brush.verticalGradient(0f to top, 0.606f to top, 1f to bottom))
    })
}
