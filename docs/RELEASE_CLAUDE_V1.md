## Einsatzleitstelle 110 – V1-Entwicklungsstand · Meilenstein M1

**Kein v1.0-Release.** Debug-APK des Branches `claude/operator-v1-gameplay`, automatisch gebaut, mit Unit-Tests, Lint, Paketprüfung (keine WebView, keine HTML/JS/CSS-Dateien) und einem Android-35-Emulatorlauf geprüft, bevor sie hier hochgeladen wird.

### Neu in M1: Live-Karte und Disposition
- **Topografische Offline-Karte für alle 16 Länder:** Autobahnen, Bundes- und Hauptstraßen aus OpenStreetMap (ODbL, einmaliger Auszug, im Repo), Gewässer und Siedlungsflächen aus Natural Earth, Nachbarländer als Kontext, Ortsnamen nach Einwohnerzahl, Hamburger Bezirksgrenzen. Wird im Hintergrund gerendert; Zoom und Schwenken bleiben flüssig.
- **Echtes Routing auf dem Straßennetz** jedes Landes (A* mit Straßenklassen-Geschwindigkeiten). Fahrzeuge fahren sichtbar die Route entlang, mit Fahrtrichtung, Blaulicht und Restzeit. Fehlt ein Netzanschluss, wird gestrichelt eine *gekennzeichnete* Distanznäherung gezeigt. Hubschrauber fliegen direkt.
- **Disposition direkt auf der Karte:** Notruf antippen → Einsatzakte mit Anrufertext, benötigten Kräften und Restfrist → mehrere Einheiten mit Fahrzeit, Eignung, Besatzung und Kraft auswählen → Routenvorschau → „Entsenden“. Später: Verstärkung nachschicken, Einheiten abziehen, gebundene Kräfte umdisponieren, Einheiten auf Rückfahrt sofort neu alarmieren.
- **Einsatzmarker** mit Priorität, Lagetyp-Symbol und Frist-Ring; **Wachen** mit freien/gesamten Kräften; Legende, Filter, Minikarte, Doppeltipp- und fingerzentrierter Zoom, Zurück-zur-Übersicht.
- **Spielbare Hamburger 8-Stunden-Schicht:** Notrufaufkommen nach Tageszeit, Notrufe bevölkerungsgewichtet an echten Orten, abwechslungsreicher Schichtbeginn, Countdown bis Schichtende, Funkmeldungen bei Ankunft/Verstärkung/Abzug.
- **Erklärbarer Einsatzausgang:** Erfolgschance aus Anfahrtszeit gegenüber Frist, passenden Kräften, Erfahrung/Ausrüstung, Müdigkeit, Verstärkung, Gefahrenlage und Maßnahmen; die Begründung steht in der Einsatzakte und im Protokoll.

### Bekannte Grenzen (ehrlich)
- Routen sind Spielnäherungen: keine Einbahnstraßen, Abbiegeverbote oder Verkehrslage. Flächenländer haben nur das Haupt-, Stadtstaaten auch das Nebenstraßennetz.
- Keine MapLibre-Integration: Die App bleibt bewusst ohne Internetberechtigung und ohne Tile-Server; die Karte ist eine native Canvas-Vektorkarte aus gebündelten Daten.
- M2–M4 (mehrstufige Einsatzabläufe mit eigenen Dialogen, SEK/MEK-Differenzierung, Loadout-Bildschirm, visueller Ketteneditor, 10-Tage-Karriere-Balancing, Audio-Mixer-Ausbau) stehen noch aus.
- Spielgefühl und Leistung auf physischen Mittelklassegeräten sind nicht gemessen; nur Emulator.

Daten: © OpenStreetMap-Mitwirkende (ODbL) · Natural Earth (gemeinfrei) · © GeoBasis-DE/BKG · Freie und Hansestadt Hamburg (LGV/BWVI). Alle Wachen, Personen und Einsätze sind fiktiv.
