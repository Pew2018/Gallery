# 06 · Gallery 页面层级、四种来源全屏 Viewer 与返回手势地图

> **当前仅追踪现有层级，未实现预测性返回或页面动画。**
> 只读基线：`refactor/media-pipeline-stability` @ `27008fc1b5226187c3c8b99c0a12911aa5163632`；新 Agent 动手前更新路径、行号和当前代码。
> 所有文件在 `app/src/main/kotlin/org/fossify/gallery/`；外部 Commons 固定来源在 `FossifyOrg/commons` tag `7.1.3`。

## 1. 页面/容器的真实层级（不是一个 Fragment NavHost）

```text
SplashActivity
  └── MainActivity [相册网格 / 收藏文件夹根页面；可包含“直系子目录分组”屏内层级]
      ├── 选择普通文件夹 / .nomedia 文件夹 → MediaActivity [mPath=/physical/folder]
      ├── 选择主页面固定“收藏”虚拟相册 → MediaActivity [mPath=FAVORITES]
      ├── 选择主页面固定“回收站”虚拟相册 → MediaActivity [mPath=RECYCLE_BIN]
      ├── 切换“相册→全部文件” → MediaActivity [mPath=""; mShowAll]
      ├── 底栏“照片” → MediaActivity [mPath=PhotoProfileMediaLoader.PATH]
      │   ├── 无自定义配置 → 媒体网格空态 + 工具栏“管理配置”
      │   ├── 管理配置 → PhotoProfileManageActivity
      │   │   ├── 新建 → PhotoProfileEditActivity [含名称输入+添加来源FAB]
      │   │   ├── 编辑现有配置 → PhotoProfileEditActivity
      │   │   └── 更多三点 → 设为当前/编辑/重命名/删除
      │   └── 浏览配置媒体 → 与普通照片共用 ViewPagerActivity，但来源/会话不同
      ├── 底栏“收藏” → MainActivity [EXTRA_FOLDER_FAVORITES=true，收藏文件夹集合]
      └── 设置 → SettingsActivity [首页分类/子分类是同一个 Activity 内切换]
          ├── 主题 → OxygenThemeActivity [自有内部颜色页]
          ├── 工具栏操作 → ToolbarQuickActionsDialog [对话框内 Popup]
          ├── 底栏排序 → BottomNavigationOrderDialog [对话框内 Popup]
          └── 各类原生/Gallery/Commons Dialog 与独立管理 Activity

MediaActivity [普通文件夹、虚拟收藏、回收站、照片配置、全部文件]
  ├── 选择图片 → ViewPagerActivity [共用连续 Viewer]
  │   ├── MyPagerAdapter
  │   │   ├── PhotoFragment [缩放、点击切 chrome、下滑退出]
  │   │   └── VideoFragment [视频显示、拖动等]
  │   └── ProgressivePhotoViewPager [边界未知时请求继续加载]
  ├── 选择视频且手势播放器启用/非照片配置 → VideoPlayerActivity
  └── 外部单媒体 Intent 可能先到 PhotoVideoActivity → ViewPagerActivity
```

**重要**：底栏三个根页面不是共用一个 RecyclerView/单 Activity 的 TabLayout，而是使用 `GalleryBottomBar.open()` 发起**跨 Activity 的新根页面**；这会决定返回动画的所有权和源/目标 Window 交接策略。

## 2. 具体入口与关键 Intent（逐类来源）

