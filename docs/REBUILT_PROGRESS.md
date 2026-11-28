# Operator Rebuilt – Entwicklungsstand

| Feld | Wert |
| --- | --- |
| Ausgangsbranch | `claude/operator-notruf` |
| Ausgangscommit | `eb88d29e11977717771a61cdb0153a1723ad14c7` |
| Entwicklungsbranch | `codex/operator-rebuilt-v020` |
| App-Version | `0.20.0-rebuilt-preview.2`, VersionCode 119 |
| Anwendung | `de.einsatzleitstelle110`, minSdk 26 |
| Abnahme | Entwicklungsstand für Vorschau 2 |

## Implementierung

- Schichtübergaben bewahren aktive und klingelnde Anrufe einschließlich Protokoll, Einsatzort und Wartezeit. Der Tagesbericht zeigt die Gesprächs-IDs. Die Telefonleiste bietet die Fortsetzung nach einem Neustart an.
- Umdisposition bereitet Rolle, Budget und neue Route vor, bevor sie Fahrzeug und Quellauftrag verändert. Beim Übergang bleibt die verbleibende Ausrückzeit erhalten.
- Zusätzliche Taskforce-Fähigkeiten werden nach Rolle geprüft. Im Karrieremodus wirken Besatzung, Qualifikation, Ausrüstung und fahrzeugspezifische Grenzen. Spezialfähigkeiten lösen ihre Einsatzpauschale aus. Die Sandbox-Einstellung ermöglicht freie Kombinationen.
- Die Einsatzakte verwendet eine Werteprojektion mit Fahrzeug-IDs, Rollen, Besatzung und Fahrzuständen. Beide Orientierungen erhalten getrennte Layouts. Das Hochformat bietet drei Größen der Einsatzakte.
- Ein bestätigter Einsatzort ermöglicht eine vorläufige Einsatzakte, durchsuchbare Stichwörter und manuelle Priorität bei offenem Gespräch. Weitere Fragen aktualisieren dieselbe Einsatzakte. Die Abschlussbewertung ordnet sich dem bestehenden Einsatz zu.
- Der Funk ordnet die Wiedergabe nach Dringlichkeit und setzt auf tatsächliche Simulationsmeldungen. Vorhandene Clips haben Vorrang. Neue Rufnamen und variable Meldungen können mit installierter lokaler deutscher Stimme wiedergegeben werden.
- ETA-Felder werden pro Straßengraph synchronisiert und bei Netzwechsel mit dem Graph ersetzt. Ein begrenzter ETA-Worker übernimmt die vorbereitende Berechnung.
- Automatische Sicherungen und die fünf manuellen Speicherplätze teilen atomare Dateiersetzung. Felder für Notrufstatus, Übergaben, Einsatzverknüpfung und Zeitpunkte ergänzen Schema 7. Ältere Einträge ohne gespeicherten Status werden als klingelnd geladen.

## Vorschau 2

- Eine neue Karriere oder Sandbox verlangt stets eine ausdrückliche Bestätigung, sobald bereits eine Partie aktiv ist. Das schützt auch reine Planung am ersten Tag, etwa eine neu gebaute Wache. Ein Geräteablauf prüft das Abbrechen beider Menüpunkte und den unveränderten Autosave.
- Der Dispositionsassistent bietet Schnellste Hilfe, Ausgewogen, Wirtschaftlich und Manuell. Bei P1 haben in den unterstützten Modi die schnellsten budgetierbaren Kräfte Vorrang. Bei den Vorschlägen stehen Rolle, ETA und Kosten. Beim Einsatz der letzten freien Streife einer Zuordnung erscheint eine Abdeckungswarnung.
- Simulation und Karte nutzen dasselbe Distanzprofil. Die kumulierten Distanzen werden einmal pro aktiver Geometrie berechnet. Routenwechsel mit derselben Punktzahl und denselben Endpunkten laden auch bei veränderter Zwischenstrecke neu.
- Vorschau-Releases leiten ihren Tag aus der tatsächlich gebauten APK ab. Die Veröffentlichung erfordert eine vollständig erfolgreiche Build-, Paket- und Geräteprüfung.

## Meilensteine

