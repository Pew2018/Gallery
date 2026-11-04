# 09 — 第二阶段：刷新生命周期与渐进发布稳定性

日期：2026-11-04（执行环境 UTC）  
仓库：Pew2018/Gallery  
唯一目标分支：fix/photo-viewer-five-item-boundary  
开始时重新读取的远程 HEAD：a80f191987f0c2de978cf43ed289598092d529aa  
开始时 main：44b31846fcb8b817d27ff5fef05829601260b3f5  
审计和修改范围：扫描终态/缓存边界、分类目录渐进快照、照片配置渐进发布；不重做阶段一排序优化，不重写 Viewer、Adapter 或媒体读取器。

## 1. 用户目标与本轮结论

用户反馈了刷新时索引长时间不结束、新媒体发现迟缓、.nomedia 内容/主页目录数不一致，以及照片网格刷新时连续移动、闪烁或滚动位置变化。本轮先以最新 fix 源码核对这些历史现象，不把历史反馈当成当前已复现的根因。

静态审计确认了三个可定点处理的问题：

1. 分类目录缓存读取异常会直接终止本次分类加载，跳过后续实际文件系统/MediaStore 扫描；这与普通主页缓存异常后仍继续正式扫描的处理不一致。
2. 分类扫描对相同目录状态存在重复进度发布入口。DirectoryAdapter 已能短路相同内容，但重复调用仍会在 Activity 侧复制、过滤和排序快照。
3. 照片配置的每个 filesystem 候选批次都会进入累计 stage 发布；批次在访问校验后可能没有新增可见媒体，仍重复进入 Viewer partial store 与 Activity/Adapter 更新路径。

没有证据支持替换现有 request generation、epoch、terminal gate、取消、Watchdog、DiffUtil 或权限系统。没有设备 Trace 证据证明现阶段的闪烁/滚动跳跃由这些提交直接造成，本轮不宣称已彻底修复视觉症状。

## 2. 审计调用链与状态边界

以下行号以起始远程 HEAD a80f191… 为基线；实际修改后的行号会后移。

