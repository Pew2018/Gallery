# 媒体页渐进加载（阶段 A）

## 从打开页面到首张缩略图

普通文件夹和「所有图片」在布局就绪后同时启动有界 Room 预览和正式扫描。Room 只取一个视口所需的可读样本，候选查询有上限，样本按当前排序显示。对「所有图片」的 Room 预览逐父目录应用可见性规则，也检查祖先保护、排除目录、点文件和 .nomedia。完整集合及其排序由正式扫描确认。

Android 的 MediaStore 读取和直接文件访问独立。MediaStore 可消费时，从游标先交付一个经过权限、文件身份与过滤校验的样本。全部索引行继续用于文件夹发现和元数据匹配。获得所有文件访问权限时，直接文件枚举仍确认每个纳入目录的实际内容，能发现 MediaStore 缺失的项目。MediaStore 失败后，普通文件夹继续读取磁盘，「所有图片」使用已知目录、公共媒体目录和配置的目录。发现范围不完整时保留 PARTIAL。

直接扫描发布第一个已验证的文件，随后按网格容量补首屏。搜集更多项目时，预览至少间隔 300 ms 且候选数量大致翻倍。由于文件系统枚举和索引样本的来源顺序可能不同，预览只补入新路径，并保留已显示的位置。完整扫描应用最新排序和分组。随机排序保留已经显示的前缀，并打乱剩余项。

## 完成状态与交互

- 缓存、索引和磁盘预览带同一请求 ID。旧请求、迟到的低优先级来源和重复路径不能覆盖新结果。
- 缩略图首批提交后隐藏中央加载。滑动中的 Adapter 更新会等到手势结束。保留相同前缀的快照使用插入通知，大变动沿用现有 Diff 或更新策略和滚动锚点。缩略图请求继续按原请求键复用。
- 搜索针对最新有效快照重新过滤，正式扫描继续运行。FastScroller 等待完整结果落入 Adapter。Viewer 接收已提交的可见邻居，临时边界可以等待正式扫描。
- 文件夹统计、Room 持久化和可选视频时长继续异步处理。成功的完整枚举才允许按现有规则修剪缓存。失败保留已验证的画面，权限状态失效时清除相关结果。

文件夹枚举继续使用现有的直接列表或 SAF 子项游标。仅有磁盘文件的目录，第一批在枚举得到文件名之后、全量元数据读取之前发布。Room 查询按修改日期取得有界样本，按大小、文件名、拍摄日期等排序的全局排名在正式扫描后确认。

## 诊断与复测

在应用内启用现有诊断后，按请求 ID 关联下列事件。常规运行仍默认关闭诊断。

- `media_page_open`、`media_cache_query_start`、`media_cache_query_end`、`media_index_preview_ready`、`media_first_candidate_ready` 与 `media_scan_partial`。
- `media_first_preview_submit`、`media_first_adapter_commit`、`media_first_thumbnail_rendered`、`media_scan_complete` 与 `media_final_commit`。缩略图记录点为真实 Drawable 进入可见 ImageView 后的预绘制。
- `MediaPipeline` 汇总扫描排队、MediaStore 查询、目录枚举、元数据读取和排序。Adapter 继续记录 Diff 等待、耗时、丢弃和提交策略。用 `scan_restart_requested` 与终态事件检查重复扫描与取消。

本地回归使用 `:app:testFossDebugUnitTest`。ARM64 Release 使用 `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`。设备端 `MediaPageStartupTest` 用相同的 5,000 张合成 JPEG 验证成功提交、可见缩略图和最终条数，并输出阶段耗时。

Pixel 8 Pro 的冷启动、热启动和有无缓存对比仍需在真机上测量。

### 交付版本的模拟器抽样

API 37 ARM64 模拟器、FOSS Debug、所有文件访问权限、单个含 5,000 张 96×64 JPEG 的 .nomedia 文件夹。同一进程先打开空缓存，再打开已填充 Room 的文件夹。以下为交付源码的一轮记录，计时起点为该请求的 `media_page_open`。

| 指标 | 空缓存 | 有 Room 缓存 |
| --- | ---: | ---: |
| 首批可用数据 | 743 ms（文件扫描） | 281 ms（Room） |
| 首次 Adapter 提交 | 997 ms | 511 ms |
| 扫描结果就绪 | 3,878 ms | 5,036 ms |
| 最终 Adapter 提交 | 3,955 ms | 5,097 ms |
| 5,000 次文件元数据读取累计 | 2,170 ms | 3,582 ms |
| 全量排序 | 17 ms | 4 ms |

