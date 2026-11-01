# Android-Host und Buildnachweise

Stand: 2026-11-01. Versionskennung bleibt `0.3.0-dev`, bis alle Produktgates geprüft sind.

## Implementiert

- Gradle-Wrapper **8.9** aus dem offiziellen Gradle-Tag `v8.9.0`; Download-Prüfsumme der Distribution fest in `gradle-wrapper.properties`.
- Wrapper-JAR SHA-256: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`. Die Apache-2.0-Lizenz ist im JAR unter `META-INF/LICENSE` enthalten; die Wrapper-Skripte tragen ihre Lizenzheader.
- Android 26+, Compile/Target 35, Java-Ziel 17, AGP 8.7.3, Kotlin 2.0.21.
- HTTPS-Origin `https://appassets.androidplatform.net/assets/` über AndroidX WebViewAssetLoader. Externe Navigation, WebView-Berechtigungsanfragen, Datei-/Content-URL-Zugriff und sämtliche externen Ressourcen sind blockiert. Keine INTERNET-, Standort-, Mikrofon- oder Speicherberechtigung nötig.
- SAF-Dateiauswahl und JSON-Export via ActivityResultContracts. Exportdatei wird bis zur SAF-Rückgabe privat atomar gespeichert; Größenlimit 15 MiB, bestehender Spielstand bleibt bei Abbruch erhalten.
- AudioFocus und Vorder-/Hintergrundsignale über `el110:audio-focus` bzw. `el110:lifecycle`, jeweils `{detail:{active:boolean}}`. WebView und Audio werden bei Activity-Pause angehalten. Simulation nimmt im Frontend nur auf ausdrückliche Fortsetzung wieder auf.
- Systemleisten-/Cutout-Insetbehandlung und eigenes Vektor-App-Icon.

## Verlustfreie Origin-Übernahme

Eine Umstellung von `file://` auf HTTPS ändert den localStorage-Ursprung. Beim ersten Start lädt der Host deshalb zunächst die **exakte alte URL** `file:///android_asset/index.html`; der Interceptor liefert dort ausschließlich den skriptfreien Inhalt von `legacy-storage.html` aus. Das vermeidet jede Annahme über gemeinsam genutzte Datei-Ursprünge und startet das Spiel nicht unter dem alten unsicheren Ursprung. Der Host liest ausschließlich die alten Schlüssel `el110-save-v2` und `el110-save`, schreibt eine unveränderte Kopie atomar in die private Datei `alpha02-origin-backup.json` und übernimmt diese vor Laden des eigentlichen Spiels in den neuen HTTPS-Ursprung. Bereits vorhandene Schlüssel am neuen Ursprung werden nicht überschrieben. Der alte Speicher und die private Sicherung werden nicht gelöscht. Bei Lesefehler, Platzmangel oder fehlgeschlagener Wiederherstellung bleibt die Übernahme offen und bietet Wiederholung an. Die JavaScript-Bridge wird erst nach der Übernahme am Spielursprung angebunden.

Die fachliche Alpha-0.2-zu-neuem-Schema-Migration erfolgt danach im versionierten Frontend-Speichermodul. Damit bleiben die Sicherung des Originals und die Schemaänderung getrennt prüfbar.

## Automatisierte Android-Prüfungen

`app/src/androidTest/java/de/einsatzleitstelle110/HostInstrumentedTest.kt` prüft auf einem echten Android-WebView:

- HTTPS-Start und Bridge-Verfügbarkeit ohne externe Ressourcen;
- blockierten Datei-/Content-Zugriff und externe Navigation;
- Lifecycle-Ereignisse beim Hintergrund-/Vordergrundwechsel;
- Übernahme des alten Datei-Ursprungs, private Originalkopie und Schutz bereits neuerer Daten.
- Authentisches Alpha-0.2-Fixture aus `tests/fixtures/alpha02.json` im Test-APK: Budget 123456 und alle ursprünglichen Einheiten werden durch Host-Origin- und Domänenmigration bis Schema 3 verfolgt.
- Tatsächliche Niedersachsen-Gesamtkarte im ausgelieferten WebView-Bundle, verteilte Notrufe, Dispatch und geografische Positionsänderung eines Fahrzeugs; komplette 24h-Schicht einschließlich interner Rotation, offener Übergaben, Report und Tag 2.

GitHub Actions führt Node-Domänentests, Browser-Workflows, Web-Bundle, `:app:assembleDebug`, `:app:lintDebug` sowie in einem eigenen API-26-`google_apis`-Emulatorjob `:app:connectedDebugAndroidTest` aus. WLAN und Mobilfunk werden vor dem Test deaktiviert. APK, Browsernachweise, Lintbericht, echte Android-Screenshots und Instrumentationsergebnisse sind getrennte Artefakte. Keine Release-Signierung und keine Veröffentlichung.

## Lokale Prüfergebnisse

Noch laufende Prüfungen werden erst nach tatsächlicher Ausführung ergänzt. Ein lokaler Versuch ohne Java-Proxyparameter endete mit `java.net.ConnectException: Connection refused`; die Umgebung benötigt für Java zusätzlich zu den geerbten HTTP_PROXY-Variablen JVM-Systemproperties. Diese Umgebungsparameter gehören nicht in die Projektkonfiguration oder CI, deren Runner regulären Netzwerkzugriff haben.

### Build/Lint: bestätigt

Lokal mit Temurin **17.0.21+9**, SDK Platform 35 und Build-Tools 35.0.0:

```sh
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Ergebnis: **BUILD SUCCESSFUL**, 69 Tasks, zuletzt 52 Sekunden, **0 Lintfehler / 6 Warnungen**. Die Warnungen betreffen bewusst ältere kompatible Bibliotheksversionen, für das Offline-Spiel erforderliches JavaScript und die synchrone Bestätigung der einmaligen Speichermigration. Der erste Lintlauf entdeckte einen API-27-only-Style bei minSdk 26; das Attribut wurde entfernt und der vollständige Lauf erfolgreich wiederholt. APK-Größe am geprüften Zwischenstand: ca. 5,2 MiB. Der integrierte Endstand wird in CI erneut aus den Webquellen gebaut.

Für genau diese Cloud-Umgebung wurden zusätzlich `JAVA_HOME=/workspace/android-tools/jdk-17.0.21+9`, `ANDROID_HOME=/workspace/android-sdk` und JVM-Proxy-/CA-Parameter gesetzt. Die Distribution wurde aus dem offiziellen Temurin-GitHub-Release geladen und per veröffentlichter SHA-256-Prüfsumme verifiziert. Diese Umgebungsinstallation ist kein Projektbestandteil.

### Android-Laufzeit: noch in Prüfung

Der erste lokale API-26-AOSP-`default`-Emulator startete ohne KVM; bei der Diagnose war sein WebView-Provider trotz installiertem Paket noch nicht bereit (`dumpsys webviewupdate`). Der Test wurde vor Ausführung auf das API-26-`google_apis`-Image verlegt. Der dabei unterbrochene Gradle-Lauf endete beim Gerätewechsel mit `No connected devices` und hat keine Produkttests bestanden. Das belegt keinen dauerhaften Defekt des ersten Images. Beim zweiten Image trat während des langsamen Erstboots außerdem ein transienter Android-Systemserverfehler in `MemoryIntArray` auf; nach automatischem Neustart stand ein gültiger Chrome-69-WebView-Provider bereit. CI nutzt ebenfalls API 26 `google_apis`. Offline-Funktionalität des Spiels wird nach Deaktivierung von WLAN/Mobilfunk geprüft.
