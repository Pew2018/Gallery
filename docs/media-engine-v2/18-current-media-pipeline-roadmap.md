# 18 — 媒体索引、加载、排序及稳定性：当前唯一实施路线图

> 状态：**阶段 0 诊断/CI 完成，设备性能基线待采集；阶段 1 定点代码和完整 CI/ARM64 Artifact 已验证，真机仍待验**。仓库 `Pew2018/Gallery`，远程工作分支最新核对 HEAD `29f3528df55c9997cfe5c95a217ddc97eb85e331`。旧 01–17 文档中的历史分支、过时计划、当前状态仅对原文 SHA 有效。

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
| **2 查询/排序/写入热点** | `MediumDao` EXPLAIN 后酌情组合索引（显式 Room Migration）；预计算排序键、ties 测试；`PhotoProfileMediaLoader` 有序批次合并；减少重复元数据和不变写入 | 全部原始成员、排序/随机/分组/收藏语义一致；同设备 SQL/CPU/IO 前后有证据 |
| **3 可信缓存/按范围增量** | 复用现有 cache/checkpoint，定点失效/变更读取；无法证明变更时安全全扫；不预设新增永久索引或第二状态机 | 重开未变目录减少读取；外部新增、.nomedia、SAF、离线卷及文件移动不会漏项/误删 |
| **4 网格/缩略图/Viewer** | 收敛上游快照与 Diff，沿用 path+版本、分段目录、UNKNOWN 边界、内容签名、删除历史锚点 | 从可见缩略图打开正确媒体；双向连续 20 项；加载中删除/刷新不闪退、不跳错图 |
| **5 压测/回归/交付** | 数据量 100/5k/30k/100k；Android 8/11/14/16；各权限模式、备份升级、后台恢复和操作并发 | 完整 FOSS Release 单测 + ARM64 APK、无新增已知 P0、同数据性能改善与未测风险明示 |

每阶段独立提交并记录固定 HEAD、源码证据 F / 风险 R / 未测 U、文件级变更、测试、Actions、APK 和真机结论。CI 通过不等于真实场景正确；执行前重读分支最新 HEAD 和 01–18。

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
