package it.cgd1.sveglia.data.ringtone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.cgd1.sveglia.protocol.AudioUpload
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Richiede rete: verifica che il server Qingping risponda ancora nel formato atteso. */
@RunWith(AndroidJUnit4::class)
class QingpingRingtoneSourceTest {

    private val source = QingpingRingtoneSource(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun listsTheOriginalRingtonesAndDownloadsOne() {
        val ringtones = source.list()
        assertTrue(ringtones.map { it.name }.toString(), ringtones.any { it.signatureHex == "ea2d4c02" })
        val pcm = source.pcm(ringtones.first())
        assertTrue("size ${pcm.size}", pcm.isNotEmpty() && pcm.size <= AudioUpload.MAX_AUDIO_BYTES)
    }
}
