# 底栏导航与 Photos 首屏交接 / 媒体列表发布（Phase 1.1）

> 基线：执行开始时 `git ls-remote origin perf/home-gallery-sorting` 与本地 HEAD 均为
> `5e0fa6ad93116d2a9d4d2912b90d42c8103c220b`
> （`perf(photos): reuse validated content and bound first-slice publication`）。
> 没有其它 Agent 的并发提交，因此只做 fast-forward 式推进：不新建分支、不改 `main`、
> 不 force push。
>
> 本文只把能由源码、单元测试或可复现命令证明的结论写成「已确认」。需要在 Pixel 8 Pro
> 真实窗口合成 / 显示扫描上才能确认的结论，一律标注「待真机验收」。

## 0. 证据可得性

两份原始附件本机**确实存在**并且已实际读取：

| 文件 | 状态 |
|---|---|
| `Gallery-Diagnostics-20261124-162353.txt` | 1,770,395 字节 / 6,318 行；已解析 header 与所有关键区段 |
| `225d205aeedd52bd27ea6dbe27ab7958.mp4` | 存在，但**未解码观看**（本轮无视频解码管线）。因此录屏内容不作证据，只引用诊断日志 |

日志头部：`App version 1.14.1 (32) / Build type release / Android 16 (API 36) / Pixel_8_Pro /
Logging enabled true / Session 57f0b748-3d95-4555-b8d8-1608be7ef80e`。

因此：**本文所有真机数字都来自该日志的原始行号，本文所有「改善」数字都来自可复现的
JVM/Robolectric 断言（工作量、调用次数、阶段顺序），不是真机可见帧耗时。**

## 1. 真机失败时间线（日志原文，5 次进入 Photos）

| 点击/请求 | `photo_cache_lookup_end` | `photo_cache_missing_filter_end` | 过滤耗时 | 首批内容帧 |
|---|---:|---:|---:|---:|
| 16:22:11.701 | 580934173（`sinceRequestMs=35`, 4996 行） | 580936588 | **2416 ms** | 2588 ms |
| 16:22:15.877 | 580938303（`sinceRequestMs=25`） | 580939290 | **986 ms** | 1103 ms |
| 16:22:18.087 | 580940509（`sinceRequestMs=21`） | 580941488 | **978 ms** | 1096 ms |
| 16:22:24.689 | 580947111（`sinceRequestMs=22`） | 580948050 | **938 ms** | 1056 ms |
| 16:22:27.180 | 580949611（`sinceRequestMs=30`） | 580950550 | **939 ms** | 1064 ms |

同一批次的对照数据（来自同一日志）：

- `photo_cache_order durationMs=0.53–1.03`（4,995 项排序）——排序从来不是瓶颈。
- `photo_access durationMs=2.06–4.42 count=161`——160 项授权验证只需 2–4 ms。
- `photo_grid_main_publish durationMs=7.0–18.0 count=160`——主线程发布本身可控。
- `media_store_scoped durationMs=105–136 count=22`、`photo_index_query durationMs=133–139`。
- 五次全部出现 `cacheHit=true reusableCatalog=false orderedCache=false forcedScan=false`。
- 五次全部出现 `loading_frame_committed_after_limit`（700 ms 兜底）→ 5/5 未达标。

结论：首屏耗时几乎全部由 `photo_cache_missing_filter`（≈938–2416 ms）构成，
即对约 5,000 个缓存文件逐个做存在性检查。

## 2. 缓存资格与完整复用的混淆（P0-A 根因）

`PhotoProfileCatalogCheckpoint.capture()` 第 24 行在
`!isRPlus() || isExternalStorageManager() || scope.sessionId != null || scope.allFiles` 时直接
返回 `null`。而在 ALL_FILES（Pixel 8 Pro 已开启"所有文件访问"）下 `scope.allFiles` 恒为 true，
于是：

```
PhotoProfileMediaCache.offer()            checkpoint = null（条目仍然写入，带正确的顺序）
PhotoProfileMediaLoader.loadStaged()      readReusable()
PhotoProfileMediaCache.readReusable()     valid = ... && entry.checkpoint != null  → false
                                          entries.remove(profileId)        ← 删除唯一的有序副本
selectCachedPreview()                     PhotoProfileMediaCache.read() → null（已被删）
                                          → readDatabaseCache() → 全量过滤 → 排序 → 截取 160
```

