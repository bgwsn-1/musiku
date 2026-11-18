# Musiku Android — liquid glass & animated backdrop notes

Follows `apple-music-motion-reference.md`. That doc is the raw Apple evidence; this one records
what was actually built in the app, what the ceilings are, and what is left.

## What shipped

### 1. Light + dark across the app (was dark-only)

The app hardcoded dark tokens; the web's `:root` is **light** and only goes dark under
`prefers-color-scheme: dark`. Both were ported.

- `theme/Theme.kt`: `MusikuColors` data class + `LightColors` / `DarkColors` (every value a direct
  port of the matching custom property), `LocalMusikuColors`, and a `Tok` object whose members are
  **`@Composable` getters** reading that local. `MusikuTheme` picks light/dark from
  `isSystemInDarkTheme()` and provides the local, so the whole tree re-reads on theme change with
  no call-site changes.
- `Ink` is unchanged: Now Playing is `force-dark` in the web too, so its fixed-dark white usage is
  correct.
- `MainActivity`: status/navigation bar icon contrast now follows the system theme (was hardcoded
  `SystemBarStyle.dark` → white icons, unreadable on light).
- `res/values{,-night}/themes.xml` + `colors.xml`: `windowBackground` is `#f2f2f7` light /
  `#0b0b0e` night, so launch no longer flashes black in light mode.

### 2. Glass surfaces follow the theme

`theme/Glass.kt`:

- fills, edges and shadow now come from `Tok` (so they flip with the theme);
- shadow uses `Tok.Shadow` (web light shadow is `rgba(30,30,70,.28)`, dark is `rgba(0,0,0,.7)`);
- Haze `backgroundColor` = `Tok.GlassBase` (`#ffffff` light / `#000000` dark) — the opaque floor
  under the blur, matching the web's `--surface-solid`;
- `glass`, `glassFallback`, `glassEdges`, `glassEdgeBrush` are now `@Composable`.

### 3. Tab bar: sliding active thumb

Web `MobileTabs` moves **one** pill behind the active label
(`transition duration-500 ease-[--ease-spring]`), which is the highlighted pill in the Apple Music
reference image. `BottomTabs` now does the same: a `BoxWithConstraints`-measured thumb animated
with a low-stiffness spring (overshooting, like the web's `cubic-bezier(0.34,1.4,0.64,1)`), and the
`Row` of tabs drawn over it.

### 4. Animated Now Playing backdrop (`components/AppleBackdrop.kt`)

Port of Apple `LyricsBackgroundLayerView` / web `AmbientWindow`:

- **three copies** of the artwork, each rotating counter/co-directionally on a linear infinite
  tween — 120 s (−360°), 90 s (+360°), 70 s (+360°) — with the web's B/C nudges
  (`-16%,-12%` and `+10%,+13%`);
- `setToSaturation(2.5f)` on every layer;
- `×1.3` overscan so rotation never reveals an edge;
- fade-in over **1000 ms** on `cubic-bezier(0, 0, 0.3, 1)` (Apple's `fadeIn`), opacity `0.6`;
- cost control: one small (96 px) Coil decode per track, upscaled — Apple's own "blur the *small*
  bitmap" trick — and the three layers are RenderNode transforms (no per-frame raster).

Replaced the previous static `blur(70.dp) × 1.6 scale` wash in `NowPlayingScreen.kt` and in the
lyrics/queue panel backdrop in `AppRoot.kt`.

## Ceiling: Haze 1.1.0 cannot saturate the backdrop

The web glass is `backdrop-filter: blur(28px) saturate(1.7)` (28/1.7 normal, 40/1.6 strong).
**Haze 1.1.0 has no API to colour-modify the sampled backdrop**, so the blur passes through at
source saturation. What we can and do reproduce exactly: the fill colours/alphas, the two edge
highlights, the shadow, the radii, and the blur radius.

Real Liquid Glass — refraction, rim highlight, edge outline, iOS-style continuous corners — exists
in **Haze 2.x** (`dev.chrisbanes.haze:haze-glass`, `GlassOptics`, `rimHighlight`, `edgeOutline`,
`ContinuousRoundedCornerShape`; changelog 2.1.0). It is **not** a drop-in:

| | project now | haze-glass 2.1.0 needs |
|---|---|---|
| Compose | 1.7.5 (bom 2024.10.01) | 1.12.2 |
| Kotlin | 2.0.20 | 2.4.21 |
| AGP | 8.5.2 | newer (Compose 1.12 toolchain) |

That is a full toolchain migration — a separate, higher-risk task. The web is the source of truth
for Musiku, and the web (CSS `backdrop-filter`) also cannot refract, so Haze 1.1.0 + correct fills
is honestly close to the web. Upgrading is a "truer to Apple" choice, not a web-parity one.

## Not done yet (available from the motion reference)

- Equaliser: still `tween(420 + i*130)`. Apple is 270 ms linear reverse + per-cycle random peak
  (web spec is `.9s ease-in-out` with 0.25/0.55 s delays — so web is the spec here).
- Nav push-zoom (Apple `fragment_open_enter`, 0.85→1.0 / 1.0→1.15, 300 ms).
- Apple's `MotionSettingsFragment` reduce-motion / "Animated Art" preferences.

## Verification

Built clean (`assembleDebug`, APK 69,185,605 bytes at
`~/Downloads/musiku-ui-parity.apk`). Dex confirms `AppleBackdrop`, `MusikuColors`,
`LocalMusikuColors`, `glassEdgeBrush`, `BottomTabs`. **Not yet seen on a device** — the phone's
wireless debugging is off, so the visual pass (blur strength, backdrop speed, thumb spring, light
contrast) is still pending.
