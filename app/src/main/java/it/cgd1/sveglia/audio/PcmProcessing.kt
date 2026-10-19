package it.cgd1.sveglia.audio

import it.cgd1.sveglia.protocol.AudioUpload
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Porta un audio mono a 16 bit nel formato della sveglia. Stesso algoritmo di
 * tools/convert_ringtone.py, usato per le suonerie incluse: le due implementazioni vanno tenute allineate.
 */
object PcmProcessing {
    // L'altoparlante della sveglia non riproduce i bassi: lasciarli consuma potenza e margine senza suonare.
    private const val HIGH_PASS_HZ = 300.0
    private const val LOW_PASS_HZ = 3500.0
    private const val BUTTERWORTH_Q = 0.7071
    // Butterworth del 4° ordine come due biquad in cascata.
    private const val LOW_PASS_Q1 = 0.5412
    private const val LOW_PASS_Q2 = 1.3066

    // Compressore: alza il volume percepito, come nelle suonerie originali che hanno pochi picchi isolati.
    private const val THRESHOLD = 0.25
    private const val RATIO = 4.0
    private const val RELEASE_MS = 150.0

    private const val TARGET_PEAK = 0.85

    // Volume medio massimo: quello della suoneria Qingping più forte (Telephone). Oltre, la sveglia
    // assorbe più corrente di quanto previsto dal suo firmware.
    private const val MAX_RMS = 0.28
    private const val FADE_IN_MS = 5
    private const val FADE_OUT_MS = 300

    fun toCgd1Pcm(samples: ShortArray, sampleRate: Int): ByteArray {
        require(sampleRate > 0) { "frequenza non valida: $sampleRate" }
        var x = DoubleArray(samples.size) { samples[it] / 32768.0 }
        x = highPass(x, sampleRate, HIGH_PASS_HZ, BUTTERWORTH_Q)
        if (sampleRate > 2 * LOW_PASS_HZ) {
            x = lowPass(lowPass(x, sampleRate, LOW_PASS_HZ, LOW_PASS_Q1), sampleRate, LOW_PASS_HZ, LOW_PASS_Q2)
        }
        x = resample(x, sampleRate, AudioUpload.SAMPLE_RATE)
        val truncated = x.size > AudioUpload.MAX_AUDIO_BYTES
        if (truncated) x = x.copyOf(AudioUpload.MAX_AUDIO_BYTES)
        x = limitRms(normalize(compress(normalize(x, 1.0)), TARGET_PEAK), MAX_RMS)

        val fadeIn = AudioUpload.SAMPLE_RATE * FADE_IN_MS / 1000
        val fadeOut = if (truncated) AudioUpload.SAMPLE_RATE * FADE_OUT_MS / 1000 else 0
        return ByteArray(x.size) { i ->
            var v = x[i]
            if (i < fadeIn) v *= i.toDouble() / fadeIn
            val remaining = x.size - i
            if (remaining <= fadeOut) v *= remaining.toDouble() / fadeOut
            ((v * 127).roundToInt() + 128).coerceIn(0, 255).toByte()
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

    // Coefficienti dal "Audio EQ Cookbook" di R. Bristow-Johnson.
    private fun lowPass(input: DoubleArray, rate: Int, cutoff: Double, q: Double): DoubleArray {
        val (c, alpha) = cosAlpha(rate, cutoff, q)
        val a0 = 1 + alpha
        return biquad(input, (1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }

    private fun highPass(input: DoubleArray, rate: Int, cutoff: Double, q: Double): DoubleArray {
        val (c, alpha) = cosAlpha(rate, cutoff, q)
        val a0 = 1 + alpha
        return biquad(input, (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
    }

    private fun cosAlpha(rate: Int, cutoff: Double, q: Double): Pair<Double, Double> {
        val w0 = 2 * PI * cutoff / rate
        return cos(w0) to sin(w0) / (2 * q)
    }

    private fun biquad(input: DoubleArray, b0: Double, b1: Double, b2: Double, a1: Double, a2: Double): DoubleArray {
        val out = DoubleArray(input.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in input.indices) {
            val x = input[i]
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[i] = y
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
        }
        return out
    }

    private fun resample(input: DoubleArray, fromRate: Int, toRate: Int): DoubleArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val outSize = (input.size.toLong() * toRate / fromRate).toInt()
        val step = fromRate.toDouble() / toRate
        return DoubleArray(outSize) { i ->
            val pos = i * step
            val index = pos.toInt()
            val frac = pos - index
            val a = input[index]
            val b = input[minOf(index + 1, input.size - 1)]
            a + (b - a) * frac
        }
    }

    // Attacco istantaneo: con un attacco lento i primi millisecondi di ogni suono sfuggono al compressore,
    // la normalizzazione successiva si regola su quel picco e tutto il resto esce a un terzo del volume.
    private fun compress(input: DoubleArray): DoubleArray {
        val release = exp(-1 / (AudioUpload.SAMPLE_RATE * RELEASE_MS / 1000))
        var envelope = 0.0
        return DoubleArray(input.size) { i ->
            envelope = maxOf(abs(input[i]), release * envelope)
            val gain = if (envelope > THRESHOLD) THRESHOLD * (envelope / THRESHOLD).pow(1 / RATIO) / envelope else 1.0
            input[i] * gain
        }
    }

    private fun limitRms(input: DoubleArray, max: Double): DoubleArray {
        if (input.isEmpty()) return input
        val rms = sqrt(input.sumOf { it * it } / input.size)
        return if (rms > max) DoubleArray(input.size) { input[it] * max / rms } else input
    }

    private fun normalize(input: DoubleArray, target: Double): DoubleArray {
        val peak = input.maxOfOrNull { abs(it) } ?: 0.0
        return if (peak > 0) DoubleArray(input.size) { input[it] * target / peak } else input
    }
}
