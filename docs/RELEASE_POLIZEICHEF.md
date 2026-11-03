# Einsatzleitstelle 110 – Polizeichef 0.11.0

Native Offline-Vorschau, **kein v1.0**. Geprüfter Quellstand `960bfa1` mit [grüner CI](https://github.com/GoldenUnicorn24/Operator/actions/runs/41577783048): **112 JVM-Tests, 9 Python-Verträge, Lint, APK-Prüfung und acht Android35-Gerätetests**. [Abnahmeliste und Grenzen](https://github.com/GoldenUnicorn24/Operator/blob/codex/operator-v1-polizeichef/docs/ACCEPTANCE_POLIZEICHEF.md).

- **P1:** isolierte Hamburger Bezirkskarten mit Wohnstraßen, Namen, gerichteten Teilstrecken/Ringstraßen und Straßenadressen; Außenmaske, geklemmte Kamera, Wasser/Grün/Bahn. Hauptnetz für landesweite Fahrzeuge bleibt erhalten.
- **P2:** gespeicherte Streifenaufträge, Präsenz/Eigenfeststellungen, Verbrauch/Kraft, wählbare Sonderrechte, FMS-Funkstatus und individuelle Fahrzeugzeichen.
- **P3:** eigene fiktive Dienststellen mit Ausbau, neun Fahrzeugmodelle, getrennte Bewerber/Personal/Besatzung, Dienstgruppen/-zeiten, eigene Einheitentypen und tägliche Kosten mit Insolvenz-Warnung.
- **P4:** Verkehrs-/Wetterspielwerte, Mehrfachanrufer/Rückruf mit echter Adresskorrektur, fünf Save-Slots/Backups, Querformat-Akte und eigene Bildschirmmodule.

[26 echte Screenshots](https://github.com/GoldenUnicorn24/Operator/tree/codex/operator-v1-polizeichef/docs/evidence/polizeichef), einschließlich Dienststellengründung per langem Kartendruck, zwei Fahrzeugen/vier Beamten, erfolgreichem Einsatz und dauerhaftem Speichern/Laden.

APK: **17581366 Bytes**, SHA256 `91cdb34d2e0cb15e8b659ff9ecc853cce5176757084305610c75111d6ed42aeb`. Ohne WebView, Web-Dokumente, Internetberechtigung oder Kartenserver. ODbL-Daten offen unter `data/osm/`; Quellen in THIRD_PARTY_NOTICES.md. Eigene Canvas-Grafiken und erhaltene Originalassets.

Grenzen: Emulatornachweis, keine physische Geräte-/Hörabnahme oder menschliches Langzeitbalancing; keine Abbiegeverbote/Echtzeitdaten/Gebäudeumrisse. Vorhandene Einheiten bleiben kompatibel, neue Beschaffungen verwenden die Polizeichef-Verwaltung. Schema7 migriert bisher unterstützte Saves. **Debug-APK: bei abweichender Signatur vorher den Spielstand exportieren.** Berlin-/Bremen-Bezirke bleiben optional.
