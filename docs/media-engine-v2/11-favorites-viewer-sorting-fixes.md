# 四项集中修复：收藏首屏、Photos Viewer 与相册排序

> **历史源码/任务资料（非当前实施指令）**：下文提及的旧工作分支、历史“当前进度”“唯一任务”“下一步”，只适用于所写日期和原固定提交。2026-11-05 起，当前工作遵循 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md) 和 [04 — 验收矩阵](04-validation-and-migration-readiness.md)。保留旧源码证据与原始 Actions/限制供追溯；不能据此声称新阶段 0–5 已完成或获得真机性能数据。

日期：2026-11-04（Asia/Taipei）。唯一分支 `fix/photo-viewer-five-item-boundary`。
开始时通过 GitHub 重新读取的 HEAD：`c6da3c5cfd7c30e3635f625bc644a837badb8018`。

## 源码确认与实际修改

| 用户现象 | 确认的源码问题 | 修改函数与行为 |
|---|---|---|
| 收藏文件夹首屏等待媒体扫描 | `MainActivity.loadFavoriteFoldersWithOverrides()` 在唯一 setupAdapter 之前遍历所有收藏并 getFilesFrom；未缓存目录还 listFiles | 同方法拆为清单与元数据两个顺序阶段。收藏表批量路径/缓存查询、路径去重、隐藏/排除/保护检查之后立即发布卡片；没有媒体查询/子项枚举前置。元数据在原 worker 上继续，用 readFolder 的 COMPLETE/accessCurrent 才替换旧卡片；每至少 8 项且至少 250ms 合并发布，结束强制发布。不删除不可访问收藏；受保护祖先卡片不复用缓存封面/统计。 |
| 扫描期间全屏拖动掉帧 | offerPartial 对已经累计排序的集合再次排序、复制、建索引、通知；consume 对相同 COMPLETE 也 gotMedia；初始 gate 只挡最终 UI | `PhotoSessionViewerCatalogStore.offerPartial()` 仅在缺失旧扩展项目、需要合并排序时重排；窗口相同复用目录且不通知，但 COMPLETE 仍独立 prepare。`ViewPagerActivity.consumePhotoCatalogAdvance()` 拖动中只排队最新查询，idle 消费；同目录只更新边界/授权状态。`MyPagerAdapter.updateMedia()` 同值列表不通知、不重建 Fragment。`MediaActivity.getPhotoProfileMedia()` 进入全屏后停止向不可见网格提交中间 stage，worker/store 继续扫描及授权，最终网格仍同步。 |
| 扫描结束后进入全屏不能左右浏览 | `initContinue()` 首次 onGlobalLayout 无条件 gotMedia(最初单张)；完整目录已先提交但尚未应用时，该回调可增加 publication generation 并取消完整目录任务。prepared COMPLETE 分支随后不主动 consume，可能永久留下单项 Pager | 将原回调拆为 `scheduleInitialViewerPublication()`，登记时捕获 publication generation；只有无新发布且无 Adapter 时才执行初始提交。更新后的 Pager 负责一次性 slideshow 入口，避免版本保护吞掉原幻灯片功能。不以 COMPLETE 本身作为相邻媒体授权，不改五项窗口。 |
| 相册/收藏未按选定排序 | gotDirectories 所有渐进发布 sortDirs=false，Adapter retainOrder 保留旧卡片顺序，只在最终结果排序 | 每个原有批次由 setupAdapter 按当前 directorySorting 排序，去掉 worker 首次重复排序。保留原 8 卡片/1200 媒体等批次，不新增排序器。Diff 的 current 条件核对排序设置，旧排序不能延迟提交覆盖新排序。随机 session rank、自定义顺序、置顶及滚动锚点沿用原实现。 |

这些是源码与回归用例层面的事实/竞态，尚不能证明用户设备只有这些原因。实际果冻感、解码、CPU/IO 竞争与触控帧耗时需要设备 Trace；本环境没有 ADB/模拟器，不报告 FPS、毫秒或提升百分比。

## 状态与安全边界

