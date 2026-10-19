package it.cgd1.sveglia.data

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
