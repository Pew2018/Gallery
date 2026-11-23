# 13 · 文件夹返回主页：前景退场精修与封面补绘修复

> 审计基线：开始执行前重新 `git fetch` 确认的 `perf/home-gallery-sorting` 远程
> HEAD `2adedaa94fcadd827dac9756da58eddd07df6e68`（与任务给出的基线一致，分支上没有
> 其他 Agent 的新提交）。
> 平台依据：AOSP `android16-release`（`platform/frameworks/base`）中
> `CustomCrossActivityBackAnimation`、`DefaultCrossActivityBackAnimation`、
> `BackNavigationController.isCustomizeExitAnimation`、`AnimationSet#getTransformationAt`
> 的行为，与 `12-ordinary-page-back-post-commit.md` 记录的一致，本轮未改动平台契约。
> 本文只把能由源码 / 资源 / 自动化测试证明的结论写成「已确认」；需要真机合成的项目
> 一律标注为「待真机验收」。

## 0. 证据可得性（先行声明）

任务描述引用的 Pixel 8 Pro 录屏与诊断日志在本机仍**不存在**（工作区、`~/Desktop`、
`~/Downloads`、`~/Documents`、`~/Movies` 均无）。因此：

- 本轮所有百分比、位移、时长都是**把真实资源按 Shell 的求值路径
  （`Animation#getTransformationAt`）算出来的数**，不是录像目测；
- 首帧手感、圆角观感、系统栏跟随、左右边缘差异的观感仍为**待真机验收**；
- P0-B 的「事件时间线」是**代码级 + Robolectric 视图状态**时间线，不是设备日志时间线。
  为了让真机可复核，本轮在封面绑定处加了三个**诊断开关下才输出**的轻量事件
  （见 §3.5），开日志后能在 P8P 上直接看到同一时间线。

## 1. 结论先行

| 项 | 结论 | 等级 |
|---|---|---|
| 文件夹返回的动画分支 | 仍是 Android 16 的 `CustomCrossActivityBackAnimation`（自定义 CLOSE），未改分支选择 | 已确认（源码） |
| 手势期间几何 | 平台原生：关闭页缩放 + 圆角 + 边缘方向偏移，父页全屏不动 | 已确认（未改动） |
| 「前景消失较突然」的机制根因 | 共用 CLOSE-exit 的 alpha 在 **26%（117 ms）** 归零，此时只走完 **25.2 dp / 96 dp（26%）** 位移 ⇒ 前景在**原地**消散，而不是「离开」 | 已确认（资源实算） |
| 同一根因的第二半 | 平台上父页在 450 ms 内持续运动（0.9→1、−96 dp→0），我们的分支父页静止，剩下的 74% 时钟里没有任何可看的东西 | 已确认（AOSP + 资源） |
| 修复方式 | **文件夹专用** CLOSE-exit（`oxygen_classic_folder_back_exit` + 两条专用插值器），alpha 与位移**成对**重定时；共用 `OxygenClassicPageBackWindow` 与设置页资源**一个字都没改** | 已实现 + 已测 |
| 26% vs 32–38% | 采用 **35%**：35% 处位移已到 78%，再晚会把两张不同网格的叠加拖成文字重影；26% 太早（位移 26%） | 已确认（实算对照） |
| 主页封面「先占位色再出现」的根因 | 不是重复扫描；是 `DirectoryThumbnailBinder.bind` 在换 key 的重绑时**无条件**重画占位背景并重开 Glide 请求，而 `loadImageBase` 的 `RequestOptions.placeholder` 是**全透明**色块 ⇒ 至少一帧露出背景 | 已确认（源码 + 测试） |
| 修复方式 | 同一路径已有帧时：不重画占位、不清 Drawable，并把**当前帧**作为这次请求的 placeholder | 已实现 + 已测 |
| 工具栏返回 | 与系统返回同一对资源、同一时钟；14+ 不调用 `overridePendingTransition`，无二次动画 | 已确认（未改动路径） |
| Viewer / 设置 / 底栏 / 自定义配置 | 未修改 | 已确认（diff 清单） |

## 2. P0-A：文件夹前景退场

### 2.1 现网数（真实资源，Shell 求值路径）

`oxygen_classic_page_back_exit.xml`（450 ms，96 dp，`fast_out_slow_in` +
`oxygen_classic_page_back_alpha`）：

| progress | alpha | 位移 |
|---|---|---|
| 0.02 | 0.915 | 0.2 dp |
| 0.05 | 0.782 | 0.6 dp |
| 0.10 | 0.552 | 2.5 dp |
| 0.20 | 0.135 | 12.9 dp |
| **0.26** | **0.000** | **25.2 dp（26%）** |
| 0.35 | 0.000 | 48.0 dp |
| 1.00 | 0.000 | 96.0 dp |

