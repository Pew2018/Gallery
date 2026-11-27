# Stage 6: viewer gestures and video handoff

Date: 2026-11-13 (Hong Kong time)

## Audit

- Start SHA: `355befb99` (stage 5 commit).
- Photo dismiss is enabled only for an unrotated, unzoomed image and a single pointer. The controller applies touch-slop and vertical-direction bias, declines horizontal intent, restores on cancellation or pointer changes, and chooses one terminal dismiss/restore result on release.
- The Activity reuses the drag progress as the base for settle behavior, takes over an active drag for explicit close, locks pager input during transitions, and avoids starting a competing bounds transition when Back arrives during entry. Existing instrumentation covers repeated horizontal paging, dismiss cancellation/confirmation, return identity, and Back during entry.
- Video handoff binds the poster to a specific active player, waits for `onRenderedFirstFrame` rather than `STATE_READY`, keeps the poster during shared motion, and retains it on playback error/retry. Stale player callbacks are ignored. The video view teardown clears the shared proxy and handoff and releases player/surface resources.
- `ViewPagerActivity` targets the framework back completion path, and the project targets SDK 36. No app-owned predictive-back progress callback is present. This code review does not establish Android 16 predictive animation behavior; that remains part of device acceptance. No specific source defect justified adding a second back-progress animation alongside the existing shared-element transition.
- No confirmed production defect was found in this source review, so stage 6 made no production change.

## Validation

Validation ran in `/private/tmp/Gallery-stage1-validation` with JBR 21 and the installed Android SDK.

- `MediaTransitionGestureControllerTest`: 6/6 passed.
- `VideoPreviewFrameHandoffTest`: 2/2 passed.
- `PhotoTransitionCompletionGateTest`: 2/2 passed.
- `PhotoViewerReturnTargetTest`: 1/1 passed.
- Targeted total: 11/11 passed; 0 failures, 0 skipped.
- `:app:compileFossDebugAndroidTestKotlin`: passed; existing viewer instrumentation compiles.
- `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`: passed (up-to-date for unchanged production sources). The current ARM64 APK is the stage 5 artifact, SHA-256 `d1138619d80443af0a222052a1c83a2869dbbeb8d50fc823ad4ec9d6c41267ef`; unsigned, package `org.fossify.gallery`, version 1.14.1 (32), arm64-v8a only.
- The device instrumentation and video playback scenarios were not run because no Android device is connected.

## Visual/device acceptance

- Pixel 8 Pro swipe, drag-cancel/close, system Back, edge Back, mixed photo/video, and slow-video-first-frame checklist: `NOT TESTED`.
- Android 16 predictive-back gesture progress: `NOT TESTED`.
- Stage status: `BUILD_PASSED / DEVICE_PENDING`.
