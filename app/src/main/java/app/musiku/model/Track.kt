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
    val album: String? = null,
) {
    fun toTrack(): Track? = videoId?.let {
        Track(it, title, artist, album = album, albumId = browseId, artistId = artistId, thumbs = thumbs)
    }
}

/** [hero] marks the shelf Home draws as Convx's tall Top Picks rail instead of a row of cards. */
data class HomeShelf(val title: String, val items: List<HomeItem>, val hero: Boolean = false)

/** One yellow/coloured card of YouTube Music's moods & genres grid. */
data class GenreItem(
    val title: String,
    val color: Long,
    val browseId: String,
    val params: String,
)

/** A card on a category page: usually a playlist, sometimes an album, artist or song. */
data class BrowseCard(
    val title: String,
    val subtitle: String? = null,
    val thumbs: String? = null,
    val browseId: String? = null,
    val videoId: String? = null,
    val pageType: String? = null,
)

data class BrowseShelf(val title: String, val cards: List<BrowseCard>)

data class OnlinePlaylist(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val thumbs: String? = null,
    val tracks: List<Track> = emptyList(),
)

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
    /** Non-null for playlists mirrored from another service; currently only "spotify". */
    val source: String? = null,
    /** Spotify playlist id, kept so the playlist can be re-fetched by the Sync action. */
    val sourceId: String? = null,
)

enum class RepeatMode { Off, All, One }
