# 43 — 收藏缓存扫描期间的排序与搜索交互

执行分支：`refactor/media-pipeline-stability`。2026-11-06 开始执行时重新读取远程 HEAD：`7f2151035edad838e8d4d6fc16a88885d430f9de`。未重置历史提交、未新建分支/PR、未修改 main。

## 附件证据与边界

实际阅读附件《收藏虚拟文件夹需要等待进度条消失之后才可以得到及时的排序和搜索反馈但是其他普通文件夹和自定义配置浏览在有进度条的时候也可以得到及时的反馈.txt》（193 行）。设备 Pixel 8 Pro / Android 16 / Release 1.14.1(32)，诊断 session `2d37a5b2`。以下时间保留导出文件的原始时钟，不另作时区推断。

| 时间 | 事件 | 身份与结果 |
| --- | --- | --- |
| 09:58:25.360 | scan_started | business request=1，FAVORITES |
| 09:58:25.579 | folder_snapshot_selected | request=1，source generation=1，382 项 |
| 09:58:25.584 | adapter_committed | final=false |
| 09:58:27.683 | virtual_sort_decision | sort generation=3，sourceVersion=1，wait_for_scan |
| 09:58:29.530 | virtual_sort_decision | generation=4，同一 sourceVersion，wait_for_scan |
| 09:58:31.378 | virtual_sort_decision | generation=5，wait_for_scan |
| 09:58:33.326 | virtual_sort_decision | generation=6，wait_for_scan |
| 09:58:37.067 | virtual_sort_decision | generation=7，wait_for_scan |
| 09:58:38.137 | scan_completed | 382 项 COMPLETE |
| 09:58:38.138 | media_scan_summary | pipeline request=ml-e807771d-2813-4d23-964a-817e2ec440a2，workerMs=12777，totalRequestMs=12779 |
| 09:58:38.501 | adapter_commit | SCAN，snapshotVersion=2，adapterVersion=2 |
| 09:58:40.972 | virtual_sort_snapshot_committed | generation=8，adapterVersion=3，27ms |
| 09:58:43.363 | virtual_sort_snapshot_committed | generation=9，adapterVersion=4，32ms |
| 09:58:45.900–47.707 | search_text_changed/results_committed | 扫描完成后，382→246(310ms)，382→48(241ms)，清空48→382(258ms) |

**已由日志证实**：缓存显示后五次排序被入口策略主动推迟，不能归因于382项排序计算慢。**未由日志证实**：扫描期间搜索的拒绝原因，因为附件中没有该阶段的搜索输入。搜索缺陷须由下面的源码和 Activity 回归测试验证。

## 根因与调用链

排序：`showSortingDialog → tryResortVirtualMediaSnapshot → VirtualMediaSortPolicy.decide`。旧策略将 `displayedSnapshotComplete=false` 与不可安全交互混为一谈，返回 WAIT_FOR_ACTIVE_REFRESH；没有进入 sortMedia/groupMedia/setupAdapter/USER_SORT。最终阶段可走 MediaAdapter 的 reorder 快速提交。

搜索：`searchQueryChanged → authorizedItems → isSearchSnapshotCurrent → MediaSearchRequestPolicy → MediaAdapter.updateMedia(USER_SEARCH)`。原来 `gotMedia(cache) → publishMedia(cache)` 不设置 displayedFavoriteRelationshipRevision，默认 -1 与初始 revision=0 不等；UI 和实际 Adapter commit 的双重 isCurrent 均可拒绝结果。普通文件夹已有 DisplayedFolderSnapshot 的 CACHE_PREVIEW accessCurrent 凭据；收藏只在正式结果上确认关系 revision，因而行为不同。自定义配置走已有 profile context/generation，不经过本次收藏专用 gate。

## 修改与保障

