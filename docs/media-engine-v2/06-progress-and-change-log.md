
**Phase 1.5 最终CI交付**：源码 `026a943a445619407134394bf434b753c65695e2`；[Run41975453251](https://github.com/Pew2018/Gallery/actions/runs/41975453251) completed/success；完整886项单测、ARM64构建、包名/ABI/v2签名与实际下载哈希核验通过；Artifact13391393642。Pixel连续浏览和性能前后复测仍PENDING_DEVICE，不等于所有P0真机验收通过，不启动原阶段2。见[27](27-phase1-5-validation-and-handoff.md)。

## 2026-11-05 — Phase 1.5 Pixel真机日志驱动的正确性修复

在 `refactor/media-pipeline-stability` 最新远程 `7670a10a…` 上审计A/B完整日志，保留旧Activity取消所有权，用当前Viewer重新验证已提交候选扩展邻域；明确grid commit版本、partial/random与COMPLETE权威排序，保留live path及权限/revision；合并阶段发布并移除逐文件默认timing。审计、实际代码/测试和交接见 [25](25-phase1-5-device-log-root-cause-audit.md)、[26](26-phase1-5-viewer-consistency-fixes.md)、[27](27-phase1-5-validation-and-handoff.md)。CI以27的真实结果为准，Pixel所有复测仍PENDING_DEVICE；原阶段2–4不提前实施。


## 2026-11-05 — 新 Phase 1（工作分支 `refactor/media-pipeline-stability`）

- 审计远程 HEAD `5cc20adf6875ca220df8db4ff3bb24e42f82de22`，阶段 0 的 CI Run 41951185632 成功、但尚无设备数据，不能用于声称阶段 1 成功。
- 当前定点改动：诊断 -1/0 区分、取消/导航/分类过期诊断收尾、拒绝执行显式失败终态、工作线程旧媒体快照数据所有权；保留业务 epoch、TerminalGate、.nomedia/权限/缓存清理与 Viewer 语义。
- 回归：诊断单测新增三个；目录代际/超时一个；bounded executor 拒绝/再次入队一个；部分来源完整性在既有测试追加断言。所有新增测试不依赖 Thread.sleep。
- 状态：GitHub Actions 完整 FOSS Release 与 ARM64 APK **以实际运行结果为准**；真实设备场景 `PENDING_DEVICE`，阶段 2 暂无定量性能依据。
- 详见 [22](22-phase1-lifecycle-audit.md)、[23](23-phase1-implementation-and-tests.md)、[24](24-phase1-results-and-handoff.md)。


# 06 — 历史精简修复与新阶段进度说明

> **2026-11-05 校订**：原文中的“当前任务”“唯一 fix 分支”和 P1/P2/P3 均是 2026-11-04 历史开发记录，不再是现行工程指令。现行权威方案：[18](18-current-media-pipeline-roadmap.md)、[05](05-minimal-scan-sort-display-refactor.md)；工作分支 `refactor/media-pipeline-stability`；基线 `main` `49e799eceab00414ef074cbf71b7f0a35133b669`。
>
> **新六阶段状态**：阶段0、1、1.5均有完整CI/ARM64交付；1.5真机仍`PENDING_DEVICE`。Phase 2A 已有成功CI与ARM64 Artifact（Run 41983357685，889项）；当前 Phase 2B 在 `ad8155c73bcd542e9e565d83cb6444e9679b58bf` 上进行排序代际、主页显示计时和 Room v11 执行计划审计/修复。最终成绩以33为准，用户设备测量未完成。历史 `main` Run 41939577702 不是当前代码或真机性能基准。

## 1. 新阶段状态总览

| 阶段 | 当前状态 | 进入下一阶段的要求 |
|---|---|---|
| 0 诊断与性能基准 | 诊断源码与 CI 完成；真机数据 PENDING_DEVICE | 请求链路、查询/枚举/数据库/排序/UI 耗时及同设备 p50/p95 |
| 1 扫描生命周期与正确性 | 源码/CI 已通过；真机 PENDING_DEVICE | 旧结果拒绝、删除/权限并发、正确终态、.nomedia 同步 |
| 2 数据库与排序热点 | 2A 已完成；2B 实施/验证中 | 2B 的完整异步排序 gate、合成 Room v11 query plan；真机时延仍 PENDING_DEVICE |
| 3 缓存复用和增量索引 | 未开始 | 外部变化检测、正确失效和无法证明时安全回退 |
| 4 缩略图/网格/Viewer | 未开始 | 身份、顺序、连续滑动、删除恢复和滚动稳定 |
| 5 大库压力与跨 Android 回归 | 未开始 | Actions、ARM64、设备权限矩阵和实测性能 |

旧精简阶段在历史 SHA 上曾经通过若干 CI 记录，仍保留下文供溯源；**不把原 CI 成绩或 8–16 人日估算计入新阶段**。

## 2. 原 fix 阶段日志规范（历史）

- **时间、分支、起始/结束 SHA**：先核对远程最新 HEAD；不得以历史 SHA 替换新提交。
- **目标与验收**：用户提出的具体异常、修改范围、明确不做的项目、对应 A01–A08。
- **收集和收敛的数据**：代码证据链接与行号、现象、调用关系、运行日志/测量（如有）、事实 F / 推断 I / 未验证 U、排除路径。
- **实际改动**：逐文件列出行为变化、向下兼容说明、是否新增/删除组件；仅文档时写“无业务代码修改”。
- **验证与构建**：实际运行的测试命令/通过失败、GitHub Actions run、APK 是否存在；未执行必须明确写未执行。
- **剩余问题/后续计划**：记录风险和下轮建议。每次代码提交同步提交本文件的日志与阶段进度。
- **状态定义**：未开始 / 进行中 / 已提交待验证 / 已验证完成 / 已阻塞。任何“已完成”都必须附实证。

## 3. 已有记录

### 2026-11-05｜Phase 2A — 热点审计与完整文件夹排序复用

- **分支/起始SHA**：仅 `refactor/media-pipeline-stability`；重新读取远程HEAD `500f327a534832b83e872babc26ef32729e07867`，未重置。
- **审计结论**：旧Pixel日志中5,285项metadata 6,720ms、排序99ms属于Phase1.5修复前样本，不能代表当前性能。当前物理补扫每个候选仍须核对实体/可读状态；Photo阶段间校验承担不同发布时点授权职责；没有安全依据合并。Room SQL/EXPLAIN与目录主线程耗时没有实测。
- **实现**：完整、当前、非分组的普通物理目录排序改为复用`mMedia`正式快照，后台排序，再以原Adapter/Diff提交；不清Adapter、不创建扫描task。PhotoProfile、收藏、首页、回收站、PARTIAL/加载中或失权时保留旧加载路径。未修改Viewer、Room Schema、`.nomedia`扫描和UI样式。
- **测试/验证**：新增3项生产策略单测；本地Wrapper下载Gradle 9.5.1因Network is unreachable未启动。最终Actions Run 41983357685在SHA `dcd41024e06451eb7a46d28db18a84157cd5102b`通过889/889完整单测与ARM64校验。
- **最终交付**：Artifact ID 13395436120，临时CI签名；准确版本、ABI、哈希和未测边界见[30](30-phase2a-results-and-handoff.md)。阶段1.5 Pixel真机可靠性验收仍`PENDING_DEVICE`。

### 2026-11-05｜Phase 2B — 排序交互可靠性、主页目录和 Room v11 审计

- **基线**：开始前读取 `origin/refactor/media-pipeline-stability` 为 `ad8155c73bcd542e9e565d83cb6444e9679b58bf`；没有重置或触碰其他本地工作区中的提交。
- **审计发现**：普通文件夹后台排序只有配置值校验，重复随机排序和 A→B→A 没有独立操作身份，旧 worker 可以与新选择同值并竞争发布。主页拍摄/修改日期和大小会走目录扫描；`setupAdapter()` 的去重/排序/筛选/搜索位于主线程。DAO 源码表明 `parent_path`、`deleted_ts`、`is_favorite` 无索引，full_path/path 持有 Room 唯一索引。
- **实施**：为物理文件排序新增独立单调 generation；新媒体请求、Activity 暂停使其失效；后台排序异常/拒绝在当前请求仍有效时退回原加载链。保留查询时的当前搜索、授权复验、DiffUtil/stable ID、滚动延期和完整扫描。主页新增可选诊断分段计时与排序确认到可见提交时间；没有绕过必要扫描。新增 Room v11 实际 Room schema in-memory 测试，造数 100/5k/30k/100k，并检查计划、媒体筛选/收藏关系与多文件夹集合。
- **测试/性能边界**：本地 Wrapper 下载 Gradle 9.5.1 因 `Network is unreachable` 未能运行测试；完整测试与数据库计划输出由当前 SHA 的 Actions 验证。合成 Room 时间不是 Pixel / 用户数据库数据；主页排序、PhotoProfile 5k、SAF 和 `.nomedia` 新版性能为 `PENDING_DEVICE`。没有新增索引/Migration，也没有性能百分比结论。
- **文档**：[31](31-phase2b-sort-concurrency-and-ui-audit.md)、[32](32-phase2b-database-query-plan.md)、[33](33-phase2b-implementation-and-validation.md)。Actions、提交 SHA、Artifact 与新增测试最终结果以33更新为准。

### 2026-11-04｜R00 — 为精简重构建档（仅文档）

**目标**：按用户最新指令，把原“大型媒体引擎 V2”实施口径收敛为扫描、排序、显示三项，保留 Fossify Gallery 成熟底层；记录当前进度为尚未开始；为 fix 分支后续工作建立强制记录方式。

**工作分支**：`fix/photo-viewer-five-item-boundary`  
**建档前 HEAD**：`0cfbb9bc41a011ca16101a6c9186f18eca14b82d`  
**对照审计 SHA**：`c5cc5b6852291491a999934d12f26314455959c6`

**本轮资料与收敛结论**：
- F：01–04 已记录现有 MainActivity/MediaActivity/MediaFetcher/PhotoProfileMediaLoader、Adapter、Viewer 的静态数据流与兼容约束。
- F：上述源码中已有取消、DiffUtil、滚动锚点、访问校验等功能，不需全部重新设计。
- I：小规模整理请求调度、排序时机和 UI 发布策略可能比全面替换引擎更经济；是否足以修复全部用户反馈需要后续测试。
- U：没有当前设备重现、耗时、帧率、队列容量、卡片计数与真机权限数据。本次无新的运行时指标。
- 范围收敛：旧 V2 大规模迁移计划不执行；以 [05](05-minimal-scan-sort-display-refactor.md) 为当前唯一开发边界。

**本轮修改内容**：新增 `05-minimal-scan-sort-display-refactor.md`、`06-progress-and-change-log.md`，并更新本目录 `README.md` 索引；**不修改任何 Kotlin/XML/数据库/设置/测试/CI 代码**。

**验证**：当前轮只进行远程文件内容核验及 Git 差异核验；无编译、无 Actions、无 APK、无真机回归。**代码修复进度仍为 0%。**

**遗留风险**：用户之前报告的刷新索引不结束、列表闪烁、`.nomedia` 数量不一致和滑动边界是否在当前 fix 最新 APK 复现，尚待设备验证。下一轮先确立可复现用例和真实基线，再仅对明确责任点实施 P1。

**本次文档提交 SHA**：由本次 GitHub 提交生成；请以 Git 历史为准，避免在提交内预填未知 SHA。

---

## 4. 历史模板（新分支进度以 18 为准）

### YYYY-MM-DD｜Rxx — 简短任务名

- **目标/验收编号**：
- **分支、起始 SHA、结束 SHA**：
- **用户反馈/复现步骤**：
- **事实 F（来源、固定 SHA、文件/行号）**：
- **推断 I（原因候选、已排除项）**：
- **未知 U（待采集数据）**：
- **修改文件与逻辑（没有修改写“无”）**：
- **影响及兼容范围（功能/权限/备份/样式）**：
- **测试与数据（测试命令、结果；未执行写“未执行”）**：
- **GitHub Actions / APK / 真机验证（实际状态和链接）**：
- **遗留问题、风险与下一步**：
- **提交记录及关联链接**：

> 更新注意：每次报告前都核对本文件和真实分支 HEAD；**任何代码修改都必须同步留下完整记录**，且禁止仅凭 CI 成功宣称设备问题解决。

## 5. 新增开发记录

### 2026-11-04｜R02 — 扫描生命周期与渐进显示稳定性精简

- **目标/验收编号**：阶段二；对应 05 S1/S3、S2/D2 及 A01/A02/A03/A04/A08。保留既有读取器、请求身份、适配器差分和 viewer 交接；减少确认无效的重复提交，并确保分类缓存异常不会阻止正式扫描。
- **分支、起始 SHA、结束 SHA**：仅 `fix/photo-viewer-five-item-boundary`；开始时远程 HEAD `a80f191987f0c2de978cf43ed289598092d529aa`；Actions 验证代码 SHA `0b70939655c738a2c8b6ed2c5de869cb735638c8`。本轮验证后文档闭环提交以 Git 历史中的最终 HEAD 为准。
- **源码证据 F**：`MainActivity.loadCategorizedFolders()` 在分类缓存读取错误时原先直接结束加载，未运行 `CategorizedFolderScanner`（起始 SHA 的 `MainActivity.kt` L1424–1497）；扫描器对已知目录先普通 `publish()`、随后无条件 `publish(force = true)`，且发现遍历中每个 seed 都会请求进度发布（`CategorizedFolderScanner.kt` L41–53、L127–148）。`DirectoryAdapter.updateDirs()` 已对相同列表短路，但 Activity 每次仍先复制、过滤和排序（`MainActivity.kt` L2603–2638；`DirectoryAdapter.kt` L968–995）。照片配置公开 filesystem batch 经授权校验、累计去重/排序后每批仍提交 `offerPartial` 与 UI stage；权限过滤可能使可见累计媒体未变（起始 SHA `PhotoProfileMediaLoader.kt` L181–198）。`MediaAdapter.updateMedia()` 已按当前内容契约短路相同列表、只保留一个在途 diff 和最新 pending 列表（`MediaAdapter.kt` L710–779）。
- **推断 I / 已排除项**：分类相同快照会重复进入 Activity 复制/排序/适配器调用路径，但 Adapter 层通常已短路；这是无效上游工作，不是已证实的大面积动画根因。照片配置累计快照每批均发布是代码事实；被权限过滤后的批次可造成相同可见快照重复进入 viewer/grid 交接，可通过内容等价策略排除。未发现需替换 epoch、终态门、请求 generation、取消、watchdog 或 Adapter DiffUtil 的缺口；没有真机帧/trace 证明扑克牌式动画仍存在。
- **未知 U**：目标设备上的实际扫描重复次数、Adapter commit/rebind、像素锚点漂移、动画帧、MediaStore/磁盘次数、时延均未采样；权限收窄时现有页面清理和实际系统媒体权限行为尚待真机复核。
- **修改文件与逻辑**：`MainActivity.loadCategorizedFolders()` 捕获当前目录快照，目录缓存读取异常时以该快照为候选并继续分类文件系统/MediaStore 扫描；执行器拒绝仍走原失败终结路径。`CategorizedFolderScanner.scan()` 在 150ms 节流后以 `DirectoryPresentation.sameContents()` 去重，只向 UI 发首个或实际变化结果；Activity 的最终扫描结果提交不变。`PhotoProfileMediaLoader.loadStaged()` 在向 Viewer partial store 与 Activity 回调前，依据 scope/revision、访问摘要、媒体顺序/身份和 `MediaUpdatePolicy.sameContent()` 消除重复可见 stage；首次有效预览和最终 COMPLETE 仍保留。
- **测试**：扩展 `FolderRefreshActivityTest`（分类缓存 Room 读取注入失败仍可发现 `.nomedia` 目录媒体）、`NoMediaCategoryRefreshTest`（分类扫描不重复发相同快照）、`PhotoProfileMediaLoadingTest`（stage 比较沿用 Adapter 可见内容契约，且真实多批 `.nomedia` 阶段不重复可见快照）。本地定向 Gradle 命令因 wrapper 下载 Gradle 9.5.1 时 `Network is unreachable`，未启动测试；等待本轮 Actions。
- **未修改范围**：无 Adapter / Viewer / DB / 用户设置 / 备份格式 / 扫描调度器重写；不关闭动画。权限、`.nomedia`、受保护来源及随机顺序校验维持原调用路径。
- **Actions / APK / 真机**：Run #463 (`41751856059`) 成功，完整测试 789/0/0/0，ARM64 APK artifact 已生成并对应 SHA `0b70939655c738a2c8b6ed2c5de869cb735638c8`；无真机/模拟器。
- **CI 修正记录**：首个 Run #462 (`41750986403`) 在 Kotlin 解析前失败，未运行测试，根因是首次提交传输时大型 `MainActivity.kt` 被错误编码/截断；已从正确源码恢复，并用本地 Git blob SHA 与 GitHub blob SHA 一致校验后，以快进修复提交 `0b709396…` 重跑。该失败属于交付传输错误，不是测试断言或应用根因；最终验证以 Run #463 为准。
- **遗留与下一步**：需要设备录屏/AdapterDataObserver 或 Perfetto 验证列表闪动和锚点体验。未采样前不宣称视觉/性能已改善。
- **文档与提交**：代码/测试/阶段记录先提交，校验后补录 08、09、README 和本日志；详细证据与验收见 [09](09-refresh-publication-stability.md)。

### 2026-11-04｜R01 — 照片配置渐进扫描重复排序精简

- **目标/验收编号**：按 05 的 O2、D2 和 A04/A08，减少照片配置 cache preview 与文件系统批次进入界面前的冗余排序，同时保持候选验证、逐批可见、累计展示顺序、完整结果和 Viewer 交接语义。
- **分支、起始 SHA、结束 SHA**：仅 `fix/photo-viewer-five-item-boundary`；本轮起始远程 HEAD `27b488a88de9ed105c3ed6fdbe3931249dabbdb9`。最终源代码 SHA `5a9d4377038d6d27b3175f2569293ec205005d3f`（两笔提交，均仅在指定分支）。
- **用户反馈/复现步骤**：用户报告刷新、排序和显示管线复杂及过度动画；本轮未连接设备，不能确认历史视觉症状当前仍复现。
- **事实 F**：`PhotoProfileMediaLoader.loadStaged()` 先排序 cache 全集，再对 preview 子集校验并排序；`preview()` 截取有序子列表且 `PhotoAccessValidationBatch.validate()` 仅过滤、不重排。公开配置的每个 filesystem batch 被 `result(FILESYSTEM, batch)` 排序，随后并入 `supplements` 后累计再排一次（当前文件约行 129–139、185–199）。`MediaFetcher.getMediaInFolder()` 每 120 项或 200ms 产生批次（当前行 513–521）。受保护混合配置有独立的授权预览策略（loader 行 177–184）。
- **推断 I**：cache preview 和公开配置的每批首次排序都不会影响最终可见排序，只增加排序工作。此次保留最终累计排序和发布频率，不推断其能解决大面积动画。
- **未知 U**：每次刷新实际批次数、排序耗时、Adapter commit/rebind 数和帧位移均无设备采样。
- **修改文件与逻辑**：`PhotoProfileMediaLoader.kt` 的 cache preview 与普通公开 filesystem batch 校验均保留输入顺序；普通批次累计合并后仍按既定排序设置排序一次再发布，受保护混合配置保持原处理。扩展 `PhotoProfileMediaLoadingTest`，新增缓存 preview 顺序测试，并为 260 项 `.nomedia` 来源检查每个渐进阶段和最终结果。
- **影响及兼容范围**：不改读取来源、文件枚举顺序契约、去重、授权、缓存、分阶段发布、随机排序策略、用户配置、DB/备份或 Viewer。无数据库/格式/UI/权限改动。
- **测试与数据**：本地定向命令 `./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.PhotoProfileMediaLoadingTest --stacktrace` 未能启动；Gradle Wrapper 下载 `gradle-9.5.1-bin.zip` 因环境网络不可达失败，未执行测试。Actions 与完整测试状态见 08。
- **GitHub Actions / APK / 真机验证**：Run `41684331467` 成功，Run URL 见 08；786 tests、0 failures/errors/skipped；ARM64 APK artifact 已生成。无真机/模拟器，真机结论待验。
- **遗留问题、风险与下一步**：滚动时闪烁/跳跃、`.nomedia` 主页计数同步、扫描实际重复次数及五张以上 Viewer 双向浏览需完整自动化/设备验证。本轮结论只覆盖重复排序的代码路径。
- **提交记录及关联链接**：`315c164a1d563d166c74b8e0389eb1d1cfe17730` 与最终代码/验证文档提交 `5a9d4377038d6d27b3175f2569293ec205005d3f`；最终提交及 Actions 见 08。


### 2026-11-04｜R03 — 显示层稳定性与滚动锚点精简

- **目标/验收编号**：第三阶段，A03/A04/A06/A07/A08；无变化快照不产生 Diff/通知，完整终态仍被处理，用户新拖动不得被过期恢复覆盖。
- **分支与 SHA**：仅 `fix/photo-viewer-five-item-boundary`；审计起始远程 HEAD `c9e2c693ddec0c9d430c7607d67c9f26345bd3c3`；实现及 Actions 验证 SHA `a8c1f45480179b030ccb248ba24ebc2098c98109`。
- **静态证据**：MediaAdapter 原先仅 `incoming == media` 时避免 Diff，而 Diff 内容判断已排除 `id`、`gridPosition`、`mediaStoreId`，造成语义分叉；DirectoryPresentation/DirectoryAdapter 使用包含 Room `id` 的 data class equality；MediaActivity.publishMedia 对同内容仍增加搜索 generation、捕获滚动锚点和刷新菜单，随后 Adapter 入队；FavoritesScrollRestorer 的 pre-draw 恢复不监听拖动，收藏页恢复 current 条件不检查滚动状态。
- **排除项/安全边界**：Adapter 构造才安排初始 `scheduleLayoutAnimation()`；现有 MyGridLayoutManager 原位修改；RecyclerView itemAnimator 已为 null。所有 adapter 清空调用为授权失效、来源/视图/排序/分组/搜索条件变化、空结果或访问错误等有语义场景，未作全局删除。照片配置 retain 继续要求 display key 与 PhotoContentRevision 相同；revision 变化仍立即清网格。COMPLETE 状态、媒体缓存写入、Viewer/收藏目录交接未跳过。
- **修改文件**：`MediaUpdatePolicy.kt` 新增有序媒体/Section 可见内容比较；`MediaAdapter.kt` 同内容只换内部快照并执行 commit callback；`DirectoryPresentation.kt` 忽略不呈现的 Room id；`DirectoryAdapter.kt` 复用一致目录内容比较；`MediaActivity.kt` 仅在照片可见数据变化时增加搜索 generation、保存配置滚动锚点和更新媒体快捷菜单；`FavoritesScrollRestorer.kt` 新拖动清除旧 anchor，滚动中不执行旧恢复。详见 [10](10-display-scroll-stability.md)。
- **测试**：新增媒体技术字段变化不排队 Diff/不发 Adapter observer、真实 metadata/order 变化仍入队；目录 id 变化无通知但 cover/count 变化会 Diff；滚动恢复排队后拖动撤销、滚动中刷新不保存 anchor。完整 FOSS Release Actions 已覆盖所有测试并通过。
- **本地验证**：定向命令尝试 `./gradlew :app:testFossReleaseUnitTest --tests ...`；Wrapper 下载 Gradle 9.5.1 时 `java.net.SocketException: Network is unreachable`，未启动测试。`git diff --check` 通过；修改的大文件 UTF-8、括号/大括号配对和 diff 删除范围已检查。
- **Actions / APK**：Run [#464 / 41755685039](https://github.com/Pew2018/Gallery/actions/runs/41755685039) 成功；完整 FOSS Release 测试 796/0/0/0；ARM64 Release assemble、签名、包名、ABI 检查成功。Artifact `gallery-foss-arm64-v8a-release`，ID `13287977148`，SHA-256 `e928b193eef55c32bed59b599477c3190f322c66635c50573bd6bec4ba3b2d34`；工作流使用临时 CI 签名，因此不能用作覆盖安装的签名兼容性证明。测试报告 Artifact ID `13287643559`。13 个改动文件 GitHub blob SHA 均与本地 `git hash-object` 完全相同，最终远程树 SHA 与本地源码树一致。
- **真机**：当前无真机、模拟器、Adapter 运行时 trace 或 Perfetto；动画、实际重绑数量、锚点像素偏移、fast scroller 同步仍待设备验证。
- **后续建议**：优先在目标设备按 [10](10-display-scroll-stability.md) 采集刷新、拖动、惯性滑动和全屏返回数据。没有新增证据前不建议继续改写 Adapter 或媒体引擎。

### 2026-11-04｜R04 — 收藏首屏、Photos 完整目录交接与排序集中修复

- 起始远程 HEAD：`c6da3c5cfd7c30e3635f625bc644a837badb8018`；仅现有 fix 分支；集中一次主要提交，不建 PR、不合并 main。
- 完整审计记录、六个业务文件修改、自动化覆盖、权限边界、性能与真机待验项见 [11-favorites-viewer-sorting-fixes.md](11-favorites-viewer-sorting-fixes.md)。
- F：收藏扫描在首次发布之前；首次布局单项提交可取消先到完整目录；相同 COMPLETE 重复提交；渐进目录绕过排序。已定点修复，保留原 scanner/store/coordinator/Adapter，没有新增通用框架。
- 本地 diff 检查通过；Gradle distribution 网络不可达，测试未启动。完整单测与 ARM64 Actions 待提交后验证；无设备帧数据，不宣称流畅度或用户真机故障已经完全验收。
- R04 首次 CI：Run `41760580210` 已完成业务 Kotlin 编译，但新增 `PhotoViewerInitializationTest` 漏导入 `RobolectricTestRunner`，在测试源码编译失败，单测未运行、无 APK。追加最小测试导入修复；不删除断言，随后重跑完整 CI。

- R04 最终验证：源码 `8bc6c617e2756cd5dd3c1e51ae4a1b9a3e9e2826`；Run [41760914157](https://github.com/Pew2018/Gallery/actions/runs/41760914157) 全部成功。803 tests / 0 failures / 0 errors / 0 skipped；ARM64 Release assemble、包名/签名/ABI 检查通过，APK artifact `gallery-foss-arm64-v8a-release` ID `13290472340`，测试报告 ID `13289949744`。APK SHA-256 `a020cc1a128133b3060a7793e87c2d1d0d8da9d7732d36f90d06eb0d7248d579`。临时 CI 签名，非覆盖安装兼容证明。所有测试断言保留。
- R04 交付状态：收藏首屏和排序已修复且自动化验证通过；Viewer 目录交接与无效更新代码完成、自动化通过，真机手势/缩放/视频播放和性能仍待验。详细逐项结论与完整产物链接见 11。本次闭环仅补验证文档，不触发额外 APK，不改变已验证业务源码。

### 2026-11-04｜R05 — Photos 会话顺序、滚动发布与目录确定性

- 唯一目标改为用户指定的 `feature/photo-session-seamless-viewer`；起始远程 HEAD `ee6597a16e4a329343e24463dcbb0352f39e9d37`。原 fix 和 main 均不修改、不推送。
- 完整证据、九处业务修改、既有测试扩展与 A01–A11 验收见 [12](12-seamless-viewer-order-scroll-sorting.md)。保留扫描器、初始化 generation、拖动发布gate、邻近五项授权、DiffUtil和渐进首屏；没有新线程池/框架。
- 确认问题：Photos点击缺已提交grid顺序；COMPLETE segmented排序依赖可淘汰ordinal并用-1；Adapter同值短路吞显式内容失效；普通目录同值比较无稳定tie；收藏show_all始终显示；Photos滚动延期发生在上游全量处理之后。
- 本地定向/完整测试尝试均因Gradle distribution网络不可达未启动；diff检查通过。本轮完整Actions待提交后验证；缺少设备帧率数据，不报告FPS/百分比。

- R05 首轮 Run `41766932465`：业务 Kotlin 编译通过，新增多来源测试 Favorite 构造参数错误导致测试编译失败；无单测/APK成功结论。最小补修构造、LRU夹具与遗漏的生产网格延期测试，并保留授权摘要变化时必要菜单刷新；完整重新验证中。

- R05 最终复核：COMPLETE窗口锚点用已有ordinal避免淘汰后的全目录定位；将Photos部分/最终更新的必要锚点统一留在publishMedia，一次实际变化只捕获一次；补自定义/置顶顺序显式用例。等待最终源码完整CI，不以较早Run代替。

- R05 第二轮 Run `41767514582`：814项、2失败，新增测试的计算属性反射及缺AppCompat inflater factory，已按生产状态修正。补齐worker→UI媒体行所有权快照（初始Adapter原为浅clone），保留所有断言；等待下一次完整CI和APK，不引用失败Run作成功证据。

- R05 解码缓存交接发现明确遗漏：Fragment失效版本未进入Medium图片缓存键，SVG/视频预览未用媒体签名。以既有失效registry/getKey最小接入，并补键一致性测试；不修改解码器。最终自动化必须对应包含此补修的源码。

- R05 `6aa62e3…` 的Run `41768645616`成功817/0/0/0及ARM64。但最终联合审计发现旧grid与新分段ordinal反向时，局部重排会与扩展方向冲突；以最小方向一致性检查在无法继承时回退权威排序并记录revision（用户允许的fallback）。20,501纯顺序及30,001实际分段窗口联合断言补齐；最终CI以随后源码为准。

- R05 最终闭环：业务源码 `fa944261dccbf1e4ece4d012a344925c04240d3a`，Run [41769940653](https://github.com/Pew2018/Gallery/actions/runs/41769940653) completed/success；818/0/0/0，ARM64、apksigner v2、包名和仅arm64 ABI校验成功，APK artifact13294711899，测试报告13294875919。APK SHA-256 `ab7f927c5fa26e9354e2cc4629a5950f31eb9e6bbbbdd98f64c86ef58884a90a`。main及原fix最后核对均保持开始SHA。完整Git/历史Run/性能口径/四项结论/A01–A11矩阵见[12](12-seamless-viewer-order-scroll-sorting.md)末节；后续仅补验证文档。临时CI签名不保证覆盖安装；真机触控、解码、性能与系统权限矩阵仍待验。
### 2026-11-04｜应用内诊断日志

- 在 `fix/photo-viewer-five-item-boundary` 增加设置→高级→性能→诊断与日志，默认关闭，开启最长 24 小时；支持复制最近 250 KiB、ACTION_CREATE_DOCUMENT 导出 TXT、确认清空及只读状态。
- 新增 app-private 单线程有界日志写入，约 5 MiB 循环文件、字段/堆栈路径隐私过滤、session-salted 媒体关联 ID、未捕获异常委托记录和 Android 11+ 退出原因摘要。保留既有 FolderRefreshTrace/ GalleryPerfTrace Logcat 行为；详细设计和限制见 [诊断功能说明](../diagnostics/app-diagnostics.md)。
- 已接入扫描/刷新关键边界、GalleryPerfTrace 已有事件、Viewer 打开/页绑定/关闭和网格/全屏删除请求结果。未记录媒体路径、URI、文件名、照片内容或逐帧事件。
- 完整验证：源码 `82c4b4ffe26b80cdb6c6b6303ce2a9488998dad4`；Run [41858925567](https://github.com/Pew2018/Gallery/actions/runs/41858925567) 成功，848 tests / 0 failures / 0 errors / 0 skipped；ARM64 Release 编译、v2 签名、包名及 arm64-only ABI 检查通过。APK artifact `gallery-foss-arm64-v8a-release` ID `13338484426`，SHA-256 `57a3cceb80e1960587d9c4c0b516a5002808644cf4340f7219ca9c7b11daf1d0`。使用临时 CI 签名，不代表与已安装 APK 可覆盖升级。没有真机验证，不能确认设备级崩溃覆盖、实际性能、系统文档提供器兼容性或界面效果；诊断模块专项新增测试只覆盖到期、脱敏和原异常处理器委托。

### 2026-11-05｜Phase 1 正式 CI 完成

受测业务代码 SHA `5b75cb2a5c7a96021f35013292a5479436f87f36`；[Run 41961735980](https://github.com/Pew2018/Gallery/actions/runs/41961735980) success、完整 FOSS Release 863 tests / 0 failure / 0 error / 0 skipped；ARM64 Artifact `gallery-foss-arm64-v8a-release` ID 13385912998，包名/ABI/v2 临时签名验证通过。真机 B01–B20/D01–D08 均待测，阶段 2 没有定量性能数据支撑。[24](24-phase1-results-and-handoff.md) 保留精确 SHA-256 与局限。
