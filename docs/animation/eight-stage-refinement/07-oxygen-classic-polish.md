# Stage 7: OxygenOS Classic motion and visual consistency

Date: 2026-11-13 (Hong Kong time)

## Audit and change

- Start SHA: `9254f0c7c325cbe3b4e551db68f114eee647eba8`.
- Media shared-element transitions use the shared 120 ms timing; scrim and loading motion use separate tokens. Viewer chrome intentionally keeps its 225 ms enter and 195 ms exit timings. Common view motion uses the same restrained easing curve, and animation cleanup resets terminal view state.
- After stage 5 removed the folder-content fade, the 100 ms `ALBUM_CONTENT_ENTER_MS` constant and `AlbumPageTransition.FADE_DURATION_MS` alias had no runtime caller. Removed this dead timing contract and its assertion. No live durations, curves, placeholder colors, or gradients were retuned without comparative screen evidence.
- No additional confirmed global timing conflict or visual defect was found in the reviewed code/resources.

## Validation

Validation ran in `/private/tmp/Gallery-stage1-validation` with JBR 21 and the installed Android SDK.

- Motion/transition suites: 34 tests across 9 suites passed (including `OxygenClassicActivityMotionTest`, `OxygenClassicMotionTest`, `ViewerMotionTest`, `GalleryRootTransitionTest`, shared transition and settings motion suites).
- Full `:app:testFossDebugUnitTest`: 1,111/1,111 passed across 163 suites; 0 failures, 0 skipped.
- `:app:compileFossDebugAndroidTestKotlin`: passed.
- ARM64 FOSS Release build passed. APK: `Gallery-1.14.1-32-foss-arm64-release-stage7-9254f0c7-unsigned.apk`; package `org.fossify.gallery`, version 1.14.1 (32), arm64-v8a only. SHA-256: `0c9c58b1789d82862b6b1c0ddbab4e05db23f52e4190850c8f56abfb4fe69dd5`. The APK is unsigned because release signing is not configured.

## Visual/device acceptance

- Pixel 8 Pro review at animator scales 0×, 0.5×, 1×, and 1.5× across navigation, folders, photos, viewer, progress, refresh, sort, and video: `NOT TESTED`.
- No timing changes are claimed to be visually accepted. Stage status: `BUILD_PASSED / DEVICE_PENDING`.
