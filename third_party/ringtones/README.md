# Suonerie incluse

Le suonerie in `app/src/main/res/raw/ringtone_*.pcm` vengono da
[lomiri-sounds](https://gitlab.com/ubports/development/core/lomiri-sounds) (UBports). Sono state convertite
nel formato della CGD1 (PCM 8 bit senza segno, 8 kHz, mono, massimo 12 s) con `tools/convert_ringtone.py`:
filtro passa-basso, normalizzazione del picco e dissolvenza finale quando il suono viene accorciato.

Le versioni convertite sono distribuite con la stessa licenza dell'originale.

| File | Originale | Autore | Licenza |
|---|---|---|---|
| `ringtone_alarm_clock.pcm` | Alarm clock.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_alarm_synth.pcm` | Alarm synth.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_bliss.pcm` | Bliss.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_call_me.pcm` | Call me.ogg | 2018, Anonymous | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `ringtone_celestial.pcm` | Celestial.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_counterpoint.pcm` | Counterpoint.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_glass_marimba.pcm` | Glass marimba.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_harp_arp.pcm` | Harp arp.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_koto_chords.pcm` | Koto chords.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_melody_piano.pcm` | Melody piano.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_sparkle.pcm` | Sparkle.ogg | 2013, Canonical Ltd. | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| `ringtone_time_not_lost.pcm` | Time not Lost.ogg | 2018, Mauricio Duarte | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `ringtone_ubports.pcm` | UBports.ogg | 2018, Mauricio Duarte | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| `ringtone_wooden_drive.pcm` | Wooden Drive.ogg | 2018, Amber Forest | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) |

Autori e licenze sono indicati anche nell'app, sotto ogni suoneria.

Il protocollo di caricamento è documentato da [cgd1-rs](https://github.com/smearor/cgd1-rs) (MIT).
Le suonerie originali dell'app Qingping non sono incluse: OpenCGD1 ne riconosce solo il nome.
