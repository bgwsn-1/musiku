package app.musiku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.musiku.R
import app.musiku.data.ThemeMode

/** Convx's Latin typeface: the same four SF Pro Text weights it ships. */
val SfPro = FontFamily(
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_bold, FontWeight.Bold),
)

/** Every Material style carries [SfPro], so components and bare `Text` share one typeface. */
private val MusikuTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = SfPro),
        displayMedium = t.displayMedium.copy(fontFamily = SfPro),
        displaySmall = t.displaySmall.copy(fontFamily = SfPro),
        headlineLarge = t.headlineLarge.copy(fontFamily = SfPro),
        headlineMedium = t.headlineMedium.copy(fontFamily = SfPro),
        headlineSmall = t.headlineSmall.copy(fontFamily = SfPro),
        titleLarge = t.titleLarge.copy(fontFamily = SfPro),
        titleMedium = t.titleMedium.copy(fontFamily = SfPro),
        titleSmall = t.titleSmall.copy(fontFamily = SfPro),
        bodyLarge = t.bodyLarge.copy(fontFamily = SfPro),
        bodyMedium = t.bodyMedium.copy(fontFamily = SfPro),
        bodySmall = t.bodySmall.copy(fontFamily = SfPro),
        labelLarge = t.labelLarge.copy(fontFamily = SfPro),
        labelMedium = t.labelMedium.copy(fontFamily = SfPro),
        labelSmall = t.labelSmall.copy(fontFamily = SfPro),
    )
}

/** Tokens copied from the web build's `.force-dark` block (globals.css). */
object Ink {
    val Window = Color(0xFF0B0B0E)
    val Label1 = Color(0xFFF5F5F7)
    val Label2 = Color(0xFFC0C0C7)
    val Label3 = Color(0xFF9A9AA3)
    val Accent = Color(0xFFFF4D63)
    val Fill1 = Color.White.copy(alpha = 0.10f)
    val Fill2 = Color.White.copy(alpha = 0.18f)
    val Track = Color.White.copy(alpha = 0.34f)
}

/**
 * The app palette. The web build ships two sets: `:root` (light, the default) and the
 * `prefers-color-scheme: dark` block; Now Playing additionally forces dark via `.force-dark`
 * (which is why [Ink] exists separately and stays fixed). Every value below is a direct port
 * of the matching custom property.
 */
@Immutable
data class MusikuColors(
    val Window: Color,
    val Surface: Color,
    val Fill1: Color,
    val Fill2: Color,
    val Hairline: Color,
    val Track: Color,
    val Label1: Color,
    val Label2: Color,
    val Label3: Color,
    val Accent: Color,
    val AccentFill: Color,
    val AccentMuted: Color,
    // glass (globals.css --glass-*): fill painted over the blur, edges, shadow
    val GlassFill: Color,
    val GlassFillStrong: Color,
    val GlassEdgeTop: Color,
    val GlassEdge: Color,
    /** Opaque base drawn *under* the blur (web has no equivalent; it composites over the page). */
    val GlassBase: Color,
    val Shadow: Color,
)

// globals.css :root  — the light default.
val LightColors = MusikuColors(
    Window = Color(0xFFF2F2F7),
    Surface = Color(0xFFFBFBFD),
    Fill1 = Color(0x143C3C43), // rgba(60,60,67,.08)
    Fill2 = Color(0x243C3C43), // rgba(60,60,67,.14)
    Hairline = Color(0x243C3C43), // rgba(60,60,67,.14)
    Track = Color(0x383C3C43), // rgba(60,60,67,.22)
    Label1 = Color(0xFF1C1C1E),
    Label2 = Color(0xFF55555D),
    Label3 = Color(0xFF6C6C75),
    Accent = Color(0xFFD60F33),
    AccentFill = Color(0xFFD91A36),
    AccentMuted = Color(0x1FD91A36), // rgba(217,26,54,.12)
    GlassFill = Color(0x94FFFFFF), // rgba(255,255,255,.58)
    GlassFillStrong = Color(0xCCFAFAFD), // rgba(250,250,253,.8)
    GlassEdgeTop = Color(0xF2FFFFFF), // rgba(255,255,255,.95)
    GlassEdge = Color(0x1A3C3C43), // rgba(60,60,67,.1)
    GlassBase = Color(0xFFFFFFFF),
    Shadow = Color(0x471E1E46), // rgba(30,30,70,.28)
)

