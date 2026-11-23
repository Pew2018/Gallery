# 11 · Phase 5：Viewer 回程终态、接管进度与系统栏收尾

> 审计基线：开始前重新 `git fetch`，`perf/home-gallery-sorting` 远程 HEAD 为
> `b9e781f7d8bed5d95220528178bfb12e79bf81cd`，与本地 HEAD 一致；该分支没有其他
> Agent 的新提交。稳定 Tag `phase-4-viewer-animation-complete`（tag 对象
> `1196a993...`）仍指向 `b9e781f7d`，本轮未移动、未删除。`origin/main` 的
> `b308a2132` 属于另一条 checkbox 工作线，未合并。
>
> 证据范围：本轮重新读取了工作区内的
> `Gallery-Diagnostics-20261123-112745.txt`（1.14.1 / build 32），并用 `ffprobe`
> 确认同目录 `10d40fd5e762ee634386df16f22a84db.mp4` 为 23.0 s、576×1280、30 fps。
> 该视频不是任务描述中的 44.9 s 录屏；其配套日志记录的是
> `themeWindowIsTranslucent=false`、`app_entry_preview`、
> `COMPLETE:system_return_no_app_visual` 的旧 app-owned 方案，**早于**
> `b9e781f` 的共享元素恢复。因此：
>
> - 该日志不能作为当前共享元素回程的黑屏、卡顿或 63–92 ms 间隔证据；
> - 本轮没有 Pixel 8 Pro 真机直连，也没有新的 SurfaceFlinger 逐帧数据；
> - 所有结论均来自当前源码、JVM/Robolectric 测试和现有回归测试，不等同于真机验收。

## 1. 本轮实际改动

| 文件 | 改动 | 目的 |
|---|---|---|
| `helpers/ViewerReturnProgress.kt`（新增） | `resume(base, progress)` 使用剩余区间映射；`settleDurationMs(progress)` 保留原 140–360 ms 曲线 | P0-B 接管进度连续、回弹时长可测试 |
| `helpers/ViewerReturnVisualClock.kt`（新增） | 单时钟剩余量；终态最多提交一次；取消不回写；新回程重新武装 | P0-A 终态幂等 |
| `activities/ViewPagerActivity.kt` | `renderSystemBackProgress` 改用 `resume()`；`onSharedReturnTerminal` 改走 `ViewerReturnVisualClock`；`mediaDismissSettled` 使用 `settleDurationMs()`；取消回弹结束时补做 lease 清理 | P0-A / P0-B |
| `helpers/OxygenSystemBars.kt` | `applyViewer()` 读后写：只有实际变化才写窗口属性/图标明暗/状态栏 underlay，并返回是否写入 | P0-C 减少回程逐帧重复写入 |
| `PhotoViewerInitializationTest` | 新增接管进度/取消恢复、终态不重复提交 2 个 Activity 级测试 | 行为验证 |
| `ViewerReturnProgressTest` / `ViewerReturnVisualClockTest`（新增） | 纯逻辑边界测试 | 行为验证 |
| `OxygenSystemBarsTest` | 重复状态不写、变化状态必须写 | P0-C |

没有修改：

- `oxygen_classic_page_back_enter.xml` / `oxygen_classic_page_back_exit.xml`
- `OxygenClassicPageBackWindow`
- `GalleryViewerTransitionTheme`
- `sharedElementEnterTransition` / `sharedElementReturnTransition` 的架构
- 预测性返回的边方向、圆角、Shell 几何
- 受保护目录、`.nomedia`、媒体身份、排序和搜索逻辑

## 2. P0-A：Viewer 返回网格的最终图层交接

### 2.1 已核对且**不需要**修改的部分

- `MediaActivity.readViewerReturnTarget(request, position)` 一直把
  `request.operation` 作为 `previewOperation` 传入；因此
  `InteractiveGridThumbnail.suppress()` 把目标 `alpha` 设为 0 时，只要预览仍属于
  当前 operation 且 holder/drawable/loadedKey 未变，映射阶段仍会接受该目标。
- `mapViewerReturnTarget()` 在确认 `expected.matches(current)` 之后才
  `releaseGridPreview()`，随后把 `transitionName` 交给平台并
  `observeFirstViewerReturnFrame()`。预览的 alpha 所有权、平台的
  `transitionAlpha` 所有权和 Adapter 的最终显示权是分开的。
- `awaitViewerReturnDraw()` 只有在 `resultDelivered && transitionTerminal != null`
  之后等待真实的 grid 绘制；目标像素、holder、adapter version、scope 任一变化都会
  走 `target_changed_at_handoff`，不会用旧坐标或旧 Drawable 收尾。
- `MediaAdapterStabilityTest` 的
  `whileFingerIsDownSwitchedTargetAloneMakesRoomForTheProxy` 与
  `aLoadedButTransparentTargetDoesNotStartSharedReturn` 已覆盖“手指按住时切换目标”
  和“平台仍隐藏缩略图”的真实交接顺序。本轮重跑通过。

因此没有把 Viewer 改回普通页面整窗返回，也没有新增第二段淡出。

### 2.2 实际存在的问题：终态视觉提交不是幂等的

旧代码：

```kotlin
if (!cancelled && sharedReturnAnimatorStarted) renderSharedReturnLayers(0f)
```

`SharedElementProgress` 的最后一帧已经是 `progress = 1f`，`renderSharedReturnLayers(1f - 1f)`
已经把 scrim、工具栏和系统栏写到终态。终态回调随后又调用一次
`renderSharedReturnLayers(0f)`：

- 对 `View.setAlpha` 而言同值是 no-op，不会造成可见的第二段动画；
- 但它仍会再次计算并写入全部 chrome、再查询一次父页工具栏，属于多余的终态提交；
- 一旦以后有人在 `renderSharedReturnLayers()` 里增加非幂等的写入，就会直接变成
  “最后一瞬间第二次缩放/淡出”的入口。

修复：引入 `ViewerReturnVisualClock`。

- `progress(progress)` 记录单时钟剩余量；
- `terminal(cancelled)` 最多批准一次终态；若最后一帧已到达 0，则跳过；
- `cancelled = true` 时不回写画面，保留当前 live frame 交给 fallback；
- 没有 animator 驱动的路径仍然不写终态（保留原有的 fallback 所有权）；
- 新回程开始时 `begin()` 重新武装，旧 operation 由
  `MediaTransitionSessionCoordinator.finishViewerMotion()` 的 token 校验拒绝。

### 2.3 取消回弹结束时的 lease 清理

`mediaDismissSettled(false)` 的 `onAnimationEnd` 过去只在 `cancelGesture()` 成功时
释放 `returnLeases`。如果该 gesture 已经被其他路径结束（outcome 已设置或状态已回到
`VIEWING`），lease 会残留，下一次回程可能因为 `Resource` 已被占用而拿不到
`INTERACTIVE_ANIMATOR` / `CHROME`。现在在“操作已经不再属于当前 return”时同样释放；
真正接管中的 `RETURNING` 仍保留 lease。

## 3. P0-B：预测性返回与下拖动画进度连续性

### 3.1 进度语义

`androidx.activity.BackEventCompat.progress` 的文档是 “Value between 0 and 1 on how
far along the back gesture is”；`OnBackPressedDispatcher` 在每个新手势的
`handleOnBackStarted` 之后重新分发 progress。也就是说：

- 新手势从屏幕边缘开始时，progress 从 0 重新计；
- 旧代码 `systemBackResumeProgress + progress` 把“上一段的绝对进度”和“新手势的
  归一化进度”直接相加。

例：回弹在 0.6 时被接管，新手势走到 0.5：

```text
旧：0.6 + 0.5 = 1.1 -> clamp 1.0   // 手指还有一半行程，envelope 已到终点
新：0.6 + 0.4 * 0.5 = 0.8         // 只消费剩余 0.4 区间
```

这不是“必须增加延迟”的问题，而是进度归一化错误：接管后的后半段拖动会变成死区，
图片在手指还没走完时就先到最小尺寸。

修复：`ViewerReturnProgress.resume(base, progress)`：

- `progress = 0` 时精确等于 base，接管第一帧不跳变；
- 单调、有界，`progress = 1` 时到达 1；
- base 非法（NaN/越界）时安全收敛。

`PhotoViewerInitializationTest.resumedSystemBackGestureConsumesOnlyTheRemainingIntervalAndCancelsToFullViewer`
直接驱动真实 `ViewPagerActivity`：

1. 下拖到 0.6；
2. 回弹未结束时开始系统返回，断言 base = 0.6；
3. progress 0 / 0.5 / 1 时 surface.scale 分别是 0.796 / 0.728 / 0.66；
4. 取消后回弹到 0，断言 scaleX/scaleY = 1、translation = 0、scrim 隐藏、系统栏
   回到 1、surface 释放。

### 3.2 取消回弹时长

现有公式是 `140 + 220 * progress`，最大 360 ms；本轮只把它提取为
`ViewerReturnProgress.settleDurationMs()` 并加测试，**没有**改成 220–260 ms 上限。
原因：任务明确要求“不要预设它一定优于现有值”，而当前没有真机逐帧 A/B 数据；
普通下拖的取消进度通常低于 0.35（217 ms），360 ms 只出现在接近满行程的系统手势取消。
该项保留为真机验收项（见第 8 节）。

## 4. P0-C：遮罩、工具栏、系统栏同步收尾

### 4.1 保留的单时钟

- 共享元素几何：平台 `sharedElementReturnTransition`；
- 遮罩：`renderSharedReturnLayers()` 中 `returnScrimAlpha * remaining`；
- 顶部/底部/视频 chrome：`returnOverlayAlphas * remaining`；
- 系统栏：`returnSystemBarProgress * remaining`。

本轮没有为任何控件新增独立 Animator。

### 4.2 工具栏硬交接的结论

`hideViewerToolbarWhenGridIsExposed(scrimAlpha)` 在 `scrimAlpha < 1` 且父页工具栏
真实可见时把 Viewer appbar 置 0。这是 2026-11-19 `Protect the grid toolbar during
viewer return` 的有意行为：回程中父页工具栏已经位于 Viewer 窗口之后，继续保留
Viewer appbar 会出现两套工具栏；置 0 后由 scrim 继续遮住父页，直到父页工具栏真正
可以被看到。它没有造成“图片消失/第二次缩放”，本轮保留该行为并在文档中标记为
“有意交接”，没有为了让 appbar 连续而新增淡出或改动父页工具栏。

### 4.3 系统栏重复写入

`BaseViewerActivity.updateViewerSystemBars()` 由 `renderSharedViewerCanvas()` 在每个
进度变化时调用。`OxygenSystemBars.applyViewer()` 过去无条件写：

- `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars`；
- API < 35 时的 `statusBarColor` / `navigationBarColor`；
- API >= 35 时的 status underlay 颜色和 visibility。

修复为读后写，并返回“是否真的写入了窗口属性”。颜色插值在相邻帧取整到同一值时，
不再重复触碰窗口；图标明暗和 underlay 可见性也一样。`OxygenSystemBarsTest` 覆盖：

- 相同 state 第二次调用返回 false；
- 进度变化返回 true；
- API 35+ 的 underlay 可见性变化返回 true；
- API 29 不把 `statusVisible` 当作 underlay 写入（该 API 由系统栏自身处理）。

没有全局隐藏系统栏，也没有改动 `WindowInsets` 处理。

## 5. P1-A：普通页面预测性返回

审计结论：**本轮不需要修改代码**。

- `oxygen_classic_page_back_enter.xml` / `exit.xml` 仍是恒等 alpha，几何由
  System Shell 的 `CustomCrossActivityBackAnimation` 负责；`PredictiveBackCloseAnimationTest`
  继续验证 1.0 → 1.0 且没有额外 scale/translation。
- `OxygenClassicActivityMotion.install()` 仍只在 Android 14+ 的普通页选择
  `OxygenClassicPageBackWindow` 的 custom CLOSE 分支；Viewer 仍走
  `configureShared()`（windowAnimations = 0），没有被改成普通页方案。
- `SystemBackTransitionTest` 继续验证 `clearOverrideActivityTransition` 只清除
  OPEN/CLOSE 两个槽、不安装长期 zero override，普通页启动不携带自定义 animation
  bundle。
- 父页恢复：常规文件夹返回走 `detachFolderViewerScan()` /
  `resumeFolderViewerScan()`，`MediaAdapterStabilityTest` 的
  `fiftyViewerPausesRejoinOneOrdinaryFolderRequestAndRevocationCancelsIt` 连续 50 次
  重入并断言 adapter version 不变、请求仍有效；Photo profile 返回走
  `sameAuthorizedGrid`，不重建 adapter。`checkLastMediaChanged()` 只在 MediaStore
  最新 id 变化时触发刷新，不是无条件重扫。

没有证据支持“返回后二次发布/白色占位/列表跳动”，因此没有改动扫描或排序。

## 6. P1-B：照片配置页面与网格显示连续性

审计结论：**本轮不需要修改代码**。

- `MediaActivity.onStop()` 仍通过 `PhotoGridRetentionPolicy.clearsDisplayedGrid()`
  保留被配置子页覆盖的 Photos 网格；`onResume()` 的 `sameAuthorizedGrid` 复用
  adapter、滚动锚点和已提交 revision。
- `PhotoProfileManageActivity.onResume()` 的 `refreshProfiles()` 使用
  `submitList`（DiffUtil），不是整页重建；从 `PhotoProfileEditActivity` 返回只刷新
  有变化的行。
