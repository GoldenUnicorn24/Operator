# Implementierungslog

## 2026-11-01 – Codex-Vorbereitung
- GitHub-Repository `GoldenUnicorn24/Operator` zunächst leer vorgefunden.
- GitHub-Basisprojekt erstellt; bestehende Alpha-0.2-Demo mit eingebetteten Bildern als unveränderte Referenz aufgenommen.
- Minimales Android-Projekt mit WebView-Host angelegt, CI für Debug-APK erstellt.
- Verbindliche Spezifikationen, Art-/Soundbriefing, Architektur, Abnahmecheckliste, Codex-Masterprompt bereitgestellt.

## Noch zu implementieren
- Geografisch fundierte Live-Karte Hamburg samt flüssiger Fahrzeugbewegung und Routen.
- Modularisierte Engine, erweiterter Einsatz- und Personal-Gameplay-Loop, zusätzliche 40 Einsätze/8 Ausbildungen, original SFX.
- Echte Android CI-/Geräte-Abnahme. Die CI-Konfiguration wurde angelegt, aber hier **noch nicht überprüft, ob der Build erfolgreich läuft**.

Codex: Fortlaufende Umsetzung und Testergebnisse mit Datumsstempel dokumentieren. Niemals ungeprüfte Fertigmeldungen übernehmen.

## 2026-11-01 – Implementierung im Branch `codex/operator-v1`

### Audit und Erhalt
- Sämtliche verbindlichen Repository-Spezifikationen einschließlich der priorisierten Landes-/Schichtänderung gelesen; Issue #1 geprüft. Arbeitsbranch war sauber.
- Alpha-JavaScript syntaktisch geprüft und unverändert unter `prototype/` erhalten. Authentisches neues Alpha-0.2-Save-Fixture aus dessen `newState()` erzeugt; alte Datenfelder und aktive Zuordnungen werden mit Migrationstests abgesichert.
- Bestehendes WebView-/HTML-Layout, alle ursprünglichen Mechaniken und bereitgestellten Grafiken als Basis erhalten. Keine Komplettneuschreibung des Android-Spiels.

### Landesweiter Vertical Slice
- Alle16 realen Ländergeometrien offline gebündelt (GeoBasis-DE/BKG2021 via geoBoundaries; Inseln/Exklaven erhalten). SVG/Mercator-Adapter statt MapLibre: geringe Offline-Datenmenge, kein CDN/GPU-/Tile-Backend nötig, kompatibel mit API26. Diese bewusste Adapterwahl erfüllt die Funktionsanforderungen ohne Pflichtserver.
- Hamburg zusätzlich mit3.735 amtlichen Hauptstraßen-Features und A*-Spielgraph. Sonst nachvollziehbares Entfernungsmodell; sämtliche Routen/ETA ausdrücklich als Näherung markiert. Wachen sind fiktiv, keine erfundenen realen Dienststellen.
- Pro neu geöffnetem Land32 verteilte Fahrzeuge, Notrufe/Einheiten/Wachen, Cluster samt Kategorie/Zähler/Einzelauswahl, Filter, Pan/Pinch/Zoom, Fit, Minimap, Tag/Nacht, Heatmap/Abdeckung und Tap-Editorplatzierung.
- Fahrzeuge fahren physisch im Simulationsmodell zum Ort und zurück. Bearbeitung beginnt erst nach Eintreffen aller benötigten Kräfte. Regionwechsel erhält Positionen und laufende Zuweisungen.

### Spielkern, Zeit und Management
- TypeScript-Module für Clock/Shifts, Disposition/Incidents, Personal/Ausrüstung/Training/Wirtschaft, Storage/Migration, Editor, Audio und Kartenrenderer; Vite erzeugt ein lokales klassisches Chromium58-IIFE.
- Absolute Zeit mit480/720/1440-Minuten-Schichten und Stundenbeginn0–23; Mitternacht endet keine Schicht. Report/Buchungen/Ausbildung genau einmal, Folgetag nur einmal über den Weiter-Button.
- Dokumentierte Übergaben erhalten Einsatz, Personal-/Fahrzeugzuordnung, verbleibende Frist und Bearbeitungszeit. 24h-Dienste wechseln auf ausgeruhte Teams; Alpha-Einheiten ohne zugekaufte Ablösung haben echte Ruhephasen.
- 48 datengetriebene Vorlagen, acht Rollen, strategische Entscheidungen, erfolgreiche/fehlgeschlagene Folgeketten, zehn freiwillige Lehrgänge, Inventar/Slots, Bilder, Besatzungs- und Rollenänderung, Fahrzeugkauf/-wartung, Dienstplan, Wachen-Ausbau und Karriere-Luftunterstützungsfreischaltung.
- Editoren schreiben tatsächlich verwendete Domänenwerte. Ketten mit bis zu20 Stufen und onSuccess/onFailure; Referenzen, Schleifen, Koordinaten, Kapazitäten, IDs und Zahlen werden validiert. Drei Slots, Backup und komprimierter lokaler Save; lesbarer JSON-Dateiexport.

