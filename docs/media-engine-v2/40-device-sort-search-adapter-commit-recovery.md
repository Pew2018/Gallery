# Device sort/search Adapter commit recovery

## Baseline and scope

Remote `refactor/media-pipeline-stability` was fetched before editing on 2026-11-05.
Its actual HEAD was `44717100de2e051ee27b89906c3d87f0137ceca0`; the working tree was clean.
No branch, PR, main change, schema, migration, or second index is part of this work.
The supplied Pixel 8 Pro / Android 16 release logs were read in full by the timeline
parser (116, 450, 1,915 and 1,365 events respectively), correlating request, scope,
generation and Adapter identity/version. No raw paths, filenames or query text are
copied into this document.

## Four device timelines

1. **Cache sorting during progress**: scope `2a99ce9a2a5fde23`, request 1 displays
   11,023 cached items at 23:42:19.247. Sort generations 2/4/6 at
   23:42:21.293 / 25.377 / 30.147 all defer with UNKNOWN stage and
   `snapshot_not_authorized`. `getCachedMedia -> gotMedia -> publishMedia -> setupAdapter`
   displayed a permitted cache, but `previewSortDuringFolderScan` required a
   `folderReadResult` that did not yet exist. ALL_FILES and cache hit are recorded;
   this is not evidence that Android permission was revoked. Requests 1–4 are
   canceled/restarted; `stop_fetching` has multiple callers (including explicit
   refresh, lifecycle and startAsyncTask). There is no `sorting_refresh_required`
   in this log. Therefore attribution of every restart to sorting is **unproven**.
   Source already avoids an unconditional sort restart while scanning.
2. **Completed ordinary folder**: same scope; request 1 cache commits at
   23:40:06.981. Scan completes 23:40:21.703, after ~15.18 s worker time;
   10,677 ms metadata and 3,313 ms enumeration are aggregated. Final Adapter submit
   is 23:40:22.407, generation 1, Adapter `99757629`, version 1. Five sorts submit
   generations 2–6, but none commit. Generation 1 is dropped only at 23:40:46.333:
   **23,926 ms submit-to-drop**, not a measured CPU-only Diff duration. Search
   generations 1–6 compute lists of 10,055 / 2,471 / 277 / 2,471 / 10,055 / 11,023
   and submit Adapter generations 7–12. All observed submissions remain at version 1;
   there is no matching `search_results_committed` for these inputs. The early
   `diffRunning` return blocks even same-content/reorder paths. Cache/final metadata
   differences can also make a user sort ineligible for the pure-reorder shortcut.
3. **Sorting scope**: no evidence of cross-folder sort preference contamination.
   However Adapter `23321792`, 3,170 rows, submits at 23:44:50.556; a user sort at
   52.883 submits generation 2; request 2 replaces that at 56.564; generation 1 is
   dropped at 58.697 (**8,141 ms**). This is the same commit starvation at a smaller
   size, not proof all other folder behavior was healthy. Other Adapters (354, 75,
   four rows) commit/skip normally. Home enumeration and repeated navigation events
   are separate from this Adapter's lifetime.
4. **.nomedia**: scope `f2e4ce5033f4efbb`, 2,012 media. Request 2 commits at
   23:43:22.361 (15 ms logged diff). Eight sorts commit versions 3–11 in
   **15–86 ms** end-to-end via reorder. Search 2,012→1,150→2,012 and
   2,012→948→2,012 commits in **187–202 ms**; clearing search restores membership.
   Request 3 final refresh is a same-content skip at 46.932, version 14. This path
   must retain its cheap reorder and small Diff behavior. Cancellations near
   21.197 and 45.019 do not identify their initiator; no metadata-refresh-required
   event proves a sort caused them. Fast-scroller sorting was read only at Adapter
   construction, a source-confirmed latent bug even when these rows committed.

All four logs' pipeline UI summary fields are -1. They cannot substantiate actual
Adapter commits or imply there were zero UI events. A scan terminal event precedes
publication and must not be treated as a displayed-list terminal event.

## Repair and version rules

- `MediaAdapter.updateMedia` owns monotonically increasing generations and one
  latest pending immutable snapshot. There is still at most one background Diff
  per Adapter; the executor's worker count is unchanged.
- Main-thread layout/scroll, Activity, attached Adapter and caller access/current
  checks run **before every** commit. Same-content and pure-media reorders run
  before the in-flight-worker check. Reorders keep stable IDs and position offset,
  rebind visible positions, clear obsolete anchor callbacks, and disable animations
  as before. Membership is checked by unique media path and rendered content.
