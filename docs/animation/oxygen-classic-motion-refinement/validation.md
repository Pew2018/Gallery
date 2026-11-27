# OxygenOS Classic motion refinement — validation

## 1. Environment and commands

- Host: macOS, `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
  (Robolectric API 36 refuses Java 17), Android SDK 36,
  `GRADLE_USER_HOME` and `ROBOLECTRIC_DEPENDENCY_DIR` pinned to the workspace
  caches, `--offline`.
- No Android 16 emulator or Pixel 8 Pro was used for this round. JVM/Robolectric,
  XML resource loading and Gradle compilation are the evidence; on-screen claims
  stay in §5.

Commands:

```
./gradlew --offline testFossDebugUnitTest \
  --tests "org.fossify.gallery.helpers.PredictiveBackCloseAnimationTest" \
  --tests "org.fossify.gallery.helpers.OxygenClassicActivityMotionTest" \
  --tests "org.fossify.gallery.helpers.ViewerVisualMotionTest" \
  --tests "org.fossify.gallery.helpers.ViewerBackGestureTest" \
  --tests "org.fossify.gallery.helpers.ViewerReturnProgressTest" \
  --tests "org.fossify.gallery.helpers.GalleryRootTransitionTest" \
  --tests "org.fossify.gallery.helpers.PhotoViewerInitializationTest"

./gradlew --offline testFossDebugUnitTest        # full FOSS debug suite
./gradlew --offline compileFossDebugKotlin
./gradlew --offline assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a
```

## 2. Targeted result (this round)

74 tests, 0 failures:

| class | tests | covers |
|---|---|---|
| `PredictiveBackCloseAnimationTest` | 8 | CLOSE style still selects the custom branch; CLOSE-enter identity; 450 ms pair; the new alpha interpolator is a legal monotonic path; alpha is front-loaded (≤0.25 at 20%, 0 by 30%), exact at 0/1, and clear while the page is still translating; negative guard against an identity no-op |
| `OxygenClassicActivityMotionTest` | 7 | entry/return clock split and symmetry per direction; folder-cover clock unchanged; window/shared-element contract unchanged |
| `ViewerVisualMotionTest` | 5 | endpoints/monotonicity of entry chrome, return chrome, scrim; toolbar cross-fade cannot pop to zero at 1% progress and is gone by 20% exposure; edge bias direction/14dp bound/zero at progress 0/continuity on takeover |
| `ViewerBackGestureTest` | 4 | left edge `+1`, right edge `-1`; clamping; progress ignored outside a gesture; cancel never commits; ownership released on commit |
| `ViewerReturnProgressTest` | 5 | resumed progress consumes only the remaining interval; invalid values cannot move backwards; settle duration bounds |
| `GalleryRootTransitionTest` | 14 | content/empty/error commit; blank never commits; loading cannot retire the source before its bounded deadline; content arriving earlier commits earlier; stale/duplicate callbacks ignored; disposal cancels callbacks; no window fades |
| `PhotoViewerInitializationTest` | 31 | entry stages and fallback; release keeps the live scale/translation/scrim/chrome; predictive takeover preserves the inherited translation; edge bias cancels to zero; toolbar cross-fade instead of a pop; renderer swap during drag; no double terminal/lease release |

New/rewritten tests in this round: **+2 classes** (`ViewerVisualMotionTest` with
5 tests, `ViewerBackGestureTest` with 4) and **6 new test methods** in existing
classes (`PredictiveBackCloseAnimationTest` +2, `GalleryRootTransitionTest` +2,
`PhotoViewerInitializationTest` +2), plus the rewritten clock contract in
`OxygenClassicActivityMotionTest` and updated chrome/entry expectations. Total
new test methods: 15.

Every assertion above evaluates behaviour through the production code path, e.g.
`Animation#getTransformationAt` (the exact Shell call, via reflection),
`ViewerVisualMotion` pure functions, the real `ViewerBackGesture` callbacks and
`GalleryBottomBar.coordinateFirstDraw` with a Robolectric view tree. The alpha
test is not an XML text match.

## 3. Full suite

`./gradlew --offline testFossDebugUnitTest` (FOSS debug, JDK 21):

```
189 test classes, 1358 tests, 0 failures, 0 errors, 0 skipped
BUILD SUCCESSFUL in 2m 59s
```

The earlier environment-only `DefaultSdkProvider` failures documented in
`docs/animation/predictive-back-root-cause/05-test-results.md` did not reproduce
once the tests ran on JDK 21 with the pre-populated `robolectric-deps`
(`android-all-instrumented-8.0.0 … 16`). No test in the changed area is skipped.

