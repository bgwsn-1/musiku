package app.musiku.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The dock's glyphs, taken from Convx.
 *
 * Convx's Apple Music UI (`appleMusicUi`, on by default) does not draw the XML drawables in
 * `Screens.kt`; its floating bar maps each destination to an SF Symbol and renders it with
 * `com.composables.sfsymbols` in monochrome. These are those three ImageVectors, with the path
 * data and viewports copied verbatim from the library, so Musiku's dock shows the same artwork
 * Convx shows.
 */
object SfIcons {

    /** `SFHouseFill` — Convx `Screens.Home`. */
    val HouseFill: ImageVector = sfIcon("HouseFill", 29.8242f, 26.2207f) {
        addSfPath("M11.3086 24.4727L11.3086 16.9238C11.3086 16.377 11.6699 16.0254 12.2168 16.0254L17.2559 16.0254C17.8027 16.0254 18.1543 16.377 18.1543 16.9238L18.1543 24.4727ZM3.73047 23.5645C3.73047 25.2246 4.72656 26.2012 6.40625 26.2012L23.0566 26.2012C24.7363 26.2012 25.7324 25.2246 25.7324 23.5645L25.7324 13.0566L15.498 4.47266C15 4.04297 14.4336 4.0625 13.9551 4.47266L3.73047 13.0371ZM0.927734 13.0176C1.2207 13.0176 1.46484 12.8613 1.67969 12.6758L14.2578 2.11914C14.4043 1.99219 14.5703 1.93359 14.7266 1.93359C14.8926 1.93359 15.0586 1.99219 15.2051 2.11914L27.7832 12.6758C27.998 12.8613 28.2324 13.0176 28.5352 13.0176C29.1113 13.0176 29.4629 12.5977 29.4629 12.168C29.4629 11.9043 29.3555 11.6504 29.1113 11.4453L16.1426 0.566406C15.6934 0.185547 15.2148 0 14.7266 0C14.248 0 13.7695 0.185547 13.3203 0.566406L0.351562 11.4453C0.107422 11.6504 0 11.9043 0 12.168C0 12.5977 0.341797 13.0176 0.927734 13.0176ZM23.0566 6.9043L25.9277 9.32617L25.9277 3.76953C25.9277 3.28125 25.6055 2.96875 25.1172 2.96875L23.877 2.96875C23.3887 2.96875 23.0566 3.28125 23.0566 3.76953Z")
    }

    /** `SFMagnifyingglass` — Convx `Screens.Search`. */
    val Magnifyingglass: ImageVector = sfIcon("Magnifyingglass", 24.7656f, 24.6387f) {
        addSfPath("M0 9.88281C0 15.3223 4.43359 19.7559 9.88281 19.7559C12.0801 19.7559 14.1016 19.0332 15.7422 17.8125L22.1777 24.2578C22.4219 24.5117 22.7637 24.6387 23.1152 24.6387C23.8867 24.6387 24.4043 24.0527 24.4043 23.3203C24.4043 22.959 24.2773 22.6465 24.043 22.4023L17.6367 15.9668C18.9648 14.2969 19.7656 12.1777 19.7656 9.88281C19.7656 4.43359 15.332 0 9.88281 0C4.43359 0 0 4.43359 0 9.88281ZM1.82617 9.88281C1.82617 5.43945 5.43945 1.82617 9.88281 1.82617C14.3262 1.82617 17.9297 5.43945 17.9297 9.88281C17.9297 14.3164 14.3262 17.9297 9.88281 17.9297C5.43945 17.9297 1.82617 14.3164 1.82617 9.88281Z")
    }

    /** `SFMusicNoteSquareStackFill` — Convx `Screens.Library`. */
    val MusicNoteSquareStackFill: ImageVector = sfIcon("MusicNoteSquareStackFill", 26.3379f, 32.8418f) {
        addSfPath("M18.6363 3.53516L7.34027 3.53516C7.40282 2.64931 7.97728 2.12891 8.91602 2.12891L17.0605 2.12891C17.9993 2.12891 18.5737 2.64931 18.6363 3.53516Z")
        addSfPath("M21.1564 6.942C20.8094 6.88925 20.4418 6.86523 20.0586 6.86523L5.91797 6.86523C5.53117 6.86523 5.16046 6.88947 4.81065 6.94255C4.9444 5.91296 5.65963 5.33203 6.80664 5.33203L19.1699 5.33203C20.3167 5.33203 21.0241 5.91275 21.1564 6.942Z")
        addSfPath("M23.8574 12.7441L23.8574 26.9629C23.8574 29.4531 22.5781 30.7324 20.0586 30.7324L5.91797 30.7324C3.38867 30.7324 2.11914 29.4629 2.11914 26.9629L2.11914 12.7441C2.11914 10.2539 3.38867 8.98438 5.91797 8.98438L20.0586 8.98438C22.5781 8.98438 23.8574 10.2637 23.8574 12.7441ZM16.6797 13.5645L12.7539 14.541C12.0801 14.6875 11.9531 14.834 11.9531 15.6348L11.9531 21.9336C11.9531 22.5391 11.9043 22.6465 11.2109 22.8418L9.93164 23.1738C8.70117 23.4961 7.67578 24.2285 7.67578 25.5371C7.67578 26.6992 8.55469 27.5293 9.90234 27.5293C11.8457 27.5293 13.125 26.1523 13.125 24.1992L13.125 19.3066C13.125 18.8184 13.2324 18.6816 13.5254 18.6133L16.7383 17.8516C17.3926 17.7051 17.5879 17.5391 17.5879 16.7578L17.5879 14.1406C17.5879 13.6133 17.4023 13.3887 16.6797 13.5645Z")
    }
}

/**
 * Same construction as the library's `sfIcon`: the longest side is 24dp, so a non-square symbol
 * keeps Apple's aspect ratio instead of being stretched into a 24x24 box.
 */
private inline fun sfIcon(
    name: String,
    viewportWidth: Float,
    viewportHeight: Float,
    block: ImageVector.Builder.() -> Unit,
): ImageVector {
    val scale = 24f / maxOf(viewportWidth, viewportHeight)
    return ImageVector.Builder(
        name = name,
        defaultWidth = (viewportWidth * scale).dp,
        defaultHeight = (viewportHeight * scale).dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).apply(block).build()
}

/** Same as the library's `addSfPath`: monochrome symbols fill at 85% opacity. */
private fun ImageVector.Builder.addSfPath(pathData: String) {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
        fillAlpha = 0.85f,
    )
}
