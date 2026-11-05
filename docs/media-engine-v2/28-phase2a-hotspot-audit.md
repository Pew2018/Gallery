# 28 — Phase 2A 热点审计与性能证据

日期：2026-11-05。仓库 `Pew2018/Gallery`，唯一工作分支 `refactor/media-pipeline-stability`。重新读取的远程起始 HEAD：`500f327a534832b83e872babc26ef32729e07867`。没有重置历史提交。Phase 1.5 真机复测仍为 `PENDING_DEVICE`。

## 1. Phase 1.5 正确性边界复核

审阅 18、25、26、27 与当前代码。以下机制仍存在，Phase 2A 没有修改 Viewer 生产者、目录交接、权限缓存或扫描终态协议：

- `ViewerFolderScanGate` 对扫描 attempt 做所有权、进度、失败/取消/拒绝/超时门控；临时窗口不能自行报告 END。
- `ViewerCommittedWindow`、`ViewerGridOrderStore` 只提供短期、有限、单次消费的已提交网格候选；不授予访问权。
- `PhotoViewerCatalogCoordinator`、`ViewerSwipePublicationGate` 保留渐进候选、COMPLETE 接管、手势期间延期及当前 path 锚点。
- `ProgressivePublicationBudget` 继续对累计发布限流，COMPLETE 独立发布。
- `ViewPagerActivity.startFolderViewer()` 与 `consumePhotoCatalogAdvance()` 未改。

因此这次局部排序不会改变 Viewer 首五项、UNKNOWN/END、网格顺序、旧请求覆盖、删除路径或保护媒体权限语义。Phase 1.5 设备症状依旧未实机验收。

## 2. 热点表（F=代码/日志事实，R=待运行验证的风险，U=未知）

| 热点 / 位置 | 触发条件与证据 | 已有观测 / 可验证性 | 成本、风险与本轮决定 |
|---|---|---|---|
| Profile 文件元数据：`PhotoProfileMediaLoader.loadStaged()` → `MediaFetcher.queryProfileIndexedMedia()` / `scanProfileFileSupplements()` → `getMediaInFolder()` → `FolderFileAccess.enumerate()` / `metadata()` | F：先查 MediaStore/Room候选，再枚举每个选定目录；支持 `.nomedia`、未索引项及显式文件。物理文件逐项做存在、regular、readable、size、mtime；SAF 对每项查询 provider 元数据并打开 descriptor。 | 旧日志：5,285候选的metadataRead 6,720ms、enumeration 808ms、MediaStore约173ms、worker 8,812ms；另一次metadata 5,685ms。来源是Phase 1.5修复前Pixel日志，不代表当前代码或性能。当前诊断只记录累计metadata及枚举，未给每条路径归属。 | metadata确为旧样本的最大已计时项。F：枚举只得到直接子项 `File`，不含可靠size/mtime/readability。跳过完整验证会允许删除、替换、撤权或不可读项错误入集。本轮不跳验证、不建立永久缓存。未找到可安全省略的重复 `FolderFileAccess.metadata()` 调用。 |
| 同请求多阶段访问验证：`PhotoProfileMediaLoader.result()` / `PhotoAccessValidationBatch.validate()` | F：cache preview、indexed预览、filesystem新增批次、最终COMPLETE会在各自发布时间重新验证候选；FINAL重验用于阻止期间权限/可见性变化。 | 代码可枚举调用点；无当前设备逐路径 syscall Trace。 | 这些是不同发布时间的授权/可见性证明，并非同一 `metadata()` 字段重复读取。跨阶段共用将扩大受保护数据暴露窗口，本轮不复用。 |
| Profile `.nomedia` / 未索引补扫 | F：目录文件系统枚举仍运行；显式配置目录扫描不依赖MediaStore包含该文件。`scanProfileFileSupplements`逐文件枚举、类型筛选、校验。 | 有既有 `.nomedia` 自动化测试；新优化未触碰此调用链。设备新媒体和真实SAF仍待验。 | 不能用MediaStore集合或目录mtime替代补扫；本轮不更改。 |
| `fileLoadingPriority` | F：SPEED不额外执行 `checkFileExistence`；COMPROMISE额外算文件大小；VALIDITY额外检查存在/类型。物理文件路径始终进入 `FolderFileAccess.metadata()`；配置补扫显式请求日期/大小信息。非物理/索引/OTG路径走不同分支。 | 静态分支已核对；无同设备三种设置的读取计数。 | 不能宣称不同优先级导致相同读取量；对物理读权限不能忽略。本轮不改。 |
| 普通文件夹排序：`MediaActivity.showSortingDialog()` | F：排序设置改变时清除Adapter、令 `mLoadedInitialPhotos=false` 并调用`getMedia()`；对已完成的普通实体文件夹仍触发新的cache读取和`GetMediaAsynctask`完整扫描。 | 旧日志无排序点击全链路基线；代码直接证明必经重载。新 `MediaSortReusePolicy` 可单测“只有当前COMPLETE且授权仍有效才复用”。 | 排序本身只依赖`Medium`已有name/path/size/modified/taken；本轮在合格情况复用列表，后台重排，保留Adapter和搜索。partial/扫描中/受保护聚合/收藏/主页/回收站继续旧加载路径。 |
| 主页目录排序：`MainActivity.showSortingDialog()` → `getDirectories()` / `gotDirectories()` | F：日期、拍摄日期、大小排序会重扫；名字/路径可直接`refreshDirectoryPresentation()`。大小和日期字段由扫描重新计算可能有必要，不能以旧目录缓存保证准确。 | `setupAdapter()`做snapshot复制、distinct、排序、可见集合筛选和搜索；其调用带`runOnUiThread`。目录量与每段耗时当前没有可比设备采样。 | 主线程列表准备是R级风险；无证据表明是显著热点，本轮不搬迁线程/重写目录presentation，避免引入跳位和旧排序覆盖。 |
| 排序CPU：`MediaFetcher.sortMedia()` | F：name/path comparator每次比较执行规范化/小写，natural排序时构造比较器；Phase1.5补有path平局次序，random独立shuffle。 | 修复前旧Profile日志 sort=99ms（5,285项）与81ms（5,285项）；不是修复后设备测量。 | 相比metadata数秒，现有证据不支持修改排序键预计算。保留所有比较语义；待设备重测发现CPU占比显著时另开任务。 |
| 扫描发布与Adapter | F：Phase1.5已有 `ProgressivePublicationBudget`、`MediaAdapter` stable ID/异步Diff/滑动延期。 | 旧日志40 submit、10 commit、14 dropped是跨请求/旧版本总数，不可用于当前版；新请求计数仍待设备。 | 不重复实现预算、不关闭Diff。本轮保留Adapter，排序操作减少一次重建/重扫；无当前bind/Diff实测。 |
| Room查询/写入 | F：`MediumDao`查询包括`parent_path IN`、`full_path IN`、`deleted_ts=0`、`deleted_ts!=0`及favorites join；实体只声明`media.full_path`唯一索引、`directories.path`唯一索引。Room版本11，Phase2A无Schema变化。 | 当前诊断字段可汇总部分调用方包裹的read/write次数、行数和时间，不能保证覆盖全部DAO调用；没有本设备DB文件/SQLite query plan，也没有逐语句trace。 | 查询调用数/累计耗时、行数分布及`EXPLAIN QUERY PLAN`未收集，标记U。无依据新增索引/Migration；不声称已验证SQL瓶颈。 |

