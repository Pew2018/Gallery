# 07 — 本轮精简重构实施记录

日期：2026-11-04（客户端时区 Asia/Taipei）  
仓库：Pew2018/Gallery  
唯一工作分支：`fix/photo-viewer-five-item-boundary`  
起始远程 HEAD：`27b488a88de9ed105c3ed6fdbe3931249dabbdb9`  
对照 main：`44b31846fcb8b817d27ff5fef05829601260b3f5`  
范围：只精简经当前源码确认的重复排序；不实施大型 Media Engine V2。

## 1. 任务目标和结论摘要

本轮依据 [05](05-minimal-scan-sort-display-refactor.md) 审计最新目标分支并实际实施两处窄范围的 O2 优化：已排序缓存经过 preview 截取和访问校验时保留次序，避免再次排序；公开来源文件系统候选按批校验、按路径合并后，只对将要发布的累计列表排序一次。原来的额外排序都不影响最终发布顺序。

保留每阶段渐进发布、最终完整读取、既有随机排序 rank、访问校验与 Viewer 目录交接。没有改动扫描调度器、目录元数据权威规则、数据库、配置格式、缓存、Adapter、动画、Viewer、权限或 CI。

**本轮确认的业务问题仅为重复排序工作。** 没有当前设备证据证明历史报告的 `.nomedia` 主页数量、长期索引、扑克牌式动画或全屏边界问题仍可复现，也没有声称这些症状因本次修改已修复。

## 2. 分支和差异审计

- 开始前通过 Git 远程核对指定分支 HEAD；与任务给定标识完全相同。没有将分支 reset 到历史 SHA。
- `main` 位于 `44b3184…`，指定 fix 不是从 main 覆盖而来。与 main 的差异包含渐进式 Viewer、部分/分段目录、发布门、授权测试等既有改动；本次未触碰或回退这些成果。
- 开始时工作区干净。本轮仅在指定分支修改代码、一个现有测试文件和 `docs/media-engine-v2/`。
- 仓库没有发现额外 `AGENTS.md`。已完整阅读 `docs/media-engine-v2/README.md` 与 01–06；01–04 作为旧审计资料保留，没有覆盖。

## 3. 当前调用关系与审计问答

行号以下对应本轮代码提交之前的起始源码；代码改动只在 PhotoProfileMediaLoader 约 191 行附近增删注释/参数，测试行号随新增断言漂移。函数链接以起始 SHA 固定：

