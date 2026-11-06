# 09 · UI Agent 可直接领取的工单、文件热区与安全边界

> 阶段：**仅预备资料**。截至此文档编制，没有任何应用 UI/动画/输入法代码被此轮修改，也没有安装 APK 或真机验收。本目录的新增文档不是功能交付。
> 审计参考 HEAD：`27008fc1b5226187c3c8b99c0a12911aa5163632`，后续实施不得停留在此历史 SHA。
> 前置：虚拟文件夹/媒体管线 Agent 完成其同分支工作并验收，获取最新远程 HEAD；只有在确认无并发代码冲突后启动精修。

## 1. 热区清单：按“一个共享入口多处生效”排序

| 优先顺序 | 模块 | 核心源文件（相对 `app/src/main/`） | 最小改动原则 | 必测屏幕 |
|---|---|---|---|---|
| UI-P0 | “照片配置”新建空草稿+IME 加号 | `kotlin/org/fossify/gallery/activities/PhotoProfileEditActivity.kt`、`res/layout/activity_photo_profile_edit.xml`、Manifest 中该 Activity 的 `adjustResize` | 先验证根 window resize，最小修复 **去掉 IME 时 FAB=GONE**，再做按实际遮挡的补偿；不修改来源配置模型 | 无配置、新建空配置、编辑已有配置 |
| UI-P1a | 收藏页两条菜单改为无 Checkbox 的双态文字 | `res/menu/menu_main.xml`、`MainActivity.kt`、`views/TiebaMenuItemView.kt`、`res/layout/abc_popup_menu_item_layout.xml`、`res/values*/strings.xml` | 保留 ID/布尔配置/鉴权与点击处理；`checkable=false`，根据当前布尔值显示下一步操作：“显示/不显示已排除项目”“显示/不显示隐藏项目”，移除专属 CheckBox 特判 | 主页底栏收藏 |
| UI-P1b | 所有三点菜单尺寸/位置/开关 | `helpers/OxygenClassicMenus.kt`、`OxygenToolbarStyle.kt`、`res/values/oxygenos_classic.xml`、`values-ldrtl/oxygen_classic_menu_offsets.xml` | 一个共享布局规范覆盖 Toolbar 与程序式 Popup，不全局反射私有 PopupWindow | 相册、收藏、普通文件夹、回收站、Viewer、配置管理、Picker、设置手柄 |
| UI-P2a | 设置分类与分组 | `SettingsActivity.kt`、`views/TiebaSettingsCategoryRow.kt`、`TiebaSettingsContainer.kt`、`activity_settings.xml` | 页面分类/回调不变，只调整字体、基线、Divider 和 page animation | 设置主页/全部 8 个内页 |
| UI-P2b | 设置内的动态三点与重排 | `helpers/BottomNavigationOrderDialog.kt`、`ToolbarQuickActionsDialog.kt` | 复用菜单规范，保护绑定位置正确性与列表持久化 | 底栏顺序 / 自定义顶栏操作 Dialog |
| UI-P3a | 底栏形态/局部切换 | `helpers/GalleryBottomBar.kt`、`GalleryNavigation.kt`、`OxygenClassicMotion.kt`、`res/values/dimens.xml` | 保留 first-draw gate 和 `overridePendingTransition(0,0)`；颜色/字体轻过渡不触发 Activity 双重动画 | 相册 / 照片 / 收藏，全部双向六组合 |
| UI-P3b | 搜索输入和 IME | `OxygenToolbarStyle.kt`、`OxygenClassicPageInput.kt`、`OxygenSearchNavigation.kt` + Commons `MySearchMenu` | 不改数据查询和 Adapter 提交时机；先验证焦点/键盘所有权 | 主界面、文件夹、回收站、独立搜索、来源 picker |
| UI-P3c | 顶/底 overscroll Glow | 各 `RecyclerView`/`NestedScrollView`/SwipeRefresh view 的实例化入口 | 先记录原有 EdgeEffect；最小共享 factory，刷新下拉冲突单独处理 | 主目录、媒体网格、设置、选取器 |
| UI-P4a | Popup/Dialog 开闭动画 | `OxygenClassicDialog.kt`、`ClassicCommonsHooks.kt`、`oxygenos_classic.xml`、AppCompat Popup 样式 | 不在每个 Dialog 构造函数复制动画；尽量用 Window presentation style，尊重 0x 与 IME | 所有 26 个 Gallery dialogs + 动态 Dialog + Commons 入口 |
| UI-P4b | Viewer/文件夹进出动画 | `MediaActivity.kt`、`ViewPagerActivity.kt`、`BaseViewerActivity.kt`、`ViewerMotion.kt`、`ViewPagerFragment.kt` | 不能改变网格/Viewer 目录顺序、权限和恢复锚点，只修改视觉层 | 普通/收藏/回收站/照片配置/外部 |
| UI-P5 | 预测性返回进度 | `SettingsActivity.kt`、`OxygenThemeActivity.kt`、`MainActivity.kt`、`MediaActivity.kt`、`ViewPagerActivity.kt`、Commons BaseSimpleActivity 调用链 | 先实现无权限副作用的 Settings 子页试点，逐来源扩展，开关只控制应用内自绘动画 | Android 12 / 13 / 14 / 16+ |

**为什么工单顺序与旧 `02-phased-implementation.md` 不完全相同？** 这是本次额外发现 UI-P0 **FAB 被 IME 强制隐藏** 后的具体实施顺序建议。旧文档的宏观 Phase 1–4 仍有效；执行时先落实 UI-P0，然后逐步开展菜单/设置/动画。不得误解为此刻已批准修改代码。

## 2. 最低成本的“首轮 UI 小修”建议

