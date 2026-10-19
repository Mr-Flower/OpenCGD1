package it.cgd1.sveglia.protocol

/** Base di tutti gli errori della libreria. */
open class Cgd1Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

/** La sveglia ha rifiutato il token (il collegamento cade su un'operazione privilegiata). */
class Cgd1AuthException(message: String, cause: Throwable? = null) : Cgd1Exception(message, cause)

/** Un comando è fallito, ha restituito uno stato d'errore o è scaduto dopo il retry. */
class Cgd1CommandException(message: String, cause: Throwable? = null) : Cgd1Exception(message, cause)

/** Il client è stato chiuso (sveglia cambiata o app in uscita): non si ricollega più. */
class Cgd1ClosedException : Cgd1Exception("client chiuso")

/** Connessione BLE impossibile, persa, timeout o errore GATT: errori per cui ha senso un retry. */
class Cgd1ConnectionException(message: String, cause: Throwable? = null) : Cgd1Exception(message, cause)
