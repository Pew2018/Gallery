# 03 · 黑色背景专项证据与根因判定

## 1. 判定结论（先行）

**普通页面返回周围黑色区域的根因 = `UNVERIFIED_HYPOTHESIS`。**

本轮没有 Android 16 设备或模拟器，也没有 WindowManager / Shell transition / SurfaceFlinger / Winscope 证据，因此**不能**把任一图层写成已确认根因。凡是“可能是 X”的表述都只作为待验证分支。已经开始实施的修复只针对 01 中代码可证的缺陷（Viewer 返回所有权），**没有**为了“消除黑块”而改颜色、动画参数或主题。

## 2. 已经静态确认的事实（可支持的结论）

| 事实 | 证据 |
|---|---|
| 普通设置页/文件夹页在 API≥34 没有启用的应用级返回回调 | `SimpleActivity.kt:86` `setAppBackStepAvailable(false)`；Commons 7.1.3 基类回调保持 disabled；`SettingsActivity.kt:164` 的回调仅 `<34` 注册 |
| 页面没有遗留持久转场 override | `SimpleActivity.kt:90` `clearPageTransitionOverrides()`；`GalleryBottomBar` 用一次性 `overridePendingTransition(0,0)` |
| 普通页面启动在 API≥34 不带自定义动画 bundle | `PredictiveBackSupport.startPage()` `:52-55` |
| 普通页窗口不透明 | `OxygenClassicRootTheme` `windowIsTranslucent=false`（`oxygenos_classic.xml:3-5`） |
| 资源背景与运行时背景有两套来源且已对齐到同一页色 | `styles.xml:6` `windowBackground=@color/oxygen_classic_page`（`#FAFAFA` / night `#212121`）；`SimpleActivity.applyPageWindowSurface():99-103` 运行时设置同色 |
| 基线提交已移除 `windowDisablePreview` 并对齐资源背景 | `git show 84bbf8be…`：删除该项、修改 `AppTheme.windowBackground` 与 night 颜色 |
| Viewer 窗口是半透明黑底且使用场景转场 | `styles.xml:30-37` `GalleryViewerTransitionTheme` |

以上说明：普通设置页的黑色区域**不能**归咎于 `GalleryPageTransitionTheme`（设置页不使用它），也没有代码证据表明是“父 Activity 被销毁”或“永久启用回调吞手势”。

## 3. 已排除的假设（基于静态证据）

- ❌ “设置页黑色来自 `GalleryPageTransitionTheme`”——设置页主题是 `AppTheme`，不使用该主题。
- ❌ “设置页返回被应用级启用回调吞掉”——API≥34 下基类与设置页回调均禁用（见上）。
- ❌ “持久 `overrideActivityTransition` 覆盖平台动画”——基类显式清理两个 override 槽；源码中 Gallery 从不 install 持久 override。

## 4. 未解决的分支（必须真机取证）

A. 父 Activity 不存在或已 finish；
B. 父 Activity 存在但未被 launch-behind 选为预览目标（透明/场景转场回退）；
C. 父窗口存在但内容未覆盖预期区域（insets/decor 裁切）；
D. 子窗口透明配置使可见区域错误；
E. 黑色来自系统任务背景或 Shell 转场背景；
F. 状态栏 underlay / DecorView 在缩放时的合成不一致。

## 5. 本轮新增的诊断能力（默认关闭）

新增 `helpers/PredictiveBackDiagnostics.kt`，仅在用户启用 Gallery 诊断时记录：Activity 类名、taskId、生命周期 finishing/destroyed、Window flags（十六进制）、`windowAnimations`、`alpha`/`dimAmount`、translucent/blur 标志、`windowBackground`/Decor/content 背景 ARGB、Decor 与 content bounds、实测宽高、uiMode、以及 `onBackPressedDispatcher.hasEnabledCallbacks()`。不含媒体路径、文件名或用户数据，颜色以 ARGB 十六进制输出。

采集点：`SettingsActivity.onCreate`（父/子页面创建）与 `closePage()`（返回前），`ViewPagerActivity.commitPlatformReturn()`（Viewer 平台返回提交）。具体采集步骤见 06。

## 6. 仍然缺少的证据

- 真机 `dumpsys window windows` / `dumpsys activity activities` 在手势开始、中间、取消、提交四个时刻的快照；
- `adb shell wm` / Perfetto/Winscope trace 中 Shell transition 类型与参与者；
- 慢放手指向下/左/右的系统录音或截图序列。

在这些证据到位前，黑色区域修复项在交付矩阵中保持 `PENDING_DEVICE_VALIDATION`，不得标注 PASS。
