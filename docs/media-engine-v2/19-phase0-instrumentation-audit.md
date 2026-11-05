# 19 — Phase 0 instrumentation audit

Date: 2026-11-05 (Asia/Taipei)
Repository: `Pew2018/Gallery`
Branch: `refactor/media-pipeline-stability`
Audited start HEAD: `29f3528df55c9997cfe5c95a217ddc97eb85e331`

This is a source audit and instrumentation handoff. It contains no device timing results. Evidence labels mean **F** = source fact, **R** = runtime risk inferred from the source, and **U** = not measured or not currently observable.

## Existing diagnostic facilities

- **F** `GalleryDiagnosticLogger` is initialized by `App`; it is opt-in, expires after 24 hours, writes asynchronously to app-private files with a 1,024-entry bounded queue, drops DEBUG early under pressure, rotates five files at about 1 MiB each, redacts sensitive key names and common paths/URIs/media filenames, and uses a per-process salted media identifier. The export header contains app/build/device metadata. It does not request storage access or upload logs. See `helpers/GalleryDiagnosticLogger.kt` and `docs/diagnostics/app-diagnostics.md`.
- **F** `FolderRefreshTrace` already records request, queue, worker, measured stage, read result, scan output, publication, adapter commit, directory metadata commit and timeout boundaries. It keeps a bounded 1,500-line in-memory trace. Per-file enumeration/filter events are not persisted. Its optional targeted trace has a separate switch; persistent events reuse the main Diagnostics & Logs switch.
- **F** `GalleryPerfTrace` already measures grid, album, Photo profile and Viewer boundaries. Before this phase, `end()` rejected non-allowlisted Release stage names before consulting the persistent diagnostics switch. Therefore stages such as `media_async_scan`, `photo_sort` and `photo_supplement_scan` could not be persisted in Release even when diagnostics were enabled. This phase fixes the ordering: existing Debug/Logcat behavior remains, and enabled persistent diagnostics can receive those stages.
- **F** `MediaActivity` already has per-request generation/terminal guards, staged Photo profile publication, a first-grid-publication marker and adapter-commit trace. `MainActivity` has `DirectoryScanEpoch`, a terminal gate, a bounded album executor and category scanner progress. These remain the business lifecycle authorities.
- **F** `MediaAdapter` keeps one in-flight Diff and the newest pending snapshot; it skips equal visible content. `DirectoryAdapter` likewise skips identical presentation content and records album Diff time through `GalleryPerfTrace`.
- **F** Viewer events already cover open, page-bound, close, catalog handoff, boundary waits and access validation. `viewer_page_bound` means the page was bound to the pager, not that the image was decoded and drawn.

## Start-HEAD source evidence

All locations below are from the unmodified audited start HEAD `29f3528df55c9997cfe5c95a217ddc97eb85e331`, before this phase's edits.

- **F** `GalleryPerfTrace.end()` was at `helpers/GalleryPerfTrace.kt:22–27`; the early `!enabled(stage)` return preceded the persistent logger call. Its allowlist was defined at lines 9–10.
- **F** `GalleryDiagnosticLogger.event()` and field filtering were at `helpers/GalleryDiagnosticLogger.kt:94–121`; queue bounds, rotation and byte limit were declared near lines 30–40 and `appendLine`/rotation near 174–202. `anonymousId()` used the session salt at lines 160–161.
- **F** HOME request and worker entry were `MainActivity.getDirectories()` at `activities/MainActivity.kt:1063–1124`; terminal publication was `finishDirectoryScan()` at 1127 onward; the per-folder index and metadata path began at `gotDirectories()` around 2059. Categorized work was `loadCategorizedFolders()` around 1459 and `CategorizedFolderScanner.scan()` was attached at roughly 1498.
- **F** Folder requests were begun in `MediaActivity.getMedia()` at `activities/MediaActivity.kt:1704`; `GetMediaAsynctask` was constructed in `startAsyncTask()` around 1800 and submitted to the existing bounded scan executor. `GetMediaAsynctask.doInBackground()` began around line 67 and `onPostExecute()` around 300.
- **F** `MediaFetcher.readFolder()` began at `helpers/MediaFetcher.kt:40`; shared provider cursor work was `queryMediaCursor()` around line 703; provider query aggregation around `getIndexedMedia()` began near 749; sorting was `sortMedia()` at 1108. File enumeration and metadata reads were wrapped with `FolderRefreshTrace.measure()` around 494 and 593.
- **F** Photo profile stages and cache/index/supplement flow were in `PhotoProfileMediaLoader.loadStaged()` at `helpers/PhotoProfileMediaLoader.kt:32–216`; its Room cache reads were in `readDatabaseCache()` below the main load function.
- **F** Media list submissions and Diff/commit logic were in `adapters/MediaAdapter.kt:742–835`; directory Diff/commit logic began at `adapters/DirectoryAdapter.kt:968`. The existing adapter paths already rejected stale requests and deferred during scrolling.
- **F** Viewer open began in `ViewPagerActivity.initViewPager()` at `activities/ViewPagerActivity.kt:805`; the persistent page-bound event was near line 4149; boundary wait/expansion started near 1147 and ended in `finishViewerBoundaryTrace()` near 1201.

## Call paths and new request correlation

