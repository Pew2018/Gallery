# Phase 1 — 照片配置编辑页 FAB 与 IME

## 根因

`PhotoProfileEditActivity.onCreate()` 给 FAB 设置的 Insets listener 在 IME visible 时直接设置 `View.GONE`。这是确定的隐藏行为。Manifest 对该 Activity 设置 `adjustResize`；页面继承 Commons 7.1.3 `EdgeToEdgeActivity`，并对 ScrollView 设置了 IME+系统栏底部 padding。Commons helper 也提供 `moveBottomSystem`，用于仅把底部控件放在系统栏上方。

## 修复

- 移除按 IME 状态切换 `GONE/VISIBLE` 的逻辑，Insets 更新时强制维持 `VISIBLE`。
- 把 FAB 加入 `setupEdgeToEdge(moveBottomSystem=...)`，系统栏基线 margin 使用项目既有 edge-to-edge 实现。
- 键盘可见时，使用 `ime.bottom - systemBars.bottom` 的非负差值作为负 `translationY`；键盘关闭后归零，避免把导航栏空间重复加到 FAB 位移。
- `WindowInsetsAnimationCompat.Callback.onProgress()` 只更新 FAB translation；不在每帧改 LayoutParams，也不替换点击监听。
- 保持 FAB 点击调用 `chooseSources()`，页面颜色仍在 `applyPageColors()` 取动态主题快照。

## 自动化覆盖与限制

`PhotoProfileEditUiTest` 覆盖合成系统栏/IME Insets 下 FAB 仍可见、相对导航栏的位移值，以及 IME 隐藏时归零。现有测试继续覆盖草稿重建恢复、来源选择草稿快照、输入框和动态主题色。

这些测试不能证明真机输入法的动画时序、FAB 距键盘边缘实际为 16dp、adjustResize 与厂商导航栏的最终窗口几何，也没有覆盖旋转/横屏、多窗、字号放大、Gboard、TalkBack、三键与手势导航。需要 Pixel 8 Pro 复测：新建空配置和编辑已有来源配置；聚焦名称输入框；键盘动画期间点加号并确认选择器只打开一次；改变键盘高度/横竖屏/字体缩放；关闭键盘并核对 FAB 回到底部；退出重进确认草稿和来源保留。
