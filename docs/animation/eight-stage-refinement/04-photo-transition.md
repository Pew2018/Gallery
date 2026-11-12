# Stage 4: photo open and close transition

Date: 2026-11-13 (Hong Kong time)

## Audit

- Start SHA: `fc04f2769296a2b099f81b54c8de0971854939ed`.
- The photo viewer maps the currently selected media path on return, validates the holder’s bound path and loaded thumbnail request, and waits for an offscreen destination to attach or uses the bounded fallback.
- The viewer’s shared surface is content-only. `PhotoFragment` derives its bounds from the loaded drawable’s image dimensions and the active image matrix, rejects unsupported rotation/zoom/animation states, and hides renderers only while the proxy owns the transition. Cleanup restores their previous alpha.
- The proxy uses `FIT_XY`, but its destination bounds come from the same image’s transformed dimensions, preserving that image’s aspect ratio. `MediaTransitionGeometry.fit()` also letterboxes by preserving ratio for video surfaces. No code-level distortion defect was confirmed from this path review.
- No production change was made because there is no visual evidence supporting a specific geometry or timing correction.

## Validation

Tests ran in `/private/tmp/Gallery-stage1-validation` with JBR 21 and the installed Android SDK.

- `MediaSharedSurfaceTest`: 2/2 passed (wide, tall, square, and panorama fit geometry; proxy cleanup and renderer restoration).
- `PhotoViewerReturnTargetTest`: 1/1 passed.
- `PhotoTransitionCompletionGateTest`: 2/2 passed.
- `MediaTransitionGestureControllerTest`: 6/6 passed.
- Total targeted: 11/11 passed, 0 failures, 0 skipped.
- Full JVM and ARM64 release evidence from the same production source is recorded under stage 2; no production code changed in this audit.

## Visual/device acceptance

- Pixel 8 Pro open/close repetitions across landscape, portrait, square, long screenshot, panorama, and high-resolution media: `NOT TESTED`.
- Offscreen, deleted, undecoded, rotation, and rapid reopen scenarios have code/test coverage in part, but no current continuous-frame device recording is available.
- Stage status: `BUILD_PASSED / DEVICE_PENDING`; no claim is made about visual continuity until recorded on the target device.

