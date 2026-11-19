package app.musiku.ui.components

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Convx `ArtworkColors` subset: colours are packed 0xRRGGBB ints. */
private object ArtworkColors {
    const val WHITE = 0xFFFFFF
    private const val DARKEN_PER_LUMINANCE = 165.9
    private const val DARKEN_THRESHOLD = 0.1

    fun rgb(r: Int, g: Int, b: Int) =
        (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    private fun linear(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Int): Double =
        0.2126 * linear((c shr 16) and 0xFF) +
            0.7152 * linear((c shr 8) and 0xFF) +
            0.0722 * linear(c and 0xFF)

    fun pageBackground(artworkBg: Int): Int {
        val y = luminance(artworkBg)
        if (y <= DARKEN_THRESHOLD) return artworkBg and 0xFFFFFF
        val d = DARKEN_PER_LUMINANCE * y
        return rgb(
            (((artworkBg shr 16) and 0xFF) - d).roundToInt(),
            (((artworkBg shr 8) and 0xFF) - d).roundToInt(),
            ((artworkBg and 0xFF) - d).roundToInt(),
        )
    }

    fun mix(from: Int, to: Int, t: Double) = rgb(
        (((from shr 16) and 0xFF) + (((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * t).roundToInt(),
        (((from shr 8) and 0xFF) + (((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * t).roundToInt(),
        ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t).roundToInt(),
    )

    /** White overlay alpha of translucent page controls; the featured card rises with luminance. */
    fun cardFillAlpha(pageBg: Int): Float =
        (0.090 + 0.25 * luminance(pageBg)).toFloat().coerceIn(0.08f, 0.14f)

    /** Edge-weighted 32-level bucket vote over the top quarter, black bucket skipped. */
    fun estimateArtworkBackground(pixels: IntArray, width: Int, height: Int): Int {
        val levels = 32
        val shift = 3
        val buckets = levels * levels * levels
        val mass = DoubleArray(buckets)
        val sr = DoubleArray(buckets)
        val sg = DoubleArray(buckets)
        val sb = DoubleArray(buckets)
        var total = 0.0
        var tr = 0.0
        var tg = 0.0
        var tb = 0.0
        val rows = max(1, height / 4)
        val hw = width / 2.0
        val hh = height / 2.0
        for (y in 0 until rows) {
            val ny = (y - hh) / hh
            val row = y * width
            for (x in 0 until width) {
                val c = pixels[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val nx = (x - hw) / hw
                val w = 1.0 + nx * nx + ny * ny
                total += w; tr += w * r; tg += w * g; tb += w * b
                val k = ((r shr shift) * levels + (g shr shift)) * levels + (b shr shift)
                if (k == 0) continue
                mass[k] += w; sr[k] += w * r; sg[k] += w * g; sb[k] += w * b
            }
        }
        var best = -1
        for (k in 1 until buckets) if (best < 0 || mass[k] > mass[best]) best = k
        if (best < 0 || mass[best] <= 0.0) {
            val m = max(1.0, total)
            return rgb((tr / m).roundToInt(), (tg / m).roundToInt(), (tb / m).roundToInt())
        }
        val m = mass[best]
        return rgb((sr[best] / m).roundToInt(), (sg[best] / m).roundToInt(), (sb[best] / m).roundToInt())
    }
}

/** Convx 0xRRGGBB → Compose Color. */
private fun artColor(rgb: Int) = Color(rgb or 0xFF000000.toInt())

/** Page tint Convx derives from an artwork's measured background. */
fun artistPageColor(artworkBg: Int): Color = artColor(ArtworkColors.pageBackground(artworkBg))
fun artistPlayFill(): Color = artColor(ArtworkColors.WHITE)
fun artistPlayGlyph(pageBg: Int): Color = artColor(ArtworkColors.mix(pageBg, 0x000000, 0.3))
fun artistCardFillAlpha(pageBg: Int): Float = ArtworkColors.cardFillAlpha(pageBg)
const val ArtistControlFillAlpha = 0.08f

/** Loads one small software bitmap for palette work; Convx asks for 160×160. */
private suspend fun loadArtPixels(context: android.content.Context, url: String?): IntArray? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(160, 160)
        .scale(Scale.FILL)
        .precision(Precision.INEXACT)
        .allowHardware(false)
        .build()
    val bmp = runCatching { context.imageLoader.execute(request) }.getOrNull()
        ?.drawable?.let { it as? BitmapDrawable }?.bitmap ?: return null
    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 160, 160, true)
    return try {
        val pixels = IntArray(160 * 160)
        scaled.getPixels(pixels, 0, 160, 0, 0, 160, 160)
        pixels
    } finally {
        if (scaled !== bmp) scaled.recycle()
    }
}

/** Estimated Apple-style background colour of the artwork, for the artist wash. */
@Composable
fun rememberArtistArtworkBg(url: String?): Int {
    val context = LocalContext.current
    var rgb by remember(url) { mutableStateOf(0x1C1C1E) }
    LaunchedEffect(url, context) {
        val pixels = withContext(Dispatchers.Default) { loadArtPixels(context, url) }
        if (pixels != null) rgb = ArtworkColors.estimateArtworkBackground(pixels, 160, 160)
    }
    return rgb
}

/** Convx's cached, baked progressive-blur tail for the square artist hero. */
private val artistFootCache = LruCache<String, ImageBitmap>(6)

private const val ArtistFootPx = 320
private const val ArtistBlurKnee = 0.78f
private val ArtistFootLevelsPt = floatArrayOf(0f, 2f, 4f, 8f, 16f, 32f, 48f)

/** Convx `resize(1200, 1200)` for YouTube/Google artwork URLs. */
private fun resizeArtwork(url: String, width: Int): String {
    val target = width.coerceIn(544, 1200)
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        val re = Regex("=\\w[\\w-]*!?$")
        return if (re.containsMatchIn(url)) url.replace(re, "=w$target-h$target-l90-rj")
        else "$url=w$target-h$target-l90-rj"
    }
    if (url.contains("ytimg.com")) {
        if (url.contains("/hqdefault.jpg")) return url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
        return url.replace(Regex("/(mq|sd[a-z]*)default\\.jpg\\b"), "/hqdefault.jpg")
    }
    return url
}

/** Exact port of Convx `stackBlurPixels`: one horizontal pass, two vertical passes. */
private fun stackBlurPixels(pixels: IntArray, w: Int, h: Int, radius: Int) {
    if (radius < 1 || w <= 0 || h <= 0) return
    require(pixels.size >= w * h) { "pixels too small: ${pixels.size} < ${w * h}" }

    val div = 2 * radius + 1
    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)
    val dv = IntArray(256 * div)
    for (i in dv.indices) dv[i] = i / div

    for (y in 0 until h) {
        val rowStart = y * w
        var ar = 0
        var ag = 0
        var ab = 0
        for (i in -radius..radius) {
            val p = pixels[rowStart + i.coerceIn(0, w - 1)]
            ar += (p and 0x00ff0000) shr 16
            ag += (p and 0x0000ff00) shr 8
            ab += p and 0x000000ff
        }
        for (x in 0 until w) {
            val out = rowStart + x
            r[out] = dv[ar]
            g[out] = dv[ag]
            b[out] = dv[ab]
            val p1 = pixels[rowStart + (x - radius).coerceIn(0, w - 1)]
            val p2 = pixels[rowStart + (x + radius + 1).coerceIn(0, w - 1)]
            ar += ((p2 and 0x00ff0000) - (p1 and 0x00ff0000)) shr 16
            ag += ((p2 and 0x0000ff00) - (p1 and 0x0000ff00)) shr 8
            ab += (p2 and 0x000000ff) - (p1 and 0x000000ff)
        }
    }

    repeat(2) {
        for (x in 0 until w) {
            var ar = 0
            var ag = 0
            var ab = 0
            for (i in -radius..radius) {
                val idx = i.coerceIn(0, h - 1) * w + x
                ar += r[idx]; ag += g[idx]; ab += b[idx]
            }
            var yp = x
            for (y in 0 until h) {
                pixels[yp] = pixels[yp] and 0xff000000.toInt() or
                    (dv[ar] shl 16) or (dv[ag] shl 8) or dv[ab]
                val add = ((y + radius + 1).coerceIn(0, h - 1)) * w + x
                val sub = ((y - radius).coerceIn(0, h - 1)) * w + x
                ar += r[add] - r[sub]
                ag += g[add] - g[sub]
                ab += b[add] - b[sub]
                yp += w
            }
        }
    }
}

private fun stackRadiusFor(sigmaPx: Float): Int =
    (-1.0 + sqrt(1.0 + 6.0 * sigmaPx * sigmaPx)).roundToInt()

/** Progressive blur curve from Convx `ArtistHero.kt`. */
private fun artistFootBlurPt(fraction: Float): Float =
    if (fraction <= ArtistBlurKnee) 0f else minOf(48f, 207f * (fraction - ArtistBlurKnee).pow(1.855f))

private suspend fun bakeArtistFoot(context: android.content.Context, url: String): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val fullUrl = resizeArtwork(url, 1200)
        val request = ImageRequest.Builder(context)
            .data(fullUrl)
            .size(1200)
            .allowHardware(false)
            .build()
        val decoded = runCatching { context.imageLoader.execute(request) }.getOrNull()
            ?.drawable?.let { it as? BitmapDrawable }?.bitmap ?: return@withContext null
        val w = ArtistFootPx
        val h = (w * ArtistHeroExtent).roundToInt().coerceAtLeast(w)
        val crop = minOf(decoded.width, decoded.height)
        if (crop <= 0) return@withContext null
        val square = android.graphics.Bitmap.createBitmap(
            decoded, (decoded.width - crop) / 2, (decoded.height - crop) / 2, crop, crop,
        )
        val scaled = android.graphics.Bitmap.createScaledBitmap(square, w, w, true)
        val base = IntArray(w * h)
        scaled.getPixels(base, 0, w, 0, 0, w, w)
        for (y in w until h) System.arraycopy(base, (w - 1) * w, base, y * w, w)

        val pxPerPt = w / 402f
        val levels = Array(ArtistFootLevelsPt.size) { i ->
            val sigma = ArtistFootLevelsPt[i]
            if (sigma == 0f) base else base.copyOf().also {
                stackBlurPixels(it, w, h, stackRadiusFor(sigma * pxPerPt).coerceAtLeast(1))
            }
        }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val sigma = artistFootBlurPt(y.toFloat() / w)
            var hi = ArtistFootLevelsPt.indexOfFirst { it >= sigma }
            if (hi < 0) hi = ArtistFootLevelsPt.lastIndex
            val lo = maxOf(hi - 1, 0)
            val t = if (hi == lo) 1f else ((sigma - ArtistFootLevelsPt[lo]) / (ArtistFootLevelsPt[hi] - ArtistFootLevelsPt[lo])).coerceIn(0f, 1f)
            val a = levels[lo]
            val b = levels[hi]
            val row = y * w
            for (x in 0 until w) {
                val ca = a[row + x]
                val cb = b[row + x]
                val rr = (((ca shr 16) and 0xFF) * (1 - t) + ((cb shr 16) and 0xFF) * t).roundToInt()
                val gg = (((ca shr 8) and 0xFF) * (1 - t) + ((cb shr 8) and 0xFF) * t).roundToInt()
                val bb = ((ca and 0xFF) * (1 - t) + (cb and 0xFF) * t).roundToInt()
                out[row + x] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
            }
        }
        android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

@Composable
fun rememberArtistFoot(url: String?): ImageBitmap? {
    val context = LocalContext.current
    var foot by remember(url) {
        mutableStateOf(url?.let { synchronized(artistFootCache) { artistFootCache.get(it) } })
    }
    LaunchedEffect(url, context) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        synchronized(artistFootCache) { artistFootCache.get(url) }?.let {
            foot = it
            return@LaunchedEffect
        }
        val baked = withContext(Dispatchers.Default) { bakeArtistFoot(context, url) }
        if (baked != null) {
            synchronized(artistFootCache) { artistFootCache.put(url, baked) }
            foot = baked
        }
    }
    return foot
}

data class MeshColor(val red: Float, val green: Float, val blue: Float) {
    fun saturated(amount: Float): MeshColor {
        val r = (red * 255).roundToInt() / 255f
        val g = (green * 255).roundToInt() / 255f
        val b = (blue * 255).roundToInt() / 255f
        val maximum = maxOf(r, g, b)
        val minimum = minOf(r, g, b)
        val lightness = (maximum + minimum) / 2f
        val range = maximum - minimum
        val saturation = if (range > 0f) range / (1f - abs(2f * lightness - 1f)) else 0f
        if (saturation < .08f) return this
        val factor = minOf(amount, 1f / saturation)
        return MeshColor(
            lightness + (r - lightness) * factor,
            lightness + (g - lightness) * factor,
            lightness + (b - lightness) * factor,
        )
    }
}

data class ArtworkMeshPalette(
    val cells: List<MeshColor>,
    val average: MeshColor,
    val sourceAverage: MeshColor = average,
) {
    init { require(cells.size == 9) }

    companion object {
        val Fallback = ArtworkMeshPalette(
            listOf(0x101E3D, 0x2E1A57, 0x0F3B4D, 0x38143D, 0x24476B, 0x4D1F4D, 0x0D2E42, 0x291A4D, 0x121C33)
                .map(::color),
            color(0x171D32),
        )

        fun fromPixels(pixels: IntArray, width: Int, height: Int): ArtworkMeshPalette {
            require(width > 0 && height > 0 && width.toLong() * height <= pixels.size)
            fun average(left: Int, top: Int, right: Int, bottom: Int): MeshColor {
                var red = 0L
                var green = 0L
                var blue = 0L
                var count = 0
                for (y in top until bottom step 2) for (x in left until right step 2) {
                    val pixel = pixels[y * width + x]
                    red += (pixel ushr 16) and 255
                    green += (pixel ushr 8) and 255
                    blue += pixel and 255
                    count++
                }
                if (count == 0) return color(pixels[(top.coerceAtMost(height - 1)) * width + left.coerceAtMost(width - 1)])
                val scale = count * 255f
                return MeshColor(red / scale, green / scale, blue / scale)
            }
            val cells = List(9) { index ->
                val column = index % 3
                val row = index / 3
                average(column * width / 3, row * height / 3, (column + 1) * width / 3, (row + 1) * height / 3)
                    .saturated(1.2f)
            }
            val sourceAverage = average(0, 0, width, height)
            return ArtworkMeshPalette(cells, sourceAverage.saturated(1.1f), sourceAverage)
        }

        private fun color(argb: Int) = MeshColor(
            ((argb ushr 16) and 255) / 255f,
            ((argb ushr 8) and 255) / 255f,
            (argb and 255) / 255f,
        )
    }
}

class ArtworkMesh(val width: Int = 32, val height: Int = width * 23 / 10) {
    private val centersX = FloatArray(9)
    private val centersY = FloatArray(9)
    private val frequencyX = floatArrayOf(.50f, .25f, .75f, .50f, .25f, .75f, .50f, .25f, .75f)
    private val frequencyY = floatArrayOf(.25f, .75f, .50f, .75f, .50f, .25f, .50f, .25f, .75f)

