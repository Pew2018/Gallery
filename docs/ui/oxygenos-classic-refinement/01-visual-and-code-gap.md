# 01 · 图片对照与当前代码差距审计

> 当前审计锚点：`b7175e8fa0411d8ff600366e316777efb28bb8f3`。只读检查，不代表已在目标真机上做运行时 View 测量。
> 证据标签：**[用户现象]**、**[源码确认]**、**[拟合建议]**、**[待验证]**。不要将假设写成根因。

## A. 截图样本角色与限制

| 样本 | 可直接观察到的现象 | 注意 |
|---|---|---|
| Gallery 收藏页菜单（用户圈出区域） | 菜单宽大、遮挡相册；旧版“始终显示已排除项目 / 始终显示隐藏项目”的 CheckBox 在截图中不可见 | **历史现状证据**，并非新设计要求；2026-11-06 用户已决定直接取消这两项的 CheckBox，改为无勾选的操作文案 |
| Gallery 相册页菜单 | 菜单相对标题栏的水平/垂直落点、文本起点与参考图不同；长菜单需避让屏幕底端 | 列表长短和菜单位置是不同问题 |
| Gallery 设置首页 | 分类行由左图标、主副标题、底部细线构成；文字留白与经典 OnePlus 仍有差距 | 带副标题的应用设置列表不应复制系统设置大标题 |
| Gallery「全屏查看」设置内页 | 分组标题独立、开关右对齐，多项间留白偏大且不统一 | UI 规格问题，不推断实际点击/存储功能已坏 |
| OnePlus 旧版设置/电话/联系人图 | 旧式细轨开关、48–72dp 列表节奏、紧凑溢出菜单、联系人下划线输入 | 系统设置、Launcher 设置、电话是**不同页面体系** |
| OnePlus 联系人下拉末端图 | 底部轻蓝色弧形 overscroll 反馈 | 静态截图不能测出动画时长、弹性、完整轨迹 |

用户另有设计指南 v1.0：29 张 OxygenOS 4.5.15/Android 7.1.1 参考图片；其 `360×640dp`、`2.4px/dp` 是拟合值，不是 ROM 原始 density 实测值。**新增截图与原 29 张不是同一个可无条件合并的统计样本**。

## B. 现有代码与风险清单

