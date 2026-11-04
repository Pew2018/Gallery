# 10 — 第三阶段：显示提交与滚动锚点稳定性

日期：2026-11-04（Asia/Taipei）
仓库：Pew2018/Gallery
唯一工作分支：`fix/photo-viewer-five-item-boundary`
开始前远程 HEAD：`c9e2c693ddec0c9d430c7607d67c9f26345bd3c3`
审计范围：MediaActivity / MainActivity、媒体与目录 Adapter、显示内容比较、滚动锚点；不改扫描器、排序器、Viewer、数据库、备份协议或权限模型。

## 1. 目标和验收编号

本阶段针对 A03、A04、A06、A07、A08，保持现有扫描器、DiffUtil Adapter、稳定 ID 和 `LayoutManager`；减少可由代码证明的重复 Diff/通知，阻止过期滚动恢复覆盖用户新手势。完成自动化测试和 ARM64 Actions 后仍需设备验收，不把 CI 成功等同于视觉问题已解决。

## 2. 静态审计证据与收敛结论

### Adapter 创建、清空、布局与动画

- `MediaActivity.setupAdapter()` 仅在 `mediaGrid.adapter == null` 时构造 `MediaAdapter`；否则复用实例并调用 `updateMedia()`。构造处的 `scheduleLayoutAnimation()` 只在 Adapter 创建时、列表视图且系统动画启用时触发（`MediaActivity.kt` 的 `setupAdapter()`）。普通有效范围刷新不主动设空。
- `MainActivity.setupAdapter()` 同样复用 `DirectoryAdapter`；构造分支才设置适配器及安排列表初始布局动画。显式 `forceRecreate` 只由恢复时文件夹样式设置变化调用。普通目录加载进度与最终结果都进入复用 Adapter。
- `adapter = null` 调用按语义分为：首次/无效适配器构建；切换视图、排序、分组、搜索筛选或滚动方向；导航/照片配置来源或浏览范围变化；收藏/受保护目录授权撤销、文件夹访问失效和可信空结果清理；Activity/View 状态恢复；以及错误处理。除范围内普通刷新和相同内容的完整结果外，这些清理保留。权限收窄前清除旧行是必要安全行为。
- 两个 `setupLayoutManager()` 均修改 Activity 已创建的 `MyGridLayoutManager` 的方向/span count/尺寸参数，没有在刷新中反复创建新 LayoutManager 的路径。
- `MediaAdapter` 已将 RecyclerView `itemAnimator` 置空。因此本阶段没有通过再次关闭 ItemAnimator 处理问题；也没有静态证据把用户报告的扑克牌动画直接归因于 RecyclerView ItemAnimator。

### 照片配置保留旧网格与安全边界

`getPhotoProfileMedia()` 只有在非 `SCOPE_CHANGED`、当前 `photoDisplayKey` 与已加载 key 相等、`PhotoContentRevision` 相等、Adapter 存在且媒体非空时才保留网格。刷新请求仍取消旧 AsyncTask、校验请求 ID / load context / stage 顺序；开始时清除 Viewer snapshot/catalog 的可消费状态。配置 ID、来源或授权能力 key 不同，或内容 revision 改变，走 `clearPhotoDisplayState()` 并移除旧 Adapter。访问失效的物理文件夹在正式发布前检查 `accessCurrent()`，失败会清行；收藏会话失效也保留撤销及清理路径。

`PhotoContentRevision` 变化并不被当作无关数据库字段：即使实际可见内容可能相同，也可能代表收藏关系、来源或备份恢复后的内容更新，因此继续严格清网格并重新验证。没有放宽 `retainGrid`。扫描失败而原 scope 有效时保留已授权旧列表并关闭 loading、提示失败；被替换请求不会接收旧目录。未修改 Loader、Viewer 和权限代码。

### 内容相等性、发布路径与缩略图

