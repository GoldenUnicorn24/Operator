# EINSATZLEITSTELLE 110: DEUTSCHLAND – Geo-Alpha 0.4

**Status:** Spielbarer Android-Entwicklungsstand. **Keine fertig verifizierte Version 1.0.**

## Neu gegenüber Alpha 0.2

- Offline-Gesamtkarte **aller 16 Bundesländer** auf Basis geografischer (vereinfachter) Ländergrenzen; echte Ortskoordinaten für 100+ regionale Einsatzorte.
- Die jeweilige **gesamte Landesfläche** wird als Kartenansicht geöffnet. Zoom, Verschieben, auf ganzes Land zurückstellen, Einsatz-/Fahrzeug-Filter, Gruppenmarker, interaktive Orte/Einsätze/Einheiten.
- Fahrzeuge stehen an unterschiedlichen Orten, fahren nach Disposition **sichtbar auf simulierten Ortskorridoren** und erreichen den Einsatz mit plausibel approximierter Reisezeit. Mehrere Einsatzfahrzeuge und Vorfälle können gleichzeitig gezeigt werden.
- **Schichtdauer:** 8h, 12h oder 24h; frei wählbarer Beginn in ganzen Stunden. Karriere und Sandbox haben Schichtplanung, Tagesauswertung, Ausbildung und Fortsetzung am Folgetag. Mitternacht löst keine falsche Schichtbeendigung aus.
- **48 Einsatzvorlagen**, **19 Start-Einheiten**, **8 Lehrgänge**, Personalporträts, optionale Ausbildung, Ausrüstungsplätze, Budget, eigene Vorlagen, eigene Einheiten und Fahrzeugkonfiguration/Wartung.
- **Original synthetisierte Sounds** per WebAudio: Notruf, P1/P2-Alarm, Funkimpuls, Entsendung, Abfahrt, Erfolg, Misserfolg, Schichtende. Sound optional und stumm beim App-Hintergrund.
- JSON-Saves und Import/Export der Alpha-0.2-Spielstände bleiben erhalten (Schema 2, zusätzliche Felder optional).

## Karten- und Routing-Ehrlichkeit

**Wichtig:** Die Gesamtansicht ist auf echten, vereinfachten Bundesland-Grenzgeometrien aufgebaut. **Es handelt sich nicht um vollständige Straßenkarten oder Echtzeit-Polizeiverkehr.** Städte/Orte sind geografisch platziert, schmale Linien sind **nur abstrahierte Ortskorridore**, Fahrzeugwege eine vereinfachte Anfahrts-Simulation. Die ETA ist keine Straßennavigation und wird in der Benutzeroberfläche als Simulation gekennzeichnet. Ein detaillierter lizenzierter Offline-Straßenroutinggraph und weitere Einsatzorganisationssysteme gehören zum v1.0-Ausbau.

## Start im Browser

`app/src/main/assets/index.html` mit den danebenliegenden Skripten, CSS-, Daten- und Bilddateien öffnen. Zusätzlich liegt die Einzeldatei `Einsatzleitstelle110_GeoAlpha04_Offline.html` als von Android unabhängige Testfassung vor (nur im Downloadpaket).

## Android-APK

Das Android-Studio-Projekt über Gradle 8.9, AGP 8.7.3, JDK 17, SDK 35 bauen:

```
gradle :app:assembleDebug
```

CI baut bei jedem Push in `codex/operator-v1` eine Debug-APK und prüft die Node-Simulations-Tests. Das Test-APK ist nicht für die Veröffentlichung im Play Store signiert.

## Tests

```
node tests/simulation.test.cjs
node --check app/src/main/assets/game.js
node --check app/src/main/assets/map.js
node --check app/src/main/assets/audio.js
```

Zusätzlich wurde die Oberfläche im Headless-Chromium-Browser mit einer über WebView-analogen HTML-Einbettung getestet. Ein Endgerätetest auf einem realen Smartphone ist weiterhin erforderlich.

## Datenrechte

Grenzgeometrien aus [isellsoap/deutschlandGeoJSON](https://github.com/isellsoap/deutschlandGeoJSON) (Repository-Lizenz Unlicense), ursprüngliche Quelle GIS-DATA (diva-gis.org/gdata). Die Geometrien wurden für die Gesamtübersicht vereinfacht. Originale Spielgrafiken aus Alpha 0.2 bleiben erhalten. Der Quellenvermerk wird in der Kartenansicht und in `THIRD_PARTY_NOTICES.md` angezeigt.

## Folgende Schritte bis zu v1.0

Straßenkarten und Offline-Routing in mehreren Detailebenen; vollständige regionale Einsatzprofile; stärkere Simulationslogik für komplexe Großlagen; native Audio-Einstellungen; ausführlichere Fahrzeug-/Dienststellendaten; komplexer grafischer Ketteneditor; Performance-/APK-/Endgerätetests.


## Volles v1.0-Lastenheft
Siehe `docs/CODEX_MASTER_PROMPT.md` und `docs/STATEWIDE_MAP_AND_SHIFTS.md` im GitHub-Repository. Diese Geo-Alpha implementiert einen wesentlichen Teil, nicht die vollständigen v1.0-Releasekriterien.
