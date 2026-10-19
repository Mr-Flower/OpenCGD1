package it.cgd1.sveglia.data

import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ClockRepository {
    val isBluetoothAvailable: Boolean
    val isBluetoothEnabled: Flow<Boolean>
    val savedClock: Flow<SavedClock?>

    /** Prima di Android 12 la scansione BLE non trova nulla con la Posizione spenta. */
    fun isLocationRequiredButOff(): Boolean

    /** Fallisce con [ClockError.ScanFailed]; termina quando il collector si cancella. */
    fun scan(): Flow<FoundClock>

    suspend fun remember(clock: SavedClock)

    fun openSession(address: String): ClockSession
}

/** Una sveglia aperta. Ogni operazione restituisce un [Result] il cui errore è sempre un [ClockError]. */
interface ClockSession {
    val isConnected: StateFlow<Boolean>

    /** Temperatura e umidità in tempo reale mentre si è collegati; valori non plausibili sono scartati. */
    val liveSensor: Flow<SensorData>

    suspend fun load(): Result<ClockSnapshot>
    suspend fun readSettings(): Result<DeviceSettings>
    suspend fun writeSettings(settings: DeviceSettings): Result<Unit>
    suspend fun writeAlarm(slot: Int, alarm: Alarm): Result<Unit>

    /** Carica l'audio e lo conserva sotto [signature]; per attivarlo va poi scritta la firma nelle impostazioni. */
    suspend fun uploadRingtone(pcm: ByteArray, signature: ByteArray, onProgress: (Float) -> Unit): Result<Unit>

    /** Bip di prova al volume indicato (la sveglia non sa riprodurre la suoneria su richiesta). */
    suspend fun previewVolume(volume: Int): Result<Unit>

    /** Invia l'ora UTC; il fuso va scritto a parte nelle impostazioni. */
    suspend fun syncTime(): Result<Unit>

    /** Cancella le operazioni in volo e chiude il link; la sessione non è più utilizzabile. */
    fun close()
}
