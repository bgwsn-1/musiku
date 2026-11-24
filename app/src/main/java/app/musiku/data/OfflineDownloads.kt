package app.musiku.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import app.musiku.model.Track
import app.musiku.player.MusikuDownloadService
import app.musiku.player.STREAM_SCHEME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

data class DownloadEntry(
    val state: Int,
    val progress: Float,
) {
    val isCompleted: Boolean get() = state == Download.STATE_COMPLETED
    val isActive: Boolean get() = state == Download.STATE_DOWNLOADING || state == Download.STATE_QUEUED
}

/**
 * Manages both the automatic LRU playback cache (512 MB in cacheDir) and explicit offline
 * downloads (NoOp evictor in filesDir) via Media3 [SimpleCache] and [DownloadManager].
 */
@UnstableApi
class OfflineDownloads(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val streams: StreamResolver,
    private val library: Library,
) {
    private val dbProvider = StandaloneDatabaseProvider(ctx)

    val playerCache: SimpleCache = SimpleCache(
        File(ctx.cacheDir, "audio_cache"),
        LeastRecentlyUsedCacheEvictor(PLAYER_CACHE_BYTES),
        dbProvider,
    )

    val downloadCache: SimpleCache = SimpleCache(
        File(ctx.filesDir, "offline_audio"),
        NoOpCacheEvictor(),
        dbProvider,
    )

    val keyFactory: CacheKeyFactory = CacheKeyFactory { spec ->
        spec.key ?: if (spec.uri.scheme == STREAM_SCHEME) {
            spec.uri.lastPathSegment ?: spec.uri.toString()
        } else {
            spec.uri.toString()
        }
    }

    private val httpFactory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    private val resolvingFactory: DataSource.Factory =
        ResolvingDataSource.Factory(DefaultDataSource.Factory(ctx, httpFactory)) { spec: DataSpec ->
            if (spec.uri.scheme == STREAM_SCHEME) {
                val id = spec.uri.lastPathSegment ?: throw IOException("bad track uri")
                val resolved = try {
                    streams.resolveStreamBlocking(id)
                } catch (e: Exception) {
                    throw IOException("resolve failed: ${e.message}", e)
                }
                spec.buildUpon()
                    .setUri(Uri.parse(resolved.url))
                    .setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to resolved.userAgent))
                    .build()
            } else {
                spec
            }
        }

    /**
     * Read-only lookup on [downloadCache] falling through to [playerCache] (which caches
     * streams resolved lazily via [resolvingFactory]).
     */
    fun playbackDataSourceFactory(): DataSource.Factory {
        val playerCacheFactory = CacheDataSource.Factory()
            .setCache(playerCache)
            .setCacheKeyFactory(keyFactory)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheKeyFactory(keyFactory)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(playerCacheFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(ctx, MusikuDownloadService.CHANNEL_ID)
    }

    val downloadManager: DownloadManager = DownloadManager(
        ctx,
        dbProvider,
        downloadCache,
        resolvingFactory,
        Executors.newFixedThreadPool(3),
    ).apply {
        maxParallelDownloads = 2
    }

    private val _states = MutableStateFlow<Map<String, DownloadEntry>>(emptyMap())
    val states: StateFlow<Map<String, DownloadEntry>> = _states

    private var progressJob: Job? = null

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = syncFromManager()

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                if (finalException != null) {
                    Log.w("MusikuDownload", "download failed ${download.request.id}", finalException)
                }
                syncFromManager()
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                syncFromManager()
            }
        })
        syncFromManager()
    }

    private fun syncFromManager() {
        val map = LinkedHashMap<String, DownloadEntry>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        cursor.use {
            while (it.moveToNext()) {
                val d = it.download
                map[d.request.id] = DownloadEntry(d.state, d.percentDownloaded.coerceIn(0f, 100f))
            }
        }
        // Overlay live in-memory progress for active downloads
        var hasActive = false
        for (d in downloadManager.currentDownloads) {
            val active = d.state == Download.STATE_DOWNLOADING || d.state == Download.STATE_QUEUED
            if (active) hasActive = true
            map[d.request.id] = DownloadEntry(d.state, d.percentDownloaded.coerceIn(0f, 100f))
        }
        _states.value = map
        if (hasActive) startProgressTicker() else stopProgressTicker()
    }

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                delay(450)
                val current = downloadManager.currentDownloads
                if (current.isEmpty()) break
                val next = _states.value.toMutableMap()
                var anyActive = false
                for (d in current) {
                    if (d.state == Download.STATE_DOWNLOADING || d.state == Download.STATE_QUEUED) {
                        anyActive = true
                    }
                    next[d.request.id] = DownloadEntry(d.state, d.percentDownloaded.coerceIn(0f, 100f))
                }
                _states.value = next
                if (!anyActive) break
            }
            progressJob = null
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun isDownloaded(videoId: String): Boolean = _states.value[videoId]?.isCompleted == true

    fun download(track: Track) {
        if (track.videoId.isBlank()) return
        library.saveDownloadedTrack(track)
        sendAdd(track)
    }

    fun downloadAll(tracks: List<Track>) {
        val valid = tracks.filter { it.videoId.isNotBlank() }
        if (valid.isEmpty()) return
        library.saveDownloadedTracks(valid)
        valid.forEach { sendAdd(it) }
    }

    private fun sendAdd(track: Track) {
        val uri = Uri.parse("$STREAM_SCHEME://track/${track.videoId}")
        val req = DownloadRequest.Builder(track.videoId, uri)
            .setCustomCacheKey(track.videoId)
            .setData(track.title.toByteArray(Charsets.UTF_8))
            .build()
        runCatching {
            DownloadService.sendAddDownload(ctx, MusikuDownloadService::class.java, req, true)
        }.onFailure {
            runCatching {
                DownloadService.sendAddDownload(ctx, MusikuDownloadService::class.java, req, false)
            }
        }
    }

    fun remove(videoId: String) {
        library.removeDownloadedTrack(videoId)
        runCatching {
            DownloadService.sendRemoveDownload(ctx, MusikuDownloadService::class.java, videoId, false)
        }
    }

    fun removeAll() {
        library.clearDownloadedTracks()
        runCatching {
            DownloadService.sendRemoveAllDownloads(ctx, MusikuDownloadService::class.java, false)
        }
    }

    companion object {
        private const val PLAYER_CACHE_BYTES = 512L * 1024L * 1024L
    }
}
