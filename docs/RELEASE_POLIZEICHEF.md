# Polizeichef – Entwicklungsstand

Kein v1.0. Arbeitsbranch `codex/operator-v1-polizeichef`, Basis 0.10.0-v1-kandidat.

P1: Hamburg-Detailnetz aus einmaliger OSM-Abfrage in CI, Wohnstraßen, Namen getrennt von Referenzen, Einbahnrichtungen, Wasser-/Grün-/Bahnflächen. Alte R1-Kartendateien bleiben lesbar. Bezirksansicht mit heller Grundkarte, Außenmaske, geklemmter Kamera, Stadtteil-/Straßenbeschriftung. Landesübersicht bleibt erreichbar. Generierte Notrufe auf benannten Straßenpunkten; keine erfundenen Hausnummern.

Das APK wird erst nach erfolgreichem Build, JVM-Tests, Lint, Paketprüfung und Android-35-Gerätetest veröffentlicht. Echte Screenshots und Emulatorwerte unter `docs/evidence/polizeichef/`, referenziert auf ihren geprüften Commit. Vorhandene Nachweise anderer Branches sind keine Abnahme dieses Standes.

Grenzen: Fahrzeiten sind Spielwerte; keine Abbiegeverbote oder Echtzeitdaten. Fehlende Verbindungen bleiben ausdrücklich markierte Distanznäherungen. Keine Gebäudeumrisse. P2–P4 werden separat dokumentiert; bis dahin keine Vollabnahme des Gesamtauftrags. Physische Geräte und menschliches Balancing nicht geprüft.

Daten: © OpenStreetMap-Mitwirkende, ODbL, offene Kopie `data/osm/`; weitere Quellen siehe THIRD_PARTY_NOTICES.md. Keine Internetberechtigung oder Kartenserver.

P2 ergänzt gespeicherte Streifenaufträge, Präsenz-/Kostenwirkung, Sonderrechte und FMS-Funkmeldungen. P3 ergänzt fiktive Dienststellen mit Ausbau, neun Fahrzeugmodelle, getrenntes Personal mit Dienstgruppen, eigene Einheitentypen und laufende Kosten. P4 ergänzt Verkehrs-/Wetterspielwerte, Mehrfachanrufer und Rückrufe, fünf Save-Slots, Querformat-Akte und getrennte UI-Module. Das Spielstandschema 7 liest die bisherigen Schemas und Migrationen weiter. Der klassische frei konfigurierbare Einheiteneditor bleibt in der Sandbox verfügbar; Karrierebeschaffung erfolgt über den Polizeichef.
