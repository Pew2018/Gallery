# Progressive loading and batch delete stability

Date: 2026-11-04

## User reproduction

The user reports that deleting several already-visible photos or videos from a normal folder or a custom Photos profile while progressive loading is still active can crash Gallery. No device crash stack trace was supplied for this change, so the original uncaught exception is not attributed to a single confirmed runtime stack.

## Source revision inspected

- Branch: `fix/photo-viewer-five-item-boundary`
- Remote HEAD before edits: `9601cddf0fd9e0b3937496a81f5664c197606671`
- Historical task SHA `ec722ed2...` was not used as a checkout/reset target.
- Fossify Commons source was inspected at the 7.1.3 dependency version.

## Confirmed code defects

1. `MediaAdapter.askConfirmDelete()` used `selectedKeys.size` as the dialog count and dereferenced `selectedMedia.first()` without checking whether keys still resolved against the live list. Progressive list replacement can make the selection keys stale.
2. `MediaAdapter.deleteFiles()` captured `Medium` objects before SAF / Android 11+ permission UI, then removed by object equality and notified `removeSelectedItems()` with current positions. Commons 7.1.3 `removeSelectedItems()` only emits `notifyItemRemoved()` and finishes action mode; it does not mutate the adapter list. The positions were therefore not a safe representation of the actual deletion change.
3. The adapter removed rows before filesystem deletion completed. This could visually remove failed items and did not represent Commons batch-delete semantics. Commons 7.1.3 casual deletion can report success when at least one item succeeded, not necessarily all selected items.
4. `MediaActivity.deleteFilteredFiles()` used a repeatedly rebuilt path list for removal and finished the Activity whenever `mMedia` became empty. During progressive loading that list is only a partial snapshot. The Activity’s scan request/generation guards already reject stale publications when a new request starts, but deletion did not start that request.
5. `movePathsInRecycleBin()` updated media DB state without notifying the process-local Photos content revision at that operation boundary.

## Root-cause status

The code defects above are confirmed by source inspection. The adapter selection dereference is a direct potential exception when a stale non-empty selection resolves to no media. The invalid positional notification and optimistic mutation create RecyclerView consistency risks. No real-device Logcat/stack trace was available, so these defects are not asserted to be the exact cause of the user’s observed crash.

## Change summary

- `MediaAdapter.kt`
  - Guarded empty/stale selections and report the count of currently resolvable media.
  - Captured selected paths as stable operation identities before permission UI.
  - After permission callbacks, verifies the adapter is still attached and resolves only the captured paths against the current list.
  - Waits for the host delete result, intersects confirmed paths with the original operation scope, and removes rows through the existing DiffUtil single-flight update path. No direct positional remove notifications remain in this flow.
- `MediaOperationsListener.kt`
  - Added a callback with confirmed deleted paths so Activities can distinguish successful items from failures.
- `MediaActivity.kt`
  - Verifies each resulting source/recycle-bin path after Commons completes, updates DB rows only for confirmed permanent deletions, increments the Photos revision once per permanent batch, and refreshes the matching scope after success.
  - Removed the partial-list-empty `finish()` condition. The existing `deleteDirectoryIfEmpty()` still checks actual directory contents before cleaning an empty folder.
  - A fresh load request invalidates prior scan/cache publication callbacks while retaining progressive loading.
- `SearchActivity.kt`
  - Adopted the same per-path result handling and confirmed-path callback to keep the listener contract correct.
- `Activity.kt`
  - Invalidates Photos profile/viewer caches when a recycle-bin move updates media content, including a partially completed move before early error return.
- Tests
  - Expanded `MediaAdapterStabilityTest` for stale selected keys, an in-flight diff superseded by deletion, stable path removal, unselected-row preservation, and exact DiffUtil removal notifications.
  - Added an Android emulator test that deletes a batch of files from a real profile folder and confirms the surviving files remain while deleted paths do not return after content-revision invalidation and reload.

## Important ordering

1. The user selection becomes a set of paths.
2. Permission UI may run; the callback verifies the original adapter is still attached.
3. Paths are resolved again against the current adapter rows.
4. The filesystem delete/recycle operation runs.
5. Each selected path is checked independently. Only confirmed paths are reported as deleted.
6. The adapter applies a normal DiffUtil update by path identity.
7. The owning Activity starts a fresh load request, invalidating older progressive scan publications and preserving the progressive scan architecture.

## Recycle bin and permanent deletion

- Normal recycle-bin deletion requires the source to be absent and the corresponding recycle-bin copy to exist before it is reported removed from the active folder/profile.
- Skipping the recycle bin, a disabled recycle bin, or deleting an item already in the recycle bin is treated as permanent deletion.
- Permanent DB deletion is performed only for paths confirmed absent. A mixed batch can retain failed paths while removing successful ones.
- `PhotoContentRevision` changes once per confirmed permanent batch; recycle-bin moves invalidate it in the move operation.
- Android SAF/Manage Media dialogs remain in the existing Commons permission path.

## Test coverage and limitations

- Host unit tests cover the adapter’s stale-selection guard and DiffUtil/removal interleaving using a controllable diff executor.
- Emulator coverage uses real Android filesystem files and the profile loader/cache revision path. It does not automate the system SAF dialog or assert a vendor-specific crash stack.
- API 34 is the configured emulator target. API 30 and API 36 were not added as separate emulator jobs.
- The emulator test verifies profile reload consistency, not a full end-to-end tap/long-press/delete flow during a deliberately paused scan. That UI timing still needs device validation and Logcat if a crash recurs.

## Validation record

To be updated after the post-change GitHub Actions runs: unit test count/results, emulator result, ARM64 APK result/artifact, and final commit SHA.

## Remaining risks

- Android storage authorization UI and OEM-specific behavior cannot be fully represented by host unit tests.
- Per-path verification depends on Commons `getDoesFilePathExist()` resolving the active storage backend correctly; SAF and OTG should be checked on devices where those sources are used.
- No claim of a 100% confirmed original crash root cause is made without a user-device exception trace.