即「看起来可以展示的预览」和「可以证明完整的目录」被合并成一个判断，导致
**ALL_FILES 下热缓存快速路径 0/5 命中**。

### 2.1 两级资格设计

改为两个正交判断，集中在 `PhotoProfileMediaCache.previewVerdict()` / `evaluate()`：

| 级别 | 条件 |
|---|---|
| `PREVIEW_ELIGIBLE` | 同一 `profileId`；`scope` 全等（含排序、来源、权限模式、排除项、绑定）；`revision` 相等且仍为当前 revision；收藏关系相等；年龄在 `MAX_AGE_MS` 内 |
| `COMPLETE_REUSABLE` | 在 `PREVIEW_ELIGIBLE` 基础上，还必须有 checkpoint，且 checkpoint 与当前卷/目录状态一致 |

`PhotoCacheMissReason` 明确区分 8 种未命中：
`NO_ENTRY`、`SCOPE_MISMATCH`、`CONTENT_REVISION_CHANGED`、`FAVORITES_CHANGED`、`EXPIRED`、
`CHECKPOINT_UNAVAILABLE`、`CHECKPOINT_CHANGED`，以及 `NONE`。

**淘汰规则**（`PhotoCacheMissReason.invalidatesEntry`）只在「条目本身被证明过期」时删除：
`CONTENT_REVISION_CHANGED` 与 `EXPIRED`。`SCOPE_MISMATCH` / `FAVORITES_CHANGED` 只拒绝、
不删除，因为这两个不匹配可能来自「旧请求在评估新条目」，删掉会破坏并发加载刚产出的条目。
`evict()` 还会用引用相等做二次确认，绝不会移除替换后的新条目。

### 2.2 ALL_FILES 下的实际行为

- `offer()` 仍然返回 `false`（无法证明完整），但条目**照常写入**。
- `reuse()` 返回 `previewEligible=true / completeReusable=false / missReason=CHECKPOINT_UNAVAILABLE /
  eviction=false`。
- `readOrdered()` 立刻命中，返回原始有序列表（私有不可变，worker 只读）。
- 随后继续走 index → filesystem → `COMPLETE`，完整性仍由真实扫描给出；`offer()` 在结束时刷新条目。

安全边界未放宽：没有删除 `capture()` 的 ALL_FILES 限制、没有把旧缓存标记为 COMPLETE、
没有假定 `.nomedia` 未变化、没有持久化授权信息。受保护来源的 profile 因为
`access.publicOnly == false` 依旧完全不进缓存（既有测试
`partialAndProtectedResultsAreNeverCached` 覆盖）。

## 3. Room 冷缓存的有限首屏验证（P0-B）

`selectCachedPreview()` 的冷路径改为「先排序（便宜）、后验证（昂贵）、只验证窗口」：

```
Room cache rows
  → CachedMediaPreviewOrder.order（≈0.5–5 ms / 5k 行）
  → CachedMediaPreviewOrder.selectBounded（160 目标 / 640 预算）
  → CachedMediaPreviewFilter.VisibleBatch（父目录按批次记忆化）
  → PhotoAccessValidationBatch（授权、隐藏、.nomedia、canonical 全部保留）
  → CACHE_PREVIEW
  → INDEXED / FILESYSTEM / COMPLETE
```

要点：

- **工作预算**：`CHECK_BUDGET_MULTIPLIER = 4`，即 160 行窗口最多检查 640 个候选。
  预算耗尽时返回 `complete=false` 的**部分**结果，而不是退化为全量同步检查。
- **不强制凑满 160**：首批是上限目标；部分结果照常发布，后续阶段补齐。
- **没有可发布内容时不发布空预览**（`publishValidatedStage` 已拒绝空 stage），因此不会
  把「尚未验证完」显示成「空相册」；`COMPLETE` 仍由真实扫描给出。
- **随机排序**保持原有安全路径（`selectBounded` 对 `SORT_BY_RANDOM` 返回 `null`），
  因为该模式下的候选窗口不是任何稳定顺序的前缀，无法证明等价。
- **父目录检查**在一个批次内记忆化（`VisibleBatch`），跨请求、跨授权状态不复用，
  符合任务 4.5 的边界。不可读父目录仍返回 `true`（保守语义），不把无法确认的文件当已删除。

## 4. 优化前后实际工作量

`PhotoCachePreviewMeasurementTest` 的确定性测量（JVM/Robolectric，合成候选，
`visible` 计数）。**这是工作量对比，不是 Pixel 8 Pro 耗时。**

