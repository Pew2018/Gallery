# Phase 3 — 设置系统精修审计与实现记录

## 执行基线与范围

- 执行前重新读取 `origin/refactor/media-pipeline-stability`：`32f813369b5f9c165c474d481b9e1922b0fbb074`。工作副本初始无本地改动。
- 只在 `refactor/media-pipeline-stability` 工作；不改 main、Room、媒体扫描、Viewer、删除或备份编码逻辑。
- 工作区中未找到用户提及的录屏 `1000039818.mp4` 或独立设计指南文件。本记录以仓库中的 OxygenOS Classic 资源、已有 UI 审计和当前 Kotlin/XML 为实现证据；不把 ROM 时间、尺寸称为精确官方参数。

## 设置系统清单

| 表面/文件 | 职责与复用 | 业务状态及主要风险 |
|---|---|---|
| `activity_settings.xml` + `SettingsActivity` | 单份 ViewBinding 行列表；主页分类行由代码创建；八个分类使用同一行实例显隐 | 必须保留 View ID、回调与条件可见性；滚动位置按 `hub`/分类记忆；所有 `setupSettingItems()` 初始化保留 |
| `TiebaSettingsContainer` | 手机全宽、宽屏连续居中；现在从当前可见直接子行绘制语义 Divider | 行高不被线条改变；GONE/INVISIBLE 行跳过；Section 两侧不重复画线；RTL 使用对称内容边距 |
| `TiebaSettingsCategoryRow` | 主页八个分类的 72dp 最小行、图标、标题、副标题、整行 Ripple | 用户强调色仅用于分类图标；主题切换原位重着色 |
| `ClassicSettingsPresentation` / `ClassicSettingsSwitch` / `OxygenClassicControls` | 整行 Ripple；Switch 的拖动、无障碍 click 和父行状态所有权；复用 Checkbox、Radio、Slider 绘制 helper | Switch 父行仍是唯一保存状态的一方；不额外添加 checked listener |
| `OxygenThemeActivity` | 主题/配色主页、更多颜色、应用图标颜色；原位绑定强调色，Radio 与颜色网格保留现有设置 | 保留预设、自定义色、图标色持久化、即时刷新与 Activity recreate 语义 |
| `SimpleActivity` / `OxygenThemeEngine` / `OxygenThemeColors` / `OxygenToolbarStyle` / `OxygenSystemBars` | 主题叠层、语义颜色、Toolbar 与系统栏基线 | 本轮不改变主题解析和系统栏业务；Divider 由页面/文字中性语义色计算，不跟随 accent |

### 八分类与直接入口

| 分类 | 当前 XML 分组/功能 | 直接打开的子界面、弹窗或系统 Picker |
|---|---|---|
| 外观 | 主题与颜色、底部导航顺序、应用图标、文件/文件夹缩略图、裁剪缩略图 | `OxygenThemeActivity`、颜色选择对话框、图标选择 |
| 相册 | 横向滚动、下拉刷新（按现有规则隐藏） | 仅行内选项 |
| 照片 | 亮度、HDR、背景、系统 UI、屏幕常亮/刘海/旋转、手势与缩放 | 选择/确认 Dialog（保留原流程） |
| 控件 | 扩展详情显示/隐藏/排序、底部操作、顶栏快捷操作 | 扩展详情管理、操作顺序与快捷操作配置 Dialog |
| 视频 | 自动播放、记住位置、循环、GIF、独立播放页、视频手势 | 播放器行为选择 Dialog；原本隐藏的 tap 项仍隐藏 |
| 文件 | 包含/排除/隐藏目录、隐藏项目、搜索、空目录、时间戳、删除确认、回收站 | Included/Excluded/Hidden Folders Activity、文件夹/文件 Picker、清空回收站确认 |
| 安全 | 应用、文件删除、隐藏/排除项目、收藏夹、照片密码保护 | 密码/PIN/图案/生物识别配置及确认 Dialog |
| 高级 | 语言、日期格式、加载优先级、诊断/日志、缓存、收藏与偏好、照片配置及整库备份恢复 | `DiagnosticsActivity`、SAF 导入导出 Picker、目录授权、备份预览/恢复确认 |

