package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.ui.daysLabel
import it.cgd1.sveglia.ui.formatTime

@Composable
fun AlarmCard(alarm: Alarm, onToggle: (Boolean) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val days = daysLabel(alarm.days)
    val details = if (alarm.snooze) stringResource(R.string.alarm_details_snooze, days) else days
    val contentColor = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    ElevatedCard(onClick = onClick, modifier = modifier) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, color = contentColor)
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val stateText = stringResource(if (alarm.enabled) R.string.alarm_on else R.string.alarm_off)
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { stateDescription = stateText },
            )
        }
    }
}
