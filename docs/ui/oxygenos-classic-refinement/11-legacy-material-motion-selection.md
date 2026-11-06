# 11 · 经典 Material Design 动画选型与代码落点（仅引擎，不改 OxygenOS 外观）

> **状态：方案制定 / 尚未改动应用 UI。** 源码证据基线：`refactor/media-pipeline-stability` @ `38ad8072435822749cfd3e0642eb0985aec0e00a`，实施前必须刷新远程 HEAD。
> 用户明确设计原则：**控件外观、尺寸、圆角、文字、Popup/对话框层级仍采用 OxygenOS 4.5.15 / Android 7.1.1 Classic；尽可能复用 Google 开源 Material Design（Views / Material Motion）的动画，不升级成 Material 3 UI。**
> 所有强调色取用户的预设/自定义配置，详见 [10-动态强调色规范](10-accent-semantic-and-runtime-invariants.md)。

## 1. 动画技术栈与版本边界

Google 在 **MDC-Android 1.2.0（Material Design 2 / Views 时期）**已提供四种 Material Motion：
- `MaterialSharedAxis`：前进/后退、空间或层级关系；
- `MaterialFadeThrough`：两个不相关内容区域之间切换；
- `MaterialFade`：单个控件或浮层内容进入/退出；
- `MaterialContainerTransform`：两个**容器**的几何变换（并非自动将同一张图片绑到两个 View）。

