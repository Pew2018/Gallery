# 本轮结论与未完成验收项

日期：2026-11-11

## 已完成修改

- 确认下滑确认退出原先先让交互图片代理 settle 到手势任意终点、再启动 Activity shared return，导致同一图像由两套动画顺序控制。现在确认退出直接从手势实时 transform 调用 `finish()`，由源缩略图 shared return 接管；取消仍回到全屏。
- `PhotoViewerInteractiveGestureTest` 在 Pixel_4 / Android 10 (API 29) 上检查实际拖动代理、方向反转恢复、确认退出交接、缩放比例和返回按钮/工具栏路径。
- Debug 测试构建在 shared-progress 回调帧中记录最多 64 个图像可见矩形和进度值；测试等待动画结束后检查多帧增长、单调进度。Release 不执行这段调试采样。
- 下滑确认退出独立运行 10 次，10/10 通过。完整的两项 instrumentation 类最终运行也通过 2/2。
- 增补参考观察、代码审计、状态设计、模拟器结果与限制说明。

## 验证结果

- Debug、AndroidTest 与 ARM64 Release APK 构建通过。
- 全量 JVM 测试 1082 项中 1080 项通过；`DeleteWithRememberDialogRippleTest` 和 `GalleryRootTransitionTest` 在 Java 17 下创建 Robolectric API 36 sandbox 时以 `UnsupportedOperationException` 失败。当前主机只安装 Java 17，不能完成 Java 21 复测。
- Release 构建原件为未签名 ARM64 APK。为便于本机试装生成的副本使用 Android Debug keystore 签名，v2/v3 验签通过；不能作为发布签名包。
- AVD 是 Pixel_4 / Android 10 / API 29 / 60 Hz，使用软件图形渲染。它适合触摸与状态回归，不适合据此结论生产设备帧性能。

## 不能宣称通过的验收

- 当前没有 Gallery 修改后的可用高帧率连续录屏。`screenrecord` 报 `Encoder failed (err=-38)`，AVD 内建录制约 3 fps。现有 `/private/tmp/gallery-goal-review/gesture-run.webm` 不能验证 300 ms 曲线、闪屏或 25/50/75% 视觉关键帧；不能证明与 Google Photos 视觉等价。
- 下滑手势轨迹的回调边界检查验证了连续 progress，但它不能代替屏幕像素视频分析，尚未证明真实屏幕无一帧闪烁或无微小跳变。
- Gallery 文件夹进入/返回、Android 系统边缘返回、预测性返回、快速重复打开、离屏/慢加载、扫描期间、视频、`.nomedia`、旋转、前后台恢复及不同动画比例/主题尚未按完整矩阵实测。API 29 不支持预测性返回验收；注入 swipe 不能稳定进入 SystemUI 边缘手势处理。
- 模拟器软件渲染 `gfxinfo` 样本全部迟帧，不用于性能结论。需要硬件加速设备或可用的高帧率 AVD 复测。
- 尚未完成同步条件下的 Google Photos/Gallery 曲线、透明度和尺寸中心误差测量。

## 本轮结论

可以确认修复了下滑确认退出中两个动画控制器串行接管同一图片的代码缺陷，并有 10 次模拟器手势回归证据。文件夹动画、边缘系统手势以及修改后视觉轨迹的完整录屏验收仍未完成，因此本轮不标记为整体 Google Photos 动画复刻达标。

测试与产物位置：

- 模拟器重复测试日志：`/private/tmp/gallery-goal-review/repeats/`
- 模拟器低帧率录制：`/private/tmp/gallery-goal-review/gesture-run.webm`
- ARM64 Release 测试签名包：`/private/tmp/gallery-goal-review/gallery-32-foss-arm64-release.apk`
- 详细过程：`01-reference-analysis.md`、`02-existing-implementation-audit.md`、`03-animation-architecture.md`、`04-emulator-test-matrix.md`、`05-frame-comparison.md`
