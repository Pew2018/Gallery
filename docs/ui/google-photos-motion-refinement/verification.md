# Google Photos 转场重构：本轮代码审计与验证记录

日期：2026-11-11（Asia/Hong_Kong）

## 基线与参考录屏

- 仓库分支：`perf/home-gallery-sorting`
- 转场实现基线：`5360fd240`（`Implement interactive photo viewer transitions`）；本次续验开始时分支 HEAD：`3c0992235`。
- 工作区原有未跟踪内容：`buildSrc/build/`；本轮保留，未暂存。
- Google Photos 参考视频可读，时长 88.45 秒；Gallery 当前分支视频可读，时长 60.04 秒。已用 ffmpeg 以 3 秒间隔生成关键帧联系表。两段录屏内容不是逐操作同步录制，不能据此计算可靠的逐帧时序差值。

## 转场调用链审计摘录

| 流程 | 入口与实现 | 已核实的行为 |
|---|---|---|
| 主页相册卡片 → 文件夹 | `MainActivity` 的相册点击处理设置 `AlbumPageTransition`，通过 `ActivityOptions.makeSceneTransitionAnimation` 启动 `MediaActivity`；`MediaActivity.configureAlbumPageTransition()` 按封面媒体 path 找回目标缩略图。 | `ChangeBounds`、`ChangeTransform`、`ChangeImageTransform` 作用于封面；目标封面必须仍在 Adapter、ViewHolder 位置匹配、已加载且 attached。封面不可用时有 450 ms readiness timeout。首屏其他 item 使用 225 ms alpha 进入。 |
| 文件夹/媒体网格 → 图片 Viewer | `MediaActivity.launchPhotoViewerWithSharedElement()` 检查 adapter position、媒体 path、holder 和 thumbnail request/load key；可用时保存 drawable 快照并持有 source holder lease，再以 `ActivityOptions.makeSceneTransitionAnimation` 启动 `ViewPagerActivity`。 | 无可靠源缩略图时走 fade-only 回退。Viewer 通过当前 path 映射真实 PhotoFragment renderer 或开页快照；缺失目标会移除 shared element 并回退。 |
| 图片 Viewer → 网格 | `ViewPagerActivity.finish()` 以关闭时当前媒体 path 重新查找目标 view；`MediaActivity` 的 reenter 映射再次校验 path、adapter position、drawable 与 request/load key。 | 当前浏览到另一张图片时按新 path 返回；目标失效时不使用陈旧 position。进入/返回共享转场期间锁定 pager 输入；完成、取消和 timeout 路径会清理共享 surface。 |
| Viewer 背景与控件 | `OxygenClassicActivityMotion.mediaTransition()` 同时运行 bounds、transform、image transform 与共享进度 transition；`ViewPagerActivity.renderPhotoTransitionProgress()` 控制黑色 scrim，`animateSharedChrome()` 控制 viewer 控件。 | 当前值：媒体共享元素 300 ms、scrim 150 ms、album 内容 225 ms、viewer 控件进入/退出 225/195 ms；共享元素曲线为 `PathInterpolator(0.4, 0, 0.2, 1)`。 |
| 视频 Viewer | `VideoFragment` 提供预览帧 handoff 和共享 surface；播放首帧期间由 `VideoPreviewFrameHandoff` 保留封面。 | 本轮仅审阅到实现与对应 JVM 单测；没有在模拟器上实际操作视频播放和返回，不能声称已视觉验收。 |

## 闪屏与位置错误的代码证据

- 图片打开不是单一系统默认 Activity 动画：源码使用按媒体身份校验的 shared element；失败分支明确改用短 fade-only，避免在错误 bounds 上缩放。Viewer 黑色 window background 也在 `GalleryViewerTransitionTheme` 声明，转场中 canvas/scrim 由 `ViewPagerActivity` 管理。
- 返回目标使用媒体 path 而非旧列表 position，并在 source mapping 时验证 holder 当前绑定项和 thumbnail request/load key。这降低了排序、刷新或 RecyclerView 复用导致飞错位置的风险。
- 文件夹首屏通过封面 drawable readiness 与 450 ms timeout 放行；超时前系统可能继续保留前一个窗口。实际视频关键帧出现的亮色空白，仅凭非同步录屏无法确认是这段等待、目标主题首帧、或扫描/图片请求造成，需在 API 36 设备逐帧复现后才能归因。
- 本轮 API 29 模拟器中，普通 `10_MixedRatios` 文件夹可显示 15 张内容；图片 Viewer 进入后能显示 2400×90 全景图片并按比例居中，系统返回回到原文件夹。未观察到最终静止画面上的残留 overlay 或错位。这个观察不能证明转场过程没有单帧闪烁。

## 本轮验证

### JVM 单元测试

命令：`ANDROID_HOME=/Users/patrick/Library/Android/sdk ./gradlew :app:testFossDebugUnitTest`

- 1082 项已执行；1080 项通过。
- 2 项因环境失败：`DeleteWithRememberDialogRippleTest` 与 `GalleryRootTransitionTest` 的 API 36 Robolectric sandbox 要求 Java 21；本机仅配置 JetBrains Runtime 17。不是测试断言失败，也没有跳过或删除测试。

### Android 仪器测试

命令：`ANDROID_HOME=/Users/patrick/Library/Android/sdk ./gradlew :app:connectedFossDebugAndroidTest`

