# EINSATZLEITSTELLE 110 – verbindliche v1.0-Produktspezifikation

## Vision
Hochwertiges Android-Leitstellen-Strategiespiel aus Deutschland: **911-Operator-Prinzip + tiefes Personal-/Ausrüstungsmanagement + lebendige Echtzeitkarte + Karriere/Sandbox**. Keine Kopie bestehender Spiele.

## Zentrale Spielschleife
**Planung → Schichtstart → eingehende Notrufe → Einsatz bewerten → disponieren → Fahrzeuge bewegen sich live → Entscheidung/Unterstützung → Einsatzbericht → Schichtende/Tagesabschluss → Fortbildung/Beschaffung → neuer Tag.**

### Muss-Systeme (v1.0)
**A. Spielmodi und Schichttag**
- Karriere und Sandbox von Anfang an auswählbar; mehrere Save-Slots.
- Früh 06–14, Spät 14–22, Nacht 22–06 Uhr; von Nutzer variabel wählbare Startzeiten erst nach v1.0 optional.
- Tages-Countdown, Pause, Geschwindigkeiten 1×/4×/8×, manuelles Schichtende nur nach klarer Regel; offene Einsätze kontrolliert an Folgeschicht übergeben.
- Tagesreport mit Einsätzen, Gefahrenlagen, Erfolgsquote, Personalbelastung, Budget, Ruf/Reputation, Schichtstatistik, Ausbildung und Fahrzeugzustand.
- **Nächster Tag**: weiterhin bestehende Beamte/Einheiten/ausgewählte Ausrüstung, laufende Lehrgänge, Geld, Ruf, Dienststellen, selbst erstellte Inhalte.

**B. Deutschland und Karte**
- Alle **16 Bundesländer** im Regionenmenü; reale landesspezifische Namen, fiktive Spielbehörden optional.
- **Hamburg**: in v1.0 geographisch plausibel mit nutzbarer Straßenkarte, konkreten Bezirken, Straßen und angetäuschter Live-Bewegung entlang Routen (keine nur schematische Karte).
- Kartendarstellung Tag/Nacht und Zoom, Pinch/Drag, gute FPS, Kartenfilter (Streifen/SEK/MEK/Kripo/Notrufe/Wachen), Overlay für Einsatzdichte, Warnbereiche.
- Einheitenpins kontinuierlich bewegen, Ziellinie/Route anzeigen, Anfahrt/Rückfahrt simulieren, ETA mit Grundlage im Simulationsmodell; im Pausenmodus friert alles ein.
- Pin → interaktiver Detailbereich → Einheiten zuweisen/zurückrufen; Einsatzpriorität und Zuständigkeiten sichtbar.
- Routing offline, zumindest hamburgzentriert auf einem reduzierten Straßengraphen oder lizenzierten kleinen Datensatz; wenn unmöglich, klar benannte Näherung, aber dennoch sichtbare Bewegung.
- Andere Bundesländer dürfen bei v1.0 auf generischen Regionendatensätzen basieren; müssen spielbar sein, jedoch keine Fake-Kartendetails behaupten.

**C. Einsätze / Disposition**
- Mindestens 40 datengetriebene Vorlagen über P1–P3, mit Hamburg-spezifischen und allgemeinen Ereignissen.
- Mehrstufiger Ablauf: Hinweis/Notruf, Klassifizierung, Disposition, Anfahrt, am Einsatzort, Bearbeitung, Folgeereignis, Abschluss oder Übergabe.
- Einsatzketten mit Bedingungen, Eskalation, mehrere benötigte Einsatztypen, parallele Anrufe, Risiko-/Ergebnisberechnung mit gut nachvollziehbaren Faktoren.
- Verschiedene Fahrzeuge, unmarkierte MEK-Fahrzeuge, Streifenpolizei, Kripo, SEK; Spezialkräfte durch Bereitschaft, Training, Kosten, Anfahrtszeit sinnvoll begrenzt.
- Das Spiel zeigt keine echte polizeitaktische Prozedur; Entscheidungen sind Management auf hoher Ebene (absichern, zusätzliche Kräfte, Verhandlungsgruppe, Behandlung etc.).
- Mindestens 10 simultane Ereignisse ohne UI-Abbruch; klare Alarmkategorie, Einsatzstatus, Dringlichkeits-Timer.

