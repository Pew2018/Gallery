# Complete ZIP backup v1: implementation, coverage and verification

Base branch: `fix/folder-viewer-swipe`. Base commit: `e5cc5700abf771a06f238b3633e15ab809ed869e`.

## Format and coverage

Exactly six UTF-8 files, in this export order:

| File | Coverage | Rules |
|---|---|---|
| manifest.json | Format/version, timezone-qualified creation date, application ID, five real byte sizes and SHA-256 hashes | Strict names/version/date/types; no duplicate declarations |
| settings.txt | Existing legacy export allowlist and value encoding | Stock-compatible key=value; Oxygen extensions never mixed in |
| favorites.txt | Media favorite paths, no database IDs | Stock-compatible one path per line; empty is valid ONLY inside ZIP |
| favorite_folders.json | Folder/SAF relationships and original timestamps | Existing JSON v1, case and offline paths preserved |
| photo_profiles.json | All UUIDs, names, FOLDER/FAVORITES sources and selected profile | Existing JSON v1; strict import; no authorizations |
| oxygen_settings.json | Explicit portable preference allowlist | JSON v1 `{version:1,values:{...}}`; no raw prefs dump |

Extension coverage:

- Theme activation, preset/custom accent, appearance, effective system-theme flag and historical toolbar compatibility fields. Historical toolbar fields are retained as data, not re-enabled as obsolete visual overrides.
- Existing `oxygen_legacy_*` color/system snapshots and Commons custom text/background/primary/accent/icon colors, when present. Raw activation setters are deliberately not used during restore, so they cannot replace imported legacy-color snapshots. Effective active-theme Commons colors are prepared before the preference commit using the existing color resolver; the later engine apply is idempotent.
- Both normalized toolbar action scopes and all three normalized bottom navigation IDs, order preserved. Unknown/duplicate actions are rejected on import. Existing legacy local toolbar/navigation values are captured through current runtime normalization.
- Favorite hidden/excluded display preferences, main/favorites browse modes, photo media filter. These are preferences only; they never confer authentication or hidden/excluded access authorization. Protected hidden/excluded visibility cannot be enabled without local authentication; existing excluded roots cannot be removed while exclusion protection is enabled. Such retained settings are counted in the preview.
- All eight direction/scroll-mode column keys, including effective resource defaults. Physical extension column keys take precedence over the legacy TXT current-orientation getter projection.
- Actual Commons `sort_folder_`, Gallery `group_folder_` and `view_type_folder_` keys. Existing code lowercases suffixes on save/read; backups preserve stored spelling rather than inventing a new mapping. Accepted suffixes are real absolute paths or known virtual scopes. `custom_folders_order` keeps the existing `|||` encoding and case.
- Default start folder, slideshow animation, last editor draw color/brush size, video mute, bounded playback speed/progress. Remaining Gallery Config members were checked: ordinary settings are already in legacy TXT; excluded fields are credentials, temporary paths/access/UI guidance, scan/history/migration state or device-specific storage identifiers.

Excluded: originals/photos/videos/thumbnails, raw Room databases, database IDs, password hashes, fingerprint configuration, lock/session tokens, `photo_source_bindings`, SAF grants/tree URIs, storage root identifiers, transient hidden/excluded authorization, widget IDs, run counts/migration/tooltips and per-video playback-position history. Global cross-app Commons theme/other-app settings and OS-level per-app locale selection are not migrated. No automatic path remapping is implemented.

## Implementation files and rationale

