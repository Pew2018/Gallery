# Phase C — Theme-aware OxygenOS Classic edge feedback

## Baseline and implementation

- Remote baseline: `923517adcbcba57556670657f0ffa5e28ad0fc0c`, fetched before this phase's commit.
- Added one shared RecyclerView edge-effect factory. It draws a short, soft classic edge glow from the current `OxygenThemeEngine.snapshot(...).interactiveAccent`; the color is resolved while drawing so a theme change does not leave a cached blue glow.
- Vertical RecyclerViews under `SwipeRefreshLayout` suppress only their top edge. The factory does not inspect `isEnabled`, so search mode cannot unexpectedly replace the refresh affordance with a top glow. Bottom feedback remains enabled. Horizontal RecyclerViews suppress vertical-style effects.
- Empty and non-scrollable RecyclerViews do not draw a glow. `RecyclerView` retains its built-in scroll and fast-scroll touch handling; no scroll listeners, touch dispatch, adapter order, or position restoration are replaced.
- Added shared classic-glow ScrollView and NestedScrollView subclasses for Settings, diagnostics, Theme, Photo Profile Edit, only. Page containers disable platform stretch and draw the shared bounded glow when content can scroll. All dialog content now follows the permanent NONE contract, including security input.
- All Directory/media/Commons FilePicker and dialog lists now use the permanent NONE policy. Fast-scroller styling has no Glow installation side effect.
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

- Added deterministic tests for refresh top suppression, bottom glow preservation, horizontal-list exclusion, refresh-parent detection while refresh is disabled, runtime re-resolution of a changed accent during drawing, and immediate disabled-motion release. The Robolectric test validates the accent delivered to the painter; exact Glow raster appearance remains a device visual check.
- `git diff --check`: passed.
- Local Gradle test command could not start because Gradle 9.5.1 could not be downloaded from `services.gradle.org` (`Network is unreachable`).
- GitHub Actions runs [#596](https://github.com/Pew2018/Gallery/actions/runs/42338816430) and [#597](https://github.com/Pew2018/Gallery/actions/runs/42339079383) exposed two truncated file transfers; complete source files were restored and verified by blob SHA. Runs [#598](https://github.com/Pew2018/Gallery/actions/runs/42339438473), [#599](https://github.com/Pew2018/Gallery/actions/runs/42339693854), and [#600](https://github.com/Pew2018/Gallery/actions/runs/42340202700) surfaced and fixed compile/test issues: the reusable ScrollView was made open, Color alpha extraction corrected, reduced-motion behavior injected deterministically, and scroll-view accent lambdas bound by name.
- Run [#601](https://github.com/Pew2018/Gallery/actions/runs/42340459954) passed Kotlin compilation but exposed a brittle single-pixel edge-boundary assertion. The test now checks that draw resolves the current color supplier and that release at disabled motion immediately reaches the zero state.
- Run [#602](https://github.com/Pew2018/Gallery/actions/runs/42341024145), commit `a12910b7ef2f8f72f048889d3d56d552e652d366`: complete FOSS Release unit-test suite passed; ARM64 Release assembly, package/signature/ABI validation, and APK artifact upload all passed. Artifact ID: `13562365004`; SHA-256: `c3bff45b058cc6f229651796acd949628282dd564bf2a49f8bd56a0a24dce177`.
- Pixel 8 Pro Android 16 verification is **待真机验收**: refresh-vs-top-glow gesture, bottom and top release curves, API 31+ stretch absence, color changes during glow, orientation changes, empty/short lists, Picker scrolling, and Settings/Dialog content.

## Limits

The custom glow geometry and timing are engineering approximations based on the static OxygenOS reference, not measured OEM animation values. Runtime verification is required on Android 7.1 through Android 16, including gesture cancellation and nested scrolling.

## 2026-11-07 永久契约修正

历史 app-wide follow-up 重新为弹窗接入了低强度效果，这是错误的工程契约，现已删除。弹窗不允许以低强度/低透明度替代彻底禁用。

当前实现及测试以 [EDGE_GLOW_PERMANENT_CONTRACT.md](EDGE_GLOW_PERMANENT_CONTRACT.md) 和 [修复审计/设备验收表](edge-glow-repair-audit-2026-11-07.md) 为准。上面的 Phase C 实施说明与旧 Actions 结果仅为历史记录，不代表本轮提交的验证结果。
