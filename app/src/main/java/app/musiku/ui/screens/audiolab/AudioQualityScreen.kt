// audio lab's quality page: streaming tiers per provider, download quality, and the orders songs play and download in

package app.musiku.ui.screens.audiolab

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.runtime.collectAsState
import app.musiku.module.AccountState
import app.musiku.module.ModuleFeature
import app.musiku.module.jiosaavn.JioSaavnModule
import app.musiku.module.applemusic.AppleMusicModule
import app.musiku.module.monochrome.MonochromeModule
import app.musiku.modules.SourceModules
import app.musiku.viewmodels.ModulesViewModel
import app.musiku.R
import app.musiku.constants.AudioQuality
import app.musiku.constants.AudioQualityKey
import app.musiku.eq.audio.AudioTap
import app.musiku.ui.theme.InterFontFamily
import app.musiku.utils.rememberEnumPreference
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private class QualityOption(
    val quality: AudioQuality,
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val format: Int,
)

private val YouTubeOptions = listOf(
    QualityOption(AudioQuality.AUTO, R.string.aq_quality_auto, R.string.aq_desc_auto, R.string.aq_fmt_adaptive),
    QualityOption(AudioQuality.HIGH, R.string.aq_quality_high, R.string.aq_desc_high, R.string.aq_fmt_high),
    QualityOption(AudioQuality.MEDIUM, R.string.aq_quality_medium, R.string.aq_desc_medium, R.string.aq_fmt_medium),
    QualityOption(AudioQuality.LOW, R.string.aq_quality_low, R.string.aq_desc_low, R.string.aq_fmt_low),
)

/** what the picture shows: the quality of whichever provider plays first */
private class Effective(val quality: AudioQuality, val title: String, val format: String)

@Composable
fun AudioQualityScreen(navController: NavController, modules: ModulesViewModel = app.musiku.ui.screens.audiolab.rememberModulesViewModel()) {
    val (stored, onQuality) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    val (downloadQuality, onDownloadQuality) = rememberEnumPreference(
        app.musiku.constants.DownloadQualityKey,
        defaultValue = app.musiku.constants.DownloadQuality.HIGH,
    )
    val quality = if (stored == AudioQuality.LOSSLESS) AudioQuality.HIGH else stored
    val context = LocalContext.current
    val metered = remember { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true }
    val tier = if (quality == AudioQuality.AUTO) (if (metered) AudioQuality.MEDIUM else AudioQuality.HIGH) else quality
    val option = YouTubeOptions.first { it.quality == tier }
    val effective = Effective(tier, stringResource(R.string.aq_youtube_tier, stringResource(option.title)), stringResource(option.format))

    AudioLabPage(
        title = LabTitle(stringResource(R.string.aq_title_first), stringResource(R.string.aq_title_second), firstSize = 40.sp, secondSize = 64.sp),
        onBack = { navController.navigateUp() },
        subtitle = "${effective.title} · ${effective.format}",
    ) {
        LabSheetTitle(stringResource(R.string.aq_section_streaming))
        LabGroup {
            ProviderCaption(
                logo = R.drawable.provider_youtube_music,
                name = "YouTube Music",
                status = stringResource(R.string.aq_youtube_status_on),
                on = true,
            )
            YouTubeOptions.forEach { ytOption ->
                QualityRow(stringResource(ytOption.title), stringResource(ytOption.description), selected = quality == ytOption.quality, enabled = true) { onQuality(ytOption.quality) }
            }
        }

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.aq_section_download))
        LabGroup {
            app.musiku.constants.DownloadQuality.entries.forEach { dlOption ->
                val (titleRes, descRes) = when (dlOption) {
                    app.musiku.constants.DownloadQuality.HIGH -> R.string.aq_quality_high to R.string.aq_dl_desc_high
                    app.musiku.constants.DownloadQuality.MEDIUM -> R.string.aq_quality_medium to R.string.aq_dl_desc_medium
                    app.musiku.constants.DownloadQuality.LOW -> R.string.aq_quality_low to R.string.aq_dl_desc_low
                }
                QualityRow(stringResource(titleRes), stringResource(descRes), selected = downloadQuality == dlOption, enabled = true) {
                    onDownloadQuality(dlOption)
                }
            }
        }
        Footnote(stringResource(R.string.quality_download_footnote))
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = LabColors.Secondary,
        modifier = Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp),
    )
}

internal fun providerLogo(id: String): Int? = when (id) {
    AppleMusicModule.Id -> R.drawable.provider_apple_music
    MonochromeModule.Id -> R.drawable.provider_monochrome
    SourceModules.YouTubeId -> R.drawable.provider_youtube_music
    else -> null
}

/** a provider's name over its qualities in the streaming group, dimmed while it's off */
@Composable
private fun ProviderCaption(logo: Int?, name: String, status: String, on: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.graphicsLayer { alpha = if (on) 1f else 0.45f }) { ProviderLogo(logo, size = 24, name = name) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Text(status, fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 14.sp, color = LabColors.Secondary)
        }
    }
}

// a provider with no logo of its own (jiosaavn, a module from a link) shows its first letter instead of a blank tile
@Composable
internal fun ProviderLogo(logo: Int?, size: Int, name: String? = null) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(Color(0xFFD9D9D9)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            androidx.compose.foundation.Image(
                painterResource(logo), null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        } else if (name != null) {
            Text(name.take(1).uppercase(), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = (size * 0.44f).sp, color = Color.Black.copy(alpha = 0.7f))
        }
    }
}

@Composable
internal fun QualityRow(title: String, description: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFF2A2A2E) else Color(0xFF111113), tween(200), label = "row")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(250), label = "enabled")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "check")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(description, fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 14.sp, color = LabColors.Secondary)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = check; scaleY = check; this.alpha = check },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
