# Quellnachweise und Lizenzen — Einsatzleitstelle 110 Native V1

## OpenStreetMap-Straßenkarte (optional)
- Daten, gerenderte Tiles: **© OpenStreetMap contributors**; Datennutzung nach ODbL.
- Tiles werden bei aktiver Kartenansicht optional per HTTPS von https://tile.openstreetmap.org/{z}/{x}/{y}.png abgerufen.
- Policy: https://operations.osmfoundation.org/policies/tiles/
- App verwendet identifizierenden User-Agent, lediglich aktuell sichtbare Tiles, maximal zwei Downloadthreads, eine lokale Cache-Gültigkeit von mindestens sieben Tagen, keine Massendownloads und keine Offline-Prefetch-Funktion.
- Die Kachelserver sind Community-finanziert, ohne SLA. Für eine umfangreich verteilte oder kommerzielle Veröffentlichung muss eine skalierbare, entsprechend lizenzierte Alternative bzw. eigener Tile-Server vorgesehen werden.
- Wird das Netz deaktiviert / fehlt Internet, funktioniert weiterhin die native Offline-Gesamtkarte mit Spielobjekten und vereinfachtem Wegenetz.

## Bundesland-Grenzgeometrie
- Die im Spiel vorliegenden vereinfachten Grenzen basieren auf dem ursprünglichen Datensatz aus **isellsoap/deutschlandGeoJSON**, Quelle laut Projekt **GIS-DATA / DIVA-GIS**: https://github.com/isellsoap/deutschlandGeoJSON
- Diese Quelldaten sind für eine nichtkommerzielle, private Alpha-/V1-Testentwicklung verwendet. Die exakte Lizenz-/Weitergabefähigkeit der ursprünglichen Datenquelle muss **vor öffentlicher kommerzieller Distribution** endgültig geprüft oder durch klar lizenzierte Verwaltungsgrenzen ersetzt werden.
- Die 16 Bundeslandkonturen sind vereinfacht und nicht für Kataster- oder Leitstellenentscheidungen geeignet.

## Personenbilder und Ausrüstungsgrafiken
- Aus der zuvor für das Projekt generierten Alpha 0.2 übernommen: fiktive Beamtenporträts, Einsatz-/Fahrzeugillustrationen und SVG-Ausrüstungsgrafiken unter \`app/src/main/assets/images/\`.
- Keine echten Personen oder realen Polizeiinternen Inhalte im Spiel.
- Vor wirtschaftlicher Veröffentlichung sämtliche Nutzungsrechte der erzeugten Assets und ihrer etwaigen Quellen kontrollieren.

## Sounds
- Alle nativen Alarm-/UI-Töne werden zur Laufzeit **original durch Kotlin/AudioTrack synthetisiert**, ohne fremde Audio-Samples.

## Abgrenzung
Eine unabhängig entwickelte fiktionale Managementsimulation. Weder \`911 Operator\` noch \`112 Operator\` stellt Assets, Logos, Interface oder Sound dieses Spiels bereit.
