# 32 — Phase 2B Room v11 SQL 与查询计划

日期：2026-11-05。`GalleryDatabase` 当前版本 11。Phase 2B 不改 Entity、DAO、表结构、索引、Migration、备份结构或用户库。测试新建 Robolectric/Room in-memory 数据库，按当前 Entity 及 Migration 10→11 结构，不访问真实 `gallery.db`，不包含用户路径。

## 1. Room 查询与调用方

下表 SQL 按 `MediumDao.kt`、`DirectoryDao.kt` 注解逐字转录。下列调用次数和耗时不是从用户图库推断；完整测试的合成数据基准在 CI 日志 `GalleryDbPlan` 输出，每个规模每条计时 DAO 查询执行一次。

| DAO 查询 | SQL / 场景 | 主要调用方 | 现有 v11 可用索引 |
|---|---|---|---|
| `MediumDao.getAllMedia()` | `SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id FROM media WHERE deleted_ts = 0`。一般缓存列表 | `Context.kt` | `full_path` 唯一索引不帮助只过滤 `deleted_ts`，预期表扫描 |
| `getMediaFromPath(path)` | 同字段投影 `WHERE deleted_ts = 0 AND parent_path = :path`。单目录媒体 cache | `ViewPagerActivity.kt`, `Context.kt` | `parent_path` 无索引，预期扫描 |
| `getCachedMediaFromFolders(folders)` | `SELECT * FROM media WHERE deleted_ts = 0 AND parent_path IN (:folders)`。PhotoProfile 多来源 cache | `PhotoProfileMediaLoader.kt` | `parent_path` 无索引，预期扫描 |
| `getCachedMediaFromFiles(paths)` | `SELECT * FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)`。Viewer 候选/恢复 | `PhotoProfileMediaLoader.kt`, `GalleryRestoreService.kt` | `index_media_full_path` 唯一索引 |
| `getFavorites()` | 同 `getAllMedia` 投影 `WHERE deleted_ts = 0 AND is_favorite = 1`。缓存标记语义 | `SplashActivity.kt` | `is_favorite` 无索引，预期扫描；关系表才是收藏权威数据 |
| `getFavoritesCount()` | `SELECT COUNT(filename) FROM media WHERE deleted_ts = 0 AND is_favorite = 1` | `MainActivity.kt`, `PhotoProfileLoadContext.kt` | 同上 |
| `getCachedFavoriteMedia()` | `SELECT media.* FROM media INNER JOIN favorites ON media.full_path = favorites.full_path WHERE media.deleted_ts = 0`。关系表语义的媒体 join | `Context.kt` | `media.full_path` 与 `favorites.full_path` 均有唯一索引；计划由合成测试打印 |
| `getDeletedMedia()` | 同字段投影 `WHERE deleted_ts != 0` | `MainActivity.kt`, `SettingsActivity.kt`, `Context.kt` | `deleted_ts` 无索引，预期扫描 |
| `getDeletedMediaCount()` | `SELECT COUNT(filename) FROM media WHERE deleted_ts != 0` | `MainActivity.kt` | 同上 |
| `getOldRecycleBinItems(ts)` | 同字段投影 `WHERE deleted_ts < :timestmap AND deleted_ts != 0` | `MainActivity.kt` | `deleted_ts` 无索引，预期扫描 |
| `DirectoryDao.getAll()` | 10 列投影 `FROM directories`。主页目录 cache | `MainActivity.directoryCacheReader` | 全表读取没有筛选，扫描合理 |
| `getByPaths(paths)` | 同 10 列投影 `WHERE path IN (:paths)`。收藏文件夹 subset | `MainActivity.loadFavoriteFoldersWithOverrides()`，分 250 paths 查询 | `index_directories_path` 唯一索引 |
| `getByPath(path)` | 同 10 列投影 `WHERE path = :path LIMIT 1`。单目录 metadata upsert | `DirectoryMetadataRepository` | `index_directories_path` 唯一索引 |

