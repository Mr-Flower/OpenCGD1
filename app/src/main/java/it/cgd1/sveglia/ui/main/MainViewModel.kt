package it.cgd1.sveglia.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.ClockError
import it.cgd1.sveglia.data.ClockRepository
import it.cgd1.sveglia.data.ClockSession
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.ui.UiText
import it.cgd1.sveglia.ui.formatOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val SCAN_TIMEOUT_MS = 30_000L

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ClockRepository,
) : ViewModel(), ScanActions, ClockActions {

    private val state = MutableStateFlow(MainUiState(isBluetoothAvailable = repository.isBluetoothAvailable))

    val uiState: StateFlow<MainUiState> = combine(
        state,
        repository.isBluetoothEnabled,
        repository.savedClock,
    ) { s, bluetoothEnabled, saved -> s.copy(isBluetoothEnabled = bluetoothEnabled, savedClock = saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    private val _effects = Channel<MainEffect>(Channel.BUFFERED)
    val effects: Flow<MainEffect> = _effects.receiveAsFlow()

    private var scanJob: Job? = null

    // Ogni sveglia aperta ha la sua sessione e il suo scope: chiudendola si cancella tutto ciò
    // che era in volo, così un'operazione vecchia non può riaprire il link né toccare lo stato.
    private var session: ClockSession? = null
    private var sessionScope: CoroutineScope? = null
    private val opMutex = Mutex()

    override fun onScanScreenShown() {
        viewModelScope.launch {
            if (repository.savedClock.first() == null) onStartScan()
        }
    }

    override fun onStartScan() {
        if (repository.isLocationRequiredButOff()) showMessage(UiText(R.string.message_location_required))
        scanJob?.cancel()
        state.update { it.copy(isScanning = true, foundClocks = emptyList()) }
        scanJob = viewModelScope.launch {
            withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                repository.scan()
                    .catch { e -> showMessage(e.toMessage()) }
                    .collect { found -> state.update { it.copy(foundClocks = it.foundClocks.upsert(found)) } }
            }
            state.update { it.copy(isScanning = false) }
        }
    }

    override fun onStopScan() {
        scanJob?.cancel()
        scanJob = null
        state.update { it.copy(isScanning = false) }
    }

    override fun onOpenSaved(clock: SavedClock) = openClock(clock, sensor = null)

    override fun onOpenFound(clock: FoundClock) = openClock(SavedClock(clock.address, clock.name), clock.sensor)

    private fun openClock(clock: SavedClock, sensor: SensorData?) {
        onStopScan()
        releaseSession()
        viewModelScope.launch { repository.remember(clock) }
        val newSession = repository.openSession(clock.address)
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext.job))
        session = newSession
        sessionScope = scope
        newSession.isConnected
            .onEach { connected ->
                // Durante il collegamento decide l'operazione in corso; un errore resta visibile finché un comando non riesce.
                state.update { s ->
                    when {
                        s.connection is ConnectionState.Connecting -> s
                        connected -> s.copy(connection = ConnectionState.Connected)
                        s.connection is ConnectionState.Failed -> s
                        else -> s.copy(connection = ConnectionState.Idle)
                    }
                }
            }
            .launchIn(scope)
        state.update {
            it.copy(
                openClock = clock, sensor = sensor, connection = ConnectionState.Connecting, isBusy = false,
                settings = null, alarms = emptyList(), firmware = null, battery = null,
            )
        }
        load(syncClock = true)
    }

    override fun onCloseClock() {
        releaseSession()
        state.update {
            it.copy(openClock = null, connection = ConnectionState.Idle, settings = null, alarms = emptyList(), isBusy = false)
        }
    }

    private fun releaseSession() {
        sessionScope?.cancel()
        sessionScope = null
        session?.close()
        session = null
    }

    override fun onRefresh() = load(syncClock = false)

    override fun onRetry() = load(syncClock = true)

    private fun load(syncClock: Boolean) = op(connecting = true) { s, update ->
        val snapshot = s.load().getOrThrow()
        update {
            it.copy(
                settings = snapshot.settings,
                alarms = snapshot.alarms,
                firmware = snapshot.firmware,
                battery = snapshot.battery ?: it.battery,
                connection = ConnectionState.Connected,
            )
        }
        if (syncClock) syncTime(s, snapshot.settings, update)
    }

    override fun onSyncTime() = op { s, update ->
        val current = state.value.settings ?: s.readSettings().getOrThrow()
        syncTime(s, current, update)
    }

    private suspend fun syncTime(s: ClockSession, current: DeviceSettings, update: StateUpdater) {
        val offset = currentOffsetMinutes()
        if (current.tzOffsetMinutes != offset) {
            val updated = current.copy(tzOffsetMinutes = offset)
            s.writeSettings(updated).getOrThrow()
            update { it.copy(settings = updated) }
        }
        s.syncTime().getOrThrow()
        showMessage(UiText(R.string.message_time_synced, formatOffset(offset)))
    }

    /** Lo slot libero si sceglie al momento della scrittura, non al tocco. */
    override fun onAddAlarm(alarm: Alarm) = op { s, update ->
        val slot = state.value.alarms.indexOfFirst { it.isEmpty }
        if (slot < 0) {
            showMessage(UiText(R.string.message_slots_full))
            return@op
        }
        s.writeAlarm(slot, alarm).getOrThrow()
        update { it.withAlarm(slot, alarm) }
        showMessage(UiText(R.string.message_alarm_added))
    }

    override fun onSaveAlarm(slot: Int, alarm: Alarm) = op { s, update ->
        s.writeAlarm(slot, alarm).getOrThrow()
        update { it.withAlarm(slot, alarm) }
        showMessage(UiText(R.string.message_alarm_saved))
    }

    /** Legge lo slot al momento dell'esecuzione, così non annulla una modifica ancora in coda. */
    override fun onToggleAlarm(slot: Int, enabled: Boolean) = op { s, update ->
        val alarm = state.value.alarms.getOrNull(slot)?.takeIf { !it.isEmpty } ?: return@op
        if (alarm.enabled == enabled) return@op
        val next = alarm.copy(enabled = enabled)
        s.writeAlarm(slot, next).getOrThrow()
        update { it.withAlarm(slot, next) }
    }

    override fun onDeleteAlarm(slot: Int) = op { s, update ->
        s.writeAlarm(slot, Alarm.EMPTY).getOrThrow()
        update { it.withAlarm(slot, Alarm.EMPTY) }
        showMessage(UiText(R.string.message_alarm_deleted))
    }

    override fun onUpdateSettings(transform: (DeviceSettings) -> DeviceSettings) = op { s, update ->
        val current = state.value.settings ?: return@op
        val next = transform(current)
        if (next == current) return@op
        s.writeSettings(next).getOrThrow()
        update { it.copy(settings = next) }
    }

    /**
     * Esegue un'operazione sulla sveglia aperta, una alla volta. Ogni modifica allo stato passa
     * da `update`, che la scarta se nel frattempo la sveglia è stata chiusa o cambiata.
     */
    private fun op(connecting: Boolean = false, block: suspend (ClockSession, StateUpdater) -> Unit) {
        val s = session ?: return
        val scope = sessionScope ?: return
        val update: StateUpdater = { f -> if (session === s) state.update(f) }
        scope.launch {
            opMutex.withLock {
                if (session !== s) return@withLock
                update { it.copy(isBusy = true, connection = if (connecting) ConnectionState.Connecting else it.connection) }
                try {
                    block(s, update)
                } catch (e: ClockError) {
                    if (connecting || e is ClockError.AuthRejected) {
                        update { it.copy(connection = e.toConnectionFailure()) }
                    } else {
                        showMessage(e.toMessage())
                    }
                } finally {
                    update { it.copy(isBusy = false) }
                }
            }
        }
    }

    private fun showMessage(text: UiText) {
        _effects.trySend(MainEffect.ShowMessage(text))
    }

    override fun onCleared() {
        releaseSession()
    }
}