- 受保护目录鉴权、`.nomedia` 可见性、profile 身份、revision、排序和搜索仍走原有
  校验，没有为动画绕过任何权限。

### 6.1 首帧诊断

现有低频事件已经区分了四层：

| 层 | 现有事件 |
|---|---|
| 数据准备完成 | `folder_snapshot_selected` / `photo_grid_publication_committed` |
| Adapter 接收数据 | `media_first_adapter_commit` / `FolderRefresh adapter_committed` |
| View 即将绘制 | `media_first_thumbnail_rendered`（pre-draw，带 visibleRect 检查） |
| 图片实际可见 | 同上事件的 `isShown && getGlobalVisibleRect` 条件 |

`photo_first_frame` 仍是 `postOnAnimation` 处的 trace 锚点，不代表合成器呈现；
本轮没有新增逐帧文件日志，也没有把 `photo_first_frame` 重命名为“已呈现”。
如果后续需要 SurfaceFlinger 级证据，应新增基于 `FrameCommitCallback` 的一次性事件，
而不是再挂一个每帧 OnDraw。

## 7. 测试与构建

本轮实际执行（JDK 21，本地 Robolectric dependency dir）：

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:assembleFossDebug
./gradlew :app:testFossDebugUnitTest
./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a
```

结果：

- 全量 `testFossDebugUnitTest`：187 个测试类、1339 个测试、0 failure / 0 error；
- `assembleFossDebug`：成功，生成 `gallery-30-foss-debug.apk`；
- 本地 `assembleFossRelease`（arm64-v8a，无仓库签名）：成功，R8/minify 与资源压缩
  通过；CI 使用临时或仓库签名生成可安装 APK。

新增/扩展测试：

- `ViewerReturnProgressTest`（5）
- `ViewerReturnVisualClockTest`（5）
- `PhotoViewerInitializationTest`（29 个方法，含新增 2）
- `OxygenSystemBarsTest`（4 个方法，跨 SDK 共 7 次执行，含新增 1）

回归测试（全部通过）：

- `MediaAdapterStabilityTest`（61）
- `MediaTransitionSessionCoordinatorTest`（10）
- `MediaTransitionGestureControllerTest`（6）
- `PhotoViewerReturnTargetTest`（2）
- `PredictiveBackCloseAnimationTest`（2）
- `SystemBackTransitionTest`（7）
- `PhotoGridRetentionPolicyTest`（4）
- `PhotoProfileManageActivityTest`（2）
- `OxygenClassicActivityMotionTest`（7）

## 8. GitHub Actions 交付

- Workflow：`Arm64 FOSS Release APK (non-publishing)`
- Run：<https://github.com/Pew2018/Gallery/actions/runs/45128600032>
- 源码提交：`c52c44c7cfc2b68747e90cf2e6c45b705fe33422`
- 状态：success（`Assemble FOSS arm64-v8a Release ONLY`、APK 校验、artifact 上传全部通过）
- Artifact：`gallery-foss-arm64-v8a-release`
- APK SHA-256：`dec6ee5820df72a0fff12b7bf3fb3ca61cca76f968a4cbd59c39afeed24f934c`
- 校验结果：`package=org.fossify.gallery`、`versionCode=32`、`versionName=1.14.1`、
  `targetSdk=36`、native code 仅 `arm64-v8a`、APK Signature Scheme v2 通过。
- 签名模式：`temporary-CI-not-upgrade-compatible`。本仓库未配置 release secrets，
  CI 按 workflow 的既有 fallback 生成临时测试签名；安装前需要卸载旧版本，不能覆盖升级。

## 9. 仍需 Pixel 8 Pro 真机验收

以下项目**没有**在本轮完成真机逐帧验收：

1. 普通照片左/右边缘返回：目标身份、回收到正确格位、无第二次缩放。
2. 下拖关闭与工具栏返回：图片从松手帧连续落到目标缩略图。
3. 回弹中重新开始返回：0.6 接管后后半段仍连续跟手（本轮只验证了数值映射）。
4. 取消后 Viewer 完整恢复：scale/translation/遮罩/系统栏/输入全部恢复。
5. 连续快速打开/关闭、Viewer 内切换照片后返回 B。
6. 视频/GIF/大图/旋转照片的降级路径。
7. 普通设置页/配置页左右预测性返回：真实父页内容、无二次淡出。
8. 深色/浅色主题、全屏/非全屏、动画关闭。
9. 若真机确认满行程取消回弹 360 ms 偏慢，再在 220–260 ms 上限做 A/B；本轮不预先改动。
