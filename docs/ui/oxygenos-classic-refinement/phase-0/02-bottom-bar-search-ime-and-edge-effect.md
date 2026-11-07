# Phase 0 · 02 — 底栏、搜索、IME/FAB 与列表边缘反馈

> 源码锚点 024b4731ac6508cab45f7d81eea73294a78d0d04。无设备/模拟器，未获得运行时 bounds、GPU 帧率或真实动画持续时间。

## 底栏

GalleryBottomBar 当前共享布局值为 56dp 外栏、52dp item、24dp icon、12sp label，水平 padding/margin由 helper 设置；源码值不等于设备像素测量。selectTab（约 119）基于 OxygenThemeEngine.snapshot 设置选中/未选颜色；updateRipple（96–116）给原 RippleDrawable.setColor，recolor（152起）不重建 views。

根切换约束仍成立：ROOT_ENTER_DURATION_MS=0、FIRST_DRAW_LIMIT_MS=250、ROOT_FINISH_FALLBACK_MS=530（GalleryBottomBar.kt:28–31）；open（172–195）执行 overridePendingTransition(0,0)，并由 opaque first-draw gate 等待内容或 themed loading fallback。不可为套 FadeThrough 恢复整窗淡入淡出。实际 Ripple 是否完整、快速切换帧、主题变更后旧颜色是否残留，需 Robolectric 和设备录屏。

## 搜索

OxygenToolbarStyle.preparePopup/applyToolbar/render 设置 popup、toolbar 和搜索输入 View 的视觉状态；关闭输入框以折叠宽度/alpha表达，打开恢复宽度。OxygenSearchNavigation、Commons MySearchMenu、MainActivity/MediaActivity/SearchActivity/Picker 协同焦点、输入法和返回。展开、关闭、焦点、光标、IME 与 query listener 的完整时序需逐页面测量，不能把一个 listener 当作所有搜索入口。

搜索输入变化仍应即时驱动既有 listener/过滤。不可用动画延迟 query 或排序；不可替换 EditText 导致焦点、光标、文本 selection 丢失。需设备录制输入、清空、IME第一次返回、连续快速输入、旋转恢复。

## FAB + IME（源码确认）

PhotoProfileEditActivity.kt:115–123 的 ViewCompat insets listener 读取 Type.systemBars() 并设置 bottomMargin=16dp + bars.bottom；随后根据 Type.ime() 可见性将 FAB 设为 GONE/VISIBLE。因而键盘显示时按钮确实被代码隐藏，不是尚待确认的推测。AndroidManifest.xml:96–101 同时声明 windowSoftInputMode=adjustResize；由于页面 edge-to-edge/insets 仍需设备确认它是否已将 root 缩短到 IME 顶部。

Phase 1 建议：去除 IME→GONE；FAB 始终可见。先测 adjustResize 对 root 与 FAB 的实际影响，避免再减一次 IME inset；若布局不随动，用 WindowInsetsCompat systemBars/ime 几何在键盘上方留候选 16dp，必要时 WindowInsetsAnimationCompat 只更改 position/translation，不改 visibility 或点击语义。Picker打开时保留既有收键盘/清焦点协同；不清空名字、sources、不绕过保存校验。测空/已有来源、错误提示、横竖屏、多窗、Gboard、字号、手势/三键、TalkBack。

## EdgeEffect / overscroll

目标源码未发现统一的 Gallery 主题化 EdgeEffect 工厂；这不能证明所有列表都是 Glow、Stretch 或关闭。RecyclerView、NestedScrollView、SwipeRefreshLayout、Picker 与 ViewPager 受 Android API、AndroidX widget、style 与 SwipeRefresh touch consumption 共同影响。本轮未逐一安装、滚动或录屏，因此不能把某一种结果写成当前体验。

后续逐页面清点 overScrollMode、自定义 EdgeEffectFactory、SwipeRefresh 下拉消费和 API 31+ Stretch。若做经典弧形 Glow，可对适用 RecyclerView 使用 edge effect 专用工厂/自绘反馈，强调色按当前页面 surface + 用户 token 计算，主题变化更新；保持 Glow 形态与动态颜色分离，禁止固定蓝。先证明不会抢 SwipeRefresh 手势，并以 Android 26–36 实机看弧形、颜色、cancel与low/high contrast。

### 2026-11-07 弹窗 Glow 审计与移除

复核远端最新代码后，确认**曾经给弹窗接入上下 Glow**，来源是 2026-11-07 合入的共享经典 EdgeGlow，而非 `OxygenClassicDialog` 自己绘制。弹窗调用链上的位置如下：

| 位置 | 原行为 | 处理 |
|---|---|---|
| `helpers/OxygenClassicDialog.kt`：`apply()` | 对整个弹窗 View 树调用 `OxygenClassicEdgeGlow.install(content, activity)`，为其中 RecyclerView 安装自定义上下 EdgeEffect | 删除安装调用；统一遍历时把 `ScrollView`、`NestedScrollView`、`AbsListView` 和纵向 `RecyclerView` 设为 `OVER_SCROLL_NEVER` |
| `helpers/ClassicDialogScrollView.kt`：`ClassicDialogScrollView` 基类 | 继承 `views.ClassicGlowScrollView`，在触顶/触底时通过 Canvas 画自定义 glow | 改回普通 `ScrollView`，并在初始化时关闭 overscroll |
| `helpers/ClassicDialogScrollView.kt`：`ClassicDialogPickerLayout.onAttachedToWindow()` | 对 Picker 对话框的子 View 树再次调用 `OxygenClassicEdgeGlow.install()` | 删除安装调用 |
| `helpers/OxygenFastScroller.kt`：`OxygenFastScroller.apply()` | 无条件给快速滚动列表安装 Glow；`PickDirectoryDialog` 和 `PickMediumDialog` 也经过此入口 | 删除该副作用。主页、媒体页、搜索和文件夹 Activity 各自仍通过 Activity 根 View 安装器保留其页面 Glow |

以上处理覆盖复用 `ClassicDialogScrollView` 的 XML/动态长内容弹窗、AppCompat/Commons 列表弹窗，以及目录、媒体和文件 Picker 的纵向列表。横向 RecyclerView 不会被统一设置为禁用；主页面 Glow 和 `SwipeRefreshLayout` 下拉刷新保持原行为。源码复核确认弹窗调用链不再安装自定义 Glow，并且常规纵向 overscroll 已关闭；未做真机录屏验收。
  
