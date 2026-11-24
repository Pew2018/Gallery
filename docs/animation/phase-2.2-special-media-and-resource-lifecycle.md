# Phase 2.2 — Special media, zoom-state consistency and the return-resource lifecycle

Source baseline: remote `perf/home-gallery-sorting` HEAD `2c0fb8640a59d95704f63f638f4c35a0f8eeae82`
(fetched and fast-forwarded before any change; equals the latest device-log identity
`Source: 2c0fb8640a59`). Evidence: `Gallery-Diagnostics-20261125-002128.txt`,
`15ea97d312afdda84c9b6b91d64c01f7.mp4`, Pixel 8 Pro / Android 16 / Gallery 1.14.1 (32) Release.

## 1. What the 2026-11-25 run established

Phase 2.1 targets held: five Viewer entries averaged 184.2 ms to the shared animation
start (250/208/214/124/125 ms), four shared returns started in 107/82/31/28 ms, the
system-bar polarity was correct 5/5 and shared entry succeeded 5/5. One return fell
back: `VIEWING → INTERACTIVE_RETURN → VIEWING → RETURN_PREPARING → FALLBACK`,
preceded by `viewer_transition_photo_unavailable count=4` twice (00:20:56.470 and
00:20:56.800) on a media whose entry had mapped and completed normally
(`entryMapped=true`, `hasSelection=true`).

## 2. P0-A — the zoom baseline went stale across the preview→full upgrade

### 2.1 What count=4 actually means

`count=4` is `gestureImageIsAtFit() == false` in `PhotoFragment.sharedElementViewFor`,
i.e. `SubsamplingHandoffPolicy.isAtFit(mCurrentGestureViewZoom, mInitialZoom,
mHasInitialZoom, 0.01f)` returned false. This was *not* the launch fallback of earlier
phases: the frame had been reported and the entry mapped, so the rejection came from
the Viewer's own proxy preparation — its fit-state bookkeeping.

### 2.2 Verified mechanism (code-level, library-confirmed)

The gestures library (`com.alexvasilkov.gestures.GestureImageView`, Fossify fork
`org.fossify:gestureviews`) updates its state whenever the renderer drawable changes
and notifies the state listener on *every* update — including resets (notified twice)
and proportional patches (notified even when the numeric state ends up unchanged):

- `GestureImageView.setImageDrawable` reads the new intrinsic size into
  `settings.setImageSize`, then either patches or resets:
  - both old and new sizes valid → `setTempZoomPatch(min(oldW/newW, oldH/newH))` →
    `updateState()` (state zoom multiplied by the patch, bounds restricted, no reset);
  - either size missing → `resetState()` (state zoom = the new fit zoom).
- There is no listener flag distinguishing "image changed" from "user gesture"; the
  image size is the only app-side discriminator.

Phase 2.1's proportional preview made this matter. For the entry photo Glide first
delivers a 0.5× preview, then the full decode:

1. preview binds (previous drawable null) → **reset** → state zoom = preview's fit
   zoom (e.g. 0.5 for a 2000px preview in a 1000px viewport). The listener captured
   `mInitialZoom = 0.5` and set `mHasInitialZoom = true`.
2. full decode binds → **patch** → state zoom × 0.5 = full's fit zoom (0.25). The
   listener ran again, but the capture branch is guarded by `!mHasInitialZoom`, so the
   baseline stayed 0.5.
3. First predictive back → `gestureImageIsAtFit()` compares 0.25 against 0.5 → false
   → `reject(4)` → `viewer_no_proxy` after a transient `RETURN_PREPARING`.

Visually nothing changed (same fit rectangle); the *absolute* zoom moved. This matches
the device timeline exactly: the entry completed with preview geometry, the upgrade
landed ~280 ms later, and the first back gesture then hit count=4.

The hypothesis was verified before the fix: the A-group tests
(`GestureZoomBaselineTest`, A01/A02) encode the 2000→4000 px upgrade and fail against
the old capture-once semantics.

### 2.3 Excluded hypotheses

- *Subsampling handoff*: rejects with 8/9 (tile renderer zoomed / geometry), not 4.
- *Thumbnail-not-loaded fallback*: `mReportedImagePath == path` held (entry mapped).
- *Rotation / GIF / identity*: rotation rejects with 3 before 4; GIF with 5.
- *Tolerance too tight*: the zooms differ by 2×, far beyond any tolerance; widening it
  would not survive arbitrary aspect ratios and was explicitly forbidden.

