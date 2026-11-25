package app.musiku.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.eq.EqualizerBand
import app.musiku.eq.EqualizerBiquadFilter
import app.musiku.eq.EqualizerPresets
import app.musiku.eq.EqualizerProfile
import app.musiku.ui.LocalApp
import app.musiku.ui.components.BackButton
import app.musiku.ui.components.PillSlider
import app.musiku.ui.components.pressable
import app.musiku.ui.theme.MIcons
import app.musiku.ui.theme.Tok
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MinGainDb = -12.0
private const val MaxGainDb = 12.0
private const val MinPreampDb = -12.0
private const val MaxPreampDb = 6.0

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val enabled by app.library.eqEnabled.collectAsState()
    val spatial by app.library.spatialAudio.collectAsState()
    val preset by app.library.eqPreset.collectAsState()
    val profile by app.library.eqProfile.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        val parsed = raw?.let { EqualizerPresets.parseAutoEq(it) }
        if (parsed == null) {
            app.player.say("Format file AutoEQ tidak dikenali")
            return@rememberLauncherForActivityResult
        }
        var name = "AutoEQ"
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val rawName = c.getString(idx)?.substringBeforeLast('.')?.trim()
                        if (!rawName.isNullOrBlank()) name = rawName.take(24)
                    }
                }
            }
        }
        app.library.importEqProfile(name, parsed)
        app.player.say("Profil $name diterapkan")
    }

    Box(Modifier.fillMaxSize().background(Tok.Window)) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "title") {
                Text(
                    "Equalizer",
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

            item(key = "master") {
                SettingsGroup("Audio") {
                    SettingsToggleRow(
                        icon = MIcons.Crossfade,
                        tint = SysPink,
                        title = "Aktifkan Equalizer",
                        subtitle = "Pemrosesan audio biquad parametrik langsung di player",
                        checked = enabled,
                        onCheckedChange = { app.library.setEqEnabled(it) },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = MIcons.Speaker,
                        tint = SysTeal,
                        title = "Spatial Audio",
                        subtitle = "Pelebaran stereo & crossfeed untuk suara lebih luas",
                        checked = spatial,
                        onCheckedChange = { app.library.setSpatialAudio(it) },
                    )
                }
            }

            item(key = "curve") {
                Spacer(Modifier.height(16.dp))
                FrequencyCurveCard(
                    profile = profile,
                    enabled = enabled,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "presets") {
                Spacer(Modifier.height(18.dp))
                PresetRow(
                    active = preset,
                    enabled = enabled,
                    onSelect = { app.library.selectEqPreset(it) },
                )
            }

            item(key = "bands") {
                Spacer(Modifier.height(18.dp))
                EqBandsCard(
                    bands = profile.bands,
                    enabled = enabled,
                    onGainChange = { index, db -> app.library.setEqBandGain(index, db) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "preamp") {
                Spacer(Modifier.height(22.dp))
                SettingsGroup("Preamp") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .alpha(if (enabled) 1f else 0.45f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Gain Preamp", color = Tok.Label1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                formatDb(profile.preamp),
                                color = Tok.Label2,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        val frac = ((profile.preamp - MinPreampDb) / (MaxPreampDb - MinPreampDb))
                            .toFloat()
                            .coerceIn(0f, 1f)
                        PillSlider(
                            fraction = frac,
                            enabled = enabled,
                            onChange = { f ->
                                val db = MinPreampDb + f * (MaxPreampDb - MinPreampDb)
                                val snapped = (db * 2.0).roundToInt() / 2.0
                                app.library.setEqPreamp(snapped)
                            },
                            barHeight = 8.dp,
                            trackColor = Tok.Fill2,
                            fillColor = Tok.Accent,
                        )
                    }
                }
            }

            item(key = "actions") {
                Spacer(Modifier.height(22.dp))
                SettingsGroup("Profil") {
                    SettingsRow(
                        icon = MIcons.Download,
                        tint = SysBlue,
                        title = "Impor profil AutoEQ (.txt)",
                        subtitle = "Mendukung format EqualizerAPO / AutoEQ",
                        onClick = { importLauncher.launch(arrayOf("text/*", "*/*")) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = MIcons.Trash,
                        tint = SysOrange,
                        title = "Reset ke Flat",
                        chevron = false,
                        onClick = {
                            app.library.resetEq()
                            app.player.say("Equalizer direset ke Flat")
                        },
                    )
                }
            }
        }
        BackButton(onBack)
    }
}

@Composable
private fun FrequencyCurveCard(
    profile: EqualizerProfile,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val curve = remember(profile) { computeResponseCurve(profile.bands) }
    val accent = Tok.Accent
    val grid = Tok.Fill2
    val zeroLine = Tok.Label3.copy(alpha = 0.45f)

    Box(
        modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Tok.Surface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Horizontal dB lines (+6, 0, -6)
            for (step in listOf(-6f, 0f, 6f)) {
                val y = midY - (step / 12f) * (h / 2f)
                drawLine(
                    color = if (step == 0f) zeroLine else grid,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (step == 0f) 1.5f else 1f,
                )
            }

            if (curve.size >= 2) {
                val linePath = Path()
                val fillPath = Path()
                curve.forEachIndexed { idx, db ->
                    val x = (idx.toFloat() / (curve.size - 1)) * w
                    val norm = (db.toFloat() / 12f).coerceIn(-1f, 1f)
                    val y = midY - norm * (h / 2f - 4f)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, midY)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, midY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    ),
                )
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    active: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val names = remember(active) {
        val base = EqualizerPresets.Presets.keys.toList()
        if (active in base) base else base + active
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            val selected = name == active
            val clickable = name in EqualizerPresets.Presets
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Tok.Accent else Tok.Fill1)
                    .border(1.dp, if (selected) Tok.Accent else Tok.Fill2, CircleShape)
                    .then(if (clickable && enabled) Modifier.pressable { onSelect(name) } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name,
                    color = if (selected) Color.White else Tok.Label1,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EqBandsCard(
    bands: List<EqualizerBand>,
    enabled: Boolean,
    onGainChange: (Int, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Show up to 10 interactive sliders (standard bands or imported bands)
    val shown = bands.take(10)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Tok.Surface)
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.45f),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        shown.forEachIndexed { index, band ->
            BandSliderColumn(
                band = band,
                enabled = enabled,
                onGainChange = { db -> onGainChange(index, db) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BandSliderColumn(
    band: EqualizerBand,
    enabled: Boolean,
    onGainChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(onGainChange)
    val trackColor = Tok.Fill2
    val accent = Tok.Accent
    val thumbColor = Color.White

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${band.gain.roundToInt()}",
            color = if (band.gain != 0.0) Tok.Label1 else Tok.Label3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(176.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun yToDb(y: Float): Double {
                        val f = 1f - (y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val raw = MinGainDb + f * (MaxGainDb - MinGainDb)
                        return (raw * 2.0).roundToInt() / 2.0
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = false
                        val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                            dragged = true
                            latest(yToDb(change.position.y))
                        }
                        if (dragged && slop != null) {
                            verticalDrag(slop.id) { change ->
                                change.consume()
                                latest(yToDb(change.position.y))
                            }
                        } else if (!dragged) {
                            val last = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (last != null && !last.isConsumed && !last.pressed) {
                                last.consume()
                                latest(yToDb(last.position.y))
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxHeight().width(10.dp)) {
                val trackW = 6.dp.toPx()
                val x = (size.width - trackW) / 2f
                val r = CornerRadius(trackW / 2f, trackW / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = r,
                )
                val norm = ((band.gain - MinGainDb) / (MaxGainDb - MinGainDb)).toFloat().coerceIn(0f, 1f)
                val thumbY = size.height * (1f - norm)
                val midY = size.height / 2f
                val top = minOf(thumbY, midY)
                val barH = kotlin.math.abs(thumbY - midY)
                if (barH > 0.5f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x, top),
                        size = Size(trackW, barH),
                        cornerRadius = r,
                    )
                }
                drawCircle(
                    color = thumbColor,
                    radius = 7.dp.toPx(),
                    center = Offset(size.width / 2f, thumbY),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatFreq(band.frequency),
            color = Tok.Label2,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun computeResponseCurve(bands: List<EqualizerBand>): List<Double> {
    val sr = 48000
    val filters = bands
        .filter { it.enabled && it.gain != 0.0 && it.frequency > 0.0 && it.frequency < sr / 2.0 && it.q > 0.0 }
        .map { EqualizerBiquadFilter(sr, it.frequency, it.gain, it.q, it.filterType) }
    val points = 64
    val minLog = ln(20.0)
    val maxLog = ln(20000.0)
    return List(points) { idx ->
        val freq = kotlin.math.exp(minLog + (idx.toDouble() / (points - 1)) * (maxLog - minLog))
        filters.sumOf { it.magnitudeDbAt(sr, freq) }.coerceIn(-12.0, 12.0)
    }
}

private fun formatFreq(hz: Double): String = when {
    hz >= 1000.0 -> {
        val k = hz / 1000.0
        if (k == k.toInt().toDouble()) "${k.toInt()}k" else "${(k * 10).roundToInt() / 10.0}k"
    }
    else -> "${hz.roundToInt()}"
}

private fun formatDb(db: Double): String {
    val rounded = (db * 10.0).roundToInt() / 10.0
    return if (rounded > 0) "+$rounded dB" else "$rounded dB"
}
