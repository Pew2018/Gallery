# 07 · 输入框、搜索/IME 路径与自定义浏览配置加号遮挡专项

> **重要新发现：[源码确认] 目前加号在 IME 出现时被明确设置为 GONE。** 本轮只记录，不改源码。
> 基线 `refactor/media-pipeline-stability` @ `27008fc1b5226187c3c8b99c0a12911aa5163632`。
> 目的：为之后最小化代码改动准备定位清单，优先修复“新建自定义照片配置、未添加来源时，输入法弹出会让底部加号不可用”。

## 1. 当前实际布局（精确到 View ID）

**Kotlin：** `app/src/main/kotlin/org/fossify/gallery/activities/PhotoProfileEditActivity.kt`

- 约 76–94：创建空草稿 `PhotoProfile(UUID, "", emptyList())`，`setContentView(binding.root)`，并使用 `setupEdgeToEdge(padTopSystem=[photoProfileEditAppbar], padBottomImeAndSystem=[photoProfileEditScroll])`。
- 约 94–104：`photoProfileEditToolbar` 的保存菜单、返回、`photoProfileEditName` 设置初始名称，`photoProfileEditAddSource.setOnClickListener { chooseSources() }`。
- 约 **115–122**：对**加号本身**设置 `ViewCompat.setOnApplyWindowInsetsListener`，读 `Type.systemBars()` 调 `params.bottomMargin=16dp+bars.bottom`，然后执行：
  ```kotlin
  view.visibility =
      if (insets.isVisible(WindowInsetsCompat.Type.ime())) View.GONE
      else View.VISIBLE
  ```
  这会在键盘弹出时**有意隐藏 FAB**，属于与产品目标冲突的确切代码事实。是否还同时发生了坐标遮挡，需要真机另行验证。
- 约 196–325：`renderSources()`/来源行生成；未添加来源时 `photoProfileEditEmpty` 可见；此处不是加号布局来源。
- 约 358–405：`chooseSources()` 打开带 `SourceSelection` 的 `PickDirectoryDialog`，完成时更新草稿来源；此逻辑必须保留。
- 约 409–426：`saveDraft()` 对名称和至少一个来源校验，不允许因此在 IME 悬浮修复时绕过业务校验。

**XML：** `app/src/main/res/layout/activity_photo_profile_edit.xml`

```text
CoordinatorLayout [root / 全屏]
├── photo_profile_edit_appbar / photo_profile_edit_toolbar [56dp]
├── photo_profile_edit_scroll [ScrollView, match_parent, appbar_scrolling_view_behavior]
│   └── LinearLayout [底部预留 96dp]
│       ├── photo_profile_edit_name_label
│       ├── photo_profile_edit_name [EditText, 52dp min, singleLine]
│       ├── photo_profile_edit_sources_title
│       ├── photo_profile_edit_sources [动态来源 rows]
│       └── photo_profile_edit_empty / photo_profile_edit_error
└── photo_profile_edit_add_source [FloatingActionButton 56x56dp]
    └── gravity=bottom|end, margin=16dp, elevation=6dp, plus icon=24dp
```

**Manifest：** `app/src/main/AndroidManifest.xml` 的 `PhotoProfileEditActivity` 已设置 `android:windowSoftInputMode="adjustResize"`（约 96–102）。但 edge-to-edge 与 IME/scroll 的不同窗口行为仍需真机观测；**不能简单假设 adjustResize 在所有 API 和导航模式下已经让 FAB 自然停在键盘上方**。

## 2. 目标行为：键盘出现，加号仍能使用

- 当 `photo_profile_edit_name` 获得焦点、IME 展开，添加来源加号依然 **VISIBLE、可点且可触达**，应位于**键盘顶边之上**，至少保留候选 16dp 视觉间隔；同时不能盖住名字输入、报错文本、已有来源行。
- 键盘收回时 FAB 平稳回到原本的右下角（保留导航栏安全区）；无需新的整屏位移动画。
- 点击 FAB 打开 `PickDirectoryDialog` 前，按照既有交互要求协调清除焦点/隐藏键盘，避免 picker 上叠加 IME；不要改变已输入的草稿名称。
- 空配置、已有 1 个/多个来源、保存校验错误和删除来源后的空态都要可见。拖动 ScrollView 时 FAB 不该拦截列表滚动。
- 兼容 Android 手势导航/三键导航、横屏、小窗口、中文/英文键盘、Gboard 高度变化、建议栏、字体缩放、系统动画关闭、TalkBack。

## 3. 实施时的最小工程路线（拟议，必须先测）

**路线 A：首选“确认 adjustResize 真实生效 + 删除错误隐藏条件”。**

