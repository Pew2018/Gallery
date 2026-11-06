# 05 · 全局菜单、设置项三点菜单与定位/开关动线索引

> **仅代码定位；未修复、未实现新动画。** 检查 `refactor/media-pipeline-stability` @ `27008fc1b5226187c3c8b99c0a12911aa5163632`（2026-11-06）；实施时再读远程 HEAD。
> 代码路径前缀：`app/src/main/`；除特别说明，Kotlin 路径前缀为 `kotlin/org/fossify/gallery/`。
> 本表覆盖本分支 **全部 15 个 `res/menu/*.xml` 资源**以及已经定位的代码动态 Popup；部分操作按钮只有“工具栏动作”，并不一定形成三点菜单。不要将 XML 文件数量误认为实际可见 Popup 数量。

## 1. 通用菜单外观/位置的唯一共享链

- `helpers/OxygenClassicMenus.kt`：`theme(context)` 选深浅 Popup Theme，`createPopup(context,anchor)` 使用 `PopupMenu(ContextThemeWrapper(...), anchor, Gravity.END, actionOverflowMenuStyle, 0)`。
- `helpers/OxygenToolbarStyle.kt`：`preparePopup(activity, toolbar / MySearchMenu)` 设置 `toolbar.setPopupTheme(...)`；`applyToolbar()` 控制 56dp 顶栏、图标前景、ActionMenu 与 inset。
- `res/values/oxygenos_classic.xml`：`OxygenClassicOverflow.Light/Dark` 定义 `overlapAnchor=true`、水平 `-20dp`、垂直 `16dp`；`OxygenClassicPopupTheme.*`、`OxygenClassicPopupMenu.*`、`OxygenClassicMenuList`、`OxygenClassicPopupCheckbox` 定义文字、行高、背景、阴影。
- `res/values-ldrtl/oxygen_classic_menu_offsets.xml`：RTL 水平 `+20dp` 偏移。
- `res/drawable/oxygen_classic_menu_light.xml` 和 `_dark.xml`：菜单表面、4dp 圆角、上下 8dp padding；`res/drawable/oxygen_classic_popup_{light,dark}.xml` 是**Dialog 背景**，不要与菜单背景混用。
- `res/layout/abc_popup_menu_item_layout.xml`、`abc_cascading_menu_item_layout.xml`：覆盖 AppCompat 标准行，根为 `views/TiebaMenuItemView.kt`，包括 title/shortcut/submenuarrow/运行时 CheckBox 与 Radio 的容器。
- `views/TiebaMenuItemView.kt`：`onMeasure()` 当前 280dp 上限；**旧实现**在 `initialize()` 对收藏页两项 Checkbox 作特殊绘制；**新方案应删除这段专属特判**，不影响整站其他 Checkbox 或菜单行测量。
- `helpers/OxygenClassicControls.kt`：普通 CheckBox、Radio、Switch 的样式，区别于 Popup 内 AppCompat 自动创建的勾选控件。

## 2. 完整静态菜单资源 → 可见宿主 → 分发入口

