# Phase 3A · OxygenOS Classic bottom navigation refinement

## Scope and baseline

- Branch: `refactor/media-pipeline-stability`
- Remote baseline fetched before editing: `646101eb16a9a32b3d78191b9870bd1c47456d3c`
- Scope: bottom bar ripple mask/color and label spacing only. `GalleryNavigation`, `SplashActivity`, activity-stack behavior, and media code were not changed.
- Reference: UI refinement work orders and the OxygenOS Classic design guide. The guide's values are visual references, not measured stock-ROM ripple values.

## Source findings

- `GalleryBottomBar.updateRipple()` used a rectangular full-item mask and created its ripple tint with a fixed alpha of 32/255.
- The selected state was already committed synchronously. The bottom bar does not have a selected-color animator; root Activity navigation uses the existing zero-duration window transition and first-draw gate.
- The label had a fixed 20dp height and a 3dp top gap. With font padding disabled, the fixed height could constrain larger text unnecessarily.
- The bar already used a 56dp outer height, 52dp items, 24dp icons, equal weights, always-visible labels, and a 12sp label size. Those dimensions and behaviors remain.

## Implemented values

| Property | Before | Phase 3A |
|---|---:|---:|
| Outer bar | 56dp | 56dp |
| Item height | 52dp | 52dp |
| Icon | 24dp | 24dp |
| Label | 12sp, fixed 20dp box | 12sp, `WRAP_CONTENT`, one line |
| Icon-label gap | 3dp | 2dp |
| Ripple mask inset | 0dp | 3dp horizontal, 2dp vertical |
| Ripple mask corners | rectangular | 2dp |
| Ripple alpha | fixed 32/255 | 24/255, 28/255, or 32/255 based on the resolved accent-to-surface contrast |

The ripple tint uses `OxygenThemeEngine.snapshot(...).interactiveAccent` and `pageBackground`; no accent color is hard-coded. The item View, click/focus behavior, and accessibility touch bounds remain full-size. Existing `RippleDrawable.setColor()` updates are preserved so recoloring does not replace an in-flight native ripple.

No `ValueAnimator` was added. The selected tint remains immediate and consistent with the destination's first frame; a second per-Activity color timeline would have no proven benefit under the retained cross-Activity 0ms window handoff.

## Files changed

- `app/src/main/kotlin/org/fossify/gallery/helpers/GalleryBottomBar.kt`
- `app/src/main/res/values/dimens.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/GalleryBottomBarRippleTest.kt`
- `docs/ui/oxygenos-classic-refinement/phase-3a-bottom-navigation-refinement.md`

The test additions cover same-instance ripple recoloring, semantic theme colors and contrast-derived alpha, ripple mask type, down/up and cancel behavior, immediate single navigation, equal sizing, all six tab orders, RTL plus 1.5x font scale, and the existing root handoff timing contract.

## Verification status

- `git diff --check`: passed before submission.
- Local targeted test command attempted: `./gradlew :app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.GalleryBottomBarRippleTest`.
- Local test execution was blocked before Gradle started because the wrapper needed Gradle 9.5.1 from `services.gradle.org`, which is unreachable in this environment. No local test pass is claimed.
- ARM64 FOSS Release workflow: [Arm64 FOSS Release APK](https://github.com/Pew2018/Gallery/actions/workflows/arm64-foss-release.yml). It runs the complete `testFossReleaseUnitTest` suite and assembles/verifies the arm64-v8a release APK on pushes to this branch. The exact run, commit SHA, test outcome, and artifact will be reported after the push completes.
- Pixel 8 Pro / Android 16 GPU ripple inspection remains pending: light/dark themes, pale/dark custom accents, quick taps, hold/cancel, tap during theme refresh, system animator scale 0/1x, navigation modes, and cold/warm launch. No device result is claimed.

## Preserved contracts

The custom order remains persisted through `config.bottomNavigationOrder`; cold launch continues to use `GalleryNavigation.launchDestination()` and the normalized first entry while preserving `lastMainViewAll`. `ROOT_ENTER_DURATION_MS = 0`, the 250ms first-draw bound, the finish fallback, `GalleryBottomBar.open()`, `coordinateFirstDraw()`, and `overridePendingTransition(0, 0)` were not altered. No media, scanning, indexing, sorting, search, Viewer, protected-media, or deletion-recovery code was touched.
