# 26 — Phase 1.5 Viewer 一致性定点实施

基线 `7670a10a3b857df490ea2220f6fa1272f48a8879`。全部修改位于 `refactor/media-pipeline-stability`；未更改main、数据库Schema、UI风格、Diff/stable ID、图片解码/签名配置。原始设备证据与F/R/U见25，最终CI及交接见27。

## 1. 文件与调用链

| 文件／函数 | 修改前 | 修改后／边界 |
|---|---|---|
| `MediaAdapter.committedVersion` / `submitPendingUpdate` | 提交日志没有独立显示版本 | 仅实际替换已显示media时递增；pending和未完成Diff不递增；保留diffGeneration及sameDisplayedContent |
| `MediaActivity.openOrdinaryViewer` | 最多5项邻近元数据，完整目录未就绪时其他缓存候选丢失 | 在点击主线程捕获已commit列表引用、版本、排序/filter/hidden/search/session/revision；worker复制候选到进程内单次Store，无大Intent |
| `MediaActivity.openInViewPager` | 交接有source/session和路径，但无独立显示版本/显示条件契约 | offer带已提交版本和显示契约；后台catalog不能冒充commit；仍需独立权限验证 |
| `ViewerGridOrderStore.offer/takeEvidence` | 30秒、5万项、单次paths证据 | 保留生命周期/上限，增候选副本、版本及显示条件；不匹配拒绝；从未包含访问凭据。普通候选在主线程点击之外复制 |
| `ViewPagerActivity.takeGridEvidence/startFolderViewer` | 第一个5项窗口之后等待枚举自然到达相邻路径 | 验证已commit的前后各64项候选（最多129），仍为PARTIAL/UNKNOWN；每次临时边界可围绕当前路径重取邻域；自己的当前session/文件可读/存在/type/hidden/search/目录范围/revision均须通过 |
| `ViewerCommittedWindow` / `GetMediaAsynctask.folderPriorityPaths` / `MediaFetcher.preferredFolderOrder` | 原始枚举顺序与点击邻域无关系 | 新扫描先读取已提交邻域，随后仍遍历每个剩余文件；未关闭.noMedia发现或全目录扫描；不引入目录mtime完整性假设 |
| `ViewerFolderScanGate` / `startFolderViewer.scanRemaining` | 默认AsyncTask执行器，无Viewer单独deadline/取消回调和拒绝处理 | 复用bounded扫描执行器，一个attempt所有权；45秒无新增有效进度deadline（初始含排队等待；新增候选才续期），失败/取消/超时/拒绝不报END，停止Loading且可在边界重试；迟到attempt不能结束新attempt |
| `ViewPagerActivity.publishFolderCandidates` | partial依靠连续路径；完整仍旧网格优先 | partial仍保守连续，重新验证候选；非随机完整使用新扫描排序；校验捕获的FolderContentRevision；complete准备中不让新候选扩展取消其发布 |
| `ViewerGridOrderPolicy.reconcilePhotoCatalog` | 无论partial/complete都旧grid优先+append | 显式complete/random契约；完整非随机采用已验证生产者全局顺序；partial与random保留旧邻域，分段ordinal继续作为权威 |
| `ViewPagerActivity.completePhotoViewerLoad/consumePhotoCatalogAdvance` | 旧1070顺序一直支配5285完整集合 | 完整非随机升级时放弃旧顺序证据、保留当前path；手势中last-wins延期。升级允许新合法成员进入，可能改变邻居；这属于完整全局排序契约，不能假称新成员也曾显示于旧网格 |
| `ViewPagerActivity.gotMedia/applySwipeMediaPublication` | 部分边界可能早报COMPLETE；异步工作完成时重新stamp revision | 实际应用目录后才认END；捕获worker输入revision并在UI/idle再核验；实际应用时读取当前path，缺失partial不覆盖，缺失complete仅复用既有删除邻居策略，不以旧整数替换 |
| `PhotoProfileMediaLoader.loadStaged` / `ProgressivePublicationBudget` | 每120项或200ms批次重建/排序累计snapshot | worker-local合并所有batch，首次有效可立即发，其后默认500ms间隔才验证新增批次、复制和排序累计；COMPLETE独立必发；用内容version，不能用count代替成员变化 |
| `FolderRefreshTrace.measure` | 默认每个metadata读取输出start/end | file_metadata仅保留pipeline聚合计数/时间；不改变实际读取/异常；其他扫描错误和终态仍记录。独立folder target开关仍可用enumerated/type/metadata_passed匿名事件定位，不向默认日志倒入每文件timing |
| `GetMediaAsynctask.onProgressUpdate` / `MediaActivity.getPhotoProfileMedia` | stagePublishCount双计数、照片businessRequest缺省0 | loader负责publication计数；onProgress只计收到的stage；照片传真实request ID；Viewer扫描标记loadReason=VIEWER |
| `ViewPagerActivity.recordViewerDiagnosticPublication` | 仅数量/整数位置 | 追加gridVersion、handoffCount、revision、当前位置/前后匿名ID、containsSelected和orderMode；配合handoff capture/consume ID判断版本交错，最多一次目录publication一条摘要 |

## 2. 目录协议

