# Technische Architektur – Alpha 0.2 → v1.0

## Historischer Ausgangsstand und Migration
Alpha 0.2: Android/Kotlin-Host mit lokaler WebView und monolithischer Offline-HTML-Simulation. Referenz `prototype/alpha-0.2-offline.html` ist unabhängig lauffähig und bewahrt die Grafiken. Siehe auch `app/src/main/assets/index.html`. Der bestehende Android-Host lädt die Assets lokal.

## Historischer Architekturentwurf für die Alpha
- **Android Shell** (Kotlin, Android 26+, target SDK aktualisieren): Activity, Dateiauswahl und Export via SAF, AudioFocus, Vibration, Immersive/Window-Inset Support; keine unnötigen Berechtigungen. WebViewAssetLoader statt file:// und sichere Origin-Bridge bevorzugen; externe Navigation blockieren.
- **Client App**: TypeScript in kleine Module, Vite für Build, UI als HTML/CSS/Canvas oder schlankes Framework falls begründet. Responsive für Touch.
- **Domain Engine**: vollständig von UI getrennt, deterministisches Simulations-Clock/Tick-Verhalten; keine API-Abhängigkeit. Funktionen als reine Reducer/State-Transitions, seeded PRNG für Tests.
- **Subsysteme**:
  `domain/clock`, `domain/shifts`, `domain/incidents`, `domain/dispatch`, `domain/units`, `domain/personnel`, `domain/equipment`, `domain/training`, `domain/career`, `domain/economy`;
  `map/mapAdapter`, `map/vehicleAnimator`, `map/routing`, `map/regions`;
  `audio/mixer`, `audio/cues`, `storage/repository`, `storage/migrations`, `editor/schema`, `ui/*`.
- **Data / Save**: Versionierte JSON-Schemas (z.B. `schemaVersion: 3`), Migrationsfunktion; atomare Speicherung/Backups. Altes Alpha-0.2-localStorage-Format erkennen; Nutzerinhalte nicht überschreiben. Bei Android robuste persistente Speicherung (z.B. Room oder validiertes lokales File), unveränderte Exportmöglichkeit. Keine Bilddaten unkontrolliert in LocalStorage.
- **Region & Map**: MapLibre GL JS im sicheren AssetLoader-Host oder native MapLibre Android. **16 korrekt georeferenzierte Polygon-Assets und Bounds** für alle Bundesländer; kameraautomatisches `fitBounds(region.boundary)` mit „Ganzes Bundesland“. Landesweite Positionen und Incident-/Unit-Layer, dynamisches Clustering und Filter. Offline-fähige vereinfachte Basiskarten für alle 16; Hamburg optional höher aufgelöst. OSM/GeoJSON-Lizenzen und Attribution beachten; keine Fantasiegrenzen als korrekt deklarieren.
- **Routing**: Getrennte Engine für landesweite Einheitenpositionen / Bewegungsanimation und verfügbare Straßen-/Wegegraphen. A*/Dijkstra bei lokal vorhandenen Wegedaten; andernfalls klar als Simulation gelabelte ETA und Live-Fahrspur. Keine falschen Echtzeit-Verkehrsdaten; bei Regionwechsel alle Positionen als georeferenzierte Daten erhalten. Landesweite Pins auch bei Zoom-out und Clustering selektierbar.
- **Audio**: Howler/WebAudio oder natives SoundPool/AudioTrack mit Fokus- und Lebenszyklus-Behandlung. Cues, Cooldowns, SFX-Priorität, getrennte Lautstärkekanäle, keine Autoplay-Sperren umgehen.
- **Art**: Assets nach `assets/portraits`, `assets/vehicles`, `assets/gear`, `assets/incidents`, `assets/sfx` gliedern; Placeholder durch Originalillustrationen ersetzen, Credits/Attribution in `THIRD_PARTY_NOTICES.md`.
- **Editor**: Versionierte JSON-Schemas für `IncidentTemplate`, `UnitTemplate`, `VehicleTemplate`, `ScenarioGraph`; Referenzen prüfen, keine dynamische `eval`-Ausführung. Knoten: trigger, message, dispatchRequirement, timer, branch, effect, end.

## Domain-Entitäten (Mindestwerte)
`GameState`: schemaVersion, mode, dayIndex, shiftType, shiftMinute, simulationSpeed, paused, regionId, budget, reputation, stats, personnel, units, incidents, trainingQueue, equipmentInventory, customContent, RNG seed.
`Personnel`: id, displayName, role, imageRef, skills, fatigue, morale, inventoryRefs, equippedSlotIds, trainingId.
`Unit`: id, callsign, type, personnelIds, vehicleId, regionId, basePosition, position, route, availability, assignments, wear.
`Incident`: id, templateId, location, priority, createdAt, deadline, requiredCapabilities, assignedUnitIds, phase, branchState, consequences.
`Training`: id, targetPersonnelIds, startDay, durationDays, remainingDays, cost, skillDelta.
`ShiftReport`: day, region, assignedCount, resolvedCount, failedCount, carriedCount, fatigue, expenditures, credits, reputationDelta.

