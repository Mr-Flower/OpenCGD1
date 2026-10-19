package it.cgd1.sveglia.ui.main

import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.data.ringtone.BuiltInRingtone
import it.cgd1.sveglia.data.ringtone.CurrentRingtone
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
    val builtInRingtones: List<BuiltInRingtone> = emptyList(),
    /** null quando non c'è un caricamento in corso. */
    val ringtoneUploadProgress: Float? = null,
    val previewingRingtoneId: String? = null,
    val isConvertingRingtone: Boolean = false,
    val customRingtone: CustomRingtoneInfo? = null,
) {
    val currentRingtone: CurrentRingtone?
        get() = settings?.let { CurrentRingtone.identify(it.ringtoneSignature, builtInRingtones) }

    val usedAlarms: List<IndexedValue<Alarm>> get() = alarms.withIndex().filter { !it.value.isEmpty }
    val canAddAlarm: Boolean get() = settings != null && alarms.any { it.isEmpty }
}

/** Il file scelto dall'utente, già convertito; l'audio resta nel ViewModel. */
data class CustomRingtoneInfo(val name: String, val durationSeconds: Float)

/** Id di anteprima per il file dell'utente, distinto dagli id delle suonerie incluse. */
const val CUSTOM_RINGTONE_PREVIEW_ID = "custom"

sealed interface MainEffect {
    data class ShowMessage(val text: UiText) : MainEffect
}
