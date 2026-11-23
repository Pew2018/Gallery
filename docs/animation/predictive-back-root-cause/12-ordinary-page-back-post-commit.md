# 12 · 普通页面预测性返回：提交后收束（post-commit）专项审计与修复

> 审计基线：开始执行前重新 `git fetch` 确认的
> `perf/home-gallery-sorting` 远程 HEAD `c1ed855ad8891ac70feecd7e54029e962270c279`
> （与任务给出的审计基线一致；分支上没有其他 Agent 的新提交）。
> 上一处涉及动画的源码提交：`c52c44c7cfc2b68747e90cf2e6c45b705fe33422`。
> 平台依据：AOSP `android16-release`（`platform/frameworks/base`，2026-11-23 实取）。
> 本文只把能由源码/资源/自动化测试证明的结论写成「已确认」；需要真机合成的项目一律
> 标注为「待真机验收」。

## 0. 证据可得性说明（先行，避免过度声称）

任务描述引用的两份证据在本机**不存在**：

| 任务引用 | 实际查找结果 |
|---|---|
| 录屏 `3518f8bc001eaa028b52b2bdebc70627.mp4` | 工作区、`~/Desktop`、`~/Downloads`、`~/Documents`、`~/Movies` 均无此文件 |
| 诊断日志 `Gallery-Diagnostics-20261123-152943.txt` | 同上，均无 |

工作区里只有更早一轮的 `evidence/10d40fd5e762ee634386df16f22a84db.mp4`
（23.0 s / 576×1280 / 30 fps）与 `evidence/Gallery-Diagnostics-20261123-112745.txt`。
本轮对这段录屏做了逐帧复核，结论是：**它只包含 Viewer（网格 ↔ 全屏浏览）与一个系统
"Stop recording?" 对话框，完全没有 Pixel 系统设置画面**（逐帧亮度/饱和度统计 +
1 fps 全景接触表）。因此它**不能**用来完成 P0-A 要求的「系统设置 vs Gallery」对照，
也不能作为本轮普通页面视觉结论的证据。

由此，本轮 P0 的对照表全部是**源码级机制对照**，不是录像观察。凡是录像才能回答的问题
（首帧手感、圆角观感、父页是否"自然"、系统栏跟随）在本轮保持「待真机验收」。

同时必须指出：任务描述中的「系统设置是本轮视觉目标」这一前提，本轮改成用
**AOSP 源码判定系统设置实际走哪条分支**来落地，而不是靠录像猜测（见 §3）。

## 1. 结论先行

| 项 | 结论 | 等级 |
|---|---|---|
| 普通页面当前使用的系统分支 | `CustomCrossActivityBackAnimation`（自 `1a87ff24e` 起） | 已确认（源码 + 资源） |
| 手势进行中（pre-commit）的几何 | 关闭页原生缩放/位移 + 圆角；进入父页保持**全屏基础矩形**、不被缩放 | 已确认（AOSP） |
| 根因 | 提交后 Shell **不再推进** `currentClosingRect`，而我们的 CLOSE-exit 是恒等 alpha ⇒ 关闭页在松手位置**冻结 170 ms 后被直接移除**，即"最后一瞬间突然消失" | 已确认（AOSP + 资源） |
| 同一根因的第二个受害者 | 同一对 CLOSE 资源也驱动**工具栏返回**（非预测性的 `finish()` 过渡）⇒ 工具栏返回是一帧硬切，完全没有关闭动画 | 已确认（AOSP 资源语义） |
| 修复 | 让 CLOSE-exit 真正承担提交后运动（alpha 1→0 + 位移 0→96dp），并把 CLOSE 对的时长对齐平台参考的 450 ms | 已实现 |
| 分支选择 | **保留 Custom 分支**，不改用 Default 分支 | 见 §4 |
| Viewer | 未修改 | 见 §7 |

## 2. Android 16 平台机制（AOSP android16-release 实取）

### 2.1 分支如何被选中

`services/core/java/com/android/server/wm/BackNavigationController.java`

- `:329-333`：仅当 `isCustomizeExitAnimation(window)` 为真时，才把
  `window.mAttrs.windowAnimations` 通过 `infoBuilder.setWindowAnimations(...)` 交给 Shell。
- `:696-720` `isCustomizeExitAnimation()`：要求
  (a) 包名不是 `android`；(b) `windowAnimations != 0`；
  (c) 由该 style 解析出的 `activityCloseExitAnimation` 资源 id **不等于平台默认 id**。
- `libs/WindowManager/Shell/src/com/android/wm/shell/back/ShellBackAnimationRegistry.java`
  `getAnimationRunnerAndInit()`：`CustomCrossActivityBackAnimation.prepareNextAnimation()`
  返回 true 就用 Custom，否则回落到 `DefaultCrossActivityBackAnimation`。

