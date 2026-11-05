# 42 — 虚拟媒体快照排序复用：实现与验证

## 实施状态

记录基于本轮远程起始 HEAD b7175e8fa0411d8ff600366e316777efb28bb8f3。业务实现已完成，最终本地/Actions SHA、单测数量、APK Artifact、哈希和签名结果须在提交并运行现有 arm64-foss-release.yml 后补录。当前这些 CI 字段为 PENDING_CI，不代表已通过。

Pixel 8 Pro 交互与显示帧验收为 PENDING_DEVICE。

## 修改范围

生产 Kotlin：

- MediaActivity.kt：排序入口新增 FAVORITES / RECYCLE_BIN 快照复用；继续用现有 MediaFetcher.sortMedia、groupMedia、MediaAdapter.updateMedia(USER_SORT)，保持完整 mMedia 与搜索过滤分离。成员快照在后台排序前后复核，排序操作、displayedSourceVersion、Adapter 身份、来源请求、权限会话和当前配置在 UI 提交前重新验证。活动中的有效扫描继续运行；完整扫描若先发布，旧排序 generation 会被丢弃。
- GetMediaAsynctask.kt：虚拟源读取期间比较最终排序配置；若模式改变且需要字段未读取，使用既有 sortingMetadataRefreshRequired 受控补读；字段可用时在正式分组前应用最新配置。未改变扫描、过滤或授权规则。
- ChangeSortingDialog.kt、SortChangePolicy.kt：相同 RANDOM 设置再次确认也触发一次排序操作，覆盖重复随机排序；确定性排序的相同设置仍短路。
- MainActivity.kt、SearchActivity.kt、Activity.kt、Context.kt 与 MediaActivity.kt：回收站 Room 成员新增、恢复、清空、删除及确认缺失记录的路径更新同一进程内 revision；没有 Room Schema 或 Migration 变更。
- VirtualMediaSortPolicy.kt：区分完整快照复用、完整快照扫描期间预览、成员核验等待、受控重载与权限失效；不改变 Adapter 或 Diff 机制。
- RecycleBinMembershipRevision.kt：给回收站 Room 成员变化提供进程内轻量 epoch。Room 成员查询前后仍做单次集合比较，以防成员变化与排序交错。

操作 revision 在 movePathsInRecycleBin、restoreRecycleBinPaths、emptyTheRecycleBin、deleteMediumWithPath、MainActivity 回收站清理、SearchActivity/MediaActivity 永久删除和 MediaActivity 确认缺失回收站行的路径更新。正式回收站发布记录本次加载 epoch；搜索和排序检查该 epoch。cache 预览不被提升为已验证快照。关系变更后原有操作刷新仍负责发布新来源。

测试：

- VirtualMediaSortPolicyTest.kt：不同虚拟来源准入、完整扫描中预览、成员/metadata/Adapter 不可用时等待或重载、失权结果。
- SortChangePolicyTest.kt：重复 RANDOM 执行、确定性相同配置短路以及 A/B 切换。
- FolderRefreshActivityTest.kt：通过 Robolectric MediaActivity 直接调用生产虚拟排序入口；分别用真实 Room 收藏关系与回收站成员检查最终顺序、Adapter 身份和 request 未变，并验证回收站 membership epoch 使旧搜索快照失效。
- 既有普通目录扫描/排序、Adapter Diff/reorder、搜索和权限用例保留。

本机 Gradle 9.5.1 使用 JDK 21 已完成 buildSrc 编译；`:app:compileFossReleaseKotlin` 在依赖解析前因缺少 Android SDK 退出（未设置 ANDROID_HOME，且没有可用的 sdk.dir）。因此本机没有 Kotlin 源码编译结果；GitHub Actions 必须验证实际编译和完整 FOSS Release 单测。

## 快照复用准入与安全

完整快照必须是当前 Activity 中已提交的 COMPLETE 来源、同路径 Adapter、完整媒体行、当前排序字段齐备且授权有效。普通闲置快照走 REUSE_COMPLETE；排序点击时已有刷新则走 PREVIEW_COMPLETE_DURING_REFRESH，不取消扫描。非完整缓存、字段不足、成员变化、无 Adapter 和失效请求不会被冒充为正式复用。

