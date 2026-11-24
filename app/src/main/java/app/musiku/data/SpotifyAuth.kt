package app.musiku.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

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
 * Authenticated Spotify Web Player session backed by `sp_dc` cookie + WebView `/api/token`
 * capture (same mechanism as Convx, without external TOTP secret dictionaries).
 */
class SpotifyAuth(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) {
    private val kSpDc = stringPreferencesKey("sp_dc")
    private val kAccessToken = stringPreferencesKey("access_token")
    private val kTokenExp = longPreferencesKey("token_exp")
    private val kUserId = stringPreferencesKey("user_id")
    private val kUserName = stringPreferencesKey("user_name")
    private val kUserAvatar = stringPreferencesKey("user_avatar")

    private val _user = MutableStateFlow<SpotifyUser?>(null)
    val user: StateFlow<SpotifyUser?> = _user

    private val refreshMutex = Mutex()

    @Volatile
    private var spDc: String? = null

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenExpiresAtMs = 0L

    val isLoggedIn: Boolean get() = !spDc.isNullOrBlank()

    init {
        scope.launch {
            val p = ctx.spotifyAuthStore.data.first()
            spDc = p[kSpDc]?.takeIf { it.isNotBlank() }
            accessToken = p[kAccessToken]?.takeIf { it.isNotBlank() }
            tokenExpiresAtMs = p[kTokenExp] ?: 0L
            val id = p[kUserId]
            val name = p[kUserName]
            if (!spDc.isNullOrBlank() && !id.isNullOrBlank() && !name.isNullOrBlank()) {
                _user.value = SpotifyUser(id, name, p[kUserAvatar]?.takeIf { it.isNotBlank() })
            }
        }
    }

    /** Called after login WebView captures `sp_dc` (and optionally an already-minted token). */
    suspend fun setSession(cookie: String, capturedToken: String? = null, capturedExpMs: Long = 0L): SpotifyUser =
        withContext(Dispatchers.IO) {
            val clean = cookie.trim()
            require(clean.isNotEmpty()) { "Cookie sp_dc kosong" }
            val prevDc = spDc
            val prevToken = accessToken
            val prevExp = tokenExpiresAtMs
            spDc = clean
            accessToken = capturedToken?.takeIf { it.isNotBlank() }
            tokenExpiresAtMs = if (accessToken != null) capturedExpMs else 0L
            try {
                val token = accessToken(force = accessToken == null)
                    ?: throw IOException("Gagal mendapatkan token Spotify")
                val me = fetchMe(token)
                ctx.spotifyAuthStore.edit {
                    it[kSpDc] = clean
                    it[kAccessToken] = token
                    it[kTokenExp] = tokenExpiresAtMs
                    it[kUserId] = me.id
                    it[kUserName] = me.name
                    if (me.avatarUrl != null) it[kUserAvatar] = me.avatarUrl else it.remove(kUserAvatar)
                }
                _user.value = me
                me
            } catch (e: Exception) {
                spDc = prevDc
                accessToken = prevToken
                tokenExpiresAtMs = prevExp
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
        SpotifyPlaylist("liked", "Liked Songs (Spotify)", null, tracks)
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
            .header("User-Agent", DESKTOP_UA)
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

    private suspend fun apiGet(url: String): JSONObject {
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
            .header("User-Agent", DESKTOP_UA)
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify API HTTP ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    private suspend fun accessToken(force: Boolean): String? {
        val dc = spDc?.takeIf { it.isNotBlank() } ?: return null
        val now = System.currentTimeMillis()
        if (!force) accessToken?.let { if (now < tokenExpiresAtMs - 60_000L) return it }

        return refreshMutex.withLock {
            val nowLocked = System.currentTimeMillis()
            if (!force) accessToken?.let { if (nowLocked < tokenExpiresAtMs - 60_000L) return@withLock it }
            val (tok, exp) = withContext(Dispatchers.Main) { harvestTokenInWebView(dc) }
            accessToken = tok
            tokenExpiresAtMs = exp
            runCatching {
                ctx.spotifyAuthStore.edit {
                    it[kAccessToken] = tok
                    it[kTokenExp] = exp
                }
            }
            tok
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private suspend fun harvestTokenInWebView(dc: String): Pair<String, Long> {
        val result = CompletableDeferred<Pair<String, Long>>()
        val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
        val saved = CompletableDeferred<Boolean>()
        cookies.setCookie(ORIGIN, "sp_dc=$dc; Domain=.spotify.com; Path=/; Secure") { saved.complete(it) }
        saved.await()
        cookies.flush()
        WebStorage.getInstance().deleteOrigin(ORIGIN)

        val view = WebView(ctx)
        try {
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.userAgentString = DESKTOP_UA
            cookies.setAcceptThirdPartyCookies(view, true)
            view.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun postMessage(payload: String) {
                        if (result.isCompleted) return
                        parseTokenPayload(payload)?.let { result.complete(it) }
                    }
                },
                BRIDGE,
            )
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && (
                        request.url.scheme != "https" ||
                            request.url.host?.let { it != "open.spotify.com" && it != "accounts.spotify.com" } != false
                        )

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (Uri.parse(url.orEmpty()).host == "open.spotify.com") {
                        view.evaluateJavascript(TOKEN_HOOK, null)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (Uri.parse(url.orEmpty()).host == "open.spotify.com") {
                        view.evaluateJavascript(TOKEN_HOOK, null)
                    }
                }
            }
            view.loadUrl("$ORIGIN/")
            return withTimeoutOrNull(25_000L) { result.await() }
                ?: throw IOException("Sesi Spotify tidak merespons. Coba masuk ulang.")
        } finally {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
            result.cancel()
        }
    }

    companion object {
        private const val ORIGIN = "https://open.spotify.com"
        const val BRIDGE = "MusikuSpotifyToken"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun parseTokenPayload(payload: String, nowMs: Long = System.currentTimeMillis()): Pair<String, Long>? {
            val o = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            if (o.optBoolean("isAnonymous", true)) return null
            val token = o.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
            val exp = o.optLong("accessTokenExpirationTimestampMs", nowMs + 25 * 60_000L)
            if (exp <= nowMs) return null
            return token to exp
        }

        val TOKEN_HOOK = """
            (function() {
              if (location.origin !== '$ORIGIN' || window.__musikuSpotifyHook) return;
              window.__musikuSpotifyHook = true;
              function report(body) { try { window.$BRIDGE.postMessage(String(body)); } catch (_) {} }
              function isToken(url) { try { var u = new URL(String(url), location.href); return u.origin === '$ORIGIN' && u.pathname === '/api/token'; } catch (_) { return false; } }
              var fetchOrig = window.fetch;
              window.fetch = function(input) {
                var res = fetchOrig.apply(this, arguments);
                if (isToken(input && input.url ? input.url : input)) {
                  res.then(function(r) { r.clone().text().then(report).catch(function() {}); }).catch(function() {});
                }
                return res;
              };
              var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(m, url) { this.__musikuTokenUrl = url; return open.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function() {
                var xhr = this;
                xhr.addEventListener('load', function() { if (isToken(xhr.__musikuTokenUrl)) { try { report(xhr.responseText); } catch (_) {} } });
                return send.apply(this, arguments);
              };
              try { var s = document.getElementById('session'); if (s) report(s.textContent); } catch (_) {}
            })();
        """.trimIndent()
    }
}
