package it.cgd1.sveglia.data.ringtone

/** Ogni errore restituito è un [RingtoneError]. */
interface RingtoneRepository {
    val builtIns: List<BuiltInRingtone>

    /** Elenco delle suonerie Qingping, dal loro server. */
    suspend fun originals(): Result<List<OriginalRingtone>>

    suspend fun loadPcm(ringtone: RingtoneOption): Result<ByteArray>

    /** Decodifica e converte un file audio scelto dall'utente. */
    suspend fun convert(uri: String): Result<CustomRingtone>
}

sealed class RingtoneError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unsupported(cause: Throwable) : RingtoneError("unsupported audio", cause)
    class Empty : RingtoneError("no audio")
    class Network(cause: Throwable) : RingtoneError("download failed", cause)
}