// globals.css  —  prefers-color-scheme: dark
val DarkColors = MusikuColors(
    Window = Color(0xFF0B0B0E),
    Surface = Color(0xFF1C1C21),
    Fill1 = Color.White.copy(alpha = 0.08f),
    Fill2 = Color.White.copy(alpha = 0.14f),
    Hairline = Color.White.copy(alpha = 0.10f),
    Track = Color.White.copy(alpha = 0.22f),
    Label1 = Color(0xFFF5F5F7),
    Label2 = Color(0xFFB3B3BA),
    Label3 = Color(0xFF8F8F98),
    Accent = Color(0xFFFF4D63),
    AccentFill = Color(0xFFD91A36),
    AccentMuted = Color(0x29FF4D63), // rgba(255,77,99,.16)
    GlassFill = Color(0x8F222228), // rgba(34,34,40,.56)
    GlassFillStrong = Color(0xCC1C1C21), // rgba(28,28,33,.8)
    GlassEdgeTop = Color(0x33FFFFFF), // rgba(255,255,255,.2)
    GlassEdge = Color(0x12FFFFFF), // rgba(255,255,255,.07)
    GlassBase = Color(0xFF000000),
    Shadow = Color(0xFF000000),
)

val LocalMusikuColors = staticCompositionLocalOf { DarkColors }

/** Resolved light/dark state, so chrome can follow the chosen theme instead of the system switch. */
val LocalIsDark = staticCompositionLocalOf { true }

/**
 * App-wide palette tokens, resolved from [LocalMusikuColors]. These are `@Composable` getters
 * (every call site sits in a composable), so the whole tree simply re-reads them when the
 * system light/dark setting changes — no call site needs to know which theme is active.
 */
object Tok {
    val IsDark: Boolean @Composable get() = LocalIsDark.current
    val Window: Color @Composable get() = LocalMusikuColors.current.Window
    val Surface: Color @Composable get() = LocalMusikuColors.current.Surface
    val Fill1: Color @Composable get() = LocalMusikuColors.current.Fill1
    val Fill2: Color @Composable get() = LocalMusikuColors.current.Fill2
    val Hairline: Color @Composable get() = LocalMusikuColors.current.Hairline
    val Track: Color @Composable get() = LocalMusikuColors.current.Track
    val Label1: Color @Composable get() = LocalMusikuColors.current.Label1
    val Label2: Color @Composable get() = LocalMusikuColors.current.Label2
    val Label3: Color @Composable get() = LocalMusikuColors.current.Label3
    val Accent: Color @Composable get() = LocalMusikuColors.current.Accent
    val AccentFill: Color @Composable get() = LocalMusikuColors.current.AccentFill
    val AccentMuted: Color @Composable get() = LocalMusikuColors.current.AccentMuted

    val GlassFill: Color @Composable get() = LocalMusikuColors.current.GlassFill
    val GlassFillStrong: Color @Composable get() = LocalMusikuColors.current.GlassFillStrong
    val GlassEdgeTop: Color @Composable get() = LocalMusikuColors.current.GlassEdgeTop
    val GlassEdge: Color @Composable get() = LocalMusikuColors.current.GlassEdge
    val GlassBase: Color @Composable get() = LocalMusikuColors.current.GlassBase
    val Shadow: Color @Composable get() = LocalMusikuColors.current.Shadow

    /** Opaque fallback for surfaces that must not be see-through (legacy call sites). */
    val Glass: Color @Composable get() = LocalMusikuColors.current.GlassFillStrong

    /** Flat, fully opaque fill used by low-device mode (dark: black, light: white for legibility). */
    val SolidGlass: Color @Composable get() = if (IsDark) Color(0xFF000000) else Color(0xFFFFFFFF)
}

@Composable
fun MusikuTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalMusikuColors provides colors,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    background = colors.Window,
                    surface = colors.Surface,
                    onBackground = colors.Label1,
                    onSurface = colors.Label1,
                    primary = colors.Accent,
                )
            } else {
                lightColorScheme(
                    background = colors.Window,
                    surface = colors.Surface,
                    onBackground = colors.Label1,
                    onSurface = colors.Label1,
                    primary = colors.Accent,
                )
            },
            typography = MusikuTypography,
            content = content,
        )
    }
}
