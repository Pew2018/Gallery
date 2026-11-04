# 06 — 小规模重构：进度与持续开发日志

> 建立日期：2026-11-04  
> 唯一分支：`fix/photo-viewer-five-item-boundary`  
> **实施状态：阶段一排序精简及阶段二扫描/渐进发布修改均已通过完整 FOSS Release 单测与 ARM64 Actions；真机验收未完成。**
> 当前开发范围：[05-minimal-scan-sort-display-refactor.md](05-minimal-scan-sort-display-refactor.md)  
> 本文为**实时工作台账**，每次处理该 fix 分支的任务必须补录；不以口头汇报替代仓库文档。

## 1. 当前状态总览

| 工作流 | 状态 | 实施进度 | 证据或阻塞 |
|---|---|---|---|
| 历史源码数据收集（01–04） | 已完成**静态审计与远程文档提交** | 文档 100% | `0cfbb9bc41a011ca16101a6c9186f18eca14b82d`；没有真机测量 |
| 精简重构目标及范围记录（05） | 本次建档 | 目标已确定 | 仅记录方案；没有 Kotlin 变更 |
| 实机基线与重现（P0 的剩余部分） | 未开始 | 0% | 缺当前 fix APK、ADB/录屏/Trace |
| P1 扫描调度与完整性 | 已完成定点审计和分类缓存失败回退修复 | 普通扫描既有 epoch/terminal/request/cancel 路径保留；分类缓存异常不再短路正式扫描 | A01/A02；Run #463 789 项 0 失败/错误/跳过；快速页面竞态仍待设备 trace |
| P2 排序与显示提交 | 阶段一排序精简、阶段二重复可见发布精简已由 Actions 验证 | 两处重复排序已移除；分类/照片配置同内容快照不再重复跨层发布 | 阶段一 Run #461 786 项、阶段二 Run #463 789 项均 0 失败/错误/跳过；真实动画、滚动与性能对照待设备测量 |
| P3 全屏/权限/备份回归 | 自动化测试通过，真机未验收 | 代码未改 Viewer/授权/备份 | Actions 完整单测通过；设备权限矩阵、手势与真实升级/恢复未测 |
| 单元测试 / 编译 / Actions（重构相关） | 阶段一、二均已通过 | Run 461、463 成功 | 阶段一 SHA `5a9d437…`：786 项；阶段二 SHA `0b709396…`：789 项；均 0 failures/errors/skipped，ARM64 artifact 已生成 |
| 主分支合并 | 未计划 | 不适用 | 禁止自行合并 `main` |
| P1 扫描生命周期与渐进发布（第二阶段） | 已验证完成；真机待验 | 分类缓存失败继续扫描；分类快照及照片配置可见快照去重 | Run #463：789 项、0 失败/错误/跳过；ARM64 APK 已生成；详见 [09](09-refresh-publication-stability.md) |

**整体状态：阶段一和阶段二代码修改、完整自动化测试与 ARM64 CI 构建均已完成；真实设备验收和运行时性能对照未完成。** 两阶段结果分别记录，不把阶段一测试/产物折算为阶段二结果。

## 2. 日志规范（每轮均必须填写）

- **时间、分支、起始/结束 SHA**：先核对远程最新 HEAD；不得以历史 SHA 替换新提交。
- **目标与验收**：用户提出的具体异常、修改范围、明确不做的项目、对应 A01–A08。
- **收集和收敛的数据**：代码证据链接与行号、现象、调用关系、运行日志/测量（如有）、事实 F / 推断 I / 未验证 U、排除路径。
- **实际改动**：逐文件列出行为变化、向下兼容说明、是否新增/删除组件；仅文档时写“无业务代码修改”。
- **验证与构建**：实际运行的测试命令/通过失败、GitHub Actions run、APK 是否存在；未执行必须明确写未执行。
- **剩余问题/后续计划**：记录风险和下轮建议。每次代码提交同步提交本文件的日志与阶段进度。
- **状态定义**：未开始 / 进行中 / 已提交待验证 / 已验证完成 / 已阻塞。任何“已完成”都必须附实证。

## 3. 已有记录

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

## 4. 新一轮日志模板（复制本节追加，不覆盖历史）

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