| 路径 | 输入 | 检查候选 | 父目录探测 | 文件探测 | 排序次数 | 首屏条数 | 预算耗尽 |
|---|---:|---:|---:|---:|---:|---:|---|
| 原全量过滤 | 160 | 160 | 160 | 160 | 1 | 160 | 否 |
| 有序内存缓存有限预览 | 160 | 160 | 1 | 160 | 0 | 160 | 否 |
| Room 排序后有限预览 | 160 | 160 | 1 | 160 | 1 | 160 | 否 |
| 首批部分失效补充 | 160 | 160 | 1 | 160 | 0 | 156 | 是（目录已到末尾） |
| 大量失效有界降级 | 160 | 160 | 1 | 160 | 0 | 0 | 是 |
| 原全量过滤 | 1000 | 1000 | 1000 | 1000 | 1 | 160 | 否 |
| 有序内存缓存有限预览 | 1000 | 160 | 1 | 160 | 0 | 160 | 否 |
| Room 排序后有限预览 | 1000 | 160 | 1 | 160 | 1 | 160 | 否 |
| 首批部分失效补充 | 1000 | 164 | 1 | 164 | 0 | 160 | 否 |
| 大量失效有界降级 | 1000 | 640 | 1 | 640 | 0 | 0 | 是 |
| 原全量过滤 | 5000 | 5000 | 5000 | 5000 | 1 | 160 | 否 |
| 有序内存缓存有限预览 | 5000 | 160 | 1 | 160 | 0 | 160 | 否 |
| Room 排序后有限预览 | 5000 | 160 | 1 | 160 | 1 | 160 | 否 |
| 首批部分失效补充 | 5000 | 164 | 1 | 164 | 0 | 160 | 否 |
| 大量失效有界降级 | 5000 | 640 | 1 | 640 | 0 | 0 | 是 |
| 原全量过滤 | 12000 | 12000 | 12000 | 12000 | 1 | 160 | 否 |
| 有序内存缓存有限预览 | 12000 | 160 | 1 | 160 | 0 | 160 | 否 |
| Room 排序后有限预览 | 12000 | 160 | 1 | 160 | 1 | 160 | 否 |
| 首批部分失效补充 | 12000 | 164 | 1 | 164 | 0 | 160 | 否 |
| 大量失效有界降级 | 12000 | 640 | 1 | 640 | 0 | 0 | 是 |

真实文件系统抽样（临时目录、5400 个缓存行、其中 400 个真实存在）：

```
real_fs: legacy rows=5400 probes=5400 100.42ms | bounded rows=400 probes=160 1.09ms selected=160
```

即 5,400 次探测 → 160 次探测（33.8×），宿主机耗时 100.42 ms → 1.09 ms。
宿主机数字只用于证明量级关系，**不能**当作 Pixel 8 Pro 的实测耗时。

## 5. 生效语义（对应验收标准）

在 ALL_FILES 热缓存下，第二次进入 Photos 现在应出现：

- `photo_cache_reuse_decision previewEligible=true completeReusable=false
  checkpointAvailable=false evictionPerformed=false missReason=CHECKPOINT_UNAVAILABLE`
- `photo_cache_missing_filter_end boundedSelection=true checks≈160 budgetExhausted=false`
- `orderedCache=true`（`photo_cache_lookup_end` 字段）
- `media_cache_db_summary dbReadRows` 在第一帧前保持为空/0

由 `PhotoCachePreviewWorkTest.fullAccessHotCachePublishesTheBoundedOrderedSliceWithoutReadingRoom`
自动断言：40 个真实文件的 ALL_FILES profile，第二次 `loadStaged` 必须
「有 CACHE_PREVIEW」「`cachePreviewEligibleCount=1`」「`cachePreviewBoundedCount=1`」
「`cacheMissCheckpointUnavailableCount=1`」「`cacheEvictionCount=0`」「首帧前 `dbReadRows=0`」。

## 6. 大型中间快照与列表发布（P1-A）

日志证据：`271 → 2410`（146 ms）、`2410 → 4026`（278 ms）、`4026 → 4995`（133 ms）。

审计结论（源码 + 确定性测试）：