所有设置回调集中在 `SettingsActivity.setupSettingItems()` 及其 `setup*` 方法；`onActivityResult()`、密码保护、权限判断和备份恢复处理均保留原方法。具体 Picker/Dialog 几何由各自现有 Activity、Commons Dialog 与布局负责，本轮不更改这些业务流程。

## 根因、处理与设计值

| 问题 | 根因 | 当前处理 |
|---|---|---|
| 子页 Divider 缺失 | XML 普通行之间没有统一装饰；主页只有分类间七条 View | `TiebaSettingsContainer.dispatchDraw()` 在可见相邻普通行之间轻量绘制；线条约 0.5dp（至少 1 个物理像素）、内容左右各 16dp；Section 前后、页尾不画；色值取 `blend(pageBackground, primaryText, 0.10)` |
| 页面运动不对称 | 页面状态先替换，然后仅同一 NestedScrollView 入场 | 新增 `ClassicSettingsPageTransition`：先绘制旧滚动视口快照，再准备目标状态；旧快照与目标内容同步向相反方向移动。方向由正向/返回和 RTL 合成 |
| 主题页面重建后才入场 | `navigateColors()` 先 `removeAllViews()`/重建，再开始入场动画 | 主题页共用同一快照转场工具；滚动位置在目标准备时恢复，Toolbar 固定 |
| 参数分散 | 普通与主题页面独立持有运动逻辑 | 共享候选值：180ms、8dp、alpha 0.92 到 1、PathInterpolator `(0.4,0,0.2,1)`；动画比例为 0 时同步提交终态 |
| 过期动画/生命周期 | 旧 Animator 或延后任务可能覆盖新状态 | 共享控制器取消旧动画、清理 overlay Bitmap；SettingsActivity 保留 epoch 与 onResume/onDestroy 归一处理 |

上述值是本项目候选值，不是 OxygenOS ROM 实测值。快照用于保留旧画面，不复制设置 View、不重跑绑定；这也使旧页退出和新页进入能够同帧运动。真机 GPU 帧连续性仍须用录屏验证。

## 交互所有权与回归保护

- Switch：保留 `ClassicSettingsSwitch`，行点击回调仍负责权限/确认后保存；Switch 自身触摸与无障碍点击路由到父行一次；本轮没有改动该类。
- Checkbox/Radio：继续使用现有经典 helper、主题色和行 Ripple；删除确认 Checkbox 的历史首次点击与 Ripple 修复未改动。
- Divider：Canvas 绘制，不创建 View，不可聚焦、不响应点击、不影响测量高度；每次绘制只遍历直接子项，不分配临时行集合。
- 状态：八分类、Section ID、条件行判断、持久化字段、Activity Result 请求码、密码/备份入口均保留。宽屏公式未改：手机全宽，600dp 内容约 522dp、840dp 内容约 630dp。

## 变更文件

- `app/src/main/kotlin/org/fossify/gallery/views/TiebaSettingsContainer.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicSettingsPageTransition.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicMotion.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt`
- `app/src/main/kotlin/org/fossify/gallery/activities/OxygenThemeActivity.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicSettingsPageTransitionTest.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/OxygenClassicMotionTest.kt`

## 验收状态和限制

- 本轮新增确定性测试：可见相邻行 Divider 与 Section 边界；转场初始方向/透明度/完成归位；共享运动常量一致性。
- 本地 Gradle 测试尚未运行：Gradle 9.5.1 Wrapper 发行包下载被当前网络连接拒绝（`services.gradle.org` Connection refused），环境中无已安装 Gradle 或 Kotlin 编译器。
- 尚未验证屏幕矩阵、TalkBack、Switch 真机拖动与滚动竞争、GPU 逐帧、Android 16 预测性返回、主题 Activity 窗口级动画以及各 Picker/密码/备份弹窗实机尺寸。
- GitHub Actions 的 ARM64 FOSS Release 构建仍需在最终 commit 推送后检查其测试、签名、ABI 与 APK artifact 结果；在 CI 结束前不声明构建通过。
