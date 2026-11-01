# Anforderungs- und Prüfnachweis

Stand 2026-11-01; Anforderungen aus `STATEWIDE_MAP_AND_SHIFTS.md` haben Vorrang. „Bestätigt“ bezeichnet die angegebene ausgeführte Prüfebene, keine pauschale Gerätefreigabe. Das Release-Label v1.0 bleibt gesperrt, solange die Android-/Geräteabnahme nicht abgeschlossen ist.

| Kriterium | Implementierung | Frischer Nachweis | Status |
| --- | --- | --- | --- |
| Alle 16 echte Vollkarten, Fit und Inseln/Exklaven | `web/data/geo`, `web/map/regions.ts`, `view.ts` | `full_state_bounds_each_region` in Node und Browser; alle Boundingboxes innerhalb mobiler Canvas | Bestätigt im Browser |
| Landesweite Orte in korrekter Geometrie | `contains`, seeded `samplePosition`, Editorprüfung | `incident_within_state`, bekannte Orte, Polygonlöcher und Inseln | Bestätigt |
| Mindestens 30 Fahrzeuge/Orte, Cluster/Filter/Auswahl | 32 Fahrzeuge pro Land, Kategoriecluster, Liste aller Pins, Zoom-to-cluster | `cluster_open_and_filter` mit 300 Objekten; UI mit132 regionalen Fahrzeugen +20 Einsätzen | Bestätigt im Browser |
| Niedersachsen Hannover/Oldenburg/Lüneburg und Bayern München/Nürnberg | regionale Städte, Beispielnotrufe, vollständige Grenzen | UI-Workflow und `docs/evidence/*overview.png` | Bestätigt im Browser |
| Liveposition, Route, ETA, Pause, Regionerhalt | absolute Zeit, Fahrprofile, Rückfahrt, geografische Distanzinterpolation | `routing_cross_district`, `region_switch_retains_vehicles`, `manual_pause_freezes_all`, UI-Dispatch | Bestätigt im Browser |
| Hamburger Straßen und Details | amtliches Hauptstraßenasset, A*, Straßennamen, Detailkamera | `hamburg_main_road_graph`, Hamburger UI-Screenshot | Bestätigt; Straßennavigation bleibt Näherung |
| 8/12/24h in Karriere und Sandbox, alle Startstunden | `domain/clock.ts`, Schichtplanung | sechs Domänen- und sechs UI-Schichtfälle, 24 Startstunden, Nacht/Mitternacht | Bestätigt im Browser |
| Einmaliger Abschluss, Bericht, Gehälter, Folgetag | `finishShift`, `nextDay`, Absolutzeit | `midnight_once`, Schichtfälle, Karriere-End-to-End | Bestätigt im Browser |
| Übergabe und Personalrotation 24h | gespeicherte Fristen/Bearbeitung/Zuweisungen, drei Teams, Ruhe | `handoff_24h`, Ruhe-Regressionsfall ohne Ablösung, UI24h | Bestätigt im Browser |
| 40+ Vorlagen, Entscheidungen und Ketten | 48 Katalogeinträge, Zeit-/Risiko-/Budgeteffekte, Erfolg-/Fehlschlagzweige | Inhalts-/Engine-/Editor-/Reviewtests, UI-Kettenablauf | Bestätigt |
| Einheitenrollen SEK/MEK/Kripo und weitere | acht Rollen, Anforderungen, Bereitschaft, Fahrzeuge und Profilgrafiken | Inhalts- und Eignungstests, UI-Filter/Profile | Bestätigt im Browser |
| Personal, Ausrüstung, Einstellung, Fahrzeug-/Wachenverwaltung | Profile, Besitz/Slots, Rolle/Besatzung, Dienstplan/Ablösung, Beschaffung/Wartung/Ausbau | Inventar-/Fahrzeug-/Karriere-Freischalttests, UI-Ausrüstung/Fahrzeug | Bestätigt im Browser |
| Mindestens acht freiwillige mehrtägige Lehrgänge | zehn Kurse, einmalige Kosten, Qualifikation/Kompetenz | `training_by_completed_shift` für8/12/24h; UI über zwei volle Dienste | Bestätigt im Browser |
| Editoren und Import/Export mit echter Wirkung | Einsatz-/Ketten-/Einheiten-/Fahrzeugmodell, Tap-Position, JSON-Schema | Editorprüfungen, Verzweigung bei Fristablauf, Fahrzeugtempo bleibt nach Neustart | Bestätigt im Browser |
| Keine Codeausführung/keine Zerstörung bei Importfehler | sichere IDs, Zahlen, Referenzen, Zyklusprüfung, zuerst validieren | neun Reviewregressionen und Importtests, negatives UI-Importbeispiel | Bestätigt |
| Alpha-Daten erhalten, Slots, Backup, Neustart | Schema3, unveränderte alte Schlüssel, verlustfreie Kompression | authentisches Alpha-Fixture, aktive Zuordnung, drei Slots/alle Länder im Speicherbudget, Browserreload | Bestätigt auf JS-Ebene |
| Zehn originale Sounds, Mixer, Mute, Lebenszyklus | synthetisierte WAVs, Mixer, Host-Fokusereignisse | drei Audiotests inkl. Abbruch noch ladender Quellen, UI-Mute/Funkprobe | Bestätigt im Browser; Geräteklang separat abhören |
| Offline ohne CDN/Server/Pflichtkonto | sämtliche Ressourcen im APK; Host ohne INTERNET-Recht | Browser blockiert alle nichtlokalen Requests; Bundle und Lizenzassets lokal | Bestätigt im Browser |
| Android bauen/linten/installieren/starten | Wrapper8.9, SDK35, AssetLoader, SAF, AudioFocus, API26-IIFE | lokale `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` erfolgreich; Instrumentation/CI siehe Android-Bericht | Teilweise bis nativer Lauf abgeschlossen |
| Mobile/Landscape und Leistung | Touchflächen, responsive Ansichten, wiederverwendete Marker, Projektionscache | 412×915 und915×412 Screenshots; `browser-performance.json` | Bestätigt im Desktopbrowser; physisches Mittelklassegerät unbestätigt |
| Quellen/Lizenzen | THIRD_PARTY_NOTICES, Geodatenmetadaten, Synthesequelle, APK-Lizenzseite | Quellen/SHA-256 und lokale Assetreferenzen geprüft | Dokumentiert; Herkunft alter Alpha-Assets gemäß Nutzerquelle |

## Reproduzieren

```sh
npm ci
npm run format:check
npm test
npm run build:web
npx playwright install --with-deps chromium
npm run test:ui
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Die letzte Zeile braucht einen laufenden Android-Emulator. Ergebnisse und Grenzen werden im [Android-Bericht](ANDROID_VALIDATION.md) und [Implementierungslog](IMPLEMENTATION_LOG.md) ergänzt. Browsernachweise sind echte Screenshots aus dem gebauten Spiel, keine Bildmockups.
