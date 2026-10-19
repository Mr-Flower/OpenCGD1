# OpenCGD1

App Android open source per comandare la sveglia Bluetooth **Qingping CGD1** senza Mi Home né Qingping+. Funziona tutto in locale via Bluetooth LE: niente account, niente cloud.

È un porting in Kotlin della libreria Python [qingping-cgd1](https://github.com/rjocoleman/qingping-cgd1) di Robert Coleman (MIT), a sua volta basata sul reverse engineering di clOwOck.

## Funzioni

**Sveglia**
- Trova la sveglia e mostra temperatura, umidità e batteria (dall'advertisement Qingping, che non è cifrato)
- Legge e scrive i 16 slot sveglia: ora, giorni della settimana, snooze, attiva/disattiva
- Interruttore generale delle sveglie e "prossima sveglia"
- Sincronizza ora **e fuso** con il telefono, a ogni collegamento e con il pulsante dedicato
- Impostazioni del dispositivo: volume, 12/24 h, °C/°F, lingua del display, luminosità giorno/notte, modalità notte con orari

**App**
- Material 3, con i colori **Material You** presi dallo sfondo (Android 12+, disattivabili)
- Font **Inter**, simile a MiSans delle app Xiaomi; icona "clock" di Lucide
- Tema **Segui il sistema**, **Chiaro**, **Scuro** o **AMOLED** (nero puro)
- Lingua **English**, **Español**, **Français**, **Italiano**, oppure quella del dispositivo (predefinita). Su Android 13+ la lingua si può cambiare anche da *Impostazioni → App → OpenCGD1 → Lingua*

## Primo collegamento

La sveglia accetta un solo "proprietario". Se è associata a Mi Home o Qingping+ rifiuta l'app:

1. Rimuovila da Mi Home, **oppure** tieni premuto il pulsante finché l'icona Bluetooth lampeggia
2. Apri l'app: la ricerca parte da sola, tocca la sveglia
3. Al primo collegamento la sveglia si lega al token dell'app e da lì in poi funziona sempre

Dopo il reset Mi Home non la vedrà più (è proprio lo scopo).

## Installazione

Scarica l'APK dall'ultima build nella scheda **Actions** del repository (artifact `opencgd1-apk`) e installalo sul telefono. Requisiti: Android 8.0 (API 26) o successivo, Bluetooth LE.

L'APK "release" è firmato con la chiave di debug: va bene per uso personale, non per il Play Store.

## Compilare

**Android Studio**: *File → Open* sulla cartella, attendi il sync di Gradle, poi *Run* con il telefono collegato via USB (debug USB attivo).

**Da terminale** (servono JDK 17 e Android SDK con `ANDROID_HOME` impostato):

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

**Senza installare niente**: ogni push su `main` avvia il workflow `.github/workflows/build.yml`, che esegue test e lint e pubblica l'APK come artifact.

## Architettura

Un solo modulo, organizzato per livelli: MVVM con stato immutabile per schermata ed effetti one-shot separati.

```
app/src/main/java/it/cgd1/sveglia/
  protocol/   Codec, modelli, costanti: Kotlin puro, nessuna dipendenza Android
  ble/        Cgd1Client (GATT + coroutine), Cgd1Scanner (callbackFlow)
  data/       ClockRepository: unico confine verso il BLE, mappa ogni eccezione in ClockError
              AppPreferencesRepository e SavedClockStore su DataStore; LanguageController
  di/         moduli Hilt
  ui/
    main/     MainViewModel, MainUiState, effetti e interfacce Actions
    scan/     ricerca della sveglia
    clock/    sveglie, stato del collegamento, impostazioni del dispositivo
    settings/ tema, Material You, lingua
    theme/    schema colori M3 (statico, dinamico, AMOLED)
    components/
app/src/test/  CodecTest (i test Python portati 1:1) e MainViewModelTest (con repository finto)
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
- L'ora si invia come epoch UTC; il fuso sta nelle impostazioni a passi di 6 minuti. **La sveglia non conosce l'ora legale**: al cambio d'ora basta aprire l'app, che riallinea il fuso da sola

## Licenza

MIT. Vedi [`LICENSE`](LICENSE). Font e icona hanno licenze proprie, elencate in [`third_party/`](third_party/README.md).
