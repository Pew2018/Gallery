# Stage 1: root navigation first frame

Date: 2026-11-13 (Hong Kong time)

## Audit

- Start SHA: `753c52214faf1a862f15b95e6478c4e2d1e77057`.
- `GalleryBottomBar.attach()` already coordinates retirement of the outgoing root with a drawn destination state. `MainActivity` and root `MediaActivity` identify visible content, loading feedback, and empty/error states in `frameState`; the blank state remains gated. Loading feedback is published before traversal when needed.
- Defect confirmed in `GalleryBottomBar.attach()`: after the first-frame coordination was installed, it set the entire directories or media RecyclerView to `alpha = 0` and scheduled a second 180 ms fade. This could hide cached content and duplicate the first-draw handoff.
- No changes were made to request tokens, stale-navigation protection, loading publication, root-window opacity, or lifecycle cleanup.

## Change

Removed only the second full-grid alpha animation. Added a regression test that attaches the root bar to a root-transition destination with a populated grid surface and asserts alpha remains 1 both immediately and after the first traversal/watch interval.

Files:

- `app/src/main/kotlin/org/fossify/gallery/helpers/GalleryBottomBar.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/GalleryRootTransitionTest.kt`

## Automated validation

All commands ran in `/private/tmp/Gallery-stage1-validation`, with JBR 21 and the installed Android SDK.

- Targeted: `:app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.GalleryRootTransitionTest` — 12/12 passed, 0 failures, 0 skipped.
- Full JVM: `:app:testFossDebugUnitTest` — 1,110/1,110 passed across 163 suites, 0 failures, 0 skipped.
- ARM64 build: `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` — passed.
- APK package/version: `org.fossify.gallery`, `1.14.1` / `32`; bundled ABI: `arm64-v8a` only.
- Release signing is not configured; the APK is unsigned and `apksigner verify` reports the expected missing manifest signature. It is not directly installable without signing.
- APK SHA-256: `cbb77777e03264007f0d32c3f56d62ee0a7e46a4a8c9c55a4078c48aaea00a52`.

## Visual/device acceptance

- Pixel 8 Pro: `NOT TESTED`; no device is connected.
- Emulator instrumentation and continuous-frame capture: `NOT TESTED` in this stage; ADB inventory was empty and ADB daemon startup was denied by the sandbox.
- Existing prior screen recordings describe the reported white interval but are not a before/after capture for this change.
- Stage status: `BUILD_PASSED / DEVICE_PENDING`; not accepted until device and visual criteria are checked.

