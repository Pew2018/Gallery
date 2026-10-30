# Custom photo profiles MVP

Base: main ac460e4f386e3531abe9bc98de1b0513c12b2eb1.
Branch: feature/custom-photo-profiles. No changes to main or PR creation.

## Design and supported behavior

- Stable ALL/FAVORITES/PHOTOS navigation IDs. Normalization preserves old relative order and appends missing IDs. All three entries persist through Config and are reorderable.
- A versioned atomic SharedPreferences JSON document stores UUID profiles and selected ID, not media snapshots. A malformed record fails closed. Source edits are private drafts until Save; names need not be unique.
- Photos reuses MediaActivity, MediaAdapter, permissions, selection, share, sorting and view controls. HOME quick-action preferences are read without rewriting them. Unsupported filter/hidden/excluded/group actions are suppressed. Sorting and view settings use a dedicated virtual path.
- Direct-folder image reads reuse MediaFetcher, including a single grouped scoped-storage MediaStore result across sources. Favorites are dynamically queried from the existing DB. Exact paths deduplicate. Taken dates fall back to modification dates. No recursive folder inclusion or videos.
- Configuration switches cancel old work, invalidate request generations, clear adapters and reset search. Profiles bypass the shared metadata-cache read/write path. Leaving the page clears its shared thumbnail references.
- Viewer launches carry a profile UUID; first authorization and every reload query the stored profile again. They never fall back to all-device media or an individual folder. Missing/deleted profiles produce no media and close the viewer. No Intent media arrays.
- Hidden paths, .nomedia ancestry and excluded roots are rejected even when global temporary visibility flags are set. Protected folders/ancestors are excluded, including from favorites. Dedicated and legacy favorites locks make the virtual favorites source unavailable. No authorization sessions are inherited or created by this feature. Preference changes clear/reload or close the current surface. Final task publication rejects results scanned under changed preferences.
- Sources that are missing or restricted remain in stored profiles and appear as unavailable in the editor. Deleting a profile only updates profile preferences.

## Verification

Unit coverage: migration and three-item persistence; UUID/Unicode round trips; CRUD isolation; duplicate sources and media; malformed/unsupported schema; invalid selections; cancelled drafts; required name/sources; direct-parent membership; dynamic favorite membership; authorization rejection; late-request isolation. Existing classic dialog, toolbar, favorites and scrolling regression tests remain in the existing APK workflow.

Local Gradle could not start: no cached Gradle distribution, and the environment cannot reach services.gradle.org. XML parsing, resource-reference checks and git diff --check are performed locally. Workflow runs execute the Kotlin tests and assemble/validate the signed arm64 FOSS APK. CI does not establish device interaction correctness.

## Required device verification

1. Upgrade with reversed two-tab order; verify Photos appended, reorder all three and relaunch. Check tap ripple, theme tint and gesture insets.
2. Create Camera + Street profile and another Favorites + Edited profile. Check cancel, duplicate names, edit/rename/delete, restart and rotation. Inspect unavailable sources.
3. Confirm images interleave by date, no nested-folder leakage, no video, no duplicates for folder/favorite overlap; test HEIC/GIF/RAW supported by the existing decoder.
4. Rapidly alternate profiles while loading; open viewer, swipe across sources, refresh, cancel favorite, delete/move current photo and return. Verify a favorite-only item disappears after unfavoriting.
5. Test hidden, excluded, .nomedia, locked parent/child and legacy/dedicated favorites locks. Changing protection while a page/viewer is open must remove/close it.
6. Test Android 11+ scoped storage, partial photo permissions, disconnected removable storage, search, sorting, grid/list, selection/share, dark/custom toolbar themes. Regression-check Albums, favorite folders, recycle bin and external pickers.

## Deliberate MVP limits

Protected and hidden/excluded sources cannot be browsed here; use the existing authenticated album flows. Source chooser lists indexed accessible media folders; it does not request new SAF grants or discover arbitrary unindexed empty directories. No recursive inclusion, videos, filesystem live watching, per-profile display settings, sync or import/export. Dialog drafts may be discarded on Activity recreation; saved configurations and selection persist. Device verification is still required.
