# Gallery 媒体管线文档索引

> **当前唯一执行依据**：[18 — 现行媒体索引/加载/排序与稳定性路线图](18-current-media-pipeline-roadmap.md)（2026-11-05，`main` `49e799eceab00414ef074cbf71b7f0a35133b669`）。新工作分支：`refactor/media-pipeline-stability`。本轮仅整理文档；新阶段 0–5 尚未实施。

## 现行任务与验收

1. [18 — 总路线图](18-current-media-pipeline-roadmap.md)：目的、职责、问题事实 F/风险 R/未测 U、阶段 0–5 与退出条件。
2. [05 — 实施细则](05-minimal-scan-sort-display-refactor.md)：名称保留旧链接兼容，**正文已改为新计划**。
3. [04 — 验证与迁移准入](04-validation-and-migration-readiness.md)：基线字段、正确性/设备矩阵、EXPLAIN 和 Room Migration 门槛。
4. [03 — 兼容契约](03-compatibility-contracts.md)：权限、.nomedia、收藏/备份、配置、OxygenOS 风格及 Viewer 不变量。
5. [01 — 历史数据流](01-current-dataflow.md)、[02 — 历史风险登记](02-evidence-and-risk-register.md)：固定旧 SHA 的事实资料，应在最新 HEAD 重新核对。

## 历史修复及构建资料（非现行执行指令）

- [06 — 旧轮次进度与日志](06-progress-and-change-log.md)：顶部已更新新阶段状态，后文历史日志保留。
- [07 — 排序优化实施](07-minimal-refactor-implementation.md)、[08 — CI 验证](08-minimal-refactor-verification.md)：旧阶段实录。
- [09 — 刷新生命周期](09-refresh-publication-stability.md)、[10 — 滚动/显示](10-display-scroll-stability.md)、[11 — 收藏/Viewer/排序](11-favorites-viewer-sorting-fixes.md)、[12 — Photos 会话](12-seamless-viewer-order-scroll-sorting.md)。
- [13 — 部分目录边界](13-final-viewer-partial-boundaries.md)、[14 — 批量删除](14-progressive-loading-batch-delete-crash.md)、[15 — 全屏删除](15-photo-viewer-delete-continuity.md)、[16 — 混合锁定来源](16-locked-mixed-profile-viewer-continuity.md)、[17 — 删除恢复锚点](17-photo-delete-restore-anchor.md)。

旧文中的“唯一 fix 分支”“当前阶段”“下一步”“未合并 main”“人日粗估”仅限原任务时间点，不能覆盖新计划。保留原始修复/CI 数字以便追溯，不推断真机已经无闪烁、索引不停或错图问题。

当前 `main` SHA `49e799eceab00414ef074cbf71b7f0a35133b669` 的 [Actions Run 41939577702](https://github.com/Pew2018/Gallery/actions/runs/41939577702) 成功（完整 FOSS Release 单元测试与 ARM64 校验）；这不是新阶段的设备性能验收。
