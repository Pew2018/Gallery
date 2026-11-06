# 10 · 强调色语义、主题动态解析与动画着色不可写死规范

> **设计约束已确认；截至本文件创建仍是文档方案，未修改 Gallery UI/动画代码。**
> 本次审计代码基线：`refactor/media-pipeline-stability` @ `38ad8072435822749cfd3e0642eb0985aec0e00a`（2026-11-06）。每次实际实现前重新读取最新远程 HEAD。
> 用户要求：**OxygenOS 4.5.15 / Android 7.1.1 的形态保持不变；所有“强调色语义”的控件/反馈/动画必须遵循用户选择的预设色或自定义色，不能写死参考截图里的蓝色。**

## 1. 用户设计决策与设计指南的边界

用户提交的 `OxygenOS_4.5.15_Android_7.1.1_UI_Design_Guide_v1.0.md` 是 **29 张参考截图的归纳规格**：其中的蓝色、青绿色、紫色分别对应不同的旧系统/应用。文档明确说不能用单一颜色混同不同应用，**静态图也无法量得原生动画曲线**。

因此：
1. **形状/几何取经典 OxygenOS 设计**：56dp Toolbar、平面列表、传统 Popup、48dp 候选菜单行、经典 Switch / Radio / CheckBox、底栏 56dp、直角/小圆角弹窗；不变成 M3 胶囊/卡片/大圆角。
2. **强调色取当前 Gallery 用户配置**：设计指南的 `accent-blue=#2196F3`、电话蓝、短信青绿以及“经典蓝色 Edge Glow”均只作**图片或候选默认外观的说明**；不能作为全局运行时强调色常量，也不能绕开 Gallery 用户设置。
3. **中性色与状态语义允许例外**：黑、白、透明、灰和禁用/错误/危险提示色用于特定背景或状态，不代表“强调色”；可保留既有明确经典对比策略（例如彩色 Toolbar 上的白色前景），但不能把**选中/焦点/加载/进度**误涂为固定蓝色。
4. 不管后续动画选用旧版 Google Material Motion 还是 AndroidX/原生动画，**颜色 API 的最终来源只能是 Gallery 已有的主题解析层**；动画库只负责时间与运动，不获准改变形状或品牌色。

## 2. 已有“主题真值源”的 Kotlin 证据

根目录：`app/src/main/kotlin/org/fossify/gallery/`。

| 文件与入口 | 已核对源码的行为 | 以后必须遵守 |
|---|---|---|
| `helpers/OxygenThemeEngine.kt:accent(config)`（约 27） | 先按 `config.oxygenThemePresetId` 取有效预设色，否则 `config.oxygenCustomAccent` | 不直接读取“永远蓝色”的资源；预设/自定义选择以现有 Config 规则为准 |
| `OxygenThemeEngine.snapshot(context,config)`（约 34） | `oxygenThemeActivated` 时用上述 accent；非经典自定义模式回退 Commons `getProperPrimaryColor()`；输出 `OxygenThemeTokens` | **统一读取这个快照**，不能为动画建立另一套固定主色 |
| `OxygenThemeEngine.apply()`（约 45） | 在经典主题启用时把背景、文字和 `primaryColor/accentColor` 等同步到 Commons 配置 | 不从动画回调写 Config；只由合法主题管理路径写用户设置 |
| `helpers/OxygenThemeColors.kt:OxygenThemeTokens`（约 27） | 有 `rawAccent`、`interactiveAccent`、`pageBackground`、`primaryText`、`secondaryText`、`disabledText`、`toolbarBackground/Foreground`、`dialogBackground` 等语义字段 | 动画对象应持有语义颜色快照/版本，不应 hardcode RGB |
| `OxygenThemeColors.readableAccent()`（约 152） | 在**实际背景**上保证可读性，可能改变显示色值，但不改变保存的种子 | 文字、焦点、细线、强调图标优先采用以所在表面为基准的可读色 |
| `OxygenThemeColors.toolbarBackground(rawAccent)`（约 148） | 基于选中强调色解析可读的 Toolbar 背景；Toolbar 前景在当前实现中固定为白色 | Toolbar 颜色由主题解析决定；白色前景是既有对比策略，**不是固定强调色** |
| `OxygenThemeColors.switchPalette(tokens)`（约 64） | 旧 OxygenOS 风格 Switch 的开启 thumb 使用用户 `rawAccent`，轨道与禁用色由语义计算 | **不要一律把所有强调都改成 `interactiveAccent`**；Switch 对 raw 色有显式例外 |
| `OxygenThemeColors.progressPalette(tokens)`（约 96） | 进度颜色基于当前主题/实际背景做对比处理 | 加载/扫描指示不能硬编码 `#2196F3` |
| `helpers/OxygenClassicControls.kt` | 经典 Switch、Radio、CheckBox、SeekBar 通过主题 tokens 着色/状态动画 | 不替换为新 Material3 控件；不要重复重建 Drawable 引发 Ripple 丢失 |
| `helpers/OxygenClassicDialog.kt` | 弹窗 `apply()`、`styleInput()`、Button Ripple、TabIndicator 从实际页面/主色推导可读强调色 | 后续接入 Dialog 动画仍使用这些视觉令牌，不能由 MDC 默认主题色覆盖 |
| `helpers/OxygenClassicPageInput.kt:style()` | 输入焦点 underline、cursor 从 `tokens.interactiveAccent` 获取 | 搜索和来源输入动画仅改 alpha/位置，不抹掉真实 ColorStateList |
| `helpers/GalleryBottomBar.kt:selectTab()/recolor()/updateRipple()`（约 119、153、96） | 选中用 `interactiveAccent`，Ripple 用 `rawAccent` 的 alpha；`RippleDrawable.setColor` 保留按压状态 | 切换时变更选中态颜色不能将 Ripple 改为固定色，也不能每帧替换背景 |
| `helpers/OxygenToolbarStyle.kt:applyToolbar()/tintMenu()` | Toolbar 表面来自 `toolbarBackground`，文字/图标用对应 `toolbarForeground` | Popup 不是 Toolbar 表面；菜单正文颜色要按弹窗表面算，不直接以 Toolbar 前景替代 |

