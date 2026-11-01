# EINSATZLEITSTELLE 110: DEUTSCHLAND

Android-Leitstellensimulation mit **Realismus + Sandbox**. Entwicklungsrepository für die erste echte Spielversion **v1.0**.

> **Wichtige Nutzeränderung:** Nicht nur Hamburg oder einzelne Städte: Jede Region zeigt das ganze jeweilige Bundesland mit allen aktuellen Einsatzfahrzeugen und Einsätzen. Schichten wählbar: **8, 12 oder 24 Stunden** in beiden Modi. Siehe [verbindliche Spezifikation](docs/STATEWIDE_MAP_AND_SHIFTS.md).

## Ausgangslage
- **Alpha 0.2** ist ein spielbarer, offline-fähiger HTML-/JavaScript-Prototyp.
- Der **vollständige Alpha-0.2-Stand einschließlich eingebetteter Grafiken** liegt unter `prototype/alpha-0.2-offline.html`. **Nicht löschen**; er dient als funktionale Referenz für die Migration.
- In Alpha 0.2 vorhanden: Karriere/Sandbox, Schichttag + Schichtende, Personalporträts, Ausrüstung, Ausbildung, SEK/MEK, Einsatzeditor, 16 Bundesländer (nur schematische Karte), Save/Load.
- **Nicht vorhanden bzw. nicht verifiziert:** geografisch präzise Live-Karte, Straßennetzrouting, fertiger Android-Release-Build, native Audioengine, vollständig funktionsgetrennte Codebasis.

## Codex: Hier beginnen
1. `AGENTS.md` vollständig lesen.
2. `docs/CODEX_MASTER_PROMPT.md` als primären Arbeitsauftrag lesen.
3. `docs/V1_PRODUCT_SPEC.md`, `docs/TECHNICAL_ARCHITECTURE.md`, `docs/ACCEPTANCE_TESTS.md` beachten.
4. Den laufenden Prototyp analysieren, seine Spielstände/Features erhalten, dann das **v1.0-Projekt** implementieren.
5. Auf eigenem Feature-Branch arbeiten, CI grün bekommen, signierbares Debug-APK-Artefakt bereitstellen und einen PR eröffnen.

## Ziel für v1.0
Eine echte offline-fähige Android-Spielversion mit **vollständiger Live-Gesamtkarte für jedes der 16 Bundesländer** (Fit-to-Bounds der realen Landesfläche), sichtbaren landesweit verteilten Fahrzeugen und Notrufen, Routen, Cluster-/Kartenfiltern sowie tiefem Hamburg-Detailgrad. **8/12/24-Stunden-Schichten** in Karriere und Sandbox, Ausbildung, Personal-/Ausrüstungsverwaltung, Editor, Sound und robustem Speichern. Verbindliche Präzisierung: `docs/STATEWIDE_MAP_AND_SHIFTS.md`.

## Quellstand
Die HTML-Demo kann im Desktop-Browser direkt geöffnet werden. Ihre Logik ist noch monolithisch; die Migration in klar testbare Module ist Teil des Auftrags.

## Rechtliches
Eigenständiges Spiel. Keine kopierten Grafiken, Karten, Sounds oder Marken aus *911 Operator* / *112 Operator*. OSM-Daten und Drittassets nur mit passender Attribution/Lizenz, Lizenznachweise im Repository.

## Status
**Vorbereitung für Codex**, nicht als veröffentlichte v1.0 oder geprüfte APK zu betrachten.
