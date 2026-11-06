# Delete confirmation checkbox ripple hotfix

## Finding

The grid and full-screen Viewer both show `DeleteWithRememberDialog`. Its two checkboxes were direct children of the dialog `RelativeLayout`, so `OxygenClassicDialog.apply()` installed each RippleDrawable on the `CompoundButton`. CompoundButton updates that drawable's hotspot around its leading checkbox glyph, which produced the small circular highlight seen in the Pixel 8 Pro screenshots.

## Change

- Wrapped each checkbox in a dedicated `DeleteConfirmationCheckboxRow`. The wrapper has no minimum height or padding, so it adds no row spacing.
- The row owns the theme-accent RippleDrawable; the checkbox keeps its original drawable, text, binding ID, checked state, and height.
- The optional recycle-bin row is hidden as a whole when unavailable.
- The row forwards clicks through the original checkbox and exposes CheckBox semantics to accessibility.
- No delete entry point, callback meaning, or other dialog component was changed.

## Verification

- Robolectric coverage exercises both row Ripple bounds, icon and label touch points, single checked-state transitions, light and dark themes, repeated styling, hidden-row behavior, and confirm/cancel callbacks.
- GitHub Actions run [#551](https://github.com/Pew2018/Gallery/actions/runs/42168357794) succeeded on source commit `f7685817f08e081d695aa7778267b94769ae3bb0`.
- Full FOSS Release suite: 944 tests, 0 failures, 0 errors, 0 skipped.
- ARM64 FOSS Release artifact: `gallery-foss-arm64-v8a-release`; APK SHA-256: `58499a6a448d5f4dcb48eab38cdee4b84d951d71d936072d2d0a75744cafa5ff`.
- CI used a temporary signing key, so this APK is not upgrade-compatible with a differently signed installed build.
- Pixel 8 Pro visual acceptance remains pending.