| 问题 | 当前代码证据与结论 |
|---|---|
| 普通文件夹从进入到加载完成经过什么？ | `MediaActivity.getMedia()` 创建 `mediaLoadRequests` 身份、停止上一 AsyncTask、设置 loading；初次进入先取 Room 缓存并在主线程校验 request，之后 `startAsyncTask()` 创建 `GetMediaAsynctask`。参见 `MediaActivity.kt` L1673–1743、L1759–1844。`GetMediaAsynctask.doInBackground()` 在线程池读取 MediaStore/文件系统、可能回调文件夹 preview，最终在后台提交元数据/缓存并在主线程 publish。参见 `GetMediaAsynctask.kt` L67–170、后段最终回调；具体线程池由其 `executeOnExecutor` 调用处决定，旧文档的简写需要以此为准。 |
| 一次主页刷新启动哪些工作？ | `MainActivity.getDirectories()` 为普通目录刷新建立 epoch 和 terminal gate，通过 `GalleryAlbumScanExecutor` 启动 `gotDirectories()`；先异步读目录缓存，缓存失败时用保留快照继续正式扫描。主页媒体读取与目录元数据提交由扫描路径按目录触发；分类目录通过单独 `CategorizedFolderScanner`，含渐进结果和最终提交，不应与普通主页计为同一任务。见 `MainActivity.kt` L1062–1139、分类路径 L1424–1489。通知触发次数需要设备/埋点观测，本轮无法给出数量。 |
| 普通文件夹下拉会有几种中间/最终结果？ | 首次进入可能有缓存 preview；实际文件读取可通过 `onFolderPreview` 交付 partial；正式扫描完成后进入最终回调。可选视频时长详情单独发回并就地局部更新，不重新发起扫描完成流程。失败/权限失效分别走 FAILED/访问处理。是否某个具体普通目录刷新产生 0/1/多次 preview 由扫描速度与批次条件决定，不能静态固定一个数量。 |
| 配置刷新中 cache、MediaStore、磁盘各做什么？ | `PhotoProfileMediaLoader.loadStaged()` 尝试复用完整缓存；未复用时可读 cache/Room 形成 preview，单次 profile MediaStore 查询获取 indexed rows，再按授权后的 folder/favorite 文件系统直接子项做补扫。见 `PhotoProfileMediaLoader.kt` L93–199、`MediaFetcher.kt` L427–456、L488–521。各阶段结果进行访问校验和路径去重后发布。 |
| 相同媒体是否可能被页面/通知重复读取？ | F：页面各有 request/generation，MediaFetcher 有取消标志；照片配置 Viewer store 提供 active producer 共享。普通媒体扫描与目录元数据/分类扫描是不同 scope，没有全应用级单一读请求表。I：同一底层文件因主页/目录/配置不同 scope 被重复读取是可能的；是否同一次用户动作实际重复需带 requestId 采样。 |
| 哪些请求可取消？ | F：MediaActivity 新请求会停止 `mCurrAsyncTask`，校验 request + generation + path 后才可回调；`GetMediaAsynctask.stopFetching()` 标记 CANCELLED、设置 `MediaFetcher.shouldStop`、取消任务并清 Handler 回调；MediaFetcher 枚举循环检查 stop；PhotoProfile context 在每个发布/阶段检查 scope、revision 和授权。旧结果不能提交的防护很多。尚未真机验证取消竞态全覆盖。 |
| 加载是否有唯一终止？ | F：主页有 `GalleryScanTerminalGate`，拒绝/异常/完成/超时归并到 finish；媒体有请求终态与 watchdog；MediaProfile Loader 通过异常取消并由 AsyncTask 承担终结。多处生命周期仍分属不同实现；当前静态证据不支持“没有终止机制”这一旧假设。所有 loading 路径是否都能闭合仍需测试组 A。 |
| 哪些地方排序、是否重复、随机是否稳定？ | 普通目录 sort 在 `MediaFetcher.sortMedia()`；照片配置在 cache、index、每个渐进累计快照和 COMPLETE 阶段排序。随机配置 `PhotoProfileMediaLoader` 会对路径分配本次 load 固定 rank；Viewer 另有稳定网格顺序交接。确认重复点：cache 全集已排序后 preview 子集再次排序；公开配置每个文件系统 batch 排序后又对累计集合排序。本轮移除了这些不影响输出的额外排序。是否跨刷新会保持随机顺序由 cache/session 路径决定，需要 A04/A05 测试。 |
| 哪些步骤更新 Adapter？ | `MediaActivity.publishMedia()` 将非缓存阶段更新 `mMedia` 后进入 `setupAdapter()`；MediaAdapter 一次只跑一个 DiffUtil，保留最新待提交快照，并在滚动/布局计算时延期。照片配置各 preview/filesystem 阶段可重复提交累计快照；DirectoryAdapter 同样以 DiffUtil 交付目录更新。分类扫描会将进度和最终结果都交给 adapter。 |
| 何时 `adapter = null` / 重建？ | F：MediaActivity 在照片配置/收藏结果可信空集时清 adapter；鉴权撤销或文件夹权限错误时清空以免泄露旧内容；目录/筛选/导航路径切换也存在 teardown。不能只按次数降低这类清空，因为其中有安全语义。配置每阶段并非必然重建；`setupAdapter()` 会复用现有 adapter（需再看对应调用路径）。本轮不改变。 |
| 哪些变化应产生 DiffUtil 操作？ | 媒体身份为 path/stable ID；内容策略由 `MediaUpdatePolicy.sameContent()` 决定，视频时长可用 payload 局部更新；真正新增/删除/顺序变化应产生 insert/remove/move；无变化快照被 `incoming == media` 短路。目录项按 path identity、DirectoryPresentation 内容契约比较。 |
| 无变化缩略图何时会重绑？ | F：MediaAdapter 对同内容 Medium 判定相同可避免 change；详情时长另做 payload。I/U：数据对象变化、项目分组、section、实际排序或被清空重建可能触发绑定；本轮没有 AdapterDataObserver/Rebind 设备 Trace，不能把重绑或闪烁归因到具体一个原因。 |
| 目录卡片与内部列表是否同一完整性语义？ | F：目录元数据提交只接受 `FolderReadStatus.COMPLETE`、当前 scope/version 与授权；Room 更新在 transaction 内再次校验。内部列表可先保留缓存/partial 预览，再提交完整结果。因显示时机不同，两者并非每个阶段都同样完整，但 incomplete 不会写成权威新计数。`.nomedia` 仍走实际枚举。A01 需要设备验证计数同步。 |
| 全屏是否继承网格顺序与当前授权？ | F：Activity 通过快照/目录 ID、邻近 handoff、grid order 和 profile catalog 交接；Viewer 有 generation、publication gate、完整性/边界状态和页面访问复核。部分窗口不等同 END；目录升级保留当前路径。该结论是代码存在这些校验，不等于 Android 实际滑动/撤权已验收。 |
| 必须保留的有效优化有哪些？ | epoch/request generation、terminal gate、取消与有界 executor、MediaStore scoped 查询复用、`.nomedia` 实际文件枚举、缓存 preview、照片配置 producer 复用、稳定随机 rank、stable ID、后台 DiffUtil、滚动延期与锚点、duration payload、目录元数据完整性提交、Viewer 分段目录与权限 revalidation。 |

