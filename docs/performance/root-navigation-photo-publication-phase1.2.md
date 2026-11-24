# 底栏导航与 Photos 媒体列表发布稳定性（Phase 1.2）

> 基线：执行开始时 `git fetch origin perf/home-gallery-sorting` 与本地 HEAD 均为
> `8a3e7a84f62d223d957f4911df36c06a2035d62c`
> （`perf(photos): keep the preview cache alive and bound the cold first slice`）。
> 远程分支无其它 Agent 的并发提交，因此只做 fast-forward 式推进：不新建分支、不改 `main`、
> 不 force push、不创建 PR，完成后停止，不进入 Phase 2。
>
> 本文只把能由源码、单元测试或可复现命令证明的结论写成「已确认」。需要在 Pixel 8 Pro
> 真实窗口合成、真实磁盘与真实授权会话上才能确认的结论，一律标注「待真机验收」。

## 0. 证据可得性

| 材料 | 状态 |
|---|---|
| `Gallery-Diagnostics-20261124-202932.txt` | 4,820 行；**会话开始时存在并已实际读取**（header、pp-3/pp-6 发布序列、27/28 双请求时间线、提交策略分布） |
| `1018fb2c2d125f2dc6b110114af4235f.mp4` | 附件存在，本机**未解码观看**；录屏内容不作证据 |

日志头部：`App version 1.14.1 (32) / Build type release / Android 16 (API 36) / Pixel_8_Pro /
Logging enabled true / Session 720011b3-21a6-41a0-bc4f-5511eebcd731`。

**限制说明（必须保留）**：该日志是微信 `temp/drag` 目录中的临时附件。本轮执行约半小时后，
微信客户端把该临时目录清理掉，后续再想复核未抽取的行已不可得。本文引用的真机数字都来自
清理前已读取并按行核对过的区段；`pp-6` 完整扫描的耗时构成（13,835 ms / 文件补充 10,000 ms /
磁盘枚举 3,891 ms / 元数据 4,209 ms / 最终访问验证 1,486 ms 等）来自任务书，不是本轮重新解析
所得。没有重新生成的数字不写成「本轮测量」。

## 1. 已确认的真机现象

### 1.1 刷新期间已有媒体列表退化（P0-A）

请求 `pp-3`（REFRESH，同 scope）与 `pp-6`（REFRESH，同 scope）都出现了「已显示 160 项 →
被下一批 120 项替换」：

| 请求 | 显示序列（`photo_grid_publication_committed` / `mediaCount`） |
|---|---|
| pp-3 | 160 → **120** → 1680 → 3480（最终扫描 4995 未在配置切换前提交） |
| pp-6 | 160 → **120** → 1200 → 2041 → 3361 → 4561 → 5401 → 6241 → 7201 → 8041 → 8988 → 9842 → 10821 → 10968 |

原始日志行（已在会话开始时核对）：2659–2669、2984–2994、3028–3188、3319–3327。

对应的 Adapter 提交证明这确实是「缩小」而不是「重排」：

```
20:28:41.636  adapter_submit   itemCount=120 oldCount=160  updateReason=SCAN
20:28:41.647  adapter_commit   itemCount=120 oldCount=160  diffMs=7 commitStrategy=diff
```

提交策略分布（20:28:50 之后，pp-6 全程）：**4 次 DIFF + 9 次 REPLACE（含最终 COMPLETE）**，
DIFF 后台耗时 8 / 42 / 77 / 184 ms，合计 311 ms；单次 REPLACE 的 `photo_grid_main_publish`
提交耗时 2–5 ms，另一次配置出现 346 ms 的单次 Diff。

### 1.2 配置切换与 `onResume` 双重加载（P0-B）

