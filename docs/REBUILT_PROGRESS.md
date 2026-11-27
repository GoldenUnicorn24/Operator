# Operator Rebuilt – Entwicklungsstand

| Feld | Wert |
| --- | --- |
| Ausgangsbranch | `claude/operator-notruf` |
| Ausgangscommit | `eb88d29e11977717771a61cdb0153a1723ad14c7` |
| Entwicklungsbranch | `codex/operator-rebuilt-v020` |
| App-Version | `0.20.0-rebuilt-preview.1`, VersionCode 118 |
| Anwendung | `de.einsatzleitstelle110`, minSdk 26 |
| Abnahme | Entwicklungsstand für die erste Vorschau |

## Implementierung

- Schichtübergaben bewahren aktive und klingelnde Anrufe einschließlich Protokoll, Fundstelle und Wartezeit. Der Tagesbericht zeigt die Gesprächs-IDs. Die Telefonleiste bietet die Fortsetzung nach einem Neustart an.
- Umdisposition bereitet Rolle, Budget und neue Route vor, bevor sie Fahrzeug und Quellauftrag verändert. Beim Übergang bleibt die verbleibende Ausrückzeit erhalten.
- Zusätzliche Taskforce-Fähigkeiten werden nach Rolle geprüft. Im Karrieremodus wirken Besatzung, Qualifikation, Ausrüstung und fahrzeugspezifische Grenzen. Spezialfähigkeiten lösen ihre Einsatzpauschale aus. Die Sandbox-Einstellung ermöglicht freie Kombinationen.
- Die Einsatzakte verwendet eine Werteprojektion mit Fahrzeug-IDs, Rollen, Besatzung und Fahrzuständen. Beide Orientierungen erhalten getrennte Layouts. Das Hochformat bietet drei Größen der Einsatzakte.
- Ein bestätigter Einsatzort ermöglicht eine vorläufige Einsatzakte, durchsuchbare Stichwörter und manuelle Priorität bei offenem Gespräch. Weitere Fragen aktualisieren dieselbe Einsatzakte. Die Abschlussbewertung ordnet sich dem bestehenden Einsatz zu.
- Der Funk ordnet die Wiedergabe nach Dringlichkeit und setzt auf tatsächliche Simulationsmeldungen. Vorhandene Clips haben Vorrang. Neue Rufnamen und variable Meldungen können mit installierter lokaler deutscher Stimme wiedergegeben werden.
- ETA-Felder werden pro Straßengraph synchronisiert und bei Netzwechsel mit dem Graph ersetzt. Ein begrenzter ETA-Worker übernimmt die vorbereitende Berechnung.
- Automatische Sicherungen und die fünf manuellen Speicherplätze teilen atomare Dateiersetzung. Felder für Notrufstatus, Übergaben, Einsatzverknüpfung und Zeitpunkte ergänzen Schema 7. Ältere Einträge ohne gespeicherten Status werden als klingelnd geladen.

## Meilensteine

| Meilenstein | Status |
| --- | --- |
| M0 | [Ausgangsaudit](REBUILT_AUDIT.md) und Branchvergleich durchgeführt. |
| M1 | Stabilitätskorrekturen und Regressionstests implementiert. Die Umstellung des Simulationsbesitzes und des vollständigen UI-Datenflusses steht aus. |
| M2 | Hauptmenü, primäre Navigation, Telefonwarteschlange, größenverstellbare Einsatzakte und Querformat-Leitstelle umgesetzt. Ein einheitlicher Umbau der Verwaltungsansichten sowie animierter Menü-Hintergrund stehen aus. |
| M3 | Vorläufige Alarmierung, Gesprächsfortsetzung, Einsatzprotokoll, priorisierte Wiedergabe und lokale dynamische Sprache umgesetzt. Breite Fragekategorien, Delegation und das neue interaktive Funkarchiv stehen aus. |
| M4 | Aufgabenbasierte Einsatzengine, Welt-Ereignisse und überarbeiteter Dispositionsassistent stehen aus. |
| M5 | Gruppen-Taskforces, Management- und Wirtschaftsumbau, neue Karriere und KI-Mitarbeiter stehen aus. |
| M6 | Erweiterte Editoren, Inhaltspakete, Statistiken und vollständige Asset-Überarbeitung stehen aus. |
| M7 | Geräteabnahme, Lasttests, vollständiger visueller Satz und Freigabe stehen aus. |

## Reproduzierbare Prüfungen

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 -m unittest discover -s tools/tests -v
python3 scripts/verify_native_apk.py app/build/outputs/apk/debug/app-debug.apk
```

Die Android-Instrumentation enthält einen Ablauf für frühe Alarmierung und Rückkehr zum laufenden Anruf. Für eine Geräteprüfung müssen App und Test-APK auf derselben Android-Instanz installiert sein. Eine native Debug-APK kann eine andere Signatur als frühere Downloads besitzen. Ein direktes Android-Upgrade setzt einen übereinstimmenden Schlüssel voraus.

## Nächster Integrationspunkt

Zuerst die Geräteprüfung und die überarbeiteten Regressionstests auf dem aktuellen Commit ausführen. Danach die direkten Zugriffe der Dialoge und der Kartendarstellung auf den veränderlichen Simulationszustand in klar definierte Befehle und unveränderliche Anzeigezustände überführen. Erst danach den Simulationsbesitz auf einen separaten Dispatcher verlagern. Die bestehende Abschnittsfolge M2 bis M7 und die ursprünglichen Abnahmekriterien bleiben die Referenz.
