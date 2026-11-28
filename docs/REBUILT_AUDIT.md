# Operator Rebuilt – Ausgangsaudit

Entwicklungsbasis ist `claude/operator-notruf` auf `eb88d29e11977717771a61cdb0153a1723ad14c7`, Version 0.16.0, VersionCode 117. Der Branch war beim Audit auf genau diesem Commit. Der Arbeitsbranch ist `codex/operator-rebuilt-v020`. Die veröffentlichten APK-Quellstände bis einschließlich der 0.15-Vorschau verwenden höchstens VersionCode 116.

## Branchvergleich

| Branch | Eigene Commits | Hinter der Basis | Relevanz |
| --- | ---: | ---: | --- |
| `claude/operator-v1-gameplay` | 0 | 56 | In der Basis enthalten |
| `codex/operator-v1-polizeichef` | 0 | 32 | In der Basis enthalten |
| `codex/operator-v1` | 0 | 88 | In der Basis enthalten |
| `codex/leitstelle-realstationen` | 15 | 88 | Überlappende Hamburger Wachen, Fahrzeugeditor und Taskforces. Eigene Prüffälle für die amtlichen Standorte sind ein Kandidat für gezielte Übernahme. |
| `feature/native-v1` | 7 | 118 | Separater, älterer Simulationskern. Eine Übernahme würde den aktuellen Kern und dessen Tests ersetzen. |
| `main` | 0 | 107 | Vorfahr der Basis |

## Befunde

| Bereich | Befund |
| --- | --- |
| Telefon, Spielstand | Beim Schichtende und Schichtstart wurde die Telefonliste geleert. Schema 7 speicherte den aktiven Status nicht. Ein gespeichertes Gespräch wurde stets wieder als klingelnd geladen. |
| Disposition | `redirect` rief erst `recall` auf. Ein abgelehnter Zielauftrag konnte danach bereits den Quellauftrag, das Fahrzeug und den Funkverlauf verändert haben. |
| Rollen | Die Ausbildungssperre bezog sich auf den primären Typ SEK oder MEK. Zusätzlich deklarierte Fähigkeiten gingen ungeprüft in die Rollenzuordnung ein. |
| Funk | Die Audio-Warteschlange löschte ältere Meldungen ohne Priorisierung. Die gesprochenen Funkrufnamen waren auf vorgerenderte Peter-Nummern begrenzt. Zufälliger Routinefunk meldete auch Spielhandlungen ohne Simulationszustand. |
| Darstellung | Die offene Einsatzakte verfolgte freie Kräfte als Anzahl. Wechselten zwei Fahrzeuge im selben Takt ihre Verfügbarkeit, konnte die alte Liste stehen bleiben. |
| Nebenläufigkeit | `advance` läuft im UI-Ticker. Karte, Disposition und Dialoge greifen direkt auf veränderliche Simulationsobjekte zu. Die ETA-Vorbereitung aus dem Telefon erzeugte pro Aufruf einen Thread. Der Detail-ETA-Cache wurde ungeschützt verändert. |
| Daten | Wichtige Geografie- und Schichttests kehrten bei fehlenden Dateien erfolgreich zurück. Die Autosave-Sicherung schrieb direkt in die Sicherungsdatei. |
| CI | Der historische [Lauf 42226516615](https://github.com/GoldenUnicorn24/Operator/actions/runs/42226516615) baute und prüfte das Paket. Der Gerätetest wurde nach dem Start der Instrumentation abgebrochen. Der [erneute Baseline-Lauf 46079206117](https://github.com/GoldenUnicorn24/Operator/actions/runs/46079206117) absolvierte Build, Unit-Tests, Lint und Paketprüfung. Beide Uploads scheiterten an der Actions-Speicherquote, der abhängige Gerätetest wurde übersprungen. |
| Signierung | 0.16 baut eine Debug-APK. Eine dauerhaft konfigurierte, mit älteren Installationen kompatible Signatur geht aus der Build-Konfiguration nicht hervor. Datenmigration und Paket-Upgrade müssen getrennt geprüft werden. |

Der Audit umfasst den nativen Codebestand, die Build- und Paketwege, die relevanten Branchdifferenzen und konkrete Fehlerpfade. Vollständige Geräte-, Lizenz-, Leistungs- und Spielbalanceprüfungen sind Bestandteil der späteren Abnahme.
