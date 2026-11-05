# Photos profile deletion during indexing: stable restoration anchor

> **历史缺陷复现及修复记录（非当前任务）**：原文的目标分支、执行规则、完成结果和下一步仅适用于当时提交。当前媒体索引/加载/排序/稳定性工程使用 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md)。保留旧数据、旧修复和测试结果作为证据，不把历史 Actions 通过视作新阶段性能/真机验收。

Date: 2026-11-04. Branch: `fix/photo-viewer-five-item-boundary`.
Baseline HEAD: `4e68872e464e2c97ba2564322d36082e89295fb6`.

## Real-device report

While a custom Photos profile is still indexing, open an image, swipe, and immediately delete the displayed image. The prior crash has stopped, but the viewer may jump to an image far from the last visited one.

## Confirmed source paths and failure modes

1. `ViewerDeletionNavigationPolicy.resolve()` previously selected `freshMedia[0]` on a complete scan if all identity-based matches failed. A complete list can contain thousands of images, so index zero is not a valid deletion neighbor.
2. `refreshViewPager()` previously passed the now-deleted current path as the next scan anchor; both `PhotoProfileMediaCache.preview()` and the catalog store's partial windows may fall back to index zero when the requested anchor is missing.
3. `recoverPhotoDeletionFromLoadResult()` previously preferred mutable `lastVisitedPath` over the path saved at deletion, and also accepted `getCurrentPath()` without proving a user-driven navigation since the delete request. A pager/list reindex must not count as a user gesture.
4. The recovery routine could prefer a catalog partial window even if it did not contain the chosen restoration path.
5. The broad `needsUnlock` guard could block recovery from separately validated public media when a different Photos profile source remained locked.

These are verifiable control-flow risks; without the exact device Logcat and publication trace, no one specific branch is claimed as the proven runtime cause.

## Implemented behavior

- Freeze the last genuine visited path, navigation epoch and swipe direction when deletion is confirmed. Only real swipe gestures, explicit viewer navigation, and resolved boundary navigation update that history.
- Recenter the new Photos staged scan and the process-local producer focus on a historical path or a bounded old-grid neighbor, never the deleted file.
- Choose restoration by verified media path: a user-confirmed page selected after deletion; the frozen last-visited page; then up to eight old-grid/local neighbors per direction, preferring the side corresponding to the last user swipe.
- Never pick the new catalog's first photo solely because a stage reports COMPLETE. With a partial or still-running catalog, keep the existing pager until a verified target is available. If a fully authorized COMPLETE catalog contains no trustworthy neighbor, close the fullscreen viewer instead of showing an unrelated photo.
- A validated partial catalog that omits the desired target is not used as the replacement window. Take a centered window from the stage result and validate it before the existing pager adapter is updated.
- Retain path-scoped access validation, staged scanning, mutation generation/revision fences, and the same ViewPager adapter/fragment lifecycle. A locked source may not block independently authorized public media.

## Regression tests added/updated

`ViewerDeletionNavigationPolicyTest` covers forward and reverse swipe history, direction fallback, deletion from a one-item window, a 5,000-item list with no known neighbor, bounded-grid exhaustion, stale programmatic page changes, restoration anchor selection, and incomplete/complete empty-catalog distinctions.

`PhotoViewerProgressivePagerTest` retains the adapter/Fragment continuity regression, updated to use the identity-based policy without index-zero fallback.

## Boundaries and verification

No emulator workflow was restored. The existing ARM64 FOSS workflow executes host unit tests and then builds the installable APK on changes under `app/**`. The device-specific rapid swipe/delete timing, system MediaStore/SAF authorization prompt and visual flicker still require on-device confirmation; static code and host tests cannot establish that every OEM animation is flawless.
