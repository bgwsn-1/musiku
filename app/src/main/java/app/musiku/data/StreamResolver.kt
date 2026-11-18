package app.musiku.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * The UA used both for extracting streams and for downloading them. googlevideo URLs are
 * minted for the client that asked, so the player must present the same one.
 */
const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

/** OkHttp-backed [Downloader] the extractor needs (same shape as NewPipe's own app). */
private class NpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: NpRequest): Response {
        val body = request.dataToSend()
        val rb = Request.Builder().url(request.url())
        rb.header("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            rb.removeHeader(name)
            for (v in values) rb.addHeader(name, v)
        }
        when (request.httpMethod()) {
            "POST" -> rb.post((body ?: ByteArray(0)).toRequestBody(null))
            "HEAD" -> rb.head()
            else -> rb.get()
        }
        client.newCall(rb.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return Response(
                r.code,
                r.message,
                r.headers.toMultimap(),
                r.body?.string(),
                r.request.url.toString(),
            )
        }
    }
}

/**
 * videoId → direct audio URL, via NewPipeExtractor (it keeps up with YouTube's signature and
 * client changes, which a hand-written resolver wouldn't). Stand-in for lib/stream.ts.
 */
class StreamResolver(client: OkHttpClient) {
    private data class Hit(val url: String, val exp: Long)

    private val cache = ConcurrentHashMap<String, Hit>()

    init {
        NewPipe.init(NpDownloader(client), Localization("id", "ID"))
    }

    /** Blocking on purpose: called from ExoPlayer's loader thread via ResolvingDataSource. */
    fun resolveBlocking(videoId: String): String {
        cache[videoId]?.let { if (it.exp > System.currentTimeMillis()) return it.url }
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        // m4a/AAC first (plays everywhere), then the highest bitrate of whatever is left
        val streams = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
        val pick = streams.filter { it.format?.suffix == "m4a" }.maxByOrNull { it.averageBitrate }
            ?: streams.maxByOrNull { it.averageBitrate }
            ?: error("no audio stream for $videoId")
        Log.d("Musiku", "resolved $videoId -> ${pick.format?.suffix} ${pick.averageBitrate}kbps")
        cache[videoId] = Hit(requireNotNull(pick.content), System.currentTimeMillis() + 30 * 60 * 1000L)
        return pick.content
    }

    suspend fun resolve(videoId: String): String = withContext(Dispatchers.IO) { resolveBlocking(videoId) }
}