- `MediaAdapter.updateMedia()` 之前只有 Kotlin 列表结构完全相等才跳过 `DiffUtil`；DiffUtil 的 `MediaUpdatePolicy.sameContent()` 另外忽略 Room `id`、`gridPosition`、`mediaStoreId`。同路径同可见内容但这些内部字段变化时仍会排队做完整 Diff，尽管没有可见行更新。
- `DirectoryPresentation.sameContents()` 之前使用完整 `Directory` data class equality，包含 Adapter 稳定 ID 不依赖的 Room `id`；目录 Diff 内容判断也比较完整对象。路径是目录项身份，数据库行 ID 不呈现。
- `MediaActivity.publishMedia()` 无论内容是否变化都会替换 `mMedia`、增加照片搜索 generation、保存滚动锚点、刷新快捷菜单并调用 `setupAdapter()`。缓存预览变 COMPLETE 即使是相同有序列表也会走这些入口。COMPLETE 还承担刷新结束、扫描完整性、照片状态、Viewer/收藏目录交接和媒体缓存写回，不能丢弃。
- `ThumbnailSection` 比较仍使用自身值相等；列表比较按行序逐项比较媒体路径和现有 `sameContent()` 字段，因此顺序改变、Section 改变、新增/删除、文件元数据、收藏标记、缩略图签名字段变化都继续触发更新。视频时长仍有 payload 更新。`setupThumbnail()` 的请求键含路径、`Medium.getKey()`（路径/修改时间）、类型、裁切、圆角、GIF、滚动方向及旋转状态；旧请求失败回调需匹配当前 request key 才能设置错误图。ViewHolder 回收取消 Picasso/Glide 请求。

### 目录排序与滚动锚点

- 普通主页目录刷新从 `getDirectories()` / 分类目录扫描进入 `MainActivity.setupAdapter()`；排序设置变化允许按新值真实重排。渐进扫描的 `preserveOrder` 路径通过 `DirectoryPresentation.retainOrder()` 保留存活卡片原顺序并把新增卡片追加；最终排序及新增/删除仍提交。`DirectoryMetadataRepository` 的 COMPLETE、scope 和授权门不变。
- 媒体 Adapter 只在 Diff 计算完成且滚动停止后提交；目录 Adapter 在拖动、选中或滚动中延期并保留最新结果。两个 Adapter 的提交校验都检查当前 Adapter 和 request/generation，媒体列表 Diff 在提交前会重新检查滚动状态。
- `FavoritesScrollRestorer.restore()` 以前会在下一次 pre-draw 前发 `scrollToPositionWithOffset()`，但恢复期间新手势不会清掉 pending anchor；收藏返回 Activity 的 `isCurrent` 只检查请求、会话和生命周期，没有检查 RecyclerView 是否正滚动。媒体与目录 Adapter 调用方另有 idle 检查，但不能覆盖收藏返回这一调用路径。
- 锚点包含首个可见媒体身份、媒体序号、轴向像素偏移和方向。保留现有 identity + offset 算法；只让用户新拖动使旧 anchor 失效，不在拖动时尝试恢复旧位置。

## 3. 已确认根因与排除项

**已确认的代码问题：** (1) MediaAdapter 和 DirectoryAdapter 对技术 ID 变动存在多余完整 Diff 工作；(2) `publishMedia()` 对照片配置相同快照仍重复触发搜索 generation、锚点捕获和菜单重建；(3) 滚动恢复 callback 没有被新的用户拖动可靠撤销。

**已排除的怀疑：** 不需要重建 Adapter；未发现普通刷新路径持续重新创建 LayoutManager；列表初始布局动画只在构造适配器时安排；RecyclerView ItemAnimator 已关闭；`PhotoContentRevision` 和 scope/access 不能安全放宽；缩略图旧请求覆盖新条目的风险已有请求键守卫。没有证据需要新增刷新框架或关闭其它动画。

**静态分析不能证明：** 真机实际 Diff/绑定/图片请求数量、刷新期间 top item 的像素漂移、扑克牌式动画是否仍能复现、fast scroller 实际同步性、性能 p50/p95，以及 Android 各权限模式的实际画面。

## 4. 实际修改文件

| 文件 | 修改 |
|---|---|
| `app/src/main/kotlin/org/fossify/gallery/helpers/MediaUpdatePolicy.kt` | 新增按顺序比较媒体/Section 的可见内容策略；媒体技术字段仍沿用现有 `sameContent()` 定义。 |
| `app/src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt` | 同可见内容时直接替换 Adapter 内部媒体快照并执行 commit callback，不排队 Diff、不发送 RecyclerView 更新通知；保留 Room/MediaStore ID 等最新值供后续操作。真实内容或顺序变化沿用现有 DiffUtil。 |
| `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt` | `publishMedia()` 仍更新加载/扫描状态、`mMedia`、COMPLETE、Viewer/收藏交接和必要数据库写回；仅当有序可见内容变化时增加照片搜索 generation、重记照片锚点及刷新依赖媒体内容的快捷菜单。Adapter commit callback 仍执行，保证完全相同的 COMPLETE 也结束刷新。 |
| `app/src/main/kotlin/org/fossify/gallery/helpers/DirectoryPresentation.kt` | 目录相等比较忽略 Room `id`，仍比较路径以外全部模型字段与 folder status。 |
| `app/src/main/kotlin/org/fossify/gallery/adapters/DirectoryAdapter.kt` | DiffUtil 内容比较复用目录内容策略；新增/删除、移动、封面/数量/状态变化仍会产生真实更新。 |
| `app/src/main/kotlin/org/fossify/gallery/helpers/FavoritesScrollRestorer.kt` | 在等待 anchor 或执行恢复期间监听 RecyclerView 拖动；新拖动清除旧 anchor。恢复在滚动非 idle 时被调用则不发程序化定位。 |
| `app/src/test/kotlin/org/fossify/gallery/helpers/MediaAdapterStabilityTest.kt` | 新增数据库 ID、MediaStore ID、grid position 变化不入队 Diff/不通知行；元数据与顺序改变仍入队 Diff。 |
| `app/src/test/kotlin/org/fossify/gallery/helpers/DirectoryAdapterRefreshTest.kt` | 新增目录 ID 变化无操作观察、重复目录快照不排队 Diff；封面及计数改变仍排队并提交。 |
| `app/src/test/kotlin/org/fossify/gallery/helpers/FavoritesScrollRestorerTest.kt` | 新增恢复排队后用户拖动取消旧 anchor、滚动中刷新不捕获旧 anchor 两项用例。 |

