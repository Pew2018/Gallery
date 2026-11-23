# 14 · 导航视觉稳定性：Photos 配置返回、文件夹退场与 Viewer 系统栏

> 审计基线：开始执行前重新 `git fetch origin --prune` 确认的
> `perf/home-gallery-sorting` 远程 HEAD 为
> `18c60a8c2a36250a88a97d2ecdaa1afb3edf1b4d`
> （与任务给出的参考 SHA 一致，分支上无其他 Agent 的并发提交）。
> 分支未新建、未合并、未强推，`main` 未改动。
>
> 本文只把能由**源码 / 资源 / 自动化测试**证明的结论写成「已确认」；需要 Pixel 8 Pro
> 真实窗口合成的观察一律标注为「待真机验收」。

## 0. 证据可得性（先行声明）

任务描述引用的两份原始文件在本机**不存在**：

| 文件 | 状态 |
|---|---|
| `0c14c376a12b06fc656bf5dc41c260cb.mp4` | 未在工作区 / `~/Desktop` / `~/Downloads` / `~/Documents` / `~/Movies` 找到 |
| `Gallery-Diagnostics-20261123-232941.txt` | 同上；本机仅有一份同版本、更早时间的 `Gallery-Diagnostics-20261123-112745.txt` |

因此本轮的处理方式是：

- **不声称重新观看过录屏**。问题 A / C 的画面描述直接引用任务中给出的前轮逐帧审计结论。
- 23:29 的日志时间线以任务描述中的记录为准；本机那份 11:27 的日志只用于**核对事件词汇与
  文本格式**（`module=PhotoGrid event=…`、`module=MediaPipeline event=…` 等确实存在，
  应用版本 1.14.1 (32)、Pixel 8 Pro、Android 16 / API 36 一致），不作为本轮缺陷的证据。
- 关于「用户实际安装的 APK commit」：诊断文件里没有 commit SHA，**不能**把本次远程 HEAD
  当成用户已安装的构建。本轮只保证「从 `18c60a8c2` 出发的源码与资源自洽并已构建」。
- 为让下一次录屏能直接判定问题 B 的图层来源，本轮在 Photos 子页面交接处新增了
  `secureWindow` / `adapterPresent` / `gridChildren` / `snapshotStage` 等**仅在用户开启
  诊断日志时**输出的字段（见 §2.5）。

## 1. 结论先行

| 项 | 结论 | 等级 |
|---|---|---|
| P0 根因（扫描生命周期） | 打开配置页时 `MediaActivity.onPause()` 无条件 `mediaLoadRequests.invalidate()` + `stopFetching("activity_pause")`，`photoChildPageOpen` 在该分支里**完全没有被使用** | 已确认（源码） |
| P0 根因（无内容可保留） | 请求被作废 ⇒ 加载器抛 `PhotoLoadInvalidatedException` ⇒ 既不 `PhotoProfileMediaCache.offer` 也不 `PhotoProfileReturnProbe.record`；日志里 `photo_grid_retained_for_child_page` 的 `adapterPresent=false / mediaCount=0` 正是「保留了空网格」 | 已确认（源码 + 日志字段） |
| P0 根因（返回冷启动） | `onResume()` 的 `sameAuthorizedGrid` 要求 `displayedMediaIsFinal`，而该次加载从未 COMPLETE ⇒ 走 `tryLoadGallery()` ⇒ 全新请求 36 冷扫 | 已确认（源码） |
| P0 修复 | 子页面覆盖期间**保留请求与 worker**，结果暂存不发布；返回时先校验 request / scope key / content revision / 能力，通过则一帧内提交最新已验证 stage | 已实现 + 已测 |
| 问题 B「黑」的图层归因 | **未能归因**。应用侧唯一能证明的是 25.534→27.791 约 2.26 s 的**内容不可用空档**；窗口背景在 `MediaActivity` 上已被设为主题页面色（`OxygenClassicActivityMotion.install` / `GalleryBottomBar.restoreRootWindow`），`FLAG_SECURE` 仅在 `photoToken() != null`（受保护源已解锁）时添加。深色主题下页面色为 `#212121`，空页面本身就读作「黑」 | 部分确认 / 图层待验 |
| P1-A 根因 | 共用 450 ms 时钟上前景 alpha 在 **35%（157 ms）** 归零，此后 **293 ms** 时钟里前景已不可见 → 观感是「先原地淡化重影，再突然切到主页」 | 已确认（资源实算） |
| P1-A 修复 | **文件夹专用** 300 ms 时钟（enter + exit 两侧同时 300 ms，否则 `max(close, enter)` 会把尾巴带回来），alpha 与位移在同一帧结束 | 已实现 + 已测 |
| P1-B 根因（写入者竞争） | 退出过程中有两个写入者：Viewer 在返回时钟上逐帧 `applyViewer`；返回目标页 `MediaActivity.onResume → updateMenuColors() → OxygenSystemBars.apply(this)` 在动画窗口下方先写一次。谁后写谁生效 | 已确认（源码） |
| P1-B 根因（终态回跳） | `onDestroy → returnLeases.releaseAll()` 的 CHROME 租约回调执行 `renderSharedViewerCanvas(1f)`，把已经交出的系统栏**写回 Viewer 外观**（近黑 + 浅色图标） | 已确认（源码调用链） |
| P1-B 根因（图标抖动） | `applyViewer` 每帧按混合色重算 `useDarkIcons`，判据落在对比度相等点附近；进度抖动即反复翻转 | 已确认（源码） |
| P1-B 修复 | 提交返回后**冻结**系统栏外观（单次写入）、终态回跳被冻结吸收、图标极性加 1.0 对比度滞回、取消返回时恢复 | 已实现 + 已测 |
| 设置页 / 主题页 / Viewer 动画时长 | 未改（`oxygen_classic_page_back_duration_ms` 仍为 450） | 已确认（测试断言） |
| 共享元素几何 / 下拖退出 / 返回目标匹配 | 未改 | 已确认（diff 清单） |