- **MediaActivity.kt / gotMedia(FAVORITES)**：后台读取真实 Favorites Room 集合，去重并交集过滤缓存候选，通过原有 FavoriteAccessPolicy.validateItems（完整文件身份与保护边界验证）得到不可扩权的 ValidatedPaths。关系集合再次比对后，UI 同时复核 request/publication generation/token/revision/config；为缓存及正式结果安装 FavoriteInteractiveSnapshot，设置显示关系 revision。缓存也按当前排序分组，避免缓存回调恢复旧设置。正式完成后的迟到缓存不能替换凭据。
- **FavoriteInteractiveSnapshot.kt**：保存 request、完整关系集合、关系 revision 与原有 session validation。它是当前显示内容的访问凭据，不是第二套索引/缓存。sourceVersion、query generation、sort operation、Adapter 身份由现有 Activity 提交链继续校验。会话实例被替换或撤销即失效。
- **VirtualMediaSortPolicy.kt**：仅 FAVORITES 的显式验证缓存可独立于 COMPLETE 进入成员验证；未验证缓存、回收站、缺少请求/成员/元数据证据仍保持原有等待/受控重读。新增 PREVIEW_VERIFIED_CACHE 语义，不提前设置 COMPLETE。
- **MediaActivity.kt / tryResortVirtualMediaSnapshot**：后台前后读取关系集合；成员集合、排序操作 generation、sourceVersion、request、session、最新 sort/group 和 Adapter 身份都须一致。数值缓存补齐之后才能通过元数据 gate。提交完整来源 mMedia，再由 setupAdapter 对最新 query 过滤。活动扫描保持原请求运行，进度状态不改。
- **FavoriteSortMetadata.kt**：名称/路径/随机无需数值补读；大小读取当前项 file.length，修改时间读取 file.lastModified；拍摄日期通过既有 queryProfileIndexedMedia 仅查询当前路径（既有批次查询和用户自定义日期覆盖），无索引日期沿用 scanner 的修改时间回退。分组日期同样补齐。缓存非零 taken 可能本来就是 fallback，不能据此声称可信，故相关字段重新取得。后台逐项检查最后排序 operation/会话。无法读取的值明确沿用0未知值和既有路径平局规则，不将未请求字段视为已经加载。发布前再做完整 session 文件身份验证，文件删除/替换导致撤权而非发布伪排序。
- **GetMediaAsynctask.kt**：Favorites 在原有正式扫描保留 date/modified/size，避免用户切换字段后因 metadata_not_loaded 重启全集扫描；继续采用最新 sorting，未改扫描器或 Room schema。
- **MediaActivity.kt / searchQueryChanged、isSearchSnapshotCurrent**：保留通用搜索逻辑，收藏改用已验证凭据；搜索工作线程还比对当前 Room 关系，访问验证和 UI/Adapter gates 保留。查询文本不记录；日志仅含长度、generation、sourceVersion、阶段及拒绝原因。清空恢复 mMedia，PARTIAL 零结果仍属临时，扫描新结果重跑当前 query。
- **Favorites Room observer**：读取实际关系集合后才推进 epoch，相同集合的无效通知不反复使快照失效；实际变化继续保留原会话裁剪、新关系撤权流程。排序前后及搜索工作线程的 Room 比对补充 observer 投递延迟窗口。

未改 UI、菜单、间距、动画、Room schema、普通文件夹或配置来源策略；不调用 notifyDataSetChanged、不拆除 Adapter。沿用 USER_SORT/USER_SEARCH/SCAN、adapter_submit/commit/replaced/fast_commit 诊断及原有滚动锚点机制。cache_authorized/cache_preview_sort/cache_metadata_validation/cache_search_rejected 与 sourceVersion 可分辨授权、计算、拒绝、实际提交；正式结果仍记录 final=true。

## 发布时序与渐进扫描结论

缓存候选 → Room成员交集/去重 → worker完整访问验证 → UI安装凭据 → 原Adapter显示 CACHE_AUTHORIZED → 用户排序/搜索（相同来源版本验证） → 正式扫描真实结束 → 完整验证/最新排序分组 → 新 sourceVersion → 保持当前query提交 → COMPLETE进度结束。

