package it.cgd1.sveglia.data.ringtone

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class QingpingRingtoneSourceUtilsTest {

    @Test fun onlyHttpsQingpingHostsAreTrusted() {
        assertTrue(isTrustedRingtoneUrl("https://qingfseu.cleargrass.com/rings/ea2d4c02.pcm"))
        assertFalse(isTrustedRingtoneUrl("http://qingfseu.cleargrass.com/rings/ea2d4c02.pcm"))
        assertFalse(isTrustedRingtoneUrl("https://cleargrass.com.evil.example/rings/x.pcm"))
        assertFalse(isTrustedRingtoneUrl("https://evilcleargrass.com/x.pcm"))
        assertFalse(isTrustedRingtoneUrl("not a url"))
    }

    @Test fun readAtMostReturnsEverythingWithinTheLimit() =
        assertArrayEquals(ByteArray(10) { 1 }, ByteArrayInputStream(ByteArray(10) { 1 }).readAtMost(10))

    @Test(expected = IOException::class)
    fun readAtMostRejectsOversizedBodies() {
        ByteArrayInputStream(ByteArray(11)).readAtMost(10)
    }
}
