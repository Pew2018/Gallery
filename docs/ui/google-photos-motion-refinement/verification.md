# Google Photos 转场重构：本轮代码审计与验证记录

日期：2026-11-11（Asia/Hong_Kong）

## 基线与参考录屏

- 仓库分支：`perf/home-gallery-sorting`
- 本轮开始的 HEAD：`5360fd240`（`Implement interactive photo viewer transitions`）
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
- 模拟器以软件图形渲染运行。`adb shell screenrecord` 返回 `Encoder failed (err=-38)`，因此本轮不能提供连续录屏或可靠的 FrameTimeline/Jank 对比；使用离散截图核对了启动、文件夹稳态、图片 Viewer 稳态和返回稳态。
- 本轮没有可用的 Android 16 / API 36 AVD。没有测试 Android 16 返回手势、预测性返回、系统动画比例变更、视频首帧、收藏/搜索/自定义照片配置、删除/排序并发等完整矩阵。

### Release APK

- 实际 Gradle task：`assembleFossRelease`，参数 `-PABI_SPLITS=true -PABI_TARGET=arm64-v8a`。
- APK：`/private/tmp/gallery-motion-delivery/gallery-32-foss-arm64-release.apk`
- 包名 `org.fossify.gallery`，versionName `1.14.1`，versionCode `32`，目标 SDK 36，APK 仅包含 `arm64-v8a` native libraries。
- 大小约 18 MiB；SHA-256：`18ac2a7b2431e57fe8695046595cbb2e3adc4370b3217da8a159f2ca34af57f8`。
- `apksigner verify` 成功；签名为本机 Android Debug 证书（SHA-256 `a0e27c62fef30cabebf91a31d83d547cfd5757f9458a6114b838c1867ae5cc43`），仅用于本地测试。
- 已在 Pixel_4 API 29 模拟器安装、启动，并实际打开/返回图片。
- 截图证据：`/private/tmp/gallery-motion-delivery/release-launch.png`、`folder-open.png`、`photo-return.png`。打开过程抽帧为 `photo-0.png` 至 `photo-4.png`；未保存成连续视频。

## 未完成项与后续验证

本轮没有改动应用源码，也没有完成全入口实测。因此这份记录不代表 Google Photos 视觉验收通过，转场时长与曲线也尚未以同步录屏做量化校准。完成目标还需要：提供可运行的 API 36 模拟器及 Java 21 环境；对文件夹封面、横竖图、方图、长图、视频和快速返回进行连续录屏；复现并确认参考录屏的亮色闪帧根因；必要时定点修复并复测；补齐相应测试和性能数据。本轮核验记录已单独提交到本地目标分支。
