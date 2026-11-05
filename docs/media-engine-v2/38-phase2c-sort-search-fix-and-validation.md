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
- Local focused/complete tests could not start because the wrapper could not download Gradle 9.5.1 from `services.gradle.org`; therefore a local red-before-fix run was not possible.
- Final Actions run [42029731443](https://github.com/Pew2018/Gallery/actions/runs/42029731443), tested source SHA `93726b35c27c96cd6a81955dfbf1fcc2aaa26ebe`: **902 tests / 0 failures / 0 errors / 0 skipped**. The uploaded XML report artifact was independently counted.
- The initial run [42029222905](https://github.com/Pew2018/Gallery/actions/runs/42029222905) failed at Kotlin compilation because the repository-upload API truncated the large Activity source blob. The complete local file and remote blob SHA were then verified equal, a follow-up commit was pushed, and the complete successful run above rebuilt and validated that source.
- Pixel 8 Pro: `PENDING_DEVICE` until the user installs and tests the resulting APK.
- No post-fix device latency is claimed. The supplied log timings are pre-fix observations only.

## CI and APK

Successful run: [42029731443](https://github.com/Pew2018/Gallery/actions/runs/42029731443), source SHA `93726b35c27c96cd6a81955dfbf1fcc2aaa26ebe`. The APK artifact `gallery-foss-arm64-v8a-release` (ID `13416366219`) was downloaded and inspected; it contains one APK, 18,369,179 bytes. APK SHA-256: `55b9bda771955449c6176b5d96f7c37b7f2c685a3f9202557a56fd4cab4c4b6c`.

CI verified package `org.fossify.gallery`, version `1.14.1` / versionCode `32`, `arm64-v8a` only, and APK Signature Scheme v2. The signer certificate is `CN=Gallery Temporary CI Test Signature, O=Local Testing`; this temporary CI signature is not an upgrade-signature compatibility guarantee. Test reports are in artifact `backup-ui-test-reports` (ID `13416450541`). The final documentation-only commit will have a HEAD later than the tested source SHA.

## Follow-up after device retest

The next Pixel 8 Pro logs showed that the first repair did not guarantee timely Adapter commits: several sort/search submissions were replaced or dropped while 2,012-item reorder diffs took 2.75–3.53 seconds. They also show nonzero search matches, so the observed failures were downstream of filtering. A source-confirmed path rejected the visible COMPLETE snapshot during an active refresh, then restarted the scan on sort fallback. These findings and the follow-up implementation/validation are recorded in [39](39-phase2c-followup-sort-search-display-commit.md). Follow-up source SHA `82478f070427cf3255823d08d2b298a1c4e8f0a7` passed [Run 42050016101](https://github.com/Pew2018/Gallery/actions/runs/42050016101): 907/0/0/0, ARM64 APK and validation passed; artifact ID `13425823509`, APK SHA-256 `da999870f0110628f43a3063bdfbf1e60251f95a62346e9cf6e56cbf504b30cc`. Full follow-up evidence is in [39](39-phase2c-followup-sort-search-display-commit.md). Pixel 8 Pro remains `PENDING_DEVICE`.

## Device retest checklist

1. Open a 5,000+ item ordinary folder, start a refresh, change sorting and verify current visible rows reorder without the grid disappearing; wait for completion and confirm final ordering matches the last choice.
2. Repeat A→B→A rapidly, including ascending/descending and random mode; only the final selection should win.
3. Enable date/name grouping and sort while scanning; ensure section headers remain attached to their groups and no incorrect temporary reordering appears.
4. During an active scan, open search, type a term, edit it, clear it, and type again. Confirm the existing visible snapshot filters immediately, a PARTIAL no-match stays provisional, and later matching files appear automatically.
5. Search while manually refreshing and while changing sort; verify the text remains and results reflect the newest published data, query and sort.
6. Repeat on a `.nomedia` directory with about 2,012 items and verify unindexed files remain present.
7. For protected folders, revoke access during sort/search and confirm old protected rows do not reappear.
