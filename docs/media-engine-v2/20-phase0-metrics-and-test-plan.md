# 20 — Phase 0 metrics and test plan

Date: 2026-11-05. The instrumentation is opt-in through the existing **Settings → Advanced → Performance → Diagnostics & Logs** control. Logging expires after 24 hours. No runtime performance sample is included in this plan.

## Event envelope

Each event uses the existing diagnostics line envelope: wall-clock display time, event level/module/name, per-process session, request token and monotonic `elapsedRealtimeMs`. Durations are measured with monotonic elapsed time and clamped to zero if a clock anomaly is observed.

Request events use an `ml-*` random UUID, an eight-byte session-salted `scopeKey`, an allowlisted `sourceType`, an allowlisted `loadReason`, a summarized `permissionMode`, and `businessRequest` for correlation with existing FolderRefreshTrace. Allowed source types: `HOME`, `FOLDER`, `CATEGORIZED`, `FAVORITES`, `RECYCLE_BIN`, `PHOTO_PROFILE`, `UNKNOWN`. Reasons not known by the caller are `UNKNOWN`; current task paths distinguish `INITIAL` and `REFRESH`, but do not reliably distinguish every RETURN, configuration switch or external media change.

Event groups: `media_scan_summary`, `media_sort_summary`, `media_source_summary`, `media_cache_db_summary`, `media_ui_summary`, `media_thumbnail_summary`, `media_load_terminal`. They share the same request UUID. The logger accepts at most 16 explicitly selected fields per event; groups are split to stay within that limit. Missing/unmeasured numeric fields are `-1`; measured zero is `0`. Do not aggregate `-1` as zero.

`media_scan_summary` includes `queueWaitMs`, `workerMs`, `publicationWaitMs`, `totalRequestMs`, `candidateCount`, `acceptedCount`, `terminalState`, plus request context. Queue wait starts immediately before enqueue; worker starts at `doInBackground()` or scanner callback; publication wait ends when the worker result reaches the main-thread callback/terminal adapter path available to that caller. It is not image decode time.

## Metric dictionary

| Metric | Unit | Start/end and source | Notes |
|---|---:|---|---|
| `queueWaitMs` | ms | enqueue timestamp → worker entry | Main/task queue only; category cache wait occurs before scan enqueue. |
| `workerMs` | ms | worker entry → worker exit | Includes the observed worker pipeline; does not identify CPU versus blocked I/O by itself. |
| `publicationWaitMs` | ms | worker exit → available main-thread result/terminal point | Adapter may commit later; use `adapter_commit` event for that boundary. |
| `totalRequestMs` | ms | request collector creation → terminal summary | Includes caller setup and callbacks visible to the collector. |
| `mediaStoreQueryCount` | queries | each shared MediaStore cursor attempt | Includes optional metadata queries through the same helper. |
| `mediaStoreQueryMs` | ms | each query attempt through cursor consumption/close | Accumulated per request. |
| `mediaStoreRows` | rows | cursor rows visited | Rows inspected, not accepted media. |
| `diskEnumerateCount` | operations | `FolderFileAccess.enumerate()` calls | Not number of files. |
| `diskEnumeratedItems` | items | enumerated entries reported by folder read | Accumulated observed value; repeated scans can count the same file again. |
| `diskEnumerateMs` | ms | enumerate call elapsed time | Includes the concrete access implementation. |
| `metadataReadCount` / `metadataReadMs` | calls / ms | file metadata read wrapper | Count occurs per actual wrapped call. |
| `safOperationCount` / `safOperationMs` | calls / ms | observed enumeration method identifies SAF | Incomplete for other provider operations; treat `-1` as unavailable. |
| `candidateCount` | items | observed folder candidates, completed Photo profile unique set, or provider cursor rows when that is all the source exposes | Source-specific and potentially overlapping across provider queries; if unavailable the value is `-1`, never guessed from a partial list. |
| `acceptedCount` | items | final media rows passed to task publication | Group headers excluded. It is not a count of successful image decodes. |
| `cacheLookupCount`, `cacheHitCount`, `cacheMissCount` | lookups | existing task fallback or Photo profile cache decision | Does not alter reuse rules. |
| `cachedItemCount` | items | rows returned by a cache hit | Same scope/request only. |
| `cacheInvalidatedCount` | invalidations | explicit invalidations | Not yet hooked comprehensively; `-1` when absent. |
| `diskSupplementCount` | items | profile final rows beyond indexed candidates or observed folder supplements | Overlap can exist across sources; not a unique-disk-I/O count. |
| `dbReadCount`, `dbReadRows`, `dbReadMs` | calls / rows / ms | Photo profile cached Room reads | Other Room reads remain outside the shared collector. |
| `dbWriteCount`, `dbWriteInputRows`, `dbWriteMs` | calls / input rows / ms | successful Photo profile `insertAll` call | `inputRows` is not affected rows. Directory metadata and cleanup coverage is incomplete. |
| `sortCount`, `sortInputItems`, `sortMs`, `sortMode` | calls / items / ms / integer mode | `MediaFetcher.sortMedia()` | `sortMode` is latest observed integer, not summed. No algorithm changes. |
| `mergeCount`, `mergeMs`, `deduplicatedCount` | calls / ms / items | merge boundary | Not broadly hooked; `-1` until a measured call site exists. |
| `stageCount`, `stagePublishCount` | stages | validated Photo stage observed and accepted for main-thread progress | Not every cache/provisional event is a visible Adapter update. |
| `adapterSubmitCount`, `adapterCommitCount`, `adapterDroppedCount`, `adapterSkippedSameContentCount` | operations | Adapter submit/replacement/same-content/commit events | MediaAdapter events correlate through `businessRequest`; summary slots can remain `-1`. |
| `diffCount`, `diffMs`, item insert/remove/move/change counts | calls / ms / operations | Diff calculate+dispatch boundary | Media adapter reports duration/count; operation-level item counts are not yet counted. |
| `thumbnailBindCount`, reload/cancel/unchanged bind | operations | image loader lifecycle | Not instrumented; values are `-1`, not invented. |
| anchor identity/offset and anchor displacement | ID / px | before/after refresh | Not part of this patch; existing scroll restoration remains unchanged. |
| `viewerOpenMs` | ms | Activity `onCreate` → initial `initViewPager()` setup | Includes the Activity's first setup handoff; does not measure the original tap timestamp or image draw. |
| `firstPageBoundMs` | ms | Activity `onCreate` → first selected pager page bound | `viewer_page_bound` event is the closest reliable current boundary; it does not prove draw/decode. |
| `viewerPublicationCount` | publications | Calls that hand a catalog to `updatePagerItems()` during this Viewer session | Separate from page selection/binding; slideshow publication is counted after its UI-thread handoff. |
| `viewerCatalogLoadMs`, `viewerExpandCount`, `viewerExpandMs`, `viewerBoundaryWaitMs` | ms / count | Viewer session summary and boundary expansion events | Boundary wait is measured for an accepted expansion; catalog-load duration is currently `-1`. No image first-frame claim. |
| `terminalState` | token | observed final state | `COMPLETE`, `PARTIAL`, `FAILED`, `CANCELLED`, `TIMED_OUT`, `ACCESS_REQUIRED`; unknown/invalidated as applicable. No logger event rewrites the business state machine. |

