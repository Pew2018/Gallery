# Refresh reliability final acceptance — 2026-11-03

Branch: `feature/oxygenos-ui-animation-consistency`
Baseline: `6259ca1afcd58a636a67cc11d0edbfb0f5953431`

## Implemented changes

1. MainActivity treats Room cache as an optional preview and uses one guarded formal-scan launch on either cache success or cache failure. Rejected submissions terminate the current request. Existing displayed rows remain available.
2. GalleryScanResult separates COMPLETE / PARTIAL / FAILED / TIMED_OUT / CANCELLED. MainActivity aggregates source, persistence and discovery uncertainty. GetMediaAsynctask passes whole-request completeness (including all-files) through MediaActivity to viewer handoffs. Only complete scans permit pruning and reusable complete viewer catalogs.
3. Metadata rebind reads stop their database watchdog before RecyclerView submission. Pending paths are acknowledged by the actual adapter commit. Superseding updates, explicit discard, detach, navigation and epoch changes preserve unapplied paths. Stable IDs, snapshots, DiffUtil and scroll anchors remain in use.
4. MetadataRetryQueue uses a finite ready snapshot, batches of 24, rotation and a monotonic 30-second cooldown. Each external wake visits each ready path at most once; there is no automatic retry loop. Protected/excluded/unreadable paths are skipped and retained. The dedicated metadata executor is unchanged.
5. The legacy first-run/every-30-runs recursive discovery now reuses bounded, cancellable discovery before final scan classification, and its discovered media folders join the same formal scan. FolderDiskDiscovery returns explicit unreadable/truncated results, throws cancellation, checks lexical and canonical policies, and bounds visited/frontier folders at 10,000 per discovery. MediaFetcher carries discovery uncertainty. Cache previews no longer trigger directory deletion. Formal directory cleanup is allowed only after complete enumeration and source reads.
6. Ordinary-media publication renders loading/terminal state through one request-gated method. Previews preserve the deadline and progress. Partial results have retry feedback and never claim a complete viewer collection, including after search. Existing progress dimensions/timing/theme and other UI resources are unchanged.
7. Added actual Activity, asynchronous scanner, Room retry-worker and adapter regressions, plus deterministic discovery and terminal/fairness checks. Existing tests are retained; cancellation tests now expect explicit cancellation instead of an empty successful Set.

## Validation commands

- `git diff --check` — passed before submission.
- `./gradlew :app:testFossReleaseUnitTest --stacktrace` — attempted locally; Gradle distribution download could not connect (`Network is unreachable`). This is not a passing test run.
- Existing GitHub Actions workflow executes the full FOSS Release suite before assembly, checks JUnit XML, signs the APK, verifies package/signature/ABI, logs APK SHA-256 and uploads `gallery-foss-arm64-v8a-release`.
- Final run URL, SHA, JUnit totals, APK checksum and artifact result belong in the delivery report after the workflow finishes. This source-side record does not predeclare CI success.

## Required device checks (not claimed as automated visual verification)

1. With existing `.nomedia` and all-files permission, copy an image, refresh inside the album, return home, then restart. Compare item, count, cover and sort.
2. During multiple album-count changes, scroll and select rows, navigate through Albums/Favorites/Photos, then return. Confirm deferred updates appear without jumping or full-grid animations.
3. Refresh ordinary/all-files/custom-profile/favorite/recycle-bin routes; enter full screen before and after partial failure; swipe both directions.
4. Disconnect removable storage or revoke authorization during scan. Confirm old authorized content is preserved where permitted, protected content is revoked, and no zero-count/deletion is committed for unreadable sources.
5. Exercise an excluded `.nomedia` tree, a protected descendant and a canonical symlink alias. Confirm policy boundaries remain enforced.
6. Let a scan exceed 45 seconds, then let its worker return. Confirm indicators stop, manual retry works, and the late result cannot overwrite the new request.
7. On a tree exceeding the discovery budget, confirm partial/retry feedback rather than a claim of complete discovery. A manual retry restarts bounded discovery; this change does not add persistent traversal continuation.

## Limits

Host/Robolectric tests cannot certify device permissions, vendor storage providers, optical smoothness or filesystem races between the last access check and an I/O operation. Pending retry paths and cooldowns are process-local. Very large discovery trees deliberately report partial; no persistent traversal cursor or automatic unlimited retry was introduced.
