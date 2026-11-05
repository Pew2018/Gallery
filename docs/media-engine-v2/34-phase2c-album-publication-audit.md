# 34 — Phase 2C 主页目录扫描、排序与显示发布审计

日期：2026-11-05。仓库 `Pew2018/Gallery`，唯一允许分支 `refactor/media-pipeline-stability`。执行前 `git ls-remote` 与本地 checkout 均确认远程 HEAD 为 `2e6165dec230630a08a06c08d55baeef3bc5d251`；与任务所列历史参考一致。没有 reset。

## 1. 证据等级及基线

- **F**：由当前 `2e6165d` 源码、既有诊断事件或自动化断言直接证明。
- **R**：代码存在明确可执行的竞态或无效工作路径，但没有当前设备样本证明其用户影响。
- **U**：当前没有足够的请求日志/设备 Trace/真实数据分布来量化。
- 当前没有新版 Pixel 8 Pro 的 2C 请求日志。旧版本 5,285 项媒体元数据读取约 6.72 秒仅是旧版本样本，不能作为当前基线或对照。

## 2. 主页调用链与阶段边界

`MainActivity.getDirectories()` 在 UI 线程创建 `DirectoryScanEpoch` request、terminal gate、可选 `MediaPipelineDiagnostics`，异步读取目录 Room 缓存；缓存读取成功或失败回退后，`GalleryAlbumScanExecutor` 以不可共享的 `MediaFetcher` 进入 `gotDirectories()`。`gotDirectories()` 先生成并发布缓存预览，再逐个读取缓存目录，更新其统计并按 8 个真实改变的卡片发布中间列表；之后发现未缓存目录，以 Room transaction 分批写新目录和媒体；最后由扫描结果与 `finishDirectoryScan()` 发布终态。`setupAdapter()` 为 worker 输入建立目录快照，主线程读取最新 query 与排序设置，执行去重、排序、分组/可见集合与搜索过滤，再走既有 stable ID、DirectoryAdapter Diff 与滚动延期/锚点恢复。

缓存预览、中间批次、正式 COMPLETE、PARTIAL/FAILED/CANCELLED、用户排序/搜索，以及拖动/滚动延期是不同语义：缓存预览保证首屏；中间批次用于早期可见变化；最终目录集合须完成扫描；失效请求不能覆盖新列表；搜索必须使用当前 query；滚动和 ActionMode 期间 Adapter 保留既有延期策略。

## 3. 发布计数与重复工作

### 3.1 当前源码能证明的量

对一个正常 HOME request，缓存目录数量记作 `C`，目录数据真实改变数记作 `V`，后续发现且通过扫描的新增目录数记作 `N`。初始缓存预览固定有一次 `setupAdapter()`。缓存卡片在 `V` 次真实对象变化达到 8、16…时提交中间列表，末尾余数 `V mod 8 > 0` 时追加一次。只看这一段，setup 调用数为 `1 + floor(V / 8) + (V mod 8 != 0 ? 1 : 0)`，不含特殊受保护收藏显示分支、未缓存目录的发现批次及最终完成逻辑。每个 `V` 项都先经过完整目录读取和 `Directory` 字段比较；无变化的卡片不增加 `V`。

| 指标 | 当前可得结论 |
|---|---|
| 缓存目录总数 `C` | `dirs` 初始集合大小，尚未作为统一 HOME terminal summary 输出 |
| 缓存目录变化数 `V` | 源码通过 `directory == revisedDir` 跳过；无请求累计计数 |
| 新发现数 `N` | `foldersToScan` 逐项读取后分批加入 `pendingDirs`；缺少终态累计计数 |
| setupAdapter 提交数 | 初始一次；每 8 个真实卡片变化；末尾余数；特殊路径可能额外调用 |
| submit / commit | `album_adapter_submit` 与 `album_adapter_commit` 事件记录当前 request、项目数、`scanComplete`；异步 Diff 可能晚于扫描终态事件 |
| identical skip | `DirectoryAdapter.updateDirs()` 的同内容分支有 `album_identical_skip` 计数事件，不做 Diff |
| Diff 丢弃 / Diff 时长 | Adapter 的 `diffGeneration` 可使旧计算退出；`album_diff_commit` 计时为聚合观测，当前不与 HOME request 完整关联 |
| 排序/过滤耗时 | `album_presentation_prepare` 和 dedupe/sort/filter 分段计时已存在；仅设备诊断开启时有实际 Release 观察值 |
| 锚点、最终 COMPLETE | 代码保留 `FavoritesScrollRestorer`、request/generation 检查和终态 gate；缺少同一请求的 top path/offset 量化记录 |