- Explicit USER_SORT / USER_SEARCH can replace the snapshot while a worker is
  blocked, including cache/final metadata differences. They do not start a second
  worker. After that commit the old generation can only finish/drop, never dispatch.
  Cooperative checks during Diff comparisons also abort superseded calculations.
- Large edit windows (at least 4,096 rows after equal identity prefix/suffix are
  removed) use atomic snapshot replacement. This bounds Myers edit-distance work
  for permutations and broad search filtering. Size alone is insufficient:
  identity-preserving metadata changes and small edits still use Diff. This is a
  conservative starting policy, **not a device-tuned performance threshold**.
- Whole replacement maintains sections, grid decoration, selected stable keys and
  media IDs; it uses notifyDataSetChanged with no item animator. Normal refresh
  remembers/restores a path anchor only after commit. User sort retains the ordinal
  viewport; search does not restore an obsolete full-list path anchor. Unchanged
  thumbnail request keys still suppress decoder/request reloads.
- Diff exceptions and rejected execution clear single-flight state, record the
  exception type and schedule one layout/access-gated replacement. Recovery cannot
  recursively re-enter the failing Diff. No stale callback clears a newer worker:
  no newer worker starts until this worker's main-thread terminal callback.
- `sorting` changes with the committed snapshot on every strategy, keeping bubble
  information consistent. Scan completion and pending visible publication remain
  distinct: the existing header progress can remain until a current Adapter commit.

## Displayed cache capability and latest source

`DisplayedFolderSnapshot` records request, source version, CACHE_PREVIEW /
SCAN_PARTIAL / SCAN_COMPLETE and a current access closure. CACHE_PREVIEW is never a
FolderReadStatus.COMPLETE or a file-existence proof. Cache capture does not enumerate.
On shared storage it requires all-files access, a covering persisted SAF read grant,
or legacy read permission on pre-R. All-files permission alone does not authorize
SAF-restricted paths: direct cache access also requires current readable-directory
capture and the existing Commons SAF restriction check; otherwise a covering tree
grant is required. Tree authority/document ID, canonical path
boundary and grant revocation are checked; no permission is requested or bypassed.
Protected ancestor/session checks and visibility/selection configuration are retained.
Scoped-storage cache without a covering grant is conservatively withheld until a
scan supplies its access capability. Virtual sources keep their existing policies.

Physical-folder publication filters protection/visibility again, sorts the full source
on a worker using latest settings, then validates settings before publication. A
choice changed during preparation reschedules preparation, not directory enumeration.
Grouped cache previews retain their existing conservative eligibility policy: no
flattening or fabricated headers, and no restart while a scan is active. Complete
grouped-source sorting retains its existing scan fallback; instant cache preview
acceptance is deliberately limited to plain media rows.

Every accepted source publication increments source version. Search captures that
version, query generation and effective sorting; it always filters mMedia (the full
source), and final/partial publication reapplies the current query. Pending sort
workers cannot replace a newer scan stage of the same request.

Physical list scans already read size and modified metadata once per file. They now
retain all sort fields and load folder date overrides during that scan, regardless
of the initial sort, so later size/date choices don't discard the entire scan.
This retains the existing .nomedia reader and optional metadata machinery.
`retainSortMetadata` is explicitly enabled only by MediaActivity; Viewer and other
callers keep their previous field requests and missing-metadata fallback. There may be
an extra folder-scoped date query when it was not initially requested; file enumeration
and per-file basic metadata are not repeated. Device query overhead: NOT_MEASURED.

## Diagnostics

New events: adapter_diff_started/finished/failed, adapter_snapshot_fast_commit,
adapter_update_deferred, folder_snapshot_selected, scan_restart_requested. They use
existing bounded opt-in logging; deferred events are limited to once per generation.
Fields correlate business request, source stage/version, Adapter generation/version,
reason, strategy, counts, sorting and elapsed time; no raw paths or search text.
Existing adapter_submit/replaced/drop/commit and search/sort committed events remain.
Fast commit means Adapter mapping/notifications committed; physical display-frame
latency still needs device observation. It does not prove pixels were rendered.

## Validation

Local verification on the implementation before the last diagnostic/style refinements:
focused suite **49 tests / 0 failures / 0 errors / 0 skipped**; full FOSS Release
suite **917 tests across 132 XML suites / 0 failures / 0 errors / 0 skipped**.
Final frozen-source verification is recorded below after completion.

