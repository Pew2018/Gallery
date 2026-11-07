# Phase 3B · OxygenOS Classic bottom navigation Material Motion

## Baseline and scope

- Repository: `Pew2018/Gallery`
- Base branch: `refactor/media-pipeline-stability`
- Fetched base SHA: `6a44b51ca525a356f14a229004f2690cfc5b5f67`
- Work branch: `refactor/oxygenos-classic-bottom-bar-material-motion`
- Initial work-branch SHA: `6a44b51ca525a356f14a229004f2690cfc5b5f67`
- Scope: native bottom-bar ripple visibility, selected-state handoff, Activity window timing, regression tests, and this document.
- No media scanning, indexing, Room, sorting, search, Viewer, deletion, or file-operation logic is modified.

## Source audit and event sequence

The item is a full-size clickable and focusable `LinearLayout` with a native `RippleDrawable` background. The CI regression test confirmed that the root `CoordinatorLayout` can defer its child's pressed state on `ACTION_DOWN` while it decides whether the gesture belongs to a scrolling parent. This delayed the first visible ripple. The item now updates its native drawable hotspot and pressed state immediately on `ACTION_DOWN`, then returns the event to the normal `View` touch handler. Android still owns Ripple rendering and `ACTION_UP`, `ACTION_CANCEL`, click, and long-press handling. The click listener synchronously updates source tab selection, then invokes the Activity navigation callback. `GalleryBottomBar.open()` calls `startActivity()` immediately and requests a zero-duration window transition. The destination prepares an opaque, theme-colored root surface, gates its first content draw for at most 250ms, acknowledges a real draw, and only then finishes the source Activity. A separate fallback finishes the source after 530ms if the destination never acknowledges.

This yields two different visual lifetimes:

1. The native ripple begins from the touched item while the source window receives `ACTION_DOWN`. The normal click callback happens on `ACTION_UP`; it does not wait for an animation timer.
2. The ripple remains owned by the source View and Drawable. The new Activity has a distinct window and Drawable instance. Its opaque background can cover the source window as soon as the destination is presented, even though the source Activity is kept alive until the destination draw acknowledgement.

The code and CI test prove the delayed pressed-state cause and its immediate-state repair, native Drawable ownership, destination opacity, zero-duration Activity transition, and bounded source lifetime. They do not prove the exact Pixel GPU frame at which the ripple becomes obscured; that needs device capture. The remaining cross-Activity limitation is therefore a source-backed inference, not a claimed device observation. Native ripple animation cannot be shared automatically between the two Activity windows.

The current code does not rebuild item backgrounds during ordinary recoloring: `updateRipple()` only calls `RippleDrawable.setColor()` when the semantic color changes. `refreshAll()` retains item Views and their Drawable instances when ordering is unchanged and reuses both when the user changes the order. The bar is disposed from both root Activity `onDestroy()` paths; root draw listeners and callbacks are removed by their cleanup closure.

## Changes in this phase

The prior tint used alpha values 24/255, 28/255, and 32/255 (about 9.4%, 11.0%, and 12.5%). This phase raises the three contrast bands to 32/255, 36/255, and 40/255 (about 12.5%, 14.1%, and 15.7%). Higher contrast accents keep the lower opacity; less contrasting accents receive the upper value. Color still comes from `OxygenThemeEngine.snapshot().interactiveAccent` and the page surface, and updates preserve the existing RippleDrawable.

The Classic shape is unchanged: a rectangular ripple, 3dp horizontal and 2dp vertical mask insets, 2dp corner radius, and the existing full-size touch target. Bottom bar size remains 56dp; items 52dp; icons 24dp; labels 12sp with 2dp gap; three equal-width items, always-visible labels, flat background, zero elevation, and persisted user ordering remain intact.

