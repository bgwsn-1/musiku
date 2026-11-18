package app.musiku.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icons ported 1:1 from the web build's `src/components/Icons.tsx` — same 24x24 (or 28x24) viewBox,
 * path data, and per-path stroke settings, so the app chrome uses the web's custom glyphs instead
 * of Material's. Paths are black placeholders; `Icon(..., tint = ...)` recolours them.
 *
 * Regenerate with /tmp/opencode/gen_icons.py after touching Icons.tsx.
 */
object MIcons {

    val Play: ImageVector = ImageVector.Builder(
        name = "Play", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M8 5.14v13.72c0 .8.87 1.3 1.56.9l11.1-6.86a1.05 1.05 0 000-1.8L9.56 4.24A1.05 1.05 0 008 5.14z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Pause: ImageVector = ImageVector.Builder(
        name = "Pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M6 4h4v16H6zM14 4h4v16h-4z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Next: ImageVector = ImageVector.Builder(
        name = "Next", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M5 5.14v13.72c0 .8.87 1.3 1.56.9l9.44-5.83v5.07h3V4h-3v5.07L6.56 4.24A1.05 1.05 0 005 5.14z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Prev: ImageVector = ImageVector.Builder(
        name = "Prev", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M19 5.14v13.72c0 .8-.87 1.3-1.56.9L8 13.93V19H5V4h3v5.07l9.44-5.83c.69-.4 1.56.1 1.56.9z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Shuffle: ImageVector = ImageVector.Builder(
        name = "Shuffle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M18 4l3 3-3 3v-2h-2.6L12.9 10.7l-1.3-1.9L14.4 6H18V4zM4 6h4l7 12h3v-2l3 3-3 3v-2h-4L7 8H4V6zm0 12h3l1.3-2.2 1.3 1.9L8 18H4v-2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Repeat: ImageVector = ImageVector.Builder(
        name = "Repeat", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val RepeatOne: ImageVector = ImageVector.Builder(
        name = "RepeatOne", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2v-5h-1l-2 .5v1l1-.3V15h1z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Heart: ImageVector = ImageVector.Builder(
        name = "Heart", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 21s-7.5-4.9-10-9.2C.3 8.9 2 5.5 5.4 5.1c1.9-.2 3.8.8 4.9 2.4h1.4c1.1-1.6 3-2.6 4.9-2.4C19.9 5.5 21.7 8.9 20 11.8c-2.5 4.3-8 9.2-8 9.2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val HeartOutline: ImageVector = ImageVector.Builder(
        name = "HeartOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 21s-7.5-4.9-10-9.2C.3 8.9 2 5.5 5.4 5.1c1.9-.2 3.8.8 4.9 2.4h1.4c1.1-1.6 3-2.6 4.9-2.4C19.9 5.5 21.7 8.9 20 11.8c-2.5 4.3-8 9.2-8 9.2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Search: ImageVector = ImageVector.Builder(
        name = "Search", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4.0,11.0 a7.0,7.0 0 1,0 14.0,0 a7.0,7.0 0 1,0 -14.0,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
        addPath(
            PathParser().parsePathString("M20 20l-3.5-3.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Home: ImageVector = ImageVector.Builder(
        name = "Home", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 2l9 8h-2v12h-5v-7h-4v7H5V10H3l9-8z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Queue: ImageVector = ImageVector.Builder(
        name = "Queue", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M3 6h14v2H3zM3 11h14v2H3zM3 16h10v2H3zM19 8v7a3 3 0 11-2-2.8V8h2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Lyrics: ImageVector = ImageVector.Builder(
        name = "Lyrics", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4 3h16a1 1 0 011 1v13a1 1 0 01-1 1h-5l-3 4-3-4H4a1 1 0 01-1-1V4a1 1 0 011-1zm3 4v2h10V7H7zm0 4v2h7v-2H7z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Volume: ImageVector = ImageVector.Builder(
        name = "Volume", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4 9v6h3l5 4V5L7 9H4zm12.5 3a3.5 3.5 0 00-2-3.2v6.4a3.5 3.5 0 002-3.2zm2.5 0a6 6 0 00-3.5-5.4v1.9A4.09 4.09 0 0117 12a4.09 4.09 0 01-1.5 3.5v1.9A6 6 0 0019 12z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val VolumeMute: ImageVector = ImageVector.Builder(
        name = "VolumeMute", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4 9v6h3l5 4V5L7 9H4zm11 .9L16.4 11 18 9.4 19.4 11 21 9.4 19.6 12l1.4 1.6-1.4 1.4-1.6-1.6-1.6 1.6-1.4-1.4 1.6-1.6-1.6-1.4L15 9.9z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Speaker: ImageVector = ImageVector.Builder(
        name = "Speaker", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4 9v6h3l5 4V5L7 9H4z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Plus: ImageVector = ImageVector.Builder(
        name = "Plus", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 5v14M5 12h14").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Trash: ImageVector = ImageVector.Builder(
        name = "Trash", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M9 3h6v2h4v2H5V5h4V3zm1 5h2v9h-2V8zm4 0h2v9h-2V8zM7 9h1L7.5 19a1 1 0 001 1h7a1 1 0 001-1L16 9h1L16.5 19.5A2 2 0 0114.5 21h-5a2 2 0 01-2-1.5L7 9z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Music: ImageVector = ImageVector.Builder(
        name = "Music", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M20 3v10.5a3.5 3.5 0 11-2-3.2V6.2L11 7.8v8.7a3.5 3.5 0 11-2-3.2V6l13-3z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Close: ImageVector = ImageVector.Builder(
        name = "Close", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M6 6l12 12M18 6L6 18").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Back: ImageVector = ImageVector.Builder(
        name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M15 5l-7 7 7 7").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Mic: ImageVector = ImageVector.Builder(
        name = "Mic", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 2a3 3 0 013 3v6a3 3 0 01-6 0V5a3 3 0 013-3zm7 8v1a7 7 0 01-6 6.94V20h3v2H8v-2h3v-2.06A7 7 0 015 11v-1h2v1a5 5 0 0010 0v-1h2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Library: ImageVector = ImageVector.Builder(
        name = "Library", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M5 3h2v18H5zM9 3h2v18H9zM13.5 3.6l6.86 17.3-1.86.74L11.6 4.34 13.5 3.6z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Spinner: ImageVector = ImageVector.Builder(
        name = "Spinner", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 2a10 10 0 0110 10h-2a8 8 0 00-8-8V2z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Forward: ImageVector = ImageVector.Builder(
        name = "Forward", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M9 5l7 7-7 7").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val ChevronRight: ImageVector = ImageVector.Builder(
        name = "ChevronRight", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M9 5l7 7-7 7").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Ellipsis: ImageVector = ImageVector.Builder(
        name = "Ellipsis", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M3.2,12.0 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M10.2,12.0 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M17.2,12.0 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Disc: ImageVector = ImageVector.Builder(
        name = "Disc", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M3.0,12.0 a9.0,9.0 0 1,0 18.0,0 a9.0,9.0 0 1,0 -18.0,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
        addPath(
            PathParser().parsePathString("M9.5,12.0 a2.5,2.5 0 1,0 5.0,0 a2.5,2.5 0 1,0 -5.0,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val User: ImageVector = ImageVector.Builder(
        name = "User", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M8.0,8.0 a4.0,4.0 0 1,0 8.0,0 a4.0,4.0 0 1,0 -8.0,0 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
        addPath(
            PathParser().parsePathString("M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Rewind: ImageVector = ImageVector.Builder(
        name = "Rewind", defaultWidth = 28.dp, defaultHeight = 24.dp,
        viewportWidth = 28f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M14 5.14v13.72c0 .8-.87 1.3-1.56.9L2.3 13.9a1.05 1.05 0 010-1.8l10.14-5.86c.69-.4 1.56.1 1.56.9zM26 5.14v13.72c0 .8-.87 1.3-1.56.9L14.3 13.9a1.05 1.05 0 010-1.8l10.14-5.86c.69-.4 1.56.1 1.56.9z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val FastForward: ImageVector = ImageVector.Builder(
        name = "FastForward", defaultWidth = 28.dp, defaultHeight = 24.dp,
        viewportWidth = 28f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M2 5.14v13.72c0 .8.87 1.3 1.56.9L13.7 13.9a1.05 1.05 0 000-1.8L3.56 4.24C2.87 3.84 2 4.34 2 5.14zM14 5.14v13.72c0 .8.87 1.3 1.56.9L25.7 13.9a1.05 1.05 0 000-1.8L15.56 4.24c-.69-.4-1.56.1-1.56.9z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val Expand: ImageVector = ImageVector.Builder(
        name = "Expand", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Keyboard: ImageVector = ImageVector.Builder(
        name = "Keyboard", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M5.0,6.0 h14.0 a2.5,2.5 0 0,1 2.5,2.5 v7.0 a2.5,2.5 0 0,1 -2.5,2.5 h-14.0 a2.5,2.5 0 0,1 -2.5,-2.5 v-7.0 a2.5,2.5 0 0,1 2.5,-2.5 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
        )
        addPath(
            PathParser().parsePathString("M6 9.8h.01M9.5 9.8h.01M13 9.8h.01M16.5 9.8h.01M6 12.9h.01M9.5 12.9h.01M13 12.9h.01M16.5 12.9h.01M8 16h8").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Miter,
        )
    }.build()

    val Crossfade: ImageVector = ImageVector.Builder(
        name = "Crossfade", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4 7.5h13.2M14.2 4l3.6 3.5-3.6 3.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            PathParser().parsePathString("M20 16.5H6.8M9.8 13l-3.6 3.5 3.6 3.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val Share: ImageVector = ImageVector.Builder(
        name = "Share", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M12 14V3.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            PathParser().parsePathString("M8.5 6.5 12 3l3.5 3.5").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            PathParser().parsePathString("M5.5 11.5v7a2 2 0 002 2h9a2 2 0 002-2v-7").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val List: ImageVector = ImageVector.Builder(
        name = "List", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M4.2,5.0 h0.0 a1.2,1.2 0 0,1 1.2,1.2 v0.0 a1.2,1.2 0 0,1 -1.2,1.2 h-0.0 a1.2,1.2 0 0,1 -1.2,-1.2 v-0.0 a1.2,1.2 0 0,1 1.2,-1.2 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M8.85,5.4 h11.3 a0.85,0.85 0 0,1 0.85,0.85 v0.0 a0.85,0.85 0 0,1 -0.85,0.85 h-11.3 a0.85,0.85 0 0,1 -0.85,-0.85 v-0.0 a0.85,0.85 0 0,1 0.85,-0.85 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M4.2,10.8 h0.0 a1.2,1.2 0 0,1 1.2,1.2 v0.0 a1.2,1.2 0 0,1 -1.2,1.2 h-0.0 a1.2,1.2 0 0,1 -1.2,-1.2 v-0.0 a1.2,1.2 0 0,1 1.2,-1.2 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M8.85,11.15 h11.3 a0.85,0.85 0 0,1 0.85,0.85 v0.0 a0.85,0.85 0 0,1 -0.85,0.85 h-11.3 a0.85,0.85 0 0,1 -0.85,-0.85 v-0.0 a0.85,0.85 0 0,1 0.85,-0.85 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M4.2,16.6 h0.0 a1.2,1.2 0 0,1 1.2,1.2 v0.0 a1.2,1.2 0 0,1 -1.2,1.2 h-0.0 a1.2,1.2 0 0,1 -1.2,-1.2 v-0.0 a1.2,1.2 0 0,1 1.2,-1.2 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
        addPath(
            PathParser().parsePathString("M8.85,16.95 h11.3 a0.85,0.85 0 0,1 0.85,0.85 v0.0 a0.85,0.85 0 0,1 -0.85,0.85 h-11.3 a0.85,0.85 0 0,1 -0.85,-0.85 v-0.0 a0.85,0.85 0 0,1 0.85,-0.85 Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = SolidColor(Color.Black),
        )
    }.build()

    val LyricsQuote: ImageVector = ImageVector.Builder(
        name = "LyricsQuote", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            PathParser().parsePathString("M5 3h14a2.5 2.5 0 012.5 2.5v9A2.5 2.5 0 0119 17h-5.2l-3.6 4v-4H5a2.5 2.5 0 01-2.5-2.5v-9A2.5 2.5 0 015 3zM6.8 6.6h3.6v3.5l-1.4 3H7.4l1-3H6.8zm6.3 0h3.6v3.5l-1.4 3h-1.6l1-3h-1.6z").toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
    }.build()

}
