# Phase 0 · 01 — 菜单、Dialog/Picker 与设置页清单

> 代码锚点 024b4731ac6508cab45f7d81eea73294a78d0d04。未在设备测量菜单矩形、文字 baseline 或对话框 window bounds。

## 1. 三点菜单资源与动态 Popup 覆盖

当前 app/src/main/res/menu 有 15 个 XML：

| 资源 | 页面/用途 |
|---|---|
| menu_main.xml | MainActivity 相册主页及收藏文件夹主页 |
| menu_main_intent.xml | MainActivity 外部 PICK / GET_CONTENT |
| menu_media.xml | MediaActivity 普通/nomedia目录、全部文件、虚拟收藏、回收站、照片配置媒体 |
| menu_viewpager.xml | ViewPagerActivity 全屏 Viewer |
| photo_video_menu.xml | PhotoVideoActivity 单媒体/外部媒体 |
| menu_video_player.xml | VideoPlayerActivity |
| menu_editor.xml | EditActivity |
| menu_search.xml | SearchActivity |
| menu_photo_profile_edit.xml | PhotoProfileEditActivity 保存操作 |
| menu_add_folder.xml | 文件夹管理添加动作 |
| menu_included_folders.xml | IncludedFoldersActivity |
| menu_set_wallpaper.xml | SetWallpaperActivity |
| cab_directories.xml | DirectoryAdapter 文件夹多选 ActionMode |
| cab_media.xml | MediaAdapter 媒体多选 ActionMode |
| cab_hidden_folders.xml | 隐藏文件夹多选 ActionMode |

XML 菜单不等于 Popup 数量；always/ifRoom 动作会因运行宽度和 Presenter 变为按钮或 overflow。动态 anchored Popup 入口：PhotoProfileManageActivity.showActions、PickDirectoryDialog.showPickerOptions、ClassicFilePickerOverflowButton.openOptions、ToolbarQuickActionsDialog 与 BottomNavigationOrderDialog 的排序手柄。Commons/系统组件可有自有上下文菜单。

## 2. 共享菜单链与静态规格

OxygenClassicMenus.theme 根据 OxygenThemeEngine.snapshot 的 dark 选主题；createPopup 使用 AppCompat PopupMenu、anchor 与 Gravity.END（helpers/OxygenClassicMenus.kt:12–22）。OxygenToolbarStyle.preparePopup 给 MaterialToolbar 设置 Popup Theme（36–43）。oxygenos_classic.xml 配 overlapAnchor、LTR -20dp 水平偏移、16dp 垂直偏移；values-ldrtl/oxygen_classic_menu_offsets.xml 配 RTL +20dp。菜单 drawable 为 4dp 圆角、上下 8dp padding。TiebaMenuItemView.onMeasure 最大宽度 280dp（68–82）。

上述是资源/源码配置，不是最终 popup rect。实际锚点矩形、系统 inset、安全区、行高、字体 baseline、图标间距、菜单最大高度、Window 裁切、长文案换行和各类 RTL 结果都没有当前 commit 的真机坐标。AppCompat ActionMenuPresenter/PopupMenu 持有 popup window，当前目标文件未见统一 Gallery open/dismiss animation。不得反射私有 PopupWindow 或把 ActionMode 当三点菜单。

主要覆盖宿主：相册主页、收藏文件夹主页、普通文件夹、nomedia、回收站、自定义照片配置、全屏图片/视频 Viewer、Picker、文件/媒体选择、配置管理、设置重排、ActionMode 与子菜单。逐项记录 source path / MenuItem ID / anchor / checkable / width rule / ripple/theme owner / back-dismiss 的运行态验证仍待设备。

## 3. 收藏菜单：新目标与最小修法

menu_main.xml:29–35 当前两个 ID favorite_always_show_excluded、favorite_always_show_hidden 设 android:checkable=true。MainActivity.refreshMenuItems（817–831）仅在 mShowingFolderFavorites 时显示并按 config.favoriteShowExcluded/Hidden 写 isChecked。TiebaMenuItemView.initialize（21–39）对这两个 ID 注入/设置 Checkbox 外观。

MainActivity.setupOptionsMenu 的 handler（902–920）先翻转并保存 boolean，再清理目录/刷新菜单，然后进入鉴权或刷新流程。excluded 分支有源码注释说明认证取消时配置仍保存。故 menu title 必须反映已保存的当前配置；不得按认证结果推测，也不得顺手更改授权取消行为。

| 当前设置 | 下一次操作文案 |
|---|---|
| favoriteShowExcluded=false | 显示已排除项目 |
| favoriteShowExcluded=true | 不显示已排除项目 |
| favoriteShowHidden=false | 显示隐藏项目 |
| favoriteShowHidden=true | 不显示隐藏项目 |

Phase 1 最小文件：menu_main.xml 明确两项 non-checkable；MainActivity.refreshMenuItems 改成依据当前值设置 title，不再 isChecked；TiebaMenuItemView 删除仅对这两个 ID 的 Checkbox 特判；资源提供四种可本地化字符串。保留 IDs、order、收藏主页可见性及 handler。其他菜单的 Checkbox 完全不受影响。测试四种组合、取消认证后的文案、重新打开菜单、长文案/RTL/窄屏；不改变配置保存时点、认证和刷新语义。

## 4. Settings 主页及内页

SettingsActivity 注册 8 类：appearance、gallery、photos、controls、videos、files、security、advanced（约 62–111）；setupSettingsHub 创建 TiebaSettingsCategoryRow（186–214）。renderSettingsPage 通过 itemIds 筛出原设置内容；不是独立 Fragment/NavHost 页面。主页行源码规格：最小 72dp、16dp 横向边距、56dp 左图标槽、36dp icon container、标题 16sp、副标题 14sp。设置内页标准 single/details row 为 56/72dp；section padding 16/8dp，min height 0（XML/helper 资源）。这些是源码/资源值；真实 density bounds 未测。

设置行点击与 Switch 范围、分割线 y、文字 baseline、当前选中状态、横屏/RTL/字号缩放和 TalkBack bounds 尚无运行实测。部分 sw600dp/w480dp/w600dp 资源存在，不等于测试覆盖。category 返回滚动至 0；没有发现内页每类独立滚动位置保存/恢复逻辑。

### 设置动画调用链与替代条件

SettingsActivity.animateSettingsPageChange（229–258）仅对 settingsNestedScrollview 操作：先 cancel 旧 animator，递增 settingsTransitionEpoch；系统动画关闭则直接执行 changePage 并归一化。否则 80ms 淡出至 alpha 0.12、水平 8dp；调用 changePage 重绘同一容器内容；再以 100ms 淡入到 alpha=1 / translationX=0。插值器 OxygenClassicMotion.easing 为 PathInterpolator(0.4,0,0.2,1)。epoch 和 onDestroy cancel 用来阻止旧回调覆盖当前页。

此处没有可独立控制的 outgoing/incoming 内容 View；hub 与筛选后设置行由共用 ScrollView/holder管理。SharedAxis 不能直接套在被重用/重建的同一 View 上并声称可无缝接入。只有后续稳定地形成两个独立容器时再评估；否则保留当前有限 property animation。局部说明/空态可单独评估 MaterialFade，但依赖需先解析。

## 5. Dialog 与 Picker

当前 Gallery dialogs 目录有 26 个类：AllFilesPermissionDialog、ChangeFileThumbnailStyleDialog、ChangeFolderThumbnailStyleDialog、ChangeGroupingDialog、ChangeSortingDialog、ChangeViewTypeDialog、ClassicPasswordMethodDialog、ConfirmDeleteFolderDialog、CustomAspectRatioDialog、DeleteWithRememberDialog、ExcludeFolderDialog、ExportFavoritesDialog、FilterMediaDialog、GalleryRestorePreviewDialog、GrantAllFilesDialog、ManageBottomActionsDialog、ManageExtendedDetailsDialog、OtherAspectRatioDialog、PickDirectoryDialog、PickMediumDialog、ResizeDialog、ResizeMultipleImagesDialog、ResizeWithPathDialog、SaveAsDialog、SlideshowDialog、StoragePermissionRequiredDialog。

概括分类：普通确认/权限/删除恢复；输入/路径/保存/批处理；排序、分组、筛选和显示选项；目录/媒体 Picker；密码与安全；GalleryRestorePreview 等恢复流程。另有动态 AlertDialog 和 Commons 7.1.3 自持 FilePicker/Security/PIN/Pattern/biometric dialogs。组件分类不意味着创建/关闭函数完全共享。

OxygenClassicDialog.show（44–75）创建 Dialog 并过 ClassicCommonsHooks；prepareWindow 使用公开 Window 属性设置 adjustResize、宽度和背景；宽度依内容 Profile 与 available insets，分类上限约 278/320/340/360/380/400/480/560dp；Surface light white/dark #303030、圆角 4dp，dim 为普通 0.45 / nested 0.20 / 隐藏biometric 0。ClassicCommonsHooks 有 first-draw/layout guard，避免未布局 Window 先显示；未发现 Gallery 统一 setWindowAnimations。Window 动画由 Dialog/Theme 窗口持有，内部 View animator另论。

MaterialFade/AndroidX Transition 是 View/Fragment 转场，不接管整个 AlertDialog Window、PopupWindow、IME 或系统 biometric Window。Picker breadcrumb Back、授权和 confirm/cancel 回调必须先于动画。安全/PIN/Pattern/biometric 不作为第一批窗口动画试点。后续最小试点只能是无业务副作用普通确认 dialog，按公开 Window style 检查，保留尺寸、dim、输入法、取消和单次回调。

