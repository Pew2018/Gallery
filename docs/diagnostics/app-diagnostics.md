# In-app diagnostics and logs

## Scope

The `Diagnostics & Logs` page is available from **Settings → Advanced → Performance**. Logging is off by default and automatically expires 24 hours after it is enabled. The page allows the user to copy recent logs, export a UTF-8 TXT file through Android's document picker, and clear diagnostic and crash files without changing the logging switch.

The logger is initialized by `App`. It keeps records in the app-private `files/diagnostics/` directory. It does not collect Logcat, request storage permissions, upload data, or add files to Gallery backup formats.

## Event format and sources

Each persistent event is one readable line with wall-clock time, level, module, event name, per-process session ID, optional request ID, monotonic elapsed time, and selected key/value fields. The writer uses one background executor with a 1,024-entry bounded queue. DEBUG events are dropped early when the queue is nearly full; later events emit an `events_dropped` count when the writer next runs.

The active file rotates at about 1 MiB across five numbered files (5 MiB total). Older files are removed first if the configured total limit is exceeded. The existing Logcat output and 1,500-entry in-memory `FolderRefreshTrace` behavior remain available under their original setting.

Persisted event sources currently include:

- `FolderRefreshTrace` request, worker, measured stage, read result, scan output, publication, adapter commit, directory metadata commit and timeout boundaries. Per-file `enumerated`, `type_passed` and `metadata_passed` events remain available only through the older targeted folder trace; they are not copied into persistent diagnostics.
- Existing `GalleryPerfTrace` grid, album, photo-loader and viewer stage/count events, without changing its release Logcat behavior.
- Full-screen Viewer open, page-bound and close events, with the current index, visible count, content revision, collection type and a session-keyed media identifier.
- Grid and full-screen delete request/result boundaries, including requested/completed counts and whether the operation was permanent or used the recycle bin.
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

The unit test suite and Release APK build are run in `.github/workflows/arm64-foss-release.yml` after the code commit. On-device visual behavior, real provider export compatibility, crash persistence under abrupt power loss, and performance impact still require a physical-device check. Native crashes, ANRs whose process is killed before managed code runs, and system force-stops cannot be guaranteed to produce a stack trace.

## Device acceptance steps

1. Open **Settings → Advanced → Performance → Diagnostics & Logs** and enable logging.
2. Reproduce a scan, refresh, photo-profile, Viewer swipe or deletion issue.
3. Return to the page and use **Copy logs** for a recent excerpt or **Export logs** to save the full TXT.
4. Attach the exported file to the issue report after reviewing it for context-specific sensitive text.
5. Use **Clear logs** to remove records and any previous crash summary. The toggle is preserved.

For system traces outside the app-private managed diagnostics, ADB/Android Studio may still be required.
