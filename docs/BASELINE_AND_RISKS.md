# Geprüfte Ausgangslage (vor Codex-V1-Arbeiten)

- GitHub-Repository war am 2026-11-01 leer und wurde für dieses Projekt initialisiert.
- Tatsächlich verfügbar: Alpha-0.2-Offline-Einzeldatei mit Spielsystemen und Grafiken in `prototype/alpha-0.2-offline.html`.
- Android-Einstiegsprojekt unter `app/` verwendet dieselbe eigenständige HTML-Demo; es gibt keine geprüfte installierbare APK.
- Bisherige Alpha kann von Codex in Module zerlegt und grafisch neu gestaltet werden, enthält aber bereits Karriere, Sandbox, Portraits, individuelle Ausrüstung, Schichten, Ausbildungen und Editoren.
- Nicht ungeprüft als fertig behaupten: geografisch korrekte Karte, live fahrenden Fahrzeuge auf Straßennetz, Audioengine, 40 neue Einsätze, 8 Lehrgänge, mehrstufige Szenario-Graphen.
- Risikobereich Karte: lizenzkonforme OSM-Daten, Offline-Kartengröße, Android-WebView-GPU, Straßennetz-Routing und Batterieschonung.
- Risikobereich Migration: lokales Save-Format und Portraitbilder; während der Umstellung Daten transformieren, keine vorhandenen Spielstände löschen.
- Risikobereich Android-Build: noch kein Gradle-Wrapper im Starter; Actions lädt Gradle 8.9 und verwendet SDK 35. Release-Buildsignierung ist nicht vorbereitet, Debug-APK dagegen geplant.
- Keines der v1.0-Releasegates ist durch diese Initialisierung als bestanden zu betrachten.

**Arbeitsprinzip:** Ein tatsächlich funktionierender Hamburg-Vertical-Slice ist wichtiger als 16 optisch vorgetäuschte Vollkarten. Transparente Kennzeichnung der Regionstiefe.
