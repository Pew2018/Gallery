# 转场控制权与状态

日期：2026-11-11

## 当前 Viewer 图片进入

Activity shared element 使用一组并行的 Android `Transition`：`ChangeBounds`、`ChangeTransform`、`ChangeImageTransform`，以及 `SharedElementProgress`。当前媒体共享转场和 Viewer scrim 时长为 120 ms；文件夹目标内容淡入为 100 ms。这是基于参考录屏人工时间线的候选值，仍需修改后连续录屏复核。Viewer 先等待所选图像可映射，再开始 postponed enter transition；转场期间横向翻页输入保持锁定。

`ViewPagerActivity.renderPhotoTransitionProgress()` 现在在每次进度回调中同时记录进度与共享图像的屏幕可见边界。Instrumentation 测试在等待进度到达终点后读取这些实际回调帧，用于判断进度单调、图像边界持续增长。这比测试线程轮询窗口状态更接近真实动画帧，也避免 Activity monitor 返回早晚造成的采样竞争。

## Viewer 下滑退出

状态顺序：

1. 手势开始时建立图像代理，并保留 Viewer 实际图像。
2. 拖动过程中只由 `photoDismissProgress()` / `renderInteractivePhotoProgress()` 更新代理位移、缩放、scrim 与 chrome。
3. 取消时由单个 settle animator 从当前进度返回全屏起点，再清理代理并恢复 Viewer 画布。
4. 确认退出时立即停止手势驱动并调用 `finish()`。Android shared-element return 从当前 transform 接管，目标是源缩略图；不再先播放一段到任意手势终点的 settle animator。

这消除了原来两个 animator 先后控制同一图像的代码路径。它仍依赖 Android Window shared-element transition 能从活动 View 的当前属性正确捕获起始值；API 29 真机录屏仍需确认是否有残余跳帧。

## 其他返回入口与回退路径

工具栏与系统返回仍进入 Viewer 的既有 `finish()` / `finishAfterTransition()` 流程；手势退出和普通返回因此汇入同一个源缩略图 return transition。缺失、离屏或超时目标继续使用现有安全 fallback。常规横向翻页没有改动。

本轮未实现 Android 预测性返回进度接入，也未重构主页至文件夹的封面转场。文件夹、慢加载、旋转、前后台切换、视频和 `.nomedia` 情形需要按 `04-emulator-test-matrix.md` 的未覆盖项继续验证。
