# 02 · 分阶段工程方案（任务级执行清单）

> 仅为后续 UI Agent 的实施蓝图。**编写此文件没有修改任何应用源码或执行编译。**
> 先完成虚拟文件夹修复的功能验收；UI 精修时再次确认 `refactor/media-pipeline-stability` 最新 HEAD。
> 本路线以最小变更、安全回退、功能测试先行和视觉验收为原则；不可一次性改动所有 Activity。

## 共用执行守则

**最高优先级的两个交叉规范**：
- [10 — 动态强调色](10-accent-semantic-and-runtime-invariants.md)：所有选中/按压/焦点/加载/进度/EdgeGlow/动画容器着色从 `OxygenThemeEngine.snapshot()` 的当前预设或用户自定义色派生；不写死任何参考蓝色；经典 Switch 的 onThumb 使用现有 `switchPalette(rawAccent)` 而非强制所有使用 `interactiveAccent`。
- [11 — 经典 Material Motion 选型](11-legacy-material-motion-selection.md)：在不改变 OxygenOS 7.1.1 Classic 几何和小圆角的情况下优先复用 Google MDC-Android Views（MD2 时期）`MaterialSharedAxis`、`MaterialFade`、`MaterialFadeThrough`；Dialog/Popup Window、跨 Activity root、IME、ViewPager 和预测性返回按各自真实 API 实施，禁止强行拿 View Transition 替代。先查实际 Gradle 解析到的 MDC/AndroidX Transition 版本，严禁无证据升级 Commons 7.1.3 或全局主题。

1. 每阶段开始：核对远程 HEAD、`git diff` 与同分支并发任务；列出本阶段确切的 Kotlin/XML 修改路径。已有虚拟文件夹/排序搜索改动不回滚。
2. 先冻结一套截图录屏与布局度量；对每个疑点建立“复现步骤 → View/资源证据 → 假设 → 最小修正 → 回归用例”记录。
3. 现有 `OxygenThemeEngine`、`OxygenClassicControls`、`OxygenClassicMotion`、`OxygenToolbarStyle`、`OxygenClassicDialog`、`OxygenClassicMenus` 是**优先复用的单一入口**。不复制同名视觉逻辑到每个 Activity。
4. 任何界面动画都要支持系统动画缩放为 0、可取消、可重入、Activity 销毁、主题切换、配置变化。不要在 UI 主线程增加文件 IO、媒体扫描或图片解码。
5. 对公共菜单、设置行、Picker 只调整 presentation；保留原 MenuItem ID、ViewBinding、配置持久化、授权回调、触摸事件语义。
6. 每阶段**集中**提交、经过 GitHub Actions ARM64 FOSS Release 及对应测试，并进行设备验收。测试失败不能以“截图正常”代替。
7. 在每阶段结束前只更新文档中的已证实结论；未安装验证的项仍标为待验收。

## Phase 0 — 前置冻结和实测基线（零 UI 代码变更）

新增工作：记录至少三种用户主题预设及自定义浅/深/低对比色下的 Toolbar、菜单、Dialog、Switch、底栏、输入、进度和 EdgeEffect；记录 MDC 与 AndroidX Transition 的 resolved dependency/API，不假定 Material Motion 已经可编译；确定哪些设置内容有两个真实 View 可进行 SharedAxis。

**依赖**：虚拟文件夹 Agent 相关提交合入当前分支并得到用户验收；如仍有 P0 不可用、闪退、未授权展示，不进入 UI 改码阶段。

任务：

