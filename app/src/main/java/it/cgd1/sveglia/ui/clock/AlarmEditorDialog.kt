package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Weekday
import it.cgd1.sveglia.ui.components.SwitchRow
import it.cgd1.sveglia.ui.daysLabel
import it.cgd1.sveglia.ui.shortName

private const val DEFAULT_HOUR = 7

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorDialog(initial: Alarm?, onDismiss: () -> Unit, onSave: (Alarm) -> Unit, onDelete: () -> Unit) {
    val time = rememberTimePickerState(
        initialHour = initial?.hour ?: DEFAULT_HOUR,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true,
    )
    // Set<Weekday> non è Bundle-friendly: si salvano gli ordinali.
    var dayOrdinals by rememberSaveable { mutableStateOf(initial?.days.orEmpty().map { it.ordinal }) }
    val days = dayOrdinals.map { Weekday.entries[it] }.toSet()
    val locale = LocalConfiguration.current.locales[0]
    var snooze by rememberSaveable { mutableStateOf(initial?.snooze ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.alarm_new else R.string.alarm_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
                Text(stringResource(R.string.alarm_repeat), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Weekday.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = {
                                dayOrdinals = if (day in days) dayOrdinals - day.ordinal else dayOrdinals + day.ordinal
                            },
                            label = { Text(day.shortName(locale)) },
                        )
                    }
                }
                Text(
                    if (days.isEmpty()) stringResource(R.string.alarm_once_hint) else daysLabel(days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(stringResource(R.string.alarm_snooze), snooze, onCheckedChange = { snooze = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Alarm(enabled = true, hour = time.hour, minute = time.minute, days = days, snooze = snooze))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
