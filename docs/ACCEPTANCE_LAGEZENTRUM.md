# Abnahme: Lagezentrum · 0.12.0-Vorschau

Umgesetzt: Live-Lagezentrum mit Filter/Handlungsgründen und gemeinsam sortierter Kartenwarteschlange; kostenschonender Vorschlag mit echter Straßen-ETA, Besatzung, Qualifikation, Gesamtbudget und vorhandenen Kräften. Keine automatische Entsendung oder Umdisposition. Der Spieler bestätigt den Auftrag.

Neue Nachweise: `NativeRoleCoverageTest`, `NativeRoleMatchingTest` und `NativeDispatchAdvisorTest` prüfen korrekte Fähigkeiten/Mehrfachbedarf, vorhandene vielseitige Einheiten, Budget, Ausbildungs-/Besatzungs-/Fahrzeuggrenzen, Sonderrechte-/Fristprognose, Zustands-/Zufallsfreiheit und Handlungsreihenfolge. 150 deterministische kleine Zuordnungsprobleme werden mit einer unabhängigen vollständigen Suche verglichen. Große importierte Bedarfslisten haben keine Bitmaskenbegrenzung.

Android-Geräteablauf: Lagezentrum → Anrufannahme beantworten → Vorschlag auswählen (noch kein Geldabzug, keine Entsendung) → bestätigte Entsendung zweier Kräfte → FMS 3 und reale Straßenrouten → Lagezentrum zeigt Anfahrt ohne fehlende Kräfte. Drei zusätzliche Screenshots sind in der verpflichtenden CI-Prüfung eingetragen.

Vorläufig: lokaler Gesamtbuild/CI/Emulator und endgültiger APK-Link werden nach tatsächlichem Abschluss hier zugeordnet. Kein v1.0.

Grenzen: Vorschlag optimiert Einsatzkosten vor Anfahrtszeit, deshalb Warnung bei drohendem Fristablauf und weiterhin freie manuelle Auswahl. Kein globaler Autodisponent über mehrere Lagen. Fahrzeiten bleiben Spielprognosen auf dem Offline-Netz. Physische Geräte-/Hörprüfung und menschliches Langzeitbalancing stehen aus. Save-Schema 7 bleibt unverändert; bisherige Migrationen und Offline-Funktionen bleiben bestehen. Die Grenzen des [Polizeichef-Stands](ACCEPTANCE_POLIZEICHEF.md) gelten weiter.