| 流程 | 调用链与源码事实 F | 状态 / 终态 / 当前风险 |
|---|---|---|
| 普通主页目录 | MainActivity.getDirectories() L1062–1123 → 版本化 getCachedDirectories() → GalleryAlbumScanExecutor → gotDirectories() → setupAdapter() / DirectoryAdapter.updateDirs() → finishDirectoryScan() L1126–1138。缓存快照先交给正式读取；cache error 使用主线程捕获的旧卡片快照继续正式扫描。 | DirectoryScanEpoch + GalleryScanTerminalGate；新请求使旧请求失效，manual refresh retire 旧扫描；执行器拒绝、扫描异常、Worker 返回、45 秒 watchdog 都有 FAILED/CANCELLED/TIMED_OUT 或正常结果路径。仅当前 epoch 可以终结并关闭 loading。F：无完整性依据的 partial 不会直接作 COMPLETE；目录 metadata repository 只提交当前范围、权限有效且 FolderReadStatus.COMPLETE 的结果。 |
| 分类目录 | MainActivity.loadCategorizedFolders() L1424–1497 → 缓存读取 → CategorizedFolderScanner.scan() → 节流进度回调 setupAdapter() → final scanned.directories 提交。取消依赖 categorizedLoadVersion、可见性快照与扫描器/fetcher cancellation。 | 分类扫描独立 version，不使用普通目录 terminal gate；cache-processing 错误/Executor 拒绝走 failure callback 关闭 loading；暂停时 version 失效、停止 fetcher、设置 reload pending，恢复后重新扫描。已知具体缺口：Room cache reader throw 时 failure callback 原先结束请求，未继续正式 scan。 |
| 普通文件夹/全部媒体/收藏 | MediaActivity.getMedia() L1673 起建立 mediaLoadRequests 与 mediaScanTerminals，arm deadline，再 cache → startAsyncTask() → GetMediaAsynctask → gotMedia() → 权限复核/publishMedia()；最终回调写 scan outcome/元数据并仅完整读取时做缓存 prune。 | AsyncTask stop 传播到 MediaFetcher.shouldStop，回调比较 request/generation/path/activity；RefreshWatchdog 覆盖 cache 等待、队列等待及 worker；执行器拒绝会终止 loading；PARTIAL 不用于删除旧缓存。收藏结果在 publish 前另过当前收藏授权 token。访问失效时受保护目录视图会清除。 |
| 照片配置 | MediaActivity.getPhotoProfileMedia() L269–353 建请求、捕获 PhotoProfileScope、检查 photoDisplayKey 和内容 revision；相同已验证 scope/revision 才允许 retainGrid，否则先清 Adapter；AsyncTask/Loader 按 CACHE_PREVIEW → INDEXED → FILESYSTEM → COMPLETE。 | Loader 每阶段验证 scope、权限和收藏关系；请求替代后 mediaLoadRequests / AsyncTask 取消；UI stage 另检查 request、scope、display key、stage ordinal。授权或来源变化会改变 display key 并清理旧网格。complete 即使和最后预览相同，仍由 AsyncTask 最终回调提交权威状态。 |
| 分类目录进度 | CategorizedFolderScanner.scan() L41–53 对变化进度约 150ms 节流；已知目录 scan 后会调用普通 publish()，随后 publish(force = true)；发现列表里每个 seed 扫描后也会 publish()。 | 这证明重复“尝试发布”路径，不等于每次产生 DiffUtil 动画。原 Adapter DirectoryPresentation.sameContents() 已短路完全相同的已排序目录，但 Activity 在进入 Adapter 前仍先复制、去重、排序。 |
| 配置渐进批次 | PhotoProfileMediaLoader.loadStaged() L120–205 做 cache/index/文件系统读取；filesystem 候选批次按路径合并进 supplements，按配置顺序排序累计结果后回调。 | 文件候选可能与已有 index/cache 重叠，也可能在 PhotoAccessValidationBatch 后无变化。原每个非空累计列表都会到 PhotoSessionViewerCatalogStore.offerPartial() 和 onStage()；Adapter 可能短路，但 publishMedia() 中的 loading、search generation、菜单、滚动和提交回调仍会运行。 |
| Adapter / Viewer | MediaAdapter.updateMedia() L710–779 单在途 diff + 最新 pending、相同列表直接 commit、滚动时延期，基于 MediaUpdatePolicy.sameContent() 比较；DirectoryAdapter.updateDirs() L968–1030 同内容短路、滚动/选中/拖动时延期并保存锚点。Viewer 有单独目录 revision、分段目录、滑动发布门和授权校验。 | Adapter 已有机制足够支持本轮上游去重，未发现需要重建 Adapter 或全局禁用动画的证据。保留 Viewer 独立实现和目录授权交接。 |

### 扫描生命周期问题清单

- 必须修改（F）：分类缓存查询失败走了最终失败处理而不是读取候选回退；cache 只是 provisional presentation，实际扫描能独立通过 .nomedia hints、已知来源、MediaStore、目录发现和常见根目录继续工作。
- 必须精简（F）：分类快照相同仍可重复跨 Activity 边界；配置累计结果相同仍可反复发 stage。两者都能在调用发布前安全比较内容。
- 保留（F）：主页 epoch/terminal gate、缓存失败 fallback、媒体 request generation、GalleryMediaScanExecutor 队列拒绝、45 秒 Watchdog、AsyncTask cancellation、目录 metadata COMPLETE + scope + authorization 门控、Adapter pending/diff/锚点、.nomedia 实际枚举、PhotoProfileSourceScanPolicy/PhotoAccessValidationBatch、Viewer partial/full 边界策略。
- 不调整（I/U）：不同页面/scope 可能读取相同物理媒体；是否一次用户操作重复读取以及重复次数需要 request trace。普通主页与文件夹页之间没有单一跨页面读请求表是代码结构事实，不代表当次一定重复扫描。
- 未确认（U）：用户报告的索引长时间运行、卡片数滞后、缩略图闪烁、快速滚动位移在目标设备当前版本是否仍复现；没有对应当前 APK 的设备日志、AdapterDataObserver、录屏或 Frame Timeline。

## 3. 实际修改

