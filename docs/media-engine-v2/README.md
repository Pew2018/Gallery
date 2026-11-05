
**Phase 1.5 最终CI交付**：源码 `026a943a445619407134394bf434b753c65695e2`；[Run41975453251](https://github.com/Pew2018/Gallery/actions/runs/41975453251) completed/success；完整886项单测、ARM64构建、包名/ABI/v2签名与实际下载哈希核验通过；Artifact13391393642。Pixel连续浏览和性能前后复测仍PENDING_DEVICE，不等于所有P0真机验收通过，不启动原阶段2。见[27](27-phase1-5-validation-and-handoff.md)。

## Phase 2A — 元数据、排序与扫描热点（2026-11-05）

- [28 — 热点审计与性能证据](28-phase2a-hotspot-audit.md)：复核Phase 1.5保护机制，分清MediaStore候选、磁盘/SAF验证、Room证据和待测数据。
- [29 — 实施与回归边界](29-phase2a-implementation-and-regression.md)：普通完整文件夹排序复用实现与回归范围。
- [30 — 结果与交接](30-phase2a-results-and-handoff.md)：最终SHA、完整FOSS Release测试、ARM64 Artifact、设备缺口与下一阶段准入。

Phase 2A验证：应用源码`dcd41024e06451eb7a46d28db18a84157cd5102b`；[Run 41983357685](https://github.com/Pew2018/Gallery/actions/runs/41983357685) success，889项完整测试、ARM64 APK Artifact ID 13395436120。只处理有直接代码证据的完整普通文件夹排序重载；媒体实体验证和`.nomedia`补扫继续运行。没有可审计的Room query plan或修复后设备计时，因此不修改Schema，也不报告性能提升数字。Phase 1.5真机验收仍为`PENDING_DEVICE`，详见[30](30-phase2a-results-and-handoff.md)。

# Gallery 媒体管线文档索引

> **当前唯一执行依据**：[18 — 现行媒体索引/加载/排序与稳定性路线图](18-current-media-pipeline-roadmap.md)。Phase 0、1、1.5与本次Phase 2A的CI/ARM64交付均已完成；设备性能与Phase 1.5 Viewer复测仍为`PENDING_DEVICE`。当前阶段文档见[28–30](28-phase2a-hotspot-audit.md)。

## 现行任务与验收

1. [18 — 总路线图](18-current-media-pipeline-roadmap.md)：目的、职责、问题事实 F/风险 R/未测 U、阶段 0–5 与退出条件。
2. [05 — 实施细则](05-minimal-scan-sort-display-refactor.md)：名称保留旧链接兼容，**正文已改为新计划**。
3. [04 — 验证与迁移准入](04-validation-and-migration-readiness.md)：基线字段、正确性/设备矩阵、EXPLAIN 和 Room Migration 门槛。
4. [03 — 兼容契约](03-compatibility-contracts.md)：权限、.nomedia、收藏/备份、配置、OxygenOS 风格及 Viewer 不变量。
5. [01 — 历史数据流](01-current-dataflow.md)、[02 — 历史风险登记](02-evidence-and-risk-register.md)：固定旧 SHA 的事实资料，应在最新 HEAD 重新核对。
6. [19 — Phase 0 埋点审计](19-phase0-instrumentation-audit.md)：已有能力、调用链、修正的 Release 过滤盲点及未覆盖项。
7. [20 — 指标与测试计划](20-phase0-metrics-and-test-plan.md)：事件字段、B01–B20 设备采集方法与统计口径。
8. [21 — Phase 0 结果与交接](21-phase0-results-and-handoff.md)：实际改动、测试、Actions、APK 与真机待采集状态。

## 历史修复及构建资料（非现行执行指令）

- [06 — 旧轮次进度与日志](06-progress-and-change-log.md)：顶部已更新新阶段状态，后文历史日志保留。
- [07 — 排序优化实施](07-minimal-refactor-implementation.md)、[08 — CI 验证](08-minimal-refactor-verification.md)：旧阶段实录。
- [09 — 刷新生命周期](09-refresh-publication-stability.md)、[10 — 滚动/显示](10-display-scroll-stability.md)、[11 — 收藏/Viewer/排序](11-favorites-viewer-sorting-fixes.md)、[12 — Photos 会话](12-seamless-viewer-order-scroll-sorting.md)。
- [13 — 部分目录边界](13-final-viewer-partial-boundaries.md)、[14 — 批量删除](14-progressive-loading-batch-delete-crash.md)、[15 — 全屏删除](15-photo-viewer-delete-continuity.md)、[16 — 混合锁定来源](16-locked-mixed-profile-viewer-continuity.md)、[17 — 删除恢复锚点](17-photo-delete-restore-anchor.md)。

旧文中的“唯一 fix 分支”“当前阶段”“下一步”“未合并 main”“人日粗估”仅限原任务时间点，不能覆盖新计划。保留原始修复/CI 数字以便追溯，不推断真机已经无闪烁、索引不停或错图问题。

当前 `main` SHA `49e799eceab00414ef074cbf71b7f0a35133b669` 的 [Actions Run 41939577702](https://github.com/Pew2018/Gallery/actions/runs/41939577702) 成功（完整 FOSS Release 单元测试与 ARM64 校验）；这不是新阶段的设备性能验收。

## Phase 1 — 生命周期可靠性与发布正确性

- [22 — 生命周期审计](22-phase1-lifecycle-audit.md)：五条请求链、F/R/U、既有保护、扫码/权限/诊断终态。
- [23 — 定点实施和回归测试](23-phase1-implementation-and-tests.md)：修改范围、完整性映射、T01–T15 覆盖与限制。
- [24 — 结果与交接](24-phase1-results-and-handoff.md)：CI/Artifact 证据链、设备矩阵与阶段 2 准入。

阶段 1 自动化测试已由受测源码 `5b75cb2a5c7a96021f35013292a5479436f87f36` 的成功 CI 核验；全部真机权限、并发和性能场景仍为 `PENDING_DEVICE`。旧 01–21 为独立历史/阶段 0 事实，不得将 0 的 Run 当成 1 的构建证明。

## Phase 1.5 — Pixel 8 Pro Viewer 和渐进发布

- [25 — 完整设备日志与F/R/U根因](25-phase1-5-device-log-root-cause-audit.md)
- [26 — 函数修复、目录协议与测试边界](26-phase1-5-viewer-consistency-fixes.md)
- [27 — CI/ARM64交付与PENDING_DEVICE矩阵](27-phase1-5-validation-and-handoff.md)

本阶段不代表进入原阶段2；现有main与历史构建证明保持独立。
