# 31 — Phase 2B 排序并发与主页显示审计

日期：2026-11-05。分支：`refactor/media-pipeline-stability`。重新读取的远程 HEAD：`ad8155c73bcd542e9e565d83cb6444e9679b58bf`。阶段 2A 的实现基线与 28–30 文档相符：普通物理文件夹完整快照可后台重排；主页与其他媒体来源沿旧加载路径。源码证据分为 F（直接事实）、R（可复现风险）、U（尚无设备/生产负载证明）。

## 1. 普通文件夹异步排序 A01–A08

调用链：`MediaActivity.showSortingDialog()` → `resortCompletedPhysicalFolder()` → 资格策略 `MediaSortReusePolicy` → 在 worker 对 `Medium.copy()` 快照调用现有 `MediaFetcher.sortMedia()` → UI 线程校验并通过 `setupAdapter()` / `MediaAdapter.updateMedia()` 提交。

| 编号 | 审计结论 | 证据与边界 |
|---|---|---|
| A01 快速改变排序 | **R 已证实为可发布竞态** | 同一 Activity 可以并发执行多个后台排序。旧回调校验当前 request、目录、文件夹权限和排序配置，但没有区分同配置的两个操作。若同一模式重复选择，尤其随机排序，配置值相同不能证明任务较新。新增独立单调 sort operation generation。 |
| A02 A→B→A | **R 已证实** | 只比较 sorting 整数时，第一个 A 会与第三个 A 混淆。现在每次菜单确认生成新 token，旧 A/B 在 UI 发布点均被拒绝。 |
| A03 排序中刷新 | **生产检查已覆盖** | `getMedia()` 在建立新媒体请求时使排序 token 失效；旧任务还必须通过 `mediaLoadRequests.isCurrent(request)` 和 `completedMediaRequest`。新扫描维护自己的集合，排序快照不能回写。刷新与排序的 Activity 端到端时序由 request/generation 双门控；需设备复核。 |
| A04 撤权 | **生产检查保留** | 受保护文件夹仍校验 `folderUnlockSessionId`、会话活动状态以及提交前最新 `folderReadResult.accessCurrent()`。权限不由缓存或排序 token 证明。设备撤权矩阵为 `PENDING_DEVICE`。 |
| A05 搜索变化 | **不捕获旧搜索结果** | 排序保存完整媒体集合；发布经当前 `mLastSearchedText` 进入既有 `searchQueryChanged()`，该函数用 search generation、当前 request 和当前查询校验 Diff 提交。测试/代码没有把搜索列表快照用于排序。 |
| A06 随机 | **修复范围** | 同一 `SORT_BY_RANDOM` 值的连续操作现在也拥有不同 generation；随机仍调用既有生产 `shuffle()`，无固定种子。 |
| A07 Diff/滚动 | **现有保护保留** | 未关闭 DiffUtil，未改 stable ID、滚动延期提交或 Adapter diffGeneration。`MediaAdapter` 仍只允许最新 diff 提交；当前路径交接来自已提交媒体列表。 |
| A08 异常/拒绝/销毁 | **恢复路径补齐** | 排序异常和执行器拒绝都回到 UI 线程；若操作/request/目录/排序仍当前，则退回原 `getMedia()` 正常加载，不留 busy 标志。销毁或旧操作时只丢弃回调。Activity 销毁、访问撤销和失败均未通过真机故障注入。 |

新增 `MediaSortOperationGateTest` 以可控的逆序完成模型验证 A→B→A 和同模式随机排序，另验证刷新/Viewer 暂停/访问变化可使待处理 token 失效。它测试生产使用的 generation gate，不冒充 Robolectric Activity 全链路或真机压力测试。`MediaSortReusePolicyTest` 继续证明仅 COMPLETE、当前、未分组普通物理文件夹可复用；PARTIAL、活动扫描、旧 request、权限无效及聚合来源走原加载路径。

## 2. 主页目录排序与显示准备

