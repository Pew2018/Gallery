# OxygenOS Classic motion refinement — implementation

Scope: `perf/home-gallery-sorting`, one follow-up commit on top of
`a6bda96d7`. No branch, PR or `main` change; no animation subsystem was rebuilt.

## 1. Ordinary page predictive back (A)

Files:

- `app/src/main/res/interpolator/oxygen_classic_page_back_alpha.xml` (new)
- `app/src/main/res/anim/oxygen_classic_page_back_exit.xml`
- `app/src/test/.../PredictiveBackCloseAnimationTest.kt`

The CLOSE-exit alpha now uses a dedicated `PathInterpolator` resource instead of
`decelerate_quad`. The path is

```
M0,0 C0.09,0.36 0.18,0.92 0.26,1 C0.5,1 0.75,1 1,1
```

with `y` used as the alpha fraction, so `alpha = 1 - y` (the same convention the
platform uses: it inverts the interpolated progress for alpha). It is fully gone
at 26% of the post-commit clock (~117 ms of 450 ms), i.e. inside the requested
20–35% window, slightly softer than the AOSP reference at 20%.

Why an interpolator and not a shorter duration: the Shell evaluates a set through
`AnimationSet#getTransformationAt`, which forwards the raw progress to each
child; the child duration is never used for renormalisation. A 100 ms alpha child
would evaluate past its end on the Shell path (no clamp) and would desynchronise
the same resource when it runs as the plain `finish()` window animation. Both
children keep `duration == 450 ms`; the set clock and the 96dp geometry are
unchanged.

The interpolator was validated against the `PathInterpolator` contract before
being relied on: start `(0,0)`, end `(1,1)`, strictly monotonic `x`, exact
endpoints. A test loads it through `AnimationUtils.loadInterpolator` and samples
0..1; another evaluates the resource through the Shell's real
`Animation#getTransformationAt` path (reflection) and asserts the fade shape.

Left/right: no change. AOSP evidence (see `root-cause.md` A3) shows the platform
default also moves the closing rect by `+96dp` for both edges after commit; the
edge-dependent movement is owned by the pre-commit phase. Adding a reverse
animation would be speculation, which the task forbids.

## 2. Viewer entry (B)

Files:

- `helpers/PhotoViewerTransition.kt`
- `helpers/PhotoFragment.kt` (`loadImage`)
- `activities/MediaActivity.kt` (launch intent)
- `activities/ViewPagerActivity.kt` (durations, diagnostics)

### 2.1 Split clocks, one clock per direction

`PhotoViewerTransition.DURATION_MS` is replaced by:

- `ENTRY_DURATION_MS = 160 ms`
- `RETURN_DURATION_MS = 120 ms` (unchanged measured baseline)

Both sides use the same constant per direction:

| side | entry | return |
|---|---|---|
| grid | `sharedElementExitTransition` = ENTRY | `sharedElementReenterTransition` = RETURN |
| viewer | `sharedElementEnterTransition` = ENTRY | `sharedElementReturnTransition` = RETURN |

The entry watchdog and fallback timings use `ENTRY_DURATION_MS`; the return
timeout uses `RETURN_DURATION_MS`. `VideoFragment`'s aspect settle keeps the
120 ms value via `RETURN_DURATION_MS` (behaviour unchanged). Folder-cover
transitions keep their own `AlbumPageTransition.DURATION_MS`.

160 ms was chosen from the task's candidate list, not measured on this machine:
the entry is the long, content-defining motion (thumbnail aperture → fitted
full-screen aperture) and 133 ms of visible movement was the reported weakness.
The return is not lengthened: continuity from the finger is its priority and the
return→target-frame metric must not regress. Both values remain marked for
device acceptance.

### 2.2 Start the real decode earlier

`MediaActivity` now puts the grid's captured signature
(`medium.getSignature()`) and the entry id into the launch intent
(`EXTRA_ENTRY_SIGNATURE`). `PhotoFragment.loadImage()`:

1. starts the Glide request immediately for the selected still image (GIF/SVG/
   APNG/AVIF keep their existing renderers);
2. reads EXIF orientation and the static-photo header on the existing
   background thread in parallel, publishing them when ready;
3. only falls back to the old "header first, then Glide" sequence when no grid
   signature is available (external/older caller), so the static handoff can
   still validate its cache key.

The header is still read and compared before a static preview is trusted
(`prepareStaticPhotoEntry`, `validatesStaticPhotoHandoff`); only the
`staticGlideSignature` precondition is seeded from the grid. Glide already
applies EXIF orientation, and the parallel value is only consumed by the Picasso
fallback and the subsampling decoder, both of which run after the interactive
frame. No decode is moved onto the main thread.

This removes one background round trip from the interactive path. It does **not**
remove the dominant full-resolution decode (82–235 ms in the available sample),
and that is reported honestly in `validation.md`.

### 2.3 Entry diagnostics

The existing diagnostics framework gained the missing phase events (all
default-off, privacy-filtered by `ViewerEntryDiagnostics`, no new I/O):

`fragment_created`, `renderer_request_started` (reason: `grid_signature` /
`awaiting_header` / `no_static_header`), `entry_pre_draw` — together with the
already existing `entry_requested`, `viewer_created`, `media_ready`,
`entry_release_requested`, `entry_released`, `shared_animation_started`,
`shared_transition_ended`, `entry_completed`.

## 3. Viewer return, predictive back and drag (C)

Files:

- `helpers/ViewerBackGesture.kt`
- `helpers/ViewerVisualMotion.kt` (new, see D)
- `activities/ViewPagerActivity.kt`

### 3.1 Continuous release handoff

