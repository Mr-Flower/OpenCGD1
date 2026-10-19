package it.cgd1.sveglia.data.ringtone

interface RingtoneRepository {
    val builtIns: List<BuiltInRingtone>

    suspend fun loadPcm(ringtone: BuiltInRingtone): ByteArray

    /** Decodifica e converte un file audio scelto dall'utente; l'errore è sempre un [RingtoneError]. */
    suspend fun convert(uri: String): Result<CustomRingtone>
}

sealed class RingtoneError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unsupported(cause: Throwable) : RingtoneError("unsupported audio", cause)
    class Empty : RingtoneError("no audio")
}
