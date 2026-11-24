# Phase 2.1 · Viewer 资源就绪、系统栏端点与返回动画最终修复

> 审计基线：`git fetch origin --prune` 后分支 `perf/home-gallery-sorting` HEAD =
> `1cdb797caf5813cf91d515fa74d5412dc1b16623`，与任务书给出的最新 HEAD 一致，没有其他
> Agent 的新提交。本地分支从 `18c60a8c2` 快进到该 HEAD，未重置到历史提交、未新建分支、
> 未创建 PR。任务书中的 `23719992e0954fee…` 是本次真机 APK 的源码身份，相对最终 HEAD
> 只差一份文档提交（`docs/animation/phase-2-navigation-viewer-motion-refinement.md`）。
>
> 真机证据：`Gallery-Diagnostics-20261124-230804.txt`（4226 行）与
> `b6dd6fdfdb50ce5b65c2e86f4c3effa4.mp4` 均已实际读取。日志头声明
> `Source: 23719992e095`。本文所有引用数值都来自该日志或对本仓库真实资源的采样；
> 录像的逐帧观感、系统窗口合成与物理屏幕呈现**仍待真机验收**。

## 1. 结论摘要

| 专项 | 状态 | 关键证据 |
|---|---|---|
| P0-A 系统栏初始端点 | **已修复** | 日志 6/6 在 `progress=0.0` 记录 `iconAppearance=DARK`，父页面声明 `lightStatusBarIcons=true`；新测试复现并断言端点继承 |
| P0-B 返回目标资源 | **已修复（已解码像素被驱逐这一类）** | 慢样本 13 `cache=LOCAL`，REQUEST_STARTED→RESOURCE_READY 676 ms；改为强引用保留网格自身的 drawable，并由 bounded reserve 交还重绑定 holder |
| P1-A Viewer 进入等待 | **已实施（低分辨率同几何 renderer 预览）** | 请求 8：`first_pre_draw` +134 ms，`renderer_signal` +406 ms，`animation_started` +429 ms；现在预览帧先满足原有 renderer-ready 门禁，完整解码继续在后台替换 |
| P1-B 预测性返回曲线 | **已重定时** | 通过 `AnimationSet#getTransformationAt()` 采样；普通页 240→210 ms，文件夹 220→200 ms 且 alpha 提前归零 |
| P1-C 手势连续性 | **审计完成 + 新增端到端测试** | 提交帧继承松手进度、取消恢复、旧 token 拒绝、无代理 fallback 的既有实现保持不变；新增 ownership→系统栏的贯通测试 |
| 真机视觉验收 | **未做** | 宿主机测试不能替代 Android 16 系统合成、左右边缘手势与物理屏幕呈现 |

## 2. P0-A：进入端点极性错误的根因与修复

### 2.1 日志证据

`viewer_status_appearance_changed` 在 6 次进入的第一帧完全一致：

```
23:06:21.030 progress=0.0           iconAppearance=DARK  navigationIconAppearance=DARK backgroundColor=-2996452
23:06:21.454 progress=0.30050552    iconAppearance=LIGHT navigationIconAppearance=DARK
...
23:06:21.544 progress=1.0           iconAppearance=LIGHT navigationIconAppearance=LIGHT
```

`-2996452 = 0xFFD2471C`，是工具栏色。按 WCAG 2.x：

- `contrast(BLACK, 0xFFD2471C) ≈ 4.64`
- `contrast(WHITE, 0xFFD2471C) ≈ 4.52`
- 差值 0.12，远小于 `IconPolarityLatch.DECISIVE_MARGIN = 3.0`，也小于
  `SystemBarIconContrast.SWITCH_MARGIN = 1.0`。

`OxygenThemeColors.resolve()` 明确为这条色带配 `lightStatusBarIcons = true`
（`toolbarForeground = WHITE`，工具栏背景被刻意压暗到白字通过 4.5:1）。因此：

> **根因不是多次写入，也不是滞回失效，而是「端点颜色由对比度现场决定」这一规则本身
> 在一个近乎平局的颜色上把页色第一帧判成了黑图标。**

