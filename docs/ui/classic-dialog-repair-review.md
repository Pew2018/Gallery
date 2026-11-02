# Classic dialog UI repair — review and acceptance record

Base: `955597a3eca64c38c8686638ae0e2ee5285c42e6`, branch `fix/folder-viewer-swipe`.

## Geometry and control rules

- Keep the existing 4dp dialog surface and dynamic theme colors. No shared overflow-menu files, anchors, offsets or item styles changed.
- Compact action menus retain the previous 278dp cap. Message/deletion confirmations use a 320dp cap. Reorder and security use 340dp; option settings 360dp; input/complex forms 400dp; properties 480dp; pickers 560dp. All noncompact profiles leave at least 16dp at each host edge. These are maximum widths, not fixed heights. Root IDs classify content; merely containing an EditText does not enlarge a window. Commons ExportSettingsDialog was traced against Commons 7.1.3 and now shares the export-favorites profile.
- A simple eight-option RadioGroupDialog remains compact; its item count no longer widens the panel. Rows are regular weight, minimum 48dp, 8dp vertical padding, and grow when text wraps.
- Scrollable middles reserve measured visible siblings (title, tabs, reset controls, actions), parent padding/margins and 32dp edge clearance. Parent AT_MOST constraints still apply. Removed the blanket 60%/136dp and toolbar-specific fixed-reserve rules. Picker list/FAB/fast-scroll logic is retained.
- Actions retain their original Button instances/listeners and AppCompat stacking. One/two actions form a trailing group: 16dp gutters, 8dp vertical padding and 8dp inter-button spacing; minimum 48dp height, 64dp width, centered 14sp text. True three-action dialogs keep the neutral action separated by AppCompat's spacer. Long actions can stack.
- Secondary dialog scrim is 20%; primary scrim remains 45%. Nesting is recorded once with weak references; parent dimensions and callbacks remain independent.
- Labelled left checkboxes reserve a genuine 12dp glyph/label gap. Right-side settings use an ordinary horizontal label/checkbox container, not forced RTL. Original checkbox IDs, types and listeners remain intact.
- Reorder glyph: shared 24dp vector, two 14dp horizontal paths, 2dp stroke, round ends, centers y=9/15. 48dp touch target, secondary theme tint. A 1 physical pixel decoration separates rows with no last divider and no added row height. Baseline row minimum 56dp, wrapping allowed. Both modal reorder lists retain long press and up/down menus, cancellation and persistence.
- The additional album reorder handles found during the global scan also use this vector and 48dp targets. Thumbnail grids keep their existing overlay positions and contrast treatment; adding full-width dividers across a thumbnail grid would break that layout.

## Screenshot-by-screenshot acceptance

All entries below describe code changes or deliberately retained behavior, **not completed device visual acceptance**. Every item still needs device checks for light/dark, 360–412dp, landscape, fonts 1.0/1.3/1.5 and IME transitions.

| Screenshot | Finding and implementation | Automated evidence |
|---|---|---|
| 1 file deletion | Confirmation profile, 24dp text start, real checkbox-label gap and shared trailing actions; deletion/default checks untouched | shared action/callback regression; existing dialog suite |
| 2 folder deletion | Root MATCH_PARENT changed to WRAP_CONTENT; red warning retained; shared confirmation/actions | XML and shared geometry tests |
| 3 profile creation | Removed duplicate name hint; retained 52dp minimum input; source add is a 56dp minimum row with tinted plus; source rows minimum 72dp | PhotoProfileEditUiTest |
| 4 profile management | Right gutter 8dp, explicit marker/menu spacing; invisible marker reserves width; 72dp minimum and source count retained | existing profile activity tests |
| 5 properties/EXIF | Property group gap 16dp and label/value gap 2dp; long content scrolls; shared neutral/positive trailing group; lighter child scrim | properties action test and parent/child regression |
| 6 navigation order | Shared two-line handle, secondary tint, matching 48dp target and right alignment, dividers, wrapping rows and bounded middle | navigation tests; glyph raster test |
| 7 thumbnail options | Explicit centered spacing value; consistent 24dp label start; independent trailing checkboxes and wrapping 56dp minimum rows | binding compilation; checkbox label toggle regression |
| 8 spacing choices | All eight values retained; compact width, regular 48dp minimum rows and internal scrolling | existing RadioGroup callback tests and long-scroll test |
| 9 full-screen actions | Removed doubled 16dp vertical checkbox padding; 8dp padding plus 48dp minimum and real glyph gap; shared actions | existing control/dialog suite |
| 10 toolbar actions | Shared vector/dividers/targets; natural wrapping list heights replace 56dp*count; shared scroll budget; tab switch resets outer scroll | ToolbarQuickActionsDialogUiTest |
| 11 password actions | Compact profile and original callbacks retained; shared single-choice row finish only | existing Commons/dialog hooks tests; device review pending |
| 12 password types | Same compact profile; no forced window expansion | existing security page/binding tests; device review pending |
| 13 PIN | Commons-compatible tab overlay; top-anchored title/current PIN; intrinsic keypad rows minimum 56dp; original C/0/OK and validation code retained; shared cancel footer | synthetic pager test plus real page geometry across font scales |
| 14 pattern | Commons-compatible tab overlay; square grid constrained by width; top-anchored title; no stretched vertical chain | real page geometry and existing pager test |
| 15 date/time | Regular single-choice rows; 24h row uses explicit horizontal label/check; same original binding IDs and save/cancel logic | date format transactional test and label-toggle regression |
| 16 export settings | Traced real Commons ExportSettingsDialog; binding-compatible ScrollView subclass overlay with wrap root and 24dp gutters; INPUT profile | matching export-width and binding regression |
| 17 export favorite folders | Wrap root; same INPUT profile and content gutters; single-line filename edit retained | matching export-width and binding regression |

