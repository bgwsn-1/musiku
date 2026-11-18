package app.musiku.ui.screens

import app.musiku.ui.theme.MIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.LocalApp
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.Eq
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.Tok

@Composable
fun QueuePanel(onClose: () -> Unit) {
    val app = LocalApp.current
    val ps by app.player.ui.collectAsState()
    val autoplay by app.library.autoplay.collectAsState()
    val rest = (ps.queue.size - ps.index - 1).coerceAtLeast(0)
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { if (ps.index > 0) list.scrollToItem((ps.index - 1).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Antrian", color = Tok.Label1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        ps.queue.isEmpty() -> "Kosong"
                        rest > 0 -> "$rest lagu berikutnya"
                        autoplay -> "Putar otomatis siap mengisi"
                        else -> "Tidak ada lagu berikutnya"
                    },
                    color = Tok.Label2, fontSize = 13.sp,
                )
            }
            Row(
                Modifier.height(40.dp).clip(CircleShape).background(Tok.Fill1).pressable { app.player.setAutoplay(!autoplay) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (autoplay) Tok.Accent else Tok.Label3))
                Text("Auto", color = if (autoplay) Tok.Label1 else Tok.Label3, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(Tok.Fill1).pressable { onClose() }, contentAlignment = Alignment.Center) {
                Icon(MIcons.Close, "Tutup antrian", tint = Tok.Label1, modifier = Modifier.size(18.dp))
            }
        }
        if (ps.queue.isEmpty()) {
            Text("Pilih lagu atau album, antriannya muncul di sini.", color = Tok.Label2, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp))
        }
        LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), modifier = Modifier.weight(1f).navigationBarsPadding()) {
            itemsIndexed(ps.queue, key = { i, t -> "${t.videoId}#$i" }) { i, t ->
                val current = i == ps.index
                if (i == ps.autoplayFrom) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).height(1.dp).background(Tok.Fill2))
                        Text("PUTAR OTOMATIS", color = Tok.Label3, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Box(Modifier.weight(1f).height(1.dp).background(Tok.Fill2))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (current) Tok.Fill2 else Color.Transparent).alpha(if (i < ps.index) 0.6f else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).pressable { app.player.jumpTo(i) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Tok.Fill2)) {
                            Artwork(t.thumbs, Modifier.size(44.dp))
                            if (current) Box(Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Eq(ps.playing) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist.orEmpty(), color = Tok.Label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(t.duration.orEmpty(), color = Tok.Label3, fontSize = 13.sp)
                    }
                    if (!current) {
                        Box(Modifier.size(40.dp).clip(CircleShape).pressable { app.player.removeFromQueue(i) }, contentAlignment = Alignment.Center) {
                            Icon(MIcons.Close, "Hapus ${t.title} dari antrian", tint = Tok.Label3, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
