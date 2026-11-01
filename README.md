# EINSATZLEITSTELLE 110: DEUTSCHLAND

Offline-Android-Leitstellenspiel auf Basis der unveränderten [Alpha 0.2](prototype/alpha-0.2-offline.html). Dieser Branch implementiert den Spielkern für Issue #1. Die Versionskennung bleibt **0.3.0-dev**, bis die dokumentierten Releasegates vollständig abgenommen sind.

## Spielen

Karriere oder Sandbox wählen, ein Bundesland und eine 8-, 12- oder 24-Stunden-Schicht mit beliebiger Startstunde planen. Auf der Landeskarte Notrufe/Cluster öffnen, passende freie Kräfte auswählen und entsenden. Fahrzeuge brauchen Bereitschafts- und Fahrzeit, bevor die Bearbeitung beginnt. Entscheidungen ändern Zeit, Kosten und Erfolgsaussicht. Nach Dienstende zeigt der Bericht Erfolge, Übergaben, Personalwechsel, Ausbildung, Kosten und Reputation; „Nächsten Tag“ erhält den Fortschritt.

- **16 vollständige Offline-Landeskarten** mit echten Grenzen einschließlich Inseln/Exklaven, Städten, Wachen, Fahrzeugen, Notrufen, Routen, Heatmap, Filtern, Clustern und Minimap. „Ganzes Bundesland“ stellt die Übersicht wieder her. Hamburg enthält zusätzlich amtliche Hauptstraßen. Neuwerk gehört zur Hamburger Gesamtkarte; „Hamburg Stadtgebiet“ öffnet die lokale Detailansicht.
- **Live-Disposition:** 32 geografisch verteilte Fahrzeuge pro neu geöffnetem Land, acht Organisationen, Rückfahrt, Zustandsverschleiß und gekennzeichnete ETA. „Verteilte Notrufe“ demonstriert gleichzeitig Hannover/Oldenburg/Lüneburg bzw. München/Nürnberg. Alle Wachen und Personen sind fiktiv.
- **8/12/24h in beiden Modi**, freie Startstunde, Pause und 1/4/8/16×. Eine Schicht zählt genau einmal als Managementtag. Über Mitternacht gibt es keinen zusätzlichen Abschluss. Offene Lagen werden samt Zuweisungen und Bearbeitungsstand an den Folgetag übergeben.
- **Personal und Wirtschaft:** individuelle Profile und Bilder, Inventar, Ausrüstung, Erfahrung, Ermüdung, Dienstplan, Ablösung, Ruhe, Beschaffung, Fahrzeugwartung, Wachen-Ausbau, Budget und Laufbahn. Neue Einheiten haben drei Dienstteams; Alpha-Kräfte behalten ihre Profile und können Ablösung einstellen.
- **48 Einsatzvorlagen**, strategische Entscheidungen und Folgeereignisse; SEK für besondere Gefahrenlagen, MEK für Observation, Kripo für Ermittlungen, dazu Streife, Verkehr, Hundestaffel, Bereitschaft und Luftunterstützung.
- **10 freiwillige Lehrgänge**, Fortschritt pro abgeschlossenem Managementtag statt Mitternacht, Kosten einmal bei Beginn und dauerhafte Qualifikationen beim Abschluss.
- **Editoren:** Einsätze/Positionen, bis zu 20 verzweigte Kettenstufen, Einheiten, zusätzliche Besatzung, Organisationen, Fahrzeugprofile und Standorte. Kartenplatzierung per Tap. Versionierter JSON-Import/Export, Referenz-/Zyklus-/Grenzprüfung, drei Spielstandslots und Sicherungskopie.
- **Originalgrafiken und zehn Originalsounds**, getrennte Master/Funk/SFX/Ambiente-Regler, Mute, Textfunk, AudioFocus und Lifecycle-Pause.

## Entwickeln und testen

