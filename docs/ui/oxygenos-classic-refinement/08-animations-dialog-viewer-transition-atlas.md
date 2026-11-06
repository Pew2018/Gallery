# 08 · 动画全景：Dialog/三点菜单、图片与文件夹进出、Viewer 左右滑动

> 状态：**动画调用链审计 + 候选优化方案**，本轮未修改动画实现。基线 `27008fc1b5226187c3c8b99c0a12911aa5163632`。以下时长是现有应用值或未来候选，**不是经验证的 OnePlus ROM 原生时长**。
> 所有 `Gallery/` 相对文件位置均在 `app/src/main/`；Commons 路径位于 `FossifyOrg/commons` tag `7.1.3`。

## 1. 该不该优化？结论与优先级

**正式技术选型（用户已确认）**：复用 Google **Material Design 2 时期的 MDC-Android Views Material Motion**：`MaterialSharedAxis`（真实页面层级）、`MaterialFade`（子 View 显隐）、`MaterialFadeThrough`（互斥内容），但控件形态/主题/菜单/弹窗规格仍保持 OxygenOS Classic，**不引入 Material 3 UI**。具体代码位置、依赖、API 边界和候选时长见 [11](11-legacy-material-motion-selection.md)。

**动态强调色硬要求**：所有涉及颜色的选中、Ripple、焦点、输入、进度、EdgeGlow、过渡容器色均由 `OxygenThemeEngine.snapshot()` 基于用户**预设/自定义强调色**派生；截图蓝色不是固定实现值。见 [10](10-accent-semantic-and-runtime-invariants.md)。

**可以优化，但必须分场景，不能“给所有弹窗统一加一套弹性动画”。** 特别是之前已有菜单、Dialog、底栏切换闪烁和媒体逐张位移问题。推荐顺序：

1. 静态可用性：收藏菜单**无勾选双态文案**、菜单宽高/锚点、FAB+IME（明确的 P1 项）；其他场景中的 Checkbox 仍需正常工作；
2. 克制的 Popup/普通 Dialog **进入/退出**（可取消、无输入焦点延迟）；
3. Viewer 内容之外的工具栏、底部操作按钮、搜索焦点的微交互；
4. 图片打开/退出、Viewer→网格、文件夹→主页的 Activity 转场（高风险，必须有稳定/授权的首帧基线）；
5. 全屏 ViewPager 左右滑动调速或插值（最高数据同步风险，**优先保留原生 ViewPager 手指跟随**）。

## 2. 总体转场所有权矩阵