| 时间 | 事件 |
|---|---|
| 20:28:44.938 | `media_request_created` request=27，`requestGeneration=4`，origin=CONFIG_SWITCH |
| 20:28:44.941 | `media_worker_started` workerId=27 |
| 20:28:44.942 | `window_restored` / `resume_after_commons`（MediaActivity 恢复） |
| 20:28:44.949 | `scan_restart_requested` reason=source_refresh，scanInProgress=true，previousRequestId=27 |
| 20:28:44.950 | `media_request_created` request=28，`requestGeneration=5`，origin=INITIAL（ENTER） |
| 20:28:44.950 | request=27 `media_request_cancel_requested`（被 supersede） |
| 20:28:45.025 | `photo_cache` durationMs=75.16 count=10968（27 的 Room 读取） |
| 20:28:45.031 | `photo_cache` durationMs=68.05 count=10968（28 的同一读取） |
| 20:28:45.039–.041 | 两个请求各自完成 160 项有限候选检查 |
| 20:28:45.041 | request=27 `media_worker_terminal`（CANCELLED） |

两个请求的 `scopeKey`、`scopeRevision` 完全相同，重复工作至少包括：一次 10,968 行 Room 读取、
一次完整缓存候选排序、160 项存在性检查（约 100 ms 的并发无效工作）。

### 1.3 实际回调链（源码审计，不只按函数名推断）

```
PhotoProfileEditActivity.onActivityResult(RESULT_OK)
  └─ MediaActivity.onActivityResult       (MediaActivity.kt:2713 → 2725)
      └─ reloadPhotoProfile()             (≈1291)  →  scope 已变 ⇒ SCOPE_CHANGED
          └─ getPhotoProfileMedia(SCOPE_CHANGED)  →  generation 4, worker 27
onResume()                                (≈2124)
  └─ isPhotoProfiles 分支                 (≈2210)
      ├─ resumeRetainedPhotoScan(retainedScan)  —— 此处 retainedScan 为空
      ├─ sameAuthorizedGrid = false        —— 新 scope 尚未提交任何显示
      ├─ catalogWorkerActive = false       —— 不是 Viewer 返回
      └─ tryLoadGallery() → getMedia() → getPhotoProfileMedia(ENTER)
                                            →  generation 5, worker 28
```

## 2. P0-A：刷新基线保留

### 2.1 三类集合

| 集合 | 含义 | 谁能证明 |
|---|---|---|
| **discovered** | 本次扫描已枚举并通过访问验证的行 | 本次请求的验证批次 |
| **displayed** | 当前可安全展示的临时集合 = baseline ∪ discovered | 旧请求的验证 + 本次扫描 |
| **authoritative** | COMPLETE 结果 | 本次合法完整扫描 |

`PhotoRefreshBaseline`（`helpers/PhotoRefreshBaseline.kt`）只承担 **displayed** 的视觉连续职责。
它明确**不**具备以下任何权力（均由代码结构保证，不是约定）：

- 不进入 COMPLETE：`loadStaged` 的 `val complete = result(PhotoLoadStage.COMPLETE, media)` 只使用
  本次扫描的 `media`，基线在合并阶段之后就不再参与；
- 不写回 Room：写入路径仍只接收 `complete.media`（`publicAccess` 分支的 `insertAll`）；
- 不跳过扫描：基线只在 `scanProfileFileSupplements` 的回调里被读取；
- 不参与授权判定：`PhotoAccessValidationBatch` 只验证本次发现的批次；基线行沿用「同一 scope、
  同一 revision、同一 accessVersion、同一解锁会话」这一已经成立的前提，任何一项不成立就不捕获基线；
- 不作为 COMPLETE 的目录边界：删除、排除、权限变化的结果只由 COMPLETE 决定。

### 2.2 合并规则

```
merged = baseline.paths ∪ discovered          // discovered 覆盖同路径的旧行
retained = |baseline \ discovered|            // 仅填充视觉空缺
added    = |discovered \ baseline|
updated  = |{p ∈ baseline ∩ discovered : !sameContent(before, after)}|
```

