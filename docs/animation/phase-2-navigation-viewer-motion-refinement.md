# Phase 2 · 普通预测性返回、Viewer 系统栏交接与共享元素精修

> 审计基线：执行前重新 `git fetch` 并核对远程 `perf/home-gallery-sorting` HEAD =
> `5807a5510e152b44fa951c75bb9fe03cc1a71fcc`（与任务给出的基线一致，分支上没有其他
> Agent 的新提交）。任务书给出的 SHA 只作历史审计用，本文所有行号、资源与结论都基于
> 该提交。
>
> 平台依据：AOSP `platform/frameworks/base`（`main`）中
> `libs/WindowManager/Shell/src/com/android/wm/shell/back/`
> `CustomCrossActivityBackAnimation.kt`、`DefaultCrossActivityBackAnimation.kt`、
> `BackAnimationController`（`isCustomizeExitAnimation`）的当前实现，以及
> `developer.android.google.cn` 的 predictive back 指南（Android 15 起系统动画默认开启；
> 注册 `PRIORITY_DEFAULT` 的返回回调会关闭该 Activity 的预测性动画）。

## 0. 证据可得性（先行声明）

任务书引用的 `Gallery-Diagnostics-20261124-212905.txt`（约 5275 行）与录屏
`3dc515bf…mp4`（约 92.5 s）在本机**不存在**：工作区、`~/Desktop`、`~/Downloads`、
`~/Documents`、`/tmp` 均无同名或近似文件。因此：

- 本文引用的日志数值（冻结进度、进入等待毫秒、`pixels_unavailable` 410 ms、
  `LIGHT→DARK→LIGHT`）全部来自**任务书转述**，本文不声称已直接查看附件；
- 本轮新增的曲线数值是把**真实资源**按 Shell 的求值路径
  （`Animation#getTransformationAt`，Robolectric 中真实调用）采样得到的，不是录像目测；
- 系统窗口合成、SystemUI 图标托管、左右边缘手势的最终观感仍为**待真机验收**。

## 1. 结论先行

| 项 | 结论 | 等级 |
|---|---|---|
| 退出时系统栏冻结在中间色 | 根因：所有权与视觉进度被同一个布尔量（`viewerSystemBarHold`）表达，冻结值即松手进度（实测样本 0.49627578 等 7 个值） | 已确认（源码 + 日志转述） |
| 修复方式 | 新增 `ViewerSystemBarChoreography`：显式 8 个阶段 + 每次返回一个 token；`RETURN_WAIT_TARGET` 保持松手帧，`RETURN_SETTLING` 用与 scrim 相同的 remainder 从该帧连续过渡到页色 | 已实现 + 已测 |
| 进入时图标极性 `LIGHT→DARK→LIGHT` | 根因：`applyViewer` 用 `InsetsController` 的**当前标志**当 hysteresis 的 previous，而同一窗口在 `onResume`/`onPostResume` 里还会被 `OxygenSystemBars.apply` 按页色重写一次 | 已确认（源码） |
| 修复方式 | 按窗口记录归属（`PAGE`/`VIEWER`）；Viewer 窗口上的页级写入改为**重放** Viewer 状态；极性由 `IconPolarityLatch` 决定，同色不重算、决定性变化立即生效、模糊带保持 48 ms | 已实现 + 已测 |
| API 35+ 导航栏 | 只有一个「状态栏 underlay」，导航栏在强制 edge-to-edge 下无任何应用侧绘制，却仍按混合色决定图标 | 已确认（源码）→ 新增 `oxygen_classic_nav_underlay` |
| 普通页面提交后「缓慢」 | 450 ms 时钟里前景在 117 ms 就全透明，剩下 333 ms 窗口仍在、仍持有输入 | 已确认（资源实算） |
| 修复方式 | 共用 CLOSE 对 450→**240 ms**，alpha 在 60%（144 ms）归零；文件夹对 300→**220 ms**，alpha 在 82%（180 ms）归零 | 已实现 + 已测 |
| 「文件夹网格叠加溶解」 | 300 ms 时钟下 40% 处前景仍有 0.559 alpha、50% 处 0.375 | 已确认（资源实算） |
| 修复方式 | 同一条曲线重定时：40% 处 0.394、50% 处 0.238，时钟再短 27% | 已实现 + 已测 |
| Viewer 进入等待 136–452 ms | 时间线此前无法归因：日志只有阶段名，没有「距点击多久」 | 已补 `msSinceEntryRequest` |
| 返回目标 `pixels_unavailable` 410 ms | 目标 holder 已绑定且身份匹配，但它的缩略图请求从未被提优先级（`pendingViewerReturnPath` 是只写字段） | 已确认 → 新增一次性 HIGH 提升 |
| 动画时长 / 系统合成 / 左右边缘观感 | **未验收** | 待真机 |

