#!/usr/bin/env bash
set -euo pipefail
mkdir -p runtime
adb shell svc wifi disable
adb shell svc data disable
# The emulator's own launcher (Quickstep) sometimes reports an ANR on slow CI hosts; hide such
# system error dialogs so they do not cover evidence screenshots. App crashes still fail the tests.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
# Android 35 still shows the launcher's ANR dialog; the tests start the app directly, so the
# emulator launcher is simply disabled for this run (never part of the app or its checks).
adb shell pm disable-user --user 0 com.android.launcher3 || true
adb shell am force-stop com.android.launcher3 || true
adb install -r release/Einsatzleitstelle110-Native-Leitstelle.apk
adb install -r release/instrumentation.apk
adb shell am instrument -w -r de.einsatzleitstelle110.test/androidx.test.runner.AndroidJUnitRunner | tee runtime/instrumentation.txt
# Capture required app evidence before optional root diagnostics restart adbd.
adb pull /sdcard/Android/data/de.einsatzleitstelle110/files runtime/screens || true
adb exec-out screencap -p > runtime/device-final.png || true
adb logcat -d > runtime/logcat.txt || true
adb shell dumpsys activity lastanr > runtime/last-anr.txt || true
adb root || true
adb wait-for-device
adb pull /data/anr runtime/anr || true
python3 - <<'PY'
from pathlib import Path
text = Path('runtime/instrumentation.txt').read_text()
assert 'OK (8 tests)' in text and 'FAILURES!!!' not in text, text
for name in ['caller-callback','save-slots','landscape-dispatch','chief-station','chief-fleet','chief-personnel','chief-reloaded','wandsbek-patrol','signal-comparison','fms-radio','wandsbek-overview','wandsbek-street-names','wandsbek-oneway-drive','wandsbek','officer','equipment','24h-report','niedersachsen','bayern','hamburg-live','dispatch-panel','vehicle-route','hamburg-8h-report','career','editor','tutorial']:
    p = Path(f'runtime/screens/native-{name}.png')
    assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), f'Missing actual screenshot: {p}'
PY
