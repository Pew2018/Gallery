# Stage 3: media and directory grid stability

Date: 2026-11-13 (Hong Kong time)

## Audit

- Start SHA: `51523d52a5730a0ae8207e73ee7dc9a1e4449e9f`.
- Media refreshes compare displayed content before dispatching adapter updates. Identical snapshots update the adapter’s internal metadata without row notifications. Plain-media sorts use the fast reorder path; grouped data keeps the identity-aware DiffUtil path. Bursts coalesce to the newest pending snapshot, and stale requests cannot commit.
- Thumbnail binds keep their current drawable and request token when the identity is unchanged. A new identity replaces the request token before canceling old loads; obsolete callbacks cannot mark a new row loaded. Thumbnail crossfade is disabled.
- `DirectoryAdapter.updateDirs()` snapshots inputs, skips unchanged content, and applies path-identity DiffUtil updates. `DirectoryGridItemAnimator` suppresses background move/change animations; only move IDs explicitly granted by a user drag can animate. A subsequent snapshot clears unused reorder permissions.
- Reviewed remaining `notifyDataSetChanged()` and range-change calls. They are scoped to user-facing display preferences, reorder-mode/menu actions, confirmed replacement strategies, or layout/column changes; no unconditional background scan path requires removal.
- No new production defect was confirmed in this stage, so adapter and animator code was left unchanged.

## Validation

All targeted tests ran in `/private/tmp/Gallery-stage1-validation` with JBR 21 and the installed Android SDK.

- `MediaAdapterStabilityTest`: 24/24 passed.
- `DirectoryAdapterRefreshTest`: 9/9 passed.
- `DirectoryGridItemAnimatorTest`: 2/2 passed.
- Total targeted: 35/35 passed, 0 failures, 0 skipped.
- Full JVM suite from the same source state: 1,111/1,111 passed across 163 suites.
- ARM64 FOSS Release build from this source state: passed; see [stage 2](02-loading-feedback.md) for APK details.

## Visual/device acceptance

- Pixel 8 Pro Camera, Screenshots, WeChat, `.nomedia`, repeated refresh, sort, add/delete, and scroll-during-refresh matrix: `NOT TESTED`.
- No connected Android device or usable continuous screen recording is available, so no flicker or frame-motion claim is made.
- Stage status: `BUILD_PASSED / DEVICE_PENDING`; the existing code-level protections are not a substitute for visual acceptance.

