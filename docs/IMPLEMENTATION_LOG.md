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
