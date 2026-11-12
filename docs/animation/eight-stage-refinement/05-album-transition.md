# Stage 5: folder-to-album transition

Date: 2026-11-13 (Hong Kong time)

## Audit and change

- Start SHA: `d9b019f20be1544f165e94f1ed89881748644e9e`.
- The album entrance postponed the shared cover transition until the matching cover was decoded (with a bounded timeout fallback), but `releaseAlbumTransition()` also set every visible non-cover row to alpha 0 and faded it back in. This introduced an extra content entrance and could make already-loaded rows flash or disappear during folder navigation.
- Removed the non-cover alpha mutation and animator. The release still waits for pre-draw, checks the Activity lifecycle, and starts the postponed shared cover transition.
- Expanded the device round-trip fixture to five images and added an assertion that all visible grid rows remain fully opaque when the shared cover is mapped.

## Validation

Validation ran in `/private/tmp/Gallery-stage1-validation` with JBR 21 and the installed Android SDK.

- `:app:compileFossDebugAndroidTestKotlin`: passed; the expanded album transition instrumentation test compiles.
- `:app:testFossDebugUnitTest`: 1,111/1,111 passed across 163 suites; 0 failures, 0 skipped.
- `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`: passed.
- APK: `Gallery-1.14.1-32-foss-arm64-release-stage5-d9b019f2-unsigned.apk`; package `org.fossify.gallery`, version 1.14.1 (32), arm64-v8a only. SHA-256: `d1138619d80443af0a222052a1c83a2869dbbeb8d50fc823ad4ec9d6c41267ef`.
- The APK is unsigned because no release signing configuration is available. The instrumentation test was compiled but not run: no Android device is connected.

## Visual/device acceptance

- Pixel 8 Pro folder-card entrance and Back round-trips, including continuous-frame inspection: `NOT TESTED`.
- Device acceptance remains pending. The source test assertion and successful build do not count as visual device evidence.
