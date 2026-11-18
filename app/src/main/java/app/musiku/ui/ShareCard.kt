package app.musiku.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import app.musiku.model.Track
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val W = 1080
private const val H = 1350

private suspend fun loadArt(ctx: Context, url: String?): Bitmap? {
    if (url == null) return null
    val res = ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).build())
    return ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
}

/** Draws the share card (cover on a blurred, darkened copy of itself) and opens the share sheet. */
suspend fun shareTrack(ctx: Context, t: Track, progress: Float) {
    val art = loadArt(ctx, t.thumbs)
    val file = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        c.drawColor(Color.parseColor("#0B0B0E"))
        if (art != null) {
            // cheap blur: shrink hard, stretch back with filtering
            val tiny = Bitmap.createScaledBitmap(art, 24, 24, true)
            c.drawBitmap(tiny, Rect(0, 0, 24, 24), Rect(-200, -200, W + 200, H + 200), p)
        }
        p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.argb(140, 0, 0, 0), Color.argb(235, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        val size = 760f
        val left = (W - size) / 2f
        val top = 150f
        val rect = RectF(left, top, left + size, top + size)
        // soft shadow
        p.color = Color.argb(90, 0, 0, 0)
        c.drawRoundRect(RectF(rect.left + 6, rect.top + 26, rect.right - 6, rect.bottom + 26), 56f, 56f, p)
        if (art != null) {
            val save = c.save()
            val path = android.graphics.Path().apply { addRoundRect(rect, 56f, 56f, android.graphics.Path.Direction.CW) }
            c.clipPath(path)
            val s = minOf(art.width, art.height)
            val src = Rect((art.width - s) / 2, (art.height - s) / 2, (art.width + s) / 2, (art.height + s) / 2)
            c.drawBitmap(art, src, rect, p)
            c.restoreToCount(save)
        } else {
            p.color = Color.parseColor("#C4183C")
            c.drawRoundRect(rect, 56f, 56f, p)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = Color.WHITE
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textSize = 64f
        c.drawText(ellipsize(t.title, text, W - 200f), 100f, top + size + 120f, text)
        text.typeface = Typeface.DEFAULT
        text.color = Color.argb(200, 255, 255, 255)
        text.textSize = 44f
        c.drawText(ellipsize(t.artist.orEmpty(), text, W - 200f), 100f, top + size + 190f, text)

        // progress pill
        val barY = top + size + 260f
        p.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(RectF(100f, barY, W - 100f, barY + 14f), 7f, 7f, p)
        p.color = Color.WHITE
        c.drawRoundRect(RectF(100f, barY, 100f + (W - 200f) * progress.coerceIn(0f, 1f), barY + 14f), 7f, 7f, p)

        text.textSize = 34f
        text.color = Color.argb(150, 255, 255, 255)
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText("Musiku", 100f, H - 70f, text)

        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        File(dir, "musiku-${t.videoId}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, if (t.artist != null) "${t.title} — ${t.artist}" else t.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Bagikan lagu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun ellipsize(s: String, p: Paint, max: Float): String {
    if (p.measureText(s) <= max) return s
    var n = s.length
    while (n > 1 && p.measureText(s.substring(0, n) + "…") > max) n--
    return s.substring(0, n) + "…"
}
