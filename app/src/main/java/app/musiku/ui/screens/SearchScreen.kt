package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.GenreItem
import app.musiku.model.SearchResults
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.Routes
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.Eq
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.pressable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.Tok
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val TABS = listOf("Semua", "Lagu", "Video", "Album", "Artis")

/**
 * Convx's search: a large title, the field, and — before anything is typed — YouTube Music's
 * moods & genres grid (two across, every tile a duotone image over its own colour). Results swap
 * in under the same field, switched by the scope pills instead of a filled segmented control.
 */
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

    val genres by rememberLoad(Unit) { app.yt.genres() }
    val load by rememberLoad(query) { if (query.isEmpty()) SearchResults() else app.yt.search(query) }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Convx blurs the first result's cover behind the whole results page; nothing while the
    // landing grid is up, since that has its own colours.
    val heroUrl = (load as? Load.Ok)?.value?.let { r ->
        r.songs.firstOrNull()?.thumbs
            ?: r.videos.firstOrNull()?.thumbs
            ?: r.albums.firstOrNull()?.thumbs
            ?: r.artists.firstOrNull()?.thumbs
    }

    Box(Modifier.fillMaxSize()) {
    if (query.isNotEmpty() && heroUrl != null) HeroBackdrop(heroUrl)

    Column(Modifier.fillMaxSize()) {
        Text(
            "Cari",
            color = Tok.Label1,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            modifier = Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = maxOf(statusTop, 62.dp),
                bottom = 16.dp,
            ),
        )

        // Convx's nav search pill: one translucent capsule (15% of the label colour), 48dp tall,
        // 24dp glyphs with 48dp tap targets, text and placeholder at 17sp. No metal rim.
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(Tok.Label1.copy(alpha = 0.15f))
                .padding(start = 11.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(MIcons.Search, null, tint = Tok.Label1, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text(
                        "Cari di YouTube Music…",
                        color = Tok.Label1.copy(alpha = 0.6f),
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Tok.Label1, fontSize = 17.sp),
                    cursorBrush = SolidColor(Tok.Label1),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { query = input.trim(); focus.clearFocus() },
                        onDone = { query = input.trim(); focus.clearFocus() },
                        onGo = { query = input.trim(); focus.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(requester),
                )
            }
            if (input.isNotEmpty()) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).pressable { input = ""; query = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MIcons.Close, "Hapus", tint = Tok.Label1, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (query.isEmpty()) {
            GenreGrid(genres) { g -> ui.navigate(Routes.genre(g.browseId, g.params, g.title)) }
            return@Column
        }

        ScopePills(TABS, tab) { tab = it }

        when (val l = load) {
            is Load.Loading -> Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                MusikuLoading()
            }
            is Load.Err -> EmptyState("Pencarian gagal", "Cek koneksi internet lalu coba lagi.")
            is Load.Ok -> Results(l.value, tab) { id -> ui.navigate(id) }
        }
    }
    }
}

/**
 * Convx's results backdrop: the leading artwork, blown up, blurred hard and laid at 55% under a
 * 45% black wash so titles stay readable over any cover.
 */
@Composable
private fun HeroBackdrop(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .blur(120.dp)
            .graphicsLayer { alpha = 0.55f },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}

/* ------------------------------------------------------------ moods & genres -- */

@Composable
private fun GenreGrid(load: Load<List<GenreItem>>, onOpen: (GenreItem) -> Unit) {
    when (load) {
        is Load.Loading -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(6) {
                Box(
                    Modifier
                        .aspectRatio(181f / 105f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Tok.Fill1),
                )
            }
        }
        is Load.Err -> EmptyState("Kategori belum bisa dimuat", "Cek koneksi, lalu buka lagi halaman ini.", Modifier.padding(top = 48.dp))
        is Load.Ok -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = DockInset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(load.value, key = { it.browseId + it.params }) { item ->
                GenreTile(item) { onOpen(item) }
            }
        }
    }
}

/**
 * Convx's category tile: the page's own artwork, desaturated and multiplied into the tile
 * colour, with the name in white at the bottom-left.
 */
@Composable
private fun GenreTile(item: GenreItem, onClick: () -> Unit) {
    val app = LocalApp.current
    var art by remember(item) { mutableStateOf<String?>(null) }
    LaunchedEffect(item) { art = app.yt.genreArt(item) }

    val ground = Color(item.color)
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier
            .aspectRatio(181f / 105f)
            .clip(shape)
            .background(ground)
            .border(0.25.dp, Color.Black.copy(alpha = 0.1f), shape)
            .pressable(onClick = onClick, pressScale = 0.97f),
    ) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .drawWithContent {
                        val paint = Paint().apply { blendMode = BlendMode.Multiply }
                        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
                        drawContent()
                        drawContext.canvas.restore()
                    },
            )
            // Lifts the duotone's highlights back toward the tile colour.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(ground.copy(alpha = 0.25f), blendMode = BlendMode.Screen)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.28f))),
        )
        Text(
            item.title,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.23).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

/* ------------------------------------------------------------------ results -- */

/**
 * Convx's scope pills: 32dp capsules, grey fill when off, accent when on. No checkmark.
 */
