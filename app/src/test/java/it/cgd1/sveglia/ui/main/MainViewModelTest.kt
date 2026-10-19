package it.cgd1.sveglia.ui.main

import app.cash.turbine.test
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.ClockError
import it.cgd1.sveglia.data.ClockSnapshot
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.data.ringtone.BuiltInRingtone
import it.cgd1.sveglia.data.ringtone.CurrentRingtone
import it.cgd1.sveglia.data.ringtone.CustomRingtone
import it.cgd1.sveglia.data.ringtone.CustomSlots
import it.cgd1.sveglia.data.ringtone.OriginalRingtone
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.Cgd1
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.Language
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.protocol.toHex
import it.cgd1.sveglia.ui.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val settings = DeviceSettings(
        volume = 3, language = Language.ENGLISH, timeFormat24h = true, unitCelsius = true, alarmsEnabled = true,
        tzOffsetMinutes = 120,
        screenLightSeconds = 10, dayBrightness = 60, nightBrightness = 10,
        nightStart = LocalTime.of(22, 0), nightEnd = LocalTime.of(7, 0), nightMode = false,
        ringtoneSignature = ByteArray(4), rawReserved = ByteArray(3),
    )
    private val existing = Alarm(enabled = true, hour = 7, minute = 0, days = emptySet(), snooze = false)
    private val alarms = List(Cgd1.ALARM_SLOT_COUNT) { if (it == 0) existing else Alarm.EMPTY }

    private val repository = FakeClockRepository(ClockSnapshot(settings, alarms, firmware = "1.0"))
    private val argon = BuiltInRingtone("argon", "Argon", "The Android Open Source Project", "Apache 2.0", rawRes = 0, signatureHex = "4f430106")
    private val ringtones = FakeRingtoneRepository(listOf(argon))
    private val player = FakeRingtonePlayer()
    private val clock = FoundClock("AA:BB:CC:DD:EE:FF", "Qingping Alarm Clock", rssi = -60, sensor = null)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = MainViewModel(repository, ringtones, player, FakeTimeSource())

    // uiState è WhileSubscribed: senza un collector resterebbe al valore iniziale.
    private fun TestScope.openedViewModel(): MainViewModel = newViewModel().also {
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

    @Test fun choosingABuiltInRingtoneUploadsItAndActivatesItsSignature() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onSelectRingtone(argon.id)
        advanceUntilIdle()
        val (pcm, signature) = repository.session.uploads.single()
        assertArrayEquals(ringtones.loadPcm(argon).getOrThrow(), pcm)
        assertArrayEquals(argon.signature, signature)
        assertArrayEquals(argon.signature, repository.session.writtenSettings.last().ringtoneSignature)
        assertEquals(CurrentRingtone.BuiltIn(argon), vm.uiState.value.currentRingtone)
        assertNull(vm.uiState.value.ringtoneUploadProgress)
    }

    @Test fun customRingtonesAlternateBetweenTheTwoSlots() = runTest(dispatcher) {
        val vm = openedViewModel()
        ringtones.converted = Result.success(CustomRingtone("mine", ByteArray(800)))
        repeat(2) {
            vm.onCustomRingtonePicked("content://audio/1")
            advanceUntilIdle()
            vm.onUploadCustomRingtone()
            advanceUntilIdle()
        }
        assertEquals(listOf(CustomSlots.A, CustomSlots.B), repository.session.uploads.map { it.second.toHex() })
        assertNull(vm.uiState.value.customRingtone)
    }

    @Test fun previewStopsByItself() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onPreviewRingtone(argon.id)
        runCurrent()
        assertEquals(argon.id, vm.uiState.value.previewingRingtoneId)
        advanceUntilIdle()
        assertNull(player.playing)
        assertNull(vm.uiState.value.previewingRingtoneId)
    }

    @Test fun batteryComesOnlyFromTheAdvertisement() = runTest(dispatcher) {
        repository.scanResults.tryEmit(clock.copy(sensor = SensorData(21.5, 40.0, battery = 63)))
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onOpenSaved(SavedClock(clock.address, clock.name))
        advanceUntilIdle()
        val readings = vm.uiState.value.readings
        assertEquals(63, readings.battery)
        assertEquals(21.5, readings.temperature!!, 0.0)
    }

    @Test fun liveSensorUpdatesTemperatureButKeepsBattery() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onOpenFound(clock.copy(sensor = SensorData(20.0, 50.0, battery = 70)))
        advanceUntilIdle()
        repository.session.liveSensor.emit(SensorData(22.3, 45.0, battery = null))
        advanceUntilIdle()
        val readings = vm.uiState.value.readings
        assertEquals(22.3, readings.temperature!!, 0.0)
        assertEquals(70, readings.battery)
    }

    @Test fun originalRingtonesAreLoadedAndSelectable() = runTest(dispatcher) {
        val cuckoo = OriginalRingtone("ea2d4c02", "Cuckoo", "https://qingfseu.cleargrass.com/rings/ea2d4c02.pcm")
        ringtones.originals = Result.success(listOf(cuckoo))
        val vm = openedViewModel()
        vm.onLoadOriginalRingtones()
        advanceUntilIdle()
        vm.onSelectRingtone(cuckoo.id)
        advanceUntilIdle()
        assertEquals("ea2d4c02", repository.session.uploads.single().second.toHex())
        assertEquals(CurrentRingtone.Original("Cuckoo"), vm.uiState.value.currentRingtone)
    }

    @Test fun volumeChangeIsWrittenAndPreviewed() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onVolumeChange(5)
        advanceUntilIdle()
        assertEquals(5, repository.session.writtenSettings.last().volume)
        assertEquals(1, repository.session.clockPreviews)
    }

    @Test fun testAlarmIsWrittenInAFreeSlotAndRemovedAfterRinging() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onTestRingtone()
        runCurrent()
        val (slot, alarm) = repository.session.writtenAlarms.single()
        assertEquals(1, slot)
        assertTrue(alarm.enabled && alarm.days.isEmpty())
        assertEquals(alarm, vm.uiState.value.testAlarm)
        assertEquals(10 to 1, alarm.hour to alarm.minute)
        advanceUntilIdle()
        assertEquals(1 to Alarm.EMPTY, repository.session.writtenAlarms.last())
        assertNull(vm.uiState.value.testAlarm)
    }

    @Test fun closingTheClockClearsAPendingTestAlarm() = runTest(dispatcher) {
        val vm = openedViewModel()
        vm.onTestRingtone()
        runCurrent()
        vm.onCloseClock()
        advanceUntilIdle()
        assertNull(vm.uiState.value.testAlarm)
    }

    @Test fun failedSettingsWriteStillBumpsTheRevision() = runTest(dispatcher) {
        val vm = openedViewModel()
        val before = vm.uiState.value.settingsRevision
        repository.session.writeError = ClockError.Connection(RuntimeException())
        vm.onUpdateSettings { it.copy(dayBrightness = 90) }
        advanceUntilIdle()
        assertEquals(before + 1, vm.uiState.value.settingsRevision)
        assertEquals(60, vm.uiState.value.settings?.dayBrightness)
    }
}