## 2. P0：照片配置管理页面返回黑闪

### 2.1 日志时间线（任务记录，按 requestId / generation 重新对齐）

| 时刻 | 事件 | 说明 |
|---|---|---|
| 23.836 | `first_page_state=LOADING` | 从底栏进入 Photos，根导航请求 35 已在跑，但**还没有任何一行** |
| 24.477 | request 35 `activity_pause` 取消 | 用户点开配置管理页 ⇒ `MediaActivity.onPause()` |
| 24.549 | `loading_frame_committed_after_limit` | 根页面交接的 700 ms 兜底触发，源页被 retire。这是**上一次根导航**的收尾，与 35 的取消是两件事，不能合并成一个事务 |
| 24.766 | `photo_grid_retained_for_child_page`（`adapterPresent=false, mediaCount=0, gridChildren=0`） | `onStop()`：策略「保留了网格」，但网格本来就是空的 |
| 25.534 | MediaActivity 窗口 resume | 子页面返回 |
| 25.557 | 新建 PHOTO_PROFILE 请求 36 | `onResume()` 判定 `sameAuthorizedGrid=false` ⇒ `tryLoadGallery()` |
| 26.987 | 请求 35 最终 `CANCELLED` | 旧 worker 直到此刻才真正退出（期间仍在占用线程） |
| 27.791 | 新请求发布 `CACHE_PREVIEW` 160 项 | 25.534→27.791 ≈ **2.26 s** 无内容 |

### 2.2 修改前后调用链

**修改前**

```
点击「照片配置管理」 (R.id.photo_profiles_manage)
  └─ photoChildPageOpen = true; PhotoProfileManageActivity.launch(this)

MediaActivity.onPause()
  ├─ if (!isPhotoProfiles || !photoViewerTransition) mediaLoadRequests.invalidate()
  │     ← photoChildPageOpen 未参与判断：子页面覆盖被当成离开 Photos
  └─ mCurrAsyncTask?.stopFetching("activity_pause")
        └─ PhotoProfileMediaLoader.checkCurrent() → PhotoLoadInvalidatedException
              ⇒ PhotoProfileMediaCache.offer() 不执行
              ⇒ PhotoProfileReturnProbe.record() 不执行

MediaActivity.onStop()
  └─ PhotoGridRetentionPolicy.clearsDisplayedGrid(childPageCovered=true) = false
        ⇒ 不 clearPhotoDisplayState()，但 adapter 本来就没有内容

PhotoProfileManageActivity.finish()
  └─ MediaActivity.onResume()
        ├─ photoChildPageOpen = false
        ├─ sameAuthorizedGrid = … && displayedMediaIsFinal   → false
        └─ tryLoadGallery()  → 全新请求 36 → 冷扫
```

**修改后**