### 2.4 The fix: `GestureZoomBaseline`

New pure policy `helpers/GestureZoomBaseline.kt` owns the fit baseline; `PhotoFragment`
delegates all three former fields (`mCurrentGestureViewZoom`, `mInitialZoom`,
`mHasInitialZoom`) to it. Per state change, keyed on the renderer's image size:

- **same size** → only the current zoom moved (user input, double-tap, programmatic);
- **size change while the user was at fit** → the incoming state zoom *is* the new
  resource's fit zoom (patch of the old fit, or a reset) → recapture (`Rebased`);
- **size change while the user had zoomed** → the library scales the user's zoom by
  the same patch, so the baseline maps through the patch (`Scaled`) instead of
  recapturing the zoomed state. The user's zoomed state is never laundered into "fit",
  and pinching back out restores shared-return eligibility (device test 13.1.9);
- `invalidate()` on `loadImage()` / `loadBitmap()` keeps rotation, portrait-stripe and
  media-switch paths on their existing recapture-on-first-callback behaviour.

The double-tap target is recomputed on `Rebased` exactly as the original capture path
did, and scaled by the patch on `Scaled`. Horizontal paging and the dismiss gates use
`zoomMatchesBaseline()` (legacy semantics: at fit before the first frame so paging
stays possible); the proxy and subsampling gates use `isAtFit()` (baseline required),
unchanged.

Forbidden shortcuts were not taken: the check is not deleted, `mInitialZoom` is not
blindly set to the current zoom, user zoom is never reset on resource delivery, the
tolerance is untouched, and neither image quality nor the existing animation timings
were traded away.

### 2.5 Tests

`GestureZoomBaselineTest` — 17 tests: A01–A16 plus invalid-size handling. A01/A02 are
the failing-then-passing device pair; A03/A09/A15 pin that a zoomed user stays zoomed
(and rejected) across upgrades; A10 covers pinch-back eligibility after an upgrade;
A13 forbids stale foreign geometry from faking a fit; A16 pins the paging comparison.

## 3. P0-B — predictive back without a shared proxy

An audit of `ViewPagerActivity` confirmed the gesture lifecycle is already safe when
`getCurrentSharedElementView` returns null: `beginGesture()` and the proxy lookup
mutate no visual state (surface, scrim, bars and layer type are touched only after a
non-null proxy), `cancelGesture` rolls the session back to `VIEWING` with no outcome,
and the committed path terminates exactly once (`terminate → releaseAll → clean →
releaseToPage → finish → fade`), reusing the single existing state machine. Nothing
that worked was redesigned and no second state machine was added.

What was missing was *attribution*: every proxy failure collapsed into
`FALLBACK:viewer_no_proxy`, with the photo renderer's bare integer codes invisible at
Activity level. Changes:

- `helpers/ViewerProxyRejection.kt` maps the legacy codes to semantic names
  (1 `SELECTION_UNAVAILABLE`, 2 `RENDERER_NOT_READY`, 3 `ROTATION_ACTIVE`,
  4 `NOT_AT_FIT`, 5 `GIF_RENDERER`, 6 `DRAWABLE_UNAVAILABLE`,
  7 `GEOMETRY_UNAVAILABLE`, 8 `SUBSAMPLING_ZOOMED`,
  9 `SUBSAMPLING_GEOMETRY_UNAVAILABLE`, 0 `PROXY_PREPARE_REJECTED`) and back.
- `PhotoFragment` records `lastProxyRejection`, emits a
  `viewer_photo_proxy_rejected` event carrying the reason, generation and zoom
  baseline state, and still logs the legacy integer for dashboard continuity.
- `ViewPagerActivity` surfaces the reason in `viewer_requested`
  (`proxyRejection`), in `viewer_return_fallback` (invalid reasons fall back to
  `PROXY_DETACHED` / `NOT_ELIGIBLE`), and on gesture start
  (`viewer_return_gesture_unavailable`).

Legitimate fallbacks remain legitimate: a genuinely zoomed, rotated or invalidated
media still falls back safely, and the state machine still ends every close operation
in exactly one terminal state.

