# Phase 3 — Settings page transition repair

## Baseline and evidence

- Repository branch: `refactor/media-pipeline-stability`.
- Remote HEAD audited before this change: `0937418ee46303e031ccecc084ad7cc410811efb` (the supplied historical SHA).
- Device report: Google Pixel 8 Pro, Android 16. The user reports old rows remaining under a new toolbar title, overlapping content, abrupt replacement, and similar artifacts when returning from categories.
- The supplied 14.04-second screen recording was opened and sampled at 4 frames per second. It documents the affected settings categories and the separate theme pages. That sampling interval is longer than the 180 ms transition, so the extracted frames do not establish the exact intermediate GPU frame sequence. The transient artifacts remain user-reported evidence pending a new on-device recording.
- The project OxygenOS classic design guide describes a fixed, 56 dp toolbar and low-decoration settings rows. It does not specify an exact original ROM page-transition curve or duration.

## Audited path and root cause

Ordinary settings categories share one `settingsHolder` inside `settingsNestedScrollview`. `SettingsActivity.openSettingsCategory()` and `navigateSettingsBack()` change `VISIBLE`/`GONE` rows in place and update the toolbar. `ClassicSettingsPageTransition` captures the outgoing scroll viewport, places it in `ViewOverlay`, changes the live views, and animates the snapshot and incoming holder.

The previous snapshot alpha formula ended at `PAGE_ALPHA_FLOOR` (0.92), so the outgoing frame stayed almost opaque throughout the animation and was removed only at its end. The incoming holder began at the same 0.92 alpha. This made the incoming page difficult to distinguish until the overlay disappeared. The toolbar was outside the snapshot and updated during `prepareTarget`, so it could show the destination title while the outgoing list still covered the viewport. The transition started from `post()`, which did not wait for a completed target layout. The `NestedScrollView` also used `wrap_content` within the CoordinatorLayout scrolling behavior, allowing the viewport size to depend on the selected page content.

These are confirmed source-level causes. The screen recording's low-rate sampling cannot independently confirm the exact transient frames.

## Selected approach

Retained the snapshot route (A): it already preserves the single set of business and interactive views, and the defects can be addressed without introducing another navigation layer. `OxygenThemeActivity.navigateColors()` also calls the shared controller, while its app-icon entry point opens the activity independently. The shared controller's visual transition changes therefore apply to the two theme color subpages as well; the theme page still owns its page construction, color selection, and scroll-position behavior. The title-commit callback is optional and is supplied only by `SettingsActivity`, so the theme toolbar behavior is unchanged.

The outgoing snapshot now moves away and fades from fully opaque to transparent while the target holder remains opaque and moves from the same direction toward its final position. This keeps the page surface covered and lets the target show continuously underneath. The toolbar title commits when the outgoing snapshot reaches the final portion of its exit; cancellation and normal completion commit it as well. Target visibility and scroll restoration are prepared once, then the first animated frame waits for `OnPreDraw` after the layout pass. The scroll viewport now fills the CoordinatorLayout's available scrolling area.

## Changes

- `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicSettingsPageTransition.kt`
  - Removed the near-opaque alpha floor; outgoing alpha is `(1 - progress) * 255`.
  - Keeps incoming content fully opaque, avoids the prior double-alpha dip, and translates both visual layers in opposite directions.
  - Waits for pre-draw after target preparation, captures only the visible viewport, and guarantees normalization and bitmap/overlay cleanup for completion, cancellation, invalid layout, activity teardown, and allocation failure.
  - Tracks the current run by container so an obsolete callback cannot remove a newer run's overlay or bitmap.
  - At 1080 × 2000 pixels, a single ARGB_8888 viewport snapshot is about 8.2 MiB. Only one snapshot per container is retained; allocation remains on the UI thread at the start of navigation and requires device frame-time validation.
- `app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt`
  - Keeps category rows in place, defers the toolbar title until the old frame is nearly gone, and delegates cancellation/teardown cleanup to the controller.
- `app/src/main/res/layout/activity_settings.xml`
  - Uses a viewport-sized `NestedScrollView` with the existing `fillViewport` and CoordinatorLayout scrolling behavior.
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicSettingsPageTransitionTest.kt`
  - Adds rendered pixel sampling at intermediate times and verifies monotonic reveal of the incoming page and the final pixels. Keeps the pre-existing divider geometry regression test.
- `app/src/test/kotlin/org/fossify/gallery/helpers/SettingsPageMotionTest.kt`
  - Continues to cover both directions, RTL, zero system animation scale, rapid navigation, teardown, and final transform normalization.

## Theme page isolation

`OxygenThemeActivity.navigateColors()` rebuilds the programmatic page inside its own `ScrollView` and shares `ClassicSettingsPageTransition`. A dedicated regression test exercises entering and leaving “More colors”, including its independent `moreColors` state and final scroll transforms. The theme picker, color selection state, instant color application, and scroll-position restoration code are unchanged. The two theme subpages still require device regression confirmation after installation.

## Verification and remaining acceptance

- Source audit and the supplied clip review are complete; extracted clip frames are too sparse to measure the 180 ms transient motion.
- Automated test execution was attempted with `./gradlew :app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.SettingsPageMotionTest`, but the checkout has no cached Gradle 9.5.1 distribution and the runtime could not reach `services.gradle.org`. Therefore no local test or compile result is claimed.
- Robolectric pixel sampling verifies the drawable composition in software, not Pixel 8 Pro GPU presentation, dropped frames, or device-specific insets.
- Pending: run the new and existing relevant tests in CI, build and inspect the ARM64 FOSS Release artifact for the final commit, and have the user repeat the category, return, rapid-navigation, RTL, zero-animation, and two theme-page checks on the Pixel 8 Pro.
