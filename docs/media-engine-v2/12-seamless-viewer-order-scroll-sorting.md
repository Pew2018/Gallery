# Photos 会话：顺序、滚动发布、相册排序与收藏顶栏

日期：2026-11-04（Asia/Taipei）。唯一目标分支：`feature/photo-session-seamless-viewer`。
起始远程 HEAD：`ee6597a16e4a329343e24463dcbb0352f39e9d37`。
开始时 main：`44b31846fcb8b817d27ff5fef05829601260b3f5`；原 fix：`ee6597a16e4a329343e24463dcbb0352f39e9d37`。

## 修改前证据收敛

完整阅读 01–11 与 README，并核查最新提交与测试。首次 Viewer `scheduleInitialViewerPublication` 有 publication generation 检查；`gotMedia`/`ViewerSwipePublicationGate` 在 DRAGGING/SETTLING 延迟提交，idle 再应用；`MyPagerAdapter` 已按 path/type 匹配 Fragment。未找到需推翻这些修复的证据。没有真实触控、Bitmap 解码、Frame Timeline，不能宣布真机滑动完全验收。

| 问题 | 已确认源码事实 | 最小范围 / 测试缺口 |
|---|---|---|
| Photos 网格/Viewer 交接 | `MediaActivity.openInViewPager` 仅交接五项邻近窗口及共享 catalog ID。后台阶段可先于 Adapter 已提交阶段，因此两者顺序版本不同 | 复用 path-only `ViewerGridOrderStore`，在点击时取得已提交网格顺序；Viewer 对当前有效数据协调顺序。不复制第二套完整 Medium 目录；超 50k 的已有 store 上限继续回退权威目录并记录事件 |
| COMPLETE 分段扩展 | `requestExpand` 按 `knownIndexOf ?: emptyOrder.indexOfFirst` 排序；LRU miss 会变成 -1。大于 LRU 容量的库确实可淘汰当前窗口的 ordinal | 每次读取初始/重心窗口和扩展批次记录真实 ordinal，只保留窗口 map；合并不查 LRU、不逐图全目录搜索，不把未知当 END |
| Fragment 同路径内容更新 | `getItemPosition` 已有 signature 检查；`updateMedia` 同值列表短路却未比较显式内容失效版本。直接 instantiate 也可复用旧 signature 的 Fragment | Adapter 保存内容签名快照，失效时允许通知；instantiate 再检查签名。保留未知元数据丰富复用；测试只证明绑定/身份，不证明实际解码 |
| 加载时滚动主线程工作 | Adapter 延期 Diff 发生在 `MediaActivity` 已执行全量复制/内容比较/锚点/菜单处理之后 | 在 Activity 入口复用已有 publication gate，滚动只保留最新闭包及 worker-owned stage 快照；新请求、scope/revision/access 失效拒绝旧结果，needsUnlock 不延期；首个内容不延期 |
| 相册排序 | 普通 comparator 对相同主值返回 0；输入次序不同可改变同值目录顺序 | 预计算词法规范路径作稳定升序次级键、原始 path 作最终键，避免 comparator 中 canonical IO。主排序降序规则不变；随机 rank、custom、pinned 分支不变 |
| 收藏固定按钮 | `refreshMenuItems` 只切换 show_all 标题，没有按收藏隐藏；宽度计算已读取可见性 | 仅该函数设置 `isVisible = !mShowingFolderFavorites`，保留资源及 click handler、普通主页按钮与 MediaActivity.folder_view |

掉帧的唯一根因、排序每次实际耗时、Decoder/IO 竞争均未证实。没有据此增加线程池/新刷新框架。`MainActivity.setupAdapter` 纯数据准备仍在原线程；先增加 path-free 时长/数量事件，没有设备测量证据时不扩大为异步目录排序重构。

## 实际修改

