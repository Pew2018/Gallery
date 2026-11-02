# Stage 3 backup reliability verification

Scope: `fix/folder-viewer-swipe`, starting remote HEAD `d7c31b5829cc377144829f28c755afc9cfd9fe7a`. No wire-format, database-version, authentication-policy, theme-layout or viewer-navigation changes.

## Boundary fixes

* Independent photo-profile export validates the original `photo_profiles_v1` string through `decodeStrict` before producing bytes. Only the empty string denotes the runtime default empty collection. Partial records, duplicate UUIDs, malformed JSON and unsupported versions cancel export; runtime tolerant decoding and editing are unchanged. The original preference is never written.
* `BackupIo.readMediaFavorites` permits empty media-favorites TXT only. It retains the same bounded read, strict UTF-8 and stream ownership as `readText`; settings and JSON readers still reject blank input. Empty merge imports are repeatable no-ops. ZIP v1 still uses the same six files.
* A selected Document Provider destination may already have been created or truncated before validation. Validation failure writes no bytes, but may leave an empty destination. An I/O failure can leave partial bytes. This is not an atomic export and the app cannot promise rollback across arbitrary providers.

## Automated coverage

The full FOSS Release unit-test task includes JVM codec tests and Robolectric Android/Room/UI tests. These are not instrumentation or real-device tests.

| Concern | Existing/extended suites |
| --- | --- |
| Legacy settings compatibility and precedence | LegacySettingsCodecTest, LegacySettingsRestoreTest, GalleryRestoreServiceTest |
| Raw profile corruption and complete export | PhotoProfileBackupCodecTest, BackupSettingsInteractionTest, BackupIoTest |
| Empty favorites, BOM, invalid UTF-8, duplicates and merge preservation | BackupIoTest, FavoriteBackupValidationTest, FavoriteBackupRestoreTest |
| Six-file ZIP v1, CRC, SHA, directory corruption, limits and interrupted providers | GalleryBackupZipCodecTest |
| PIN/pattern/biometric callback, cancellation, stale proof, owner/mode binding, fresh plan | ProtectedGalleryRestoreTest, RestoreAuthenticationInteractionTest, RestoreBiometricCallbackTest |
| Actual new relation, cache flag and fresh virtual-favorites profile context | ProtectedGalleryRestoreTest, PhotoProfileMediaLoadingTest |
| Merge idempotence, replacement limits, unsafe paths and protected roots | GalleryRestoreServiceTest, ProtectedGalleryRestoreTest, RestoreSafBoundaryTest |
| Room rollback, preference compensation, durable journal and independent mutations | GalleryRestoreServiceTest, ProtectedGalleryRestoreTest |
| Theme, custom colors, toolbar, navigation, sorting and directional columns | OxygenSettingsBackupCodecTest, LegacySettingsRestoreTest, GalleryRestoreServiceTest |
| Confirmation controls, cancellation, classic layout and bounded preview | GalleryRestorePreviewDialogTest, BackupSettingsInteractionTest, ClassicDialogLayoutRegressionTest |

Compensation checks both at the SharedPreferences writer and the restore coordinator now stop if a touched key has an independent third value rather than either the saved before or target value. Tests inject this after a failed editor commit and after a successful preference update, assert the new value survives, Room rolls back, the journal remains and recovery refuses to overwrite it. These checks are conservative evidence, not a compare-and-set or cross-writer atomicity guarantee.

Backup error logs record exception types only, without provider exception messages or stack traces that may contain private paths.

The full suite uses a 2 GiB test-worker heap. The first local 512 MiB run passed 624 tests but exhausted heap in the native preview matrix; after this test-only allocation change all 625 tests passed. APK heap policy is unchanged.

Additional fault hooks `after_checkpoint` and `before_room_changes` extend the existing deterministic injection mechanism. They introduce no delay, additional transaction, scheduler or authorization cache. Simulated termination is an Error at a known boundary, not an actual killed Android process.

## Reproducing host measurements

