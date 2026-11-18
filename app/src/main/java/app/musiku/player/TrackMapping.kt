package app.musiku.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.musiku.model.Track

const val STREAM_SCHEME = "musiku"

fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString("albumId", albumId)
        putString("artistId", artistId)
        putString("duration", duration)
    }
    val md = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(thumbs?.let { Uri.parse(it) })
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri("$STREAM_SCHEME://track/$videoId")
        .setMediaMetadata(md)
        .build()
}

fun MediaItem.toTrack(): Track {
    val md = mediaMetadata
    val ex = md.extras
    return Track(
        videoId = mediaId,
        title = md.title?.toString().orEmpty(),
        artist = md.artist?.toString(),
        album = md.albumTitle?.toString(),
        albumId = ex?.getString("albumId"),
        artistId = ex?.getString("artistId"),
        thumbs = md.artworkUri?.toString(),
        duration = ex?.getString("duration"),
    )
}