如果用户在虚拟文件夹验收后决定“先小修并出 APK”，不必一口气进入全部动画：

1. 从远程最新 HEAD 建立可重复的真机布局测量（尤其 PhotoProfileEditActivity 的 insets/FAB）。
2. 只处理 **FAB 被 IME 隐藏** 和 **收藏 Popup 改为纯文字双态操作** 两个可直接验证的问题。
3. 运行 `ClassicOverflowPositionTest`、`ClassicGalleryUiContractTest`、`ClassicToolbarLayoutTest`、相关照片配置测试，另加 FAB/IME、收藏两项 `checkable=false`、四种状态对应操作文案与操作持久化测试。
4. Actions 构建 ARM64 FOSS Release，并复核 Android 手势导航/普通导航下 IME 开/关、收藏两条**无勾选框操作菜单**的截图及状态。
5. 成功后再进入可预测性更低、覆盖范围更大的 Popup 位置与动画工程。

优点：修改集中，风险清楚，不需要任何 Room Schema、媒体索引、数据库或多 Activity 导航架构重写。

## 3. 大型“完整 UI 动画重构”明确禁止的捷径

- 禁止为了“点击图片立即动起来”在 Viewer 收到授权前展示旧缩略图或旧目录缓存。
- 禁止将 Activity 根页面切换过渡一概改回非零 alpha 动画；保留 `GalleryBottomBar` 的已验证抗闪帧设计。
- 禁止把 `ProgressivePhotoViewPager` 改成一套新的拖动距离/页数索引算法，仅为复刻“卡片滑动感”。
- 禁止动画运行中调用 `notifyDataSetChanged()` 并重新绑定整个网格，绕过现有 `ViewerSwipePublicationGate`。
- 禁止“退出 Viewer”时直接删除 `FavoritesReturnCover` 或任意保护权限遮罩。
- 禁止为收藏两条菜单重新实现旁路 `PopupWindow`；直接使用已有 AppCompat MenuItem 和 `refreshMenuItems()` 更新文案，不修改其他需要 Checkbox 的菜单机制，继续支持长菜单/键盘/RTL/无障碍。
- 禁止为处理 IME 把原姓名草稿与已选择来源重建/清空。
- 禁止在 System Back 手势进度事件里触发 `finish()`、释放会话或提交配置变更。
- 禁止只通过修改截图或模拟器布局就标记为“真机通过”。

## 4. 当前可用的测试/文档资产（无需重造）

- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicOverflowPositionTest.kt` — Toolbar 与 anchored Popup 的 LTR/RTL、3/20 项定位。
- `ClassicGalleryUiContractTest.kt`、`ClassicToolbarLayoutTest.kt`、`GalleryBottomBarRippleTest.kt`、`OxygenClassicMotionTest.kt`、`SettingsPageMotionTest.kt`、`ClassicSettingsControlsTest.kt` — 经典布局/底栏/动画行为回归。
- `docs/ui/classic-dialog-repair-review.md`、`docs/classic-dialog-review.md` — 之前保留的 Dialog 宽度、内容滚动和回调保护合同。
- `docs/testing/classic-motion-device-checks.md` — 原底栏/Viewer 真实帧测试基线。
- `docs/media-engine-v2/README.md` — 当前虚拟文件夹/媒体数据管线资料，不得以 UI 任务覆盖其功能回归。

上述资产是待运行的已有测试和文档，**不是此次已运行的测试结果**。

## 5. 新 Agent 开工前的“证据记录表”

每个工单必须先有以下最小记录，再编代码：

| 字段 | 示例/要求 |
|---|---|
| 当前远程 HEAD | 必须现查，不能重用本文 SHA |
| 用户看到的症状 | 图 / 录屏序号、设备和 Android 版本 |
| 代码入口和 View ID | activity → layout → helper/view → callback |
| 渲染/调用链 | 具体函数、路径、文件行号，因并发改动而更新 |
| 候选根因 + 置信度 | 已源码确认、可复现的运行态证据、未证实猜测分开 |
| 修改触点 | Kotlin、XML、res、测试的最小文件集合 |
| 隐私/媒体/鉴权边界 | 禁止被动画触发的数据或副作用 |
| 自动化验收 | 新增/扩展哪些具体测试、断言什么 |
| 真机验收 | 菜单坐标/IME 遮挡/错误帧/Ripple/Back 取消 |
| 回退 | UI 代码变更独立可撤销，不影响并发 Agent |
| 状态 | 未开始 / 设计中 / 已编译 / 已安装验证 / 用户已验收 |

## 6. 文档之间的分工

- `README.md`：目标、范围、顺序和状态。
- `01-visual-and-code-gap.md`：视觉差距与源码证据。
- `02-phased-implementation.md`：宏观实施阶段。
- `03-verification-matrix.md`：跨页面测试与真机验收。
- `04-predictive-back-feasibility.md`：系统 Back API/开关能力。
- `05-menu-settings-popup-code-atlas.md`：完整静态菜单资源/动态锚定 Popup/设置手柄。
- `06-navigation-hierarchy-and-viewer-back-map.md`：根 Activity→文件夹→Viewer→返回，虚拟收藏/回收站/配置/普通路径差异。
- `07-input-ime-empty-profile-fab.md`：输入入口、Commons 搜索、IME 对加号的明确隐藏逻辑与最小修法。
- `08-animations-dialog-viewer-transition-atlas.md`：26 个 Gallery Dialog、Commons 依赖、全套开闭/翻页/退出动画归属。
- **本页**：可以发给下一位 Agent 的阶段工单和安全红线。

> 全部文档均为“计划 + 代码静态审计”，除非之后另行记录真机检查、测试与提交，否则**所有 UI 实施状态仍为未开始**。