- `MediaActivity.getPhotoProfileMedia`/`submitPhotoGridResult`/`applyPhotoGridResult`：在大型处理之前合并滚动发布。收到新阶段即更新 stage 下限，旧 INDEXED 不能替换已排队 COMPLETE。idle 应用时重新验证 request、scope、revision 和生命周期；`clearPhotoDisplayState`/新请求/当前失败清空 pending。删掉每阶段重复的锚点遍历和菜单刷新，保留 publishMedia 的实际内容变化处理。Loader 的校验结果是 worker 构建的独立列表，未使用正在修改的扫描集合；原后台扫描、first preview、COMPLETE 仍执行。
- `MediaActivity.openInViewPager`：点击时用当前 Adapter rows 记录 path-only 顺序。`ViewPagerActivity.authenticateViewerRequest` 消费一次，初始、catalog advance、gotMedia 的后台数据准备都协调已显示顺序；仅 fresh、当前可用媒体参与，不复活删除/不在新目录中的旧路径；新媒体按权威顺序追加。store 超限/过期/丢失回退目录，记录 `viewer_grid_order_authoritative_fallback`。顺序凭据不授予授权，现有邻近五项授权仍执行。
- `PhotoSessionViewerCatalogStore.Catalog` 增加进程内窗口 ordinal map；prepare/catalogAt/requestExpand 读取时填充。COMPLETE 合并只用确定 ordinal，缺失则拒绝发布；裁剪窗口后不能保留被裁掉一端的 END。未改 segment 大小、缓存上限、磁盘格式/预算或授权规则。
- `MyPagerAdapter.updateMedia`/`instantiateItem`：显式内容失效版本参与无变化判断，过期实例不复用；state-saved 时隐藏过期实例并用已有 deferred page 路径恢复。
- `ViewerPositionPolicy.resolve`：列表查找与 map 查找均使用精确 path。删除当前图仍采用旧索引就近替代（末项 clamp），`refreshUI` 从新数据更新位置、标题、Adapter；不按旧整数复用另一图片。
- `Context.getSortedDirectories`：稳定次级比较只用于普通排序。grouped 父目录已由现有 `addParentWithoutMediaFiles` 汇总数量/大小/日期，排序读取实际字段而不是历史 sortValue，无需新聚合器。
- `MainActivity.refreshMenuItems`：仅收藏主页隐藏 show_all；`updateMenuColors` 随后以实际可见性释放固定按钮空间。
- `GalleryPerfTrace`/`MainActivity.setupAdapter`/`DirectoryAdapter.updateDirs`：受控、无路径的 `photo_grid_main_publish`、`photo_grid_publication_deferred/replaced`、`album_presentation_prepare`、`album_identical_skip`、`album_diff_commit`；供设备采样。Diff 架构、稳定 ID、锚点与真实变化提交均保留。

## 自动化与性能验证口径

扩展现有测试，不新建测试框架：

- `PhotoSessionViewerCatalogStoreTest`：30,001 条（超过20k及20,512 LRU），真实 segment 读取/位置淘汰后双向多批扩展；逐个 path、ordinal、pathIndex 与完整预期 slice 对照；首/中/末、真 END、revision 变化与增删重建。原20,501测试继续运行。
- `PhotoProfileMediaLoadingTest`：两个实际临时目录、同名不同 path、虚拟收藏重叠，真实 loader 阶段和点击顺序交接；去重、顺序、选中身份。
- `ViewerGridOrderPolicyTest`：30张指定网格序列，INDEXED/FILESYSTEM/COMPLETE样例逐位置对照，双向超过5项，新增/删除不复活；旧阶段拒绝继续由 store/初始化测试覆盖。
- `PhotoViewerProgressivePagerTest`：原普通/收藏/Photos及真实 Pager/FragmentManager回归保留，增加每一步实际 Fragment arguments path 断言。
- `MyPagerAdapterLifecycleTest`：同路径失效版本不能被列表相等吞掉；删除当前路径后新位置绑定明确替代媒体。已有 size/modified/未知元数据、state saved 用例保留。
- `GalleryRefreshRegressionTest`：六种主排序/升降序、相同值/case-distinct path/八次不同输入顺序、真实计数变化必要移动；grouped 元数据、切换模式及废弃 sortValue。原随机rank/数字名、自定义/置顶、目录滚动/锚点等套件保留。
- `FavoriteFolderProgressiveActivityTest`：真实 MainActivity 收藏/普通主页反复切换菜单状态，保留阻塞扫描时改排序测试。
- `ViewerSwipePublicationGateTest`：100个滚动阶段在昂贵准备前折叠为1个执行，授权清空丢弃 pending。这是可控场景行为计数，不是设备 FPS 数据。
- `ViewerPositionPolicyTest`：大小写不同路径精确匹配。