| Area | Current path and observable boundary | New phase-0 evidence |
|---|---|---|
| Album home | `MainActivity.getDirectories()` → `GalleryAlbumScanExecutor` → `gotDirectories()` → `MediaFetcher` → progressive `setupAdapter()` → `finishDirectoryScan()` | HOME request summary with queue/worker/publication elapsed times and source, sort, cache/DB and UI summary slots. Directory-count candidate totals remain `-1` when not measured. |
| Categorized albums | `MainActivity.loadCategorizedFolders()` → `CategorizedFolderScanner.scan()` → `MediaFetcher.readFolder()` → progress/final adapter | CATEGORIZED request summary, stage count, fetcher source/sort counters and terminal state. |
| Folder / All media / Favorites / Recycle bin | `MediaActivity.getMedia()` → bounded `GalleryMediaScanExecutor` via `GetMediaAsynctask` → `MediaFetcher` → `onPostExecute()` → `publishMedia()` → `MediaAdapter.updateMedia()`/commit | A diagnostic UUID (`ml-*`) is separate from the existing business request number. Summary includes `businessRequest` so it joins existing FolderRefreshTrace and adapter events without replacing generation/epoch identities. |
| `.nomedia` | Same folder task; `MediaFetcher.readFolder()` and `FolderFileAccess.enumerate()` select actual file access when needed | Disk enumeration count/items/time and access-method-derived SAF counters are accumulated per request; no per-file persistent events are introduced. The exact `.nomedia` reason is available in the existing read-result trace, not yet normalized into the request summary. |
| Photo profile | `GetMediaAsynctask` → `PhotoProfileMediaLoader.loadStaged()` → cache/index/file supplements → validation/sort/stage publication → cache/Room handoff | Cache hit/miss and cached row counts, MediaStore query/read counts, disk supplement count, DB cache read and cache-write input rows/time, sort count/input/time/mode and stage count/publication count. |
| Viewer | Grid click/handoff → `ViewPagerActivity` catalog/neighbor path → page bound/expansion/access checks | A viewer session request token joins `viewer_open`, `viewer_page_bound`, boundary expansion and close/summary events. `viewerOpenMs` is Activity creation → initial viewer setup; `firstPageBoundMs` is Activity creation → selected pager page bound. Neither claims image decode or first drawn frame. |

The request UUID is the diagnostic correlation key. `businessRequest` is the existing Activity/scan identifier and is included for joining traces. `scopeKey` is an eight-byte digest produced by the current session salt; raw scope text, profile names and media paths are never emitted by this instrumentation. Missing measurements are emitted as `-1`; a measured zero is `0`.

## Original blind spots and remaining limits

- **F** MediaStore access was wrapped in a shared cursor query; the source had no request-level count/time aggregation for each cursor. It now records query attempts, elapsed time, and cursor rows in a request collector.
- **F** Folder enumeration and file metadata already had opt-in measured trace events, but no persistent request-level count totals. This phase accumulates those totals without per-file writes.
- **F** `MediaFetcher.sortMedia()` already called `FolderRefreshTrace.measure("sorting")`; Release persistence depended on the filtered `GalleryPerfTrace` prefixes. Sorting count, input size, mode and duration are now aggregated when the task has an enabled collector.
- **F** `PhotoProfileMediaLoader` had cache/index/supplement and Room boundaries, but the events were not one request summary. The profile loader now shares its parent task collector.
- **F** Room write APIs such as `insertAll()` do not expose actual affected rows at these call sites. Current write reporting is explicitly named `dbWriteInputRows`; it does not imply successful affected-row count. Directory metadata commits and many DAO calls are not yet timed under the common request collector.
- **F** Media adapter events now report submit, replaced pending submit, same-content skip, dropped diff and commit with item counts and diff time under the existing business request ID. Directory adapter Diff duration remains available through `GalleryPerfTrace`; a common request-keyed directory adapter operation count is not yet connected.
- **U** Thumbnail reload/cancel/unchanged-bind counts would require observing image-loader lifecycle and stable item identity across recycling. This phase does not touch Glide/Picasso lifecycle. No synthetic decode or extra image load is performed.
- **U** Pixel anchor before/after refresh, JankStats, Android Frame Timeline, Perfetto frame durations, image decode completion and actual on-screen draw are not reliably inferable from current app callbacks. No dropped-frame count is reported.
- **U** MediaStore selection-specific counts (all-media versus folder query), SAF provider calls beyond the observed enumeration method, access-mode detail for each mixed custom source, Room cleanup, and DB affected rows remain incomplete.

## Measurement safety

The request collector is created only when `GalleryDiagnosticLogger.isEnabled()` is true; disabled calls retain no request map, UUID, hashed scope, counter object or summary event. Counters are in-memory, synchronized totals and are discarded after the request. They do not change the scan result, sorting order, permission checks, cache decisions, Room transaction boundaries or Adapter commit policy. Summary events go through the existing asynchronous bounded logger. If its queue is full, diagnostics can be dropped; scan workers never wait for file writing.

The Release event payload limit is 16 fields per event. Summaries are split into scan, sort, source, cache/DB, UI, thumbnail and terminal groups. This keeps the persisted fields within that limit and uses one diagnostic request UUID across groups.

## Risks and evidence boundary

- **F** Several scopes already publish provisional results and later COMPLETE results; the new stage counters count observed publications and do not redefine COMPLETE.
- **R** Multiple requests may still wait on the existing bounded queues, and multiple source reads/sorts may still be expensive. This phase records queue/worker time and aggregate work; it does not change executor size, algorithms or caches.
- **R** A category or Activity can be invalidated while work is running. Request collectors have finite lifetime and a one-shot terminal guard; business cancellation/publication guards remain unchanged. A duplicate diagnostic terminal emits a warning.
- **U** Runtime cost and memory overhead on 5k–100k media libraries require same-device comparison with diagnostics off/on. No Android device or `adb` executable was available in this environment.
