# Delete confirmation checkbox ripple hotfix

## Finding

The grid and full-screen Viewer both show `DeleteWithRememberDialog`. Its two checkboxes were direct children of the dialog `RelativeLayout`, so `OxygenClassicDialog.apply()` installed each RippleDrawable on the `CompoundButton`. CompoundButton updates that drawable's hotspot around its leading checkbox glyph, which produced the small circular highlight seen in the Pixel 8 Pro screenshots.

## Change

- Wrapped each checkbox in a dedicated `DeleteConfirmationCheckboxRow`. The wrapper has no minimum height or padding, so it adds no row spacing.
- The row owns the theme-accent RippleDrawable; the checkbox keeps its original drawable, text, binding ID, checked state, and height.
- The optional recycle-bin row is hidden as a whole when unavailable.
- Follow-up after Pixel 8 Pro recording: removed parent-to-child `performClick()` forwarding. The native CheckBox now remains clickable and independently accessible; the row mirrors its pressed state and touch hotspot only to draw the full-row Ripple. This keeps one native state-toggle path and avoids making the wrapper a competing click target.
- No delete entry point, callback meaning, or other dialog component was changed.

## Verification

- Robolectric coverage dispatches events from the dialog content root through the actual row/CheckBox hierarchy. It checks immediate first-tap state changes, cancellation, icon and label touch points, independent row state, Ripple ownership/bounds, light and dark themes, repeated styling, hidden-row behavior, and confirm/cancel callbacks.
- Local execution of the targeted test is currently unavailable because the environment cannot download the Gradle 9.5.1 distribution; the wrapper failed with `Network is unreachable`. The branch's ARM64 FOSS Release workflow runs the complete unit-test suite before assembling the APK.
- The first follow-up runs exposed stale test assumptions about `row.performClick()`, synthetic touch routing, and when a View assigns Drawable bounds. The final tests now dispatch from the laid-out dialog content root and draw the row before checking its Ripple bounds.
- GitHub Actions run [#555](https://github.com/Pew2018/Gallery/actions/runs/42173855517) succeeded on source commit `63066114189e45b3bde9d5f478a3b558ff24d902`.
- Full FOSS Release suite: 945 tests, 0 failures, 0 errors, 0 skipped.
- ARM64 FOSS Release artifact: `gallery-foss-arm64-v8a-release`; APK SHA-256: `0a7951d91b97ed1b073c6607b6b3c27ec7d31bebedeee701e6f301b025564bab`.
- CI used a temporary signing key, so this APK is not upgrade-compatible with a differently signed installed build.
- Pixel 8 Pro visual acceptance remains pending.
