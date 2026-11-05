# In-app diagnostics and logs

## Scope

The `Diagnostics & Logs` page is available from **Settings → Advanced → Performance**. Logging is off by default and automatically expires 24 hours after it is enabled. The page allows the user to copy recent logs, export a UTF-8 TXT file through Android's document picker, and clear diagnostic and crash files without changing the logging switch.

The logger is initialized by `App`. It keeps records in the app-private `files/diagnostics/` directory. It does not collect Logcat, request storage permissions, upload data, or add files to Gallery backup formats.

## Event format and sources

Each persistent event is one readable line with wall-clock time, level, module, event name, per-process session ID, optional request ID, monotonic elapsed time, and selected key/value fields. The writer uses one background executor with a 1,024-entry bounded queue. DEBUG events are dropped early when the queue is nearly full; later events emit an `events_dropped` count when the writer next runs.

The active file rotates at about 1 MiB across five numbered files (5 MiB total). Older files are removed first if the configured total limit is exceeded. The existing Logcat output and 1,500-entry in-memory `FolderRefreshTrace` behavior remain available under their original setting.

Persisted event sources currently include:

- `FolderRefreshTrace` request, worker, measured stage, read result, scan output, publication, adapter commit, directory metadata commit and timeout boundaries. Per-file `enumerated`, `type_passed` and `metadata_passed` events remain available only through the older targeted folder trace; they are not copied into persistent diagnostics.
- Existing `GalleryPerfTrace` grid, album, photo-loader and viewer stage/count events. Release Logcat behavior is unchanged; user-enabled diagnostics can persist stages outside the existing Release Logcat prefix allowlist.
- Full-screen Viewer open, page-bound and close events, with the current index, visible count, content revision, collection type and a session-keyed media identifier.
- Grid and full-screen delete request/result boundaries, including requested/completed counts and whether the operation was permanent or used the recycle bin.
- Photo-profile full-screen delete recovery stages, path-identity candidate selection, validation failures, and the pager's applied item/index. Recovery events reuse the delete operation ID and use session-keyed anonymous media IDs; no media paths are recorded.
- Java/Kotlin uncaught exception summaries, written synchronously as a bounded best-effort crash file before delegating to the previous process handler.
- Android 11+ historical process exit reason numbers when enabled at app start. This is supplementary system metadata, not full ANR or native-crash capture.

## Privacy controls

Event callers select fields explicitly. Keys related to paths, URIs, credentials, passwords, PINs, GPS and location are omitted. Values and crash stack lines receive a second scrub for common private storage paths, content URIs and media filenames. Per-item correlation IDs are salted with the current process session ID, so they are not stable across app launches. The exported header contains app version/build type, Android release/API, manufacturer/model, log/session times and enabled status. It excludes account identifiers and media content.

Copy keeps at most 250 KiB of the newest text and adds a truncation note. Export streams the snapshot and crash summary to a user-selected document URI on a background thread. Canceling the picker does nothing. Clearing is serialized with queued log writes; logging may continue after the clear operation when the switch remains on.

## Files and tests

- `GalleryDiagnosticLogger.kt`: opt-in configuration, async bounded writer, rotation, snapshots, export header, redaction, exit metadata and chained exception handler.
- `DiagnosticsActivity.kt` and `activity_diagnostics.xml`: Oxygen Classic diagnostics page and document actions.
- `GalleryPerfTrace.kt`, `FolderMediaReadResult.kt`, `MediaActivity.kt`, `ViewPagerActivity.kt`: focused event bridges.
- `DiagnosticPrivacyTest.kt`: expiry behavior, privacy redaction and preservation of the original uncaught-exception delegate.

## Automated verification

- Final source commit: `82c4b4ffe26b80cdb6c6b6303ce2a9488998dad4` on `fix/photo-viewer-five-item-boundary`.
- GitHub Actions Run [41858925567](https://github.com/Pew2018/Gallery/actions/runs/41858925567) completed successfully on that exact commit.
- Full FOSS Release unit suite: 848 tests, 0 failures, 0 errors, 0 skipped.
- ARM64 Release APK assembled and passed package name, v2 signature and arm64-only ABI checks. Artifact: `gallery-foss-arm64-v8a-release` (ID `13338484426`). APK SHA-256: `57a3cceb80e1960587d9c4c0b516a5002808644cf4340f7219ca9c7b11daf1d0`.
- CI used a temporary signing key (`temporary-CI-not-upgrade-compatible`); the APK cannot be assumed compatible as an in-place upgrade over a differently signed installation.
- The added diagnostic-specific test class covers expiry, redaction and uncaught-handler delegation. The other store concurrency, export-provider, UI interaction and device lifecycle cases listed in the feature request do not each have dedicated new tests; the project-wide suite passing is not evidence for all those scenarios.

On-device visual behavior, real provider export compatibility, crash persistence under abrupt power loss, and performance impact still require a physical-device check. Native crashes, ANRs whose process is killed before managed code runs, and system force-stops cannot be guaranteed to produce a stack trace.

## Media pipeline Phase 0 additions

The `refactor/media-pipeline-stability` branch adds request-scoped events through `MediaPipelineDiagnostics` while reusing this switch, expiry, privacy rules, private storage, queue and rotation. See [Phase 0 audit](../media-engine-v2/19-phase0-instrumentation-audit.md), [metric plan](../media-engine-v2/20-phase0-metrics-and-test-plan.md) and [results](../media-engine-v2/21-phase0-results-and-handoff.md).

For enabled requests, one `ml-*` request token links split `media_scan_summary`, `media_sort_summary`, `media_source_summary`, `media_cache_db_summary`, `media_ui_summary`, `media_thumbnail_summary` and `media_load_terminal` events. The generated request token does not replace Activity request IDs; `businessRequest` connects them to existing FolderRefreshTrace and Adapter event records. A per-session salted `scopeKey` is emitted instead of the raw path/scope. Missing metrics use `-1` and measured zero uses `0`.

Observed counters cover MediaStore cursor attempts/rows/time, filesystem enumeration and metadata calls, sorting, selected Photo profile cache and Room boundaries, stage publication, and MediaAdapter submit/diff/commit events. This is not a complete Room trace. Actual database affected rows, all directory metadata/cleanup queries, thumbnail lifecycle, anchor displacement, decoded/drawn image frame and frame jank are not currently measured. Do not treat `viewer_page_bound` as an image draw. The full field-level limits and comparison protocol are in the metric plan.

The 848-test/Run `41858925567` record above is the historical diagnostics feature build. It is not evidence that Phase 0 changes passed; use the actual current branch Actions and artifact recorded in the Phase 0 results file.

## Device acceptance steps

1. Open **Settings → Advanced → Performance → Diagnostics & Logs** and enable logging.
2. Reproduce a scan, refresh, photo-profile, Viewer swipe or deletion issue.
3. Return to the page and use **Copy logs** for a recent excerpt or **Export logs** to save the full TXT.
4. Attach the exported file to the issue report after reviewing it for context-specific sensitive text.
5. Use **Clear logs** to remove records and any previous crash summary. The toggle is preserved.

For system traces outside the app-private managed diagnostics, ADB/Android Studio may still be required.
