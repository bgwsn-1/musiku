package app.musiku.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val Context.spotifyAuthStore by preferencesDataStore(name = "spotify_auth")

data class SpotifyUser(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

data class SpotifyUserPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val cover: String?,
    val trackCount: Int,
    val isPublic: Boolean,
)

/**
 * Authenticated Spotify Web Player session backed by the `sp_dc` cookie obtained from WebView
 * login. Unlocks private playlists, >100-track playlists, and Liked Songs without needing a
 * Spotify Developer Client ID or Premium playback.
 */
class SpotifyAuth(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) {
    private val kSpDc = stringPreferencesKey("sp_dc")
    private val kUserId = stringPreferencesKey("user_id")
    private val kUserName = stringPreferencesKey("user_name")
    private val kUserAvatar = stringPreferencesKey("user_avatar")

    private val _user = MutableStateFlow<SpotifyUser?>(null)
    val user: StateFlow<SpotifyUser?> = _user

    @Volatile
    private var spDc: String? = null

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtMs = 0L

    @Volatile
    private var totpKey: ByteArray? = null

    @Volatile
    private var totpVersion: String? = null

    @Volatile
    private var secretsFetchedAt = 0L

    val isLoggedIn: Boolean get() = !spDc.isNullOrBlank()

    init {
        scope.launch {
            val p = ctx.spotifyAuthStore.data.first()
            spDc = p[kSpDc]?.takeIf { it.isNotBlank() }
            val id = p[kUserId]
            val name = p[kUserName]
            if (!spDc.isNullOrBlank() && !id.isNullOrBlank() && !name.isNullOrBlank()) {
                _user.value = SpotifyUser(id, name, p[kUserAvatar]?.takeIf { it.isNotBlank() })
            }
        }
    }

    suspend fun setCookie(cookie: String): SpotifyUser = withContext(Dispatchers.IO) {
        val clean = cookie.trim()
        require(clean.isNotEmpty()) { "Cookie sp_dc kosong" }
        val prevDc = spDc
        spDc = clean
        accessToken = null
        tokenExpiresAtMs = 0L
        try {
            val token = accessToken(force = true) ?: throw IOException("Gagal mendapatkan token Spotify")
            val me = fetchMe(token)
            ctx.spotifyAuthStore.edit {
                it[kSpDc] = clean
                it[kUserId] = me.id
                it[kUserName] = me.name
                if (me.avatarUrl != null) it[kUserAvatar] = me.avatarUrl else it.remove(kUserAvatar)
            }
            _user.value = me
            me
        } catch (e: Exception) {
            spDc = prevDc
            accessToken = null
            tokenExpiresAtMs = 0L
            throw e
        }
    }

    fun logout() {
        spDc = null
        accessToken = null
        tokenExpiresAtMs = 0L
        _user.value = null
        scope.launch {
            runCatching { ctx.spotifyAuthStore.edit { it.clear() } }
            runCatching {
                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
            }
        }
    }

