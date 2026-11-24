package app.musiku.data

import android.util.LruCache
import app.musiku.model.AlbumDetail
import app.musiku.model.AlbumSummary
import app.musiku.model.ArtistCard
import app.musiku.model.ArtistDetail
import app.musiku.model.BrowseCard
import app.musiku.model.BrowseShelf
import app.musiku.model.GenreItem
import app.musiku.model.HomeChip
import app.musiku.model.HomeItem
import app.musiku.model.HomeShelf
import app.musiku.model.OnlinePlaylist
import app.musiku.model.SearchResults
import app.musiku.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube Music metadata over the same InnerTube endpoints the web build reaches through
 * `ytmusic-api` (lib/music.ts). Parsing is deliberately structural: instead of following
 * one fixed path it looks for the renderer objects wherever they sit, so small layout
 * changes on YouTube's side don't break every screen.
 */
class YtMusic(private val http: OkHttpClient) {

    private val cache = LruCache<String, Pair<Long, Any>>(128)

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, block: suspend () -> T): T {
        val hit = cache.get(key)
        if (hit != null) {
            if (hit.first > System.currentTimeMillis()) return hit.second as T
            cache.remove(key)
        }
        val v = block()
        // never cache an empty home/shelf payload: YouTube occasionally answers a cold request with
        // only the onboarding shelf, and caching that makes the app look broken for the whole TTL
        val empty = (v is Collection<*>) && v.isEmpty()
        if (!empty) cache.put(key, System.currentTimeMillis() + TTL to v)
        return v
    }

    // ── transport ────────────────────────────────────────────────────────────

    private fun context(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", HL)
            .put("gl", GL),
    )

    private suspend fun post(
        endpoint: String,
        body: JSONObject,
        query: String = "",
        visitorData: String? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            body.put("context", context())
            val url = "https://music.youtube.com/youtubei/v1/$endpoint?alt=json&key=$KEY$query"
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", UA)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                .apply { if (visitorData != null) header("X-Goog-Visitor-Id", visitorData) }
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("YouTube Music HTTP ${r.code}")
                JSONObject(r.body?.string() ?: throw IOException("empty body"))
            }
        }

    // ── shared parsing ───────────────────────────────────────────────────────

    private fun upscale(url: String, width: Int): String {
        val target = width.coerceIn(544, 1200)
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            val re = Regex("=\\w[\\w-]*!?$")
            return if (re.containsMatchIn(url)) url.replace(re, "=w$target-h$target-l90-rj")
            else "$url=w$target-h$target-l90-rj"
        }
        if (url.contains("ytimg.com")) {
            if (url.contains("/hqdefault.jpg")) return url.replace("/hqdefault.jpg", "/maxresdefault.jpg")
            return url.replace(Regex("/(mq|sd[a-z]*)default\\.jpg\\b"), "/hqdefault.jpg")
        }
        return url
    }

    private fun thumbOf(node: Any?): String? {
        val arr = node.findArray("thumbnails") ?: return null
        var best: JSONObject? = null
        var bestW = -1
        for (o in arr.objects()) {
            val w = o.optInt("width", 0)
            if (w >= bestW && o.optString("url").isNotEmpty()) {
                best = o; bestW = w
            }
        }
        val url = best?.optString("url") ?: return null
        return upscale(url, bestW.coerceAtLeast(0))
    }

    private fun pageType(run: JSONObject): String? =
        run.str("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")

    private fun browseId(run: JSONObject): String? = run.str("navigationEndpoint.browseEndpoint.browseId")

    private val durationRe = Regex("^\\d{1,2}(:\\d{2}){1,2}$")
    private val typeWords = setOf("song", "lagu", "video", "single", "album", "ep", "playlist", "artist", "artis")

    private fun isSep(t: String) = t.trim() == "•" || t.trim() == "·"

    private fun flexRuns(r: JSONObject, col: Int): List<JSONObject> =
        r.obj("flexColumns.$col.musicResponsiveListItemFlexColumnRenderer.text").runs()

    private fun videoIdOf(r: JSONObject): String? =
        r.str("playlistItemData.videoId")
            ?: r.str("overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint.videoId")
            ?: flexRuns(r, 0).firstNotNullOfOrNull { it.str("navigationEndpoint.watchEndpoint.videoId") }

    private class Meta(var artist: String? = null, var artistId: String? = null, var album: String? = null, var albumId: String? = null, var duration: String? = null, var year: String? = null)

    /** Classify the "Artist • Album • 3:45" style run list by link type and shape. */
    private fun readMeta(runs: List<JSONObject>): Meta {
        val m = Meta()
        for (run in runs) {
            val t = run.optString("text")
            if (t.isBlank() || isSep(t)) continue
            when (pageType(run)) {
                "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> if (m.artist == null) {
                    m.artist = t; m.artistId = browseId(run)
                }
                "MUSIC_PAGE_TYPE_ALBUM" -> if (m.album == null) {
                    m.album = t; m.albumId = browseId(run)
                }
                else -> when {
                    durationRe.matches(t.trim()) -> m.duration = t.trim()
                    Regex("^\\d{4}$").matches(t.trim()) -> m.year = t.trim()
                    t.trim().lowercase() in typeWords -> Unit
                    m.artist == null && !t.contains("views", true) && !t.contains("x ditonton", true) -> m.artist = t.trim()
                }
            }
        }
        return m
    }

    private fun songFrom(r: JSONObject, fallbackArtist: String? = null, fallbackThumb: String? = null): Track? {
        val id = videoIdOf(r) ?: return null
        val title = flexRuns(r, 0).firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() } ?: return null
        val meta = readMeta(flexRuns(r, 1))
        val fixedDur = r.obj("fixedColumns.0.musicResponsiveListItemFixedColumnRenderer.text").runs()
            .firstOrNull()?.optString("text")?.takeIf { durationRe.matches(it.trim()) }
        // album tracks put the album/plays in column 2
        val col2 = readMeta(flexRuns(r, 2))
        return Track(
            videoId = id,
            title = title,
            artist = meta.artist ?: fallbackArtist,
            album = meta.album ?: col2.album,
            albumId = meta.albumId ?: col2.albumId,
            artistId = meta.artistId,
            thumbs = thumbOf(r.obj("thumbnail")) ?: fallbackThumb,
            duration = meta.duration ?: fixedDur,
        )
    }

    private fun albumFromRow(r: JSONObject): AlbumSummary? {
        val id = r.str("navigationEndpoint.browseEndpoint.browseId") ?: return null
        val title = flexRuns(r, 0).firstOrNull()?.optString("text") ?: return null
        val meta = readMeta(flexRuns(r, 1))
        return AlbumSummary(id, title, meta.artist, meta.year, thumbOf(r.obj("thumbnail")))
    }

    private fun artistFromRow(r: JSONObject): ArtistCard? {
        val id = r.str("navigationEndpoint.browseEndpoint.browseId") ?: return null
        if (!id.startsWith("UC")) return null
        val name = flexRuns(r, 0).firstOrNull()?.optString("text") ?: return null
        return ArtistCard(id, name, thumbOf(r.obj("thumbnail")))
    }

    // ── search ───────────────────────────────────────────────────────────────

    private enum class F(val params: String) {
        Songs("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
        Videos("EgWKAQIQAWoMEA4QChADEAQQCRAF"),
        Albums("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
        Artists("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    }

    private suspend fun searchRaw(q: String, f: F): List<JSONObject> =
        cached("search:${f.name}:$q") {
            post("search", JSONObject().put("query", q).put("params", f.params))
                .collectAll("musicResponsiveListItemRenderer")
        }

    /**
     * Songs and videos only. The Spotify import scores candidates on their own, so it has no use
     * for the album/artist columns — skipping those two endpoints halves the requests per track.
     */
    suspend fun searchTracks(q: String): List<Track> = coroutineScope {
        val songs = async { runCatching { searchRaw(q, F.Songs) }.getOrDefault(emptyList()) }
        val videos = async { runCatching { searchRaw(q, F.Videos) }.getOrDefault(emptyList()) }
        (songs.await().mapNotNull { songFrom(it) } + videos.await().mapNotNull { songFrom(it) })
            .distinctBy { it.videoId }
    }

    suspend fun search(q: String): SearchResults = coroutineScope {
        val songs = async { runCatching { searchRaw(q, F.Songs) }.getOrDefault(emptyList()) }
        val videos = async { runCatching { searchRaw(q, F.Videos) }.getOrDefault(emptyList()) }
        val albums = async { runCatching { searchRaw(q, F.Albums) }.getOrDefault(emptyList()) }
        val artists = async { runCatching { searchRaw(q, F.Artists) }.getOrDefault(emptyList()) }
        SearchResults(
            songs = songs.await().mapNotNull { songFrom(it) }.take(20),
            videos = videos.await().mapNotNull { songFrom(it) }.take(10),
            albums = albums.await().mapNotNull { albumFromRow(it) }.take(12),
            artists = artists.await().mapNotNull { artistFromRow(it) }.take(8),
        )
    }

    // ── browse: moods & genres ───────────────────────────────────────────────

    /** Every card of YouTube Music's moods & genres grid, with its tile colour. */
    suspend fun genres(): List<GenreItem> = cached("genres") {
        val root = post("browse", JSONObject().put("browseId", "FEmusic_moods_and_genres"))
        root.collectAll("musicNavigationButtonRenderer").mapNotNull { r ->
            val title = r.obj("buttonText").runs().firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val color = (r.at("solid.leftStripeColor") as? Number)?.toLong() ?: return@mapNotNull null
            val be = r.obj("clickCommand.browseEndpoint") ?: return@mapNotNull null
            val id = be.str("browseId") ?: return@mapNotNull null
            GenreItem(title, color, id, be.str("params").orEmpty())
        }.distinctBy { it.browseId + it.params }
    }

    /** A card on a category page: playlists, artists, albums and the odd song. */
    private fun browseCard(r: JSONObject): BrowseCard? {
        val title = r.obj("title").runs().joinToString("") { it.optString("text") }.takeIf { it.isNotBlank() }
            ?: return null
        val subtitle = r.obj("subtitle").runs()
            .map { it.optString("text") }
            .filter { it.isNotBlank() && !isSep(it) }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
        val be = r.obj("navigationEndpoint.browseEndpoint")
        return BrowseCard(
            title = title,
            subtitle = subtitle,
            thumbs = thumbOf(r.obj("thumbnailRenderer")),
            browseId = be?.str("browseId"),
            videoId = r.str("navigationEndpoint.watchEndpoint.videoId"),
            pageType = be?.str("browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType"),
        )
    }

    suspend fun category(browseId: String, params: String): List<BrowseShelf> =
        cached("category:$browseId:$params") {
            val root = post("browse", JSONObject().put("browseId", browseId).put("params", params))
            root.collectAll("musicCarouselShelfRenderer").mapNotNull { shelf ->
                val title = shelf.obj("header.musicCarouselShelfBasicHeaderRenderer.title").runs()
                    .joinToString("") { it.optString("text") }
                val cards = shelf.arr("contents").objects().mapNotNull { c ->
                    c.obj("musicTwoRowItemRenderer")?.let(::browseCard)
                        ?: c.obj("musicResponsiveListItemRenderer")?.let { row ->
                            songFrom(row)?.let { t ->
                                BrowseCard(
                                    title = t.title,
                                    subtitle = t.artist,
                                    thumbs = t.thumbs,
                                    videoId = t.videoId,
                                    pageType = "MUSIC_PAGE_TYPE_SONG",
                                )
                            }
                        }
                }
                if (cards.isEmpty()) null else BrowseShelf(title, cards)
            }
        }

    /** Convx borrows the lead artwork of each category page for its tile. */
    suspend fun genreArt(item: GenreItem): String? = runCatching {
        val cards = category(item.browseId, item.params).flatMap { it.cards }
        (cards.firstOrNull { it.pageType == "MUSIC_PAGE_TYPE_ARTIST" } ?: cards.firstOrNull())?.thumbs
    }.getOrNull()

    suspend fun playlist(browseId: String): OnlinePlaylist = cached("playlist:$browseId") {
        val root = post("browse", JSONObject().put("browseId", browseId))
        val header = root.findFirst(
            "musicResponsiveHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicEditablePlaylistDetailHeaderRenderer",
        )
        val title = header.obj("title").runs().firstOrNull()?.optString("text").orEmpty()
        val subtitle = header.obj("subtitle").runs()
            .map { it.optString("text") }
            .filter { it.isNotBlank() && !isSep(it) && !it.equals("Playlist", true) }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
        val tracks = root.collectAll("musicResponsiveListItemRenderer")
            .mapNotNull { songFrom(it) }
            .distinctBy { it.videoId }
        OnlinePlaylist(
            id = browseId,
            title = title,
            subtitle = subtitle,
            thumbs = thumbOf(header?.opt("thumbnail") ?: header),
            tracks = tracks,
        )
    }

    // ── home ─────────────────────────────────────────────────────────────────

    private fun homeItemFromTwoRow(c: JSONObject): HomeItem? {
        val title = c.obj("title").runs().firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() } ?: return null
        val thumb = thumbOf(c.obj("thumbnailRenderer"))
        val subRuns = c.obj("subtitle").runs()
        val meta = readMeta(subRuns)
        val vid = c.str("navigationEndpoint.watchEndpoint.videoId")
        if (vid != null) return HomeItem(title, videoId = vid, artist = meta.artist, artistId = meta.artistId, thumbs = thumb, album = meta.album)
        val bid = c.str("navigationEndpoint.browseEndpoint.browseId") ?: return null
        val type = c.str("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType")
        if (type != "MUSIC_PAGE_TYPE_ALBUM") return null // playlists/artists have no page in this app (same as web)
        return HomeItem(title, browseId = bid, artist = meta.artist, artistId = meta.artistId, thumbs = thumb, isAlbum = true)
    }

    private fun homeItemFromRow(r: JSONObject): HomeItem? {
        val t = songFrom(r) ?: return null
        return HomeItem(t.title, videoId = t.videoId, artist = t.artist, artistId = t.artistId, thumbs = t.thumbs, album = t.album)
    }

    private fun shelvesFrom(root: JSONObject): List<HomeShelf> =
        root.collectAll("musicCarouselShelfRenderer").mapNotNull { sh ->
            val title = sh.obj("header.musicCarouselShelfBasicHeaderRenderer.title").runs().firstOrNull()?.optString("text").orEmpty()
            val items = sh.arr("contents").objects().mapNotNull { c ->
                c.obj("musicTwoRowItemRenderer")?.let(::homeItemFromTwoRow)
                    ?: c.obj("musicResponsiveListItemRenderer")?.let(::homeItemFromRow)
            }
            if (items.isEmpty()) null else HomeShelf(title, items)
        }

    private fun continuationOf(root: JSONObject): String? =
        root.findArray("continuations")?.objects()?.firstNotNullOfOrNull { it.str("nextContinuationData.continuation") }

    suspend fun home(): List<HomeShelf> = cached("home") {
        val shelves = ArrayList<HomeShelf>()

        // The web build's home is synthesised from new releases + charts (rustypipe), not from
        // YouTube's personalised FEmusic_home feed: that feed is thin for a logged-out visitor.
        // Mirror those three shelves so the app opens on the same rows as the web.
        runCatching {
            val r = post("browse", JSONObject().put("browseId", "FEmusic_new_releases"))
            val albums = r.collectAll("musicTwoRowItemRenderer")
                .mapNotNull(::homeItemFromTwoRow)
                .filter { it.isAlbum }
                .distinctBy { it.browseId }
                .take(12)
            if (albums.isNotEmpty()) shelves += HomeShelf("Rilis Baru", albums)
        }
        runCatching {
            val r = post("browse", JSONObject().put("browseId", "FEmusic_new_releases_videos"))
            val videos = r.collectAll("musicTwoRowItemRenderer")
                .mapNotNull(::homeItemFromTwoRow)
                .filter { it.videoId != null && !it.isAlbum }
                .distinctBy { it.videoId }
                .take(12)
            if (videos.isNotEmpty()) shelves += HomeShelf("Video Musik Baru", videos)
        }
        runCatching {
            val r = post("browse", JSONObject().put("browseId", "FEmusic_charts"))
            // Convx opens Home on Top Picks; YouTube's charts page carries its own top-songs
            // carousel, which is the closest real feed Musiku has to that rail.
            val top = shelvesFrom(r)
                .map { sh -> sh.copy(items = sh.items.filter { it.videoId != null }.distinctBy { it.videoId }) }
                .firstOrNull { it.items.size >= 4 }
            if (top != null) {
                shelves.add(0, HomeShelf("Pilihan teratas untukmu", top.items.take(12), hero = true))
            }
            val artists = r.collectAll("musicResponsiveListItemRenderer").mapNotNull { row ->
                val name = flexRuns(row, 0).firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = row.str("navigationEndpoint.browseEndpoint.browseId")
                    ?: flexRuns(row, 0).firstNotNullOfOrNull {
                        it.str("navigationEndpoint.browseEndpoint.browseId")
                    }
                    ?: return@mapNotNull null
                if (!id.startsWith("UC")) return@mapNotNull null
                HomeItem(name, browseId = id, artistId = id, thumbs = thumbOf(row.obj("thumbnail")), isArtist = true)
            }.distinctBy { it.browseId }.take(12)
            if (artists.isNotEmpty()) shelves += HomeShelf("Artis Populer", artists)
        }

        // Fallback: if YouTube changed those browse shapes, use the personalised feed so the
        // screen never comes up empty.
        if (shelves.isEmpty()) {
            val first = post("browse", JSONObject().put("browseId", "FEmusic_home"))
            shelves += shelvesFrom(first)
            var token = continuationOf(first)
            var hops = 0
            while (token != null && hops < 4) {
                val page = runCatching {
                    post("browse", JSONObject(), "&ctoken=$token&continuation=$token&type=next")
                }.getOrNull() ?: break
                shelves += shelvesFrom(page)
                token = continuationOf(page)
                hops++
            }
        }
        android.util.Log.d("Musiku", "home shelves=" + shelves.size + " " + shelves.joinToString { it.title + "(" + it.items.size + ")" })
        shelves
    }

    // ── home chips ───────────────────────────────────────────────────────────

    /**
     * YouTube Music's mood filters, read from the header of the guest home page. The shelves on
     * screen are synthesised, so the chip cloud has to be fetched from the personalised feed
     * that carries it; only the chips are used from that response.
     */
    suspend fun homeChips(): List<HomeChip> = cached("homeChips") {
        val root = post("browse", JSONObject().put("browseId", "FEmusic_home"))
        root.collectAll("chipCloudChipRenderer").mapNotNull { c ->
            val title = c.obj("text").runs().firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val id = c.str("navigationEndpoint.browseEndpoint.browseId") ?: return@mapNotNull null
            HomeChip(
                title = title,
                browseId = id,
                params = c.str("navigationEndpoint.browseEndpoint.params"),
            )
        }.distinctBy { it.browseId + "|" + it.params }
    }

    /** One chip's feed: the same home browse id with the chip's params. */
    suspend fun homeChip(chip: HomeChip): List<HomeShelf> =
        cached("homeChip:${chip.browseId}:${chip.params}") {
            val body = JSONObject().put("browseId", chip.browseId)
            if (!chip.params.isNullOrBlank()) body.put("params", chip.params)
            shelvesFrom(post("browse", body))
        }

    // ── album ────────────────────────────────────────────────────────────────

    suspend fun album(browseId: String): AlbumDetail = cached("album:$browseId") {
        val root = post("browse", JSONObject().put("browseId", browseId))
        val header = root.findFirst("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer")
        val title = header.obj("title").runs().firstOrNull()?.optString("text").orEmpty()
        val headerRuns = header.obj("subtitle").runs() + header.obj("straplineTextOne").runs()
        val meta = readMeta(headerRuns)
        val thumb = thumbOf(header?.opt("thumbnail") ?: header)
        val rows = root.collectAll("musicResponsiveListItemRenderer")
        val tracks = rows.mapNotNull { songFrom(it, fallbackArtist = meta.artist, fallbackThumb = thumb) }
            .map { it.copy(album = it.album ?: title, albumId = it.albumId ?: browseId, artistId = it.artistId ?: meta.artistId) }
        val totalSec = tracks.sumOf { parseDuration(it.duration) }
        AlbumDetail(
            browseId = browseId,
            title = title,
            artist = meta.artist,
            artistId = meta.artistId,
            year = meta.year,
            thumbs = thumb,
            tracks = tracks,
            durationLabel = if (totalSec > 0) "${(totalSec / 60.0).toInt().coerceAtLeast(1)} menit" else null,
        )
    }

    private fun parseDuration(s: String?): Int {
        if (s == null) return 0
        val p = s.trim().split(":").mapNotNull { it.toIntOrNull() }
        return p.fold(0) { acc, n -> acc * 60 + n }
    }

    // ── artist ───────────────────────────────────────────────────────────────

    suspend fun artist(browseId: String): ArtistDetail = cached("artist:$browseId") {
        val root = post("browse", JSONObject().put("browseId", browseId))
        val h = root.findFirst("musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicHeaderRenderer")
        val name = h.obj("title").runs().firstOrNull()?.optString("text").orEmpty()
        val banner = thumbOf(h?.obj("thumbnail"))
        val squares = root.obj("microformat.microformatDataRenderer.thumbnail")?.arr("thumbnails").objects()
        val avatar = squares.lastOrNull()?.optString("url")?.takeIf { it.isNotEmpty() }
        val subs = h.obj("subscriptionButton.subscribeButtonRenderer.subscriberCountText").runs().firstOrNull()?.optString("text")
        val monthly = h.obj("monthlyListenerCount").runs().firstOrNull()?.optString("text")

        val sectionList = root.collectAll("sectionListRenderer").firstOrNull()
        val topSongs = (sectionList ?: root).collectAll("musicShelfRenderer").firstOrNull()
            ?.arr("contents").objects()
            ?.mapNotNull { it.obj("musicResponsiveListItemRenderer") }
            ?.mapNotNull { songFrom(it, fallbackArtist = name) }
            .orEmpty()

        val albums = ArrayList<AlbumSummary>()
        val singles = ArrayList<AlbumSummary>()
        var topVideo: String? = null
        var albumCarouselSeen = false
        for (sh in root.collectAll("musicCarouselShelfRenderer")) {
            val items = sh.arr("contents").objects().mapNotNull { it.obj("musicTwoRowItemRenderer") }
            if (items.isEmpty()) continue
            val vid = items.firstNotNullOfOrNull { it.str("navigationEndpoint.watchEndpoint.videoId") }
            if (vid != null) {
                if (topVideo == null) topVideo = vid
                continue
            }
            val albumItems = items.filter {
                it.str("navigationEndpoint.browseEndpoint.browseEndpointContextSupportedConfigs.browseEndpointContextMusicConfig.pageType") ==
                    "MUSIC_PAGE_TYPE_ALBUM"
            }
            if (albumItems.isEmpty()) continue
            val parsed = albumItems.mapNotNull { c ->
                val id = c.str("navigationEndpoint.browseEndpoint.browseId") ?: return@mapNotNull null
                val t = c.obj("title").runs().firstOrNull()?.optString("text") ?: return@mapNotNull null
                val sub = c.obj("subtitle").runs().map { it.optString("text") }.filter { it.isNotBlank() && !isSep(it) }
                val year = sub.firstOrNull { Regex("^\\d{4}$").matches(it.trim()) }
                AlbumSummary(id, t, name, year, thumbOf(c.obj("thumbnailRenderer"))) to sub
            }
            val looksSingle = parsed.count { (_, sub) -> sub.any { it.trim().lowercase() in setOf("single", "ep") } } > parsed.size / 2
            if (!albumCarouselSeen && !looksSingle) {
                albums += parsed.map { it.first }; albumCarouselSeen = true
            } else if (singles.isEmpty()) {
                singles += parsed.map { it.first }
            }
        }

        ArtistDetail(
            browseId = browseId,
            name = name,
            thumbs = banner,
            avatar = avatar ?: banner,
            banner = banner,
            topVideoId = topVideo,
            subscribers = subs,
            monthlyListeners = monthly,
            topSongs = topSongs,
            albums = albums,
            singles = singles,
        )
    }

    // ── up next / autoplay ───────────────────────────────────────────────────

    suspend fun related(videoId: String): List<Track> = cached("related:$videoId") {
        val root = post(
            "next",
            JSONObject().put("videoId", videoId).put("playlistId", "RDAMVM$videoId").put("isAudioOnly", true),
        )
        root.collectAll("playlistPanelVideoRenderer").mapNotNull { r ->
            val id = r.str("videoId") ?: return@mapNotNull null
            if (id == videoId) return@mapNotNull null
            val title = r.obj("title").runs().firstOrNull()?.optString("text") ?: return@mapNotNull null
            val meta = readMeta(r.obj("longBylineText").runs())
            Track(
                videoId = id,
                title = title,
                artist = meta.artist,
                album = meta.album,
                albumId = meta.albumId,
                artistId = meta.artistId,
                thumbs = thumbOf(r.obj("thumbnail")),
                duration = r.obj("lengthText").runs().firstOrNull()?.optString("text"),
            )
        }.take(25)
    }

    /** Plain lyrics from YouTube Music itself (fallback when lrclib has nothing). */
    suspend fun ytLyrics(videoId: String): String? = runCatching {
        val next = post("next", JSONObject().put("videoId", videoId))
        val tabs = next.findArray("tabs").objects()
        val lyricsBrowse = tabs.firstNotNullOfOrNull { tab ->
            val id = tab.str("tabRenderer.endpoint.browseEndpoint.browseId")
            if (id != null && id.startsWith("MPLY")) id else null
        } ?: return@runCatching null
        val page = post("browse", JSONObject().put("browseId", lyricsBrowse))
        page.collectAll("musicDescriptionShelfRenderer").firstOrNull()
            ?.obj("description").runs().firstOrNull()?.optString("text")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        private const val KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val CLIENT_VERSION = "1.20250310.01.00"
        private const val HL = "id"
        private const val GL = "ID"
        private const val TTL = 10 * 60 * 1000L
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
