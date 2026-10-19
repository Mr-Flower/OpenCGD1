package it.cgd1.sveglia.ui.main

import androidx.compose.runtime.Stable
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings

@Stable
interface ScanActions {
    fun onScanScreenShown()
    fun onStartScan()
    fun onStopScan()
    fun onOpenSaved(clock: SavedClock)
    fun onOpenFound(clock: FoundClock)
}

@Stable
interface ClockActions {
    fun onCloseClock()
    fun onRefresh()
    fun onRetry()
    fun onSyncTime()
    fun onAddAlarm(alarm: Alarm)
    fun onSaveAlarm(slot: Int, alarm: Alarm)
    fun onToggleAlarm(slot: Int, enabled: Boolean)
    fun onDeleteAlarm(slot: Int)

    /** Scrive il volume e fa suonare un bip di prova a quel volume. */
    fun onVolumeChange(volume: Int)

    /** [transform] si applica alle impostazioni correnti al momento della scrittura, non del tocco. */
    fun onUpdateSettings(transform: (DeviceSettings) -> DeviceSettings)
}

@Stable
interface RingtoneActions {
    fun onLoadOriginalRingtones()
    fun onTestRingtone()
    fun onPreviewRingtone(id: String)
    fun onPreviewCustomRingtone()
    fun onStopPreview()
    fun onSelectRingtone(id: String)
    fun onCustomRingtonePicked(uri: String)
    fun onUploadCustomRingtone()
    fun onDismissCustomRingtone()
}