```
./gradlew :app:testFossReleaseUnitTest --stacktrace
./gradlew :app:testFossReleaseUnitTest --tests '*BackupPerformanceTest' --rerun-tasks --stacktrace
```

`BackupPerformanceTest` creates real host temporary files, fresh in-memory Room databases, 100/10/3, 1000/50/10 and 10000/200/30 media/folder/profile datasets. Every scale has five cycles. Cycle zero is the first pass at that scale, not a process cold start. Cycles one through four use warm host caches and fresh databases. Each cycle restores a half-existing dataset, then repeats the same restore to verify idempotence. All measurements execute on a worker; there are no latency thresholds in correctness tests.

Raw CSV/JSON are written to `app/build/reports/backup-performance/` and included in the `backup-ui-test-reports` Actions artifact. Values use `System.nanoTime`. Scales are synthetic, not the user's actual collection sizes. Service measurements use a deterministic available-path inspector and no favorite password; host filesystem metadata and real protection snapshots are measured separately. This avoids presenting simulated access as device permission performance.

Phase interpretation:

* ZIP export includes the production post-write inspection. ZIP inspection includes central/local directory, CRC and SHA checks. ZIP decoding includes inspection. These overlap and must not be added together.
* The SHA-only sample hashes encoded favorites, not all six ZIP files. It is a micro-measurement, not an isolated internal manifest contribution.
* The device batch measurement uses production `DeviceBackupPathAccess` against host temporary files under Robolectric. It includes protection checks. The separate metadata and protection measurements are diagnostic overlapping operations.
* Collector, planner, preview and restore have independent outer durations. Planner excludes collection; preview includes collection. Restore includes required revalidation, journal, one Room transaction, preference commits and observed-result validation.
* The Room relation interval covers production `applyRelations` inside the existing transaction and excludes transaction commit. Preference commit is measured inside the real restore. Both are nested in restore total.
* Isolated SQLite transaction-commit latency, physical device disk latency, SAF provider latency, device protected-session performance, true process cold start, post-restore page synchronization, main-thread frame stalls and Activity-recreate counts are **not measured** by this harness.

## Device acceptance still required

Install only after checking signing compatibility and preserving user data. Test both restore modes with existing/new/duplicate favorites, an independent protected directory, an offline SD path, persisted SAF grants, favorite-only custom profiles and favorite folders. Cancel PIN/pattern/biometric and replace confirmation; verify no database or preference changes. Then authenticate once, review the regenerated preview and confirm. The new favorites must be visible after separately satisfying the viewer's normal protection rules; the restore grant must not unlock that viewer.

Between preview and confirmation, change a password, revoke a session, add a protected directory, revoke SAF access, replace a file and add/remove a favorite. Each stale plan must stop without writes. Repeat merge twice. Test empty replacement without removing media files, passwords, device bindings or inaccessible relations.

Test actual app process kill and device storage/provider failures around journal and preference commits. Reopen and verify conservative recovery. Independent later changes must stop automatic reconciliation. Room and SharedPreferences remain separate stores with bounded compensation and a durable checkpoint, **not a cross-store atomic transaction**.

On OxygenOS hardware, record theme/navigation/filter/sorting/columns after recovery and rotation, Activity recreation count, media scanner/query count, grid position, thumbnails, frame timing and post-restore refresh. Mocked callbacks, simulated URI permissions and host filesystem tests do not replace this acceptance.

## Recorded local results (2026-11-02)

Linux container, Temurin JDK 17.0.21, 8 reported processors, Gradle 9.5.1, Robolectric SDK28, 2 GiB test-worker heap, host temporary filesystem and in-memory Room. Both baseline and final full suites: **625 tests, 91 suites, zero failures/errors/skips**. Forty backup preview PNG files match the Stage 2 CI images byte for byte. No instrumented or real-device run was performed.

The baseline includes the Stage 3 reliability changes before the planner optimization; it is not a reset to an earlier branch commit. The final code reuses the already-created target Set for membership in removal counts, retaining the same sorted target list. No access check, proof, confirmation boundary, transaction, compensation or checkpoint was removed. The final benchmark also compares the former List formula and Set formula on identical MERGE and REPLACE targets, asserting exact count equivalence.