| res/menu XML | 宿主/真实页面 | Kotlin 入口、用途和注意 |
|---|---|---|
| `menu_main.xml` | 相册主页（文件夹视图）、底栏「收藏」**收藏文件夹**页 | `activities/MainActivity.kt:setupOptionsMenu()`（约 855–940）；`refreshMenuItems()`（约 817）；两项收藏**无勾选双态文字操作**只在 `mShowingFolderFavorites` 时显示（当前代码仍为 Checkbox，待后续实施） |
| `menu_main_intent.xml` | 外部 PICK/GET_CONTENT 等调用下的主页 | `MainActivity.setupOptionsMenu()`；只含隐藏/排除临时可见项，不能误应用普通主页菜单状态 |
| `menu_media.xml` | 普通物理文件夹、主页「全部文件」、固定虚拟收藏**媒体**文件夹、回收站、自定义「照片」配置网格 | `activities/MediaActivity.kt:setupOptionsMenu()`（约 1269），`refreshMenuItems()`（约 1203–1267）；按 `mPath`、`isPhotoProfiles`、Picker 标志动态切换可见性；回收站特有清空/全部恢复项 |
| `menu_viewpager.xml` | Gallery 内部全屏连续浏览 | `activities/ViewPagerActivity.kt:setupOptionsMenu()`（约 779），`refreshMenuItems()`（约 726）；包含旋转/朝向多级菜单、删除/分享/恢复/设置等 |
| `photo_video_menu.xml` | 单文件 `PhotoVideoActivity`（例如外部查看或中转） | `activities/PhotoVideoActivity.kt:setupOptionsMenu()`（约 143），`fragmentViewerToolbar`，按 URI/媒体类型显示操作 |
| `menu_video_player.xml` | 独立视频播放器 | `activities/VideoPlayerActivity.kt:setupOptionsMenu()`（约 365），方向子菜单、分享、用其他应用打开 |
| `menu_editor.xml` | 编辑器工具栏（`activity_edit.xml` app:menu） | `activities/EditActivity.kt:setupOptionsMenu()`（约 185），保存/覆盖/编辑方式/分享 |
| `menu_search.xml` | 单独搜索界面 | `activities/SearchActivity.kt:setupOptionsMenu()`（约 90），当前只有文件名显示工具栏操作；未必有溢出窗口 |
| `menu_photo_profile_edit.xml` | 新建/编辑自定义照片配置 | `activities/PhotoProfileEditActivity.kt:onCreate()`（约 95–100），保存为 always；与底部添加来源 FAB 不是同一个组件 |
| `menu_add_folder.xml` | 特定文件夹管理/添加上下文 | 检查 `activities/ExcludedFoldersActivity.kt` 等管理页面的 Toolbar/XML menu 或 menu inflate；目前为单动作 `add_folder`，不应强行添加三点动画 |
| `menu_included_folders.xml` | 已包含文件夹管理 | `activities/IncludedFoldersActivity.kt` + `res/layout/activity_manage_folders.xml`；单个添加动作 |
| `menu_set_wallpaper.xml` | 设置壁纸 | `activities/SetWallpaperActivity.kt:setupOptionsMenu()`（约 80），保存与裁剪宽高比选项 |
| `cab_directories.xml` | 文件夹网格多选上下文 ActionMode | `adapters/DirectoryAdapter.kt`；固定/解固定、移动顺序、隐藏、删除、收藏文件夹等；不是普通 Toolbar Popup |
| `cab_media.xml` | 文件夹内图片/视频网格多选 ActionMode | `adapters/MediaAdapter.kt`；复制/移动/收藏/回收站恢复/日期修复等；跟进其状态权限 |
| `cab_hidden_folders.xml` | 隐藏文件夹管理多选 ActionMode | `adapters/ManageHiddenFoldersAdapter.kt` 等；只有 `cab_unhide` |

上述 XML 中标记为 `always`/`ifRoom` 的按钮可能直接停留在工具栏，也可能因屏宽/动态配置被移入溢出；审计时以 **运行时 ActionMenuPresenter** 结果为准。不能因为 XML 有 `never` 就认定对应按钮在全部页面可见。

## 3. 动态 PopupMenu（没有单独 menu XML）

| 所在页/操作入口 | 生成调用 | 目标功能 | 风险 |
|---|---|---|---|
| 自定义「照片配置管理」每个配置行右侧三点 | `activities/PhotoProfileManageActivity.kt:showActions(id,anchor)`（约 165–193）；锚点来自 `adapters/PhotoProfileManageAdapter.kt` 的 `profileManageMore` | 使用、编辑、重命名、删除 | Popup 打开时配置可能已删；代码在点击时重新按 UUID 查询 |
| 添加照片来源选择器右侧三点 | `dialogs/PickDirectoryDialog.kt:showPickerOptions()`（约 215–243），锚点 `directoriesOverflowMenu`（`res/layout/dialog_directory_picker.xml`） | 暂时显示隐藏/排除项目，或恢复默认 | 密码授权与选择器内的当前路径状态 |
| 文件选择器的隐藏文件入口 | `helpers/ClassicFilePickerOverflowButton.kt:openOptions()`（约 49–72），`res/layout/dialog_filepicker.xml` 的 `filepicker_overflow_menu` | 代理 Commons 原隐藏文件 FAB 的单次授权逻辑 | 不能直接调用内部显示设置绕过 Commons 鉴权 |
| 设置 → 信息与操作栏 → 自定义顶栏操作中的每个排序手柄 | `helpers/ToolbarQuickActionsDialog.kt`（约 310–326），`OxygenClassicMenus.createPopup()` | 上移/下移当前动作 | ViewHolder 位置失效、重排时 Popup 关闭与焦点恢复 |
| 设置 → 外观与界面 → 底栏顺序中的手柄 | `helpers/BottomNavigationOrderDialog.kt`（约 96–112） | 上移/下移底栏项 | 始终保留原列表顺序配置与 48dp 目标 |
| 其他 Builder/系统右键行为 | Commons `7.1.3` 或第三方编辑/文件浏览组件 | **待动态确认**：实现可能不在 Gallery 内 | 不能用“Gallery 仅有五处”声称已覆盖所有可能的系统上下文菜单 |