Voraussetzungen: Node.js 22+, Python 3, JDK 17, Android SDK 35 (Build Tools 35.0.0). Android-Minimum API 26; das Bundle ist auch für dessen ursprünglichen Chromium-58-WebView als klassisches IIFE mit Kompatibilitätshelfern gebaut.

```sh
npm ci
npm test
npm run build:web
npx playwright install --with-deps chromium
npm run test:ui
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

Die Debug-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`. Installation: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Das Debug-Paket braucht keine Netzwerk-, Ortungs-, Mikrofon- oder Speicherberechtigung.

Browser-Vorschau des **tatsächlich gebauten APK-Inhalts**: `python3 tools/serve.py`, dann <http://127.0.0.1:4173>. Für Änderungen: `npm run dev`; die Entwicklungsansicht verwendet die gleichen TypeScript-Module. Grafik-/Sounddateien liegen in `app/src/main/assets/`; der Webbuild erhält sie. Kein CDN, API-Schlüssel oder Kartenserver wird zur Laufzeit gebraucht.

Native Tests mit laufendem API-26+-Emulator: `./gradlew :app:connectedDebugAndroidTest`. GitHub Actions baut die APK, führt Domänen-/Browser-/Lint-Prüfungen und einen separaten API-26-WebView-Test aus. Artefakte und Testnachweise: [Actions](https://github.com/GoldenUnicorn24/Operator/actions/workflows/android-ci.yml).

## Architektur und Daten

`web/domain/` enthält den deterministischen Simulations- und Schichtkern; `web/map/` Projektion, Routing, Cluster und Renderer; `web/storage/` validierte Migration/Sicherung; `web/editor/` Inhaltsverträge; `web/audio/` den Mixer. `web/data/` enthält Kataloge und lizenzierte Offline-Geometrien. `index.html` erhält das Layout und die Managementansichten der Alpha als Ausgangspunkt; Vite erzeugt daraus `app/src/main/assets/index.html` und das lokale Bundle.

Schema 3 migriert echte Alpha-0.2-Daten, erhält ursprüngliche IDs, Profile, Inventar, Budget, Training, eigene Vorlagen und aktive Zuordnungen. Alte lokale Schlüssel bleiben als Original erhalten; der Android-Host sichert den Wechsel vom alten Datei-Ursprung zur sicheren HTTPS-Asset-Origin separat. Autosave/Slots werden verlustfrei komprimiert; der Datei-Export bleibt lesbares JSON. Defekte Importe ersetzen keinen aktuellen Spielstand. Bei Speicherfehlern erscheint eine Meldung mit Exportmöglichkeit.

## Grenzen und Abnahme

Die Karte ist ein Managementspiel: außerhalb Hamburgs nutzt die Anfahrt Großkreisdistanz × 1,28 / Fahrzeugtempo plus Bereitschaft. Das Hamburger Hauptstraßennetz unterstützt A*, enthält jedoch keine Abbiege-/Einbahn-/Verkehrsregeln. Alle Routen bleiben ausdrücklich Simulationsnäherungen. Die Grenzen entsprechen dem Quellenstand 2021. Kein Produkt für echte Einsatzplanung.

Die Kartenleistung wird im Desktop-Test mit mindestens 150 Objekten gemessen; daraus folgt keine garantierte Bildrate auf jedem Android-Handy. Geräteleistung und subjektive Grafik-/Soundabnahme bleiben eigene Releaseprüfungen. Der Entwicklungsstand wird deshalb noch nicht als endgültige v1.0 veröffentlicht.

Verbindlicher Umfang: [Bundeslandkarten & Schichten](docs/STATEWIDE_MAP_AND_SHIFTS.md), [Abnahme](docs/ACCEPTANCE_TESTS.md). Fortschritt/Nachweise: [Implementierungslog](docs/IMPLEMENTATION_LOG.md), [Android](docs/ANDROID_VALIDATION.md), [Prüfmatrix](docs/VERIFICATION_MATRIX.md). Rechtliches: [Quellen & Lizenzen](THIRD_PARTY_NOTICES.md).
