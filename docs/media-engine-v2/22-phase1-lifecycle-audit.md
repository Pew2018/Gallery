# 22 — Phase 1 请求生命周期、完整性与异步发布审计

日期：2026-11-05（Asia/Taipei）。唯一工作分支：`refactor/media-pipeline-stability`。审计时远程 HEAD：`5cc20adf6875ca220df8db4ff3bb24e42f82de22`（只作本阶段开始时的基线，不重置、不更新 main）。参照现行 03/04/05/18/19/20/21、诊断说明及历史 01–17 的原有边界，不将历史结论自动视为当前运行故障。

证据定义：**F**＝在该 HEAD 有可定位的源码事实（其中 F-fix 表示行为违反明确契约）；**R**＝可以发生但缺少可控竞态/设备证据的风险；**U**＝当前尚未验证。静态源码证据不等于真实设备 P0 复现。下列行号以审计 HEAD 为准，实施后会有小幅位移。

## 1. 五条权威请求链、取消方、正式结果与发布者

| 入口 / 范围 | 开始／异步读取 | 结果、终态／Loading | 允许发布、缓存与权限 |
|---|---|---|---|
| 首页普通目录 HOME | `MainActivity.getDirectories` 1063–1141 → `DirectoryScanEpoch.begin()` → 缓存读取 `getCachedDirectories` → `GalleryAlbumScanExecutor` → `gotDirectories` 2117–2637 → `MediaFetcher.readFolder` | `finishDirectoryScan` 1143–1171：唯一 terminal gate，停止 loading/refresh，PARTIAL/FAILED/TIMED_OUT 提示；`retireDirectoryScan` 259–269 在新请求、退出和导航失效 | `isCurrentDirectoryScan` 255–257；目录卡片递进由 `setupAdapter` 再校验 epoch；`DirectoryMetadataRepository.commit` 108–177 仅 COMPLETE、scope/会话/版本及事务内二次检查；`checkInvalidDirectories` 仅在 outcome.complete 时执行 |
| 普通文件夹／All／收藏／回收站 | `MediaActivity.getMedia` 1709–1779 → `mediaLoadRequests.begin()` → 缓存预览 → `startAsyncTask` 1795–1989 → bounded `GalleryMediaScanExecutor` → `GetMediaAsynctask.doInBackground` | `onProgressUpdate`／`onPostExecute`／`onCancelled` 336–409；`renderMediaScanState` 518–525 竞争第一个 terminal 并清 loading；`onPause` 943–966 取消；`armMediaRefreshDeadline` 487–515 处理 TIMED_OUT | request+generation+scope、Activity 活性和受保护访问校验；`DirectoryMetadataRepository` 按 version；仅 scanResult.complete 可进入缓存 prune 1935–1956 |
| 分类目录 CATEGORIZED | `MainActivity.loadCategorizedFolders` 1489–1594 → 分类 version/visibility 快照 → 缓存（失败时用 retained）→ `CategorizedFolderScanner.scan` → 分批 progress | 旧版以分类 version/visibility 检查回调，终态及 loading 在 final/failure UI 回调；`onPause` 增加 version | `CategorizedFolderScanner` 通过 `FolderCategoryPolicy`、`readFolder`、`DirectoryMetadataRepository` 控制访问/提交。partial 阶段不能被视为完整扫描 |
| 自定义照片配置 PHOTO_PROFILE | `MediaActivity.getPhotoProfileMedia` → `GetMediaAsynctask` → `PhotoProfileMediaLoader.loadStaged` 33–256 → CACHE_PREVIEW、INDEXED、FILESYSTEM、COMPLETE | `PhotoProfileLoadContext` 的 scope、revision、favorites 与 Session；`submitPhotoGridResult` 369–397 按 request、scope、stage 拒绝过期结果 | `PhotoProfileSourceScanPolicy.select` 保护扫描来源；`PhotoAccessValidationBatch` 对发布数据重新验证；混合锁定只显示当前允许的公开来源；`PhotoSessionViewerCatalogStore` 接管有效目录 |
| 全屏 Viewer／文件操作 | grid/path handoff → `ViewPagerActivity`／分段 catalog／viewer access coordinator → 授权页面更新 | `PhotoContentRevision`、Session 版本、Viewer catalog/path identity、删除恢复锚点；不允许将窗口末端当作真实 END | Viewer 的范围、身份和权限检查已有现成实现；本阶段不重写分页器或 Glide。删除/恢复、编辑/收藏对跨页面一致性的设备证据仍为 U |

