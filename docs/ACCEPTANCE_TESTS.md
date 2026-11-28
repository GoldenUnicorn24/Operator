# v1.0 – Abnahmecheckliste und Tests

**Diese Kriterien sind Release-Gates.** Nicht das Label v1.0 vergeben, solange zentrale Gates fehlen.

## Gates
- [ ] **Android APK** durch reproduzierbare GitHub Actions gebaut und als Debug-Artefakt verfügbar; auf Emulator Android API 26+ gestartet.
- [ ] **Offline:** Flugmodus ab erstem Start; Kernspiel, Assets und **Bundesland-Gesamtübersicht für jedes der 16 Bundesländer** funktionieren; keine externen Pflicht-CDNs.
- [ ] **Erster Spieltag**: Karriere auswählen → Hamburg → Frühschicht → min. drei Notrufe einschließlich P1 → Einheiten disponieren → Anfahrt sichtbar → Ereignis abschließen → Schichtende → Tagesreport → Tag 2.
- [ ] **Zweiter Spieltag**: Personalausrüstung, Budget, Statistik, Tagesnummer, Trainingsfortschritt und eigene Vorlagen bleiben erhalten.
- [ ] **Landesweite Livekarte**: In jedem Bundesland muss beim Öffnen die **vollständige Landesfläche einschließlich realer Grenzen** sichtbar sein (`fitBounds`). Einsatzfahrzeuge, Wachen, SEK/MEK, offene/aktive Vorfälle sind im ganzen Land georeferenziert sichtbar und anwählbar, bei Bedarf geclustert. Hamburg zusätzlich mit vertiefter Straßenkarte, Live-Routen, gesetzeskonformer OSM-Attribution. Nicht nur eine Stadt oder schematische Region.
- [ ] **Schichten & Zeitlogik**: In **beiden Modi** 8-, 12- und 24-Stunden-Schicht wählbar. Pause stoppt Bewegung/Fristen; 1×/4×/8× wirkt synchron; Ende/Report und Tagwechsel auch bei 22–06, 18–06, 06–06 korrekt (einmaliger Abschluss), Ausbildungen pro abgeschlossenem Spieltag.
- [ ] **Schichtende** mit offener Einsatzlage: deterministische Übergabe, keine verschwindenden Einsätze, Übergabe im Report erfasst.
- [ ] **SEK/MEK**: eigene Einheitentypen, spezifische Verfügbarkeiten, Kosten, Dispatch-/Aufgabeneignung, sichtbare Avatare und Fahrzeuge.
- [ ] **Portrait/Equipment**: Einzelbeamtenprofil anzeigen; Ausrüstung kaufen, an-/ablegen, Wirkung/Inventar prüfen; Benutzerbild lokal hinzufügen und bei Neustart erhalten.
- [ ] **Training**: mind. 8 wählbare Fortbildungen; Lehrgang 2 Tage, verbleibende Zeit exakt nach 2 Schichtabschlüssen 0; nie unendlich Kosten abziehen; Abschluss verändert Fähigkeit.
- [ ] **Personal/Fahrzeuge**: Einstellen, Einheiten-/Fahrzeugverwaltung und einfache Wartung/Dienstplanung spielbar; Kapazitäten und Kosten wirken in der Simulation.
- [ ] **Sandbox**: Kosten/Training/Frequenz anpassbar; eigene Einheit und mehrstufigen Einsatz anlegen, in Karte starten, Erfolg/Verzweigung erleben.
- [ ] **Editor** validiert Referenzen, ungültiges JSON/Eingaben werden abgefangen; Export/Import ohne Codeausführung.
- [ ] **Sound**: mindestens 8 unterscheidbare, lizenzierte originale SFX/Töne; Gesamt-/SFX-/Funk-/Ambient-Regler; Stummschalten ohne Fehler; bei App-Pause Audio stoppen.
- [ ] **Sichtbar hochwertige Grafik**: konsistente Beamtenbilder (min. 8), SEK/MEK/Streife, Icons, Fahrzeugbilder, Ausrüstungsmenü, animierte Marker; mindestens 4 echte UI-Ansichten mit Assets.
- [ ] **Deutschland**: 16 Bundesländer geografisch korrekt als Gesamtkarte, jeweils mit eigenem Layer für Einsatzfahrzeuge und Vorfälle, Kameraanpassung, Zoom und Rückkehr zur Landesübersicht; Hamburg detailliert. Tests in Niedersachsen, Bayern und Hamburg.
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


