package app.musiku.constants

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.audioLabDataStore: DataStore<Preferences> by preferencesDataStore(name = "audiolab_prefs")

enum class AudioQuality { AUTO, HIGH, MEDIUM, LOW, LOSSLESS }
enum class DownloadQuality { HIGH, MEDIUM, LOW }
val DownloadQualityKey = stringPreferencesKey("download_quality")
enum class AudioPassMode(val minSdk: Int) {
    SONG_RATE(34),
    BIT_PERFECT(34),
    DIRECT_USB(24),
    EXCLUSIVE_USB(27),
    EXCLUSIVE_DAC(27),
    ;

    val exclusive: Boolean get() = this != SONG_RATE

    companion object {
        val Default: AudioPassMode
            get() = if (android.os.Build.VERSION.SDK_INT >= 34) SONG_RATE else DIRECT_USB
    }
}
enum class CrossmixMode { OFF, AUTO_CROSSFADE, MANUAL_CROSSFADE, CROSSMIX }
enum class TransportEffect { OFF, TURNTABLE, TAPE, VINYL }

val DefaultTransitionMode = CrossmixMode.OFF

val AudioQualityKey = stringPreferencesKey("audio_quality")
val AudioNormalizationKey = booleanPreferencesKey("audio_normalization")
val AudioPassModeKey = stringPreferencesKey("audio_pass_mode")
val AudioPassFixedVolumeKey = booleanPreferencesKey("audio_pass_fixed_volume")
val HiFiResamplerKey = booleanPreferencesKey("hifi_resampler")
val HiResOutputKey = booleanPreferencesKey("hi_res_output")
val PreferUsbDacKey = booleanPreferencesKey("prefer_usb_dac")
val UsbDriverKey = stringPreferencesKey("usb_driver")
val UsbPassthroughKey = booleanPreferencesKey("usb_passthrough")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfade_enabled")
val CrossmixModeKey = stringPreferencesKey("crossmix_mode")
val TransportEffectKey = stringPreferencesKey("transport_effect")
val SkipSilenceKey = booleanPreferencesKey("skip_silence")
val MusicHapticsEnabledKey = booleanPreferencesKey("music_haptics_enabled")
val HideSettingsDescriptionsKey = booleanPreferencesKey("hide_settings_descriptions")

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) defaultValue else enumValues<T>().firstOrNull { it.name.equals(this, ignoreCase = true) } ?: defaultValue

fun transitionModeOf(prefs: Preferences): CrossmixMode =
    prefs[CrossmixModeKey].toEnum(
        if (prefs[CrossfadeEnabledKey] == true) CrossmixMode.AUTO_CROSSFADE else DefaultTransitionMode
    )
