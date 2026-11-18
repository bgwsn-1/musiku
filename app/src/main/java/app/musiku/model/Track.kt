package app.musiku.model

data class Track(
    val videoId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val thumbs: String? = null,
    /** "3:45" as YouTube Music shows it; null when unknown */
    val duration: String? = null,
)

data class LyricLine(val timeSec: Float, val text: String)

data class AlbumSummary(
    val browseId: String,
    val title: String,
    val artist: String? = null,
    val year: String? = null,
    val thumbs: String? = null,
)

data class AlbumDetail(
    val browseId: String,
    val title: String,
    val artist: String? = null,
    val artistId: String? = null,
    val year: String? = null,
    val thumbs: String? = null,
    val tracks: List<Track> = emptyList(),
    val durationLabel: String? = null,
)

data class ArtistCard(
    val browseId: String,
    val name: String,
    val thumbs: String? = null,
)

data class ArtistDetail(
    val browseId: String,
    val name: String,
    val thumbs: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val topVideoId: String? = null,
    val subscribers: String? = null,
    val monthlyListeners: String? = null,
    val topSongs: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singles: List<AlbumSummary> = emptyList(),
)

data class HomeItem(
    val title: String,
    val videoId: String? = null,
    val browseId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val thumbs: String? = null,
    val isAlbum: Boolean = false,
    val isArtist: Boolean = false,
) {
    fun toTrack(): Track? = videoId?.let {
        Track(it, title, artist, albumId = browseId, artistId = artistId, thumbs = thumbs)
    }
}

data class HomeShelf(val title: String, val items: List<HomeItem>)

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val videos: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistCard> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && videos.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val synced: Boolean = false,
    val plain: String? = null,
    val source: String? = null,
) {
    val hasText get() = lines.isNotEmpty() || !plain.isNullOrBlank()
}

data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val tracks: List<Track>,
)

enum class RepeatMode { Off, All, One }
