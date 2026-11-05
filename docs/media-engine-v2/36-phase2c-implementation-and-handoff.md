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

## 3. 测试与 CI 验证

新增测试：

- `DirectoryPresentationTest.sameSizedCatalogWithDifferentMembersIsNotAnIdenticalPresentation`
- `DirectoryPresentationTest.twentyChangedCardsRetainEveryPathAndCurrentSortOrder`
- `DirectoryPresentationTest.presentationPreparationBenchmarkReportsSyntheticDirectoryScales`
- `GalleryDatabaseQueryPlanTest.repeatedUnchangedBatchWriteAndMetadataUpdatePreserveRowsAndFavoriteRelations`

当前执行环境无法访问 `services.gradle.org`，本地 Gradle Wrapper 未能下载 Gradle 9.5.1，因此没有本地测试结果。GitHub Actions 最终验证源码提交为 `034bd9deee48aaf900956bb896f2152a43e6f05e`，Run [42012246773](https://github.com/Pew2018/Gallery/actions/runs/42012246773) completed/success：完整 `:app:testFossReleaseUnitTest` 为 **897 tests / 0 failures / 0 errors / 0 skipped**；`assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` 成功；APK 包名 `org.fossify.gallery`、versionCode `32`、versionName `1.14.1`、native ABI 仅 `arm64-v8a`，APK v2 签名验证通过。签名证书为临时 CI 测试证书，不证明可覆盖安装。

## 4. 性能数据解释

两项测试只提供合成 JVM/Robolectric 运行测量。GitHub runner 上目录准备 benchmark 3 次取中位数，排序+去重+可见集合+搜索的耗时如下（微秒）：

| 目录行数 | 名称 | 日期 | 大小 | 随机 |
|---:|---:|---:|---:|---:|
| 100 | 1,756 | 835 | 751 | 332 |
| 500 | 2,974 | 1,529 | 1,493 | 1,052 |
| 1,000 | 2,471 | 1,954 | 1,889 | 1,313 |
| 5,000 | 13,910 | 8,426 | 7,997 | 8,725 |

这不是 Android 主线程帧时延，也不是 Pixel 8 Pro 表现。Room v11 的 500 行样本中，一次重复 `insertAll` 调用输入 500 行、测得 3,763 微秒，最终媒体行 500、收藏关系行 50；它不代表设备数据库、批量内部 SQL 次数或多请求重复写频率。没有新版真机同条件基准，不计算性能提升百分比。

## 5. 当前验收状态及后续准入

- 正确性保护：生产扫描 epoch、终态 gate、权限校验、Diff generation、滚动延期、收藏关系、Room v11、`.nomedia`、Viewer 既有逻辑未修改。
- Phase 2D 索引：**暂不进入正式 Migration**。缺真实设备 query frequency/latency、执行计划、行分布、索引空间与写入成本成对数据。
- Phase 3 缓存/增量：**暂不引入第二缓存或永久媒体索引**。先用设备诊断判断重复 IO/Room 写入在哪些真实请求可复现，并确立可靠失效及权限边界。
- 真机待验：Pixel 8 Pro 新版诊断的 `C/V/N`、缓存媒体写 rows/time、PhotoProfile/HOME 重叠写、per-request submit/commit/dropped diff、dedupe/sort/filter 主线程 ms、scroll top path/offset、排序/搜索并发、`.nomedia`、SAF、保护目录、删除/移动/回收站交错和 Viewer 连续浏览。

最终源码 SHA：`034bd9deee48aaf900956bb896f2152a43e6f05e`。Actions Run：[42012246773](https://github.com/Pew2018/Gallery/actions/runs/42012246773)。完整 suite：897/0/0/0。ARM64 APK Artifact：`gallery-foss-arm64-v8a-release`，ID `13407079108`，大小 10,442,642 bytes，SHA-256 `0ea101cf8598234ad512b6e31d3fa3f01bc3f62907ec5ac10f72a242a4f1aa31`。测试报告 Artifact ID `13407213579`。最终提交在 `refactor/media-pipeline-stability`；起始远程 HEAD 为 `2e6165dec230630a08a06c08d55baeef3bc5d251`。CI 汇总与签名/ABI 细节见 Actions 日志。

本阶段的两次失败 Actions 分别捕获测试集合类型编译错误和 Robolectric/Picasso 全局单例隔离问题，均在最终成功源码之前修复。其间一次 GitHub API 文件传输损坏在 Actions 编译发现后重传；修复后的远程 blob 与本地文件 Git SHA 核对一致。最终 Run 验证的是完整最终源码，而不是失败 Run。
