# 09 · Viewer 返回目标锚点、进入首帧与系统 CLOSE 所有权

> 审计基线（开始前重新 `git fetch` 确认）：
> `perf/home-gallery-sorting` 远程 HEAD `90de55d45e6aceacbc5fd88f94be19346d69e506`。
> 本轮开始时该分支没有比它更新的提交。
> 证据：用户提供的 `10d40fd5e762ee634386df16f22a84db.mp4` 与
> `Gallery-Diagnostics-20261123-112745.txt`（1.14.1，arm64 pipeline 的 versionCode 32）。
> 日志没有源码 SHA；本文只把“日志状态 + 当前源码 + 录屏关键帧”能支持的部分写成结论。

## 1. 录屏关键帧确认：进入先黑屏

本轮按 30 fps 抽取了 `00:01.5–00:01.8` 的帧。点击后的一帧仍然完整显示 Grid；
下一帧（约 00:01.65）屏幕几乎全黑，只在中间偏右出现一个很暗的小缩略图；
随后才看到图片展开。

这不是“窗口透明度还没上来”的普通渐暗：

- Viewer 主题 `GalleryViewerTransitionTheme` 是 `windowIsTranslucent=false` 的不透明窗口；
- MediaActivity 在 Viewer 显示后已经被停止/遮挡，系统不会因为它上面的窗口
  `window.attributes.alpha < 1` 就继续合成它；
- 因此 Viewer 的 `window.alpha` 从 0 上升时，透出的是系统黑色底，而不是 Grid；
- 旧实现只把被点缩略图画在黑色 Viewer 画布上，别无他物。

日志 `11:27:00.709 app_entry_preview windowAlpha=0.0` 与
`11:27:00.924 shared_animation_started windowAlpha=1.0` 也说明窗口在约 215 ms
内已经完全不透明；录屏中的黑屏阶段正好落在这个区间。

### 修复

- 在 `MediaActivity` 启动 app-owned Viewer 前，用一次 `decorView.draw(Canvas)`
  冻结当时真实的 Grid 页面（工具条、缩略图、Oxygen 状态栏 underlay），通过
  `MediaTransitionSessionCoordinator.publishEntryBackdrop()` 交给 Viewer。
- Viewer 把它作为 root 的第 0 层（scrim 与预览层之下）；entry 期间窗口保持
  alpha = 1，页面 canvas 透明。第一帧就是用户点击前的 Grid，不再有黑屏。
- 缩略图预览在同位置覆盖 backdrop，之后按同一个 `appEntryAnimator` 时钟展开；
  backdrop 只在 entry 完成/销毁时移除并 `recycle()`。
- 没有 backdrop（截图失败/动画关闭/非 14+）时保留旧的窗口 alpha 路径，不强行
  改变 fallback 语义。

这不是“再加一个动画层”：backdrop 不参与进度，只提供 frame 0 的真实像素；
可见几何仍由唯一的 `appEntryAnimator` 驱动。

## 2. `no_target`：把“父窗口不可见”误判为“没有返回几何”

日志三次拖动提交都出现：

```
grid_interactive_preview detail=no_target
viewer_app_return_target_missing attempts=12
app_return_fallback
```

旧 `readViewerReturnTarget()` 要求 `image.isShown`、`windowVisibility == VISIBLE`、
`transitionAlpha > 0`。Viewer 覆盖 MediaActivity 后这些实时可见性条件必然失败，
即使 Holder、Drawable、媒体身份、Adapter 版本和屏幕矩形都仍然有效。

### 修复

1. 新增纯决策 `ViewerReturnTargetProbe`。失败原因精确区分：
   `NO_POSITION`、`GRID_UNSETTLED`、`HOLDER_UNAVAILABLE`、`PIXELS_UNAVAILABLE`、
   `BOUNDS_UNAVAILABLE`、`PARENT_WINDOW_HIDDEN`、`TARGET_NOT_VISIBLE`、
   `IDENTITY_CHANGED`、`SCOPE_CHANGED`。
   `PARENT_WINDOW_HIDDEN` 在几何、身份、像素、Adapter 版本都有效时是**可接受**的，
   并明确标记 `geometryFromCoveredParent=true`；它不会被记录成“缩略图可见”。