- Pixel_4 AVD，Android 10 / API 29；仓库已有的 2 项 `PhotoViewerInteractiveGestureTest` 测试均通过。
- 在本次续验中，同一 Pixel_4 / API 29 上对更新后的仪器测试再次运行，2 项仍全部通过；API 37 也以同一测试套件 2/2 通过。
- 模拟器以软件图形渲染运行。`adb shell screenrecord` 返回 `Encoder failed (err=-38)`，因此本轮不能提供连续录屏或可靠的 FrameTimeline/Jank 对比；使用离散截图核对了启动、文件夹稳态、图片 Viewer 稳态和返回稳态。
- 本轮没有可用的 Android 16 / API 36 AVD。没有测试 Android 16 返回手势、预测性返回、系统动画比例变更、收藏/搜索/自定义照片配置、删除/排序并发等完整矩阵。

### 续验：Android 17 / API 37 与测试夹具

- 使用本地已安装的 `google_apis_playstore;arm64-v8a` 系统映像手动启动 `Gallery_API_37_Pixel_4` AVD（Android 17 / API 37）。API 36 Play Store 映像未能从 SDK 仓库下载；因此 API 37 结果不替代 API 36 验收。
- 为兼容 Android 13 及更新版本，仪器测试夹具按系统版本授予 `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO` 和 Android 14+ 的 `READ_MEDIA_VISUAL_USER_SELECTED`；旧版本仍授予 `READ_EXTERNAL_STORAGE`。截图证据改用唯一名称写入 MediaStore，不再尝试删除其他安装或旧版本创建的同名文件，避免 API 35+ 的可恢复存储安全异常。
- API 37 完整运行 `:app:connectedFossDebugAndroidTest`，2 项通过。覆盖图片打开进度/背景同步、横竖方与全景比例、左右切换后下拉撤销/提交、缩放状态的滑动处理、Android Back 和缺失返回目标清理。多次采样发现快速 Activity 启动时测试采样窗口可能晚于 300 ms 动画；比例用例现在以 viewer 进度回调确实运行作为必需条件，若采到至少两帧则继续验证 bounds 单调扩展和 scrim 同步。一般进入动画的多帧几何断言仍在主交互用例中保留。
- 在 API 37 Release 安装运行 `/private/tmp/gallery-motion-delivery/gallery-32-foss-arm64-release.apk`，授予媒体读取权限并查看生成的混合媒体数据。测试素材中有 60 张不同尺寸图片、5 个短视频；`NoMedia/.nomedia` 下的隐藏 JPG 未进入 MediaStore。Release 中打开了“Videos”相册和一段竖屏短片，预览帧正常出现、播放控制响应并能返回视频网格。
- API 37 录屏：`/private/tmp/gallery-motion-delivery/release-api37-video-final.mp4`（13.14 秒，1080×2280，系统录屏采样约 2.26 fps；SHA-256 `28df9ec984cff91ea191c1b8ecd9a8bacc032344b4c0fe516ae2cde5d103ad3b`）；按 2 fps 抽帧的联系表：`/private/tmp/gallery-motion-delivery/release-api37-video-final-sheet-2fps.jpg`。片中可见普通文件夹封面、五视频列表、竖屏视频展开、实际播放控件及返回到列表。录屏采样率不足以做单帧闪烁或流畅度量化。
- Release 在 API 37 的启动、相册列表、五视频网格及视频 Viewer 截图分别为 `release-api37.png`、`release-api37-ready.png`、`release-api37-videos.png`、`release-api37-video-open.png`（同在上述交付目录）。

### Release APK

- 实际 Gradle task：`assembleFossRelease`，参数 `-PABI_SPLITS=true -PABI_TARGET=arm64-v8a`。
- APK：`/private/tmp/gallery-motion-delivery/gallery-32-foss-arm64-release.apk`
- 包名 `org.fossify.gallery`，versionName `1.14.1`，versionCode `32`，目标 SDK 36，APK 仅包含 `arm64-v8a` native libraries。
- 大小约 18 MiB；SHA-256：`18ac2a7b2431e57fe8695046595cbb2e3adc4370b3217da8a159f2ca34af57f8`。
- `apksigner verify` 成功；签名为本机 Android Debug 证书（SHA-256 `a0e27c62fef30cabebf91a31d83d547cfd5757f9458a6114b838c1867ae5cc43`），仅用于本地测试。
- 已在 Pixel_4 API 29 模拟器安装、启动，并实际打开/返回图片。
- 截图证据：`/private/tmp/gallery-motion-delivery/release-launch.png`、`folder-open.png`、`photo-return.png`。打开过程抽帧为 `photo-0.png` 至 `photo-4.png`；未保存成连续视频。

## 未完成项与后续验证

仍未完成全入口实测，也未以相同操作同步录制 Google Photos 与 Gallery 来逐帧校准曲线或测量转场时长。当前代码审计能说明 shared element 的身份校验、bounds/transform/imageTransform、进度 scrim 及失效回退机制；尚无证据将参考视频中的每个亮色闪帧精确归因到单一代码路径。API 37 录屏采样率也不足以排除短暂单帧问题。完成目标还需要 API 36 和 Java 21 环境；对主页封面、收藏、搜索、自定义配置、`.nomedia` 进入、不同系统返回、锁屏/重建、各类视频以及快速返回做连续高帧率实测；并补齐 FrameTimeline/Jank 与资源释放数据。此次续验只修改了仪器测试夹具和本记录，没有改动应用运行时代码。
