package app.musiku.data

import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

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
 * videoId → direct audio URL, via NewPipeExtractor with InnerTube multi-client fallbacks
 * (`ANDROID_VR`, `IOS`, `ANDROID_MUSIC`) when NewPipe hits bot-checks or signature changes.
 */
class StreamResolver(private val client: OkHttpClient) {
    private data class Hit(val url: String, val exp: Long)

    private val cache = LruCache<String, Hit>(256)

    init {
        NewPipe.init(NpDownloader(client), Localization("id", "ID"))
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Blocking on purpose: called from ExoPlayer's loader thread via ResolvingDataSource. */
    fun resolveBlocking(videoId: String): String {
        cache.get(videoId)?.let {
            if (it.exp > System.currentTimeMillis()) return it.url
            cache.remove(videoId)
        }

        val url = runCatching { resolveWithNewPipe(videoId) }
            .onFailure { Log.w(TAG, "NewPipe failed for $videoId: ${it.message}") }
            .getOrNull()
            ?: resolveWithInnerTubeFallbacks(videoId)
            ?: error("no audio stream for $videoId")

        val expMs = parseExpiryMs(url) ?: (System.currentTimeMillis() + DEFAULT_TTL_MS)
        cache.put(videoId, Hit(url, expMs))
        return url
    }

    private fun resolveWithNewPipe(videoId: String): String {
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val streams = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
        val pick = streams.filter { it.format?.suffix == "m4a" }.maxByOrNull { it.averageBitrate }
            ?: streams.maxByOrNull { it.averageBitrate }
            ?: error("no audio stream for $videoId")
        Log.d(TAG, "resolved $videoId via NewPipe -> ${pick.format?.suffix} ${pick.averageBitrate}kbps")
        return requireNotNull(pick.content)
    }

    private data class ClientProfile(
        val name: String,
        val version: String,
        val ua: String,
        val deviceMake: String,
        val deviceModel: String,
        val osName: String,
        val osVersion: String,
        val sdk: Int? = null,
        val clientId: String,
    )

    private val fallbackClients = listOf(
        ClientProfile(
            name = "ANDROID_VR",
            version = "1.60.19",
            ua = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            osName = "Android",
            osVersion = "12L",
            sdk = 32,
            clientId = "28",
        ),
        ClientProfile(
            name = "IOS",
            version = "19.29.1",
            ua = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            osName = "iPhone",
            osVersion = "17.5.1.21F90",
            clientId = "5",
        ),
        ClientProfile(
            name = "ANDROID_MUSIC",
            version = "7.16.53",
            ua = "com.google.android.apps.youtube.music/7.16.53 (Linux; U; Android 14; gzip)",
            deviceMake = "Google",
            deviceModel = "Pixel 8",
            osName = "Android",
            osVersion = "14",
            sdk = 34,
            clientId = "21",
        ),
    )

    private fun resolveWithInnerTubeFallbacks(videoId: String): String? {
        for (c in fallbackClients) {
            val url = runCatching { fetchInnerTubeAudioUrl(videoId, c) }
                .onFailure { Log.w(TAG, "Fallback ${c.name} failed for $videoId: ${it.message}") }
                .getOrNull()
            if (url != null) {
                Log.d(TAG, "resolved $videoId via fallback ${c.name}")
                return url
            }
        }
        return null
    }

    private fun fetchInnerTubeAudioUrl(videoId: String, c: ClientProfile): String? {
        val clientObj = JSONObject()
            .put("clientName", c.name)
            .put("clientVersion", c.version)
            .put("deviceMake", c.deviceMake)
            .put("deviceModel", c.deviceModel)
            .put("osName", c.osName)
            .put("osVersion", c.osVersion)
            .put("hl", "id")
            .put("gl", "ID")
        if (c.sdk != null) clientObj.put("androidSdkVersion", c.sdk)

        val payload = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", clientObj))

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", c.ua)
            .header("X-YouTube-Client-Name", c.clientId)
            .header("X-YouTube-Client-Version", c.version)
            .header("Origin", "https://music.youtube.com")
            .build()

        val json = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("InnerTube HTTP ${r.code}")
            JSONObject(r.body?.string() ?: throw IOException("empty body"))
        }

        val status = json.optJSONObject("playabilityStatus")?.optString("status")
        if (status != null && status != "OK") {
            throw IOException("playability=$status")
        }

        val adaptive = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            ?: return null

        var bestUrl: String? = null
        var bestScore = -1
        for (i in 0 until adaptive.length()) {
            val f = adaptive.optJSONObject(i) ?: continue
            val url = f.optString("url").takeIf { it.startsWith("http") } ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("audio/")) continue
            val bitrate = f.optInt("bitrate", 0)
            // Prefer audio/mp4 (m4a/AAC) over opus/webm for universal compatibility
            val score = bitrate + if (mime.contains("mp4")) 1_000_000 else 0
            if (score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun parseExpiryMs(url: String): Long? {
        val expSec = runCatching { Uri.parse(url).getQueryParameter("expire")?.toLongOrNull() }.getOrNull()
            ?: return null
        // Refresh 2 minutes before actual expiry
        return (expSec * 1000L - 120_000L).takeIf { it > System.currentTimeMillis() }
    }

    suspend fun resolve(videoId: String): String = withContext(Dispatchers.IO) { resolveBlocking(videoId) }

    companion object {
        private const val TAG = "MusikuStream"
        private const val DEFAULT_TTL_MS = 30 * 60 * 1000L
    }
}