两次打开分别记录到五次和七次增量追加，均使用插入通知，提交记录为 1–20 ms。每次打开只有一次完整扫描，工作排队分别为 1 ms 和 4 ms。测试观察到两个场景均显示实际缩略图，首张绘制的独立计时事件未取得。本地 1,127 个 JVM 测试和 2 个设备测试通过，包含 5,000 张合成照片与 FastScroller 状态切换。没有同环境的旧版对照、所有图片大库绘制计时、过期队列等待统计或 Pixel 8 Pro 真机数据。

## 综合整改复审（2026-11-14）

### 上一轮实际做到和没有证明的内容

- `431648c` 给媒体页加了缓存、MediaStore、磁盘三阶段预览、请求代际和首张可见缩略图诊断；`51792c1` 仅调整 FastScroller 的完整范围处理。静态调用链显示普通目录与 All 已调用渐进页回调。
- 先前 5,000 项设备测试只断言“首个 Adapter 提交早于最终提交”。它没有断言首张真实缩略图在完整扫描完成之前绘制；因此不能排除预览早提交但屏幕仍迟迟无图。现在补上该时序断言。
- All 和普通目录共用的 `GetMediaAsynctask` 先逐行消费 MediaStore 游标，再开始磁盘补齐。MediaStore 行会在游标遍历期间回调，但未索引及 `.nomedia` 媒体只能在后续磁盘阶段出现。此前缓存回调还有 `folderCache.isNotEmpty()` 短路，缓存非空就跳过实时增量；已移除短路并在 PARTIAL 阶段保留缓存，只有完整读取才能确认缓存项缺失。
- `scopedAvailable` 原先把 Android 版本、MediaStore 可用性和“所有文件访问”状态合成一个条件。现在 MediaStore 查询与直接文件访问分开判断，完整文件权限不会关闭索引预览。
- 文件夹进入曾使用封面缩略图共享元素，并通过 `postponeEnterTransition()` 等待目标缩略图，最长等待 1.2 秒。当前已切换回 Oxygen Classic 页面级窗口动画（170ms 进入、140ms 退出），文件夹扫描、网格绑定和封面解码不再参与导航就绪条件。
- `GalleryListDiffExecutor` 原本是固定两线程但使用无界 FIFO 队列。现在改用两线程、最多四个排队任务的有界执行器，与媒体扫描隔离；过期 Diff 仍通过代际检查尽早中止，队列满时保留既有整表恢复路径。

### Viewer 图层交接

Viewer 先显示 Glide 的 FIT_CENTER 图像，再让覆盖其上的 SubsamplingScaleImageView 异步解码；此前该视图在加载时已可见，且图片就绪监听器在 `setImage()` 后才安装。现在先安装监听，保留 Glide 图层，Subsampling 就绪后于共享转场结束再以 90ms 渐显接管。解码失败会隐藏未就绪图层并保留 Glide 回退。

这修复了监听注册竞态并缓和渲染器交接，但没有取得 Pixel 8 Pro 上实际可见矩形、帧时间或 EXIF 方向动画截图证据，因此不宣称 Viewer 几何已通过真机验收。

### 验证边界

本轮完整单元测试共 1,106 项，1,104 项通过；两项失败均是 Robolectric SDK 36 要求 Java 21，而本机只有 Java 17（`DeleteWithRememberDialogRippleTest`、`GalleryRootTransitionTest`）。缓存保留测试及 `RefreshReliabilityTest` 另行通过，共 17 项。Android instrumentation 测试 Kotlin 源码编译通过；设备测试未运行。ARM64 FOSS Release 编译通过，产物 `gallery-32-foss-arm64-v8a-release-unsigned.apk` 为 18 MiB，SHA-256 `85deba072e4d4b24a730dfa1ff77c8338403f9d3ebd90d7d7a5b0653193b06b6`。随后以临时本地测试证书签名并通过 `apksigner verify`，包名为 `org.fossify.gallery`、仅包含 `arm64-v8a`，可安装签名包位于 `/private/tmp/gallery-foss-arm64-v8a-release-test-signed.apk`，SHA-256 `35dc86a419fb2199b12f844dab3ac01cea7477e0b1a688dcac387575d9d48c19`。该测试证书不具备后续升级兼容性；本轮没有设备可用于安装和画面验收。

