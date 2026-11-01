# Technische Architektur – Alpha 0.2 → v1.0

## Baseline / Migration
Aktuell: Android/Kotlin-Host mit lokaler WebView und monolithischer Offline-HTML-Simulation. Referenz `prototype/alpha-0.2-offline.html` ist unabhängig lauffähig und bewahrt die Grafiken. Siehe auch `app/src/main/assets/index.html`. Der bestehende Android-Host lädt die Assets lokal.

## Empfohlene v1.0-Architektur (inkrementell)
- **Android Shell** (Kotlin, Android 26+, target SDK aktualisieren): Activity, Dateiauswahl und Export via SAF, AudioFocus, Vibration, Immersive/Window-Inset Support; keine unnötigen Berechtigungen. WebViewAssetLoader statt file:// und sichere Origin-Bridge bevorzugen; externe Navigation blockieren.
- **Client App**: TypeScript in kleine Module, Vite für Build, UI als HTML/CSS/Canvas oder schlankes Framework falls begründet. Responsive für Touch.
- **Domain Engine**: vollständig von UI getrennt, deterministisches Simulations-Clock/Tick-Verhalten; keine API-Abhängigkeit. Funktionen als reine Reducer/State-Transitions, seeded PRNG für Tests.
- **Subsysteme**:
  `domain/clock`, `domain/shifts`, `domain/incidents`, `domain/dispatch`, `domain/units`, `domain/personnel`, `domain/equipment`, `domain/training`, `domain/career`, `domain/economy`;
  `map/mapAdapter`, `map/vehicleAnimator`, `map/routing`, `map/regions`;
  `audio/mixer`, `audio/cues`, `storage/repository`, `storage/migrations`, `editor/schema`, `ui/*`.
- **Data / Save**: Versionierte JSON-Schemas (z.B. `schemaVersion: 3`), Migrationsfunktion; atomare Speicherung/Backups. Altes Alpha-0.2-localStorage-Format erkennen; Nutzerinhalte nicht überschreiben. Bei Android robuste persistente Speicherung (z.B. Room oder validiertes lokales File), unveränderte Exportmöglichkeit. Keine Bilddaten unkontrolliert in LocalStorage.
- **Region & Map**: MapLibre GL JS im sicheren Assetloader-Host oder, falls signifikant stabiler, native MapLibre Android integriert. Entscheid dokumentieren. Regionendatensätze `regions/*.json` für 16 Länder; **Hamburg**-Geographie mit lizenzierten GeoJSON/OSM-Assets und attribution. Keine hardcodierten frei erfundenen Grenzpolygone als geographisch korrekt kennzeichnen.
- **Routing**: Offline-Gewichtungsgraph für Hamburg als kleiner Assetdatensatz; A*/Dijkstra auf Straßenknoten/vereinfachten Straßenwegen; deterministisch reproduzierbare ETA aus Weglänge × Fahrprofil × dynamischer Belegung. Kein echtes Verkehrslageversprechen. Falls Datensatz fehlt: sichtbare geradlinige Fallback-Bewegung, klar als Näherung gelabelt, aber V1-Releasegate bleibt eine bessere Kartenlösung.
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