2. 启动 Viewer 时保存 `ViewerReturnAnchor`：媒体身份、scope、Adapter 版本、
   源 `StaticPhotoSource`（屏幕坐标、可见裁切、缩略图裁切/方向、文件版本）、
   源 ImageView/Drawable 弱引用与 loadedKey。
3. 应用内退出先探测实时目标；不合法时，`ViewerCachedReturnAnchorPolicy`
   重新校验 launch、scope、当前页、Adapter 版本、位置/签名、文件版本、窗口宽度、
   Drawable。任何一项变化都拒绝，绝不无条件复用旧坐标。
4. 目标探测只在 operation/reason 变化时写 `viewer_return_target_probe` /
   `viewer_return_target_resolved` / `viewer_return_target_unavailable`，
   不在每帧刷日志。

目标解析优先级：

```
实时可见 Grid 目标
  -> 被覆盖但身份/几何仍有效的实时 Grid 目标
  -> 通过全部不变量校验的 launch anchor
  -> 单次窗口淡出 fallback
```

## 3. 下拖 fallback 的两个视觉退出阶段

旧 `startAppReturnFadeFallback()`：160 ms 内把 surface 缩到 0.8 并 alpha 到 0；
`finishAppReturnFallback()` 再 `super.finish()` +
`overridePendingTransition(0, photo_viewer_fade_out)`，于是图片消失后黑色窗口
还要再淡出一次。

### 修复

- 从手指释放时的 `scaleX/scaleY` 与 `window.alpha` 继承当前帧；
- 只运行一个 160 ms 的**整窗 alpha** 淡出（surface 仅做 6% 的延续缩放，
  surface alpha 保持 1），Grid 在窗口后面逐渐显露；
- 终态只 `super.finish()` + `overridePendingTransition(0, 0)`，不再叠加第二个
  `photo_viewer_fade_out`；
- 旧 generation 的 `onAnimationEnd` 恢复 window alpha 并拒绝 finish，避免
  快速重入后窗口半透明。

目标有效时仍使用 `MeasuredPhotoReturnView`，从当前 surface 矩形连续到实测
Grid 格位；目标无效时才走上述单次淡出。

## 4. Android 14–16 左右预测性返回：A/B/C 对照

### AOSP 事实

- `BackNavigationController` 只有在关闭窗口声明了非零
  `activityCloseExitAnimation` 时，才把 `CustomAnimationInfo` 交给 Shell 的
  `CustomCrossActivityBackAnimation`；否则走 `DefaultCrossActivityBackAnimation`
  （AOSP `libs/WindowManager/Shell/.../back/CustomCrossActivityBackAnimation.kt`）。
- 两个实现都**由系统**缩放/移动整个窗口：
  - Default：`targetClosingRect.scaleCentered(0.9f)`，左边缘额外向右偏移；
    进入页从屏幕左侧 `enteringStartOffset` 开始并同步缩放；
  - Custom：`targetClosingRect.scaleCentered(0.9f)`，按 `swipeEdge` 向相反边缘
    偏移（左/右都有方向差异），进入页保持 full-screen base rect。
- App 的 CLOSE 资源只是叠加在系统 rect/geometry 上的 `Animation`
  （`CustomCrossActivityBackAnimation.getPreCommitEnteringBaseTransformation` /
  `onPostCommitProgress`）。它不能改变 Shell 的方向逻辑，却可以额外叠加
  alpha/translation，造成第二个视觉阶段。

