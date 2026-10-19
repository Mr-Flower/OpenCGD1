package it.cgd1.sveglia.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.AudioUpload
import it.cgd1.sveglia.protocol.Cgd1
import it.cgd1.sveglia.protocol.Cgd1AuthException
import it.cgd1.sveglia.protocol.Cgd1CommandException
import it.cgd1.sveglia.protocol.Cgd1ConnectionException
import it.cgd1.sveglia.protocol.Codec
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Cgd1Client"
private const val DEFAULT_MTU = 23
private const val UPLOAD_MTU = 247
private const val ATT_HEADER_SIZE = 3

/**
 * Client GATT per la Qingping CGD1: connessione, autenticazione con token, comandi.
 * Porting di QingpingCGD1Client (Python/bleak) sulle API Bluetooth di Android.
 *
 * Android ammette una sola operazione GATT in volo per volta, quindi ogni
 * operazione passa da [opMutex]; i comandi di alto livello passano da [cmdMutex].
 */
@SuppressLint("MissingPermission") // i permessi sono richiesti dall'Activity prima di usare il client
class Cgd1Client(
    context: Context,
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
    private val token: ByteArray = Cgd1.DEFAULT_AUTH_TOKEN,
    private val commandTimeoutMs: Long = 10_000,
    private val idleDisconnectMs: Long = 60_000,
) {
    private val appContext = context.applicationContext

    private val _connected = MutableStateFlow(false)

    /** Diventa false anche quando il link cade per inattività: il client si ricollega da solo al comando dopo. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val cmdMutex = Mutex()
    private val opMutex = Mutex()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var ready = false
    @Volatile private var connectWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var opWaiter: CompletableDeferred<ByteArray>? = null
    @Volatile private var settingsWaiter: CompletableDeferred<ByteArray>? = null
    @Volatile private var firmwareWaiter: CompletableDeferred<ByteArray>? = null
    @Volatile private var alarmWaiter: CompletableDeferred<List<Alarm>>? = null
    private val ackWaiters = ConcurrentHashMap<Int, CompletableDeferred<Int>>()
    private val alarmSlots = arrayOfNulls<Alarm>(Cgd1.ALARM_SLOT_COUNT)
    @Volatile private var mtu = DEFAULT_MTU

    // Da Android 14 il sistema negozia l'MTU da solo: quella callback non deve completare un'altra operazione.
    @Volatile private var mtuRequestPending = false
    private var idleJob: Job? = null

    /** Dopo [closeNow] il client non si ricollega più, nemmeno dai percorsi di retry. */
    @Volatile private var closed = false


    suspend fun connect() = cmdMutex.withLock {
        idleJob?.cancel()
        ensureConnected()
        scheduleIdleDisconnect()
    }

    suspend fun disconnect() {
        idleJob?.cancel()
        cmdMutex.withLock { closeLink() }
    }

    /** Chiusura immediata, non sospendente (per onCleared / cambio sveglia). */
    fun closeNow() {
        closed = true
        idleJob?.cancel()
        closeLink()
    }

    suspend fun readSettings(): DeviceSettings = runCommand { Codec.decodeSettings(requestSettings()) }

    suspend fun writeSettings(settings: DeviceSettings) {
        val payload = Codec.encodeSettings(settings)
        runCommand { writeWithAck(Cgd1.DATA_WRITE_UUID, payload) }
    }

    suspend fun readAlarms(): List<Alarm> = runCommand { requestAlarms() }

    suspend fun writeAlarm(slot: Int, alarm: Alarm) {
        require(slot in 0 until Cgd1.ALARM_SLOT_COUNT) { "slot fuori range: $slot" }
        val payload = Codec.encodeAlarm(slot, alarm)
        runCommand { writeWithAck(Cgd1.DATA_WRITE_UUID, payload) }
    }

    suspend fun deleteAlarm(slot: Int) = writeAlarm(slot, Alarm.EMPTY)

    /** Invia i secondi epoch (UTC); la sveglia mostra l'ora applicando il fuso delle impostazioni. */
    suspend fun syncTime(epochSeconds: Long = System.currentTimeMillis() / 1000) {
        val payload = Codec.encodeTime(epochSeconds)
        runCommand { writeWithAck(Cgd1.AUTH_WRITE_UUID, payload) }
    }

    /** Luminosità immediata 0–100 (passi da 10). */
    suspend fun setBrightness(level: Int) {
        require(level in 0..100) { "luminosità 0-100, ricevuto $level" }
        val payload = Cgd1.CMD_BRIGHTNESS + byteArrayOf((level / 10).toByte())
        runCommand { writeWithAck(Cgd1.DATA_WRITE_UUID, payload) }
    }

    suspend fun readFirmware(): String = runCommand {
        val raw = requestFirmware()
        val len = raw[1].toInt() and 0xFF
        String(raw, 2, minOf(len, raw.size - 2), Charsets.US_ASCII)
    }

    /**
     * Carica una suoneria già convertita e la conserva sotto [signature]. Il trasferimento tiene il
     * link per sé dall'inizio alla fine: un comando in parallelo lo farebbe abortire.
     */
    suspend fun uploadRingtone(pcm: ByteArray, signature: ByteArray, onProgress: (Float) -> Unit) {
        val padded = AudioUpload.pad(pcm)
        val packets = AudioUpload.packets(padded)
        runCommand {
            ensureMtu()
            onProgress(0f)
            writeWithAck(Cgd1.DATA_WRITE_UUID, AudioUpload.encodeInit(padded.size, signature))
            packets.chunked(AudioUpload.PACKETS_PER_BLOCK).forEachIndexed { block, blockPackets ->
                sendAudioBlock(blockPackets)
                onProgress((block + 1).toFloat() * AudioUpload.PACKETS_PER_BLOCK / packets.size)
            }
        }
    }

    /** Batteria dalla caratteristica GATT standard 0x2A19, se presente. */
    suspend fun readBattery(): Int? = runCommand {
        val ch = findChar(Cgd1.BATTERY_UUID) ?: return@runCommand null
        val v = gattOp("read battery") { g -> readChar(g, ch) }
        if (v.isNotEmpty()) v[0].toInt() and 0xFF else null
    }


    private suspend fun <T> runCommand(block: suspend () -> T): T = cmdMutex.withLock {
        idleJob?.cancel()
        try {
            ensureConnected()
            try {
                block()
            } catch (e: Cgd1ConnectionException) {
                // Il link cade sotto carico: una riconnessione pulita di solito basta, riprova una volta.
                Log.w(TAG, "command failed, reconnecting: ${e.message}")
                closeLink()
                ensureConnected()
                try {
                    block()
                } catch (e2: Cgd1ConnectionException) {
                    throw Cgd1CommandException("comando fallito dopo la riconnessione", e2)
                }
            }
        } finally {
            if (ready) scheduleIdleDisconnect()
        }
    }

    private fun scheduleIdleDisconnect() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleDisconnectMs)
            cmdMutex.withLock { closeLink() }
        }
    }

    private suspend fun requestSettings(): ByteArray {
        val w = CompletableDeferred<ByteArray>()
        settingsWaiter = w
        writeChar(Cgd1.DATA_WRITE_UUID, Cgd1.CMD_READ_SETTINGS)
        return awaitOrTimeout(w, "lettura impostazioni")
    }

    private suspend fun requestFirmware(): ByteArray {
        val w = CompletableDeferred<ByteArray>()
        firmwareWaiter = w
        writeChar(Cgd1.AUTH_WRITE_UUID, Cgd1.CMD_READ_FIRMWARE)
        return awaitOrTimeout(w, "lettura firmware")
    }

    private suspend fun requestAlarms(): List<Alarm> {
        // La sveglia invia i 16 slot su più notifiche: si azzera la tabella e il
        // waiter si completa quando ogni slot ha un valore.
        synchronized(alarmSlots) { alarmSlots.fill(null) }
        val w = CompletableDeferred<List<Alarm>>()
        alarmWaiter = w
        writeChar(Cgd1.DATA_WRITE_UUID, Cgd1.CMD_READ_ALARMS)
        return awaitOrTimeout(w, "lettura sveglie")
    }

    private suspend fun writeWithAck(charUuid: UUID, payload: ByteArray) {
        // L'ACK riporta il sotto-comando (secondo byte): 07 05 -> 04 ff 05, 05 09 -> 04 ff 09.
        val sub = payload[1].toInt() and 0xFF
        val w = CompletableDeferred<Int>()
        ackWaiters[sub] = w
        val status = try {
            writeChar(charUuid, payload)
            awaitOrTimeout(w, "ack ${payload.copyOfRange(0, 2).toHex()}")
        } finally {
            ackWaiters.remove(sub)
        }
        if (status != 0x00 && status != 0x09) {
            throw Cgd1CommandException("comando ${payload.copyOfRange(0, 2).toHex()} fallito, stato 0x%02x".format(status))
        }
    }

    private suspend fun ensureMtu() {
        if (mtu - ATT_HEADER_SIZE >= AudioUpload.PACKET_SIZE) return
        mtuRequestPending = true
        try {
            gattOp("request mtu") { g -> g.requestMtu(UPLOAD_MTU) }
        } finally {
            mtuRequestPending = false
        }
        if (mtu - ATT_HEADER_SIZE < AudioUpload.PACKET_SIZE) {
            throw Cgd1CommandException("MTU troppo piccolo per caricare la suoneria: $mtu")
        }
    }

    /** La sveglia conferma ogni blocco da 512 byte con `04 ff 08`, non ogni pacchetto. */
    private suspend fun sendAudioBlock(blockPackets: List<ByteArray>) {
        val sub = Cgd1.CMD_AUDIO_DATA[1].toInt() and 0xFF
        val w = CompletableDeferred<Int>()
        ackWaiters[sub] = w
        val status = try {
            blockPackets.forEach { writeChar(Cgd1.DATA_WRITE_UUID, it) }
            awaitOrTimeout(w, "ack blocco audio")
        } finally {
            ackWaiters.remove(sub)
        }
        if (status != 0x00) throw Cgd1CommandException("blocco audio rifiutato, stato 0x%02x".format(status))
    }

    private suspend fun <T : Any> awaitOrTimeout(w: CompletableDeferred<T>, what: String): T =
        withTimeoutOrNull(commandTimeoutMs) { w.await() }
            ?: throw Cgd1ConnectionException("timeout: $what")


    private suspend fun ensureConnected() {
        if (closed) throw CancellationException("client chiuso")
        if (ready && gatt != null) return
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                openLink()
                authenticate()
                ready = true
                _connected.value = true
                return
            } catch (e: Cgd1AuthException) {
                throw e
            } catch (e: CancellationException) {
                closeLink()
                throw e
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "connect attempt ${attempt + 1} failed: ${e.message}")
                closeLink()
                delay(600)
            }
        }
        throw Cgd1ConnectionException("impossibile collegarsi alla sveglia", last)
    }

    private suspend fun openLink() {
        if (closed) throw CancellationException("client chiuso")
        val w = CompletableDeferred<Unit>()
        connectWaiter = w
        // L'alternativa con BluetoothGattConnectionSettings esiste solo da API 37.
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw Cgd1ConnectionException("connectGatt ha restituito null")
        withTimeoutOrNull(15_000) { w.await() } ?: throw Cgd1ConnectionException("timeout di connessione")
        gattOp("discover services") { g -> g.discoverServices() }
        val authNotify = findChar(Cgd1.AUTH_NOTIFY_UUID) ?: throw Cgd1ConnectionException("caratteristica auth assente: è una CGD1?")
        val dataNotify = findChar(Cgd1.DATA_NOTIFY_UUID) ?: throw Cgd1ConnectionException("caratteristica dati assente")
        if (findChar(Cgd1.AUTH_WRITE_UUID) == null || findChar(Cgd1.DATA_WRITE_UUID) == null) {
            throw Cgd1ConnectionException("caratteristiche di scrittura assenti")
        }
        enableNotify(authNotify)
        enableNotify(dataNotify)
    }

    private suspend fun authenticate() {
        // La sveglia fa ACK anche a un token sbagliato: l'auth si prova con una lettura
        // privilegiata. Token errato (o sveglia non in pairing) = link che cade.
        try {
            writeChar(Cgd1.AUTH_WRITE_UUID, Cgd1.AUTH_STEP1 + token)
            writeChar(Cgd1.AUTH_WRITE_UUID, Cgd1.AUTH_STEP2 + token)
            requestSettings()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            closeLink()
            throw Cgd1AuthException("la sveglia ha rifiutato il token", e)
        }
    }

    private fun closeLink() {
        val g = gatt
        gatt = null
        ready = false
        mtu = DEFAULT_MTU
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        failPending(Cgd1ConnectionException("disconnesso"))
        _connected.value = false
    }

    private fun failPending(err: Exception) {
        connectWaiter?.completeExceptionally(err)
        opWaiter?.completeExceptionally(err)
        settingsWaiter?.completeExceptionally(err)
        firmwareWaiter?.completeExceptionally(err)
        alarmWaiter?.completeExceptionally(err)
        ackWaiters.values.forEach { it.completeExceptionally(err) }
        ackWaiters.clear()
    }


    private fun findChar(uuid: UUID): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        g.getService(Cgd1.SERVICE_UUID)?.getCharacteristic(uuid)?.let { return it }
        return g.services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }
    }

    /** Avvia un'operazione GATT e attende la callback corrispondente. */
    private suspend fun gattOp(what: String, start: (BluetoothGatt) -> Boolean): ByteArray = opMutex.withLock {
        val g = gatt ?: throw Cgd1ConnectionException("non connesso")
        val w = CompletableDeferred<ByteArray>()
        opWaiter = w
        try {
            if (!start(g)) throw Cgd1ConnectionException("operazione GATT rifiutata: $what")
            withTimeoutOrNull(commandTimeoutMs) { w.await() } ?: throw Cgd1ConnectionException("timeout GATT: $what")
        } finally {
            opWaiter = null
        }
    }

    private suspend fun writeChar(uuid: UUID, value: ByteArray) {
        val ch = findChar(uuid) ?: throw Cgd1ConnectionException("caratteristica $uuid assente")
        gattOp("write $uuid") { g ->
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = value
                    g.writeCharacteristic(ch)
                }
            }
        }
    }

    private fun readChar(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean = g.readCharacteristic(ch)

    private suspend fun enableNotify(ch: BluetoothGattCharacteristic) {
        val g = gatt ?: throw Cgd1ConnectionException("non connesso")
        if (!g.setCharacteristicNotification(ch, true)) throw Cgd1ConnectionException("notifiche non attivabili")
        val cccd = ch.getDescriptor(Cgd1.CCCD_UUID) ?: return
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        gattOp("enable notify ${ch.uuid}") { gg ->
            if (Build.VERSION.SDK_INT >= 33) {
                gg.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    cccd.value = value
                    gg.writeDescriptor(cccd)
                }
            }
        }
    }


    private fun onNotify(frame: ByteArray) {
        if (frame.isEmpty()) return
        val b0 = frame[0].toInt() and 0xFF
        val b1 = if (frame.size > 1) frame[1].toInt() and 0xFF else -1
        when {
            frame.size >= 5 && b0 == 0x04 && b1 == 0xFF ->
                ackWaiters.remove(frame[2].toInt() and 0xFF)?.complete(frame[4].toInt() and 0xFF)
            b0 == 0x13 -> settingsWaiter?.complete(frame)
            b0 == 0x11 && b1 == 0x06 -> handleAlarmFrame(frame)
            b0 == 0x0B -> firmwareWaiter?.complete(frame)
            else -> Log.d(TAG, "notify ignorata: ${frame.toHex()}")
        }
    }

    private fun handleAlarmFrame(frame: ByteArray) {
        if (frame.size < 3) return
        val base = frame[2].toInt() and 0xFF
        val body = frame.copyOfRange(3, frame.size)
        val complete: List<Alarm>? = synchronized(alarmSlots) {
            var offset = 0
            while (offset < body.size - 4) {
                val slot = base + offset / 5
                if (slot in 0 until Cgd1.ALARM_SLOT_COUNT) {
                    alarmSlots[slot] = Codec.decodeAlarm(body.copyOfRange(offset, offset + 5))
                }
                offset += 5
            }
            if (alarmSlots.all { it != null }) alarmSlots.map { it!! } else null
        }
        if (complete != null) alarmWaiter?.complete(complete)
    }


    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "state=$newState status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connectWaiter?.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                if (gatt === g) {
                    gatt = null
                    ready = false
                    mtu = DEFAULT_MTU
                    runCatching { g.close() }
                    failPending(Cgd1ConnectionException("collegamento perso (stato $status)"))
                    _connected.value = false
                } else {
                    runCatching { g.close() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = completeOp(status, ByteArray(0))

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@Cgd1Client.mtu = mtu
            if (mtuRequestPending) completeOp(status, ByteArray(0))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) =
            completeOp(status, ByteArray(0))

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            completeOp(status, ByteArray(0))

        // API 33+
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            completeOp(status, value)

        // API < 33
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            completeOp(status, ch.value ?: ByteArray(0))
        }

        // API 33+
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            onNotify(value)

        // API < 33
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onNotify(ch.value?.copyOf() ?: return)
        }

        private fun completeOp(status: Int, value: ByteArray) {
            val w = opWaiter ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) w.complete(value)
            else w.completeExceptionally(Cgd1ConnectionException("errore GATT $status"))
        }
    }
}
