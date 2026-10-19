package it.cgd1.sveglia.data.ringtone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import it.cgd1.sveglia.protocol.AudioUpload
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RingtonePlayer"

/** Anteprima sul telefono, nello stesso formato a 8 kHz e 8 bit che riceverà la sveglia. */
interface RingtonePlayer {
    /** false se il telefono non riesce a riprodurre l'audio (formato non supportato, audio vuoto). */
    fun play(pcm: ByteArray): Boolean
    fun stop()
}

@Singleton
class AudioTrackRingtonePlayer @Inject constructor() : RingtonePlayer {
    private var track: AudioTrack? = null

    override fun play(pcm: ByteArray): Boolean {
        stop()
        if (pcm.isEmpty()) return false
        val audioTrack = try {
            AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                    .setSampleRate(AudioUpload.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size)
            .build()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack unavailable", e)
            return false
        }
        track = audioTrack
        return try {
            audioTrack.write(pcm, 0, pcm.size)
            audioTrack.play()
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "preview playback failed", e)
            stop()
            false
        }
    }

    override fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}