| 来源 | 网格/宿主 | 进 Viewer 的入口 | 会影响权限/顺序的参数与存储 | 返回路径 |
|---|---|---|---|---|
| 普通物理文件夹、含 .nomedia 文件夹 | `MainActivity.itemClicked(path)`（约 2072）→ `MediaActivity`，`DIRECTORY=path` | `MediaActivity.setupAdapter()` itemClick（约 1640）→ `itemClicked()`（约 2909）→ `openOrdinaryViewer()`（约 2950–3050）→ `ViewPagerActivity` | `FOLDER_UNLOCK_SESSION` 如需保护、`PATH`、`IS_FROM_GALLERY`、`ViewerGridOrderStore`、`ViewerNeighborHandoffStore`、`FolderViewerSnapshotStore` | Viewer 完成退出回 MediaActivity；保留网格锚点和目录状态；受保护 session 不得提前释放 |
| 主页面固定虚拟“收藏”媒体相册 | `MainActivity.itemClicked(FAVORITES)` → `MediaActivity[mPath=FAVORITES]` | 同上，但 `openOrdinaryViewer()` 会捕捉 `FavoriteViewerCatalogStore` | `FAVORITE_ACCESS_SESSION`、`SHOW_FAVORITES`、收藏捕获路径、`FavoritesScrollRestorer`、`FavoritesReturnCover` | Viewer→虚拟收藏列表时需可见区域稳定，退出/撤权不能泄露收藏 |
| 回收站虚拟文件夹 | `MainActivity.itemClicked(RECYCLE_BIN)` → `MediaActivity[mPath=RECYCLE_BIN]` | `itemClicked()` → `openOrdinaryViewer()`；完整物理目录快照分支**明确排除 RECYCLE_BIN** | `SHOW_RECYCLE_BIN`、`PATH`、回收站可见菜单 `empty_recycle_bin`、`restore_all_files`、永久删除/恢复 | Viewer→回收站列表；返回中不能触发恢复/清空或丢失原目录引用 |
| 底栏「照片」自定义配置 | `MainActivity.openGalleryRoot()`（约 1639）或 `MediaActivity.openGalleryRoot()` → `MediaActivity[mPath=PhotoProfileMediaLoader.PATH]` | `MediaActivity.openInViewPager()`（约 3050–3130）→ `ViewPagerActivity` | `PhotoProfileMediaLoader.EXTRA_ID`、`PhotoProfileAccessSessionManager.EXTRA_SESSION`、`PhotoSessionViewerCatalogStore`、`ViewerGridOrderStore`、`ViewerNeighborHandoffStore` | Viewer→当前配置网格；必须校验会话和配置未改变；配置切换后禁止旧图重显 |
| “全部文件”视图 | `MainActivity.openGalleryRoot(GalleryContentView.FILES)` → `MediaActivity[mPath=""]` | `MediaActivity` 共用点击和 Viewer | `SHOW_ALL`、动态筛选、目录排序上下文 | Viewer→全部文件，保持搜索/网格含义 |
| 外部查看/第三方 Picker/壁纸 | `MainActivity` 外部 Intent 或 `PhotoVideoActivity` | `PhotoVideoActivity.sendViewPagerIntent()/openViewPager()`（约 306–335），或 picker 返回 Intent | `IS_VIEW_INTENT`、`IS_FROM_GALLERY`、`PATH` 等 | 返回外部调用方；**不能强行跳 Gallery 主页** |

### 入口共同部分与差异

- `MainActivity.setupAdapter()`（约 2734）通过 `DirectoryAdapter` 的点击回调：若启用“直系子目录分组”，可能先更新 `mCurrentPathPrefix`、`mOpenedSubfolders`、重建网格；否则进入 `itemClicked(path)`。
- `MainActivity.itemClicked()` 会异步查找保护祖先、处理排除文件夹的密码，再 `startActivityForResult(MediaActivity,...)`。预测性返回手势**不能在进度时触发这些鉴权副作用**。
- `MediaActivity.itemClicked(path)` 先分支区分壁纸/第三方选择器/内部照片或视频；不能把这些路径都当作“打开 ViewPager”。
- `MediaActivity.openOrdinaryViewer()` 在异步线程准备邻近窗口、当前网格顺序与完成快照，UI 线程确认请求仍有效后才 `startActivity(ViewPagerActivity)`；**不要为了转场动画将整个目录拷贝到主线程**。
- `MediaActivity.openInViewPager()` 当 `isPhotoProfiles` 采用配置自己的 revision/token/授权会话；到 `startActivity` 前再验请求 source/selected path，避免配置切换后错误导航。
- `VideoPlayerActivity` 使用 `BaseViewerActivity`；其菜单/方向/进度条与 `ViewPagerActivity` 不是一条完全相同的内部渲染链。

## 3. 根页面和设置子页面返回所有权

**最上层通用回调不是 Gallery 自己：**

