# EINSATZLEITSTELLE 110 — Native Android V1

This Android application is **not a WebView**. It runs a native Kotlin game engine, Android Views, a Canvas-rendered map, local JSON/SharedPreferences saves, and native synthesized SoundPool audio. No HTML/JS files are packaged in the APK.

- 16 state boundary polygons and cities packaged for offline rendering.
- Native map with road tiles on demand via OpenStreetMap raster tiles where online. Note: public OSM tiles are online-only, stored only in a short-lived, limited cache, **not** bulk-downloaded. An offline simulation map is used when tiles are unavailable. Realistic road-network navigation remains future work: unit ETAs and trajectories are approximate.
- 8/12/24-hour shift plans, career/sandbox, training, incident dispatch, SEK/MEK, equipment, custom incident and unit creation, local Save and SAF JSON import/export, native audio.
- Self-authored UI and sound effects, no assets from the 911 Operator franchise.

## Build
Android Studio with JDK17 and Android SDK 35; `gradle :app:assembleDebug`, artifact `app/build/outputs/apk/debug/app-debug.apk`. CI uploads artifact and creates GitHub release `v1.0.0-native` as a prerelease for review.

## Limitations
An HTML/JavaScript prototype is retained separately under `prototype/` as an archival reference. The native version does not load it. This is a native V1 candidate, with on-device usability review and map tile-license compliance still required. Native offline road routing (full road graphs for all states), deep branched editor, and full 3D/photorealistic visuals are not implemented.
