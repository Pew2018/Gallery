# 33 — Phase 2B 实施与验证交接

日期：2026-11-05。仓库 `Pew2018/Gallery`，唯一允许分支 `refactor/media-pipeline-stability`。执行前重新读取远程 HEAD：`ad8155c73bcd542e9e565d83cb6444e9679b58bf`，与用户提供的历史参考 SHA 一致；未 reset、未创建分支/PR、未修改 main。

## 1. 修改概要

- `MediaSortOperationGate` 为每个普通物理目录排序选择分配独立单调代际。刷新/新 request、Activity 暂停使旧 task 失效；发布时仍重验当前 request、folder、sorting、folder unlock token 和 `FolderMediaReadResult.accessCurrent()`。后台失败/拒绝时，若请求仍有效则回退到原 `getMedia()` 链路。
- `MainActivity.setupAdapter()` 保持 UI 工作所在主线程、Adapter Diff 和滚动逻辑不变。已有总准备计时外，诊断开关可记录去重、目录排序、可见项/搜索筛选的分段时长；首页排序新增请求到正确当前设置提交的耗时。
- `GalleryDatabaseQueryPlanTest` 使用真实 Room `GalleryDatabase` 当前 schema 建合成 in-memory v11 库，数据量 100/5,000/30,000/100,000。测试检查 path/parent/favorites query plan、复数目录/媒体成员、收藏关系、删除项筛选和数量，并输出单次合成 DAO 查询耗时。
- 未改数据库 schema、migration、查询、索引、备份、viewer、`.nomedia` 或 OxygenOS UI。主页面日期/大小必要扫描、物理文件的实体/可读校验和 SAF 权限验证保留。

## 2. 验证结果

| 检查 | 结果 |
|---|---|
| 本地 `git diff --check` | 待最终提交前记录 |
| 本地定向 Gradle | 未启动；Wrapper 下载 Gradle 9.5.1 报 `Network is unreachable` |
| 定向竞态与 Room 测试 | 由当前提交的 GitHub Actions 执行并记录下方 |
| 完整 `:app:testFossReleaseUnitTest` | 待 Actions |
| ARM64 FOSS Release、签名、包名、ABI | 待 Actions |
| Room query plan | Robolectric 合成数据库；待提取测试输出；不是 Pixel/user DB |
| Pixel 8 Pro / Android 16 | `PENDING_DEVICE` |

### GitHub Actions

待提交后触发 `Arm64 FOSS Release APK (non-publishing)` 工作流；不能将 pending run 或单独的 workflow dispatch 当作通过。

- Run / source SHA：待记录
- 完整测试数、failures/errors/skipped：待记录
- 测试 artifact 与 APK artifact：待记录
- APK package/version/ABI/signature/digest：待记录
- Actions job `GalleryDbPlan` 计划与合成单次时延：待记录

## 3. 性能与风险边界

普通文件目录排序快捷路由的源码可证明：有效 COMPLETE snapshot 的重排没有启动新扫描；任务量从整条媒体加载路径收窄为 snapshot copy、现有排序器和 Adapter Diff。没有新版真机计数器/计时数据，因此不报告具体磁盘调用数、毫秒或百分比。

主页排序仍可能扫描以更新统计。补充的计时只收集证据，不声称已降低主线程时间。Room 计划与延迟是合成 Room schema/SQLite 的测试数据，用户数据库表行数、调用次数及 Pixel 时延未知。PhotoProfile 5k、SAF、多来源补扫、`.nomedia` 和真机权限撤销仍 `PENDING_DEVICE`。

## 4. 下一阶段准入

先取得本阶段完整 CI/APK 结果；再用同一 Pixel、同一授权、同一媒体集采集普通目录排序、100/5k/30k+主页目录、5k PhotoProfile、SAF/`.nomedia` 的请求级计数、主线程分段耗时、Diff、滚动 top path/offset。只有真实用户库 `EXPLAIN QUERY PLAN` 与 query frequency/latency 支持，才另开带 Migration 的数据库优化任务；需要先量化索引空间和媒体批量写入成本。不从本阶段的合成 `SCAN` 或单次 timing 推导用户性能收益。