- 收藏 `hasVisibleManifest` 表示首屏可用；coordinator 仍为 Loading，直到统计结束才 Loaded/Empty/AwaitingAuthentication，部分读取失败为 Error，旧 generation 不能完成新请求。首屏进度指示表示等待首屏；后台处理不再把整个页面表现为未加载。
- 受保护来源、隐藏/排除授权、.nomedia 祖先分类、文件读取 accessCurrent、Photos scope/revision、收藏关系和五项邻近校验仍保留。首次收藏卡片仅安全复用可访问且非受保护祖先的缓存，未知卡片无封面/计数。不可访问或不完整扫描不抹掉可信卡片，不自动删除收藏记录。
- COMPLETE 扫描完成、Viewer 局部窗口、真实 END 和邻近文件授权仍分别处理。超过 20,000 的分段目录不被标记为完整内存目录；现有 requestExpand/真实边界机制保留，新增晚到 partial 不覆盖已完成分段窗口。
- 目录排序用 directorySorting；普通媒体/仅此文件夹/Photos 媒体排序仍 getFolderSorting，未改配置、DB schema、备份、签名、UI 样式或 main。

## 修改文件清单

业务代码：
- `activities/MainActivity.kt`
- `activities/MediaActivity.kt`
- `activities/ViewPagerActivity.kt`
- `adapters/MyPagerAdapter.kt`
- `helpers/FavoriteFolderLoadCoordinator.kt`
- `helpers/PhotoSessionViewerCatalogStore.kt`

测试（均在 `app/src/test/kotlin/org/fossify/gallery/helpers/`）：
- `FavoriteFolderLoadCoordinatorTest.kt`：首屏可见与统计完成独立，替换/失败/撤销。
- `FavoriteFolderProgressiveActivityTest.kt`：真实 MainActivity，在 provider 查询被阻塞时清单已发布；隐藏过滤、不可访问收藏保留、后台统计回填、复用 Adapter、统计中改排序及旧代次不覆盖。
- `PhotoViewerInitializationTest.kt`：实际 ViewPagerActivity 首次布局回调不能取消先到的完整目录 publication。
- `PhotoViewerProgressivePagerTest.kt`：真实 ProgressivePhotoViewPager/MyPagerAdapter/FragmentManager，60 项混合照片/视频元数据，完整目录双向超过五项、延迟邻近授权、缓存过期重验、撤销；重复相同目录八次零 Adapter 通知、零新增 Fragment，当前 Fragment 复用。测试页代替真实媒体解码/视频播放器。
- `PhotoSessionViewerCatalogStoreTest.kt`：重复 partial 六次不通知/不重建目录，COMPLETE 仍通知；20,501 分段窗口两端、扩展及晚到 partial 防护。
- `GalleryRefreshRegressionTest.kt`：名称/路径/日期/大小/数量升降序、数字名称、主页/收藏同规则，独立文件夹媒体排序不被覆盖；原随机稳定和 Adapter 差分测试继续执行。

## 验证记录

- `git diff --check`：通过。
- 本地定向 `./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.PhotoViewerProgressivePagerTest --stacktrace`：Gradle 9.5.1 下载因 `Network is unreachable` 失败，测试未启动，不计为通过。
- 完整 `:app:testFossReleaseUnitTest` 与 `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`：提交后使用现有 arm64 工作流验证，结果以本次提交的实际 Actions 为准；本记录提交时待验证。

## 性能与验收口径

代码路径上，收藏首屏不再等待 N 次目录媒体扫描；首屏前仍需必要数据库和可见性检查。目录重复通知/Fragment 创建次数由自动化观察断言验证，不能换算成设备帧率。GalleryPerfTrace 的 viewer_catalog_upgrade、viewer_adapter_publish、viewer_main_thread_publish、viewer_page_window_validation、viewer_catalog_deferred_during_swipe、viewer_fragment_created/destroyed 保留供设备采样。

四项代码与自动化验证已完成；真实触控、解码、播放与权限矩阵仍待真机验收。Android 8/11/14/16、SAF/所有文件、受保护源撤权、真实图片缩放/视频播放、.nomedia 新增文件、删除/移动/重命名返回、搜索/多选、深浅主题仍需要设备矩阵验收。


## 最终 Actions 与提交

