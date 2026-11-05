# 35 — Phase 2C Room 写入与索引评估

日期：2026-11-05。分析基于当前分支 `2e6165dec230630a08a06c08d55baeef3bc5d251`。本阶段没有修改 Room Entity、Schema v11、数据库版本、Migration 或生产 SQL。

## 1. 媒体与目录写入路径

### HOME 缓存目录

`MainActivity.gotDirectories()` 对每个有效物理目录调用一次 `scanFetcher.readFolder()`，并在完成状态、权限仍有效后尝试 `DirectoryMetadataRepository.commit()`。随后为普通目录执行 `mediaDB.insertAll(curMedia)`。因此每个被读取的缓存物理目录最多有一次该函数内整目录媒体 REPLACE 调用；若目录内容未变，当前代码仍写入该目录的全部 `curMedia`。单次 HOME request 不会在这个缓存目录循环里重复处理同一个列表路径两次（正常输入按目录 path 去重以外还需以现场确认），但页面返回/刷新/照片配置等独立 request 可能再次写相同媒体。

`directoryDB.insertAll(pendingDirs)` 和 `mediaDB.insertAll(pendingMedia)` 在新发现目录批次中位于同一个 Room transaction；这是新增目录首次纳入缓存的路径。收藏文件夹关系由 `favorite_folders` 表权威维护。照片标记的 `is_favorite` 不是收藏关系；媒体写入没有改动 `favorites` 表。

### 可证明与未知

- **F**：已缓存物理目录写的是本次完整 `curMedia` 批次，不论 DAO 主键是否已存在；Room DAO 使用 REPLACE 语义。缓存比较没有先查逐条 size/mtime/权限签名。
- **F**：新发现目录目录行与媒体行成批 transaction 写入；transaction 前后均校验当前 scan request。Room 写入异常将新发现路径放进 `folderFailures`，不会被当作成功的目录数。
- **F**：缓存物理媒体的 `insertAll` 捕获异常但忽略；这一行不会回滚文件扫描或目录卡片的实际 `curMedia` 计数。它可能导致 DB 缓存落后，但真实目录统计来自本次读取。
- **F**：对于缓存目录，完整读入后执行媒体表 insert，然后注册异步 stale prune；回调需要 scan terminal COMPLETE、Fetcher 所有权、当前 request 及路径存在/父目录可读性条件。单纯缺席 MediaStore 不能删缓存。
- **R**：目录扫描任务已完成访问校验之后，缓存写入本身没有再次以 `isCurrentDirectoryScan()` 包裹 DAO 调用；理论上扫描被新 request 取消时可能存在“request 通过检查后，旧线程进入 DAO 写入”的窄竞态。写后 stale prune 则有 request/terminal gate。没有执行器屏障测试证明该时序实际发生。不能仅凭 `insertAll()` 行数量宣称旧扫描一定复活媒体；需要 transaction 与删除/移动交错测试。
- **U**：真实目录中新增/修改/相同媒体记录行数、HOME 与 PhotoProfile 重叠写入行数、Room 写入耗时、cache hit/query 数、写入期间删移交错频率均未知。

## 2. 重复 REPLACE 合成测量

新增 `GalleryDatabaseQueryPlanTest.repeatedUnchangedBatchWriteAndMetadataUpdatePreserveRowsAndFavoriteRelations`，在 Room v11 in-memory 数据库插入 500 个合成 Medium 和 50 条 favorites 关系，重复 `insertAll()` 500 条相同 Medium，测量单次重复写时长并断言 media 行仍为 500、favorites 关系仍为 50；再更新一个现存路径的 size/modified 并断言新 metadata 可读，且 favorites 关系未变。

该样本可证明 DAO 重复写的实际处理规模和 REPLACE 正确性，但它只是一批、一次、Robolectric SQLite in-memory 的值，不证明 HOME 代码重复扫描了相同目录，也不代替真实设备/文件 DB、冷热缓存、索引维护或并发删除测试。禁止将其表述为 Pixel 数据。

既有 `GalleryDatabaseQueryPlanTest` 在当前实体 schema 下以 100、5,000、30,000、100,000 条合成媒体测 SQL 计划和单次查询；Phase 2B 的旧结果仍是历史参考，不作为 2C 新运行结果。索引前后对照数据库、写入成本、库文件大小对照本轮未构造，因此没有 Migration 决策数据。

## 3. DAO 查询入口与频率证据

下表频率均为**调用点结构**，除已有诊断明确覆盖的路径外，没有用户设备的请求次数数据。

