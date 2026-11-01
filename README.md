# EINSATZLEITSTELLE 110: DEUTSCHLAND — Native Android V1

**Eine echte Kotlin-Android-App, KEINE WebView.** Für Android ab API 26, ohne Registrierung oder Account. Die Karten-Engine, das Spielmodell, die Dialoge, die Audioausgabe und die Speicherung sind native Android-Implementierungen.

## APK herunterladen
**[Installierbare APK auf GitHub Releases](https://github.com/GoldenUnicorn24/Operator/releases/tag/v1.0.0-native)**. Die veröffentlichte Datei heißt \`Einsatzleitstelle110-NativeV1.apk\`. Es handelt sich um eine Debug-APK für Tests, nicht um ein im Play Store zertifiziertes Release.

## Spielbare Funktionen
- **Alle 16 Bundesländer** mit **geografisch plausiblen Ländergrenzen**, automatisch auf die Gesamtkarte gezoomt, mit Zoom/Drag, Einsatzpins und landesweit verteilten Einsatzfahrzeugen.
- **Taktische Karte auf Android Canvas**, inklusive interaktiver Fahrzeugmarker, SEK/MEK, Streife/Kripo, Einsatzfahrten und ETAs. Bei Internetverbindung kann optional eine **echte dunkle OSM-Straßenkartenebene** angezeigt werden; ansonsten funktioniert eine eigene Offline-Kartenzeichnung.
- **Kein WebView** und keine HTML/CSS/JS-Dateien in der veröffentlichten App. Der Alt-Prototyp bleibt im Repository unter \`prototype/\` zu Archivzwecken.
- **Karriere und Sandbox**, Tagesschichten **8, 12 oder 24 Stunden** mit wählbarer Startstunde, Pause, Simulationstempo und Tagesabschluss.
- **48 Einsatzvorlagen**, P1–P3, Einheitenzuweisung, Anfahrt, Bearbeitung, Auswertung, Übergabe am Schichtende und Folgetag.
- **Spezialisierte Polizeieinheiten** (SEK, MEK, Kripo, Streife) sowie abstrahiert Rettung/Feuerwehr.
- **Personalporträts, Ausrüstung, Kauf, Fortbildung (8 Lehrgänge) und Budget/Reputation**.
- **Einsatz- und Einheiteneditor**, Speicherung und manuelles JSON-Export/Import über das native Android-Dateisystem.
- **Originale synthetisierte Funk-/Alarm-Signale** aus \`AudioTrack\`, offline.

## Architektur
- \`MainActivity.kt\` — native Android-Menüs, Formulare und Dialoge.
- \`TacticalMapView.kt\` — native Canvas-Zeichenfläche, Marker und Kartensteuerung.
- \`NativeStreetTiles.kt\` — **optionale** interaktive OSM-Rasterkacheln mit User-Agent, Cache, 2 Downloadthreads, ohne Bulk-/Offline-Prefetching; offline stets eigene Vektordarstellung.
- \`GeoData.kt\` — GeoJSON-Parser für alle 16 Länder, Städte und Entfernungsmessung.
- \`GameState.kt\` — Domänenobjekte, Tick/Disposition/Auswertung, Fortbildung, Inventar.
- \`GameStore.kt\` — lokale versionierte JSON-Spielstände.
- \`NativeAudio.kt\` — synthetisierte Audiosignale.
- \`app/src/main/native_assets/states.geojson\` — Bundeslandgrenzen; \`app/src/main/assets/images/\` — grafische Personalassets.
- \`app/src/test/\` — native Spielmechaniktests; \`.github/workflows/\` — APK-Build und Emulator-Test.

## Grenzen dieser Version
Die Karten-Gesamtansicht und die Bundeslandgrenzen sind georeferenziert. Die Fahrwege und ETAs sind **spielerische Simulationen und keine echte Straßenrouting-Engine**. Die echte Straßenkarte wird online geladen; standardmäßig sind offline nur vereinfachte Verbindungsnetze gespeichert. Noch nicht umgesetzt sind ein vollwertiger 911-Operator-artiger Straßenroutinggraph für ganz Deutschland, komplexe mehrstufige Einsatztabellen, alle spezialisierten Behördenabläufe, ein großer Fahrzeuge-/Wachenbaukasten oder ein externer Community-Szenariokatalog. **Dies ist eine native erste Spielversion mit diesen Grenzen**, kein vollständiger realistischer Polizeileitstellen-Nachbau.

## Build
In Android Studio Projekt öffnen oder per GitHub Actions testen:
\`\`\`bash
gradle :app:testDebugUnitTest :app:assembleDebug
\`\`\`
Java 17, Android SDK 35, Gradle 8.9. Für öffentliche Veröffentlichung separate Signatur und Lizenzprüfung nötig. Die V1 verwendet für Spieleinsatzentscheidungen ausschließlich abstrakte Managementregeln, keine echten taktischen Anleitungen.

## Datenschutz & Attribution
Es gibt kein Login, keine Werbung und keine Cloudspielstände. Falls die optionale Straßenkarte aktiviert ist, sendet das Gerät normale Kachelanfragen für den aktuell sichtbaren Ausschnitt an OpenStreetMap. © OpenStreetMap contributors. Es findet **kein Bulk-/Hintergrund-Download** ganzer Regionen statt. Weitere Quellenhinweise: \`THIRD_PARTY_NOTICES.md\`.
