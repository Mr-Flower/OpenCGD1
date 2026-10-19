package it.cgd1.sveglia.data.ringtone

import it.cgd1.sveglia.R

private const val AOSP = "The Android Open Source Project"
private const val APACHE_2 = "Apache 2.0"

private fun aosp(id: String, name: String, rawRes: Int, signatureHex: String) =
    BuiltInRingtone(id, name, AOSP, APACHE_2, rawRes, signatureHex)

// Suoni di allarme di AOSP (frameworks/base/data/sounds), pensati per svegliare e ricchi di acuti come le
// suonerie Qingping. Le firme "4f4301xx" sono di OpenCGD1: non vanno mai riassegnate a un altro suono.
val BUILT_IN_RINGTONES: List<BuiltInRingtone> = listOf(
    aosp("beep_1", "Beep 1", R.raw.ringtone_beep_1, "4f430101"),
    aosp("beep_2", "Beep 2", R.raw.ringtone_beep_2, "4f430102"),
    aosp("buzzer", "Buzzer", R.raw.ringtone_buzzer, "4f430103"),
    aosp("classic", "Classic", R.raw.ringtone_classic, "4f430104"),
    aosp("rooster", "Rooster", R.raw.ringtone_rooster, "4f430105"),
    aosp("argon", "Argon", R.raw.ringtone_argon, "4f430106"),
    aosp("barium", "Barium", R.raw.ringtone_barium, "4f430107"),
    aosp("carbon", "Carbon", R.raw.ringtone_carbon, "4f430108"),
    aosp("helium", "Helium", R.raw.ringtone_helium, "4f430109"),
    aosp("krypton", "Krypton", R.raw.ringtone_krypton, "4f43010a"),
    aosp("osmium", "Osmium", R.raw.ringtone_osmium, "4f43010b"),
    aosp("platinum", "Platinum", R.raw.ringtone_platinum, "4f43010c"),
    aosp("scandium", "Scandium", R.raw.ringtone_scandium, "4f43010d"),
    aosp("awaken", "Awaken", R.raw.ringtone_awaken, "4f43010e"),
    aosp("drip", "Drip", R.raw.ringtone_drip, "4f43010f"),
    aosp("nudge", "Nudge", R.raw.ringtone_nudge, "4f430110"),
    aosp("orbit", "Orbit", R.raw.ringtone_orbit, "4f430111"),
    aosp("rise", "Rise", R.raw.ringtone_rise, "4f430112"),
)
