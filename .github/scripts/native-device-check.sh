#!/usr/bin/env bash
set -euo pipefail
mkdir -p runtime
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
test "$(adb shell settings get global airplane_mode_on | tr -d '\r')" = 1
# The emulator's own launcher (Quickstep) sometimes reports an ANR on slow CI hosts; hide such
# system error dialogs so they do not cover evidence screenshots. App crashes still fail the tests.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
# Android 35 still shows the launcher's ANR dialog; the tests start the app directly, so the
# emulator launcher is simply disabled for this run (never part of the app or its checks).
adb shell pm disable-user --user 0 com.android.launcher3 || true
adb shell am force-stop com.android.launcher3 || true
adb install -r "${APP_APK:-release/Einsatzleitstelle110-Native-Leitstelle.apk}"
if [[ -n "${APP_APK:-}" ]]; then
    adb shell am start -W -n de.einsatzleitstelle110/.MainActivity > runtime/launch.txt
    grep -q '^Status: ok' runtime/launch.txt
    adb shell pidof de.einsatzleitstelle110 >> runtime/launch.txt
    adb exec-out screencap -p > runtime/preview-launch.png
    adb shell am force-stop de.einsatzleitstelle110
fi
adb install -r release/instrumentation.apk
instrument_status=0
timeout 40m adb shell am instrument -w -r de.einsatzleitstelle110.test/androidx.test.runner.AndroidJUnitRunner \
    | tee runtime/instrumentation.txt || instrument_status=$?
# Capture required app evidence before optional root diagnostics restart adbd.
adb pull /sdcard/Android/data/de.einsatzleitstelle110/files runtime/screens || true
adb exec-out screencap -p > runtime/device-final.png || true
adb logcat -d > runtime/logcat.txt || true
adb shell dumpsys activity lastanr > runtime/last-anr.txt || true
if (( instrument_status != 0 )); then
    echo "Instrumentation exited with status $instrument_status" >&2
    exit "$instrument_status"
fi
python3 - <<'PY'
import os
import re
from pathlib import Path
text = Path('runtime/instrumentation.txt').read_text()
result = re.search(r"^OK \((\d+) tests?\)$", text, re.M)
expected_minimum = 14 if os.getenv("APP_APK") else 10
assert result and int(result[1]) >= expected_minimum and 'FAILURES!!!' not in text and 'INSTRUMENTATION_FAILED' not in text, text
for name in ['phone-ringing','phone-call','phone-result','incident-desk','dispatch-advice','incident-desk-dispatched','caller-callback','save-slots','landscape-dispatch','chief-station','chief-fleet','chief-personnel','chief-reloaded','wandsbek-patrol','signal-comparison','fms-radio','wandsbek-overview','wandsbek-street-names','wandsbek-oneway-drive','wandsbek','officer','equipment','24h-report','niedersachsen','bayern','hamburg-live','dispatch-panel','vehicle-route','hamburg-8h-report','career','editor','tutorial']:
    p = Path(f'runtime/screens/native-{name}.png')
    assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), f'Missing actual screenshot: {p}'
if os.getenv("APP_APK"):
    for name in ("radio-archive", "parallel-incident"):
        evidence = Path(f"runtime/screens/native-{name}.png")
        assert evidence.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), f'Missing actual screenshot: {evidence}'
PY
