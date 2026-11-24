# 底栏导航与 Photos 首屏交接 / 媒体列表发布（第一批）

> 基线：开始执行时重新 `git fetch origin --prune`，`perf/home-gallery-sorting`
> 远程 HEAD 为 `afc4626ea4c84f7fcde769ea4d13da1875a28fa9`。本地工作副本此前停在
> `f0867983c`（是该 SHA 的祖先，无本地独有提交），因此只做 fast-forward，没有 reset、
> 没有新建分支、没有改动 `main`。
>
> 本文只把能由源码、单元测试或可复现命令证明的结论写成「已确认」。需要 Pixel 8 Pro
> 真实窗口合成/显示扫描的结论一律标注「待真机验收」。

## 0. 证据可得性（先行声明）

任务点名的两份原始文件在本机**不存在**：

| 文件 | 状态 |
|---|---|
| `1000040921.mp4` | 未在工作区、`~/Desktop`、`~/Downloads`、`~/Documents`、`~/Movies` 找到 |
| `Gallery-Diagnostics-20261124-075736.txt` | 同上；本机仅有更早一次采集的 `Gallery-Diagnostics-20261123-112745.txt`（应用 1.14.1 (32) / Pixel 8 Pro / API 36，事件词汇与本任务一致） |

因此：

- **没有重新观看任务点名的录屏，也没有该时刻的完整诊断日志。**
- 第 1 节的修复前时间线**直接引用任务给出的记录**，只用于定位调用链，不作为本轮新测数据。
- 11:27 的旧日志只用于核对事件名/字段格式；不把其中的 requestId、耗时当作 07:57 那次会话的证据。
- 本轮所有「改善」数字都来自可复现的 JVM/Robolectric 断言（工作量、提交路径、阶段顺序），
  **不是**真机可见帧耗时。

## 1. 修复前真实时间线（任务提供，未重新采集）

| 时刻（07:57） | 事件 | 说明 |
|---|---|---|
| 13.437 | 点击 Photos | MainActivity 发起根导航 |
| ~13.501 | 目标 MediaActivity 首帧 | 状态 `LOADING`，内容尚未发布 |
| ~13.492 | `photo_cache durationMs≈0.82` | 4,993 项热缓存读取很快 |
| 14.404 | `CACHE_PREVIEW` 进入发布，160 项 | 距缓存读取约 **912 ms** |
| ~14.419 | `photo_first_frame` ≈932 ms | 由 `postOnAnimation` 近似记录，不是像素证明 |
| ~14.203 | `loading_frame_committed_after_limit` | 700 ms 兜底已触发 |
| ~14.211 | 目标帧提交；旧 MainActivity 结束 | 交接依赖 Loading 兜底 |

方向性差异：进入 Photos 6/6 次触发 700 ms 兜底；返回 Albums/Favorites 通常 94–137 ms。
（任务记录；本机无 07:57 日志。）

## 2. 根因与证据等级

