# Operator Rebuilt – Entwicklungsstand

| Feld | Wert |
| --- | --- |
| Ausgangsbranch | `claude/operator-notruf` |
| Ausgangscommit | `eb88d29e11977717771a61cdb0153a1723ad14c7` |
| Entwicklungsbranch | `codex/operator-rebuilt-v020` |
| App-Version | `0.20.0-rebuilt-preview.4`, VersionCode 121 |
| Anwendung | `de.einsatzleitstelle110`, minSdk 26 |
| Abnahme | Entwicklungsstand für Vorschau 4 |

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

### Erster Android-Lauf

[CI-Lauf 46086676763](https://github.com/GoldenUnicorn24/Operator/actions/runs/46086676763) hat Build, JVM-Tests, Lint, Datenverträge, Paketprüfung und Offline-Kaltstart abgeschlossen. Von zwölf Gerätetests liefen fünf erfolgreich durch. Der sechste Test blieb vor seiner ersten Aufnahme im Rückrufablauf hängen. Das Schlussbild zeigt die geöffnete Einsatzakte bei pausierter Simulation. Der Emulator zeichnete während des Hängers fortlaufend Frames. Nach 40 Minuten beendete die Pipeline den unvollständigen Lauf und übersprang die Veröffentlichung. Die Wiederholung friert Auswahlpulse während der Pause ein, nutzt für Screenshots eine Hauptthread-Barriere und begrenzt jeden Gerätetest auf vier Minuten mit Stackdiagnose.

## Vorschau 3

- Die monotone Spieluhr berechnet Minuten und Fahranimation aus derselben Temporegel. Anzeige und Auswahl unterscheiden tatsächliches und gewähltes Tempo. Bei längeren Unterbrechungen holt die Uhr je Tick höchstens eine reale Sekunde nach. Hintergrundzeit und Zeit einer anderen Partie laufen nicht weiter.
- Das Funkarchiv speichert bis zu 2.000 der jüngsten tatsächlichen Übertragungen mit Spielzeit, Organisation, Rufname, Priorität und Einsatz-ID, soweit der Funkanlass einem Einsatz zugeordnet ist. Suche, Prioritätsfilter und erneute Audiowiedergabe sind über „Mehr“ erreichbar. Bestehende Textprotokolle bleiben im Tagesprotokoll lesbar. Variable Audioinhalte benötigen eine installierte lokale deutsche Stimme.
- [Vorschau 3](https://github.com/GoldenUnicorn24/Operator/releases/tag/v0.20.0-rebuilt-preview.3) ist veröffentlicht. [CI-Lauf 46133578431](https://github.com/GoldenUnicorn24/Operator/actions/runs/46133578431) bestätigt 169 Unit-Tests, 13 Gerätetests, einen Offline-Kaltstart und 35 Emulatorbilder. Lint meldete 0 Fehler und 64 Warnungen. SHA-256 der APK: `2f58390ab8f6c8224a6baef64db06c74fd6a0c638edabebd33d0da480e8737ab`.

## Vorschau 4

- Einsätze mit mehreren benötigten Kräften besitzen eigene Aufgaben je Rollenanforderung. Eintreffende Besatzungen arbeiten sofort und parallel. Ein Mehrzweckfahrzeug zählt je Einsatz nur für eine abgeschlossene Aufgabe. Wird eine Besatzung abgezogen, übernimmt eine geeignete Ablösung die Restarbeit.
- Die Akte zeigt erledigte, laufende und offene Aufgaben samt Restzeit und Rufname. Erledigte Aufgaben werden als tatsächliche Einsatzereignisse gefunkt und im Funkarchiv geführt. Bleibt nach Ablauf der Ankunftsfrist eine Anforderung unversorgt und ist keine andere Arbeit möglich, bilanziert der Tagesbericht den Teilabschluss separat.
- Vollständig erledigte Mehrrollen-Einsätze erhalten einen eindeutigen Abschluss. Die erfassten Szenarioentscheidungen, Folgeeinsätze und Spielkosten bleiben in den Ablauf eingebunden. Einzelrollen-Einsätze verwenden in dieser Ausbaustufe weiterhin ihre bisherige Bearbeitungs- und Erfolgslogik.
- Schema 7 speichert Aufgabenfortschritt und Tagesbilanz. Ältere aktive Mehrrollen-Einsätze übernehmen die gespeicherte Restarbeitszeit ihrer Besatzungen. Bei bereits geschlossenen älteren Einsätzen wird keine Aufgabenhistorie ergänzt.

### Regressionen und Lastmessung

Der erste [CI-Lauf für Vorschau 4](https://github.com/GoldenUnicorn24/Operator/actions/runs/46134433611) reproduzierte eine Balance-Regression: Der aktive Karriere-Test endete mit 89 Ruf, passive Spielweise mit 93. Vollständig erledigte Einsatzaufgaben bleiben zuverlässig abgeschlossen. Die Bewertung berücksichtigt den dokumentierten Risikoverlauf beim Ruf. Der Karriere-Test entsendet verfügbare Ergänzungskräfte auch, wenn vor Ort bereits erste Aufgaben laufen. Lokal endete der Vier-Schichten-Vergleich nach der Korrektur mit 94,3 % vollständig abgeschlossenen Einsätzen bei aktiver und 91,2 % bei passiver Spielweise, beide mit Ruf 94.

Bei 50, 100, 250 und 500 Fahrzeugen sowie 10, 25, 50 und 100 Mehrrollen-Einsätzen betrugen die P95-Zeiten 0,71, 1,43, 3,60 und 9,04 ms pro Tick. Pro Laststufe ergaben drei Läufe à 120 Spielminuten nach einem Aufwärmlauf die Messwerte. Gemessen wurde auf der JVM mit dem Hamburger Straßennetz, ohne Kartenrendering und ohne physisches Android-Gerät.

## Meilensteine

| Meilenstein | Status |
| --- | --- |
| M0 | [Ausgangsaudit](REBUILT_AUDIT.md) und Branchvergleich durchgeführt. |
| M1 | Stabilitätskorrekturen und Regressionstests implementiert. Die Umstellung des Simulationsbesitzes und des vollständigen UI-Datenflusses steht aus. |
| M2 | Hauptmenü, primäre Navigation, Telefonwarteschlange, größenverstellbare Einsatzakte und Querformat-Leitstelle umgesetzt. Ein einheitlicher Umbau der Verwaltungsansichten sowie animierter Menü-Hintergrund stehen aus. |
| M3 | Vorläufige Alarmierung, Gesprächsfortsetzung, Einsatzprotokoll, priorisierte Wiedergabe und lokale dynamische Sprache umgesetzt. Das durchsuchbare Funkarchiv ist eingebaut. Breite Fragekategorien, Delegation und spielwirksame Funkantworten stehen aus. |
| M4 | Die vier Dispositionsmodi sind eingebaut. Mehrrollen-Einsätze nutzen speicherbare parallele Arbeiten und Teilabschlüsse. Die Erweiterung auf Einzelrollen, Welt-Ereignisse und weitere Abdeckungslogik stehen aus. |
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

## Veröffentlichte Vorschau 2

| Prüfung | Ergebnis |
| --- | --- |
| Verifizierter Code | [bf75b7c](https://github.com/GoldenUnicorn24/Operator/commit/bf75b7c6cfae0584aca7df1907b7b6b166eb6ef8) |
| Unit-Tests | 163 bestanden |
| Android-35-Emulator | 12 bestanden, einschließlich Rückruf/Querformat und Autosave-Schutz am ersten Tag |
| Paket und Offline-Start | Native Paketprüfung bestanden, VersionCode 119, Kaltstart im Flugmodus nach 5.355 ms |
| Lint | 0 Fehler, 63 Warnungen |
| SHA-256 der veröffentlichten APK | `51eca93b5c001d08832a9ae72fa703e0c2291d78e6d028e6596a1a1daf9389c6` |
| Prüfnachweise | [CI-Lauf 46132381958](https://github.com/GoldenUnicorn24/Operator/actions/runs/46132381958) und 34 Emulatorbilder |
| Download | [GitHub-Pre-Release](https://github.com/GoldenUnicorn24/Operator/releases/tag/v0.20.0-rebuilt-preview.2) |

Der erste Wiederanlauf stoppte beim fehlenden JUnit-Import für die Zeitgrenze. Anschließend kompilierten beide APKs und Lint lokal. Der vollständige GitHub-Lauf prüfte alle 12 Abläufe erfolgreich. Die Release-APK trägt weiterhin eine Debug-Signatur.

## Provisorische Simulationsmessung

JVM mit Java 21, Hamburger Offline-Routengraph, aktive Fahrzeuge und gleichzeitige Einsätze. Pro Zeile wurden nach einem Aufwärmlauf drei Folgen von jeweils 120 simulierten Minuten gemessen. Die Werte umfassen `advance(1)` ohne Kartenrendering, Audio, ETA-Vorschau oder UI. Laufzeiten auf physischen Geräten und Bildrate sind offen. Im Android-35-Emulator benötigte die Detailkarte im separaten Software-Canvas-Test durchschnittlich 24,58 ms je Zeichenvorgang und 31,10 ms am P95. Im veröffentlichten Prüflauf waren es durchschnittlich 36,50 ms und 38,15 ms am P95. Beide Werte beschreiben Software-Canvas-Zeichenvorgänge im Emulator.

| Fahrzeuge | Parallele Einsätze | Median pro Tick | P95 | Spitze |
| ---: | ---: | ---: | ---: | ---: |
| 50 | 10 | 0,09 ms | 0,75 ms | 12,59 ms |
| 100 | 25 | 0,14 ms | 1,37 ms | 22,79 ms |
| 250 | 50 | 0,49 ms | 6,03 ms | 17,58 ms |
| 500 | 100 | 0,39 ms | 6,88 ms | 31,75 ms |

Der entsprechende Lauf mit der gemeinsam indexierten Route auf dem Code der zweiten Vorschau ergab für 50, 100, 250 und 500 Fahrzeuge P95-Tickzeiten von 0,44, 1,07, 3,26 und 5,81 ms. Die jeweils parallelen Einsatzmengen entsprechen der Tabelle. Das sind JVM-Messwerte ohne Kartenrendering oder physisches Gerät.

## Zweite Durchsicht

- **Spielerperspektive.** Frühe Disposition ist erstmals im Gesprächsablauf nutzbar. Der Emulator-Ablauf bestätigt, dass Anruf, priorisierter Einsatz und entsandte Fahrzeuge gleichzeitig im Modell bestehen. Die drei Layoutaufnahmen zeigen, wo Fortsetzung, Fahrzeugliste und Akte zu finden sind.
- **Spieldesign.** Die Vorschau verändert den Zeitpunkt der Alarmierung und baut auf den bestehenden Szenarien auf. Eigenständige Arbeitsaufgaben pro Einheit, neue Abschlusslogik, kontinuierliche Weltentwicklungen und langfristige Finanzen benötigen eigene Balance- und Langzeitprüfungen.
- **Technik.** Umdisposition und Dateioperationen wurden auf unveränderten Quellzustand bei Ablehnung geprüft. Die Routing-Nebenläufigkeit ist durch parallele Abfragen abgesichert. Die frühere grobe UI-Erkennung ist ersetzt. Die Simulationsautorität liegt weiter beim Hauptthread und ist der wichtigste offene Architekturpunkt.
- **Release.** Der vorhandene Anwendungsschlüssel der archivierten 0.16-APK lässt sich aus dem veröffentlichten Paket nicht wiederherstellen. Ein kompatibel signiertes Upgrade ist ein eigener Freigabepunkt.

## Nächster Integrationspunkt

Die bestätigte Vorschau ist der Ausgangspunkt. Die Bestätigung beim Neuanlegen einer Partie benötigt noch einen Prüffall mit reiner Personal- oder Wachplanung am ersten Tag. Die direkten Zugriffe der Dialoge und der Kartendarstellung auf den veränderlichen Simulationszustand in klar definierte Befehle und unveränderliche Anzeigezustände überführen. Erst danach den Simulationsbesitz auf einen separaten Dispatcher verlagern. Die bestehende Abschnittsfolge M2 bis M7 und die ursprünglichen Abnahmekriterien bleiben die Referenz.

## Nächster Entwicklungsabschnitt

Vorschau 3 ist freigegeben. Für Vorschau 4 gehört ein eigener Ablauf zur parallelen Aufgabenbearbeitung zur Geräteabnahme.

Die Simulation benötigt weiterhin eindeutigen Zustandsbesitz durch einen Dispatcher und unveränderliche Werte für Hauptmenü, Karte, Telefon und Einsatzakte. Ein geeigneter Einstieg ist ein getesteter Zeitgeber mit serialisierter Befehlsübergabe. Vor dem Verschieben des Ticks müssen UI-Zugriffe, Audioereignisse, Ladevorgänge und Speicher-Snapshots vom veränderlichen Spielzustand getrennt sein. Die Aufgabenbearbeitung für Mehrrollen-Einsätze ist umgesetzt. Die nächste Erweiterung betrifft Einzelrollen-Einsätze sowie vor Ort aktiv ausgelöste Folgeaufgaben. Der Draft-PR #7 bleibt auf `claude/operator-notruf` gerichtet.