## 4. 根因、排除项与修改细节

### 已确认问题 F

`PhotoProfileMediaLoader.loadStaged()` 在普通公开 profile 文件系统 batch 回调中：

1. `result(FILESYSTEM, batch)` 执行授权校验和排序；
2. 把已校验 batch 按 `path` 合入包含先前 cache/index 的 `supplements`；
3. 对 `supplements.values` 全量排序；
4. 发布这一累计列表。

批次初次排序结果立即被 LinkedHashMap 以媒体身份收集，后续累计列表按同一个 `scope.sorting` 再次排序。因此第一步的排序对输出没有贡献。`MediaFetcher` 以每 120 项或每 200ms（满足一个阈值）发布 batch，媒体规模增大时重复调用数随批次数上升。

另一个重复点在 cache preview：cache 全集先排序，`PhotoProfileMediaCache.preview()` 通过有序子列表截取保持次序；`PhotoAccessValidationBatch.validate()` 对媒体做授权过滤且保持输入遍历顺序，之后旧实现又排序 preview 子集。该次排序也不改变可见顺序。

### 已排除的扩大方向

- 没有找到足以证明刷新缺乏 request id、取消机制、完整性状态、Adapter diff 或目录计数门控的证据；已有多层实现，因此不新增并行 Manager/状态机。
- 目录卡片计数并非从一个 partial 快照直接提交为完整；Repository 有 COMPLETE + scope + authorization 门控。历史 `.nomedia` 用户问题仍值得复测，但本次不改已存在的读取器或语义。
- 看见多阶段 publish 不足以证明每次都闪烁。Adapter 已折叠待提交快照并在滚动中延期，需用真机帧与 Adapter 事件验证后再改动画或提交频率。
- Viewer 改动范围大且当前 fix 已有窗口、分段、权限策略；没有扫描/排序修改导致 Viewer 的已复现回归，本轮不触碰它。

### 实际修改

| 文件/函数 | 修改前 | 修改后 | 目的与风险控制 |
|---|---|---|---|
| `app/src/main/kotlin/org/fossify/gallery/helpers/PhotoProfileMediaLoader.kt` / `loadStaged()` cache preview 分支 | cache 排序、preview 截取/过滤后再排序 | preview 校验时保留已有顺序 | 不改变锚点窗口或授权过滤后的序列。 |
| 同文件 / `loadStaged()` 文件系统 batch 分支 | 每批先排序 batch，合并后对累计列表再排序 | 普通公开配置 batch 校验后保留输入次序；累计去重后仍按既定设置排序再发布。受保护混合配置分支未改 | 保留每阶段访问校验、排序语义与渐进 UI；路径/随机排序均仍在累计快照处生效。 |
| `app/src/test/kotlin/org/fossify/gallery/helpers/PhotoProfileMediaLoadingTest.kt` / `validatedCachePreviewRetainsItsSortedOrder()` | 缓存 preview 顺序未单独断言 | 新增缓存 fixture 并断言名称排序保持 | 覆盖 cache preview 跳过冗余排序后的顺序契约。 |
| 同文件 / `unindexedNoMediaFolderPublishesValidatedBatchesBeforeComplete()` | 渐进批次顺序未锁定 | 对 profile 显式设置名称排序，并断言每个渐进批次与最终结果均为该排序 | 验证输出顺序且保留 `.nomedia` 多批次覆盖。 |

未加新基础设施；未修改数据库 schema、备份格式、Android 最低 SDK、权限判断和 UI/Viewer。

## 5. 本轮自动化覆盖和状态

扩展已有 Robolectric `PhotoProfileMediaLoadingTest`，未另造测试类。目标分支完整 FOSS Release 单元测试、ARM64 FOSS Release 构建，以及 Actions/APK 对应关系见 [08](08-minimal-refactor-verification.md)：786 项测试、0 failures/errors/skipped，构建与 APK 校验成功。本地 wrapper 因无法下载 Gradle 9.5.1 失败，故本轮没有本地 Kotlin 编译或设备测试。

## 6. 剩余事项

- A01：普通与 `.nomedia` 目录新增/删除后主页计数、内部列表、配置列表真实一致性待真机测试。
- A02：高频切页/取消/异常下每个请求终态与 loading 指示待完整自动化及设备检查。
- A03：后台刷新时锚点、动画、缩略图 rebind 待录屏/Adapter trace；当前优化不宣称解决扑克牌动画。
- A04：跨来源排序、去重、随机顺序跨阶段与 Viewer 顺序待测试。
- A05/A06：普通、收藏、配置超过五张连续双向滑动和撤权行为待测试。
- A07：FOSS 完整测试覆盖旧数据/备份/配置兼容，仍需完整测试结果及手动抽检。
- A08：没有设备基线和后测；只可报告代码已省掉 cache preview 子集的一次排序及每个公开 profile 文件系统 batch 的一次临时排序。无毫秒、百分比或掉帧改善数据。
