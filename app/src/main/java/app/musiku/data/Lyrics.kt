package app.musiku.data

import app.musiku.model.LyricLine
import app.musiku.model.LyricWord
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

/** Port of Convx's line LRC parser plus its EnhancedLrc (A2) word-stamp parser. */
object LrcParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    // [00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna <00:28.07>call
    private val a2Line = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val a2Word = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")
    private const val TAIL_MS = 800L
    private const val MIN_GAP_MS = 4_000L

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

    /** Null when the file has no word stamps, so the caller can fall back to [parse]. */
    fun parseEnhanced(raw: String): List<LyricLine>? {
        val rows = raw.lineSequence()
            .mapNotNull { a2Line.matchEntire(it.trim()) }
            .map { m ->
                Row(
                    timeMs = a2Stamp(m.groupValues[1], m.groupValues[2], m.groupValues[3]),
                    words = a2Word.findAll(m.groupValues[4]).toList(),
                    plain = m.groupValues[4].trim(),
                )
            }
            .sortedBy { it.timeMs }
            .toList()
        if (rows.none { it.words.isNotEmpty() }) return null

        return withInstrumentalGaps(rows.mapIndexedNotNull { index, row ->
            if (row.words.isEmpty()) {
                val text = decodeEntities(row.plain)
                if (text.isEmpty()) null else LyricLine(row.timeMs / 1000f, text)
            } else {
                // A word runs until the next one starts; the last runs until the next line does.
                val lineEnd = rows.getOrNull(index + 1)?.timeMs ?: (a2Stamp(row.words.last()) + TAIL_MS)
                val runs = row.words.mapIndexed { i, w ->
                    TimedRun(
                        a2Stamp(w),
                        row.words.getOrNull(i + 1)?.let(::a2Stamp) ?: lineEnd,
                        decodeEntities(w.groupValues[4]),
                    )
                }
                val words = wordsFromRuns(runs)
                if (words.isEmpty()) null else LyricLine(
                    timeSec = minOf(row.timeMs, words.first().startMs) / 1000f,
                    text = words.joinToString(" ") { it.text },
                    words = words,
                )
            }
        })
    }

    private class Row(val timeMs: Long, val words: List<MatchResult>, val plain: String)
    private class TimedRun(val startMs: Long, val endMs: Long, val raw: String)

    private fun a2Stamp(m: MatchResult): Long = a2Stamp(m.groupValues[1], m.groupValues[2], m.groupValues[3])

    private fun a2Stamp(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    /** Keeps the raw spacing: a run with no space after it is a syllable of the next word. */
    private fun wordsFromRuns(runs: List<TimedRun>): List<LyricWord> {
        val glue = runs.any { it.raw.any(Char::isWhitespace) }
        val words = ArrayList<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L

        fun flush() {
            if (current.isNotEmpty()) {
                words += LyricWord(current.toString(), start, maxOf(start, end))
                current.setLength(0)
            }
        }

        for (run in runs) {
            val content = run.raw.trim()
            if (content.isEmpty()) {
                flush()
                continue
            }
            val joins = glue && current.isNotEmpty() && !run.raw.first().isWhitespace() &&
                !current.last().isUnspacedScript() && !content.first().isUnspacedScript()
            if (!joins) flush()
            if (current.isEmpty()) {
                start = run.startMs
                end = run.startMs
            }
            current.append(content)
            end = maxOf(end, run.startMs, run.endMs)
            if (!glue || run.raw.last().isWhitespace()) flush()
        }
        flush()
        return words
    }

    /** Scripts written without spaces between words. */
    private fun Char.isUnspacedScript(): Boolean =
        this in '\u3400'..'\u9FFF' || this in '\u3040'..'\u30FF' || this in '\u3000'..'\u303F' ||
            this in '\uFF00'..'\uFFEF' || this in '\u0E00'..'\u0E7F'

    /** SimpMusic serves rich sync HTML-escaped; decode once so words aren't sung literally. */
    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    /** Marks instrumental stretches with blank lines, as Convx's LyricGaps does. */
    private fun withInstrumentalGaps(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val out = ArrayList<LyricLine>(lines.size + 4)
        if (lines.first().timeSec * 1000f >= MIN_GAP_MS) out += LyricLine(0f, "")
        lines.forEachIndexed { index, line ->
            out += line
            val words = line.words ?: return@forEachIndexed
            val end = words.last().endMs
            val start = words.first().startMs
            val next = lines.getOrNull(index + 1) ?: return@forEachIndexed
            val silence = next.timeSec * 1000f - end
            if (silence >= MIN_GAP_MS && end > start) out += LyricLine(end / 1000f, "")
        }
        return out
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
                            val lines = LrcParser.parseEnhanced(synced) ?: LrcParser.parse(synced)
                            if (lines.isEmpty()) null else Lyrics(lines = lines, synced = true, source = "lrclib")
                        }
                        plain != null -> Lyrics(plain = plain, source = "lrclib")
                        else -> null
                    }
                }
            }.getOrNull()
        }
}