**实际变更前附加搜索**：`OxygenFastScroller.kt`、`OxygenProgressStyle.kt`、`OxygenSelectionMode.kt`、`ClassicSettingsText.kt`、`TiebaSettingsCategoryRow.kt`、`OxygenClassicMenus.kt`、`PhotoProfileEditActivity.kt:applyPageColors()`、`OxygenThemeActivity.kt` 以及所有 `res/color`、`res/drawable` 引用固定蓝色或固定 state list 的位置。不能据此推定这些文件现在都存在问题；只须纳入检查范围。

## 3. 每一种视觉用途该取哪个 Token

| 控件/动画的上色位置 | 优先数据源 | 动画行为 |
|---|---|---|
| 设置分组标题、选中项文本、行内带语义的强调图标 | `interactiveAccent`；位于 Dialog 表面时 `readableAccent(rawAccent, dialogBackground)` | 若主题变化，结束旧颜色过渡，以最新 Token 重渲染；不使用蓝色常量 |
| 两条收藏“显示/不显示”操作菜单 | 普通 Popup 主文本：`primaryText`／实际 Dialog 表面文字色；Ripple 按可读主题色 | **无 CheckBox**；文案随 boolean 切换，颜色不被当作勾选状态指示 |
| Dialog 正向确定按钮/选中 Radio、Tab 指示 | `readableAccent(rawAccent, dialogBackground)`；禁用优先 `disabledText` | 显隐淡入不改变按钮和选中控件着色 |
| 开启的经典 Switch thumb | `switchPalette(tokens)` 的 onThumb（基于 rawAccent） | 动画插值仅在现有开/关语义调色；不用 MDC 新 Switch 外形 |
| Slider/SeekBar 已进度、扫描进度、加载指示 | `progressPalette(tokens)` 或 `interactiveAccent`，按具体背景检查 | 不写死 loading 蓝，不从 MDC 的主题默认 primary 抢色 |
| 页面 EditText 光标/聚焦下划线 | `OxygenClassicPageInput.style()` 的 states | 转场只移动/淡入，不替换 EditText、丢失输入焦点 |
| 底栏选中图标/文字 | `interactiveAccent`；未选中为 `secondaryText` | 选中 alpha/tint 动画只插值来自快照的颜色，目标变更时取消旧动画 |
| 底栏 Ripple / 经典 CheckBox/菜单 row Ripple | 从当前有效语义强调色生成 stateful alpha（现有控件有各自适用规则） | 保留 `RippleDrawable` 实例与 state；不得自行用固定蓝色 splash |
| Edge Glow / Overscroll（若后续实现） | 当前表面上可见的有效强调色；必要时 `readableAccent(rawAccent, pageBackground)` | **旧版截图的亮蓝色只是参考**；不可在自定义绿色/紫色主题下仍固定蓝 |
| MaterialSharedAxis / MaterialFade / FadeThrough | 默认只驱动 alpha/translation/scale；实际 View 仍使用原有 theme | 不给 View 强行叠加 MDC 颜色/形状主题 |
| MaterialContainerTransform（严格受限试点） | `startContainerColor/endContainerColor` 分别取当时两个实际表面，`scrimColor` 按现有主题的遮罩策略计算 | 不直接采用 Material 默认容器色、M3 大圆角，不让半透明镜像泄露受保护媒体 |
| 视频/Viewer 黑色背景与白色工具栏图标 | 原本媒体观看对比度与沉浸式策略；Popup 仍由 ThemeTokens 解析 | 不将强调色刷在照片、视频画面或已定义的黑色沉浸式底板上 |
| 错误、危险、未授权/禁用等状态 | 保留语义色/现有权限反馈规则 | **不得用强调色代替错误提示色**；也不得反过来把错误红当主题色 |