进入 0.30 时色带已足够暗，白色图标决定性胜出，于是出现一次 `DARK→LIGHT`。
Phase 2 修掉的 `LIGHT→DARK→LIGHT` 双重反转不再出现，但开屏瞬间的这一次反转仍在。

### 2.2 修复

- `SystemBarIconContrast.IconPolarityLatch.align(polarity, surface)`：端点帧强制当前极性、
  丢弃待定候选、记录对应色值；同色后续帧直接从记忆返回。
- `OxygenSystemBars.applyViewer()`：
  - `progress <= PAGE_ENDPOINT_EPSILON (0.001)` 为真父页面端点，状态栏极性取
    `!colors.lightStatusBarIcons`，导航栏取 `colors.darkNavigationBarIcons`；
  - progress=1 仍由实际绘制的 viewer 表面对比度决定（近似黑，白色图标，结论不受影响）；
  - 中间帧继续走原有 hysteresis，**不再**以 `lastSurface != surface` 为门禁跳过
    latch：同一颜色重复帧也必须给待定候选一个解析机会，只是不会重写窗口。
- `applyViewer` 的 status / navigation 可见性拆成两个参数（默认相同），
  `BaseViewerActivity` 增加 `viewerNavigationSurfaceVisible` 覆盖点；
  `viewer_status_appearance_changed` 增加 `navigationBarVisible`、
  `navigationUnderlayVisibility`、`pageStatusIcons`、`pageNavigationIcons` 字段。
  Gallery 的沉浸模式/幻灯播放仍同时切换两栏，因此**行为未变**，但 API 不再把两栏
  的可见性绑死。
- 保留 `ViewerSystemBarChoreography` 的 8 个 phase、token 与
  `returnBarProgress()` 机制，没有回退到全程冻结方案。

### 2.3 效果（确定性测试）

- `OxygenSystemBarsTest.bothPageEndpointsInheritTheDeclaredPolarity`：进入 0 与返回 0
  都等于页面声明，progress=1 等于 viewer 表面。
- `SystemBarIconContrastTest.theToolbarToViewerBlendKeepsTheDeclaredIconsForTheWholeEntry`：
  按真机色值遍历 0→1 的 60 帧，`align` 后翻转次数 = **0**；同一测试同时断言若不
  `align`，该颜色上 `useDarkIcons(..., previous=null) == true`，即旧代码必然翻一次。
- `ViewerSystemBarChoreographyTest` 的 release-frame、单调性、旧 token 测试全部保留；
  新增 `aCommittedReturnReachesThePagePolarityThroughTheOwnershipClock` 贯通测试。

## 3. P0-B：返回目标资源的真实耗时链路与有界准备

### 3.1 日志拆分（请求 13，视频）

```
23:06:48.783 viewer_requested
23:06:48.808 grid_position_requested index=2206
23:06:48.816 thumbnail_request REQUEST_CREATED bindPriority=LOW
23:06:48.819 thumbnail_request REQUEST_STARTED priority=HIGH
23:06:48.829 grid_target readiness=pixels_unavailable boundMatches=true drawable=true
23:06:49.495 thumbnail_request RESOURCE_READY cache=LOCAL   (+676 ms)
23:06:49.500 DRAWABLE_BOUND
23:06:49.548 viewer_animator_started
```

`cache=LOCAL` 是关键：请求**不是**在 Glide 内存排队，而是真正从本地文件重新解码。
请求创建→派发只有 3 ms，`HIGH` 提升已经生效；此前把慢样本归因于「请求尚未派发」
的解释与这条日志不符。进入时该媒体的网格缩略图已 `loaded=true`，但播放器与 Viewer
自己的解码会把 670×670 的缩略图从 Glide 内存缓存中挤出去；返回时 holder 又被重绑定，
于是只剩一次 676 ms 的本地解码。

### 3.2 实现

新增 `helpers/ViewerReturnPixelReserve.kt`：

