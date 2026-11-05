# 01 — 现有刷新 / 索引 / 列表 / 全屏数据流

> **历史源码/任务资料（非当前实施指令）**：下文提及的旧工作分支、历史“当前进度”“唯一任务”“下一步”，只适用于所写日期和原固定提交。2026-11-05 起，当前工作遵循 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md) 和 [04 — 验收矩阵](04-validation-and-migration-readiness.md)。保留旧源码证据与原始 Actions/限制供追溯；不能据此声称新阶段 0–5 已完成或获得真机性能数据。

审计基线：c5cc5b6852291491a999934d12f26314455959c6；所有路径相对于仓库根目录。链接中的行号对应固定 commit。

## 1. 五条主要页面通路

### A. 相册主页（文件夹卡片）

入口：**MainActivity.getDirectories()** [L1062-L1140](https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt#L1062-L1140)

~~~text
页面启动 / 下拉刷新 / 视图切换
 -> 已有目录快照或 getCachedDirectories()
 -> DirectoryScanEpoch.begin() + GalleryScanTerminalGate.begin()
 -> GalleryAlbumScanExecutor
 -> gotDirectories(...) / MediaFetcher / DirectoryMetadataRepository
 -> DirectoryAdapter.updateDirs() / DiffUtil / 滚动锚点
 -> finishDirectoryScan() / loading=false / RefreshLayout=false
~~~

代码状态：手动刷新可 retire 前次目录扫描；失败、超时、取消都存在显式状态。不同的收藏文件夹、分类过滤场景会转入**另一路加载**，不是同一个方法覆盖全部状态。

### B. 分类与隐藏/排除场景

**MainActivity.loadCategorizedFolders()** [L1424-L1489](https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt#L1424-L1489) 使用 categorizedLoadVersion / categorizedLoadActive、旧缓存、独立的 CategorizedFolderScanner、onProgress 和最终结果；**CategorizedFolderScanner** 对已知目录、.nomedia hint、MediaStore 和递归发现的候选做扫描与中途发布。参考 **CategorizedFolderScanner.kt L14-L147**。

分类路径允许多次中间结果提交，并在最终结果再次更新 Adapter。是否因此产生用户观察到的动画/位移，需要真机 Trace，不能只凭代码判断。

### C. 普通文件夹 / 全部媒体 / 虚拟收藏媒体

**MediaActivity.getMedia()** [L1673-L1743](https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt#L1673-L1743)

~~~text
下拉刷新 / 返回页面 / 媒体变更
 -> 鉴权/收藏当前关系
 -> mediaLoadRequests.begin() / mediaScanTerminals.begin()
 -> 停止旧 mCurrAsyncTask / 超时监控
 -> （首次）getCachedMedia() -> 可选 gotMedia(cache preview)
 -> startAsyncTask() -> GetMediaAsynctask
 -> MediaFetcher.readFolder()/Indexed MediaStore/文件系统
 -> onFolderPreview()/gotMedia(partial)
 -> gotMedia(final) / DirectoryMetadataRepository.commit()
 -> FolderViewerSnapshotStore.prepare() / 完整性条件下缓存清理
 -> MediaAdapter.updateMedia()
~~~

可核对：**MediaActivity L1759-L1954**、**GetMediaAsynctask.kt L67-L334**。最新结果按请求 id / generation 验证；仅完整扫描可进入旧媒体缓存裁剪路径（MediaActivity L1899-L1920）。目录元数据的提交还需要完整读取、范围与权限证明（DirectoryMetadataRepository L93-L170）。

### D. 底栏“照片”自定义配置

**MediaActivity.getPhotoProfileMedia()** [L269-L382](https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt#L269-L382)

~~~text
已选 PhotoProfile / sources / 权限会话
 -> PhotoProfileLoadContext.capture() + PhotoProfileSourceScanPolicy
 -> PhotoProfileMediaCache 可复用完整目录 / 旧缓存预览
 -> MediaFetcher.queryProfileIndexedMedia()
 -> PhotoSessionViewerCatalogStore.offerIndexedCandidates()
 -> scanProfileFileSupplements() / FILESYSTEM 分批
 -> PhotoAccessValidationBatch 校验
 -> PhotoLoadStage: CACHE_PREVIEW / INDEXED / FILESYSTEM / COMPLETE
 -> MediaActivity.publishMedia()
 -> COMPLETE: PhotoSessionViewerCatalogStore.prepare()
~~~

主要证据：**PhotoProfileMediaLoader.kt L32-L218**，其中 L42 协调加载生产者，L50-L61 按当前能力限制扫描来源；L93-L113 检查缓存可复用性；L120-L194 分阶段索引与补扫；L195-L217 完整结果、公共元数据写库与全屏目录准备。

注意：配置数据由 PhotoProfileRepository KEY = photo_profiles_v1 持久化，照片来源包括真实目录和收藏关系；受保护源不能仅凭缓存中存在媒体就被授权。

### E. 全屏点击、预加载与左右滑动（fix 分支新增关键内容）

**ViewPagerActivity.kt** [L230-L372](https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt#L230-L372) 维护滑动时延迟发布、加载/鉴权 generation、完整与部分目录、边界请求、已授权页面集合。

**ViewPagerActivity L818-L1152**：
- 优先使用 grid 提供的完整或部分目录；有 grid 正在生成时避免重复启动同样的全量配置扫描。
- 邻近候选仅作优先读取；边界由 PhotoViewerCatalogCoordinator 管理 UNKNOWN / HAS_MORE / END。
- 遇到 provisional 边界时可 requestPhotoPartialExpand()，从 PhotoSessionViewerCatalogStore 取新的已授权媒体批次。
- 目录升级通过 gotMedia() / updatePagerItems() 接入既有 Pager；正在滑动、Fragment 状态已保存时有延期发布策略（L1928-L1959）。

**PhotoSessionViewerCatalogStore.kt L20-L337**：保存目录、部分结果、生产者状态；超过 20,000 项候选时通过 **SegmentedPhotoCatalog** 写分段临时目录；扩展每次至多 120 个新候选，仍做权限校验。20,000 是该 store 的内存窗口阈值，**不是应用可浏览照片总数的功能限制**。

**SegmentedPhotoCatalog.kt L9-L71**：分段写入每段 512 条，磁盘预算 128 MiB；属于可销毁的进程私有元数据，不是永久索引，更不是授权证明。

## 2. 数据源与事实所有者（当前实际分散）

| 数据类型 | 当前来源 / 更新者 | 读者或副作用 |
|---|---|---|
| 物理媒体目录与文件 | MediaStore / MediaFetcher / FolderFileAccess / FolderDiskDiscovery | MainActivity、MediaActivity、配置 Loader |
| 目录卡片与计数 | GalleryDatabase DirectoryDao + DirectoryMetadataRepository | MainActivity、DirectoryAdapter |
| 媒体条目缓存 | GalleryDatabase MediumDao + PhotoProfileMediaCache | 媒体/照片配置页、全屏过渡 |
| 收藏关系 | FavoritesDao、FavoriteFoldersDao | 收藏页、配置虚拟收藏来源、备份 |
| 自定义照片浏览配置 | PhotoProfileRepository + Config/SharedPreferences | MediaActivity、PhotoProfileLoadContext、备份 |
| 当前列表顺序与滚动位置 | MediaAdapter / DirectoryAdapter / 页面 state | RecyclerView / FastScroller / 全屏点击 |
| 全屏导航快照与增量窗口 | FolderViewerSnapshotStore / PhotoSessionViewerCatalogStore / SegmentedPhotoCatalog | ViewPagerActivity / MyPagerAdapter |
| 权限能力及有效性 | FolderUnlockSessionManager / FavoriteAccessSessionManager / PhotoProfileAccessSessionManager / PhotoAccessValidationBatch | 各 Loader 与 Viewer |
| 备份快照 | GalleryBackupCollector、备份 codecs | 用户导入导出，不能依赖临时索引存在 |

## 3. 更新与发布点的分布

| 主体 | 请求版本 / 终止判定 | 预览或正式发布 |
|---|---|---|
| MainActivity 普通目录 | DirectoryScanEpoch + GalleryScanTerminalGate | setupAdapter / DirectoryAdapter.updateDirs |
| MainActivity 分类目录 | categorizedLoadVersion + active flags | onProgress / 最终 setupAdapter |
| MediaActivity 普通媒体 | mediaLoadRequests + mediaScanGeneration + mediaScanTerminals | gotMedia / MediaAdapter.updateMedia |
| MediaActivity 照片配置 | mediaLoadRequests + PhotoProfileLoadContext + photoPublishedStage | publishMedia 每阶段 |
| 目录元数据 | DirectoryMetadataRepository.begin(path) / commit(path) | Room transaction + revision observers |
| ViewPagerActivity | viewerLoadGeneration + viewerAccessGeneration + mediaPublicationGeneration + swipePublicationGate | gotMedia / updatePagerItems |
| 照片配置目录交接 | PhotoSessionViewerCatalogStore loading / revision / entry validity | offerPartial / prepare / requestExpand |

**采集结论（F）**：已经有大量局部防护与异步取消机制；不存在“完全没有 DiffUtil / Cancel / Cache”的情况。**架构判断（I）**：统一事实源与统一提交协议有价值，但不应在没有阶段性对照的情况下直接重写所有代码。

## 4. 兼容技术与配置基线

- Room：GalleryDatabase.kt 使用 version=11，并定义显式迁移；当前未计划变更 schema。
- 构建：app/build.gradle.kts + gradle/libs.versions.toml；最低 Android SDK 26，目标 SDK 36（以固定 SHA 的构建配置为准）。
- Android 11+ MediaStore 以及有所有文件访问权限时的直接读取、无完全权限时的 SAF persisted-tree 均属于实际存在的模式（FolderFileAccess.kt L24-L78）。
- .nomedia 的实际目录枚举和 MediaStore 缺失补扫是必保路径。不能用“MediaStore 查不到”当作该目录的 COMPLETE/EMPTY 证据。
