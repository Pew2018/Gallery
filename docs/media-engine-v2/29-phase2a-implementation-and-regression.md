# 29 — Phase 2A 实施与回归边界

基于审计 [28](28-phase2a-hotspot-audit.md)。本分支：`refactor/media-pipeline-stability`；修改前远程HEAD `500f327a534832b83e872babc26ef32729e07867`。当前实现只有普通单文件夹排序复用；不重写Viewer、不新增媒体索引/Room Schema、不改UI主题。

## 1. 代码路径变化

### 普通文件夹排序

**修改前**：`MediaActivity.showSortingDialog()` 将 `mLoadedInitialPhotos=false`、`mediaGrid.adapter=null`，随后`getMedia()`；即使当前普通文件夹已有完整、当前请求对应的媒体集合，也再次查缓存并调度媒体扫描。

**修改后**：`MediaSortReusePolicy.canReuse()`只允许以下状态复用：当前路径是普通物理目录、非照片配置/主页/回收站、没有活动扫描、完成请求与当前代际一致、终态和folder read均COMPLETE、访问仍有效、当前列表为未分组Medium行。条件满足时捕获不可变副本，在后台用原`MediaFetcher.sortMedia()`排序；返回主线程前再次检查请求、排序选择、保护会话及文件夹read权限代际。之后替换`mMedia`并沿原`setupAdapter()`/MediaAdapter更新路径交给Diff，不清除Adapter。当前搜索仍由`searchQueryChanged()`从完整媒体快照重新筛选；不丢搜索词或媒体成员。

条件不足或扫描/刷新中时仍走原全量加载，PARTIAL最终结果照常完成并接管。照片配置、收藏、主页、回收站和特殊分组不走此快捷路径。名称/路径、拍摄/修改日期、大小、随机全部复用生产排序器，所以升降序、natural numeric、locale、path tie-break和random语义没有复制一套实现。

### 媒体元数据、PhotoProfile补扫、Viewer、Adapter与数据库

没有改这些读取/发布逻辑：每个物理媒体仍检查实体存在、常规文件与可读状态；SAF仍验证持久化授权、查询provider document metadata并打开descriptor；显式`.nomedia`目录仍进行磁盘枚举；MediaStore只提供候选和既有metadata，不授予文件访问权。缓存签名没有变化。Viewer保护机制按[28](28-phase2a-hotspot-audit.md)逐项核对，`ViewPagerActivity.startFolderViewer()`、`consumePhotoCatalogAdvance()`和所有列举的gate/store均无diff。

没有修改`ProgressivePublicationBudget`、MediaAdapter、stable ID、DiffUtil或滑动延期。没有Room Entity/DAO/Schema/Migration改动，也没有推测性索引。

## 2. 工作量变化和数据完整性

- 静态调用链证明：满足复用条件时，不再为一次排序触发`getMedia()`、缓存读取/扫描task创建或全量磁盘补扫；只复制当前列表、后台排序和一次既有Adapter更新。
- 本轮未运行设备计数器，不能报告具体少了几次MediaStore/Room调用或多少毫秒。系统调用减少是由代码分支避免整条加载路径推导，不是设备实测数量。
- 每次本地重排使用同一正式COMPLETE集合，成员不被filter/搜索剔除；搜索只作用于显示派生列表。任何partial、过期generation或权限变化均拒绝复用。
- 文件在完整扫描后、用户点击排序前发生外部增删时，普通排序按最近一次COMPLETE列表处理；发现新变化仍由正常刷新/媒体变化gate获取。本优化不声称排序本身触发发现新文件。
- 文件大小未知时继续沿原`Medium.size`排序（零值位置由原比较器确定）；不伪造尺寸。首个cache/partial没有Complete元数据时不复用。
- 随机排序仍调用既有`shuffle()`；此快捷路径不固定随机顺序，也不共享永久排序键。
- 受保护文件夹仅在本次扫描access closure和当前unlock session都有效时复用；权限撤销会拒绝UI回写，不把网格快照当授权凭据。

## 3. 自动化回归

新增`MediaSortReusePolicyTest` 3项：当前完整目录允许复用；partial、扫描中、过期请求、无效read权限拒绝复用；Profile/主页/回收站/分组媒体使用原路径。测试复用生产调用的纯策略。

排序实际比较器已有`Phase15GridEvidenceTest`覆盖路径次级顺序与升降序，并保留自然数字、随机分支及已有Phase1.5 Viewer一致性用例。本阶段新增策略测试不替代UI/Adapter交互或真机耗时验证。

本地执行`./gradlew :app:testFossReleaseUnitTest`未能启动：Gradle Wrapper下载9.5.1时环境返回`Network is unreachable`。因此本地没有测试通过结论；完整测试以提交后GitHub Actions结果为准。提交前静态diff检查：`git diff --check`。

## 4. 未实施事项与待设备测量

- 不跳过文件存在性/readability/regular验证、不复用跨阶段授权证据、不按MediaStore行判定文件存在。
- 不做排序键预计算：旧日志显示排序99ms/81ms（修复前、约5285项），明显小于metadata数秒；无新设备证据说明排序已成为显著热点。
- 不搬迁`MainActivity.setupAdapter()`准备流程到后台：目前只确认其处于主线程路径，目录规模、阶段耗时及锚点抖动缺少当前测量。
- 不减掉PhotoAccessValidationBatch的preview/complete授权重验；不更改`.nomedia`补扫或扫描COMPLETE/PARTIAL协议。
- 不更改Room查询/索引：本地没有目标设备DB及可执行的实际query plan；DAO源码可列出语句，但无法替代SQLite `EXPLAIN QUERY PLAN`实测。
- Pixel 8 Pro/Android 16所有数据规模、SAF、ALL_FILES、收藏、多文件夹和扫描中切页矩阵均为`PENDING_DEVICE`；本地JVM/JUnit结果不能代替真实首显时间、Perfetto或触控连续浏览。
