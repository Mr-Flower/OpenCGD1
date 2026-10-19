package it.cgd1.sveglia.data

import it.cgd1.sveglia.protocol.Cgd1AuthException
import it.cgd1.sveglia.protocol.Cgd1CommandException

/** Errori del data layer: le eccezioni BLE e di piattaforma non escono dal repository. */
sealed class ClockError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** La sveglia è ancora legata a Mi Home / Qingping+ e rifiuta il token. */
    class AuthRejected(cause: Throwable) : ClockError("token rejected", cause)

    /** Link non stabilito o perso: avvicinarsi e riprovare di solito basta. */
    class Connection(cause: Throwable) : ClockError(cause.message ?: "connection error", cause)

    /** La sveglia ha risposto con uno stato d'errore. */
    class Command(cause: Throwable) : ClockError(cause.message ?: "command error", cause)

    /** Permesso Bluetooth revocato mentre l'app era in uso. */
    class PermissionMissing(cause: Throwable) : ClockError("bluetooth permission missing", cause)

    class ScanFailed(val code: Int) : ClockError("scan failed: $code")
}

/**
 * Un permesso revocato può arrivare avvolto in un errore di collegamento: si cerca in tutta la catena
 * delle cause, altrimenti l'utente leggerebbe "sveglia ancora associata a Mi Home".
 */
internal fun Throwable.toClockError(): ClockError = when {
    generateSequence(this) { it.cause }.any { it is SecurityException } -> ClockError.PermissionMissing(this)
    this is Cgd1AuthException -> ClockError.AuthRejected(this)
    this is Cgd1CommandException -> ClockError.Command(this)
    else -> ClockError.Connection(this)
}