Raw records: [baseline CSV](stage3-performance-baseline.csv), [final CSV](stage3-performance-final.csv). Every common phase has five samples per scale. The four additional reference/Set micro-measurements occur only in the final run. All times below are milliseconds, **median [minimum, maximum]**, with nested phases overlapping.

| Phase | 100/10/3 final | 1000/50/10 final | 10000/200/30 final |
| --- | --- | --- | --- |
| collector_snapshot | 5.840 [3.947, 14.590] | 5.921 [5.380, 24.840] | 16.747 [16.339, 20.100] |
| device_batch_including_protection | 8.651 [6.176, 22.827] | 36.088 [33.628, 62.754] | 329.358 [321.730, 351.187] |
| filesystem_metadata_only | 0.360 [0.310, 2.390] | 3.032 [2.902, 3.596] | 25.626 [23.424, 42.341] |
| planner_fixed_access | 5.794 [3.133, 6.845] | 6.307 [4.040, 8.376] | 21.560 [17.654, 27.509] |
| preferences_commit_nested | 3.103 [2.697, 6.176] | 2.536 [2.338, 3.934] | 2.122 [1.816, 2.757] |
| protection_snapshot_only | 2.772 [1.849, 10.035] | 14.514 [13.678, 21.775] | 124.551 [106.158, 151.486] |
| removed_count_list_reference_MERGE | 0.026 [0.023, 0.398] | 0.665 [0.571, 4.558] | 61.911 [55.504, 64.876] |
| removed_count_list_reference_REPLACE | 0.021 [0.020, 0.163] | 0.631 [0.568, 1.542] | 73.238 [68.307, 76.015] |
| removed_count_set_including_build_MERGE | 0.015 [0.009, 0.087] | 0.061 [0.060, 0.064] | 0.739 [0.641, 0.850] |
| removed_count_set_including_build_REPLACE | 0.014 [0.009, 0.021] | 0.068 [0.064, 0.073] | 0.851 [0.716, 1.069] |
| repeat_preview_fixed_access | 13.330 [12.363, 19.406] | 18.368 [13.615, 34.870] | 94.564 [72.968, 140.190] |
| repeat_restore_fixed_access | 14.338 [10.734, 19.112] | 16.668 [14.167, 18.543] | 76.414 [68.408, 103.298] |
| room_relation_changes_nested_excludes_commit | 23.778 [9.181, 24.878] | 51.380 [33.453, 86.836] | 165.278 [117.505, 314.056] |
| service_preview_fixed_access | 9.577 [6.512, 17.034] | 10.330 [8.357, 15.006] | 46.423 [40.362, 55.735] |
| service_restore_fixed_access | 42.206 [26.274, 64.160] | 72.464 [53.492, 113.720] | 258.080 [190.920, 429.663] |
| sha256_favorites_only | 0.090 [0.062, 0.138] | 0.332 [0.211, 0.492] | 1.988 [1.830, 19.805] |
| zip_decode_including_inspect | 3.952 [2.036, 6.820] | 2.577 [2.219, 5.654] | 11.150 [7.831, 13.802] |
| zip_export_including_validation | 8.952 [6.164, 27.169] | 8.412 [5.702, 14.434] | 17.344 [14.720, 20.967] |
| zip_inspect_including_crc_sha | 1.077 [0.759, 2.061] | 0.749 [0.708, 1.238] | 3.204 [2.340, 3.650] |

### Before/after comparison

