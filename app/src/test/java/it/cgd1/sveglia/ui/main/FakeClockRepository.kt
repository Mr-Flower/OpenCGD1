package it.cgd1.sveglia.ui.main

import it.cgd1.sveglia.ble.FoundClock
import it.cgd1.sveglia.data.ClockError
import it.cgd1.sveglia.data.ClockRepository
import it.cgd1.sveglia.data.ClockSession
import it.cgd1.sveglia.data.ClockSnapshot
import it.cgd1.sveglia.data.SavedClock
import it.cgd1.sveglia.data.TimeSource
import it.cgd1.sveglia.data.ringtone.BuiltInRingtone
import it.cgd1.sveglia.data.ringtone.CustomRingtone
import it.cgd1.sveglia.data.ringtone.OriginalRingtone
import it.cgd1.sveglia.data.ringtone.RingtoneError
import it.cgd1.sveglia.data.ringtone.RingtoneOption
import it.cgd1.sveglia.data.ringtone.RingtonePlayer
import it.cgd1.sveglia.data.ringtone.RingtoneRepository
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.AudioUpload
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.SensorData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId

class FakeClockRepository(private val snapshot: ClockSnapshot) : ClockRepository {
    override val isBluetoothAvailable = true
    override val isBluetoothEnabled = MutableStateFlow(true)
    override val savedClock = MutableStateFlow<SavedClock?>(null)
    val scanResults = MutableSharedFlow<FoundClock>(replay = 1)
    val session = FakeClockSession(snapshot)

    override fun isLocationRequiredButOff() = false

    override fun scan(): Flow<FoundClock> = scanResults

    override suspend fun remember(clock: SavedClock) {
        savedClock.value = clock
    }

    override fun openSession(address: String): ClockSession = session
}

class FakeClockSession(private val snapshot: ClockSnapshot) : ClockSession {
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(false)
    override val liveSensor = MutableSharedFlow<SensorData>()
    var clockPreviews = 0
    var loadError: ClockError? = null
    var writeError: ClockError? = null
    val writtenAlarms = mutableListOf<Pair<Int, Alarm>>()
    val writtenSettings = mutableListOf<DeviceSettings>()
    var closed = false

    override suspend fun load(): Result<ClockSnapshot> = loadError?.let { Result.failure(it) } ?: Result.success(snapshot)

    override suspend fun readSettings(): Result<DeviceSettings> = Result.success(snapshot.settings)

    override suspend fun writeSettings(settings: DeviceSettings): Result<Unit> = write { writtenSettings += settings }

    override suspend fun writeAlarm(slot: Int, alarm: Alarm): Result<Unit> = write { writtenAlarms += slot to alarm }

    val uploads = mutableListOf<Pair<ByteArray, ByteArray>>()

    override suspend fun uploadRingtone(pcm: ByteArray, signature: ByteArray, onProgress: (Float) -> Unit): Result<Unit> =
        write {
            onProgress(1f)
            uploads += pcm to signature
        }

    override suspend fun previewVolume(volume: Int): Result<Unit> = write { clockPreviews++ }

    override suspend fun syncTime(): Result<Unit> = Result.success(Unit)

    override fun close() {
        closed = true
    }

    private fun write(record: () -> Unit): Result<Unit> = writeError?.let { Result.failure(it) } ?: Result.success(record())
}

class FakeRingtoneRepository(override val builtIns: List<BuiltInRingtone>) : RingtoneRepository {
    var converted: Result<CustomRingtone> = Result.failure(RingtoneError.Empty())
    var originals: Result<List<OriginalRingtone>> = Result.success(emptyList())

    override suspend fun originals(): Result<List<OriginalRingtone>> = originals

    // Un secondo di audio: l'anteprima deve durare abbastanza da poterla osservare in corso.
    override suspend fun loadPcm(ringtone: RingtoneOption): Result<ByteArray> =
        Result.success(ByteArray(AudioUpload.SAMPLE_RATE) { ringtone.id.length.toByte() })

    override suspend fun convert(uri: String): Result<CustomRingtone> = converted
}

/** Sempre le 10:00:10 dell'1 luglio 2026 a Roma (UTC+2). */
class FakeTimeSource : TimeSource {
    override fun now(): Instant = Instant.parse("2026-07-01T08:00:10Z")
    override fun zone(): ZoneId = ZoneId.of("Europe/Rome")
}

class FakeRingtonePlayer : RingtonePlayer {
    var playing: ByteArray? = null

    override fun play(pcm: ByteArray): Boolean {
        playing = pcm
        return true
    }

    override fun stop() {
        playing = null
    }
}
