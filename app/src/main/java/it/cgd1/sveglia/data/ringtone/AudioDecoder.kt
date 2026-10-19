package it.cgd1.sveglia.data.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import it.cgd1.sveglia.audio.PcmProcessing
import it.cgd1.sveglia.protocol.AudioUpload
import java.nio.ByteOrder
import java.nio.ShortBuffer

private const val TIMEOUT_US = 10_000L

/** Primi 12 s di qualsiasi formato supportato da Android, come PCM 16 bit mono. */
internal class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

internal fun decodeAudio(context: Context, uri: Uri): DecodedAudio {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IllegalArgumentException("nessuna traccia audio")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return drain(extractor, codec, format)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    } finally {
        extractor.release()
    }
}

private fun drain(extractor: MediaExtractor, codec: MediaCodec, inputFormat: MediaFormat): DecodedAudio {
    var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var floatOutput = false
    val out = ShortArrayBuilder()
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    // Basta decodificare quanto entra nella sveglia: un mp3 di 5 minuti non va letto tutto.
    fun enough() = out.size >= (AudioUpload.MAX_AUDIO_BYTES.toLong() * sampleRate / AudioUpload.SAMPLE_RATE + 1) * channels

    while (true) {
        if (!inputDone) {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val buffer = checkNotNull(codec.getInputBuffer(inIndex))
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0 || enough()) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val f = codec.outputFormat
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                floatOutput = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            }
            outIndex >= 0 -> {
                val buffer = checkNotNull(codec.getOutputBuffer(outIndex)).order(ByteOrder.nativeOrder())
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                if (floatOutput) {
                    val floats = buffer.asFloatBuffer()
                    while (floats.hasRemaining()) out.add((floats.get().coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
                } else {
                    out.addAll(buffer.asShortBuffer())
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    }
    return DecodedAudio(PcmProcessing.downmix(out.toArray(), channels), sampleRate)
}

private class ShortArrayBuilder {
    private var data = ShortArray(64 * 1024)
    var size = 0
        private set

    fun add(value: Short) {
        ensure(size + 1)
        data[size++] = value
    }

    fun addAll(buffer: ShortBuffer) {
        val n = buffer.remaining()
        ensure(size + n)
        buffer.get(data, size, n)
        size += n
    }

    fun toArray(): ShortArray = data.copyOf(size)

    private fun ensure(capacity: Int) {
        if (capacity > data.size) data = data.copyOf(maxOf(capacity, data.size * 2))
    }
}
