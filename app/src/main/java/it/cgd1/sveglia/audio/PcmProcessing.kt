package it.cgd1.sveglia.audio

import it.cgd1.sveglia.protocol.AudioUpload
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Porta un audio mono a 16 bit nel formato della sveglia. Stesso trattamento di
 * tools/convert_ringtone.py, usato per le suonerie incluse.
 */
object PcmProcessing {
    private const val CUTOFF_HZ = 3600.0
    private const val TARGET_PEAK = 0.89
    private const val FADE_MS = 300

    fun toCgd1Pcm(samples: ShortArray, sampleRate: Int): ByteArray {
        require(sampleRate > 0) { "frequenza non valida: $sampleRate" }
        val filtered = if (sampleRate > 2 * CUTOFF_HZ) lowPass(samples, sampleRate) else samples.toFloatArray()
        val resampled = resample(filtered, sampleRate, AudioUpload.SAMPLE_RATE)
        val truncated = resampled.size > AudioUpload.MAX_AUDIO_BYTES
        val clip = if (truncated) resampled.copyOf(AudioUpload.MAX_AUDIO_BYTES) else resampled
        val peak = clip.maxOfOrNull { abs(it) }?.takeIf { it > 0f } ?: 1f
        val gain = (TARGET_PEAK * Short.MAX_VALUE / peak).toFloat()
        val fade = if (truncated) AudioUpload.SAMPLE_RATE * FADE_MS / 1000 else 0
        return ByteArray(clip.size) { i ->
            var v = clip[i] * gain
            val remaining = clip.size - i
            if (remaining <= fade) v *= remaining.toFloat() / fade
            ((v / 256f).roundToInt() + 128).coerceIn(0, 255).toByte()
        }
    }

    /** Media dei canali interleaved. */
    fun downmix(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        return ShortArray(interleaved.size / channels) { frame ->
            var sum = 0
            for (c in 0 until channels) sum += interleaved[frame * channels + c]
            (sum / channels).toShort()
        }
    }

    // Biquad passa-basso (RBJ cookbook): senza filtro, il ricampionamento a 8 kHz produrrebbe aliasing udibile.
    private fun lowPass(input: ShortArray, sampleRate: Int): FloatArray {
        val w0 = 2 * PI * CUTOFF_HZ / sampleRate
        val alpha = sin(w0) / (2 * 0.7071)
        val cosW0 = cos(w0)
        val a0 = 1 + alpha
        val b0 = ((1 - cosW0) / 2 / a0).toFloat()
        val b1 = ((1 - cosW0) / a0).toFloat()
        val a1 = (-2 * cosW0 / a0).toFloat()
        val a2 = ((1 - alpha) / a0).toFloat()
        val out = FloatArray(input.size)
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        for (i in input.indices) {
            val x = input[i].toFloat()
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            out[i] = y
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
        }
        return out
    }

    private fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val outSize = (input.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return FloatArray(outSize) { i ->
            val pos = i * step
            val index = pos.toInt()
            val frac = (pos - index).toFloat()
            val a = input[index]
            val b = input[minOf(index + 1, input.size - 1)]
            a + (b - a) * frac
        }
    }

    private fun ShortArray.toFloatArray(): FloatArray = FloatArray(size) { this[it].toFloat() }
}