1. 先在真机分别记录 IME 打开/关闭时 `root.height`、`photoProfileEditScroll.height`、`photoProfileEditAddSource.getLocationOnScreen()`、`WindowInsetsCompat.Type.ime()` 的 bottom/top 与 `Type.navigationBars()` bottom。
2. 若 CoordinatorLayout 已被 `adjustResize` 压缩到 IME 顶部，将 FAB 的 `GONE` 删去后即可在缩小的根容器底边上保持 `16dp` 边距；同一次 IME inset 不再手动叠加整个键盘高度，避免**双重上移**。
3. 原本监听只读 system bars 加 margin 的行为仍需审查导航栏 inset 是否被根 View / scroll 层已消费。使用 idempotent 的 baseMargin（如初始值 `16dp`）处理每次 Insets，避免重复累积。
4. 若确认已有内容行可滚到键盘上方，保留 ScrollView 的底部内容 padding；必要时增加 `clipToPadding=false`、自动滚到当前输入/错误提示位置，不通过缩小来源列表解决问题。

**路线 B：当 edge-to-edge 下根窗口并未真正缩小，计算“FAB 实际屏幕坐标 → IME 顶部可用区域”。**

- 优先根据 `rootView.getRootWindowInsets()`、`WindowInsetsCompat.Type.ime()`、root 的屏幕坐标判断 FAB 与键盘是否重叠。
- 仅补偿真实发生的**重叠高度 + 期望的边距**；避免同时用 `scroll paddingBottom`、`FAB marginBottom`、`translationY` 三套方式叠加。
- 对 `WindowInsetsAnimationCompat.Callback` 仅负责连续过渡中的视觉位置，动画结束统一归一化最终 margin/translation；取消/方向切换时恢复稳定态。
- 避免在每个动画帧调用 `view.layoutParams = ...` 触发整树布局；可用轻量 translation 对齐 IME 顶边，到终态再一次性设置合法 layout。若设备表现不稳定，宁可直接无动画跳到正确位置。
- 不修改 `PhotoProfileRepository`、`PhotoProfileCodec`、来源权限检验或配置保存结构。

**建议的最终布局职责**：CoordinatorLayout 根负责可用屏幕/IME 边界；ScrollView 负责可滚内容；FAB 仅负责一个右下角浮动动作。不要把加号直接塞进名字输入框底部或让它随内容滚到屏外。

## 4. 搜索输入与页面输入的源文件分布

### A. 顶栏搜索（Commons + Gallery）

| 页面 | View/资源 | 交互控制 |
|---|---|---|
| 相册主页/虚拟收藏文件夹根页 | `res/layout/activity_main.xml` 的 `main_menu`（`MySearchMenu`） | `MainActivity.setupOptionsMenu()`；`onSearchTextChangedListener` → `setupAdapter(...)` |
| 普通文件夹/虚拟收藏媒体/回收站/自定义「照片」 | `res/layout/activity_media.xml` 的 `media_menu` | `MediaActivity.setupOptionsMenu()`；搜索请求转到当前来源数据的 `searchQueryChanged()` |
| 独立搜索 | `res/layout/activity_search.xml` 的 `search_menu` | `SearchActivity.setupOptionsMenu()`；单独 Grid Adapter |
| 添加来源文件夹选择器 | `res/layout/dialog_directory_picker.xml` 的 `folder_search_view` | `dialogs/PickDirectoryDialog.kt:configureSearchView()/setSearchViewListeners()` |
| 其他 Commons Picker | Commons `FilePickerDialog` 自有 Breadcrumb/List（具体依赖接口） | 不因输入栏动画重建 Picker 的权限/路径逻辑 |

**共同底层：** `FossifyOrg/commons` tag `7.1.3` 的 `commons/src/main/kotlin/org/fossify/commons/views/MySearchMenu.kt`，`setupMenu()` 在 Search 图标被点时给 `topToolbarSearch` 请求焦点/显示键盘；焦点触发 `openSearch()`，`closeSearch()` 清查询并隐藏键盘。Gallery 在 `helpers/OxygenToolbarStyle.kt:render()` 切换 title/search 控件可见/位置，并使用 `helpers/OxygenSearchNavigation.kt` 处理明确的 IME 收起；因此**搜索动画必须保证 Commons 的 focus/文本监听器不会重复调用**。

### B. 页面级真实输入框

