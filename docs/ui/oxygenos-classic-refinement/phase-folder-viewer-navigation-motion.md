# Phase E — Folder and Viewer hierarchy transitions

## Baseline and implementation

- Baseline: `82beb4fd7466081cf8834801541f0d79a23a94c0`, after Phase D's passing Actions run.
- Installs one shared Window transition style on `MediaActivity` and the shared `BaseViewerActivity`. This covers ordinary folders, .nomedia folders, favorites, recycle bin, profile media grids, internal full-screen Viewer, and the external single-media Viewer path.
- Open motion is a short 14dp forward translation with alpha; close motion restores the previous page with a 4dp reverse movement while the current page exits 10dp. Enter/exit durations are 170ms/140ms, declared as shared XML tokens. These are engineering choices, not measured OxygenOS ROM timings.
- The transition runs at the Activity Window layer. It does not animate RecyclerViews, rebind a ViewPager, publish media early, alter Viewer order, or restore scroll position.
- `GalleryBottomBar.open()` and its 0ms Activity transition/readiness gate are unchanged. Its explicit no-transition override remains the owner of root navigation.
- No predictive-back progress handler was added. No shared-element media animation was added because media identity, bounds and protected authorization continuity cannot be guaranteed across every path.
- No query, media scan, sort, deletion recovery, authorization-session or photo-profile loader code changed.

## Files changed

- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicActivityMotion.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/BaseViewerActivity.kt`
- `app/src/main/res/values/oxygenos_classic.xml`
- `app/src/main/res/anim/oxygen_classic_page_*.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/OxygenClassicActivityMotionTest.kt`
- This phase document.

## Verification

- The deterministic test installs the shared Activity Window style and verifies the transition resource tokens.
- Full FOSS Release unit-test suite, UI/media regression checks, ARM64 APK assembly and artifact validation are pending for this phase.
- Pixel 8 Pro Android 16 is **待真机验收** for page direction, correct backgrounds, folder scroll restoration, Viewer return position and preserved root-tab first-draw behavior.

## Limits

Window transitions are controlled by Android's Activity WindowManager and animator scale. Exact OEM motion fidelity and visual continuity across external callers require real-device validation.