### Grafik, Audio und Android
- Ursprüngliche acht Porträts erhalten, neue originale Fahrzeug-/Ausrüstungsvektoren, neues fiktives MEK-Gruppenbild. Zehn originale reproduzierbare WAV-Cues mit Master/Funk/SFX/Ambiente, Mute, Fokus-/Lifecycle-Stopp und Textfunk.
- Sicherer Android-HTTPS-AssetLoader, externe Ressourcen/Navigation blockiert, keine Internetberechtigung; verlustfreie private Sicherung beim Wechsel vom exakten alten Datei-Ursprung, SAF, App-Icon und Insets. Wrapper mit Distribution-Prüfsumme, Node-/Browser-/Android-CI und APK-Artefakt.

### Frische Fehlerbehebung und Prüfung
- Unabhängiger Review deckte konkrete Probleme auf: Handler-IDs konnten in inline-JavaScript geraten; fehlende Routenzahlen/Sequenzvalidierung konnten Saves beschädigen; Ruhe ohne Ablösung wurde immer weiter verlängert; erste Kettenstufe und Frist-Fehlschlagzweig waren unvollständig; importierte Reports wurden zu oberflächlich geprüft. Alle behoben und durch Regressionen abgesichert.
- Zusätzlich Planungsspeicherung nach Wechsel auf12/24h korrigiert; uneinheitliche Straßenpunktabstände werden bei Bewegung nach Entfernung interpoliert; neue Personal-IDs kollidieren nicht mit bestehenden Profilen.
- Domänentests, Browserabläufe und Buildresultate siehe Prüfmatrix und nachfolgender Abschlussnachweis. Keine unfertigen Releasegates als bestanden umetikettiert. Version bleibt vorerst0.3.0-dev; physische Geräte-/Hörabnahme ist vom Desktopbenchmark getrennt.

### Lokaler Abschlussnachweis (vor Android-Runtime/Actions)
- `npm test`: **45/45 bestanden**, einschließlich echter Alpha-Migration, sechs Modus-/Schichtkombinationen, 24h-Übergabe, zwei Ausbildungstage, Kettenfehlerzweigen, Importangriffen und Speicherbudget aller Länder plus Slots.
- `npm run build:web`: **Exit0**, TypeScript streng geprüft, lokales Chromium58-IIFE erzeugt (ca.1,08MB unkomprimiert inklusive aller Offline-Geometrien; ca.304KB gzip).
- `npm run test:ui`: **14/14 bestanden**, gebauter APK-Webinhalt, ausschließlich lokale Requests, 412×915 sowie915×412. Dazu Karriere-Hamburg-Tag1→Tag2, Niedersachsen-Dispatch, Bayern, alle16 Bounds, Profile, Editor, zwei Ausbildungstage und Regression für Einheitenwahl zwischen Live-Ticks.
- `npm run format:check` und `git diff --check`: **Exit0**.
- Reproduzierter weiterer Bedienfehler behoben: der Dispositionsselektor verlor bei einem Tick zwischen Fokuswechsel und Klick seine Auswahl. Auswahl wird jetzt pro Einsatz gehalten; deterministischer Browserregressionsfall prüft genau diesen Ablauf.
- Browser-Leistungsprobe:132 regionale Fahrzeuge +20 Einsätze (weitere32 Fahrzeuge im anderen Land gespeichert), 60 Kartenupdates; Messwerte/Browserkennung unter `docs/evidence/browser-performance.json`. Das ist kein physischer Android-Benchmark.
- Sieben echte Browseransichten unter `docs/evidence/`; Android-Nachweise werden separat ergänzt. Keine APK- oder Runtime-Ergebnisse aus fehlgeschlagenen Umgebungsversuchen als bestanden gezählt.

