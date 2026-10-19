package it.cgd1.sveglia.protocol

import java.time.DayOfWeek
import java.time.LocalTime

/** Giorno della settimana; [bit] è il bit usato nella maschera dei giorni. */
enum class Weekday(val bit: Int, val dayOfWeek: DayOfWeek) {
    MONDAY(0x01, DayOfWeek.MONDAY),
    TUESDAY(0x02, DayOfWeek.TUESDAY),
    WEDNESDAY(0x04, DayOfWeek.WEDNESDAY),
    THURSDAY(0x08, DayOfWeek.THURSDAY),
    FRIDAY(0x10, DayOfWeek.FRIDAY),
    SATURDAY(0x20, DayOfWeek.SATURDAY),
    SUNDAY(0x40, DayOfWeek.SUNDAY);

    /** Indice con lunedì = 0, come datetime.weekday() in Python. */
    val index: Int get() = Integer.numberOfTrailingZeros(bit)
}

enum class Language { CHINESE, ENGLISH }

private const val EMPTY_MARKER = 0xFF

/** Uno slot sveglia. [days] vuoto = sveglia singola (one-shot). */
data class Alarm(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val days: Set<Weekday>,
    val snooze: Boolean,
) {
    val isEmpty: Boolean get() = hour == EMPTY_MARKER && minute == EMPTY_MARKER

    companion object {
        val EMPTY = Alarm(enabled = false, hour = EMPTY_MARKER, minute = EMPTY_MARKER, days = emptySet(), snooze = false)
    }
}

/** Il blob di impostazioni da 20 byte, decodificato. */
data class DeviceSettings(
    val volume: Int,
    val language: Language,
    val timeFormat24h: Boolean,
    val unitCelsius: Boolean,
    val alarmsEnabled: Boolean,
    val tzOffsetMinutes: Int,
    val screenLightSeconds: Int,
    val dayBrightness: Int,
    val nightBrightness: Int,
    val nightStart: LocalTime,
    val nightEnd: LocalTime,
    val nightMode: Boolean,
    val ringtoneSignature: ByteArray,
    val rawReserved: ByteArray,
) {
    // equals/hashCode espliciti: i ByteArray vanno confrontati per contenuto.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceSettings) return false
        return volume == other.volume && language == other.language &&
            timeFormat24h == other.timeFormat24h && unitCelsius == other.unitCelsius &&
            alarmsEnabled == other.alarmsEnabled && tzOffsetMinutes == other.tzOffsetMinutes &&
            screenLightSeconds == other.screenLightSeconds && dayBrightness == other.dayBrightness &&
            nightBrightness == other.nightBrightness && nightStart == other.nightStart &&
            nightEnd == other.nightEnd && nightMode == other.nightMode &&
            ringtoneSignature.contentEquals(other.ringtoneSignature) &&
            rawReserved.contentEquals(other.rawReserved)
    }

    override fun hashCode(): Int {
        var h = volume
        h = 31 * h + language.hashCode()
        h = 31 * h + tzOffsetMinutes
        h = 31 * h + nightStart.hashCode()
        h = 31 * h + nightEnd.hashCode()
        h = 31 * h + ringtoneSignature.contentHashCode()
        h = 31 * h + rawReserved.contentHashCode()
        return h
    }
}

/** Lettura di temperatura/umidità/batteria; ogni campo può mancare. */
data class SensorData(
    val temperature: Double?,
    val humidity: Double?,
    val battery: Int?,
)