@Composable
private fun ScopePills(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (on) Tok.Accent else Color(0xFF767680).copy(alpha = 0.12f))
                    .pressable(onClick = { onSelect(i) }, pressScale = 0.95f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    label,
                    color = if (on) Color.White else Tok.Label1,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = (-0.23).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Results(r: SearchResults, tab: Int, go: (String) -> Unit) {
    if (r.isEmpty) {
        EmptyState("Tidak ada hasil", "Coba kata kunci lain atau periksa ejaannya.")
        return
    }
    val app = LocalApp.current
    val ui = LocalUi.current
    val ps by app.player.ui.collectAsState()
    val all = tab == 0
    val cap = if (all) 8 else Int.MAX_VALUE

    LazyColumn(contentPadding = PaddingValues(bottom = DockInset)) {
        if ((all || tab == 1) && r.songs.isNotEmpty()) {
            item(key = "h-songs") { ResultSectionTitle("Lagu", first = true) }
            val list = r.songs.take(cap)
            itemsIndexed(list, key = { i, t -> "s${t.videoId}-$i" }) { i, t ->
                val active = ps.current?.videoId == t.videoId
                ResultRow(
                    thumbs = t.thumbs,
                    title = t.title,
                    subtitle = listOfNotNull(t.artist, t.duration).joinToString(" • ").ifBlank { null },
                    active = active,
                    playing = active && ps.playing,
                    accent = active,
                    onClick = { if (active) app.player.toggle() else app.player.playTracks(r.songs, i) },
                    onMenu = { rect -> ui.openMenu(t, anchor = rect) },
                )
            }
        }
        if ((all || tab == 2) && r.videos.isNotEmpty()) {
            item(key = "h-videos") { ResultSectionTitle("Video", first = all && r.songs.isEmpty()) }
            val list = r.videos.take(cap)
            itemsIndexed(list, key = { i, t -> "v${t.videoId}-$i" }) { i, t ->
                val active = ps.current?.videoId == t.videoId
                ResultRow(
                    thumbs = t.thumbs,
                    title = t.title,
                    subtitle = listOfNotNull(t.artist, t.duration).joinToString(" • ").ifBlank { null },
                    active = active,
                    playing = active && ps.playing,
                    accent = active,
                    onClick = { if (active) app.player.toggle() else app.player.playTracks(r.videos, i) },
                    onMenu = { rect -> ui.openMenu(t, anchor = rect) },
                )
            }
        }
        if ((all || tab == 3) && r.albums.isNotEmpty()) {
            item(key = "h-albums") {
                ResultSectionTitle("Album", first = all && r.songs.isEmpty() && r.videos.isEmpty())
            }
            val list = r.albums.take(cap)
            itemsIndexed(list, key = { i, a -> "a${a.browseId}-$i" }) { _, a ->
                ResultRow(
                    thumbs = a.thumbs,
                    title = a.title,
                    subtitle = listOfNotNull(a.artist, a.year).joinToString(" • ").ifBlank { null },
                    active = ps.current?.albumId != null && ps.current?.albumId == a.browseId,
                    playing = false,
                    accent = false,
                    onClick = { go(Routes.album(a.browseId)) },
                )
            }
        }
        if ((all || tab == 4) && r.artists.isNotEmpty()) {
            item(key = "h-artists") {
                ResultSectionTitle(
                    "Artis",
                    first = all && r.songs.isEmpty() && r.videos.isEmpty() && r.albums.isEmpty(),
                )
            }
            val list = r.artists.take(cap)
            itemsIndexed(list, key = { i, a -> "r${a.browseId}-$i" }) { _, a ->
                ResultRow(
                    thumbs = a.thumbs,
                    title = a.name,
                    subtitle = null,
                    active = false,
                    playing = false,
                    accent = false,
                    onClick = { go(Routes.artist(a.browseId)) },
                )
            }
        }
    }
}

/**
 * Convx's section header on a results page: 22/28 bold, full gutter, no wrapper box.
 */
@Composable
private fun ResultSectionTitle(text: String, first: Boolean = false) {
    Text(
        text,
        color = Tok.Label1,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.22).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = if (first) 8.dp else 24.dp, bottom = 6.dp),
    )
}

/**
 * Convx's `ListItem`: 58dp tall, 48dp cover on a 5dp corner, 15/20 title, 13/18 metadata, and a
 * hairline under the row that starts at the text so the covers stay in one clear column. The
 * playing row gets the full-bleed 10% highlight iOS uses, not a chip.
 */
@Composable
private fun ResultRow(
    thumbs: String?,
    title: String,
    subtitle: String?,
    active: Boolean,
    playing: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
    onMenu: ((Rect) -> Unit)? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (active) Tok.Label1.copy(alpha = 0.10f) else Color.Transparent),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .onGloballyPositioned { bounds = it.boundsInWindow() }
                    .pressable(
                        pressScale = 0.985f,
                        description = title,
                        onLongClick = onMenu?.let { f -> { f(bounds) } },
                        onClick = onClick,
                    )
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(5.dp)).background(Tok.Fill1)) {
                    Artwork(thumbs, Modifier.size(48.dp), contentDescription = null)
                    if (playing) {
                        Box(
                            Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) { Eq(true) }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        color = if (accent) Tok.Accent else Tok.Label1,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.23).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = Tok.Label3,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onMenu != null) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).pressable { onMenu(bounds) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MIcons.Ellipsis, "Aksi untuk $title", tint = Tok.Label3, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 78.dp, end = 20.dp)
                .height(0.75.dp)
                .background(Tok.Hairline),
        )
    }
}