- `activity_photo_profile_edit.xml:photo_profile_edit_name` — 新建/修改照片配置的名称（本次 FAB 问题）。
- `activities/PhotoProfileManageActivity.kt:renameProfile()`（约 196–235）— 动态创建 `EditText` 的重命名弹窗。
- `activities/OxygenThemeActivity.kt:chooseColor()`（约 439–475）— 动态 `EditText` 的自定义颜色弹窗。
- `helpers/OxygenClassicPageInput.kt:style()` — 页面 EditText 的颜色/焦点/光标；`helpers/OxygenClassicDialog.kt:styleInput()` — Dialog 输入框。

### C. Gallery 内包含输入框的主要 XML 资源（代码定位）

| 资源 | 输入控件 ID / 场景 |
|---|---|
| `dialog_create_new_folder.xml` | `folder_path` 只读路径、`folder_name` 新目录名；真实弹窗由 Commons `CreateNewFolderDialog` 引导 |
| `dialog_custom_aspect_ratio.xml` | `aspect_ratio_width`、`aspect_ratio_height` |
| `dialog_resize_image.xml` | `resize_image_width`、`resize_image_height` |
| `dialog_resize_image_with_path.xml` | `resize_image_width/height`、`folder`、`filename_value`、`extension_value` |
| `dialog_resize_multiple_images.xml` | `resize_factor_edit_text` |
| `dialog_save_as.xml` | `folder_value`、`filename_value`、`extension_value` |
| `dialog_export_favorites.xml` | `export_favorites_filename` |
| `dialog_export_settings.xml` | `export_settings_path`、`export_settings_filename`（Commons `ExportSettingsDialog` 的资源覆盖） |
| `dialog_slideshow.xml` | `interval_value` 数字输入 |
| 共享 Commons Security / PIN / Pattern | `tab_pin.xml`、`tab_pattern.xml` 等安全输入视图；不可与普通 `EditText` 同样处理 IME |

以上是当前通过布局源逐个检查的 Gallery 可见输入入口，不宣称已经覆盖 Commons 和外部系统组件中的所有输入框；全量实现前应再次扫描 `app/src/main/res/layout`、`src/main/kotlin` 中动态 `EditText`、Compose/第三方 UI。

## 5. 输入、弹窗与键盘动画如何共存

- **输入法出现/消退由系统负责**：不要用 `200ms` 固定延时猜键盘高度，避免跟系统手势 Back 产生重复关闭。
- **焦点和 underline**：保留 `OxygenClassicPageInput` 的 ColorStateList，候选用轻微的颜色/alpha 过渡，不能刷新时换掉 EditText 实例、光标或 Selection。
- **弹窗带输入**：Dialog 根据 IME 重新测量可用高度，固定 title/footer、内容区域滚动；不要在键盘进出时重新计算 dialog width 导致弹窗“呼吸”。
- **搜索数据链路**：输入字符同步触发原 `onSearchTextChangedListener`，动画只改布局呈现，不改变搜索数据发布时机。
- **返回优先级**：键盘展开时的系统 Back、应用返回和 Dialog Back 是不同所有者；先核查 Commons `BaseSimpleActivity.registerBackPressedCallback()` 再添加自定义进度动画。

## 6. FAB 的确定性测试与真机验收

| 场景 | 观测点 | 退出条件 |
|---|---|---|
| 空配置：开始输入名字后弹 IME | `FAB.visibility`、bounds 对 IME 顶部、安全区、是否被键盘挡住 | 按钮始终 VISIBLE；最少 48dp 可点击；不遮输入字段 |
| 名称未填/有字/有错误状态 | 保存错误、焦点、IME、FAB | 错误提示可读；FAB 点击仍启动原来源选择 |
| 加入 1/多来源，列表滚动 | 内容 footer/滚动范围、FAB/来源卡片位置 | 最后一行可达，不被浮按钮永久挡住 |
| 键盘消失/出现连续切换 | 按钮复位、是否反复上移 | 无跳动和过时 translation |
| 手势/三键导航、横屏、窗口缩放 | bottom insets、WindowMetrics、IME | 安全边距不重复计算，不越过屏幕 |
| System animations 0、IME 高度变化、非 Gboard | FAB 仍可触达 | 无异步残留、延迟遮挡和 double inset |
| TalkBack / 字体缩放 | `contentDescription` 与焦点遍历 | 既能操作加号，也能读到名称与错误 |

**回归不可破坏**：`saveDraft()` 名称/来源校验、`chooseSources()` 的授权、`PickDirectoryDialog` 的手动来源/隐藏目录选择、编辑现有配置时的 UUID 和来源一致性。

> 推荐优先级：**UI-09（本新问题）应在菜单/设置动画全面精修前进行单独的布局小修复**，因源码已有明确错误条件，预计比全局预测性返回改造容易验证；但仍需等并行虚拟文件夹 Agent 完成其冲突文件工作。