## Zusätzliche Pflichtprüfung: Bundeslandkarte und 8/12/24h-Schichten
- [ ] Niedersachsen vollständig anzeigen: Einsatzpins bei Hannover, Oldenburg und Lüneburg gleichzeitig in einer Landesansicht; Fahrzeuge verteilt, Markercluster/Filter navigierbar.
- [ ] Bayern vollständig anzeigen: z. B. München und Nürnberg als getrennte Einsatzorte, zoom to incident, Rückkehr zur Gesamtkarte.
- [ ] Während aktiver Schicht Fahrzeug-Icons auf Karte bewegen sich in allen Bundesländern; Pause friert Icons und Timer ein.
- [ ] 8-, 12-, 24-Stunden-Schicht pro Modus: exakt 480/720/1440 Minuten, inklusive Mitternacht, Report, Übergaben und neuem Tag ohne doppelte Buchungen.
- [ ] Mindestens 30 geografisch verteilte Fahrzeug-/Einsatzobjekte in einer Bundesland-Gesamtansicht performant darstellen; Cluster offenlegen und einzelne Fahrzeuge anwählen.
- [ ] 24h-Schicht mit interner Personalrotation/Ermüdung simulieren und Report testen; Ausbildungsschritte pro abgeschlossener Schicht (Gameplay-Tag) zählen.
- [ ] Screenshots: gesamte Niedersachsenkarte, gesamte Bayernkarte, Hamburg detail/overview, Schichtauswahl 8/12/24 und 24h-Tagesreport.
- [ ] Automat: `full_state_bounds_each_region`, `incident_within_state`, `vehicles_visible_across_region`, `region_switch_retains_vehicles`, `cluster_open_and_filter`, `shift_8h`, `shift_12h`, `shift_24h`, `midnight_once`.

Verbindlicher Detailvertrag: [STATEWIDE_MAP_AND_SHIFTS.md](STATEWIDE_MAP_AND_SHIFTS.md).

## Rebuilt – Regressionen und Freigabe

| Verhalten | Nachweis |
| --- | --- |
| Notrufübergabe und laufendes Gespräch nach dem Laden | `NativePhoneDeskTest.activeAndRingingCallsSurviveHandoffAndSave` |
| Frühe Alarmierung und Fortsetzung auf Android | `NativeControlRoomTest.canDispatchAndThenContinueTheSameCallFromTheNativeViews` |
| Neuere Informationen bleiben mit demselben Einsatz verbunden | `NativePhoneDeskTest.earlyDispatchKeepsTheCallerOnTheLineAndReusesTheSameIncident` |
| Abgelehnte Umdisposition bewahrt den gesamten Spielzustand | `NativeDispatchTest.failedRedirectPreservesOriginVehicleBudgetAndLogs` |
| Zusätzliche Einsatzrollen erfüllen Besatzung, Ausbildung und Ausrüstung | `NativeDispatchAdvisorTest.careerTaskforceCannotDeclareItsWayIntoSpecialistAssignments` |
| Fahrzeugwechsel bei konstanter Anzahl aktualisiert die Einsatzakte | `NativeDispatchAdvisorTest.incidentFileChangesWhenFreeVehiclesSwapEvenWithSameCount` |
| Gleichzeitige ETA-Abfragen bleiben mit den Routen konsistent | `NativeDirectedRoutingTest.concurrentDetailEtaReadsAgreeWithTheRoutedJourneys` |
| Routinemeldungen verdrängen dringende Funkmeldungen nicht | `NativeRadioQueueTest.routineOverflowDoesNotDiscardUrgentOrIncidentTraffic` |
| Fehlende Pflichtdaten führen zu einer erklärenden Fehlermeldung | `NativeTestAssets` in Geografie-, Telefon- und Schichttests |
| Simulationsuhr behält Tempo bei unregelmäßigen Ticks und pausiert bei Hintergrundwechsel | `SimulationClockTest` |
| Erste Besatzung arbeitet vor der Ankunft weiterer Dienste | `NativeGameEngineTest.crewsStartIndependentWorkBeforeTheOtherServiceArrives` und der native Einsatzakten-Ablauf |
| Restarbeit überlebt Speichern, Neustart und eine neue Besatzung | `NativeGameEngineTest.runningTaskSurvivesSavingRecallAndAReplacementCrew` |
| Routineeinsatz endet bei erledigter Arbeit über mehrere Seeds zuverlässig | `NativeGameEngineTest.finishedRoutineTaskSucceedsAcrossRandomSeeds` |
| Eine abgezogene und wieder alarmierte Unterstützung wiederholt ihren Arbeitsbonus nicht | `NativeGameEngineTest.recallingAndResendingItsSupportCannotApplyTheSameBenefitTwice` |
| Alte laufende Mehrrollen-Einsätze setzen die gespeicherte Arbeitszeit fort | `NativeGameEngineTest.aLegacyWorkingMultiRoleIncidentResumesItsRemainingMinutes` |
| Teilabschlüsse erscheinen im Schichtbericht, aktive Entscheidungen behalten ihren Wert | `NativeGameEngineTest.completedLocalWorkCountsAsPartialWhenOtherServiceNeverArrives`, `NativeCareerTest.engagedPlayBeatsPassivePlay` |
| Allgemeine Rückfragen verändern geskriptete Wendungen und offene Nachforderungen nur bei neuem Fallwissen | `NativePhoneDeskTest.theCommonQuestionGuidePreservesScriptedRevelations` und der Geräteablauf für frühe Alarmierung |
| Funkstatus antwortet für die tatsächlich eingesetzte Besatzung, Quittierungen bleiben erhalten | `NativeRadioArchiveTest.radioCommandsArePersistedAndDoNotControlAReassignedVehicle` und der native Funkarchiv-Ablauf |
| Vorschau erscheint nach erfolgreicher Paketprüfung, Geräteprüfung und Offline-Start | [Rebuilt Android](../.github/workflows/rebuilt-android.yml) |

Die vollständige Rebuilt-Freigabe erfordert darüber hinaus die laut [Entwicklungsstand](REBUILT_PROGRESS.md) offenen System-, Geräte- und Leistungsprüfungen.