### 3.2 问题 A 的结论

- **F**：目录缓存扫描阶段不是每个目录变化都提交；门槛为每 8 个变化。Adapter 能短路同内容；同数量但不同路径会由于路径 identity/content 集合检查进入更新。Adapter 的旧 diff generation 与 `isCurrent()` 会拦截过期提交；排序设置在 `setupAdapter()` 的 UI 发布阶段读取，而不是捕获扫描开始时的值。
- **F**：多个中间列表会为当前累计目录执行快照、去重、排序、可见项/搜索准备。发生真实统计变化时重复排序属于保持当前顺序所需工作。最终列表同内容时 Adapter 不做 Diff，但上游准备仍然发生。
- **R**：若多个 `setupAdapter()` 主线程任务排队、前一 Diff 尚未完成，新任务可能使旧 Diff 计算结果被丢弃。源码有 generation 拦截，但当前没有 `diffDropped / diffStarted` 的同 request 计数可证明浪费规模。
- **R**：扫描期间排序改变后的新快照按设置时排序，并由 Adapter `isCurrent()` 校验排序模式；需要设备并发注入验证排序菜单、搜索、滚动共同发生时锚点行为。
- **U**：真实 `C/V/N`、每次实际 submit/commit、Diff 丢弃、主线程毫秒、锚点偏移与 COMPLETE 成功率均未在 Pixel 新版诊断中采集。不能给出每请求性能百分比。
- **决定**：不延后所有阶段发布、不改 8 项节奏、不批处理至扫描终结，也不搬移 `setupAdapter()`。当前没有证据证明这些有效发布为无意义更新；挪动线程或合并阶段可能推迟用户可见目录或改变滚动行为。

## 4. 显示准备规模测试

新增 `DirectoryPresentationTest.presentationPreparationBenchmarkReportsSyntheticDirectoryScales`，使用生产 `getSortedDirectories()` 与 `getDirsToShow()`，为 100、500、1,000、5,000 合成目录运行名称、日期、大小、随机四种排序；同一计时窗口包含路径去重、可见集合准备与名称筛选，重复 3 次报告中位数。计时没有阈值断言，避免 Robolectric/JVM 负载导致伪失败；CI 日志记录每组单次测试环境样本，不视作 Pixel 主线程时延。它不执行 Activity 的完整搜索状态、RecyclerView Diff、滚动恢复和 adapter commit。

新增同类测试验证相同数量不同成员不是相同 presentation，以及 20 个变化的累计目录最终成员与名称排序保持一致。它验证生产展示辅助函数，不模拟 Android RecyclerView 和滚动手势。

## 5. A01–A07 审计结论

| 项 | 级别 | 结论 |
|---|---|---|
| A01 重复列表 | F/R | 有效批次排序重复是代码事实；同内容 Adapter 更新已短路。无无效 Diff 占比数据，暂不合并批次。 |
| A02 过期 Diff | F/R | Adapter generation 会使旧结果退出并只接受最新当前 Diff；丢弃计算的次数与成本未关联 request。 |
| A03 扫描中排序变化 | F | setupAdapter 在主线程读取当前设置，提交校验排序值；日期/大小需扫描统计，继续扫描正确。 |
| A04 滚动刷新 | F/R | 适配器可在滚动时延期，锚点仅恢复仍 idle 的列表；设备 top path/offset 未测。 |
| A05 同数不同成员 | F | `sameContents` 同时比较长度、位置、路径和可见内容；测试覆盖同尺寸成员替换。 |
| A06 旧 request | F | request epoch、`isCurrentDirectoryScan`、Diff generation 三处拒绝过期提交；没有端到端可控 UI race test，视为待补测试范围。 |
| A07 特殊来源 | F | 受保护目录在扫描入口跳过；收藏需关系表及 access policy；回收站独立结果；SAF/非物理源走 `getFilesFrom`；本轮未改变这些路径。 |

自动化覆盖方面，现存 `DirectoryAdapterRefreshTest`、`DirectoryPresentationTest`、`GalleryRefreshRegressionTest`、`FolderRefreshActivityTest` 与 Phase 1.5 Viewer/Room 保护测试需继续由完整 FOSS Release suite 运行。T01/T02/T03/T04/T05/T06/T07/T08 不是全部已实现的 Activity 级场景；本轮只新增生产 presentation 层的成员、批次最终排序断言，不把它们描述成端到端覆盖。
