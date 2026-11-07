# Phase 3C · Bottom navigation window handoff repair

## Scope and baseline

- Repository: `Pew2018/Gallery`
- Base branch: `refactor/media-pipeline-stability`
- Work branch: `refactor/oxygenos-classic-bottom-bar-material-motion`
- Audited remote baseline for this repair: `ef8e9eb82c2f15fd6a09a62fc5ad9795f7298c4e`; local implementation commit: `73423df00` plus the follow-up first-frame correction described below.
- Scope: bottom-bar ripple dispatch, root Activity window handoff, deterministic tests, and this report.
- No media scanning, indexing, Room, sorting, search, Viewer, deletion, or file-operation logic is in scope.

## Findings and evidence labels

| Finding | Evidence |
|---|---|
| The root click path synchronously changes source selection, then calls `startActivity()`; navigation is not delayed by a ripple timer. | `CONFIRMED_BY_CODE` |
| The bottom-bar item sets its hotspot and pressed state on `ACTION_DOWN`, then returns `false` so the regular View touch handler owns release, cancel, click, and long-press behavior. Existing Robolectric coverage dispatches down/up and cancel. | `CONFIRMED_BY_CODE`, `CONFIRMED_BY_TEST` |
| The previous launch explicitly requested `overridePendingTransition(0, 0)`, so no root-window transition could be seen. | `CONFIRMED_BY_CODE` |
| The previous destination set its content container alpha to zero immediately before its first draw; after that draw it released the source and only then faded content in. This could expose the destination's themed background while the source was gone. | `CONFIRMED_BY_CODE` |
| Each Activity has its own bar View and RippleDrawable. The system does not transfer the source drawable's in-progress native ripple to the destination Activity. | `CONFIRMED_BY_CODE` |
| Exact ripple clipping, release tail, Surface ordering, and perceived transition quality on Pixel 8 Pro / Android 16. | `DEVICE_VERIFICATION_REQUIRED` |

The code establishes the lifecycle ordering above. It does not establish which exact GPU frame first covered the source ripple on a Pixel device; that remains a device-capture question.

## Previous and repaired sequence

Previous sequence:

1. `ACTION_DOWN` updates the source item hotspot and pressed state.
2. `ACTION_UP` invokes the click listener; source tab tint updates synchronously.
3. `GalleryBottomBar.open()` starts the destination and requests a zero-duration window transition.
4. The destination's opaque surface can cover the source RippleDrawable.
5. The destination first-draw gate sets its content container to alpha 0, draws, and releases the source.
6. A separate 180ms property animation fades the destination content over its background.

The gap was structural: there was no window animation linking steps 3–6, and the target content began transparent. The source ripple belonged to the source window, so increasing its color alpha or waiting before `startActivity()` would not create a continuous cross-Activity ripple.

Repaired sequence:

1. `ACTION_DOWN` still sets the native ripple hotspot/pressed state immediately; the normal View machinery continues to handle release/cancel/click.
2. `ACTION_UP` still navigates immediately. The source selection updates on the click frame.
3. `GalleryBottomBar.open()` starts the destination immediately and requests paired 180ms alpha window animations (`oxygen_classic_root_enter` / `oxygen_classic_root_exit`).
4. The outgoing opaque Activity surface remains underneath the entering window during the system window transition. The ripple release therefore recedes as its owning source surface fades, instead of being cut off by a zero-duration handoff.
5. The destination is allowed to draw its initial loading, empty, or authorization state immediately. It no longer blocks `OnPreDraw` waiting for adapter rows or a content-ready marker, and it does not set the content container to alpha 0.
6. After the first actual draw acknowledgement, source finish is scheduled for the remainder of the 180ms window transition. The existing 250ms loading-indicator reveal and 530ms startup fallback remain bounded. Navigation itself is not delayed.

