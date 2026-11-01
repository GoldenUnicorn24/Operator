# Grafik-, Asset- und Sound-Briefing

## Art Direction
Eigenständige Premium-Optik: Hamburger Leitstelle bei Nacht, dunkelblaues Interface, Cyan für verfügbare Einheiten, Bernstein für P2, Rot nur P1/Kritisch, Mint für Erfolge. Hierarchie: zuerst Karte und Notrufe, dann Ressourcen, Personal, Ereignisdetails, Reports. Nicht vorhandene Spiel-Screenshots lediglich als Inspiration behandeln.

### Mindestens benötigte visuelle Assets
- 8–12 stilistisch konsistente, **fiktive** Beamtenporträts (Streife, SEK, MEK, Kripo, Dienstleitung), neutral/realistisch, keine Identifizierbarkeit realer Polizisten.
- SEK-/MEK-Gruppenbild, Bereitschaftspolizei, Streifenwagen, zivile Fahrzeuge, Dienststelle, Funkleitstelle, Hamburger Hintergrundpanorama.
- Ausrüstungsbilder/Icons: Funk, Schutzweste, Helm, Bodycam, Erste Hilfe, Taschenlampe, Schutzschild, Kamera, Drohne, Handschuhe, zusätzlicher Schutz.
- Statusvarianten: frei, im Einsatz, Anfahrt, Ausbildung, erschöpft, wartung, gesperrt.
- Originale Kartenpin-Sprites / SVGs und animierte Route- und Dispatch-Overlays.
- Regionalkarten sachgerecht aus zulässigen geographischen Daten; Hamburg ist der visuelle Star.
- App-Icon und Launch-Screen. Lesbare Fonts unter Android, Touchflächen >= 44dp, Kontrast.

**Asset-Erstellung:** Originalillustrationen mit einer verfügbaren Bilderstellungsfunktion generieren; falls keine vorhanden, hochwertige eigene Vektorillustrationen/Piktogramme programmatisch erzeugen. Vorhandene Alpha-0.2-Bilddateien aus der eingebetteten HTML-Demo extrahieren, falls verwendbar. Kein starres KI-Bild als Ersatz für programmierbare Benutzeroberfläche; jede UI-Funktion ist interaktiv.

### Bild-Schnittstellen
`PortraitAsset { id, uri, category, dimensions, license, attribution }`
`VehicleVisual { type, state, icon, cardImage, mapMarker }`
`EquipmentVisual { itemId, icon, preview, slot }`
Für hochauflösende Geräte 2×-Assets, komprimierte WebP/AVIF mit funktionierenden Fallbacks; Ladefehler stets abfangen.

## Sound-Kategorien (eigene Assets)
| Cue | Einsatz | Sollgefühl |
|---|---|---|
| incoming_call | neuer Notruf | dezentes 2-Ton-Signal |
| p1_alarm | Gefahrenlage P1 | prägnant, kurz, dringlich |
| p2_alarm | P2 | mittel, nie nervig |
| radio_squelch | Funkbeginn/-ende | kurzes Rauschen |
| dispatch_ack | Einheit zugewiesen | knackiges bestätigendes Click |
| unit_departure | Start Fahrt | dezenter Motor/Funkimpuls |
| incident_success | Einsatz beendet | leichte Bestätigung |
| incident_failure | Einsatz fehlgeschlagen | tiefer kurzer Warnhinweis |
| shift_end | Schichtwechsel | kurzer positiver Tagesabschluss |
| ambient_dispatch | Leitstellenatmosphäre | optionale Hintergrundkulisse |

Technisch separate Lautstärken: master/funk/sfx/ambient, mute, Audio-Focus, keine Dauerbeschallung, event deduplication und Cooldown. Events auch als Text/Funkmeldung verfügbar. **Keine echten Polizeifunkmitschnitte, keine kopierten Spiele-Audios.** SFX gerne reproduzierbar mit codebasierter Synthese generieren; Sounddateien samt `tools/generate_audio.py` oder ähnlichem einchecken. Synthese-Quellcode + Lautstärkerichtlinien dokumentieren.

## Bild- und Soundrecht
- Quellen, Lizenz, Urheber/Copyright, Link, Änderungen in `THIRD_PARTY_NOTICES.md`.
- OpenStreetMap © OpenStreetMap contributors, ODbL-Datenpflichten/Attribution erfüllen; Kacheln über einen passenden Anbieter oder eigene Offline-Pakete, öffentliche Standard-Server nicht bulk downloaden.
- Keine Logos/Assets bekannter Operator-Spiele kopieren. Die Spielidee darf inspirieren, die Gestaltung muss original sein.