| Scale | Phase | Baseline | Final | Median change |
| --- | --- | --- | --- | --- |
| 100 | planner_fixed_access | 4.940 [4.076, 7.364] | 5.794 [3.133, 6.845] | +17.3% |
| 100 | service_preview_fixed_access | 10.377 [8.275, 14.241] | 9.577 [6.512, 17.034] | -7.7% |
| 100 | repeat_preview_fixed_access | 12.413 [10.345, 22.488] | 13.330 [12.363, 19.406] | +7.4% |
| 100 | service_restore_fixed_access | 44.173 [28.449, 56.061] | 42.206 [26.274, 64.160] | -4.5% |
| 1000 | planner_fixed_access | 11.024 [9.471, 14.681] | 6.307 [4.040, 8.376] | -42.8% |
| 1000 | service_preview_fixed_access | 18.109 [13.006, 31.974] | 10.330 [8.357, 15.006] | -43.0% |
| 1000 | repeat_preview_fixed_access | 28.639 [21.519, 31.320] | 18.368 [13.615, 34.870] | -35.9% |
| 1000 | service_restore_fixed_access | 104.845 [68.456, 167.530] | 72.464 [53.492, 113.720] | -30.9% |
| 10000 | planner_fixed_access | 134.967 [123.696, 172.745] | 21.560 [17.654, 27.509] | -84.0% |
| 10000 | service_preview_fixed_access | 160.548 [149.347, 178.015] | 46.423 [40.362, 55.735] | -71.1% |
| 10000 | repeat_preview_fixed_access | 349.364 [283.498, 433.537] | 94.564 [72.968, 140.190] | -72.9% |
| 10000 | service_restore_fixed_access | 251.720 [190.596, 317.655] | 258.080 [190.920, 429.663] | +2.5% |

### First pass and warmed passes

The first pass is cycle zero per scale in the same test JVM, **not a process cold start**. Warm medians use cycles one through four. Actual repeated restore has its own phase in each cycle.

| Scale | Phase | First pass | Warm median [min,max] |
| --- | --- | --- | --- |
| 100 | planner_fixed_access | 6.845 | 5.122 [3.133, 5.954] |
| 100 | service_preview_fixed_access | 17.034 | 9.488 [6.512, 11.256] |
| 100 | service_restore_fixed_access | 47.326 | 37.467 [26.274, 64.160] |
| 100 | repeat_restore_fixed_access | 19.112 | 13.321 [10.734, 14.723] |
| 1000 | planner_fixed_access | 7.104 | 5.658 [4.040, 8.376] |
| 1000 | service_preview_fixed_access | 15.006 | 9.458 [8.357, 12.582] |
| 1000 | service_restore_fixed_access | 113.720 | 63.564 [53.492, 75.306] |
| 1000 | repeat_restore_fixed_access | 16.668 | 15.617 [14.167, 18.543] |
| 10000 | planner_fixed_access | 24.888 | 21.369 [17.654, 27.509] |
| 10000 | service_preview_fixed_access | 55.735 | 44.564 [40.362, 48.495] |
| 10000 | service_restore_fixed_access | 429.663 | 224.670 [190.920, 268.417] |
| 10000 | repeat_restore_fixed_access | 102.708 | 73.485 [68.408, 103.298] |

The large planner and repeated-preview reductions are supported by both the outer measurements and the same-target List/Set comparison. There was no measured reason to change the path inspector, protection snapshot, database writer, ZIP format or page loading architecture. Large restore median increased from 251.720 to 258.080 ms (+2.5%); its ranges overlap (190.596–317.655 vs 190.920–429.663 ms). Small timings also vary. This is not evidence of a consistent total-restore improvement or a guarantee of no regression. Device measurements remain required.

### Final diff audit

No legacy TXT/JSON or ZIP v1 structure changed; no Room schema, dependency, theme/layout, picker scrollbar, media-loader or fullscreen-viewer changes. The test-only heap setting affects Gradle workers. All provider streams retain ownership/closure, validation happens before output bytes, and failed exports/restores cannot execute success callbacks. ZIP temporary-file cleanup remains unchanged. Authorization checks and one Room transaction remain in place, with additional conservative compensation checks. Logging is limited to error class names. Content notifications still occur only after verified commit or a verified recovery. Render equality supports layout stability; actual scanner counts, device Activity recreation, frame stalls and protected SAF revocation need device validation.