```
MediaActivity.onPause()
  ├─ detachPhotoScanForChildPage()
  │     PhotoChildReturnPolicy.detachesScan(isPhotoProfile, viewerTransition,
  │         childPageCovered=photoChildPageOpen, scanRunning, photoRequestedKey, photoDisplayKey())
  │     true  ⇒ mediaGridPublication.clear(); retainedPhotoScan = RetainedPhotoScan(request, task, key, revision)
  │              （不 invalidate、不 stopFetching）
  │     false ⇒ 旧路径：invalidate + stopFetching("activity_pause")
  └─ 只有未保留时才 stopFetching

MediaActivity.onStop()
  ├─ coveringChild = photoChildPageOpen && retainedPhotoScan != null
  │     true  ⇒ 只记 photo_grid_snapshot_retained，不动请求与 worker
  │     false ⇒ photoReturnProbeGeneration++ / invalidate / stopFetching（旧行为）
  └─ 网格保留策略不变（PhotoGridRetentionPolicy 未修改）

getPhotoProfileMedia → onPhotoResult
  └─ PhotoChildReturnPolicy.holdsResult(childPageCovered, scanRetained)
        true ⇒ retained.latest = result.snapshotForUi()（不发布到已停止的窗口）
        false ⇒ submitPhotoGridResult(...)

MediaActivity.onResume()
  ├─ retainedScan = retainedPhotoScan; retainedPhotoScan = null
  ├─ resumeRetainedPhotoScan(retainedScan)
  │     PhotoChildReturnPolicy.rejoins(returnedFromChildPage, requestCurrent,
  │         keyUnchanged, revisionCurrent, accessCurrent, taskAlive)
  │     true  ⇒ submitPhotoGridResult(latest, request, key) —— 一帧内提交
  │     false ⇒ task.stopFetching("photo_child_scan_invalidated") → 走旧的重载分支
  └─ 只有 !rejoined && !sameAuthorizedGrid && !catalogWorkerActive 才 tryLoadGallery()
```

### 2.3 三种返回分支

| 分支 | 条件 | 行为 |
|---|---|---|
| 1 · 同源、授权未变、已有合法网格 | `PhotoChildReturnPolicy.rejoins` 通过，且 `retained.latest` 为 COMPLETE | 保留原 Adapter / 媒体身份 / 缩略图 / 滚动位置，返回即显示；后台只做 `PhotoProfileReturnProbe.needsReconcile` 的轻量校验 |
| 2 · 同源、授权未变、扫描未完成 | 同上，但 `latest` 只是 CACHE_PREVIEW / INDEXED / FILESYSTEM | 保留原请求 generation 与 worker，先发布最新已验证 stage，其余 stage 继续正常流入 |
| 3 · 来源 / 授权 / 筛选 / 排序变化 | `requestCurrent`、`keyUnchanged`、`revisionCurrent`、`accessCurrent`、`taskAlive` 任一为假 | `stopFetching` 丢弃保留，按新 scope 重新加载；旧数据一个都不显示 |

分支 3 的判据是**身份**而不是时间：`PhotoProfileScope.toString()` 里已经包含 profileId、
selectedId、sources、filter、sorting、excluded、bindings、hidden、accessVersion、
sessionId、allFiles、readPermissions，所以「授权被撤销」「筛选被改」「权限被收回」都会
让 key 变化，不需要额外状态。

### 2.4 「没有已提交网格」的情况

修改后仍有以下情况会走重载分支，并且**立即呈现主题背景 + 居中加载指示**（不是黑屏）：

- 首次进入 Photos、尚未收到任何 stage 就打开配置页（`retained.latest == null`）；
- 配置页里真的改了 profile（`onActivityResult` 先于 `onResume` 触发 `reloadPhotoProfile()`
  ⇒ `getPhotoProfileMedia()` 头部 `dropRetainedPhotoScan("new_request")`，`onResume` 自然走分支 3）；
- 访问能力变化（key 变化）、profile 被删除、Activity 真被销毁。

`getPhotoProfileMedia()` 的既有路径在 `!retainGrid` 时执行
`discardInaccessibleGrid() + setMediaLoading(true)`，`setMediaLoading(true)` 在
「adapter 里没有任何 `Medium`」时显示居中指示（`OxygenProgressStyle.setLoading`）。
该路径本轮未改，因为它本来就满足「无可用网格时立即呈现正确主题的加载界面」。

### 2.5 图层归因：仍然待定

已经排除的：

- **Window / DecorView 背景**：`OxygenClassicActivityMotion.install()` 在
  `PredictiveBackSupport.systemDrivesPageTransition`（API ≥ 34）时对非 root 页面执行
  `window.setBackgroundDrawable(pageBackground)`；根导航页面走
  `GalleryBottomBar.restoreRootWindow()`，同样是页面色。`AppTheme` 的
  `android:windowBackground` 也已被覆盖成 `@color/oxygen_classic_page`。
- **TaskDescription 背景**：`SimpleActivity.onApplyThemeResource` 通过
  `OxygenClassicTaskBackground` 注入 `android:colorBackground`，平台据此写入
  TaskDescription。
- **`PhotoProfileManageActivity` 自身窗口**：manifest 未单独声明 theme，继承 `AppTheme`，
  窗口背景 = 页面色；`onCreate` 里 `renderTheme()` 又把 `binding.root` 与内容区设成页面色。

仍然可能的：

1. 深色主题下 `#212121` 的空页面本身在录像中就是「黑」；
2. 屏幕录制对受保护窗口的遮蔽（`FLAG_SECURE` 路径：`getPhotoProfileMedia` 在
   `photoToken() != null` 时添加，Viewer 同理）；
3. 平台在 `onStop` 时刻抓的任务快照恰好是空网格。

