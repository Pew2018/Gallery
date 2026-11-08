# OxygenOS Classic Viewer and Basic Editor dark surface polish

## Scope and baseline

- Target branch: `refactor/oxygenos-classic-bottom-bar-material-motion`
- Audited HEAD: `e16e296823f15aafe45119797f1143def5db44ef`
- Design: black media canvas, `#212121` toolbar, `#0D0D0D` editor controls, `#303030` editor dialogs, and the user's saved accent.
- No database, media indexing, sorting, search, permissions, deletion, or save encoding changes were made.

## Findings and evidence

| Issue | Confirmed evidence | Status / remaining uncertainty |
|---|---|---|
| Fixed blue Editor accent | `EditActivity` declared `EDITOR_ACCENT = #2196F3` and used it for the brush width SeekBar, active tool icons, and crop ratio labels. The `editor_accent` resource also supplied blue to video SeekBar controls. | Removed runtime blue dependency. Editor selected controls now resolve from `OxygenThemeEngine.snapshot(...).rawAccent`, contrast-adjusted against black for visible rendering. The saved accent is not changed. |
| Editor / Viewer surface mismatch | Editor layout already declared black canvas and `#212121` toolbar, but Editor toolbar setup briefly supplied the theme toolbar color before `applyImmersiveEditorStyle()` reset it. Editor panel was `#101010`. | Editor toolbar setup now uses its fixed neutral token; redundant post-resume restyling was removed. Bottom panel is `#0D0D0D`. Viewer root and image hosts receive black surfaces. Device capture is still needed to verify actual first-frame timing. |
| Theme application after inflation | `EditActivity.applyImmersiveEditorStyle()` called `theme.applyStyle(OxygenClassicActivityOverlay_Dark)` after controls had been inflated, and the method ran from resume, post-resume, and configuration callbacks. | Removed the late whole-Activity theme mutation. Neutral surfaces are set in resources and the dynamic accent is applied to Editor controls as a focused pass. Base theme initialization remains in `SimpleActivity` before content inflation. |
| Crop handles and filter selection | Cropper view had a black background but no explicit theme-linked border/guideline colors. Filter adapter used a fixed white stroke. | Cropper border/corner/guideline colors now reference the current `colorAccent`; filter outline and selected label use the resolved Editor accent. Cropper color attributes are from the existing CanHub Cropper 4.7.0 dependency. |
| Editor dialogs on light Gallery appearance | Editor custom-ratio, alternate-ratio, resize, and save-as dialogs used `OxygenClassicDialog.apply()` which chose light or dark surfaces from the Gallery page background. | Added a scoped `forceDark` option used only by these Editor dialogs. Their input focus and confirmation colors still use the Gallery accent with dialog-surface contrast handling. Existing non-Editor dialog behavior is unchanged. |
| Viewer / Editor image-area white flashes | Viewer layouts had a black parent surface, but the reused Commons pager item did not explicitly set a background on the root and image hosts. Glide loads without an explicit placeholder; this alone does not prove a white flash because its image hosts may be transparent over the black parent. | Set the pager item, gesture image, subsampling view, GIF frame, and relevant Activity window backgrounds to black. This prevents transparent/letterboxed areas from exposing a light theme surface. A root cause for the reported 19.8–20.6 second recording interval is **not confirmed**: that recording was not present among this turn's workspace attachments, and no before/after device trace was available. No claim is made that a media-content transition or device-specific white flash is fully eliminated. |

## Color and behavior rules

- Canvas: `#000000`; transparent image pixels continue to reveal the canvas.
- Editor toolbar: `#212121`; status underlay: `#1E1E1E`.
- Editor bottom controls: `#0D0D0D`; dialog surface: `#303030`.
- Editor selection, cropper control, filter selection, slider, and dialog action colors follow the active Gallery accent. Small interactive marks are contrast-adjusted against their actual surface; saved preferences remain untouched.
- Normal unselected Editor icons remain white. Editing image pixels, aspect-ratio math, crop behavior, filter processing, draw color, saving, EXIF handling, and navigation logic were not intentionally changed.

## Changed files

- `app/src/main/kotlin/org/fossify/gallery/activities/EditActivity.kt`: remove the fixed blue value, use the dark toolbar token, resolve/apply the current accent, style current selected tool states, and pass the scoped dark-dialog request.
- `app/src/main/kotlin/org/fossify/gallery/activities/SimpleActivity.kt`: establish black starting-window surfaces for the photo viewer/editor before content is installed; stop routing Editor toolbar refresh through the shared theme toolbar pass.
- `app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt`: explicitly keep media host surfaces black.
- `app/src/main/kotlin/org/fossify/gallery/adapters/FiltersAdapter.kt`: make selected filter outline and label accent-aware.
- `app/src/main/kotlin/org/fossify/gallery/fragments/VideoFragment.kt`: replace the removed static Editor accent resource in video seek controls with the current theme accent.
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicDialog.kt`: add an opt-in dark-surface styling mode; default dialog behavior remains unchanged.
- `app/src/main/kotlin/org/fossify/gallery/dialogs/{CustomAspectRatioDialog,OtherAspectRatioDialog,ResizeDialog,SaveAsDialog}.kt`: use dark styling only when opened by the Basic Editor.
- `app/src/main/res/layout/activity_edit.xml`, `bottom_editor_actions_filter.xml`, `app/src/main/res/values/{colors,oxygenos_classic}.xml`: align the Editor's surfaces and cropper/popup accents.
- `app/src/test/kotlin/org/fossify/gallery/helpers/OxygenThemeColorsTest.kt`: cover blue, teal, pink, pale yellow, and near-black accent behavior on the black canvas.

## Verification

- Passed: `:app:testFossReleaseUnitTest` using the preinstalled Gradle 9.5.1, JDK 21, and Android SDK. This includes the full FOSS Release unit test suite and the new accent contrast assertions.
- Kotlin, resource linking, and unit-test compilation completed successfully as part of that run.
- Not verified in this environment yet: emulator/device screenshot comparison, actual Viewer first-frame behavior, IME placement in the Editor dialogs, cropper touch/visual behavior, and the reported recording interval.
- Required before release: run `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`, inspect the produced APK, and verify the corresponding GitHub Actions run/artifact after push.