来源：
<https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/libs/WindowManager/Shell/src/com/android/wm/shell/back/CustomCrossActivityBackAnimation.kt>
<https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/libs/WindowManager/Shell/src/com/android/wm/shell/back/DefaultCrossActivityBackAnimation.kt>

### A/B/C

| 方案 | 左右方向 | 真实父页 | 黑色背景 | 额外退出阶段 |
|---|---|---|---|---|
| A 旧 custom CLOSE：enter 0.96→1，exit 1→0 | 系统按 edge 偏移（有） | 是 | 窗口不透明，无系统黑底 | **有**：exit 再淡出一次 |
| B 去掉 CLOSE，走系统 default | 仅左边缘偏移进入页；右边缘进入页仍从左侧进入 | 进入页 0.9 缩放 + 左移，露出 Shell backdrop | 有 | 系统 450 ms 二次收束 |
| C 保留 custom CLOSE，资源改 identity | 系统按 edge 双向偏移（有） | 是，full-screen | 无 | 无 |

最终选择 **C**：保留 `OxygenClassicPageBackWindow`（非零 CLOSE 对，确保选择
custom 分支），但把 enter/exit 动画改成 alpha 1→1、无位移/缩放的 identity。
系统的 `CustomCrossActivityBackAnimation` 是唯一视觉控制者；应用不再提交
`startAppReturnAnimation()`、`startAppReturnFadeFallback()` 或整窗 fade。

稳定 `VIEWING` 状态下 `ViewerBackGesture.isEnabled=false`，边缘手势继续由
Android 16 原生跨 Activity 预测性返回处理；这条所有权没有被改成应用自绘。

## 5. 进入 Preview → Proxy → Renderer 交接

- backdrop 保证 frame 0 是 Grid；
- 预览与真实 `StaticPhotoEntryView` 仍共享同一个 `appEntryAnimator`；
- 新增 `viewer_entry_clock_started` / `viewer_entry_handoff_ready` /
  `viewer_entry_handoff_rejected`；
- 若预览目标与 renderer 目标超出 1 px，**不再**在动画中途移除预览去换一个
  有尺寸差的 proxy；预览保留到同一时钟结束，proxy 在 p=1 接替。没有固定延迟。

## 6. 验证

- `./gradlew :app:compileFossDebugKotlin`：通过。
- 新增测试（实际状态推进，不是源码字符串匹配）：
  - `ViewerReturnTargetProbeTest`：九种拒绝原因逐项断言，覆盖父窗口隐藏但几何有效。
  - `ViewerCachedReturnAnchorTest`：launch/scope/删除/重排/文件版本/窗口/像素失效。
  - `ViewerMediaIdentityReturnTest`：真实临时文件版本变化后锚点失效。
  - `ViewerEntryHandoffTest`：backdrop 为 frame 0、窗口保持不透明、完成后回收。
  - `ViewerEntryClockTest`（原有）：无 backdrop 时窗口 alpha 仍与 entry 进度同钟。
  - `ViewerInteractiveDismissTest`：从 0.5 帧回弹到全屏，surface 不残留缩放。
  - `ViewerSingleExitAnimatorTest`：单次窗口淡出、只一个 terminal、无第二次 pending fade。
  - `ViewerRapidReentryTest`：旧 generation 动画不能结束新访问，窗口 alpha 恢复。
  - `ViewerTransitionCleanupTest`：销毁时 session CLEANED、无残留 lease。
  - `VideoViewerFallbackTest`：视频 fallback 清除 backdrop/lease。
  - `PredictiveBackCloseAnimationTest`：CLOSE 对可加载且 alpha/矩阵为 identity。
  - `OxygenClassicActivityMotionTest`（扩展）：Android 16 Viewer 选择 custom CLOSE，
    且不安装 scene transition。
- 未完成：Pixel 8 Pro 上的逐帧视觉验收、左右边缘实际合成、深浅色主题下的
  状态栏/导航栏过渡。本文不声称这些已经通过。