| 项目 | 实际结果 |
|---|---|
| 起始 HEAD | `c6da3c5cfd7c30e3635f625bc644a837badb8018` |
| 主要提交 | `0187ef4f6eab070386d7bf953131604d5fd089d1` |
| 最终验证源码 SHA | `8bc6c617e2756cd5dd3c1e51ae4a1b9a3e9e2826`，仅补测试 runner 导入；本报告闭环提交无业务代码变更 |
| 分支 | [fix/photo-viewer-five-item-boundary](https://github.com/Pew2018/Gallery/tree/fix/photo-viewer-five-item-boundary) |
| Actions | [41760914157](https://github.com/Pew2018/Gallery/actions/runs/41760914157)，完整 job 成功 |
| `:app:testFossReleaseUnitTest` | 803 tests、0 failures、0 errors、0 skipped，工作流 `Verified JUnit totals` 日志确认 |
| ARM64 Release | `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` 成功 |
| APK 校验 | 包名 `org.fossify.gallery`，版本 1.14.1/32，v2 签名校验成功，native ABI 仅 arm64-v8a |
| APK Artifact | [gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/41760914157/artifacts/13290472340)，ID 13290472340，ZIP 10,393,100 bytes |
| APK SHA-256 | `a020cc1a128133b3060a7793e87c2d1d0d8da9d7732d36f90d06eb0d7248d579` |
| 测试报告 | [backup-ui-test-reports](https://github.com/Pew2018/Gallery/actions/runs/41760914157/artifacts/13289949744)，ID 13289949744 |
| 签名方式 | 原有工作流自动使用 temporary-CI-not-upgrade-compatible；未修改签名配置/密钥，不能保证覆盖现有安装 |
| 首次失败 | [41760580210](https://github.com/Pew2018/Gallery/actions/runs/41760580210)：业务 Kotlin 编译成功；新测试漏导入 runner，测试源码编译失败。最小补交修复后完整重跑成功；未删除断言。 |
| 源码完整性 | 主提交 15 个文件 Blob SHA 全部匹配本地；主树 `cedcf87cda671fc43d0f75d3920d3ffeba9bc1c8`；验证源码树 `35b23b65e33ef3fb13094936a05a02d9f6bee860` 与本地一致 |

## 四项最终验收

| 问题 | 自动化证据与行为区别 | 结论与真机边界 |
|---|---|---|
| 收藏先显示清单 | 查询阻塞期间真实 MainActivity 已显示安全卡片，后台回填到同一 Adapter；隐藏项过滤，离线关系保留，统计中改排序不被旧结果覆盖 | **已修复且自动化验证通过**；大目录首次可见时延、SAF/外置存储/保护授权仍需真机 |
| 扫描中 Viewer 减少无效更新 | 相同值目录连续提交 8 次，Adapter 通知 0 次，额外 Fragment 创建 0 次，当前 Fragment 实例保留；相同 partial 连续 6 次不通知/不重建；COMPLETE 仍通知 | **代码修复完成但需要真机验证**；自动化行为断言通过，不能据此宣称果冻感/FPS 改善，未运行 Frame Timeline |
| COMPLETE 后双向导航 | 首次布局回调不再取消先到完整目录；真实 Pager/Adapter/FragmentManager 混合照片/视频元数据双向超过五项；原 partial→complete、20,501 分段扩展、真边界、授权 TTL/撤销用例全部通过 | **代码修复完成但需要真机验证**；测试页替代真实 PhotoFragment/VideoFragment 解码/播放器，完整生产 Activity 手势链、图片缩放和视频播放仍待设备 |
| 主页/收藏排序一致 | 名称/路径/日期/大小/数量升降序、数字名称与独立媒体排序测试通过；原随机 rank/自定义/置顶实现保持，真实收藏 Activity 中新排序胜过阻塞旧任务 | **已修复且自动化验证通过**；真实长列表重排锚点与各样式观感待设备 |

本环境没有 Android 设备/模拟器，未取得 viewer_catalog_upgrade、viewer_adapter_publish、viewer_main_thread_publish 等设备端 Trace 时间序列，未测扫描与解码 CPU/IO 占用及实际帧耗时。上述 8 次/6 次是自动化场景的计数，不能视为用户图库运行时基准。原有 .nomedia、多来源/虚拟收藏、普通媒体、授权、备份及显示稳定性回归已包含在 803 项套件中；真实系统权限与文件操作仍按设备矩阵复核。

开发记录还修改了 `docs/media-engine-v2/06-progress-and-change-log.md`、`08-minimal-refactor-verification.md`、`README.md` 和本文；全部只在目标 fix 分支，未新建分支/PR、未合并 main。