收藏：沿用 FAVORITES 访问 token。后台检查 token 当前凭据仍有效、当前关系成员全部处于 token 捕获集合，并将 DAO 路径集合与正式扫描记录的 relationship snapshot 比较。收藏 Room observer 增加 Activity 内关系 revision；旧搜索在 observer 确认改动后失效。若新增关系超出现有 token 捕获集合，复用既有撤权/重新解析路径建立新会话，不扩大旧 token 权限。排序不重复逐文件 capture、不因普通排序重新鉴权；排序提交再次读取轻量关系集合，撤权监听仍负责清除受保护列表。

回收站：比较当前单次 getUpdatedDeletedMedia() 结果与完整显示成员，并检查 revision 在查询前后及 UI 提交时未改变。恢复、永久删除、批量恢复和清空更新 epoch；旧排序/搜索发布因此失效。没有 Room Schema 或 Migration 变更。

扫描并发：GetMediaAsynctask 对虚拟来源复读最新 sorting；必要字段缺失则用现有 metadata refresh callback 重新发起有字段读取的请求。正式回收站发布还做最新 sort/group 配置校验。相同 RANDOM 点击由排序操作 generation 新执行一次；旧结果需同时通过操作 generation、来源版本和成员 revision。

搜索：共享 searchQueryChanged 和 MediaAdapter USER_SEARCH 保持不变。排序提交更新完整来源版本并按最新 query 重算；搜索清空从当前完整来源重建。回收站成员 revision 不匹配期间禁止旧搜索快照提交。收藏沿用 session、query generation、request 和 displayedSourceVersion gates。

## 性能证据

- 快路径不调用 getMedia、不创建 GetMediaAsynctask、不进行目录枚举或 MediaStore 全库查询。收藏与回收站各在排序前、提交前读取一次来源成员集合；每个核验阶段是一条批量关系查询，不会按媒体逐项查 Room。
- 成功路径调用 MediaFetcher.sortMedia 与 groupMedia 后提交现有 Adapter；提交策略及耗时由既有 MediaAdapter 诊断记录。
- 同条件主机合成耗时前后比较：NOT_MEASURED。本轮没有虚构扫描耗时降低百分比。
- Pixel 8 Pro 点击至真实屏幕绘制、缩略图重绑、滚动稳定：PENDING_DEVICE。

## Actions/APK

- Workflow：.github/workflows/arm64-foss-release.yml
- Run ID / URL：PENDING_CI
- 实际构建 SHA：PENDING_CI
- FOSS Release 单测总数/失败数：PENDING_CI
- Kotlin 编译、ARM64 APK 构建：PENDING_CI
- 包名/版本/ABI/签名：PENDING_CI
- APK Artifact ID 与下载 SHA-256：PENDING_CI

## 限制及 Pixel 8 Pro 清单

- 当前回收站 epoch 覆盖本轮审计到的 Gallery Room 修改入口；若未来引入绕过这些入口的外部数据库写入，需要接入同一 invalidation 机制。
- 收藏成员变化通过当前 DAO 集合对比和既有 Room observer/session revoke 保护；新受保护收藏关系不可能由旧 token 自动授权，必须走现有授权解析。
- 新增虚拟来源专属 Activity 测试覆盖真实 Room 成员、排序后的成员顺序、Adapter 身份、request 稳定和回收站搜索 epoch 失效。逐项恢复/删除竞态及完整搜索交错矩阵仍需 Actions 与后续真机/回归场景补充；共享 Adapter/Diff/搜索基础测试沿用既有套件。
- 旧正式快照中媒体文件在扫描完成后被外部系统删除、但 Room/关系集合没有变化的竞态仍依赖系统文件变化刷新；本地排序不逐项 File.exists()，避免将每次排序退化为全集合 IO。
- 检查收藏受保护/普通混合来源、排序/搜索/清空交错及返回锚点；回收站逐项与批量恢复、永久删除、清空期间快速排序；切换随机模式、A→B→A、日期/大小字段缺失补读；检查分组标题、Adapter 身份、选择、滚动、Viewer 网格顺序和缩略图请求。