- 采集菜单运行态：相册主页、收藏、普通文件夹、.nomedia 文件夹、照片配置、全屏 Viewer、编辑器、文件选择器、媒体选择器以及双层子菜单。
- 记录每个菜单 `anchorRect`、`popupRect`、安全区/Insets、内容行数、最大标题宽、画布最小/最大高、左基线、顶部/底部 padding。收藏两项只记录**纯文本菜单行**的实际 bounds、文案和 `checkable=false`；**其他仍使用 CheckBox 的菜单**按原规格测量控件。
- 为设置首页八个分类与内部页面记录 dp/sp、baseline、line-height、分隔线 y 坐标；包括字体缩放 1.0x 与 1.5x。
- 建立底栏与搜索动画逐帧基线；采样系统手势/3 键导航、横竖屏、不同动画 scale、深浅/自定义色。
- 记录本文基线参考截图和新生成的真机截图的来源、设备、版本、分辨率、逻辑密度和隐私去标识化状态。不要将个人相册缩略图原图提交 GitHub。
- 先确定各页面是否使用原生 EdgeEffect / RecyclerView Stretch / SwipeRefresh，而非假定“动画缺失”。

**产物**：可复现缺陷表、布局度量 JSON/表格、截屏匿名对比、验收基线。无足够证据时标注待验证。

**退出条件**：UI-01 两项“按状态显示下一步操作文案”的规则和现有鉴权/重载行为已核对清楚；UI-02 至 UI-09 至少有一例对应可重放视频。

## Phase 1 — 三点菜单与基础控件（最先实际改码）

### 1.1 收藏页两条菜单改为无 Checkbox 的双态操作文案（已决定，未改码）

**设计规则：** 每条菜单永远显示**下一次点击将执行的操作**，而不是用固定文案 + CheckBox 展示当前状态：

| 当前配置值 | `favorite_always_show_excluded` | `favorite_always_show_hidden` |
|---|---|---|
| `false`（未启用） | **显示已排除项目** | **显示隐藏项目** |
| `true`（已启用） | **不显示已排除项目** | **不显示隐藏项目** |

首要文件：
- `app/src/main/res/menu/menu_main.xml`：保留两个现有 item ID 和顺序、可见性条件，移除这两项的 `android:checkable="true"` 或明确置 `false`；不添加图形占位/伪 Checkbox。
- `app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt`：`refreshMenuItems()` 使用 `config.favoriteShowExcluded` / `config.favoriteShowHidden` 动态设置 `title`，不再写这两项的 `isChecked`；原点击 handler 继续切换两个布尔值，按原逻辑刷新收藏目录、鉴权与权限状态。
- `app/src/main/kotlin/org/fossify/gallery/views/TiebaMenuItemView.kt`：移除只针对这两个 ID 的 CheckBox 专属着色/留白特判；保留全站菜单行测量及其他控件行为。
- `app/src/main/res/values/strings.xml` 及各已有 `values-*/strings.xml`：使用四条可本地化的操作字符串；检查翻译缺失回退和长字符串，不把中文直接写死 Kotlin。

实现顺序：
1. 先确认旧 handler：当前源码点击时会**先保存配置并重载**，对于开启隐藏/排除项目的操作可弹出保护源认证；**认证取消时配置值仍可能保持开启**，不得在纯 UI 文案改造中擅自更改这条既有功能语义。
2. 菜单被打开前/刷新时，分别根据两个持久化布尔值计算下一步操作文字；两项独立，允许 4 种组合。菜单关闭、再次打开、旋转、主题变化、恢复进程后均应展示最新文案。
3. 点击后仍调用原菜单 ID 的操作回调，保留全部数据刷新/鉴权流程，菜单行的语义和可见性仅在 `mShowingFolderFavorites` 时适用。
4. 从 `TiebaMenuItemView` 移除两项专用的 CheckBox 注入/着色逻辑，检查在 AppCompat 菜单内两行没有额外右侧空白，宽度跟随最长文字。
5. 区分收藏专属“显示/不显示”持久化项与其他 `temporarily_show_hidden/temporarily_show_excluded` 的临时操作；不改变后者 ID、文案或功能。

**DoD**：两项均为不带 CheckBox/Radio 的普通 Ripple 菜单行；四种状态组合文案正确，点击后按当前**配置**更新下一次菜单文案；保留状态持久化、保护目录鉴权、刷新以及 RTL/字体放大可读性。

