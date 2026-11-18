# Musiku — Android (Kotlin / Jetpack Compose)

Port dari web app Musiku. Target: Samsung Note 20, Android 12+ (minSdk 31).

## Build
1. Buka folder ini di **Android Studio** (Koala atau lebih baru), tunggu Gradle sync.
   (Wrapper cuma berisi properties — kalau Studio minta, biarkan dia download Gradle 8.9.)
2. Colok HP (USB debugging) → Run ▶.

## Isi
- `data/` — klien YouTube Music (InnerTube, search/home/album/artist/related), lirik lrclib, resolver stream (NewPipeExtractor), penyimpanan lokal (DataStore: favorit, playlist, recent, setelan)
- `player/` — Media3 `PlaybackService` (jalan di background + lock screen) + `PlayerController` (antrian, shuffle/repeat, autoplay, sleep timer, crossfade)
- `ui/` — Beranda, Cari, Pustaka, Album, Artis, Playlist, Now Playing, Lirik (animasi ripple), Antrian, mini player, tab bar, menu lagu, share card

## Catatan
- Kalau lagu gagal diputar: YouTube sering ganti sesuatu → naikkan versi `NewPipeExtractor` di `app/build.gradle.kts`.
- Crossfade = fade-out/fade-in antar lagu (bukan overlap dua player).
- Mode gelap saja.