| 转场/交互 | 当前代码与样式 | 现有事实 | 拟议做法 / 不可越界边界 |
|---|---|---|---|
| 普通 Toolbar 三点打开/关闭 | `OxygenClassicMenus.kt`、`OxygenToolbarStyle.kt`、`res/values/oxygenos_classic.xml`、`TiebaMenuItemView.kt` | 主要由 AppCompat Popup/ActionMenuPresenter 的 PopupWindow 实现；目前没有统一手写 Popup 开闭动画控制器 | 第一轮只测 `show/dismiss` 真实窗口行为；必要时使用公开 API 可用的 window 动画配置。不要在每个 item 上对整个菜单做缩放 |
| 配置管理/Picker/设置手柄动态三点 | `PhotoProfileManageActivity.showActions`、`PickDirectoryDialog.showPickerOptions`、`ClassicFilePickerOverflowButton.openOptions`、`ToolbarQuickActionsDialog`、`BottomNavigationOrderDialog` | 复用 `OxygenClassicMenus.createPopup()` | 位置/Enter/Exit 与 Toolbar 菜单一同校准，但独立覆盖 anchor 生命周期 |
| Gallery 经典 Dialog 打开/关闭 | `helpers/OxygenClassicDialog.kt`、`helpers/ClassicCommonsHooks.kt`、`res/values/oxygenos_classic.xml`、`res/drawable/oxygen_classic_popup_*.xml` | 共同处理尺寸、样式与初次绘制；没有发现 Gallery 统一为 Dialog 设置的自定义 enter/exit 动画 token | 先记录系统默认 Window 动画和 scrim；试点平滑淡入/轻微 Y 位移、退出反向，不重新创建 Dialog 内容树；保持原按钮和取消回调 |
| 带输入框的 Dialog | `OxygenClassicDialog.styleInput` + `ClassicCommonsHooks` + 各 Layout 的 `TextInputEditText` | 已有 IME 独立宽度/可滚动内容逻辑 | **动画不得在请求焦点/显示 IME 前等待完成**，窗口尺寸不随键盘抖动；多层 Dialog 的父弹窗 dim 不被重复应用 |
| 设置主页→分类/返回 | `SettingsActivity.animateSettingsPageChange()` | 退出 80ms 到 alpha 0.12/位移 8dp；进入 100ms 恢复 | 候选降低淡出幅度，保持 epoch 取消保护；不干扰设置保存 |
| OxygenTheme 内部颜色子页 | `OxygenThemeActivity.navigateColors()` | 使用约 150ms translation/alpha | 同类型设置页动作可共享 motion token，但不能破坏现有页内局部状态 |
| 底栏不同根页面 | `GalleryBottomBar.open()`；`anim/gallery_root_enter.xml`、`gallery_root_hold.xml` | 目前真正 Activity 切换用 `overridePendingTransition(0,0)`；XML 两个 160ms alpha 1→1 只是已有资源，**不能认定当前被调用** | 维护 first-draw gate 和不透明画面，只在底栏选中 tint 处尝试轻量动画；不恢复旧闪屏 |
| 普通文件夹 → 媒体网格 | `MainActivity.itemClicked()` → `MediaActivity` | 普通 `startActivityForResult`，含保护目录鉴权步骤 | 若增加 Window transition，来源界面保持直到目标已准备好正确颜色/隐私状态；无共享媒体时可先仅微弱转场 |
| 媒体缩略图 → 全屏 Viewer | `MediaActivity.itemClicked/openOrdinaryViewer/openInViewPager()` → `ViewPagerActivity` | 打开前准备临近媒体、可见顺序和会话标记，部分环节异步 | 优先保持快进与目标照片优先绑定；过渡不要等待全目录扫描；如共享元素，先只试非受保护且已验证单图 |
| Viewer 工具栏/底部显隐 | `helpers/ViewerMotion.kt:ViewerChromeMotion.show()` + `OxygenClassicMotion.VIEWER_CHROME_MS=170` | 有 generation 取消机制，alpha 动画可打断 | 可作为小范围调整，不在每一页替换动画容器 |
| Viewer 加载提示 | `ViewerLoadingMotion` + `OxygenClassicMotion` | 120ms 延迟、180ms 最短可见、140ms fade | 保留“反馈不延迟真实媒体发布”的约束 |
| Viewer 常规左右滑动 | `views/ProgressivePhotoViewPager.kt`、`ViewPagerActivity` 页状态、`adapters/MyPagerAdapter.kt` | 基于原生/Commons ViewPager 的交互滚动，扫描完成发布推迟到 `SCROLL_STATE_IDLE` | **不通过换 ViewPager/自定义翻页 Animator 改写分页语义**。先调视觉 PageTransformer 的可行性（若加入须确保缩放子控件/RTL/多指手势） |
| Viewer 幻灯片 | `ViewPagerActivity.animatePagerTransition()`（约 2335） | 用 `ValueAnimator` 实现幻灯片翻页；跟普通手指滑动不同 | 分开测试，不能把幻灯片动画时长套给手指滑动 |
| 单图向下滑退出 | `fragments/ViewPagerFragment.handleEvent()` | `finish()` + Commons `R.anim.slide_down` | 保持现有纵向关闭语义，与边缘横向系统返回区分；避免同屏叠加两种退出动画 |
| Viewer → 网格返回 | `MediaActivity` 的 `FavoritesReturnCover`、`FavoritesScrollRestorer`、目录 snapshot | 有保护收藏权限与滚动锚点/返回遮盖的特有逻辑 | **不能为转场降低隐私屏障**，待 grid 准备后再渐变移除遮盖；其他来源也需保证不闪旧图 |
| 普通文件夹 → 主页相册 | `MediaActivity` finish / Commons `BaseSimpleActivity` Back fallback | 当前主要依赖系统 Window 返回；不属于底栏根切换 | 先区分 system-back 与 toolbar Back，再评估是否应交系统预测性返回 |
| 输入聚焦/键盘开闭 | `OxygenToolbarStyle`、`OxygenSearchNavigation`、Commons `MySearchMenu`、`OxygenClassicPageInput` | 关闭搜索时宽度 1dp/alpha 0，打开时全宽/alpha 1 | 推荐轻量可中断动画和 Insets 同步，不能等待动画才过滤数据 |
| EdgeEffect 顶/底 | RecyclerView/NestedScrollView/SwipeRefresh | 未证实全页面有统一旧版 Android Glow | 先判定实际 over-scroll owner，再由共享工厂/相应 View 实现小幅经典发光 |