其他已定义索引：`date_takens.full_path` 唯一、`favorites.full_path` 唯一、`favorite_folders.path` 主键、`widgets.widget_id` 唯一。`media.parent_path`、`media.deleted_ts`、`media.is_favorite` 没有索引。依据 Room Entity/迁移声明核对；没有查询用户数据库 `sqlite_master`。

## 2. 可重复执行计划测试

新增 `GalleryDatabaseQueryPlanTest`，使用实际 `GalleryDatabase` Room schema（而非手写另一套 Room DAO）和 SQLite `EXPLAIN QUERY PLAN`，分别在 100、5,000、30,000、100,000 条合成 `media` 行后采集以下计划：

- `deleted_ts = 0 AND full_path IN (...)`
- `deleted_ts = 0 AND parent_path = ?`
- `media INNER JOIN favorites ON full_path ... WHERE deleted_ts = 0`

测试在每个规模执行 `ANALYZE`，断言 full_path 查询和收藏关系 join 使用 `index_media_full_path`，而 parent_path 查询对当前 v11 走 media scan；同一目录多路径 query 不返回重复路径；非回收站、回收站与收藏关系结果数按造数规则匹配。DAO 查询计时以单次 `System.nanoTime()` 采样并打印在测试日志；它表示 Robolectric/合成数据库运行值，不能替代设备存储、SQLite cache warm/cold 或用户行分布。

### Run 41994855432 的合成实测（单次样本）

API 28 Robolectric/Room in-memory SQLite；测试数据完全合成，不是 Pixel 或用户数据库。每个规模所有计划一致：`full_path IN` 为 `SEARCH TABLE media USING INDEX index_media_full_path (full_path=?)`；`parent_path = ?` 为 `SCAN TABLE media`；收藏关系为 `SCAN TABLE favorites USING COVERING INDEX index_favorites_full_path | SEARCH TABLE media USING INDEX index_media_full_path (full_path=?)`。

| media rows | parent = (µs / rows) | parent IN two (µs / rows) | full_path IN (µs / rows) | favorites JOIN (µs / rows) | deleted (µs / rows) | directories all (µs / rows) |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 760 / 1 | 617 / 1 | 1,075 / 1 | 1,217 / 5 | 1,304 / 5 | 6,486 / 100 |
| 5,000 | 1,421 / 50 | 1,865 / 50 | 684 / 1 | 3,595 / 250 | 2,572 / 250 | 1,370 / 100 |
| 30,000 | 3,342 / 300 | 3,956 / 300 | 433 / 1 | 7,274 / 1,500 | 5,521 / 1,500 | 839 / 100 |
| 100,000 | 24,194 / 1,000 | 25,003 / 1,000 | 862 / 1 | 39,204 / 5,000 | 28,990 / 5,000 | 1,267 / 100 |

每格为测试中一次 `System.nanoTime()` 样本，不重复取平均；数据表在每个阈值重新 `ANALYZE`，首次目录全表查询受初始化/缓存影响明显。不能据此推导真实设备性能、冷/热 cache 对照或用户媒体分布。测试在每个规模每种计时 DAO 查询调用一次，计划查询与正确性断言另有调用。总调用数与真实 Activity 的日常查询频率未知，保持 `PENDING_DEVICE`。

## 3. 评估与 Migration 门槛

v11 的唯一已明确筛选热点是 `parent_path` 过滤及 deleted/favorite 条件表扫描；但当前尚无真实设备查询延迟、调用频率与写入代价数据，不能因 `SCAN` 就直接加索引。Phase 2B 不做 Schema 变更。独立 Migration 提案必须同时提供：设备 query 次数和耗时、EXPLAIN 实际索引路径、存储占用、批量 insert/replace 更新代价、v10/v11 升级测试与关系/回收站语义回归、低空间失败恢复。若未来复合索引候选为 `(parent_path, deleted_ts)` 或 `(deleted_ts, is_favorite)`，必须以实际计划比较顺序和选择率后定案，不在本轮预设最优列序。

媒体批量写入沿用扫描事务及现有 `insertAll` 路径。没有本轮新设备 trace 证明重复写入是性能主因，不改变写入节奏；收藏关系仍由 `favorites` 表负责，不重建关系。