| 结论 | 等级 |
|---|---|
| Diff 在专用后台 executor 上运行，主线程只做 commit + `dispatchUpdatesTo`，因此 146/278/133 ms **不是**掉帧时长 | 已确认（源码） |
| 一次只允许一个在飞 Diff；期间的新快照会替换 `pendingUpdate`，旧 Diff 通过 generation 检查被丢弃 | 已确认（源码 + `PhotoPublicationPolicyTest`） |
| 增长的目录按日期倒序插入头部，`isContentAppend` 立刻在索引 0 失配，因此这些阶段必然是真 Diff；把中间阶段合并掉会牺牲渐进可见内容 | 已确认（源码 + 测试） |
| 纯重排、NO_OP、REPLACE、尾部 APPEND 四条快速路径均保留且未被替换为 `notifyDataSetChanged()` | 已确认（源码 + 测试） |

本轮**没有**削减渐进阶段数量：3 次中间发布是为了让 2400/4000 项在 COMPLETE 之前可见，
砍掉它们是可见的体验退化，而不是"消除无效计算"。本轮改为把结论固化进测试
（C01–C10），并保留既有合并语义。

## 7. 快速滚动结束后的缩略图恢复（P1-B）

### 7.1 已确认的机制缺陷

`MediaActivity.releaseMediaGridPublicationIfSafe()` 原来顺序是：

```
if (isMediaGridTouchScrolling || gridTransitionHoldsPublication || isFinishing || isDestroyed) return
...
getMediaAdapter()?.setFastScrollActive(false, submitPending = false)
```

`setFastScrollActive(false)` 是**唯一**会解除适配器暂停状态、并 `postOnAnimation(admitThumbViewport)`
去为已静止视口启动缩略图的地方。它被放在发布门禁之后，所以只要发布仍被持有
（Viewer 返回中、`transitionSession.entryActive`）或栅格仍处于 touch-scrolling，
松开滚动条就只清了 `mediaFastScrollActive`，适配器却永久停在 `fastScrollActive = true`：
`onBindViewHolder` 不再启动请求、`waitForThumbViewport()` 不再被调用、
`startPendingVisibleThumbnails()` 不再被投递 —— 静止可见区域保持灰白，直到用户再拖一次滚动条。

这与录屏后段"大面积灰白占位、松手后长期不恢复"的现象一致。

### 7.2 修复

把缩略图准入的释放提到门禁之前并无条件执行；发布释放仍受原有门禁保护：

```kotlin
getMediaAdapter()?.setFastScrollActive(false, submitPending = false)
if (isMediaGridTouchScrolling || gridTransitionHoldsPublication || isFinishing || isDestroyed) return
```

不取消任何快速滚动保护：`setFastScrollActive(true)` 仍然暂停绑定期加载、
仍用 `THUMB_VIEWPORT_QUIET_MS = 48` 聚合、仍只对**静止可见**的 holder 准入。

### 7.3 新增测量

| 事件 | 字段 |
|---|---|
| `thumbnail_recovery_window` | `recoveryGeneration`、`visiblePending`（松手瞬间仍待加载的可见行数）、`adapterVersion` |
| `thumbnail_recovery_first_ready` | `sinceFastScrollEndMs`、`remaining` |
| `thumbnail_recovery_complete` | `sinceFastScrollEndMs`、`firstReadyMs`、`adapterVersionChanged` |
| `thumbnail_viewport_admitted`（扩展） | `recoveryGeneration`、`sinceFastScrollEndMs` |

计时绑定 `adapterVersion` 与松手时的行路径集合：被回收或已改绑到其他媒体的 holder
不会被计入"已恢复"。

回归测试：`ThumbnailRecoveryPolicyTest` 证明「发布被持有 / Activity finishing 时释放滚动条，
适配器仍然解除暂停」（修复前这两条必然失败），并证明重复释放是无害的 no-op。

尚未完成（明确标记）：Camera 文件夹 10,968 项的完整扫描 ~8.3 s 不在本阶段范围内；
本轮没有在真机上采集 `thumbnail_recovery_*` 的数值，需要下一轮真机日志确认恢复时延。

## 8. 诊断关联与统一计时（P1-C）

修复前同一份日志里同一个 Photos 会话出现 **4 个不同的匿名 scope key**，并且
`navigation=none`、UI 侧 `sinceRequestMs=0`。