This is a whole-window cross-fade. It includes toolbar and bottom bar; the cross-Activity architecture cannot keep the bar as one shared View. The motion is short and stationary, without translation or scale. It provides a genuine visible window handoff but is not a content-only cross-fade and must not be described as one. A single stable host could avoid cross-fading the bar, but would require substantial lifecycle, security, saved-state, and loading changes across MainActivity and MediaActivity. That risk is out of scope for this focused P0 repair.

## Ripple and selection behavior

- The native RippleDrawable remains a low-contrast rectangular mask with the current 3dp horizontal / 2dp vertical inset and 2dp corner radius. The full clickable 52dp item remains intact.
- Ripple colors remain derived from OxygenThemeEngine's semantic accent and page surface. `setColor()` updates the existing drawable and does not replace it during theme recoloring.
- Immediate press state remains on `ACTION_DOWN`; release, cancel, and click stay with standard View dispatch. No timer or delayed navigation was added.
- Selected tint and accessibility state remain synchronous. No icon movement, scale, pill, or extra selection animator was added.
- Quick repeated clicks remain guarded by the per-bar navigation latch; launch exceptions restore the previous selected tab.

## Lifecycle, loading, and cleanup

- The target listener observes a real draw but never blocks it. If the Activity never draws, the 530ms fallback releases the source; the 250ms callback may reveal its existing loading indicator.
- Empty, authorization, failed, and loading states use their existing Activity UI. No content-ready markers are needed for navigation.
- `dispose()` removes root listeners and callbacks. Pending roots use weak references to the source Activity; failed launch removes its pending timeout.
- The system **window animation** scale controls this WindowManager transition. When it is 0, the source is released after the first drawn target frame without holding it for the 180ms motion interval. The unrelated property animator scale does not control root navigation.
- No screenshot/cached-content overlay is created. Protected content continues to be rendered by its destination Activity under existing access checks.

## Changed files

- `app/src/main/kotlin/org/fossify/gallery/helpers/GalleryBottomBar.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt`
- `app/src/main/res/anim/oxygen_classic_root_enter.xml`
- `app/src/main/res/anim/oxygen_classic_root_exit.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/GalleryBottomBarRippleTest.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/GalleryRootTransitionTest.kt`
- `docs/ui/oxygenos-classic-refinement/phase-3b-bottom-navigation-material-motion.md`

## Automated verification

Robolectric coverage checks bottom-bar sizing, six custom orders, ripple identity across recoloring, immediate pressed state, normal release/cancel, rapid navigation gating, failed-launch selection rollback, an immediately drawable loading frame, source retention through the remaining window transition, zero window-animation scale, and bounded startup fallback. Updated transition tests assert that paired root window animations are requested and that the content is not independently zeroed.

These tests do not render Android's actual Activity Surface composition, GPU alpha blending, native RippleDrawable release curve, gesture navigation, or three-button navigation Insets. A passing unit test is not a visual acceptance result.

Prior build evidence (before this repair): GitHub Actions Run [#631](https://github.com/Pew2018/Gallery/actions/runs/42384359687) passed 1,021 FOSS Release unit tests and produced an ARM64 APK for commit `90dc32fb4fbb5ea1efc4a30c653d02d59f4f0616`. SHA-256: `8f131f1510b8c182860d252c6c3d1ef9a540855716f89334aefa2c21365abe50`. Artifact: [gallery-foss-arm64-v8a-release](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13578269432/zip), with temporary CI signing; that APK may not install over a build signed by the repository release key. This is historical evidence and does not verify the current repair.

## Pixel 8 Pro / Android 16 device acceptance required

Record 60fps or higher video and inspect press-down, ripple spread/release, window handoff, first visible destination content, end of transition, and bottom-bar selected tint. Cover quick tap; long press; press then drag outside; rapid switching; all six direction pairs; empty Favorites; photo profiles loading; large lists; light/dark/custom accents; window-animation scale 0 and 1; immediate Back; and gesture plus three-button system navigation. Verify there is no white/black gap, double-selected ghosting, or bar layout jump.

Status: **not device-verified in this change** until a Pixel 8 Pro / Android 16 recording is reviewed.
