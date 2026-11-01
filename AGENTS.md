# Codex-Arbeitsregeln für EINSATZLEITSTELLE 110

**Mission:** Aus der spielbaren Alpha 0.2 eine erste vollwertige, offline-fähige Android-Leitstellensimulation v1.0 machen. **Umsetzen, nicht nur einen Plan schreiben.**

## Pflichtlektüre (Reihenfolge)
1. `README.md`
2. `docs/CODEX_MASTER_PROMPT.md`
3. `docs/V1_PRODUCT_SPEC.md`
4. `docs/TECHNICAL_ARCHITECTURE.md`
5. `docs/ACCEPTANCE_TESTS.md`
6. `docs/ART_SOUND_BRIEF.md`
7. `prototype/alpha-0.2-offline.html` (funktionale Referenz, enthält alle Originalgrafiken eingebettet)
8. Android-Quellcode unter `app/`

## Unverhandelbar
- Es ist **ein Android-Spiel**, nicht nur Web-Screenshots, eine Designskizze oder ein statisches Mockup.
- **Realismus + Sandbox**: Karriere- und Sandbox-Modus unterstützen Schichttag/Schichtende, frei wählbare Fortbildungen, Personal, Einheiten und Ausrüstung.
- Bestehende Funktionen und Daten **nicht verlieren**. Die Alpha-0.2-Spielstände versioniert migrieren, Export/Import erhalten. Ausgangsprototyp behalten.
- **Hamburg** ist die erste voll spielbare Region; die **16 Bundesländer** sind als Auswahl verfügbar. Keine erfundenen exakten Polizeiwachen/Kartenkoordinaten; fiktive Standorte klar kennzeichnen.
- **Live-Karte**: sinnvolle, interaktive Karte mit Fahrzeugbewegung und visualisierten Anfahrtswegen. Keine gefälschten ETA-Werte; bei fehlenden Straßendaten klar als Simulation markierte Näherung.
- **Originalgrafiken und Originalsounds** verwenden, sauber lizenzieren, Attribution. Niemals Grafiken/Sounds/UI aus existierenden Spielen kopieren.
- Keine echten personenbezogenen Daten, Telemetrie, Werbung, APIs oder Push als Pflicht. Offline-Gameplay muss möglich bleiben.
- Prioritäten: korrekter Spielzustand > spielbares Gameplay > UX > Art/Sound > optionale Details.
- Sauberer, modularer, getesteter Code; keine TODO-Attrappen für zugesagte v1.0-Kernfunktionen.
- Bestehenden codebase inspizieren, **nicht** ohne triftigen Grund neu schreiben.
- Android Debug APK in CI bauen und als Artefakt anbieten; wenn der Build scheitert, Fehler beheben und dokumentieren.
- Keine Secrets ins Repo; offizielle OSM Tile-Server nicht als unbegrenztes Download-/Offline-Backend missbrauchen.
- Keine reale taktische SEK-Einsatzanleitung: Spezialeinsätze bleiben abstrakt-strategisches Management.

## Vorgehensweise
1. Baseline-Spiel starten / JS-Syntax und Gradle-Konfiguration prüfen; fehlenden Wrapper hinzufügen.
2. `docs/IMPLEMENTATION_LOG.md` mit Status/Entscheidungen pflegen.
3. Architektur in kleine, testbare Module überführen (keine unnötige Komplettneuschreibung).
4. Kern-Vertical-Slice **eine vollständige Hamburger Schicht mit Karte → Notruf → Zuweisung → Fahrzeug fährt → Ereignisabschluss → Schichtende → Ausbildung/Ausrüstung → Folgetag** zuerst fertigstellen.
5. Spielinhalte, Editor, Animation und Audio ausbauen.
6. Smoke-, Unit-, Persistenz-, Android- und Performance-Tests ausführen.
7. PR mit Screenshots, Feature-/Testliste, Debug-APK Artefakt und bekannten Grenzen.

## Definition of Done
ALLE Muss-Kriterien in `docs/ACCEPTANCE_TESTS.md` nachweislich erfüllt. Nicht als v1.0 bezeichnen, solange die Kernkriterien fehlen. Ansonsten PR als Zwischenstand kennzeichnen und weiterarbeiten.
