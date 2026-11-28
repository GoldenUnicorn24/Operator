# EINSATZLEITSTELLE 110: DEUTSCHLAND

**Native Android-Leitstellensimulation.** Die entwickelte Spielversion wird als Kotlin-App mit nativen Views, Canvas-Karte und SoundPool weitergeführt. Die installierbare App enthält keine HTML-, JavaScript- oder CSS-Dateien, keine WebView und benötigt weder Internet noch eine eigene Domäne.

[Operator Rebuilt Vorschau 2 herunterladen](https://github.com/GoldenUnicorn24/Operator/releases/tag/v0.20.0-rebuilt-preview.2) · [Emulatorbilder der Vorschau](https://github.com/GoldenUnicorn24/Operator/releases/download/v0.20.0-rebuilt-preview.2/Emulator-Screenshots.tar.gz)

## Operator Rebuilt – Entwicklungsvorschau

Der Branch `codex/operator-rebuilt-v020` entwickelt die native Version 0.16 weiter. Rebuilt verbindet Notruf, Einsatzakte und Disposition im laufenden Gespräch. Die Folgeschicht kann offene Notrufe übernehmen. Vorschau 2 schützt den Autosave beim Neuanlegen einer Partie und ergänzt vier Dispositionsmodi. Aktueller Umfang, Testbefunde und offene Meilensteine stehen im [Entwicklungsstand](docs/REBUILT_PROGRESS.md).

## Lagezentrum und Polizeichef (0.12.0)

Arbeitsbranch `codex/operator-v1-polizeichef`: Live-Lagezentrum mit Handlungsgründen, Frist-/Entscheidungswarnungen und kostenschonenden Dispositionsvorschlägen. Vorschläge berücksichtigen die echten Fähigkeiten, Ausbildung, Gesamtbudget und Straßen-Anfahrt; entsendet wird nach Bestätigung. Dazu Hamburger Bezirksdetailkarte, Streifen/FMS/Sonderrechte, eigene Dienststellen mit Fahrzeug- und Personalverwaltung sowie Wetter, Rückrufe, fünf Speicherplätze und Querformat-Einsatzakte. Native Offline-App, weiterhin ohne Internetberechtigung. [Aktuelle Abnahme](docs/ACCEPTANCE_LAGEZENTRUM.md) · [Vorheriger Stand 0.11](docs/ACCEPTANCE_POLIZEICHEF.md) · [Release-Notizen](docs/RELEASE_POLIZEICHEF.md). Kein v1.0; Tests und Gerätebelege werden auf den jeweils tatsächlich geprüften Commit bezogen.

## V1-Entwicklungsstand (Branch `claude/operator-v1-gameplay`)

Meilensteine **M1–M4** umgesetzt (Version 0.10.0-v1-kandidat): topografische Offline-Live-Karte aller 16 Länder mit Straßenrouting, Disposition direkt auf der Karte, 34 Einsatzabläufe mit Entscheidungen, SEK/MEK-Differenzierung, Karriere mit Freischaltungen, Loadout, visueller Ketteneditor, Tutorial. Abnahmestand: [docs/ACCEPTANCE_CLAUDE_V1.md](docs/ACCEPTANCE_CLAUDE_V1.md). [Preview-APK](https://github.com/GoldenUnicorn24/Operator/releases/tag/operator-claude-v1-preview) · [Gerätescreenshots](docs/evidence/claude-m1/) · [Release-Notes](docs/RELEASE_CLAUDE_V1.md). Noch kein v1.0: menschlicher Spieltest und Messung auf echtem Gerät stehen aus.

## Aktueller nativer Stand

- 16 vollständige Offline-Bundeslandkarten mit echten Grenzen, Städten, fiktiven Wachen, Fahrzeugen, Notrufen, Clustern, Filtern und Routen. Hamburg mit amtlichem Hauptstraßennetz und einem A*-Spielgraph.
- Optional **ganz Hamburg oder ein echter Bezirk**, z.B. **Wandsbek**. Acht lokale Kräfte statt der landesweiten Startflotte; Vorfälle innerhalb der gewählten Zuständigkeit. Bestehende Fahrzeuge bleiben an ihren Standorten. „Land“ zeigt jederzeit die vollständige Landesübersicht, ohne die Zuständigkeit zu ändern.
- **Persönliche kostenlose P99 für jeden Polizeibeamten**, einschließlich Ablösungen und importierter Profile. Kein Einzelkauf und kein zusätzlicher Ausrüstungsslot. Zusätzliche MP5/G36 und andere Ausrüstung als abstrakte Spielwerte; keine Waffenbedienung oder realen taktischen Abläufe.
- Neue generierte **acht Personenporträts**, zehn Fahrzeugansichten und 16 Ausrüstungsbilder. Die Karte verwendet eigenständig gezeichnete, bewegte Fahrzeugsilhouetten mit Fahrtrichtung, Organisation und Status.
- **SEK und MEK können auch Streifenaufträge übernehmen**; umgekehrt ersetzt eine Streife keine Spezialkräfte. Direkte, nach Fahrzeit sortierte Disposition mit Unterstützung von außerhalb des Bezirks.
- 48 Szenarien, zehn freiwillige mehrtägige Lehrgänge, acht Polizeiorganisationen plus koordinierte Hilfsdienste, individuelle Inventare, Erfahrung, Ermüdung, Fahrzeugwartung und Dienstplan.
- 8/12/24h-Schichten in Karriere und Sandbox, Beginn0–23 Uhr, Pause und1/4/8/16×. Absolute Simulationszeit, einmaliger Tagesabschluss, interne Ablösungsteams und gespeicherte offene Lagen über mehrere Tage.
- Native Editoren für Einheiten/Fahrzeuge, eigene Einsätze und verzweigte Ketten, JSON-Import/Export. Alpha-0.2-/Geo-v2-/vorherige Web-Schema3-Exporte sowie ältere native Spielstände werden in das native Schema7 übernommen. Laufende Anfahrten und verbleibende Bearbeitung werden erhalten.
- Zehn originale WAV-Signale, Master/Funk/SFX/Ambiente, Mute, AudioFocus und Hintergrundpause. Keine Pflicht-CDNs, Kartenserver, Registrierung oder Telemetrie.

## Bauen

JDK17, Android SDK35 / Build Tools35.0.0 und Python3 für die Paketprüfung.

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/verify_native_apk.py app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Rebuilt-Vorschau **0.20.0-rebuilt-preview.2**, VersionCode 119, minSdk 26, targetSdk 35. Kein Release-Signing-Schlüssel im Repository. `app/src/main/native_assets` und die Bildressourcen sind die einzigen APK-Assetquellen. Frühere Alpha-/Webquellen bleiben als Entwicklungs- und Migrationsreferenz erhalten; sie werden nicht mit dem Spiel ausgeführt oder als Web-App ausgeliefert.

Der CI-Workflow baut das APK, führt Unit-Tests/Lint/Paketprüfung aus und startet einen nativen Android-Emulatorablauf inklusive Wandsbek, P99, Spezialkräfte-Disposition,24h, Folgetag und Screenshots. Ein verifizierter Preview-Build wird als APK-Artefakt und separater GitHub-Preview-Download bereitgestellt. [Actions](https://github.com/GoldenUnicorn24/Operator/actions/workflows/android-ci.yml).

## Architektur

`PoliceChief.kt`/`PoliceChiefCodec.kt`: Dienststellen, Fuhrpark und Personal; `PatrolRouting.kt`/`SimulationConditions.kt`: Streifen und Verkehrs-/Wetterwerte; `SaveSlots.kt`: unabhängige Speicherplätze; `GameState.kt`: reine native Engine und Modelle; `GameStore.kt`/`NativeMigration.kt`: validierte atomare Speicherung und Datenübernahme; `OperationsAreas.kt`/`GeoData.kt`: echte Regionen und Bezirke; `NativeRouting.kt`/`MapClustering.kt`/`TacticalMapView.kt`: Offline-Routing und Canvas-Karte; `MainActivity.kt`: native Bedienung; `EquipmentArt.kt`: Bitmap-Atlanten; `NativeAudio.kt`: native Audiosignale.

Die Ausgabe wird zusätzlich direkt auf HTML-/Script-Dateien und WebView-Klassenreferenzen geprüft. Vor einem Wechsel von einer anders signierten Debug-APK den Spielstand exportieren; nach Installation über „Spielstand importieren“ übernehmen. Eine native App kann den alten Chromium-localStorage nicht ohne Browserkomponente automatisch ausführen; vorhandene Daten werden deshalb über den JSON-Export übernommen.

## Grenzen und Nachweise

Straßengraph und Fahrzeiten sind **Spielnäherungen**: Hamburg berücksichtigt Einbahnrichtungen; Abbiegeverbote und Echtzeit-Verkehrsdaten fehlen. Alle 16 Länder besitzen gebündelte OSM-Netze; bei fehlenden Anschlüssen wird eine Distanznäherung ausdrücklich markiert. Die 141 Hamburger Polizei- und Feuerwehrstandorte dienen als geografische Vorlagen. Ihre Ressourcen und Kapazitäten sowie eigene Wachen, Personen, Einsätze und Ausstattungswerte sind simuliert. Die Fahrzeug- und Ausrüstungsbilder sind generierte Illustrationen und keine Herstellerproduktdokumentation. Die visuelle Abnahme und Leistung auf physischen Mittelklassegeräten bleiben eigene Releasegates; kein vorzeitiges v1.0-Fertiglabel.

[Native Änderungsanforderungen und Nachweise](docs/NATIVE_REVISION.md) · [Implementierungslog](docs/IMPLEMENTATION_LOG.md) · [Quellen/Lizenzen](THIRD_PARTY_NOTICES.md).