- 同一路径不重复：以 `path` 为 Map key；
- 新元数据优先：`discovered` 的 size/modified/taken/isFavorite 覆盖旧行（A04）；
- 新增媒体按当前排序进入（合并后统一 `sort`，A05）；
- 真实删除只能在 COMPLETE 生效（A06/A07），部分批次缺少某路径不构成删除证据。

### 2.3 访问安全边界（3.6）

旧实现的保留条件是

```kotlin
( isMediaGridMoving || photoLoadedRevision == PhotoContentRevision.current() )
```

**滚动被当成了一种授权理由**：只要正在滚动，即使内容 revision 已经变化也继续展示旧缩略图。
本轮删除该分支，改为 `PhotoRefreshBaselinePolicy.rejection(...)` 的显式判定，顺序固定为：

| 顺序 | 拒绝原因 | token |
|---:|---|---|
| 1 | 没有 Adapter / 没有已发布 key | `adapter_missing` |
| 2 | 显示集合为空 | `no_display` |
| 3 | scope key 不同（profile/来源/filter/排序/保护/session 变化） | `scope_changed` |
| 4 | accessVersion 变化（授权或解锁会话变化） | `access_changed` |
| 5 | 当前配置需要解锁 | `locked` |
| 6 | 搜索态（搜索结果不是完整源快照） | `search_active` |
| 7 | 内容 revision 变化 | `revision_changed` |
| 8 | 随机排序（顺序不可复现） | `unstable_order` |

授权先于内容 revision 判定：**权限撤销不会被报告成普通的「内容变化」**。滚动不参与判定；
权限撤销由 `onPhotoAccessRevoked()` 直接清空显示并重建请求。

### 2.4 部分与最终快照协调（P1-A 的准入）

`PhotoSnapshotAdmission.decide(...)`（`helpers/PhotoSnapshotAdmission.kt`）在**验证、拷贝、排序之前**
决定这一批是否需要显示，规则按优先级：

1. `COMPLETE` 一律发布（最终提交活性不可被合并牺牲）；
2. 网格为空 → 立即发布（首屏不受中间批次限频影响，C01）；
3. 显示的集合本身就是该 scope 的 COMPLETE → 扫描进度不替换（C02/C03）；
4. 新集合会移除已显示媒体 → 不发布（永不缩小，C07）；
5. 用户离列表末端不足 24 项（一屏）→ 发布（浏览不能被合并吞掉，C05）；
6. 其余情况要求增长量 ≥ `max(已显示数, 120)` 才发布（几何合并，C04）。

`ProgressivePublicationBudget`（500 ms）保留，只作为「用户需要更多」路径的节流，不是主策略，
也没有被调大间隔。

当基线因任何原因被拒绝时，`baselineComplete` 不再抑制中间发布（否则会出现长时间空白页）：
`coveredByCompleteGrid = baselineComplete && baseline != null`。

### 2.5 显示侧第二道闸门

`MediaActivity.submitPhotoGridResult` 增加一条与 worker 无关的不变量：非 COMPLETE 阶段若
`result.media.size < mMedia` 中的媒体数，直接拒绝并记录
`photo_partial_publication_suppressed(reason=would_remove_displayed_media)`。即使将来某条旁路
（排序预览、子页面重入）绕过了 worker 合并，网格也不会被部分扫描缩小。

## 3. P0-B：请求准入

### 3.1 位置

`getPhotoProfileMedia()` 中，`PhotoLoadAdmission.decide(...)` 在以下副作用**之前**执行：
`mediaLoadRequests.begin()`、`mCurrAsyncTask.stopFetching()`、`retainedPhotoScan` 退役、
`mediaGridPublication.clear()`、`discardInaccessibleGrid()`、诊断身份重建。

### 3.2 等价性

