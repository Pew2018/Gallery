#!/usr/bin/env bash
set -euo pipefail
OUT="$PWD/tools/emulator-tests/output"
mkdir -p "$OUT"
APP="org.fossify.gallery.debug"
capture() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1 || true
  adb shell dumpsys meminfo "$APP" > "$OUT/meminfo.txt" 2>&1 || true
  adb shell dumpsys window windows > "$OUT/window.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
  adb shell df -h /sdcard > "$OUT/storage.txt" 2>&1 || true
  # Android 14 may block shell from Android/data; the app is a debug build,
  # so run-as can still export synthetic test screenshots without relaxing security.
  mkdir -p "$OUT/viewer-evidence"
  adb shell run-as "$APP" ls -la files/gallery-ci-evidence > "$OUT/viewer-evidence-list.txt" 2>&1 || true
  adb exec-out run-as "$APP" tar -cf - -C files gallery-ci-evidence \
    > "$OUT/viewer-evidence.tar" 2> "$OUT/viewer-export-error.txt" || true
  if [[ -s "$OUT/viewer-evidence.tar" ]]; then
    tar -xf "$OUT/viewer-evidence.tar" -C "$OUT/viewer-evidence" 2>> "$OUT/viewer-export-error.txt" || true
  fi
  adb shell screencap -p /sdcard/gallery-ci-final.png >/dev/null 2>&1 || true
  adb pull /sdcard/gallery-ci-final.png "$OUT/final.png" >/dev/null 2>&1 || true
}
trap capture EXIT
printf "suite=%s\nscale=%s\nsha=%s\n" "${CI_SUITE:-smoke}" "${CI_MEDIA_SCALE:-smoke}" "${GITHUB_SHA:-unknown}" > "$OUT/inputs.txt"
# Host-side media generation creates 50k truly distinct PNG pixels in seconds
# and retains source timestamps; this is faster than emulator PNG recompression.
python3 tools/emulator-tests/generate_media.py \
  --scale "$CI_MEDIA_SCALE" \
  --output "$RUNNER_TEMP/gallery-ci-media.tar" \
  --manifest "$OUT/media-manifest.json"
# Android 14+ blocks shell writes under Download on some emulator images.
# ADB's /data/local/tmp is shell-owned and supports push safely without root.
adb shell mkdir -p /sdcard/Pictures
adb push "$RUNNER_TEMP/gallery-ci-media.tar" /data/local/tmp/gallery-ci-media.tar
adb shell tar -xf /data/local/tmp/gallery-ci-media.tar -C /sdcard/Pictures
adb shell rm /data/local/tmp/gallery-ci-media.tar
adb shell "find /sdcard/Pictures/GalleryCI -type f | wc -l" > "$OUT/fixture-file-count.txt"
apk=$(find app/build/outputs/apk/foss/debug -maxdepth 1 -name '*.apk' -type f | head -n 1)
test -n "$apk"
adb install -r "$apk"
adb shell appops set "$APP" MANAGE_EXTERNAL_STORAGE allow
adb shell pm grant "$APP" android.permission.READ_MEDIA_IMAGES || true
adb shell pm grant "$APP" android.permission.READ_MEDIA_VIDEO || true
adb shell pm grant "$APP" android.permission.READ_EXTERNAL_STORAGE || true
adb shell appops get "$APP" MANAGE_EXTERNAL_STORAGE > "$OUT/appops.txt"
./gradlew :app:connectedFossDebugAndroidTest -PEMULATOR_TEST_BUILD_TYPE=debug "-Pandroid.testInstrumentationRunnerArguments.scale=$CI_MEDIA_SCALE" \
  "-Pandroid.testInstrumentationRunnerArguments.suite=${CI_SUITE:-smoke}" --stacktrace
