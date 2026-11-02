# Quellen und Lizenzen der nativen Fassung

## Offline-Karten

**Landesgrenzen:** © GeoBasis-DE / BKG2021, NUTS250 über geoBoundaries gbOpen DEU ADM1. Datenlizenz Deutschland Namensnennung2.0, https://www.govdata.de/dl-de/by-2-0. Gepinnte Quelle und SHA-256: `app/src/main/native_assets/sources/states-source.json`. Attribute reduziert, Koordinaten auf fünf Nachkommastellen gerundet; Inseln/Exklaven und Polygonlöcher erhalten.

**Hamburger Bezirke:** amtlicher WFS Verwaltungsgrenzen/ALKIS, Freie und Hansestadt Hamburg, Landesbetrieb Geoinformation und Vermessung. Der WFS nennt unter Fees ausdrücklich „Datenlizenz Deutschland Namensnennung2.0, Quellenvermerk: Freie und Hansestadt Hamburg, Landesbetrieb Geoinformation und Vermessung“. GML-GetFeature für `app:bezirke`, Abruf2026-11-01. Sieben Bezirke mit allen Ringen, auf fünf Nachkommastellen gerundet. Quelle, Änderungen und SHA-256 unter `sources/districts-source.json` im Assetverzeichnis.

**Hauptstraßen Hamburg:** Freie und Hansestadt Hamburg, Behörde für Wirtschaft, Verkehr und Innovation, WFS Hauptverkehrsstraßen, Datenlizenz Deutschland Namensnennung2.0.3.735 Straßenfeatures, Attribute reduziert/gerundet/vereinfacht. Quellenmetadaten: `sources/roads-source.json`.

**Landesweite Straßen und Orte (seit V1-Entwicklungsstand, Claude):** © OpenStreetMap-Mitwirkende, https://www.openstreetmap.org/copyright. Lizenz: Open Database License (ODbL) 1.0, https://opendatacommons.org/licenses/odbl/1-0/. Einmaliger Auszug je Bundesland über die öffentliche Overpass-API (Autobahnen, Bundes-/Hauptstraßen; in Stadtstaaten zusätzlich Neben-/Stadtstraßen; Städte/Orte mit Einwohnerzahl), erzeugt durch `tools/build_osm_roads.py` in GitHub Actions, vereinfacht und an Kreuzungen geteilt. Die abgeleitete Datenbank liegt offen unter `app/src/main/native_assets/geo/roads-*.json` und steht ebenfalls unter ODbL 1.0. Keine Tile-Server, kein Laufzeitabruf: die App bleibt ohne Internetberechtigung.

**Gewässer, Siedlungsflächen, Übersichtsstraßennetz:** Natural Earth 1:10m (gemeinfrei, https://www.naturalearthdata.com/about/terms-of-use/), erzeugt durch `tools/build_geo_base.py` als `geo/germany-base.json`.

Fahrzeiten und Routen auf diesen Netzen sind Spielnäherungen (keine Einbahnstraßen, Abbiegeverbote oder Verkehrslage). Fehlt für eine Strecke ein Netzanschluss, zeigt die Karte gestrichelt eine ausdrücklich gekennzeichnete Distanznäherung; Hubschrauber fliegen direkt.

Routen sind ausdrücklich Simulationsnäherungen. Keine echte Navigation und keine heruntergeladenen OSM-Tiles im nativen Spiel. Der ehemalige optionale Online-Tileadapter bleibt nur unter `prototype/previous-native/` als Referenz erhalten und wird nicht kompiliert. Die App besitzt keine Internetberechtigung.

Früher mitgelieferte, inzwischen inaktive Geometrie aus isellsoap/deutschlandGeoJSON: Unlicense, ursprünglicher Anbieter GIS-DATA/diva-gis.org. Diese Quelle bleibt als Repository-Referenz erhalten; die aktuelle native Karte nutzt die oben genannten amtlichen Daten.

## Originalgrafiken

Die vier Atlanten `art/gear-atlas.png`, `art/portrait-atlas.png`, `art/vehicle-atlas.png`, `art/vehicle-extras-atlas.png` wurden eigens am2026-11-01 mit dem OpenAI-Bildgenerator aus Textbeschreibungen erzeugt. Keine fremde Bildvorlage, keine Assets eines bestehenden Spiels, keine realen Personen. Acht fiktive Beamtenporträts, zehn fiktive Fahrzeugansichten,16 Inventarmotive. Die Atlanten bleiben als Originaldateien erhalten; einzelne Ansichten werden in Android nativ als Bitmapbereiche gerendert. Bildhashes unter `sources/art-source.json`. Die Nutzung richtet sich nach den geltenden OpenAI-Nutzungsbedingungen; keine fremde CC-Lizenz oder Herstellerpartnerschaft wird behauptet. Waffennamen dienen der Beschreibung abstrahierter Spielgegenstände, nicht einer Produktzertifizierung.

Die ursprünglichen Alpha-0.2-Bilder stammen aus der vom Nutzer bereitgestellten Referenz, welche sie als KI-generierte Spielgrafiken bezeichnet. Sie bleiben für Rückwärtskompatibilität und einzelne Hintergrundansichten erhalten. `art/mek_team.webp` ist eine zusätzliche frühere eigene OpenAI-Generierung aus diesem Arbeitsauftrag.

Die Kartenfahrzeuge sind eigenständige native Canvas-Zeichnungen; keine kopierten Symbole, UI oder Sounds aus 911Operator/112Operator.

## Audio und Bibliotheken

Zehn WAVs sind eigens mathematisch synthetisierte Originale ohne Funkmitschnitte oder Fremdsamples. Quelle: `tools/generate_audio.py`; Dateien unter `app/src/main/native_assets/audio/`. Diese zehn WAVs stehen unter CC01.0 Universal (https://creativecommons.org/publicdomain/zero/1.0/). Die Engine nutzt Android SoundPool und AudioFocus.

Kotlin Standard Library2.0.21: JetBrains, Apache License2.0. Android SDK: Android Open Source Project. Der Apache-Lizenztext wird in der App unter „Quellen und Lizenzen“ mitgeliefert. AndroidX Test, JUnit4 und org.json20240303 dienen nur der Verifikation und werden nicht als Testbibliotheken in die Spiel-APK gepackt. Gradle-Wrapper8.9 ist Apache-2.0-lizenziert; Lizenz im JAR und in den Skript-Headern. Kein Release-Signierschlüssel im Repository.
