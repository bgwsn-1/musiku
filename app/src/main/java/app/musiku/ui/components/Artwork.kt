package app.musiku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** Cover art. No URL (or the demo) → a soft gradient with a few translucent circles. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onError: (() -> Unit)? = null,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onError = { if (onError != null) onError() },
            modifier = modifier,
        )
    } else {
        Canvas(
            modifier.background(
                Brush.linearGradient(listOf(Color(0xFFF58FBF), Color(0xFFC4183C))),
            ),
        ) {
            val m = size.minDimension
            drawCircle(Color.White.copy(alpha = 0.18f), radius = m * 0.34f, center = Offset(size.width * 0.22f, size.height * 0.30f))
            drawCircle(Color.White.copy(alpha = 0.14f), radius = m * 0.30f, center = Offset(size.width * 0.80f, size.height * 0.38f))
            drawCircle(Color.White.copy(alpha = 0.12f), radius = m * 0.40f, center = Offset(size.width * 0.38f, size.height * 0.78f))
        }
    }
}
