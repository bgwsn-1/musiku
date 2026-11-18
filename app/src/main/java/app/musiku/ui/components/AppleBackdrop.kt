package app.musiku.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/**
 * Apple's animated Now Playing backdrop, ported from `LyricsBackgroundLayerView` (decompiled,
 * Apple Music 7.0.0). Its constructor draws **three copies of the same artwork**, each rotating
 * continuously around the view centre on a `LinearInterpolator`:
 *
 *   rot1: 0 → -360° over 120_000 ms   (2 min)
 *   rot2: 0 →  360° over  90_000 ms   (1.5 min)
 *   rot3: 0 →  360° over  70_000 ms
 *
 * with `setSaturation(2.5f)` on every layer and a ×1.3 overscan so rotation never reveals an edge.
 * The whole field fades in over 1000 ms on `cubic-bezier(0, 0, 0.3, 1)`.
 *
 * The web build has the same recipe in `AmbientWindow.tsx` (`.liquid-a/b/c`, 120s/90s/70s) — this
 * follows both. Where Apple runs a RenderScript blur and the web pre-blurs into a 64px canvas, we
 * ask Coil for a small bitmap once per track and let the upscale do the blurring: the field is a
 * soft colour wash either way, at a fraction of the cost (Apple's own comment: blur the *small*
 * bitmap). The three layer transforms are RenderNode (GPU) only, so nothing is re-rasterised.
 */
@Composable
fun AppleBackdrop(
    url: String?,
    modifier: Modifier = Modifier,
    saturation: Float = 2.5f,
    overlayAlpha: Float = 0.6f,
) {
    val ctx = LocalContext.current
    var art by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        art = null
        if (url == null) return@LaunchedEffect
        val req = ImageRequest.Builder(ctx)
            .data(url)
            .size(96, 96) // Apple blurs a downscaled raster; upscaling it is the blur.
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowHardware(false) // needs a software Bitmap to hand to Compose
            .build()
        val bmp = runCatching { ctx.imageLoader.execute(req) }.getOrNull()
            ?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
        art = bmp?.asImageBitmap()
    }

    val bitmap = art ?: return
    val image = remember(bitmap) { bitmap }

    // Three counter-rotating layers, exactly Apple's durations/directions (all linear, infinite).
    val t = rememberInfiniteTransition(label = "np-backdrop")
    val a1 by t.animateFloat(0f, -360f, infiniteRepeatable(tween(120_000, easing = LinearEasing)), label = "l1")
    val a2 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "l2")
    val a3 by t.animateFloat(0f, 360f, infiniteRepeatable(tween(70_000, easing = LinearEasing)), label = "l3")

    // Fade the whole field in once per track: 1000 ms, cubic-bezier(0, 0, 0.3, 1) (Apple's fadeIn).
    val fade = remember { Animatable(0f) }
    LaunchedEffect(image) {
        fade.snapTo(0f)
        fade.animateTo(overlayAlpha, tween(1000, easing = CubicBezierEasing(0f, 0f, 0.3f, 1f)))
    }

    val filter = remember(saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }

    // Layer offsets: the web nudges layers B/C off-centre so the field is asymmetric (liquid-a/b/c).
    val layers = listOf(
        Triple(a1, 0f, 0f),
        Triple(a2, -0.16f, -0.12f),
        Triple(a3, 0.10f, 0.13f),
    )

    Box(modifier) {
        for (i in 0 until 3) {
            val (rot, ox, oy) = layers[i]
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                alpha = fade.value,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // ×1.3 overscan so rotation never shows the bitmap edge / a transparent corner.
                        scaleX = 1.3f
                        scaleY = 1.3f
                        rotationZ = rot
                        translationX = ox * size.width
                        translationY = oy * size.height
                    },
            )
        }
    }
}
