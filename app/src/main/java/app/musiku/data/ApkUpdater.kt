package app.musiku.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update: fetch the release APK into the app's cache and hand it straight to the system
 * installer, so updating never has to leave Musiku for a browser tab.
 *
 * The APK is validated before it is offered to the installer (same package, higher version code),
 * and the finished file is served through the app's existing [FileProvider], because Android
 * refuses `file://` install intents.
 */
object ApkUpdater {

    private const val Tag = "MusikuUpdate"
    private const val Buffer = 64 * 1024

    private fun updatesDir(ctx: Context): File = File(ctx.cacheDir, "updates")

    /** A still-valid APK from an earlier download, if the cache kept it. */
    fun cached(ctx: Context, version: String): File? =
        File(updatesDir(ctx), "musiku-$version.apk").takeIf { it.isFile && it.length() > 0 }

    /**
     * Streams [url] into the cache. Progress is 0..1, or null when the server sends no size.
     * Throws with a message the card can show when the download or the APK check fails.
     */
    suspend fun download(
        ctx: Context,
        url: String,
        version: String,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = updatesDir(ctx).apply { mkdirs() }
        // One APK at a time: a new download replaces whatever an old prompt left behind.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "musiku-$version.apk")
        val part = File(dir, "musiku-$version.apk.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Musiku-Android")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Server membalas $code")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().buffered(Buffer).use { out ->
                    val buffer = ByteArray(Buffer)
                    var read = 0L
                    var lastPercent = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (part.length() <= 0L) error("Berkas kosong")
            onProgress(null)
            verify(ctx, part)
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) error("Gagal menyimpan berkas")
            }
            target
        } catch (e: Exception) {
            part.delete()
            Log.w(Tag, "download failed", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Same package and a strictly newer version code — nothing else gets installed. */
    private fun verify(ctx: Context, file: File) {
        val pm = ctx.packageManager
        val incoming = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: error("Berkas APK tidak valid")
        if (incoming.packageName != ctx.packageName) error("APK bukan untuk Musiku")
        val remote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            incoming.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            incoming.versionCode.toLong()
        }
        val installed = runCatching { pm.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val current = when {
            installed == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> installed.longVersionCode
            else -> {
                @Suppress("DEPRECATION")
                installed.versionCode.toLong()
            }
        }
        if (remote <= current) error("APK bukan versi yang lebih baru")
    }

    /** Whether Android will let Musiku start the package installer at all. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** Opens the system installer for a downloaded APK. */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** "Install unknown apps" page for this app, for the first update on a fresh phone. */
    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
