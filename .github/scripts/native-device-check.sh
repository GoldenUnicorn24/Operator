#!/usr/bin/env bash
set -euo pipefail
mkdir -p runtime
adb shell svc wifi disable
adb shell svc data disable
# The emulator's own launcher (Quickstep) sometimes reports an ANR on slow CI hosts; hide such
# system error dialogs so they do not cover evidence screenshots. App crashes still fail the tests.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
adb install -r release/Einsatzleitstelle110-Native-Leitstelle.apk
adb install -r release/instrumentation.apk
adb shell am instrument -w -r de.einsatzleitstelle110.test/androidx.test.runner.AndroidJUnitRunner | tee runtime/instrumentation.txt
adb root
adb wait-for-device
adb pull /data/anr runtime/anr || true
adb pull /sdcard/Android/data/de.einsatzleitstelle110/files runtime/screens || true
adb exec-out screencap -p > runtime/device-final.png
adb logcat -d > runtime/logcat.txt
adb shell dumpsys activity lastanr > runtime/last-anr.txt || true
python3 - <<'PY'
from pathlib import Path
text = Path('runtime/instrumentation.txt').read_text()
assert 'OK (4 tests)' in text and 'FAILURES!!!' not in text, text
for name in ['wandsbek','officer','equipment','24h-report','niedersachsen','bayern','hamburg-live','dispatch-panel','vehicle-route','hamburg-8h-report','career','editor']:
    p = Path(f'runtime/screens/native-{name}.png')
    assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), f'Missing actual screenshot: {p}'
PY
