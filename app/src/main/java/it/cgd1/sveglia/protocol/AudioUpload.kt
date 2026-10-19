package it.cgd1.sveglia.protocol

/**
 * Formato e pacchettizzazione della suoneria (dalle note di cgd1-rs, MIT).
 * La sveglia conserva un solo suono: PCM 8 bit senza segno, 8 kHz, mono.
 */
object AudioUpload {
    const val SAMPLE_RATE = 8000
    const val SIGNATURE_SIZE = 4

    /** 12 s: il firmware accetta circa 98 KB una volta riempito l'ultimo blocco. */
    const val MAX_AUDIO_BYTES = 12 * SAMPLE_RATE

    const val BLOCK_SIZE = 512
    const val PACKET_AUDIO_SIZE = 128
    const val PACKETS_PER_BLOCK = BLOCK_SIZE / PACKET_AUDIO_SIZE

    /** Header (2 byte) + audio: serve un MTU che lo faccia stare in un solo pacchetto ATT. */
    const val PACKET_SIZE = 2 + PACKET_AUDIO_SIZE

    private const val END_MARKER: Byte = 0x00
    private const val FILL: Byte = 0xFF.toByte()

    /** Chiude l'audio con il marcatore 00 e riempie di FF fino a un multiplo di [BLOCK_SIZE]. */
    fun pad(pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty()) { "audio vuoto" }
        require(pcm.size <= MAX_AUDIO_BYTES) { "audio troppo lungo: ${pcm.size} byte" }
        val size = (pcm.size + 1 + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE
        return ByteArray(size) { i ->
            when {
                i < pcm.size -> pcm[i]
                i == pcm.size -> END_MARKER
                else -> FILL
            }
        }
    }

    /** `08 10` + lunghezza (3 byte LE) + firma (4 byte). */
    fun encodeInit(paddedSize: Int, signature: ByteArray): ByteArray {
        require(signature.size == SIGNATURE_SIZE) { "la firma deve essere di $SIGNATURE_SIZE byte" }
        return Cgd1.CMD_AUDIO_INIT + byteArrayOf(
            (paddedSize and 0xFF).toByte(),
            ((paddedSize shr 8) and 0xFF).toByte(),
            ((paddedSize shr 16) and 0xFF).toByte(),
        ) + signature
    }

    /** Pacchetti `81 08` + 128 byte; l'audio arriva già paddato, quindi ogni blocco ha 4 pacchetti pieni. */
    fun packets(padded: ByteArray): List<ByteArray> {
        require(padded.size % BLOCK_SIZE == 0) { "audio non paddato" }
        return (padded.indices step PACKET_AUDIO_SIZE).map { start ->
            Cgd1.CMD_AUDIO_DATA + padded.copyOfRange(start, start + PACKET_AUDIO_SIZE)
        }
    }
}