| DAO | 生产入口（本轮追踪） | 频率/返回量判断 | v11 查询/索引判断 |
|---|---|---|---|
| `getMediaFromPath()` | `Context.getCachedMedia()` / Viewer 相关缓存 | 每个调用请求对应所选目录；真实调用次数、每目录行数 U | `parent_path` 无索引；SQL 扫描 `media` |
| `getCachedMediaFromFolders()` | `PhotoProfileMediaLoader` | 一个配置阶段可按已选来源集合查询；阶段次数及返回量设备未知 | `parent_path IN` 无索引；SQL 扫描 `media` |
| `getCachedFavoriteMedia()` | 主页面/Context 收藏缓存 | 用户启动、收藏视图等频率未量化；数据量为全收藏媒体 | favorites.full_path 与 media.full_path 索引可用于 join |
| `getDeletedMedia()` | `MainActivity`、`SettingsActivity`、Context 回收站 | 打开/刷新回收站频率及回收站规模未知 | deleted_ts 无索引；SQL 扫描 |
| `getFavoritesCount()` | MainActivity 与 `PhotoProfileLoadContext` | 主页建立/扫描阶段的调用点可见，但次数未做 request summary | 根据 deleted/is_favorite 条件扫描 |
| `getAllMedia()` | `Context` | 启动/缓存读取点存在；真实频率/行数未知 | 无过滤索引可用，扫描所有非删除媒体 |
| `DirectoryDao.getAll()` | `MainActivity` 目录缓存读取 | HOME scan 的缓存预览查询 | 全表列表查询，扫描符合语义 |
| `DirectoryDao.getByPaths()` | 收藏目录加载，每批至多 250 个 path | 由 favorite folder 数决定批数；设备实际数量未知 | `directories.path` 唯一索引可用 |

`MediaPipelineDiagnostics` 已有媒体 Room 的 `dbReadCount/dbReadRows/dbReadMs/dbWriteCount/dbWriteInputRows/dbWriteMs` 结构，但本次 `MainActivity.gotDirectories()` 的 directory/media cache 写路径没有把其每项工作接入同一个完整的 HOME terminal 摘要。Phase 2B 的 `album_adapter_submit/commit` 不是 DB 频率计数。

## 4. 候选索引与 Migration 准入

| 候选 | 已知计划证据 | 可能成本/选择性问题 | 决定 |
|---|---|---|---|
| `(parent_path, deleted_ts)` | Phase 2B 合成 v11 数据上 parent 查询扫描 | 媒体新增/替换/删移均更新索引；parent 的目录基数与单目录行分布未知；文件体积增量未测 | 不进 Migration |
| `deleted_ts` | recycle-bin 条件扫描 | 若删除项稀少，过滤收益可能高；如主要行 `deleted_ts=0`，常用查询选择性低，索引写放大仍存在 | 不进 Migration |
| `(deleted_ts, is_favorite)` 或 `is_favorite` | 收藏标记扫描；但收藏关系权威在单独 favorites 表 | 标记更新频率/分布未知；不能由该字段替代收藏关系 join | 不进 Migration |

Room/SQLite `SCAN` 只是计划事实，不是卡顿结论。本轮没有在隔离库实现候选索引，所以没有查询计划、耗时、INSERT/REPLACE 成本和文件体积的成对结果。下一次 Migration 提案至少需要 Pixel 或同级真实 SQLite DB 的 query frequency/rows/time；以真实统计数据比较 `EXPLAIN QUERY PLAN`；v11 与候选 schema 冷/热查询；整批 REPLACE、单行更新、删除/移动时的耗时和 DB 大小；以及明确的旧版升级、收藏关系、回收站和低空间失败恢复测试。

## 5. Room 优化结论

只凭已证实的“目录媒体按批 REPLACE”不能跳过写入。文件大小相同不足以证明内容相同，缓存不能授予权限，Room 更新结果可能承载本次最新时间/类型/回收站/收藏显示标记。单条比较查询也可能比写入更贵。本阶段仅增加重复写/metadata 更新 synthetic test，不改写入策略、不新增缓存、不增索引、不改 schema。

T09–T16 中，本轮新增的直接覆盖是重复 unchanged batch 的实际输入规模、现存 metadata 更新及收藏关系保持。删除期间旧请求、移动/重命名/回收站交错、受保护来源、DB 故障与终态等继续依赖现有专项回归；本轮没有声称新增端到端覆盖这些场景。
