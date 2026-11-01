# v1.0 – Abnahmecheckliste und Tests

**Diese Kriterien sind Release-Gates.** Nicht das Label v1.0 vergeben, solange zentrale Gates fehlen.

## Gates
- [ ] **Android APK** durch reproduzierbare GitHub Actions gebaut und als Debug-Artefakt verfügbar; auf Emulator Android API 26+ gestartet.
- [ ] **Offline:** Flugmodus ab erstem Start; Kernspiel, Assets und Hamburg-Karte funktionieren; keine externen Pflicht-CDNs.
- [ ] **Erster Spieltag**: Karriere auswählen → Hamburg → Frühschicht → min. drei Notrufe einschließlich P1 → Einheiten disponieren → Anfahrt sichtbar → Ereignis abschließen → Schichtende → Tagesreport → Tag 2.
- [ ] **Zweiter Spieltag**: Personalausrüstung, Budget, Statistik, Tagesnummer, Trainingsfortschritt und eigene Vorlagen bleiben erhalten.
- [ ] **Karte geografisch plausibel**: Hamburg mit echtem, rechtmäßig nutzbarem Straßennetz/Ländergrundlage und Attribution; scrollen, zoomen, Einheiten auswählen, Fahrzeug-Route zeichnen; Bewegung auf Timeline. Kein Ersatz durch nur schematische Kacheln.
- [ ] **Zeitlogik**: Pause stoppt Bewegung/Fristen; 1×/4×/8× wirken synchron auf die gesamte Simulation; Nachtwechsel 22–06 Uhr korrekt.
- [ ] **Schichtende** mit offener Einsatzlage: deterministische Übergabe, keine verschwindenden Einsätze, Übergabe im Report erfasst.
- [ ] **SEK/MEK**: eigene Einheitentypen, spezifische Verfügbarkeiten, Kosten, Dispatch-/Aufgabeneignung, sichtbare Avatare und Fahrzeuge.
- [ ] **Portrait/Equipment**: Einzelbeamtenprofil anzeigen; Ausrüstung kaufen, an-/ablegen, Wirkung/Inventar prüfen; Benutzerbild lokal hinzufügen und bei Neustart erhalten.
- [ ] **Training**: mind. 8 wählbare Fortbildungen; Lehrgang 2 Tage, verbleibende Zeit exakt nach 2 Schichtabschlüssen 0; nie unendlich Kosten abziehen; Abschluss verändert Fähigkeit.
- [ ] **Personal/Fahrzeuge**: Einstellen, Einheiten-/Fahrzeugverwaltung und einfache Wartung/Dienstplanung spielbar; Kapazitäten und Kosten wirken in der Simulation.
- [ ] **Sandbox**: Kosten/Training/Frequenz anpassbar; eigene Einheit und mehrstufigen Einsatz anlegen, in Karte starten, Erfolg/Verzweigung erleben.
- [ ] **Editor** validiert Referenzen, ungültiges JSON/Eingaben werden abgefangen; Export/Import ohne Codeausführung.
- [ ] **Sound**: mindestens 8 unterscheidbare, lizenzierte originale SFX/Töne; Gesamt-/SFX-/Funk-/Ambient-Regler; Stummschalten ohne Fehler; bei App-Pause Audio stoppen.
- [ ] **Sichtbar hochwertige Grafik**: konsistente Beamtenbilder (min. 8), SEK/MEK/Streife, Icons, Fahrzeugbilder, Ausrüstungsmenü, animierte Marker; mindestens 4 echte UI-Ansichten mit Assets.
- [ ] **Deutschland**: 16 Bundesländer in Regionenmenü auswählbar; Hamburg detailliert; weitere Regionen mit ehrlich gekennzeichnetem Spiel-Datenumfang.
- [ ] **Save-Migration**: echtes Alpha-0.2-JSON und neu gespeicherter v1-Stand; Save ohne Datenschutzproblem/Schemafehler importiert; Defekte Dateien abweisen ohne den aktuellen Save zu zerstören.
- [ ] **Performance & UX**: auf 6–7-Zoll-Emulator Hochformat nutzbar, Landscape navigierbar, keine abgeschnittenen Hauptfunktionen; bei 10+ laufenden Einsätzen spielbar.
- [ ] **Dokumentation**: README-Build-Schritte, Third-Party-Notices inkl. Kartenlizenz, CI-Links, bekannte Einschränkungen und Änderungslog.

## Automatisierbare Testfälle
1. `shift_crosses_midnight`: Nacht 22–06, genau 480 Schichtminuten, DayIndex steigt genau einmal.
2. `manual_pause_freezes_all`: positions, incident timers, training remain identical after paused ticks.
3. `dispatch_assigns_capability`: passende Einheit wird belegt, unpassende/schon belegte nicht.
4. `route_progress_monotonic`: Abstand zum Ziel entlang des Wegs verringert sich; position never NaN.
5. `incident_chain`: valid trigger spawns exactly one follow-up, no runaway loops.
6. `training_2_days`: 2 Schichtabschlüsse führen zur Qualifizierung, 1 nicht.
7. `equipment_not_duplicated`: Kaufen verändert Inventar/Budget, genau ein Gegenstand pro Slot, keine negativen Bestände.
8. `sandbox_unlimited`: optionale unbegrenzte Mittel verhindern negative Budgets.
9. `career_economy`: Gehälter/Wartung/Einnahmen buchen genau einmal pro Tag.
10. `save_roundtrip`: save → reload → deep equivalent critical state.
11. `save_migration_alpha02`: alte Spielstände behalten relevante Daten; migrationslog dokumentiert.
12. `custom_editor_validation`: ungültige Referenzen werden geblockt, aber bestehende Daten nicht zerstört.
13. `audio_muted`: cue request produces no audio when muted, event still logged.
14. `map_no_network`: fallback does not crash and dispatch actions work.
15. `portrait_upload_sanitized`: Größe/Format/Fehlerabbruch, keine unlimitierte Speicherung.

## Testdokumentation
Je nach Buildumgebung: Playwright/JS Unit-Tests und Android-Instrumentation. In PR aufführen: exakte Kommandos, Ergebnis, Screenshot für Map/Personal/Ausrüstung/Schichtende, Artefaktlink, bekannte fehlende Kriterien. Keine erfundenen Testergebnisse.
