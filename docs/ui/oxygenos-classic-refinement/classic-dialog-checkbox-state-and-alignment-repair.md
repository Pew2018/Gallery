# Classic dialog Checkbox state and alignment repair

## Baseline and scope

- Branch: `refactor/media-pipeline-stability`
- Remote HEAD used for this work: `01c1ec01ef6f41e32cbaad435d664368d0b7854d` (`Fix folder dialog binding package`)
- Scope: OxygenOS Classic dialog Checkbox/RadioButton state rendering, selection mark alignment, list choice marks, row ripple ownership, and regression coverage.
- Media scanning, sorting semantics, Viewer, database schema, navigation, and preference/authentication behavior were not changed.

## Recording evidence and source findings

| Recording time | Observed issue | Source path |
| --- | --- | --- |
| 00:02–00:03 | Filter options show a pale checked-looking mark after being unchecked. | `OxygenClassicDialog.apply()` selects the drawable palette; `OxygenClassicControls.ClassicDrawable.draw()` paints checkbox fill and tick from `selectionProgress`. |
| 00:35–00:37 | View-type supplemental Checkbox is left aligned and separated from its text, while RadioButton marks are on the right. | `OxygenClassicDialog.apply()` trailing-ID handling; `dialog_change_view_type.xml`. |
| 00:36–00:37 | Row feedback appears at a different scale/area than the full option. | `ClassicDialogCheckboxRow` and the `row.background` / `button.background` ownership in `OxygenClassicDialog.apply()`. |
| 00:46–00:51 | Rechecked folder filters retain the light accent while the protected-folder Checkbox is dark. | `OxygenClassicDialog.apply()` chooses dialog control colors; `OxygenClassicControls.ClassicDrawable.draw()` chooses fill from `surfaceAccent` and palette. |

### Confirmed from source

1. Most dialog Checkboxes were drawn with the shared drawable's toolbar-derived fill (`toolbarBackground(rawAccent)`). Only a small set of IDs used the surface accent. This gives identical checked semantics different fill colors and can make a selected box look washed out on a dialog surface. The dialog now supplies the resolved, contrast-checked accent to every dialog Checkbox and requests the solid surface selection style.
2. The view-type supplemental Checkbox IDs were absent from the trailing-mark set. They now use the same relative-end compound-mark treatment as grouping and sorting.
3. After moving a trailing mark to a compound drawable, the old non-trailing branch could still wrap the now-null `buttonDrawable` in an `InsetDrawable`. That retained an empty leading button slot beside the new right mark, contributing to the excessive text gap. The leading inset path now runs only for non-trailing controls that still have a button drawable.
4. `CheckedTextView` styling treated every choice mode other than SINGLE as multiple. That included `CHOICE_MODE_NONE`. Styling now explicitly handles NONE, SINGLE, MULTIPLE, and MULTIPLE_MODAL; NONE clears the check mark.
5. `ClassicDialogCheckboxRow` already owns the touch target and rectangular ripple, and clears the child Checkbox background. The row continues to call `checkbox.performClick()` once. The existing binding IDs and business listeners remain attached to the Checkbox.

### Not established as the sole cause

The recording alone cannot distinguish a transient animator interruption from an accent-palette mismatch. The existing Drawable animated checked transitions and retained the current progress on interruption, but its natural-end callback did not explicitly commit the target progress. The callback now snaps to the target only on natural completion; canceled transitions preserve the last rendered value so the replacement animation starts there. A disabled or animation-disabled transition still settles synchronously. This closes a state-machine edge but does not claim that animation was the only source of the recorded colors.

No project-local `setMultiChoiceItems()` or `setSingleChoiceItems()` call was found in the audited application source. The shared `CheckedTextView` handler was nevertheless corrected so plain list rows, single-choice rows, and both multiple-choice modes do not inherit the wrong mark style. This change does not remove a required selection indicator.

## State and layout rules after repair

- Enabled unchecked: transparent interior, neutral outline, no check stroke.
- Enabled checked: full resolved dialog accent fill with a contrasting check stroke.
- Disabled checked and disabled unchecked retain distinct visual states through the disabled palette and the true checked Drawable state.
- Checkbox selection progress animates only for genuine checked transitions. A natural animation end stores exactly 0 or 1. Cancellation preserves the last frame for reversal; disabled state, disabled system animations, and `jumpToCurrentState()` settle to the actual checked state.
- Filter and visibility rows retain their left Checkbox, 56dp minimum row, and row-owned ripple.
- Supplemental Checkbox options in grouping, sorting, and view type keep normal LTR/RTL text direction and place one mark at relative end. Their compound drawable and RadioButton mark use matching 24dp geometry and 24dp end padding.
- RadioButton list rows continue to use one right-side mark. Multiple-choice CheckedTextView rows use the Checkbox drawable. NONE rows have no selection mark.

## Verification

### Automated coverage added

- `ClassicDialogCheckboxStateRegressionTest`: Native Graphics bitmap assertions for checked and unchecked pixels, six dark/light and accent combinations, repeated reversals, settled animator output, `jumpToCurrentState()`, disabled checked/unchecked separation, and a real CheckBox wrapped in `InsetDrawable` with animation disabled.
- `DialogUiRepairTest`: all six filter and four folder-visibility rows exercise the one-click row path and bounded row ripple; they also inspect rendered dialog mark color. Grouping, sorting, and view-type layouts inspect the actual trailing compound marks, absence of a second button mark, and stable end padding after repeated styling.
- Existing targeted contrast, motion, dialog layout, and delete-confirmation ripple tests remain part of the validation run.

### Results

- Local targeted Gradle test run: **blocked before Gradle started**. The wrapper distribution `gradle-9.5.1` was not installed in this environment and downloading it from `services.gradle.org` failed with `Network is unreachable`.
- GitHub Actions ARM64 FOSS Release run: pending/record after push.
- Pixel 8 Pro / Android 16: still requires device verification. Robolectric bitmap and view tests do not count as a device acceptance result.

## Remaining device checks

On Pixel 8 Pro / Android 16, verify the six filter options, four folder-visibility options, and the three supplemental Checkbox dialogs in both light and dark themes. Exercise icon, label, blank-area, and row-edge taps; uncheck then recheck; close/cancel while pressed; and confirm one rectangular ripple, the solid final checked accent, no unchecked tick, and right-edge alignment. Check at the user's normal display/text scale and with increased font scale.
