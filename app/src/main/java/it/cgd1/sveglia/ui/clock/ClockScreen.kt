package it.cgd1.sveglia.ui.clock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Cgd1
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.Language
import it.cgd1.sveglia.protocol.Weekday
import it.cgd1.sveglia.ui.components.SectionHeader
import it.cgd1.sveglia.ui.components.SwitchRow
import it.cgd1.sveglia.ui.main.ClockActions
import it.cgd1.sveglia.ui.main.ClockReadings
import it.cgd1.sveglia.ui.main.ConnectionState
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import java.time.LocalTime

/** Lo slot di una sveglia nuova si sceglie al salvataggio; qui serve solo a distinguere "nuova" da "modifica". */
private const val NEW_ALARM = -1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    state: MainUiState,
    actions: ClockActions,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenRingtones: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = actions::onCloseClock)
    var editingSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val settings = state.settings
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.openClock?.name ?: stringResource(R.string.default_clock_name)) },
                navigationIcon = {
                    IconButton(onClick = actions::onCloseClock) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = actions::onRefresh, enabled = !state.isBusy) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.app_settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.canAddAlarm) {
                ExtendedFloatingActionButton(
                    onClick = { editingSlot = NEW_ALARM },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_new_alarm)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "status") {
                    ClockDashboard(state, onRetry = actions::onRetry, modifier = Modifier.contentWidth())
                }
                if (settings != null) {
                    item(key = "next") {
                        NextAlarmCard(
                            settings = settings,
                            alarms = state.alarms,
                            isBusy = state.isBusy,
                            onSyncTime = actions::onSyncTime,
                            modifier = Modifier.contentWidth(),
                        )
                    }
                    item(key = "alarms-header") {
                        SectionHeader(stringResource(R.string.section_alarms), Modifier.contentWidth())
                    }
                    item(key = "alarms-master") {
                        OutlinedCard(Modifier.contentWidth()) {
                            SwitchRow(
                                label = stringResource(R.string.alarms_master_switch),
                                checked = settings.alarmsEnabled,
                                onCheckedChange = { on -> actions.onUpdateSettings { it.copy(alarmsEnabled = on) } },
                            )
                        }
                    }
                    if (state.usedAlarms.isEmpty()) {
                        item(key = "alarms-empty") {
                            Text(
                                stringResource(R.string.alarms_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.contentWidth(),
                            )
                        }
                    }
                    items(state.usedAlarms, key = { "alarm-${it.index}" }) { (slot, alarm) ->
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { actions.onToggleAlarm(slot, it) },
                            onClick = { editingSlot = slot },
                            modifier = Modifier
                                .animateItem()
                                .contentWidth(),
                        )
                    }
                    item(key = "settings-header") {
                        SectionHeader(stringResource(R.string.section_settings), Modifier.contentWidth())
                    }
                    item(key = "settings") {
                        DeviceSettingsCard(
                            settings = settings,
                            currentRingtone = state.currentRingtone,
                            onUpdate = actions::onUpdateSettings,
                            onOpenRingtones = onOpenRingtones,
                            modifier = Modifier.contentWidth(),
                        )
                    }
                }
            }
        }
    }

    editingSlot?.let { slot ->
        AlarmEditorDialog(
            initial = if (slot == NEW_ALARM) null else state.alarms.getOrNull(slot)?.takeIf { !it.isEmpty },
            onDismiss = { editingSlot = null },
            onSave = {
                if (slot == NEW_ALARM) actions.onAddAlarm(it) else actions.onSaveAlarm(slot, it)
                editingSlot = null
            },
            onDelete = {
                actions.onDeleteAlarm(slot)
                editingSlot = null
            },
        )
    }
}

internal fun Modifier.contentWidth(): Modifier = widthIn(max = 640.dp).fillMaxWidth()

private object PreviewClockActions : ClockActions {
    override fun onCloseClock() = Unit
    override fun onRefresh() = Unit
    override fun onRetry() = Unit
    override fun onSyncTime() = Unit
    override fun onAddAlarm(alarm: Alarm) = Unit
    override fun onSaveAlarm(slot: Int, alarm: Alarm) = Unit
    override fun onToggleAlarm(slot: Int, enabled: Boolean) = Unit
    override fun onDeleteAlarm(slot: Int) = Unit
    override fun onUpdateSettings(transform: (DeviceSettings) -> DeviceSettings) = Unit
}

internal val previewSettings = DeviceSettings(
    volume = 3, language = Language.ENGLISH, timeFormat24h = true, unitCelsius = true, alarmsEnabled = true,
    tzOffsetMinutes = 120, screenLightSeconds = 10, dayBrightness = 60, nightBrightness = 10,
    nightStart = LocalTime.of(22, 0), nightEnd = LocalTime.of(7, 0), nightMode = true,
    ringtoneSignature = ByteArray(4), rawReserved = ByteArray(3),
)

@Preview(showBackground = true)
@Composable
private fun ClockScreenPreview() {
    val alarms = List(Cgd1.ALARM_SLOT_COUNT) { Alarm.EMPTY }.toMutableList().apply {
        this[0] = Alarm(true, 7, 0, setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY), true)
        this[1] = Alarm(false, 9, 30, setOf(Weekday.SATURDAY, Weekday.SUNDAY), false)
    }
    OpenCgd1Theme(dynamicColor = false) {
        ClockScreen(
            state = MainUiState(
                openClock = SavedClock("AA:BB:CC:DD:EE:FF", "Qingping Alarm Clock"),
                connection = ConnectionState.Connected,
                settings = previewSettings,
                alarms = alarms,
                firmware = "1.0.4",
                readings = ClockReadings(21.4, 48.0, 87, updatedAtMillis = 0, batteryUpdatedAtMillis = 0),
            ),
            actions = PreviewClockActions,
            snackbarHostState = remember { SnackbarHostState() },
            onOpenSettings = {},
            onOpenRingtones = {},
        )
    }
}