## 2. P0-A：系统栏所有权与进度协调

### 2.1 修复前的三个缺陷

1. **所有权与进度混用**。`requestViewerClose()` 在任何退出路径上把
   `viewerSystemBarProgress` 冻结成 `viewerSystemBarHoldProgress`，`renderSharedViewerCanvas()`
   之后一律用冻结值。目的（防止父页面与自己两个写者竞争）是对的，代价是：手势松手在
   哪个进度，系统栏就停在哪个颜色，窗口被移除时再跳到页色。设备日志里 14 次退出记录了
   7 个不同的中间值，正是这个机制的直接产物。
2. **previous 极性来自别的写者**。`OxygenSystemBars.applyViewer()` 用
   `bars.isAppearanceLightStatusBars` 作为 hysteresis 的 `previous`。同一个
   `ViewPagerActivity` 窗口还会被 `SimpleActivity.onPostResume()`（`usesSharedToolbar`
   为真时）调用 `OxygenSystemBars.apply()` 按页色重写：`onResume` 先 blend(0)，随后
   `onPostResume` 按页极性强写一次，下一帧 Viewer 再按自己的色重算 —— 单方向进入因此
   可以出现 `LIGHT→DARK→LIGHT`。
3. **API 35+ 有半个实现**。`applyViewer()` 在 `SDK>=35` 只画状态栏 underlay；
   导航栏既没有 `navigationBarColor`（36 起是 no-op），也没有 underlay，却仍然用
   `colors.navigationBarBackground → BLACK` 的混合色决定图标极性。结果是图标按一个
   窗口从未绘制过的颜色选择。

### 2.2 状态机（`helpers/ViewerSystemBarChoreography.kt`）

```
PAGE            → 窗口已交还（不再写）
VIEWER_ENTERING → 入口时钟 0→1 混合，只有入口帧可写
VIEWER          → 稳定浏览，页面色 = viewer surface
INTERACTIVE_RETURN → 拖拽 / 预测性手势驱动
RETURN_WAIT_TARGET → 已提交、等待网格目标；保持松手帧（有意为之）
RETURN_SETTLING    → 返回时钟运行，从松手帧连续过渡到页色
PAGE_HANDOFF       → 页色已写（至多一次），此后禁止任何写入
CANCEL_RESTORE     → 取消手势后恢复 Viewer 外观
```

要点：

- 每个阶段**只有一个合法写者**，写者由 `canvasBarProgress()`（入口/手势帧）与
  `returnBarProgress()`（返回帧）两个入口划分，二者互斥；
- 每次 `beginReturn()` 递增 `returnToken`；旧 token 的进度帧与终态帧一律返回 `null`，
  迟到生命周期回调无法覆盖新所有者；
- `PAGE_HANDOFF` 之后 `terminal()`、`canvasBarProgress()`、`returnBarProgress()`
  全部返回 `null`：这就是原来「teardown 把系统栏刷回 viewer 色」的闪光路径。

### 2.3 进度协调公式

```
RETURN_WAIT_TARGET : value = releaseProgress              (不写，值不变)
RETURN_SETTLING    : value = releaseProgress * remaining  (remaining = 1 - transitionProgress)
PAGE_HANDOFF       : value = 0                            (至多一次)
CANCEL_RESTORE     : value = 1                            (一次，然后回 VIEWER)
```

