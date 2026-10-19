package it.cgd1.sveglia.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import it.cgd1.sveglia.protocol.Cgd1
import it.cgd1.sveglia.protocol.Codec
import it.cgd1.sveglia.protocol.SensorData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundClock(
    val address: String,
    val name: String?,
    val rssi: Int,
    val sensor: SensorData?,
)

class ScanFailedException(val errorCode: Int) : Exception("scan failed: $errorCode")

/**
 * Scansione BLE passiva: riconosce la CGD1 dall'advertisement Qingping (service data 0xFDCD,
 * byte modello 0x0C), che non è cifrato e porta temperatura, umidità e batteria.
 */
@SuppressLint("MissingPermission") // i permessi sono verificati dalla UI prima di avviare la scansione
class Cgd1Scanner(private val adapter: BluetoothAdapter) {

    private val advUuid = ParcelUuid(Cgd1.ADVERTISEMENT_SERVICE_UUID)

    /** Avvisi grezzi, anche ripetuti per lo stesso dispositivo; la scansione si ferma quando il collector si cancella. */
    fun scan(): Flow<FoundClock> = callbackFlow {
        val scanner = adapter.bluetoothLeScanner ?: throw ScanFailedException(-1)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parse(result)?.let { trySend(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> parse(result)?.let { trySend(it) } }
            }

            override fun onScanFailed(errorCode: Int) {
                close(ScanFailedException(errorCode))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Nessun filtro hardware: i filtri su service data non sono affidabili su tutti i telefoni.
        scanner.startScan(null, settings, callback)
        awaitClose {
            // Con il Bluetooth spento nel frattempo stopScan lancia: non c'è più niente da fermare.
            runCatching { scanner.stopScan(callback) }
        }
    }

    private fun parse(result: ScanResult): FoundClock? {
        val record = result.scanRecord ?: return null
        val sensor = record.getServiceData(advUuid)?.let { Codec.parseAdvertisement(it) }
        val name = record.deviceName ?: result.device.name
        val looksLikeClock = sensor != null || name != null && CLOCK_NAMES.any { name.contains(it, ignoreCase = true) }
        return if (looksLikeClock) FoundClock(result.device.address, name, result.rssi, sensor) else null
    }

    private companion object {
        val CLOCK_NAMES = listOf("Qingping", "CGD1", "Alarm Clock")
    }
}
