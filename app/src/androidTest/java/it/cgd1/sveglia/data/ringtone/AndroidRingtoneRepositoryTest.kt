package it.cgd1.sveglia.data.ringtone

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.cgd1.sveglia.protocol.AudioUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Decodifica vera con MediaCodec: sulla JVM non si può verificare. */
@RunWith(AndroidJUnit4::class)
class AndroidRingtoneRepositoryTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val repository = AndroidRingtoneRepository(instrumentation.targetContext, Dispatchers.Default)

    private fun assetUri(name: String): String {
        val file = File(instrumentation.targetContext.cacheDir, name)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    @Test fun stereoOggIsConvertedToMono8kHz() = runTest {
        val ringtone = repository.convert(assetUri("tone_3s_stereo.ogg")).getOrThrow()
        assertTrue("size ${ringtone.pcm.size}", ringtone.pcm.size in 23_500..24_500)
        assertEquals("tone_3s_stereo", ringtone.name)
    }

    @Test fun longMp3IsCutTo12Seconds() = runTest {
        val ringtone = repository.convert(assetUri("tone_20s.mp3")).getOrThrow()
        assertEquals(AudioUpload.MAX_AUDIO_BYTES, ringtone.pcm.size)
    }

    @Test fun nonAudioFileFailsWithRingtoneError() = runTest {
        val file = File(instrumentation.targetContext.cacheDir, "not_audio.txt").apply { writeText("hello") }
        val result = repository.convert(Uri.fromFile(file).toString())
        assertTrue(result.exceptionOrNull() is RingtoneError.Unsupported)
    }

    @Test fun builtInRingtonesFitTheClock() = runTest {
        repository.builtIns.forEach { ringtone ->
            val pcm = repository.loadPcm(ringtone)
            assertTrue(ringtone.id, pcm.isNotEmpty() && pcm.size <= AudioUpload.MAX_AUDIO_BYTES)
        }
    }
}