## 5. 自动化验证与诊断

自动化通过 Robolectric `ManualExecutor` 观察 Diff 队列是否产生工作，并用 `RecyclerView.AdapterDataObserver` 观察行操作；不会在 Release 增加日志，也不输出路径、文件名或授权信息。Adapter 现有 `unchangedFullBindKeepsDrawableAndRequestToken()`、旧请求失效及滚动延期用例继续覆盖缩略图绑定键和请求生命周期。

- 计划定向用例：`MediaAdapterStabilityTest`、`DirectoryAdapterRefreshTest`、`FavoritesScrollRestorerTest`、`GalleryRefreshRegressionTest`。
- 本地 `./gradlew` 未能启动：wrapper 下载 Gradle 9.5.1 时环境返回 `java.net.SocketException: Network is unreachable`；未安装可用全局 Gradle，因此当前没有本地编译或测试结果。
- GitHub Actions Run #464 成功，见下表；完整测试和 ARM64 APK 均对应验证源 SHA `a8c1f45480179b030ccb248ba24ebc2098c98109`。
- 真机无 ADB、模拟器、录屏或 Perfetto 设备可用，运行时测量待验。

### Run #464 结果

| 项目 | 结果 |
|---|---|
| Actions | [#464 / 41755685039](https://github.com/Pew2018/Gallery/actions/runs/41755685039)，成功 |
| 源码 SHA | `a8c1f45480179b030ccb248ba24ebc2098c98109` |
| 完整 FOSS Release 测试 | 796 项；失败 0、错误 0、跳过 0 |
| ARM64 Release | assemble、apksigner、包名和 ABI 检查通过；仅 `arm64-v8a` |
| APK Artifact | `gallery-foss-arm64-v8a-release`，ID `13287977148`，[下载 ZIP](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13287977148/zip)；10,392,208 bytes |
| APK SHA-256 | `e928b193eef55c32bed59b599477c3190f322c66635c50573bd6bec4ba3b2d34` |
| 签名 | 使用临时 CI 签名，非仓库发布密钥；不能证明与现有正式安装版本签名兼容 |
| 测试报告 Artifact | `backup-ui-test-reports`，ID `13287643559`，[下载 ZIP](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13287643559/zip) |
| 传输完整性 | 所有 13 个修改/新增文件 Blob SHA 与本地 Git Blob SHA 一致；提交前后 Git 文件树与本地一致 |

## 6. 安全与兼容检查

未修改 PhotoProfile retain 条件、请求 scope/revision/access 校验、收藏或文件夹授权、`.nomedia` 扫描、Viewer 目录、PhotoSession store、数据库 schema、设置/配置格式、备份协议、签名密钥、底栏/顶栏和主题。无变化比较仍按列表位置逐项比较：不会吞掉插入、删除、排序、Section、可见媒体字段、文件修改版本、收藏状态、目录封面/数量/状态变化。相同内容快照不会改变 Viewer COMPLETE/目录准备和普通媒体缓存写入语义。

## 7. 第四阶段建议

第三阶段代码级问题修正和 CI 验证后，建议先做设备验收而非立即第四轮代码改造。使用目标设备记录 APK SHA、系统版本与授权模式；在照片配置刷新、普通目录增删、长列表拖动/惯性滑动、全屏返回中采集 top path + offset、Adapter observer、缩略图请求计数和录屏/Frame Timeline。只有这些数据仍显示跳动、重绑或闪烁，才针对命中的调用点另开下一阶段。`.nomedia` 新媒体同步、权限撤销和 Viewer 已有行为也需设备复测。
