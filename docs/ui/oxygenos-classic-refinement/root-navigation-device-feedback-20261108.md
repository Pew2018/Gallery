# Root navigation: 2026-11-08 device-feedback repair

## Baseline and evidence limits

Remote branch `refactor/oxygenos-classic-bottom-bar-material-motion` was read again before editing: `d5596168c7e0b568f8cc51a1bb60c3e5f735f1e7`. It is the supplied baseline; there are no intervening commits to audit. No main, PR, new branch, or media index changes are involved.

Recording `1000039918.mp4`: 1344 × 2992, 8.8055 s, 759 frames, average 86.20 fps (variable frame rate; the advertised r_frame_rate of 240 is not its average). A 20 fps contact sheet of 2.30–2.90 s shows Photos, near-black frames, then Albums with the entire window progressively brightening, including toolbar and bottom bar. This establishes a whole-window visual failure. It does **not** identify the compositor layer, lifecycle boundary or navigation request that caused it. There is no synchronized device log for the old recording. Downscaled 10 fps YAVG samples (video-range luma, not display brightness) are 109.764 at 2.3 s, 32.0201 at 2.4/2.5 s, 72.5589 at 2.6 s and 171.806 at 2.7 s. The measurement supports the visible dark interval but cannot assign its source window.

`adb devices` in this execution environment returns no attached devices. Pixel 8 Pro / Android 16 re-recording, SurfaceFlinger/WindowManager tracing, actual starting-window values, and system animation scale 0/1 acceptance are **pending**. No unit-test or APK build result is a device visual pass.

## Confirmed defects and scoped changes

| Finding | Evidence | Change |
| --- | --- | --- |
| Short press fades before completing expansion | Original formula starts 180 ms fade at UP, independent of 225 ms expansion. New regression with 20 ms press fails against original code: feedback is already gone at 225 ms. | Fade begins at max(release, expansion end), preserving original clock across windows; no navigation delay. |
| Expired snapshot is retained | New test fails against original continuePulse: its pulse field still holds a released pulse aged 2 seconds. Old rendering already hid this expired pulse; retaining it was not proof of a visible ghost. | Filter at snapshot capture and installation; copy values, never restart an expired ripple or retain a foreign View/Drawable. |
| Blank root draw can acknowledge navigation | Original OnDraw callback never checks rows, empty state or loading state, and can cancel its 250 ms loading reveal immediately. | Production callers supply visible content/loading/empty classification. No classification means the pre-draw traversal is cancelled (source surface remains) and there is no acknowledgement. Root handoff publishes an immediate header loading strip if no valid state exists. |
| Superseded callback can retire source after newer teardown | Old guard only checks currently pending requests; disposing/removing a newer request makes that guard false again. | Keep latest request token independently of the pending map. A late callback cannot finish the source for another token. |
| Static preview differs from runtime palette | Main/Media use manifest AppTheme. Audited Commons 7.1.3 AAR defines AppTheme.Base on Theme.Material3.Dark.NoActionBar. Runtime config resolves light/custom backgrounds after Activity creation; setBackgroundDrawable in onCreate cannot change a previously system-created preview. | Root Activity manifest theme disables preview and explicitly stays opaque. Keep source window and apply resolved runtime background before inflation/again after Commons resume. This removes a known mismatch path; causation of recorded black frames remains unverified. |
| Separate folder-close motion can be requested during root retirement | MediaActivity.finish calls OxygenClassicActivityMotion.applyClose; root handoff then attempts a second override. | Explicit retirement guard skips folder animation for root handoff; normal folder/Viewer close retains original behavior. Root launch also supplies zero animations in ActivityOptions before start, rather than relying only on a post-start override. |
| Selection colors switch abruptly | Source and destination have distinct bars; previously each only used final colors. | One 150 ms ValueAnimator blends icon and label together on destination first pre-draw, without changing surface alpha, position, selected state or adding an indicator. Scale 0 uses final colors. Resume/theme recolor cancels any previous tint animator. |

The real touch hotspot and bounded 28 dp circle remain unchanged. A touch beside the icon legitimately starts beside it; the recording alone does not prove an incorrect hit rectangle. Normalized hotspot and rectangular item clipping are preserved. No M3 pill, active indicator, or whole-page alpha is added. The custom ripple is retained: its value-only cross-window continuation is testable; platform RippleDrawable cannot serialize an in-flight hardware ripple across distinct windows.

