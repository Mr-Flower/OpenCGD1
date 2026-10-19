package it.cgd1.sveglia.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.cgd1.sveglia.ble.Cgd1Client
import it.cgd1.sveglia.ble.Cgd1Scanner
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.ble.ScanFailedException
import it.cgd1.sveglia.di.DefaultDispatcher
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Cgd1AuthException
import it.cgd1.sveglia.protocol.Cgd1CommandException
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BleClockRepository"

@Singleton
class BleClockRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: SavedClockStore,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ClockRepository {

    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scanner = adapter?.let(::Cgd1Scanner)

    override val isBluetoothAvailable: Boolean get() = adapter != null

    // Il Bluetooth spento dalle impostazioni rapide non passa da onResume: serve il broadcast.
    override val isBluetoothEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(adapter?.isEnabled == true)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(adapter?.isEnabled == true)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override val savedClock: Flow<SavedClock?> = store.savedClock

    override fun isLocationRequiredButOff(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(lm)
    }

    override fun scan(): Flow<FoundClock> {
        val s = scanner ?: return flow { throw ClockError.ScanFailed(-1) }
        return s.scan().catch { e ->
            throw when (e) {
                is ScanFailedException -> ClockError.ScanFailed(e.errorCode)
                is SecurityException -> ClockError.PermissionMissing(e)
                else -> e
            }
        }
    }

    override suspend fun remember(clock: SavedClock) = store.save(clock)

    override fun openSession(address: String): ClockSession {
        val bt = checkNotNull(adapter) { "Bluetooth non disponibile" }
        return BleClockSession(context, bt, address, CoroutineScope(SupervisorJob() + dispatcher))
    }
}

private class BleClockSession(
    context: Context,
    adapter: BluetoothAdapter,
    address: String,
    private val scope: CoroutineScope,
) : ClockSession {

    private val client = Cgd1Client(context, adapter.getRemoteDevice(address), scope)

    override val isConnected: StateFlow<Boolean> = client.connected

    // La scala /100 della notifica viene da clOwOck e potrebbe dipendere dal firmware: meglio niente che un valore assurdo.
    override val liveSensor: Flow<SensorData> = client.sensor.filter { it.isPlausible() }

    override suspend fun load(): Result<ClockSnapshot> = call {
        client.connect()
        ClockSnapshot(
            settings = client.readSettings(),
            alarms = client.readAlarms(),
            firmware = optional { client.readFirmware() },
        )
    }

    override suspend fun readSettings(): Result<DeviceSettings> = call { client.readSettings() }

    override suspend fun writeSettings(settings: DeviceSettings): Result<Unit> = call { client.writeSettings(settings) }

    override suspend fun writeAlarm(slot: Int, alarm: Alarm): Result<Unit> = call { client.writeAlarm(slot, alarm) }

    override suspend fun uploadRingtone(pcm: ByteArray, signature: ByteArray, onProgress: (Float) -> Unit): Result<Unit> =
        call { client.uploadRingtone(pcm, signature, onProgress) }

    override suspend fun previewVolume(volume: Int): Result<Unit> = call { client.previewVolume(volume) }

    override suspend fun syncTime(): Result<Unit> = call { client.syncTime() }

    override fun close() {
        scope.cancel()
        client.closeNow()
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "clock operation failed", e)
        Result.failure(e.toClockError())
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "optional read failed", e)
        null
    }

    private fun SensorData.isPlausible(): Boolean =
        (temperature == null || temperature in -30.0..60.0) && (humidity == null || humidity in 0.0..100.0)

    private fun Exception.toClockError(): ClockError = when (this) {
        is Cgd1AuthException -> ClockError.AuthRejected(this)
        is Cgd1CommandException -> ClockError.Command(this)
        is SecurityException -> ClockError.PermissionMissing(this)
        else -> ClockError.Connection(this)
    }
}