本地已尝试定向及完整 `./gradlew :app:testFossReleaseUnitTest --stacktrace`，均在 Gradle 9.5.1 下载阶段失败：`java.net.SocketException: Network is unreachable`，测试未启动。`git diff --check` 通过。完整测试、ARM64构建、签名/包名/ABI校验与产物以本轮实际 Actions 为准，提交时待验证。

安全回归包括原 `.nomedia`、多来源/收藏、SAF、所有文件、scope/revision/generation、授权撤销和备份全套。未修改数据库、备份协议、用户配置格式、签名密钥、动画或无关界面；main/原fix无推送。

## 验收矩阵（提交时）

| ID | 代码/自动化范围 | 尚缺证据 |
|---|---|---|
| A01 | 首次布局保护、滑动 gate 原实现保留及 Pager 回归 | 真机连续触控 |
| A02 | 上游折叠滚动发布、保留最后有效阶段与首屏、安全例外 | 缺少设备帧率数据、无前后耗时/Frame Timeline |
| A03 | 点击时网格顺序优先，fresh有效目录协调，超限回退有事件 | 超大实际图库/搜索/文件操作设备矩阵 |
| A04 | path/type身份、内容signature、删除就近替代及标题数据同步 | 真实图片解码/视频播放 |
| A05 | 30,001全序与真实LRU淘汰、双向扩展、增删/revision | 实机超大库IO/边界手势 |
| A06 | 收藏隐藏，普通主页恢复，宽度读取实际可见性 | 主题/搜索展开/窄屏观感 |
| A07 | 六类排序ties确定，主降序保持，分组真实元数据 | 真机排序切换 |
| A08 | 原增量扫描保留，Adapter同值跳过/滚动延期保留 | 真机锚点像素/实际提交次数 |
| A09–10 | 原授权、媒体读取与显示/备份回归套件保留 | 系统权限撤销/SAF真实设备 |
| A11 | 待本轮完整Actions验证 | 实际成功Run及APK |


## 首轮 CI 及最小补修

Run [41766932465](https://github.com/Pew2018/Gallery/actions/runs/41766932465)，源码 `dc7fafa7723e2da45a3cb2d4abbd9adff974b82e`：业务 Kotlin 编译通过；新多来源测试 `Favorite` 构造漏传四字段，测试源码编译失败，单测未启动、无 APK。仅修正该构造，并完善同轮遗漏：分段LRU用例改用3,000附近窗口、跨多个segment后再次淘汰再反向扩展；`PhotoViewerInitializationTest` 直接驱动生产 MediaActivity 的发布入口，确认滚动期间mMedia/Adapter未变化、COMPLETE替代pending、晚到INDEXED拒绝、revision变化后pending不可提交。不会删除旧断言。

复核菜单去重时保留权限摘要变化的必要 `refreshMenuItems`，避免只有 needsUnlock 改变而媒体未变化时菜单不更新；普通同内容阶段仍不重复刷新。其余业务逻辑不变，下一Run重新执行完整测试及ARM64构建。

文件操作复核另补一处明确交接遗漏：成功且通过现有授权检查的重命名/隐藏改名，在会话grid path顺序中原位替换oldPath，避免新path被当作新增项追加末尾；复用ViewerGridOrderPolicy，fresh成员过滤仍保留，测试拒绝未在fresh中的新路径。移动出scope仍由原扫描/授权处理。