| 当前运行 | 新意图 | 决策 | 理由 |
|---|---|---|---|
| SCOPE_CHANGED（同 scope/revision/access） | ENTER | `REUSE` | 同一意图，保留 generation 与诊断 id |
| ENTER | 重复 ENTER | `REUSE` | 合并 |
| REFRESH | ENTER | `REUSE` | 刷新覆盖进入意图 |
| REFRESH | REFRESH | `COALESCE_REFRESH` | 不忽略手势，结束后补一次有界刷新 |
| ENTER | REFRESH | `NEW` | 手动刷新要求真实磁盘重扫，缓存命中的 ENTER 不满足 |
| 任意 | 不同 scope（含 profile/filter/排序） | `NEW` | 真实 scope 变化 |
| 任意 | revision / accessVersion 变化 | `NEW` | 旧结果已失效 |
| 已结束/已取消/非当前 generation | 任意 | `NEW` | 失败可重试 |

`COALESCE_REFRESH` 不是「丢掉这次刷新」：`photoPendingRefresh` 会在本次 COMPLETE 提交后
触发一次后续 REFRESH（`coalesced_follow_up`），因此「刷新期间新增的文件」不会被漏掉（B07）。

配置页返回但配置未变化时（`reloadPhotoProfile`），若同一 scope 的扫描仍在运行，直接复用并记录
`photo_load_request_rejected(reason=unchanged_scope_scan_running)`（B08）。

### 3.3 诊断

| 事件 | 关键字段 |
|---|---|
| `photo_load_intent_received` | loadReason、decision、activeRequest、activeReason、workerRunning、revisionMatch、scopeMatch |
| `photo_load_request_admitted` | request、previousRequest、superseded |
| `photo_load_request_reused` | activeRequest、activeReason、loadReason |
| `photo_load_request_rejected` | reason（`coalesced_into_running_refresh` / `unchanged_scope_scan_running`） |
| `photo_refresh_baseline_captured` / `_rejected` | baselineCount、baselineComplete、displayedCount |
| `photo_partial_reconciled` | baseline、discovered、retained、added、updated |
| `photo_partial_publication_suppressed` | reason、displayed、mergedCount、headroom |
| `photo_grid_adapter_committed` | stage、committed、sourceCount、fromCache |
| `photo_ui_final_summary` | adapterSubmitCount、adapterCommitCount、committed、sourceCount |

`photo_grid_publication_committed` 仍在 `publishMediaSnapshot()` 之前发出，含义是「接受了待发布
快照」；Adapter 的真实提交由 `photo_grid_adapter_committed` 证明。两者不再混用。
`media_ui_summary` 中 `adapterSubmitCount` / `adapterCommitCount` 仍可能是 `-1`（worker 在没有 UI
计数时结束），UI 侧最终数字改由 `photo_ui_final_summary` 单独给出，避免用未完成的 worker 汇总
伪造 UI 数据。

## 4. 工作量对比（可复现的主机证据）

`PhotoSnapshotAdmissionTest.scenarioTwo` 用 pp-6 的真实候选序列
`160, 1200, 2041, 3361, 4561, 5401, 6241, 7201, 8041, 8988, 9842, 10821, 10968` 驱动真实策略：

| 指标 | 修复前（真机日志） | 修复后（策略模拟） |
|---|---:|---:|
| FILESYSTEM 中间发布 | 12 | 3 |
| 总提交（含 COMPLETE） | 13 | 4 |
| 是否出现 160 → 120 | 是 | 否 |
| 显示数量是否单调 | 否 | 是 |

几何合并点：1200（≥2×160）→ 3361（≥2×1200）→ 7201（≥2×3361）→ COMPLETE。

> 这些是**工作量**（发布次数/集合规模）对比，不是 Pixel 8 Pro 的毫秒数。宿主机上的 JVM
> 毫秒不能被当作真机性能。

场景一（已有完整 12,000 项且内容未变）：中间发布 0 次，只有 COMPLETE 一次 `NO_OP` 提交。
场景三（真实变化）：删除只在 COMPLETE 生效，`SUPPRESS_SHRINK` 不得吞掉最终结果。

## 5. 自动化测试

新增（51 个用例，全部通过）：