1. 点击证据：Adapter.media已经commit；source配置+搜索+排序+筛选+session+revision组成契约，Store只短期保存元数据。异步候选/最新catalog不表示网格已显示。
2. 快速首屏：当前图片和原先最多5项邻近窗口；普通候选可在自身Worker重新验证后扩展，不等全量元数据扫描。没有可验证候选时维持UNKNOWN并继续扫描。
3. partial：仅接纳旧grid中围绕**当前path**的连续已验证路径，保留未知缺口；已删除/不可读/未授权不为了连续性绕过检查。
4. COMPLETE：来源与授权仍有效才可采用；非随机按生产者既有全局排序；随机保留已显示相对顺序并追加新成员。只对现有比较器主键相同的情形补充原始路径次级键，未重写比较器或每页重排整个目录。
5. 手势中目录延期，idle时再读当前真实path。缺失partial不覆盖；完整证明已移除时优先既有历史/邻居删除策略。请求代际、revision失效、失权优先于顺序。
6. 普通旧Activity仍在onPause取消并失效其UI代际。共享的是已提交候选副本，不是旧Activity worker；不声称已经避免全部重复枚举/元数据读取。当前请求内读取并未发现足够证据安全跳过physical metadata，保持每个文件验证。

## 3. 回归测试与证据限度

新增 `Phase15ViewerRegressionTest`（17 tests）和 `Phase15GridEvidenceTest`（6 tests）。使用真实生产helper和显式事件调度，不使用Thread.sleep。具体真实总数以CI XML为准。

| 需求 | 新/既有覆盖 | 限制 |
|---|---|---|
| A01/A03/A04/A06 | 真实coordinator + committed window + position policy + scan gate；100/11348项，连续20项及完整结果晚到 | helper/JVM，不是实际Pixel触控或耗时证明 |
| A02/A05 | attempt取消/旧完成/重试/拒绝类型序列；保留RefreshReliabilityTest bounded executor和RefreshWatchdog测试 | 没有新共享Activity producer；实际onPause→新Activity时序需设备 |
| B01/B02/B03/B06 | 1070/3434/5285，version9；完整全局日期样例；idle last-wins与live path | Store/排序/门控证明，不是实际RecyclerView帧录像 |
| B04/B05 | random稳定邻域，partial缺口和COMPLETE新增成员全局顺序 | `.nomedia`实际读取依赖既有PhotoProfileMediaLoadingTest和FolderRefreshActivityTest |
| B07/D01/D04 | source/search/sort/session/revision不匹配拒绝；已撤销FolderUnlockSession不能靠grid evidence读取；既有protected source tests | scoped/SAF动态授权仍PENDING_DEVICE |
| B08 | 审计MyPagerAdapter身份、Glide target/signature/clear；保留ViewerMediaUpdatePolicy等测试 | 未构造真实B位图晚到A的失败实验；不改Glide、不宣称通过B08 |
| C01/C02/C04/C05 | 45批次手动时钟→9次admit，COMPLETE最后结果门控，same version skip，同count不同content version | 固定时钟合成输入，只证明预算，不是设备性能改善 |
| C03 | 既有MediaAdapter/Diff、ViewerSwipePublicationGate；新idle最新集合断言 | UI滚动+后台Diff真机时序待测 |
| D02/D03/D05 | 既有.nomedia/目录读状态/取消/删除历史测试；新鲜成员不能复活删除路径；captured revision UI检查 | 外部真实移动/撤权和文件系统实际故障矩阵待测 |
| 诊断开/关 | file_metadata block全部执行、异常传播；开时不生成200个逐文件timing | 不代表诊断磁盘开销已量化 |

修复前红灯证据：完整日志证明A窗口长期保持5项；B2旧算法用 `grid=[100,80,60], fresh=[120,100,90,80,70,60]` 可静态得旧结果 `[100,80,60,120,90,70]`，新测试断言权威fresh。**未在修复前成功运行Gradle取得红灯XML**：本地wrapper下载报Network is unreachable。不能把新测试编译错误或CI通过冒充前后红绿实验。运行测试及全部历史863项扩展后的结果见27。

## 4. 性能边界

原设备两轮完整扫描的元数据分别6720ms/5685ms，排序99ms/81ms。此次只合并无意义publication，未改Room/排序主键或随机语义、线程池尺寸或跳过真实文件读取。新增有界候选重新验证有实际文件状态检查成本；没有同设备前后测量，无法承诺扩展毫秒数或性能百分比。所有真实性能复测项目见27。

补充的B2排序边界：`MediaFetcher.sortMedia` 在主键相等时原本继承输入顺序，累计/完整扫描的输入顺序可能不同。只添加原始path次级键（方向沿用原descending）；新测试对名称/大小/拍摄日期/修改日期/路径的相等键、正逆输入与升降序调用真实sortMedia。随机分支仍提前shuffle返回，不受影响。这个修改用于确定性一致性，不声称是性能优化。

最后的所有权清理：`startFolderViewer` 每次重新认证前 retire 旧 gate、停止旧watchdog并清空局部扩展准备标志；failed也清空扩展标志。A02/A05所覆盖的旧attempt完成不得退休retry，必须同时落实在真实Activity入口，不仅存在于helper。最终受测源码见27。

无进度超时回归：新增生产gate的progress(attempt,count)，只有当前attempt且accepted数量严格增加才能续期；重复/倒退/旧attempt不能隐藏停滞，retry独立从0累计。`a05OnlyNewCurrentAttemptProgressMayRenewTheStallDeadline` 显式覆盖此时序；既有RefreshReliabilityTest覆盖真实Handler watchdog的stop/arm/过期，均无sleep。两份Phase15测试文件最终共23项。
