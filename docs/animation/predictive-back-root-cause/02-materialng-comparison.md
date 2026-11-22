# 02 · MaterialNG 源码级对照

> MaterialNG：`master` @ `579609354b460af049a97cbb819aa7cb87a73283`（实测 HEAD，与任务给定 SHA 一致，2026-11-03 提交）。
> 代码路径根：`MaterialNG/V2rayNG/app/src/main/`。全部结论来自实际源码 file:line，不从截图推断。

## 1. 使返回正确的实际机制

MaterialNG 在用户 Pixel 8 Pro（Android 16）上表现出正确系统预测性返回，其源码机制是：

1. **子页面不注册任何 Activity 级返回回调**。
   - `ui/base/BaseComponentActivity.kt:11-26`：`BaseComponentActivity : AppCompatActivity()`，`onCreate` 只调 `enableEdgeToEdge()`（`:16`）与 `setContent{}`（`:17-21`），无 `OnBackPressedDispatcher` / `OnBackInvokedCallback`。
   - `ui/base/HelperBaseComponentActivity.kt:22-33`：仅初始化三个 helper，无返回处理。
   - `ui/settings/SettingsActivity.kt:97-109`：无返回回调；分类是同一 Activity 的新实例（`:131-136`），返回按钮 `:137 onBackClick={finish()}`。
   - 全仓库 `onBackPressed` / `finishAfterTransition` override 零匹配；唯一 `finish()` override 在 `AppPickerActivity.kt:95-103`，只写 result 后 `super.finish()`。
2. **每个页面都是真实、不透明、仍然存活的独立 Activity**。
   - `MainActivity singleTask` 根（`AndroidManifest.xml:66-69`），子页压在同一任务栈上；父页在 `onCreate` 已用 Compose 渲染完成（`Base:17-21`）。系统把子窗口缩小/移开时，露出的是真实父页。
   - 主题 `themes.xml`：`AppThemeDayNight.NoActionBar → AppThemeBase → AppThemeBaseCommon → Theme.AppCompat.DayNight.NoActionBar`（`:5/13/17`）；`windowBackground` 为实色 `window_background_light` / `window_background_dark`（`:9`；`values-night/themes.xml:8`）。
   - **普通页面没有 `windowIsTranslucent`**；Manifest 中 translucent 只用于三个 `process=":daemon"` 的 shortcut Activity（`Manifest.xml:168-185`）。
   - 全 res 无 `windowDisablePreview`、`windowContentTransitions`、`windowActivityTransitions`、`windowAnimationStyle`。
3. **应用级 opt-in + 平台默认系统动画**。
   - `AndroidManifest.xml:47` `android:enableOnBackInvokedCallback="true"`；`app/build.gradle.kts:10/15` `compileSdk=37 / targetSdk=37`。
   - 子页启动带 `makeCustomAnimation(this, R.anim.m3e_activity_enter, R.anim.m3e_activity_exit)`（`MainActivity.kt:192-196`、`SettingsActivity.kt:132-133`），但动画资源是 220/180ms、位移 1–4% 的极轻微位移淡入淡出（`res/anim/m3e_activity_enter.xml`、`m3e_activity_exit.xml`）；返回由平台默认跨 Activity 动画负责，无 `overrideActivityTransition` / `overridePendingTransition` / `clearOverrideActivityTransition`（全仓库零匹配）。
4. **Compose 层只有条件性、默认关闭的返回处理器**。
   - `MainScreen.kt:185-192` 两个 `BackHandler(enabled=reorderMode || showSearch)`，默认 disabled；`MainDrawerMotion.kt:123` 的 `PredictiveBackHandler(enabled=isOpen && !navigationPending)` 只驱动抽屉，且只用 `event.progress`（`:127`），**不用 `swipeEdge`**。

## 2. 与 Gallery 的具体差异

| 维度 | MaterialNG（源码实证） | Gallery 基线 `84bbf8be…` |
|---|---|---|
| 返回回调 | 子页面零回调；根页仅两个条件性 Compose handler | Viewer 永久启用 `OnBackPressedCallback(true)`（`ViewerBackGesture.kt:23`） |
| 谁拥有 Viewer/子页动画 | 平台默认跨 Activity 窗口动画 | 应用图片缩放 / 共享元素回程 |
| 窗口透明度 | 普通子页不透明，`windowIsTranslucent` 仅 daemon shortcut | Viewer `windowIsTranslucent=true` + 黑底（`styles.xml:31-37`） |
| 主题基线 | AppCompat DayNight（`themes.xml:5`） | `AppTheme → AppTheme.Base`（Commons Commons Material3 系） |
| 窗口转场配置 | 无 `windowContentTransitions` / `windowAnimationStyle` | `GalleryPageTransitionTheme` 与 Viewer 主题均 `windowContentTransitions=true` |
| 自定义启动动画 | `makeCustomAnimation`（轻微 1–4% 位移） | API≥34 普通页已改为裸 `startActivity`（`PredictiveBackSupport.kt:52-55`） |
| 系统栏 | `enableEdgeToEdge()` + 透明状态/导航栏色（`Base:16`、`themes.xml:6-7`、`Theme.kt:217-219`） | `setupEdgeToEdge` / `OxygenSystemBars` 自绘状态栏 underlay |
| 父页存活 | 是，同一任务栈、已渲染 | 普通页父 Activity 存活；Viewer 父网格 Activity 存活但被半透明黑底 Viewer 覆盖 |

## 3. 可以解释差异的代码事实（CODE-PROVEN）

1. **返回所有权**：MaterialNG 子页无启用回调 ⇒ 平台播放跨 Activity 动画；Gallery Viewer 的启用回调按官方文档会阻止该动画。这是两者最本质、源码可证的差异。
2. **窗口不透明性**：MaterialNG 普通子页不透明，系统缩小窗口时下层是真实父页；Gallery Viewer 半透明黑底 + 自己的 scrim，下层真实网格是否可见取决于合成，属待验证。
3. **窗口转场配置**：MaterialNG 完全没有 `windowContentTransitions` / 自定义窗口动画样式；Gallery 两个页面主题都显式开启 `windowContentTransitions=true`，属于潜在冲突源（未在真机归因）。

## 4. 需要设备验证、不得写成结论的部分

- Pixel 8 Pro 上平台是否真的把 `m3e_activity_enter/exit` 用作跨 Activity 返回的窗口动画，以及它和真实父页的组合方式。
- “看到的父页”是否为实时渲染内容还是系统快照（慢放动画 / `dumpsys activity` / Winscope 层级确认）。
- Gallery 黑色区域究竟来自 translucent 黑底主题、被清空的 override、场景转场回退，还是 SystemUI scrim——见 03。
