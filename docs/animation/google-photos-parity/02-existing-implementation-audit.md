# 转场实现审计与本轮修复记录

基线：`perf/home-gallery-sorting`，开始时 HEAD `a9f7d70b2eebd2000810d7265e47af4a06dd82c7`。本地与 fetch 到的远程分支 HEAD 一致。预先存在的未跟踪目录 `buildSrc/build/` 已保留。

## 下滑退出调用链

`PhotoFragment` 将下滑进度转给 `ViewPagerActivity.photoDismissProgress()`；`renderInteractivePhotoProgress()` 同时更新图片代理的缩放/位移、scrim 和 Viewer chrome。确认退出时，此前 `photoDismissSettled(true)` 再用独立 `ValueAnimator` 将代理推到手势模型中的 `p=1`。其结束后调用 `finish()`，再由 `ViewPagerActivity.finish()` 启动 Android Activity shared-element return transition（`ChangeBounds`、`ChangeTransform`、`ChangeImageTransform`）。

这个顺序使自定义手势 animator 与系统共享元素 animator 连续拥有同一图片的控制权，中间还人为落到一个与实际缩略图无关的手势终点。它是“先向下运动，再开始缩回缩略图”的直接代码原因。

## 本轮修改

- `ViewPagerActivity.photoDismissSettled(true)` 现在立即结束交互拖动状态并调用 `finish()`，把图片当前实际 transform 交给 shared-element return；不再先运行到任意 `p=1`。
- 取消退出仍由现有 animator 从当前进度回到全屏状态，并走既有画布恢复清理。
- `PhotoViewerInteractiveGestureTest` 增加集成断言：确认退出必须在 `interactivePhotoProgress` 仍处于手势中间值时进入 Activity 返回流程；超时失败信息包含目录、适配器数量、可见 item 与图片 tag，便于诊断素材发布问题。

## 尚待证据

- 修改后的完整视觉轨迹尚未在可正常捕获画面的模拟器上验证，因此当前只能说消除了代码中串行的两个 settle 阶段，不能宣称 Gallery 与 Google Photos 视觉一致。
- 黑/白/灰空帧、Viewer 进入、文件夹封面展开、系统边缘返回和预测性返回仍需设备录屏核对。
