# PRIORITÄT 0: Vollständige Bundesland-Livekarte & 8/12/24-Stunden-Schichten

**Verbindliche Nutzerpräzisierung vom 2026-11-01. Diese Anforderungen übersteuern ältere Aussagen, die nur Hamburg als Kartenausschnitt oder ausschließlich 8-Stunden-Schichten vorsehen.**

## 1. Die GESAMTE Karte jedes einzelnen Bundeslandes

### Hauptanforderung
- Der Spieler wählt eines der **16 Bundesländer**. Die Haupt-Leitstellenkarte zeigt standardmäßig **die gesamte geografische Ausdehnung des gewählten Bundeslandes** mit korrekten Landesgrenzen, nicht nur eine Startstadt, Bezirksschematik oder Hamburg-Ausschnitt.
- Zum Start: **Kamera an Bundesland-Grenzen anpassen** (`fitBounds` plus angemessener Rand). Immer sichtbare Schaltfläche **„Ganzes Bundesland“**, die nach Zoomen wieder zur Gesamtansicht zurückkehrt.
- Zoomen, Wischen, Pins auswählen. Das Spiel simuliert Einsätze **überall im Bundesland**, nicht nur im Regionszentrum. Ausgewählte Einsatzfahrzeuge haben **geografische Positionen** und bewegen sich auf der Karte. Auch Wachen, Stützpunkte, Einsatzmarkierungen, offene Notrufe, zugewiesene Einheiten, Fahrtwege, laufende Einsätze und Sonderkräfte gehören auf diese Bundeslandkarte.
- Filter/Layer: **alle Einheiten**, Streife, SEK, MEK, Kripo, weitere Organisationen, freie/beschäftigte Fahrzeuge, offene/laufende/abgeschlossene Einsätze, Wachen, Gebietsabdeckung, Heatmaps.
- In der Bundesland-Gesamtansicht müssen **nicht nur 3 Beispielmarker** sichtbar sein. Marker-Clustering mit **Zähler, Zoom to cluster und Kategorieindikator**, minimap/Übersichtsleiste und „Alle anzeigen“/Auswahl sollen selbst bei Hunderten Einheiten Übersicht geben; Zahl der aktiven Marker/Zwischenfälle in der Legende anzeigen, um Verwechslungen durch Clustering zu vermeiden.
- Auf der Karte erkenne ich direkt: **Wo befinden sich Fahrzeuge? Was passiert gerade wo? Welche Fahrzeuge sind auf Anfahrt? Was ist frei? Wie lange dauert die Anfahrt?** Pins / Tooltips / Detailpanel sind Touch-interaktiv.
- Wenn mehrere Einsätze gleichzeitig vorkommen, bleiben sie in der ganzen Bundeslandkarte sichtbar (Clustering/Aggregation zulässig, aber nie ohne Zugriff verstecken). Statusänderungen und Bewegung rendern im laufenden Simulations-Tick, nicht nur beim manuellen Neuladen.
- Alle 16 Länder benötigen **reale geografische Staats-/Landesgrenzen, korrekte Kamera-Bounds, Städte und mindestens ein hinreichend nutzbares Straßennetz bzw. eine ehrlich als Simulationsnäherung deklarierte Fahrgraphik**; **nicht** nur Bundeslandmenü mit Platzhalterkarte. Hamburg behält zusätzlichen handgebauten Gameplay-Detailgrad, darf aber die landesweite Karte der anderen Länder nicht ersetzen.
- Orte bei Einsätzen müssen zu den Grenzen der jeweiligen Bundeslandgeometrie gehören (Punkt-in-Polygon/GeoJSON), und Fahrzeugstandorte dürfen nicht bei Regionwechsel versehentlich teleportieren.
- Große Bundesländer wie Bayern, NRW, Niedersachsen und Brandenburg müssen bei Overview-/Detail-Zoom performant bleiben. Niedrige Zoomstufe: offline-fähige vereinfachte Vektor-Geometrien, gestufte Detaildaten/Tiles, Marker-Clustering. Höhere Zoomstufen: Straßen, lokale Routen und Regionen nachladen. Datenquellen und Attribution dokumentieren.
- Lokal/Offline verfügbarer Basiskartenlevel für **alle** 16 Länder. Hochdetail-Kartenpakete können optional installiert werden, dürfen aber nicht Voraussetzung für die Bundesland-Gesamtansicht sein. Keine unlizenzierte Massennutzung des öffentlichen OSM-Kachelservers.
- Die Leistung der Einsatzsimulation muss aus sinnvollen geografischen Größen/Wegezeiten abgeleitet werden: bundeslandweite Distanzen, Fahrzeugstatus, Stützpunkt, Bereitschaft, begrenzte Reichweite. ETA mit physikalisch nachvollziehbarem Modell; Straßennetz-Routing wo Daten vorhanden, sonst klar als Approximation markierter Algorithmus.
- Einsätze und Personal/Dienststellen können über das Land verteilt angelegt werden. Neue Einheiten im Editor mit lat/long im Bundesland und Wachen als Heimatstandort; Kartenplatzierung per Tap/Drag.

### Interaktions-Vertical-Slice v1.0
1. Bundesland **Niedersachsen** auswählen und vollständig einschließlich Landesgrenze in der Kartenansicht sehen (nicht nur Hannover).
2. Einsatz near **Hannover** erstellen, weiteren near **Oldenburg** und dritten near **Lüneburg**; alle gleichzeitig auf derselben Kartenansicht erkennbar bzw. clusterfähig.
3. Fahrzeuge an verschiedenen Standorten zeigen; passende Streife anwählen, zur Einsatzstelle schicken, **Bewegung live verfolgen**, Einsatz abschließen und zum gesamten Bundesland zurückzoomen.
4. Dasselbe in **Bayern** mit Einsatz in München und Nürnberg sowie in **Hamburg** als dichtem Stadtstaat demonstrieren.
5. Nach Bundeslandwechsel Zustand jedes Landes erhalten; eigene Szenarien und Einsätze in gewählter Region behalten.

