package app.musiku.ui.nowplaying

import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.MMenuIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.model.Track
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.Tok
import app.musiku.ui.theme.glass
import dev.chrisbanes.haze.HazeState

private enum class MenuView { Main, Sleep, Crossfade }

/** Floating action panel, anchored bottom-right like the web build's PlayerMenu. */
@Composable
fun PlayerMenu(
    track: Track,
    state: NowPlayingState,
    actions: NowPlayingActions,
    dismiss: () -> Unit,
    maxHeight: Dp,
    haze: HazeState,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(MenuView.Main) }
    val shape = RoundedCornerShape(28.dp)
    val act: (() -> Unit) -> () -> Unit = { f -> { f(); dismiss() } }

    Column(
        modifier
            .width(268.dp)
            .heightIn(max = maxHeight)
            // web: rounded-[28px] border-white/10 bg-[rgba(30,30,33,.86)] backdrop-blur-2xl
            .glass(haze, shape, strong = true, shadow = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        when (view) {
            MenuView.Main -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pressable { act(actions.onShare)() }
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(MMenuIcons.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text("Bagikan", color = Color.White, fontSize = 14.sp)
                }
                Sep()
                MenuRow(MMenuIcons.Next, "Putar berikutnya", onClick = act(actions.onPlayNext))
                MenuRow(MMenuIcons.Queue, "Tambah ke antrian", onClick = act(actions.onAddToQueue))
                Sep()
                MenuRow(
                    if (state.liked) MMenuIcons.HeartFilled else MMenuIcons.Heart,
                    if (state.liked) "Hapus dari favorit" else "Tambah ke favorit",
                    onClick = act(actions.onLike),
                )
                MenuRow(MMenuIcons.Plus, "Tambah ke playlist…", onClick = act(actions.onAddToPlaylist))
                Sep()
                actions.onOpenAlbum?.let {
                    MenuRow(MMenuIcons.Album, "Buka album", subtitle = track.album, onClick = act(it))
                }
                actions.onOpenArtist?.let {
                    MenuRow(MMenuIcons.Mic, "Buka artis", subtitle = track.artist, onClick = act(it))
                }
                MenuRow(MMenuIcons.Info, "Info lagu", onClick = act(actions.onInfo))
                Sep()
                MenuRow(
                    MMenuIcons.Moon,
                    "Pewaktu tidur",
                    subtitle = state.sleepLabel,
                    chevron = true,
                    onClick = { view = MenuView.Sleep },
                )
                MenuRow(
                    MMenuIcons.Cross,
                    "Crossfade",
                    subtitle = if (state.crossfade) "Aktif · ${state.cfSec} detik" else "Mati",
                    onClick = act(actions.onCrossfade),
                )
                if (state.crossfade) {
                    MenuRow(
                        null,
                        "Durasi crossfade",
                        chevron = true,
                        onClick = { view = MenuView.Crossfade },
                    )
                }
            }

            MenuView.Crossfade -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { view = MenuView.Main }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(MMenuIcons.Back, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Crossfade", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Sep()
                listOf(2, 4, 6, 8, 10, 12).forEach { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressable { act { actions.onCfSec(n) }() }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$n detik", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (state.cfSec == n) {
                            Icon(MMenuIcons.Check, contentDescription = "Dipilih", tint = Color(0xFFFA586A), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            MenuView.Sleep -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable { view = MenuView.Main }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(MMenuIcons.Back, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Pewaktu tidur", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Sep()
                listOf(15, 30, 45, 60).forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressable { act { actions.onSleep(m) }() }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$m menit", color = Color.White, fontSize = 17.sp)
                    }
                }
                if (state.sleepLabel != null) {
                    Sep()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressable { act { actions.onSleep(null) }() }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Matikan pewaktu", color = Color(0xFFFF6B7D), fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

/**
 * One row of a floating menu card. [dark] is for surfaces that are always dark — the player — where
 * the labels stay white whatever the app theme is doing. The track menu floats over the page, so
 * there it has to follow the theme or it would be white-on-white in light mode.
 */
@Composable
internal fun MenuRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    chevron: Boolean = false,
    dark: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val fg = when {
        danger -> Tok.Accent
        dark -> Color.White
        else -> Tok.Label1
    }
    val sub = if (dark) Color.White.copy(alpha = 0.55f) else Tok.Label2
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressable { onClick() }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(23.dp))
        else Box(Modifier.size(23.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 17.sp, lineHeight = 20.sp)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    color = sub,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chevron) {
            Icon(
                MIcons.ChevronRight,
                contentDescription = null,
                tint = sub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun Sep(dark: Boolean = true) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(if (dark) Color.White.copy(alpha = 0.15f) else Tok.Hairline),
    )
}
