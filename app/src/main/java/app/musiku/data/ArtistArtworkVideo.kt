package app.musiku.data

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Motion artwork for an artist hero, from the artwork-reference service Convx uses.
 *
 * The service maps one (song, artist, album, duration) tuple to the artwork Apple shipped for that
 * release: `animated` is the mp4, `videoUrl` a second mp4 name for the same thing and `static` the
 * still. There is no contract or version, so every failure is swallowed and the caller keeps the
 * static hero.
 *
 * Positive hits live for six hours; misses live for the session (mirrors [Canvas]'s `misses`) so a
 * recomposition never turns into another request.
 */
class ArtistArtworkVideo(private val http: OkHttpClient) {

    /** [url] is the motion mp4; [static] is the higher-resolution still, when the service has one. */
    data class Result(val url: String, val static: String?)

    private class CacheEntry(val value: Result, val expiresAtMs: Long)

    private val hits = LruCache<String, CacheEntry>(64)
    private val misses = LruCache<String, Boolean>(128)

    /** Concurrent callers for the same track wait for one request instead of both firing it. */
    private val fetch = Mutex()

    suspend fun lookup(song: String, artist: String, album: String?, durationSec: Int?): Result? {
        val normArtist = normalize(artist)
        val normSong = normalize(song)
        if (normArtist.isEmpty() || normSong.isEmpty()) return null
        val key = "$normArtist|$normSong|${album?.let(::normalize).orEmpty()}"
        if (misses.get(key) == true) return null
        hits.get(key)?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            hits.remove(key)
        }

        return fetch.withLock {
            if (misses.get(key) == true) return@withLock null
            hits.get(key)?.let { entry ->
                if (entry.expiresAtMs > System.currentTimeMillis()) return@withLock entry.value
                hits.remove(key)
            }
            val result = request(song, artist, album, durationSec)
            if (result == null) {
                misses.put(key, true)
                null
            } else {
                hits.put(key, CacheEntry(result, System.currentTimeMillis() + TTL_MS))
                result
            }
        }
    }

    private suspend fun request(
        song: String,
        artist: String,
        album: String?,
        durationSec: Int?,
    ): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("s", song)
                .addQueryParameter("a", artist)
                .apply {
                    album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("al", it) }
                    durationSec?.takeIf { it > 0 }?.let { addQueryParameter("d", it.toString()) }
                }
                .build()
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            http.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val video = json.optString("animated").ifBlank { json.optString("videoUrl") }
                    .trim()
                    .takeIf { it.isNotEmpty() } ?: return@use null
                Result(video, json.optString("static").trim().ifEmpty { null })
            }
        }.getOrNull()
    }

    private fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT)

    private companion object {
        const val BASE_URL = "https://artwork-reference.koiiverse.cloud/"
        const val TTL_MS = 6L * 60 * 60 * 1000
    }
}
