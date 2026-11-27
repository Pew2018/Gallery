# Photo profile loading regression checks

The profile loader uses bounded complete MediaStore projections in both storage modes,
then enumerates explicit directories only for filesystem supplements. Favorites outside
those directories use exact-file queries and file reads. It retains the custom-date
precedence and raw/canonical ancestor protection policy. Ordinary album/favorites
loaders keep their existing storage-mode decisions.

Automated checks in the FOSS Release unit suite:
- Direct-child SQL, literal percent/underscore/backslash names, bounded large selections.
- One complete media query, custom dates, indexed metadata reuse and unindexed supplements.
- Exact favorite reads, unrelated neighbors excluded, indexed-only scoped media retained.
- Raw and resolved file symlink protection, nested locks and cancellation.

Device checks (not claimed as executed by CI):
1. With and without all-files permission, select Camera plus two Pictures directories.
   Compare counts, dates, sizes, videos and global ordering against the previous build.
2. Copy a new JPG/RAW/SVG into a selected folder before the system indexes it; refresh.
   It should appear without including unrelated child directories.
3. Add Favorites as a source, including files both inside and outside selected folders.
   Overlaps appear once. Add/remove favorites and refresh, including file-only favorites.
4. Scroll and refresh the same profile. The current grid stays visible, gets revalidated,
   and the final update retains its visible anchor. An empty final result removes the old grid.
5. Refresh repeatedly, switch profiles, edit sources/exclusions/protection, revoke access,
   or leave the activity. Older callbacks cannot restore cleared content.
6. During refresh open a photo: the viewer must load its authorized current scope rather
   than consume an unfinished grid snapshot. Delete/move a photo, then return and refresh.
7. Trigger a scan failure: a same-scope retained grid remains with an error message;
   a first load reports failure instead of showing an unrelated retained profile.
8. Change a file symlink from a public target to a nested protected target; refresh.
   The file must be excluded unless every required lock is bound and access remains active.

No general first-entry or persistent protected-media cache is introduced.

## Staged loading and re-entry

Public final metadata now has a bounded process cache (two profiles / 12,000 items,
60-second eviction) plus selected-source Room reads. Hits are candidates, never access
proofs. A fresh batch validates a bounded viewport preview, then an indexed stage is
published before filesystem reconciliation; only COMPLETE can feed the final viewer
handoff. Same-screen refresh retains the authorized adapter without revalidating and
republishing the entire old list. Re-entry discards rendering and validates cached
metadata again. Protected/mixed sources do not enter the new public final cache.

Debug-only stages: photo_cache, photo_index, photo_supplement, photo_access,
photo_sort and photo_first_frame. The first-frame measurement marks the grid frame
scheduled after publication, not completion of every asynchronous thumbnail decode.

Additional unit coverage checks indexed-before-supplement ordering, cache re-entry,
unrelated preferences / rename, source changes in flight, symlink target changes
between stages, cache copies, content revision invalidation and anchor preview bounds.

## Follow-up completion audit

- Empty searches over provisional windows do not claim a final no-results state.
  Each Photos publication invalidates searches over the preceding dataset, including
  clearing an inline query while multiple stages share the same request ID.
- A provisional adapter without laid-out visible rows cannot erase the pending anchor.
- Photo viewer clicks capture their scope and are discarded if the profile changes or
  the clicked path leaves the dataset before the asynchronous launch completes.
- Public viewer snapshots copy Medium records and are freshly path/protection-validated
  on a bounded worker before display. Changed symlink targets and deleted physical
  clicked files cannot reuse an earlier metadata handoff as authorization.
- Returning from the viewer keeps a same-scope grid but always reconciles sources in
  the background; a process-local revision is not proof against external changes.
- Bulk favorite imports/clear, shared metadata deletion and date repair invalidate
  both profile metadata and pending viewer handoffs.