本机 Android Debug Bridge 无法启动 daemon（系统拒绝监听 socket），Pixel 真机和模拟器本轮不可连接。shell `git fetch` 因运行环境 DNS 无法解析 github.com 失败；GitHub 只读接口确认执行开始时远端 HEAD 与本地相同，远端原始 HEAD 的 ARM64 Release workflow `43457052215` 成功。自动审查拒绝了本轮远端写入，并明确要求不要通过其它方式绕过；因此提交后的新 Actions workflow 尚未运行。待用户在对话中明确授权向已核验的 `Pew2018/Gallery` `perf/home-gallery-sorting` 发布后，再触发 Actions 并验证其签名 APK。

### 本地缩放交接回归补充（2026-11-14）

- 后续复查发现，深度缩放视图就绪后即使用户已经在 Glide 图层放大，也可能以默认适配比例接管。现在只有 Glide 初始缩放已测得且当前缩放仍处于适配容差内，才启动/显示 Subsampling；不可见加载期间继续由 Glide 接收触摸。增加 `SubsamplingHandoffPolicyTest` 覆盖未知初始几何、容差边界、用户已放大和非有限缩放值。
- `:app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.SubsamplingHandoffPolicyTest --tests org.fossify.gallery.helpers.MediaSharedSurfaceTest` 通过，3 项、0 失败；同一 Gradle 调用中的 ARM64 FOSS Release 重新构建成功。该全量本地测试仍受 Java 17 限制，两项 Robolectric SDK 36 测试不能运行通过。
- 修改后的 `:app:compileFossDebugAndroidTestKotlin` 也再次编译成功；这只证明 instrumentation 测试源码和 APK 可编译，不代表设备端用例已执行。
- 复跑 19 个 A/B/C/D 相关 JVM 测试类共 111 项，0 失败；另将渐进预览的候选节奏和不完整缓存增量合并覆盖扩展到 100、1,000、5,000、10,000 条合成记录，`MediaPagePreviewTest` 5 项通过。它验证策略与身份去重，不代表这些规模下的真实缩略图首绘耗时。
- `HomeSortingComparisonBenchmarkTest` 在 Robolectric 上使用 3 个目录、合计 360 条合成媒体记录测得 `firstUsableMs=270.1`、`fullStatsMs=390.8`；这只是本地主机上的对话框/目录统计观测，没有旧版对照，也不是 Pixel 8 Pro 的首屏或帧率结果。
- 当前未签名 ARM64 APK：`app/build/outputs/apk/foss/release/gallery-32-foss-arm64-v8a-release-unsigned.apk`，SHA-256 `77238e03a21d2e16b6456ddba91b8e318ca094fff0cca2590e06af2ad310e6fc`。另用一次性本地测试证书签名并通过 `apksigner verify` 的 ARM64 APK：`/private/tmp/gallery-perf-home-gallery-3a277b8db-arm64-test-signed.apk`，SHA-256 `c8f30b4dac30bced1ba8f8d97179cacd10ecbea744990bc17a5767efe3cfffd2`；包名 `org.fossify.gallery`、版本 `1.14.1`、ABI 仅 `arm64-v8a`。测试证书不能作为正式签名或用于覆盖正式签名安装，本轮没有把该 APK 安装到设备。
- 复查发现本机唯一 AVD 是 Android 10 / API 29 的 Pixel 4，模拟器二进制报告主机处理器不兼容；因此它不构成可用于本次 Android 16 验收的模拟器。ADB 守护进程也仍因 `could not install *smartsocket* listener: Operation not permitted` 无法启动。此前记录的 API 37 抽样属于先前代码状态，不作为本次提交的视觉验收证据。
- 本地 `perf/home-gallery-sorting` HEAD 为 `3a277b8db2ec383507c4a44501c842b01bb08c5b`，GitHub 只读 ref 查询确认远端仍为 `51792c1942f8ff4171fb2d1066bbcfdc8afd1024`。提交后的 workflow 与 APK artifact 仍待发布授权和远端运行；不得将旧基线的 Actions 成功计作当前代码的验证。

### FastScroller 隐藏祖先的根因及实测验证（2026-11-14）

本轮恢复连接的 Pixel 4 API 29 AVD 上，使用 `MediaPageStartupTest` 对含 5,000 张合成 JPEG 的 `.nomedia` 目录执行空缓存和 Room 热缓存两次启动。此前诊断记录发现首图 Drawable 已就绪，但 `RecyclerViewFastScroller` 在全量目录范围确认前被设为 `GONE`；该控件同时是 RecyclerView 的容器，因此整张网格都无法绘制。这里是首图等待的直接 UI 根因，和候选生成/Adapter 提交是不同阶段。

