package app.musiku

import android.app.Application
import app.musiku.data.Library
import app.musiku.data.LyricsRepo
import app.musiku.data.StreamResolver
import app.musiku.data.YtMusic
import app.musiku.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
}
