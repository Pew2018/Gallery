# 02 — 固定源码证据与结构性风险登记

> **历史源码/任务资料（非当前实施指令）**：下文提及的旧工作分支、历史“当前进度”“唯一任务”“下一步”，只适用于所写日期和原固定提交。2026-11-05 起，当前工作遵循 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md) 和 [04 — 验收矩阵](04-validation-and-migration-readiness.md)。保留旧源码证据与原始 Actions/限制供追溯；不能据此声称新阶段 0–5 已完成或获得真机性能数据。

审计基线：c5cc5b6852291491a999934d12f26314455959c6。F=已查见代码，I=待验证风险，U=未测行为。此处登记不等于确认缺陷。

## 1. 源码证据目录

| 编号 | 等级 | 文件与行号（固定 SHA） | 查见的行为 |
|---|---|---|---|
| E01 | F | MainActivity.kt L249-L269, L1062-L1140 | 主页目录有 epoch、terminal gate、cache preview、并发扫描及取消/超时状态。 |
| E02 | F | MainActivity.kt L1424-L1489 | 分类目录另有版本和扫描路径，支持进度快照与最终再次发布。 |
| E03 | F | MainActivity.kt L2125-L2200 | 相册主页按目录读取，锁定目录不从主页扫描实际受保护媒体；使用 DirectoryMetadataRepository 维护数量。 |
| E04 | F | MediaActivity.kt L269-L382 | 配置页按 CACHE/INDEX/FILESYSTEM/COMPLETE 分阶段发布，条件不成立时可销毁现有 adapter。 |
| E05 | F | MediaActivity.kt L1673-L1743, L1759-L1954 | 普通媒体页独立做请求身份、缓存回退、AsyncTask、元数据提交、查看器快照和缓存清理。 |
| E06 | F | MediaFetcher.kt L40-L103 | 读取结果区分 COMPLETE/PARTIAL/FAILED/ACCESS_REQUIRED；取消经过 CancellationSignal。 |
| E07 | F | MediaFetcher.kt L505-L545, L674-L850 | 真实目录枚举、候选批次、索引查询并存；当前批次阈值为 120 条或 200 ms，另有进度节流。 |
| E08 | F | FolderFileAccess.kt L24-L78 | 全文件权限直接读取、受限读取尝试持久 SAF 树授权，受限失败与真正空目录有区别。 |
| E09 | F | DirectoryMetadataRepository.kt L93-L170 | 只有 COMPLETE、scope/current/authorized 均满足才写目录计数及封面；Room transaction + revision observer。 |
| E10 | F | GalleryScanResult.kt L3-L35 | COMPLETE/PARTIAL/FAILED/TIMED_OUT/CANCELLED 与只能终止一次的 Gate 已定义。 |
| E11 | F | GalleryMediaScanExecutor.kt L8-L34 | 媒体扫描、目录扫描与目录元数据重试用不同有界 executor；存在容量拒绝分支。 |
| E12 | F | MediaAdapter.kt L670-L785 | stable ID、后台 DiffUtil、单个进行中的 diff、移动时延迟提交、按路径滚动锚点。 |
| E13 | F | DirectoryAdapter.kt L935-L1038 | 另一路 stable ID、DiffUtil、选中/拖动/滚动时延迟提交、目录锚点、局部数据同步。 |
| E14 | F | PhotoProfileMediaLoader.kt L32-L217 | 当前 fix 具有生产者共享、来源授权筛选、缓存、索引/补扫分阶段加载、完整目录传给 Viewer store。 |
| E15 | F | PhotoProfileSourceScanPolicy.kt L9-L46 / PhotoAccessValidationBatch.kt L26-L85 | 来源授权与实际媒体访问校验是两关；不允许缓存绕过受保护来源。 |
| E16 | F | PhotoSessionViewerCatalogStore.kt L20-L337 | 全屏共用部分/完整目录、20k 内存边界、分段候选、120 条增量扩展、重新授权。 |
| E17 | F | SegmentedPhotoCatalog.kt L9-L71 | 512 条/段、临时磁盘元数据、128 MiB 上限、丢失不得解释成 EOF。 |
| E18 | F | ViewPagerActivity.kt L230-L372, L818-L1152 | 多个 viewer generation、swipePublicationGate、目录升级、UNKNOWN 边界等待、失败回退。 |
| E19 | F | ViewPagerActivity.kt L1928-L1959 | StateSaved 时延期发布，更新 Pager 时尝试保留当前项并避免意外归零。 |
| E20 | F | Config.kt L25-L27 / ToolbarQuickActions.kt L10-L149 / GalleryBottomBar.kt L170-L173 | 现有双场景顶栏快捷操作及底栏 ALL/FAVORITES/PHOTOS 是独立外部功能。 |
| E21 | F | GalleryBackupSnapshot.kt L10-L74 / PhotoProfileRepository.kt L6-L16 / GalleryDatabase.kt L12-L36 | 完整备份读取设置/收藏/照片配置；DB 当前 schema v11 具非破坏性迁移意图。 |

上表的文件位置可按固定目录前缀寻找：app/src/main/kotlin/org/fossify/gallery/；源码链接模板： https://github.com/Pew2018/Gallery/blob/c5cc5b6852291491a999934d12f26314455959c6/app/src/main/kotlin/org/fossify/gallery/ 。

## 2. 风险与可证伪问题