## Lifecycle and diagnostic contract

Existing opt-in diagnostic logger records `module=RootNavigation`, one request token and both uptime/elapsed-realtime clocks. Enable Settings → Advanced → Performance → Diagnostics before reproduction, and export the resulting log afterward.

Events cover touch DOWN/UP/CANCEL, pulse creation/release, click, snapshot, startActivity, create before/after Commons, prepare surface, resume after Commons, first root draw, first page state, valid frame draw, frame commit (next-frame approximation below API 29/software rendering), source finish and teardown. Fields include section/tab where applicable, resolved theme background/animation resource, actual window animation ID, window alpha/flags and Decor color. No media filenames or paths are added. Logger remains opt-in, bounded and asynchronous.

Call chain: item touch → View click → root destination mapping → open(token/snapshot/ActivityOptions) → SimpleActivity/Commons creation → Main/Media setup and real loading publication → root draw with visible state → frame commit → source retirement without folder close motion. Destination selection comes from the same GallerySection mapping as its title/content. Outgoing selection remains attached to outgoing content.

Rapid clicks on the outgoing Activity continue to accept the first request and suppress duplicate navigation until handoff/timeout. They are not a last-click queue. New windows can navigate normally; obsolete acknowledgements cannot retire their source for a newer request. Background refresh retains the existing adapter; the cross-root legacy mMedia clearing remains necessary to prevent presenting Photos data as All/Favorites.

## Alternatives reviewed

Material Components Android tag 1.0.0:

- [BottomNavigationItemView.java](https://github.com/material-components/material-components-android/blob/1.0.0/lib/java/com/google/android/material/bottomnavigation/BottomNavigationItemView.java): checked item owns selection, icon tint and labels together. Its label-shifting/scaling is not copied.
- [API 21 ripple background](https://github.com/material-components/material-components-android/blob/1.0.0/lib/java/com/google/android/material/bottomnavigation/res/drawable-v21/design_bottom_navigation_item_background.xml): native ripple with colorPrimary, not a pill. Native feedback is appropriate in one window; it does not solve cross-window continuation.

A single-host migration would remove the window boundary but requires extracting Main's folder scanning/search/menu/picker state and Media's per-profile access/session/scroll/loading state, replacing Activity assumptions in adapters/dialogs and preserving security and back dispatch. This is a multi-stage architectural change across both large Activities, not a safe substitution in this focused repair. Reconsider only if synchronized Android 16 traces still show a window-composition failure after this patch.

## Acceptance and tests

Focused tests cover 20/50/120/800 ms release, cancellation and disabled feedback, same-clock snapshot/expiry and delayed target installation, circular raster geometry, icon/label tint agreement, destination semantic-frame gating, all six section directions, light/dark/custom runtime surfaces, no-draw timeout and obsolete callbacks. Existing non-root tests remain part of the full FOSS Release suite.

Pixel acceptance matrix is pending: all six directions at normal and zero window/transition/animator scales, short/long/cancel/off-center touches, rapid presses, empty Favorites, large Photos profile and cold/slow loads, light/dark/custom backgrounds and immediate Back. Repeat the recording path around the original 2.4–2.8 s failure, with diagnostics enabled; compare same-token start/create/draw/commit/finish events to recording timestamps. For an exact compositor diagnosis, collect a synchronized system window/surface trace. An unsynchronized video cannot be retroactively assigned uptime values.

## Local verification record

Before the repair, 22 focused tests ran against the original production implementation: exactly the two newly added short-tap/expired-snapshot regressions failed; the other 20 passed. After the repair, the first focused run completed 31 tests with zero failures/errors/skips. Final full-suite and CI results are recorded in delivery. A local incremental AAPT resource-table cache failure was resolved by rerunning resource merge; no application workaround was added for that environment failure.

The complete local FOSS Release suite passed **1,033 tests across 146 suites**, with zero failures/errors/skips (4m46s), before the final pre-draw gate and real short-touch integration assertion. Those final changes receive a focused rerun and the complete CI suite before APK assembly. No Pixel visual acceptance is implied.
