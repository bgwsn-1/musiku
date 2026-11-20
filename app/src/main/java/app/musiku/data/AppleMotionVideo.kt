package app.musiku.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist motion artwork from Apple Music's catalog — the same `editorialVideo` assets Convx uses.
 *
 * There is no public API, so this mirrors the web player: scrape an `AMPWebPlay` token from the
 * music.apple.com web bundle, search artists, then ask for one artist's editorial video. The result
 * is an HLS playlist (media3's ExoPlayer plays it without another dependency). Tokens are cached on
 * disk, lookups in memory. Every failure is swallowed: the caller keeps the static hero.
 */
class AppleMotionVideo(private val ctx: Context, private val http: OkHttpClient) {

    private class Hit(val url: String, val expiresAtMs: Long)

    private val hits = ConcurrentHashMap<String, Hit>()
    private val misses = ConcurrentHashMap.newKeySet<String>()
    private val fetch = Mutex()

    suspend fun lookup(artist: String): String? = withContext(Dispatchers.IO) {
        val key = normalize(artist)
        if (key.isEmpty() || key in misses) return@withContext null
        hits[key]?.let { hit ->
            if (hit.expiresAtMs > System.currentTimeMillis()) return@withContext hit.url
            hits.remove(key)
        }
        fetch.withLock {
            if (key in misses) return@withLock null
            hits[key]?.let { hit ->
                if (hit.expiresAtMs > System.currentTimeMillis()) return@withLock hit.url
                hits.remove(key)
            }
            val url = searchAndFetch(artist)
            if (url == null) {
                misses.add(key)
            } else {
                hits[key] = Hit(url, System.currentTimeMillis() + HIT_TTL_MS)
            }
            url
        }
    }

    private suspend fun searchAndFetch(artist: String): String? {
        val root = request(
            "/v1/catalog/$STOREFRONT/search",
            "term" to artist, "types" to "artists", "limit" to "3",
        ) ?: return null
        val data = root.optJSONObject("results")?.optJSONObject("artists")?.optJSONArray("data")
            ?: return null
        val scored = (0 until data.length()).mapNotNull { i ->
            val item = data.optJSONObject(i) ?: return@mapNotNull null
            val name = item.optJSONObject("attributes")?.optString("name").orEmpty()
            if (!name.contains(artist, true) && !artist.contains(name, true)) return@mapNotNull null
            val score = if (name.equals(artist, true)) 10 else 5
            score to item
        }.sortedByDescending { it.first }
        for ((score, item) in scored) {
            if (score < 4) continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            fetchById(id)?.let { return it }
        }
        return null
    }

    private suspend fun fetchById(id: String): String? {
        val root = request(
            "/v1/catalog/$STOREFRONT/artists/$id",
            "extend" to "editorialVideo,editorialArtwork",
        ) ?: return null
        val attrs = root.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("attributes")
            ?: return null
        return attrs.optJSONObject("editorialVideo")?.let(::extractVideo)
            ?: attrs.optJSONObject("editorialArtwork")?.let(::extractVideo)
    }

    /** Square first: the artist hero is a square crop, then any other motion variant. */
    private fun extractVideo(section: JSONObject): String? {
        for (key in PREFERRED_KEYS) {
            val url = section.optJSONObject(key)?.optString("video").orEmpty().trim()
            if (url.startsWith("http")) return url
        }
        for (key in section.keys()) {
            val url = section.optJSONObject(key)?.optString("video").orEmpty().trim()
            if (url.startsWith("http")) return url
        }
        return null
    }

    // ── catalog transport ────────────────────────────────────────────────────

    private suspend fun request(
        path: String,
        vararg params: Pair<String, String>,
    ): JSONObject? = runCatching {
        var token = token()
        if (token.isEmpty()) return@runCatching null
        var response = execute(token, path, params)
        if (response.first == 401 || response.first == 403) {
            invalidate(token)
            token = token()
            if (token.isEmpty()) return@runCatching null
            response = execute(token, path, params)
        }
        if (response.first != 200) return@runCatching null
        JSONObject(response.second)
    }.getOrNull()

    private fun execute(
        token: String,
        path: String,
        params: Array<out Pair<String, String>>,
    ): Pair<Int, String> {
        val url = (AMP + path).toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", USER_AGENT)
            .build()
        return http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
    }

    // ── web player token ─────────────────────────────────────────────────────

    private val tokenMutex = Mutex()

    @Volatile
    private var token: String = ""

    @Volatile
    private var rejectedToken: String? = null

    @Volatile
    private var nextScrapeAt = 0L

    @Volatile
    private var diskChecked = false

    private suspend fun token(): String {
        if (!isExpired(token)) return token
        return tokenMutex.withLock {
            if (!isExpired(token)) return@withLock token
            if (!diskChecked) {
                diskChecked = true
                readSaved()?.let { token = it }
                if (!isExpired(token)) return@withLock token
            }
            if (System.currentTimeMillis() >= nextScrapeAt) {
                val fresh = scrape()
                if (fresh != null) {
                    token = fresh
                    save(fresh)
                } else {
                    nextScrapeAt = System.currentTimeMillis() + SCRAPE_BACKOFF_MS
                }
            }
            token.takeUnless { isExpired(it) }.orEmpty()
        }
    }

    private fun invalidate(rejected: String) {
        rejectedToken = rejected
        if (token == rejected) {
            token = ""
            // Do not restore the same rejected token from disk during renewal.
            diskChecked = true
        }
    }

    private fun tokenFile(): File = File(ctx.filesDir, "apple_webplay.token")

    private fun readSaved(): String? = runCatching {
        tokenFile().takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { isWebPlayToken(it) && !isExpired(it) }
    }.getOrNull()

    private fun save(value: String) {
        runCatching { tokenFile().apply { parentFile?.mkdirs() }.writeText(value) }
    }

    private suspend fun scrape(): String? = runCatching {
        val html = fetchText("https://music.apple.com/us/browse") ?: return@runCatching null
        envMeta.find(html)?.groupValues?.get(1)
            ?.let { pickWebPlayToken(URLDecoder.decode(it, "UTF-8")) }
            ?.let { return@runCatching it }
        // Modern bundle first: the legacy one is larger and only needed if the token moves there.
        val assets = indexJs.findAll(html).map { it.value }.distinct().sortedBy { "legacy" in it }
        assets.firstNotNullOfOrNull { asset ->
            fetchText("https://music.apple.com$asset")?.let(::pickWebPlayToken)
        }
    }.getOrNull()

    private fun fetchText(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return http.newCall(req).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    private fun pickWebPlayToken(text: String): String? =
        jwt.findAll(text).map { it.value }.firstOrNull {
            it != rejectedToken && isWebPlayToken(it) && !isExpired(it)
        }

    private fun payload(jwtToken: String): String? = runCatching {
        val part = jwtToken.split('.').getOrNull(1) ?: return@runCatching null
        val padded = part.padEnd((part.length + 3) / 4 * 4, '=')
        String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
    }.getOrNull()

    private fun isWebPlayToken(jwtToken: String): Boolean =
        payload(jwtToken)?.let { issuerClaim.find(it)?.groupValues?.get(1) } == WEB_PLAY_ISSUER

    /** JWT `exp` with a day of margin. Unparseable counts as expired. */
    private fun isExpired(jwtToken: String): Boolean {
        val exp = payload(jwtToken)?.let { expirationClaim.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?: return true
        return exp - 86_400 <= System.currentTimeMillis() / 1000
    }

    private fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT)

    private companion object {
        const val AMP = "https://amp-api.music.apple.com"
        const val STOREFRONT = "us"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        const val HIT_TTL_MS = 24L * 60 * 60 * 1000
        const val SCRAPE_BACKOFF_MS = 10L * 60 * 1000
        const val WEB_PLAY_ISSUER = "AMPWebPlay"

        val PREFERRED_KEYS = listOf(
            "motionArtistSquare1x1",
            "motionArtistFullscreen16x9",
            "motionArtistWide16x9",
            "motionDetailRaw",
            "motionDetailTall",
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionTallVideo3x4",
        )
        val jwt = Regex("""eyJ[\w-]+\.eyJ[\w-]+\.[\w-]+""")
        val issuerClaim = Regex(""""iss"\s*:\s*"([^"]+)"""")
        val expirationClaim = Regex(""""exp"\s*:\s*(\d+)"""")
        val envMeta = Regex("""name="desktop-music-app/config/environment"\s+content="([^"]+)"""")
        val indexJs = Regex("""/assets/index[\w~.-]*\.js""")
    }
}
