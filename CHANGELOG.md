# V1-Entwicklungsstand (Claude) – Meilenstein M2 · 02.11.2026

**Mehrstufiges Gameplay:** 34 ausgearbeitete Einsatzabläufe auf den bestehenden Vorlagen (alle 48 bleiben erhalten) mit Anruferdialog und Entscheidungsphasen *Anrufannahme → Lage vor Ort → Lageänderung*. Jede Option hat eigene, vor dem Tippen sichtbare Folgen (Risiko, Zeit vor Ort, Frist, Nachforderung, Priorität, Kosten, Ruf, Folgeermittlung); Optionen können an zugewiesene Kräfte, Ausrüstung (z. B. Drohne, Bodycam) oder Lehrgänge (z. B. Deeskalation, Einsatzführung) gebunden sein und zeigen gesperrt den Grund. Die übrigen 14 Vorlagen haben eine generische Anrufannahme mit drei unterschiedlichen Strategien.
**Konsequenzen:** unbeantwortete Entscheidungen nach 12 Spielminuten automatisch mit Standard und Risikoaufschlag; Nachforderungen öffnen den Einsatz wieder (Abschluss nach 45 Min Wartezeit mit erhöhtem Risiko); Folgeeinsätze nach Erfolg, Fehlschlag oder getroffener Entscheidung (z. B. Observation → Auswertung/Durchsuchung). Uhr läuft bei offener Entscheidung höchstens ×4.
**SEK/MEK/Hubschrauber:** Einsatzpauschalen (4.200/2.100/1.500 €), Erholung nach dem Einsatz (150/90/60 Min, auch auf der Rückfahrt nicht alarmierbar), begrenzte Anzahl, eigene Optionen (SEK-Lösung vs. Verhandlung, MEK-Beobachtung mit Folgeermittlung); Zweckentfremdung für Routinelagen wird vermerkt und kostet Ruf; Budgetablehnung wird erklärt.
**Balancing:** Rufverlust nach Priorität (P1 −5, P2 −3, P3 −1), P1-Erfolg +2; Begründung des Ausgangs nennt Entscheidungen; Tagesbericht mit Spezialkräfte-Kosten und eigenen Entscheidungen.
**Nachweise:** 47 JVM-Tests (11 neu, u. a. Katalogprüfung: keine zwei Optionen mit gleicher Wirkung), Android 35 `OK (4 tests)` in Lauf 41385064751.

# V1-Entwicklungsstand (Claude) – Meilenstein M1 · 02.11.2026

Branch `claude/operator-v1-gameplay`, Basis `codex/operator-v1` (0.9.0-native-preview). **Kein v1.0.**

**Live-Karte:** native Offline-Vektorkarte für alle 16 Länder mit Straßenhierarchie (OSM, ODbL), Gewässern und Siedlungsflächen (Natural Earth), Nachbarländern, Ortsnamen nach Einwohnerzahl und Hamburger Bezirksgrenzen; Hintergrund-Rendering, fingerzentrierter Pinch- und Doppeltipp-Zoom, Zurück zur Übersicht, Kerngebiet vs. ganzes Land inkl. Exklaven, dunkles Leitstellen-Design standardmäßig, Legende, Minikarte.
**Routing:** A* und Dijkstra-ETA auf dem Straßennetz jedes Landes, Straßenklassen-Geschwindigkeiten, Hubschrauber im Direktflug; fehlende Netzanschlüsse als Distanznäherung gekennzeichnet.
**Fahrzeuge:** gleiten zwischen Simulationsticks entlang der Route mit Fahrtrichtung, Blaulicht, Restzeit; Wachen mit frei/gesamt.
**Disposition auf der Karte:** Einsatzakte als Panel über der Karte, Mehrfachauswahl mit Fahrzeit/Eignung/Besatzung/Kraft, Routenvorschau, Verstärkung, Abziehen, Umdisponieren gebundener Kräfte, Alarmierung von Einheiten auf Rückfahrt.
**Spielablauf:** tageszeitabhängiges Notrufaufkommen, bevölkerungsgewichtete Einsatzorte, fairer abwechslungsreicher Schichtbeginn, Countdown, Funkmeldungen; erklärbarer Einsatzausgang mit gespeicherter Begründung, Bilanz und Ø-Eintreffzeit im Tagesbericht; neue Spielstände verteilen Kräfte auf Wachen in den größten Orten.
**Nachweise:** 36 JVM-Tests (u. a. Landesrouting Niedersachsen/Bayern auf echten Daten) (u. a. komplette Hamburger 8h-Schicht auf echten Straßen), 4 Android-Instrumentationstests auf API 35 mit Screenshots unter `docs/evidence/claude-m1/`.

# Release Notes – Geo-Alpha 0.4 (01.11.2026)

**Umgesetzt:** 16 real begrenzte, geografische Landesgesamtansichten; 100+ Orts-Pins; räumlich verteilte Fahrzeuge und Notrufe; Zoom/Pan/Filter/Kartenmarker; simulierte fahrende Fahrzeuge mit Zeit und ETA; 8-/12-/24-Stunden-Dienste mit frei gewählter Anfangsstunde und Mitternachtsübergang; 48 Einsätze, 8 Kurse; 19 Fahrzeuge/Einheiten; Fahrzeugmodell, Wartung und Zustand; optische Kartenerneuerung und 9 originale WebAudio-Signale.

**Erhalten:** Karriere, Sandbox, Schichtende, Spieltage, Personalporträts, Ausrüstung, Ausbildung, Eingabeeditoren, Import/Export, lokale Speicherung.

**Nicht umgesetzt:** echte Straßentiles/Live-Google-Karten, Straßenrouting, offizielle Wachenstandorte oder 100-prozentige v1.0-Funktionsabdeckung. Die Landeskarte verwendet vereinfachte Grenzpolygone und Ortskorridore. Kein Simulator für reale Polizeieinsätze.

**Technischer Test:** Node-Logiktest: 16 Länder, 48 Einsätze, 8 Kurse, drei Schichtdauern, Schichtabschluss/Tagwechsel, Training, simulierte Bewegung. Chromium-UI-Smoketest für 24h-Schicht und Regionenauswahl. GitHub-Actions-APK folgt aus CI.