`remaining` 与 `renderSharedReturnLayers()` 给 scrim、chrome 用的是同一个
`ViewerReturnVisualClock` remainder，所以「前景消失」与「系统栏落到页色」在同一帧完成，
不会出现「图片已经到网格、状态栏还是半灰」的一帧。

### 2.4 图标极性（`IconPolarityLatch`）

- 决策只依赖**本窗口实际绘制的 surface**；同色第二次调用直接返回上一次结论（不重算，
  不可能出现同色两个答案）；
- `|contrast(black) - contrast(white)| >= 3.0` 视为决定性变化，立即应用（主题切换、
  直接跳到 viewer surface 这类场景不能等）；
- 在 `SWITCH_MARGIN(1.0)` 之外的模糊带内，变化需连续保持 48 ms 才应用；
- `adopt()`：`OxygenSystemBars.apply()` 把自己刚画上的页极性交给锁存，因此 Viewer 在
  progress=0（surface 就是页色）不会产生任何一次多余反转；
- 不使用「永久固定白/黑图标」的规避手段。

### 2.5 API 35+ edge-to-edge

`OxygenSystemBars.applyViewer()` 在 `SDK>=35` 同时维护两个 underlay：

| 视图 | 位置 | 高度 | 颜色 |
|---|---|---|---|
| `oxygen_classic_status_underlay` | TOP | statusBars ∪ displayCutout（`IgnoringVisibility`，稳定） | `blend(page.statusBar, viewer_status_surface, p)` |
| `oxygen_classic_nav_underlay` | BOTTOM | navigationBars（`IgnoringVisibility`，稳定） | `blend(page.navigationBar, BLACK, p)` |

`SDK<35` 继续写 `statusBarColor` / `navigationBarColor`，不创建导航 underlay，
旧版本行为不变（`OxygenSystemBarsTest` 的 29 用例覆盖）。

### 2.6 各退出路径的所有权归属

| 路径 | 入口 | 终态 |
|---|---|---|
| toolbar 返回 | `finish()` → `requestViewerClose("finish")` | shared return → `onSharedReturnTerminal(false)` |
| 系统返回键 | `finishAfterTransition()` → `requestViewerClose("system_back")` | 同上 |
| 左/右边缘预测性返回 | `ViewerBackGesture`（`PRIORITY_DEFAULT`，平台不动画该窗口）→ 自己的 envelope → `commitSystemBack()` → `requestViewerClose` | 同上 |
| 向下拖拽 | `mediaDismissSettled(true)` → `finish()` → `requestViewerClose` | 同上（从拖拽帧继续） |
| 手势取消 | `mediaDismissSettled(false)` / `cancelSystemBackGesture()` | `cancelInteractive()` → `VIEWER` |
| 无代理 / 超时兜底 | `releaseToPage()` + `overridePendingTransition(0, fade_out)` | `PAGE_HANDOFF`，不再写 |
| 进入中关闭 | `finishDuringMediaEntry()` | `releaseToPage()` |
| 系统动画关闭 | `OxygenClassicMotion.enabled=false` → `eligible=false` → 兜底 fade | `PAGE_HANDOFF` |

> `ViewerBackGesture` 以默认优先级注册，按官方指南这会关闭该 Activity 的**平台**
> 预测性动画，因此 Viewer 的左右边缘预览完全由应用自己的 envelope 负责；这与普通页面
> （不注册回调、平台驱动）是两套机制，本轮没有混用。

## 3. P0-B：普通页面原生预测性返回

### 3.1 平台机制（AOSP main 实读）

`CustomCrossActivityBackAnimation`（本应用使用的分支）：

- `preparePreCommitClosingRectMovement()`：关闭页 `scaleCentered(MAX_SCALE)` 并按边缘
  偏移，父页 `startEnteringRect = targetEnteringRect = startClosingRect`（**不动**）；
- `getPreCommitEnteringBaseTransformation(p) = enterAnimation.getTransformationAt(p * 0.2)`，
  `PRE_COMMIT_MAX_PROGRESS = 0.2`；
- `onPostCommitProgress()`：`currentClosingRect` **保持松手位置不再前进**，只把
  加载到的 CLOSE-exit 资源按 `getPostCommitProgress(linearProgress)` 求值后作用在
  冻结矩形上；
