# Abnahme gegen den V1-Auftrag (Stand M4, 02.11.2026)

Legende: **✓** nachgewiesen (Test/Gerät) · **◐** teilweise / nur in Simulation · **○** offen.
Nachweise: JVM-Tests unter `app/src/test`, Android-35-Emulatorlauf (CI „Native Android APK and device verification“), Screenshots und Messwerte unter `docs/evidence/claude-m1/`.

## Finale Abnahme (Auftrag, Abschnitt 9)
| Kriterium | Status | Nachweis |
|---|---|---|
| Je eine vollständige Schicht in drei Bundesländern | ✓ JVM / ◐ Gerät | `NativeAcceptanceTest.fullShiftsInHamburgNiedersachsenAndBayern` auf echten Grenzen und OSM-Straßen (HH 14–22 Uhr, NI 6–14 Uhr, BY 22–6 Uhr). Auf dem Gerät: komplette Hamburger 8h-Schicht; NI/BY dort nur Karte, Disposition und Messung. |
| Mehrere Notrufe mit unterschiedlichen Taktiken (Managementsinn) | ✓ | 34 Abläufe mit Entscheidungen, `NativeScenarioTest` (u. a. Verhandeln vs. SEK-Lösung: messbar unterschiedliche Chancen). Gerät: Entscheidungskarte im Screenshot `native-dispatch-panel.png`. |
| Über die ganze Landeskarte disponieren, echte Spielrouten sehen | ✓ | Routing auf OSM-Netz aller 16 Länder; Gerät: `native-vehicle-route.png`, Niedersachsen/Bayern-Übersicht. |
| 10 Tage Karriere mit Personal-/Finanzentscheidungen | ◐ | `NativeCareerTest`: 10 Tage auf Hamburger Daten mit Wartung, Lehrgängen, Beförderungen, Budgetverlauf. Nur Bot-Spieler; kein menschlicher 10-Tage-Spieltest. |
| Eigene komplexe Szenarien erstellen, speichern, importieren, am Folgetag spielen | ✓ | `NativeAcceptanceTest.ownScenarioCreatedExportedImportedSavedAndPlayedNextDay`; Editor auf dem Gerät (`native-editor.png`). |
| APK ohne WebView, reproduzierbar aus GitHub | ✓ | CI baut, prüft das Paket (keine HTML/JS/CSS, keine WebView-Referenz, keine Internetberechtigung), installiert auf dem Emulator; Release `operator-claude-v1-preview`. |

**Ergebnis:** Noch **kein v1.0**. Es fehlen ein menschlicher Spieltest (Spaß, Verständlichkeit, Balancing über 10 Tage), Messungen auf einem physischen Mittelklassegerät, eine Prüfung im Querformat und eine Hörprobe der Audio-Mischung.

## Testliste aus Abschnitt 8
| Prüfung | Status | Wo |
|---|---|---|
| Saves, Rundreise, Snapshots | ✓ | `NativeSnapshotTest`, mehrere `fromJson(toJson())`-Prüfungen |
| 24h über Mitternacht, Speichern mitten in der Schicht | ✓ | `NativeGameEngineTest`, `NativeAcceptanceTest.saveDuringTwentyFourHourShift…` (deterministisch identisch) |
| 10 aufeinanderfolgende Spieltage | ✓ | `NativeCareerTest` |
| Einsatzketten, Verzweigungen | ✓ | `NativeEditorTest`, `NativeGameEngineTest` |
| Rückfahrt, Verfügbarkeit, Alarmierung auf Rückfahrt | ✓ | `NativeDispatchTest`, `NativeAvailabilityTest` |
| Parallele Zuweisungen, Verstärkung, Abziehen, Umdisponieren | ✓ | `NativeDispatchTest` |
| Pausieren | ✓ | `NativeGameEngineTest.everyHourlyStartAndPause` |
| Editor-Import, Zyklen-/Dublettenschutz | ✓ | `NativeEditorTest` |
| 16 Landesgrenzen | ✓ Gerät | Instrumentationstest prüft alle 16 Länder im Bild |
| 30+ Marker / 20+ Einsätze | ✓ | 48 Einheiten in großen Ländern; Hamburger Schicht 22–24 Notrufe |
| Zoom | ◐ | fingerzentrierter Zoom implementiert; nur manuell/visuell, kein automatischer Gestentest |
| AudioFocus, Hintergrund/Neustart | ◐ | AudioFocus/Lifecycle im Code, Gerätetest prüft Pause/Neustart des Spielstands; keine Hörprobe |
| Speicherstabilität | ◐ | Laden aller 16 Länder im Gerätetest ohne Absturz; kein Langzeit-Profiling |
| Performance (Emulator) | ✓ gemessen | `perf-emulator.txt`: Bayern-Graph 265 ms (63.658 Knoten), Grundkarte 1,9–2,6 s im Hintergrund, Live-Overlay Ø 4,7 ms je Bild. Kein physisches Gerät. |

## Bewusste Entscheidungen
- **Keine MapLibre-Einbindung:** ohne Internetberechtigung und Tile-Server wären mehrere hundert MB Offline-Pakete nötig. Stattdessen native Vektorkarte aus gebündelten OSM-/Natural-Earth-/BKG-Daten (16,2 MB APK gesamt).
- **Routen sind Spielnäherungen** auf dem Hauptstraßennetz (Stadtstaaten mit Nebenstraßen); keine Einbahn-/Abbiegeregeln, keine Verkehrslage. Fehlende Anschlüsse werden als Distanznäherung gekennzeichnet.
- **Balancing für Flächenländer** (vom Abnahmetest gefunden: Niedersachsen zunächst 7/20 gelöst): Fristen wachsen mit der Landesgröße, große Länder haben 48 Einheiten an 16 Wachen.