两件事同时成立，缺一不能解释现象：

1. 焦点在 117 ms 就消失（`0.26 × 450`），但**只走了 26% 的位移**——读者看到的是
   「在原位淡掉」，不是「滑走」；
2. 之后 333 ms 里窗口仍在，只是 alpha=0，观感上等于动画在 117 ms 就结束了。

这也解释了为什么「窗口缩小/圆角/父页预览」都正常：那些是**手势期间**由平台几何给出的，
提交后平台冻结 `currentClosingRect`，我们自己给的这对资源是唯一的后续运动来源。

### 2.2 为什么不能直接照抄平台参考

`DefaultCrossActivityBackAnimation` 的 alpha = `max(1 - progress * 5, 0)`，20% 淡完、
450 ms 时钟——这在**它自己的分支**里是对的，因为它的父页在整个 450 ms 里都在动
（0.9→1.0、−96 dp→0）。我们的自定义分支为了保住「真实父页全屏可见」把父页钉死，
照抄这组时间就会退化为 §2.1 的现象。所以参数必须按**本分支**的运动所有权重新配。

### 2.3 26% / 34% / 35% 的对照与选择

| 候选 | alpha 归零处 | 归零时位移 | 问题 |
|---|---|---|---|
| 现状 26% | 117 ms | 25.2 dp（26%） | 原地消散 |
| 32–38% 中值 34%，位移仍用共用曲线 | 153 ms | ≈46 dp（48%） | 只改善一半；仍在中途消失 |
| **35% + 配合位移曲线（采用）** | 157 ms | **74.9 dp（78%）** | 淡出结束时页面已明显离开 |

判定依据不是「35 落在建议区间」，而是**顺序关系**：alpha 归零必须发生在位移基本走完之后，
同时不能晚到让两张不同网格长时间叠印。测试把这两条写成两个断言
（位移 ≥ 70%、淡出结束 ≤ 40%），而不是写死一个百分比。

### 2.4 新增资源

| 文件 | 作用 |
|---|---|
| `res/anim/oxygen_classic_folder_back_exit.xml` | 文件夹 CLOSE-exit：alpha + 96 dp 平移，两子动画 duration 都是 450 ms |
| `res/interpolator/oxygen_classic_folder_back_alpha.xml` | 淡出曲线：前 ~2% 几乎不降（保住松手帧），35% 归零，尾段平坦 |
| `res/interpolator/oxygen_classic_folder_back_slide.xml` | 位移曲线：起步慢（9 ms 内 1 dp），中段承担可见离开，长收尾停在 450 ms |
| `values/oxygenos_classic.xml` · `OxygenClassicFolderBackWindow` | 文件夹专用窗口样式 |
| `helpers/FolderBackPage.kt` | 页面标记接口，让「哪一页用文件夹曲线」成为页面属性而不是类判断 |

复用的 token（**没有**新增时长/距离来源，避免出现第二个时钟）：
`oxygen_classic_page_back_duration_ms = 450`、
`oxygen_classic_page_back_slide_distance = 96 dp`、
CLOSE-enter 仍指向共用的 `oxygen_classic_page_back_enter`（恒等，手势期间会被平台以
`progress * 0.2` 应用，任何位移都会破坏真实父页预览）。

### 2.5 为什么是「文件夹专用样式」而不是改共用资源

`OxygenClassicPageBackWindow` 同时服务设置、主题选择、自定义照片配置等页面，这些页面的
返回已经被验收为正确。改共用 CLOSE-exit 等于把它们一起重定时，违反「不破坏已正常工作的
动画」。因此只新增一个同样只声明三项的样式，由 `OxygenClassicActivityMotion.pageBackStyle()`
按 `FolderBackPage.usesFolderBackMotion` 选择：

- `MediaActivity` 且 `mPath != PhotoProfileMediaLoader.PATH` ⇒ 文件夹样式；
- 自定义照片配置网格虽然复用 `MediaActivity`，但它是底栏分区（走 root 交接，本来也不会
  进入这个分支），显式返回 `false`，保持共用样式；
- 其余页面 ⇒ 共用样式，资源与数值不变。

`activityOpenEnterAnimation` 在新样式里**必须重声明**：一旦声明了 `windowAnimations`，
平台只从该样式读取 OPEN 资源，缺失即解析为 0，文件夹打开会变成硬切
（`01-open-branch-audit-and-refinement.md` 记录的同一个坑）。

### 2.6 未改动的东西

