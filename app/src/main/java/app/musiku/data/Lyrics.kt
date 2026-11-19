package app.musiku.data

import app.musiku.model.LyricLine
import app.musiku.model.Lyrics
import app.musiku.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Port of `parseLrc` (LyricsPanel.tsx) + the lrclib lookup from lib/music.ts. */
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (line in raw.split(Regex("\\r?\\n"))) {
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.replace(Regex("\\[[^\\]]*]"), "").trim()
            for (m in matches) {
                val min = m.groupValues[1].toInt()
                val sec = m.groupValues[2].toInt()
                val frac = m.groupValues[3].ifEmpty { "0" }.padEnd(3, '0').toInt() / 1000f
                out += LyricLine(min * 60f + sec + frac, text)
            }
        }
        return out.sortedBy { it.timeSec }
    }
}

class LyricsRepo(private val http: OkHttpClient, private val yt: YtMusic) {
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun load(track: Track, durationSec: Float?): Lyrics {
        cache[track.videoId]?.let { return it }
        val found = fromLrclib(track.title, track.artist, durationSec)
            ?: yt.ytLyrics(track.videoId)?.let { Lyrics(plain = it, source = "youtube-music") }
            ?: Lyrics()
        // don't cache "nothing" for a track whose duration wasn't known yet
        if (found.hasText || durationSec != null) cache[track.videoId] = found
        return found
    }

    private suspend fun fromLrclib(title: String, artist: String?, durationSec: Float?): Lyrics? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                    .addQueryParameter("track_name", title)
                    .apply { if (!artist.isNullOrBlank()) addQueryParameter("artist_name", artist) }
                    .build()
                val req = Request.Builder().url(url).header("User-Agent", "Musiku/1.0 (android)").build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val items = JSONArray(r.body?.string() ?: return@use null)
                    if (items.length() == 0) return@use null
                    val target = durationSec ?: 0f
                    var best: org.json.JSONObject? = null
                    var bestScore = Double.NEGATIVE_INFINITY
                    for (i in 0 until items.length()) {
                        val o = items.optJSONObject(i) ?: continue
                        val synced = o.optString("syncedLyrics", "").takeIf { s -> s.isNotBlank() && s != "null" }
                        val score = (if (synced != null) 1_000_000.0 else 0.0) - abs(o.optDouble("duration", 0.0) - target)
                        if (score > bestScore) { bestScore = score; best = o }
                    }
                    val b = best ?: return@use null
                    val synced = b.optString("syncedLyrics", "").takeIf { s -> s.isNotBlank() && s != "null" }
                    val plain = b.optString("plainLyrics", "").takeIf { s -> s.isNotBlank() && s != "null" }
                    when {
                        synced != null -> {
                            val lines = LrcParser.parse(synced)
                            if (lines.isEmpty()) null else Lyrics(lines = lines, synced = true, source = "lrclib")
                        }
                        plain != null -> Lyrics(plain = plain, source = "lrclib")
                        else -> null
                    }
                }
            }.getOrNull()
        }
}