- 以**网格自己的 requestKey**（path、signature、type、crop、corners、动画/滚动标志）
  为键保存同一份 drawable；值相等即可复用，文件版本或裁剪变化必然 miss；
- 容量硬上限 3（当前项 + 前后各一项的量级），LRU 淘汰；命中即移除，不会重复交给
  两个 holder；
- 额外校验 drawable 的固有尺寸与目标 holder 的测量尺寸（±1 px），列数/布局变化
  直接 miss 而不是交出错误分辨率的位图；
- `endViewerReturn()`、Activity 销毁、下一次 Viewer 启动都会 `clear()`，不会跨会话残留；
  没有新的无限增长 Bitmap 缓存。

接线位置：

- `MediaActivity.launchPhotoViewerWithSharedElement()`：在 `source_validated`
  之后把该 drawable 记为 reserve；
- `onThumbnailLoaded`：Viewer 期间若该路径被重新解码，刷新 reserve；
- `readViewerReturnTarget()`：`pixels_unavailable` 前尝试把 reserve 的**同一 drawable 实例**
  绑到当前 holder，并写回 `thumbnail_loaded_key`；所有原有身份、可见面积、alpha、
  transitionAlpha、几何检查在其后照常执行；
- 记录 `grid_target_reserved_pixels`（含索引、版本、尺寸与 reserve 大小）用于真机验证。

在 `cache=LOCAL` 这一类失败上，reserve 把 676 ms 的返回等待变成一帧绑绘；
对「从未解码过」的目标，仍走原请求 + HIGH 提升 + 安全 fallback，**不伪造 readiness、
不延长超时**。Glide 内部排队/解码子阶段在现有公共 API 下无法可靠拆分，故不做编造。

### 3.3 测试

`ViewerReturnPixelReserveTest`（7 项）：身份一致命中、签名/裁剪不同 miss、尺寸不符
拒绝、容量硬界、单项失效、整会话释放、非法身份不入池。

## 4. P1-A：不删除 renderer-ready 门禁的进入加速

### 4.1 时间线（请求 8）

```
entry_requested               +0
static_source_geometry        +2    来源几何已具备
viewer_created                +65
first_pre_draw                +134  窗口与 pager 已布局
viewer_first_renderer_signal  +406  完整尺寸解码结束
entry_release_requested       +407
shared_animation_started      +429
shared_transition_ended       +579
entry_completed               +585
```

窗口准备只占 134 ms；真正等待的是同一张图的完整解码（约 270 ms）。

### 4.2 方案

`helpers/ViewerEntryPreviewPolicy.kt` + `PhotoFragment.loadWithGlide()`：

- 只对**进入的那一张**、且 `StaticPhotoSource` 的文件版本/签名已与网格核验通过的
  静态图片，在原 Glide 请求上追加 `.thumbnail(0.5f)`（线性尺寸一半，解码面积为 1/4）；
- 预览与最终图使用**同一个 model、signature、fit-center 变换和磁盘策略**，
  因此几何由真实 renderer 推导，不需要猜测目标框；
- renderer-ready 门禁、operation token、generation、proxy/renderer lease、超时与
  fallback 全部保留；`reportImageRendered()` 原有去重仍只发布一次 `imageRendered`；
- 旋转、subsampling、相邻页、GIF/APNG/AVIF/SVG/视频、content URI、未核验来源一律
  走原单阶段解码；
- 预览与最终解码的取整差由 `PhotoViewGeometry.nearForHandoff()`（2 px）吸收，
  真实几何变化仍会被拒绝；
- 记录 `viewer_entry_preview_strategy` 性能事件，供下一轮真机把策略与
  `media_ready msSinceEntryRequest` 对齐。

这是有界的方案，不是「代理提前进入 + 长代理持有」的重构：后者需要让代理在共享动画
结束后继续存活到 renderer 就绪（请求 8 中约 145 ms 的空隙），并在任何路径下保证单一
终态；当前架构没有现成的代理租约支持，盲改会威胁已经 6/6 通过的共享进入。

### 4.3 待真机验证