| 文件 | 覆盖 |
|---|---|
| `PhotoRefreshBaselineTest` | A01–A12：部分批次不丢旧媒体、完整列表不退回扫描窗口、同路径去重与元数据更新、不同身份同数量、scope/access/revision/locked/search/random 拒绝 |
| `PhotoRefreshBaselineLoaderTest` | 真实临时目录 + 真实 `loadStaged`：删除 20/40 后首个 FILESYSTEM 阶段仍 ≥ 40，COMPLETE = 20（权威）；完整基线不产生中间发布；未变化刷新提交相同权威集合 |
| `PhotoLoadAdmissionTest` | B01–B12：SCOPE_CHANGED→ENTER 复用、onResume 先到、陈旧回调、REFRESH→ENTER、A→B→A、失败重试、连续下拉合并、内容/授权变化 |
| `PhotoSnapshotAdmissionTest` | C01–C09、C14 与三个场景：首个预览不被阻塞、完整列表不被反复替换、部分列表增长合并、临近末端及时扩展、COMPLETE 永远发布、永不缩小、头部插入不是 APPEND、尾部追加保留快速路径、纯重排走 REORDER |
| `Phase12PhotoPublicationWiringTest` | 准入门在 generation 之前、复用分支无副作用、合并刷新有后续、`isMediaGridMoving` 已从保留条件移除、基线与 headroom 已传入 worker、显示侧第二道闸门、诊断事件名 |

`PhotoRefreshBaselineLoaderTest` 的有效性已单独验证：临时把 loader 的基线取用改为 `null` 后，
`firstFilesystemBatchKeepsTheDisplayedRowsAndCompleteStaysAuthoritative` 立即失败，恢复后通过。

命令（本机 JDK 21，与 CI 一致）：

```
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:testFossDebugUnitTest
```

结果：**1,504 tests / 0 failures / 0 skipped**（本轮基线为 1,453，新增 51）。

> 注意：本机默认 `java` 是 17，Robolectric 的 API 36 沙箱需要 21；用 17 运行会得到
> `Android SDK 36 requires Java 21` 的 2 个环境性失败（与本轮改动无关）。

## 6. P2：扫描与访问验证成本（有限审计，未实施）

有证据但**未改动**，因为任何降低最终访问验证成本的方案都会触及安全边界：

- 最终 COMPLETE 的完整访问验证（约 1,486 ms / 10,968 项）是完整性证明，不能跳过；
- 中间阶段的 121 项 `photo_access` 批次（4–36 ms）已经是按批合并的父目录检查；
- 累计排序 13 次、输入 78,786 项、69 ms，不是主要热点；
- 同一请求内的 canonical path / 父目录检查已由 `PhotoAccessValidationBatch` 做批次内复用；
  跨请求缓存 canonical path 授权结果被明确禁止。

本轮没有为此重写比较器或缓存授权，符合任务书的「优先交付正确的 P0」。

## 7. 待真机验收（不得由本文件声称已通过）

| 项目 | 目标 |
|---|---|
| 已有部分列表刷新 | 不再出现 160 → 120 |
| 已有完整列表刷新 | 不再缩小为初始扫描窗口 |
| 配置切换 | 只启动一个 worker（`photo_load_request_reused` 出现且无第二次 `media_worker_started`） |
| 最终媒体数量 | 与合法来源一致；真实删除生效 |
| 权限撤销 | 无受保护媒体残留 |
| 搜索/排序 | 不被旧扫描覆盖 |
| Viewer 连续浏览 | 无边界或图片身份错配 |
| 首屏 | 保持 ≈200 ms，优先 <500 ms；不出现常态化 700 ms 兜底 |
| 热内存缓存 | 完整扫描后在有效期内往返 5 次，`previewEligible=true` / `completeReusable=false` / `evictionPerformed=false` |

## 8. 本轮未做

- 未执行 Phase 2（Viewer 转场、预测性返回、下拖退出等）；
- 未新增 Room schema 或 Migration；
- 未在真机上验证任何「修复后」指标；
- 未对 P2 做任何代码级优化。
