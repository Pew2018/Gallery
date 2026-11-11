# 模拟器测试记录（当前轮）

## 环境

- AVD：`Pixel_4`，Android 10 / API 29，1080×2280，60 Hz。
- Gradle：使用本机 Android SDK `/Users/patrick/Library/Android/sdk`。
- 模拟器图形：以 `-gpu software -skip-adb-auth` 启动后，Android 10 / API 29 Pixel_4 AVD 的截图与 ADB 恢复正常。软件渲染可用于触摸行为与画面检查，性能数据不能代表设备性能。
- 用户原始录屏元数据已由 ffprobe 检查；未把抽样视频当作设备验收。

## 执行结果

| 检查 | 结果 | 说明 |
|---|---|---|
| `:app:assembleFossDebug` | 通过 | 当前转场代码与 instrumentation 源码编译通过 |
| 全量 `:app:testFossDebugUnitTest` | 1082 项中 1080 项通过，2 项失败 | `DeleteWithRememberDialogRippleTest` 与 `GalleryRootTransitionTest` 的 Robolectric API 36 sandbox 在本机 Java 17 环境报 `UnsupportedOperationException`；本机未安装 Java 21 |
| 聚焦转场 JVM 测试 | 通过 | `PhotoTransitionCompletionGateTest`、`ViewerMotionTest`、`OxygenClassicActivityMotionTest`、`PhotoViewerContinuousSwipeTest`、`MediaTransitionGestureControllerTest` |
| `:app:connectedFossDebugAndroidTest`（第一次） | 2 项均未到达动画断言 | 适配器有 2 项但 RecyclerView 可见子项为 0；当时模拟器输出全黑画面 |
| `PhotoViewerInteractiveGestureTest`（软件图形重跑） | 2/2 通过 | 覆盖比例、拖动跟手、反向拖动恢复、下滑确认退出、返回路径、Android Back 键、工具栏返回、缩放手势仲裁和缺失目标清理；包括新增的松手状态交接断言 |
| `PhotoViewerInteractiveGestureTest`（采样修正前的两次尝试） | 先 1 项通过/1 项失败，后 2 项失败 | Activity monitor 在 postponed enter 开始前就可返回，测试线程轮询会漏掉中间帧。没有放宽检查；改为 Debug build 在 shared-progress 回调中记录最多 64 个实际帧的 progress 与图像边界，测试等待到进度终点后验证 |
| `PhotoViewerInteractiveGestureTest`（采样修正后） | 2/2 通过 | Pixel_4 / API 29；覆盖下滑过程/反向恢复/确认退出/返回路径，以及横图、竖图、方图、长图比例；以真实共享进度回调帧检查图像边界增长 |
| Android Studio 中再次运行 `PhotoViewerInteractiveGestureTest` | 0/2 通过 | 两项均在进入转场断言前失败：测试媒体已进入数据集，但 RecyclerView 可见子项为 0（`visible=[]`）；此次 AVD 截图仍全黑。保留此前软件渲染 AVD 的 2/2 成功记录；该次复跑未能重新确认转场行为 |
| 下滑确认退出独立重复运行 | 10/10 通过 | 每次单独启动 Android instrumentation 测试；完整日志保存在 `/private/tmp/gallery-goal-review/repeats/downward-1.log` … `downward-10.log` |
| 最终 Debug / AndroidTest / ARM64 Release 构建 | 通过 | 分开执行 `:app:assembleFossDebug :app:assembleFossDebugAndroidTest :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`；帧边界记录只在 Debug 构建启用 |
| 连续录屏/逐帧比较 | 证据不足 | `screenrecord` 编码器失败（err=-38）。模拟器内建录制得到 68 秒 VP9/WebM，1080×2280，平均 3 fps；不能用于评估 300 ms 动画曲线或单帧闪烁 |
| `dumpsys gfxinfo` | 仅记录，不作性能验收 | 软件渲染样本 57 帧全部迟帧（p50 46 ms，p90 61 ms，p95 89 ms）；软件渲染与录屏负载不代表应用设备性能 |

仪器测试截图及 gfxinfo 原始文件存于本机临时目录 `/private/tmp/gallery-goal-review/emulator-evidence/`，未提交到仓库。

## Release 构建产物

- 构建命令：`ANDROID_HOME=/Users/patrick/Library/Android/sdk ./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`
- Gradle APK：`app/build/outputs/apk/foss/release/gallery-32-foss-arm64-v8a-release-unsigned.apk`（18 MiB，ARM64，versionCode 32）。
- 为便于本机安装验证，另以本机 Android debug keystore 签名到 `/private/tmp/gallery-goal-review/gallery-32-foss-arm64-release.apk`。APK 签名验证通过（v2、v3）；SHA-256：`5e63603f90b79ccc7dffe550195c1c185bc7bb535d7ad11738c90f291e83d6cd`。该证书是本机测试签名，不是发布密钥。
- 该 APK 尚未在模拟器上安装运行；当前 on-device instrumentation 运行的是 Debug APK。

## 后续动作

1. 取得可用的高帧率连续录屏或改用可输出原始帧序列的 instrumentation 采样，检查松手到缩略图终点的连续轨迹。
2. 系统边缘手势未获可靠自动化验证：将 API 29 AVD 切到 Android gestural overlay 后，`input swipe` 仍未稳定交给 SystemUI；不要把普通触摸注入当作系统边缘返回证据。需用可交互设备或 UI 自动化支持真实系统手势的环境复测。
3. 测试文件夹进入/返回、快速连续进入退出、目标离屏、慢加载、视频、旋转与前后台恢复；API 29 环境未覆盖预测性返回。
4. Java 21 可用时重跑两个 API 36 Robolectric 失败项及全量 JVM 测试。