- `media_ready` 与 `shared_animation_started` 相对点击的毫秒数；
- 代理端点与捕获帧是否仍完全一致（新增的 2 px 容差是否足够）；
- 预览→完整图在动画过程中是否可被察觉；若出现可见的清晰度跳变，可只调
  `PREVIEW_SIZE_MULTIPLIER` 或关闭策略，不需要改动几何/租约代码。

## 5. P1-B：预测性返回的平台依据与曲线修改

### 5.1 已重新核对（不是沿用旧知识）

- AOSP `main` 的 `DefaultCrossActivityBackAnimation.kt`：
  `enteringStartOffset` 取自 `cross_activity_back_entering_start_offset`（注释 96dp）；
  `closingAlpha = max(1f - linearProgress * 5, 0f)`，即 post-commit 线性进度 20% 即全透明；
  `POST_COMMIT_DURATION = 450L`；提交后 `targetClosingRect` =
  `backAnimRect` 再按 `currentClosingRect.left + enteringStartOffset` 平移。
- `Pew2018/MaterialNG` `master`（实际读取）：`m3e_activity_exit.xml` 只有 180 ms、
  `alpha 1→0.96`、`x 0→-1%p`，没有自定义 cross-activity back 动画，预测性返回基本交由
  平台默认实现。它的观感更自然，代价是没有 OxygenOS 7.1.1 的退场个性。
- 结论：本仓库的 `OxygenClassicPageBackWindow` 自定义 CLOSE 分支是为保留 Classic 视觉而
  主动选择的，不是平台缺陷；因此继续精修现有资源，而不是全盘切回默认动画。
  `enter` 资源保持 identity（alpha 1、无几何），以保证真实父页面就是被展示的内容。

### 5.2 通过 `AnimationSet#getTransformationAt()` 采样得到的前后参数

测试方法：Robolectric 中运行 API 36，反射调用隐藏的 `getTransformationAt`（运行时真实
存在），并用公开的时间驱动 `getTransformation(elapsed)` 交叉验证（两条路径误差 < 0.02）。

| 普通页面，210 ms（原 240 ms） | 0.00 | 0.10 | 0.20 | 0.30 | 0.40 | 0.50 | 0.60 | 0.70 | 0.80 | 0.95 | 1.00 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| alpha（本次） | 1.000 | 0.733 | 0.484 | 0.280 | 0.131 | 0.032 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 |
| alpha（Phase 2，240 ms） | 1.000 | 0.733 | 0.484 | 0.280 | 0.131 | 0.032 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 |
| 绝对时刻（ms） | 0 | 21 | 42 | 63 | 84 | 105 | 126 | 147 | 168 | 199 | 210 |

普通页曲线形状不变，只把时钟 240→210 ms：**完全消失时刻 144→126 ms**，
前景全透明的无效尾部 **96→84 ms**（Phase 1 是 450 ms 时钟下 117 ms 淡出、333 ms 尾部）。

| 文件夹，200 ms（原 220 ms） | 0.00 | 0.10 | 0.20 | 0.30 | 0.40 | 0.50 | 0.60 | 0.70 | 0.80 | 0.95 | 1.00 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| alpha（本次） | 1.000 | 0.926 | 0.777 | 0.571 | 0.320 | 0.179 | 0.073 | 0.006 | 0.000 | 0.000 | 0.000 |
| alpha（Phase 2，220 ms） | 1.000 | 0.944 | 0.819 | 0.633 | 0.392 | 0.239 | 0.133 | 0.053 | 0.006 | 0.000 | 0.000 |
| 绝对时刻（ms） | 0 | 20 | 40 | 60 | 80 | 100 | 120 | 140 | 160 | 190 | 200 |

文件曲线同时把归零点从 82% 提前到约 72%：两个密集网格同时半透明的时间窗从约
180 ms 降到约 130 ms；最后一帧仍有位移，没有「前景静止后突然消失」。
`oxygen_classic_folder_back_alpha.xml` 的控制点已按新形状更新，并同步更新了资源内注释。

### 5.3 新增测试

