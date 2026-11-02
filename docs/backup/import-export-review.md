# Import/export implementation and APK regression checklist

Base: `fix/folder-viewer-swipe`, `1908844f1223e5aed44a7b61393efa11bc983901`.

## Boundaries and reusable interfaces

- `backup/BackupIo`: bounded 8 MiB UTF-8 bytes, BOM, malformed-encoding rejection, null provider streams, stream ownership including snapshot/encoding failures.
- `backup/BackupJson`: strict JSON, duplicate-member rejection, depth/record/size bounds.
- `backup/BackupResult`: mutually exclusive record outcomes; `skipped` is the sum of unavailable, invalid, initially protected, newly protected, and duplicate input lines. Conflicts are a subset of imported photo profiles, not an additional record outcome.
- `backup/BackupOperationRunner`: serialize backup jobs using existing Commons background dispatch; no coroutine scope or executor lifecycle.
- `backup/LegacySettingsSnapshot`: unchanged stock key/value export allowlist, order, comma encoding. `LegacySettingsCodec`: parse/validate a plan without mutation. Unknown keys ignored; duplicate keys or invalid known values reject the entire plan; no supported values never succeeds.
- `backup/LegacySettingsRestore`: merge folder sets and album covers before one scoped preference commit. Handle pinning the recycle bin, language/icon companion flags, and orientation/scroll-specific column keys. Package-manager icon updates and view refresh happen on the UI thread after commit, and are not part of a preference transaction.
- `backup/BackupPreferences`: a single editor for affected keys only; on failed/thrown commit, attempt scoped rollback and surface failure. Android SharedPreferences does not provide a database transaction: a persistent disk failure or process termination during rollback cannot be guaranteed recoverable. No `clear()` or whole-store replacement is used in application code.
- `FavoriteBackupCodec`: original path-per-line media TXT, unchanged folder JSON format/version 1, paths/case/timestamps preserved. Duplicate folder records keep first timestamp. Offline/SD/SAF folder relationships survive import.
- `backup/FavoriteBackupRestore`: one Room transaction for relation and cached media flags, IGNORE semantics, two protection checks, revision only after a committed media change/cache repair. Folder imports touch only the favorite-folder table. MainActivity already rereads folder relations on resume; no full media scan is added.
- `backup/PhotoProfileBackupCodec`: strict JSON v1 structure-only snapshot. Runtime `PhotoProfileCodec.decode` remains tolerant. Canonical UUID syntax, bounded nonblank names/sources, nonroot absolute paths without dot/control segments, valid selection, and proper source types are checked. Names and trailing slashes are normalized consistently with the existing store. Unknown extension members cannot populate device credentials or authorizations.
- `PhotoProfileStore.importState`: preflight merge then one import-specific write. Keep local order; append in input order. New UUID adds, same ID/content skips, same ID/different content creates a new UUID unless an equivalent copy exists. Existing valid selection stays. A null/missing selection falls back to the first profile; an explicit dangling ID rejects the backup. Explicit empty `profiles: []` is valid and never clears local profiles.
- `PhotoProfileRepository`: existing editing writer remains unchanged; only backup import uses checked synchronous persistence on its worker. Management resumes by reading the store. Existing MediaActivity preference listener compares its current display key before reloading; importing an unused profile does not scan/reload the active profile.
- `SettingsActivity`: eight existing-style backup rows, original first-six order. API 29+ document MIME flows; pre-Q classic picker/permission flow. Commons settings dialog/naming/legacy mapping is retained, while its result writer is intercepted for shared error/stream handling. Read/open/parse/write run on workers. Cancel and missing URI are no-ops. UI callbacks check finishing/destroyed state.

No schema or Room migration, database packing, ZIP, media-file backup, source remapping, password/hash/biometric/session/SAF-grant/binding restoration is introduced. `photo_source_bindings` is an authorization fingerprint map consumed by `PhotoProfileAccessSessionManager`, not portable UI state.

## Automated tests

New tests under `helpers/backup/`: `BackupIoTest` (8), `LegacySettingsCodecTest` (12), `LegacySettingsRestoreTest` (7), `PhotoProfileBackupCodecTest` (18), `FavoriteBackupRestoreTest` (6), `FavoriteBackupValidationTest` (4), `BackupSettingsInteractionTest` (3), `BackupPreferencesTest` (2). Run together with existing `FavoriteBackupCodecTest`, `FavoriteBackupDatabaseTest`, `PhotoProfileTest` and the complete release suite.

Commands:

```sh
./gradlew :app:testFossReleaseUnitTest --stacktrace
./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --stacktrace
```

Local execution requires network-accessible Gradle/Android dependencies. The provided environment could not download Gradle; the branch's release workflow is the authoritative compiled test/build verification. Do not treat this checklist as evidence that a physical-device check has run.

## APK checks requiring a device/real provider (not automatically completed)

1. On Android 9 and Android 10+, open Settings → Advanced → Backup. Verify six original entries retain order, dimensions, colors and dialog styles; two profile entries follow. Cancel every picker and confirm no state changes/toasts claiming success.
2. Export all four formats, including Chinese names/case/`=` inside paths. Inspect media TXT (no header), settings TXT (first `=` separates value), folder JSON format/version/timestamps, profile JSON UUID/name/sources/selection. Verify no password hashes or `photo_source_bindings` appear.
3. Export a stock Fossify settings/media TXT on a test installation. Import into this build. Export this build's settings/media TXT and import into stock Fossify. Use separate test installs/devices as signatures/packages may conflict. Do not uninstall a valuable installation without separately preserving its data.
4. Import invalid integer/float/boolean, duplicate-key TXT, malformed JSON, wrong versions, bad source UUID/path, invalid UTF-8, empty and >8 MiB files. Compare state before/after; no partial changes or success feedback. Valid unknown stock setting keys are ignored.
5. Use a provider with denied/revoked permission, offline backend, null/failed streams, full storage, and failed output creation. Confirm failure feedback and responsiveness. Rotate/leave/destroy Settings during slow I/O; no stale view access or crash.
6. Import media favorites twice: new, existing, duplicated, unavailable, initially protected and newly protected paths. Sum mutually exclusive categories; only authorized accessible paths are added. Reopen favorites and a profile referencing virtual favorites without restarting; protect/unprotect changes must still require existing authorization.
7. Import favorite folders containing an offline SD folder and SAF URI, then reconnect storage. Retain original dates/case. Existing dates/media favorites must not change. Return to folder favorites and verify immediate new relations without restarting.
8. Export/import zero, one and several profiles. Verify UUIDs, mixed sources, current selection and missing folders. Reimport; same-ID conflicts add one copy only, even after another repeat. Existing current profile remains active; importing unused profiles causes no library scan. Reopen management; added profiles are present.
9. With protected folders/favorites/photos, import a structurally valid profile pointing at them. Confirm it remains protected/missing according to the existing resolver; no binding, fingerprint, password or SAF permission is acquired.
10. Smoke-test profile editing/switching/deletion, three bottom tabs, normal folder/fullscreen swiping, picker scrollbar, quick actions, sorting, hidden/excluded-folder rules. These implementations are not changed by this work.

Signing: inspect the final workflow's `Signing mode` output. A temporary CI key cannot replace an installation signed with another key.