| 编号 | 级别 | 类型 | 风险假设 / 证据链 | 必须如何验证 |
|---|---|---|---|---|
| R01 | 高 | I | 多个页面有独立 generation、terminal gate、callback；一次外部媒体变化可能引发多路缓存、扫描、目录 metadata 更新。E01/E02/E05/E09 | 单次外部复制的 requestId 时序图，断言单个 scope 只有一个可发布的正式版本；统计重复源查询。 |
| R02 | 高 | I | 同一目录有中间预览、分类扫描渐进更新、目录卡片元数据 observer；在真正 COMPLETE 前可能产生多次视觉提交。E02/E04/E05/E09/E12/E13 | 录屏 + AdapterDataObserver + 布局管理器 anchor 日志，验证有无非必要 move/change 与闪烁。 |
| R03 | 高 | I | .nomedia 补扫依赖实际文件系统访问能力；MediaStore 缺行不能证明没有新文件。E06/E07/E08/E09 | 同目录放 100 张，增量添加/删除；分别测 ALL_FILES、受限 SAF、普通目录、.nomedia；对比文件实数/文件夹预览/配置/Viewer。 |
| R04 | 高 | I | 全屏部分目录、完整目录、独立 Viewer authorization、分页窗口同时管理列表版本和方向。E14/E16/E18/E19 | 从任意 index 连续反向/正向 20 次、边界等待中转向、授权撤销，验证当前路径与顺序不跳。 |
| R05 | 高 | I | 配置页可经历缓存/索引/文件系统多阶段提交；某些场景会移除 adapter；局部 diff 虽有防护仍会发生大量列表更新。E04/E14/E12 | 统计每次 refresh 的 publishCount、diffCount、rebindCount、adapterResetCount、滚动锚点漂移。 |
| R06 | 高 | I | 多处权限变更能使内容和目录过期，不能把缓存或某次读取授权当成后续浏览授权。E05/E08/E09/E15/E18 | 保护目录/收藏/配置在扫描中和全屏中撤销权限，禁止一帧内容泄漏；拒绝缓存快照升级授权。 |
| R07 | 中高 | I | 进程私有分段目录有磁盘写入预算及线性 indexOf 未命中扫描，可能在超大库中引入 IO/CPU 峰值。E16/E17 | 5k/20k/50k/100k 列表，记录临时文件体积、首帧、boundary wait、segment reads/evictions、取消残留。 |
| R08 | 中 | I | 有界队列遇到高频刷新会拒绝任务；失败处理与 UI 是否结束加载依赖多个分支。E01/E05/E10/E11 | 并发 10 次下拉/切页/返回，观察 rejected、每个请求的 terminalCount、loadingIndicator 终止和重试按钮。 |
| R09 | 中 | I | 滚动时 Adapter 延迟提交较安全，但积累的新结果被更换、重复布局变化仍需验证稳定 ID 与内容比较策略。E12/E13 | 高速 fling 期间增删/改时标/改排序；严格比较可见 top path + offset 和缩略图 requestCount。 |
| R10 | 中 | I | 新索引如错误地与 Room 用户关系混为一谈会损坏收藏与备份。E20/E21 | 完整导出/清数据后恢复/旧设置恢复/收藏 TXT/配置导出；字节级/语义级前后对照。 |

## 3. 已有安全阀：不可误当作“没有设计”

- 已存在的请求取消、旧 generation 失效、部分失败区别、权限校验、缓存回退和元数据提交范围检查都必须在新设计中保留或被等价证明取代（E01/E05/E06/E09/E10）。
- Adapter 已有稳定 ID 和 DiffUtil；“列表闪烁”不意味着缺少 DiffUtil，可能涉及上游多次不同快照、布局/绑定载荷、滑动时更新策略，待证据确认（E12/E13）。
- fix 里的增量 Viewer、部分目录扩展、首次已选图加载、分段目录尤其不能根据 main 旧逻辑直接删去（E14/E16/E18）。
- 对安全边界：**目录元数据不可授权**、**索引行不可授权**、**旧 snapshot 不可授权**；缺失/不可读取不能自动按删除处理（E06/E09/E15）。

## 4. 尚未取得的数据（U：本轮明确不虚构）

- 当前 fix APK 对应的实际版本、是否在目标设备上复现、ADB logcat trace、Perfetto / frame timeline。
- 目录总数、媒体总数、.nomedia 目录数、收藏关系数和配置 source 分布。
- 每次刷新真实读取了多少文件、MediaStore 查询次数、重复读数、耗时 p50/p95、内存峰值、最大缓存/临时磁盘占用。
- 真实刷新动画次数、可见条目 top path/offset 漂移、图片加载取消/重试次数、Pager 翻页 p95。
- 按 Android 8 / 11 / 14 / 16 和“所有文件访问 / 受限权限 / SAF”分别采集的结果。
- 本轮没有运行 Gradle、Robolectric、Android instrumentation 或 Actions；**不得宣称测试已通过**。

## 5. 下一轮复现优先级（建议）

P0：相册索引不停、.nomedia 新增文件是否刷新并更新目录数量、普通目录增量插入导致闪烁、全屏连续反向/正向边界、受保护来源取消授权。

P1：照片配置跨目录合并顺序/去重、全屏目录从部分升级为完整、切换配置时旧页面遗留、后台恢复、超大库分段读。

P2：特殊分组/随机排序/文件选择器/缩略图长列表滚动条，与已保留 UI 外观的交互边界。
