# Edge Glow 修复审计与验收（2026-11-07）

永久规则：[EDGE_GLOW_PERMANENT_CONTRACT.md](EDGE_GLOW_PERMANENT_CONTRACT.md)。历史“弹窗低强度 Glow”全部作废。

## 基线与范围

开始远程 HEAD：`fc452093a8b5c0f1fccd86dd0039c47cf3cc1dfd`，实际 fetch 后确认，非假定历史基线。使用干净的 `refactor/media-pipeline-stability` 工作区；另一个旧工作区的底栏测试/文档未提交修改保留，没有重置或新建分支。最终 SHA 和 Actions 状态在交付回复列出。

修改范围为越界反馈、共享弹窗/菜单初始化、相关 XML 主题、构建时呈现边界、测试和文档。未修改媒体扫描、Room、排序、搜索、删除、Viewer、刷新和媒体加载业务。

## 全仓库入口审计

已检索 OxygenClassicEdgeGlow、ClassicGlowPainter/Pulse/ScrollView/NestedScrollView、ClassicDialogScrollView、EdgeEffect/Factory、onOverScrolled、onPullDistance、onAbsorb、overScrollMode、OVER_SCROLL_*、DIALOG_INTENSITY_SCALE，以及 Dialog/Fragment/Popup 初始化、attach、post 和 XML 容器。

| 文件与方法（路径相对 app/src/main） | 容器 | 基线反馈 | 目标 | 修改 | 验证 |
|---|---|---|---|---|---|
| helpers/ClassicDialogScrollView.kt：ClassicDialogScrollView | 长文本、长选项、存储权限说明弹窗 | 自绘 Pulse | None | 删除全部 Glow 状态/回调/绘制；保留 onMeasure/IME | DialogOverscrollPolicyTest.galleryLongCheckboxDialogRemainsForbiddenAcrossReopenAndThemeChange |
| helpers/OxygenClassicDialog.kt：apply | Gallery、Commons、排序/筛选/主题/配置/安全/底部操作弹窗 | 纵向 Recycler 低强度 Glow | None | 装饰/内容永久策略，删除安装和安全类型跳过分支 | DialogOverscrollPolicyTest；既有 ClassicDialogTest |
| helpers/ClassicCommonsHooks.kt：beforeDialogShow | Commons 公共 setupDialogStuff/嵌套弹窗 | 后续安装 Glow | None | show 前 protect decor/content | commonsHookProtectsLateListsAndPreservesScrollbars |
| helpers/ClassicDialogScrollView.kt：ClassicDialogPickerLayout.init/onAttachedToWindow/post | Commons FilePicker、Gallery 目录 Picker | 延迟 Factory | None | 初始化/附着均禁用，保留快滚触摸安装 | actualPickerLayoutsAndSecurityContainersDefaultToNone |
| dialogs/PickDirectoryDialog.kt、PickMediumDialog.kt；layout/dialog_{directory_picker,medium_picker,filepicker}.xml | 目录/媒体/文件 Picker | 经共享弹窗路径 Glow | None | 共享策略覆盖；无需修改选择/访问业务 | 实际 XML 膨胀测试；快滚与 SAF 真机待验收 |
| views/ClassicSecurityScrollView.kt、ClassicSecurityViewPager.kt | PIN、图案、生物识别与密码页 | 安全滚动容器原先被跳过，可能系统效果 | None | 初始化立即 protect | actualPickerLayoutsAndSecurityContainersDefaultToNone |
| helpers/OxygenFastScroller.kt：apply 等 | 页面及 Picker | 无 Glow 安装副作用 | 页面 Glow / 弹窗 None | 无需修改 | 源码检索；保留业务监听 |
| helpers/OxygenClassicMenus.kt、OxygenToolbarStyle.kt；values/oxygenos_classic.xml | Toolbar overflow、PopupMenu/子菜单 | 系统列表越界可用 | None | 弹出主题 never；构建边界禁用列表 | 源码/构建变换；实际系统菜单须真机 |
| fragments/PlaybackSpeedFragment.kt：onCreateDialog/onStart | DialogFragment | 已走 Commons hooks | None | 共享 hooks 覆盖，不改播放逻辑 | dialogFragmentBoundaryProtectsTheWholeWindowBeforeFirstDraw |
| buildSrc/.../ClassicCommonsVisitorFactory.java | 普通 Dialog、DialogFragment、ListPopupWindow | 未集中禁止所有边界 | None | show/onStart/buildDropDown 注入公共策略 | Actions 编译/变换；实际窗口真机待验收 |
| helpers/OxygenClassicEdgeGlow.kt：install/remove/ClassicFactory | 全部 RecyclerView | 视口 24% 矩形渐变；可重新安装弹窗 | 页面 Glow / 弹窗 None | 永久策略守卫、彻底卸载、布局/attach/绘制守卫 | OxygenClassicEdgeGlowTest |
| helpers/OxygenClassicEdgeGlow.kt：ClassicGlowPainter | 全部普通页面 | 圆形渐变半径与矩形裁切不协调 | 有限椭圆 Glow | 48dp 硬上限、32 Alpha、渐变透明终点、shader 清理 | 原生 Canvas 多宽度/密度像素测试 |
| helpers/OxygenClassicEdgeGlow.kt：ClassicGlowPulse/ClassicEdgeEffect | 普通页面 | 连续创建 Animator、fling 可能残留 | 短暂 Glow | 动画复用、auto-release、生命周期、距离契约 | 状态/动画释放/空列表测试 |
| views/ClassicGlowScrollViews.kt；layout/activity_{settings,diagnostics,photo_profile_edit}.xml；activities/OxygenThemeActivity.kt | 设置/高级/查看设置分类、诊断、配置编辑、主题 | 大面积 Glow | 有限 Glow | 同一 Painter/tint/Pulse；生命周期保护 | 共享绘制测试；每页边缘真机待验收 |
| activities/MainActivity.kt、MediaActivity.kt、SearchActivity.kt、PhotoProfileManageActivity.kt、IncludedFoldersActivity.kt、HiddenFoldersActivity.kt、ExcludedFoldersActivity.kt：已有 install | 相册/照片/收藏/文件夹/配置/搜索/目录列表 | 同一旧 Factory | 有限 Glow | 无需修改入口，更新共享 Factory | refreshListsSuppressOnlyTheirTopVerticalEdge；真机页面待验收 |
| layout/bottom_editor_actions_filter.xml、pager_photo_item.xml | 原本 never 的滚动内容 | None | None | 保留 | 源码确认 |

