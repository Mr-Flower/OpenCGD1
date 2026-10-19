package it.cgd1.sveglia.ui.main

import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.ClockError
import it.cgd1.sveglia.data.ClockRepository
import it.cgd1.sveglia.data.ClockSession
import it.cgd1.sveglia.data.ClockSnapshot
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeClockRepository(private val snapshot: ClockSnapshot) : ClockRepository {
    override val isBluetoothAvailable = true
    override val isBluetoothEnabled = MutableStateFlow(true)
    override val savedClock = MutableStateFlow<SavedClock?>(null)
    val scanResults = MutableSharedFlow<FoundClock>()
    val session = FakeClockSession(snapshot)

    override fun isLocationRequiredButOff() = false

    override fun scan(): Flow<FoundClock> = scanResults

    override suspend fun remember(clock: SavedClock) {
        savedClock.value = clock
    }

    override fun openSession(address: String): ClockSession = session
}

class FakeClockSession(private val snapshot: ClockSnapshot) : ClockSession {
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
    var loadError: ClockError? = null
    var writeError: ClockError? = null
    val writtenAlarms = mutableListOf<Pair<Int, Alarm>>()
    val writtenSettings = mutableListOf<DeviceSettings>()
    var closed = false

    override suspend fun load(): Result<ClockSnapshot> = loadError?.let { Result.failure(it) } ?: Result.success(snapshot)

    override suspend fun readSettings(): Result<DeviceSettings> = Result.success(snapshot.settings)

    override suspend fun writeSettings(settings: DeviceSettings): Result<Unit> = write { writtenSettings += settings }

    override suspend fun writeAlarm(slot: Int, alarm: Alarm): Result<Unit> = write { writtenAlarms += slot to alarm }

    override suspend fun syncTime(): Result<Unit> = Result.success(Unit)

    override fun close() {
        closed = true
    }

    private fun write(record: () -> Unit): Result<Unit> = writeError?.let { Result.failure(it) } ?: Result.success(record())
}