## 3. 元数据问题逐项结论

1. MediaStore的`Medium`携带size、modified、taken、type等字段；它不证明路径当前存在或可读。物理补扫仍重新读文件状态/size/mtime，安全上不能将索引记录当作实体证明。
2. `FolderFileAccess.enumerate()`目前只返回直接子项列表与枚举方法/权限当前性，不带metadata。SAF enumerate查询document id/display name，之后metadata另行provider query并开文件descriptor；是provider两阶段开销，不能伪称等同File API。
3. 当前目录内每个已支持且通过类型/筛选的物理子项至多调用一次 `FolderFileAccess.metadata(file)`；异常记PARTIAL，不提交COMPLETE。跨preview、filesystem batch和COMPLETE存在多次访问验证，原因是发布时权限/隐藏/.nomedia/存在状态可变化。未观察到同一physical scan内重复执行同一个`metadata()`，无足够安全证据把这些跨阶段校验合并。
4. 某路径删除、移动或替换时，新扫描每次新枚举，不以旧MediaStore行建立成员；旧文件被覆盖且mtime、size保持不变的限制仍与既有缓存签名限制相同。本轮无跨请求元数据缓存。
5. Permission变化由当前access/session/revision闸门及`FolderEnumeration.current`检查；文件metadata失败使观察状态PARTIAL，不能把失败目录当空或COMPLETE。

## 4. F / R / U 与准入

- **F**：已完成普通文件夹的排序回调确实清空Adapter并触发重新加载；当前`Medium`有各排序所需字段；Profile排序已在后台处理候选排序；Phase1.5保护机制仍在。
- **R**：`MainActivity.setupAdapter()`的去重、排序、可见筛选在主线程；SAF存在enumerate与metadata两次provider交互；跨阶段验证可能有重复系统访问。其实际用户耗时未测。
- **U**：当前版本元数据调用的逐文件归属、不同权限模式下的系统调用数；100/5000/10000+数据集的首显/COMPLETE时间；Adapter/Diff/bind计数；Room逐查询计划与时延；任何修复后Pixel 8 Pro性能对照。

仅实施有代码证据支持的普通文件夹本地排序路径。其余按阶段门槛保留，不把修复前日志当新版本成绩。
