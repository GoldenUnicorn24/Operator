# Grafik- und Audioquellen

## Neue Originalgrafiken (2026-11-01)

Die sieben Dateien `app/src/main/assets/images/vehicle_{sek,mek,kripo,bereitschaft,hund,verkehr,heli}.svg` sind eigens für dieses Repository entworfene Vektorillustrationen. Auch `gear_torch.svg`, `gear_gloves.svg` und `gear_service_weapon.svg` sind neue Originalzeichnungen. Ihre SVG-Dateien sind zugleich die bearbeitbaren Quellen; sie enthalten keine eingebetteten Fremdbilder, externen Fonts, Markenlogos oder Netzwerkanfragen. Die dargestellten Fahrzeuge und Kennzeichnungen gehören zur fiktiven Spielflotte.

Diese neuen SVG-Grafiken und die unten genannten synthetisierten WAV-Dateien sind unter **CC0 1.0 Universal** freigegeben: <https://creativecommons.org/publicdomain/zero/1.0/>. Diese Freigabe gilt ausschließlich für die hier ausdrücklich aufgelisteten neuen Dateien, nicht für vorhandene Alpha-Grafiken oder das gesamte Repository.

`images/mek_team.webp` wurde mit dem verfügbaren OpenAI-Bildgenerator speziell für dieses Projekt am 2026-11-01 neu generiert. Inhalt: drei fiktive erwachsene Beamte in ziviler Kleidung neben einem fiktiven zivilen Fahrzeug bei Dämmerung; keine Vorlage eines bestehenden Spiels, keine reale Person, kein reales Abzeichen. Herkunft: neue Generierung aus Text, keine Drittdatei. Das generierte Original (1536×1024) wurde für die APK auf 1000×667 verkleinert und als WebP mit Qualität 80 komprimiert; das Original bleibt im Generierungsarbeitsverzeichnis erhalten. Die weitere Verwendung richtet sich nach den für die Generierung geltenden OpenAI-Nutzungsbedingungen; es wird keine fremde CC-Lizenz behauptet.

## Originale Sounds

Alle zehn Dateien in `app/src/main/assets/audio/` werden ausschließlich aus mathematischen Oszillatoren und deterministischem Rauschen erzeugt. Sie verwenden keine Aufnahmen, Samplebibliotheken, Polizeifunkmitschnitte oder Sounds vorhandener Spiele.

Erzeugung aus dem Repository-Wurzelverzeichnis:

```sh
python3 tools/generate_audio.py
```

Die Synthesequelle ist `tools/generate_audio.py`. Format: 22.050 Hz, mono, PCM16 WAV; Anti-Klick-Hüllkurven, maximale Aussteuerung 68 %, maximal vier Sekunden. Die Gesamtdatenmenge beträgt etwa 423 KiB.

| Datei / Cue | Gestaltung | Mixerkanal |
| --- | --- | --- |
| incoming_call | Zweiton-Hinweis 660/880 Hz | SFX |
| p1_alarm | Vier kurze abwechselnde hohe/tiefe Töne | SFX |
| p2_alarm | Kurzes fallendes Zweiton-Signal | SFX |
| radio_squelch | Abklingendes synthetisches Rauschen | Funk |
| dispatch_ack | Zwei kurze Bestätigungstöne | Funk |
| unit_departure | Synthetischer ansteigender Motorimpuls | SFX |
| incident_success | Aufsteigender Dreiklang | SFX |
| incident_failure | Fallender tiefer Zweiklang | SFX |
| shift_end | Vierstufige Abschlussmelodie | SFX |
| ambient_dispatch | Leises synthetisches Rauschen und Grundtöne, einmalig 4 s | Ambiente |

Die Signale sind fiktive Spielsignale; sie bilden keine echten polizeilichen Signalcodes nach. Ambiente startet nur durch expliziten Cue-Aufruf und läuft nicht von selbst endlos. Der Mixer bietet Master/Funk/SFX/Ambiente und Mute. Er erstellt WebAudio erst beim ersten Resume-/Play-Aufruf und respektiert Browser-Autoplay-Regeln. Er unterdrückt Duplikate, begrenzt gleichzeitige Quellen, bricht aktive und noch ladende Quellen bei Suspend ab und speichert Cue-Ereignisse auch stumm für die textuelle Darstellung. Android-AudioFocus und App-Lifecycle werden vom Host an die UI übergeben.

## Vorhandene Alpha-0.2-Grafiken

Die vorhandenen WebP-Porträts, Einsatz-, Fahrzeug-, Trainings- und Leitstellenbilder sowie die acht ursprünglichen Ausrüstungs-SVGs wurden unverändert aus dem vom Nutzer bereitgestellten Alpha-0.2-Stand übernommen. Die Alpha beschreibt die Bilder als KI-generierte Spielgrafiken. Der Prototyp bleibt als Referenz erhalten. Eine unabhängige Herstellungs- oder Lizenzhistorie über diese im Repository vorhandene Angabe hinaus liegt nicht vor; hierfür wird keine zusätzliche Drittanbieter-Lizenz erfunden.

## Inhalt

`web/data/catalog.ts` enthält 48 eigens verfasste Einsatzvorlagen, davon vier zusätzliche Hamburg-Vorlagen, zehn Lehrgänge und elf Ausrüstungsgegenstände. IDs und Kernnamen der neun ursprünglichen Alpha-Einsatzvorlagen sowie alle ursprünglichen Kurs-/Ausrüstungs-IDs bleiben stabil. Alle Namen und Ereignisse sind fiktiv. Entscheidungen beschreiben Ressourcen, Zeit, Betreuung und Dokumentation auf Managementebene.

SEK hat besondere Gefahrenlagen, MEK Observation und Fahndung, Kripo Ermittlung und Spuren, die Streife Aufnahme und Absicherung; weitere Rollen sind Bereitschaftspolizei, Hundestaffel, Verkehr und Luftunterstützung. Geschwindigkeiten sind Simulationsparameter und keine Behauptung über reale Fahrleistungen.