| 文件 / 函数 | 修改前 | 修改后 | 兼容理由 |
|---|---|---|---|
| app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt / loadCategorizedFolders() | 分类缓存读取异常进入 failed，停止 loading 但跳过正式 scan。 | 主线程预先复制当前目录快照；cache reader 异常时记录无路径日志并回退到该快照，继续已有分类扫描；执行器拒绝/过滤流程异常仍进入原失败终结。 | 保留 cache fallback 为 presentation-only，不把旧数据当权威结果；分类策略、隐藏/排除、.nomedia、受保护目录检查、COMPLETE metadata 提交原样执行。 |
| app/src/main/kotlin/org/fossify/gallery/helpers/CategorizedFolderScanner.kt / scan() progress | 150ms 节流后不区分内容，重复提交相同快照。 | 仍保留 150ms 节流和首个有效预览；比较 DirectoryPresentation.sameContents()，只发首次或实际可见差异快照。 | final Result 和 Activity final setupAdapter 不变；节流只过滤无变化状态，不丢新增目录或完成结果；只比较展示列表及 statuses。 |
| app/src/main/kotlin/org/fossify/gallery/helpers/PhotoProfileMediaLoader.kt / loadStaged() | 每个非空 stage 都调用 Viewer partial offer 和 Activity 回调。 | 增加轻量 PhotoLoadPublicationPolicy：同一 PhotoProfileLoadContext、相同 PhotoAccessSummary，并且顺序/身份及 MediaUpdatePolicy.sameContent() 一致时跳过后续 stage 发布。 | 保留 CACHE_PREVIEW 首次有效预览、真正新增/删除/元数据变化时的 stage、access/context 变化；COMPLETE 不走中间发布去重，仍由最终 AsyncTask 回调交接。比较字段与 MediaAdapter 当前可见内容契约一致（排除 DB id、MediaStore id、gridPosition）。 |
| app/src/test/kotlin/org/fossify/gallery/helpers/FolderRefreshActivityTest.kt | 未覆盖分类 cache read failure。 | 新增 categoryCacheQueryFailureFallsBackToFormalFilesystemScan()，通过注入 cache failure 验证 .nomedia 文件夹新照片仍形成主页分类卡片、loading 结束。 | 复用既有真实 Activity 回归类，不建新测试框架。 |
| app/src/test/kotlin/org/fossify/gallery/helpers/NoMediaCategoryRefreshTest.kt | 测试媒体计数/.nomedia 和渐进首次可见，但未断言重复 snapshot。 | 新增 categoryProgressDoesNotRepublishAnIdenticalDirectorySnapshot()，断言连续 progress 快照的展示内容都不同，并保留至少一个首次有效 preview。 | 读写只在 Robolectric 临时目录。 |
| app/src/test/kotlin/org/fossify/gallery/helpers/PhotoProfileMediaLoadingTest.kt | 多批 filesystem 与顺序已有覆盖，未验证可见相同 stage 被抑制/策略匹配 Adapter。 | 新增 stagedPublicationComparisonMatchesGridVisibleContentAndKeepsScopeAndAccessStrict()，验证忽略纯数据库/布局身份字段，但内容、权限摘要、scope revision 改变仍发更新；多批 .nomedia 测试另外断言没有相邻重复可见 stage。 | 不削弱跨源去重、随机 rank、当前访问校验或 Viewer catalog handoff。 |

### 前后可观察行为

- 分类缓存可读时仍先用缓存作为扫描种子；缓存异常时从先前展示的不可变快照继续走扫描器，不将缓存失败解释成空目录或停止加载。
- 分类进度仍按原时间节流增量显示；完全相同的进度不再重复进入 MainActivity 的复制、排序和 Adapter 更新入口。最终 scanned.directories 仍无条件通过 current-generation 检查后提交。
- 照片配置仍可先展示 cache/index/首批 filesystem 内容；后续 filesystem 批次只有在网格可见内容/顺序或访问摘要变化时才推送新的 partial 和 Activity stage；最终完整结果总是继续进入 complete callback。
- adapter = null、滚动锚点、选中模式、拖动模式、授权失效清理路径没有改动。普通/受保护目录的 retainGrid 当前依赖 PhotoProfileScope.capture()、来源/key 和 PhotoContentRevision；未改其条件。

## 4. 自动化验证

已加入的测试：

