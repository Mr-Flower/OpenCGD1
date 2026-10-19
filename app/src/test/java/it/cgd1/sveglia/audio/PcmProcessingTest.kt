package it.cgd1.sveglia.audio

import it.cgd1.sveglia.protocol.AudioUpload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

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

    @Test fun quietAudioIsNormalizedNearFullScale() {
        val out = PcmProcessing.toCgd1Pcm(tone(440.0, 1.0, 8000, amplitude = 500.0), 8000)
        val peak = out.maxOf { (it.toInt() and 0xFF) - 128 }
        assertTrue("peak $peak", peak in 105..114)
    }

    @Test fun silenceStaysAtMidpoint() {
        val out = PcmProcessing.toCgd1Pcm(ShortArray(800), 8000)
        assertTrue(out.all { (it.toInt() and 0xFF) == 128 })
    }

    @Test fun downmixAveragesChannels() =
        assertArrayEquals(shortArrayOf(150, -50), PcmProcessing.downmix(shortArrayOf(100, 200, -100, 0), 2))
}