**本轮没有关闭 `FLAG_SECURE`、没有改受保护媒体的权限规则**，也没有把结论写成
「窗口背景导致」。为了下一次能直接判定，`photo_child_navigation_begin` /
`photo_grid_snapshot_retained` / `photo_return_snapshot_ready` 里新增了
`secureWindow`（布尔）、`adapterPresent`、`gridChildren`、`snapshotStage`、
`itemCount`、`accessNeedsUnlock`、`snapshotVersion`、`elapsedRealtimeMs` 字段。
这些事件只在 `GalleryDiagnosticLogger.isEnabled()` 时输出。

### 2.6 首帧交接（`GalleryBottomBar.coordinateFirstDraw`）

审计结论：**不改**。

- `LOADING` 帧不算 `commits`，只有 `CONTENT / EMPTY / ERROR` 立即提交；
- `LOADING` 帧靠 `LOADING_HANDOFF_LIMIT_MS = 700 ms` 兜底，日志里的
  `loading_frame_committed_after_limit`（23.836 → 24.549）正是这条兜底，属于**上一次根
  导航**的收尾，不是黑闪原因；
- `registerFrameCommitCallback` 只证明「帧已提交」，**不等于已合成上屏**。这一限制在
  §9 里如实记录，没有通过缩短或延长 700 ms 来"修"它。

本轮没有修改该逻辑：它没有把「父页面已准备好」误判成「无内容」，也没有因为错误门禁
让页面长期停在无内容状态。P0 的空档发生在**子页面返回**路径，那条路径根本不经过
`coordinateFirstDraw`（它不是 root transition）。

## 3. P1-A：文件夹退场最终精修

### 3.1 两个阶段的责任边界（未改变）

| 阶段 | 控制权 |
|---|---|
| 手势进行中（左右边缘、慢放、快速甩动、中途取消） | **Android 系统**：窗口缩放、圆角、位置、返回预览都由 `CustomCrossActivityBackAnimation` 的 pre-commit 阶段驱动；左右方向由平台决定 |
| 提交之后 | 应用 CLOSE-exit 资源：`OxygenClassicFolderBackWindow` 的
`activityCloseExitAnimation`，且关闭 rect 被冻结在松手位置，只有 alpha 与 matrix 生效 |

因为提交后的目标永远是「松手位置再往 +X 移动 96 dp」，所以 XML 里**只有一个正方向
target**，不做左右机械反转；这也被测试 `folderExitKeepsThePlatformDirectionOnBothEdges` 固定。

逐场景审计（本轮全部按此表核对，未改的标「未改」）：

| 场景 | 谁驱动 | 本轮处理 |
|---|---|---|
| 1 左边缘发起预测性返回 | 平台 pre-commit（缩放 / 圆角 / 方向） | 未改；提交后仍走同一个 +X 目标 |
| 2 右边缘发起预测性返回 | 同上，方向由平台给 | 未改；**未**在 XML 里反转动画 |
| 3 手指中途停止并取消 | 平台把预览回弹到 0 | 未改；应用侧没有提交后动画参与 |
| 4 手势缓慢释放 | 平台按释放进度提交，再跑 post-commit 时钟 | 时钟 450→300 ms；释放帧 alpha ≥ 0.99 |
| 5 快速甩动并提交 | 同上，但释放进度更小 | 同上；前段位移更快（20% 已走 23.8% 而不是 36.3% 的"先溶后移"） |
| 6 顶栏返回箭头 | `finish()` → `OxygenClassicActivityMotion.applyClose` | 14+ 不调 `overridePendingTransition`，与系统返回共用同一对资源 |
| 7 Android 14 以下 | `overridePendingTransition(oxygen_classic_page_close_enter/exit)` | **未改**（测试 `legacyPlatformsKeepTheExistingPageAnimation`） |

### 3.2 旧实现的问题（实算）

旧组合：450 ms 时钟；alpha 完成于 35%（157 ms）；位移完成于 450 ms。

```
progress   alpha   travel(×96dp)
0.02       0.993   0.010
0.10       0.914   0.114
0.20       0.692   0.363
0.30       0.295   0.648
0.35       0.000   0.780     ← alpha 归零，剩 293 ms
0.50       0.000   0.883
1.00       0.000   1.000
```

两个后果：
1. 0–35% 期间前景半透明地压在**已完整绘制的静止父页**上 ⇒ 两张网格的交叉溶解（重影）；
2. 35% 之后 293 ms 内前景不可见、父页静止、窗口却还没被移除 ⇒ 观感上「已经开始收尾」
   与「真正结束」之间有一段空档，随后窗口消失，读作**突然切换**。

### 3.3 新实现（文件夹专用 300 ms 单时钟）

资源：

- `values/oxygenos_classic.xml`
  - 新增 `integer/oxygen_classic_folder_back_duration_ms = 300`（**只**被文件夹样式使用）
  - `OxygenClassicFolderBackWindow` 的 `activityCloseEnterAnimation` 改为
    `@anim/oxygen_classic_folder_back_enter`（新增文件），`activityCloseExitAnimation` 仍为
    `@anim/oxygen_classic_folder_back_exit`