重新确认 `onFavoritePreview` 在 GetMediaAsynctask 中存在，Activity 未接入。它提供累积候选而非访问凭据，最多每500ms投递；直接接入将替换缓存全集、反复全会话验证并与最终publication取消相争。本次日志完整验证一次约118–131ms，现有 validateItems 包含两次文件快照校验；给每个候选阶段再叠加完整授权、缓存合并/分组以及diff会引入不可忽略的额外IO与时序范围。因此本轮不接入候选回调；这不是A/B即时交互的前提。后续若接入须提供独立的候选合并/完整终态优先级证据。未以未经验证候选接Adapter来满足清单。

## 确定性回归与验证状态

FolderRefreshActivityTest 新增真实Activity/RecyclerView场景，使用382个真实临时文件、真实Room Favorites及有效session；调用生产 gotMedia(cache)，将正式扫描请求保持在运行状态，不等待真实扫描。关键断言：

1. favoriteCache382SortsBeforeScanCompletesAndKeepsTheRequest：名称排序提交、Adapter不变、请求不变、scan仍运行、completedMediaRequest=-1、final=false。原代码必走wait_for_scan，无法满足顺序断言。
2. favoriteCacheSearchAndClearCommitBeforeScanCompletes：扫描中382→10→382真实Adapter提交。原代码缓存关系版本=-1，结果被拒绝。
3. favoriteCacheRepairsSizeAndDateAndLastSortWins：快速排序、0 size/modified及非零fallback taken补齐、随机提交/去重。
4. favoriteCacheFinalPublicationKeepsLatestQueryAndSort：搜索与排序交错，正式结果晚到仍保留最后query和排序，最后真实完成。
5. favoriteCacheRejectsChangedRelationshipsRevocationAndActivityExit：关系revision和session撤销使生产发布gate拒绝。
6. favoriteCacheDoesNotPublishUnauthorizedCandidateOrOldRequest：不在关系/会话内的候选不发布，旧request不能覆盖。
7. VirtualMediaSortPolicyTest 明确验证无凭据缓存仍WAIT、验证收藏可PREVIEW、回收站不可借用凭据、无成员/元数据证据仍拒绝。

本地 `git diff --check` 可执行。Gradle测试尝试被distribution下载阻断：services.gradle.org Network is unreachable；本地没有Android SDK/已缓存Gradle，未宣称单测通过。完整FOSS Release单测/编译由现有Actions工作流执行，结果将在本文件追加记录。

## 性能与残余风险

| 指标 | 修复前日志 | 修复后 |
| --- | --- | --- |
| 缓存名称排序 | 五次wait，依赖12.8s扫描 | PENDING_DEVICE，目标≤200ms |
| COMPLETE排序 | 27/32ms | PENDING_DEVICE |
| 缓存搜索 | 日志无输入 | PENDING_DEVICE，目标≤500ms |
| COMPLETE搜索 | 310/241/258ms | PENDING_DEVICE |
| 正式扫描 | worker12777ms | 未优化/未测量 |

单测证明状态/提交，不证明Pixel触控到画面延迟。数值补齐、session全量验证、日期分组需要后台IO，可能超过目标，须真机测量；不虚构耗时改进。扫描12.8秒的具体热点证据不足，留待独立任务。

PENDING_DEVICE：快速名称→大小→修改→随机及反复随机；搜索/删字/清空与排序交错；日期分组/倒序；滚动中Adapter锚点与缩略图闪烁；收藏新增/取消/删除/上锁/退出重入；完整扫描终态与进度；普通文件夹/配置/回收站不退化。外部文件身份变动仍依靠原session验证与系统刷新；极短的验证到UI间外部文件变动窗口保持现有安全模型。无变更observer去重与异步投递还须真机验证，不声称整个并发矩阵已全部验证。