| # | 结论 | 等级 |
|---|---|---|
| A-1 | `MediaActivity.getMedia()` 的 Photos 分支调用 `getPhotoProfileMedia()` 时使用默认参数，而该默认值是 `REFRESH`；`REFRESH` 被翻译为 `manualRefresh=true`，再传到 `loadStaged(forceScan = true)`。普通底栏进入因此共享了「用户主动下拉刷新」的强制扫描语义 | **已确认（源码）** |
| A-2 | 缓存读取（≈0.8 ms）之后，预览路径仍对全部缓存行执行 `CachedMediaPreviewFilter.removeConfirmedMissing`（逐文件 `isFile`/父目录检查）与完整 `sort`，然后才切 160 行；这正好落在 13.492→14.404 的空档内 | **已确认（源码）**；具体毫秒占比待真机分段计时 |
| A-3 | `PhotoProfileCatalogCheckpoint.capture()` 在 `isExternalStorageManager()` 或 `scope.allFiles` 时返回 `null`，`readReusable()` 因而不可能命中。ALL_FILES 下完整目录缓存复用被结构性地挡住 | **已确认（源码）** |
| A-4 | 700 ms 只在目标已经绘制 Loading、且尚未请求提交时启动；内容帧若先到会立即提交。代码没有把 Loading 当成有效内容，也没有把 700 ms 直接当成首屏耗时 | **已确认（源码 + 既有测试）** |
| B-1 | 适配器的 APPEND 快速路径只允许 `CACHE_PREVIEW/INDEX_PREVIEW/SCAN_PARTIAL` 三个字符串；Photos 的阶段发布传入的 `snapshotStage` 实际为 `null`，因此「前缀完全保持、只在尾部增长」的 FILESYSTEM 阶段仍然走完整 DiffUtil | **已确认（源码）** |
| B-2 | DiffUtil 在专用后台 executor 上运行，主线程只做 commit + dispatch；任务给的 319 ms 不能直接等同于掉帧 | **已确认（源码）**；主线程耗时/掉帧待真机 |
| B-3 | 相同可见内容已有 NO_OP、纯重排已有专用路径、滚动/Viewer 返回已有发布门禁 | **已确认（源码 + 既有测试）** |

## 3. 实际修改的调用链与文件

### 3.1 加载原因分类（A-1）

```
GalleryBottomBar click
  → MainActivity/MediaActivity.openGalleryRoot(section)
    → MediaActivity.getMedia()                     // 默认 ENTER（不再是 REFRESH）
      → getPhotoProfileMedia(ENTER)
        → GetMediaAsynctask(manualRefresh=false)
          → PhotoProfileMediaLoader.loadStaged(forceScan=false)
```

改动：

- `MediaActivity.getMedia(manualRefresh, reason, photoReason)` 新增可选 `photoReason`；
  Photos 分支按 `photoReason ?: (manualRefresh ? REFRESH : ENTER)` 决定。
- `getPhotoProfileMedia` 的默认原因由 `REFRESH` 改为 `ENTER`。
- 逐入口显式分类（未机械替换）：
  - 普通进入 / onResume / 布局或分组变化 / 失败重试 / Viewer 与配置子页返回：
    `ENTER`，优先复用已验证且仍 current 的会话/目录缓存。
  - 用户下拉刷新、部分失败后的显式重试：`REFRESH`（仍执行完整检查，
    `discoverUnindexedFolders` 语义保留）。
  - 权限/来源/保护/可见性/过滤/发布配置变化：`SCOPE_CHANGED`（作用域键变化使旧缓存立即失效）。
  - 检测到媒体实际变化（MediaStore 轮询、Viewer 返回对账发现变化、恢复校验期间内容变化）：
    `REFRESH`。
  - 排序变化：沿用既有 `getPhotoProfileMedia(ENTER, previousSortScope)` 的保留网格 + 重排路径。
- `REFRESH` 仍是唯一会显示下拉刷新指示并强制全量检查的原因；
  `ENTER` 只影响「是否跳过已验证目录缓存的短路」，最终 `COMPLETE` 依然执行完整扫描与逐项验证。

### 3.2 缓存读取到首屏的分段计时（A-2）

新增 `PhotoPublicationTimeline`，仅在诊断开启时输出 `module=PhotoPublication` 事件：

| 事件 | 位置 |
|---|---|
| `photo_cache_lookup_end` | `readReusable` / 内存缓存 / Room 兜底之后 |
| `photo_cache_missing_filter_end` | 有界选择或全量缺失过滤之后 |
| `photo_cache_order_ready` | 复用既有顺序或完整排序之后 |
| `photo_preview_slice_ready` | 160 行窗口确定之后 |
| `photo_preview_access_validated` | 该窗口通过路径/保护/`.nomedia`/收藏验证之后 |
| `photo_preview_ui_enqueued` | `onStage(CACHE_PREVIEW)` 即将投递 UI 之前 |
| `photo_preview_adapter_committed` | Adapter 真正提交（含 NO_OP/APPEND/DIFF）之后 |
| `photo_preview_first_draw_observed` | `registerFrameCommitCallback`（API 29+）或 `postOnAnimation` 近似回调 |

