package it.cgd1.sveglia.audio

import it.cgd1.sveglia.protocol.AudioUpload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PcmProcessingTest {

    private fun tone(hz: Double, seconds: Double, rate: Int, amplitude: Double = 8000.0) =
        ShortArray((seconds * rate).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toInt().toShort() }

    @Test fun resamplesTo8kHz() {
        val out = PcmProcessing.toCgd1Pcm(tone(440.0, 1.0, 44_100), 44_100)
        assertEquals(8000, out.size)
    }

    @Test fun longAudioIsCutTo12SecondsAndFadesOut() {
        val out = PcmProcessing.toCgd1Pcm(tone(440.0, 20.0, 8000), 8000)
        assertEquals(AudioUpload.MAX_AUDIO_BYTES, out.size)
        assertEquals(128, out.last().toInt() and 0xFF)
    }

    @Test fun quietToneIsRaisedToTheLoudnessCeiling() {
        val out = PcmProcessing.toCgd1Pcm(tone(1000.0, 1.0, 8000, amplitude = 500.0), 8000)
        val rms = sqrt(out.sumOf { val v = (it.toInt() and 0xFF) - 128.0; v * v } / out.size)
        // 0.28 di fondo scala = 35.6 su 127: il volume medio della suoneria originale più forte.
        assertEquals(35.6, rms, 1.5)
    }

    @Test fun sparseSoundKeepsPeakNearFullScale() {
        val clicks = ShortArray(8000) { if (it % 2000 < 80) (8000 * sin(2 * PI * 1000 * it / 8000)).toInt().toShort() else 0 }
        val peak = PcmProcessing.toCgd1Pcm(clicks, 8000).maxOf { (it.toInt() and 0xFF) - 128 }
        assertTrue("peak $peak", peak in 100..110)
    }

    @Test fun bassIsRemovedAndMidsKept() {
        val mix = tone(60.0, 1.0, 44_100).zip(tone(1000.0, 1.0, 44_100)) { a, b -> (a + b).toShort() }.toShortArray()
        val out = PcmProcessing.toCgd1Pcm(mix, 44_100)
        val ratio = magnitude(out, 60.0) / magnitude(out, 1000.0)
        assertTrue("60 Hz / 1 kHz = $ratio", ratio < 0.1)
    }

    /** Ampiezza di una singola frequenza (DFT su un bin). */
    private fun magnitude(pcm: ByteArray, hz: Double): Double {
        var re = 0.0
        var im = 0.0
        pcm.forEachIndexed { i, b ->
            val v = (b.toInt() and 0xFF) - 128.0
            val angle = 2 * PI * hz * i / AudioUpload.SAMPLE_RATE
            re += v * cos(angle)
            im -= v * sin(angle)
        }
        return sqrt(re * re + im * im)
    }

    @Test fun neverEmitsEndMarkerOrFill() {
        val out = PcmProcessing.toCgd1Pcm(tone(440.0, 1.0, 8000, amplitude = 32_000.0), 8000)
        assertTrue(out.none { it == 0x00.toByte() || it == 0xFF.toByte() })
    }

    @Test fun silenceStaysAtMidpoint() {
        val out = PcmProcessing.toCgd1Pcm(ShortArray(800), 8000)
        assertTrue(out.all { (it.toInt() and 0xFF) == 128 })
    }

    @Test fun downmixAveragesChannels() =
        assertArrayEquals(shortArrayOf(150, -50), PcmProcessing.downmix(shortArrayOf(100, 200, -100, 0), 2))
}
