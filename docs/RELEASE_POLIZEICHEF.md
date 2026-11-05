# Einsatzleitstelle 110 – Lagezentrum 0.12.0

Native Offline-Vorschau, kein v1.0. [Abnahmeliste](https://github.com/GoldenUnicorn24/Operator/blob/codex/operator-v1-polizeichef/docs/ACCEPTANCE_LAGEZENTRUM.md).

- Live-Lagezentrum: Entscheidungen, Fristen und fehlende Verstärkung verständlich anzeigen; nach Handlungsbedarf, aktiven Lagen oder Archiv filtern. Kartenwarteschlange verwendet dieselbe Reihenfolge.
- Kostenschonender Dispositionsvorschlag: echte Straßen-Anfahrt, Streifen/Rückkehrer, mehrfacher Bedarf, Besatzungen, Ausbildung und Gesamtbudget. Vorhandene Kräfte werden einbezogen. Auswahl und Entsendung bleiben getrennt; keine automatische Umdisposition.
- Korrekte Fähigkeiten eigener Einheitentypen und eindeutige Rollenabdeckung; Fahrzeug-Grundtypen ersetzen keine fehlenden Fähigkeiten.
- Hamburger Detailkarten, Streife/FMS, Polizeichef-Verwaltung, Wetter/Rückrufe, fünf Speicherplätze und Querformat bleiben enthalten. Save-Schema 7 unverändert, keine neuen Assets oder Berechtigungen.

Geprüfter Quellstand `fc68015`: [vollständige grüne CI](https://github.com/GoldenUnicorn24/Operator/actions/runs/41939501660), **126 JVM-Tests + 9 Python-Verträge + 9 Android35-Gerätetests**, beide APKs, Lint und native Paketprüfung. [29 echte Screenshots](https://github.com/GoldenUnicorn24/Operator/tree/codex/operator-v1-polizeichef/docs/evidence/polizeichef). [APK herunterladen](https://github.com/GoldenUnicorn24/Operator/releases/download/operator-polizeichef-fc68015/Einsatzleitstelle110-Native-Leitstelle.apk).

APK **17601778 Bytes**, SHA256 `07165f4241b83989b1f25895a568a54bdbf13bfa2b6010db8acc0b0b0789e0d7`. Keine Internetberechtigung, WebView oder Web-Dokumente; keine neuen Fremdassets. ODbL-Daten weiterhin offen im Repository.

Grenzen: Vorschlag spart Einsatzkosten vor Fahrzeit und warnt vor Fristüberschreitung; keine automatische Planung aller Lagen. Emulatorprüfung ersetzt keinen physischen Geräte- oder menschlichen Langzeittest. Debug-APK: Bei anderer Signatur vor Installation den Spielstand exportieren. Kein v1.0.