The first Lint run encountered a Kotlin FIR internal indexing exception. Sources
had changed while it was running, so that run is not accepted as a clean Lint
result. The final run uses frozen sources. Detekt was also run against an archived,
unmodified copy of the fetched HEAD with the exact same configuration/baseline:
**2,752 findings**. Current frozen sources report **2,749 findings**. Existing
configuration and baseline were not weakened. Rule/file/normalized-message
comparison found no new rule/message class; new-source-file and added-line
findings were separately checked during cleanup. Existing debt still makes the
repository-wide detekt task fail; this is not a passing detekt claim.
Regression coverage includes held old Diff with sort/search assertions before release,
2,012/5,000/11,023 rows, final permutation plus metadata changes, search clear,
metadata-only linear Diff, section-preserving replacement, executor failure recovery,
access invalidation/detach, current sorting, and ordinary cache sort without any read
result or restarted scan. Existing tests are retained.
Host metrics are emitted to `app/build/reports/ui-regression/media-commit-host.csv`
and included in the existing Actions test-report artifact. They measure synthetic
Adapter submit-to-callback, **not** Pixel input-to-frame or actual storage scanning.
Baseline comparable host metrics: NOT_MEASURED. Device post-fix performance: PENDING_DEVICE.

## Pixel acceptance (all PENDING_DEVICE)

- 11,023-row ordinary directory: cache sort while progress is visible; all sort modes
  after progress ends; verify thumbnails actually change and latest bubble values.
- Rapid A→B→A, search typing/backspace/clear, sort during search and final scan during
  query: final membership/order matches the last intent, without an extra enumeration.
- Compare large refresh/sort/search viewport stability, no card-like animations,
  flash, header mismatch or unnecessary image requests; repeat while dragging.
- Repeat .nomedia 2,012-row sorting/search/clear and folder-specific/global scopes.
- Interleave delete, protected-session revocation, SAF grant loss, Activity exit,
  scan finish and final Diff callback; no resurrection or unauthorized publication.
- Measure input timestamp → Adapter commit → rendered frame. The ~1 s goal is not
  established by host tests. Record counts, versions, strategy and scan-restart reason.

## Measurement boundaries

The held-executor regression holds the old Runnable before calculation and asserts
new Adapter rows/versions synchronously before releasing it. This models an occupied
single-flight slot deterministically; it is not a CPU benchmark of an actively
blocked Android DiffUtil thread. Each 2,012/5,000/11,023 case proves that the released
old generation cannot advance committedVersion or replace the latest 12-row query.
Host queue-wait and isolated Diff CPU timings: NOT_MEASURED; submit-to-callback and
queued task counts are recorded. The new real-runtime diagnostics separately record
queue wait, Diff computation and publication wait. Device stale-drop counts and
physical scan restart counts after this repair: PENDING_DEVICE. The cache Activity
regression observes unchanged request ID and no newly created scan task.

## Final local verification

Frozen sources: **917 tests / 132 suites / 0 failures / 0 errors / 0 skipped**.
`compileFossReleaseKotlin` and test compilation passed. `git diff --check` passed.
Command: `:app:testFossReleaseUnitTest :app:compileFossReleaseKotlin
:app:lintFossRelease :app:detekt --continue` (JDK 17, Android SDK 36).
The combined command exits nonzero because the static tasks fail:

- Lint completed analysis: **4 errors, 789 warnings** after the existing baseline.
  Errors are two ignored Glide transition results in Context.kt:781, ignored
  Rect.intersect result in FavoritesReturnCover.kt:45, and AppCompatCustomView in
  ToolbarQuickActionsDialog.kt:93. Git blob hashes confirm all three source files
  are byte-for-byte identical to the fetched HEAD. No Lint error names a changed file.
- Detekt: **2,749 findings / 3,140 weighted issues** versus unchanged HEAD
  **2,752 / 3,144**. Both were executed; neither task is reported as passing.
  No test, baseline, static configuration, or unrelated Viewer code was weakened.

Final host measurements below were taken while Lint also ran, so scheduling and
JVM warmup materially affect them. All times are milliseconds, synthetic Adapter
submit-to-commit callback, with no device rendering or real file enumeration.