- `FossifyOrg/commons` tag `7.1.3`：`commons/src/main/kotlin/org/fossify/commons/activities/BaseSimpleActivity.kt` 的 `registerBackPressedCallback()`（约 270）注册一个 `OnBackPressedCallback`：先调用 Gallery 子类可重写的 `onBackPressedCompat()`；如果返回 false，临时禁用自己，再交给系统 `onBackPressedDispatcher`。
- 同文件的 `performDefaultBack()`（约 180）可强制把返回传给下层 dispatcher；未来如更改回调必须防止重复调用/死循环。
- `Gallery/activities/SimpleActivity.kt` 继承 Commons 基类，分发各种 Gallery 通用样式/insets，**并不拥有独立全站预测性返回栈**。
- `MainActivity.onBackPressedCompat()`（约 762）：关闭搜索优先；开启直系分组并非根层时先回上一级；否则锁定 App 后交给默认返回。
- `MediaActivity.onBackPressedCompat()`（约 1173）：关闭搜索优先；对于特定根/收藏会锁定或撤销收藏 session，然后返回 false 交给默认 Activity 退出。**当前副作用发生在按下完成时**，预测性手势进度不得提前执行。
- `SettingsActivity`（约 138–143）额外注册仅在子分类启用的 `settingsBackCallback`，处理 `navigateSettingsBack()`；`openSettingsCategory()` 与 `animateSettingsPageChange()` 会延后替换页内容。
- `OxygenThemeActivity`（约 111–146）自有启用状态的 `OnBackPressedCallback`，处理多级颜色页面和当前 150ms 动画。
- `dialogs/PickDirectoryDialog.kt`（约 183）以及 **Commons `FilePickerDialog`**（约 155）会在 Dialog 自己的 `onBackPressedDispatcher` 中处理文件夹 Breadcrumb 层级，不能当作单一 `dismiss()`。
- `ViewPagerFragment.handleEvent()`（约 173–190）：允许“向下滑退出”（`activity.finish()` 并指定 Commons 的 `R.anim.slide_down`）；与系统**边缘横向预测性返回**是两种手势，必须分清。

### 建议的分层逻辑（后续编码时先做确认）

```text
系统/输入法持有的 Back
  → IME 可见时先由系统/输入法按其规则消耗
  → 当前 Dialog/Picker 的内部分层
  → 内联搜索页面关闭
  → MainActivity 目录分组 / SettingsActivity 分类 / OxygenThemeActivity 颜色层
  → ViewPagerActivity、VideoPlayerActivity 的退出（考虑手势冲突与鉴权）
  → 当前 Activity 栈回退 / 返回外部调用方 / 返回桌面
```

这是**返回意图优先级候选**，不是对 AndroidX 真实分发时序的假设。必须以实际 API 事件和设备日志核验，且符合 Compose/系统对话框等各自 BackDispatcher 的所有权。

## 4. 全屏左右滑动、边界与退出动画的分离

| 层 | 代码位置 | 职责 | 动画改动约束 |
|---|---|---|---|
| 手指翻页手势 | `views/ProgressivePhotoViewPager.kt:dispatchTouchEvent()` | 识别暂时未知的目录边界，需区分 RTL、单指、缩放子控件可横向滚动 | 禁止为美化翻页而改变“未知边界不是 EOF”的语义 |
| ViewPager 状态 | `ViewPagerActivity.onPageScrolled/onPageSelected/onPageScrollStateChanged()`（约 4305–4410） | 当前图片、左右预取、最后浏览路径、滑动期间延迟目录结果发布 | **禁止**在 SCROLL_STATE_DRAGGING/SETTLING 中插入整目录适配器刷新 |
| 网格→Viewer 交接 | `ViewerNeighborWindow`、`ViewerNeighborHandoffStore`、`ViewerGridOrderStore`，照片配置另有 `PhotoSessionViewerCatalogStore` | 预读邻近数据并保持原网格序、受保护来源已授权 | 不能用共享元素动画提前替换授权验证结果 |
| 页面 Adapter | `adapters/MyPagerAdapter.kt` + `PhotoFragment`/`VideoFragment` | Fragment 生成、复用、只在稳定点发布 | 不为了动画将 fragment 全量拆卸重建 |
| Chrome 动画 | `helpers/ViewerMotion.kt:ViewerChromeMotion` | 工具栏、底部操作的 alpha；可被新动作抢占 | 可轻度精修，必须保证 0x 和中断后最终态 |
| 加载反馈 | `ViewerLoadingMotion` + `OxygenClassicMotion` | 有延时、最短可见期与隐藏动画 | 不把图片加载挂到动画时长上 |
| 幻灯片 | `ViewPagerActivity.animatePagerTransition()`（约 2335） | 定时幻灯片的淡入/滑动 | 不是普通手指左右滑动，需单独测试 |
| 全屏下滑退出 | `fragments/ViewPagerFragment.kt:handleEvent()` | finish + `R.anim.slide_down` | 不和系统横向 Back 动画重复 |
| 单文件界面 chrome | `PhotoVideoActivity.fragmentClicked()`（约 420） | Toolbar、阴影、底栏 `ViewPropertyAnimator.alpha` | 目前与 `ViewerChromeMotion` 不完全统一，需后续收敛 |
| Viewer→网格恢复 | `MediaActivity` 的 `FavoritesReturnCover`、`FavoritesScrollRestorer`、`photoScroll`/snapshot | 保护返回过程中不闪烁、恢复滚动锚点 | **动画必须覆盖同一授权范围内稳定画面，不能显示旧网格** |