参考： [Google MDC Motion 文档](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md)、[Google MDC 1.2.0 发布](https://medium.com/google-design/material-components-for-android-1-2-0-is-now-available-aade483ed841) 以及 [官方 Codelab](https://developer.android.com/codelabs/material-motion-android)。

包名区分：
- `com.google.android.material.transition.*`：基于 **AndroidX Transition**，适用于 Fragments/Views，**不直接控制 Activity、Window、PopupWindow**；
- `com.google.android.material.transition.platform.*`：基于 **Android Framework Transition**，可用于配置好的 Activity/Window 转场；只有通过对应 feature/ActivityOptions 与目标 View 契约，才可能生效；
- `androidx.transition.TransitionManager` / `androidx.transition.Fade`、Android `RippleDrawable`/`ValueAnimator`/`ViewPropertyAnimator`/`EdgeEffect`/`WindowInsetsAnimationCompat` 仍是可复用的 Google 原生/AndroidX 能力，不必所有动画都硬接 MDC。

**历史风格不等于历史时长**：Material Motion 的现成四模式晚于 2017 年 OxygenOS。可以复用其成熟实现，但不能宣称它是旧版 ROM 的原厂曲线。默认的 MaterialFadeThrough/SharedAxis 约 300ms 对 Gallery 可能偏慢；具体需复用既有 `OxygenClassicMotion` 时长并真机校准。

**库与依赖限制**：Gallery 使用 MaterialToolbar/FloatingActionButton 等 Material Views；Commons 固定 `7.1.3`（`gradle/libs.versions.toml` 与 `app/build.gradle.kts`）。此次只审计源码，**未获取 Gradle 完整 resolved dependency tree，因此没有证据确认当前 MDC/AndroidX Transition 的确切版本或哪几个 API 一定可编译**。开工前使用现有可运行的 FOSS Release Gradle configuration 跑 `dependencies` 和 `dependencyInsight --dependency com.google.android.material:material`，仅当需要时才补明确的兼容版本，保持 Commons、minSdk/targetSdk、资源主题及 R8 兼容，不盲升 Material/M3/Compose。官方 Views MDC 库已进入维护模式，Apache-2.0 许可见 [仓库](https://github.com/material-components/material-components-android)。

## 2. 实际页面与动画模式的最终选型表

文件统一相对 `app/src/main/`，Kotlin 根为 `kotlin/org/fossify/gallery/`。

| 场景 | 推荐技术（优先 → 回退） | 具体代码入口 | 形态/业务红线 |
|---|---|---|---|
| 设置主页→8 类内部设置页，内页 Back | **MaterialSharedAxis.X** 的轻位移变体；若并无同时存在的两个独立 View，回退现有 `ViewPropertyAnimator` | `activities/SettingsActivity.kt:openSettingsCategory()/animateSettingsPageChange()/navigateSettingsBack()`、`res/layout/activity_settings.xml` | 当前同一 Activity 更新设置内容，不是 NavHost 两个 Fragment；**不能直接对清空后重建的同一 NestedScrollView 使用 beginDelayedTransition**。保留 epoch 中断逻辑、设置行布局 |
| OxygenTheme 内层颜色/主题页面 | SharedAxis.X 或当前短平移动画 | `activities/OxygenThemeActivity.kt` 的子页与 Back 回调 | 所有页面颜色由当前 ThemeToken 获取，不能在返回时闪旧色 |
| 设置某一帮助说明、选项区域显隐 | **MaterialFade**（缩放可关闭）；AndroidX Fade 回退 | `SettingsActivity.renderSettingsPage()`、`views/TiebaSettingsContainer.kt`、`TiebaSettingsCategoryRow.kt` | 只针对明确有显隐状态的 View，不对整个设置 RecyclerView 逐行加过渡 |
| 两个无强层级关联的内部内容区域互换 | **MaterialFadeThrough**（可移除 secondary scale provider）；普通 alpha 回退 | `SettingsActivity` 的稳定双容器/局部 empty state（需先确认真正存在两个实例） | 不把 Toolbar、底栏、媒体网格纳入整个 FadeThrough |
| 新建照片配置空态提示、添加来源后空态消失 | **MaterialFade** | `activities/PhotoProfileEditActivity.kt:renderSources()` / `res/layout/activity_photo_profile_edit.xml` | 只淡入提示，不替换用户输入或来源列表 |
| 自定义照片配置 FAB 功能显隐 | **MaterialFade**（只在真的要隐藏/显示时） | `PhotoProfileEditActivity.kt:photoProfileEditAddSource` | **IME 弹起时不许隐藏 FAB**；键盘顶部随动由 `WindowInsetsCompat` + `WindowInsetsAnimationCompat` 完成，不能用 Fade 代替位置适配 |
| 搜索框展开/收起、焦点 | AndroidX `TransitionManager`/Fade/有限 ChangeBounds，或 ViewPropertyAnimator | `helpers/OxygenToolbarStyle.kt:render()`、`OxygenSearchNavigation.kt`、`OxygenClassicPageInput.kt`；Commons `MySearchMenu` | 保留 EditText 对象/焦点、光标、即时查询/排序提交和 IME 返回顺序 |
| 相册主页、收藏、普通文件夹、回收站、Viewer 的三点菜单 | **保留 AppCompat PopupMenu/ActionMenuPresenter**，公开 Window 弹出动画样式视可行性试点 | `helpers/OxygenClassicMenus.kt`、`OxygenToolbarStyle.kt`、`views/TiebaMenuItemView.kt`；见 05 文档 | **AndroidX MaterialFade 是 View 过渡，不等于 AppCompat PopupWindow 入退动画**；不反射私有窗口；菜单形态不变；收藏两项**无 Checkbox、双态文字** |
| 管理自定义照片配置/设置手柄/Picker 内动态三点 | 同上 | `activities/PhotoProfileManageActivity.kt:showActions()`、`helpers/BottomNavigationOrderDialog.kt`、`ToolbarQuickActionsDialog.kt`、`dialogs/PickDirectoryDialog.kt`、`ClassicFilePickerOverflowButton.kt` | 锚点、重新读 item ID、权限与长列表滚动不能破坏 |
| 普通 AlertDialog 打开/关闭 | **原生 Dialog Window animation**（以 MaterialFade 作为视觉语义参考），内部真实 View 才可单独 MaterialFade | `helpers/OxygenClassicDialog.kt:show()/prepareWindow()/apply()`、`ClassicCommonsHooks.kt` 和 `res/values/oxygenos_classic.xml` | AndroidX MaterialFade 不能直接对整个 Dialog Window 使用；保持现有小圆角、实际宽高、dim、输入焦点、只执行一次确认/取消 |
| 文件/媒体 Picker，密码/PIN/生物识别弹窗 | 先保留现有 Window 动画，确定不会重复回调后再小试点 | Gallery `dialogs/PickDirectoryDialog.kt`、`PickMediumDialog.kt`、`ClassicPasswordMethodDialog.kt`；Commons `FilePickerDialog/SecurityDialog` | 系统/权限/鉴权和多层 back 所有权优先；不为了动画更换对话框实现 |
| 底栏三个根页面的跨 Activity 切换 | **现有 RippleDrawable + ValueAnimator 的局部 selected tint**；不把跨 Activity root 套上 FadeThrough | `helpers/GalleryBottomBar.kt:selectTab()/recolor()/open()/coordinateFirstDraw()` | `ROOT_ENTER_DURATION_MS=0`、`overridePendingTransition(0,0)` 和 first-draw gate 是抗闪烁约束；不恢复全屏 fade |
| 主页目录→普通文件夹/收藏/回收站网格 | 系统 Activity 动画优先；完成安全首帧证明后可少量试验 platform MaterialFade | `activities/MainActivity.kt:itemClicked()`、`MediaActivity.kt` | 目录异步鉴权与内容完成前不准用旧图片/占位虚构 transition |
| 缩略图→全屏图片 | **原系统进入优先；MaterialContainerTransform 只在非保护、已稳定单图时试点（默认关）** | `activities/MediaActivity.kt:itemClicked()/openOrdinaryViewer()/openInViewPager()` → `ViewPagerActivity.kt` | ContainerTransform 需要两个可用 View 容器和双方矩形，不能自动维持媒体 path；禁止旧图/错误范围和高分辨率图片错帧 |
| Viewer 左右滑动 | **保留原生/Commons ViewPager 自然拖动** | `views/ProgressivePhotoViewPager.kt`、`ViewPagerActivity.onPageScrolled()/onPageScrollStateChanged()`、`adapters/MyPagerAdapter.kt` | 不用 SharedAxis/FadeThrough 替换 ViewPager，保护未知边界、邻页、RTL、缩放与发布时机 |
| Viewer 顶栏/底部浮层显隐 | 现有 `ViewerChromeMotion`，可借 MaterialFade 的 easing，逐个局部 View 实验 | `helpers/ViewerMotion.kt:ViewerChromeMotion.show()` / `activities/PhotoVideoActivity.kt:fragmentClicked()` | 保留 generation/cancel 和显示最终态；不对媒体本体做透明切换 |
| Viewer 退出回网格、文件夹返回主页 | 优先系统 Window 返回；同 Activity 的 Main 分组目录可单独 SharedAxis.X 试点 | `ViewPagerActivity` Back、`MediaActivity.onBackPressedCompat()`、`MainActivity.onBackPressedCompat()`；见 06 | `FavoritesReturnCover`、滚动锚点、受保护会话、回收站状态不改变 |
| 系统预测性返回 | **AndroidX Activity / Android back-progress API**；仅在允许的内部层级把进度映射到局部 Material 动画 | Commons `BaseSimpleActivity.registerBackPressedCallback()` + `SettingsActivity/OxygenThemeActivity/MainActivity/MediaActivity/ViewPagerActivity` | 不以 MaterialSharedAxis 自动取代系统返回；取消不会 finish、释放权限或触发删除副作用 |
| ScrollView/RecyclerView 上下越界反馈 | Android `EdgeEffect` + `RecyclerView.EdgeEffectFactory`（适用时） | 主页、媒体网格、设置、Picker 各自 Recycler/NestedScrollView | 强调色**从用户配置推导**，不使用固定蓝；刷新下拉与越界反馈互不抢占 |

**特别澄清**：Google 的 MaterialFadeThrough 文档确实举过底栏导航的例子，但 Gallery 底栏是**跨 Activity 的新根页面**，不能直接照搬 Fragment 过渡示例。

## 3. 参数策略：沿用 Gallery token，不采用夸张默认时长

`helpers/OxygenClassicMotion.kt` 现有：`VIEWER_CHROME_MS=170`、`VIEWER_LOADING_FADE_MS=140`、`TAB_MS=150`、`LIST_MOVE_MS=170`、`SETTINGS_EXIT_MS=80`、`SETTINGS_ENTER_MS=100`，`easing() = PathInterpolator(0.4,0,0.2,1)`。

| 动画 | 候选初始值 | 限制 |
|---|---|---|
| 设置 SharedAxis X | 总 160–220ms，X 位移 8–16dp，先尽量保持现有 80+100ms 的可中断时序 | 若真实双容器条件不满足，**不为了接入 MDC 重构页面层级** |
| 单个 View 的 MaterialFade | 进入 120–160ms、退出 75–120ms；必要时 `secondaryAnimatorProvider=null` 去 Scale | 不让弹出说明块变成 M3 缩放卡片 |
| 内容 FadeThrough | 总 160–220ms；去掉或降低默认大幅 scale | 不对整个媒体网格、列表各行做交叉淡入 |
| 普通 Popup Window | 若公开窗口动画有效，进入 100–150ms、退出 75–120ms | 不要求必须改动：原生 Popup 已平滑则继续原生 |
| 普通 Dialog Window | 进入 140–200ms、退出 100–160ms；最多 4–8dp Y 位移 | 不动画化宽高/圆角/密码框布局 |
| 底栏 selected tint | 120–160ms，Ripple 生命周期交还系统 | 不在 Activity 切换前后各播放一遍 |
| Viewer 侧边滑动 | 保留 ViewPager 自然跟手/settling | 不引入扑克牌式规模动效 |
| 底栏根 Activity | **0ms** | 任何改动必须先通过首帧与抗闪烁验收 |

所有数字是**待真机验证的工程候选**，绝不是 OxygenOS 原厂实测。共享 `OxygenClassicMotion` 为唯一动画参数表，不在每个 Activity 再复制散落常量。系统 animator scale 0 必须直接完成最终状态。

## 4. 强调色、控件形态与 Material 默认主题的隔离

- Material Motion **只控制 alpha、translation、scale、相对时间和曲线**。Material 默认的 `colorPrimary`、M3 大圆角和 widget layout **没有权限覆盖 Gallery 的 `OxygenThemeEngine.snapshot()`**。
- Popup 的文字与 Ripple、Dialog 确认按钮、Switch、底栏 selected tint/pressed Ripple、输入光标和焦点线、进度指示、Edge Glow 按 [10](10-accent-semantic-and-runtime-invariants.md) 逐个取用户颜色；Toolbar 白色前景与错误红/媒体黑底可保留现有语义。
- 若做 ContainerTransform，明确用实际 `startContainerColor/endContainerColor` 和按主题设置的 scrim，绝不让 Material 默认 surface 把 OxygenOS 的方形容器 morph 成胶囊/大圆角。
- 切换主题时取消旧色 animator，终态绑定新 Token；同一 View 的 alpha/translation 不能同时由旧 Animator 和新 MaterialTransition 两个控制器修改。
- 只将 `TransitionManager.beginDelayedTransition()` 应用于确实稳定的两个 View，使用 `addTarget()/excludeTarget()` 缩小范围；不要在异步媒体扫描、`RecyclerView` 大范围 diff、Picker 状态变化中对整棵视图树过渡。

## 5. 推荐实施顺序（独立 Motion 阶段）

1. **Motion-A 依赖审计 + 基线**：读依赖树，确认实际 MDC/AndroidX Transition API、Commons 7.1.3，不改业务；截取目前设置页、Dialog、底栏、Viewer 与不同强调色的真实动态基线。
2. **Motion-B 低风险 View 动画**：设置稳定的局部显隐/分类试点、配置空态 MaterialFade、搜索局部 Fade；不动所有 Gallery Activity 的窗口切换。
3. **Motion-C Popup/Dialog Window**：验证 AppCompat Popup 的真实打开关闭机制，仅对普通确认 Dialog 试点短淡入；安全/密码/Picker 的后退和 IME 另验。
4. **Motion-D 底栏/Viewer/预测返回**：根窗保持 0ms，Viewer 仅 chrome 微调；MaterialContainerTransform 不作为必做项。先保证四类来源的 Viewer 顺序、鉴权和返回网格，再谈高风险动画。

每阶段先重新读取远程 HEAD；改动限制在 UI presentation + 测试文档，遵守当前分支并发修改；集中提交后 Actions ARM64 FOSS Release + 真机回归。**本轮制定规范没有运行任何上述步骤。**

## 6. 必须通过的验证

- **依赖**：目标 API 真能在当前 Gradle/R8 FOSS Release 编译；不强制升级 Commons/Theme。
- **经典几何**：Toolbar 56dp、经典菜单行、Dialog 宽高/小圆角、Switch 轨/球、底栏图标文字与内边距保持 OxygenOS；Material Motion 只能改变运动，不让最终界面变成 M3。
- **用户主题**：至少 3 个主题预设 + 自定义极浅/极暗/半透明种子，浅色/深色/非 Oxygen 模式；选中/按压/焦点/进度显示正确自定义强调色，动画中途换主题不闪旧色。
- **回调与竞态**：快速连续进入退出、取消、旋转、背景恢复、动画比例 0/1/2；Dialog 确认和安全鉴权恰好一次。
- **媒体和返回**：Viewer 左右 20 张序列/网格原顺序、.nomedia、收藏/回收站/照片配置、删除恢复、保护访问与首帧遮罩无回退；底栏首次绘制不得闪背景。
- **现有测试**：`GalleryBottomBarRippleTest`、`OxygenClassicMotionTest`、`SettingsPageMotionTest`、`ViewerMotionTest`、`ClassicOverflowPositionTest`、`ClassicDialogLayoutRegressionTest` + 新建 MaterialMotion 类/真实 Theme Token 动画测试。

## 7. 官方来源与相关文档

- [Google MDC Motion API 和使用方式](https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md)
- [MDC-Android 1.2.0：老 Material Design Views 时期 Motion 引入](https://medium.com/google-design/material-components-for-android-1-2-0-is-now-available-aade483ed841)
- [Google 官方 Material Motion Codelab](https://developer.android.com/codelabs/material-motion-android)
- [开源库许可证与维护状态](https://github.com/material-components/material-components-android)（Apache-2.0）
- 本项目 [02-分阶段实现](02-phased-implementation.md)、[03-测试](03-verification-matrix.md)、[06-Viewer 路径](06-navigation-hierarchy-and-viewer-back-map.md)、[08-动画全景](08-animations-dialog-viewer-transition-atlas.md)、[09-任务](09-ui-work-orders-and-safety-rails.md)、[10-强调色](10-accent-semantic-and-runtime-invariants.md)。

> **交付约束**：只规定技术选型和相应代码位置，不宣布 Material 动画已加入，也不更改用户自定义强调色、功能行为或界面控件形态。
