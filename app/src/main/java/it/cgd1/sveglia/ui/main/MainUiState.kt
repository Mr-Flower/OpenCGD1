package it.cgd1.sveglia.ui.main

import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.ui.UiText

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val reason: UiText, val isAuthRejected: Boolean) : ConnectionState
}

data class MainUiState(
    val isBluetoothAvailable: Boolean = true,
    /** null finché non si conosce lo stato, per non far lampeggiare la schermata "Bluetooth spento". */
    val isBluetoothEnabled: Boolean? = null,
    val savedClock: SavedClock? = null,
    val isScanning: Boolean = false,
    val foundClocks: List<FoundClock> = emptyList(),
    val openClock: SavedClock? = null,
    val connection: ConnectionState = ConnectionState.Idle,
    val isBusy: Boolean = false,
    val settings: DeviceSettings? = null,
    val alarms: List<Alarm> = emptyList(),
    val firmware: String? = null,
    val battery: Int? = null,
    val sensor: SensorData? = null,
) {
    val usedAlarms: List<IndexedValue<Alarm>> get() = alarms.withIndex().filter { !it.value.isEmpty }
    val canAddAlarm: Boolean get() = settings != null && alarms.any { it.isEmpty }
}

sealed interface MainEffect {
    data class ShowMessage(val text: UiText) : MainEffect
}