Tests: `ViewerProxyFallbackTest` (B05/B06/B07 sequences, code↔name round-trip) plus
the existing `MediaTransitionSessionCoordinatorTest` scenarios (gesture cancel/commit,
stale generations, single terminal).

## 4. P0-C — Glide resource ownership of the pixel reserve

### 4.1 What was verified

Glide 5.0.7 (via Commons 7.1.3). The previous reserve stored the `Drawable` instance
the Grid holder was displaying, keyed by the holder's request list. That is only safe
while the original request keeps the resource *active* — which cannot be proven for
every path:

- the reserve was also filled from the return-time thumbnail callback, where the
  holder is a normal recyclable holder;
- anything that clears or replaces the Glide request (rebind, `onViewRecycled`,
  adapter refresh, cache eviction) releases the resource; after the memory-cache
  eviction Glide returns the bitmap to the `BitmapPool`, and a later decode may
  overwrite pixels the reserve still hands out;
- removing an entry from the reserve map proves nothing about the request's
  ownership, and `take()` gave two holders a shared drawable.

### 4.2 Decision: defensive copy, not a retained resource

`remember()` now copies the bitmap (`ARGB_8888`, ~1.8 MB for a 670×670 grid
thumbnail, one bounded `memcpy` per entry) and `take()` returns a fresh
`BitmapDrawable` over that copy. The copy severs every link to the request, the
holder and the cache in one step, and makes `take()` a true exclusive transfer.

- Only software `BitmapDrawable` pixels are reserved; animated, hardware and
  non-bitmap drawables are skipped rather than held beyond their request.
- Budget: capacity 3 entries (unchanged), per-entry cap `MAX_ENTRY_BYTES` 12 MB,
  total cap `MAX_TOTAL_BYTES` 24 MB with eldest-first eviction, plus the existing
  exact-size check (`SIZE_TOLERANCE_PX = 1`) and the request-key identity.
- Session ownership: every entry records the owning `MediaActivity` instance and
  `endViewerReturn` releases with `clear(owner = this)`. A configuration change
  creates the replacement Activity before destroying the old one, and the old
  instance's teardown can no longer erase the new session's freshly filled entry.

Rejected alternatives: holding Glide internals' refcounts (private API), a second
full decode at return time (the original 676 ms problem), or unbounded retention.

Tests: `ViewerReturnPixelReserveTest` — the original identity/size/consumption
contract plus C01/C02/C04/C06/C07/C08/C14/C15/C16-style ownership cases (copy
isolation from source mutation and recycle, owner-scoped clear, oversized rejection,
byte budget).

## 5. P1-A — bounded preparation of the current media's return pixels

Phase 2.1 only kept the launch media's already-decoded drawable (`grid_target_reserved_pixels`
never appeared in the 2026-11-25 log; the observed fast returns were ordinary cache
hits at 45 ms and 22 ms). This phase makes the preparation follow the Viewer:

- `MediaAdapter.returnPrefetchRequest(medium, width, height)` builds the request with
  the adapter's own parameters, so the Glide engine key and the request identity the
  returning holder will use are the same by construction (D05 asserts this against a
  really bound holder).
- `Context.gridThumbnailRequestBuilder` is the extracted, shared construction used by
  both `loadImageBase` and the prefetch (signature, decode format, disk strategy,
  transforms, decode class, WebP decoder flag).
- `ViewerCurrentMediaRegistry` carries the Viewer's current path to the stopped Grid
  Activity. `ViewPagerActivity.onPageSelected` reports the settled page; the Grid
  registers one observer per Viewer session and removes it when the session ends.
- `MediaActivity` arms a 150 ms stability window per page change, cancels the
  in-flight prefetch on every new page (rapid paging never decodes what it flies
  over), issues at most one request at a time at `Priority.LOW` (not part of the
  Glide key, so it never competes with the Viewer's foreground loads for priority
  while still sharing the cache entry), and copies the result into the reserve,
  immediately releasing the engine resource. Steady-state memory therefore stays
  bounded by the reserve, not by the prefetch.
- Video uses the identical builder, so it warms the grid's standard video-frame
  thumbnail; the player frame is never substituted.
