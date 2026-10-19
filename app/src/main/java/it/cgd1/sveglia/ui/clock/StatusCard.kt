package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.main.ConnectionState
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.sensorLabel

@Composable
fun StatusCard(state: MainUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val liveModifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (val connection = state.connection) {
        ConnectionState.Connecting -> ElevatedCard(liveModifier) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.status_connecting), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is ConnectionState.Failed -> Card(
            liveModifier,
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
                        stringResource(if (connection.isAuthRejected) R.string.status_rejected else R.string.status_failed),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(connection.reason.asString(), style = MaterialTheme.typography.bodyMedium)
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
        ConnectionState.Connected, ConnectionState.Idle -> ElevatedCard(liveModifier) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(
                        if (connection == ConnectionState.Connected) R.string.status_connected else R.string.status_idle,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.sensor != null || state.battery != null) {
                    Text(sensorLabel(state.sensor, state.battery), style = MaterialTheme.typography.titleMedium)
                }
                state.firmware?.let {
                    Text(
                        stringResource(R.string.status_firmware, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
