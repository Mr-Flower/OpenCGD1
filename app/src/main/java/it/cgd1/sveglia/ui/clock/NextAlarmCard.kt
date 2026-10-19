package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Codec
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.ui.formatOffset
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MINUTE_MS = 60_000L

@Composable
fun NextAlarmCard(
    settings: DeviceSettings,
    alarms: List<Alarm>,
    isBusy: Boolean,
    onSyncTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ricalcolata a ogni minuto: dopo che una sveglia ha suonato la "prossima" cambia anche senza nuovi dati.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            value = LocalDateTime.now()
        }
    }
    val next = remember(alarms, settings.alarmsEnabled, now) {
        if (settings.alarmsEnabled) Codec.nextAlarm(alarms, now) else null
    }
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.next_alarm_pattern)
    val formatter = remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.next_alarm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    !settings.alarmsEnabled -> stringResource(R.string.next_alarm_disabled)
                    next == null -> stringResource(R.string.next_alarm_none)
                    else -> next.format(formatter).replaceFirstChar { it.titlecase(locale) }
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.clock_timezone, formatOffset(settings.tzOffsetMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onSyncTime, enabled = !isBusy) { Text(stringResource(R.string.action_sync_time)) }
        }
    }
}
