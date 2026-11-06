# Delete confirmation: native state and visible mark

Baseline: remote `refactor/media-pipeline-stability` was freshly read at
`24cc2b3ed9fa881d5e77f5b5a62318602fcbe300` on 2026-11-06.

## Evidence and limits

The supplied 21-second recording shows pressed-row feedback while the box is
still empty, followed by checked boxes on later interactions. A recording does
not expose `isChecked`, so it cannot establish whether the original first touch
was cancelled or whether its visual update was delayed. No device event trace is
claimed. Existing logical-only assertions did not establish visible correctness.

The native Commons 7.1.3 checkbox only adds font/color configuration; it does not
override touch dispatch, performClick or setChecked. DeleteWithRememberDialog
initializes each value once and reads the native values when confirming. No
repeat reset was found. Both MediaAdapter (grid) and ViewPagerActivity (viewer)
use this dialog. Inside the recycle bin they pass `showSkipRecycleBinOption=false`,
so the same fix covers the single-row deletion dialog. The separate empty-bin
confirmation has no checkbox.

A definite defect exists in ClassicDrawable: checked -> unchecked before the
first animator frame leaves progress at zero. The former `shouldAnimate` guard
then skips cancelling the old animator, allowing it to finish at checked despite
the native unchecked state. This is not asserted to be the sole cause of the
reported first-touch symptom.

## Implementation

* DeleteConfirmationCheckbox subclasses the existing Commons checkbox. Native
  CompoundButton still owns every click, toggle, keyboard and accessibility action.
  No forwarding listener, extra click target, or manual ACTION_UP toggle is added.
* On drawable state changes, the installed mark (including its InsetDrawable)
  jumps to its native state and the checkbox View explicitly invalidates. Delete
  confirmation skins also disable selection animation. The first visible mark
  therefore does not depend on an animator frame or drawable-only invalidation.
* Ripple pressed state and hotspot are published from the checkbox's own state
  callbacks, including delayed press changes and keyboard interaction. The wrapper
  no longer observes dispatchTouchEvent after the fact. The Ripple itself remains
  animated and bounded to the same original row.
* ClassicDrawable cancels an obsolete animation on every checked-state change,
  even when current progress already equals the new target. Other controls retain
  their selection transitions.
* Existing IDs, binding, labels, padding, dimensions, square geometry, colors,
  callbacks and the independently focusable/checkable native checkbox are retained.

## Verification

DeleteWithRememberDialogRippleTest now runs on API 28 and API 36. It adds actual
installed-mark bitmap comparisons immediately after native click without advancing
the animation clock, rapid reversals, icon/text/far-edge decor dispatch, accessibility
click and checkable state, disabled input, independent choices, and final callback
values for both two-row and recycle-bin single-row presentations in light/dark themes.
The obsolete-animator regression separately exercises checkbox and radio skins.
Existing CANCEL, ripple bounds, hidden-row geometry and callback tests remain.

Local Gradle could not download its distribution (network unreachable); full unit
tests and the installable ARM64 FOSS Release build are delegated to the existing
GitHub Actions workflow. CI result must be reported separately after completion.
Robolectric API 36 and bitmap checks improve coverage but do not constitute a
Pixel 8 Pro hardware/TalkBack acceptance result. Final device acceptance remains
the visible first-tap/second-tap behavior on the delivered APK; no further debug
capture is required from the user.
