# Phase 2C sorting/search fix and validation

## Changes

- `MediaActivity.kt`: retain the visible adapter when a sort requires a scan; add an authorized PARTIAL-only preview for plain physical-folder rows; route preview and COMPLETE sorting through actual adapter commit callbacks; rerun filtering after media publication; bind search validity to the current displayed snapshot, page lifecycle and source authorization; keep PARTIAL empty results provisional; add query-length/count/generation-only diagnostics.
- `GetMediaAsynctask.kt`: for ordinary folders, reread the effective sorting configuration before final grouping and apply the latest mode when its required metadata was already collected. If a newly selected mode requires metadata that the scan did not collect, defer publication and start a correctly configured scan while keeping the current grid attached.
- `ChangeSortingDialog.kt`: compare effective sorting and custom-scope ownership after saving, so an equal integer does not hide a scope change.
- `MediaSortPreviewPolicy.kt`, `MediaSearchPublicationPolicy.kt`, `MediaSearchRequestPolicy.kt`: small production policies used by the UI path to gate partial sort previews, provisional empty results, and snapshot-owned search publication.
- Tests: preview authorization/section guards, partial empty search state, displayed-snapshot validity across refresh, and an actual `MediaAdapter.updateMedia()` order commit using the existing Robolectric adapter fixture. Existing sort gate/reuse tests remain unchanged.

## Safety and semantics

The PARTIAL sort is display-only and does not write Room or change scan completeness. It refuses group-header rows and special collections. Complete folder reuse still uses the pre-existing strict policy. The operation gate remains the last-choice-wins guard. Physical-folder preview and search publication recheck the folder read capability and protected-folder session before commit. Search text is never logged; diagnostics record only its length.

The complete scan applies the latest sorting mode before grouping, retaining the ordinary folder scanner and its source completeness/authorization gates. No Room schema, database version, migration, persistent cache, OxygenOS Classic style, Viewer, favorite, recycle-bin, deletion recovery or SAF implementation was changed.

## Validation status

- `git diff --check`: passed during implementation.
- Focused and complete FOSS Release unit tests: **not yet run**. The local wrapper could not download Gradle 9.5.1 because network access to `services.gradle.org` was unavailable. Therefore a local red-before-fix run was not possible; the new tests are committed as regression coverage, and the existing GitHub Actions workflow is the required validation route after the source commit is pushed.
- Pixel 8 Pro: `PENDING_DEVICE` until the user installs and tests the resulting APK.
- No repair-time or post-fix device latency is claimed. The supplied log timings are pre-fix observations only.

## CI and APK

Pending successful GitHub Actions run. Fill this section with the tested source SHA, run URL, complete test counts, artifact ID/name, APK package/version/ABI/signature type and SHA-256 only after the run and artifact have been verified. The expected CI signature is temporary unless workflow evidence says otherwise.

## Device retest checklist

1. Open a 5,000+ item ordinary folder, start a refresh, change sorting and verify current visible rows reorder without the grid disappearing; wait for completion and confirm final ordering matches the last choice.
2. Repeat A→B→A rapidly, including ascending/descending and random mode; only the final selection should win.
3. Enable date/name grouping and sort while scanning; ensure section headers remain attached to their groups and no incorrect temporary reordering appears.
4. During an active scan, open search, type a term, edit it, clear it, and type again. Confirm the existing visible snapshot filters immediately, a PARTIAL no-match stays provisional, and later matching files appear automatically.
5. Search while manually refreshing and while changing sort; verify the text remains and results reflect the newest published data, query and sort.
6. Repeat on a `.nomedia` directory with about 2,012 items and verify unindexed files remain present.
7. For protected folders, revoke access during sort/search and confirm old protected rows do not reappear.