### 1.2 菜单位置/尺寸

首要文件：`OxygenClassicMenus.kt`、`OxygenToolbarStyle.kt`、`TiebaMenuItemView.kt`、`oxygenos_classic.xml`、`oxygen_classic_menu_{light,dark}.xml`、`values-ldrtl/oxygen_classic_menu_offsets.xml`。

- 分别测量 AppCompat Toolbar Overflow 与自定义锚定 PopupMenu；不要混用“当前菜单右边缘”的坐标系。
- 研究移除/替代固定 `-20dp`、`+20dp`、`16dp` 的必要性：优先真实 anchor、WindowInsets、RTL 和 PopupWindow 的原生裁剪能力。保持 AppCompat 对触摸/键盘/子菜单的所有权。
- 当前 280dp 测量上限只可作候选上限。目标宽度依赖菜单文案、图标、**其他仍可勾选的菜单行**、边距与可用屏宽；收藏新双态文字不得因旧 CheckBox 预留空间而过宽，在 320/360/412/600dp 窗口评估动态调整。
- 48dp 基本行高和 8dp 菜单上下内边距作为参考；不压缩多行中文字；过高时使用原生菜单滚动与可见安全区。
- 检查点击反馈：真实 View 背景 Ripple，按住/松开/滑出/焦点状态不能只显示瞬间浅色块；不改变原本事件分发。
- 覆盖 `menu_main.xml`、`menu_media.xml`、`menu_viewpager.xml`、`menu_search.xml`、各 CAB、文件与媒体 Picker 子菜单以及长文本/双层菜单。

**DoD**：锚点误差采用 Phase 0 的测量标准；菜单没有越过安全区、不会遮挡唯一可点击的关键行、长列表可滚动，浅色与深色外观一致。

### 1.3 第一轮扫尾

菜单文字颜色、选中框、单选框、子菜单箭头、主题色可读性、Popup 对话框圆角均与现有系统兼容；检查旧 `docs/ui/classic-dialog-repair-review.md` 的 Dialog 尺寸合同，不在菜单任务里改回过大密码/文件选择器。

**阶段测试建议**：扩展 `ClassicOverflowPositionTest`（现有覆盖 LTR/RTL、3/20 项）、`ClassicGalleryUiContractTest`；为两个收藏 item 增加 `checkable=false`、无右侧勾选视图/多余空白、四种组合的文案与点击持久化断言；普通可勾选菜单继续保留独立 CheckBox 测试。必要时使用 `GraphicsMode.NATIVE` 截图并进行真机检查。

## Phase 2 — 设置主页、分类和内页排版

**Motion 选型**：设置主页→内页优先评估 AndroidX View `MaterialSharedAxis.X`；**若当前同一 NestedScrollView 被销毁/重建而没有稳定 outgoing/incoming View，维持现有 `animateSettingsPageChange()` 只统一 Google Material easing 与现有 80+100ms 时间，不为使用库而重构页面。** 局部说明显隐可试 `MaterialFade`；互斥内容才考虑 `MaterialFadeThrough`，默认缩放可取消。所有强调色来自当前 `OxygenThemeEngine.snapshot()`。