- `anim/oxygen_classic_folder_back_enter.xml`（新增）：alpha 恒为 1，唯一作用是**时长**
  ——`getPostCommitAnimationDuration() = min(2000, max(close.duration, enter.duration))`，
  只缩短 exit 仍会得到 450 ms 的 Shell 时钟，尾巴会回来。
- `anim/oxygen_classic_folder_back_exit.xml`：两个子动画都改用文件夹时钟，几何契约不变
  （无 scale、无 rotate、96 dp、+X）。
- `interpolator/oxygen_classic_folder_back_alpha.xml`：
  `M0,0 C0.18,0.03 0.33,0.30 0.55,0.72 C0.74,0.92 0.90,0.99 1,1`
- `interpolator/oxygen_classic_folder_back_slide.xml`：
  `M0,0 C0.16,0.10 0.28,0.42 0.50,0.70 C0.70,0.88 0.87,0.97 1,1`

### 3.4 新旧曲线采样对照

（两者都按 Shell 的求值路径 `AnimationSet#getTransformationAt` 采样，位移以 96 dp 为单位）

| progress | 旧 alpha | 旧 travel | 新 alpha | 新 travel |
|---:|---:|---:|---:|---:|
| 0.00 | 1.000 | 0.000 | 1.000 | 0.000 |
| 0.02 | 0.993 | 0.010 | 0.996 | 0.014 |
| 0.05 | 0.973 | 0.037 | 0.985 | 0.039 |
| 0.10 | 0.914 | 0.114 | 0.958 | 0.094 |
| 0.15 | 0.822 | 0.227 | 0.917 | 0.161 |
| 0.20 | 0.692 | 0.363 | 0.864 | 0.238 |
| 0.25 | 0.519 | 0.507 | 0.800 | 0.320 |
| 0.30 | 0.295 | 0.648 | 0.727 | 0.403 |
| 0.40 | 0.000 | 0.820 | 0.559 | 0.561 |
| 0.50 | 0.000 | 0.883 | 0.375 | 0.700 |
| 0.60 | 0.000 | 0.928 | 0.230 | 0.785 |
| 0.70 | 0.000 | 0.959 | 0.143 | 0.858 |
| 0.80 | 0.000 | 0.980 | 0.074 | 0.920 |
| 0.90 | 0.000 | 0.993 | 0.025 | 0.968 |
| 0.95 | 0.000 | 0.997 | 0.009 | 0.986 |
| 1.00 | 0.000 | 1.000 | 0.000 | 1.000 |

结论对照：

| 指标 | 旧 | 新 |
|---|---|---|
| alpha 归零位置 | 35%（157 ms） | 99.2%（≈298 ms），与窗口移除同帧 |
| 不可见尾段 | **293 ms** | **≈2 ms**（不到一帧） |
| alpha=0.5 时的位移 | 27.7 dp | 74 dp |
| 20% 时前景不透明度 | 69% | 86%（前段以运动为主，不是溶解为主） |
| 0–2% 的掉幅 | 0.7% | 0.4%（松手帧不塌） |
| 子动画时长 | 450 ms（两段） | 300 ms（两段，与 set 一致） |

### 3.5 未改动的部分

- `oxygen_classic_page_back_duration_ms` 仍为 450，`OxygenClassicPageBackWindow` 的
  enter / exit 一个字节未变（测试 `sharedClosePairIsLeftUntouched` 断言 450 且旧曲线
  在 40% 处已经为 0）；
- `OxygenClassicPageWindow`（Android 14 以下的降级路径）未变
  （测试 `legacyPlatformsKeepTheExistingPageAnimation`）；
- 设置页、主题页、自定义照片配置页的动画时长与曲线未变；
- Viewer 的共享元素几何、下拖退出、预测性返回进度衔接未变。

## 4. P1-B：Viewer 退出系统栏

### 4.1 三个写入者 / 两类闪烁

`ViewPagerActivity` 在返回过程中有三条路径会碰到系统栏：

1. `renderSharedViewerCanvas(progress)` ← `renderSharedReturnLayers` / `renderInteractiveDismissProgress`
   / `renderSharedReturnProgress`，每帧调用 `updateViewerSystemBars()`；
2. `BaseViewerActivity.onResume → refreshViewerSystemBars()`；
3. `onDestroy → returnLeases.releaseAll()` 的 CHROME 租约：
   `ownReturnChrome` 的清理块 `restoreBlackViewerCanvas(); renderSharedViewerCanvas(1f)`。

同时，返回目标页在自己 `onResume` 里也会写：
`MediaActivity.onResume() → updateMenuColors() → OxygenSystemBars.apply(this)`。