字段：匿名化 `navigation`、`mediaRequestGeneration`、匿名化 `scopeKey`、`stage`、
`mediaCount`、`durationMs`（距上一阶段）、`sinceRequestMs`。不写媒体路径。
`photo_first_frame` 保留，但文档明确它只是 `postOnAnimation` 近似，不能与
`photo_preview_first_draw_observed` 的 frame-commit 语义混用，也不声称真实显示器已呈现。

### 3.3 首批内容发布顺序（A-3）

`CachedMediaPreviewOrder.select()`：

- 仅当缓存来自 `PhotoProfileMediaCache`（其条目属于当前完整 `PhotoProfileScope`，
  排序字段也在 scope 内）时信任既有顺序；
- 从锚点前 1/4 窗口开始，只对候选行做存在性检查，凑满 160 行即停；
- 锚点靠近末尾时向前补齐，并保持原升序；
- `SORT_BY_RANDOM` 永远不信任缓存顺序，回退到完整过滤 + 排序；
- Room 缓存行只是候选集合，顺序不可信，同样回退到完整路径；
- 预览仍经过 `PhotoAccessValidationBatch`（路径/符号链接/保护/`.nomedia`/收藏成员），
  不跳过授权与存在性检查；
- 受保护/混合配置继续使用既有的 stable-locked-preview 规则，不额外发布。

`PhotoProfileMediaLoader` 现在先发布有界的 `CACHE_PREVIEW`，再执行
`INDEXED → FILESYSTEM → COMPLETE`；后台扫描不阻塞首批提交，预览不清空已显示网格，
`COMPLETE` 仍走完整扫描与逐项验证，不把缓存当最终权威。

### 3.4 底栏交接（A-4）

未修改 `LOADING_HANDOFF_LIMIT_MS = 700L`、`FIRST_DRAW_LIMIT_MS`、窗口动画、
Ripple/选中态或既有导航事务。改的是让热缓存内容更早到达：
内容帧一旦提交，`coordinateFirstDraw` 仍走 `valid_frame_draw` 并在同一个
`registerFrameCommitCallback` 上收尾；没有内容时才落到有界 Loading 兜底。
`GalleryRootTransitionTest` 继续覆盖：Loading 不能提前退休源页、内容帧可先于 700 ms 提交、
迟到的旧帧不能结束新请求。

### 3.5 列表发布与 Diff（B-1/B-2/B-3）

`MediaAdapter.submitPendingUpdate()`：

- APPEND 的判据改为**内容本身**：`reason == SCAN && preserveScroll &&
  isContentAppend(media, incoming)`（旧列表必须是新列表的完整前缀且严格更短）。
  不再依赖阶段名字符串。
- 拍摄日期倒序时，新项目插入表头会破坏前缀，`isContentAppend` 为 false，仍走正常 Diff；
  纯排序变化仍走 `commitReorder`；相同内容仍走 NO_OP。
- 未引入 `notifyDataSetChanged()`，未关闭 RecyclerView 动画，未改动 generation、
  `isCurrent`、`deferCommit`、滚动/Viewer 返回门禁。
- `publishMediaSnapshot` 现在把 `displayedPublicationStage` 一并作为诊断阶段名，
  便于下一次日志区分 PREVIEW/PARTIAL/FINAL；该字段只用于诊断，不参与内容判定。

发布门禁保持：
1. 首批有效内容（适配器为空时 `deferMediaGridPublication` 不拦截）；
2. Viewer 返回/触摸/快速滚动期间延迟非紧急提交；
3. 用户搜索/排序/删除仍以用户原因为准；
4. 后台中间批次可被新快照替换；
5. `COMPLETE` 不受进度预算限制，最终提交。

### 3.6 生命周期与并发（B-4）

未新增线程池或并行扫描。`onPause/onStop/onDestroy` 的 request 失效、retained scan、
Viewer 过渡和配置子页覆盖逻辑保持不变；新增的 frame-commit 观察只注册一次性回调，
Activity 销毁后回调自行返回，不持有静态引用。