Device verification still required: type an unmatched search during first-entry preview,
clear it during final publication, switch profile immediately after tapping a thumbnail,
return from the viewer after another app changes the selected directory, and confirm
that unchanged live grids keep their visible anchor without a blank reload.

## Phase 1: navigation entry reason and bounded first slice

`MediaActivity` now classifies the Photos load reason explicitly instead of inheriting
`REFRESH` from a default parameter. Plain entry, resume, layout/grouping changes and retries
use `ENTER` and may reuse a catalog whose scope, content revision, favorites and freshness
checkpoint are still current. Only the swipe-refresh listener, explicit retry, and paths
that observed real media changes force a full check (`REFRESH`). Permission, source,
protection, visibility and filter changes use `SCOPE_CHANGED`, which the scope key already
invalidates.

The preview path no longer filters and sorts the whole in-memory catalog before showing the
first 160 rows. `CachedMediaPreviewOrder.select` trusts the catalog order only because a
readable cache entry belongs to the exact scope (sorting included), checks existence for at
most the rows it can show, and still runs the full access/protection validation batch.
Random sorting and Room-only candidates always fall back to the full filter + sort path.
`COMPLETE` remains the only authority and still performs the full scan/validation.

Diagnostics now emit `photo_cache_lookup_end`, `photo_cache_missing_filter_end`,
`photo_cache_order_ready`, `photo_preview_slice_ready`, `photo_preview_access_validated`,
`photo_preview_ui_enqueued`, `photo_preview_adapter_committed` and
`photo_preview_first_draw_observed`. Only the last one is a frame-commit observation; it is
still not proof that the display scanned the frame out.

Adapter publications that keep the whole displayed prefix and only append rows now use the
existing APPEND commit regardless of the stage label. A head insertion in date-descending
order breaks the prefix and still uses DiffUtil. Device timing/700 ms/diff jank acceptance
remains open; see `docs/performance/root-navigation-photo-publication-phase1.md`.

## Phase 1.1: two-level cache eligibility and a bounded cold path

The Phase 1 fast path never fired on a device with all-files access. A complete-catalog
reuse proof requires a catalog checkpoint, `PhotoProfileCatalogCheckpoint.capture()` returns
null under `Environment.isExternalStorageManager()`, and the reuse check deleted the whole
entry when the checkpoint was missing. Every entry therefore paid the Room full-filter path
again: 938-2416 ms for 4995 rows in the 16:23 device export.

Eligibility is now evaluated in two levels:

- `PREVIEW_ELIGIBLE`: same profile scope (sorting included), same content revision, same
  favorite relation, inside the 60 s limit. This is enough to *show* validated metadata.
- `COMPLETE_REUSABLE`: additionally backed by a checkpoint that still matches the live
  volume/folder state. Only this may skip the scan.

Only a proven-stale entry (`CONTENT_REVISION_CHANGED`, `EXPIRED`) is evicted. A scope or
favorite mismatch rejects the entry but never deletes it, because the mismatch may mean an
old request is evaluating a newer entry. `evict()` re-checks reference identity first.

The Room cold path now orders candidates (about 1 ms for 5k rows) and then walks a bounded
window: 160 target rows, 640 candidate budget, one memoized parent-readability probe per
folder for that batch. An exhausted budget publishes a partial, explicitly incomplete
preview instead of scanning the whole catalog; an empty result publishes nothing and lets
the indexed/filesystem stages converge. Random sorting keeps the previous full path.

New checks: `PhotoProfileCacheEligibilityTest`, `PhotoCachePreviewWorkTest`,
`PhotoPublicationPolicyTest`, `ThumbnailRecoveryPolicyTest`,
`PhotoCachePreviewMeasurementTest`. Releasing the fast scroller now clears the adapter's
thumbnail pause unconditionally, before the publication guard, so a held publication can no
longer leave the settled viewport blank.

Acceptance on device is still open; see
`docs/performance/root-navigation-photo-publication-phase1.1.md`.