- 手势识别、`onBackStarted/Progressed/Cancelled/Pressed`、`BackProgressAnimator` 回弹：未动；
- 窗口圆角、缩放、边缘方向偏移、父页全屏基础矩形：仍由平台负责；
- 未引入任何 View 级缩放/位移动画去「模拟」预测性返回；
- 未改 `applyClose()`：14+ 依旧直接返回，工具栏返回与系统返回共用同一对资源。

## 3. P0-B：主页封面补绘

### 3.1 代码级时间线（先证明原因）

返回主页时的实际调用链：

1. `MediaActivity.finish()` → `super.finish()` → `OxygenClassicActivityMotion.applyClose()`
   （14+ 直接 return，平台动画接管）；
2. `MainActivity.onResume()` → `tryLoadGallery()` → `getDirectories()`；
3. `getCachedDirectories(...)` 先发一版缓存，随后 `gotDirectories(...)` 扫完再发一版；
4. 每版都走 `setupAdapter()` → `DirectoryAdapter.updateDirs()`；
   - 列表内容完全一致 ⇒ `DirectoryPresentation.sameContents` 命中，`album_identical_skip`，
     **不重绑**；
   - 有任何差异（统计、封面路径、计数…）⇒ `DiffUtil` 只对变化行 `notifyItemChanged`；
5. 变化行重绑 ⇒ `DirectoryAdapter.setupView()` → `DirectoryThumbnailBinder.bind()`；
6. `bind()` 的 early-return 要求 `request_key == loaded_key == 当前 key`。key 里含
   `path + signature(folder.modified) + crop + animateGifs + horizontalScroll + corners`，
   只要其中一项变化，守卫就落空；
7. 落空后旧实现无条件执行 `OxygenThumbnailPlaceholder.apply()`（灰底）+ 重新请求；
8. `Context.loadImageBase()` 的 `RequestOptions.placeholder` 是
   `ColorDrawable(TRANSPARENT)`，而 Glide 在 `SingleRequest.begin()` 里**同步**把它设给
   ImageView ⇒ 灰底至少露出一帧，解码完成后缩略图「重新出现」。

所以现象不是「重复扫描导致内容丢失」，而是**「没有媒体变化的重绑也会先清帧再重载」**。
`DirectoryAdapter` 的 stable id、`sameContents` 短路、`FavoritesScrollRestorer` 都没问题，
不需要改。

### 3.2 最小修复

`DirectoryThumbnailBinder.bind()`：

- 同一路径已有可见帧时：**不重画**占位背景、**不清** Drawable；
- 把当前帧作为本次请求的 placeholder（新增可选参数
  `loadImage(placeholderDrawable=…)` → `loadImageBase` → `RequestOptions.placeholder`），
  这样即使 key 变了（例如 folder `modified` 更新）也不会闪一帧灰；
- 路径变了（回收复用）：行为完全不变——占位 + 清帧 + 重载，这是正确行为；
- `loaded_key` 守卫命中时直接返回，连 placeholder 都不碰。

### 3.3 与「目录身份 / 滚动位置」的关系

- 目录身份：`getItemId()` 仍由 path 派生，`setHasStableIds(true)` 未动；
- 列表发布：`sameContents` 短路仍在，未加任何延迟或防抖；
- 滚动位置：`FavoritesScrollRestorer` / `GalleryNavigation.restoreListState` 未动；
- 不阻塞返回：所有改动都是 `bind()` 内部的纯 view 状态决策，没有新增等待、没有固定延时。

### 3.4 测试如何证明

`DirectoryCoverReturnStabilityTest`（5 项）把 §3.1 的四个分叉写成断言：

| 场景 | 断言 |
|---|---|
| 首次绑定（无帧） | 画占位背景 |
| 同路径 + 已加载 | 什么都不做（背景仍为 null，帧不变） |
| 同路径 + key 变化 | 帧保持是原来那个 Drawable 实例；**占位背景不得被重画** |
| 复用给另一路径 | 旧帧被清掉、占位背景重画（行为不变） |
| 空封面路径 | 旧帧被替换为共用文件夹图标，`tag` 清空 |

### 3.5 真机可复核的时间线

`DirectoryThumbnailBinder` 在诊断开关打开时输出三个事件（关闭时仅一次 `isEnabled()` 判断）：

- `CoverBinding/cover_bind_skip`：守卫命中，没有重绑；
- `CoverBinding/cover_hold_frame`：保留当前帧重载（修复后的正常路径）；
- `CoverBinding/cover_placeholder`：无帧可保，画了占位（首次绑定 / 回收复用）。

