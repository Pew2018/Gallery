# Photos 会话：顺序、滚动发布、相册排序与收藏顶栏

> **历史源码/任务资料（非当前实施指令）**：下文提及的旧工作分支、历史“当前进度”“唯一任务”“下一步”，只适用于所写日期和原固定提交。2026-11-05 起，当前工作遵循 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md) 和 [04 — 验收矩阵](04-validation-and-migration-readiness.md)。保留旧源码证据与原始 Actions/限制供追溯；不能据此声称新阶段 0–5 已完成或获得真机性能数据。

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


最终源码复核补足：COMPLETE扩展锚点直接使用当前窗口保存的ordinal，避免LRU miss时每次边界操作从目录头部indexOf；普通部分目录仍保留既有查找与UNKNOWN。Photos锚点在publishMedia实际内容变化时统一捕获一次，部分/最终阶段都保留，不再在onPhotoResult重复遍历；新增自定义顺序及置顶优先的显式回归。与前述测试补修统一进入下一次完整验证。


第二轮 Run [41767514582](https://github.com/Pew2018/Gallery/actions/runs/41767514582)（`b92052f…`）：814项运行、2项失败，均为新增夹具错误：MainActivity收藏标志是计算属性，应设置mGallerySection；网格测试在onCreate前inflate需安装AppCompat ViewFactory。按真实初始化条件修正，不移除断言。大目录顺序、Fragment失效和多来源交接等其余用例已运行，但本轮未生成APK，不能作为最终通过证据。

线程所有权复核：首个MediaAdapter的clone是浅拷贝，网格还会修改Medium.gridPosition。为避免UI与继续扫描的stage共享可变Medium，GetMediaAsynctask在既有worker上对阶段与最终PhotoLoadResult执行snapshotForUi（复制媒体行），UI排队只持独立结果。无新线程池；新增测试双向修改扫描/网格元数据，证明互不污染，scope/revision/access仍为原请求。


解码缓存交接复核确认的遗漏：Medium.getSignature原只含path/mtime/size，显式ViewerContentInvalidationRegistry版本不参与；因此重建Fragment本身不能证明Glide/Picasso不复用旧位图。将既有进程内失效版本接入getSignature（0版本保持原键），PhotoFragment SVG和VideoFragment预览也使用既有getKey；普通图片/Picasso/zoom decoder自动复用同一签名。没有改解码器、永久配置或DB。测试覆盖同路径size/mtime/显式version各自改变缓存键、不影响其他路径；只能证明缓存键与绑定逻辑，实际Bitmap/视频解码仍真机待验，进程内registry原4096容量和生命周期保持。


## 分段扩展方向与网格顺序的最终契约

Run [41768645616](https://github.com/Pew2018/Gallery/actions/runs/41768645616) 验证 `6aa62e3…`：817 tests/0 failures/errors/skipped；ARM64与签名/包名/ABI/上传成功。此结果不代替下一补修源码的验证。

最终交接复核发现确定反例：若旧网格逆序、新COMPLETE分段目录正序，单独将当前驻留窗口按旧网格逆序，Viewer向后滑动却仍要求store按正序向前扩展，可能扩展不到实际下一张。不能仅靠分段store内部ordinal正确就宣称联合通路正确。已复用ViewerGridOrderPolicy增加窗口方向一致性检查：小目录继续点击网格优先；分段窗口若协调后路径序列与权威序列一致，继续继承；若相反或新增项目交错导致不一致，放弃旧顺序凭据，采用已验证权威序列。初次与catalog advance均使用同一判断，在UI有效generation/revision检查后清旧凭据，当前照片仍按path保留。fallback事件含revision，不增加第二套完整目录缓存，不改变授权。

新增20,501项逆序/同序、插入交错与resident对照测试；30,001真实分段/LRU淘汰的每个首中末及双向窗口也验证同一handoff决定和完整路径序列。超过50k/过期/丢失的旧store上限回退继续保留。最后一轮完整CI须覆盖本契约后才最终交付。


## 最终交付与验证（2026-11-04，Asia/Taipei）

本节覆盖前文“提交时待验证”的状态。最终业务源码 `fa944261dccbf1e4ece4d012a344925c04240d3a`，已通过完整测试和ARM64构建；之后仅补验证文档，不修改APK对应的业务源码。最终文档HEAD见本次交付消息/Git历史。

### Git与范围

| 项目 | 结果 |
|---|---|
| 仓库 | https://github.com/Pew2018/Gallery |
| 唯一目标 | `feature/photo-session-seamless-viewer` |
| 开始远程HEAD | `ee6597a16e4a329343e24463dcbb0352f39e9d37` |
| 最终验证源码 | `fa944261dccbf1e4ece4d012a344925c04240d3a` |
| 主要集中提交 | `dc7fafa7723e2da45a3cb2d4abbd9adff974b82e` |
| 测试/交接补修 | `b92052f3b7fa3d440efc7919a4eb573c74572afa` |
| 锚点及UI快照所有权 | `b0119c2c192a2dac5f3504d224248547f7740b51` |
| 显式内容失效进入解码缓存键 | `6aa62e381cf280e2bd78d4c9209c02f2dfa571dd` |
| 分段方向一致性补修 | `fa944261dccbf1e4ece4d012a344925c04240d3a` |
| main最后重新核对 | `44b31846fcb8b817d27ff5fef05829601260b3f5`，与开始一致，本轮未修改/推送 |
| 原fix最后重新核对 | `ee6597a16e4a329343e24463dcbb0352f39e9d37`，与开始一致，本轮未修改/推送 |
| 源码树完整性 | `cfc4f6e7bd26359c01162950a734ea8113cb01b5`，本地write-tree与远端完全一致；所有上传文件逐一Blob SHA一致 |
| 验证源码差异 | 相对开始30文件，15业务/12测试/3文档，701插入61删除；后续只增补验证文档 |

无新分支、PR、main合并、强制更新、签名配置或密钥改动；无Room/schema、用户配置结构、备份协议或无关UI变化。

### 最终CI与APK

| 项目 | 实际结果 |
|---|---|
| Run | [41769940653](https://github.com/Pew2018/Gallery/actions/runs/41769940653)，completed/success |
| 验证源码SHA | `fa944261dccbf1e4ece4d012a344925c04240d3a`，日志Verified source commit一致 |
| 完整FOSS Release单元测试 | **818 tests、0 failures、0 errors、0 skipped**，Verified JUnit totals确认 |
| ARM64 Release | `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --stacktrace`成功 |
| APK校验 | apksigner Verifies，v2=true；包名`org.fossify.gallery`、1.14.1/32；native ABI仅`arm64-v8a` |
| APK Artifact | [gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/41769940653/artifacts/13294711899)，ID **13294711899**，ZIP 10,398,155 bytes |
| APK SHA-256 | `ab7f927c5fa26e9354e2cc4629a5950f31eb9e6bbbbdd98f64c86ef58884a90a`（APK本身，非ZIP摘要） |
| 测试报告 | [backup-ui-test-reports](https://github.com/Pew2018/Gallery/actions/runs/41769940653/artifacts/13294875919)，ID13294875919 |
| 签名 | 既有工作流自动使用`temporary-CI-not-upgrade-compatible`；可安装测试，不保证与已安装版本签名兼容/覆盖安装 |
| 本地测试 | 定向和完整命令均在Gradle9.5.1下载阶段Network is unreachable，测试未启动；未伪称本地通过 |
| diff检查 | `git diff --check`及最终源码与远端tree一致性检查通过 |

历史Run分开记录，不将失败/取消/旧成功版本作为最终成功证据：

| Run | 源码 | 实际状态与原因 |
|---|---|---|
| [41766932465](https://github.com/Pew2018/Gallery/actions/runs/41766932465) | dc7fafa… | 业务Kotlin编译通过，新测试Favorite构造错误使测试源码编译失败；无测试成功/APK |
| [41767514582](https://github.com/Pew2018/Gallery/actions/runs/41767514582) | b92052f… | 814 tests、2 failures，新增测试计算属性反射及AppCompat初始化夹具错误；保留断言修正 |
| [41768280319](https://github.com/Pew2018/Gallery/actions/runs/41768280319) | b0119c2… | 工作流既有cancel-in-progress规则在后续补修推送时取消；不是成功证据 |
| [41768645616](https://github.com/Pew2018/Gallery/actions/runs/41768645616) | 6aa62e3… | 817/0/0/0与ARM64成功；此后补分段方向反例，故不是最终APK |
| [41769940653](https://github.com/Pew2018/Gallery/actions/runs/41769940653) | fa94426… | **最终818/0/0/0及ARM64、签名/包名/ABI、Artifact全部成功** |

### 四项最终结论与性能证据

| 项目 | 已完成的修改及自动化证据 | 结论 / 尚未解决的验收限制 |
|---|---|---|
| 全屏滑动复核、加载网格滚动 | 原首次布局generation和滑动gate保留；真实Pager双向超过5项；上游Activity在全量准备前只保留最新stage，worker建立独立媒体行；生产Activity用例确认COMPLETE替代pending、晚到INDEXED不能覆盖、revision失效不提交；部分和最终应用仍捕获一次必要锚点 | **代码已优化，自动化通过，真机待验**。未发现证据需重写初始化/扫描/Adapter；没有设备FPS或触控结论 |
| 全屏顺序及实际媒体身份 | 点击时已提交grid path顺序；fresh目录过滤成员；成功改名原位替换路径；same-path显式失效触发Fragment及缓存键更新；30张/多来源同名不同path/收藏重叠/阶段竞争/30,001分段LRU双向完整序列测试；分段方向冲突时使用权威序列并记录revision，保留当前path | **代码已修复，自动化通过，真机待验**。测试Page和缓存键用例不能代替真实Bitmap/视频解码。原50k顺序store上限/TTL失效以及不兼容分段顺序明确回退权威排序，不承诺无限保留旧网格序列 |
| 收藏固定文件视图按钮 | 仅MainActivity.refreshMenuItems按收藏隐藏show_all；实际可见性驱动原快捷操作宽度计算；真实MainActivity反复切换ALL/FAVORITES断言，资源/handler/普通主页/MediaActivity.folder_view均保留 | **已修复，自动化验证通过**；主题/窄屏/搜索展开观感仍需设备复核 |
| 索引相册排序稳定性 | 六种主排序升降序、相同值稳定path键、大小写最终键、八次不同输入顺序、真实计数变化必要移动、分组合成元数据/过期sortValue、custom/pinned及随机rank回归通过；原增量与旧排序拒绝/Diff/锚点保留 | **已修复，自动化验证通过**；真实长列表像素锚点、索引过程观感与sort耗时仍待设备数据 |

可报告的自动化行为计数：100个滚动待发布结果只执行最后1次昂贵准备（滚动时0次）；清空后旧结果0次执行。真实Pager相同目录连续8次更新，通知0次、额外Fragment创建0次（原用例继续通过）。这些是受控用例计数，**不是用户设备的前后性能测量**。分段COMPLETE锚点用窗口ordinal定位，合并排序不依赖LRU缓存命中，不逐媒体全目录搜索；未知缺失不作为END。

`album_presentation_prepare`、`album_identical_skip`、`album_diff_commit`、`photo_grid_main_publish`、`photo_grid_publication_deferred/replaced`、既有viewer事件及含revision的fallback可供设备采样。没有主线程时长前后对比、实际扫描/Diff/绑定次数、内存峰值或Frame Timeline；**缺少设备帧率数据**。MainActivity纯目录排序仍保留原执行位置，未凭假设新增异步排序框架。

### A01–A11最终验收矩阵

| ID | 当前结果 |
|---|---|
| A01 | 代码与自动化双向导航通过；真实连续触控/边界手势待验 |
| A02 | 合并过期滚动发布/安全例外/请求失效自动化通过；真实掉帧改善待验 |
| A03 | 已提交grid优先、fresh成员协调、分段方向冲突/超限权威回退通过；真实大库/搜索/文件操作组合待验 |
| A04 | Fragment路径/身份/内容失效及替代选择自动化通过；真实图片和视频解码待验 |
| A05 | 20,501顺序反例及30,001实际分段读写/LRU淘汰/完整path序列/真边界通过；设备IO与手势待验 |
| A06 | 收藏隐藏、普通主页恢复及重复切换通过；实际屏幕观感待验 |
| A07 | 普通排序ties确定，真实metadata变化仍移动，custom/pinned/random保留，自动化通过 |
| A08 | 原渐进扫描保留，真实变化继续显示，Adapter同值跳过/滚动延期/锚点相关回归通过；像素锚点与实际提交频率待验 |
| A09 | 既有授权/撤销/scope/revision/文件访问套件通过；Android系统权限/SAF/受保护媒体真实设备矩阵待验 |
| A10 | 完整818项回归通过，未删旧断言；.nomedia、收藏、Photos、缓存失败、搜索、文件操作和备份原覆盖保留；真实媒体矩阵待验 |
| A11 | 最终源码完整测试、ARM64编译、签名/包名/ABI校验、Artifact上传全部成功 |

本环境无ADB/Android真机或完整模拟器；未测Android8/11/14/16、SAF/所有文件访问、系统撤权后的实际画面、真实照片解码/缩放、视频播放或端到端帧表现。不得将818测试通过解释为以上真机项目全部验收。
