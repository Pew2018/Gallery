# 21 — Phase 0 results and handoff

Date: 2026-11-05. Branch: `refactor/media-pipeline-stability`.
Start HEAD: `29f3528df55c9997cfe5c95a217ddc97eb85e331`.
Code commit validated by CI: `eeddba4b68779a0809c24ede5daaff613aed9426`.
Successful Actions run: [41951185632](https://github.com/Pew2018/Gallery/actions/runs/41951185632).
APK artifact: `gallery-foss-arm64-v8a-release` (ID `13380139965`), available from the run's **Artifacts** section through 2026-11-19.

## Scope delivered in source

- Fixed Release `GalleryPerfTrace.end()` filtering so enabled persistent diagnostics can record stages outside the existing Logcat allowlist. Diagnostics-off calls do not build persistent field maps. Negative elapsed durations are clamped.
- Added opt-in request-scoped `MediaPipelineDiagnostics`; a disabled request has no collector allocation. Enabled summaries use `ml-*` request UUIDs and the logger's per-session salted scope digest. Existing business request IDs remain unchanged and are included for joins.
- Added HOME and CATEGORIZED request lifecycle timing; instrumented GetMedia task queue/worker/result delivery for folder, All media, Favorites, Recycle Bin and Photo profile paths.
- Added MediaStore query/row totals, file enumeration and metadata totals, Photo profile cache and Room read/write input totals, sort totals, stage publication totals, media Adapter submit/diff/commit events and Viewer session open/page-bound/boundary summaries.
- Kept summaries within the existing 16-field event limit by splitting metric groups. Missing values are `-1`; a measured zero remains zero.
- Added regression tests for counters, request isolation, concurrency, clock rollback, session-salted identifiers and Release stage policy.

The unit test's captured summary fixture (synthetic test data, not a device run) is equivalent to:

```text
module=MediaPipeline event=media_scan_summary request=ml-summary scopeKey=scope-hash sourceType=FOLDER loadReason=REFRESH permissionMode=SCOPED_STORAGE businessRequest=19 queueWaitMs=0 workerMs=10 publicationWaitMs=3 totalRequestMs=20 candidateCount=8 acceptedCount=7 terminalState=PARTIAL
```

The durations, counts, scope token and request token above come from fixed test inputs. They are not measured Gallery performance.

## Files changed

Code: `GalleryPerfTrace.kt`, `GalleryDiagnosticLogger.kt`, new `MediaPipelineDiagnostics.kt`, `GetMediaAsynctask.kt`, `MediaFetcher.kt`, `PhotoProfileMediaLoader.kt`, `MainActivity.kt`, `MediaActivity.kt`, `ViewPagerActivity.kt`, `MediaAdapter.kt`, `.github/workflows/arm64-foss-release.yml`. Tests: new `MediaPipelineDiagnosticsTest.kt`. Documentation: new 19–21 plus README, roadmap 18, validation 04, progress log 06 and `docs/diagnostics/app-diagnostics.md`.

No business scan results, sorting algorithms, database schema, cache validity rules, executor sizing, permission policy, Viewer ordering or UI style were intentionally changed.

## Automated verification

- Local command attempted: `./gradlew :app:testFossReleaseUnitTest --no-daemon`.
- Result: **not started**. Gradle 9.5.1 wrapper download failed with `java.net.SocketException: Network is unreachable`.
- `adb devices -l`: **not available**, `adb: command not found`.
- `git diff --check`: passed after final source/documentation edits.
- GitHub Actions run `41950122386` exposed two compile errors in the new permission-mode and constructor visibility code; both were fixed. Run `41950527727` exposed an incorrect test assumption about session salts; that test was corrected.
- Successful Actions run [41951185632](https://github.com/Pew2018/Gallery/actions/runs/41951185632), commit `eeddba4b68779a0809c24ede5daaff613aed9426`: **856 tests, 0 failures, 0 errors, 0 skipped**; FOSS ARM64 Release assembly succeeded.
- APK validation succeeded: package `org.fossify.gallery` version `1.14.1` (`versionCode 32`), v2 signature valid, ARM64-only native libraries. APK SHA-256: `730d8ff0be3dc0586383646fe988b9eab7baf1b857ce752683c427307ffad0c3`.
- Artifact: `gallery-foss-arm64-v8a-release`, ID `13380139965`, downloadable from the successful run's Artifacts section; GitHub expiry is 2026-11-19. Signing mode was `temporary-CI-not-upgrade-compatible`; it may not install over an APK signed with a different certificate.
- New test count: 8 test methods in `MediaPipelineDiagnosticsTest`; full FOSS Release suite count: 856.

## Runtime evidence

No physical Android device, emulator, media dataset or Perfetto session was available. Device matrix status is **`PENDING_DEVICE`**. No runtime duration, p50/p95, frame-drop, memory or overhead number is reported here.

## Coverage limits

- Room timing/rows are presently covered only for Photo profile cache reads and its cache insert input; other DAO reads/writes, metadata transactions, cleanup time and actual affected rows remain unmeasured.
- MediaAdapter commit events correlate with the business request ID; DirectoryAdapter commit and item-level Diff operation counts are not fully aggregated into the common request context.
- Thumbnail bind/reload/cancel counts, image decode/draw first frame, anchor movement, JankStats/Frame Timeline and precise allocations remain unmeasured.
- SAF metrics observe filesystem enumeration access method only. Mixed source permission detail and `loadReason` beyond INITIAL/REFRESH can be UNKNOWN.
- HOME and categorized request summaries may report candidate totals as `-1` where the current source path does not expose a truthful aggregate.

## Stage 1 prerequisites

First collect B01–B20 results on real devices where available, with 10 comparable runs per condition and the data described in [20](20-phase0-metrics-and-test-plan.md). Use the evidence to rank queue wait, provider/file I/O, repeated sorts, DB work, adapter commits and Viewer waits. Keep any lifecycle defect report separate from performance inference. Begin stage 1 only with a reproducible request trace or an independently confirmed correctness issue; do not infer a dominant bottleneck from source complexity alone.