## Additional consistency scan

Reviewed all 25 Gallery dialogs named in the task and their layouts, plus shared Commons confirmation, radio, file picker, properties, export-settings and security entry points. Additional oversized root heights in slideshow, exclusion and other-aspect-ratio layouts now wrap. Extended-details multi-select rows use the same tighter vertical padding. The media picker receives a viewport-limiting RelativeLayout subclass without replacing its RecyclerView/fast-scroller hierarchy. File picker navigation, SAF, protected-folder callbacks and fast-scroller code are unchanged.

No media loading, deletion/export implementation, password validation/hash/lockout, configuration storage, or menu popup appearance was edited. Presentation callbacks for label clicks forward to the original checkbox click path.

## Validation and limits

- All repository resource XML parsed locally; git diff whitespace checks passed before submission.
- Local Gradle could not start because no cached distribution/Android SDK is installed and services.gradle.org is unreachable from this environment. This is an environment failure, not a passing test result.
- The existing arm64 Actions workflow runs the complete `:app:testFossReleaseUnitTest` suite before `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`, then verifies package/signature/ABI and uploads the APK. Use the workflow for the delivered commit as the authoritative test/build result. Failures require a corrective commit.
- Added regression checks cover action alignment/idempotence/callbacks, export-width and binding compatibility, label-click semantics, exact two-band glyph rasterization, real PIN/pattern geometry at several widths/font scales and nested scrim/parent state.
- No connected Android device/emulator was available. No claim of visual completion, real fingerprint success, device rotation correctness or real IME testing is made solely from unit tests/build success.

## Changed Kotlin/XML files

- `app/src/main/kotlin/org/fossify/gallery/activities/PhotoProfileEditActivity.kt`
- `app/src/main/kotlin/org/fossify/gallery/adapters/PhotoProfileManageAdapter.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/BottomNavigationOrderDialog.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicDialogScrollView.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicDialog.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/TiebaDialogChoices.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/ToolbarQuickActionsDialog.kt`
- `app/src/main/res/drawable/oxygen_classic_drag_handle.xml`
- `app/src/main/res/layout/activity_photo_profile_edit.xml`
- `app/src/main/res/layout/dialog_change_date_time_format.xml`
- `app/src/main/res/layout/dialog_change_file_thumbnail_style.xml`
- `app/src/main/res/layout/dialog_change_folder_thumbnail_style.xml`
- `app/src/main/res/layout/dialog_confirm_delete_folder.xml`
- `app/src/main/res/layout/dialog_delete_with_remember.xml`
- `app/src/main/res/layout/dialog_exclude_folder.xml`
- `app/src/main/res/layout/dialog_export_favorites.xml`
- `app/src/main/res/layout/dialog_manage_bottom_actions.xml`
- `app/src/main/res/layout/dialog_manage_extended_details.xml`
- `app/src/main/res/layout/dialog_medium_picker.xml`
- `app/src/main/res/layout/dialog_other_aspect_ratio.xml`
- `app/src/main/res/layout/dialog_slideshow.xml`
- `app/src/main/res/layout/directory_item_grid_rounded_corners.xml`
- `app/src/main/res/layout/directory_item_grid_square.xml`
- `app/src/main/res/layout/directory_item_list.xml`
- `app/src/main/res/layout/item_photo_profile_manage.xml`
- `app/src/main/res/layout/item_property.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicDialogTest.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/PhotoProfileEditUiTest.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ToolbarQuickActionsDialogUiTest.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicReorderDecoration.kt`
- `app/src/main/kotlin/org/fossify/gallery/views/ClassicCheckboxRow.kt`
- `app/src/main/kotlin/org/fossify/gallery/views/ClassicMediaPickerLayout.kt`
- `app/src/main/res/layout/dialog_export_settings.xml`
- `app/src/main/res/layout/tab_pattern.xml`
- `app/src/main/res/layout/tab_pin.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicDialogLayoutRegressionTest.kt`
