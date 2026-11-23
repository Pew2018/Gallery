# 08 · Viewer 打开/关闭动画与照片配置页预测性返回背景

> 审计基线：`perf/home-gallery-sorting` 远程 HEAD `de80f574d10848ac33ceb7992aeedce8767f2e64`
> （开始工作前重新 `git fetch` 确认，未重置、未覆盖并发提交）。
> 交付提交：见本轮 `perf/home-gallery-sorting` 的提交（本文与代码同一提交）。
> 设备证据：用户提供的 `Gallery-Diagnostics-20261123-102753.txt` 与录屏
> `56e15be04a03b83effdc3d8db878e469.mp4`。本轮实际抽取并检查了录屏的关键帧
> （约 00:06–00:10 的 Viewer 进入、00:31–00:32 的配置页预测性返回），没有逐帧
> 声称系统合成结果。日志没有记录源码 SHA，不能仅凭 1.14.1 (32) 认定 APK 就是
> 当前 HEAD。

## 1. 结论先行

| 问题 | 已确认的证据 | 本轮修复 |
|---|---|---|
| Viewer 打开退化 | 日志同一次打开：`app_entry_preview` 在 `10:27:12.118`（windowAlpha=0），`entry_release_requested` 在 `10:27:12.385`（windowAlpha=1），`app_entry_started` 在 `10:27:12.391`，`entry_completed` 在 `10:27:12.528`。窗口淡入 120 ms 先独立完成，图片展开 137 ms 后才开始。代码中 `animateViewerWindowIn()` 与 `startAppOwnedPhotoEntry()` 是两个 `ValueAnimator`。 | 一个统一的 entry progress：预解码缩略图几何、scrim、chrome、window alpha 共用同一进度；全尺寸代理在可映射时**加入正在运行的进度**，不再新建第二个 120 ms 动画。 |
| 下拉松手被提前截断 | 日志 `10:27:01.078` 进入 `INTERACTIVE_RETURN`，`10:27:01.319` 直接 `COMPLETE:system_return_no_app_visual`。代码 `mediaDismissSettled(true) -> finish() -> requestViewerClose()` 的 `appOwnedPhotoEntry()` 分支直接 `commitSystemReleaseReturn(appFadeOut=true)`。 | 松手达到阈值后保留当前 surface，从当前位移/缩放/透明度继续向 Grid 实测目标收束；少于阈值仍回弹；回弹中再次拖动/返回从当前帧接管。 |
| 关闭 API 顺序 | `overridePendingTransition()` 原来在 `super.finish()` 之前调用（与官方文档相反）。 | 调整到 `super.finish()` 之后；只走 one-shot 的 `overridePendingTransition`，不设置可长期残留的 `overrideActivityTransition` 覆盖。 |
| 配置页预测性返回白底 | `MediaActivity.onStop()` 对 `isPhotoProfiles && !photoViewerTransition` 无条件 `clearPhotoDisplayState()`；配置管理页是 `SimpleActivity` 子页，不是 Viewer，所以它一覆盖父页，父页的 `mediaGrid.adapter` 就被清空、`mMedia` 清空。日志在返回后 `10:27:23.631` 重新 resume 并重新扫描/发布。录屏 00:31–00:32 中缩放中的配置页四周只剩页面底色，没有缩略图。 | 配置子页覆盖期间保留已授权的 Photos Grid（adapter、滚动锚点、快照）；返回后按结果协议刷新/校验，不再先清空再重建。 |

**尚未由真机证明的部分**：系统 `CustomCrossActivityBackAnimation` 实际合成的每一帧、深色/浅色主题下的最终观感、以及缩略图放大阶段的清晰度。本文只把代码、日志和录屏关键帧能支持的部分写成结论。

## 2. 录屏关键帧（本轮实际检查）

