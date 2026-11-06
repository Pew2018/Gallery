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

Added Robolectric tests for full-row Ripple bounds, touch points on the icon and label area, one state transition per tap, light and dark themes, repeated styling, optional-row GONE behavior, and confirm/cancel callback behavior. GitHub Actions test/build status and APK artifact are recorded after the run completes. Pixel 8 Pro visual acceptance remains pending.
