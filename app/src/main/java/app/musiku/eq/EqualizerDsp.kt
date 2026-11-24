package app.musiku.eq

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class EqualizerFilterType {
    PK,
    LSC,
    HSC,
    LPQ,
    HPQ,
}

data class EqualizerBand(
    val frequency: Double,
    val gain: Double,
    val q: Double = 1.41,
    val filterType: EqualizerFilterType = EqualizerFilterType.PK,
    val enabled: Boolean = true,
)

data class EqualizerProfile(
    val preamp: Double = 0.0,
    val bands: List<EqualizerBand> = EqualizerPresets.flatBands(),
)

/**
 * Stateful two-channel RBJ biquad filter ported from Convx.
 */
class EqualizerBiquadFilter(
    sampleRate: Int,
    frequency: Double,
    gain: Double,
    q: Double = 1.41,
    filterType: EqualizerFilterType = EqualizerFilterType.PK,
) {
    private var a1 = 0.0
    private var a2 = 0.0
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0

    private var x1Left = 0.0
    private var x2Left = 0.0
    private var y1Left = 0.0
    private var y2Left = 0.0
    private var x1Right = 0.0
    private var x2Right = 0.0
    private var y1Right = 0.0
    private var y2Right = 0.0

    init {
        require(sampleRate > 0)
        require(frequency.isFinite() && frequency > 0.0 && frequency < sampleRate / 2.0)
        require(gain.isFinite())
        require(q.isFinite() && q > 0.0)

        val omega = 2.0 * PI * frequency / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)
        when (filterType) {
            EqualizerFilterType.PK -> {
                val amplitude = 10.0.pow(gain / 40.0)
                setCoefficients(
                    b0 = 1.0 + alpha * amplitude,
                    b1 = -2.0 * cosOmega,
                    b2 = 1.0 - alpha * amplitude,
                    a0 = 1.0 + alpha / amplitude,
                    a1 = -2.0 * cosOmega,
                    a2 = 1.0 - alpha / amplitude,
                )
            }
            EqualizerFilterType.LSC -> setShelfCoefficients(
                amplitude = 10.0.pow(gain / 40.0),
                sinOmega = sinOmega,
                cosOmega = cosOmega,
                isLowShelf = true,
            )
            EqualizerFilterType.HSC -> setShelfCoefficients(
                amplitude = 10.0.pow(gain / 40.0),
                sinOmega = sinOmega,
                cosOmega = cosOmega,
                isLowShelf = false,
            )
            EqualizerFilterType.LPQ -> setCoefficients(
                b0 = (1.0 - cosOmega) / 2.0,
                b1 = 1.0 - cosOmega,
                b2 = (1.0 - cosOmega) / 2.0,
                a0 = 1.0 + alpha,
                a1 = -2.0 * cosOmega,
                a2 = 1.0 - alpha,
            )
            EqualizerFilterType.HPQ -> setCoefficients(
                b0 = (1.0 + cosOmega) / 2.0,
                b1 = -(1.0 + cosOmega),
                b2 = (1.0 + cosOmega) / 2.0,
                a0 = 1.0 + alpha,
                a1 = -2.0 * cosOmega,
                a2 = 1.0 - alpha,
            )
        }
    }

    fun processSample(input: Double): Double {
        val output = b0 * input + b1 * x1Left + b2 * x2Left - a1 * y1Left - a2 * y2Left
        x2Left = x1Left
        x1Left = input
        y2Left = y1Left
        y1Left = output
        return output
    }

    fun processRightSample(input: Double): Double {
        val output = b0 * input + b1 * x1Right + b2 * x2Right - a1 * y1Right - a2 * y2Right
        x2Right = x1Right
        x1Right = input
        y2Right = y1Right
        y1Right = output
        return output
    }

    /** Magnitude response in dB at [testFreq] for drawing the EQ frequency curve. */
    fun magnitudeDbAt(sampleRate: Int, testFreq: Double): Double {
        val omega = 2.0 * PI * testFreq / sampleRate
        val cos1 = cos(omega)
        val sin1 = sin(omega)
        val cos2 = cos(2.0 * omega)
        val sin2 = sin(2.0 * omega)
        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1.0 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)
        val mag2 = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm).coerceAtLeast(1e-18)
        return 10.0 * log10(mag2.coerceAtLeast(1e-18))
    }

    fun reset() {
        x1Left = 0.0; x2Left = 0.0; y1Left = 0.0; y2Left = 0.0
        x1Right = 0.0; x2Right = 0.0; y1Right = 0.0; y2Right = 0.0
    }

    private fun setShelfCoefficients(
        amplitude: Double,
        sinOmega: Double,
        cosOmega: Double,
        isLowShelf: Boolean,
    ) {
        val alpha = sinOmega / 2.0 * sqrt(2.0)
        val aPlusOne = amplitude + 1.0
        val aMinusOne = amplitude - 1.0
        val twoRootAlpha = 2.0 * sqrt(amplitude) * alpha
        if (isLowShelf) {
            setCoefficients(
                b0 = amplitude * (aPlusOne - aMinusOne * cosOmega + twoRootAlpha),
                b1 = 2.0 * amplitude * (aMinusOne - aPlusOne * cosOmega),
                b2 = amplitude * (aPlusOne - aMinusOne * cosOmega - twoRootAlpha),
                a0 = aPlusOne + aMinusOne * cosOmega + twoRootAlpha,
                a1 = -2.0 * (aMinusOne + aPlusOne * cosOmega),
                a2 = aPlusOne + aMinusOne * cosOmega - twoRootAlpha,
            )
        } else {
            setCoefficients(
                b0 = amplitude * (aPlusOne + aMinusOne * cosOmega + twoRootAlpha),
                b1 = -2.0 * amplitude * (aMinusOne + aPlusOne * cosOmega),
                b2 = amplitude * (aPlusOne + aMinusOne * cosOmega - twoRootAlpha),
                a0 = aPlusOne - aMinusOne * cosOmega + twoRootAlpha,
                a1 = 2.0 * (aMinusOne - aPlusOne * cosOmega),
                a2 = aPlusOne - aMinusOne * cosOmega - twoRootAlpha,
            )
        }
    }

    private fun setCoefficients(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        require(a0.isFinite() && a0 != 0.0)
        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = a1 / a0
        this.a2 = a2 / a0
    }
}

