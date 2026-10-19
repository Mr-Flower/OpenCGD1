package it.cgd1.sveglia.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.ui.components.SectionHeader
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.main.ScanActions
import it.cgd1.sveglia.ui.sensorLabel
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    state: MainUiState,
    actions: ScanActions,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(actions) { actions.onScanScreenShown() }
    DisposableEffect(actions) { onDispose { actions.onStopScan() } }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.app_settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.savedClock?.let { saved ->
                item(key = "saved") { SavedClockCard(saved, onClick = { actions.onOpenSaved(saved) }) }
            }
            item(key = "header") {
                ScanHeader(
                    isScanning = state.isScanning,
                    onStart = actions::onStartScan,
                    onStop = actions::onStopScan,
                )
            }
            if (state.foundClocks.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (state.isScanning) R.string.scan_in_progress else R.string.scan_idle_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.contentWidth(),
                    )
                }
            }
            items(state.foundClocks, key = { it.address }) { found ->
                FoundClockCard(found, onClick = { actions.onOpenFound(found) }, modifier = Modifier.animateItem())
            }
            item(key = "help") { FirstTimeCard() }
        }
    }
}

// Su tablet e telefoni orizzontali il contenuto non deve allargarsi a tutto schermo.
private fun Modifier.contentWidth(): Modifier = widthIn(max = 640.dp).fillMaxWidth()

@Composable
private fun SavedClockCard(clock: SavedClock, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.contentWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.scan_your_clock),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                clock.name ?: stringResource(R.string.default_clock_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(clock.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.scan_tap_to_open),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ScanHeader(isScanning: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.contentWidth()) {
        SectionHeader(stringResource(R.string.scan_nearby), Modifier.weight(1f))
        if (isScanning) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
        } else {
            TextButton(onClick = onStart) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_search))
            }
        }
    }
}

@Composable
private fun FoundClockCard(clock: FoundClock, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier.contentWidth()) {
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.ic_clock), contentDescription = null) },
            headlineContent = { Text(clock.name ?: stringResource(R.string.default_clock_name)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.scan_address_rssi, clock.address, clock.rssi))
                    clock.sensor?.let { Text(sensorLabel(it, null)) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun FirstTimeCard() {
    OutlinedCard(modifier = Modifier.contentWidth()) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            headlineContent = { Text(stringResource(R.string.scan_first_time_title)) },
            supportingContent = { Text(stringResource(R.string.scan_first_time_body)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private object PreviewScanActions : ScanActions {
    override fun onScanScreenShown() = Unit
    override fun onStartScan() = Unit
    override fun onStopScan() = Unit
    override fun onOpenSaved(clock: SavedClock) = Unit
    override fun onOpenFound(clock: FoundClock) = Unit
}

@Preview(showBackground = true)
@Composable
private fun ScanScreenPreview() {
    OpenCgd1Theme(dynamicColor = false) {
        ScanScreen(
            state = MainUiState(
                savedClock = SavedClock("AA:BB:CC:DD:EE:FF", "Qingping Alarm Clock"),
                isScanning = true,
                foundClocks = listOf(
                    FoundClock("11:22:33:44:55:66", "Qingping Alarm Clock", -62, SensorData(21.4, 48.0, 87)),
                ),
            ),
            actions = PreviewScanActions,
            snackbarHostState = remember { SnackbarHostState() },
            onOpenSettings = {},
        )
    }
}
