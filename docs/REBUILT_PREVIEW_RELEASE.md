# Operator Rebuilt 0.20.0 – Entwicklungsvorschau 3

Die native Offline-Vorschau verwendet VersionCode 120. Grundlage ist Operator 0.16 auf `eb88d29`. Ein nachgewiesener APK-Start ohne WLAN und mobile Daten ist Voraussetzung für diese Veröffentlichung.

- Das Funkarchiv filtert und durchsucht echte Simulationsmeldungen. Es zeigt Priorität, Rufnamen und Einsatzbezug und ermöglicht erneute Wiedergabe.
- Spieluhr und Fahranimation stimmen ihre Zeitbasis ab. Die Anzeige weist die effektive Drosselung durch Telefonate und offene Entscheidungen aus.
- Die Disposition bietet schnellste Hilfe, ausgewogene und wirtschaftliche Vorschläge sowie eine manuelle Auswahl. P1-Vorschläge priorisieren frühe Ankunft unter Berücksichtigung des Budgets.
- Beim Neuanlegen einer Partie erscheint stets eine Sicherheitsabfrage. Personal- und Wachplanung am ersten Spieltag wird damit genauso berücksichtigt wie spätere Schichten.
- Fahrbewegung und Karte teilen vorberechnete Streckenabschnitte. Umdisponierte Routen werden auch bei identischen Endpunkten neu dargestellt.
- Eine vorläufige Einsatzakte kann während eines laufenden Notrufs angelegt und disponiert werden.
- Schichtübergaben und Spielstände bewahren offene Anrufe mit Protokoll und Wartezeit.
- Umdisposition, Rollenprüfung, Funkwarteschlange, ETA-Cache und Autosave sind abgesichert.
- Hauptmenü, Telefonwarteschlange, Größen der Einsatzakte und Querformat wurden überarbeitet.

Die Aufgabenengine, das Gruppen-Taskforce-System, der Umbau von Karriere und Präsidium, die umfassende Geräteabnahme sowie die vollständige visuelle Abnahme sind offen. Die APK trägt eine Debug-Signatur. Eine vorhandene Installation mit anderer Signatur kann über diese APK nicht direkt aktualisiert werden.

Die anhängenden Dateien dokumentieren den Paketinhalt, den Offline-Start und die tatsächlich erzielten Prüfergebnisse. Die Veröffentlichung ist als GitHub-Pre-Release gekennzeichnet.
