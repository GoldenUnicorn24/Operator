# Polizeichef-Entwicklungsstand · 0.11.0 · 02.11.2026

P1: Hamburg mit Wohnstraßen, Einbahnrichtungen, getrennten Straßen-/Referenznamen und lokalen Wasser-/Grün-/Bahnflächen. Rückwärtslesbares Kartenformat R2, helle isolierte Bezirkskarte, geklemmte Kamera, Straßenbeschriftung und Straßenpunkte für neue Notrufe. Gerichtete Teilstrecken und ETA stimmen überein.

P2: Streifenrunden, Schwerpunkt/Kontrollstelle, Präsenzwirkung, Eigenfeststellungen, Sonderrechte mit Kosten/Risiko, FMS und individuelle Fahrzeugzeichen bei jeder Zoomstufe.

P3: Fiktive Dienststellen und Ausbau, Fahrzeugkatalog, getrennte Bewerber/Personal/Besatzungen, Dienstgruppen, eigene Einheitentypen, tägliche Kosten und Insolvenz-Warnung.

P4: Verkehr/Wetter als Spielwerte, Mehrfachanrufer/Rückruf mit Adresskorrektur, fünf atomare Speicherplätze, Querformat-Einsatzakte und ausgelagerte Bildschirmmodule. Save-Schema 7 migriert bestehende native und bereits unterstützte ältere Spielstände.

Nachweise und Grenzen: [docs/ACCEPTANCE_POLIZEICHEF.md](docs/ACCEPTANCE_POLIZEICHEF.md). Kein v1.0; finale Geräte-/CI-Nachweise werden mit geprüfter Commit-ID ergänzt.

# V1-Entwicklungsstand (Claude) – Meilenstein M4 · 02.11.2026 · Version 0.10.0-v1-kandidat

**Tutorial:** drei geführte Hamburger Einsätze (Disponieren, Entscheidung bei der Anrufannahme, Mehrfachauswahl) mit Hinweisbanner auf der Karte, danach freie Schicht; jederzeit überspringbar.
**Personal getrennt von Fahrzeugen:** Planstellen (Beamter samt Ablösungen) zwischen Fahrzeugen versetzen; Mindest- und Höchstbesatzung, unterbesetzte Fahrzeuge rücken nicht aus.
**Audio:** Signale entprellt (je Signal Mindestabstand, höchstens drei in zwei Sekunden), keine Überlagerung bei vielen Ereignissen.
**Kartendaten:** kompaktes Binärformat (23,3 MB JSON → 8,5 MB, Laden etwa 8× schneller); lesbare ODbL-Kopie in `data/osm`.
**Balancing Flächenländer:** Fristen wachsen mit der Landesgröße, Länder über 25.000 km² mit 48 Einheiten an 16 Wachen, Spezialkräfte in den drei größten Städten, Streife kann Verkehrsaufgaben übernehmen.
**Abnahme:** Schichten in Hamburg, Niedersachsen und Bayern auf echten Daten; eigenes Szenario erstellen → exportieren → importieren → speichern → am Folgetag spielen; Speichern mitten in einer 24h-Schicht über Mitternacht und identisches Weiterlaufen. 62 JVM-Tests, Android 35 `OK (4 tests)`, Emulator-Messwerte unter `docs/evidence/claude-m1/perf-emulator.txt`. Checkliste: `docs/ACCEPTANCE_CLAUDE_V1.md`.

# V1-Entwicklungsstand (Claude) – Meilenstein M3 · 02.11.2026

**Karriere:** vier Stufen (Leitstelle Hamburg → Regionalleitstelle Nord mit Niedersachsen/Schleswig-Holstein → Überregionale Leitstelle mit Bayern, Bremen, Mecklenburg-Vorpommern, Berlin → Lagezentrum Deutschland), jeweils an abgeschlossene Schichten, Ruf und ausgeglichenes Budget gebunden. Ruf muss täglich neu verdient werden (abnehmende Gewinne nahe 100, Routine-P3 ohne Rufgewinn, späte Ankunft kostet Vertrauen, nachts Annäherung an 72). Zuschüsse wachsen mit Stufe und Ruf. Gesperrte Länder mit Begründung, Karrierekarte mit Bedingungen, Beförderungsmeldung. Alte Spielstände behalten alle Länder.
**Loadout:** Slots je Kategorie (Dienstwaffe, Zusatzwaffe, Schutz ×2, Kommunikation ×2, Hilfsmittel ×3, Technik), 12-kg-Traglast, Kompatibilität (G36 nur SEK/MEK/Bereitschaft), Wachen-Lager mit Bestand (einkaufen, ausgeben, zurückgeben); jedes Teil nennt die Entscheidungen, die es freischaltet. Raster statt endloser Produktkarten, P99 bleibt kostenlos.
**Visueller Editor:** beliebige Ketten bis 20 Stufen mit Erfolg-/Fehlschlag-Verzweigungen als Graph, je Stufe Kräfte, Dauer, Timer, Priorität, Funktext; echter Ort im Land; Live-Prüfung (Schleifen mit Pfad, unerreichbare Stufen, Dubletten, fehlende Verweise, Ort außerhalb); Bearbeiten/Löschen; Einsatzpaket-Export/-Import mit Fehlerliste; „Speichern & auslösen“.
**Sandbox/Schwierigkeit:** Leicht/Normal/Schwer (Notrufaufkommen, Fristen), Ausbildungspflicht für SEK/MEK (Spezialkräfte starten mit ihrem Lehrgang).
**Nachweise:** 56 JVM-Tests, darunter eine 10-Tage-Karriere auf echten Hamburger Daten (Stufe 2 an Tag 3, ganz Deutschland an Tag 9 bei fehlerfreier Disposition; Budget +60.000 € in 10 Tagen, kein Geldregen); Android 35 `OK (4 tests)` in Lauf 41387892483 mit Screenshots von Karriere, Loadout und Editor.

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