## 2. 已有保护（不要重新实现）

- **F** `DirectoryScanEpoch` 的 `begin/retire/isCurrent`（`helpers/DirectoryScanEpoch.kt:5–13`），以及 `GalleryScanTerminalGate` 的 `begin/finish/result`（`GalleryScanResult.kt:24–35`）已具单请求幂等终态；`DirectoryScanScope.matches` 与 `DirectoryMetadataRepository.commit` 保护 COMPLETE/权限/版本/事务提交。
- **F** `GetMediaAsynctask.onCancelled` 和 `stopFetching`（`asynctasks/GetMediaAsynctask.kt:406–430`）已处理预期取消与任务/回调清理。旧任务 `onFolderPreview`、时长细节和媒体正式结果由 `MediaActivity` 的 request+generation 拦截。
- **F** `FolderMediaScan.load`（`helpers/FolderMediaScan.kt:13–32`）区分失败与成功来源、保留失败目录的可信 cache，取消抛异常；`MediaActivity` 1935–1956 只在 scanResult.complete 时做媒体缓存删除，并用 `MediaCachePrunePolicy` 再验证真实不存在与父目录可读。
- **F** 普通及 `.nomedia` 路径使用 `FolderFileAccess`、`NoMediaSnapshot` 和 `MediaFetcher.readFolder`；`CategorizedFolderScanner` 保留失败来源旧卡片，发现状态不完整时记录失败。当前已有 `NoMediaCategoryRefreshTest`、`FolderRefreshActivityTest`、`PhotoProfileMediaLoadingTest`。
- **F** `PhotoProfileSourceScanPolicy` 的 permitted/blocked 分离不把 `needsUnlock` 误当全部公开目录不可显示；旧 cache 不能代替新授权。分类、照片配置、Viewer 均已有版本/权限判定，不增加第二套 scan manager。
- **F** `MediaAdapter`、`DirectoryAdapter` 既有 stable ID/Diff/scroll defer，Viewer 分段目录及路径身份保留原样。

## 3. 缺陷和风险清单

| ID | 证据级别 | 源码与错误时序 | 修复/处置 |
|---|---|---|---|
| F01 | F-fix | `MediaPipelineDiagnostics.finish` 原 64–66 行将候选/接纳计数 `-1` `coerceAtLeast(0)`；第 0 阶段约定 -1 未测、0 实测零 | 仅修指标处理、非法负值和测试；业务媒体完全不变 |
| F02 | F-fix（诊断边界） | `MainActivity.finishDirectoryScan` 原 1151 行若在 worker 已完成、主线程回调前被新 epoch 替代则早退；`onPause/retireDirectoryScan` 也未结束请求级诊断 | 用原 epoch 失效动作同步关闭诊断，旧业务请求仍禁止发布 |
| F03 | F-fix（诊断边界） | `loadCategorizedFolders` 原 1566 行过期 final UI 回调早退；1514 行 failure 在 UI current 校验前记录 FAILED，可能把切换后的过期请求记成实际失败 | 基于版本/flags 的同一 UI 校验记录 CANCELLED/FAILED，不变更目录业务状态机 |
| F04 | F-fix（终态边界） | `MediaActivity.startAsyncTask` 原 1968 行 RejectedExecutionException 已关闭 loading 与允许重试，但没有 `GalleryScanTerminalGate.finish(FAILED)`；AsyncTask 取消诊断可能误记 CANCELLED | 拒绝路径先结算诊断 FAILED 和既有终态，再取消 task，保持原失败 UI |
| F05 | F（数据所有权风险） | `MediaActivity` 原 1886 行 `ensureBackgroundThread` 中迭代跨请求可变的 `mMedia`；UI 下一次刷新会替换该集合，导致 cache reconciliation 的旧基线可能竞争 | 在主线程正式回调创建 copy snapshot，再交给后台处理；保留请求复核与完整性剪枝条件 |
| R01 | R | `GalleryScanTerminalGate` 和 epoch 能阻止 UI 旧结果，但真实删除/移动与 Room 事务跨请求交错需更强可控数据库调度器/真机证据 | 记录；本轮不臆测全部删除/恢复问题均已消失 |
| R02 | R | 分类 scan 进度/最终 Adapter commit 异步可被后续导航替代；诊断终结与可见帧不是同一个时间点 | 保留 Adapter 自己的 current 门；在第 4 阶段用 observer + device trace 验证实际显示 |
| R03 | R | ALL Files 分区存储、SAF 撤权和扫描途中外部修改涉及多来源一致性 | 维持 PARTIAL/FAILED、不做全局缓存清理；待设备权限矩阵验证 |
| U01 | U | 设备上的 `.nomedia` 100→101→201、移动 10 项、并行首页/内部刷新 | `PENDING_DEVICE`；不能用 JVM mock 等价替代 |
| U02 | U | 自定义配置跨多个来源、解锁弹窗、收藏动态修改、Viewer 连续 20 张及被中断数据恢复 | `PENDING_DEVICE` |
| U03 | U | p50/p95、Room/MediaStore 实际瓶颈、帧耗时/首帧画面 | 无设备日志；不进入阶段 2 热点优化 |

