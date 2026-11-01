# Quellen, Lizenzen und Attribution

Eigenständige fiktive Spielsimulation. Keine übernommenen Assets oder Benutzeroberflächen aus bestehenden Operator-Spielen. Keine echten Notrufdaten, Polizeifunkaufnahmen, Personen- oder Wachenstandorte.

## Landesgrenzen

**© GeoBasis-DE / BKG 2021**, NUTS250, bereitgestellt über **geoBoundaries gbOpen**, DEU ADM1 (`DEU-ADM1-10402087`). Lizenz: **Datenlizenz Deutschland – Namensnennung – Version 2.0**, <https://www.govdata.de/dl-de/by-2-0>.

Gepinnte Quelle: <https://github.com/wmgeolab/geoBoundaries/tree/f6920d43375832a91201bdb7954a200beaa40202/releaseData/gbOpen/DEU/ADM1>. Attribute reduziert, Koordinaten auf fünf Nachkommastellen gerundet; alle Polygonkomponenten erhalten. Anzeige zusätzlich zoomabhängig vereinfacht; Punktprüfung verwendet die vollständigen ausgelieferten Geometrien. [Quelle, SHA-256 und Änderungen](web/data/geo/source.json), [Datenhinweise](web/data/geo/README.md).

## Hamburger Hauptstraßen

**Freie und Hansestadt Hamburg, Behörde für Wirtschaft, Verkehr und Innovation**: Datensatz *Hauptverkehrsstraßen Hamburg*, amtlicher WFS. Lizenz: **Datenlizenz Deutschland Namensnennung 2.0**. 3.735 Straßen-Features, Abruf 2026-11-01. Attribute reduziert, gerundet und mit Douglas–Peucker vereinfacht. [Download-URL, SHA-256 und Quellenmetadaten](web/data/geo/hamburg-roads-source.json).

Die Darstellung und der bidirektionale Spielgraph sind keine echte Navigation. Intercity-Korridore in den übrigen Ländern sind sichtbar gekennzeichnete geografische Simulationsverbindungen. Es werden keine öffentlichen OSM-Tiles heruntergeladen oder ausgeliefert; diese Daten stammen nicht von OpenStreetMap.

## Grafik und Sound

Die vom Nutzer bereitgestellten Alpha-0.2-Grafiken werden erhalten. Der Ausgangsprototyp bezeichnet sie als KI-generierte Original-Spielgrafiken; zusätzliche Fremdlizenzen oder eine unabhängig nachgewiesene Herstellungsgeschichte werden hierfür nicht behauptet.

Neue Fahrzeug-/Ausrüstungs-SVGs und zehn ausschließlich mathematisch synthetisierte WAVs sind Originale dieses Projekts, **CC0 1.0 Universal**. Synthesequelle: [generate_audio.py](tools/generate_audio.py). Ein zusätzliches fiktives MEK-Gruppenbild wurde eigens mit OpenAI generiert; keine reale Person oder fremde Bildvorlage. Details, Dateiliste und Nutzungsbedingungen: [Asset-Herkunft](docs/ASSET_PROVENANCE.md). UI-Schriften sind Systemfonts, keine eingebetteten Fremdschriftdateien.

## Laufzeitbibliotheken

- **lz-string 1.5.0**, Copyright © 2013 Pieroxy, MIT. [Vollständige Lizenz](licenses/lz-string.txt), <https://github.com/pieroxy/lz-string>. Verlustfreie lokale Spielstandkompression.
- **AndroidX Activity 1.9.3, AndroidX WebKit 1.12.1** und transitive AndroidX-Komponenten, Copyright The Android Open Source Project, Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>. HTTPS-AssetLoader, Lifecycle und Activity Results. Versionen sind in `app/build.gradle.kts` festgelegt; vollständiger Abhängigkeitsbaum: `./gradlew :app:dependencies`.
- **Kotlin Standard Library 2.0.21**, JetBrains, Apache License 2.0: <https://github.com/JetBrains/kotlin>.

Der Apache-2.0-Lizenztext und diese Hinweise werden zusätzlich im APK-Asset `licenses.html` mitgeliefert. Gradle Wrapper 8.9 stammt aus dem offiziellen Gradle-Repository, Apache-2.0; seine Lizenz liegt im JAR und in den Skript-Headern. Node-Werkzeuge (TypeScript, Vite, Playwright, tsx, Prettier) sind Entwicklungsabhängigkeiten, im Lockfile festgelegt und werden nicht als Laufzeitpakete mit der APK verteilt.