The release path is unchanged in principle: the surface, scrim alpha and chrome
snapshot stay as they were under the finger, and the platform transition starts
from that captured frame. Two gaps were closed:

- **Takeover continuity.** If a predictive-back gesture interrupts a horizontal
  drag/settle, the inherited translation is now re-expressed as a slope against
  the inherited progress before the system path renders, so the takeover frame
  is pixel-identical instead of snapping to x = 0.
- **Residue.** Releasing the interactive lease now restores the surface's rest
  frame (`scale = 1`, translation `0`, `LAYER_TYPE_NONE`), so a fallback or a
  renderer swap cannot leave a scaled/translated proxy behind.

A renderer swap between the gesture and the return is a supported path (the new
renderer becomes the mapped proxy and the released lease restores the old one).
It is reported as `viewer_return_surface_replaced`; it deliberately does **not**
introduce a second return coordinator.

Dynamic return duration was **not** implemented. The grid installs its reenter
transition before the drag exists; the two sides of one cross-Activity handoff
therefore cannot share a release-dependent `ReturnMotionSpec`. The task allows a
fixed, symmetric return clock in that situation, which is what is used.

### 3.2 Predictive-back edge direction

`BackEventCompat.swipeEdge` is forwarded as `+1` (left edge), `-1` (right edge)
or `0` (unknown/touch). The Viewer adds a **14 dp** side bias
(`ViewerVisualMotion.BACK_EDGE_OFFSET_DP`) that:

- is exactly 0 at progress 0 and for the opposite edge;
- ramps with smoothstep, fully applied by 30% of a fresh gesture;
- ramps from the takeover progress when the gesture resumes an interrupted
  frame, so the first frame cannot jump;
- disappears with progress on cancel (translation returns to 0).

Direction follows the platform's pre-commit movement. It is a visual hint inside
the existing interactive return; it never becomes another exit path and does not
compete with the system window animation.

### 3.3 Cancelled rebound

`ViewerReturnProgress.resume()` and the settle animator are untouched. The same
`renderInteractiveDismissProgress` function is used for drag, system gesture and
rebound, so the chrome/toolbar/translation curves recover together.

## 4. Layers, toolbar and system bars (D)

Files:

- `helpers/ViewerVisualMotion.kt` (new)
- `activities/ViewPagerActivity.kt`

`ViewerVisualMotion` is a pure, single-clock mapping. No layer owns an Animator;
each call site passes the same normalized progress:

| layer | mapping |
|---|---|
| image geometry | platform shared-element transition / `1 - 0.34p` during drag |
| scrim | identity on progress (follows the image) |
| system bars | `returnSystemBarProgress * remaining` (tied to the scrim clock) |
| entry chrome | `smoothstep((p - 0.12) / 0.76)` — controls join after the image |
| return chrome | `smoothstep((remaining - 0.28) / 0.72)` — controls leave before the media |
| toolbar cross-fade | 1 above scrim 1.0, 0 at/below 0.80, smoothstep between |

Every function is exact at 0 and 1 and monotonic, so entry, return, cancel and
fallback all land on the same endpoints.

The toolbar fix keeps both protections: `protectGridToolbarDuringReturn` still
clips the moving image above the exposed grid toolbar, and the Viewer toolbar is
still hidden while the grid owns the top bar — but via the continuous cross-fade
above. At the moment the fade starts the grid is still ~99% covered by the
scrim, and it is fully gone by the time the grid is meaningfully visible.

No system-bar colour was forced. Status/navigation-bar appearance continues to
follow `viewerSystemBarProgress`, which is restored to 1 on cancel and cleaned up
by the existing lifecycle paths.

## 5. Root page handoff (E)

Files:

- `helpers/GalleryBottomBar.kt`
- `activities/MainActivity.kt`
- `activities/MediaActivity.kt`

`frameState` is now a typed `GalleryBottomBar.RootFrame?`:

| state | meaning |
|---|---|
| `CONTENT` | the real grid/list is on screen with children |
| `EMPTY` | the real empty placeholder is on screen |
| `ERROR` | the real failure placeholder is on screen |
| `LOADING` | a loading indicator is actually visible on this frame |
| `null` | blank — nothing may be committed |

`coordinateFirstDraw` commits immediately on `CONTENT` / `EMPTY` / `ERROR`.
A `LOADING` frame is allowed to draw (so feedback is never blocked) but does not
retire the outgoing page on its own; a bounded
`LOADING_HANDOFF_LIMIT_MS = 700 ms` deadline then accepts the stable loading
frame and retires the source, so no activity is retained indefinitely. Content
arriving before the deadline commits immediately and cancels the waiting.

The Albums root gained the same 3 dp `LinearProgressIndicator` strip the folder
and media roots already use, added to the existing `MySearchMenu` with the
existing `OxygenProgressStyle` (no new colour/control language). Its
`loadingFrame` reveals the strip synchronously before the first traversal, so
the first presented Albums frame is either real rows, a real empty state, or a
visible loading strip — not the bare page background. `setDirectoryLoading(false)`
takes the strip down through the existing minimum-visible policy.

`MediaActivity`'s state now also requires the indicator to be really visible and
includes the central `loadingIndicator`, so a requested-but-invisible load can no
longer be mistaken for a loading frame.

## 6. Preserved architecture

Untouched: `MediaTransitionSessionCoordinator` state machine and leases, source/
target/proxy leases, shared-element snapshot/mapping, media path and identity
checks, Viewer publication boundary and selection state, grid return targeting,
`ViewerReturnProgress.resume()`, fallback and timeout cleanup, the custom CLOSE
branch and its real parent preview. No second Viewer transition coordinator and
no unrelated media-scan/sort/database/permission changes were made.