- 00:06–00:10：Viewer 进入时先出现整块黑色窗口，缩略图在一小段时间内停留在源位置/很小，然后快速展开；与日志的“窗口先完成、图片后展开”一致。
- 00:31–00:32：`Manage photo profiles` 页做预测性返回缩放时，父页 `Photos` 的工具栏和底栏可见，但网格区域是整片底色，没有本应存在的缩略图；手势提交后缩略图才重新出现。这与父页在 `onStop` 被清空、返回后重扫的日志时序一致。
- 录屏没有覆盖所有主题/左右边缘/取消返回组合；这些仍需 Pixel 8 Pro 验收。

## 3. 打开：一个 progress，而不是两个时钟

### 3.1 旧行为为什么分段

1. `showAppOwnedEntryPreview()` 把网格缩略图 bitmap 放到源矩形，立即 `animateViewerWindowIn()` 把整个窗口 alpha 0→1（120 ms）。
2. 大图解码完成后 `releasePhotoSharedElement()` → `startAppOwnedPhotoEntry()`；它才 `prepareStaticPhotoEntry()`、`capture()`，然后新建一个 120 ms 的 `ValueAnimator` 做缩略图→全屏几何。
3. 因此用户先看到“窗口出现 + 图片不动”，再看到“图片突然开始展开”。`StaticPhotoEntryView` 本身没有问题；问题在启动时机与两个动画各自完成。

### 3.2 新行为

- `showAppOwnedEntryPreview()` 不再启动窗口淡入。它在后台只做一次 **bounds-only** 的 `StaticPhotoHeader.read`（同一本地文件的 EXIF/尺寸；不做全尺寸解码），并用网格给出的 `StaticPhotoSource` 在首帧后计算全屏 fit 目标。
- 目标可用时创建 `GridPhotoEntryPreview`：用网格缩略图在 `StaticPhotoEntryGeometry` 上从源矩形向目标矩形绘制。它把裁切缩略图正确投影到源格，并用同一 bitmap 拉伸填满裁切没有覆盖的条带，保证 progress 0 与网格像素一致、展开过程中没有空白带。
- 一个 `ValueAnimator` 驱动 `applyAppEntryProgress(p)`：preview 几何、scrim alpha、chrome alpha、system bars、**window alpha** 都读取同一个 `p`。窗口在 p=0 仍不可见，网格保持显示；从 p>0 起才与图片展开一起淡入。
- 全尺寸代理可用时，`startAppOwnedPhotoEntry()` 在**当前 p** 创建/接管 `StaticPhotoEntryView`，移除预解码 preview，然后让同一个 animator 继续；若 animator 已到 1，则代理直接落在 1 并完成 entry。不会再从 0 重启动画。
- 如果 header/缩略图不可用，窗口保持不可见直到全尺寸代理可映射，再由代理从 0 启动同一个时钟；不会出现“窗口已经淡入、图片还停着”的分段。
- 视频或没有静态缩略图几何的媒体没有可测量的 preview 目标，保留原有的窗口淡入 + fallback/loading 路径，不强行套用图片几何。
- `entry_preview_target_mismatch` 诊断记录预解码目标与正式 renderer 目标的差异；正常应在 1 px 内。

## 4. 关闭：从当前帧继续到真实目标

### 4.1 旧行为

- 拖动时 `renderInteractiveDismissProgress()` 已把 surface 缩放/位移、scrim/chrome 淡出。
- `mediaDismissSettled(true)` → `finish()` → `requestViewerClose()`；`appOwnedPhotoEntry()` 分支直接 `commitSystemReleaseReturn(appFadeOut = true)`，只结束窗口并播放窗口淡出，没有从当前拖动姿态继续的图片回程。
- 日志的 `COMPLETE:system_return_no_app_visual` 正是这条路径。

### 4.2 新行为