`PredictiveBackCurveTest`（5 项）：首尾值、单调性、单帧跳变上限、淡出时刻与绝对时长、
enter 资源恒等、Shell 进度路径与时间路径一致、96dp 位移资源未变。

## 6. P1-C：手势连续性与特殊返回路径

对 `renderInteractiveDismissProgress()`、`renderSharedReturnLayers()`、
`renderSharedReturnProgress()`、`onSharedReturnTerminal()`、`requestViewerClose()` 逐条审计：

- 提交帧沿用 `interactiveDismissProgress` 与 `viewerSystemBarProgress`，
  `beginReturn(viewerSystemBarProgress)` 把松手进度作为 bar 的基准，没有恢复全屏再缩小；
- 取消走 `viewerBars.terminal(token, cancelled=true)` + `settleViewer()`，scrim/chrome
  保持手势最后一帧，不重写为 1；
- `onSharedReturnTerminal()` 只在时钟确认需要时补写终帧，`ViewerReturnVisualClock` 保证
  终帧最多一次；
- 无代理/无 animator 的 fallback 仍由 `startEntryFallback()` 与
  `entryMediaLoadFailed()` 处理，未放宽身份或几何校验。

本项没有发现需要改动生产代码的缺陷；新增的是 `OxygenSystemBarsTest` 中的
return→ownership→系统栏贯通测试，确保端点规则不破坏已验证的连续性。左右边缘方向、
真实父页面合成与下拖的逐帧观感仍需真机确认。

## 7. 性能与资源测量边界

- 进入：`ViewerEntryPreviewPolicy` 把第一次 renderer 交付从完整解码改为半尺寸预览，
  解码面积降到 1/4；实际毫秒收益必须由下一轮真机 `media_ready` 时间戳确认。
  宿主机没有 Android 16 系统合成，**不把模拟值写成 Pixel 8 Pro 结果**。
- 返回：reserve 命中时目标像素由一帧绑绘提供；未命中时仍为原解码成本，
  `grid_target_reserved_pixels` 与 `thumbnail_request` 的 `cache=` 字段可用于统计命中率。
- 资源上界：reserve 固定 3 项、命中即释放；预览只针对 1 张已进入的静态图片，
  没有新增常驻 Bitmap 缓存。

## 8. 自动化验证结果

实际执行（本机 JDK 21，CI 同版本；JDK 17 无法运行 Robolectric API 36）：

```
./gradlew :app:testFossDebugUnitTest
BUILD SUCCESSFUL in 3m 2s
208 suites / 1554 tests / 0 skipped / 0 failures / 0 errors
```

新增测试类：`PredictiveBackCurveTest`(5)、`ViewerEntryPreviewPolicyTest`(4)、
`ViewerReturnPixelReserveTest`(7)；扩充
`OxygenSystemBarsTest`(+7)、`SystemBarIconContrastTest`(+2)、
`StaticPhotoEntryGeometryTest`(+1)，并按新曲线更新 `FolderBackCloseAnimationTest`。
Phase 1/1.1/1.2/2 的既有测试全部保留并通过。

## 9. 未完成 / 需要下一轮真机验证

1. 6 次 Viewer 进入的第一帧是否全部为 `LIGHT`，且不再出现任何图标反转；
2. `viewer_entry_preview_strategy` 是否只在进入图片上出现一次，
   `media_ready` 是否显著前移（目标 150–200 ms 内开始共享动画），
   预览到完整图的清晰度跳变是否可接受；
3. 慢视频返回的 `grid_target_reserved_pixels` 是否命中、
   `viewer_requested→viewer_animator_started` 是否降到 100 ms 量级；
4. 文件夹退场的双网格重影是否随 130 ms 的半透明窗口明显减轻；
5. 真机的左右边缘方向、取消手势、toolbar/back 键一致性，以及物理屏幕呈现；
6. 普通 Camera 文件夹刷新（25 项预览→10970 项提交）按任务书要求**未实施**，
   仅保留记录，作为独立后续任务。

本文不把源码修改、单元测试通过或 APK 编译成功等同于真机视觉验收成功。