| ID | 现状/证据（在分支内） | 判断 | 后续需要实际证明的事项 |
|---|---|---|---|
| UI-01 | **当前源码（历史实现）**：`menu_main.xml` 的两项 `android:checkable="true"`，`MainActivity.refreshMenuItems()` 写 `isChecked`；`TiebaMenuItemView.initialize()` 特判两项勾选框 | **新设计决定废弃这套 Checkbox 外观与相关特判**；保留同一 MenuItem ID、配置布尔值、操作回调/鉴权/刷新 | 改成 `checkable=false` 的纯文字操作项；根据当前配置显示“显示已排除项目/不显示已排除项目”“显示隐藏项目/不显示隐藏项目”，确认双向切换、持久化和鉴权不回退 |
| UI-02 | `res/values/oxygenos_classic.xml` `oxygen_classic_overflow_horizontal_offset=-20dp`（RTL +20dp）、VerticalOffset=16dp、overlapAnchor=true；`OxygenClassicMenus.createPopup()` 用 `Gravity.END`；`TiebaMenuItemView.onMeasure()` 限制 280dp | 代码采用近似固定锚点和最大宽，不保证多设备/长标题/普通复选控件情况恰好符合截图 | 采集实际 anchor screen rect、popup screen rect、可视框、文本起点与宽度；分别测试收藏新文案、Toolbar Overflow 与独立 PopupMenu |
| UI-03 | `TiebaSettingsCategoryRow`: 最小 72dp，16dp 横内边距，左 56dp slot / 36dp icon container，主标题 16sp、次标题 **14sp**；`SettingsActivity.setupSettingsHub()` 逐行 `removeAllViews()` 重建 | 与指南拟合副标题约 12sp 存在差异；重建可影响焦点/Ripple/滚动，无实时证据说明这里造成闪烁 | 量测主副标题基线、行高与分割线；确认 `onResume` 重建的实际 UI 影响 |
| UI-04 | `activity_settings.xml` 下多组条目共用 `OxygenClassicSettingsSingleRow` 56dp / `DetailsRow` 72dp；`OxygenClassicSettingsSection` 仅 paddingTop=16dp, bottom=8dp, minHeight=0 | 分组的视觉高度不被统一约束；内容长短及系统字体缩放可能改变视觉节奏 | 对同级标题测量顶部/底部空白，检查仅剩一项时的分组显示逻辑 |
| UI-05 | `GalleryBottomBar` 使用 56dp 外栏、52dp item、24dp 图标、12sp 标签，左右外 padding 6dp、项左右 margin 2dp；`selectTab()` 直接设置 tint/typeface | 现有选择状态跳变，仍有精修空间 | 同一帧 Ripple 是否被导航中断；图标视觉居中与标签 baseline；快速点按和配置重排 |
| UI-06 | `GalleryBottomBar.open()` 用 `overridePendingTransition(0,0)`；首帧等待上限 250ms、退出兜底 530ms | **关闭 Activity 窗口动画是防闪烁约束**，不能因追求旧风格直接恢复 | 验证不透明首帧、受保护图片是否提前出现、冷启与慢加载，不得让动画依赖图片解码 |
| UI-07 | `OxygenToolbarStyle.render()` 对关闭搜索输入设置 width=1dp, alpha=0；开启则 MATCH_PARENT/alpha=1；`OxygenSearchNavigation` 控制 IME 与 focus | 有离散状态切换，未见该处统一展开动画；**不能由此断言所有输入无动画** | Search、主页、文件夹和第三方 Intent 入口分别录屏，键盘返回、清空、旋转、连续输入同步 |
| UI-08 | `OxygenClassicPageInput.style()` 配置有焦点/无焦点颜色状态列表；`OxygenClassicControls` 处理 Switch/CheckBox/SeekBar | 颜色状态已有基础，不应重新造控件；焦点动画仍需可视验证 | 检查光标/下划线变化与 IME 协调，是否存在叠加 Ripple |
| UI-09 | `SettingsActivity.animateSettingsPageChange()` 使用退出 80ms、进入 100ms、透明度最低 0.12、水平位移 8dp | 当前是强淡出+短位移；用户目标更偏平面克制 | 对反向操作/Activity 销毁、系统动画比例 0 的复位验证 |
| UI-10 | 已检视 `MainActivity`、`MediaActivity` 的列表入口，未确认存在统一的专用经典 `EdgeEffect` 模块 | **覆盖范围尚不明，不等于系统默认 overscroll 完全不存在** | 逐页面采样 RecyclerView / NestedScrollView / Picker / ViewPager 的 EdgeEffect 与刷新手势 |
| UI-11 | `SettingsActivity` 有 `OnBackPressedCallback`；`MainActivity`、`MediaActivity` 通过 `onBackPressedCompat()` 区分搜索/层级；Viewer 存在复杂滑动/鉴权 | 返回所有权分散，预测性返回不能仅添加一个动画开关 | 先追踪 `SimpleActivity`、Commons Back API、Manifest 和依赖版本；参见 04 |
| UI-12 | `OxygenClassicDialog`、`ClassicCommonsHooks`、`OxygenToolbarStyle`、`OxygenClassicControls` 等已存在 | 复用现有经典组件而非并行重写；Dialog 之前有独立尺寸修复史 | 与历史审计对照，避免回滚密码/文件选择器宽高、CheckBox Ripple 和 Quick Actions 行为 |

## C. 候选尺寸与交互规范（全部待实测）