/**
 * Media3 [AudioProcessor] applying parametric EQ via RBJ biquads + soft clipping.
 */
@UnstableApi
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET

    @Volatile
    private var equalizerEnabled = false

    private var processingBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var filters: List<EqualizerBiquadFilter> = emptyList()
    private var preampGain: Double = 1.0
    private var pendingProfile: EqualizerProfile? = null

    @Synchronized
    fun applyProfile(profile: EqualizerProfile) {
        pendingProfile = profile
        equalizerEnabled = true
        if (sampleRate <= 0 || encoding != C.ENCODING_PCM_16BIT || channelCount !in 1..2) {
            return
        }
        preampGain = 10.0.pow(profile.preamp / 20.0)
        createFilters(profile.bands)
        filters.forEach { it.reset() }
        Log.d(TAG, "Applied EQ profile: ${filters.size} active bands, preamp=${profile.preamp}dB")
    }

    @Synchronized
    fun disable() {
        equalizerEnabled = false
        filters = emptyList()
        preampGain = 1.0
        pendingProfile = null
    }

    private fun createFilters(bands: List<EqualizerBand>) {
        if (sampleRate <= 0) return
        filters = bands
            .filter { it.enabled && it.gain != 0.0 && it.frequency > 0.0 && it.frequency < sampleRate / 2.0 && it.q > 0.0 }
            .map { band ->
                EqualizerBiquadFilter(
                    sampleRate = sampleRate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType,
                )
            }
    }

    @Synchronized
    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    private fun applyFormat() {
        sampleRate = pendingFormat.sampleRate
        channelCount = pendingFormat.channelCount
        encoding = pendingFormat.encoding
        if (!supports(pendingFormat)) {
            filters = emptyList()
            return
        }
        pendingProfile?.let { profile ->
            preampGain = 10.0.pow(profile.preamp / 20.0)
            createFilters(profile.bands)
        }
    }

    private fun supports(format: AudioProcessor.AudioFormat): Boolean =
        format.encoding == C.ENCODING_PCM_16BIT && format.sampleRate > 0 && format.channelCount in 1..2

    override fun isActive(): Boolean = equalizerEnabled && supports(pendingFormat)

    @Synchronized
    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) return
        if (!equalizerEnabled || channelCount !in 1..2 ||
            encoding != C.ENCODING_PCM_16BIT || (filters.isEmpty() && preampGain == 1.0)
        ) {
            outputBuffer = inputBuffer.slice().order(ByteOrder.nativeOrder())
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val output = replaceOutputBuffer(inputSize)
        processAudioBuffer16Bit(inputBuffer, output)
        output.flip()
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (processingBuffer.capacity() < size) {
            processingBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            processingBuffer.clear()
        }
        outputBuffer = processingBuffer
        return outputBuffer
    }

    private fun softClip(x: Double): Double {
        val threshold = 0.9
        return when {
            x > threshold -> threshold + (1 - threshold) * tanh((x - threshold) / (1 - threshold))
            x < -threshold -> -(threshold + (1 - threshold) * tanh((-x - threshold) / (1 - threshold)))
            else -> x
        }
    }

    private fun toPcm16Sample(sample: Double): Short =
        (softClip(sample) * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

    private fun processAudioBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        val frames = input.remaining() / (Short.SIZE_BYTES * channelCount)
        repeat(frames) {
            var left = input.short / 32768.0
            var right = if (channelCount == 2) input.short / 32768.0 else 0.0
            for (index in filters.indices) {
                val filter = filters[index]
                left = filter.processSample(left)
                if (channelCount == 2) right = filter.processRightSample(right)
            }
            output.putShort(toPcm16Sample(left * preampGain))
            if (channelCount == 2) output.putShort(toPcm16Sample(right * preampGain))
        }
        output.put(input)
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    @Deprecated("Deprecated in Java")
    @Synchronized
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        applyFormat()
        filters.forEach { it.reset() }
    }

    @Synchronized
    override fun reset() {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        @Suppress("DEPRECATION")
        flush()
        processingBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        filters.forEach { it.reset() }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    companion object {
        private const val TAG = "MusikuEQ"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}

object EqualizerPresets {
    val Frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    fun flatBands(): List<EqualizerBand> = Frequencies.map { EqualizerBand(it, 0.0) }

    val Presets: Map<String, EqualizerProfile> = linkedMapOf(
        "Flat" to EqualizerProfile(0.0, flatBands()),
        "Bass Boost" to profile(-3.0, 6.0, 5.0, 3.5, 1.5, 0.0, 0.0, 0.0, 0.0, 0.5, 0.5),
        "Pop" to profile(-1.5, -1.0, 1.5, 3.5, 4.0, 2.0, -0.5, -1.0, 1.0, 2.5, 3.0),
        "Rock" to profile(-2.5, 4.5, 3.5, 1.5, -1.5, -2.0, 0.5, 2.5, 4.0, 4.5, 4.0),
        "Vokal" to profile(-1.5, -2.5, -2.0, -0.5, 1.5, 3.5, 4.5, 3.5, 1.5, 0.0, -1.0),
        "Jazz" to profile(-2.0, 3.0, 2.0, 1.0, 2.0, -1.5, -1.5, 0.5, 2.0, 3.0, 3.5),
        "Elektronik" to profile(-3.0, 5.0, 4.0, 1.5, 0.0, -1.5, 1.5, 1.0, 2.0, 4.0, 4.5),
        "Treble" to profile(-2.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.5, 3.0, 4.5, 5.5, 6.0),
        "Akustik" to profile(-1.5, 3.5, 2.5, 1.5, 0.5, 1.5, 1.5, 2.5, 3.0, 2.5, 1.5),
    )

    private fun profile(preamp: Double, vararg gains: Double): EqualizerProfile =
        EqualizerProfile(
            preamp = preamp,
            bands = Frequencies.mapIndexed { idx, freq -> EqualizerBand(freq, gains.getOrElse(idx) { 0.0 }) },
        )

    /** Parses standard AutoEQ / EqualizerAPO `.txt` files. */
    fun parseAutoEq(raw: String): EqualizerProfile? {
        var preamp = 0.0
        val bands = ArrayList<EqualizerBand>()
        val preampRe = Regex("""Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
        val filterRe = Regex(
            """Filter\s+\d+:\s*(ON|OFF)\s+(PK|LSC|HSC|LPQ|HPQ)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB\s+Q\s+(\d+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE,
        )
        for (line in raw.lineSequence()) {
            preampRe.find(line)?.let { m ->
                preamp = m.groupValues[1].toDoubleOrNull()?.coerceIn(-15.0, 15.0) ?: 0.0
            }
            filterRe.find(line)?.let { m ->
                val enabled = m.groupValues[1].equals("ON", ignoreCase = true)
                val type = runCatching { EqualizerFilterType.valueOf(m.groupValues[2].uppercase()) }
                    .getOrDefault(EqualizerFilterType.PK)
                val freq = m.groupValues[3].toDoubleOrNull() ?: return@let
                val gain = m.groupValues[4].toDoubleOrNull()?.coerceIn(-15.0, 15.0) ?: return@let
                val q = m.groupValues[5].toDoubleOrNull()?.coerceIn(0.1, 20.0) ?: 1.41
                if (freq in 10.0..22000.0) {
                    bands += EqualizerBand(freq, gain, q, type, enabled)
                }
            }
        }
        return if (bands.isEmpty()) null else EqualizerProfile(preamp, bands.take(20))
    }
}
