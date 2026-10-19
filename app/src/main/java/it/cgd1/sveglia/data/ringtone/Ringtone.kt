package it.cgd1.sveglia.data.ringtone

import androidx.annotation.RawRes
import it.cgd1.sveglia.protocol.AudioUpload
import it.cgd1.sveglia.protocol.hex
import it.cgd1.sveglia.protocol.toHex

/** Suoneria inclusa nell'app, già convertita nel formato della sveglia (vedi tools/convert_ringtone.py). */
data class BuiltInRingtone(
    val id: String,
    val name: String,
    val author: String,
    val license: String,
    @param:RawRes val rawRes: Int,
    /** Firma con cui la sveglia la conserva: è l'unico modo per riconoscerla rileggendo le impostazioni. */
    val signatureHex: String,
) {
    val signature: ByteArray get() = hex(signatureHex)
}

/** Un file scelto dall'utente, già convertito e pronto da ascoltare o caricare. */
class CustomRingtone(val name: String, val pcm: ByteArray) {
    val durationSeconds: Float get() = pcm.size / AudioUpload.SAMPLE_RATE.toFloat()
}

/** Come presentare la suoneria attualmente sulla sveglia. */
sealed interface CurrentRingtone {
    data class BuiltIn(val ringtone: BuiltInRingtone) : CurrentRingtone

    /** Una delle suonerie dell'app Qingping, caricata prima di passare a OpenCGD1. */
    data class Original(val name: String) : CurrentRingtone

    data object Custom : CurrentRingtone

    data class Unknown(val signatureHex: String) : CurrentRingtone

    companion object {
        // Firme delle suonerie dell'app Qingping, documentate da cgd1-rs. I suoni non sono inclusi: sono di Qingping.
        private val ORIGINAL_NAMES = mapOf(
            "fdc366a5" to "Beep",
            "0961bb77" to "Digital Ringtone",
            "ba2c2c8c" to "Digital Ringtone 2",
            "ea2d4c02" to "Cuckoo",
            "791bacb3" to "Telephone Ringtone",
            "1d019fd6" to "Exotic Guitar",
            "6e70b659" to "Lively Piano",
            "8f004886" to "Story Piano",
            "26522519" to "Forest Piano",
        )

        fun identify(signature: ByteArray, builtIns: List<BuiltInRingtone>): CurrentRingtone {
            val hex = signature.toHex()
            builtIns.firstOrNull { it.signatureHex == hex }?.let { return BuiltIn(it) }
            ORIGINAL_NAMES[hex]?.let { return Original(it) }
            return if (hex in CustomSlots.ALL) Custom else Unknown(hex)
        }
    }
}

object CustomSlots {
    const val A = "deaddead"
    const val B = "beefbeef"
    val ALL = setOf(A, B)

    /** Si alterna fra due firme: la sveglia può rifiutare un caricamento sulla firma della suoneria attiva. */
    fun next(currentSignature: ByteArray): ByteArray = hex(if (currentSignature.toHex() == A) B else A)
}
