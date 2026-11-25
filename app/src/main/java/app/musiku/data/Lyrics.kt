package app.musiku.data

import app.musiku.lyrics.LyricsEntry
import app.musiku.lyrics.LyricsUtils
import app.musiku.lyrics.TTMLParser
import app.musiku.model.LyricLine
import app.musiku.model.Lyrics
import app.musiku.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

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
        val durationInt = durationSec?.roundToInt() ?: -1
        val found = fromBetterLyrics(track.title, track.artist, durationInt, track.album)
            ?: fromLrcRed(track.title, track.artist, durationInt)
            ?: fromLrclib(track.title, track.artist, durationSec)
            ?: yt.ytLyrics(track.videoId)?.let { Lyrics(plain = it, source = "youtube-music") }
            ?: Lyrics()
        if (found.hasText || durationSec != null) cache[track.videoId] = found
        return found
    }

    private fun toSyncedLyrics(lrc: String, source: String): Lyrics? {
        val entries: List<LyricsEntry> = runCatching { LyricsUtils.parseLyricsCached(lrc) }
            .getOrElse { emptyList() }
            .filter { it.text.isNotBlank() }
        if (entries.isEmpty()) return null
        val lines = entries.map { LyricLine(it.time / 1000f, it.text) }
        val wordSynced = entries.any { !it.words.isNullOrEmpty() || !it.syllables.isNullOrEmpty() }
        return Lyrics(
            lines = lines,
            entries = entries,
            synced = true,
            wordSynced = wordSynced,
            source = source,
        )
    }

    private suspend fun fromBetterLyrics(
        title: String,
        artist: String?,
        durationSec: Int,
        album: String?,
    ): Lyrics? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
                .addQueryParameter("s", title)
                .apply { if (!artist.isNullOrBlank()) addQueryParameter("a", artist) }
                .apply { if (durationSec > 0) addQueryParameter("d", durationSec.toString()) }
                .apply { if (!album.isNullOrBlank()) addQueryParameter("al", album) }
                .build()
            val req = Request.Builder().url(url).header("User-Agent", "Musiku/1.0 (android)").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body?.string() ?: return@use null
                val ttml = JSONObject(body).optString("ttml", "").takeIf { it.isNotBlank() } ?: return@use null
                val parsed = TTMLParser.parseTTML(ttml)
                if (parsed.isEmpty()) return@use null
                toSyncedLyrics(TTMLParser.toLRC(parsed), "betterlyrics")
            }
        }.getOrNull()
    }

    private suspend fun fromLrcRed(
        title: String,
        artist: String?,
        durationSec: Int,
    ): Lyrics? = withContext(Dispatchers.IO) {
        runCatching {
            val query = listOfNotNull(title, artist?.takeIf { it.isNotBlank() }).joinToString(" ")
            val isrc = searchLrcRed(query, title, artist, durationSec)
                ?: if (!artist.isNullOrBlank()) searchLrcRed(title, title, artist, durationSec) else null
                ?: return@runCatching null
            val ttmlReq = Request.Builder()
                .url("https://lrc.red/s/$isrc.ttml")
                .header("User-Agent", "Musiku/1.0 (android)")
                .build()
            val ttml = http.newCall(ttmlReq).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.string()
            } ?: return@runCatching null
            val parsed = TTMLParser.parseTTML(ttml)
            if (parsed.isEmpty()) return@runCatching null
            toSyncedLyrics(TTMLParser.toLRC(parsed), "lrcred")
        }.getOrNull()
    }

    private fun searchLrcRed(query: String, title: String, artist: String?, durationSec: Int): String? {
        val url = "https://lrc.red/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Musiku/1.0 (android)").build()
        val body = http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } ?: return null
        val hits = JSONObject(body).optJSONArray("hits") ?: return null
        var bestIsrc: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 0 until hits.length()) {
            val h = hits.optJSONObject(i) ?: continue
            val isrc = h.optString("isrc", "").takeIf { it.isNotBlank() } ?: continue
            val hTitle = h.optString("title", "")
            val hArtist = h.optString("artist", "")
            val hDur = h.optDouble("duration", 0.0)
            var score = 0.0
            if (hTitle.equals(title, ignoreCase = true)) score += 100.0
            else if (hTitle.contains(title, ignoreCase = true)) score += 40.0
            if (!artist.isNullOrBlank() && hArtist.contains(artist.substringBefore(",").trim(), ignoreCase = true)) score += 60.0
            if (durationSec > 0 && hDur > 0) {
                val diff = abs(hDur - durationSec)
                if (diff > 8.0) continue
                score -= diff * 4.0
            }
            if (score > bestScore) {
                bestScore = score
                bestIsrc = isrc
            }
        }
        return bestIsrc
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
                    var best: JSONObject? = null
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
                        synced != null -> toSyncedLyrics(synced, "lrclib")
                        plain != null -> Lyrics(plain = plain, source = "lrclib")
                        else -> null
                    }
                }
            }.getOrNull()
        }
}