修复将容器及网格保持 `VISIBLE`，仅在完整范围未就绪时将 FastScroller 的轨道、滑块和弹出标签设为 `INVISIBLE`，并禁用快速滚动；最终范围可用后再显示控件。仪器测试也检查真实 ImageView 已显示、缩略图标签已加载，且首图预绘制事件早于扫描完成。

| 从 `media_page_open` 起算 | 空缓存 | Room 热缓存 |
| --- | ---: | ---: |
| 首个 Adapter 提交 | 240 ms（1 项） | 126 ms（21 项） |
| 首张真实缩略图绘制 | 584 ms | 253 ms |
| 扫描完成 | 894 ms | 620 ms |
| 最终 Adapter 提交 | 906 ms | 637 ms |

设备测试 1 项通过；两种缓存状态都成功提交全部 5,000 项，并确认首图早于扫描完成。完整 FOSS Debug JVM 套件为 **1,131 项、0 失败、0 错误**，仪器测试 Kotlin 编译成功。此设备是 Android 10 / API 29 Pixel 4 AVD，不代表 Google Pixel 8 Pro / Android 16 的测量；仍需在目标真机上复测布局、动画与帧率。

本次代码的 ARM64 FOSS Release 重新构建成功。未签名 APK SHA-256 为 `3a85fecbbcef79376c750e72b6cb938f1789fc7f84106aaa97106b8d81c85a63`；用一次性本地测试证书签名并通过 `apksigner verify` 的可安装 APK 位于 `/private/tmp/gallery-final-home-gallery-arm64-test-signed.apk`，SHA-256 为 `fb44b9e3d68236fdf37784511973ddd01427366dbd9c00853ebe83407387732d`，包名 `org.fossify.gallery`、版本 `1.14.1`、ABI 为 `arm64-v8a`。该证书仅供本地测试，不能覆盖正式签名安装或用于后续升级。

### All 页索引预览及完整仪器套件复测（2026-11-14）

随后恢复了 Pixel 4 API 29 AVD 的连接，并完成 `:app:connectedFossDebugAndroidTest` 全量复测：8 项通过，0 失败、0 跳过。用例覆盖 5,000 张目录的空缓存/热缓存及 All 页索引预览、部分扫描期间网格可见性、Viewer 比例、打开/关闭和拖动手势、收藏照片与文件夹，以及自定义照片资料夹返回当前照片。自定义资料夹用例改成等待 ActivityMonitor 捕获目标 Activity，避免 `startActivitySync` 等待主线程完全闲置造成的套件级超时；索引样本也在 `finally` 中从 MediaStore 删除。

All 页此次从打开到事件记录的计时：MediaStore 索引预览 21 项在 176 ms 就绪，首个 Adapter 提交在 208 ms，首张真实缩略图绘制在 409 ms；全量直接扫描于 1,367 ms 结束，并在 1,381 ms 提交 5,227 项。扫描最终状态为 `PARTIAL`：API 29 测试权限不能覆盖所有目录，测试用例确认的 5,000 项目标目录内容都已纳入结果，但这些数据不能代表 Android 16 的“所有照片”权限场景。缩略图绘制发生在扫描结束之前，索引预览先于完整扫描呈现。

该次动画采样仍来自 API 29 模拟器：59 帧中 59 帧被模拟器的帧耗时统计标为 janky，P50 26 ms、P90 38 ms、P95 46 ms、P99 73 ms。这组数据不能证明 60 FPS，也不作为 Pixel 8 Pro 性能验收；该真机尚未连接。上面早期章节提到的“ADB 不可用”及远端仍停留在基线的记录已被本轮恢复连接、通过 8 项测试和推送的新提交所取代；本轮没有新的目标真机证据。

提交 `226e16b11adc0fd8ab3cd5344e60a404c2024fef` 的 GitHub Actions run `43583900632` 已通过 ARM64 FOSS Release 组装和 APK 签名、包名、ABI 校验。下载的 APK 为 `/private/tmp/gallery-ci-artifact-43583900632/gallery-foss-arm64-v8a-release/gallery-foss-arm64-v8a-release.apk`，SHA-256 `694f5289617eecfdfab79c787fe9651fd78d167e57856ba12f3d54c08c41c004`；`apksigner verify` 通过，包名 `org.fossify.gallery`、版本 `1.14.1`、ABI `arm64-v8a`。该次 CI 没有仓库正式签名密钥，因此使用临时测试证书（SHA-256 指纹 `c446b96da6a66b5861c3d992e922869539dac1e5f77e0a45d03c67c1b6b4b98f`）；该包适合干净安装验证，不能升级已安装的正式签名版本。