于是有两类可以证明的问题：

| # | 机制 | 表现 |
|---|---|---|
| A | Viewer 逐帧写 + 父页在动画窗口下方写一次，谁后写谁生效 | 状态栏在退出开始处「暗一下又亮回来」 |
| B | 返回结束、租约释放时 `renderSharedViewerCanvas(1f)` 把进度从 0 拉回 1，重写 Viewer 外观（`#1E1E1E` + 浅色图标） | 窗口消失前最后一帧的近黑闪烁 + 图标再翻一次 |
| C | `applyViewer` 每帧按混合色重算图标极性，判据就在对比度相等点 | 手势抖动 / 取消重放时极性反复翻转 |

### 4.2 修复后的所有权协议

```
Viewer 可见（entry / steady）
  ├─ renderSharedViewerCanvas(progress) 正常跟踪进度 —— 此阶段 Viewer 就是目标页
  └─ 图标极性：SystemBarIconContrast.useDarkIcons(bg, 当前极性)（1.0 对比度滞回）

requestViewerClose(reason)   ← 所有退出路径的唯一入口
  ├─ viewerSystemBarHold = true
  └─ viewerSystemBarHoldProgress = 当前 viewerSystemBarProgress
        ⇒ 之后 renderSharedViewerCanvas 的 barProgress 恒为 hold 值
        ⇒ 第一帧之后 applyViewer 的入参不再变化 ⇒ 不再写任何属性

返回取消（onSharedReturnTerminal cancelled=true）
  ├─ viewerSystemBarHold = false
  └─ renderSharedViewerCanvas(1f) —— 单次恢复到 Viewer 外观

返回完成（cancelled=false）
  └─ 记录 viewer_system_bar_return_completed；不再写
       （最终外观由父页在自己窗口上写，Viewer 窗口随即被移除）

onDestroy → 租约清理 renderSharedViewerCanvas(1f)
  └─ barProgress = hold 值 = 当前进度 ⇒ applyViewer 入参相同 ⇒ 不写（终态回跳被吸收）
```

关键点：**所有权不是一个布尔开关，而是「最后一次写入者」被限制成唯一**。
`applyViewer` 本来就是幂等的（返回 `changed`），冻结只是把它的入参固定住，
所以不需要新增状态机，也不需要跨 Activity 的令牌。

### 4.3 图标极性的滞回

`SystemBarIconContrast`（纯 Kotlin，可 JVM 直测）：

```
dark  = contrast(#000, bg)
light = contrast(#FFF, bg)
previous != null && |dark - light| < 1.0  ⇒ 保持 previous
否则                                       ⇒ dark > light
```

- 稳态（`#FAFAFA` 页面色 / `#1E1E1E` Viewer 面）两侧对比度差约 19–20，远大于 1.0，
  所以端点永远选对，不会因为「曾经是深色图标」而留在低对比状态；
- 只有中间那段「两种选择都在 3:1 附近」的灰带会保持现状（实测该带内黑/白对比度
  分别约 4.6 / 4.57，都 ≥ 3.0）；
- 没有固定黑图标、没有一直隐藏状态栏、没有关闭系统栏动画。

### 4.4 Android 16 / edge-to-edge

- API ≥ 35 路径继续只使用应用自绘的 `oxygen_classic_status_underlay`（decor 坐标，
  `stableInsets` 高度不随沉浸模式变化），没有回退到 `statusBarColor`；
- 未使用反射、未触碰 SystemUI、未修改系统级返回动画；
- `FLAG_SECURE`、受保护媒体的隐私规则完全未动。

## 5. 修改清单