首要文件：
- `app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt`
- `app/src/main/kotlin/org/fossify/gallery/views/TiebaSettingsCategoryRow.kt`
- `app/src/main/kotlin/org/fossify/gallery/views/TiebaSettingsContainer.kt`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/values/oxygenos_classic.xml`
- `app/src/main/kotlin/org/fossify/gallery/views/ClassicSettingsSwitch.kt`

任务：
1. **首页紧凑分类行与子页区分**。仍采用 56dp Toolbar + 图标/标题/副标题分类列表；不要照搬系统设置首页约 124dp 大标题块。
2. 主标题 16sp、副标题先测 12sp/13sp 两档。保持 72dp 作为默认最小高度而非全部文本的硬性高度；对 1.5x 字体自然扩展。
3. 统一左图标中心、文字起点、行上下留白、Divider 细度；不得在列表项外增加 M3 卡片/宽胶囊。
4. 分组标题统一语义色、12sp、独立 48–56dp 候选节奏（按实测定最终值），确保隐藏条件行后不会残留孤立分组。
5. 检查右侧 Switch 位置、可点区/Ripple、滑动与点击；保留真正状态处理器，避免行与开关重复触发。
6. 检查 `setupSettingsHub()` 在 `onResume` 重建行时的焦点、滚动位置、Accessibility 焦点与 Ripple；**只有证据表明需要时**才做增量更新。
7. 调整设置页切换动画：重点观察现有 0.12 alpha 是否显得空白；降低透明度变化或位移必须同样通过中断/销毁/动画关闭的测试，避免重建时短暂闪烁。
8. 归纳 `OxygenThemeActivity` 与其他自建设置屏的排版差异；统一 Toolbar 与分组规则，但保持各页面布局功能。

**阶段测试建议**：`ClassicSettingsControlsTest`、`SettingsPageMotionTest`、组件尺寸、可点击语义/无障碍，设置页面返回、重入、多窗和旋转。

**DoD**：八类首页和各自内页无截断、错位、重复分割线；操作状态一致，页面进出最终 alpha=1/translationX=0。

## Phase 3 — 底栏、搜索输入、滚动反馈

### 3.1 底栏：局部选中动画，不改 Activity 交接

**MDC 限制**：Gallery 根底栏跨 Activity，保留 0ms Window 动画/first-draw gate，**不直接移植 MaterialFadeThrough 的 bottom navigation 示例**；只使用原生 `RippleDrawable` 与轻量颜色 ValueAnimator，颜色在每次着色时解析用户主题 token。

文件：`GalleryBottomBar.kt`、`GalleryNavigation.kt`、`OxygenClassicMotion.kt`、`res/values/dimens.xml`、`MainActivity.kt`、`MediaActivity.kt`。

- 先以现有 56/52/24dp + 12sp 为几何基准修复图标视觉中心、文字 baseline 与左右间距。
- `selectTab()` 当前直接变更颜色/字重；候选将色彩过渡置于约 120–160ms 的轻量 ValueAnimator，但先测 Ripple 生命周期。不要在选中态缩放整个 View 或改变项宽度。
- 保持 `ROOT_ENTER_DURATION_MS=0`、`overridePendingTransition(0,0)` 和 first-draw gate，除非有独立稳定性证据与完整替代方案。
- 禁止在普通重着色时反复 `removeAllViews()` 或重建 `RippleDrawable`；底栏重排是不同场景，先结束/取消按压态并验证。
- 冷启动/慢扫描/受保护源必须无错误内容泄露、错误底色帧或黑白闪烁。

### 3.2 搜索输入

**Motion 选型**：可对稳定子 View 试用 AndroidX `Fade/ChangeBounds`；IME 位置由 Insets/系统处理，`MaterialFade` 只用于真实功能显隐，不让 FAB 因软键盘弹出而变成 GONE。颜色源为 `OxygenClassicPageInput` 和 [10](10-accent-semantic-and-runtime-invariants.md)；不能用 MDC 默认蓝染色。

文件：`OxygenToolbarStyle.kt`、`OxygenClassicPageInput.kt`、`OxygenSearchNavigation.kt`，相关 `MainActivity`、`MediaActivity`、`SearchActivity`。

- 建模 UI 状态 `CLOSED / OPENING / OPEN / CLOSING`；与搜索查询状态隔离。
- 调整 title/search icon/输入文本框的透明度与位置时应同步或无视觉跳变；不可等待 UI 动画完成才向现有搜索监听器提交文本。
- IME 由 `WindowInsetsControllerCompat` 与系统/输入法驱动，布局跟随 Insets；Android 7.1 的静态参考截图不能直接作为现代键盘动态规范。
- 保持“先收起 IME 再处理明确导航”的原有行为；系统键盘返回不可被错误地当作页面返回。
- 输入框焦点下划线、光标、选择状态继续复用现有 tint 与真实 EditText。

### 3.3 顶部与底部经典过滚动

候选新增共享 `OxygenClassicEdgeEffectFactory`（**文件名是规划建议，当前未创建**）；仅在 RecyclerView 确有默认 Stretch/反馈差异时使用。

- 按 `RecyclerView.EdgeEffectFactory`/适当 `EdgeEffect` 生命周期实现触边的轻蓝/主题色反馈；支持 RTL、方向、硬件加速与系统动画设置。
- 对 `NestedScrollView` 另行判断是否需要自定义包装；不应强制把 RecyclerView 工厂挂给所有 View。
- 先确定 `SwipeRefreshLayout` 的下拉手势所有权：触发刷新时不再叠加顶部强烈 glow。全屏图像缩放/横向切换和视频进度拖动不采用通用纵向 Glow。
- 不引入粘滞拉伸、过度回弹或图像内容位置跳变。

**阶段测试建议**：`GalleryBottomBarRippleTest`、`OxygenClassicMotionTest`、`ClassicToolbarLayoutTest`、搜索监听器时序、手势冲突及设备录屏。

**DoD**：切 tab、按压、搜索和触边都可打断且有正确最终态；媒体排序/搜索数据正确性与前期稳定性无回退。

## Phase 4 — 预测性返回专项 + 跨组件最终扫尾

**Motion 专项分级**：普通 AlertDialog/Popup 的 Window 进出参照 MaterialFade 的**视觉语义**，但不误把 AndroidX `MaterialFade(View)` 施加于独立 Window；Viewer 左右翻页仍保留原生 ViewPager；仅在非保护且 anchor 稳定的普通单图试验 `MaterialContainerTransform`（默认禁用）；预测性返回使用 AndroidX Back progress 规范，不能靠 `MaterialSharedAxis` 自动解决。按 [11](11-legacy-material-motion-selection.md) 的风险级别分别验收。

预测性返回不是“UI 统一顺手加一行”的改动，单独按 [04 评估](04-predictive-back-feasibility.md) 先做小范围 Spike。

1. 梳理活动栈、屏内 Settings 分类、内联搜索、Viewer、全屏 UI、权限对话框、文件选择器、编辑器的返回优先级。
2. 核实 AndroidX Activity 与 Commons 的 Back API 实际运行路径、Manifest、系统版本差异。
3. 在**无鉴权副作用**的设置内页先试点手势进度/取消/提交；验证后再扩展其他场景。
4. 如添加设置开关，精确定义“应用内预测性返回动画”，避免承诺运行时关闭 Android 系统自己的 back-to-home 动画。
5. 与 Dialog/Picker/Theme/RTL/IME/无障碍整体联测；如无法达到稳定性门槛，先交付功能正确的旧返回逻辑，不为视觉一致性冒险。

**DoD**：手势取消不丢失界面状态；返回完成恰好一次；权限/密码/删除等副作用不在手势进度中执行；root pages 系统返回仍可正常结束；“关闭”选项行为与文案一致。

## 交付与回退要求

- 每阶段：记录基线 SHA、修改文件、截图/录屏（去除私密媒体）、测试命令与报告、Actions 构建链接、已知问题与后续任务。
- 修复过程中如发现 UI 需求与虚拟文件夹/媒体管线有冲突，**停止跨功能变更并记录依赖**，不擅自篡改后者的实现。
- 回退范围应限制在该阶段的 UI 变更；不要用 `git reset --hard` 回到历史 SHA 或强推覆盖其他 Agent。
- Phase 1 的收藏**双态文字操作**与菜单对齐优先于细微动画。Phase 2/3 的视觉修正不得削弱 Phase 1 功能；Phase 4 不阻塞前三阶段的功能交付。