**顺序要求**：先保持静态/无动画的网格→Viewer→网格 100% 正确，再试“短淡入/轻位移/共享元素”的可取消方案；否则难以区分数据 publication 问题和动画问题。

## 5. 跨页面返回动画应分两类设计

**新增动画策略**：[11-老 Material Motion 选型](11-legacy-material-motion-selection.md) 规定，Viewer 图片主体保留 ViewPager 原生跟手；`MaterialContainerTransform` 只在非保护、单图锚点已稳定且无旧帧泄露的场景作为可选实验。GalleryBottomBar 根页跨 Activity 时仍使用 0ms 抗闪帧，不套 MDC 底栏示例；MaterialSharedAxis 主要考虑设置/屏内导航，预测性返回由 AndroidX Back API 控制。任何可能带颜色的 transition container/scrim 取实际当前页面/弹窗主题 Token，而不是固定强调蓝，见 [10](10-accent-semantic-and-runtime-invariants.md)。

1. **系统 Activity Window 返回**：普通文件夹→主页、Viewer→文件夹、设置页→上级 Activity，优先让系统处理，核查是否已具备预测性返回；不要滥用 `overridePendingTransition`。
2. **同 Activity 层级返回**：Main 的分组目录、Settings 的内页、OxygenTheme 的颜色子页；才考虑绑定 `BackEventCompat.progress` 的应用内预览与取消恢复。完成手势时只执行一次状态提交。
3. **GalleryBottomBar 根页面切换例外**：`GalleryBottomBar.open()` 用 `startActivity` + `overridePendingTransition(0,0)` 和 first-draw gate；这是曾经为闪帧所做的保护，**不把它当作普通 Back 过渡**。
4. **安全与生命周期**：鉴权/删除/进出 Picker 事务在 commit 发生，cancel 仅恢复视觉状态。退出受到保护的 Viewer 之前必须先避免受保护媒体下一帧泄露。

## 6. 必补的导航验证矩阵

| 从哪里 | 到哪里 | 交互 | 对应验证 |
|---|---|---|---|
| 普通文件夹 | 主页相册列表 | 点击系统 Back/工具栏 Back/预测性手势 | 无错误相册、无位置跳动、根层正确 |
| .nomedia 文件夹 | 主页 | 同上，扫描中立刻退 | 不遗留后台扫描回写或隐私状态 |
| 虚拟收藏媒体 | 主页收藏入口 | 同上，并在 Viewer 返回 | 收藏权限、封面/计数/滚动锚点不乱 |
| 回收站 | 主页 | 同上，含恢复/删除完成分支 | 未误恢复、已删除项不闪现 |
| 照片配置网格 | 底栏其他页、管理页、全屏 Viewer | Back 取消/完成/切换配置 | 只显示当前配置范围 |
| 任何 Viewer | 原网格 | 右/左系统边缘 Back、下滑退出、工具栏 Back | 系统 Back 不劫持左右翻页或图片缩放 |
| 设置分类 | 设置主页 | 半途取消/完成 | 字段不被提前更改，alpha/translation 最终 1/0 |
| 外部照片查看或 Picker | 外部调用者 | 取消/确认 | 正确 Result/Intent，不错误回 Gallery 首页 |

参考：[04-predictive-back-feasibility.md](04-predictive-back-feasibility.md) 是 API 和开关评估；本页补齐具体的类、Activity/Fragment 归属与调用次序。