## 4. 主题变化、动画竞争、生命周期

- **何时取色**：在渲染/打开弹窗/启动动画时通过 `OxygenThemeEngine.snapshot(activity, activity.config)` 读取；`onResume`、现有 `renderTheme/applyPageColors/recolor`、用户修改主题后通知，重新解析，不长期使用 `object const val ACCENT = BLUE`。
- **交互进行中改变主题**：取消并归一化旧 animator/transition，终态以新语义快照直接绑定；若必须动画跨色过渡，则起始色取控件当前显示值、目标色取**新快照**，避免显示第一帧旧主题的错误强调色。
- **View/Drawable 不重建**：特别是 `GalleryBottomBar.updateRipple()` 已明确通过 `setColor()` 更新既有 Ripple；Switch/CheckBox/SeekBar、Popup/AlertDialog 先复用已有 theme state/style hook；不要因 Material Transition 创建另一套并行控件树。
- **旧的 Popup 已打开时变色**：若控件不能可靠热重着色，可以在菜单关闭、重新打开时应用新主题，禁止为即时变色反射 AppCompat PopupWindow 私有字段。
- **不同 surface**：`pageBackground` 与 `dialogBackground` 分别做对比检查；夜间/经典自定义主题和未激活 Oxygen 主题的 Commons 模式都应正确回退；不假设用户只会选高对比蓝色。
- **透明 ARGB 自定义色**：按 `OxygenThemeColors` 已有 `opaque()`、`readableAccent()`、`switchPalette()` 规则处理可读性/轨道透明度，不擅自改写用户保存值。
- **动画关**：系统 Animator Duration Scale = 0 时立即落到正确主题终态，不得延迟改变配置或业务列表；取消/重入/Activity 销毁时无残留颜色与遮罩。

## 5. 禁止的实现方式与允许的例外

**不允许**：
```kotlin
// 反例：实际业务 UI 中固定某一种视觉样本蓝色
val selected = Color.rgb(33, 150, 243)
fab.backgroundTintList = ColorStateList.valueOf(Color.BLUE)
edgeEffect.setColor(0xFF2196F3.toInt())
transition.setScrimColor(Color.BLUE)
```

**允许**：
```kotlin
// 仅示意主题解析接口，非本轮已提交的代码
val tokens = OxygenThemeEngine.snapshot(activity, activity.config)
val emphasizedText = tokens.interactiveAccent
val dialogAccent = OxygenThemeColors.readableAccent(tokens.rawAccent, tokens.dialogBackground)
val switchOn = OxygenThemeColors.switchPalette(tokens).onThumb
```

静态中性色、遮罩 alpha、危险红、视频纯黑底和 Toolbar 既定白色前景可保持原有语义，**但必须明确它们不是交互强调色**。

## 6. 测试/验收方案

1. **颜色组合**：至少 3 种真实 Gallery 用户预设 + 任意自定义色、极浅色、极暗色、低对比色、带 alpha 色；分别在经典浅色、深色及关闭 Oxygen 主题时测。
2. **主题变更**：设置页面切换选中色，打开/关闭 Popup，Dialog 确定按钮、输入下划线、Switch/Slider、底栏 Ripple、加载进度、Scrollbar/EdgeGlow 在下次重渲染后更新；不出现缓存旧蓝色。
3. **状态组合**：选中/未选中/禁用、Pressed/Focused/Hover（适用时）、收藏无 Checkbox 双态文字；所有状态读取一致配置。
4. **颜色/可读性**：有文本的强调色必须对实际背景有合理对比；按 `OxygenThemeColors.readableAccent()` 现有 4.5:1 目标测试，图形目标遵循控件已有门槛，不把同一阈值强用于所有语义。
5. **动画状态**：在 MaterialFade/SharedAxis 中途改主题、快速返回、旋转、反复点击、系统动画 0；不泄漏 Animator/旧色、没有错误的一帧。
6. **可用的现有测试**：`OxygenThemeColorsTest`、`OxygenThemePresetsTest`、`GalleryBottomBarRippleTest`、`ClassicSettingsControlsTest`、`ClassicDialogTest`、`OxygenClassicMotionTest`；需补充测试断言，不可仅靠截图肉眼通过。

## 7. 与 Material Motion 选型的配套规定

具体场景/类/触点请见 [11-legacy-material-motion-selection.md](11-legacy-material-motion-selection.md)。动画库**只产生运动，颜色继续来自此文定义的 Token**。若所选 Material Motion 转场需要设置容器颜色、scrim 或 effect mask，则必须显式传入从 Gallery 主题解析得到的颜色，并验证黑白底、权限遮罩与缩略图首帧。
