# 23 — Phase 1 实施、状态映射与自动化回归清单

日期：2026-11-05。起始远程 HEAD `5cc20adf6875ca220df8db4ff3bb24e42f82de22`；唯一分支 `refactor/media-pipeline-stability`。审计证据及未确认风险见 [22](22-phase1-lifecycle-audit.md)。以下“修改”表示已编入本阶段待 CI 验证的代码，不代表 APK/真机已通过。

## 1. 代码变更和根因

| 文件/函数 | 之前 | 之后 |
|---|---|---|
| `helpers/MediaPipelineDiagnostics.kt`: `add`, `set`, `finish`, `emit` | 汇总候选与接纳计数 -1 被转为 0；其它计数可记任意非法负值；诊断早退遗漏，终态有时不可追踪 | 合法非负计数保留、其余统一 -1；未知计数与实测零严格分离；长时间计时只对不合法负数做非负处理，未知计时仍记 -1；累加拒绝负数并防溢出；`finishIfOpen` 为生命周期被取消的诊断提供幂等补终态，不更改业务 `GalleryScanTerminalGate` |
| `activities/MainActivity.kt`: `retireDirectoryScan`、`getDirectories`、`finishDirectoryScan`、`loadCategorizedFolders`、`onPause` | HOME worker 已完成但 UI 早退时诊断可能没有终态；CATEGORY 变更/切页后旧 final/failure 诊断会丢失或误记 FAILED | 当前诊断引用与原有 epoch/version 一同被失效，旧回调仅结束日志为 CANCELLED，不能重新发布；正常页按既有 terminal gate 关闭 loading；分类错误仅在相同的当前 UI 检验中记 FAILED |
| `asynctasks/GetMediaAsynctask.kt`: `finishPipelineDiagnostics`、`reportRejectedExecution` | 拒绝执行没有合适的 FAILED worker 诊断；CANCELLED/FAILED 的空结果会被记录为已正式接纳 0 项 | 无已发布正式结果时 `acceptedCount=-1`；拒绝路径可以独立结算 FAILED；重复异步 onCancelled 不再将已结束的诊断改为 CANCELLED |
| `activities/MediaActivity.kt`: `startAsyncTask` | 执行器拒绝时 UI 被恢复但 terminal gate 未记 FAILED；后台调和读取可变 `mMedia` | 在拒绝 catch 中先对当前 task 记录诊断/业务 FAILED，再取消、失效请求、恢复已有 UI；正式结果回调在主线程复制旧媒体快照，再下发后台读差集。继续在旧 request/scanGeneration 与 COMPLETE 限制下剪枝 |
| `DirectoryScanEpoch.kt`、`GalleryScanResult.kt`、`FolderMediaScan.kt`、`DirectoryMetadataRepository.kt` | 已具备版本／终态／完整性／会话安全门 | **不修改业务实现**，只扩充现有测试，避免引入第二个管理器 |
| `MediaFetcher.kt`、`FolderFileAccess.kt`、`CategorizedFolderScanner.kt`、`PhotoProfileMediaLoader.kt`、`PhotoProfileLoadContext.kt` | 已分清文件/索引、缓存/正式阶段和受保护来源 | 无足够可复现的新缺陷时不改读取语义、SQL、排序、阶段模型或权限 |
| `MediaAdapter.kt`、`DirectoryAdapter.kt`、`ViewPagerActivity.kt`、`GalleryDiagnosticLogger.kt` | 原本具有 Diff/身份/授权/日志保护 | 不改视觉、布局、缩略图、签名或 Viewer 分段结构 |

完整性与数据删除的原有屏障为：`DirectoryMetadataRepository.commit` 需要 `FolderReadStatus.COMPLETE` 且 requestScope、访问会话、revision、transaction 仍有效；`MediaActivity` 完整扫描才可运行基于 `MediaCachePrunePolicy` 的缓存删除；`GalleryScanResult.sources(successfulSources,failures,enumerationComplete)` 的 PARTIAL/FAILED 不可升级为 COMPLETE。

## 2. 请求发布前后状态机（不引入业务新枚举）

```text
开始新 epoch / mediaLoadRequests.begin / 分类 version
    -> 缓存可用 ? CACHE_PREVIEW (provisional; 不清 loading/不认 COMPLETE)
    -> Worker 已排队
       -> 执行器拒绝: FAILED 终态 + loading off + 原重试 UI
       -> 旧请求取消: CANCELLED 终态, 不能提交 UI/Room
       -> Worker 完成: 投递主线程回调
          -> generation/scope/授权已失效: CANCELLED, 不正式发布
          -> 超时先获 terminal gate: TIMED_OUT, 拒绝晚到 COMPLETE
          -> 完整读取: COMPLETE, 允许通过二次校验的元数据/缓存操作
          -> 仅部分来源成功: PARTIAL, 不准执行 COMPLETE 专用清理
          -> 读取失败/失权: FAILED/ACCESS_REQUIRED, 保留可信数据并反馈
```

最终业务状态只由已有 request/terminal gate + 读取完成度决定。诊断 `finishIfOpen` 只能写审计事件，**不允许**决定 UI 是否接纳某次结果。锁定来源独立于允许访问来源的阶段完成情况：`PhotoAccessSummary.needsUnlock` 和 `publicOnly` 不被压入 `GalleryScanState`；仅当前授权的公开媒体可显示。

## 3. 逐层状态映射