| 组件 | 当前值（源码） | 目标候选 / 判据 | 允许例外 |
|---|---|---|---|
| Toolbar | 56dp，20sp | 保持 56dp；操作/返回图标 24dp、触摸区 48dp | 大字体需防止文字裁剪 |
| PopupMenu 文本 | 16sp | 保持 16sp regular；同级菜单左基线一致 | RTL/无障碍放大 |
| PopupMenu 普通行 | min 48dp | 通常 48dp，长文本可增高 | 多行与字体缩放 |
| PopupMenu 画布 | onMeasure max 280dp；背景 top/bottom 8dp | **内容测量 + 屏宽/insets 上限**；起点与锚点可量化 | 横屏/小窗会动态调整 |
| PopupMenu 背景 | 4dp 圆角、8dp elevation | 优先对照 2–4dp 经典轻圆角和克制阴影 | 深色主题 |
| 收藏菜单两项（目标） | 当前为 `checkable=true` + `tieba_choice_check` 特判 | **删除两项 Checkbox 图形，改为不带图标的纯文字行**；保留 48dp 候选触摸行高与 Ripple | 其他菜单 CheckBox 仍遵循各自样式，不受本决策影响 |
| 设置主行 | 56dp | 保持默认 min 56dp，允许自动长高 | 长文/字体缩放 |
| 设置双行 | 72dp | 保持默认 min 72dp；主 16sp，副 **12–13sp 候选** | 各语言自然换行 |
| 设置分组 | 当前 min 0dp + padding 16/8dp | 候选总行距 48–56dp，分组字 12sp | 第一个分组可有额外顶距 |
| 底栏 | 56dp / item 52dp / 图标 24dp / 文字 12sp | 保持基准，精修图标视觉平衡及颜色过渡约 120–160ms 候选 | 0x 动画立即到最终态 |
| Search | 关闭 1dp/alpha 0，打开全宽/alpha 1 | 选用可打断、无跳帧的 title/icon/input 过渡；**数据查询不等待动画** | 低端机、0x、IME |
| EdgeEffect | 未形成统一视觉合同 | Android 7 类似轻弧形发光，使用主题强调色、边界安全 | SwipeRefresh 先占用下拉动作 |

指南中 `#FAFAFA` 页面、`#FFFFFF` 白色弹层、`#212121` 标题栏和 `#2196F3` 蓝色代表**参考外观**，Gallery 的主题引擎 `OxygenThemeEngine` 仍要允许用户设置深浅模式、顶栏前景和强调色；不得硬编码为每屏同色。

## D. 必须区分“截图发现”与“代码猜测”

- **[历史截图确认]** 收藏页旧版两行 Checkbox 在所提供的截屏不可见。
- **[源码确认]** 审计时确实存在两个 `checkable=true` item、`isChecked` 更新及 View 定制代码；这是**待替换的旧实现**，不再需要投入工程量去诊断其勾选框是否被裁剪。
- **[用户新设计决策]** 两行改为无勾选框的纯操作菜单项，文字依当前布尔值切换：`false → 显示已排除项目 / 显示隐藏项目`，`true → 不显示已排除项目 / 不显示隐藏项目`；需验证文案与实际开关状态一致，且认证要求不改变。
- **[截图确认]** 菜单位置和宽度与参考图的视觉比例不符。
- **[源码确认]** 当前存在固定偏移与 280dp 上限。
- **[尚未确认]** 调整哪个参数能对所有 ROM/多窗口/RTL 保持一致。
- **[源码确认]** 底栏状态直接改色、窗口切换禁止动画。
- **[尚未确认]** 底栏具体哪个过渡在目标机上“显得不自然”，必须用录屏逐帧定位。
- **[参考图确认]** Android 7 联系人页面触边时出现浅蓝弧形。
- **[尚未确认]** Gallery 实际默认 overscroll 是 Stretch、Glow、禁用还是被 SwipeRefresh 消费；必须观测而非假定。

## E. 不可破坏的功能行为

- 收藏过滤允许切换与取消认证后正确回退，不泄露受保护图片；显隐开关实际含义与存储不改变。
- 搜索文本变更仍驱动媒体过滤；排序立即反馈且保持媒体顺序；不通过动画掩盖异步发布错误。
- 底栏三个根页面不显示错误帧/错误颜色，保留滚动锚点与正确的当前选中状态。
- Viewer 滑动、图片缩放、视频手势和删除后恢复路径优先级不变。
- 字体 1.5x/RTL/深色模式/无障碍 TalkBack 可用；点击区域应与视觉控件分开考虑。
