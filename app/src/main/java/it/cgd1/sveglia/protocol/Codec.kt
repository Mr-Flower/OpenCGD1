package it.cgd1.sveglia.protocol

import java.time.LocalDateTime
import java.time.LocalTime

/** Codifica/decodifica pura del protocollo CGD1. Niente BLE, niente coroutine. */
object Codec {
    private const val RECORD_LENGTH = 5
    private val EMPTY_RECORD = hex("ffffffffff")
    private const val SETTINGS_LEN = 20

    // Bit del byte flag (indice 5). Diversi sono invertiti: bit a zero = stato "on".
    private const val FLAG_ENGLISH = 0x01
    private const val FLAG_12H = 0x02
    private const val FLAG_FAHRENHEIT = 0x04
    private const val FLAG_ALARMS_OFF = 0x10

    // Fuso: passi interi da 6 minuti più un flag di segno separato.
    private const val TZ_STEP_MINUTES = 6
    private const val TZ_SIGN_POSITIVE = 1
    private const val BRIGHTNESS_STEP = 10

    // Advertisement 0xfdcd: frame, modello (0x0c), MAC (6), 2 ignoti, temp int16 LE,
    // umidità uint16 LE, 2 ignoti, batteria. Scala /10.
    private const val ADV_MODEL = 0x0C
    private const val ADV_MIN_LEN = 17
    private const val ADV_SCALE = 10.0

    // Notifica 0x0100 da connessi: scala /100 (diversa dall'advertisement!).
    private const val CONNECTED_MIN_LEN = 5
    private const val CONNECTED_SCALE = 100.0

    private fun int16le(b: ByteArray, i: Int): Int = ((b.u(i + 1) shl 8) or b.u(i)).toShort().toInt()
    private fun uint16le(b: ByteArray, i: Int): Int = (b.u(i + 1) shl 8) or b.u(i)


    private fun encodeAlarmRecord(alarm: Alarm): ByteArray {
        if (alarm.isEmpty) return EMPTY_RECORD.copyOf()
        val mask = alarm.days.sumOf { it.bit }
        return byteArrayOf(
            (if (alarm.enabled) 1 else 0).toByte(),
            alarm.hour.toByte(),
            alarm.minute.toByte(),
            mask.toByte(),
            (if (alarm.snooze) 1 else 0).toByte(),
        )
    }

    /** Comando set-alarm per uno slot. */
    fun encodeAlarm(slot: Int, alarm: Alarm): ByteArray =
        Cgd1.CMD_SET_ALARM + byteArrayOf(slot.toByte()) + encodeAlarmRecord(alarm)

    /** Decodifica un record sveglia da 5 byte; tutto 0xff = slot vuoto. */
    fun decodeAlarm(record: ByteArray): Alarm {
        if (record.size != RECORD_LENGTH) {
            throw Cgd1CommandException("alarm record must be $RECORD_LENGTH bytes, got ${record.size}")
        }
        if (record.contentEquals(EMPTY_RECORD)) return Alarm.EMPTY
        val mask = record.u(3)
        return Alarm(
            enabled = record.u(0) != 0,
            hour = record.u(1),
            minute = record.u(2),
            days = Weekday.entries.filter { mask and it.bit != 0 }.toSet(),
            snooze = record.u(4) != 0,
        )
    }


    private fun encodeFlags(s: DeviceSettings): Int {
        var flags = 0
        if (s.language == Language.ENGLISH) flags = flags or FLAG_ENGLISH
        if (!s.timeFormat24h) flags = flags or FLAG_12H
        if (!s.unitCelsius) flags = flags or FLAG_FAHRENHEIT
        if (!s.alarmsEnabled) flags = flags or FLAG_ALARMS_OFF
        return flags
    }

    /** Serializza le impostazioni nel blob da 20 byte (header 13 01). */
    fun encodeSettings(s: DeviceSettings): ByteArray {
        val b = ByteArray(SETTINGS_LEN)
        b[0] = Cgd1.CMD_SET_SETTINGS[0]
        b[1] = Cgd1.CMD_SET_SETTINGS[1]
        b[2] = s.volume.toByte()
        b[3] = s.rawReserved[0]
        b[4] = s.rawReserved[1]
        b[5] = encodeFlags(s).toByte()
        b[6] = (Math.abs(s.tzOffsetMinutes) / TZ_STEP_MINUTES).toByte()
        b[7] = s.screenLightSeconds.toByte()
        val day = s.dayBrightness / BRIGHTNESS_STEP
        val night = s.nightBrightness / BRIGHTNESS_STEP
        b[8] = ((day shl 4) or night).toByte()
        b[9] = s.nightStart.hour.toByte()
        b[10] = s.nightStart.minute.toByte()
        b[11] = s.nightEnd.hour.toByte()
        b[12] = s.nightEnd.minute.toByte()
        b[13] = (if (s.tzOffsetMinutes >= 0) TZ_SIGN_POSITIVE else 0).toByte()
        b[14] = (if (s.nightMode) 1 else 0).toByte()
        b[15] = s.rawReserved[2]
        s.ringtoneSignature.copyInto(b, destinationOffset = 16, startIndex = 0, endIndex = 4)
        return b
    }

