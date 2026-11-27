# 05 · 测试结果与覆盖边界

## 1. 基线与环境

- 机器：macOS，`java 17.0.21`，Gradle 9.5.1，Android SDK `/Users/patrick/Library/Android/sdk`。
- 命令：`ANDROID_HOME=... ./gradlew testFossDebugUnitTest`（FOSS flavor；`testDebugUnitTest` 在仓库中有歧义，必须用带 flavor 的任务）。
- **基线（改动前，HEAD `84bbf8be…`）**：`1218 tests completed, 5 failed`；5 个失败为 `DeleteWithRememberDialogRippleTest`、`GalleryRootTransitionTest`、`MediaAdapterStabilityTest`、`OxygenSystemBarsTest`、`SystemBackTransitionTest`，均在 `classMethod` 阶段抛 `java.lang.UnsupportedOperationException at DefaultSdkProvider.java:170`。这是 Robolectric 在本环境缺少对应 SDK jar 的**环境问题**，不是代码断言失败；离线与联网两种模式结果一致。

## 2. 本轮新增测试（全部通过）

| 测试类 | 数量 | 结果 | 覆盖内容 |
|---|---|---|---|
| `helpers/ViewerBackOwnershipTest.kt` | 5 | PASS | API33 应用拥有、API34/36 平台拥有、in-app step 保持应用拥有、拖拽判定、两 owner 互斥穷举 |
| `helpers/ViewerBackGestureTest.kt` | 7 | PASS | 回调默认禁用、启用开关无副作用、`swipeEdge` 转发、进度钳制、取消不提交、提交恰好一次、begin 失败时进度不生效 |
| `helpers/PlatformViewerReturnTest.kt` | 4 | PASS | 平台返回序列 `beginReturn→targetReady→terminate(COMPLETE)→acceptResult→clean`、重复 reenter/result 被拒、取消不进入 RETURNING、result 只携带唯一 operation token |

专项命令（全部 BUILD SUCCESSFUL）：
```
./gradlew testFossDebugUnitTest \
  --tests "org.fossify.gallery.helpers.ViewerBackOwnershipTest" \
  --tests "org.fossify.gallery.helpers.ViewerBackGestureTest" \
  --tests "org.fossify.gallery.helpers.PlatformViewerReturnTest"
./gradlew compileFossDebugKotlin
```

## 3. 这些测试**不能**证明什么（覆盖边界）

JVM / Robolectric 只能证明 Kotlin 调用路径、回调注册与启用状态、生命周期与会话状态机、资源与状态清理、Theme/Manifest 源码配置、动画所有权的**代码配置**。它们**不能**证明：

- 系统 Surface 的实际缩放比例与圆角；
- 黑色区域是否消失、来自哪个图层；
- 左/右边缘手势的真实视觉差异；
- WindowManager/Shell 是否真的播放了跨 Activity 动画；
- translucent 窗口是否触发系统预览回退。

因此本报告不把上述任何一项写成 PASS。全部列入 06 的真机验收矩阵并保持 `PENDING_DEVICE_VALIDATION`。

## 4. 仪器测试状态

`app/src/androidTest/.../PhotoViewerInteractiveGestureTest.kt` 通过 `dispatchOnBackStarted/Progressed/Cancelled` 模拟返回，且假设稳定 Viewer 页有启用回调。整改后稳定页回调默认禁用（这正是修复点），因此这些用例的**前提已变化**，需要在真机上：
- 把模拟手势的用例改为在 in-app step 内触发（先进入 `INTERACTIVE_RETURN`），或
- 改写为断言平台路径已被配置（如 `hasEnabledCallbacks()==false`）。

本轮没有在无法运行的情况下擅改这些用例，也没有声称它们通过。

## 5. 静态检查（区分既有问题与新增问题）

- **完整 `testFossDebugUnitTest`（改动后）**：`1234 tests completed, 5 failed`。测试总数从基线 1218 增至 1234（+16，全部为本轮新增且通过）；5 个失败与基线完全相同（同一组 `DefaultSdkProvider` 环境失败），**无新增测试失败**。
- **`compileFossDebugKotlin`**：BUILD SUCCESSFUL。
- **合并 Manifest**（`merged_manifest/fossRelease/.../AndroidManifest.xml`）：`enableOnBackInvokedCallback="true"` 出现 2 次（application + 合并传播），无 `false`；`minSdkVersion=26`、`targetSdkVersion=36`；Viewer 主题 `GalleryViewerTransitionTheme` 保留。
- **Lint（`lintFossRelease`）**：15 errors / 875 warnings（另有 baseline 过滤）。逐一核对：15 个 error 全部位于**未改动文件**（`Context.kt`、`FavoritesReturnCover.kt`、`MediaActivity.kt`、`ViewerEntryDiagnostics.kt`、`MediaSharedSurface.kt`、`ToolbarQuickActionsDialog.kt`、`ClassicGlowScrollViews.kt`、`GalleryBottomBar.kt`、`OxygenFastScroller.kt`），以及 `ViewPagerActivity.kt:1539` 的 `GestureBackNavigation`（对 `onBackPressed()` override 的告警——该 override 在基线 `84bbf8be…:1429` 已存在）。本轮新增/修改的行**没有引入任何新的 lint error**。
- **Detekt**：`build.maxIssues=0` 且无 `detekt-baseline.xml`，仓库在基线即有 4024 weighted issues（全项目历史债务）。已核对本轮新增的 6 个文件：修复一处 `MaxLineLength` 后，新增文件 **0 issues**。Detekt 的整体失败是既有基线状态，非本轮引入。

## 6. 构建验证

FOSS Release ARM64 构建结果、产物名称与 SHA-256 记录在最终交付回复中（第 10–12 节）。
