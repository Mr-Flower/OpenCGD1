package it.cgd1.sveglia.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioUploadTest {

    @Test fun padAddsEndMarkerThenFillsTheBlock() {
        val padded = AudioUpload.pad(ByteArray(3) { 0x80.toByte() })
        assertEquals(512, padded.size)
        assertArrayEquals(hex("80808000ffff"), padded.copyOfRange(0, 6))
        assertTrue(padded.drop(4).all { it == 0xFF.toByte() })
    }

    @Test fun exactBlockStillGetsAnEndMarker() {
        val padded = AudioUpload.pad(ByteArray(512) { 1 })
        assertEquals(1024, padded.size)
        assertEquals(0x00.toByte(), padded[512])
    }

    @Test fun twelveSecondsFitIn188Blocks() = assertEquals(188 * 512, AudioUpload.pad(ByteArray(AudioUpload.MAX_AUDIO_BYTES)).size)

    @Test(expected = IllegalArgumentException::class)
    fun tooLongAudioIsRejected() {
        AudioUpload.pad(ByteArray(AudioUpload.MAX_AUDIO_BYTES + 1))
    }

    @Test fun initCarriesLittleEndianSizeAndSignature() =
        assertArrayEquals(hex("0810" + "007801" + "deaddead"), AudioUpload.encodeInit(0x017800, hex("deaddead")))

    @Test fun packetsAre130BytesWithHeader() {
        val packets = AudioUpload.packets(AudioUpload.pad(ByteArray(600)))
        assertEquals(8, packets.size)
        assertTrue(packets.all { it.size == AudioUpload.PACKET_SIZE && it[0] == 0x81.toByte() && it[1] == 0x08.toByte() })
    }
}
