# Theme dialogs and refresh diagnostics cleanup

2026-11-07. Baseline: `faf38ceabc1d01bf570faa682ca25c9d972cec25` on
`refactor/media-pipeline-stability`.

## Implemented interaction boundaries

- `OxygenThemeActivity` remains the theme/appearance page, with all **11 classic**
  colors from `OxygenThemePresets.group("classic")` in source order. Appearance,
  current-color summary, custom color, and restore-default remain. Its scroll
  state and cross-Activity settings return transition remain. The `moreColors`,
  `navigateColors`, `theme_more`, icon Activity extra and icon subpage are removed.
- `MaterialColorPickerDialog` reads only `group("material")`: the original 12
  IDs, translated labels, seed HEX values and order are unchanged. Each row has
  a 28dp swatch, 16sp label and passive 24dp radio; the whole row owns its bounded
  ripple/click and selected accessibility state. A classic/custom selection
  selects no Material radio. Cancel writes nothing. Selection dismisses first,
  then reuses Activity `activatePreset` → `change` → `refreshThemeIfNeeded`.
- `AppIconPickerDialog` uses the existing `getAppIconIDs/getAppIconColors`
  resources. The 80dp preview and HEX update from a local selection index. Grid
  columns are recalculated from measured content width (up to five, 64dp cells).
  Cells use 48dp original icons, 2dp semantic-accent outline, 4dp corners and 8%
  selected tint. Only Apply writes a changed `appIconColor` and calls the original
  `checkAppIconColor` launcher mechanism. Cancel, back, outside cancellation and
  host destruction discard selection. The settings icon row does not set the
  theme navigation lock or start an Activity.
- `ClassicCustomColorDialog` freezes the current resolved `snapshot.rawAccent`
  on open, including legacy/system mode, instead of the last saved custom seed.
  Equal-width 60dp previews compare current and new colors. A labeled, minimum
  48dp input uses uppercase text and the existing strict `parseHex/formatHex`.
  Trimming is allowed; only `#RRGGBB` is valid. Invalid edits immediately show a
  neutral new swatch, dash, error and disabled Save. Valid edits show uppercase
  HEX; they never write preferences. Save dismisses then invokes the existing
  theme change path with custom seed/ID/activation. Cancel writes nothing. The
  redundant third restore button is removed; main-page restore remains.

## Shared presentation and lifecycle

`ClassicThemeDialog` uses `OxygenClassicDialog.builder/show`; no new window
animation or framework. A dedicated `classic_theme_dialog_content` root selects
only the new THEME width profile, max 400dp with 16dp window-edge clearance.
Other profiles and IDs are unchanged. The scroller reserves measured fixed
AlertDialog title/actions and constrains the complete dialog to about 80% of
available host height. The existing host window/insets/IME calculation and
ADJUST_RESIZE remain authoritative, with no fixed Y offset. Custom input starts
without focus/keyboard; focused editing scrolls within available height.

Only one theme dialog opens per host. Commits require a showing window and live
host, dismiss before mutation, and ignore repeated taps. A lifecycle observer
dismisses on host destruction; dismissal removes the observer and host tag.
Selection controls expose names and state; radios have no independent click or
focus target. Rows wrap labels at larger fonts; title/actions remain outside the
scroller. Dialog colors use current theme tokens and shared control styling.

All content uses `ClassicDialogScrollView`, which calls
`DialogOverscrollPolicy.protect` before attachment. Shared show hooks protect
content/decor and dynamically added children. **No Glow or Stretch is introduced.**
The permanent contract in `EDGE_GLOW_PERMANENT_CONTRACT.md` remains controlling.

## Removed refresh feature, retained independent logs

Removed settings initialization, `setupRefreshDiagnostics`, target EditText,
enable/disable/copy dialog, clipboard access, both settings registration/group
mappings, XML row/children, and six exclusive strings in English and Simplified
Chinese. Global source searches find no old view or `folder_diagnostics` resource
reference. Old manual preferences are no longer read or written.

The former `FolderRefreshTrace`, defined alongside `FolderMediaReadResult`,
combined two features: the manual trace and an independent persistent logger
bridge (`GalleryDiagnosticLogger.event`, category `FolderRefresh`). That second
purpose has real consumers in MainActivity/MediaActivity, GetMediaAsynctask,
MediaFetcher, CategorizedFolderScanner and DirectoryMetadataRepository.

The retained bridge is now `FolderRefreshDiagnostics`: stage events and coarse
measurements only, gated by the existing general logger. Removed manual enabled
state, target path/hash, configure, 1,500-line memory buffer, dump, clear,
Logcat output, and per-file enumerated/type/metadata events. Independent error
IDs now use the existing salted `GalleryDiagnosticLogger.anonymousId`; no new
raw path logging. Business reads, cancellation, sorting, scan publication,
metadata writes and cache decisions are unchanged. The existing no-op metadata
measurement remains for compatibility; aggregate counters still belong to
`MediaPipelineDiagnostics`.

Retained: diagnostics/logs settings row, `DiagnosticsActivity`,
`GalleryDiagnosticLogger`, crash summaries, copy/export, `MediaPipelineDiagnostics`
and `GalleryPerfTrace`. Tests of the deleted manual buffer/target API are replaced
with privacy and retained execution/error-propagation coverage.

## Verification

- All resource XML parsed locally with ElementTree; `git diff --check` clean.
- Added `ThemeDialogTest`: actual dialogs, exact Material order and selection,
  repeated-click/duplicate-open boundaries, icon preview/cancel/apply/destroy,
  custom valid/invalid/deletion/paste sequences across classic/Material/custom/
  legacy modes, window animation and descendant overscroll protection, settings
  icon entry without Activity navigation, retained general diagnostics row.
- Added exact Material ID/name/HEX/order fixture to `OxygenThemePresetsTest`.
- Updated `ClassicSettingsControlsTest` and `SettingsPageMotionTest` to the new
  dialog navigation boundary while retaining theme-page/return-motion checks.
- Local Gradle attempt stopped before compilation: distribution download failed
  with `Network is unreachable`; this is not a passing test result.
- GitHub Actions runs the complete FOSS Release unit suite and ARM64 FOSS Release
  assembly, followed by package/signature/ABI validation. The delivery report
  records the actual commit, run result and artifact; do not infer success from
  a push alone.

## Pixel 8 Pro acceptance still required

Verify all three dialogs in light/dark modes, large text, rotation, narrow split
screen, gesture/back/outside cancellation, reduced/disabled animations and
repeated reopen. Check custom editor with IME opening/closing and full button
visibility. Check Material list overscroll at both ends has no Glow/Stretch;
check icon preview does not change Launcher until Apply (Launcher refresh may
be delayed). Verify restore-default, all 11 classic and 12 Material entries,
TalkBack names/states, retained diagnostics record/copy/export and unchanged
folder refresh/count/cache behavior. Compare deletion, security/PIN/pattern,
file picker, bottom actions and toolbar dialogs for unchanged geometry.
Automated hooks/measurements do not establish device visual acceptance.
