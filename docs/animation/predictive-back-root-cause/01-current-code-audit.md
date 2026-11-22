# 01 · 当前代码审计（预测性返回专项）

> 基线：`perf/home-gallery-sorting` @ `84bbf8be934a47ed69635f9b0b69a2932c8776f2`（= 本次开始时远程真实 HEAD）。
> 审计日期：2026-11-22。所有 file:line 均以该 SHA 的实际源码为准，已重新核查，未假定历史事实不变。
> 参考实现：MaterialNG `master` @ `579609354b460af049a97cbb819aa7cb87a73283`（见 02）。

## 0. 基线与并发核查

- `git ls-remote origin refs/heads/perf/home-gallery-sorting` 实测 HEAD = `84bbf8be…`，与任务给出的历史审计 SHA 相同，但它是**当前真实分支尖端**（提交于 2026-11-22 19:22 +0800，标题 “Reveal parent page behind predictive back”）。
- 本地另有 `96ae9201…` 提交，是 `84bbf8be…` 的**祖先**（更早），不在远程尖端；本轮从远程重新克隆，不回退、不覆盖并发提交。
- 克隆后的工作副本：`/Users/patrick/WorkBuddy/2026-11-22-19-54-11/Gallery`，起点工作树干净。

## 1. 导航路径矩阵（返回所有权）

| 路径 | 父 → 子 | 启动点 | finish 路径 | API≥34 预期所有权 |
|---|---|---|---|---|
| A01 | Settings 首页 → 外观与界面 | `SettingsActivity.kt:304 openSettingsCategory()` → `:307 startActivity(Intent…EXTRA_CATEGORY)`，无 flags／无 options | `closePage()` → `finish()` | 平台（跨 Activity） |
| A02 | 设置分类 → 主题设置 | `SettingsActivity.kt:539 openThemeSettings()` → `:555 PredictiveBackSupport.startPage()`（≥34 为裸 `startActivity`） | `OxygenThemeActivity.goBack()` → `finish()` | 平台 |
| A03/A04 | 相册主页 → 普通/收藏文件夹 | `MainActivity.itemClicked()` → `handleMediaIntent()` → `startActivityForResult` | `MediaActivity.finish()` | 平台 |
| A05/A06 | 文件夹/分类 → 全屏 Viewer | `MediaActivity.launchPhotoViewerWithSharedElement()` → `makeSceneTransitionAnimation` + `startActivityForResult` | `ViewPagerActivity.requestViewerClose()` / `finishAfterTransition()` | **本轮整改对象**（见 04） |
| A07 | 文件夹列表 → 排除的文件夹 | `SettingsActivity.setupManageExcludedFolders()` → `startActivity` | Commons toolbar `finish()` | 平台 |
| A08 | 浏览配置管理 → 创建配置 | `PhotoProfileManageActivity` → `startActivityForResult` | `finish()` | 平台 |
| A09 | 主页嵌套目录 → 上一级 | `MainActivity.albumsBackCallback` / `popSubfolder()`，**同 Activity** | 不 finish，仅改状态 | 应用内部步骤（callback 保留） |
| A10 | 相册根页面 → 桌面 | 根页面无 in-app step | 系统 back-to-home | 平台 |

关键事实：普通设置页/文件夹页**不是**靠永久启用的返回回调关闭。`SimpleActivity.kt:86` 对每个页面调用 `setAppBackStepAvailable(false)`，`:90` 调 `clearPageTransitionOverrides()`；Commons 7.1.3 `BaseSimpleActivity` 注册的基础回调因此保持禁用。设置/主题页的 `OnBackPressedCallback(true)` 仅在 `<34` 注册（`SettingsActivity.kt:164`、`OxygenThemeActivity.kt:104`）。

底栏根切换（`GalleryBottomBar.kt:237 open()`、`:143 finishOutgoing()`）使用一次性零动画 `ActivityOptions` + `overridePendingTransition(0,0)`，并且 `:90` 的基类清理会清空持久 override，因此不会遗留到普通页面返回。