这解释了为什么 `1a87ff24e` 里"声明一对恒等 CLOSE 动画"确实能把普通页切到 Custom 分支：
恒等 alpha 也是**非默认**资源 id，条件 (c) 成立。

### 2.2 Custom 分支到底做了什么

`libs/WindowManager/Shell/src/com/android/wm/shell/back/CustomCrossActivityBackAnimation.kt`

- `:79-92` `preparePreCommitClosingRectMovement()`：关闭页缩放到 `MAX_SCALE`，并按滑动边
  向对侧偏移一个 `displayBoundsMargin`。
- `:94-98` `preparePreCommitEnteringRectMovement()`：**进入父页矩形 = 全屏，不动**。
- `:106-110` `getPreCommitEnteringBaseTransformation(progress)`：
  `enterAnimation.getTransformationAt(progress * 0.2)`。
- `:130-155` `onPostCommitProgress()`：
  - 关闭页用 **`currentClosingRect`**（手势结束时的那一帧）叠加 `closeAnimation` 的
    alpha/matrix；
  - **`currentClosingRect` 在提交后不会被再次赋值**，所以关闭页几何**冻结**。
- `:100-104` `getPostCommitAnimationDuration()` =
  `min(2000, max(closeAnimation.duration, enterAnimation.duration))`。

### 2.3 参考分支（系统设置）做了什么

`DefaultCrossActivityBackAnimation.kt`：`:51-63` 关闭页缩放 + 有向偏移；
`:65-72` 进入父页从 −96dp 起步并同步缩放到 0.9；`:74` `POST_COMMIT_DURATION = 450L`；
`:76-86` 提交后把关闭页目标改成 `currentClosingRect.left + enteringStartOffset`；
`:88-107` 关闭页 alpha = `max(1 - linearProgress * 5, 0)`，即前 20% 淡出。

系统设置实际走哪条分支（源码判定，非录像）：`packages/apps/Settings` 的
`Theme.SubSettings` → `Theme.Settings.NoActionBar` → `Theme.Settings` → DeviceDefault，
而 `core/res/res/values/styles_device_defaults.xml:434`
`Animation.DeviceDefault.Activity` 的父样式是 `styles_material.xml:1234`
`Animation.Material.Activity`，后者的父样式 `core/res/res/values/styles.xml:96`
`Animation.Activity` **没有再覆盖** `activityCloseEnterAnimation` /
`activityCloseExitAnimation`——两者都等于框架默认 id。于是
`isCustomizeExitAnimation()` 的 (c) 不成立，**系统设置走 Default 分支**。
这一条是"继承链无覆盖"的源码推导，等级为「源码推导，未真机对照」。

### 2.4 两个驱动路径的差异（容易踩坑）

- Shell 用的是 `AnimationSet.getTransformationAt(progress)`
  （`core/java/android/view/animation/AnimationSet.java:371-381`）：它**把同一个 0..1
  progress 直接转发给每个子动画**，不会按子动画的 duration/startOffset 重新归一化；
  `Animation.getTransformationAt()`（`Animation.java:996-999`）也**不做 clamp**。
  ⇒ 在 Shell 路径下，子动画的 `duration`/`startOffset` 对**形状**无效，只有
  `AnimationSet.getDuration()`（`:301-313`，取子动画最大值）对**时长**有效。
- 非预测性 `finish()` 的窗口过渡走 `Animation.getTransformation(currentTime)`，会正常
  按子动画时长/偏移播放。
- 因此 CLOSE 对的每个子动画都写成 **duration = 集合时长**，两条驱动路径才一致。

### 2.5 背景层

`CrossActivityBackAnimation.kt:168-174` `getBackgroundColor()` 取
`enteringTarget.taskInfo.taskDescription.backgroundColor`；
`core/java/android/app/Activity.java:5594-5598` 把主题的
`android:colorBackground` 写进 TaskDescription。Gallery 用
`OxygenClassicTaskBackground`（`dcd209444`）把它固定为 `@color/oxygen_classic_page`
（日间 `#FAFAFA` / 夜间 `#212121`）。这是 Custom/Default 两条分支共用的机制。

## 3. P0-B 对照表（环境受限版）

> 「系统设置」列是 §2.3 的源码推导；「Gallery」列是当前源码资源。
> 没有任何一列来自本轮录像观察。