## 3. Gallery 自己实现的 Dialog：逐文件可导航列表（26 个类/对象）

**文件目录**：`app/src/main/kotlin/org/fossify/gallery/dialogs/`。所有这些均应纳入入口/退出动画审计；下面显示的是类位置和主题功能，而不是说明所有弹窗当前都缺动画。

| 文件 | UI 类型 / 参考布局 |
|---|---|
| `AllFilesPermissionDialog.kt` | 全部文件权限说明；Commons `dialog_message` |
| `ChangeFileThumbnailStyleDialog.kt` | 文件缩略图风格/大小；`dialog_change_file_thumbnail_style.xml` |
| `ChangeFolderThumbnailStyleDialog.kt` | 相册封面风格；`dialog_change_folder_thumbnail_style.xml` |
| `ChangeGroupingDialog.kt` | 分组选择；`dialog_change_grouping.xml` |
| `ChangeSortingDialog.kt` | 排序选择；`dialog_change_sorting.xml` |
| `ChangeViewTypeDialog.kt` | 视图方式；`dialog_change_view_type.xml` |
| `ClassicPasswordMethodDialog.kt` | 密码方式；动态 `OxygenClassicDialog` |
| `ConfirmDeleteFolderDialog.kt` | 删除文件夹确认；`dialog_confirm_delete_folder.xml` |
| `CustomAspectRatioDialog.kt` | 自定义长宽比；`dialog_custom_aspect_ratio.xml` |
| `DeleteWithRememberDialog.kt` | 删除并记住选项；`dialog_delete_with_remember.xml` |
| `ExcludeFolderDialog.kt` | 排除目录；`dialog_exclude_folder.xml` |
| `ExportFavoritesDialog.kt` | 收藏导出；`dialog_export_favorites.xml` |
| `FilterMediaDialog.kt` | 媒体过滤；`dialog_filter_media.xml` |
| `GalleryRestorePreviewDialog.kt` | 备份恢复预览/再次确认；动态 Dialog |
| `GrantAllFilesDialog.kt` | 全部文件授权引导；`dialog_grant_all_files.xml` |
| `ManageBottomActionsDialog.kt` | 自定义底部操作；`dialog_manage_bottom_actions.xml` |
| `ManageExtendedDetailsDialog.kt` | 自定义媒体详细信息；`dialog_manage_extended_details.xml` |
| `OtherAspectRatioDialog.kt` | 其他长宽比；`dialog_other_aspect_ratio.xml` |
| `PickDirectoryDialog.kt` | 目录/自定义照片来源选择器；`dialog_directory_picker.xml` |
| `PickMediumDialog.kt` | 媒体选择器；`dialog_medium_picker.xml` |
| `ResizeDialog.kt` | 图片调整大小；`dialog_resize_image.xml` |
| `ResizeMultipleImagesDialog.kt` | 批量调整大小；`dialog_resize_multiple_images.xml` |
| `ResizeWithPathDialog.kt` | 改大小/目录/名称；`dialog_resize_image_with_path.xml` |
| `SaveAsDialog.kt` | 另存为；`dialog_save_as.xml` |
| `SlideshowDialog.kt` | 幻灯片参数；`dialog_slideshow.xml` |
| `StoragePermissionRequiredDialog.kt` | 访问权限说明；`dialog_storage_permission_required.xml` |

