package it.cgd1.sveglia.data.ringtone

import androidx.annotation.RawRes
import it.cgd1.sveglia.protocol.AudioUpload
import it.cgd1.sveglia.protocol.hex
import it.cgd1.sveglia.protocol.toHex

/** Una suoneria che si può ascoltare e caricare; la sveglia la riconosce solo dalla firma. */
sealed interface RingtoneOption {
    val id: String
    val name: String
    val signatureHex: String
    val signature: ByteArray get() = hex(signatureHex)
}

/** Inclusa nell'app, già convertita nel formato della sveglia (vedi tools/convert_ringtone.py). */
data class BuiltInRingtone(
    override val id: String,
    override val name: String,
    val author: String,
    val license: String,
    @param:RawRes val rawRes: Int,
    override val signatureHex: String,
) : RingtoneOption

/** Suoneria dell'app Qingping, scaricata dal server Qingping quando serve: non è inclusa nell'app. */
data class OriginalRingtone(
    override val signatureHex: String,
    override val name: String,
    val pcmUrl: String,
) : RingtoneOption {
    override val id: String get() = "qingping-$signatureHex"
}

/** Un file scelto dall'utente, già convertito e pronto da ascoltare o caricare. */
class CustomRingtone(val name: String, val pcm: ByteArray) {
    val durationSeconds: Float get() = pcm.size / AudioUpload.SAMPLE_RATE.toFloat()
}

/** Come presentare la suoneria attualmente sulla sveglia. */
sealed interface CurrentRingtone {
    data class BuiltIn(val ringtone: BuiltInRingtone) : CurrentRingtone

    data class Original(val name: String) : CurrentRingtone

    data object Custom : CurrentRingtone

    data class Unknown(val signatureHex: String) : CurrentRingtone

    companion object {
        // Elenco pubblicato da Qingping in /raw/rings: serve finché quello scaricato non è disponibile.
        internal val ORIGINAL_NAMES = mapOf(
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

        // Suonerie Canonical incluse fino alla 0.4.0: una sveglia può averne ancora una caricata.
        private val LEGACY_NAMES = mapOf(
            "4f430001" to "Alarm Clock", "4f430002" to "Alarm Synth", "4f430003" to "Bliss",
            "4f430004" to "Call Me", "4f430005" to "Celestial", "4f430006" to "Counterpoint",
            "4f430007" to "Glass Marimba", "4f430008" to "Harp Arp", "4f430009" to "Koto Chords",
            "4f43000a" to "Melody Piano", "4f43000b" to "Sparkle", "4f43000c" to "Time Not Lost",
            "4f43000d" to "UBports", "4f43000e" to "Wooden Drive",
        )

        fun identify(signature: ByteArray, builtIns: List<BuiltInRingtone>, originals: List<OriginalRingtone>): CurrentRingtone {
            val hex = signature.toHex()
            builtIns.firstOrNull { it.signatureHex == hex }?.let { return BuiltIn(it) }
            (originals.firstOrNull { it.signatureHex == hex }?.name ?: ORIGINAL_NAMES[hex])?.let { return Original(it) }
            LEGACY_NAMES[hex]?.let { return Original(it) }
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