### Integration paralleler GitHub-Änderungen und APK-Übergabe
- Zwischenzeitlich eingegangene Remote-Commits regulär zusammengeführt; deren Quellmodule, Geometrie und Tests bleiben erhalten. Die aktive Oberfläche nutzt die modularisierte Implementierung. Migration zusätzlich für Geo-v2-Schichten (12/24h, freie Startzeit), Fahrzeugwerte, Koordinaten und Anfahrten erweitert; Originalobjekt zusätzlich erhalten. 46 Domänentests bestanden.
- GitHub-Actions-Erstlauf stoppte vor Browser-/Android-Build, weil ubuntu-latest inzwischen26.04 ist und Playwright1.56 dieses System nicht unterstützt. Runner auf reproduzierbares ubuntu22.04 festgelegt.
- Lokaler API26-Softwareemulator: drei Instrumentationstests mit JavaScript-/Start-Timeouts fehlgeschlagen. Keine native Runtime-Freigabe behauptet. Debug-APK und Lint lokal weiterhin erfolgreich; CI prüft mit KVM erneut.


## Native Überarbeitung 2026-11-01
Explizite Nutzerkorrektur umgesetzt: eigene native Leitstelle statt WebView oder unveränderter fremder nativer Zwischenfassung. Neue Grafik, persönliche kostenlose P99, Bezirkszuständigkeit, flexible SEK/MEK-Disposition, portierte Einsätze und Spielstandmigration. Details und ehrliche Grenzen: NATIVE_REVISION.md. Lokaler Gradle-Lauf für Unit-Tests, beide APKs und Lint erfolgreich; Android-Gerätetest folgt als verpflichtender CI-Schritt vor Release-Upload.

Android-Geräteprüfung deckte einen ANR bei großen gespeicherten Mehrland-Spielständen auf. Speicherarbeit (Validierung, Backup, fsync und atomarer Austausch) in einen einzelnen Hintergrundschreiber verlagert, Zwischenstände zusammengefasst, Planung nicht mehr periodisch gesichert und Activity-Wiederöffnung gegen ausstehende Schreibvorgänge abgesichert. Der Gerätetest prüft den Folgetag zusätzlich direkt aus der dauerhaften Datei. Die vollständige Gerätewiederholung bleibt vor Veröffentlichung verpflichtend.

Weitere Gerätebelege: echte Wandsbek-Karte, persönliche P99-/MP5-Ansicht, Beamtenprofil und 24h-Report erfasst. Lifecycle-Timeout zusätzlich durch vollständig native, tief kopierte Hintergrund-Snapshots adressiert: auch JSON-Erzeugung liegt jetzt außerhalb des UI-Threads; Regressionstest prüft alle veränderlichen Profil-/Inventar-/Ablösungs-/Einsatzreferenzen. 27 Kotlin-Tests und lokaler vollständiger Gradle-Lauf erfolgreich. CI nutzt ein reines Android-35-AOSP-System ohne unnötige Google-Dienste und erfasst bei Fehlern vollständige ANR-Daten.

Native Android-35-Abnahme in Lauf 41362143918: OK (3 tests), alle 16 Bounds, Wandsbek/P99/Spezialkräfte, 24h/Rotation/Übergabe, tatsächliche Datei und Wiederöffnung, Niedersachsen/Bayern. Produktionsbuild, 27 Kotlin-Tests, Lint und Paketprüfung erfolgreich. Nachgelagerter Diagnosebefehl dumpsys lastanr liefert ohne ANR Exit2; als optionale Diagnose korrigiert. Die reguläre Veröffentlichungs-Pipeline hält erfolgreiche Builds und echte Android-Tests als Voraussetzung für den APK-Upload bei.

Wiederholung 41362853131 bestätigt erneut OK (3 tests), einschließlich aller sechs PNGs. Die Emulator-Action interpretiert jedoch jede `script`-Zeile separat; eine mehrzeilige Screenshot-Schleife war daher im Nachbereitungsschritt ungültig. Den vollständigen Geräteablauf in `.github/scripts/native-device-check.sh` verlagert, Syntax mit `bash -n` geprüft und auf die bereits erfolgreich heruntergeladenen sechs Screenshots vereinheitlicht. Keine Spieltests entfernt oder abgeschwächt.


