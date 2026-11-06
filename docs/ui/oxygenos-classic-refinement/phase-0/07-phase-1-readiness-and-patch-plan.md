# Phase 0 · 07 — Phase 1 接续准备与最小补丁计划

> 此文只提出接续方案；本轮未修改任何应用代码。

## 优先顺序

### P1-A：照片配置编辑页 FAB 被 IME 隐藏

影响：用户编辑配置名称、键盘出现时，添加来源FAB消失，无法直接点它打开来源选择。

证据：PhotoProfileEditActivity.kt:115–122，listener在IME可见时将view置为GONE。Manifest已声明adjustResize，但edge-to-edge下实际window/root高度尚无设备数据。

最小代码集合：PhotoProfileEditActivity.kt；仅当bounds实测证明约束不足才改activity_photo_profile_edit.xml；回归测试文件。删去IME→GONE，FAB保持VISIBLE。先测adjustResize；若root已缩小，不重复加IME bottom；若没有随动，再用WindowInsetsCompat的IME/system bars geometry或WindowInsetsAnimationCompat只调整bottom position/translation。保持Picker入口、focus/IME协同、草稿、来源、名称/来源校验。

验收：键盘可见FAB仍VISIBLE且可点，距IME顶缘候选16dp；空/已有来源、错误、旋转、横屏、多窗、Gboard、font scale、两种导航与TalkBack。

### P1-B：收藏两条操作改为无Checkbox双态文字

最小文件：menu_main.xml、MainActivity.kt、TiebaMenuItemView.kt、按需strings资源和测试。保留原ID/order/show条件/点击业务回调。刷新菜单时依据当前持久化config设置title：
- favoriteShowExcluded=false：显示已排除项目；true：不显示已排除项目。
- favoriteShowHidden=false：显示隐藏项目；true：不显示隐藏项目。

MenuItem明确non-checkable；refreshMenuItems停止设置isChecked；移除TiebaMenuItemView仅对此两ID的checkbox注入/着色。四条本地化字符串；不能写死中文。保留设置值持久化、刷新和授权行为。

关键红线：MainActivity handler先保存偏好，授权取消时偏好仍可能保持开启。新文案描述“下一步点击动作”，但必须由已保存值计算；不能把认证取消时的配置值回滚或把标题按授权显示结果推测。其他菜单的checkbox逻辑保留。

验收：FF/FT/TF/TT四种状态、每条操作点击后重开菜单、认证成功/取消、主题/语言变化、RTL、长文案、窄屏、无右侧勾选控件。

### P1-C：全局Overflow位置、宽度、Ripple

上述两项稳定后再做。先逐宿主记录toolbar overflow、anchored popup、dialog内popup、ActionMode的位置/所有者。最小候选文件OxygenClassicMenus.kt、OxygenToolbarStyle.kt、TiebaMenuItemView.kt、oxygenos_classic.xml、RTL offset与既有overflow tests。以真实anchor和WindowInsets算位置；不能先验扩大280dp，也不反射PopupWindow。此项需设备测量证明差距，先不加入菜单开闭动画。

测试所有host：主页/收藏、普通/nomedia、回收站、照片配置、Viewer、Picker、管理配置、设置手柄、长菜单、多层菜单、320/360/412/600窗口、RTL、字号与键盘。

## 后续Settings/Motion工单

当前Settings同一NestedScrollView完成80ms退出+100ms进入，alpha下限0.12、8dp位移、epoch取消。由于不存在独立稳定outgoing/incoming views，暂不将SharedAxis作为简单插入。Material Motion等dependency insight与class probe成功后再选局部空态/说明Fade。Dialog/Popup Window归Window样式处理；底栏root 0ms与ViewPager自然拖动不改。

## Phase 0完成状态及遗留

| 项 | 状态 |
|---|---|
| branch HEAD、上轮差异、并发热区 | 静态完成；本地无Git工作树 |
| 12份既有UI文档与3份历史文档 | 已核对；01–11原文保留 |
| 15菜单XML、动态Popup主要入口 | 静态代码图完成；运行时矩形待测 |
| Settings八类/内页/动画容器 | 源码确认 |
| 26 Gallery Dialog类及共享Window hooks | 静态完成；Commons内部每类实现未逐个展开 |
| 底栏、搜索、IME/FAB、EdgeEffect | 静态完成主链；FAB GONE已确认；edge type逐页待设备 |
| Viewer/返回层级 | 代码地图已核；gesture competition待设备 |
| Material Motion实际resolved依赖/API classpath | 未完成：当前环境无Gradle/checkout |
| 用户强调色 | 核心theme helper静态扫描完成；全仓资源/运行主题仍待验证 |
| 自动化与真机矩阵 | 未运行；无APK/SDK/设备 |
| Phase 1修法 | 已列出最小文件和回归 |
| 应用代码、构建配置 | 本轮未改 |

交付状态：Phase 0静态审计与接续文档完成；Gradle依赖验证、自动化测试、全量运行基线与真机验收待补。不得称为UI代码已修复或Phase 0全部设备验收通过。

