package it.cgd1.sveglia.data.ringtone

import it.cgd1.sveglia.R

private const val CANONICAL = "Canonical Ltd."
private const val CC_BY_SA_3 = "CC BY-SA 3.0"

// Suoni da lomiri-sounds (UBports): https://gitlab.com/ubports/development/core/lomiri-sounds
// Le firme "4f43xxxx" ("OC" + indice) sono di OpenCGD1 e non vanno mai riassegnate a un altro suono.
val BUILT_IN_RINGTONES: List<BuiltInRingtone> = listOf(
    BuiltInRingtone("alarm_clock", "Alarm Clock", CANONICAL, CC_BY_SA_3, R.raw.ringtone_alarm_clock, "4f430001"),
    BuiltInRingtone("alarm_synth", "Alarm Synth", CANONICAL, CC_BY_SA_3, R.raw.ringtone_alarm_synth, "4f430002"),
    BuiltInRingtone("bliss", "Bliss", CANONICAL, CC_BY_SA_3, R.raw.ringtone_bliss, "4f430003"),
    BuiltInRingtone("call_me", "Call Me", "Anonymous", "CC0 1.0", R.raw.ringtone_call_me, "4f430004"),
    BuiltInRingtone("celestial", "Celestial", CANONICAL, CC_BY_SA_3, R.raw.ringtone_celestial, "4f430005"),
    BuiltInRingtone("counterpoint", "Counterpoint", CANONICAL, CC_BY_SA_3, R.raw.ringtone_counterpoint, "4f430006"),
    BuiltInRingtone("glass_marimba", "Glass Marimba", CANONICAL, CC_BY_SA_3, R.raw.ringtone_glass_marimba, "4f430007"),
    BuiltInRingtone("harp_arp", "Harp Arp", CANONICAL, CC_BY_SA_3, R.raw.ringtone_harp_arp, "4f430008"),
    BuiltInRingtone("koto_chords", "Koto Chords", CANONICAL, CC_BY_SA_3, R.raw.ringtone_koto_chords, "4f430009"),
    BuiltInRingtone("melody_piano", "Melody Piano", CANONICAL, CC_BY_SA_3, R.raw.ringtone_melody_piano, "4f43000a"),
    BuiltInRingtone("sparkle", "Sparkle", CANONICAL, CC_BY_SA_3, R.raw.ringtone_sparkle, "4f43000b"),
    BuiltInRingtone("time_not_lost", "Time Not Lost", "Mauricio Duarte", "CC BY 4.0", R.raw.ringtone_time_not_lost, "4f43000c"),
    BuiltInRingtone("ubports", "UBports", "Mauricio Duarte", "CC BY 4.0", R.raw.ringtone_ubports, "4f43000d"),
    BuiltInRingtone("wooden_drive", "Wooden Drive", "Amber Forest", "CC BY 3.0", R.raw.ringtone_wooden_drive, "4f43000e"),
)
