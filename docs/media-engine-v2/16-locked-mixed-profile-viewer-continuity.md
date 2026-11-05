# 未解锁混合照片配置的全屏连续浏览修复

> **历史缺陷复现及修复记录（非当前任务）**：原文的目标分支、执行规则、完成结果和下一步仅适用于当时提交。当前媒体索引/加载/排序/稳定性工程使用 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md)。保留旧数据、旧修复和测试结果作为证据，不把历史 Actions 通过视作新阶段性能/真机验收。

日期：2026-11-04。分支：`fix/photo-viewer-five-item-boundary`；基线：`ca15f11daa26811ee96802316851319c8dd6847a`。

## 复现与代码根因

一个自定义照片配置同时选中普通文件夹和受保护文件夹。用户未解锁，但普通照片已经在网格中可见；进入全屏并继续向左右滑动时，触发“未能加载更多照片，请再次滑动重试”。

`PhotoProfileMediaLoader` 的扫描计划已跳过未经授权的受保护来源；`PhotoAccessValidationBatch` 已逐项排除未经授权媒体，仍以 `needsUnlock=true` 表示**其它来源**需要解锁。然而 `PhotoSessionViewerCatalogStore.offerPartial()`、`prepare()` 以此标志拒绝发布**整个已验证结果**；`requestExpand()` 也在已验证候选存在时按该标志失败。全屏因而滞留在五项邻居窗口，扩展失败后可进入重试提示分支。

## 最小修复及授权边界

仅在 `PhotoSessionViewerCatalogStore.kt` 三处移除 `needsUnlock` 对已验证结果的整体拒绝。完成状态现在指“**当前已获授权范围**的目录已扫描完成”，并不意味着所有所选来源均已解锁。保持以下既有边界：

- `PhotoProfileSourceScanPolicy` 扫描前跳过未获授权来源。
- `PhotoPathAccess` 对原始路径、规范路径、祖先路径执行鉴权。
- `PhotoAccessValidationBatch` 对每批/每页候选重新校验，绝不发布未授权媒体。
- `PhotoProfileAccessSessionManager` 的会话失效、`PhotoViewerPageAccessState` 的页面窗口权限继续生效。
- `PhotoProfileMediaCache.offer()` 仍以 `publicOnly` 限制公共缓存复用；不把本次完整目录授权视作持久化凭据。
- 网格中的 `needsUnlock` 提示、解锁入口不变。

## 回归覆盖

- `PhotoProfileMediaLoadingTest.mixedLockedNoMediaAndPublicSourcesStayStableUntilComplete`：普通与明确选中的 `.nomedia` 来源全部可通过 COMPLETE 目录交接；锁定目录照片缺席且不可通过路径取用；原单次中间预览限制保持。
- `PhotoProfileMediaLoadingTest.lockedSourceCannotBlockPublicPartialCatalogExpansion`：使用真实受保护根目录与未解锁会话，先逐项验证公开预览，再请求正反方向扩展；受保护索引候选不能进入发布结果。
- `PhotoSessionViewerCatalogStoreTest.partialStagesCannotClaimCompletenessButValidatedAccessibleSubsetCanHandoff`：仍禁止把 INDEXED 当作 COMPLETE；对经过验证的、仅含已授权媒体的 COMPLETE 不因其它来源锁定而拒绝交接。

## 验证状态

代码更改提交后由 `arm64-foss-release.yml` 自动运行完整 FOSS Release 单测并编译 arm64-v8a APK。此处不预先宣称 CI 或真机通过，验证需查看实际运行结果。模拟器工作流此前已经归档并禁用，本次不恢复。
