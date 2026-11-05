
**Phase 1.5 最终CI交付**：源码 `026a943a445619407134394bf434b753c65695e2`；[Run41975453251](https://github.com/Pew2018/Gallery/actions/runs/41975453251) completed/success；完整886项单测、ARM64构建、包名/ABI/v2签名与实际下载哈希核验通过；Artifact13391393642。Pixel连续浏览和性能前后复测仍PENDING_DEVICE，不等于所有P0真机验收通过，不启动原阶段2。见[27](27-phase1-5-validation-and-handoff.md)。

## 2026-11-05 — Phase 1.5 Pixel真机日志驱动的正确性修复

在 `refactor/media-pipeline-stability` 最新远程 `7670a10a…` 上审计A/B完整日志，保留旧Activity取消所有权，用当前Viewer重新验证已提交候选扩展邻域；明确grid commit版本、partial/random与COMPLETE权威排序，保留live path及权限/revision；合并阶段发布并移除逐文件默认timing。审计、实际代码/测试和交接见 [25](25-phase1-5-device-log-root-cause-audit.md)、[26](26-phase1-5-viewer-consistency-fixes.md)、[27](27-phase1-5-validation-and-handoff.md)。CI以27的真实结果为准，Pixel所有复测仍PENDING_DEVICE；原阶段2–4不提前实施。

# 18 — 媒体索引、加载、排序及稳定性：当前唯一实施路线图

> 状态（2026-11-05更新）：Phase 2C 正在 `refactor/media-pipeline-stability` 上执行，审计起始 HEAD `2e6165dec230630a08a06c08d55baeef3bc5d251`。Phase 2C 目前没有证据充分支持生产发布/Room 写入/索引行为变更；补充合成性能与 Room 写入回归，最终 CI/ARM64 与交接见[34–36](34-phase2c-album-publication-audit.md)。Pixel 请求级性能、滚动锚点及 Phase 1.5 Viewer 复测仍`PENDING_DEVICE`。Phase 2B受测代码与 CI 见[31–33](31-phase2b-sort-concurrency-and-ui-audit.md)。

## Phase 2D — 虚拟来源排序与搜索（2026-11-05）

开始审计前重新获取目标远程 HEAD b7175e8fa0411d8ff600366e316777efb28bb8f3。源码确认虚拟收藏和回收站不符合普通物理目录快照复用准入，闲置时排序会走 getMedia；扫描期间则等有效请求完成。定点实现继续复用现有 Adapter、排序器、searchQueryChanged 与扫描；增加来源成员有效性、回收站 Room 成员 epoch、扫描末端的最新排序配置处理以及同值 RANDOM 操作反馈。实现、测试、CI/APK 及真机状态见[41–42](41-virtual-favorites-recycle-bin-sort-search-audit.md)。

当前实现不增加永久索引或 Room Migration，也不逐项 File.exists()。本机 buildSrc 编译通过，但 app Kotlin 编译因缺少 Android SDK 未启动；GitHub Actions Run [42085423790](https://github.com/Pew2018/Gallery/actions/runs/42085423790) 已验证 SHA `27008fc1b5226187c3c8b99c0a12911aa5163632` 的 924 项单测和 ARM64 APK。Pixel 8 Pro 为 PENDING_DEVICE。

## 1. 任务、目标与职责

**目标**：先确保发现媒体、目录数量、访问控制和发布顺序正确，再减少重复索引/扫描、MediaStore 和文件 IO、Room 读写、全量排序、Diff 与缩略图重绑；同时提升首屏、重复打开、刷新、全屏左右滑动及删除后的稳定性。

**分工规则（不预设具体人员）**：需求所有者负责设备场景和验收/合并决定；开发实施者负责最新 HEAD 复核、定点修改、测试、文档和提交；复核者负责区分真实源码事实、推断与真机测试；CI 只证明指定 SHA 的单测/编译，不替代设备验收。未经用户指令不修改 `main`、不创建 PR、不强推。

**保持现有功能**：OxygenOS Classic UI、ALL/FAVORITES/PHOTOS、普通/收藏/回收站/.nomedia/自定义配置、搜索/筛选/排序/分组、受保护目录、SAF/所有文件访问、文件操作、全屏渐进目录、收藏和备份、老版本升级。权限缓存永远不是授权依据。

## 2. 核对过的源码事实与待证风险

- **F01 主页开销**：`MainActivity.gotDirectories()`（L2130–2385、L2436–2547）按目录 `readFolder`，提交元数据、处理媒体缓存与 Room，虽复用了部分索引查询，仍需量化重复 IO。
- **F02 分散生命周期**：`MainActivity.getDirectories()`、`MediaActivity.getMedia()`、`CategorizedFolderScanner`、`PhotoProfileMediaLoader`、Viewer 使用多条请求/发布链；已经存在 epoch、取消、terminal gate，不得错误宣称“完全没有并发保护”。
- **F03 累计排序**：`PhotoProfileMediaLoader.loadStaged()`（L176–204）在每个 FILESYSTEM 阶段将累计列表重排并尝试发布；此前已精简过部分重复排序，不能当作零基础。
- **F04 排序热区**：`MediaFetcher.sortMedia()`（L1108–1146）在比较器内部重复 normalize/lowercase；部分相同主排序值无显式稳定路径次级键，需测试后调整。
- **F05 数据库**：`Medium.kt` 目前仅对 `full_path` 声明唯一索引，而 `MediumDao` 按 `parent_path`、`deleted_ts` 等查询；索引收益须先以 `EXPLAIN QUERY PLAN` 证明。
- **F06 复用条件**：`FolderReturnCheckpoint`、`PhotoProfileCatalogCheckpoint` 对部分 Android 11+ 场景可验证缓存新鲜度，但对所有文件访问等采用保守回退；不得直接用目录 mtime 单独判断完整性。
- **F07 既有安全阀**：Adapter 已有 stable ID、异步 Diff、滚动延期；`DirectoryMetadataRepository.commit` 有 COMPLETE、scope、revision、授权门槛。新实现必须保留等价保护。
- **R**：重复扫描与多阶段发布可能导致不必要 IO、重绑/闪烁/错图或竞态；只是合理风险，需请求级日志、录屏/Trace、AdapterObserver、实际 Path 断言证伪。
- **U**：尚无 `49e799eceab00414ef074cbf71b7f0a35133b669` 对应的统一实机首屏/完成 p50/p95、查询次数、内存峰值、帧时间、.nomedia/SAF/受保护目录矩阵；不得臆造提升比例或已修复症状。

## 3. 六阶段路线和退出门槛

| 阶段 | 工作与优先入口 | 必须验证 |
|---|---|---|
| **0 诊断/基线** | 扩展 `GalleryPerfTrace`、`GalleryDiagnosticLogger`，记录 scope+request、排队、查询、枚举、DB、排序、stage、UI、首屏/完成、heap | 同 APK、设备、授权、数据集；冷/热/刷新各至少 10 次 p50/p95；无源码功能改动 |
| **1 扫描生命周期与正确性** | 复用 `DirectoryScanEpoch`、`GalleryScanTerminalGate`、现有 loaders；精确修复旧 generation、终态和删除/撤权竞态 | COMPLETE/PARTIAL/FAILED/CANCELLED/TIMED_OUT/ACCESS_REQUIRED 正确；不伪装空集、不复活删除媒体、不无限加载 |
| **1.5 Viewer设备日志正确性** | 已提交网格候选重新验证、UNKNOWN扩展/恢复、COMPLETE全局确定性排序及live path、阶段发布合并；详见25–27 | 886项CI与ARM64已通过；Pixel双向20项、删除/撤权、同条件等待时间复测仍PENDING_DEVICE |
| **2 查询/排序/写入热点** | `MediumDao` EXPLAIN 后酌情组合索引（显式 Room Migration）；预计算排序键、ties 测试；`PhotoProfileMediaLoader` 有序批次合并；减少重复元数据和不变写入 | 全部原始成员、排序/随机/分组/收藏语义一致；同设备 SQL/CPU/IO 前后有证据 |
| **3 可信缓存/按范围增量** | 复用现有 cache/checkpoint，定点失效/变更读取；无法证明变更时安全全扫；不预设新增永久索引或第二状态机 | 重开未变目录减少读取；外部新增、.nomedia、SAF、离线卷及文件移动不会漏项/误删 |
| **4 网格/缩略图/Viewer** | 收敛上游快照与 Diff，沿用 path+版本、分段目录、UNKNOWN 边界、内容签名、删除历史锚点 | 从可见缩略图打开正确媒体；双向连续 20 项；加载中删除/刷新不闪退、不跳错图 |
| **5 压测/回归/交付** | 数据量 100/5k/30k/100k；Android 8/11/14/16；各权限模式、备份升级、后台恢复和操作并发 | 完整 FOSS Release 单测 + ARM64 APK、无新增已知 P0、同数据性能改善与未测风险明示 |

每阶段独立提交并记录固定 HEAD、源码证据 F / 风险 R / 未测 U、文件级变更、测试、Actions、APK 和真机结论。CI 通过不等于真实场景正确；执行前重读分支最新 HEAD 和相关阶段文档。

## 9. Phase 2A — 历史热点与状态（2026-11-05）

审计、实施与最终结果详见[28](28-phase2a-hotspot-audit.md)、[29](29-phase2a-implementation-and-regression.md)、[30](30-phase2a-results-and-handoff.md)。最终应用源码`dcd41024e06451eb7a46d28db18a84157cd5102b`通过Run41983357685：889项完整FOSS Release测试、ARM64构建与签名/包名/ABI校验成功；Artifact ID13395436120。有直接代码证据的改动仅为：对仍有效、正式COMPLETE、授权有效的普通物理文件夹，改变排序时复用当前媒体快照，在后台调用现有排序器并沿既有Adapter/Diff更新；不清Adapter、不重扫。扫描中、部分结果、受保护聚合、收藏/主页/回收站等保留原加载路径。

元数据方面未找到可以安全跳过的单次实体验证：枚举不含文件size/mtime/readability；MediaStore记录不证明文件当前存在；跨阶段再次校验用于发布时权限与可见性。`.nomedia`补扫和COMPLETE/PARTIAL区分未变。排序CPU、主线程目录准备、Room实际SQL计划与设备读时延没有修复后数据；不新增缓存或Migration。阶段1.5设备回归仍为`PENDING_DEVICE`。最终源码、测试与Actions以[30](30-phase2a-results-and-handoff.md)为准。

## 10. Phase 2B — 排序并发、主页显示与 Room v11 计划

阶段2B基于重新获取的远程HEAD `ad8155c73bcd542e9e565d83cb6444e9679b58bf`。审计发现普通文件夹连续排序缺少排序操作身份：A→B→A 或重复 RANDOM 时同一配置值不能阻止旧 worker 最后覆盖。修复使用独立 generation，不复用 scan request 作为唯一操作身份。刷新、暂停、权限和 Activity 生命周期仍各走原 gate。主页日期/大小等排序继续必要的目录读取；不以无版本的缓存元数据跳过扫描。补充分段主页准备计时并构造真实 Room v11 schema 的合成数据库执行计划测试。无 Pixel 新版日志时，主页帧预算、用户库查询次数及真实 SQL 时延继续 `PENDING_DEVICE`。结果以[31–33](31-phase2b-sort-concurrency-and-ui-audit.md)为准。

## 11. Phase 2C — 主页发布与 Room 写入/索引证据

在远程起始 HEAD `2e6165dec230630a08a06c08d55baeef3bc5d251` 审计 `MainActivity.gotDirectories()`、`setupAdapter()`、`DirectoryAdapter.updateDirs()` 与 Room 写路径。代码证明缓存目录每 8 个真实卡片变化进行中间发布；相同可见内容由 Adapter 短路；缓存目录循环中每个物理目录一次整批媒体 `insertAll()`，但跨独立 request 的重叠写入及设备成本尚未知。由此，本阶段不依据推测改变扫描发布、Room 写入、主线程计算或 Schema；新增 100/500/1k/5k 生产 presentation synthetic benchmark、成员变化/20卡片回归与 500 行 Room unchanged-write/metadata/favorite-relation 样本。合成测试不代表 Pixel 性能。审计和结果以[34–36](34-phase2c-album-publication-audit.md)为准。

最终源码 `034bd9deee48aaf900956bb896f2152a43e6f05e` 通过 [Run 42012246773](https://github.com/Pew2018/Gallery/actions/runs/42012246773)：897 tests / 0 failures / 0 errors / 0 skipped；ARM64 APK 与包名、version、ABI、v2 签名校验成功。Artifact ID `13407079108`。真实 Pixel 8 Pro 请求级工作量仍未采集；Phase 2D 暂不进入正式索引 Migration，Phase 3 暂不新增缓存/增量索引。

## 4. 必须保持的实现约束

- **真实媒体来源**：MediaStore 与文件系统/SAF 同等重要；显式选中的 `.nomedia` 目录可能未进入 MediaStore。查询空/读取失败不允许作为文件已删除的证据。
- **数据完整性**：每个 scope/generation 最多一个有效正式 COMPLETE；预览与部分批次不得覆盖更新的正式结果，旧扫描不得写 Room/网格/Viewer。
- **鉴权**：公开快照、Room、分段索引均不是受保护来源的访问令牌；权限变化必须重新校验，撤权立即停止旧展示。
- **同序交接**：网格与全屏使用可证明相同的媒体路径、内容版本和排序；局部窗口不是 END，删除后恢复可信浏览历史，不以整数位置猜图片。
- **数据库和备份**：保留 GalleryDatabase v11 的用户数据、收藏关系、照片配置序列化和备份语义。只有阶段 2 的测量证明必要时实施明确 Migration，禁用 destructive migration；不重建全新持久化索引作为默认答案。
- **显示**：利用已有 stable ID、Diff、滑动中延期，减少重复发布/绑定；权限收窄时清 UI 是必要安全例外。不得为稳定动画彻底关闭刷新或修改 Classic 视觉规范。

## 5. 测量与工作流

关键字段：`requestId`、匿名 `scopeKey`、队列等待、MediaStore 查询数/时长、枚举/文件元数据数/时长、Room read/write rows 与时长、sortCount/sortMs、stagePublishCount、adapter diff/bindCount、topPathHash/offset、firstVisibleMs、completeMs、heap/GC 与 Viewer 首帧。禁止在 Release 日志记录真实路径、照片名、保护凭据。

工作分支 `refactor/media-pipeline-stability`，从 `main` `49e799eceab00414ef074cbf71b7f0a35133b669` 建立；本轮只整理文档，不触发新的编译、不合并 `main`。老阶段 01–17 为追溯资料；[04](04-validation-and-migration-readiness.md) 为当前验证矩阵，[05](05-minimal-scan-sort-display-refactor.md) 为实施细则，[03](03-compatibility-contracts.md) 为用户功能和授权契约。

## 6. Phase 0 状态

阶段 0 实施及指标边界见 [19 — instrumentation audit](19-phase0-instrumentation-audit.md)、[20 — metrics and test plan](20-phase0-metrics-and-test-plan.md) 与 [21 — results and handoff](21-phase0-results-and-handoff.md)。目前修改范围为计时门控、请求级 opt-in 汇总、媒体读取/排序/Photo 缓存与 DB、Adapter 提交观测、回归测试和文档；不调整扫描/排序/缓存/DB/UI 行为。

日志字段缺失用 `-1` 表示，测得为零才记 `0`。`ml-*` UUID 连接一个请求的汇总；`businessRequest` 用于与既有扫描日志、适配器事件关联。统计值在采集完整前不能替代设备测量。

本轮 Phase 0 的完整 FOSS Release 单测和 ARM64 APK 已由 [Actions Run 41951185632](https://github.com/Pew2018/Gallery/actions/runs/41951185632) 在提交 `eeddba4b68779a0809c24ede5daaff613aed9426` 上通过。开发环境没有 `adb`，本地 wrapper 下载因网络不可达而未启动；真机性能数据保持 `PENDING_DEVICE`，请按文档 20 执行场景采集。

## 7. 历史状态证明

当前 `main` 的 [Actions Run 41939577702](https://github.com/Pew2018/Gallery/actions/runs/41939577702) 为 success，包括完整 FOSS Release 单测、ARM64 Release APK 与签名/ABI 校验。此结果**不是**新六阶段的设备性能和并发验收。本轮未修改 Kotlin/数据库/CI，未进行真机测量；不承诺历史旧 8–16 或 43–78 人日估算。

## 8. Phase 1：生命周期正确性（2026-11-05）

按 [22](22-phase1-lifecycle-audit.md)、[23](23-phase1-implementation-and-tests.md)、[24](24-phase1-results-and-handoff.md) 实施本阶段的**定点请求终态/诊断可靠性**。开始审计的远程工作分支 HEAD 是 `5cc20adf6875ca220df8db4ff3bb24e42f82de22`。发现的明确问题包括 -1 未测值被记为零、HOME/CATEGORIZED 被旧请求取消时诊断遗漏/错误终态，以及媒体 Executor 拒绝时业务终态门缺 FAIL；此外避免扫描结束后后台直接读取 UI 的可变 mMedia 以降低竞态。继续复用原 epoch、terminal、auth、complete-only prune，未修改 SQL/排序/线程池/UI/Viewer 基础架构。

阶段 0 的同设备性能基线仍为 `PENDING_DEVICE`，不得在阶段 1 虚构性能提升；本阶段完整 Actions/APK 和真机验收请以 [24](24-phase1-results-and-handoff.md) 所列验证证据为准。阶段 2 必须等待可核实的 IO/SQL/排序/渲染热点数据，不可越级修改。

阶段 1 的最终经 CI 验证源码为 `5b75cb2a5c7a96021f35013292a5479436f87f36`，完整 [Run 41961735980](https://github.com/Pew2018/Gallery/actions/runs/41961735980) **success**、863/863 单测通过，ARM64 APK Artifact ID `13385912998`。真机 `PENDING_DEVICE`，阶段 2 尚未开始。详见 [24](24-phase1-results-and-handoff.md)。

## Phase 2C device sort/search follow-up (2026-11-05)

The next bounded pass addresses ordinary-folder sort feedback and search while scanning. The audit separates confirmed source paths from unproven causes in the Pixel logs; see [37](37-phase2c-device-sort-search-root-cause.md). The implementation and evidence status are tracked in [38](38-phase2c-sort-search-fix-and-validation.md). Room/schema, persistent index, Viewer continuity, delete anchors and home-card movement remain out of scope. Device verification remains `PENDING_DEVICE` until the updated APK is tested on Pixel 8 Pro.

Follow-up logs showed that `adapter_submit` could be replaced or dropped before commit and that sorting during refresh could restart an active scan. The follow-up implementation permits authorized current COMPLETE snapshots during refresh and commits plain-media reorder-only updates directly; sectioned media stays on DiffUtil. See [39](39-phase2c-followup-sort-search-display-commit.md). Follow-up source `82478f070427cf3255823d08d2b298a1c4e8f0a7` passed [Actions Run 42050016101](https://github.com/Pew2018/Gallery/actions/runs/42050016101): 907/0/0/0, ARM64 APK validated; Pixel 8 Pro remains `PENDING_DEVICE`.

## 12. 2026-11-06 — 最新排序/搜索显示提交修复状态

已核对 `refactor/media-pipeline-stability` 实现提交
`bebbe96692a964cc7669c47e481e30f6e93030aa`，详见
[40 — Adapter 提交阻塞修复与真机证据](40-device-sort-search-adapter-commit-recovery.md)。
本次在保持 Adapter 单一 Diff 工作任务、完整权限校验与扫描生命周期
约束的前提下，允许用户排序/搜索绕过过期 Diff 等待并直接提交可授权
的最新媒体列表；增加显示来源版本和物理文件夹排序元数据保留。
这不是新的 Room/MediaStore 持久索引或数据库 Migration。

实现源 [Actions 42068997299](https://github.com/Pew2018/Gallery/actions/runs/42068997299)
**success**，917 项完整单测零失败，ARM64 APK artifact **13436865163**，
包名、ABI、v2 签名检查通过；签名是临时 CI 测试签名。
独立静态分析仍有既有失败项。Pixel 8 Pro 的真实搜索/排序输入到
显示帧延迟、滚动/闪烁、`.nomedia` 和授权撤销交错、额外元数据
读取开销仍为 **PENDING_DEVICE**，不能用 synthetic host timing
或 CI 通过代替真机验收。此节为构建后的文档补录，不修改业务代码。
