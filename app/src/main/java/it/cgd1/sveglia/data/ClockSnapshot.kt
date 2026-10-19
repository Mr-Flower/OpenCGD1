package it.cgd1.sveglia.data

import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.DeviceSettings

data class ClockSnapshot(
    val settings: DeviceSettings,
    val alarms: List<Alarm>,
    val firmware: String?,
)