- Failures and gaps are explicit: `grid_target_prefetch_started/ready/failed/cancelled/skipped`
  with reason (`page_changed`, `superseded_by_current`, `session_ended`,
  `media_not_in_grid`, `request_unavailable`), plus `reserved`/`reserveBytes` on
  success.

Scope limits kept: only the current media is prepared (no neighbours), no full media
scan, no RecyclerView re-scroll, no change to the grid's own loading policy, and the
Viewer's full-size decode always wins the scheduler.

Tests: `ViewerReturnPrefetchPolicyTest` — D05 key parity, D06 size rejection,
D08 video/image identity, D02/D03/D17 registry behaviour.

## 6. P1-B — preview, full resource and quality-handoff diagnostics

The entry gate deduplicates its Activity notification on purpose, which had made the
quality handoff unobservable. Additions in `PhotoFragment`:

- `viewer_entry_preview_ready` / `viewer_entry_full_resource_ready` from the Glide
  listener (`isFirstResource`), with generation, cache source and intrinsic size;
- `viewer_entry_resource_bound` on *every* renderer binding (not deduplicated) with
  role (PREVIEW/FULL), intrinsic and settings dimensions, viewport, current zoom, fit
  zoom, baseline presence, at-fit and elapsed-since-request;
- the `viewer_photo_proxy_rejected` event from §3 carrying the same zoom fields.

These are gated on diagnostics being enabled and are not built per frame; real paths
are still never logged (anonymous ids only). Correctness guarantees the events now
make visible: the preview and full decode share one renderer pipeline, the upgrade
carries no second readiness path, and the entry gate still releases exactly once
(E02/E03 in `PhotoResourceStageTest`).

## 7. Deterministic checks and numbers

Reserve copy cost: one `Bitmap.copy` per reservation — 670×670 ARGB_8888 ≈ 1.8 MB
(~0.5–1 ms on the host JVM measurement here); at most 3 entries, ≤ 24 MB total, and
each reservation is consumed or released with its session. No synchronous copy exists
on the return path itself; the return still hands over an already-decoded drawable.

Host tests only validate policy, lifecycle and arithmetic. They are not evidence of
Android 16 frame composition, and no host timing is reported as Pixel 8 Pro
performance. The device targets from §13.3 of the task remain open until the next
real-device run.

## 8. Tests added in this phase

| Group | File | Tests |
|---|---|---:|
| A | `GestureZoomBaselineTest` | 17 |
| B | `ViewerProxyFallbackTest` | 5 |
| C | `ViewerReturnPixelReserveTest` (extended) | +9 |
| D | `ViewerReturnPrefetchPolicyTest` (incl. the 50-switch stress case) | 8 |
| E | `PhotoResourceStageTest` | 3 |
| — | new tests total | 42 |

Full suite: `./gradlew :app:testFossDebugUnitTest` on JDK 21 (Android SDK 36 requires
it; JDK 17 fails the eleven `sdk=[36]` classes at sandbox creation) —
**1596 tests, 0 failures, 0 skipped** (previous phase: 1554).

## 9. Still to verify on device

1. Long screenshots: open, return immediately from both edges, return after the full
   decode and after subsampling takeover, down-drag and cancel, zoom then return,
   return after restoring fit, quick open/return. `count=4` must not reappear for
   media that is visually at fit.
2. Return resources: immediate return, after 5 pages, after 12+ pages, after paging
   to a video, after a fast fling that settles, video targets outside the grid
   viewport, cache-missing large files. Confirm `grid_target_prefetch_ready` and a
   real `grid_target_reserved_pixels` hit, not just shorter waits.
3. Quality handoff: `viewer_entry_preview_ready` → `viewer_entry_full_resource_ready`
   ordering and deltas, no visible jump, no repeated entry animation.
4. Performance targets unchanged from the task table (entry ≈184 ms, prepared return
   ≤100 ms to the remaining animation, Video return improvement, Photos first screen
   ≤500 ms, no fast-scroll regression, bounded memory).

## 10. Unresolved / not attempted

- The ordinary-folder refresh publication policy (`publishPagePreview` rejecting
  intermediate results until the ~9 s full publication) is still out of scope and
  untouched, as instructed.
- No emulator or device run was possible here; every timing claim above is either
  from the supplied 2026-11-25 log or host-side test output.