- `mediaDismissSettled(true)` 在 app-owned entry 下直接进入统一关闭核心，不再 `finish()`。
- 关闭核心保留 `interactiveDismissSurface` 的当前变换，`beginReturn()` 复用同一个 gesture token，发布一次结果（位置 + 删除集合 + token）。
- 通过 `MediaTransitionSessionCoordinator.photoReturnTarget()` 读取 Grid 已被 `showGridPreview()` 抑制的真实目标（允许在 `INTERACTIVE_RETURN` / `RETURN_PREPARING` 读取，不必等不会发生的 Activity reenter）。
- 用 `MeasuredPhotoReturnView` 从 surface 的当前屏幕矩形 `origin` 向 `StaticPhotoReturnTarget` 的实测格位绘制；progress 0 就是手指松开的那一帧，progress 1 落在真实缩略图格位并交叉淡化网格缩略图。**不会先回到全屏再关闭**。
- 目标不可用（缩略图被回收、已删除、滚动出视口、视频等）时，surface 连续缩小/淡出，随后用 one-shot 窗口淡出离开；不会硬切、黑屏或跳到错误缩略图。
- 未达到阈值仍走原有回弹 animator；回弹过程中再次拖动或触发返回，`mediaDismissGestureBegin()` / 关闭核心从当前 progress 接管。
- 工具栏返回走**同一个**关闭核心：先 `beginInteractiveDismiss()` 取得当前图片 surface，再从全屏帧收束到网格目标。系统左右边缘返回在稳定 `VIEWING` 时仍由平台整窗动画负责，应用不运行第二套图片回程。

## 5. `overridePendingTransition()` 顺序

`commitSystemReleaseReturn()` 现在在 `super.finish()` **之后**调用 `overridePendingTransition(0, R.anim.photo_viewer_fade_out)`，只在没有可用 surface 的降级路径使用。没有调用 `overrideActivityTransition()`，因此不会在 Activity 的 OPEN/CLOSE 覆盖槽里留下长期设置，也不会影响下一次系统预测性返回。

## 6. 配置页白底：根因与修复

### 6.1 根因

`MediaActivity.onStop()`：

```kotlin
if (isPhotoProfiles && !photoViewerTransition) {
    ...
    clearPhotoDisplayState()   // -> discardInaccessibleGrid() -> mediaGrid.adapter = null; mMedia.clear()
}
```

`PhotoProfileManageActivity` 是独立 `SimpleActivity` 子页；它覆盖 Photos Grid 时 `photoViewerTransition` 仍为 false，所以父页被当成“真正离开 Photos”而清空。平台做跨 Activity 预测性返回时，父页的快照/最后一次绘制只剩页面底色；提交后 `onResume()` 再重扫、重建 adapter，缩略图才回来。日志 `10:27:23.631` 的 resume 后重新 scan/发布与这个解释一致。它不是“缩略图一直没加载”，也不是扫描慢：缩略图在配置页打开前已经 `RESOURCE_READY`，是父页在覆盖期间被主动清空。

### 6.2 修复

- 新增纯决策 `PhotoGridRetentionPolicy.clearsDisplayedGrid(isPhotoProfile, viewerTransition, childPageCovered)`。
- `MediaActivity` 在菜单启动 `PhotoProfileManageActivity` 前设置 `photoChildPageOpen = true`；`onStop()` 仍然取消扫描/请求，但**不清空**显示的 Grid；`onResume()` 捕获并清除该标记，`sameAuthorizedGrid` 现在也接受“从配置子页返回”。
- 结果协议不变：`onActivityResult` 继续根据 `EXTRA_RELOAD_MEDIA` / `EXTRA_REFRESH_CURRENT_DISPLAY` 调用 `reloadPhotoProfile()` / `refreshPhotoProfileDisplay()`；真正改变 scope 时仍会 `clearPhotoDisplayState()` 重建。
- 新增低频诊断 `PhotoGrid/photo_grid_retained_for_child_page`，记录 profile、adapterVersion、mediaCount、adapterPresent、gridChildren，便于下一次真机确认父页在覆盖期间确实保留了内容。没有增加逐帧或逐文件日志。

### 6.3 未确认部分

- 系统预测性返回最终合成的是父页窗口快照还是重新绘制的父页，仍需要 Pixel 8 Pro 的 `dumpsys SurfaceFlinger`/录屏确认；本轮只确认了“父页在覆盖期间不再清空”这一必要前置条件。
- 配置页自身仍是白色页面背景（浅色主题），这是页面设计，不是本次白底根因；没有为了让父页透出而修改配置页背景色。

