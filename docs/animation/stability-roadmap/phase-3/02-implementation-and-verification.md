# 阶段 3 · 实现与验收记录

基线为 `phase-2-complete`（`495838dd7`）。本阶段的几何路径用于已有 Grid 共享资格的普通 JPEG/PNG。其他渲染入口沿用当前路径。JPEG/PNG 的几何、文件版本或平台捕获无法确认时使用 Viewer 现有淡入淡出。

## 调用链

1. `Context.loadImageBase` 为目标网格的 Glide 请求记录实际请求尺寸、已交付 bitmap 尺寸、预裁切和圆角。只有相同 request token 和实际 drawable 才能经 `MediaAdapter` 发布此信息。已有缩略图变换与缓存键继续使用。
2. `MediaActivity.launchPhotoViewerWithSharedElement` 捕获缩略图本地矩阵及其屏幕投影，保留可见裁口和 Insets。平台映射与 snapshot 捕获重新比对 Holder、drawable、loaded key、Adapter 版本和屏幕端点。若渐进发布仅推进版本，已绑定的 Holder、drawable、loaded key、文件版本与端点仍逐项吻合，捕获采用重验后的新版本。入场 Session 仅保存几何与版本数据。
3. `PhotoFragment` 在原有后台文件读取路径补充 bounds-only 尺寸。Viewer 首屏可以使用缺少大小与修改时间的临时 Medium。若文件大小、修改时间和失效代际经核验与 Grid 一致，则该次 Glide 请求使用经过核验的 Grid 版本。元数据与几何不一致时降级。
4. `StaticPhotoEntryGeometry` 计算方向校正后的完整照片投影，并将实际裁口与圆角作为独立的遮罩。阶段 3 入场专用 Transition 使用相同平台时钟驱动现有代理层。代理共享 Glide 位图资源，并以当前代理坐标绘制。
5. `ViewPagerActivity` 在 `onSharedElementStart` 核对平台应用的源边界和当前目标，确认后才遮住 Glide。动画终点核对同一请求、真实 renderer 与裁口后，通过 Session 租约恢复 Glide 并移除代理。取消和降级复用原有清理路径。

## 可复验入口

- `StaticPhotoEntryGeometryTest` 覆盖方向、采样裁切、可见裁口、文件版本和 Session token。
- `MediaSharedSurfaceTest.staticEntryDrawsRealGlideCropAndThenTheFullRendererPixels` 用 Glide 的真实 CenterCrop 计算生成有颜色标记的对照，并检查绘制起点、半可见遮罩、终点与 renderer 还原。
- `PhotoViewerInteractiveGestureTest.staticPhotoEntrySharesAllFourAspectsAndBothThumbnailModes` 走真实 Activity 转场。方、横、竖、长 PNG 和镜像旋转 JPEG，分别运行裁切与圆角开关的四个组合。每组要求走共享、完成 Glide 接手并归还输入锁。
- 使用现有下拖、视频回程、快速往返、收藏和自定义入口用例检查阶段 2 的行为。

记录里的 draw 和帧提交用于验证应用内几何和所有权。Pixel 8 Pro 的实际屏幕呈现须使用同一 Release APK 的录屏单独验收。

## 执行结果

- JDK 21 全量 JVM 回归通过，1,261 项，0 失败，0 跳过。最后的版本重验变更用定向几何、代理像素与网格租约测试复核。
- API 29 静态入场矩阵通过。API 37 静态入场矩阵、收藏返回、自定义入口返回和连续 50 次往返通过。50 次往返包含 46 次共享完成及 4 次降级，均完成 Session 清理。
- 高负载同时运行多项构建与模拟器时，API 29 录到 renderer 超过现有准备时限和入场采样不足，设备定向复验单独运行。阶段 3 沿用原准备时限。
