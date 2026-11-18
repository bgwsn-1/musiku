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
 * Stroke glyphs from the web build's `PlayerMenu.tsx` (24x24, stroke 1.7, round caps/joins),
 * which draws its own line icons instead of using the filled `Icons.tsx` set.
 *
 * Paths are re-tokenised: the web writes glued arc flags (`... 0 0112 7.4 ...`), which Compose's
 * `PathParser` mis-reads, and mixes `fill` into the stroke set. Regenerate with
 * /tmp/opencode/gen_menu_icons.py after touching PlayerMenu.tsx.
 */
object MMenuIcons {
    private fun strokeIcon(
        name: String,
        vararg paths: String,
        strokeWidth: Float = 1.7f,
        fill: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                PathParser().parsePathString(d).toNodes(),
                pathFillType = PathFillType.NonZero,
                fill = if (fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()


    /** `Heart` (filled / outline variant) */
    private val HeartPaths = arrayOf(
        "M 12 20 s -7.5 -4.6 -7.5 -10 A 4.3 4.3 0 0 1 12 7.4 A 4.3 4.3 0 0 1 19.5 10 c 0 5.4 -7.5 10 -7.5 10 Z",
    )

    val Heart: ImageVector = strokeIcon("Heart", *HeartPaths)
    val HeartFilled: ImageVector = strokeIcon("HeartFilled", *HeartPaths, fill = true)

    /** `Share` */
    private val SharePaths = arrayOf(
        "M 12 15 V 3.5 M 8 7 l 4 -4 l 4 4 M 6.5 10.5 H 6 A 2 2 0 0 0 4 12.5 v 6 A 2 2 0 0 0 6 20.5 h 12 a 2 2 0 0 0 2 -2 v -6 a 2 2 0 0 0 -2 -2 h -.5",
    )

    val Share: ImageVector = strokeIcon("Share", *SharePaths)

    /** `Next` */
    private val NextPaths = arrayOf(
        "M 3.5 6 h 3 M 3.5 12 h 17 M 3.5 18 h 17 M 10 6 h 10.5",
        "M 4 3.5 l 2.6 2.5 L 4 8.5",
    )

    val Next: ImageVector = strokeIcon("Next", *NextPaths)

    /** `Queue` */
    private val QueuePaths = arrayOf(
        "M 3.5 6 h 17 M 3.5 12 h 17 M 3.5 18 h 8",
        "M 17 15.5 v 5 M 14.5 18 h 5",
    )

    val Queue: ImageVector = strokeIcon("Queue", *QueuePaths)

    /** `Plus` */
    private val PlusPaths = arrayOf(
        "M 3.0 12.0 a 9.0 9.0 0 1 0 18.0 0 a 9.0 9.0 0 1 0 -18.0 0 Z",
        "M 12 8 v 8 M 8 12 h 8",
    )

    val Plus: ImageVector = strokeIcon("Plus", *PlusPaths)

    /** `Album` */
    private val AlbumPaths = arrayOf(
        "M 8.6 8.5 h 6.8 a 1.6 1.6 0 0 1 1.6 1.6 v 8.8 a 1.6 1.6 0 0 1 -1.6 1.6 h -6.8 a 1.6 1.6 0 0 1 -1.6 -1.6 v -8.8 a 1.6 1.6 0 0 1 1.6 -1.6 Z",
        "M 8.5 5.5 h 7 M 9.8 3 h 4.4",
    )

    val Album: ImageVector = strokeIcon("Album", *AlbumPaths)

    /** `Mic` */
    private val MicPaths = arrayOf(
        "M 14.2 4.3 a 3.3 3.3 0 0 1 4.7 4.7 l -5.4 5.4 l -4.7 -4.7 l 5.4 -5.4 Z M 9.2 13.3 l -5.4 6.9 M 7.5 10.3 l 6.2 6.2",
    )

    val Mic: ImageVector = strokeIcon("Mic", *MicPaths)

    /** `Info` */
    private val InfoPaths = arrayOf(
        "M 3.0 12.0 a 9.0 9.0 0 1 0 18.0 0 a 9.0 9.0 0 1 0 -18.0 0 Z",
        "M 12 11 v 5.5 M 12 7.6 v .1",
    )

    val Info: ImageVector = strokeIcon("Info", *InfoPaths)

    /** `Moon` */
    private val MoonPaths = arrayOf(
        "M 19.5 14.2 A 7.8 7.8 0 0 1 9.8 4.5 a 8 8 0 1 0 9.7 9.7 Z",
        "M 15 3 h 4 l -4 4.2 h 4",
    )

    val Moon: ImageVector = strokeIcon("Moon", *MoonPaths)

    /** `Cross` */
    private val CrossPaths = arrayOf(
        "M 3.5 12.0 a 5.5 5.5 0 1 0 11.0 0 a 5.5 5.5 0 1 0 -11.0 0 Z",
        "M 9.5 12.0 a 5.5 5.5 0 1 0 11.0 0 a 5.5 5.5 0 1 0 -11.0 0 Z",
    )

    val Cross: ImageVector = strokeIcon("Cross", *CrossPaths)

    /** `Back` */
    private val BackPaths = arrayOf(
        "M 15 5 l -7 7 l 7 7",
    )

    val Back: ImageVector = strokeIcon("Back", *BackPaths)

    /** `Check` */
    private val CheckPaths = arrayOf(
        "M 5 12.5 l 4.5 4.5 L 19 7.5",
    )

    val Check: ImageVector = strokeIcon("Check", *CheckPaths)

}