| 文件 | 改动 |
|---|---|
| `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt` | 新增 `RetainedPhotoScan` / `retainedPhotoScan` / `photoRequestedKey`、`dropRetainedPhotoScan`、`detachPhotoScanForChildPage`、`resumeRetainedPhotoScan`、`logPhotoChildEvent`；改 `onPause`、`onStop`、`onResume`、`onDestroy`、`getPhotoProfileMedia`（`dropRetainedPhotoScan("new_request")` + `onPhotoResult` 分流）、`clearPhotoDisplayState` |
| `app/src/main/kotlin/org/fossify/gallery/helpers/PhotoChildReturnPolicy.kt` | **新增**：三种返回分支的纯函数判据 |
| `app/src/main/kotlin/org/fossify/gallery/helpers/SystemBarIconContrast.kt` | **新增**：WCAG 亮度 / 对比度 + 极性滞回 |
| `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenSystemBars.kt` | `applyViewer` 改用滞回极性；删除本地 `useDarkIcons` |
| `app/src/main/kotlin/org/fossify/gallery/activities/BaseViewerActivity.kt` | `updateViewerSystemBars` 变动时记录 `viewer_status_appearance_changed` |
| `app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt` | 新增 `viewerSystemBarHold` / `viewerSystemBarHoldProgress`、`logSystemBarOwner`、`logSystemBarReturnCompleted`；改 `renderSharedViewerCanvas`、`requestViewerClose`、`onSharedReturnTerminal` |
| `app/src/main/res/values/oxygenos_classic.xml` | 新增 `oxygen_classic_folder_back_duration_ms`；`OxygenClassicFolderBackWindow` 换用文件夹 enter |
| `app/src/main/res/anim/oxygen_classic_folder_back_enter.xml` | **新增**：父页面 CLOSE-enter（alpha 恒 1，只为携带文件夹时钟） |
| `app/src/main/res/anim/oxygen_classic_folder_back_exit.xml` | 改用文件夹时钟，注释改为新契约 |
| `app/src/main/res/interpolator/oxygen_classic_folder_back_alpha.xml` | 新曲线（1 → 0 与位移同帧结束） |
| `app/src/main/res/interpolator/oxygen_classic_folder_back_slide.xml` | 新曲线（前段快，末段缓，1.0 收尾） |
| `app/src/test/.../FolderBackCloseAnimationTest.kt` | 契约更新为 300 ms 单时钟 + 无尾段 + 运动优先 |
| `app/src/test/.../PhotoChildReturnPolicyTest.kt` | **新增**（9 项） |
| `app/src/test/.../SystemBarIconContrastTest.kt` | **新增**（7 项） |
| `app/src/test/.../ViewerSystemBarOwnershipTest.kt` | **新增**（8 项，API 34 + 36 各跑一遍） |
| `docs/animation/predictive-back-root-cause/14-navigation-visual-stability.md` | **本文** |

## 6. 自动化验证

### 6.1 本轮新增 / 更新

| 测试 | 覆盖 |
|---|---|
| `PhotoChildReturnPolicyTest`（9） | 三种分支；`viewerTransition` 不误触发；key 变化不跨覆盖；`rejoins` 与 `discards` 恰好互补（穷举 32 组） |
| `SystemBarIconContrastTest`（7） | 端点极性、滞回保持、滞回带内两种极性的对比度都 ≥ 3:1、单调混合只翻一次、抖动混合不翻 |
| `FolderBackCloseAnimationTest`（14） | 样式隔离（文件夹 vs 设置/主题/配置）、文件夹时钟 300 且 enter/exit 一致、alpha 与位移同帧结束、无死尾段、松手帧不掉、运动优先、纯水平平移、左右方向约定、插值器合法性、14 以下降级路径未变、共用 pair 仍为 450 |
| `ViewerSystemBarOwnershipTest`（8，sdk 34/36） | entry 仍跟踪进度；冻结期间任意进度帧零写入；租约清理不能撤销交接；取消返回能取回并恢复；`applyViewer` 幂等 |

### 6.2 完整回归（本地，JDK 21，`:app:testFossDebugUnitTest`）

```
test classes = 193   tests = 1403   failures = 0   skipped = 0
BUILD SUCCESSFUL
```

其中本轮新增 24 项（`PhotoChildReturnPolicyTest` 9 + `SystemBarIconContrastTest` 7 +
`ViewerSystemBarOwnershipTest` 8，后者在 API 34 与 36 各跑一遍）、更新 14 项
（`FolderBackCloseAnimationTest`）。既有 `PhotoViewerInitializationTest` 中有一条断言
（`continuousDragRevealsGridWithoutChangingOpacityWhenReleased`）编码的是旧契约
「返回动画期间系统栏跟随进度」，已按 §4.2 的新协议改为「图层跟随进度、系统栏冻结」，
并注明原因。

### 6.3 `:app:detekt`

`:app:detekt` 在本分支**本次修改之前就已经失败**：

| 树 | weighted issues |
|---|---|
| 干净 `18c60a8c2`（临时 worktree 实测） | **4036** |
| 本轮修改后 | **4036** |

也就是说本轮**没有引入任何新的 detekt 发现**：新增的 5 个文件为 0 条，
`BaseViewerActivity` 的一条 `MaxLineLength` 在自查后已消除。
`MediaActivity` / `ViewPagerActivity` 中若干 `LongMethod` / `TooManyFunctions` 的
计数文本会随行数变化，但它们本身在基线里已经存在；要消除它们需要拆分 5000+ 行的类，
属于本次任务明确排除的范围。`detekt.yml` 的 `maxIssues: 0` 与仓库自带的
`app/detekt-baseline.xml` 未覆盖当前代码，是**历史遗留**状态，如实报告、未掩盖、
也未顺手修改配置。

## 7. 无模拟器验证的范围与局限

**已覆盖（本地、确定性）**

- 动画资源加载、求值（`Animation#getTransformationAt`，与 Shell 同一调用）、时长、曲线；
- 窗口样式选择与文件夹 / 非文件夹隔离；
- Photos 三种返回分支的判据（纯 JVM，穷举）；
- 系统栏写入协议（Robolectric，API 34 与 36），包括 `oxygen_classic_status_underlay`
  的复用与高度稳定性；
