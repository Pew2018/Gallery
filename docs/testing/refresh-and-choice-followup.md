# Refresh and choice follow-up (2026-11-03)

Baseline: feature/oxygenos-ui-animation-consistency, 61b10e0f5eec81be77a1fdb54cd962b03c489f6e. No new branch, PR or main merge.

## Refresh

Manual MainActivity pulls now retire a busy scan and begin a new generation, instead of dropping the request. Cache/scan worker finally releases scanning state independently of deferred Adapter commits. Existing selection/scroll deferral still controls visual publication. Scope epochs reject old callbacks.

MediaFetcher owns a CancellationSignal, canceled by shouldStop=true, used for its primary provider queries. A two-worker media executor replaces MediaActivity's use of AsyncTask's process-global serial executor. Photo/profile authorization and stale-result guards remain.

Ordinary scoped folders now merge nonempty indexed rows with permitted direct-file enumeration. Indexed-only accessible media remain; direct unindexed children are added, duplicates merge, nested children are not added to a single-folder grid. Index metadata is reused rather than queried again per file. A manual album refresh additionally discovers child album folders under known albums, included roots, DCIM/Pictures and standard Downloads/Movies, applying visibility and protected-ancestor checks before descending and rejecting symlink escapes. This is background work, cancellable; it does not grant filesystem/SAF access or bypass permission boundaries. No blind full-storage reindex is performed: readable new files can be published directly without waiting for MediaScanner.

Media results publish before optional FolderViewerSnapshotStore preparation. Its provider freshness check and metadata copy run outside the monitor used by photo taps; generation and current-request checks guard the brief publication section and prevent invalidated preparation from returning after clear/new prepare.

## UI

Favorite overflow items preserve checkbox drawable Insets and menu sizing/position; remove inherited checkbox background, leave the menu row selector as the only feedback owner.

Toolbar option rows now use a separate label/check-state body with one rectangular ripple and a trailing checkmark (blank reserved slot when unchecked). The original CompoundButton still drives listeners/selection rules, without a button drawable or background. Adjacent 48dp reorder handles are separate targets. Fixed HOME search/browse entries have plain labels, no checkmark and no press effect. HOME/FOLDER state, maximum five, ordering/reset/cancel/save remain transactional.

Favorite/Photo password helper windows are explicitly tagged before first sizing and share the 380dp security width cap with the next method/input windows. Other generic COMPACT lists remain unchanged. Settings dismisses the management window before opening its next step. Content still wraps and security content scrolls; system biometric prompts and credential logic unchanged.

## Regression and device acceptance

RefreshRepairTest exercises a stale nonempty MediaStore plus real newly written file, retention of index-only media, no nested/hidden leakage, de-duplication and actual MediaAdapter insertion. It also checks new album discovery, protected-path callback pruning, symlink exclusion, cancellation, a blocked worker with a fresh request, and snapshot lock release/stale rejection.
UiFollowupTest checks both actual AppCompat menu checks, original geometry and absent child ripple; toolbar body/handle/fixed item behavior; both security management helpers versus next-step window width across five screen sizes, plus unchanged generic list width. Existing toolbar save/cancel/scope/order, 90-window security layout matrix, directory scroll/selection and media access tests remain enabled.

Current editing host has no Android SDK/device/emulator; local Gradle distribution cannot be downloaded. CI executes the full FOSS Release unit suite and arm64 Release build. Device visual acceptance remains pending. Test the final APK with all-files access on/off, external-app copied files with stale index, new child folders, rapid pulls while scrolling/selecting, provider failure/cancellation, scoped inaccessible files, protected/hidden/excluded paths, theme/font/landscape variants and immediate photo opening. Verify new paths in the grid, no stale authorization, no lost scroll/profile state, and no duplicated checkbox ripple. Do not equate Robolectric success with physical-device verification.
