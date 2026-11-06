# Phase 2 · 03 — 页面运动与状态

## 选型与依赖证据

SettingsActivity 复用同一NestedScrollView，hub与分类是同一holder内的显隐集合，没有稳定独立的outgoing/incoming双页。因此保留ViewPropertyAnimator，不接入MaterialSharedAxis/Fade/FadeThrough，不改Activity/navigation所有权，不增加依赖。Google easing仍为PathInterpolator(0.4,0,0.2,1)。

本地dependencyInsight被Gradle分发包下载阻断。基线8620d441对应成功Actions run42183475173的实际transform日志已证实：Commons7.1.3、Material Components1.14.0、AndroidX Transition1.5.0。这是CI解析证据，不是catalog猜测，也不冒称本地运行dependency tree。最终run的日志再次复核见04；没有使用新Material API，无需改变依赖。

## 设置分类导航

- 保留exit80ms+enter100ms，8dp X；alpha最低0.12→0.72，降低近空白风险。实际闪帧数量仍需设备录屏，不宣称0帧。
- 正/反向结合layoutDirection反转；Toolbar在动画容器外保持稳定。
- 每个请求先递增epoch并cancel旧animator，归一后启动；过期exit回调不换页；enter结束归位alpha1/X0。
- 相同pending/current分类重复点击忽略；新的不同导航可以中断旧导航；Back在pending时可用。
- 系统animator scale0立即changePage和归位；onResume取消未完成导航并刷新当前实际页；onDestroy递增epoch、cancel和归位。
- 新增按hub/category的scrollY map；离页记位置，render之后doOnLayout恢复，epoch/page key过滤陈旧恢复；savedInstanceState保存当前category和map。
- setupSettingsHub首次建行、onResume保留原View/Drawable和焦点；业务setupSettingItems仍按原方式刷新保证配置即时性。

## 主题子页

原有即时buildPage后仅enter的结构保留；共享alpha0.72/8dp/enter100ms/easing，RTL反转，cancel后新导航可立即执行；0x直接终态，销毁取消归位。没有为统一exit而重构主题页面。

这些时间/alpha是Gallery工程候选，不是旧ROM实测。SettingsPageMotionTest测方向/可见alpha floor/最终态/中断/销毁/0x；SettingsPhaseTwoTest测滚动恢复、重进与Activity状态。真实帧、系统返回手势和旋转窗口几何在05中待设备。

最终Run42188141776已再次从解析transform日志确认Material1.14.0 / Transition1.5.0 / Commons7.1.3；对应977测试和Release/R8构建成功。该证据不意味着本阶段接入了Material Motion。