## 7. 保留的预测性返回机制

未回退上一轮的以下结构：

1. Viewer 主题不透明。
2. Android 14+ 启动 Viewer 不使用 `ActivityOptions.makeSceneTransitionAnimation()`。
3. Viewer 入场由应用自绘。
4. 稳定 `VIEWING` 状态下禁用应用返回回调，系统边缘返回交给平台。
5. 普通页通过可加载的 CLOSE 动画选择系统 Custom 跨 Activity 返回分支。

三个出口现在是：

| 出口 | 行为 |
|---|---|
| 系统左右边缘返回（稳定 VIEWING） | 平台整窗预测性返回；应用只发布结果，不安装图片回程。 |
| 工具栏返回 | 应用统一关闭核心：当前帧 → 真实 Grid 目标 → 结果交接。 |
| 手指下拉 | 跟手；未过阈值回弹；过阈值从当前帧连续收束到目标；失败时连续淡出降级。 |

快速交叉操作：入场未结束返回走既有窗口淡出降级（不在本轮改为反向几何，避免在 entry 租约/状态机上引入新的竞态）；回弹中返回/拖动从当前帧接管；连续打开退出受 `appEntryGeneration`/`appReturnGeneration` 与 session token 保护；媒体切换后关闭会因 path/page 不匹配取消旧 gesture 并降级；媒体删除后目标不可用走连续淡出。

## 8. 验证

### 8.1 编译

```bash
./gradlew :app:compileFossDebugKotlin
```

结果：`BUILD SUCCESSFUL`（本地 JDK 17）。

### 8.2 相关测试

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'org.fossify.gallery.helpers.PhotoGridRetentionPolicyTest' \
  --tests 'org.fossify.gallery.helpers.GridPhotoEntryPreviewTest' \
  --tests 'org.fossify.gallery.helpers.ViewerAppReturnSessionTest' \
  --tests 'org.fossify.gallery.helpers.AppOwnedViewerCloseTest' \
  --tests 'org.fossify.gallery.helpers.ViewerEntryClockTest' \
  --tests 'org.fossify.gallery.helpers.PlatformViewerReturnTest' \
  --tests 'org.fossify.gallery.helpers.ViewerBackOwnershipTest' \
  --tests 'org.fossify.gallery.helpers.ViewerBackGestureTest' \
  --tests 'org.fossify.gallery.helpers.SystemBackTransitionTest' \
  --tests 'org.fossify.gallery.helpers.MediaTransitionSessionCoordinatorTest' \
  --tests 'org.fossify.gallery.helpers.PhotoViewerReturnTargetTest' \
  --tests 'org.fossify.gallery.helpers.StaticPhotoEntryGeometryTest' \
  --tests 'org.fossify.gallery.helpers.PhotoProfileManageActivityTest'
