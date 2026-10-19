# Suonerie incluse (AOSP)

Le suonerie in `app/src/main/res/raw/ringtone_*.pcm` sono suoni di allarme dell'Android Open Source Project,
da [`frameworks/base/data/sounds`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds),
Copyright © The Android Open Source Project, con licenza Apache 2.0 (vedi [`LICENSE`](LICENSE)).

Modifiche: convertite nel formato della CGD1 (PCM 8 bit senza segno, 8 kHz, mono, massimo 12 s) con
`tools/convert_ringtone.py`: passa-alto 300 Hz, passa-basso 3,5 kHz, compressione, normalizzazione.

| File | Originale AOSP |
|---|---|
| `ringtone_beep_1.pcm` | `Alarm_Beep_01.ogg` |
| `ringtone_beep_2.pcm` | `Alarm_Beep_03.ogg` |
| `ringtone_buzzer.pcm` | `Alarm_Buzzer.ogg` |
| `ringtone_classic.pcm` | `Alarm_Classic.ogg` |
| `ringtone_rooster.pcm` | `Alarm_Rooster_02.ogg` |
| `ringtone_argon.pcm` | `alarms/ogg/Argon.ogg` |
| `ringtone_barium.pcm` | `alarms/ogg/Barium.ogg` |
| `ringtone_carbon.pcm` | `alarms/ogg/Carbon.ogg` |
| `ringtone_helium.pcm` | `alarms/ogg/Helium.ogg` |
| `ringtone_krypton.pcm` | `alarms/ogg/Krypton.ogg` |
| `ringtone_osmium.pcm` | `alarms/ogg/Osmium.ogg` |
| `ringtone_platinum.pcm` | `alarms/ogg/Platinum.ogg` |
| `ringtone_scandium.pcm` | `alarms/ogg/Scandium.ogg` |
| `ringtone_awaken.pcm` | `alarms/material/ogg/Awaken_OG7_1ch_48k.ogg` |
| `ringtone_drip.pcm` | `alarms/material/ogg/Drip_OG7_1ch_48k.ogg` |
| `ringtone_nudge.pcm` | `alarms/material/ogg/Nudge_OG7_1ch_48k.ogg` |
| `ringtone_orbit.pcm` | `alarms/material/ogg/Orbit_OG7_1ch_48k.ogg` |
| `ringtone_rise.pcm` | `alarms/material/ogg/Rise_OG7_1ch_48k.ogg` |

Le suonerie originali Qingping **non** sono nel repository né nell'APK: l'app le scarica a richiesta dal server
Qingping (`qingplus.cleargrass.com/raw/rings`), lo stesso che usa l'app ufficiale.