    fun render(palette: ArtworkMeshPalette, phase: Float, pixels: IntArray) {
        require(pixels.size >= width * height)
        val time = if (phase.isFinite()) phase % PhasePeriod else 0f
        for (index in 0..8) {
            centersX[index] = .5f + .3f * sin(time * frequencyX[index] + index * 1.7f)
            centersY[index] = .5f + .3f * cos(time * frequencyY[index] + index * .9f)
        }
        val dimension = maxOf(width, height).toFloat()
        val widthScale = width / dimension
        val heightScale = height / dimension
        var output = 0
        for (y in 0 until height) for (x in 0 until width) {
            val u = x.toFloat() / (width - 1).coerceAtLeast(1)
            val v = y.toFloat() / (height - 1).coerceAtLeast(1)
            val warpX = sin(v * 3.1f + time) * cos(u * 2.3f - time * .75f) +
                .5f * sin((u + v) * 4.9f - time * 1.25f)
            val warpY = cos(u * 2.9f - time) * sin(v * 2.6f + time * .5f) +
                .5f * cos((u - v) * 5.4f + time)
            var sampleU = u + .15f * warpX
            var sampleV = v + .15f * warpY
            sampleU += .15f * .28f * sin(sampleV * 6.7f - time * 1.5f)
            sampleV += .15f * .28f * cos(sampleU * 7.1f + time * 1.5f)
            var weightSum = .16f
            var red = palette.average.red * .16f
            var green = palette.average.green * .16f
            var blue = palette.average.blue * .16f
            for (index in 0..8) {
                val dx = (sampleU - centersX[index]) * widthScale / .45f
                val dy = (sampleV - centersY[index]) * heightScale / .45f
                val falloff = 1f / (1f + (dx * dx + dy * dy) * 4.5f)
                val weight = falloff * falloff
                weightSum += weight
                red += palette.cells[index].red * weight
                green += palette.cells[index].green * weight
                blue += palette.cells[index].blue * weight
            }
            pixels[output++] = (255 shl 24) or
                (((red / weightSum).coerceIn(0f, 1f) * 255).toInt() shl 16) or
                (((green / weightSum).coerceIn(0f, 1f) * 255).toInt() shl 8) or
                ((blue / weightSum).coerceIn(0f, 1f) * 255).toInt()
        }
    }

