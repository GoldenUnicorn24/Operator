# V1-Entwicklungsstand (Claude) – Meilenstein M1 · 02.11.2026

Branch `claude/operator-v1-gameplay`, Basis `codex/operator-v1` (0.9.0-native-preview). **Kein v1.0.**

**Live-Karte:** native Offline-Vektorkarte für alle 16 Länder mit Straßenhierarchie (OSM, ODbL), Gewässern und Siedlungsflächen (Natural Earth), Nachbarländern, Ortsnamen nach Einwohnerzahl und Hamburger Bezirksgrenzen; Hintergrund-Rendering, fingerzentrierter Pinch- und Doppeltipp-Zoom, Zurück zur Übersicht, Kerngebiet vs. ganzes Land inkl. Exklaven, dunkles Leitstellen-Design standardmäßig, Legende, Minikarte.
**Routing:** A* und Dijkstra-ETA auf dem Straßennetz jedes Landes, Straßenklassen-Geschwindigkeiten, Hubschrauber im Direktflug; fehlende Netzanschlüsse als Distanznäherung gekennzeichnet.
**Fahrzeuge:** gleiten zwischen Simulationsticks entlang der Route mit Fahrtrichtung, Blaulicht, Restzeit; Wachen mit frei/gesamt.
**Disposition auf der Karte:** Einsatzakte als Panel über der Karte, Mehrfachauswahl mit Fahrzeit/Eignung/Besatzung/Kraft, Routenvorschau, Verstärkung, Abziehen, Umdisponieren gebundener Kräfte, Alarmierung von Einheiten auf Rückfahrt.
**Spielablauf:** tageszeitabhängiges Notrufaufkommen, bevölkerungsgewichtete Einsatzorte, fairer abwechslungsreicher Schichtbeginn, Countdown, Funkmeldungen; erklärbarer Einsatzausgang mit gespeicherter Begründung, Bilanz und Ø-Eintreffzeit im Tagesbericht; neue Spielstände verteilen Kräfte auf Wachen in den größten Orten.
**Nachweise:** 35 JVM-Tests (u. a. komplette Hamburger 8h-Schicht auf echten Straßen), 4 Android-Instrumentationstests auf API 35 mit Screenshots unter `docs/evidence/claude-m1/`.

# Release Notes – Geo-Alpha 0.4 (01.11.2026)

**Umgesetzt:** 16 real begrenzte, geografische Landesgesamtansichten; 100+ Orts-Pins; räumlich verteilte Fahrzeuge und Notrufe; Zoom/Pan/Filter/Kartenmarker; simulierte fahrende Fahrzeuge mit Zeit und ETA; 8-/12-/24-Stunden-Dienste mit frei gewählter Anfangsstunde und Mitternachtsübergang; 48 Einsätze, 8 Kurse; 19 Fahrzeuge/Einheiten; Fahrzeugmodell, Wartung und Zustand; optische Kartenerneuerung und 9 originale WebAudio-Signale.

**Erhalten:** Karriere, Sandbox, Schichtende, Spieltage, Personalporträts, Ausrüstung, Ausbildung, Eingabeeditoren, Import/Export, lokale Speicherung.

**Nicht umgesetzt:** echte Straßentiles/Live-Google-Karten, Straßenrouting, offizielle Wachenstandorte oder 100-prozentige v1.0-Funktionsabdeckung. Die Landeskarte verwendet vereinfachte Grenzpolygone und Ortskorridore. Kein Simulator für reale Polizeieinsätze.

**Technischer Test:** Node-Logiktest: 16 Länder, 48 Einsätze, 8 Kurse, drei Schichtdauern, Schichtabschluss/Tagwechsel, Training, simulierte Bewegung. Chromium-UI-Smoketest für 24h-Schicht und Regionenauswahl. GitHub-Actions-APK folgt aus CI.