| 文件／来源读取 | 聚合扫描状态 | 照片配置阶段／访问 | 可见与后续 |
|---|---|---|---|
| 读取成功，范围完整 | COMPLETE | 完成阶段 COMPLETE；公开/已解锁范围 | 正式结果；如版本稳定允许元数据提交 |
| 至少一来源成功且其他来源不可读/枚举未完 | PARTIAL | CACHE_PREVIEW/INDEXED/FILESYSTEM 或需授权的混合状态 | 可显示经过校验的公开内容；不清理未确认缺失数据 |
| 无可信成功来源，IO/权限失败 | FAILED | 失败、锁定待解 | 保持可信缓存，错误反馈与重试；不是 EMPTY |
| 检测到 cancel/过期 generation | CANCELLED | PhotoLoadInvalidatedException | 不提交正式结果，不得将旧结果发布到当前网格 |
| deadline 先到 | TIMED_OUT | 不接受更迟的旧 COMPLETE | 关闭 loading 并反馈，等待下一次请求 |
| 需要解锁/访问已撤销 | 底层 ACCESS_REQUIRED，业务可能 FAILED/PARTIAL | needsUnlock 与当前 permitted 来源并存 | 失权媒体不展示；其余仍授权来源保持可浏览 |

注意：阶段预览不是 COMPLETE，权限限制不是文件真的不存在；`GalleryScanResult` 不加 ACCESS_REQUIRED 枚举值。

## 4. 回归测试矩阵与证据限度

| 验收用例 | 来源、断言 | 结果状态 |
|---|---|---|
| T01/T04 两请求交错、延迟主线程发布 | `DirectoryScanEpochTest.latestEpochAndFirstTerminalOwnDelayedUiPublication` 使用手动 callback 顺序，断言老 epoch 不发布 | 待 CI 验证 |
| T03 超时后 Worker 迟到 | 同上；TIMED_OUT 先占 terminal gate，后到 COMPLETE 被拒绝 | 待 CI 验证 |
| T05 bounded 执行器拒绝和安全再次入队 | `RefreshReliabilityTest.rejectedBoundedScanHasAnExplicitFailedTerminalAndLaterRetryCanRun`，受控 latch，无 sleep | 待 CI 验证 |
| T06 页面退出后的旧回调 | 既有 `FolderRefreshActivityTest`、`DirectoryScanEpochTest` 与 `GalleryRefreshRegressionTest`；本轮补 HOME/CATEGORIZED 诊断闭合 | 既有覆盖；真机待验 |
| T07 PARTIAL 不得清缓存 | `FolderMediaScanTest.failedFolderDoesNotPreventLaterFoldersAndKeepsOnlyItsOwnCache` 新增 PARTIAL/outcome.complete=false 断言；`MediaCachePrunePolicyTest` 既有删缓存门 | 待 CI 验证 |
| T08 `.nomedia` 新增媒体 | 既有 `NoMediaCategoryRefreshTest`、`FolderRefreshActivityTest.pullRefreshAddsCopiedNoMediaImageToTheActualAdapterAndHomeCard`；本轮不重写 | 既有代码证据；真机待验 |
| T09 分类条件变更 | `CategorizedFolderScanner` / `loadCategorizedFolders` 复用 version + flags 校验；诊断关闭新增 | 代码修复；动态 UI 交错待专测 |
| T10 照片配置切换后旧阶段失效 | 既有 `PhotoProfileSourceScanPolicyTest`、`PhotoProfileMediaLoadingTest` 与 `PhotoProfileLoadContext.isCurrent` | 既有代码覆盖；完整阶段交错待专测 |
| T11 混合锁定来源公开可见 | 既有 `PhotoProfileSourceScanPolicyTest.lockedVirtualFavoritesDoNotSuppressPublicFolders` 和 `PhotoProfileMediaLoadingTest` | 已有静态/模拟覆盖；设备待验 |
| T12/T13 删除/移动/恢复竞态 | `ViewerDeletionNavigationPolicyTest`、`PhotoViewer...Test`、`MediaCachePrunePolicyTest` 等既有测试与路径身份保护 | 不等同真实并发事务测试，标为 R/PENDING_DEVICE |
| T14/T15 终态、未知值、非法负数 | `MediaPipelineDiagnosticsTest` 新增 3 项：未知 vs 真零、非法负计数、不重复关闭诊断 | 待 CI 验证 |
| T02 缓存回调晚于请求失效 | `MediaActivity.getMedia` 回调核验 `mediaLoadRequests.isCurrent`，已有生命周期 Activity 回归 | 尚无独立可控缓存调度器的针对性新测试 |

**测试改动清单**：`MediaPipelineDiagnosticsTest.kt`（3 项），`DirectoryScanEpochTest.kt`（1 项），`RefreshReliabilityTest.kt`（1 项），`FolderMediaScanTest.kt`（扩充 1 个原测试断言）。已有 `FolderRefreshActivityTest`、`NoMediaCategoryRefreshTest` 和 `PhotoProfileMediaLoadingTest` 保留，不重复复制夹具。上述测试数量不是全部 15 项都具独立强制调度验收的声明。

## 5. 验证计划与禁止事项

- 本地：检查 diff、可用时 `./gradlew :app:testFossReleaseUnitTest`；若 wrapper 无法下载则记录真实阻断，不能写“本地通过”。
- CI：已有 push 工作流 `.github/workflows/arm64-foss-release.yml` 必须运行完整 FOSS Release 单测、唯一 arm64-v8a APK、包名、签名和 ABI 校验，确认 Artifact 真实存在。结果以相同业务源码 SHA 为准。
- 真机：Android 8/11/14/16、All Files/SAF/Scoped、`.nomedia` 增删、扫描中删除/恢复、连续刷新与后台、混合授权、跨配置 Viewer 20 张都仍为 `PENDING_DEVICE`。
- 阶段 2 未授权：线程池尺寸、Room Schema/SQL、排序、Glide、分页、UI 动画和布局一律不动。没有设备/真实统计不得推断性能提升。