另有 `helpers/ToolbarQuickActionsDialog.kt`、`helpers/BottomNavigationOrderDialog.kt` 是**对话框创建/管理逻辑但不位于 dialogs/**；`activities/PhotoProfileManageActivity.kt:renameProfile()/confirmDelete()`、`activities/OxygenThemeActivity.kt:chooseColor()` 也动态创建对话框。禁止因为只扫描 `dialogs/` 就漏掉它们。

### 依赖提供的 Commons 7.1.3 Dialog

位于 [Commons 7.1.3 dialogs 源码目录](https://github.com/FossifyOrg/commons/tree/7.1.3/commons/src/main/kotlin/org/fossify/commons/dialogs)；当前 Gallery 依赖来自 `gradle/libs.versions.toml`。

与 Gallery UI 相关、需要按实际调用补充记录的入口包括：

`FilePickerDialog`、`CreateNewFolderDialog`、`PropertiesDialog`、`BasePropertiesDialog`、`RadioGroupDialog`、`ChangeDateTimeFormatDialog`、`ConfirmationDialog`、`ConfirmationAdvancedDialog`、`EnterPasswordDialog`、`SecurityDialog`、`RenameDialog`、`RenameItemDialog`、`RenameItemsDialog`、`ExportSettingsDialog`、`StoragePickerDialog`、`ColorPickerDialog`、`PermissionRequiredDialog`、`WritePermissionDialog`、`FileConflictDialog` 与 `FolderLockingNoticeDialog`。

**注意**：Commons 还有 Compose 对话框、依赖自己的文件夹选择/鉴权逻辑；是否被 Gallery 当前路径调用须以实际入口为准。Gallery 通过 `ClassicCommonsHooks` 对 Commons 的显示流程做已存在的展示层 hook，不能直接替换它们的持久化和权限代码。

## 4. Dialog 开闭动画的建议设计

**避免错误引用**：`com.google.android.material.transition.MaterialFade` 的 AndroidX 版本只处理 View/Fragment；不能直接对整个 AppCompat AlertDialog Window 使用。真正的 Window 入退应在原有 `OxygenClassicDialog.prepareWindow()` 和公开 window animation style 层试点；Dialog 内部可控 View 显隐才考虑 MaterialFade。模态按钮、聚焦与 Ripple 用当前用户主题 Token，不借用 Material 默认蓝色。

**技术基础与触点**

- `OxygenClassicDialog.kt`：`prepareWindow()/apply()/show()/builder()`，负责窗口几何、色彩、滚动、按钮与输入框样式。
- `ClassicCommonsHooks.kt`：固定 Commons 7.1.3 的展示 Hook，处理初次绘制/窗口边界变化；必须保证幂等，不应在每一帧重设窗口 Drawable。
- `oxygenos_classic.xml` 与 `oxygen_classic_popup_*.xml`：Dialog Window/背景；将 PopupMenu 样式与 Dialog Window 样式分开。
- `OxygenClassicMotion.kt`：目前有 Switch、Tab、Settings、Viewer 等 token；没有为所有 Dialog/Popup 自定义开闭 token 的证据。
- `res/anim/gallery_root_enter.xml`、`gallery_root_hold.xml` 虽然存在，但与 Dialog Window 动画不是同一机制。

**候选视觉**：普通 Dialog 进入约 140–200ms，短距离（如 4–8dp）平移 + 轻淡入；退出约 100–160ms 反向；Popup 较短但仍应优先尊重 AppCompat native window。上述都是**需真机测定的实验参数**，不声称来自 OxygenOS ROM 的确切时长。

**分级实现建议**

1. 先只在无 IME 的确认型 Dialog 试验；保存原 Window 样式、暗层与 enter/exit 截屏。
2. 若可以通过 AppCompat/Window 公开 style 确保闭合时机、焦点和外部点击统一，优先资源方式；否则仅加 presentation wrapper，不能 hack 私有弹窗字段。
3. 带输入、Radio/Checkbox、媒体 Picker/文件 Picker、密码/PIN/图案/生物识别、多层确认全部单独验收。它们的打开/关闭会与 IME、权限和 Fragment 生命周期交叉。
4. 点击取消、按 Back、触摸窗口外、用户选项完成、Activity 销毁、快速连续弹两个 Dialog，各自只触发业务回调一次。
5. 尊重系统动画 scale 0；显示/消失必须跳到最终状态，不能残留 dim、禁用触摸或透明 Window。
6. 特别禁止在 Dialog 开始动画时清除/替换原始 ViewBinding、焦点或 Insets 监听器。

## 5. 图片打开/退出与全屏滑动的建议设计

**MaterialContainerTransform 的级别**：只属于可选受控实验，不是全站必做。Viewer 的 Fragment/目录扫描/邻页预取/权限比容器变形复杂，若无安全稳定的跨 Activity shared element 就沿用系统动画；Viewer 左右拖动继续现有 ViewPager，禁止套用 SharedAxis/FadeThrough。未来 ContainerTransform 的容器和 scrim 颜色按实际用户主题派生。

- 对普通“点击缩略图进入全屏”：**先考察默认 Activity open/close**，再比对 OnePlus 参考；不要以缩略图截图快照长期覆盖真实 Viewer 内容。
- 如果考虑 Shared Element：必须满足 selected path 已经通过授权、图片尺寸/矩形可从当前 RecyclerView 获取、不重新构造 Fragment、不因扫描部分结果替换 anchor；候选先在单个**未受保护的普通物理文件夹**实验，特殊来源暂退回系统动画。
- 全屏“点按显隐 chrome”可以精修 `ViewerChromeMotion`，但不能触发 ViewPager `setCurrentItem` 或左右预取。
- “左右翻页”优先保持手指拖动的直接跟随、原生 settle；如果尝试 PageTransformer，需要证明在媒体还未 READY、被删除、RTL、图片放大、切换视频时不会出现旧缩略图闪帧。
- “Viewer 返回文件夹”与“文件夹返回主页”分开设计：前者要保证返回的媒体网格滚动锚点/权限/当前排序，后者要恢复目录层级和搜索状态；两者都不能被全局 bottom-tab 窗口切换动画误套。

## 6. 不可牺牲的验收标准

| 测试动作 | 通过条件 |
|---|---|
| 10 次连续打开/关闭三点 Popup、一个长 Popup 滚动到最末项 | 不闪帧、不弹双窗、没有粘滞 Ripple、Anchor 与 inset 正确 |
| 普通 Dialog 点取消、Back、外部点击、点击确认，重复 10 次 | 只执行一次回调，Window 不泄漏 |
| 密码 Dialog + IME + 旋转 + 嵌套确认 | 不失焦、不改变密码/授权语义、窗口可见 |
| 普通文件夹图片进入 Viewer，滑 20 张后退出 | 顺序与网格一致、返回位置不跳；不加载旧图 |
| 收藏、回收站、照片配置 Viewer 快速进入与退出 | 未授权 0 媒体可见帧，状态/会话/恢复操作不提前 |
| 在 Viewer 缩放图片，然后左右划/系统返回/下滑退出 | 手势不互相抢占、没有未预期边界 |
| 底栏根切换，扫描尚未完成马上返回 | 不出现错误颜色的空白/旧媒体窗口；已有 first-draw 约束继续有效 |
| 动画 scale 0、不同刷新率及 Android 版本 | 所有交互可立即完成、最终状态稳定 |

历史补充：[docs/classic-dialog-review.md](../../classic-dialog-review.md)、[docs/ui/classic-dialog-repair-review.md](../classic-dialog-repair-review.md) 和 [docs/testing/classic-motion-device-checks.md](../../testing/classic-motion-device-checks.md)。这三份历史记录的“代码已修复”不能自动升级成“新动画真机已验收”。

> 最终取舍：**有必要提升弹窗与页面动画的一致性，但第一步应精修现有原生行为，不应优先增加复杂转场。** 明确不改图片预取、数据发布、鉴权和删除恢复状态机。