    suspend fun myPlaylists(): List<SpotifyUserPlaylist> = withContext(Dispatchers.IO) {
        val out = ArrayList<SpotifyUserPlaylist>()
        var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        var pages = 0
        while (nextUrl != null && pages < 4) {
            val json = apiGet(nextUrl)
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val p = items.optJSONObject(i) ?: continue
                val id = p.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = p.optString("name").takeIf { it.isNotBlank() } ?: "Playlist"
                val owner = p.optJSONObject("owner")?.optString("display_name")?.takeIf { it.isNotBlank() }
                val images = p.optJSONArray("images")
                val cover = images?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
                val count = p.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                val isPublic = p.optBoolean("public", true)
                out += SpotifyUserPlaylist(id, name, owner, cover, count, isPublic)
            }
            nextUrl = json.optString("next").takeIf { it.startsWith("https://") }
            pages++
        }
        out
    }

    suspend fun playlistTracks(playlistId: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val meta = apiGet("https://api.spotify.com/v1/playlists/$playlistId?fields=id,name,images")
        val name = meta.optString("name").takeIf { it.isNotBlank() } ?: "Playlist Spotify"
        val cover = meta.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
        val tracks = ArrayList<SpotifyTrack>()
        var nextUrl: String? = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100"
        var pages = 0
        while (nextUrl != null && pages < 5) {
            val json = apiGet(nextUrl)
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val t = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                parseApiTrack(t)?.let(tracks::add)
            }
            nextUrl = json.optString("next").takeIf { it.startsWith("https://") }
            pages++
        }
        if (tracks.isEmpty()) throw IOException("Playlist kosong atau tidak bisa dibaca.")
        SpotifyPlaylist(playlistId, name, cover, tracks)
    }

    suspend fun likedTracks(): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val tracks = ArrayList<SpotifyTrack>()
        var nextUrl: String? = "https://api.spotify.com/v1/me/tracks?limit=50"
        var pages = 0
        while (nextUrl != null && pages < 6) {
            val json = apiGet(nextUrl)
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val t = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                parseApiTrack(t)?.let(tracks::add)
            }
            nextUrl = json.optString("next").takeIf { it.startsWith("https://") }
            pages++
        }
        if (tracks.isEmpty()) throw IOException("Belum ada lagu yang disukai di Spotify.")
        SpotifyPlaylist("liked", "Liked Songs (Spotify)", tracks.firstOrNull()?.let { null }, tracks)
    }

    private fun parseApiTrack(t: JSONObject): SpotifyTrack? {
        val uri = t.optString("uri").takeIf { it.startsWith("spotify:track:") } ?: return null
        val title = t.optString("name").takeIf { it.isNotBlank() } ?: return null
        val artistsArr = t.optJSONArray("artists")
        val artist = if (artistsArr == null) null else (0 until artistsArr.length())
            .mapNotNull { artistsArr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        val durationMs = t.optLong("duration_ms", 0L)
        return SpotifyTrack(uri, title, artist, durationMs)
    }

    private fun fetchMe(token: String): SpotifyUser {
        val req = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", UA)
            .build()
        val json = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify /me HTTP ${r.code}")
            JSONObject(r.body?.string().orEmpty())
        }
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: "spotify"
        val name = json.optString("display_name").takeIf { it.isNotBlank() } ?: id
        val avatar = json.optJSONArray("images")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
        return SpotifyUser(id, name, avatar)
    }

    private fun apiGet(url: String): JSONObject {
        val firstToken = accessToken(force = false) ?: accessToken(force = true)
            ?: throw IOException("Belum login Spotify")
        return runCatching { callApi(url, firstToken) }.getOrElse { err ->
            val fresh = accessToken(force = true) ?: throw err
            callApi(url, fresh)
        }
    }

    private fun callApi(url: String, token: String): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify API HTTP ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    private fun accessToken(force: Boolean): String? {
        val dc = spDc?.takeIf { it.isNotBlank() } ?: return null
        val now = System.currentTimeMillis()
        if (!force) accessToken?.let { if (now < tokenExpiresAtMs - 60_000L) return it }

        ensureSecrets(force)
        val key = totpKey ?: return null
        val version = totpVersion ?: return null

        val serverSeconds = runCatching {
            JSONObject(get("https://open.spotify.com/api/server-time")).optLong("serverTime")
        }.getOrNull()?.takeIf { it > 0L } ?: (now / 1000L)

        val url = "https://open.spotify.com/api/token".toHttpUrl().newBuilder()
            .addQueryParameter("reason", "init")
            .addQueryParameter("productType", "web_player")
            .addQueryParameter("totp", totp(now / 1000L, key))
            .addQueryParameter("totpVer", version)
            .addQueryParameter("totpServer", totp(serverSeconds, key))
            .build()

        val req = Request.Builder()
            .url(url)
            .header("Cookie", "sp_dc=$dc")
            .header("User-Agent", UA)
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .build()

        val o = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Token Spotify HTTP ${r.code}")
            JSONObject(r.body?.string().orEmpty())
        }
        if (o.optBoolean("isAnonymous", true)) {
            throw IOException("Sesi Spotify kedaluwarsa, silakan login ulang.")
        }
        val token = o.optString("accessToken").ifEmpty { return null }
        accessToken = token
        tokenExpiresAtMs = o.optLong("accessTokenExpirationTimestampMs", now + 25 * 60_000L)
        return token
    }

    private fun ensureSecrets(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && totpKey != null && now - secretsFetchedAt < SECRETS_TTL_MS) return
        val o = JSONObject(get(SECRETS_URL))
        val newest = o.keys().asSequence().maxByOrNull { it.toIntOrNull() ?: 0 }
            ?: throw IOException("empty secret dictionary")
        val data = o.optJSONArray(newest) ?: throw IOException("bad secret dictionary")
        val key = StringBuilder()
        for (i in 0 until data.length()) key.append(data.optInt(i) xor ((i % 33) + 9))
        totpKey = key.toString().toByteArray(Charsets.UTF_8)
        totpVersion = newest
        secretsFetchedAt = now
    }

    private fun totp(seconds: Long, key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
        var counter = seconds / 30
        val message = ByteArray(8)
        for (i in 7 downTo 0) {
            message[i] = (counter and 0xff).toByte()
            counter = counter shr 8
        }
        val h = mac.doFinal(message)
        val offset = h[19].toInt() and 0x0f
        val code = ((h[offset].toInt() and 0x7f) shl 24) or
            ((h[offset + 1].toInt() and 0xff) shl 16) or
            ((h[offset + 2].toInt() and 0xff) shl 8) or
            (h[offset + 3].toInt() and 0xff)
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    companion object {
        private const val SECRETS_URL =
            "https://raw.githubusercontent.com/Thereallo1026/spotify-secrets/main/secrets/secretDict.json"
        private const val SECRETS_TTL_MS = 6 * 60 * 60 * 1000L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
