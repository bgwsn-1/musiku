package app.musiku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.BrowseCard
import app.musiku.model.BrowseShelf
import app.musiku.model.Track
import app.musiku.MusikuApp
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.UiState
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.GridCard
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.Tok

/**
 * A moods & genres category, the page Convx opens from a search tile: titled shelves of
 * playlists, albums and artists. Playlists open in [OnlinePlaylistScreen]; songs play straight
 * from their shelf.
 */
@Composable
fun GenreScreen(browseId: String, params: String, title: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val load by rememberLoad(browseId to params) { app.yt.category(browseId, params) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text(
                        title,
                        color = Tok.Label1,
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (val l = load) {
                is Load.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { MusikuLoading() }
                }
                is Load.Err -> item(key = "error") {
                    EmptyState("Kategori belum bisa dimuat", "Cek koneksi internet lalu coba lagi.")
                }
                is Load.Ok -> l.value.forEachIndexed { index, shelf ->
                    item(key = "shelf-$index-$title") {
                        BrowseShelfRow(shelf) { card ->
                            openCard(card, shelf, app, ui)
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}

private fun openCard(
    card: BrowseCard,
    shelf: BrowseShelf,
    app: MusikuApp,
    ui: UiState,
) {
    when (card.pageType) {
        "MUSIC_PAGE_TYPE_ALBUM" -> card.browseId?.let { ui.navigate(Routes.album(it)) }
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" -> card.browseId?.let { ui.navigate(Routes.artist(it)) }
        "MUSIC_PAGE_TYPE_PLAYLIST" -> card.browseId?.let { ui.navigate(Routes.onlinePlaylist(it)) }
        else -> {
            val queue = shelf.cards.mapNotNull { c ->
                c.videoId?.let { Track(it, c.title, c.subtitle, thumbs = c.thumbs) }
            }
            val index = queue.indexOfFirst { it.videoId == card.videoId }
            if (index >= 0) app.player.playTracks(queue, index)
        }
    }
}

@Composable
private fun BrowseShelfRow(shelf: BrowseShelf, onCard: (BrowseCard) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            shelf.title,
            color = Tok.Label1,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.22).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.cards, key = { (it.browseId ?: it.videoId ?: it.title) + it.title }) { card ->
                GridCard(card.title, card.subtitle, card.thumbs, onClick = { onCard(card) })
            }
        }
    }
}