## 验证记录及边界

- 源码：`git diff --check`、XML 解析、关键词/安装入口复查执行；不得以此代替运行测试。
- 本地自动化测试启动受阻：`./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.OxygenClassicEdgeGlowTest --stacktrace` 在下载 Gradle 9.5.1 时报 `java.net.SocketException: Network is unreachable`，尚未进入编译。当前运行环境只有 Java 17、没有 Gradle 缓存/Android SDK。未把此情况计为测试通过。
- Actions：使用现有 `arm64-foss-release.yml` 执行完整 FOSS Release 单元测试、ARM64 Release 装配、包名/签名/ABI 校验和产物上传。最终结果由提交绑定的 Run 单独报告。
- 静态检查：本地完整 lint/detekt 同受 Gradle 获取阻塞；已有 Actions 不包含独立 lint/detekt，不能声称全部静态检查通过。
- 设备：本环境未连接 Pixel 8 Pro 或其他 Android 真机。以下全部为待验收，测试位图也不等于真机视觉通过。

## Pixel 8 Pro / Android 16 验收步骤

| 界面 | 操作 | 预期 | 状态 |
|---|---|---|---|
| 相册主页、照片主页、收藏主页 | 长列表到顶/底继续拖动、fling、反向、取消 | 顶部只有刷新；底部短弧 Glow；无 Stretch、内容位移、染色 | 待真机 |
| 普通文件夹、自定义照片配置、搜索结果 | 各测顶/底、快滚拖动、加载中滚动、空/短列表 | 顶部刷新独占；普通边缘短弧自动消散；短/空列表无 Glow；加载顺序不变 | 待真机 |
| 设置、高级设置、全屏查看设置、主题设置 | 各测顶/底、fling 后松手、快速反向 | ≤48dp 柔和弧光，无矩形截边、正文大面积染色；停止后消散 | 待真机 |
| 自定义底部操作按钮、长 Checkbox、排序、筛选、主题弹窗 | 正常/快速滚动，顶/底继续拖，反向；关开两次 | 永远无 Glow/Stretch，正常滚动，Ripple 和进出场动画保留 | 待真机 |
| 文件/目录/媒体 Picker | 同上，加快滚、FAB、键盘、目录/文件选择、SAF | 无 Glow，快滚/输入法/FAB/权限路径正常 | 待真机 |
| 密码、PIN、图案、生物识别、密码方式 | 同上，大字体/键盘压缩、安全页切换 | 无越界效果，输入/验证/页面高度正常 | 待真机 |
| 三点溢出/滚动子菜单 | 长菜单顶/底继续拖，反向 | 无系统 Glow/Stretch；定位/Ripple/进退场正常 | 待真机 |
| 以上所有类别 | 切换浅/深主题/强调色；动画比例 0/0.5/1/2；退后台/前台 | 页面使用当前色、无旧色/残留；弹窗永久禁用 | 待真机 |

未来新增弹窗必须通过永久禁用回归测试。任何结构升级不可引用历史“低强度”说明绕开永久契约。

## 首轮 Actions 结果与补修原因

首轮提交 `5fefddd8e2e65cfc421a2d96f102d912bdb83af9` 的 [Run 42368505072](https://github.com/Pew2018/Gallery/actions/runs/42368505072) 完成 Kotlin 编译，执行 1010 tests，1 failed。失败为 `pulseUsesCurrentAccentAndDisabledAnimationsReleaseImmediately` 自动消散用例：测试 View 未附着，View.postDelayed 仍在待附着 RunQueue，主 Looper idle 无法执行它。补修将测试 View 放入真实 Robolectric Activity 窗口，保留原生位图透明断言；同时增加附着后的 fling 自动释放检查，没有恢复旧 Glow 契约或放宽像素断言。

同步复查补强：所有 RecyclerView Factory（含默认系统/外部 Factory）在禁用边界均替换成同一个 NoGlowFactory，清除 RecyclerView 缓存的旧系统效果；增加幂等/外部 Factory 回归。Dialog 子类型的 show 调用由 AGP ClassData 父类信息识别，不依赖具体类名白名单。嵌套禁用根避免重复布局扫描，处理浮动 ViewTreeObserver 在 attach 时合并造成的重复注册，重新独立附着恢复监听；增加重附着动态内容及长弹窗仍可滚动测试。普通页面反向离开边缘时开始释放。以上为首轮后独立补修提交的原因，非改动其他 UI 功能。