### F：源码可确定

- `MainActivity.showSortingDialog()` 对拍摄日期、修改日期和大小排序调用 `retireDirectoryScan()` 与 `getDirectories()`；这些模式目前重新执行目录扫描。姓名/路径/自定义等目录字段可用模式调用 `refreshDirectoryPresentation()`，不主动重新枚举目录。
- 新扫描的 `gotDirectories()` 逐目录调用 `MediaFetcher.readFolder()` / `getFilesFrom()`，按当前目录排序与分组需要更新日期、大小字段，并保持 `.nomedia` 文件系统补扫和授权检查。现有目录字段没有可证明外部媒体变化新鲜度的完整性戳；本轮不把它们作为省扫描依据。
- `setupAdapter()` 在 UI 线程执行 `distinctBy`、`getSortedDirectories()`、`getDirsToShow()` 和搜索过滤；调用者先制作 Directory 快照。Adapter 仍由 `DirectoryAdapter.updateDirs()` 计算异步 Diff，结果 generation、ActionMode/滚动延期和路径锚点恢复机制保留。
- 已有计时 `album_presentation_prepare` 覆盖 UI 侧去重、排序、可见集合筛选与搜索准备；`album_diff_commit` 覆盖后台 Diff 到 UI dispatch 的阶段。新增诊断开关开启时的 `album_presentation_dedupe/sort/filter` 分段计时；`album_sort_requested` 与 `album_sort_to_commit` 分别记录选择及最终可见提交（包含日期/大小模式所需的完整扫描等待）。`album_adapter_submit/commit` 和 `album_identical_skip` 提供数量/重复提交证据。

### R：需要通过数据判定

主线程排序是 O(n log n)，目录复制、去重、筛选会随目录数量增长；在没有目录规模、分段耗时、Diff耗时样本之前，不能说它已超帧预算或某一排序值得迁移到后台。同数量不等于同成员，`DirectoryAdapter` 的内容 diff 和稳定路径 ID 不可省略。扫描中的排序通过继续运行同一请求并在后续批次使用最新设置，不能用部分列表替代最终目录集。

### 优化与安全边界

本轮仅添加计时，未把 `setupAdapter()` 搬到后台，也未减少排序模式触发的扫描。只有未来数据证明主线程准备超预算、且目录统计有可信版本或同请求完整读取，才可抽离纯转换；发布必须继续校验目录 scan epoch、排序、搜索、权限、请求/目录版本，并走原 Adapter Diff 和滚动锚点。当前可测设备样本为 0，主线程实际耗时与锚点变化仍 `PENDING_DEVICE`。

## 3. Viewer 与媒体元数据边界

阶段 1.5/2A 既有完整性与发布 gate 仍在当前源码中。排序没有触碰 Viewer 临时窗口、grid order handoff、`.nomedia` 文件系统枚举或跨阶段鉴权。旧设备日志中 5,285 条 metadata 读取汇总约 6.72 秒（仅旧版本单次样本）；日志没有按目录、SAF provider 或系统调用类型拆分。start/end 日志成对计数且未识别请求内同路径重复物理读取证据。该旧样本不属于当前源码性能结果，元数据快路径继续保持原样，后续测量需用新 APK 请求级、路径匿名计数。

## 4. 性能状态

- 普通文件夹排序：控制流证明符合资格时不会启动 `getMedia()` / 新扫描；后台排序与 Adapter 更新仍执行。点击到显示的真机时延、Adapter bind 和帧时间 `PENDING_DEVICE`。
- 主页目录：新补充计时点已进入源码，但还未在 Android 设备采样；当前记录为 `PENDING_DEVICE`。
- 5,000 项 PhotoProfile、多来源文件系统补扫、设备 Room 查询：未在真机测量，均 `PENDING_DEVICE`。
- 本轮无新版 Pixel 8 Pro 日志，不复用历史日志冒充前后测，也不计算 p50/p95 或性能提升百分比。