## 4. 缓存安全性与生命周期设计

- **没有引入第二套长期缓存。** 预览只读取既有 `PhotoProfileMediaCache` / Room 候选。
- 可读条件仍是完整的 `PhotoProfileScope` 相等 + `PhotoContentRevision` + 收藏关系 +
  60 秒年龄；排序、过滤、来源、排除项、隐藏偏好、保护偏好、权限版本、ALL_FILES 模式
  都在 scope 内。
- ALL_FILES 下 `PhotoProfileCatalogCheckpoint.capture()` 仍返回 `null`，
  `readReusable()` 仍不会把完整目录缓存当权威结果；本次只让**首批可见行**走有界验证，
  随后仍执行完整扫描。
- 预览阶段的 `PhotoAccessValidationBatch` 重新收集路径证据；受保护来源在
  未授权时不会因缓存命中而显示。
- `.nomedia` / 隐藏媒体 / 收藏 / 自定义照片配置的最终正确性由既有 `COMPLETE` 路径保证；
  本轮没有放宽这些检查。
- 随机排序、Room 行序、scope 变化都会显式回退到完整路径。

## 5. 修改前后的可比较指标

以下都是**可自动化复现**的指标，不是 Pixel 8 Pro 的可见帧耗时。
host JVM 微测只用于证明「全量过滤确实是可以避免的固定成本」，不能外推为设备端 912 ms 的全部归因；
剩余时间必须靠第 3.2 节的分段事件在真机上定位。

| 指标 | 修改前（代码行为） | 修改后（测试断言） | 证据 |
|---|---|---|---|
| 热缓存预览的文件存在性检查次数（非随机排序） | 全部缓存行（日志样本 4,993） | ≤160 | `CachedMediaPreviewWindowTest.boundedWindowChecksOnlyTheRowsItCanShow` |
| 热缓存预览前的完整排序 | 对全部缓存行执行 | 可信顺序下不排序 | `cachedScopeOrderIsReusedWithoutWalkingTheWholeCatalog` |
| 首个预览阶段是否早于索引查询 | 是，但之前已付全量过滤/排序 | 仍是（回归保护） | `hotCachePublishesABoundedPreviewBeforeTheIndexQuery`（首阶段时索引查询数 = 0） |
| 普通进入是否强制全量扫描 | 是（默认 REFRESH） | 否（ENTER 复用 current 目录） | `plainEnterReusesTheStableCatalogWhileExplicitRefreshPerformsTheFullQuery` |
| 用户刷新是否仍完整检查 | 是 | 是（断言重新查询索引） | 同上 |
| 前缀保持的阶段增长 | 每阶段 1 次 DiffUtil | 0 次 DiffUtil + 1 次 `notifyItemRangeInserted` | `prefixPreservingScanGrowthUsesAppendCommitWithoutDiff` |
| 表头插入（日期倒序） | 正常 Diff（正确） | 仍正常 Diff，不误用 APPEND | `dateDescendingHeadInsertionIsNeverMistakenForAnAppend` |
| 相同可见内容 | NO_OP | NO_OP | 既有测试 + `identicalPartialSnapshotStaysANoOp` |
| 4,993 行缺失过滤（host JVM，APFS，5 次中位数） | 94.41 ms | 3.07 ms（160 行） | `scripts/perf/PreviewWindowBench.java`，非设备数据 |
| 4,993 行完整排序（host JVM，5 次中位数） | 0.34 ms | 0（可信顺序时） | 同上；设备端比较器成本另计 |
| 点击→首帧 / 首缩略图绘制 / 真机可见帧 | 任务记录 932 ms 近似 | **未采集** | 需真机复测 |
| 700 ms 兜底是否触发 | 6/6 | **未采集** | 需真机复测 |
| 主线程 commit/layout 耗时、掉帧 | 未采集 | **未采集** | 需 Perfetto/FrameMetrics |