## 4. What the tests cannot prove

- The Shell's actual surface composition, rounded corners, scrim colour blending
  and the real 450 ms post-commit clock on a Pixel 8 Pro.
- The perceived fade shape of the alpha PathInterpolator on hardware vsync.
- Whether the 160 ms entry expansion reads better than 120/180 ms.
- Whether the 14 dp predictive-back bias feels "light direction feedback" rather
  than a sideways fly-out.
- Whether the Viewer toolbar / grid toolbar cross-fade is free of visible double
  text at real capture time.
- Whether the Albums loading strip appears before the first visible frame on a
  real root switch (Robolectric proves the state machine, not the compositor).
- Real Glide decode latency for the selected media.

## 5. Performance reporting

No new device measurement was possible this round. The only log available in
the task workspace is from an older build (2026-11-23 11:27); its entry spans are
used in `root-cause.md` as shape evidence, not as a before/after benchmark.

| metric | previous device baseline (task) | expected effect of this round | status |
|---|---|---|---|
| request → shared animation start | median ~238 ms | one background round trip removed from before the first Glide request; the dominant full decode is unchanged | **not re-measured** (needs device) |
| shared entry animation | median ~133 ms (configured 120) | configured 160 ms | **not re-measured**; 160 ms is a candidate per the brief |
| request → entry complete | median ~383.5 ms | ≈ +27 ms of animation minus the removed round trip | **not re-measured** |
| shared return animation | median ~133 ms (configured 120) | unchanged (120 ms) | **not re-measured** |
| return request → grid target frame | median ~242 ms | unchanged | **not re-measured** |
| shared transition completion | 8/8 sample | no new fallback path; the added fallback is the already existing one | **not re-measured** |
| return fallback | 0 | only on a genuine missing/replaced proxy as before | **not re-measured** |

The new `renderer_request_started` / `fragment_created` / `entry_pre_draw`
events make the next device sample able to split Activity creation, renderer
request, decode and pre-draw without adding diagnostics to the default path.

## 6. Device acceptance checklist (Pixel 8 Pro / Android 16)

Record screen at 120 fps (developer options → show refresh rate) and keep
`Gallery-Diagnostics` enabled while doing the entries below.

1. Ordinary page back, left edge: real parent page visible, no black/white
   background, no doubled toolbar/list text, no visible jump at commit.
2. Ordinary page back, right edge: same, plus compare the post-commit direction
   with the system Settings app (both should move the closing page the same way).
3. Predictive back cancel on both edges: page fully restored, including status
   and navigation bars.
4. Toolbar back (non-gesture) into a parent page: the page must not hard-cut or
   linger invisibly.
5. Viewer entry from folder, Favorites, All Photos: thumbnail → full-screen must
   look like one expansion (no full-screen flash first); repeat 8× to compare the
   `entry_requested → shared_animation_started` span with the old median.
6. Viewer entry, cold vs warm: confirm the `renderer_request_started` reason is
   `grid_signature` for grid launches and measure the decode span.
7. Viewer down-drag release at 25%, 50% and 90% progress: the image must continue
   from the finger frame (no reset/scale jump), and the toolbar must not pop.
8. Down-drag cancel and immediate re-drag before the rebound ends: continuity and
   no double finish.
9. Left and right predictive back inside the Viewer: 14 dp bias, cancel restores
   exactly, commit returns to the correct thumbnail.
10. Cancel a predictive back, then drag: the surface must not be left scaled or
    with faded chrome.
11. Reduced motion (Animator duration scale 0): Viewer still closes, no scaled
    surface/transparent proxy/dead input remains.
12. Root switch Albums → Photos → Favorites → Albums, including a cold open of
    Albums with a large folder DB: no bare-page flash; the 3 dp strip or cached
    rows are visible, and the source activity is retired within the bounded wait.
13. Rapidly tap the same/new bottom tabs while a root switch is in progress: no
    double activity, ripple still works, predictive back from the settled root is
    still correct.
14. Viewer continuous swipe left/right, delete a photo, return to the grid: target
    thumbnail identity/position unchanged.
15. `.nomedia`, protected folder and custom photo profile open/close: no
    regression in access checks or the shared-element identity.

## 7. Known limitations

- `ViewerVisualMotion` timing constants are design tokens chosen without a
  device; they are pinned by tests but still need visual acceptance.
- The entry duration candidate 160 ms is unverified visually.
- The root handoff's 700 ms loading bound is a product trade-off; measure that it
  does not feel like the tab is stuck.
- The Shell's alpha/matrix composition is verified against AOSP sources and the
  AOSP-derived Robolectric framework, not against a physical Pixel 8 Pro.