## V1-Auftrag (Claude) – Audit und Meilenstein M1 · 2026-11-02
Arbeitsbranch `claude/operator-v1-gameplay` von `codex/operator-v1` (Stand 536dfe0, 0.9.0-native-preview, PR #3 offen; dessen Änderungen bleiben erhalten).

**Audit – wichtigste echte Spielflussprobleme vor M1:**
1. Straßen nur für Hamburg; die übrigen 15 Länder zeigten gestrichelte Stadt-zu-Stadt-Linien und fuhren Luftlinie.
2. Fahrzeuge wurden zwischen Ticks linear interpoliert und schnitten bei hoher Geschwindigkeit quer durch Blöcke; Pinch-Zoom nicht fingerzentriert; Hamburg wegen Neuwerk winzig.
3. Disposition nur Einheit für Einheit über einen kartenverdeckenden Dialog; kein Abziehen, Umdisponieren oder Nachalarmieren auf Rückfahrt; Verstärkung nicht möglich.
4. Jede Schicht begann mit demselben Verkehrsunfall; Notrufe in festem 30-Minuten-Takt; zwei „Entscheidungen“ mit identischer Risikowirkung; Erfolg ≈ Würfelwurf (~65 %) ohne Begründung.
5. 24 von 32 Einheiten pro Land hatten zufällige Heimatpunkte statt Wachen.

**Architekturentscheidungen:** Karte bleibt nativer Canvas (keine WebView, keine Internetberechtigung). MapLibre Native wurde bewusst nicht eingebunden: ohne Tile-Server bräuchte es mehrere hundert MB Offline-Pakete oder eine Online-Pflicht; stattdessen gebündelte Vektordaten (OSM-Auszug einmalig in CI, ODbL; Natural Earth gemeinfrei) mit Hintergrund-Rendering in Bitmaps (`MapRenderer`) und einem Live-Overlay (`TacticalMapView`). Routing als eigener Graph pro Land (`RoadGraph`, CSR, Hauptkomponente, Raster-Index); ETA-Listen über eine Dijkstra-Distanzkarte je Einsatzort statt A* je Kandidat. UI-Panel (`DispatchPanel`/`UiKit`) getrennt von `MainActivity`; Engine-Erweiterungen bleiben rein (JVM-testbar).

**Werkzeugkette:** lokal kein Android-SDK erreichbar (Google-/Maven-Server gesperrt). Engine und JVM-Tests lokal mit dem Kotlin-2.0.21-Compiler aus der Gradle-Distribution und JUnit 4.13.2 geprüft; APK, Lint und Emulator ausschließlich in GitHub Actions. Gerätescreenshots werden von der CI nach `docs/evidence/claude-m1/` committet.

**Geodaten:** erster OSM-Lauf scheiterte an 504-Timeouts der Overpass-Server (Berlin) und verwarf alles; Pipeline seither länderweise, fortsetzbar, mit Teil-Commits und Zeitbudget. Zweiter Lauf 41370046831: alle 16 Länder, 478.471 Straßenabschnitte, ~1,0 Mio. Stützpunkte, 2.546 Orte, 23 MB JSON. Lokal gemessen (JVM, kein Gerätewert): Niedersachsen Laden+Graph 360 ms / 42.057 Knoten, Hannover–Oldenburg 154 km A* 25 ms; Bayern 63.658 Knoten, München–Nürnberg 169 km A* 4 ms. Hamburger 14–22-Uhr-Testschicht auf OSM-Netz: 18 Notrufe, 16 gelöst, 25/25 Fahrten auf Straßen.

**Gerätelauf mit allen Landesnetzen:** 41373766228 – Build, 36 JVM-Tests, Lint, APK-Prüfung (verlangt jetzt alle 16 Straßennetze), Android 35 `OK (4 tests)`, Screenshots in `docs/evidence/claude-m1/`.

**Ergebnis M1 (siehe CHANGELOG, Release-Notes `docs/RELEASE_CLAUDE_V1.md`):** CI-Lauf 41369424543 und Folgeläufe: Build, 35 Unit-Tests, Lint, Paketprüfung grün; Android-35-Emulator `OK (4 tests)` inklusive neuer Hamburger Live-Karten-/Dispositions-/8h-Schicht-Prüfung. Lokale JVM-Simulation einer Hamburger 14–22-Uhr-Schicht mit automatischer Disposition: 17 Notrufe, 16 gelöst, 24 von 24 Fahrten auf dem Straßennetz (Werte variieren mit Seed/Daten).

**Offen (M2–M4):** mehrstufige Einsatzabläufe mit eigenen Dialogen und ≥30 ausgearbeiteten Typen, SEK/MEK-Spezifik, Personal/Fahrzeug getrennt, Loadout-Bildschirm, visueller Ketteneditor, 10-Tage-Karriere, Mixer/Onboarding, Leistungsmessung auf physischem Gerät.