| File | Change / reason / old behavior impact |
|---|---|
| GalleryBackupSnapshot.kt | Logical model, relation-only collector, before/after preferences/revision/relation checks with bounded retry; local concurrency evidence is never exported. This is not an absolutely atomic cross-store snapshot. |
| GalleryBackupManifest.kt | Versioned manifest with strict application identity, timestamp, size and hash validation. |
| GalleryBackupZipCodec.kt | Fixed-name ZIP encode/decode; private temporary files only, automatic cleanup, no arbitrary extraction. Both central and local headers/CRCs/sizes/methods checked; malformed, duplicate, missing, unknown, truncated or trailing input rejected. |
| OxygenSettingsSnapshot.kt | Explicit portable values; absence retains local values in merge or restores scoped defaults in replace. |
| OxygenSettingsBackupCodec.kt | Audited key/type/range/ID/scope validation and effective captures described above; credentials filtered before encoding. |
| BackupPathAccess.kt | No-unlock metadata classifier: available, missing, offline volume, SAF permission, media permission, protected or unconfirmed. Unknown access is not guessed as missing. Protected virtual media favorites are not modified. |
| GalleryRestorePlan.kt | Typed modes/targets/counts/statuses and stale/pending/compensation failure outcomes. |
| GalleryRestorePlanner.kt | Read-only plans for both modes, safe media retention/deletions, offline structural restoration, profile conflict handling and scoped preference preparation. |
| GalleryRestoreService.kt | Revalidates local state and access after preview, serializes requests, one Room transaction for both relation tables and cached media flags, one scoped preference commit, checkpoint/compensation and post-commit revisions. No Activity import entry points called. |
| GalleryRestoreJournal.kt | AtomicFile checkpoint, fsynced before mutations. Only affected portable preferences before/after plus relation fingerprints are stored, never device credentials or a database dump. |
| GalleryRestorePreviewDialog.kt | Existing classic scroll/dialog/choice styling, true data counts and access categories, merge default, second replace confirmation. Cancel is read-only. |
| SettingsActivity.kt | Appends two rows, API 29+ ZIP create/open document flows, pre-Q classic permission/picker flows, worker processing and lifecycle-safe callbacks. Refreshes existing setting/theme/navigation APIs after success. Old eight routes preserved; pending checkpoints block backup writes until reconciled. |
| App.kt | Small worker-only startup hook to reconcile interrupted restore; normal startup does not scan media or wait for ZIP/database work. Localized result/error notification. |
| activity_settings.xml | Two identical classic rows after the original eight; visibility registry and section rows updated together. |
| values/full_backup.xml; values-zh-rCN/full_backup.xml | Complete localized entry, preview, mode, path, progress, failure, recovery and completion strings. |
| LegacySettingsRestore.kt | Exposes a no-write `prepare(..., merge)` phase; original independent apply retains merge semantics. |
| LegacySettingsCodec.kt | Exposes its existing key allowlist for scoped defaults/checkpoint validation. No wire-format change. |
| PhotoProfileBackupCodec.kt | Also skips equivalent content with a new UUID, as required for ZIP idempotence. Existing original-ID conflicts continue to create at most one equivalent copy. |
| BackupOperationRunner.kt | Reentrant synchronous serialization boundary for reusable services, using existing Commons worker dispatch. |
| BackupPreferences.kt | Scoped null values remove only explicitly selected allowed keys; no clear/all-store replacement. |
| BackupJson.kt | Optional bounded private-journal parsing budget; default 8 MiB input behavior unchanged. |
| tests/helpers/backup | ZIP, extension, coordinator, crash/rollback, concurrent/stale plans and classic preview regression tests; existing row/merge assertions updated for the new behavior. |

No Room schema/migration, media-loader/viewer/picker/scrollbar, navigation implementation, toolbar catalogue or shared Classic styling changes.

## Safety and limits

