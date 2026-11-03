# Auftrag Teil 2 – automatisierte Abnahme 0.11.0

Geprüfter Quellstand `960bfa1`: [CI 41577783048](https://github.com/GoldenUnicorn24/Operator/actions/runs/41577783048) vollständig grün. **112 JVM-Tests, 9 Python-Vertragstests, beide APKs, Lint, native Paketprüfung und Android35 `OK (8 tests)`.** 26 echte Gerätescreenshots mit [Commit-/Laufzuordnung](evidence/polizeichef/SOURCE.txt). Nachfolgende Beleg-/Dokumentationscommits ändern keinen App-Code. Kein v1.0.

[APK herunterladen](https://github.com/GoldenUnicorn24/Operator/releases/download/operator-polizeichef-960bfa1/Einsatzleitstelle110-Native-Leitstelle.apk) · [Release](https://github.com/GoldenUnicorn24/Operator/releases/tag/operator-polizeichef-960bfa1) · [PR #5](https://github.com/GoldenUnicorn24/Operator/pull/5)

| Meilenstein | Bestätigter Ablauf | Nachweis |
|---|---|---|
| P1 Bezirkskarte | Hamburg-Detailnetz/R2, Namen und Einbahnrichtungen einschließlich Teilstrecken und Ringstraßen, Maske/Kameragrenzen, generierte Straßenadressen. Bezirkslogik für alle sieben Hamburger Bezirke; visuelle Geräteabnahme in Wandsbek. | `NativeMapFormatTest`, `NativeDirectedRoutingTest`, `NativeNetworkSelectionTest`, `NativeClosedRoadTest`, `NativeDistrictCameraTest`, `NativeOperationsAreasTest`; [Übersicht](evidence/polizeichef/native-wandsbek-overview.png), [Straßennamen](evidence/polizeichef/native-wandsbek-street-names.png), [fahrendes Fahrzeug](evidence/polizeichef/native-wandsbek-oneway-drive.png) |
| P2 Streife | Wache/Gebiet/Schwerpunkt/Kontrollstelle, gerichtete Rundkurse ohne Sackgassen-Dauerschleifen, Präsenz/Eigenfeststellungen, Verbrauch/Kraft, Sonderrechte und FMS. Fahrzeugzeichen auf jeder Zoomstufe. | `NativePatrolTest`, `NativePatrolScopeTest`; [Streife](evidence/polizeichef/native-wandsbek-patrol.png), [mit/ohne Blaulicht](evidence/polizeichef/native-signal-comparison.png), [Funkstatus 1/3/4](evidence/polizeichef/native-fms-radio.png) |
| P3 Polizeichef | Echte Kartenplatzierung per langem Druck → fiktive Dienststelle → zwei Fahrzeuge → vier Beamte → Besetzung → erfolgreicher Einsatz → dauerhafte Datei → Activity-Neustart. Ausbau, eigene Typen, Dienstgruppen/-zeiten und Tageskosten zusätzlich in JVM-Tests. | `NativePoliceChiefTest`, `NativeChiefScheduleTest`; [Dienststelle](evidence/polizeichef/native-chief-station.png), [Fuhrpark](evidence/polizeichef/native-chief-fleet.png), [Personal](evidence/polizeichef/native-chief-personnel.png), [nach Laden](evidence/polizeichef/native-chief-reloaded.png) |
| P4 Realismus/Bedienung | Verkehrs-/Wetterspielwerte, zusammengeführte Anrufer, Rückruf mit Adresskorrektur/Umleitung, fünf atomare Slots/Backups, Querformat-Akte und ausgelagerte Bildschirmmodule. | `NativeConditionsTest`, `NativeSaveSlotsTest`; [Rückruf](evidence/polizeichef/native-caller-callback.png), [Speicherplätze](evidence/polizeichef/native-save-slots.png), [Querformat](evidence/polizeichef/native-landscape-dispatch.png) |

## Paket und Leistung

CI-APK **17,581,366 Bytes** (rund 17.6 MB; Ziel <40 MB). SHA256 `91cdb34d2e0cb15e8b659ff9ecc853cce5176757084305610c75111d6ed42aeb`. APK ohne Web-Inhalte/WebView/Internetberechtigung; Detaildaten R2, übrige Landesdateien rückwärtslesbar R1. Offene ODbL-Kopie unter `data/osm/`. Keine Gebäudeumrisse.

[Unveränderte Emulator-Messwerte](evidence/polizeichef/perf-emulator.txt):

```text
DE-BY Straßengraph: 478 ms, 63658 Knoten
DE-BY Region gewählt bis Grundkarte fertig: 3322 ms
DE-BY Live-Overlay (Software-Canvas, 48 Einheiten, 3 Lagen): Ø 23.7 ms je Bild
DE-NI Straßengraph: 361 ms, 42057 Knoten
DE-NI Region gewählt bis Grundkarte fertig: 2659 ms
DE-NI Live-Overlay (Software-Canvas, 48 Einheiten, 4 Lagen): Ø 16.9 ms je Bild
P1 Wandsbek Übersicht bis Grundkarte: 1272 ms
P1 Einbahn-Wohnstraße: Weg 1, Richtung 1, 7 Routenpunkte, 2 min
P1 Straßenebene bis Grundkarte: 1116 ms
P1 Detail-Overlay 60 Bilder: Ø 17.112109149999995 ms, p95 17.384251 ms, max 43.024921 ms; Bitmap 8320320 bytes
```

Die Overlaywerte messen 60 Software-Canvas-Zeichnungen mit bereits gerendertem Hintergrund; die Grundkartenwerte sind gemessene Wartezeiten bis zur fertigen Hintergrundkarte. Das sind **keine physischen Gerätewerte und kein allgemeiner FPS-/Langzeit-Stresstest**.

## Grenzen und Kompatibilität

- Keine physische Geräte-/Hörabnahme oder menschliches Langzeitbalancing. Kein v1.0-Fertiglabel.
- Fahrzeiten, Wetter, Kosten und Ressourcen sind fiktive Spielwerte; keine Abbiegeverbote oder Echtzeitdaten. Fehlende Straßenverbindungen bleiben als Distanznäherung markiert. Landesweite Hamburger Fahrzeuge verwenden das Hauptnetz, Bezirksfahrzeuge das Detailnetz.
- Vorhandene Einheiten behalten ihr kompatibles bisheriges Crew-/Profilmodell. Neue Polizeichef-Beschaffungen besitzen unabhängige Personalreserve und Fuhrparkverwaltung; Versetzungen zwischen diesen Modellen werden ausdrücklich abgefangen, statt Personal zu verlieren.
- Save-Schema 7 liest Schemas 5/6 und die bereits unterstützten älteren Migrationen. Slots ergänzen die bestehende automatische Sicherung. Bei Wechsel zwischen unterschiedlich signierten Debug-APKs vor Installation exportieren; Release-Signing ist nicht eingerichtet.
- Berlin-/Bremen-Bezirke sind optional und nicht umgesetzt. Alle Dienststellen, Personen und Lagen sind fiktiv; keine reale taktische Anleitung.
