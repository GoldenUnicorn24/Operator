# Abnahme: Lagezentrum · 0.12.0-Vorschau

Umgesetzt: Live-Lagezentrum mit Filter/Handlungsgründen und gemeinsam sortierter Kartenwarteschlange; kostenschonender Vorschlag mit echter Straßen-ETA, Besatzung, Qualifikation, Gesamtbudget und vorhandenen Kräften. Keine automatische Entsendung oder Umdisposition. Der Spieler bestätigt den Auftrag.

Neue Nachweise: `NativeRoleCoverageTest`, `NativeRoleMatchingTest` und `NativeDispatchAdvisorTest` prüfen korrekte Fähigkeiten/Mehrfachbedarf, vorhandene vielseitige Einheiten, Budget, Ausbildungs-/Besatzungs-/Fahrzeuggrenzen, Sonderrechte-/Fristprognose, Zustands-/Zufallsfreiheit und Handlungsreihenfolge. 150 deterministische kleine Zuordnungsprobleme werden mit einer unabhängigen vollständigen Suche verglichen. Große importierte Bedarfslisten haben keine Bitmaskenbegrenzung.

Android-Geräteablauf: Lagezentrum → Anrufannahme beantworten → Vorschlag auswählen (noch kein Geldabzug, keine Entsendung) → bestätigte Entsendung zweier Kräfte → FMS 3 und reale Straßenrouten → Lagezentrum zeigt Anfahrt ohne fehlende Kräfte. Drei zusätzliche Screenshots sind in der verpflichtenden CI-Prüfung eingetragen.

Geprüfter Quellstand **`fc680157c82558ca5b16ccefcc15bd668f606f0d`**: [CI 41939501660](https://github.com/GoldenUnicorn24/Operator/actions/runs/41939501660) vollständig grün. **126 JVM-Tests, 9 Python-Verträge, beide APKs, Lint und native Paketprüfung; Android35 `OK (9 tests)`.** Nachfolgende Beleg-/Dokumentationscommits ändern keinen App-Code.

[APK herunterladen](https://github.com/GoldenUnicorn24/Operator/releases/download/operator-polizeichef-fc68015/Einsatzleitstelle110-Native-Leitstelle.apk) · [Release](https://github.com/GoldenUnicorn24/Operator/releases/tag/operator-polizeichef-fc68015) · [PR #5](https://github.com/GoldenUnicorn24/Operator/pull/5). APK **17601778 Bytes** (rund 17.6 MB), SHA256 `07165f4241b83989b1f25895a568a54bdbf13bfa2b6010db8acc0b0b0789e0d7`. CI-APK direkt mit `aapt2` geprüft: Version 0.12.0/113, keine Berechtigungen, minSdk26/targetSdk35.

29 tatsächliche Gerätescreenshots mit [Quellenzuordnung](evidence/polizeichef/SOURCE.txt): [Lagezentrum](evidence/polizeichef/native-incident-desk.png), [Vorschlag vor Entsendung](evidence/polizeichef/native-dispatch-advice.png), [bestätigte Anfahrt](evidence/polizeichef/native-incident-desk-dispatched.png). Live-Filter nach einer echten Spielminute im selben Gerätetest geprüft.

[Emulator-Messwerte](evidence/polizeichef/perf-emulator.txt):

```text
Lagezentrum Wandsbek: Vorschlag für 2 Rollen / 40 Fahrzeuge in 1.21138 ms (warmer Routingcache)
DE-BY Straßengraph: 558 ms, 63658 Knoten
DE-BY Region gewählt bis Grundkarte fertig: 3366 ms
DE-BY Live-Overlay (Software-Canvas, 48 Einheiten, 3 Lagen): Ø 18.1 ms je Bild
DE-NI Straßengraph: 371 ms, 42057 Knoten
DE-NI Region gewählt bis Grundkarte fertig: 2819 ms
DE-NI Live-Overlay (Software-Canvas, 48 Einheiten, 4 Lagen): Ø 15.0 ms je Bild
P1 Wandsbek Übersicht bis Grundkarte: 1372 ms
P1 Einbahn-Wohnstraße: Weg 1, Richtung 1, 7 Routenpunkte, 2 min
P1 Straßenebene bis Grundkarte: 787 ms
P1 Detail-Overlay 60 Bilder: Ø 18.67659778333334 ms, p95 30.078769 ms, max 34.501861 ms; Bitmap 8320320 bytes
```

Vorschlagszeit misst den realen Wandsbek-Spielzustand mit warmem Routingcache. Grundkartenzeiten sind Wartezeiten bis zum fertigen Hintergrund; Overlaywerte sind Software-Canvas-Stichproben. Keine physischen Gerätewerte oder allgemeine FPS-Garantie. Kein v1.0.

Grenzen: Vorschlag optimiert Einsatzkosten vor Anfahrtszeit, deshalb Warnung bei drohendem Fristablauf und weiterhin freie manuelle Auswahl. Kein globaler Autodisponent über mehrere Lagen. Fahrzeiten bleiben Spielprognosen auf dem Offline-Netz. Physische Geräte-/Hörprüfung und menschliches Langzeitbalancing stehen aus. Save-Schema 7 bleibt unverändert; bisherige Migrationen und Offline-Funktionen bleiben bestehen. Die Grenzen des [Polizeichef-Stands](ACCEPTANCE_POLIZEICHEF.md) gelten weiter.
