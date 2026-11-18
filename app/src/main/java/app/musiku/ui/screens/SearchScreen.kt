package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.SearchResults
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.GridCard
import app.musiku.ui.components.Load
import app.musiku.ui.components.Segmented
import app.musiku.ui.components.SectionTitle
import app.musiku.ui.components.Skeleton
import app.musiku.ui.components.pressable
import app.musiku.ui.components.TrackRow
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.delay

private val TABS = listOf("Semua", "Lagu", "Video", "Album", "Artis")

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val focus = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }
    val requester = remember { FocusRequester() }

    // debounce typing → query
    LaunchedEffect(input) {
        if (input.isBlank()) { query = ""; return@LaunchedEffect }
        delay(450)
        query = input.trim()
    }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { requester.requestFocus() } }

    val load by rememberLoad(query) { if (query.isEmpty()) SearchResults() else app.yt.search(query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Cari", color = Tok.Label1, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.73).sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 20.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(CircleShape).background(Tok.Fill1).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(MIcons.Search, null, tint = Tok.Label3, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Lagu, artis, album…", color = Tok.Label3, fontSize = 17.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Tok.Label1, fontSize = 17.sp),
                    cursorBrush = SolidColor(Tok.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { query = input.trim(); focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(requester),
                )
            }
            if (input.isNotEmpty()) {
                Box(Modifier.size(24.dp).clip(CircleShape).pressable { input = ""; query = "" }, contentAlignment = Alignment.Center) {
                    Icon(MIcons.Close, "Hapus", tint = Tok.Label3, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (query.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Tok.Fill1), contentAlignment = Alignment.Center) {
                    Icon(MIcons.Search, null, tint = Tok.Label2, modifier = Modifier.size(26.dp))
                }
                Text("Cari apa hari ini?", color = Tok.Label1, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                Text("Lagu, artis, album, atau video.", color = Tok.Label2, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
            return@Column
        }

        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).horizontalScroll(rememberScrollState())) {
            Segmented(TABS, tab, { tab = it })
        }

        when (val l = load) {
            is Load.Loading -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { Skeleton(Modifier.fillMaxWidth().height(56.dp)) }
            }
            is Load.Err -> EmptyState("Pencarian gagal", "Cek koneksi internet lalu coba lagi.")
            is Load.Ok -> Results(l.value, tab) { id -> ui.navigate(id) }
        }
    }
}

@Composable
private fun Results(r: SearchResults, tab: Int, go: (String) -> Unit) {
    val app = LocalApp.current
    if (r.isEmpty) {
        EmptyState("Tidak ada hasil", "Coba kata kunci lain atau periksa ejaannya.")
        return
    }
    val all = tab == 0
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = DockInset)) {
        if ((all || tab == 1) && r.songs.isNotEmpty()) {
            item { SectionTitle("Lagu", Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp)) }
            val list = r.songs.take(if (all) 8 else 50)
            itemsIndexed(list, key = { i, t -> "s${t.videoId}-$i" }) { i, t -> TrackRow(t, r.songs, i) }
        }
        if ((all || tab == 4) && r.artists.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 24.dp)) {
                    SectionTitle("Artis", Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        r.artists.forEach { a -> GridCard(a.name, "Artis", a.thumbs, onClick = { go(Routes.artist(a.browseId)) }, round = true, size = 130.dp) }
                    }
                }
            }
        }
        if ((all || tab == 3) && r.albums.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 24.dp)) {
                    SectionTitle("Album", Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        r.albums.forEach { a ->
                            GridCard(a.title, listOfNotNull(a.artist ?: "Album", a.year).joinToString(" • "), a.thumbs, onClick = { go(Routes.album(a.browseId)) })
                        }
                    }
                }
            }
        }
        if ((all || tab == 2) && r.videos.isNotEmpty()) {
            item { SectionTitle("Video", Modifier.padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 4.dp)) }
            itemsIndexed(r.videos, key = { i, t -> "v${t.videoId}-$i" }) { i, t -> TrackRow(t, r.videos, i) }
        }
    }
}
