# Auftrag Teil 2 – Abnahmestand

Kein v1.0. Kriterien gelten erst nach frischen Nachweisen des angegebenen Commits als bestätigt. JVM-Simulation ersetzt keinen menschlichen Spieltest.

| Meilenstein | Implementierung | Nachweis / ausstehend |
|---|---|---|
| P1 Bezirkskarte | Alle sieben Hamburger Bezirke, Detailnetz/R2, Maske/Kamera, Namen, gerichtete Teilstrecken, Straßenpunkte für generierte Notrufe | Python- und JVM-Verträge vorhanden; Android-Übersicht, Straßenebene, fahrendes Fahrzeug und Messwerte werden in CI erfasst |
| P2 Streife | Gebiet/Schwerpunkt/Kontrollstelle/Wache, Präsenz, Eigenfeststellungen, Verbrauch/Kraft, Sonderrechte, FMS, individuelle Symbole | JVM-Rundkurs-/Richtungs-/Status-/Signaltests; Android-Streifen-/Signal-/Funkbilder in CI |
| P3 Polizeichef | Mehrere fiktive Dienststellen, Ausbau/Kosten, neun Modelle, unabhängiges Personal, eigene Typen, Tagesrechnung | JVM-Ablauf Wandsbek→2 Fahrzeuge→4 Beamte→Einsatz→Save; Android mit echter Datei und Activity-Neustart in CI |
| P4 Realismus/Bedienung | Verkehr/Wetter, Anrufer/Rückruf, 5 Slots, Querformat, eigene Bildschirmmodule | JVM-Bedingungs-/Anrufer-/Dateisystemtests; Slots/Rückruf/Querformat-Screenshots in CI |

Verbleibende Grenzen: Keine Abbiegeverbote oder Echtzeitverkehrsdaten. Fahrzeug-/Personalwerte sind fiktive Spielwerte. Kein realer Polizeieinsatzablauf. Keine Gebäudeumrisse. Fehlende Straßenverbindungen werden als Distanznäherung markiert. Messungen gelten nur für den Android-35-Emulator; physische Geräte, Hörabnahme und menschliches Langzeitbalancing bleiben offen. Berlin/Bremen-Bezirke sind optional und nicht umgesetzt.

Veröffentlichung erst nach grünem Build, allen bestehenden und neuen JVM-Tests, Lint, APK-Prüfung und acht Android-Instrumentationstests. Screenshots unter `docs/evidence/polizeichef/` mit SOURCE.txt. APK-Budget: Ziel <40 MB, tatsächlichen Paketwert aus `package-audit.json` des verifizierten Releases entnehmen.