| Meilenstein | Status |
| --- | --- |
| M0 | [Ausgangsaudit](REBUILT_AUDIT.md) und Branchvergleich durchgeführt. |
| M1 | Stabilitätskorrekturen und Regressionstests implementiert. Die Umstellung des Simulationsbesitzes und des vollständigen UI-Datenflusses steht aus. |
| M2 | Hauptmenü, primäre Navigation, Telefonwarteschlange, größenverstellbare Einsatzakte und Querformat-Leitstelle umgesetzt. Ein einheitlicher Umbau der Verwaltungsansichten sowie animierter Menü-Hintergrund stehen aus. |
| M3 | Vorläufige Alarmierung, Gesprächsfortsetzung, Einsatzprotokoll, priorisierte Wiedergabe und lokale dynamische Sprache umgesetzt. Breite Fragekategorien, Delegation und das neue interaktive Funkarchiv stehen aus. |
| M4 | Die vier Dispositionsmodi sind eingebaut. Aufgabenbasierte Einsatzengine, Welt-Ereignisse und Erweiterung der Abdeckungslogik stehen aus. |
| M5 | Gruppen-Taskforces, Management- und Wirtschaftsumbau, neue Karriere und KI-Mitarbeiter stehen aus. |
| M6 | Erweiterte Editoren, Inhaltspakete, Statistiken und vollständige Asset-Überarbeitung stehen aus. |
| M7 | Die erste Kandidaten-APK hat 11 Geräteprüfungen absolviert. Zusätzliche Geräteabläufe, physische Leistungsmessungen, vollständiger visueller Satz und Vollfreigabe stehen aus. |

## Reproduzierbare Prüfungen

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 -m unittest discover -s tools/tests -v
python3 scripts/verify_native_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Die visuelle Prüfung des ersten Emulatorlaufs zeigte eine kurzzeitig leere Fortsetzen-Schaltfläche und einen zu niedrigen Aktenbereich im Querformat. Der Folgecommit setzt die Beschriftung bei der Erstellung und reserviert mehr Höhe für die Akte. Die Android-Tests prüfen beide Fälle.