## Android-Build
- Aktuell AGP 8.7.3 / Kotlin 2.0.21 / SDK 35 / Java 17. Gradle 8.9 ist mit AGP kompatibel.
- Wrapper für reproduzierbaren Build ergänzen. GitHub Actions `.github/workflows/android-ci.yml` als Ausgangspunkt; Debug APK als Artefakt.
- Verwendete Versionen beim Codex-Start prüfen und ggf. gemeinsam aktualisieren statt blind Versionen zu mischen.
- Release-Signing-Keys gehören **niemals** ins Repo; fürs Testen genügt Debug APK.

## CI / Tests
- JS/TS Unit-Tests für Uhr, Schichtende, 2-Tage-Training, Inventar, Fahrzeugrouting, Meldungs-Folgeereignis, Save-Migration.
- Browser-Smoke (Playwright) für einen vollständigen Spieltag und eine zweite Schicht, Bild-Uploads, Grafik-Assets, Tonoptionen, Editor, Import/Export.
- Android CI: kompilieren, minimale Start-/Lifecycle-Prüfung, APK hochladen.
- Keine externe Datenquelle als Hard Dependency in CI.


## Neuer Schicht- und Zeitkern (verbindlich)
- `ShiftPlan { durationMinutes: 480|720|1440, startAbsoluteMinute, endAbsoluteMinute, elapsedMinutes, selectedPreset, onDutyTeamIds }`; Kalenderdatum/Spieluhr von `absoluteSimMinutes` ableiten. `shiftIndex` exakt einmal beim Schichtabschluss erhöhen, *nicht* bei Mitternacht.
- 8/12/24h in Karriere/Sandbox; Report, offene Übergaben, Trainingsfortschritt und Gehälter exakt einmal beim Abschluss. 24h erfordert interne Personalrotation und Müdigkeit/Ruhephasen.
- `RegionMap { regionId, polygonGeoJSON, bounds, cityMarkers, roadDetailLevel, facilityLocations }`; Echtzeitpositionen pro `VehicleState` mit lat/lon, route polyline, progress, assignedIncidentId, status. Zoom-out mit marker clustering/priority aggregation; Übersicht aller aktiven Orte/Einheiten auch bei großen Flächen.
- Siehe `docs/STATEWIDE_MAP_AND_SHIFTS.md` (höhere Priorität bei Widersprüchen).

## Operator Rebuilt – native Übergabepunkte

Die APK verwendet Kotlin, native Android-Views, SoundPool und eine Canvas-Karte. Die Assets für Karte und Sprache werden in das Paket aufgenommen. Lint, Paketprüfung und Offline-Start laufen auf dem tatsächlichen Android-Build.

- `SimulationClock` verwendet monotone Zeit, teilt die Temporegel mit Anzeige und Kartenanimation und begrenzt nachzuholende Ticks. Ein Hintergrundwechsel pausiert die Spielzeit.
- `IncidentWork` speichert je Rollenanforderung Restarbeit und Fahrzeug-IDs. Der Rollenabgleich ordnet einer Besatzung höchstens eine Aufgabe zu. Eine Ablösung übernimmt die Restarbeit. Abgeschlossene Aufgaben gehen als strukturierte Funkereignisse ins Archiv ein.
- `CallQuestions` ordnet Originalskripte acht Fragethemen zu. Zusatzfragen belegen Zeit im Gespräch und können sich auf die Auswertung auswirken. Nur die Originalfragen können geskriptete Wendungen auslösen oder deren Detailkenntnisse liefern.
- `RadioOperations` prüft Quittierungen und Statusabfragen gegen den gespeicherten Einsatzbezug und die aktuelle Zuordnung. Das Archiv speichert bis zu 2.000 Meldungen einschließlich Text, Priorität und Kontext. Antworten auf ältere Meldungen werden anhand der aktuellen Lage validiert.
- `GameController` bereitet Zielroute, Einsatzrolle, Kosten und Ausrückzeit vor. `commitDispatch` übernimmt die geprüfte Disposition und aktualisiert anschließend die Funkmeldung.
- `PhoneDesk` verbindet den aktiven `PhoneCall` mit derselben `IncidentState`. Die Einsatzakte liest die Zeitpunkte und das gespeicherte Gesprächsprotokoll aus dem Modell.
- `DispatchPanelState` projiziert die für die offene Einsatzakte sichtbaren Werte. Die Werteprojektion enthält keine veränderlichen Referenzen auf Fahrzeuge oder Einsatzdaten.
- `RoadGraph` besitzt seine ETA-Felder und synchronisiert deren Cache. Hintergrundvorbereitung nutzt skalare Routing-Eingaben. Die Simulationsmutation läuft weiterhin auf dem UI-Ticker. Der Fortschritt zur neuen Zustandsarchitektur steht in [REBUILT_PROGRESS.md](REBUILT_PROGRESS.md).
- Autosave und Speicherplätze schreiben validierte Daten mit `writeSaveAtomically`. Schema 7 behandelt die neuen Felder als optional und bewahrt die bestehenden Migrationen.
- `RadioQueue` trennt Prioritäten, `RadioScript` erzeugt Meldungen aus Simulationsereignissen und `VoicePlayer` wählt gebündeltes Audio oder eine installierte Offline-Stimme.