**D. Beamte / sichtbare Ausrüstung**
- Personalkarten mit Porträt, Name, Status, Fähigkeiten/Erfahrung, Müdigkeit, Einsatzzuteilung.
- Unterschiedliche **visuelle Profile** für Streife, SEK, MEK, Kripo; ergänzend Fahrzeugbilder, Einsatz-/Dienststellen-Illustrationen.
- Mehrere individuelle Ausrüstungsslots je Beamtem, inventarisierte Gegenstände, sichtbarer Look/Ladebalken/Stats, klare Auswirkungen auf Ausdauer/Schutz/Kommunikation/Ausrückzeit.
- Ausrüstungskauf, anlegen/ablegen, Kosten/Bestand, kein Doppelbelegen nicht vorhandener Gegenstände. Für SEK/MEK passende, aber vereinfachte abstrakte Ausrüstungsvarianten.
- Bild-Upload eines eigenen Porträts lokal möglich, datensparsam und auf Dateigröße begrenzt.
- Einheiten mit mehreren Beamten, Fahrzeug, Standort, Funkrufname, Schicht-/Bereitschaftsstatus, optional Sonderfähigkeiten.

**E. Ausbildung / Management**
- Mehrtägige fakultative Trainings; Training kostet Budget/Bindung und kann Verfügbarkeit beeinträchtigen; Fortschritt nur bei Tagesübergang.
- Mindestens 8 Lehrgänge (Funk/Deeskalation/Erste Hilfe/Fahndung/Verkehr/SEK/MEK/Führung), echte Werte- oder Fähigkeitsverbesserung.
- Personal einstellen, Dienstplan/Schichten planen, Ruhe-/Ermüdungszustand, Fahrzeuge warten/beschaffen, einfache Wachen-/Stützpunktverwaltung.
- Karriere mit Einkommen, laufenden Kosten, Rang-/Reputationsfortschritt, Freischaltungen. Sandbox kann Kosten, Progression, Zeit, Alarmfrequenz, Training deaktivieren/anpassen.

**F. Editor (Karriere und Sandbox)**
- Einsatzeditor: Name, Region, Ort/Positionswahl, Priorität, Beschreibung/Notruftext, benötigte Einheiten, Timer, Dauer, Folgeereignisse, Bedingungen, Erfolg/Misserfolg, Belohnung.
- Einheiteneditor: Typ, Funkrufname, Personal, Fahrzeug, Werte, Ausrüstung, Bild.
- Fahrzeugeditor: Bild, Kapazität, Fahrprofil, Wartung, Kosten, Geschwindigkeit, Skills.
- Datenexport/-import (JSON, Schema-Version), Vorlagen validieren, Fehlermeldungen freundlich, ZIP optional.
- Editierte Inhalte **in beiden Modi** auswählbar, sandboxtypische Übermächtigkeit nur freiwillig.

**G. Sound / Grafik / UX**
- Hochwertige dunkle Control-Room-UI, topografische Texturen, saubere Beschriftungen, keine unleserlichen Kleinsttexte, für 6–7-Zoll-Geräte.
- Einheitliche Asset-Sprache: original illustrierte Personalporträts, Fahrzeugkarten, Ausrüstungspiktogramme; dezent animierte Sirenenmarker, Funkstatus, Wetter/Tag-Nacht-Karte.
- Mindestens 8 eigene Sound-Cues: eingehender Notruf, P1-Alarm, P2-Alarm, Funk-Rauschen/Squelch, Bestätigung, Fahrzeugabfahrt, Einsatz abgeschlossen, Schichtende; optionale dezente Leitstellen-Ambience.
- Audio-Mixer (Gesamt, Funk, SFX, Ambiente), mute, Vibrationsoption und Untertitel/Text statt Audio (barrierefrei).
- Originale Sounddateien selbst synthetisieren, erzeugen oder mit ausdrücklicher Lizenz aufnehmen; keine vorhandenen Spiel-Audios kopieren.

**H. Qualitätsanforderungen**
- Offline spielbar ohne Registrierung. Online-Karten optional, Offline-Fallback vorhanden. Kein Schlüssel im Clientcode.
- Stabile Save-Migration von Alpha 0.2, jederzeit Save/Load/Export/Import.
- 30 FPS Ziel in Kartenansicht auf moderatem Androidgerät, keine unkontrollierte Speicherzunahme, lesbare UI bei Font-Skalierung.
- Minimale Gerätevoraussetzung dokumentieren; Porträt/Landscape-UX prüfen; Datenschutz und Lizenznachweise.

## Should-have bei Zeitbudget
Wetter, Polizeihubschrauber, Rettungs-/Feuerwehrkoordination, Funkverkehr als Textdialog/TTS, mehrtägige Kampagnen, Ermittlungsketten, Anpassungen von Dienststellen, statistische Heatmaps, Nachrichtenfeed.

## Nicht v1.0
Echte Einsatzzentralen anbinden, echte Notrufdaten, vollständiges realistisches Netz aller deutschen Polizeiwachen, Multiplayer-Backend, 3D-Shooter, Live-Ortung echter Polizeifahrzeuge, kostenpflichtige Karten-APIs, KI-Cloudpflicht.

## Inhaltliche Wahrheit
Die Hauptfunktionen müssen spielbar sein, nicht nur optisch vorgetäuscht. Das V1-Label erst nach bestandener End-to-End-Schicht und Android-Build vergeben.
