# OxygenOS Classic motion refinement — root causes

Baseline for this round:

- Branch: `perf/home-gallery-sorting`
- Re-fetched remote HEAD at start: `a6bda96d7fb6961457f7efc799d5ff9073dd81d7`
  (`git rev-list --left-right --count HEAD...origin/perf/home-gallery-sorting`
  returned `0 0`; no other agent commit existed at start).
- Previous animation commit on the branch: `c52c44c7c` and `a6bda96d7`.
- Platform reference: AOSP `android16-release`
  (`core/java/android/view/animation/{Animation,AnimationSet,PathInterpolator}.java`,
  `libs/WindowManager/Shell/src/com/android/wm/shell/back/CustomCrossActivityBackAnimation.kt`),
  fetched and read during this round.

> Evidence boundary first: the task workspace (outside this repository) only
> holds an older recording and an older diagnostics log from 2026-11-23 11:27,
> **not** the eight-sample recording/log quoted in the task. The numbers in the task are treated as the
> previous device baseline. Nothing in this document is claimed as a new device
> measurement; every on-screen conclusion stays in the device-acceptance list of
> `validation.md`.

## A. Ordinary page predictive back

### A1. Real parent preview is preserved

`OxygenClassicActivityMotion.install()` keeps a non-zero
`activityCloseExitAnimation` window style (`OxygenClassicPageBackWindow`), which is
what makes WindowManager offer the *custom* CLOSE branch to the Shell
(`BackNavigationController.isCustomizeExitAnimation`). That branch keeps the
entering parent on a full-screen base rect. The CLOSE-enter resource stays
alpha-1/matrix-identity, so the parent is never displaced or faded by the app.
This round did not change the branch, the style, or the enter resource.

### A2. Why the exiting page still double-exposed its text

The Shell drives the CLOSE resources through `AnimationSet#getTransformationAt`,
which forwards the **same raw 0..1 progress** to every child. Each child then
applies its own interpolator inside `Animation#getTransformationAt`
(`interpolatedTime = mInterpolator.getInterpolation(normalizedTime)`).

The previous CLOSE-exit used `@android:interpolator/decelerate_quad`:

```
alpha(p) = 1 - (1 - (1-p)^2) = (1 - p)^2
alpha(0.25) = 0.5625      alpha(0.35) = 0.4225
```

The real parent page is fully composed underneath from the release frame, so a
56% opaque foreground at a quarter of the post-commit clock reads as a second
copy of the toolbar/list text. The platform reference,
`DefaultCrossActivityBackAnimation`, uses `max(1 - linearProgress * 5, 0)`, i.e.
the closing page is fully gone at 20% progress.

Reducing the alpha child's `duration` cannot fix this: the Shell path ignores the
child duration when evaluating the transformation (only the set duration feeds
`getPostCommitAnimationDuration()`), while the plain `finish()` window transition
in the same window uses the normal time-driven path. An interpolator is the only
mechanism that works on both paths.

### A3. Left/right direction

The fixed `+96dp` translation is not a bug. In the custom branch
`preparePreCommitClosingRectMovement(swipeEdge)` already owns the directional
pre-commit movement (left edge moves the closing page right, right edge moves it
left), and after commit `currentClosingRect` is frozen: the XML matrix is applied
to that release rect. The platform's own default branch retargets the closing
rect to `currentClosingRect.left + enteringStartOffset` for **both** edges, the
same `+96dp`. Adding a second, edge-dependent reverse animation would therefore
diverge from the platform rather than fix anything, so no geometry change was
made.

## B. Viewer entry

The entry chain is the one added in `b9e7817d`/`c52c44c7c`:

```
MediaActivity tap
→ ActivityOptions.makeSceneTransitionAnimation
→ ViewPagerActivity.onCreate: postponeEnterTransition()
→ PhotoFragment.onViewCreated → loadImage()
→ Glide onResourceReady → reportImageRendered → imageRendered()
→ requestMediaEntryRelease → doOnPreDraw → releasePhotoSharedElement
→ startPostponedEnterTransition → shared element enter transition
```

From the available older diagnostics sample (8 entries) the shape is
consistent:

| span | observed |
|---|---|
| `entry_requested` → `viewer_created` | 25–31 ms |
| `viewer_created` → `media_ready` (Glide) | 82–235 ms |
| `media_ready` → `entry_released` | 3–6 ms |
| `entry_released` → `shared_animation_started` | 2–9 ms |

So the wait is **decode-bound**, not a redundant Glide request or an extra
layout pass in the transition gate. Auditing the path found:

- The selected media is requested exactly once by `PhotoFragment`; the shared
  surface reuses the delivered drawable (`sharedElementViewFor`), it does not
  issue a second request.
- The grid already holds the media identity, geometry, drawable and signature.
- The one avoidable serialisation was in `PhotoFragment.loadImage()`: Glide was
  started only after a background `getImageOrientation()` + `StaticPhotoHeader.read`
  round trip and a return to the main thread. Glide applies EXIF itself, so the
  orientation is not needed for the interactive frame.
- `postponeEnterTransition()` must stay: releasing it before the target renderer
  has pixels is exactly the full-screen-then-transition defect.

Options that were **rejected** with evidence:

- *Preload the full image when the grid is tapped.* Glide's memory-cache key
  includes the requested size. The Viewer's `ImageView` is not measured yet at
  tap time, so the only available size (display metrics) is not guaranteed to
  match the view size; a mismatch decodes a second full-screen ARGB_8888 bitmap
  (≈16 MB) for nothing. That is a memory risk on a gallery, not a measured win.
- *Move the grid thumbnail first and swap to the full renderer later.* The
  thumbnail is not the full media frame; at the end of the expansion the proxy
  would have to swap from a low-resolution crop to the full decode, producing
  exactly the second scale/crop jump the task forbids. `StaticPhotoEntryView`
  already exists for the case where the full renderer frame is available.

## C. Viewer return and predictive back

The live-frame handoff added in `c52c44c7c` is structurally correct: on release
`mediaDismissSettled(true)` keeps `interactiveDismissSurface`, keeps the scrim
alpha and the chrome snapshot, and hands the surface to
`sharedElementReturnTransition`, whose `ChangeBounds` + `ChangeTransform` capture
the translated/scaled proxy as the start. The remaining defects were in the
seams around it:

1. A predictive-back gesture that took over an interrupted horizontal drag reset
   the translation slope to zero, so the first gesture frame snapped the image
   sideways. The inherited translation is stored as a slope, but the system path
   never applied it.
2. No edge information was forwarded, so left/right predictive back looked
   identical even though the platform's own pre-commit movement is directional.
3. A fallback or a renderer swap could leave the interactive surface scaled,
   translated and hardware-layered after the lease was released.
4. With a two-sided cross-Activity transition, a viewer-only dynamic return
   duration cannot be made reliable: the grid's `sharedElementReenterTransition`
   is installed before the user ever drags. A dynamic duration would make the two
   activities disagree about the same animation.

## D. Viewer layers and toolbar

`renderInteractiveDismissProgress` set the chrome to `1 - p`, and
`hideViewerToolbarWhenGridIsExposed` immediately wrote `mediumViewerAppbar.alpha
= 0` as soon as `scrimAlpha < 1`. That is a single-frame pop, not a transition,
and it fired while the grid toolbar was still ~99% covered by the black scrim.
There was also no single mapping for entry chrome, scrim, chrome exit and the
toolbar cross-fade; each call site applied its own linear arithmetic.

## E. Root page first frame

`GalleryBottomBar.coordinateFirstDraw` committed (and finished the outgoing
activity) on the first draw where `frameState() != null`. The state was a
`String?` produced by request flags as much as by the UI:

- `MainActivity`: `mIsGettingDirs || categorizedLoadActive -> "loading"`. The
  Albums root had **no loading indicator at all**, so the acknowledged "loading"
  frame could be the bare page background. A root switch into Albums could
  therefore retire the source on a blank frame.
- `MediaActivity`: `mediaHeaderProgress?.isShown -> "loading"` is a real
  indicator, but it was still treated as a handoff frame, and the root handoff
  could finish the source before the cached preview or the empty/error state was
  published.

There was no distinction between real content, a real empty/error state, a
visible loading frame and a blank frame; and no bound specific to "loading only"
beyond the global 5 s navigation lock, which does not finish the source.
