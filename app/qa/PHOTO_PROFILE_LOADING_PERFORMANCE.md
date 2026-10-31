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
