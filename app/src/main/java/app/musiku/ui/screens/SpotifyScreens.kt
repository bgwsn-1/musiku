package app.musiku.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.musiku.ui.LocalApp
import app.musiku.ui.LocalUi
import app.musiku.ui.components.Artwork
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.DockInset
import app.musiku.ui.components.EmptyState
import app.musiku.ui.components.Load
import app.musiku.ui.components.MusikuLoading
import app.musiku.ui.components.pressable
import app.musiku.ui.components.rememberLoad
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var verifying by remember { mutableStateOf(false) }

    fun extractSpDc(cookieStr: String?): String? {
        if (cookieStr.isNullOrBlank()) return null
        return cookieStr.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter("sp_dc=")
            ?.takeIf { it.isNotBlank() }
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Masuk ke Spotify",
                    color = Tok.Label1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (verifying) return
                                val u = url ?: return
                                if (!u.contains("open.spotify.com")) return
                                val dc = extractSpDc(cm.getCookie("https://open.spotify.com"))
                                    ?: extractSpDc(cm.getCookie("https://accounts.spotify.com"))
                                    ?: return
                                verifying = true
                                scope.launch {
                                    runCatching { app.spotifyAuth.setCookie(dc) }
                                        .onSuccess { user ->
                                            app.player.say("Masuk sebagai ${user.name}")
                                            onLoggedIn()
                                        }
                                        .onFailure { err ->
                                            verifying = false
                                            app.player.say(err.message ?: "Gagal masuk ke Spotify")
                                        }
                                }
                            }
                        }
                        loadUrl("https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F")
                    }
                },
            )
        }
        if (verifying) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MusikuLoading()
                    Text("Menghubungkan akun Spotify…", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        BackButton(onBack)
    }
}

@Composable
fun SpotifyLibraryScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val user by app.spotifyAuth.user.collectAsState()

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        if (user == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(MIcons.Link, null, tint = Tok.Accent, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Hubungkan Akun Spotify", color = Tok.Label1, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Masuk dengan akun Spotify untuk mengakses playlist privat, Liked Songs, dan menyinkronkannya ke Musiku.",
                    color = Tok.Label2,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Tok.Accent)
                        .pressable { onOpenLogin() }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text("Masuk dengan Spotify", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val u = user!!
            val load by rememberLoad(u.id) { app.spotifyAuth.myPlaylists() }
            LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = DockInset + 24.dp),
            ) {
                item(key = "header") {
                    Text(
                        "Spotify Saya",
                        color = Tok.Label1,
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 14.dp),
                    )
                }

                item(key = "account") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Tok.Surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(u.avatarUrl, Modifier.size(44.dp).clip(CircleShape).background(Tok.Fill1))
                        Column(Modifier.weight(1f)) {
                            Text(u.name, color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Terhubung ke Spotify", color = Tok.Label2, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Tok.Fill1)
                                .pressable {
                                    app.spotifyAuth.logout()
                                    app.player.say("Keluar dari Spotify")
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text("Keluar", color = Tok.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item(key = "liked-songs") {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Tok.Surface)
                            .pressable {
                                ui.spotifyImportOpen = true
                                app.importer.importLiked(app.spotifyAuth)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Tok.Accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MIcons.Heart, null, tint = Tok.Accent, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Liked Songs (Spotify)", color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Ketuk untuk impor lagu yang disukai", color = Tok.Label2, fontSize = 13.sp)
                        }
                        Icon(MIcons.Download, null, tint = Tok.Label3, modifier = Modifier.size(18.dp))
                    }
                }

                item(key = "playlists-title") {
                    Text(
                        "Playlist Anda",
                        color = Tok.Label1,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }

                when (val l = load) {
                    is Load.Loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { MusikuLoading() }
                    }
                    is Load.Err -> item(key = "err") {
                        EmptyState("Gagal memuat playlist", l.error.message ?: "Sesi mungkin kedaluwarsa. Coba masuk ulang.")
                    }
                    is Load.Ok -> {
                        if (l.value.isEmpty()) {
                            item(key = "empty") {
                                EmptyState("Belum ada playlist", "Buat playlist di Spotify lalu buka halaman ini lagi.")
                            }
                        } else {
                            items(l.value, key = { it.id }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressable {
                                            ui.spotifyImportOpen = true
                                            app.importer.import("https://open.spotify.com/playlist/${pl.id}")
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Artwork(pl.cover, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Tok.Fill1))
                                    Column(Modifier.weight(1f)) {
                                        Text(pl.name, color = Tok.Label1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val sub = listOfNotNull(
                                            if (!pl.isPublic) "Privat" else null,
                                            "${pl.trackCount} lagu",
                                            pl.owner,
                                        ).joinToString(" · ")
                                        Text(sub, color = Tok.Label2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Icon(MIcons.Download, "Impor playlist", tint = Tok.Label3, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        BackButton(onBack)
    }
}
