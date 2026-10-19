package it.cgd1.sveglia.data.ringtone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import it.cgd1.sveglia.protocol.AudioUpload
import javax.inject.Inject
import javax.inject.Singleton

/** Anteprima sul telefono, nello stesso formato a 8 kHz e 8 bit che riceverà la sveglia. */
interface RingtonePlayer {
    fun play(pcm: ByteArray)
    fun stop()
}

@Singleton
class AudioTrackRingtonePlayer @Inject constructor() : RingtonePlayer {
    private var track: AudioTrack? = null

    override fun play(pcm: ByteArray) {
        stop()
        val audioTrack = AudioTrack.Builder()
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
        audioTrack.write(pcm, 0, pcm.size)
        audioTrack.play()
        track = audioTrack
    }

    override fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}