Der erste lokale Rebuilt-Lauf startete 157 Unit-Tests. 156 waren erfolgreich. Der verpflichtende Vergleich des Binärnetzes mit den eingecheckten Rohdaten meldete den fehlenden lokalen JSON-Export. Die geprüften eingebetteten Assets des Ausgangs-APKs stimmen mit 2.685 Git-Blob-Prüfsummen überein. Sie ersetzen den Rohdatenvergleich nicht. APK, Instrumentation-APK und Lint wurden lokal erfolgreich erzeugt. Der native APK-Prüfer hat keine Web-Dokumente gefunden. Der GitHub-Lauf [46082334536](https://github.com/GoldenUnicorn24/Operator/actions/runs/46082334536) hat Unit-Tests, Build, Lint, Geodatenverträge und Paketprüfung im vollständigen Repository erfolgreich ausgeführt. Sein Prüfarchiv enthält 157 bestandene Unit-Tests, 11 bestandene Android-Tests und 34 Emulatorbilder. Lint meldete 0 Fehler und 76 Warnungen. Der SHA-256-Wert der lokalen Vorschau lautet `3df9d6e07adad53356ff49d48a700b431a823616604e938672bcc3840afc30de`.

Die Android-Instrumentation enthält einen Ablauf für frühe Alarmierung und Rückkehr zum laufenden Anruf. Für eine Geräteprüfung müssen App und Test-APK auf derselben Android-Instanz installiert sein. Die lokale Debug-APK und der archivierte 0.16-CI-Build haben unterschiedliche Zertifikate. Ein direktes Android-Upgrade dieser 0.16-Installation ist mit dem hier geprüften APK deshalb nicht möglich.


## Veröffentlichte Vorschau 1

| Prüfung | Ergebnis |
| --- | --- |
| Verifizierter Code | [aa4860d](https://github.com/GoldenUnicorn24/Operator/commit/aa4860d4381a94f6dee114b5fe60d410786c8b1f) |
| Unit-Tests | 157 bestanden |
| Android-35-Emulator | 11 bestanden, darunter Alarmierung im laufenden Gespräch und das höhere Aktenlayout im Querformat |
| Paket | VersionCode 118, native Paketprüfung bestanden, 2.685 APK-Assets mit der 0.16-Ausgangs-APK identisch |
| Offline-Start | Installation und Kaltstart im Flugmodus, gemeldete Startzeit 3.131 ms |
| Lint | 0 Fehler, 63 Warnungen |
| SHA-256 der veröffentlichten APK | `c583bbd69410ab3e0383ca7c227552f054dff1183e03e20ae6f7944f7d0862b9` |
| Prüfnachweise | [CI-Lauf 46083439125](https://github.com/GoldenUnicorn24/Operator/actions/runs/46083439125) und 34 Emulatorbilder |
| Download | [GitHub-Pre-Release](https://github.com/GoldenUnicorn24/Operator/releases/tag/v0.20.0-rebuilt-preview.1) |
| Pull Request | [Draft-PR #7](https://github.com/GoldenUnicorn24/Operator/pull/7) mit Ziel `claude/operator-notruf` |

Auch das Zertifikat der veröffentlichten APK weicht vom Zertifikat der archivierten 0.16-APK ab. Der Test der Spielstanddaten und die Freigabe eines direkt installierbaren Updates sind damit getrennte Abnahmepunkte.

## Provisorische Simulationsmessung

JVM mit Java 21, Hamburger Offline-Routengraph, aktive Fahrzeuge und gleichzeitige Einsätze. Pro Zeile wurden nach einem Aufwärmlauf drei Folgen von jeweils 120 simulierten Minuten gemessen. Die Werte umfassen `advance(1)` ohne Kartenrendering, Audio, ETA-Vorschau oder UI. Laufzeiten auf physischen Geräten und Bildrate sind offen. Im Android-35-Emulator benötigte die Detailkarte im separaten Software-Canvas-Test durchschnittlich 24,58 ms je Zeichenvorgang und 31,10 ms am P95. Im veröffentlichten Prüflauf waren es durchschnittlich 36,50 ms und 38,15 ms am P95. Beide Werte beschreiben Software-Canvas-Zeichenvorgänge im Emulator.

| Fahrzeuge | Parallele Einsätze | Median pro Tick | P95 | Spitze |
| ---: | ---: | ---: | ---: | ---: |
| 50 | 10 | 0,09 ms | 0,75 ms | 12,59 ms |
| 100 | 25 | 0,14 ms | 1,37 ms | 22,79 ms |
| 250 | 50 | 0,49 ms | 6,03 ms | 17,58 ms |
| 500 | 100 | 0,39 ms | 6,88 ms | 31,75 ms |

## Zweite Durchsicht

- **Spielerperspektive.** Frühe Disposition ist erstmals im Gesprächsablauf nutzbar. Der Emulator-Ablauf bestätigt, dass Anruf, priorisierter Einsatz und entsandte Fahrzeuge gleichzeitig im Modell bestehen. Die drei Layoutaufnahmen zeigen, wo Fortsetzung, Fahrzeugliste und Akte zu finden sind.
- **Spieldesign.** Die Vorschau verändert den Zeitpunkt der Alarmierung und baut auf den bestehenden Szenarien auf. Eigenständige Arbeitsaufgaben pro Einheit, neue Abschlusslogik, kontinuierliche Weltentwicklungen und langfristige Finanzen benötigen eigene Balance- und Langzeitprüfungen.
- **Technik.** Umdisposition und Dateioperationen wurden auf unveränderten Quellzustand bei Ablehnung geprüft. Die Routing-Nebenläufigkeit ist durch parallele Abfragen abgesichert. Die frühere grobe UI-Erkennung ist ersetzt. Die Simulationsautorität liegt weiter beim Hauptthread und ist der wichtigste offene Architekturpunkt.
- **Release.** Der vorhandene Anwendungsschlüssel der archivierten 0.16-APK lässt sich aus dem veröffentlichten Paket nicht wiederherstellen. Ein kompatibel signiertes Upgrade ist ein eigener Freigabepunkt.

## Nächster Integrationspunkt

Die bestätigte Vorschau ist der Ausgangspunkt. Die Bestätigung beim Neuanlegen einer Partie benötigt noch einen Prüffall mit reiner Personal- oder Wachplanung am ersten Tag. Die direkten Zugriffe der Dialoge und der Kartendarstellung auf den veränderlichen Simulationszustand in klar definierte Befehle und unveränderliche Anzeigezustände überführen. Erst danach den Simulationsbesitz auf einen separaten Dispatcher verlagern. Die bestehende Abschnittsfolge M2 bis M7 und die ursprünglichen Abnahmekriterien bleiben die Referenz.

## Nächster Entwicklungsabschnitt

Die Simulation benötigt weiterhin eindeutigen Zustandsbesitz durch einen Dispatcher und unveränderliche Werte für Hauptmenü, Karte, Telefon und Einsatzakte. Ein geeigneter Einstieg ist ein getesteter Zeitgeber mit serialisierter Befehlsübergabe. Vor dem Verschieben des Ticks müssen UI-Zugriffe, Audioereignisse, Ladevorgänge und Speicher-Snapshots vom veränderlichen Spielzustand getrennt sein. Anschließend folgt die pro Einsatz speicherbare Aufgabenbearbeitung mit Regressionen für Teilfortschritt und Abschluss. Der Draft-PR #7 bleibt auf `claude/operator-notruf` gerichtet.
