> Follow-up device feedback and updated implementation: [2026-11-08 repair](root-navigation-device-feedback-20261108.md). Timing and handoff details below describe the earlier baseline.

# Root navigation feedback follow-up

Baseline: `7bec997f9fbbad77b71fcc74cebf77a2d4795e5f` on
`refactor/oxygenos-classic-bottom-bar-material-motion`.

## Observed failure and implemented boundary

The device recording shows a wide accent rectangle and whole-window grey/white
flashes during root tab switching. The old mask is an inset rounded rectangle;
root enter and exit animations simultaneously fade both entire windows. Keeping
individual View alpha at 1 does not prevent WindowManager surface transparency.

Root navigation now requests zero window animations on launch and finish,
including API 34+ Activity transition overrides. The two legacy root animation
resources are opaque zero-duration compatibility resources. Folder/Viewer motion
resources and media loading code are not changed.

Selection stays with outgoing content; destination attach selects the destination.
The old source is retired following a frame-commit callback on supported hardware
windows, or a next-frame approximation on older/software-rendered windows. A
startup timeout cleans up the transaction and unlocks the source, but never treats
a deadline as proof that the target has drawn.

## Circular feedback continuity

`NavigationRippleDrawable` draws only a bounded antialiased circle at the real
touch hotspot (28dp maximum radius). No rectangular pressed-state wash or moving
selection indicator is drawn. Expansion is 225ms with fast-out-slow-in easing;
release fades over 180ms. These are implementation values, not claimed OEM values.
Animator duration scale is refreshed on theme/resume updates and new presses; a zero scale gives
immediate held feedback and no release tail.

Root navigation starts immediately. The transaction carries value-only feedback
snapshots keyed by tab name: original start time, normalized hotspot, release time.
Destination feedback resumes the same clock, rather than restarting a new ripple.
A slow destination arriving after expiry draws no stale feedback. Source and target
never share a Drawable callback, Activity context, or View. Theme refresh preserves
drawable identity while updating color.

This is a focused cross-Activity implementation, **not** the proposed single-root
host migration. MainActivity and MediaActivity remain separate. It also does not
queue the last of multiple taps delivered to an outgoing Activity: duplicate
navigation is suppressed until handoff or timeout. Native RippleDrawable is
replaced only for these root navigation items; other controls are unchanged.

## Verification

- GalleryBottomBarRippleTest: selection/content alignment, launch failure retry,
  duplicate clicks, DOWN/UP/CANCEL, theme changes, ordering, RTL/font sizing,
  value-only cross-window handoff and absence of window fades.
- GalleryRootTransitionTest: opaque compatibility animations, all section pairs,
  frame handoff, no-draw timeout and disposal, window animation scale 0/1.
- NavigationRippleDrawableTest: clock continuity, expiry, disabled animation and
  native Canvas circle geometry (transparent rectangle corners).
- Run full FOSS Release unit tests, then ARM64 FOSS Release assembly in Actions.

Device acceptance remains required: on Pixel 8 Pro, record all six root directions,
fast repeated switches, long hold/release, drag out/cancel, light/dark themes, slow
loading and authentication. Window compositor behavior cannot be certified by
Robolectric; no device visual pass is claimed by automated test success.

## Pre-commit result

Full `:app:testFossReleaseUnitTest`: **1,023 tests, zero failures, zero errors,
zero skipped**, across 146 report suites, with JDK 21 and Android SDK 36.
The local test runner needed proxy forwarding for Robolectric runtime downloads;
that environment-only configuration is outside the repository.
