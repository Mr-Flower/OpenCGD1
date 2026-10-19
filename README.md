# OpenCGD1

App Android open source per comandare la sveglia Bluetooth **Qingping CGD1** senza Mi Home né Qingping+. Funziona in locale via Bluetooth LE: niente account, niente cloud. Internet serve solo, a richiesta, per scaricare le suonerie originali dal server Qingping.

È un porting in Kotlin della libreria Python [qingping-cgd1](https://github.com/rjocoleman/qingping-cgd1) di Robert Coleman (MIT), a sua volta basata sul reverse engineering di clOwOck.

## Funzioni

**Sveglia**
- Trova la sveglia da sola e, una volta collegata, apre una **dashboard** con temperatura e umidità in grande (aggiornate in tempo reale), batteria e ora dell'ultima lettura
- Batteria letta dall'advertisement Qingping: la caratteristica GATT standard 0x2A19 della CGD1 risponde sempre 99% e non viene usata
- Legge e scrive i 16 slot sveglia: ora, giorni della settimana, snooze, attiva/disattiva
- Interruttore generale delle sveglie e "prossima sveglia"
- Sincronizza ora **e fuso** con il telefono, a ogni collegamento e con il pulsante dedicato
- Impostazioni del dispositivo: volume, 12/24 h, °C/°F, lingua del display, luminosità giorno/notte, durata della luce schermo, modalità notte con orari
- **Suoneria**: le 9 originali Qingping (scaricate dal loro server), 18 suoni di allarme AOSP inclusi o un file audio tuo. Anteprima sul telefono, "Fai suonare la sveglia" per sentirla dall'altoparlante vero, barra di avanzamento durante il caricamento

**App**
- Guida di benvenuto al primo avvio (come liberare la sveglia da Mi Home, permessi), rivedibile dalle Impostazioni
- Material 3, con i colori **Material You** presi dallo sfondo (Android 12+, disattivabili)
- Font **Inter**, simile a MiSans delle app Xiaomi; icona "clock" di Lucide
- Tema **Segui il sistema**, **Chiaro**, **Scuro** o **AMOLED** (nero puro)
- Lingua **English**, **Español**, **Français**, **Italiano**, oppure quella del dispositivo (predefinita). Su Android 13+ la lingua si può cambiare anche da *Impostazioni → App → OpenCGD1 → Lingua*

## Primo collegamento

La sveglia accetta un solo "proprietario". Se è associata a Mi Home o Qingping+ rifiuta l'app:

1. Rimuovila da Mi Home, **oppure** tieni premuto il pulsante finché l'icona Bluetooth lampeggia
2. Apri l'app e segui la guida di benvenuto: la ricerca parte da sola, tocca la sveglia
3. Al primo collegamento la sveglia si lega al token dell'app e da lì in poi funziona sempre

Dopo il reset Mi Home non la vedrà più (è proprio lo scopo).

## Installazione

Scarica l'APK dall'ultima build nella scheda **Actions** del repository (artifact `opencgd1-apk`) e installalo sul telefono. Requisiti: Android 8.0 (API 26) o successivo, Bluetooth LE.

L'APK "release" è firmato con la chiave di debug: va bene per uso personale, non per il Play Store.

## Suonerie

La sveglia conserva una sola suoneria: sceglierne una vuol dire caricarla via Bluetooth (fino a 96 KB, qualche decina di secondi), con il telefono vicino.

- **Originali Qingping**: le 9 suonerie dell'app ufficiale, scaricate a richiesta dallo stesso server che usa l'app Qingping (`qingplus.cleargrass.com/raw/rings`) e tenute in cache. Non sono nel repository né nell'APK
- **Incluse**: 18 suoni di allarme dell'Android Open Source Project (Apache 2.0), dettagli in [`third_party/aosp-sounds`](third_party/aosp-sounds/README.md)
- **Personalizzata**: qualsiasi file audio che Android sa leggere

Le incluse e i file personali passano dallo stesso trattamento, pensato per l'altoparlante minuscolo della sveglia: via i bassi sotto 300 Hz (non si sentono e consumano solo potenza), filtro anti-aliasing a 3,5 kHz, compressione per alzare il volume percepito, picco normalizzato e volume medio limitato a quello della suoneria originale più forte. Taglio a 12 secondi con dissolvenza.

Per aggiungere una suoneria all'app: `python3 tools/convert_ringtone.py input.ogg app/src/main/res/raw/ringtone_nome.pcm`, poi una voce in `BuiltInRingtones.kt` con una firma nuova.

## Domande frequenti

**Si può aggiornare il firmware della sveglia dall'app?** No. Il protocollo di aggiornamento della CGD1 non è documentato da nessuno, i file del firmware sono distribuiti solo da Qingping/Xiaomi e un aggiornamento fatto male può rendere la sveglia inutilizzabile.

## Compilare

**Android Studio**: *File → Open* sulla cartella, attendi il sync di Gradle, poi *Run* con il telefono collegato via USB (debug USB attivo).

**Da terminale** (servono JDK 17 e Android SDK con `ANDROID_HOME` impostato):

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
./gradlew connectedDebugAndroidTest   # test su dispositivo/emulatore (decodifica audio)
adb install app/build/outputs/apk/release/app-release.apk
```

**Senza installare niente**: ogni push su `main` avvia il workflow `.github/workflows/build.yml`, che esegue test e lint e pubblica l'APK come artifact.

## Architettura

Un solo modulo, organizzato per livelli: MVVM con stato immutabile per schermata ed effetti one-shot separati.

```
app/src/main/java/it/cgd1/sveglia/
  protocol/   Codec, modelli, costanti, pacchetti audio: Kotlin puro, nessuna dipendenza Android
  audio/      conversione PCM (filtro, ricampionamento, normalizzazione): Kotlin puro
  ble/        Cgd1Client (GATT + coroutine), Cgd1Scanner (callbackFlow)
  data/       ClockRepository: unico confine verso il BLE, mappa ogni eccezione in ClockError
              AppPreferencesRepository e SavedClockStore su DataStore; LanguageController
    ringtone/ catalogo suonerie, download originali Qingping, decodifica con MediaCodec, anteprima con AudioTrack
  di/         moduli Hilt
  ui/
    main/     MainViewModel, MainUiState, effetti e interfacce Actions
    scan/     ricerca della sveglia
    clock/    sveglie, stato del collegamento, impostazioni del dispositivo
    ringtone/ scelta, anteprima e caricamento della suoneria
    settings/ tema, Material You, lingua
    welcome/  guida del primo avvio
    theme/    schema colori M3 (statico, dinamico, AMOLED)
    components/
app/src/test/         CodecTest (i test Python portati 1:1), AudioUploadTest, PcmProcessingTest, MainViewModelTest
app/src/androidTest/  decodifica audio reale con MediaCodec, download dal server Qingping
tools/                convert_ringtone.py per preparare le suonerie incluse
```

Scelte principali:

- **Compose + Material 3**, edge-to-edge, splash screen di sistema, layout limitato in larghezza su tablet
- **Hilt** per la DI, **Coroutines/Flow** per l'asincronia, **DataStore** per le preferenze
- Gli errori di piattaforma restano nel data layer: il ViewModel riceve `Result<T>` con `ClockError`
- Testi solo nelle risorse (`values`, `values-it`, `values-fr`, `values-es`); il ViewModel produce `UiText`, risolto dalla UI
- Le composable di contenuto dipendono da interfacce `*Actions`, non dal ViewModel, così restano testabili e hanno le `@Preview`
- Version catalog in `gradle/libs.versions.toml`, aggiornamenti delle dipendenze via Dependabot

## Note sul protocollo

- Servizio `22210000-554a-4546-5542-46534450464d`; caratteristiche 0x0001/0x0002 (auth), 0x000B/0x000C (dati)
- Autenticazione: `11 01 + token`, `11 02 + token`, poi una lettura delle impostazioni come prova (la sveglia fa ACK anche a token errati)
- Batteria: solo dall'advertisement `FDCD` (campo TLV `02`, bit 7 da mascherare); temperatura e umidità anche dalla notifica `0x0100` da collegati
- Anteprima della suoneria sulla sveglia: `02 04 + volume`
- Suoneria: `08 10 + lunghezza (3 byte LE) + firma (4 byte)`, poi pacchetti `81 08 + 128 byte` con conferma `04 ff 08` ogni 512 byte; serve un MTU di almeno 133. L'audio termina con `00` ed è riempito di `FF` fino al blocco
- L'ora si invia come epoch UTC; il fuso sta nelle impostazioni a passi di 6 minuti. **La sveglia non conosce l'ora legale**: al cambio d'ora basta aprire l'app, che riallinea il fuso da sola

## Licenza

MIT. Vedi [`LICENSE`](LICENSE). Font e icona hanno licenze proprie, elencate in [`third_party/`](third_party/README.md).