| Scenario | Rows submitted | Submit → commit ms | Queued Diff tasks | Final rows |
| --- | ---: | ---: | ---: | ---: |
| initial | 2012 | 25.686 | 1 | 2012 |
| sort | 2012 | 35.945 | 0 | 2012 |
| initial | 5000 | 3.961 | 0 | 5000 |
| sort | 5000 | 8.632 | 0 | 5000 |
| initial | 11023 | 10.008 | 0 | 11023 |
| sort | 11023 | 13.209 | 0 | 11023 |
| final_changed_order_and_metadata | 11023 | 15.595 | 0 | 11023 |
| search_narrow | 120 | 0.301 | 0 | 120 |
| search_clear | 11023 | 40.988 | 0 | 11023 |
| metadata_only | 11023 | 29.325 | 1 | 11023 |

The earlier isolated focused run measured 2,012/5,000/11,023 pure reorder at
2.190/3.862/5.860 ms; these are separate runs, not a claimed before/after comparison.
Old device visible-commit delay: at least the logged 23,926 ms submit-to-drop window,
with no successful final commit for the affected operation in that log. Post-fix
Pixel timings and visual/scroll acceptance remain **PENDING_DEVICE**.

## Post-push verified delivery and current-code audit (2026-11-06)

Verified implementation SHA: `bebbe96692a964cc7669c47e481e30f6e93030aa`
on `refactor/media-pipeline-stability`; its parent was
`44717100de2e051ee27b89906c3d87f0137ceca0`. The implementation
changed `MediaActivity`, `MediaAdapter`, `GetMediaAsynctask`,
`DisplayedFolderSnapshot`, `MediaUpdatePolicy`, three regression-test files,
this document and its README index. No Room schema/migration, Viewer, new
persistent index, CI workflow or `main` changes are part of that commit.

### Verified GitHub Actions (the exact implementation SHA)

- [Run 42068997299](https://github.com/Pew2018/Gallery/actions/runs/42068997299):
  push event, completed **success**, attempt 1, `head_sha` exactly
  `bebbe96692a964cc7669c47e481e30f6e93030aa`.
- Full FOSS Release JUnit XML aggregation: **917 tests, 0 failures, 0 errors,
  0 skipped**. ARM64 Release assembly, APK checks, artifact upload and
  temporary-keystore cleanup passed.
- APK artifact `gallery-foss-arm64-v8a-release`, **ID 13436865163**.
  Uploaded *artifact ZIP* SHA-256:
  `351fd2e4ef0d23370701533b3e84813f318a365c2922a5caaccbce9c8dcabf83`.
  Enclosed *APK* SHA-256 recorded in the workflow:
  `6d76d391b70f242d7b0885af47162f0a02276a5f379252bd04c3b5e8a9430de3`.
- Verified package `org.fossify.gallery`, version `1.14.1` (code `32`),
  native libraries `arm64-v8a` only and APK Signature Scheme **v2**. Signer:
  `CN=Gallery Temporary CI Test Signature, O=Local Testing`. A differently
  signed installation may not accept this CI APK as an in-place update.
- Test report artifact `backup-ui-test-reports` **ID 13433374868**; source
  UI preview artifact **ID 13435645534**.

### Source and verification boundaries

- `MediaAdapter.submitPendingUpdate()` attempts same-content, pure reorder
  and bounded whole-snapshot replacement before the `diffRunning` wait;
  a superseded background Diff cannot publish stale rows. This addresses
  user-sort/search commit starvation without creating a second Diff worker.
- `MediaActivity` now attaches effective sorting, source-version and
  access checks to adapter publication. Authorized `CACHE_PREVIEW` is
  represented explicitly, separate from scan completeness.
  `GetMediaAsynctask` retains optional sort metadata for physical-folder
  scans started by MediaActivity.
- New unit tests model an old held Diff and latest sort/search for 2,012,
  5,000 and 11,023 rows; section-preserving replacement, permission loss,
  rejected executor and cache-preview sorting are also covered. They do
  **not** measure on-device input-to-rendered-frame latency.
- The local independent Lint (4 errors / 789 warnings) and Detekt (2,749
  findings) results described above are **not passing**, despite the
  successful CI APK workflow. Host timing figures are synthetic, not a
  measured old-to-new Pixel speedup.
- **PENDING_DEVICE:** Pixel 8 Pro / Android 16 final-frame responsiveness,
  scroll anchor and thumbnail stability, rapid sort/search/clear, grouped
  files, `.nomedia`, FastScroller sorting bubble, SAF/protected session
  revocation, scan restarts and physical-sort metadata-query overhead.
  The four source logs precede this fix, not post-fix acceptance.

This documentation-only follow-up cannot retrospectively change the verified
implementation SHA or its APK. Any workflow triggered by the documentation
commit must be considered a separate run.
