package app.musiku.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check against GitHub Releases, the same source Convx reads.
 *
 * The release tag doubles as the version name (`v0.12` / `0.12`); the app compares that against
 * its own `versionName`, so bumping `versionName` in `build.gradle.kts` is all a release has to
 * match. The first `.apk` asset of the newest non-prerelease release is what "Unduh" opens.
 */
object UpdateSource {
    /** `owner/repo` that publishes Musiku's releases. Blank disables the check entirely. */
    const val Repo = "bgwsn-1/musiku"
}

data class UpdateInfo(
    val version: String,
    val notes: String?,
    val apkUrl: String?,
    val pageUrl: String,
)

/** One release's body, used by the changelog shown after an update. */
data class ReleaseNotes(val version: String, val notes: String)

/**
 * State of the in-app update download, owned by `AppRoot` and drawn by the update card.
 * [file] is the finished APK; [needsPermission] means Android still has to be told that
 * Musiku may install packages.
 */
data class UpdateDownload(
    val running: Boolean = false,
    /** 0..1 while downloading; null when the server does not report a size. */
    val progress: Float? = null,
    val file: java.io.File? = null,
    val needsPermission: Boolean = false,
    val error: String? = null,
)

private val Context.updateStore by preferencesDataStore(name = "updates")
private val kDismissed = stringPreferencesKey("dismissedVersion")
private val kNotified = stringPreferencesKey("notifiedVersion")
private val kLastVersion = stringPreferencesKey("changelogSeenVersion")

/** Which version the user already said "nanti" to, so it does not ask again for the same one. */
suspend fun dismissedUpdateVersion(ctx: Context): String? =
    runCatching { ctx.updateStore.data.first()[kDismissed] }.getOrNull()

suspend fun dismissUpdate(ctx: Context, version: String) {
    runCatching { ctx.updateStore.edit { it[kDismissed] = version } }
}

/** Last version a status-bar notification was posted for, so the worker stays quiet otherwise. */
suspend fun notifiedUpdateVersion(ctx: Context): String? =
    runCatching { ctx.updateStore.data.first()[kNotified] }.getOrNull()

suspend fun markUpdateNotified(ctx: Context, version: String) {
    runCatching { ctx.updateStore.edit { it[kNotified] = version } }
}

/** Installed version the changelog was last handled for; null on the first launch ever. */
suspend fun lastSeenVersion(ctx: Context): String? =
    runCatching { ctx.updateStore.data.first()[kLastVersion] }.getOrNull()

suspend fun setLastSeenVersion(ctx: Context, version: String) {
    runCatching { ctx.updateStore.edit { it[kLastVersion] = version } }
}

/**
 * Notes for every published release in `(fromExclusive, toInclusive]`, oldest first. GitHub lists
 * newest first, so the result is reversed at the end. An empty list means GitHub answered but has
 * nothing to show; a thrown error means the caller should not advance the last-seen version yet.
 */
suspend fun releaseNotesBetween(fromExclusive: String?, toInclusive: String): List<ReleaseNotes> =
    withContext(Dispatchers.IO) {
        val repo = UpdateSource.Repo
        if (repo.isBlank() || toInclusive.isBlank()) return@withContext emptyList()
        val body = getJson("https://api.github.com/repos/$repo/releases?per_page=30")
        val releases = JSONArray(body)
        val found = ArrayList<ReleaseNotes>()
        for (i in 0 until releases.length()) {
            val rel = runCatching { releases.getJSONObject(i) }.getOrNull() ?: continue
            if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
            val version = rel.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (version.isBlank() || isNewerVersion(version, toInclusive)) continue
            if (fromExclusive != null && !isNewerVersion(version, fromExclusive)) continue
            val notes = rel.optString("body").trim().take(4_000).takeIf { it.isNotEmpty() } ?: continue
            found += ReleaseNotes(version, notes)
        }
        // Skipping many releases should not turn the dialog into the whole release history.
        found.take(5).asReversed()
    }

fun openInBrowser(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * The newest release newer than [currentVersion], or null. Network/API failures are swallowed:
 * an update check must never be the reason the app complains on launch.
 */
suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
    val repo = UpdateSource.Repo
    if (repo.isBlank() || currentVersion.isBlank()) return@withContext null
    val body = runCatching { getJson("https://api.github.com/repos/$repo/releases?per_page=10") }
        .onFailure { android.util.Log.w("MusikuUpdate", "fetch failed for $repo", it) }
        .getOrNull() ?: return@withContext null
    val releases = runCatching { JSONArray(body) }
        .onFailure { android.util.Log.w("MusikuUpdate", "bad json", it) }
        .getOrNull() ?: return@withContext null
    for (i in 0 until releases.length()) {
        val rel = runCatching { releases.getJSONObject(i) }.getOrNull() ?: continue
        if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue
        val version = rel.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (!isNewerVersion(version, currentVersion)) continue
        val assets = rel.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        return@withContext UpdateInfo(
            version = version,
            notes = rel.optString("body").trim().take(2000).takeIf { it.isNotEmpty() },
            apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
            pageUrl = rel.optString("html_url"),
        )
    }
    null
}

/** Dotted numeric compare: 0.12 > 0.9 > 0.8.1, non-numeric suffixes ignored. */
fun isNewerVersion(remote: String, current: String): Boolean {
    fun parts(v: String) = v.split('.', '-', '+', '_').mapNotNull { it.toIntOrNull() }
    val a = parts(remote)
    val b = parts(current)
    if (a.isEmpty()) return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private fun getJson(url: String): String {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 12_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Musiku-Android")
    }
    return try {
        c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