## 2. 已确认的代码缺陷（CONFIRMED_CODE_DEFECT）

### D1 · Viewer 永久启用默认优先级返回回调（核心缺陷）
- 位置：`helpers/ViewerBackGesture.kt:23`（基线）`OnBackPressedCallback(true)`；`ViewPagerActivity.kt:1159` 注册。
- 官方机制（developer.android.com，2026-11-22 核对）：若应用以 `PRIORITY_DEFAULT`/`PRIORITY_OVERLAY` 启用 `OnBackPressedCallback`，**预测性返回动画不会运行，且应用必须自己处理返回事件**。
- 结论：Viewer 的稳定浏览页把跨 Activity 返回手势从系统手中夺走，交给 Gallery 自己的图片缩放（`renderInteractiveDismissProgress()`，`ViewPagerActivity.kt:419`）。这与“整个 Activity 随系统窗口动画缩放、背后显示真实网格”的目标不符。
- 状态：**CONFIRMED_CODE_DEFECT**（代码与官方 API 行为均可验证）。

### D2 · 边缘方向由 touchX 猜测
- 位置：`ViewPagerActivity.kt:493`（基线）`touchX.isNaN() || touchX < screenWidth / 2f`。
- 官方应使用 `BackEventCompat.swipeEdge`。注意：仅修正方向**不会**把图片动画变成系统窗口动画，因此方向修正只是附属正确性问题，不是 Viewer 修复的完成条件。
- 状态：CONFIRMED_CODE_DEFECT（次要）。

### D3 · 双重返回所有权 / 二次动画的结构风险
- 位置：`ViewPagerActivity.kt:1412 finishAfterTransition()` → `requestViewerClose("system_back")` → `:1510` 安装 `window.sharedElementReturnTransition` → `:1548 super.finishAfterTransition()`；同时 `commitSystemBack()`、工具栏 `:1761` 也汇入同一函数。
- 机制：一旦把返回交还系统（禁用 D1 的回调），框架在完成手势时仍会调用 `finishAfterTransition()`，于是**平台跨 Activity 动画 + 应用共享元素回程**会同时触发。
- 状态：CONFIRMED_CODE_DEFECT（结构性，触发条件已由 D1 修复激活，见 04）。

### D4 · Commons 回调状态不一致（已记录，影响有限）
- Commons 7.1.3 `performDefaultBack()` 字节码在 `onBackPressed()` 后无条件 `setEnabled(true)`，与页面“无 in-app step 应禁用回调”的策略不一致。调用点 `MediaActivity.kt` 工具栏返回分支随后同步 `finish()`，实例即销毁，实际手势预览受损有限。
- 状态：CONFIRMED_CODE_DEFECT（低风险），本轮未改动，避免扩大范围。

## 3. 未确认假设（UNVERIFIED_HYPOTHESIS，禁止写成根因）

- **H-BLACK**：普通页面返回时黑色区域来自哪一个图层（父 Activity 不存在／未参与预览／窗口内容未覆盖／子窗口透明配置／系统任务背景／状态栏合成）。静态代码不足以判定，需要真机 WindowManager/Shell/SurfaceFlinger 证据。详见 03。
- **H-TRANSLUCENT**：Viewer 的 `windowIsTranslucent=true` + `windowContentTransitions=true` + 共享元素场景转场，是否满足 Android 16 跨 Activity 预览条件。公开 WindowManager 行为指出“参与者包含不适合的透明 Activity”“当前 Activity 处于场景转场”会回退到回调；但本设备最终路径必须真机验证。
- **H-COMMONS-REARM**：D4 在真实快速返回序列中是否造成一次单帧切屏，缺少帧级证据。

## 4. 测试边界

本轮新增 JVM/Robolectric 测试仅证明：回调启用状态、状态机单一 owner、结果提交一次、swipeEdge 转发、会话清理。**不能**证明系统 Surface 的圆角缩放、黑色区域消失、左右手势的视觉差异或窗口合成正确。见 05、06。
