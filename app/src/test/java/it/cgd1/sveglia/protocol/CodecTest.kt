package it.cgd1.sveglia.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Test portati 1:1 dalla suite Python di qingping-cgd1, con gli stessi vettori
 * catturati da una CGD1 reale: garantiscono che il porting parli lo stesso protocollo.
 */
class CodecTest {

    private inline fun <reified E : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return
            fail("expected ${E::class.simpleName}, got $e")
        }
        fail("expected ${E::class.simpleName}, nothing thrown")
    }


    private val weekdayAlarm = Alarm(
        enabled = true, hour = 7, minute = 30,
        days = setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY), snooze = false,
    )
    private val weekdayRecord = hex("01071e1500")

    @Test fun encodeAlarmPrefixesOpcodeAndSlot() {
        val out = Codec.encodeAlarm(3, weekdayAlarm)
        assertArrayEquals(hex("070503"), out.copyOfRange(0, 3))
        assertArrayEquals(weekdayRecord, out.copyOfRange(3, out.size))
    }

    @Test fun decodeWeekdayRecord() = assertEquals(weekdayAlarm, Codec.decodeAlarm(weekdayRecord))

    @Test fun emptyRecordDecodesToEmptyAlarm() = assertTrue(Codec.decodeAlarm(hex("ffffffffff")).isEmpty)

    @Test fun encodeEmptyAlarmIsAllFf() {
        val out = Codec.encodeAlarm(9, Alarm.EMPTY)
        assertArrayEquals(hex("ffffffffff"), out.copyOfRange(3, out.size))
    }

    @Test fun alarmRoundTrip() {
        listOf(
            weekdayAlarm,
            Alarm(false, 0, 0, emptySet(), true),
            Alarm(true, 23, 59, Weekday.entries.toSet(), true),
            Alarm.EMPTY,
        ).forEach { a ->
            val enc = Codec.encodeAlarm(0, a)
            assertEquals(a, Codec.decodeAlarm(enc.copyOfRange(3, enc.size)))
        }
    }

    @Test fun decodeAlarmRejectsWrongLength() = assertThrows<Cgd1CommandException> { Codec.decodeAlarm(hex("010203")) }


    private val blob = hex("130103580201780fa51600070001010001020304")
    private val settings = DeviceSettings(
        volume = 3, language = Language.ENGLISH, timeFormat24h = true, unitCelsius = true,
        alarmsEnabled = true, tzOffsetMinutes = 720, screenLightSeconds = 15,
        dayBrightness = 100, nightBrightness = 50,
        nightStart = LocalTime.of(22, 0), nightEnd = LocalTime.of(7, 0), nightMode = true,
        ringtoneSignature = hex("01020304"), rawReserved = hex("580200"),
    )

    @Test fun decodeReferenceBlob() = assertEquals(settings, Codec.decodeSettings(blob))

    @Test fun encodeReferenceSettings() = assertArrayEquals(blob, Codec.encodeSettings(settings))

    @Test fun settingsRoundTrip() = assertEquals(settings, Codec.decodeSettings(Codec.encodeSettings(settings)))

    @Test fun decodeAcceptsReadReplyHeader1302() {
        val reply = hex("1302") + blob.copyOfRange(2, blob.size)
        assertEquals(settings, Codec.decodeSettings(reply))
    }

    @Test fun flagBitsChinese12hFahrenheitAlarmsOff() {
        val b = blob.copyOf()
        b[5] = 0x16
        val d = Codec.decodeSettings(b)
        assertEquals(Language.CHINESE, d.language)
        assertFalse(d.timeFormat24h)
        assertFalse(d.unitCelsius)
        assertFalse(d.alarmsEnabled)
        assertEquals(0x16, Codec.encodeSettings(d)[5].toInt() and 0xFF)
    }

    @Test fun timezoneSignAndMagnitude() {
        listOf(Triple(720, 0x78, 0x01), Triple(0, 0x00, 0x01), Triple(-210, 0x23, 0x00)).forEach { (off, b6, b13) ->
            val enc = Codec.encodeSettings(settings.copy(tzOffsetMinutes = off))
            assertEquals(b6, enc[6].toInt() and 0xFF)
            assertEquals(b13, enc[13].toInt() and 0xFF)
            assertEquals(off, Codec.decodeSettings(enc).tzOffsetMinutes)
        }
    }

    @Test fun brightnessNibbles() {
        val enc = Codec.encodeSettings(settings.copy(dayBrightness = 0, nightBrightness = 100))
        assertEquals(0x0A, enc[8].toInt() and 0xFF)
        assertEquals(0, Codec.decodeSettings(enc).dayBrightness)
        assertEquals(100, Codec.decodeSettings(enc).nightBrightness)
    }

    @Test fun decodeSettingsRejectsBadLength() = assertThrows<Cgd1CommandException> { Codec.decodeSettings(hex("130100")) }

    @Test fun decodeSettingsRejectsBadHeader() =
        assertThrows<Cgd1CommandException> { Codec.decodeSettings(hex("9901") + blob.copyOfRange(2, blob.size)) }


    @Test fun encodeTimeLayout() = assertArrayEquals(hex("05098058446a"), Codec.encodeTime(1782864000L))

    @Test fun encodeTimeEpochZero() = assertArrayEquals(hex("050900000000"), Codec.encodeTime(0L))


    private val frame = hex("880c523886342d580104c8000502020150")

    @Test fun parseAdvertisementValues() {
        val r = Codec.parseAdvertisement(frame)
        assertNotNull(r)
        assertEquals(20.0, r!!.temperature!!, 0.0)
        assertEquals(51.7, r.humidity!!, 0.0)
        assertEquals(80, r.battery)
    }

    @Test fun advertisementNegativeTemperatureIsSigned() {
        val r = Codec.parseAdvertisement(hex("880c523886342d58010488ff0502020150"))
        assertEquals(-12.0, r!!.temperature!!, 0.0)
    }

    @Test fun wrongModelByteReturnsNull() {
        val f = frame.copyOf()
        f[1] = 0x0D
        assertNull(Codec.parseAdvertisement(f))
    }

    @Test fun shortFrameReturnsNull() = assertNull(Codec.parseAdvertisement(hex("880c5238")))


    @Test fun parseConnectedValues() {
        val r = Codec.parseConnectedSensor(hex("002e09c611"))
        assertEquals(23.5, r.temperature!!, 0.0)
        assertEquals(45.5, r.humidity!!, 0.0)
        assertNull(r.battery)
    }

    @Test fun connectedNegativeTemperature() =
        assertEquals(-12.5, Codec.parseConnectedSensor(hex("001efbc611")).temperature!!, 0.0)

    @Test fun scaleTrap() {
        assertEquals(23.5, Codec.parseConnectedSensor(hex("002e09c611")).temperature!!, 0.0)
        assertEquals(23.5, Codec.parseAdvertisement(hex("880c523886342d580104eb000502020150"))!!.temperature!!, 0.0)
    }

    @Test fun connectedRejectsBadPrefix() = assertThrows<Cgd1CommandException> { Codec.parseConnectedSensor(hex("992e09c611")) }

    @Test fun connectedRejectsShortFrame() = assertThrows<Cgd1CommandException> { Codec.parseConnectedSensor(hex("002e")) }


    private val now = LocalDateTime.of(2026, 7, 1, 8, 0)
    private fun oneShot(h: Int, m: Int, enabled: Boolean = true) = Alarm(enabled, h, m, emptySet(), false)
    private fun weekly(h: Int, m: Int, vararg d: Weekday) = Alarm(true, h, m, d.toSet(), false)

    @Test fun noEnabledAlarmsReturnsNull() = assertNull(Codec.nextAlarm(listOf(Alarm.EMPTY, oneShot(7, 0, false)), now))

    @Test fun oneShotLaterToday() = assertEquals(LocalDateTime.of(2026, 7, 1, 9, 30), Codec.nextAlarm(listOf(oneShot(9, 30)), now))

    @Test fun oneShotPassedRollsToTomorrow() = assertEquals(LocalDateTime.of(2026, 7, 2, 7, 0), Codec.nextAlarm(listOf(oneShot(7, 0)), now))

    @Test fun weekdayCrossesWeekBoundary() =
        assertEquals(LocalDateTime.of(2026, 7, 6, 6, 0), Codec.nextAlarm(listOf(weekly(6, 0, Weekday.MONDAY)), now))

    @Test fun weekdayTodayPassedWaitsAWeek() =
        assertEquals(LocalDateTime.of(2026, 7, 8, 6, 0), Codec.nextAlarm(listOf(weekly(6, 0, Weekday.WEDNESDAY)), now))

    @Test fun weekdayTodayNotPassedFiresToday() =
        assertEquals(LocalDateTime.of(2026, 7, 1, 9, 30), Codec.nextAlarm(listOf(weekly(9, 30, Weekday.WEDNESDAY)), now))

    @Test fun exactEqualityCountsAsFired() = assertEquals(LocalDateTime.of(2026, 7, 2, 8, 0), Codec.nextAlarm(listOf(oneShot(8, 0)), now))

    @Test fun returnsTheSoonerOfTwo() =
        assertEquals(LocalDateTime.of(2026, 7, 1, 9, 30), Codec.nextAlarm(listOf(weekly(6, 0, Weekday.MONDAY), oneShot(9, 30)), now))
}