- Compressed input <=48 MiB; manifest <=64 KiB; each payload <=8 MiB. Total expansion <=40 MiB +64 KiB. The additional 64 KiB deliberately allows all five payloads to reach their existing 8 MiB budgets while still accommodating the manifest. Both actual streamed byte counts and manifest bytes/hashes are validated. Journal <=64 MiB and fails before mutation if its budget/storage is insufficient.
- Payload staging uses one encoded file at a time in cache, then streams ZIP output. Imports spool only to a random private ZIP file, validate completely, then decode fixed payloads. Raw media is never read/decoded. Temporary files are deleted on normal success or failure; OS cache cleanup may be needed after hard process termination during export/decoding.
- MERGE keeps local media, folder dates, existing selection and unspecified extension values. Equivalent profiles skip even if their UUID differs; original-ID conflicts append one new UUID copy. REPLACE restores supported scopes only, counts removals and retains existing unsafe/unavailable/protected media favorites. Invalid media paths reject the ZIP, rather than being interpreted as an empty target. Folder/photo source structures remain even when offline.
- State tokens include local preference/security state and relation fingerprints in memory only. Confirmation reruns checks; changed data/access rejects the plan and asks for a new preview. Restore jobs serialize. Normal app writers are not under an absolute global lock, so a small external state race is inherently possible; the SQLite writer-lock/preference checks narrow it without claiming cross-store atomicity.
- Preferences commit occurs while the Room transaction is open. A caught transaction failure rolls Room back and compensates scoped preferences. If compensation or checkpoint cleanup fails, report failure and retain checkpoint; never claim complete success. A later recovery compares current relations and scoped values with before/after evidence. Old relations → restore old preferences; confirmed target relations → finish target preferences. Ambiguous preference-only interruptions revert. Independent/unrecognized changes block automatic reconciliation without overwriting data.
- Process termination between commits is recoverable in these recognized cases, not transactional. Durable checkpoint corruption, persistent disk failure, independent edits before recovery, or a compromised app-private directory can require manual review. Do not erase app data or install a differently signed build to fix a checkpoint error.
- Media revisions/caches are invalidated only after media relation/cache changes; folder-only restoration uses existing resume reads. Profile preferences trigger the existing current-display-key listener. Restoring unused profiles does not scan/reload the active profile. Appearance uses existing theme refresh logic, which can perform one ordinary recreation when required; there is no all-Activity recreation loop.
- Paths are literal; offline SD roots are not assumed equivalent to primary storage. SAF URI strings never transfer grants. Device passwords/fingerprints remain local, and protected source structures do not unlock access.

## Automated verification and manual APK checks

Run the complete FOSS Release suite and arm64 assembly via `.github/workflows/arm64-foss-release.yml`. Local Gradle execution was attempted but dependency download was blocked by the environment; CI logs are authoritative.

New classes: GalleryBackupZipCodecTest, OxygenSettingsBackupCodecTest, GalleryRestoreServiceTest, GalleryRestorePreviewDialogTest. Existing independent backup, database and photo tests remain in the full suite. Tests cover real ZIP bytes, hashes, UTF-8, empty collections, corrupt/oversize input, strict extensions, both modes, protected/offline access, profile equivalence, setting/column/theme precedence, no-write preview/cancel, injected disk/SQLite failures, compensation, simulated death on either side of DB commit, ambiguous recovery, stale access/data and concurrent restores.

Physical device/provider checks still required:

1. Android 9 and Android 10+: all ten rows/permissions/chooser MIME behavior; cancel each. Check content wrapping and second replacement confirmation under dark/light themes and enlarged fonts.
2. Export a populated backup; inspect six names and manifest lengths/hashes. Save via local/cloud/SD providers, test output write failure and slow/revoked providers. Existing eight operations and stock TXT mutual import remain usable.
3. Preview a backup with primary storage, missing files, disconnected SD, protected sources, and ungranted SAF. Compare counts and removal impact. Cancel both dialogs and verify unchanged state.
4. Run MERGE twice, including new-UUID equivalent profiles and same-UUID conflicts. Selection stays; no accumulating copies. REPLACE only after explicit second confirmation; media files remain on disk.
5. Restore theme/preset/custom color, navigation and toolbar order, custom folder sort/view/group and all direction counts. Rotate/return to existing pages; no unwanted full-library scan, scroll jump or repeated recreation.
6. Restore empty favorites/profiles in both modes. Test full-backup restore into a different device/storage layout; offline relationships remain and protected sources still require the original local authentication policy.
7. During restore, exercise permission revocation and process termination at real timing boundaries. Restart for checkpoint reconciliation. Fault-injection unit tests do not replace physical low-storage/power-loss testing.
8. Confirm source commit, successful full test totals, arm64 package/signature/ABI validation and nonexpired downloadable artifact. Temporary CI keys cannot update differently signed installed builds.