## 2. Konfigurierbare Schichten: 8, 12 und 24 Stunden

**Beide Modi (Karriere und Sandbox) bieten an jedem Schichtbeginn eine Auswahl:**
- **8 Stunden:** z. B. Früh 06:00–14:00 / Spät 14:00–22:00 / Nacht 22:00–06:00.
- **12 Stunden:** z. B. Tag 06:00–18:00 / Nacht 18:00–06:00.
- **24 Stunden:** komplette Ganztagesschicht, z. B. 06:00–06:00 Folgetag oder wahlweise 00:00–24:00.
- Startzeit in Schritten von einer Spielstunde frei wählbar oder aus den Vorlagen; Endzeit korrekt aus Start + Dauer berechnen, inklusive Datums-/Mitternachtswechsel.
- **Eine ausgewählte Schicht = ein spielbarer Managementtag**, egal ob 8, 12 oder 24 Stunden. Mit Schichtende erscheint die Tages-/Schichtauswertung, danach „Nächsten Tag / nächste Schicht starten“. Der Spieltagzähler steigt exakt **einmal** bei gültigem Schichtabschluss.
- Ausbildungsfortschritt in klar dokumentierten **abgeschlossenen Spieltagen**; keine dreifache Beschleunigung allein durch kurze Schichten, ohne Simulation/Balance zu begründen.
- **Zeitmodell:** `shiftStartAbsoluteMinute`, `shiftDurationMinutes` (480/720/1440), `shiftElapsedMinutes`, `shiftEndAbsoluteMinute`; Spieluhr (lokale Stunden) separat aus absoluter Simulationszeit ableiten. 22–06/18–06/24h korrekt abwickeln; **kein Doppelabschluss** bei Tageswechsel.
- Bei jedem Schichtende: Zusammenfassung mit Zahlen für Notrufe, Erfolge, unerledigte/übergebene Einsätze, SEK/MEK-Einsätze, Geld, Gehälter, Wartung, Müdigkeit, Trainingsfortschritt, Reputation, Gesamtstunden.
- **Rest-/Müdigkeitssystem:** längere 12/24h Schichten haben glaubwürdige Personalbelastung. Personalwechsel, Bereitschaft, Ruhezeiten, Schichtplan und Rettung offener Lagen; wer erschöpft ist, ist weniger effizient. Für 24h Schicht notwendige interne Rotationen und Pausen verfügbar, statt alle Polizisten ununterbrochen 24h tätig zu lassen.
- Warnung bei kritischen offenen Einsätzen am Ende; automatische oder manuelle nachvollziehbare **Übergabe**. Kein Datenverlust.
- Spieltempo 1× / 4× / 8× / optional 16× plus Pause. Optionaler **„Schicht vorspulen“**-Mechanismus nur wenn keine bedienpflichtigen Ereignisse offen sind; die Simulation muss laufende Einsätze, Löhne, Fristen, Training konsistent behandeln.
- Karriere kann Schichtwahl mit Ressourcen/Einsatzlast balancieren; Sandbox bietet Einstellung „freie Schichtlänge“ und optional freie Personalermüdung.

## 3. Nicht verhandelbare Release-Gates

1. Jede Bundeslandkarte per Fit-to-Bounds komplett sichtbar. Alle 16 Regionen haben korrektes Boundary-Asset; optisch und funktional verifizierbar (nicht nur Dropdown/Platzhalter).
2. In der Gesamtübersicht eines großen Bundeslands mindestens 30 bewusst räumlich verteilte Einheiten/Einsätze anzeigen, deren Pins/Cluster anwählbar bleiben. Mindestens eine Anfahrt über Landkreisgrenze hinweg zeigen.
3. Fahrzeuge werden während laufender Spielzeit sichtbar bewegt, auch wenn die Karte auf Gesamtansicht steht.
4. Filter und schnelle Zugriffsmöglichkeiten für freie Einheiten, SEK, MEK und aktive P1/P2/P3-Einsätze.
5. 8-, 12- und 24-Stunden-Schichten jeweils in Karriere und Sandbox spielbar; Nacht und Mitternachtswechsel sowie Report/Weiter-Button nachweislich korrekt.
6. Ein 24-Stunden-Spieltag mit mehreren Schicht-/Personalrotationen hält an, ohne Einheiten/Notrufe zu verlieren; Dauer und Kosten korrekt.
7. Tests: `full_state_bounds_each_region`, `incident_within_state`, `vehicles_visible_across_region`, `region_switch_retains_vehicles`, `cluster_open_and_filter`, `routing_cross_district`, `shift_8h`, `shift_12h`, `shift_24h`, `midnight_once`, `training_by_completed_shift`, `handoff_24h`.
8. Screenshots/Video-Prüfung: **Niedersachsen Gesamtkarte**, **Bayern Gesamtkarte**, **Hamburg Livekarte**, **Schichtwahl 8/12/24**, **24-Stunden-Report**.

## 4. Einordnung
Diese Anforderungen sind eine **Spezifikationsänderung für die künftige Version 1.0** und werden noch implementiert. Die bisherige Alpha 0.2 enthält diese bundeslandweite Livekarte und die 12/24h-Schichten noch nicht. Codex muss sie entwickeln und testen, nicht als bereits geliefert melden.
