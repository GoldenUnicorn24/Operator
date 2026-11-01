# EINSATZLEITSTELLE 110: DEUTSCHLAND

Android-Leitstellensimulation mit **Realismus + Sandbox**. Entwicklungsrepository für die erste echte Spielversion **v1.0**.

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
Eine echte offline-fähige Android-Spielversion mit interaktiver Hamburg-Straßenkarte, live fahrenden Einheiten, Einsätzen, Personal und sichtbarer Ausrüstung, Fortbildungen, Schichtenden, Karriere- und Sandbox-Modus, Einsatz- und Einheiteneditor, Sound sowie robustem Speichern. Alle 16 Bundesländer auswählbar; außerhalb Hamburg zunächst Basisregionen.

## Quellstand
Die HTML-Demo kann im Desktop-Browser direkt geöffnet werden. Ihre Logik ist noch monolithisch; die Migration in klar testbare Module ist Teil des Auftrags.

## Rechtliches
Eigenständiges Spiel. Keine kopierten Grafiken, Karten, Sounds oder Marken aus *911 Operator* / *112 Operator*. OSM-Daten und Drittassets nur mit passender Attribution/Lizenz, Lizenznachweise im Repository.

## Status
**Vorbereitung für Codex**, nicht als veröffentlichte v1.0 oder geprüfte APK zu betrachten.