| 观察项目 | 系统设置（源码推导） | Gallery（源码确认） | 关联源码 | 证据等级 |
|---|---|---|---|---|
| 左边缘返回 pre-commit | 关闭页 0.9，向右侧收敛 | 同（平台几何） | `Custom…:79-92` | 已确认 |
| 右边缘返回 pre-commit | 关闭页 0.9，**居中不额外偏移** | 关闭页 0.9，向左侧偏移（Custom 对右边缘也做偏移） | `Default…:51-63` vs `Custom…:79-92` | 已确认（差异存在，方向观感待真机） |
| 前景窗口缩放 | 0.9 | 0.9 | `MAX_SCALE` | 已确认 |
| 前景窗口圆角 | 平台窗口圆角 | 同 | `Custom…` 继承 `applyTransform` 的 `setCornerRadius` | 已确认 |
| 父页面呈现 | 缩放 0.9 + 从 −96dp 滑入 | **全屏、不动**（仅被 scrim 压暗） | `Default…:65-72` vs `Custom…:94-98` | 已确认 |
| 背景层 | 任务背景色（=页面色） | 同 | `BackAnimationBackground`、`Activity:5594` | 已确认 |
| 提交后收束 | 450 ms，关闭页继续移动 96dp + 前 20% 淡出 | **修复前：冻结 170 ms 后硬切**；修复后：450 ms 内位移 96dp + 淡出 | `Custom…:130-155`、`Default…:74,88-107` | 已确认（修复前/后） |
| 取消后回弹 | 平台 `BackProgressAnimator` 回弹 | 同（未改动） | `CrossActivityBackAnimation.Callback` | 已确认（机制） |
| 工具栏返回 | 平台默认 CLOSE 过渡 | **修复前：一帧硬切**；修复后：同一对 CLOSE 资源 | `activityCloseEnter/ExitAnimation` | 已确认（修复前/后） |
| 录制首帧手感 / 圆角观感 / 系统栏跟随 | 未观察 | 未观察 | — | **待真机验收** |

## 4. 为什么保留 Custom 分支（而不是改回 Default）

Default 分支确实"更接近系统设置"，但代价是把 `1a87ff24e` / `07` 已经修掉的问题重新引入：

1. Default 分支把父页缩放到 0.9 并向左偏移 96dp，父页外侧必然露出
   `BackAnimationBackground` 颜色层。`07-verified-mechanism-and-fix.md` §1 已明确记录，
   用户看到的"黑色/白色大面积背景"**就是这层颜色层**；把它改成页面色只是把黑色换成
   白色/浅灰，用户仍然会读成"占位背景"。
2. 验收标准要求"背景是真实父页面""不出现黑色或白色占位背景"，并要求
   "不能以视觉优化换取已解决问题的回归"。
3. Custom 分支的父页基础矩形是全屏，真实父页**完整**可见，这才是本轮要保住的能力。

因此本轮不删除 `OxygenClassicPageBackWindow`，也不改用 Default 分支；而是补齐
Custom 分支本来就要求应用提供的那一半——提交后的关闭页运动。

## 5. 根因（已确认）

`1a87ff24e` 为了让 Custom 分支可加载，把 CLOSE 对写成了 `1.0 → 1.0` 的恒等 alpha。
这在**手势期间**是对的（几何由平台负责），但忽略了 Custom 分支的合同：
**提交后关闭页的几何由平台冻结、视觉完全交给这两份资源**。

于是提交瞬间发生的是：

1. `currentClosingRect` 停在手指松开的 0.9 位置，此后不再变化（`Custom…:130-155`）；
2. `closeAnimation` 的 alpha 恒为 1，平移/缩放为 0；
3. 只有 `FlingMode.FLING_SHRINK` 带来几个百分点的弹簧收缩；
4. `getPostCommitAnimationDuration()` = `max(140, 170)` = **170 ms** 后
   `finishAnimation()` 直接移除窗口。

净效果：前景页冻住 170 ms 然后凭空消失。这正是任务里"手势提交时……不突然跳变"、
"页面收束自然，不突然消失"、"没有明显二次动画"要解决的问题。

同一对资源还驱动工具栏返回：`activityCloseEnterAnimation` /
`activityCloseExitAnimation` 在非预测性 CLOSE 过渡中分别作用于进入页与关闭页。两者都是
恒等 ⇒ **工具栏返回是一帧硬切**。这条此前没有被单独记录。

## 6. 修复

改动只发生在普通页面的 CLOSE 资源与其时长 token，没有改动任何手势识别、调用链或
系统分支选择逻辑。

### 6.1 `app/src/main/res/anim/oxygen_classic_page_back_exit.xml`

从恒等 alpha 改为真正承担提交后运动：

| 层次 | 值 | 依据 |
|---|---|---|
| alpha | 1 → 0，`decelerate_quad`，450 ms | 前景在移动中解体，末尾没有可"啪"掉的内容 |
| translate | X 0 → 96dp，`fast_out_slow_in`，450 ms | `Default…:76-86` + `cross_activity_back_entering_start_offset=96dp` |
| 缩放/旋转 | 无 | Shell 会在其矩阵之上再叠加自己的 scale/crop |

