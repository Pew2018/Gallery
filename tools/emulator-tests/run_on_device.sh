#!/usr/bin/env bash
set -euo pipefail
OUT="$PWD/tools/emulator-tests/output"
mkdir -p "$OUT"
APP="org.fossify.gallery.debug"
capture() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  adb shell dumpsys meminfo "$APP" > "$OUT/meminfo.txt" 2>&1 || true
  # Android 14 may block shell from Android/data; the app is a debug build,
  # so run-as can still export synthetic test screenshots without relaxing security.
  mkdir -p "$OUT/viewer-evidence"
  adb exec-out run-as "$APP" tar -cf - -C files gallery-ci-evidence 2>/dev/null |
    tar -xf - -C "$OUT/viewer-evidence" 2>/dev/null || true
  adb shell screencap -p /sdcard/gallery-ci-final.png >/dev/null 2>&1 || true
  adb pull /sdcard/gallery-ci-final.png "$OUT/final.png" >/dev/null 2>&1 || true
}
trap capture EXIT
apk=$(find app/build/outputs/apk/foss/debug -maxdepth 1 -name '*.apk' -type f | head -n 1)
test -n "$apk"
adb install -r "$apk"
adb shell appops set "$APP" MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant "$APP" android.permission.READ_MEDIA_IMAGES || true
adb shell pm grant "$APP" android.permission.READ_MEDIA_VIDEO || true
adb shell pm grant "$APP" android.permission.READ_EXTERNAL_STORAGE || true
adb shell appops get "$APP" MANAGE_EXTERNAL_STORAGE > "$OUT/appops.txt"
./gradlew :app:connectedFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug "-Pandroid.testInstrumentationRunnerArguments.scale=$CI_MEDIA_SCALE" --stacktrace