```

本轮新增测试的实际断言（不是源码字符串检查）：

- `AppOwnedViewerCloseTest`：用真实 `ViewPagerActivity`、真实 `MediaTransitionSessionCoordinator` 和共享 surface 驱动 `mediaDismissSettled(true)`；断言松手瞬间状态是 `RETURN_PREPARING`、窗口尚未 finishing，推进 looper 后终态是 `FALLBACK:app_return_*`/`COMPLETE:app_return_*`，且**没有** `system_return_no_app_visual`。这直接覆盖了旧分支被截断的失败时序。
- `ViewerEntryClockTest`：用真实 Activity 发布缩略图/source 后启动 entry；断言 animator 已启动时 window alpha 与 `appEntryProgress` 都是 0，动画完成后两者都是 1，证明窗口不再先独立淡入。
- `ViewerAppReturnSessionTest`：验证 `showGridPreview`/`photoReturnTarget` 在 `INTERACTIVE_RETURN` 与 `RETURN_PREPARING` 可用、同一 gesture token 只发布一次结果、目标缺失时走 app fallback。
- `GridPhotoEntryPreviewTest`：验证裁切缩略图在源帧精确落回网格位置、展开时保持相对位置、非裁切缩略图覆盖整个投影。
- `PhotoGridRetentionPolicyTest`：配置子页覆盖保留、真正离开清除、Viewer 保留、非 Photos 页不受影响。

本次实际运行 52 个测试，0 failure、0 error（`app/build/test-results/testFossDebugUnitTest` 汇总）。Robolectric 使用 API 34（本地 JDK 17 可运行）；没有为此搭建模拟器。

既有测试 `SystemBackTransitionTest` 含 `@Config(sdk=[36])` 方法，本机 JDK 17 创建 Robolectric sandbox 时报
`Android SDK 36 requires Java 21 (have Java 17)`，因此未能在本轮本地执行；这是环境限制，不是本轮代码失败。
CI 的 `arm64-foss-release` workflow 使用 JDK 21，但它只运行 `assembleFossRelease`，不运行单测。

`AppOwnedViewerCloseTest` 使用的测试 Grid 没有提供目标，因此它覆盖的是“目标缺失仍走 app return/连续 fallback”的路径；
有目标时的 `MeasuredPhotoReturnView` 路径由 `ViewerAppReturnSessionTest`、`GridPhotoEntryPreviewTest` 与代码审查覆盖，
最终像素仍需真机验收。

### 8.3 没有做什么

- 没有启动模拟器，没有搭建 Android 16 模拟环境。
- 没有做真机视觉验收；所有“动画连续/背景正确”的最终判断仍需 Pixel 8 Pro。
- 没有修改 `main`、没有新建分支、没有 PR、没有强推。

## 9. Pixel 8 Pro 待验收

1. 网格点开图片：窗口不应先整体出现再停顿；缩略图应在第一帧后立即开始向全屏展开；大图接管时不应出现空白/黑闪。
2. 左右边缘返回：稳定页面应看到系统整窗缩放并露出真实网格；不应叠加应用图片回程。
3. 边缘返回取消：图片/网格应回到原状；随后立即下拉不应跳到错误帧。
4. 工具栏返回：应从当前全屏帧连续收束到被点击的缩略图，然后窗口离开；不应先回全屏或只黑屏淡出。
5. 下拉跟手：图片随手指移动、缩放、遮罩变淡；松手未过阈值回弹，回弹中再次拖动从当前帧接管。
6. 下拉过阈值：从松手帧连续到真实缩略图；目标不可用（删除/滚动出视口/视频）时应连续淡出，不硬切。
7. 配置管理页预测性返回：缩放中的配置页四周应露出**真实 Photos 缩略图**，不是整片白/灰底；深浅色主题、配置编辑页、取消手势分别复核。
8. 快速操作：入场未结束返回、系统返回取消后立即下拉、连续打开退出、切换媒体后关闭、删除当前媒体后关闭。

## 10. 涉及文件

- `activities/ViewPagerActivity.kt`：统一 entry clock、app-owned close core、结果发布抽取、关闭 API 顺序。
- `helpers/GridPhotoEntryPreview.kt`（新增）：预解码缩略图几何预览与无空白带条带填充。
- `helpers/StaticPhotoEntryGeometry.kt`：`StaticPhotoHeaderReader`（后台 bounds-only 读取）。
- `helpers/MediaTransitionSessionCoordinator.kt`：允许交互/准备阶段读取 Grid 目标。
- `helpers/MeasuredPhotoReturnView.kt`：暴露 header 供 app-owned 回程复用。
- `helpers/PhotoViewerTransition.kt`：`APP_RETURN_MS`。
- `activities/MediaActivity.kt` + `helpers/PhotoGridRetentionPolicy.kt`（新增）：配置子页覆盖期间保留 Photos Grid。
- `fragments/PhotoFragment.kt`：回程 header/drawable 复用接口。
- 新增测试：`AppOwnedViewerCloseTest`、`ViewerEntryClockTest`、`ViewerAppReturnSessionTest`、`GridPhotoEntryPreviewTest`、`PhotoGridRetentionPolicyTest`。