## Data interpretation

`COMPLETE` means the existing loader reported its existing COMPLETE state. A provisional or partial publication remains PARTIAL/stage evidence. Cancellation is not success. Permission failure maps to `ACCESS_REQUIRED` only when the failure is actually identified as a security/access error; otherwise use FAILED. A summary absent because a request was superseded or diagnostics were turned off is missing evidence, not an empty successful scan. Duplicate finalization records `media_load_duplicate_terminal`.

The current cache/DB/source counters are not a complete Room trace. Compare only same source, Android version, permission mode, media set, APK and operation. Do not compare `inputRows` to affected rows or infer UI jank from worker time.

## Standard device scenarios

Use B01–B20 with the request's exact conditions documented. Each row should be run at 100, 5,000, 30,000 and 100,000 media where the scenario applies.

| ID | Scenario |
|---|---|
| B01 | Album home cold start |
| B02 | Album home warm start or return |
| B03 | Ordinary folder first open |
| B04 | Same ordinary folder second open |
| B05 | `.nomedia` folder first open |
| B06 | Add image to `.nomedia` folder, then refresh |
| B07 | Favorite folder / favorite media browse |
| B08 | Custom Photo profile first entry |
| B09 | Same Photo profile second entry |
| B10 | Switch a multi-source Photo profile |
| B11 | Sort by date, name and size |
| B12 | Fast scroll during large media scan |
| B13 | Tap grid item to enter full screen |
| B14 | Repeated bidirectional swipe and boundary expansion |
| B15 | Refresh repeatedly, switch pages and cancel during scan |
| B16 | Add, delete, move or edit files during scan |
| B17 | Authorize and revoke protected sources |
| B18 | Load and restore Recycle Bin items |
| B19 | SAF / restricted storage access |
| B20 | Background return, Activity recreation or process restore |

Target OS matrix is Android API 26, 30, 34 and 36. Record model, OS/API, app version, APK SHA, permission mode (`LEGACY`, `SCOPED_STORAGE`, `ALL_FILES`, `SAF`, `MIXED`), dataset count, folder count, cold/warm/manual-refresh state and any errors/cancellations/timeouts. The matrix is a plan, not evidence that those devices were run.

## Collection and summary procedure

1. Install the phase-0 ARM64 APK on the test device. Record its SHA and signing compatibility with the installed app before attempting an update.
2. Open Diagnostics & Logs, enable logging immediately before the scenario, and verify that the 24-hour timer is active.
3. Perform one scenario at a time. For repeated operations keep the same dataset, permission, app process policy and cache state; separate cold start, warm return and manual refresh.
4. Repeat each comparable operation at least 10 times. Export the TXT log and retain the scenario notes separately. Review the export for sensitive context before sharing.
5. Group by APK, device/API, permission mode, case ID, dataset and operation type. Match `ml-*` event groups by request UUID. Join adapter/FolderRefreshTrace rows by `businessRequest`.
6. Report p50, p95, min, max and counts of FAILED, PARTIAL, ACCESS_REQUIRED, CANCELLED and TIMED_OUT. For an even-sized sample, percentile uses linear interpolation between sorted neighboring values; keep raw observations with the summary.
7. Do not combine warm and cold runs. Do not claim performance improvement from JVM unit test elapsed time.

## Automated checks

- Collector tests: independent request state, synchronized concurrent accumulation, nonnegative/accumulated durations, diagnostic event filter policy and session-scoped anonymous IDs.
- Existing `DiagnosticPrivacyTest`: default-off/24-hour expiry, path/URI/filename redaction, sensitive field filtering and exception-handler delegation.
- Release CI: complete FOSS Release unit suite followed by ARM64 Release package/signature/ABI validation. CI does not replace the device matrix.
- Device overhead comparison: same APK/data/device/operation with logging off versus on, at least 10 repetitions. Compare p50/p95 total/worker time, queue depth and dropped events; measure allocation/GC and UI frame timeline with Android Studio/Perfetto. App counters alone cannot provide precise allocation or frame-timeline metrics.