private typealias StateUpdater = ((MainUiState) -> MainUiState) -> Unit

private fun currentOffsetMinutes(): Int = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60

private fun List<FoundClock>.upsert(found: FoundClock): List<FoundClock> {
    val index = indexOfFirst { it.address == found.address }
    return if (index >= 0) toMutableList().also { it[index] = found } else this + found
}

private fun MainUiState.withAlarm(slot: Int, alarm: Alarm): MainUiState =
    if (slot !in alarms.indices) this else copy(alarms = alarms.toMutableList().also { it[slot] = alarm })

private fun ClockError.toConnectionFailure(): ConnectionState.Failed = when (this) {
    is ClockError.AuthRejected -> ConnectionState.Failed(UiText(R.string.connection_auth_help), isAuthRejected = true)
    is ClockError.PermissionMissing -> ConnectionState.Failed(UiText(R.string.error_permission), isAuthRejected = false)
    else -> ConnectionState.Failed(UiText(R.string.connection_failed_help), isAuthRejected = false)
}

private fun Throwable.toMessage(): UiText = when (this) {
    is ClockError.AuthRejected -> UiText(R.string.connection_auth_help)
    is ClockError.PermissionMissing -> UiText(R.string.error_permission)
    is ClockError.ScanFailed -> UiText(R.string.error_scan_failed, code)
    is ClockError.Command -> UiText(R.string.error_command)
    else -> UiText(R.string.error_connection)
}
