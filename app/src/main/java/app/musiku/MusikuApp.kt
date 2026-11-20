package app.musiku

import android.app.Application
import android.content.Intent
import app.musiku.data.ArtistArtworkVideo
import app.musiku.data.Canvas
import app.musiku.data.Library
import app.musiku.data.LyricsRepo
import app.musiku.data.Spotify
import app.musiku.data.SpotifyImporter
import app.musiku.data.StreamResolver
import app.musiku.data.UpdateWorker
import app.musiku.data.YtMusic
import app.musiku.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Process-wide singletons. The playback service and the UI share the same instances. */
class MusikuApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }
    val yt: YtMusic by lazy { YtMusic(http) }
    val lyrics: LyricsRepo by lazy { LyricsRepo(http, yt) }
    val streams: StreamResolver by lazy { StreamResolver(http) }
    val library: Library by lazy { Library(this, scope) }
    val player: PlayerController by lazy { PlayerController(this, scope, library, yt) }
    val spotify: Spotify by lazy { Spotify(http) }
    val canvas: Canvas by lazy { Canvas(this, http) }
    val artistVideo: ArtistArtworkVideo by lazy { ArtistArtworkVideo(http) }
    val importer: SpotifyImporter by lazy { SpotifyImporter(spotify, yt, library) }

    /** A playlist link arriving from the share sheet or a VIEW intent, picked up by AppRoot. */
    val pendingSpotify = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.schedule(this)
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        if (spotify.looksLikePlaylist(text)) pendingSpotify.value = text
    }
}