## 6. 自动化测试覆盖与结果

新增/扩展：

- `CachedMediaPreviewWindowTest`（6 项）：可信顺序、缺失行跳过、锚点窗口、
  尾部反向补齐、随机排序回退、空缓存/非法 limit、160 次检查上界。
- `PhotoProfileMediaLoadingTest`（+3 项）：
  - ENTER 复用稳定目录缓存、REFRESH 重新查询索引；
  - 热缓存首阶段为 `CACHE_PREVIEW` 且早于索引查询，最终 `COMPLETE` 收敛；
  - 冷缓存无预览、仍收敛到完整列表。
- `MediaAdapterStabilityTest`（+3 项）：前缀增长走 APPEND 且不排 Diff；
  表头插入不误判 APPEND；相同预览快照 NO_OP。
- 既有 `GalleryRootTransitionTest`（14 项）继续覆盖 700 ms 兜底、内容先提交、
  空白/迟到帧与销毁清理，本轮未修改其断言。

执行命令与结果见第 7 节；完整套件必须全绿后才提交。

## 7. 验证命令与结果

```bash
# 工作副本（JDK 21，与 CI 一致；Robolectric API 36 不接受 JDK 17）
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=$HOME/Library/Android/sdk \
GRADLE_USER_HOME=<workspace>/.gradle-home \
ROBOLECTRIC_DEPENDENCY_DIR=<workspace>/robolectric-deps \
./gradlew :app:testFossDebugUnitTest
```

- 基线（JDK 21）：`BUILD SUCCESSFUL`，194 个测试类、1,416 项断言全部通过。
- JDK 17 下曾出现 `DeleteWithRememberDialogRippleTest` 的
  `DefaultSdkProvider UnsupportedOperationException`；这是本机 JDK 版本限制，
  CI 使用 Java 21，切换后消失，不是本轮代码失败。
- 最终结果：见下方「交付记录」；最终树复跑同为 1,416 项全绿。

静态检查：`./gradlew :app:detekt`。该任务在**本批开始前的 HEAD 上就已经失败**：
`afc4626ea` 干净工作树报告 3,465 条告警（303 行的 `doInBackground`、既有
`PhotoFragment`/`VideoFragment` 复杂度、全仓库 `MagicNumber`/`MaxLineLength` 等）。
本批工作树报告 3,464 条，净减少 1 条（删除了适配器里一个过复杂的组合条件）；
没有新增检测规则类别或新文件告警。变化只来自既有指标的数值：
`getMedia` 复杂度 28→30、`publishMediaSnapshot` 156→158、`MediaActivity` 函数数
173→176（新增 3 个仅诊断用的观察函数）；`PhotoProfileMediaLoader.loadStaged`
从 166 行降到 164 行。**不把 detekt 说成通过**。

## 8. 尚需真机确认

1. 热缓存点击 Photos：首帧、首批 Adapter 提交、首张缩略图绘制、首个有效内容帧、
   是否仍触发 `loading_frame_committed_after_limit`。
2. 同一媒体集合下 Diff 次数/输入规模/后台耗时、主线程 commit/layout 耗时。
3. 快速 Albums→Photos→Favorites→Photos 往返时，旧请求是否仍会提交到新页面。
4. 滚动中后台扫描时，列表位置与已显示缩略图是否稳定；APPEND 路径是否触发不必要的
   进入动画。
5. 受保护来源、`.nomedia`、ALL_FILES 与 scoped storage 各跑一次真实刷新。
6. 120 Hz 流畅性与真实显示器呈现——**本报告不作任何此类声称**。

## 9. 明确未做

- 未修改 Viewer 的图片几何、共享元素、下拖退出、预测性返回曲线、系统栏视觉策略。
- 未把三个底栏页面迁移到单 Activity/Fragment。
- 未缩短 700 ms/250 ms 常量来「修复」问题。
- 未关闭渐进加载、未隐藏加载圈、未清空已有网格、未忽略权限验证。
- 未用 `notifyDataSetChanged()` 替代精细更新。