- 图标极性滞回的对比度数值。

**未覆盖 / 无法证明**

- 真实 Surface 合成：`frame_commit` 回调不等于「已上屏」，Robolectric 更不能证明；
- 屏幕录制对 `FLAG_SECURE` 窗口的遮蔽行为；
- 文件夹退场在 Pixel 8 Pro 上的实际观感（圆角、双页叠加的视觉权重、松手瞬间速度连续性）；
- 系统栏图标在快速退出时的视觉感受（虽然写入次数已被压到 0/1，最后一次写入的实际观感仍需人眼）；
- 真实 MediaStore 在「子页面覆盖 2 秒期间有新文件写入」时的行为（本轮只保证身份校验，
  不保证内容新鲜度，返回后的 `PhotoProfileReturnProbe.needsReconcile` 负责后续轻量校正）。

### 7.1 Viewer 退出场景的自动化覆盖对照

| 场景 | 自动化 | 说明 |
|---|---|---|
| 普通照片退出 | 部分 | `ViewerSystemBarOwnershipTest` 的冻结 / 交接 / 恢复三例；共享元素终态由既有 `PhotoViewerInitializationTest` 覆盖 |
| 滑动到其他照片之后退出 | 既有回归 | 返回目标匹配由 `PhotoViewerReturnTargetTest` / `ViewerGridOrderPolicyTest` 覆盖，本轮未改该路径 |
| 左 / 右预测性返回 | 部分 | 两条边缘共用同一条 `renderSystemBackProgress` → `renderInteractiveDismissProgress` → `renderSharedViewerCanvas`，协议断言即是覆盖；边缘方向本身由平台决定，无法在 JVM 里证明 |
| 返回取消 | 是 | `aCancelledReturnTakesTheBarsBack` + `onSharedReturnTerminal(cancelled=true)` 只恢复系统栏、不动 scrim（既有测试 `sharedReturnAnimatorDrivesLayersAndLateCallbacksKeepTheFinalFrame` 固定这一点） |
| 下拖退出 | 部分 | `continuousDragRevealsGridWithoutChangingOpacityWhenReleased` 已更新为「图层跟随时钟、系统栏冻结」的新契约 |
| 下拖一半后回弹 | 既有回归 | settle 动画路径未改；系统栏在回弹期间同样处于冻结前的正常跟踪状态（未提交返回前不冻结） |
| 下拖中被系统返回接管 | 既有回归 | `resumedSystemBackGestureConsumesOnlyTheRemainingIntervalAndCancelsToFullViewer` |
| 深色主题 / 浅色主题 | 部分 | `SystemBarIconContrastTest` 用真实页面色与 Viewer 面色枚举极性；主题本身在 Robolectric 里只有默认一套 |
| 状态栏显示 / 隐藏 | 部分 | `viewerStatusSurfaceVisible`（slideshow / 全屏）路径未改；`edgeToEdgeReusesOneStatusSurfaceAndPreservesInsetsThroughImmersion` 覆盖 underlay 复用与高度稳定 |
| 缩略图匹配 / 无法匹配的淡出降级 | 既有回归 | `PhotoViewerReturnTargetTest`、`PhotoTransitionCompletionGateTest`；本轮未改 |

**明确不在自动化范围内**：以上任一场景的**视觉**结论。自动化只证明「应用写了几次、写了什么值」。

## 8. 需要 Pixel 8 Pro 再次验收的项目

1. **P0**：Photos 仍在加载时打开配置管理页，停留 2 秒后返回 —— 是否直接看到原网格 / 主题加载态，
   无黑屏、无纯色闪帧；返回后滚动位置与排序是否稳定。
2. **P0**：配置页里真的切换 profile 后返回 —— 必须显示新 profile 的内容，不能出现旧数据。
3. **P0**：配置页里删除当前 profile 后返回 —— 必须进入空状态，不能停留在旧网格。
4. **P1-A**：左边缘慢拖 -> 中途取消 -> 再慢拖 -> 快速甩动提交，四种组合的退场是否连贯；
   父页面是否始终是真实主页；是否仍有文字重影或突然硬切。
5. **P1-B**：普通照片退出、滑动到另一张后退出、左/右预测性返回、返回取消、下拖退出、
   下拖一半回弹、下拖中被系统返回接管 —— 状态栏图标是否只变一次且方向正确；
   浅色 / 深色主题各一次；Viewer 全屏（状态栏隐藏）模式下一次。
6. **诊断复核**：开启诊断日志后重复步骤 1，检查 `photo_child_navigation_begin` /
   `photo_grid_snapshot_retained` / `photo_scan_rejoined` / `photo_return_snapshot_ready`
   是否连续出现且 `secureWindow` 的取值；据此可最终判定问题 B 的黑色图层来源。
