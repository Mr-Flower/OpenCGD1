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
import it.cgd1.sveglia.data.TimeSource
import it.cgd1.sveglia.data.ringtone.CustomSlots
import it.cgd1.sveglia.data.ringtone.RingtoneError
import it.cgd1.sveglia.data.ringtone.RingtoneOption
import it.cgd1.sveglia.data.ringtone.RingtonePlayer
import it.cgd1.sveglia.data.ringtone.RingtoneRepository
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.AudioUpload
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.ui.UiText
import it.cgd1.sveglia.ui.formatOffset
import it.cgd1.sveglia.ui.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

private const val SCAN_TIMEOUT_MS = 30_000L
private const val ADVERTISEMENT_TIMEOUT_MS = 4_000L
private const val TEST_ALARM_MARGIN_S = 30
private const val TEST_ALARM_RING_MS = 90_000L

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ClockRepository,
    private val ringtones: RingtoneRepository,
    private val player: RingtonePlayer,
    private val time: TimeSource,
) : ViewModel(), ScanActions, ClockActions, RingtoneActions {

    private val state = MutableStateFlow(
        MainUiState(isBluetoothAvailable = repository.isBluetoothAvailable, builtInRingtones = ringtones.builtIns),
    )

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

    private var previewJob: Job? = null
    private var customPcm: ByteArray? = null

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
        newSession.liveSensor
            .onEach { sensor -> state.update { it.copy(readings = it.readings.withLive(sensor, nowMillis())) } }
            .launchIn(scope)
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
                openClock = clock, connection = ConnectionState.Connecting, isBusy = false,
                settings = null, alarms = emptyList(), firmware = null, testAlarm = null, ringtoneUploadProgress = null,
                readings = sensor?.let { s -> ClockReadings().withAdvertisement(s, nowMillis()) } ?: ClockReadings(),
            )
        }
        load(syncClock = true, readAdvertisement = sensor?.battery == null)
    }

    override fun onCloseClock() {
        releaseSession()
        state.update {
            it.copy(
                openClock = null, connection = ConnectionState.Idle, settings = null, alarms = emptyList(), isBusy = false,
                testAlarm = null, ringtoneUploadProgress = null,
            )
        }
    }

    private fun releaseSession() {
        sessionScope?.cancel()
        sessionScope = null
        session?.close()
        session = null
    }

    // Da scollegata la sveglia trasmette di nuovo gli advertisement: è l'occasione per aggiornare la batteria.
    override fun onRefresh() = load(syncClock = false, readAdvertisement = state.value.connection != ConnectionState.Connected)

    override fun onRetry() = load(syncClock = true, readAdvertisement = state.value.readings.battery == null)

    private fun load(syncClock: Boolean, readAdvertisement: Boolean) = op(connecting = true) { s, update ->
        if (readAdvertisement) readAdvertisement(update)
        val snapshot = s.load().getOrThrow()
        update {
            it.copy(
                settings = snapshot.settings,
                alarms = snapshot.alarms,
                firmware = snapshot.firmware,
                connection = ConnectionState.Connected,
            )
        }
        if (syncClock) syncTime(s, snapshot.settings, update)
    }

    /** La batteria si legge solo dall'advertisement, che la sveglia smette di trasmettere appena ci si collega. */
    private suspend fun readAdvertisement(update: StateUpdater) {
        val address = state.value.openClock?.address ?: return
        val found = withTimeoutOrNull(ADVERTISEMENT_TIMEOUT_MS) {
            repository.scan()
                .catch { }
                .firstOrNull { it.address == address && it.sensor != null }
        }
        found?.sensor?.let { sensor ->
            update { it.copy(readings = it.readings.withAdvertisement(sensor, nowMillis())) }
        }
    }

    override fun onSyncTime() = op { s, update ->
        val current = state.value.settings ?: s.readSettings().getOrThrow()
        syncTime(s, current, update)
    }

    private suspend fun syncTime(s: ClockSession, current: DeviceSettings, update: StateUpdater) {
        val offset = time.zone().rules.getOffset(time.now()).totalSeconds / 60
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
        try {
            val current = state.value.settings ?: return@op
            val next = transform(current)
            if (next == current) return@op
            s.writeSettings(next).getOrThrow()
            update { it.copy(settings = next) }
        } finally {
            update { it.copy(settingsRevision = it.settingsRevision + 1) }
        }
    }

    override fun onLoadOriginalRingtones() {
        val current = state.value.originalRingtones
        if (current is OriginalRingtonesState.Loading || current is OriginalRingtonesState.Loaded) return
        viewModelScope.launch {
            state.update { it.copy(originalRingtones = OriginalRingtonesState.Loading) }
            val result = ringtones.originals()
            state.update {
                it.copy(originalRingtones = result.fold({ list -> OriginalRingtonesState.Loaded(list) }, { OriginalRingtonesState.Failed }))
            }
        }
    }

    // La sveglia non ha un comando per riprodurre la suoneria caricata: l'unico modo fedele è farla suonare davvero.
    override fun onTestRingtone() = op { s, update ->
        val settings = state.value.settings ?: return@op
        if (!settings.alarmsEnabled) {
            showMessage(UiText(R.string.message_test_alarm_master_off))
            return@op
        }
        val slot = state.value.alarms.indexOfFirst { it.isEmpty }
        if (slot < 0) {
            showMessage(UiText(R.string.message_slots_full))
            return@op
        }
        val now = LocalDateTime.ofInstant(time.now(), time.zone())
        // Almeno 30 secondi di margine: il tempo di scriverla e di avvicinarsi alla sveglia.
        val fireAt = now.plusMinutes(if (now.second < TEST_ALARM_MARGIN_S) 1 else 2).withSecond(0).withNano(0)
        val alarm = Alarm(enabled = true, hour = fireAt.hour, minute = fireAt.minute, days = emptySet(), snooze = false)
        s.writeAlarm(slot, alarm).getOrThrow()
        update { it.withAlarm(slot, alarm).copy(testAlarm = alarm) }
        showMessage(UiText(R.string.message_test_alarm_set, formatTime(alarm.hour, alarm.minute)))
        val address = state.value.openClock?.address
        // Fuori dallo scope della sessione: se la sveglia si scollega per inattività e poi si riapre,
        // la pulizia deve avvenire lo stesso. Il controllo sull'indirizzo evita di toccare un'altra sveglia.
        viewModelScope.launch {
            delay(Duration.between(now, fireAt).toMillis() + TEST_ALARM_RING_MS)
            if (state.value.openClock?.address == address) removeTestAlarm(slot, alarm)
        }
    }

    private fun removeTestAlarm(slot: Int, alarm: Alarm) = op { s, update ->
        try {
            if (state.value.alarms.getOrNull(slot) == alarm) {
                s.writeAlarm(slot, Alarm.EMPTY).getOrThrow()
                update { it.withAlarm(slot, Alarm.EMPTY) }
            }
        } finally {
            update { it.copy(testAlarm = null) }
        }
    }

    override fun onVolumeChange(volume: Int) = op { s, update ->
        try {
            val current = state.value.settings ?: return@op
            if (current.volume != volume) {
                val next = current.copy(volume = volume)
                s.writeSettings(next).getOrThrow()
                update { it.copy(settings = next) }
            }
            s.previewVolume(volume).getOrThrow()
        } finally {
            update { it.copy(settingsRevision = it.settingsRevision + 1) }
        }
    }

    override fun onPreviewRingtone(id: String) {
        val ringtone = ringtoneOption(id) ?: return
        preview(id) { ringtones.loadPcm(ringtone) }
    }

    override fun onPreviewCustomRingtone() {
        val pcm = customPcm ?: return
        preview(CUSTOM_RINGTONE_PREVIEW_ID) { Result.success(pcm) }
    }

    private fun preview(id: String, load: suspend () -> Result<ByteArray>) {
        val previous = previewJob
        previewJob = viewModelScope.launch {
            // Il finally della vecchia anteprima chiama stop(): va atteso prima di far partire la nuova.
            previous?.cancelAndJoin()
            val pcm = load().getOrElse { e ->
                showMessage(e.toMessage())
                return@launch
            }
            state.update { it.copy(previewingRingtoneId = id) }
            try {
                if (player.play(pcm)) {
                    delay(pcm.size * 1000L / AudioUpload.SAMPLE_RATE)
                } else {
                    showMessage(UiText(R.string.error_preview))
                }
            } finally {
                player.stop()
                state.update { if (it.previewingRingtoneId == id) it.copy(previewingRingtoneId = null) else it }
            }
        }
    }

    override fun onStopPreview() {
        previewJob?.cancel()
    }

    override fun onSelectRingtone(id: String) {
        val ringtone = ringtoneOption(id) ?: return
        if (state.value.settings?.ringtoneSignature?.contentEquals(ringtone.signature) == true) return
        onStopPreview()
        uploadRingtone(ringtone.signature, onDone = {}) { ringtones.loadPcm(ringtone).getOrThrow() }
    }

    override fun onCustomRingtonePicked(uri: String) {
        onStopPreview()
        viewModelScope.launch {
            state.update { it.copy(isConvertingRingtone = true) }
            ringtones.convert(uri)
                .onSuccess { custom ->
                    customPcm = custom.pcm
                    state.update { it.copy(customRingtone = CustomRingtoneInfo(custom.name, custom.durationSeconds)) }
                }
                .onFailure { e -> showMessage(e.toMessage()) }
            state.update { it.copy(isConvertingRingtone = false) }
        }
    }

    override fun onUploadCustomRingtone() {
        val pcm = customPcm ?: return
        val current = state.value.settings?.ringtoneSignature ?: return
        onStopPreview()
        uploadRingtone(CustomSlots.next(current), onDone = ::clearCustomRingtone) { pcm }
    }

    override fun onDismissCustomRingtone() {
        if (state.value.previewingRingtoneId == CUSTOM_RINGTONE_PREVIEW_ID) onStopPreview()
        clearCustomRingtone()
    }

    private fun clearCustomRingtone() {
        customPcm = null
        state.update { it.copy(customRingtone = null) }
    }

    /** Carica l'audio e poi lo attiva scrivendone la firma nelle impostazioni. */
    private fun uploadRingtone(signature: ByteArray, onDone: () -> Unit, load: suspend () -> ByteArray) = op { s, update ->
        val pcm = load()
        update { it.copy(ringtoneUploadProgress = 0f) }
        try {
            s.uploadRingtone(pcm, signature) { progress -> update { it.copy(ringtoneUploadProgress = progress) } }.getOrThrow()
            val current = state.value.settings ?: return@op
            val next = current.copy(ringtoneSignature = signature)
            s.writeSettings(next).getOrThrow()
            update { it.copy(settings = next) }
            onDone()
            showMessage(UiText(R.string.message_ringtone_set))
        } finally {
            update { it.copy(ringtoneUploadProgress = null) }
        }
    }

    private fun ringtoneOption(id: String): RingtoneOption? {
        val originals = (state.value.originalRingtones as? OriginalRingtonesState.Loaded)?.ringtones.orEmpty()
        return (state.value.builtInRingtones + originals).firstOrNull { it.id == id }
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
                } catch (e: RingtoneError) {
                    showMessage(e.toMessage())
                } finally {
                    update { it.copy(isBusy = false) }
                }
            }
        }
    }

    private fun nowMillis(): Long = time.now().toEpochMilli()

    private fun showMessage(text: UiText) {
        _effects.trySend(MainEffect.ShowMessage(text))
    }

    override fun onCleared() {
        player.stop()
        releaseSession()
    }
}

private typealias StateUpdater = ((MainUiState) -> MainUiState) -> Unit

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
    is RingtoneError.Empty -> UiText(R.string.error_ringtone_empty)
    is RingtoneError.Unsupported -> UiText(R.string.error_ringtone_unsupported)
    is RingtoneError.Network -> UiText(R.string.error_ringtone_download)
    else -> UiText(R.string.error_connection)
}
