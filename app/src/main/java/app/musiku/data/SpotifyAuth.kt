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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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
 * capture, querying Spotify's Pathfinder GraphQL endpoint (`api-partner.spotify.com`) just like
 * Convx so it never hits `api.spotify.com/v1` Web Player 429 rate limits.
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

    private val _playlists = MutableStateFlow<List<SpotifyUserPlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyUserPlaylist>> = _playlists

    private val refreshMutex = Mutex()
    private val hashMutex = Mutex()
    private val hashes = ConcurrentHashMap<String, String>().apply {
        put("profileAttributes", "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0")
        put("libraryV3", "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455")
        put("fetchPlaylist", "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf")
        put("fetchLibraryTracks", "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240")
    }

    @Volatile
    private var hashesRefreshedAt = 0L

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
                runCatching { myPlaylists() }
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
                val me = runCatching { fetchMePathfinder() }
                    .getOrElse { SpotifyUser("spotify", "Pengguna Spotify", null) }
                ctx.spotifyAuthStore.edit {
                    it[kSpDc] = clean
                    it[kAccessToken] = token
                    it[kTokenExp] = tokenExpiresAtMs
                    it[kUserId] = me.id
                    it[kUserName] = me.name
                    if (me.avatarUrl != null) it[kUserAvatar] = me.avatarUrl else it.remove(kUserAvatar)
                }
                _user.value = me
                scope.launch { runCatching { myPlaylists() } }
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
        _playlists.value = emptyList()
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
        var offset = 0
        val limit = 50
        for (page in 0 until 4) {
            val vars = JSONObject()
                .put("filters", JSONArray().put("Playlists"))
                .put("order", JSONObject.NULL)
                .put("textFilter", "")
                .put("features", JSONArray().put("LIKED_SONGS").put("YOUR_EPISODES_V2").put("PRERELEASES").put("EVENTS"))
                .put("limit", limit)
                .put("offset", offset)
                .put("flatten", true)
                .put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL)
                .put("includeFoldersWhenFlattening", false)

            val res = graphqlPost("libraryV3", vars)
            val lib = res.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("libraryV3")
                ?: return@withContext out
            val items = lib.optJSONArray("items") ?: return@withContext out
            if (items.length() == 0) return@withContext out

            for (i in 0 until items.length()) {
                val wrapper = items.optJSONObject(i)?.optJSONObject("item") ?: continue
                if (wrapper.optString("__typename") != "PlaylistResponseWrapper") continue
                val data = wrapper.optJSONObject("data") ?: continue
                val uri = wrapper.optString("_uri").ifBlank { data.optString("uri") }
                val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: continue
                val name = data.optString("name").takeIf { it.isNotBlank() } ?: "Playlist"
                val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")?.takeIf { it.isNotBlank() }
                val cover = data.optJSONObject("images")?.optJSONArray("items")
                    ?.optJSONObject(0)?.optJSONArray("sources")
                    ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
                val count = data.optJSONObject("content")?.optInt("totalCount", -1)?.takeIf { it >= 0 }
                    ?: data.optJSONObject("tracks")?.optInt("totalCount", 0)
                    ?: 0
                out += SpotifyUserPlaylist(id, name, owner, cover, count, isPublic = true)
            }
            if (items.length() < limit) break
            offset += limit
        }
        _playlists.value = out
        out
    }

    suspend fun playlistTracks(playlistId: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        var name = "Playlist Spotify"
        var cover: String? = null
        val tracks = ArrayList<SpotifyTrack>()
        var offset = 0
        val limit = 100
        for (page in 0 until 5) {
            val vars = JSONObject()
                .put("uri", "spotify:playlist:$playlistId")
                .put("offset", offset)
                .put("limit", limit)
                .put("enableWatchFeedEntrypoint", false)
            val res = graphqlPost("fetchPlaylist", vars)
            val pl = res.optJSONObject("data")?.optJSONObject("playlistV2") ?: break
            if (page == 0) {
                pl.optString("name").takeIf { it.isNotBlank() }?.let { name = it }
                cover = pl.optJSONObject("images")?.optJSONArray("items")
                    ?.optJSONObject(0)?.optJSONArray("sources")
                    ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
            }
            val items = pl.optJSONObject("content")?.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val wrapper = items.optJSONObject(i)?.optJSONObject("itemV2") ?: continue
                val data = wrapper.optJSONObject("data") ?: continue
                val uriOverride = wrapper.optString("_uri").ifBlank { wrapper.optString("uri") }.takeIf { it.isNotBlank() }
                parseGqlTrack(data, uriOverride)?.let(tracks::add)
            }
            if (items.length() < limit) break
            offset += limit
        }
        if (tracks.isEmpty()) throw IOException("Playlist kosong atau tidak bisa dibaca.")
        SpotifyPlaylist(playlistId, name, cover, tracks)
    }

    suspend fun likedTracks(): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val tracks = ArrayList<SpotifyTrack>()
        var offset = 0
        val limit = 50
        for (page in 0 until 6) {
            val vars = JSONObject().put("offset", offset).put("limit", limit)
            val res = graphqlPost("fetchLibraryTracks", vars)
            val items = res.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("library")
                ?.optJSONObject("tracks")?.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val wrapper = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                val data = wrapper.optJSONObject("data") ?: continue
                val uriOverride = wrapper.optString("_uri").ifBlank { wrapper.optString("uri") }.takeIf { it.isNotBlank() }
                parseGqlTrack(data, uriOverride)?.let(tracks::add)
            }
            if (items.length() < limit) break
            offset += limit
        }
        if (tracks.isEmpty()) throw IOException("Belum ada lagu yang disukai di Spotify.")
        SpotifyPlaylist("liked", "Liked Songs (Spotify)", null, tracks)
    }

    private fun parseGqlTrack(data: JSONObject, uriOverride: String?): SpotifyTrack? {
        val uri = uriOverride ?: data.optString("uri").takeIf { it.isNotBlank() }
            ?: data.optString("_uri").takeIf { it.isNotBlank() } ?: return null
        if (!uri.startsWith("spotify:track:")) return null
        val title = data.optString("name").takeIf { it.isNotBlank() } ?: return null
        val artistsArr = data.optJSONObject("artists")?.optJSONArray("items")
        val artist = if (artistsArr == null) null else (0 until artistsArr.length())
            .mapNotNull { artistsArr.optJSONObject(it)?.optJSONObject("profile")?.optString("name")?.takeIf { n -> n.isNotBlank() } }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
        val durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
            ?.takeIf { it > 0L }
            ?: data.optLong("durationMs", 0L)
        return SpotifyTrack(uri, title, artist, durationMs)
    }

    private suspend fun fetchMePathfinder(): SpotifyUser {
        val res = graphqlPost("profileAttributes", JSONObject())
        val profile = res.optJSONObject("data")?.optJSONObject("me")?.optJSONObject("profile")
            ?: throw IOException("Profil Spotify tidak tersedia")
        val uri = profile.optString("uri")
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: "spotify"
        val name = profile.optString("name").takeIf { it.isNotBlank() } ?: id
        val avatar = profile.optJSONObject("avatar")?.optJSONArray("sources")
            ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
        return SpotifyUser(id, name, avatar)
    }

    private suspend fun graphqlPost(operation: String, variables: JSONObject): JSONObject {
        val token = accessToken(force = false) ?: accessToken(force = true)
            ?: throw IOException("Belum login Spotify")
        val hash = hashes[operation] ?: throw IOException("Hash $operation tidak ditemukan")
        val first = runCatching { executeGql(operation, hash, variables, token) }
        val res = first.getOrElse { err ->
            if (err.message?.contains("401") == true) {
                val fresh = accessToken(force = true) ?: throw err
                executeGql(operation, hash, variables, fresh)
            } else {
                throw err
            }
        }
        val errors = res.optJSONArray("errors")
        val msg = errors?.optJSONObject(0)?.optString("message").orEmpty()
        if (msg.contains("PersistedQueryNotFound", ignoreCase = true)) {
            refreshHashesFromWebPlayer()
            val newHash = hashes[operation] ?: hash
            return executeGql(operation, newHash, variables, accessToken(force = false) ?: token)
        }
        if (errors != null && errors.length() > 0 && res.optJSONObject("data") == null) {
            throw IOException(msg.ifBlank { "Spotify GraphQL error" })
        }
        return res
    }

    private fun executeGql(operation: String, sha256: String, variables: JSONObject, token: String): JSONObject {
        val body = JSONObject()
            .put("variables", variables)
            .put("operationName", operation)
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", sha256),
                ),
            )
        val req = Request.Builder()
            .url(GQL_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("app-platform", "WebPlayer")
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("User-Agent", DESKTOP_UA)
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Spotify Pathfinder HTTP ${r.code}")
            return JSONObject(r.body?.string().orEmpty())
        }
    }

    private suspend fun refreshHashesFromWebPlayer() = hashMutex.withLock {
        if (System.currentTimeMillis() - hashesRefreshedAt < 60_000L) return@withLock
        runCatching {
            val page = httpGet("$ORIGIN/")
            val bundleUrl = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/web-player\.[a-f0-9]+\.js""")
                .find(page)?.value ?: return@runCatching
            val script = httpGet(bundleUrl)
            for (op in hashes.keys.toList()) {
                val found = Regex("""["']${Regex.escape(op)}["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
                    .find(script)?.groupValues?.get(1)
                if (found != null) hashes[op] = found
            }
            hashesRefreshedAt = System.currentTimeMillis()
        }
    }

    private fun httpGet(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
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
        private const val GQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query"
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
