# 36 — Phase 2C 实施与交接

日期：2026-11-05。仓库 `Pew2018/Gallery`，仅更新已有分支 `refactor/media-pipeline-stability`；起始远程 HEAD `2e6165dec230630a08a06c08d55baeef3bc5d251`。本阶段报告必须以最终提交和 Actions 实际结果更新本文件。

## 1. 实施范围

| 文件 | 改动 |
|---|---|
| `app/src/test/kotlin/org/fossify/gallery/helpers/DirectoryPresentationTest.kt` | 添加同数量不同 path 检测、20 个变化的最终成员/排序回归，以及 100/500/1,000/5,000 合成目录的生产排序/去重/可见集合/搜索中位数计时 |
| `app/src/test/kotlin/org/fossify/gallery/databases/GalleryDatabaseQueryPlanTest.kt` | 添加 Room v11 的 500 行相同媒体重复批量写入测量、metadata 更新断言与 favorites 关系完整性断言 |
| `docs/media-engine-v2/34-phase2c-album-publication-audit.md` | 主页面目录扫描/发布调用链、现有保护、频次公式、风险分级与测试边界 |
| `docs/media-engine-v2/35-phase2c-room-write-and-index-evaluation.md` | Room 写入/DAO 调用点、已知语义、synthetic REPLACE 测量边界与 Migration 准入建议 |
| `docs/media-engine-v2/README.md` | 加入 Phase 2B/2C 导航 |
| `docs/media-engine-v2/18-current-media-pipeline-roadmap.md` | 更新当前阶段状态与 Phase 2C 结论 |
| `docs/media-engine-v2/06-progress-and-change-log.md` | 记录本阶段实施、测试和交付 |
| `docs/media-engine-v2/36-phase2c-implementation-and-handoff.md` | 本阶段交接 |

## 2. 生产代码决定

没有改动生产 Kotlin、Room Schema/版本/Migration、查询、Adapter 发布时序、OxygenOS Classic UI、Viewer、权限或缓存。源码证据说明中间发布只由真实变化触发、Adapter 有内容相等短路、Room 每个已缓存目录只出现一次整批写入口；但不存在新版设备的同 request 工作量、Diff 丢弃数、Room 重复行数/时间、主线程预算或滚动锚点测量。因此不能负责任地判定哪段当前是真实热点，也不能安全跳过权限验证、媒体更新或缓存写入。

未实施发布合并、纯计算线程迁移、Room 写前查询或新增索引。该决定满足“只对证据充分的热点修改代码”的门槛；尚未实现的生产性能候选进入真机测量清单。

## 3. 测试与当前本地验证状态

新增测试：

- `DirectoryPresentationTest.sameSizedCatalogWithDifferentMembersIsNotAnIdenticalPresentation`
- `DirectoryPresentationTest.twentyChangedCardsRetainEveryPathAndCurrentSortOrder`
- `DirectoryPresentationTest.presentationPreparationBenchmarkReportsSyntheticDirectoryScales`
- `GalleryDatabaseQueryPlanTest.repeatedUnchangedBatchWriteAndMetadataUpdatePreserveRowsAndFavoriteRelations`

执行 `./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.DirectoryPresentationTest --tests org.fossify.gallery.databases.GalleryDatabaseQueryPlanTest` 时，Gradle Wrapper 因当前执行环境无法访问 `services.gradle.org` 下载 Gradle 9.5.1，在测试启动前失败；尚无本地编译/测试结果。完整 `:app:testFossReleaseUnitTest`、ARM64 assemble、APK 元信息/签名/ABI 检查均须由本提交 GitHub Actions 重新执行后，方可在此补录结论。Phase 2B 的 893 项成功结果不是本提交的验证。

## 4. 性能数据解释

两项测试只提供合成 JVM/Robolectric 运行测量。目录 benchmark 统计生产纯数据 helper 的排序、去重、可见集合和搜索过滤；不代表 Android 主线程帧时延。Room 样本测 500 行单次重复写；不代表 Pixel 文件数据库或重复发生频率。没有新版真机同条件基准，不计算百分比。

## 5. 当前验收状态及后续准入

- 正确性保护：生产扫描 epoch、终态 gate、权限校验、Diff generation、滚动延期、收藏关系、Room v11、`.nomedia`、Viewer 既有逻辑未修改。
- Phase 2D 索引：**暂不进入正式 Migration**。缺真实设备 query frequency/latency、执行计划、行分布、索引空间与写入成本成对数据。
- Phase 3 缓存/增量：**暂不引入第二缓存或永久媒体索引**。先用设备诊断判断重复 IO/Room 写入在哪些真实请求可复现，并确立可靠失效及权限边界。
- 真机待验：Pixel 8 Pro 新版诊断的 `C/V/N`、缓存媒体写 rows/time、PhotoProfile/HOME 重叠写、per-request submit/commit/dropped diff、dedupe/sort/filter 主线程 ms、scroll top path/offset、排序/搜索并发、`.nomedia`、SAF、保护目录、删除/移动/回收站交错和 Viewer 连续浏览。

最终 Git SHA、Actions URL、完整 suite 数、ARM64 APK artifact ID、包名/version/ABI/signature、APK 哈希与 CI 日志性能样本：待最终 CI 完成后补入。