**设置主页与设置内页本身不是全都有“三点菜单”**：`SettingsActivity` 通过 `activity_settings.xml` 和 `SettingsActivity.setupSettingItems()` 组织点击行；上述两处“上/下移动”是设置内部的**真实锚定三点 Popup**，而选项（排序方式、过滤、底栏管理）多是 Dialog，不是 Popup。

## 4. 收藏页两条菜单的“按当前状态显示下一步操作”设计（取代 CheckBox 方案）

**当前源码的历史状态（本次尚未改码）**：
- `res/menu/menu_main.xml`：`favorite_always_show_excluded`、`favorite_always_show_hidden` 目前为 `android:checkable="true"`，顺序值约 40/41。
- `MainActivity.refreshMenuItems()`：当 `mShowingFolderFavorites` 时设置 `isVisible`，并按 `config.favoriteShowExcluded/favoriteShowHidden` 设置 `isChecked`。
- `MainActivity.setupOptionsMenu()`：点击当前条目会翻转布尔配置、清除旧目录显示并重载；选择开启时可能要求受保护目录授权。**当前代码在授权被取消时仍会保存新的配置值**，未来 UI 文案应反映该保存值，不应擅自修改行为。
- `TiebaMenuItemView.initialize()`：现有对两个 ID 的 CheckBox 着色与留白特判；**新方案移除而非继续调试**。Commons FilePicker 里的其他 checkable item 不受此修改影响。

**新目标（用户已确认）**：

| 当前配置状态 | `favorite_always_show_excluded` 行应显示 | `favorite_always_show_hidden` 行应显示 |
|---|---|---|
| `false`（当前不启用） | **显示已排除项目** | **显示隐藏项目** |
| `true`（当前启用） | **不显示已排除项目** | **不显示隐藏项目** |

两项保持原 MenuItem ID、显示条件、排序顺序、持久化配置和点击业务回调；各为**普通纯文本行**，必须 `checkable=false`，不创建 CheckBox/Radio，右侧不预留原勾选框。菜单再次打开/状态刷新时更新标题；使用 4 条独立的本地化字符串，按系统语言翻译。这里的“显示/不显示”指**用户点击后要执行的操作**，不是当前状态标签。暂时显示隐藏/排除项目的另外两组菜单仍是独立功能，不改其含义。

**新验收**：组合 `FF/FT/TF/TT` 四种持久化状态、菜单重开、切换语言/主题、旋转、长文案换行、认证取消，都应保持文字与配置相符。无需再检查旧 CheckBox 的绘制缺失原因。

## 5. Popup 打开/关闭动画应修改哪里？

1. 菜单配置：`OxygenClassicMenus`、`OxygenToolbarStyle.preparePopup`、`oxygenos_classic.xml` 与 Popup 背景。
2. **打开与关闭行为**：多数由 AppCompat `Toolbar.showOverflowMenu` / `PopupMenu.show/dismiss` 的内部 `PopupWindow`/ActionMenuPresenter 持有；本分支没有观察到一个统一 `animateOpenMenu()/animateDismissMenu()` 实现。**不要在 `TiebaMenuItemView` 的每一行上启动菜单整体动画**。
3. 为开/关视觉添加过渡必须先验证 AppCompat 支持的公开 Window 动画样式及安卓版本上的行为，再考虑包装的 presentation 层；**不建议反射其私有 PopupWindow**。
4. 菜单应以原生窗口尺寸为基线，在显示前一次性确定 anchor、宽度、菜单安全区；关闭后恢复原 View 焦点和按压状态。
5. 任何被关闭后立刻重新显示的菜单必须能取消动画；长菜单/IME/RTL/深色模式不能出现留影或遮挡。

### 相关自动化与真机记录

- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicOverflowPositionTest.kt` 已有 LTR/RTL、3/20 项 PopupWindow 位置和截图生成测试；后续需要加入配置管理/设置手柄/Picker/回收站/长中文、**收藏纯文字两态宽度**及其他普通 CheckBox 场景。
- `docs/testing/classic-motion-device-checks.md` 保留底栏、Ripple 与真实窗口帧验收标准。
- 真机审计记录字段：`screen / anchorView / popupType / itemCount / itemIds / checkedStates / boundsDP / insets / layoutDirection / fontScale / enterMs / exitMs / clickSource / dismissReason`。

> 此文档只提供代码路线图；所有“应该怎样改动画”的内容均是设计建议，没有任何 UI 改动或运行结果。