- `getPostCommitAnimationDuration() = min(2000, max(close.duration, enter.duration))`，
  该值同时是**资源求值分母**与**窗口移除时刻**；
- `getPostCommitProgress(p) = min(1, postCommitDuration / min(2000, duration) * p)`：
  ⇒ 关闭/进入两个资源的 duration 必须相等，否则较短的一方会被提前拉满。

结论：应用能控制的只有「时钟长度 + 两条曲线」，`96dp` 位移方向与 `+X` 终点、预提交
几何、父页静止都由平台决定，本轮没有改动其中任何一项。

### 3.2 方案比较

| 方案 | 真实父页预览 | 左右方向 | 提交后动效 | 结论 |
|---|---|---|---|---|
| A 平台默认（`setWindowAnimations(0)`） | 父页被缩到 `MAX_SCALE` 并偏移 96dp，四周露出 Shell 背景（本项目此前的黑底问题） | 平台 | 450 ms，alpha 20% 归零 | 不采用（与既有真机结论冲突，且无法在无设备条件下验证） |
| B 现有自定义 CLOSE 对 | 父页保持全屏 base rect，真实上一页填满 | 平台（预提交） | 由资源决定 | **保留**，仅重定时 |
| C 只自定义提交阶段 | 同 B | 平台 | 同 B | 即当前实现形态 |

CLOSE-enter 仍保持 identity（alpha 1、无位移）：它在手势期间被
`getPreCommitEnteringBaseTransformation` 调用，任何几何都会让真实父页位移。

### 3.3 时钟与曲线（修改前后）

**共用页面对（设置、主题、自定义照片配置）** —— `oxygen_classic_page_back_*`

| 归一化进度 | 旧 alpha（450 ms） | 新 alpha（240 ms） |
|---:|---:|---:|
| 0.02 | 0.915 | 0.949 |
| 0.10 | 0.552 | 0.733 |
| 0.20 | 0.135 | 0.482 |
| 0.26 | 0.000 | 0.372 |
| 0.40 | 0.000 | 0.130 |
| 0.60 | 0.000 | 0.000 |
| 1.00 | 0.000 | 0.000 |

绝对时间：前景消失 117 ms → **144 ms**；无可见前景的尾部 333 ms → **96 ms**。

**文件夹对（相册主页 → 主页）** —— `oxygen_classic_folder_back_*`

| 归一化进度 | 旧 alpha（300 ms） | 新 alpha（220 ms） | 位移（x96dp） |
|---:|---:|---:|---:|
| 0.02 | 0.996 | 0.995 | 0.014 |
| 0.20 | 0.864 | 0.820 | 0.238 |
| 0.30 | 0.727 | 0.634 | 0.403 |
| 0.40 | 0.559 | **0.394** | 0.561 |
| 0.50 | 0.375 | **0.238** | 0.700 |
| 0.60 | 0.230 | 0.132 | 0.785 |
| 0.80 | 0.074 | 0.005 | 0.920 |
| 0.82 | 0.055 | 0.000 | 0.938 |

两处改动同时生效：半透明重叠的**时长**（时钟 −27%）与**不透明度**（40% 处 0.559→0.394）
都下降，而最后一帧仍带着 94% 位移，不是「原地蒸发」。

数值不是手写估算：`PredictiveBackCloseAnimationTest` / `FolderBackCloseAnimationTest`
用反射调用 `Animation#getTransformationAt`（Shell 的同一条路径）重新采样并断言上述区间。

### 3.4 刻意未改动

- `96dp` 终点与 `+X` 方向：与 `cross_activity_back_entering_start_offset` 一致，
  两侧边缘都由预提交阶段负责方向（不按 LTR/RTL 推断）；
- CLOSE-enter 的 identity；
- `<set>` 两个子动画 duration 必须等于 set duration（`getPostCommitProgress` 的分母）；
- `SDK<34` 的 `overridePendingTransition` 路径与 `oxygen_classic_page_close_*` 资源。

## 4. P1：Viewer 进入 / 返回

### 4.1 进入时间线