在 P8P 上开诊断后从文件夹返回，日志里若出现 `cover_hold_frame`，说明这次重载没有闪帧；
若仍出现 `cover_placeholder` 且伴随可见灰闪，则说明该行确实是「另一路径」或有其它清帧点，
可据此定位（本次源码审计未发现第三条清帧路径）。

## 4. 改动清单

```
app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt          （实现 FolderBackPage）
app/src/main/kotlin/org/fossify/gallery/helpers/FolderBackPage.kt            （新增）
app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicActivityMotion.kt（样式选择）
app/src/main/kotlin/org/fossify/gallery/helpers/DirectoryThumbnailBinder.kt  （保留帧 + 时间线事件）
app/src/main/kotlin/org/fossify/gallery/extensions/Context.kt                （可选 placeholder 透传）
app/src/main/res/anim/oxygen_classic_folder_back_exit.xml                    （新增）
app/src/main/res/interpolator/oxygen_classic_folder_back_alpha.xml           （新增）
app/src/main/res/interpolator/oxygen_classic_folder_back_slide.xml           （新增）
app/src/main/res/values/oxygenos_classic.xml                                 （新增文件夹样式）
app/src/test/kotlin/org/fossify/gallery/helpers/FolderBackCloseAnimationTest.kt        （新增 11 项）
app/src/test/kotlin/org/fossify/gallery/helpers/DirectoryCoverReturnStabilityTest.kt   （新增 5 项）
```

未触碰：`ViewPagerActivity`、`ViewerBackGesture`、`ViewerReturnProgress`、
`ViewerReturnVisualClock`、`PhotoViewerTransition` 参数、`MediaTransitionSessionCoordinator`、
`MediaTransitionGestureController`、`GalleryBottomBar`、`SettingsActivity`、
`ClassicSettingsPageTransition`、`GalleryViewerTransitionTheme`、`OxygenClassicMotion` 常量、
`oxygen_classic_page_back_enter.xml`、`oxygen_classic_page_back_exit.xml`、
`oxygen_classic_page_back_alpha.xml`。

## 5. 自动化验证

- 全量 JVM/Robolectric：`:app:testFossDebugUnitTest` — **190 个测试类 / 1376 项 / 0 失败**
  （JDK 25，Robolectric SDK 36；API 36 需要 JDK 21+，本机默认 JDK 17 会在
  `DefaultSdkProvider` 处直接拒绝，这是既有环境要求，不是本轮改动引入）。
- 新增 `FolderBackCloseAnimationTest`（11 项）：文件夹页选文件夹样式、非文件夹页仍选共用样式、
  共用样式仍指向共用资源、文件夹样式仍带 OPEN 资源、时钟 450 ms 且两子动画等长、
  松手帧不跳变（2% 时 alpha > 0.98）、淡出结束时位移 ≥ 70%、淡出结束 ≤ 40%、
  平移单调且为纯 +X（无缩放/斜切）、两条插值器路径合法单调。
- 新增 `DirectoryCoverReturnStabilityTest`（5 项）：见 §3.4。
- **负向对照（证明测试不是空转）**：
  - 把文件夹样式的 CLOSE-exit 指回共用资源 ⇒
    `folderStyleStillSelectsTheCustomPlatformBranch` 失败；
  - 把文件夹 CLOSE-exit 的两条插值器换回共用曲线 ⇒
    `folderExitHoldsTheReleaseFrame` 与
    `folderExitClearsTheForegroundOnlyAfterItHasVisiblyDeparted` 同时失败；
  - 把 `bind()` 的占位判断改回无条件执行 ⇒
    `rebindOfTheSameCoverKeepsTheFrameOnScreen` 失败。

## 6. 仍需 Pixel 8 Pro 真机验收

1. 提交后 157 ms 淡出 + 75 dp 位移的手感是否明显比原来「更连续」；
2. 左右边缘滑动的首帧贴合度、以及取消回弹后是否完全复原；
3. 圆角半径、scrim 明暗在日/夜主题下的实际观感；
4. 工具栏返回与手势返回是否「看起来是同一个动画」；
5. 状态栏/导航栏是否随窗口连续变化；
6. 主页封面在返回瞬间是否还有灰闪（配合 §3.5 的 `CoverBinding` 事件）；
7. `.nomedia`、受保护目录、深色模式、RTL 下的观感（RTL 侧文件夹曲线与共用曲线走同一个
   96 dp 平台 token，未新增 `values-ldrtl` 覆盖，预期行为一致，但需真机确认）；
8. 关闭系统动画后的状态清理（窗口是否正常移除、无残留 alpha）。

以上任何一项在拿到真机录屏前**不得**标注为已通过 Pixel 8 Pro 验收。