1. FolderRefreshActivityTest.categoryCacheQueryFailureFallsBackToFormalFilesystemScan
2. NoMediaCategoryRefreshTest.categoryProgressDoesNotRepublishAnIdenticalDirectorySnapshot
3. PhotoProfileMediaLoadingTest.stagedPublicationComparisonMatchesGridVisibleContentAndKeepsScopeAndAccessStrict
4. PhotoProfileMediaLoadingTest.unindexedNoMediaFolderPublishesValidatedBatchesBeforeComplete 扩展相邻可见 stage 去重断言。

本地定向命令：

    ./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.NoMediaCategoryRefreshTest --tests org.fossify.gallery.helpers.FolderRefreshActivityTest --tests org.fossify.gallery.helpers.PhotoProfileMediaLoadingTest --stacktrace

实际状态：Gradle 尚未启动，测试尚未执行。Wrapper 尝试获取仓库固定的 Gradle 9.5.1 distribution，执行环境连接 services.gradle.org 时返回 java.net.SocketException: Network is unreachable。本环境不存在已安装的 Gradle 9.5.1 或 Gradle cache。该结果是环境阻塞，不是 Kotlin 编译或测试断言失败。本轮完整 FOSS Release 测试、APK 和 Actions 状态在代码提交后补录本文件及 08。

## 5. 兼容性和未修改项

- 没有修改 MediaFetcher 文件读取算法、MediaStore/SAF 选择、.nomedia 读取、Room schema、设置键、照片配置持久格式、备份格式或签名/密钥。
- 没有修改 ViewPagerActivity、Adapter 更新实现、Ripple 或 RecyclerView 项目动画；保留现有双向渐进 Viewer 和权限 revalidation。
- 没有对 Android 8/11/14/16 真机/模拟器做验证。本地 build 环境无 Gradle，不能用静态审查代替 Actions 编译。
- 本次发布去重只以 UI 实际内容策略过滤重复快照，不更改最终媒体顺序、scope、权限或数据删除权威条件。

## 6. A01 / A02 / A03 / A04 / A08 验收台账

| 验收 | 本轮结论 | 证据 / 尚缺 |
|---|---|---|
| A01 普通与 .nomedia 数量及来源一致 | 待本轮 CI + 真机 | 增加分类 cache failure 的 .nomedia Activity 回归用例；普通主页/分类数与目录内列表三方一致仍需完整测试与设备数据。 |
| A02 刷新生命周期 / 旧请求不覆盖 / loading 结束 | 代码防护审计已完成；本轮 CI 待验证；真机待验 | 原有 epoch/terminal/request/watchdog/cancel/拒绝路径保留；新测试锁定分类缓存失败结束/继续读取。高频返回/切换页面真机序列无 trace。 |
| A03 刷新稳定 / 滚动 / 闪烁 | 代码级重复发布减少；最终视觉结果未验证 | 分类和配置重复可见快照不会再发；Adapter diff/pending/锚点保持原样。没有设备录屏、像素偏移、重绑计数，不称“扑克牌动画已修复”。 |
| A04 排序、身份、随机顺序、Viewer 顺序 | 阶段一 Actions 已通过；本轮阶段测试待 Actions | 仅按 Adapter 同一内容比较去重；stage 顺序/随机 rank/全屏交接代码未改。Android 实机网格与 Viewer 一致性待验。 |
| A08 性能对照 | 运行时指标未测 | 确定减少的是重复分类快照跨 Activity 发布以及同内容配置 stage 的 Viewer/UI 交接；无性能百分比、p50/p95、磁盘/查询或 frame data。 |

## 7. Actions / APK 与剩余工作

代码提交 SHA、Run ID、Run URL、FOSS Release 测试汇总、ARM64 artifact 名称/入口、构建对应 SHA 将在 Actions 完成后补记。此前 Stage 1 Run 41684331467 的 786 项通过和 ARM64 artifact 只验证 5a9d437… 旧代码，不作为本轮验证结果。

剩余最小建议：先由本轮 CI 验证三个新回归；若通过，下一轮可做纯显示层设备采样（AdapterDataObserver + top item path/offset + 录屏/Frame Timeline）后再决定是否调整 Adapter/动画。未经采样不扩大 UI 修改。