进入的门是 `PhotoFragment.reportImageRendered()` → `ViewPagerActivity.imageRendered()` →
`requestMediaEntryRelease()` → 下一帧 `doOnPreDraw` → `startPostponedEnterTransition()`。
`entryRenderer()` 要求 `deliveredImage` 正是 `gesturesView` 当前 drawable，也就是
**整幅图解码完成**才放行；这正是日志里 136–452 ms 等待的主要构成。

本轮做了两件事：

1. 在启动 intent 里写入 `EXTRA_REQUESTED_AT`（elapsedRealtime，仅诊断开启时），
   `logMediaEntry()` 的每条事件现在都带 `msSinceEntryRequest`，时间线可以按
   「距点击」直接排序，不必再跨行相减；
2. **没有**改动放行条件。用缩略图代理提前放行会同时触碰几何精度、身份校验、
   renderer handoff 三条已验收契约（`takeOverSharedEntry` / `validatesStaticPhotoHandoff`），
   在无设备条件下无法验证不闪烁，故留作下一轮带真机测量后再动。

### 4.2 返回目标资源

`MediaActivity.readViewerReturnTarget()` 的 `pixels_unavailable` 意味着 holder 已绑定、
`media_bound_path` 与 `thumbnail_request_key/loaded_key` 已匹配，只是 drawable 还没到。
2026-11-24 的慢样本（视频，410 ms）正属于这一类。

本轮新增 `MediaAdapter.promoteQueuedThumbnail(path)`：在目标 holder 上，如果它的
`ThumbnailLoad.pending != null`（请求已排但未启动），立刻以 `Priority.HIGH` 启动；
对已启动的解码不做二次请求（避免重复解码），对已完成的请求不做无效提升。
`MediaActivity` 在等待循环里**每个返回请求最多提升一次**，并记录
`grid_target_promoted`（仅诊断开启）。

未采用的手段（明确排除）：延长 `READY_TIMEOUT_MS`、把 readiness 强制写成 ready、
每次退出重新做全量扫描或整表替换。

### 4.3 手势与最终动画的连续性

拖拽/预测性返回的松手帧由现有 `MediaSharedSurface.takeOver` +
`ChangeTransform`/`MediaReturnCrop` 承接，本轮未改。系统栏以前是这条链上唯一
「松手后不再前进」的图层，现在也跟随同一个 remainder，因此图片、scrim、chrome、
系统栏四者从松手帧开始同步收敛。

### 4.4 fallback 分类（保持现有语义）

| 触发点 | 状态 | 处理 |
|---|---|---|
| `requestViewerClose` 时 `canReturn=false` | `FALLBACK:viewer_no_proxy` | `releaseToPage()` + fade_out，不伪造共享 |
| 返回目标准备超时 | `FALLBACK:viewer_return_timeout` | 同上 |
| 平台在无动画情况下 finish | `FALLBACK:platform_finished_without_motion` | 同上 |
| 进入被拒（缩略图未就绪等） | `Stage.FALLBACK` | 进入 fade（`photo_viewer_fade_out`），不冒充共享进入 |

日志里的 `FALLBACK:viewer_no_proxy`（进入请求 48：`source_rejected reason=thumbnail_not_ready`
→ `sharedCandidate=false reason=window_fade` → `entryMapped=false`）说明该次兜底与**进入时
源缩略图未就绪**直接相关，属于安全路径，本轮没有为了消灭统计数字而强行共享。

## 5. 诊断事件变化

| 事件 | 变化 |
|---|---|
| `viewer_system_bar_owner_changed` | `owner` 从 `VIEWER/VIEWER_HOLD` 改为 8 个阶段名；`heldProgress` 语义改为「本次返回的松手进度」 |
| `viewer_system_bar_return_completed` | `owner=PAGE_HANDOFF` |
| `viewer_return_scrim_armed` | 新增 `barOwner` |
| `ViewerEntry:*` 全部事件 | 新增 `msSinceEntryRequest` |
| `ViewerReturn:grid_target_promoted` | 新增，记录被提升的目标 index/version |

