# Phase 2C device audit: folder sorting and in-scan search

## Scope and evidence

The target branch was fetched before work. At audit start, `refactor/media-pipeline-stability` was at `6a7a49e33fc8f5fdc1e20a0b7d0877158ff230cb`.

The Pixel 8 Pro logs supplied for this pass show:

- **Ordinary folder log A:** the folder scope is consistent across repeated requests. At approximately line 397, a 5,269-item request completed in 6,434 ms. Nearby requests for the same scope terminate as `CANCELLED`, including scans lasting roughly 1.5–6 seconds. Sorting summaries show sorting itself was milliseconds; the user-visible delay is associated with the scan/restart and publication path, not an expensive sort comparator.
- **`.nomedia` log B:** around lines 1348–1361, the explicit direct read enumerated 2,021 filesystem entries, accepted 2,012 items, and completed successfully. Around lines 1367–1373, the adapter submitted 2,012 rows and later submitted one search result. This confirms that search did produce a result in that run. It does not establish whether every reported failure came from input focus, a stale search request, an empty partial snapshot, or a delayed Adapter commit.

The log excerpts contain no search text, and no diagnosis here uses filenames or paths.

## Audited call chain and confirmed code paths

### Sorting

`MediaActivity.showSortingDialog()` receives the effective configuration change. It first calls `resortCompletedPhysicalFolder()`. That path correctly protects reuse with `MediaSortReusePolicy`, a current request, a COMPLETE scan/read, current authorization, and ungrouped `Medium` rows. The fallback previously set `mLoadedInitialPhotos = false`, detached `mediaGrid.adapter`, and called `getMedia()`. Thus every scan-in-progress or otherwise non-reusable case removed the visible list and made feedback depend on another cache/scan publication. Repeated choices can cancel and restart a 5,000+ item scan.

`MediaSortOperationGate` already gives each choice its own generation and remains in place. `GetMediaAsynctask` previously captured `folderSorting` at scan start and used it for the final ordinary-folder order, even if the user changed the setting while the read continued.

`ChangeSortingDialog` saved the “this folder” scope separately but only called back when the integer sort value changed. Toggling between global and folder-specific ownership with an equal effective integer could therefore skip the display action.

### Search

The search input callback reaches `searchQueryChanged()`, which snapshots `mMedia`, filters and groups off the UI thread, then submits through `MediaAdapter.updateMedia()` with generation and request guards. These guards correctly reject obsolete results, but the request guard also required the captured request to remain the active scan request. When a refresh starts, the currently displayed authorized snapshot can remain on screen while that old request is no longer current. Searches and clear-search updates against that still-displayed snapshot could then be rejected until the replacement scan publishes.

Each publication calls `setupAdapter()` and reruns the current query. The search worker already had a complete/partial distinction, but its empty-state condition treated an empty PARTIAL result as final for ordinary folders. That could prematurely render a no-results state before later scan batches add matches.

No loading view in `activity_media.xml` covers or disables the search toolbar; the progress indicator is a small centered view. The logs do not prove an input-control disable/occlusion fault.

## Proven facts versus remaining inference

- **Proven from source:** non-reusable sorting detached the adapter; sort operation generations reject older workers; dialog scope ownership was not included in its callback comparison; final scan ordering used a scan-start setting; active scan request identity could reject searches on an older still-displayed snapshot; ordinary PARTIAL empty search results were treated as final.
- **Proven from device logs:** repeated cancel/restart requests and multi-second scans occurred; `.nomedia` enumeration completed; a one-row search result was committed.
- **Not proven from these logs:** that the search field was physically unclickable, which particular generation was discarded, or that the empty PARTIAL rule caused the observed search symptom. The added diagnostics distinguish query/snapshot/access drops from successful adapter commits in the next device run.

## Deterministic failure shapes addressed

1. Sorting during PARTIAL with an attached authorized adapter fell through the strict COMPLETE reuse gate and detached the adapter before a replacement scan.
2. A refresh made the old scan request stale while its authorized rows remained the displayed snapshot; search and clear-search commit checks treated the scan identity as the display identity.
3. A PARTIAL snapshot with no match could show the final no-results placeholder before a later batch introduced the match.
4. A final scan could publish ordering computed from the setting captured before an in-scan sort choice.

## Behavior change

The patch keeps the strict COMPLETE reuse and authorization checks. A PARTIAL display-only sort is limited to active, authorized, ordinary physical-folder snapshots whose rows contain only `Medium`; sectioned or special-source lists are left to the existing scan path. Required rescans retain the existing adapter. Search results now follow the displayed snapshot identity and revalidate lifecycle/source access. A later media publication still re-runs the latest query, and only COMPLETE empty results are final.
