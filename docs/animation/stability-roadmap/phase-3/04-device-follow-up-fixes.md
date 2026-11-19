# Pixel 8 Pro 四项问题整改

实现基线为 15aa2ed57。复核材料和原始现象见 03-pixel8pro-evidence-review.md。

## 实现

- 照片回程与视频回程共同使用按当前视口计算的等比裁切。ChangeBounds 改变代理宽高时，每帧从当前视口重新推导图像矩阵，避免用起止矩阵插值造成中途非等比缩放和末段裁口跳变。真实网格加载记录若已预裁切，即使 View 使用 FIT_CENTER，也按该预裁切端点处理。照片回程同步使用已有的顶栏裁切租约。
- publishAdjacent 在首屏 Adapter/Fragment 尚未建立时可发布，后续稳定页面仍受转场发布门控制。相同媒体集合的优化仅在 Adapter 已建立后生效，避免把建立首屏所需的相同快照丢弃。
- 静态照片的来源确认在启动和平台映射/到达时重验。平台 snapshot 是否生成不再是确认的唯一入口。原 Holder、drawable、request/loaded key、文件版本、发布版本和屏幕裁口继续逐项验证。
- 无 renderer 的入场降级保持 ENTRY_FALLBACK，显示加载状态并允许必要首屏发布。当前媒体发出 renderer 信号后，在预绘制中核对当前页面与媒体，再完成淡入和会话。加载失败单独记录 entry_renderer_failed，并归还会话资源。entry_completed 不再在正常加载尚未产生 renderer 时提前记录。
- 原生回归进一步发现：入场期间提前按 Back、未经过 reenter 即收到有效结果时，Grid 的 pendingViewerReturnPath 仍留有启动路径。现在在该结果分支清空它，并增加 resultWithoutReenterClearsTheLaunchPath 回归。

## 回归入口

- PhotoViewerInitializationTest 检查首次相邻窗口发布、相同快照建立首屏、降级等待 renderer、预绘制恢复和加载失败。
- MediaSharedSurfaceTest 使用约 1:30 的图像，检查返回视口变化时两个轴始终等比，并覆盖已预裁切但 View 为 FIT_CENTER 的目标。
- PhotoViewerInteractiveGestureTest 增加全部媒体和半可见首排矩阵。静态矩阵的长图改为 101 × 3031，并在原生回程绘制帧中检查两个轴缩放比例一致。保留原下拖和视频回程测试。

Pixel 8 Pro 同 APK 的屏幕呈现仍需以修复版真机录屏复验。模拟器的几何和会话通过不代表已获得该机的视觉验收。

## 验证记录（2026-11-19）

- 最终完整 JVM 回归：1267 tests，0 failures，0 errors，0 skipped；Debug 主包和 Android 测试包编译通过。
- Android API 29：普通静态入场、ALL_MEDIA、半可见首排三组矩阵均通过。每组覆盖五类图像（方形、横图、竖图、约 1:30 长图、EXIF 转置），以及裁切开关与圆角开关，共 20 个组合；长图原生回程帧的横纵缩放保持一致。
- API 29：下拖取消与提交、切图后返回、入场期间提前 Back 的既有完整交互测试通过；视频回程顶栏测试通过。最后两项重跑结果为 OK (2 tests)，包含已有大量照片的 ALL_MEDIA 定位。
- Android API 37：上述三组静态矩阵和视频回程通过。下拖测试在软件渲染模拟器上未通过：一次释放动画采到的中间帧不足；降低分辨率复测又在连续切图等待 renderer 处超时。未放宽断言，也未将该项计为通过；API 29 同一完整下拖测试已通过。
- 修复未修改共享元素超时预算、动画时长或插值曲线。Pixel 8 Pro 修复版需重点复验原录屏长图回程末段、全部媒体连续重开、半可见首排，以及加载慢时的提示和最终首帧。