**特别说明：** F02/F03 是可以直接从回调早退路径验证的诊断终态遗漏；不代表业务扫描会重启或旧结果能成功发布。F05 确认跨线程共享可变对象的代码事实，但具体设备错误仍须复测。

## 4. COMPLETE 与权限的逐层判定

`GalleryScanState` 仍为 `COMPLETE / PARTIAL / FAILED / TIMED_OUT / CANCELLED`；权限可在底层 `FolderReadStatus.ACCESS_REQUIRED` 和 UI/诊断 `ACCESS_REQUIRED` 表达，不向业务枚举额外添加不兼容枚举。`PhotoLoadStage.CACHE_PREVIEW/INDEXED/FILESYSTEM` 是阶段性可见结果，不具备正式 COMPLETE 的语义。混合授权以 **当前 permitted 来源** 计算有效公开结果，blocked 来源仍独立显示 `needsUnlock`；既不泄露受保护媒体，也不强制公开部分一直空白。

- 完整且仍有权限：允许正式 COMPLETE、仅符合 scope+version 的元数据提交；确认文件不存在才可执行缓存清理。
- 有成功来源但其他读取失败、枚举截断或权限不明：PARTIAL，允许经过重新验证的公开快照，但禁止 complete-only cleanup。
- 全部必需来源失败：FAILED，保留可信旧结果并反馈；不是 EMPTY。
- 用户取消、导航失效：CANCELLED，关闭 loading、禁止正式发布。
- 计时期限先结束：TIMED_OUT 赢得终态门，后续旧 worker 不可改写；UI 保留可信内容并可重试。
- 受保护访问待授予：ACCESS_REQUIRED 只作为适用的读取/反馈状态，不替换业务 COMPLETE 枚举；Session 当前校验永远优先于缓存。

## 5. 验证方法与限制

定向测试覆盖真实 `DirectoryScanEpoch`＋`GalleryScanTerminalGate` 的延迟 UI 完成顺序，以及 bounded executor 被占满后的 rejection/retry；现有 `FolderRefreshActivityTest` 通过真实 Robolectric Activity、媒体文件读取和目录 DB 检查首页/内部目录 `.nomedia` 刷新与媒体列表。不可称虚拟文件系统和 Robolectric 为 Android 11+ SAF/厂商权限真机证明。无 `Thread.sleep` 的新并发测试使用显式 pending callback 与同步 latch；保留旧测试的既有等待实现。

最终测试、Actions 和设备矩阵见 [23](23-phase1-implementation-and-tests.md) 及 [24](24-phase1-results-and-handoff.md)；本页的源代码行号基于修复前 HEAD。
