package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.main.ClockReadings
import it.cgd1.sveglia.ui.main.ConnectionState
import it.cgd1.sveglia.ui.main.MainUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Temperatura e umidità in primo piano, come nell'app Qingping; sotto, batteria e stato del collegamento. */
@Composable
fun ClockDashboard(state: MainUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (state.connection as? ConnectionState.Failed)?.let { FailureCard(it, onRetry) }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    val celsius = state.settings?.unitCelsius ?: true
                    Reading(
                        label = stringResource(R.string.dashboard_temperature),
                        value = state.readings.temperature?.let {
                            if (celsius) stringResource(R.string.dashboard_celsius, it)
                            else stringResource(R.string.dashboard_fahrenheit, it * 9 / 5 + 32)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Reading(
                        label = stringResource(R.string.dashboard_humidity),
                        value = state.readings.humidity?.let { stringResource(R.string.dashboard_percent, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                StatusRow(state.connection, state.readings)
                state.firmware?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.status_firmware, it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Reading(label: String, value: String?, modifier: Modifier = Modifier) {
    val shown = value ?: "—"
    Column(modifier.clearAndSetSemantics { contentDescription = "$label $shown" }) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(shown, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun StatusRow(connection: ConnectionState, readings: ClockReadings) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val chipColors = AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        AssistChip(
            onClick = {},
            label = {
                Text(
                    stringResource(
                        when (connection) {
                            ConnectionState.Connecting -> R.string.status_connecting
                            ConnectionState.Connected -> R.string.status_connected
                            is ConnectionState.Failed -> R.string.status_failed
                            ConnectionState.Idle -> R.string.status_idle_short
                        },
                    ),
                )
            },
            leadingIcon = if (connection == ConnectionState.Connecting) {
                { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            colors = chipColors,
        )
        readings.battery?.let { battery ->
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.dashboard_battery, battery)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_battery), contentDescription = null, Modifier.size(18.dp)) },
                colors = chipColors,
            )
        }
    }
    val updated = readings.updatedAtMillis
    Text(
        when {
            updated == null -> stringResource(R.string.dashboard_no_readings)
            readings.batteryUpdatedAtMillis != null && readings.batteryUpdatedAtMillis != updated ->
                stringResource(R.string.dashboard_updated_battery, time(updated), time(readings.batteryUpdatedAtMillis))
            else -> stringResource(R.string.dashboard_updated, time(updated))
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FailureCard(failure: ConnectionState.Failed, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (failure.isAuthRejected) R.string.status_rejected else R.string.status_failed),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(failure.reason.asString(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