    companion object {
        const val PhasePeriod = 25.132742f
        const val RadiansPerSecond = 1.92f
    }
}

data class PlaylistAppearance(val mesh: ArtworkMeshPalette, val background: Color, val foreground: Color)

/** Convx `rememberPlaylistAppearance`: palette lift, luminance gate, neutral/weight mix. */
@Composable
fun rememberPlaylistAppearance(imageUrl: String?): PlaylistAppearance {
    val context = LocalContext.current
    var palette by remember(imageUrl) {
        mutableStateOf(ArtworkMeshPalette.Fallback)
    }
    LaunchedEffect(imageUrl, context) {
        if (imageUrl == null) return@LaunchedEffect
        val sampled = withContext(Dispatchers.Default) {
            loadArtPixels(context, imageUrl)?.let { ArtworkMeshPalette.fromPixels(it, 160, 160) }
        }
        palette = sampled ?: ArtworkMeshPalette.Fallback
    }
    return remember(palette) {
        val average = palette.sourceAverage
        val dark = average.red * .2126f + average.green * .7152f + average.blue * .0722f < .52f
        val neutral = if (dark) .055f else .94f
        val weight = if (dark) .38f else .30f
        PlaylistAppearance(
            palette,
            Color(
                average.red * weight + neutral * (1f - weight),
                average.green * weight + neutral * (1f - weight),
                average.blue * weight + neutral * (1f - weight),
            ),
            if (dark) Color.White else Color.Black,
        )
    }
}

private const val PlaylistMeshLift = 0.12f

private fun ArtworkMeshPalette.lifted(amount: Float): ArtworkMeshPalette {
    fun lift(c: MeshColor) = MeshColor(
        c.red + (1f - c.red) * amount,
        c.green + (1f - c.green) * amount,
        c.blue + (1f - c.blue) * amount,
    )
    return copy(cells = cells.map(::lift), average = lift(average))
}

/** Convx's animated 32×73 artwork mesh, 33 ms cadence, two-bitmap ping-pong. */
@Composable
fun ArtworkMeshBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    appearance: PlaylistAppearance,
    paused: () -> Boolean = { false },
) {
    val palette = remember(appearance.mesh) { appearance.mesh.lifted(PlaylistMeshLift) }
    val mesh = remember { ArtworkMesh() }
    val bitmaps = remember(mesh) {
        val first = IntArray(mesh.width * mesh.height)
        mesh.render(palette, 0f, first)
        List(2) {
            android.graphics.Bitmap.createBitmap(mesh.width, mesh.height, android.graphics.Bitmap.Config.ARGB_8888)
                .also { it.setPixels(first, 0, mesh.width, 0, 0, mesh.width, mesh.height) }
        }
    }
    val images = remember(bitmaps) { bitmaps.map(android.graphics.Bitmap::asImageBitmap) }
    var displayed by remember(images) { mutableStateOf(images.first()) }

    LaunchedEffect(mesh, bitmaps, imageUrl) {
        val pixels = IntArray(mesh.width * mesh.height)
        var phase = 0f
        var nextBitmap = 1
        var previousFrame = 0L
        var lastRendered = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (lastRendered != 0L && paused()) {
                previousFrame = 0L
                continue
            }
            if (frame - lastRendered < 33_000_000L) continue
            val elapsed = if (previousFrame == 0L) 0f else ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, .1f)
            previousFrame = frame
            lastRendered = frame
            phase = (phase + ArtworkMesh.RadiansPerSecond * elapsed) % ArtworkMesh.PhasePeriod
            val bitmap = bitmaps[nextBitmap]
            withContext(Dispatchers.Default) {
                mesh.render(palette, phase, pixels)
                bitmap.setPixels(pixels, 0, mesh.width, 0, 0, mesh.width, mesh.height)
            }
            displayed = images[nextBitmap]
            nextBitmap = 1 - nextBitmap
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawImage(
            image = displayed,
            dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        drawRect(
            if (appearance.foreground == Color.White) Color.Black.copy(alpha = .03f)
            else Color.White.copy(alpha = .06f),
        )
    }
}
