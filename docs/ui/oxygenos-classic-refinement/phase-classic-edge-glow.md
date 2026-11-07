# Phase C — Theme-aware OxygenOS Classic edge feedback

## Baseline and implementation

- Remote baseline: `923517adcbcba57556670657f0ffa5e28ad0fc0c`, fetched before this phase's commit.
- Added one shared RecyclerView edge-effect factory. It draws a short, soft classic edge glow from the current `OxygenThemeEngine.snapshot(...).interactiveAccent`; the color is resolved while drawing so a theme change does not leave a cached blue glow.
- Vertical RecyclerViews under `SwipeRefreshLayout` suppress only their top edge. The factory does not inspect `isEnabled`, so search mode cannot unexpectedly replace the refresh affordance with a top glow. Bottom feedback remains enabled. Horizontal RecyclerViews suppress vertical-style effects.
- Empty and non-scrollable RecyclerViews do not draw a glow. `RecyclerView` retains its built-in scroll and fast-scroll touch handling; no scroll listeners, touch dispatch, adapter order, or position restoration are replaced.
- Added shared classic-glow ScrollView and NestedScrollView subclasses for Settings, diagnostics, Theme, Photo Profile Edit, and long Gallery dialog content. They disable platform stretch and draw the shared bounded glow only when content can scroll. Security input uses its existing dedicated scroll view and remains untouched.
- Directory/media Pickers use the same RecyclerView factory through the fast-scroller styling entry point. Commons FilePicker lists are covered when the shared picker layout attaches. Dialog RecyclerViews are covered through the existing dialog styling path.
- No media scan, query, adapter publication, sorting, delete recovery, Viewer neighbor, permission, or photo-profile loading code changed.

## Files changed

- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicEdgeGlow.kt`
- `app/src/main/kotlin/org/fossify/gallery/views/ClassicGlowScrollViews.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicMotion.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenFastScroller.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicDialog.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicDialogScrollView.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/{MainActivity,MediaActivity,SearchActivity,PhotoProfileManageActivity,IncludedFoldersActivity,HiddenFoldersActivity,ExcludedFoldersActivity,OxygenThemeActivity}.kt`
- `app/src/main/res/layout/{activity_settings,activity_diagnostics,activity_photo_profile_edit}.xml`
- `app/src/main/res/values/classic_ids.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/OxygenClassicEdgeGlowTest.kt`

## Verification

- Added deterministic tests for refresh top suppression, bottom glow preservation, horizontal-list exclusion, refresh-parent detection while refresh is disabled, custom accent rendering, and immediate animation-scale-zero completion.
- `git diff --check`: passed.
- Local Gradle test command could not start because Gradle 9.5.1 could not be downloaded from `services.gradle.org` (`Network is unreachable`).
- First GitHub Actions run [#596](https://github.com/Pew2018/Gallery/actions/runs/42338816430) failed during Android resource parsing, before Kotlin compilation or APK assembly. The large Settings layout had an output-truncation banner prepended while it was transferred. The complete, well-formed workspace file is being restored in a follow-up commit; this was not a source-layout syntax error.
- The resource fix allowed Kotlin compilation to start, but follow-up run [#597](https://github.com/Pew2018/Gallery/actions/runs/42339079383) then found the same transfer-truncation issue in the two large activity source files. Their complete workspace versions are being restored in a second follow-up commit.
- Passing follow-up Actions result: pending.
- Pixel 8 Pro Android 16 verification is **待真机验收**: refresh-vs-top-glow gesture, bottom and top release curves, API 31+ stretch absence, color changes during glow, orientation changes, empty/short lists, Picker scrolling, and Settings/Dialog content.

## Limits

The custom glow geometry and timing are engineering approximations based on the static OxygenOS reference, not measured OEM animation values. Runtime verification is required on Android 7.1 through Android 16, including gesture cancellation and nested scrolling.