    /** Decodifica il blob da 20 byte (risposta 13 01 o 13 02). */
    fun decodeSettings(d: ByteArray): DeviceSettings {
        if (d.size != SETTINGS_LEN) {
            throw Cgd1CommandException("settings blob must be $SETTINGS_LEN bytes, got ${d.size}")
        }
        if (d.u(0) != 0x13 || d.u(1) !in setOf(0x01, 0x02)) {
            throw Cgd1CommandException("unexpected settings header %02x %02x".format(d.u(0), d.u(1)))
        }
        val flags = d.u(5)
        val magnitude = d.u(6) * TZ_STEP_MINUTES
        val sign = if (d.u(13) == TZ_SIGN_POSITIVE) 1 else -1
        return DeviceSettings(
            volume = d.u(2),
            language = if (flags and FLAG_ENGLISH != 0) Language.ENGLISH else Language.CHINESE,
            timeFormat24h = flags and FLAG_12H == 0,
            unitCelsius = flags and FLAG_FAHRENHEIT == 0,
            alarmsEnabled = flags and FLAG_ALARMS_OFF == 0,
            tzOffsetMinutes = sign * magnitude,
            screenLightSeconds = d.u(7),
            dayBrightness = (d.u(8) shr 4) * BRIGHTNESS_STEP,
            nightBrightness = (d.u(8) and 0x0F) * BRIGHTNESS_STEP,
            nightStart = safeTime(d.u(9), d.u(10)),
            nightEnd = safeTime(d.u(11), d.u(12)),
            nightMode = d.u(14) != 0,
            ringtoneSignature = d.copyOfRange(16, 20),
            rawReserved = byteArrayOf(d[3], d[4], d[15]),
        )
    }

    private fun safeTime(h: Int, m: Int): LocalTime = LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59))


    /** Pacchetto di sincronizzazione: opcode + secondi epoch, little-endian. */
    fun encodeTime(epochSeconds: Long): ByteArray {
        val t = epochSeconds and 0xFFFFFFFFL
        return Cgd1.CMD_TIME_SYNC + byteArrayOf(
            (t and 0xFF).toByte(),
            ((t shr 8) and 0xFF).toByte(),
            ((t shr 16) and 0xFF).toByte(),
            ((t shr 24) and 0xFF).toByte(),
        )
    }


    /** Advertisement passivo fdcd; null se troppo corto o di un altro modello. */
    fun parseAdvertisement(serviceData: ByteArray): SensorData? {
        if (serviceData.size < ADV_MIN_LEN) return null
        if (serviceData.u(1) != ADV_MODEL) return null
        return SensorData(
            temperature = int16le(serviceData, 10) / ADV_SCALE,
            humidity = uint16le(serviceData, 12) / ADV_SCALE,
            battery = serviceData.u(16),
        )
    }

    /** Notifica sensore 0x0100 (scala /100). La batteria non c'è: si legge da 0x2A19. */
    fun parseConnectedSensor(d: ByteArray): SensorData {
        if (d.size < CONNECTED_MIN_LEN || d.u(0) != 0x00) {
            throw Cgd1CommandException("connected sensor frame must start 0x00 and be at least 5 bytes")
        }
        return SensorData(
            temperature = int16le(d, 1) / CONNECTED_SCALE,
            humidity = int16le(d, 3) / CONNECTED_SCALE,
            battery = null,
        )
    }


    private fun nextFire(alarm: Alarm, now: LocalDateTime): LocalDateTime {
        val fireTime = LocalTime.of(alarm.hour, alarm.minute)
        if (alarm.days.isEmpty()) {
            var c = now.toLocalDate().atTime(fireTime)
            if (!c.isAfter(now)) c = c.plusDays(1)
            return c
        }
        val nowIdx = now.dayOfWeek.value - 1 // lunedì = 0
        return alarm.days.map { day ->
            val ahead = Math.floorMod(day.index - nowIdx, 7)
            var c = now.toLocalDate().plusDays(ahead.toLong()).atTime(fireTime)
            if (!c.isAfter(now)) c = c.plusDays(7)
            c
        }.min()
    }

    /** Prossimo orario di una sveglia attiva, o null se nessuna è attiva. */
    fun nextAlarm(alarms: List<Alarm>, now: LocalDateTime): LocalDateTime? =
        alarms.filter { it.enabled && !it.isEmpty }.map { nextFire(it, now) }.minOrNull()
}
