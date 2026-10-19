package it.cgd1.sveglia.ui.main

import app.cash.turbine.test
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.ClockError
import it.cgd1.sveglia.data.ClockSnapshot
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Cgd1
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.Language
import it.cgd1.sveglia.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val settings = DeviceSettings(
        volume = 3, language = Language.ENGLISH, timeFormat24h = true, unitCelsius = true, alarmsEnabled = true,
        tzOffsetMinutes = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 60,
        screenLightSeconds = 10, dayBrightness = 60, nightBrightness = 10,
        nightStart = LocalTime.of(22, 0), nightEnd = LocalTime.of(7, 0), nightMode = false,
        ringtoneSignature = ByteArray(4), rawReserved = ByteArray(3),
    )
    private val existing = Alarm(enabled = true, hour = 7, minute = 0, days = emptySet(), snooze = false)
    private val alarms = List(Cgd1.ALARM_SLOT_COUNT) { if (it == 0) existing else Alarm.EMPTY }

    private val repository = FakeClockRepository(ClockSnapshot(settings, alarms, firmware = "1.0", battery = 80))
    private val clock = FoundClock("AA:BB:CC:DD:EE:FF", "Qingping Alarm Clock", rssi = -60, sensor = null)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // uiState è WhileSubscribed: senza un collector resterebbe al valore iniziale.
    private fun TestScope.openedViewModel(): MainViewModel = MainViewModel(repository).also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
        it.onOpenFound(clock)
        advanceUntilIdle()
    }

    @Test fun openingAClockLoadsItAndRemembersIt() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.uiState.value.let { state ->
            assertEquals(ConnectionState.Connected, state.connection)
            assertEquals(settings, state.settings)
            assertEquals(alarms, state.alarms)
            assertEquals(SavedClock(clock.address, clock.name), state.savedClock)
            assertFalse(state.isBusy)
        }
    }

    @Test fun rejectedTokenShowsAuthFailure() = runTest(dispatcher) {
        repository.session.loadError = ClockError.AuthRejected(RuntimeException())
        val vm = openedViewModel()
        val connection = vm.uiState.value.connection
        assertTrue(connection is ConnectionState.Failed && connection.isAuthRejected)
    }

    @Test fun newAlarmGoesInTheFirstFreeSlot() = runTest(dispatcher) {
        val vm = openedViewModel()
        val alarm = Alarm(enabled = true, hour = 6, minute = 30, days = emptySet(), snooze = true)
        vm.onAddAlarm(alarm)
        advanceUntilIdle()
        assertEquals(listOf(1 to alarm), repository.session.writtenAlarms)
        assertEquals(alarm, vm.uiState.value.alarms[1])
    }

    @Test fun failedWriteKeepsStateAndShowsMessage() = runTest(dispatcher) {
        val vm = openedViewModel()
        repository.session.writeError = ClockError.Command(RuntimeException())
        vm.effects.test {
            assertEquals(R.string.message_time_synced, (awaitItem() as MainEffect.ShowMessage).text.id)
            vm.onToggleAlarm(0, enabled = false)
            advanceUntilIdle()
            assertEquals(MainEffect.ShowMessage(UiText(R.string.error_command)), awaitItem())
        }
        assertTrue(vm.uiState.value.alarms[0].enabled)
    }

    @Test fun unchangedSettingsAreNotWritten() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onUpdateSettings { it }
        advanceUntilIdle()
        assertTrue(repository.session.writtenSettings.isEmpty())
    }

    @Test fun closingTheClockReleasesTheSession() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onCloseClock()
        advanceUntilIdle()
        assertTrue(repository.session.closed)
        assertNull(vm.uiState.value.openClock)
    }
}
