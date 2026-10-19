/*
 * Porting in Kotlin di qingping-cgd1 (https://github.com/rjocoleman/qingping-cgd1)
 * Copyright (c) 2026 Robert Coleman — licenza MIT.
 * Il protocollo deriva dalle note di reverse engineering di clOwOck.
 */
package it.cgd1.sveglia.protocol

import java.util.UUID

object Cgd1 {
    const val ALARM_SLOT_COUNT = 16

    /**
     * Token di pairing. Una sveglia già associata (Mi Home / Qingping+) rifiuta
     * un token sconosciuto; una sveglia resettata si lega al primo token che riceve.
     */
    val DEFAULT_AUTH_TOKEN: ByteArray = hex("6e021111c28d192cfedbe04038a7f238")

    private fun uuid16(value: Int): UUID =
        UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", value))

    val AUTH_WRITE_UUID: UUID = uuid16(0x0001)
    val AUTH_NOTIFY_UUID: UUID = uuid16(0x0002)
    val DATA_WRITE_UUID: UUID = uuid16(0x000B)
    val DATA_NOTIFY_UUID: UUID = uuid16(0x000C)
    val SENSOR_NOTIFY_UUID: UUID = uuid16(0x0100)
    val BATTERY_UUID: UUID = uuid16(0x2A19)
    val CCCD_UUID: UUID = uuid16(0x2902)

    val SERVICE_UUID: UUID = UUID.fromString("22210000-554a-4546-5542-46534450464d")
    val ADVERTISEMENT_SERVICE_UUID: UUID = uuid16(0xFDCD)

    val AUTH_STEP1 = hex("1101")
    val AUTH_STEP2 = hex("1102")

    val CMD_READ_SETTINGS = hex("0102")
    val CMD_READ_ALARMS = hex("0106")
    val CMD_READ_FIRMWARE = hex("010d")
    val CMD_SET_ALARM = hex("0705")
    val CMD_SET_SETTINGS = hex("1301")
    val CMD_BRIGHTNESS = hex("0203")
    val CMD_TIME_SYNC = hex("0509")
}

fun hex(s: String): ByteArray {
    val clean = s.replace(" ", "")
    require(clean.length % 2 == 0)
    return ByteArray(clean.length / 2) { i -> clean.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

/** Byte senza segno all'indice [i]. */
internal fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF
