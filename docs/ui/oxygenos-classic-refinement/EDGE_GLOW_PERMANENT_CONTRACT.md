# Edge Glow 永久契约

2026-11-07。此契约覆盖历史“统一全应用 Glow”说明；普通页面和弹窗使用不同策略。

## 不可覆盖的规则

`DIALOG_OVERSCROLL_EFFECT = NONE`

Fossify Gallery 所有已有及未来新增的弹窗永久禁止 Edge Glow 和 Stretch。全应用视觉统一、新主题、Android 兼容、Material 升级、动画重构、EdgeEffect 统一及列表精修均不得覆盖此规则。低透明度、低强度和短持续时间都不构成例外。

允许正常滚动、滚动条、按钮/Checkbox/Switch Ripple、弹窗进出场动画及正常滚动边界限制。禁止系统或自定义 Glow、Stretch、发光背景、模拟边缘发光的动画叠层。无须且禁止反射修改平台 EdgeEffect。

覆盖 AlertDialog、DialogFragment、普通 Dialog、Gallery/Commons 弹窗、文件/目录/媒体 Picker、排序/筛选/主题配置、长 Checkbox 列表、安全/PIN/图案/生物识别内容以及应用可控制的滚动弹出菜单。系统独立进程的鉴权/SAF 窗口由系统管理，不在 Gallery View 控制范围内；不得宣称已经验证其视觉表现。

## 代码边界

- `DialogOverscrollPolicy.protect(root)`：内容尚未挂接时就声明永久禁用；窗口 decor 在 show 前也声明禁用。Tag 随 View 存续，不依赖 Activity 主题猜测弹窗身份。
- 所有后代 `overScrollMode = OVER_SCROLL_NEVER`；包含横向容器、安全容器和任何普通 View。根节点的全局布局观察器处理动态内容；不替换 OnHierarchyChangeListener、触摸、滚动或刷新监听器。
- 观察器在 detach 时移除 ViewTreeObserver 注册，在重附着时恢复；只在布局事件扫描，无逐帧扫描、全局强引用或定时轮询。重复 protect 复用 root observer。
- `OxygenClassicEdgeGlow.install()` 两个重载都检查禁用标识/父链；已有 Factory 的 attach/layout/create/draw 路径再次检查。尚未 parenting 的早期安装在弹窗初始化或 attach/layout 时被卸载，绘制前亦会拒绝。
- `remove(recycler)` 终止已创建效果/动画及吸收回调，移除本组件的 attach/layout/lifecycle 监听，清除 Factory Tag，替换成无效果 Factory，再关闭系统 overscroll。Adapter、LayoutManager 和业务监听器保持不变。
- 自绘页面容器实现 `ClassicGlowHost`，禁用策略立即结束其动画；`ClassicGlowPulse` 在输入/绘制时再次检查策略。
- `ClassicDialogScrollView` 仅负责正常滚动和 IME/窗口高度约束，完全不持有 Glow、Animator、绘制或越界触发代码。
- `ClassicDialogPickerLayout` 在初始化及附着后的原有回调声明禁用；原有快滚条触摸、FAB、Insets 不变。
- `ClassicCommonsHooks.beforeDialogShow`、`OxygenClassicDialog.apply` 覆盖 Gallery 与 Commons 7.1.3 的公开初始化边界。
- `ClassicCommonsVisitorFactory` 将普通 Dialog 的 show、AndroidX DialogFragment 的 onStart、AppCompat ListPopupWindow 的 buildDropDown 连接到禁用边界。菜单列表在返回测量高度前即禁用；使用公开 listView API，无反射。弹窗/菜单主题也默认 `android:overScrollMode=never`。
- `ClassicSecurityScrollView`、`ClassicSecurityViewPager` 自身初始化即禁用。无安全输入或验证逻辑改动。

## 普通页面规范

使用共享椭圆 radial shader：单位圆缩放，水平半径为宽度 0.62，垂直半径 `min(48dp, width*0.22, viewportHeight)`，圆心位于边缘外 0.12 倍垂直半径。渐变在绘制边界前完全透明，不再使用视口高度 24% 的矩形截取。宽度方向采用有限位移（35%–65%），顶部和底部同一算法旋转。

峰值 Alpha 上限 32/255，且与强调色自身透明度无关。`edgeGlowTint` 从当前 interactiveAccent 和当前页面背景派生，浅色表面混入 20% 背景、深色混入 12%，每次绘制获取当前主题。不是固定青绿，也不是新的主题交互色。

出现 70ms，释放 220ms。每个效果复用一个 ValueAnimator，相同目标不重启；连续拉动更新目标。ScrollView 的 fling 触边有自动释放回调；吸收动画有受清理保护的延迟 release。暂停、窗口隐藏、detach、卸载清理动画；动画关闭时即时更新/释放。系统非零时长缩放由 ValueAnimator 处理。

API 31+ onPullDistance 返回实际累计距离变化，getDistance 与释放/finish 一致；不使用系统 Stretch。短列表/空列表禁止反馈。SwipeRefreshLayout 祖先无论 enabled 与否均抑制顶部 Glow，底部保留；不消费触摸、不更改刷新阈值/时序。

## 测试与后续开发

- `OxygenClassicEdgeGlowTest`：重复安装、父链/动态子节点/延迟回调禁止重启、卸载状态、API31 距离/释放、空/短列表、刷新边缘、原生 Canvas 像素高度/透明终点/强度/对称性/密度/窄宽屏、当前主题颜色、关闭动画/自动消散。
- `DialogOverscrollPolicyTest`：普通 Dialog/AlertDialog、Gallery 长 Checkbox、Commons 公开 hooks、实际 Picker XML、安全容器、重复打开、主题重新应用、DialogFragment 边界。Robolectric 显式调用公共边界，不代替 APK 字节码变换或系统窗口的设备验收。
- 新增弹窗必须声明 content/decor 禁用，加入上述回归测试。新增自绘滚动反馈必须实现停止接口并检查禁用策略。禁止增加弹窗强度参数，也禁止用旧测试恢复低强度 Glow。
- 构建边界变更须审查 Commons/AndroidX 兼容性；依赖升级必须重新确认 DialogFragment.onStart / ListPopupWindow.buildDropDown 的边界，并执行设备验收。

设备验收见 [本轮审计与验收表](edge-glow-repair-audit-2026-11-07.md)。这些数值是应用复古视觉设计参数，非官方 Material 或 OEM 精确实测。