未新增任何会改变系统返回行为的消费型回调；系统拥有的普通跨 Activity 返回**不可观测**，
本轮不做逐帧采集（官方指南要求用 `PRIORITY_SYSTEM_NAVIGATION_OBSERVER` 之类只观察的
通道，而这里连观察都不需要）。

## 6. 自动化测试

见 §9 的实测结果。新增/更新：

- `ViewerSystemBarChoreographyTest`（纯 JVM，9 例）：阶段、松手帧保持、settling 单调、
  取消恢复、终态至多一次、旧 token 拒绝、fade 兜底、手势取消。
- `ViewerSystemBarOwnershipTest`（Robolectric，sdk 34/36/35）：入口混合、提交后连续、
  teardown 不覆盖、取消恢复、**页级写入不能重绘 Viewer 窗口**、API 35 导航 underlay。
- `SystemBarIconContrastTest`：同色同结论、决定性变化立即、单调 blend 至多一次翻转、
  模糊带保持 48 ms、`adopt` 继承、`reset`。
- `PredictiveBackCloseAnimationTest` / `FolderBackCloseAnimationTest`：时钟、曲线、
  端点、单调性、无长尾、`96dp` 纯平移、两个样式互不串扰。

映射到任务书测试矩阵：

| 组 | 覆盖情况 |
|---|---|
| A01–A12 | 全部由 `ViewerSystemBarChoreographyTest` + `ViewerSystemBarOwnershipTest` 覆盖；A12 为 API 35/36 underlay 用例 |
| B01–B05、B07–B09、B11–B12 | `PredictiveBackCloseAnimationTest`、`PredictiveBackSupportTest`、`PredictiveBackManifestTest`、`FolderBackCloseAnimationTest` |
| B06、B10 | 由既有 `PageEntryMotionTest`、`PredictiveBackSupportTest`（回调优先级）覆盖 |
| C01–C20 | 既有 `PhotoViewerInitializationTest`、`PhotoTransitionCompletionGateTest`、`ViewerReturnVisualClockTest`、`Phase15ViewerRegressionTest` 等继续通过；本轮新增返回目标提升没有独立 UI 用例（见 §8） |
| D 组 | 既有 Phase 1 回归测试全部保留并通过 |

**B 组的真实父页、左右边缘方向、系统合成，以及 C 组多数视觉项，仍只能真机确认。**

## 7. API 兼容性

| API | 状态栏 | 导航栏 | 图标 |
|---|---|---|---|
| < 34 | `statusBarColor` | `navigationBarColor` | `WindowInsetsControllerCompat` |
| 34 | 同上；返回动画由平台 Shell 驱动 | 同上 | 同上 |
| 35+ | underlay 视图（状态栏） | underlay 视图（导航栏） | 同上；`statusBarColor/navigationBarColor` 不再写 |

`< 34` 的普通页面返回仍走 `overridePendingTransition(oxygen_classic_page_close_*)`，
资源未改；`FolderBackCloseAnimationTest.legacyPlatformsKeepTheExistingPageAnimation`
断言这一点。

## 8. 未完成 / 需真机验证

1. **动画观感**：240/220 ms 与两条新曲线是「资源实算 + 目标区间」的结论，最终是否
   自然必须真机确认（尤其右侧边缘返回与向下拖拽）。
2. **系统栏在合成层的表现**：应用侧只能保证「单一写者 + 连续进度 + 终态等于页色」；
   SystemUI 在两窗口叠加期间按哪个窗口的 appearance 绘制图标，无法在宿主验证。
3. **Viewer 进入的 136–452 ms**：本轮只补了可归因的时间线，没有改放行条件。
   若下一轮要压到 150–200 ms，需要真机配合确认「低分辨率代理 + 后台高分辨率 handoff」
   不会闪一帧。
4. **返回目标提升的实际收益**：只有在真机上、且目标请求确实处于「已排队未启动」时
   才生效；`promoteQueuedThumbnail` 没有独立 UI 用例（需要真实 RecyclerView + Picasso
   队列），目前只由调用点的诊断事件 `grid_target_promoted` 观察。
5. **本次未触碰**：媒体扫描、缓存、底栏导航、数据库、首屏有限候选机制、
   Viewer 转场协调器的整体结构。