The selected tint remains synchronous. A 120–160ms color animator on the source Activity would run underneath a newly presented opaque destination window and could not produce reliable cross-Activity continuity. Delaying `startActivity()` would add click latency. The destination instead fades only its `directories_holder` or `media_holder` content container from alpha 0 to 1 over 180ms, after the existing first-draw gate releases it. The toolbar, system bars, bottom bar, and full-screen background do not fade. The target container includes its existing list and empty/loading placeholders; it does not alter their data or wait for media scanning. Animator scale 0 uses Android's immediate property-animation behavior. `dispose()` cancels and restores any still-running content animation.

Material Design's classic bottom-navigation guidance recommends an in-place cross-fade between destinations; the 180ms duration and ripple opacity bands above are project-selected experiment values, not Material-mandated numbers. Android's `RippleDrawable` documentation assigns pressed-state and hotspot animation to the native drawable, while the property-animation API provides the content alpha transition.

No existing motion constants, Activity lifecycle paths, media adapters, or page state transitions were changed. Animator scale 0 continues to use immediate state; no custom Animator was added, so there is no new animation listener or per-Activity Animator to leak.

## Automated coverage

`GalleryBottomBarRippleTest` checks the balanced contrast bands, immediate pressed state on the `ACTION_DOWN` dispatch, stable native Drawable identity, pressed-state preservation through recoloring, cancel behavior, failed-navigation rollback/retry, rapid single navigation, all six user orders, equal widths, RTL, large font scale, existing first-draw fallback timing, and that only the destination content container fades after its first draw. The first full CI run found the deferred pressed state and a nullable missing-content lookup in older transition fixtures; both were fixed in the follow-up. Existing `GalleryNavigationTest` and `GalleryNavigationPersistenceTest` cover navigation semantics and order persistence.

The ARM64 workflow already supports `workflow_dispatch`, but the connected GitHub interface cannot dispatch it and the browser sign-in flow could not be completed. The branch was added to the existing push branch filter, and this phase report was added to the path filter so a report with final verification results also triggers the same checks. Existing branch triggers remain unchanged.

The targeted command attempted locally:

```text
./gradlew :app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.GalleryBottomBarRippleTest --tests org.fossify.gallery.helpers.GalleryNavigationTest --tests org.fossify.gallery.helpers.GalleryNavigationPersistenceTest
```

It could not start because Gradle 9.5.1 was not cached and `services.gradle.org` is unreachable from this environment. No local test pass or Pixel hardware result is claimed.

GitHub Actions Run [#631](https://github.com/Pew2018/Gallery/actions/runs/42384359687) verified source commit `90dc32fb4fbb5ea1efc4a30c653d02d59f4f0616`: 1,021 FOSS Release unit tests passed, with 0 failures, 0 errors, and 0 skipped. The ARM64 release APK was assembled and validated for package name, signature, and ABI. APK SHA-256: `8f131f1510b8c182860d252c6c3d1ef9a540855716f89334aefa2c21365abe50`.

Artifact: [gallery-foss-arm64-v8a-release](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13578269432/zip) (expires 2026-11-21). The workflow used its temporary CI signing key because repository signing secrets were unavailable; this APK is suitable for fresh-install device testing but is not upgrade-compatible with an APK signed by the repository release key.

## Pixel 8 Pro / Android 16 acceptance still required

- Light and dark themes; pale and dark custom accents.
- Single quick tap, long press/release, and press then drag outside the item.
- Rapid tab switching and immediate Back after navigation.
- First bottom-bar tap after cold launch.
- Animator scale 0x and 1x; gesture and three-button system navigation.
- Empty Favorites, a loading photo profile, and an album with many media items.
- Confirm ripple visibility on the press frame and whether the destination window covers its release tail.
- Confirm all six user-defined tab orders retain the correct destination and selected state.

Robolectric assertions, Actions results, and device/GPU acceptance are separate verification categories.

## References

- [Material Design 2: Bottom navigation, transitions](https://m2.material.io/components/bottom-navigation/)
- [Android API: `RippleDrawable`](https://developer.android.com/reference/android/graphics/drawable/RippleDrawable)
- [Android API: View drawable hotspot state](https://developer.android.com/reference/android/view/View#drawableHotspotChanged(float,%20float))
- [Android: Property animation overview](https://developer.android.com/develop/ui/views/animations/prop-animation)
