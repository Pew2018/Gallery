# 04 — 当前基准采集、验证矩阵与安全迁移准入

> 2026-11-05 按 [18 — 现行路线图](18-current-media-pipeline-roadmap.md) 更正；旧 S0–S7 全新引擎/双实现迁移方案已废止，不再当作执行任务。`main` 静态基线 `49e799eceab00414ef074cbf71b7f0a35133b669`。**以下均为计划，不代表本轮已有测试结果。**

## 1. 阶段 0：必要的性能与正确性证据

必须记录 APK SHA、设备型号/SDK、存储授权、媒体和文件夹数量、匿名 scope/request、sourceType、queueWaitMs、cacheHit、MediaStore queryCount/queryMs、directory enumerate/statCount/ioMs、Room read/write rows/ms、sortCount/sortMs、stagePublishCount、Adapter submit/commit/drop/bindCount、topPathHash/offsetPx、firstVisibleMs、completeMs、heap/GC、Viewer firstFrame/boundaryMs 和终态。

推荐在现有 `GalleryDiagnosticLogger` 手动启用并脱敏的前提下做按请求摘要，避免默认给每个文件写大量 Release 日志。`GalleryPerfTrace` 当前仅允许部分前缀进入 Release，不应假定全量计数已存在。

```csv
apk_sha,android_sdk,permission_mode,case_id,media_count,folder_count,scope_hash,first_visible_ms,complete_ms,query_count,enumerate_count,room_write_rows,sort_count,adapter_commits,bind_count,anchor_shift_px,scan_state
```

同设备/同数据集至少重复 10 次，冷启动、热打开、手动刷新和回前台分别统计 p50/p95。无基线不得宣称“秒开”“提升 xx%”。

## 2. 核心测试矩阵

| 场景 | 必须满足 |
|---|---|
| 普通、.nomedia 文件夹新增/删除、主页面与配置 | 数量/路径最终一致；空 MediaStore 不能判定文件被删除 |
| 收藏、回收站、多来源照片配置 | 保留动态关系、路径去重和完整性；受保护来源不泄漏 |
| 连续 10 次刷新、切页、取消、后台恢复 | 旧请求拒绝发布，新请求终态明确，进度不永远停留 |
| 扫描中删除、移动、改名及撤权 | 不复活旧照片，失败不视为成功，授权不可被缓存冒用 |
| 排序、分组、搜索、随机、时间/大小 ties | 网格与全屏路径顺序一致；随机会话不无故打乱 |
| 5k/30k/100k 高速 fling、全屏往返 20 张 | anchor 正确、缩略图不绑错、部分窗口不假报 END |
| Android 8/11/14/16，All-files/受限/SAF/离线 | 正确错误/部分状态、允许安全回退，不误删缓存 |
| 老版本升级、设置/收藏/配置备份恢复 | 不丢收藏关系、配置 ID、文件夹规则和用户设置 |

现有测试入口包括 `GalleryRefreshRegressionTest`、`NoMediaCategoryRefreshTest`、`FolderMediaScanTest`、`MediaAdapterStabilityTest`、`PhotoProfileMediaLoadingTest`、`PhotoViewerProgressivePagerTest` 等；必须核对最新源码的真实断言，不得把存在测试文件当作实机已验收。

## 3. 各阶段退出条件

0：数据及可复现/待测结论可追踪。1：终态/删除/失权回归和 P0 不再出现于覆盖场景。2：EXPLAIN、SQL/排序基线前后可比、顺序/备份回归。3：可信缓存复用和外部变更/.nomedia 正确。4：同 path/顺序/版本的 Grid/Viewer、滚动/删除稳定。5：完整 FOSS 单测+ARM64、设备/权限/100k 压测、性能前后对照且标出残留风险。

**数据库条件**：先用 `EXPLAIN QUERY PLAN` 评估 `MediumDao` 的 `parent_path`、`deleted_ts` 过滤与批量写入成本；如测量证明索引有效，做显式 Room Migration、旧库/收藏/配置/备份升级验证，绝不 destructiveMigration。不预设新永久媒体索引、影子双引擎或固定工期。

## 4. 本轮状态

只更正文档；没有执行新 Unit、真实设备 Profiling、数据库迁移或 APK 构建。历史 CI 成绩与旧计划的人日估算不计入新阶段验收。
