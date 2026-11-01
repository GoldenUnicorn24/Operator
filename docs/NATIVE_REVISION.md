# Native Überarbeitung nach Nutzerkorrektur

Die ausdrückliche Nutzerentscheidung ersetzt den früher bevorzugten WebView-Weg: Android-Views, Canvas, Bitmap und SoundPool; keine WebView, HTML-, JavaScript- oder CSS-Dateien in der APK, keine Internetberechtigung. Die frühere TypeScript-Implementierung bleibt als Referenz erhalten.

Hamburg bietet die Gesamtzuständigkeit und sieben amtlich begrenzte Bezirke, einschließlich Wandsbek. Der Land-Button erlaubt weiterhin die gesamte Landesübersicht. Neue Einsätze entstehen innerhalb der gewählten Zuständigkeit; bestehende Kräfte werden beim Wechsel nicht teleportiert. SEK und MEK können reguläre Streifenanforderungen erfüllen.

Alle Polizeibeamten erhalten eine kostenlose, nicht separat zu kaufende P99 als Grundausstattung. MP5, G36 und Schutzausrüstung sind abstrahierte zusätzliche Spielwerte. Vier neue originale Bildatlanten liefern acht fiktive Porträts, sechzehn Ausrüstungsbilder und zehn Fahrzeugansichten. Kartenfahrzeuge sind eigene richtungsabhängige Canvas-Symbole.

Native Engine: 48 Einsatzvorlagen, zehn mehrtägige Lehrgänge, Karriere/Sandbox, 8/12/24 Stunden, frei wählbare Startstunde, Personalrotation, Übergabe, Tagesreport, Inventar und JSON-Import/Export. Native Speicherung enthält Migrationen aus Alpha und bisherigen Web-Spielständen; Browserdaten müssen zunächst dort als JSON exportiert werden. Originaldaten bleiben im migrierten Spielstand erhalten.

Verifikation: Kotlin-Domänentests, Android Lint, Produktions- und Instrumentierungs-APK sowie Paketprüfung gegen Web-Inhalte. GitHub Actions prüft die echte App zusätzlich auf einem Android-35-Emulator bei ausgeschaltetem Netzwerk und erzeugt Screenshots. Deren Ergebnis ist am jeweiligen Workflow zu prüfen.

Dies ist eine native Vorschau, keine Erklärung sämtlicher v1.0-Abnahmekriterien als erfüllt. Noch offen sind insbesondere physische Mittelklasse-Geräte-/Langzeit-Performance und der vollständige Abnahmevergleich aller erweiterten Editor- und Karrierefunktionen. Außerhalb Hamburgs ist Routing eine gekennzeichnete Distanzsimulation; Hamburg nutzt den lokalen Hauptstraßengraphen mit vereinfachten Anschlüssen, ohne echte Navigationsgarantie.

Die native Android-Abnahme hat inzwischen alle drei Instrumentierungstests bestanden (Quelle und unveränderte Screenshots unter `docs/evidence/native-v2/`). Der eigenständige Snapshot-Regressionsfall erhöht die Kotlin-Domänentests auf 27. Die Speicherengine verwendet tief getrennte native Kopien; JSON, Validierung, Backup und Dateisynchronisierung laufen im Hintergrund. Der frühere WebView-Download ist zur eindeutigen Abgrenzung im Prototypenarchiv abgelegt.
