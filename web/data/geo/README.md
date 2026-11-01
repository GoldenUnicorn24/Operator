# Offline-Kartenquellen

`germany-states.json` enthält alle 16 Länder einschließlich Inseln und Exklaven
als WGS84-Polygon/MultiPolygon. Quelle: **© GeoBasis-DE / BKG 2021**,
NUTS250, bereitgestellt in **geoBoundaries gbOpen**, DEU ADM1
(`DEU-ADM1-10402087`). Quelle und SHA-256 sind in `source.json` festgehalten.

Lizenz: **Datenlizenz Deutschland – Namensnennung – Version 2.0**,
<https://www.govdata.de/dl-de/by-2-0>.
geoBoundaries: <https://www.geoboundaries.org/>;
Datenpaket: <https://github.com/wmgeolab/geoBoundaries/tree/f6920d43375832a91201bdb7954a200beaa40202/releaseData/gbOpen/DEU/ADM1>.

Änderungen: Attribute reduziert, Koordinaten auf fünf Nachkommastellen gerundet.
Die bereits vereinfachte Quellgeometrie bleibt einschließlich aller Komponenten
erhalten. Kein amtliches Navigationsprodukt; die Küsten- und Verwaltungsgrenzen
entsprechen dem Quellenstand 2021. Landesgrenzen werden zur Anzeige zusätzlich
zoomabhängig vereinfacht; Standortvalidierung nutzt stets die vollständige
mitgelieferte Geometrie. Die Kartendarstellung benutzt eine Mercatorprojektion.

Städtenamen und Stadtmittelpunkte sind redaktionell zusammengestellte geografische
Fakten, keine Standortdaten echter Polizeidienststellen. Alle Polizeistützpunkte
im Spiel sind ausdrücklich fiktiv. Gestrichelte Fahrkorridore und Dispositionsrouten
zeigen eine **Simulation** (Großkreisdistanz × 1,28 / Fahrzeuggeschwindigkeit),
keine behauptete Straßenführung oder aktuelle Verkehrsdaten.

`hamburg-roads.json`: **Hauptverkehrsstraßen Hamburg**, WFS der Freien und
Hansestadt Hamburg. Quellenvermerk gemäß GetCapabilities: **Freie und Hansestadt
Hamburg, Behörde für Wirtschaft, Verkehr und Innovation**. Lizenz: ebenfalls
**Datenlizenz Deutschland Namensnennung 2.0**. 3.735 LineString-Features am
2026-11-01 abgerufen; Download-URL und SHA-256 in `hamburg-roads-source.json`.
Attribute reduziert, Koordinaten auf fünf Nachkommastellen gerundet, Linien mit
Douglas–Peucker bei 0,000035° Toleranz vereinfacht. Der Dienst erklärt in
GetCapabilities „Es gelten keine Zugriffsbeschränkungen“.

In Hamburg nutzt die Fahrnäherung, falls beide Punkte an den Hauptstraßengraphen
angebunden werden können, einen A*-Weg mit tatsächlicher Liniendistanz und
kurzen Zufahrtsverbindern. Der Graph wird als bidirektionale **Spielnäherung**
verwendet: keine Abbiegebeschränkungen, keine Verkehrs-/Baustellendaten. Auf
Neuwerk, bei fehlender Verbindung oder außerhalb Hamburgs gilt das ausdrücklich
deklarierte Distanzmodell. **Keine echte Navigation.**

Die Daten werden mit der App ausgeliefert. Start, Gameplay und Builds benötigen
keinen Kartenserver und laden keine öffentlichen OSM-Kacheln herunter.

Reproduktion: `python tools/import_geography.py` lädt die gepinnte Quelle und
erzeugt das kompakte Asset. Normale App-Builds verwenden das eingecheckte Asset.
Straßendaten neu erzeugen: WFS-URL aus `hamburg-roads-source.json` als GeoJSON
herunterladen, dann `python tools/import_geography.py --hamburg-roads DATEI`.