两者 duration 都等于集合时长，理由见 §2.4。

### 6.2 `app/src/main/res/anim/oxygen_classic_page_back_enter.xml`

保持恒等 alpha，**这不能改**：它在手势期间以 `progress * 0.2` 被应用
（`Custom…:106-110`），任何位移都会把真实父页推离全屏位置，直接破坏 Custom 分支的
唯一目的。只把时长换成共享 token，让它参与 `max(close, enter)` 的提交时钟。

### 6.3 `app/src/main/res/values/oxygenos_classic.xml`

新增两个专用 token（**不复用** `< 34` 的
`oxygen_classic_page_enter_duration_ms=170` / `exit_duration_ms=140`，那两个仍服务
Android 13 及以下的经典窗口动画）：

- `oxygen_classic_page_back_duration_ms = 450`
- `oxygen_classic_page_back_slide_distance = 96dp`

### 6.4 Kotlin

生产逻辑**无需改动**（`install()` / `applyClose()` / `PredictiveBackSupport` 的行为不变）。
只更新了 `OxygenClassicActivityMotion.install()` 与 `PredictiveBackSupport` 的注释：原文
描述"仅叠加近恒等的 CLOSE-enter"、"CLOSE XML 只是让分支可加载"，在 CLOSE-exit 变成真实
动画后不再准确，容易误导后续改动。

## 7. Viewer 未被修改（可核对的证据）

本提交触碰的文件只有：

```
app/src/main/res/anim/oxygen_classic_page_back_enter.xml
app/src/main/res/anim/oxygen_classic_page_back_exit.xml
app/src/main/res/values/oxygenos_classic.xml
app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicActivityMotion.kt   (仅注释)
app/src/main/kotlin/org/fossify/gallery/helpers/PredictiveBackSupport.kt         (仅注释)
app/src/test/kotlin/org/fossify/gallery/helpers/PredictiveBackCloseAnimationTest.kt
docs/…
```

冻结清单中的文件（`ViewPagerActivity.kt`、`ViewerBackGesture.kt`、
`ViewerReturnProgress.kt`、`ViewerReturnVisualClock.kt`、`PhotoViewerTransition.kt`
的动画参数、`MediaTransitionSessionCoordinator.kt`、`MediaTransitionGestureController.kt`、
`GalleryViewerTransitionTheme`、`OxygenClassicMotion.MEDIA_SHARED_ELEMENT_MS`）**一个都没有
出现在 diff 中**。`configureShared()` 与 `mediaTransition()` 未改动。

## 8. 自动化验证

见 `05-test-results.md` 体例。本轮：

- 新增/重写 `PredictiveBackCloseAnimationTest`（6 项）：
  - 分支选择：style 仍声明两份可加载的 CLOSE 资源；
  - 两份资源可加载、`duration = 450`、每个子动画 `duration == 集合时长`；
  - Shell 提交时钟 `min(2000, max(close, enter)) == 450`，并与资源 token 一致；
  - **CLOSE-enter 在 0…1 全程 alpha=1 且矩阵恒等**（保证真实父页不被推离）；
  - **CLOSE-exit 在 progress 0 全不透明、在 1 全透明**，alpha 单调不增、X 单调不减、
    且为纯 +X 平移（无缩放/斜切/纵向位移）；
  - 反向守卫：CLOSE-exit 不得退化为"另一个恒等空操作"。
- 这些断言通过反射调用 `Animation#getTransformationAt(float, Transformation)`，
  也就是 `CustomCrossActivityBackAnimation` 的真实调用路径，而不是匹配 XML 文本。
- **负向对照**：临时把 `oxygen_classic_page_back_exit.xml` 改回恒等 alpha 后，
  `closeExitIsNotAnotherIdentityNoOp` 与 `closeExitFinishesTheFrozenClosingPageInsteadOfLeavingItStill`
  双双 FAILED；恢复修复后重新通过。这证明测试不是空转。

## 9. 自动化无法覆盖、必须真机验收的项目

1. 左/右边缘滑动的第一帧手感与"跟随手指"的贴合度。
2. 提交后 450 ms 的观感是否与 Pixel 系统设置同节奏（本机无 Android 16 设备）。
3. 圆角半径与 scrim 明暗在日间/夜间下的实际观感。
4. 工具栏返回与手势返回是否"看起来是同一个动画"。
5. 状态栏/导航栏是否随窗口连续变化、取消后是否完全复原。
6. 快速连续返回、返回取消（松手回弹）在真机上的表现。
7. 深色主题下 `#212121` 背景层是否与页面无可见接缝。

以上任何一项在获得真机录屏前**不得**标注为已通过 Pixel 8 Pro 验收。
