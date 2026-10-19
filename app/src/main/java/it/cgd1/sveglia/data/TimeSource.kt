package it.cgd1.sveglia.data

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Ora e fuso del telefono, iniettabili per poter testare sveglie di prova e sincronizzazione. */
interface TimeSource {
    fun now(): Instant

    /** Letto a ogni chiamata: l'utente può cambiare fuso mentre l'app è aperta. */
    fun zone(): ZoneId
}

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