| 缺陷 | 根因（源码） | 修复 |
|---|---|---|
| `navigation=none` | `PhotoPublicationTimeline` 的 `navigationToken` 实参传的是 `scope.sessionId`（公共 Photos 恒为 null） | 新增 `GalleryBottomBar.navigationToken(activity)`，把 `EXTRA_ROOT_TOKEN` / `latestRequests` 传入 loader 与 Asynctask |
| `mediaRequestGeneration` 被当成全局唯一 | 实参是 Activity 局部的 `requestId`，每个 Activity 从 1 开始 | 新增 `GalleryDiagnosticLogger.nextPhotoRequestId()`（进程内 `pp-N`），同时作为日志行 `request=` 与字段 `requestId`；Activity 局部代际改名 `businessRequest` 保留可读性 |
| 4 个 scope key | `photo-profile:<id>` / 裸 id / `PHOTO_PROFILE:<id>` / 裸路径 | 统一 `PhotoDiagnosticIdentity.scopeKey(profileId)`，覆盖 `PhotoPublicationTimeline`、`previewEventFields`、`logPhotoScrollEvent`、`MediaPipelineDiagnostics.create`、`MediaRequestDiagnostics.scopeId` |
| `sinceRequestMs=0` | `photoPreviewStartAt` 取 `GalleryPerfTrace.begin()`（`System.nanoTime()`），却与 `SystemClock.elapsedRealtime()` 相减，结果恒为负数被钳到 0 | 统一用 `elapsedRealtime`；`photo_first_frame` 改走 `GalleryPerfTrace.duration()` 显式换算 |

`PhotoPublicationTimeline` 现在额外输出 `requestId`、`businessRequest`、`navigation`、`scopeKey`、
`durationMs`、`sinceRequestMs`，全部来自同一个单调时钟注入点。

证据等级仍然分开记录，不合并：Adapter 提交 ≠ 缩略图就绪；Frame Commit ≠ 物理屏幕呈现。

APK 源码身份：本轮**未**加入构建内 commit SHA。仓库的 `arm64-foss-release.yml` 用
`actions/checkout` + 未改动的 `build.gradle.kts` 产出 APK，任何在构建期注入的时间戳或
随机值都会破坏"同一源码可复现"的前提，而运行时执行 `git` 是被禁止的。因此该事项
标记为**未完成**，不以其它指标替代。

## 9. 自动化测试

新增文件：

| 文件 | 覆盖 |
|---|---|
| `PhotoProfileCacheEligibilityTest`（11 项） | A01–A10：ALL_FILES 无 checkpoint 不淘汰、失败后仍可预览、checkpoint 存在时完整复用不退化、checkpoint 变化、scope/排序/revision/收藏变化、过期、并发读改写、PARTIAL 与受保护结果不入缓存 |
| `PhotoCachePreviewWorkTest`（12 项） | B01–B09、B11 与两条端到端：冷 Room 路径只做有限探测、ALL_FILES 热缓存不读 Room |
| `PhotoPublicationPolicyTest`（9 项） | C01–C06、C09、C10 |
| `ThumbnailRecoveryPolicyTest`（3 项） | D02/D03 核心：松手必须解除暂停，发布门禁不得拥有暂停状态 |
| `PhotoCachePreviewMeasurementTest`（2 项） | 第四节工作量测量与真实文件系统抽样 |

任务要求的 B10/B12/B13、D01/D04/D05、C07/C08 由既有测试覆盖，
例如 `PhotoProfileMediaLoadingTest`（B12 `.nomedia` 与最终一致性、B13 Viewer 邻近窗口、
B10 scope 切换丢弃过期预览）、`MediaSharedSurfaceTest`、`GalleryRefreshRegressionTest`、
`FolderViewerSnapshotPolicyTest`。本阶段没有放宽任何既有断言。

`./gradlew :app:testFossDebugUnitTest` 结果见第 11 节。

## 10. 静态检查

`./gradlew :app:detekt` 与 `./gradlew :app:lintFossDebug` 的实际结果见第 11 节。
本轮不修改 `detekt.yml` / `lint.xml` 基线来"通过"检查。

## 11. 结果与待验收事项

见提交信息与 CI Run。**必须再次强调：**

- 单元测试通过、合成工作量测量通过、APK 编译成功都**不等于** Pixel 8 Pro 真机性能验收通过。
- 第 4 节的所有毫秒数都是宿主机 JVM 数字，不是设备耗时。
- 需要下一轮真机复测的项：热缓存首批内容帧（目标 300–500 ms）、700 ms 兜底次数（目标 0/5）、
  `orderedCache=true` 与 `boundedSelection=true` 的实际出现率、`thumbnail_recovery_complete`
  的 `sinceFastScrollEndMs`、以及大型中间 Diff 是否仍在 133–278 ms 量级。
