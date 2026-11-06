# Phase 1 — Overflow 菜单布局与 Ripple 审计

## 覆盖范围

按 Phase 0 的清单复核 `res/menu/` 中 15 个 XML：主页/外部 Intent、媒体目录、Viewer、单媒体、视频播放器、编辑器、搜索、照片配置编辑、添加文件夹、已包含文件夹、壁纸与 3 个 CAB。确认 XML menu item、工具栏 ActionMenu、真正 Overflow Popup、动态 PopupMenu、ActionMode 各由不同锚点/Presenter 管理；文件选择器和媒体选择器的 Commons/系统弹窗不应一概算作 Toolbar Popup。

动态 Popup 入口复核：照片配置管理行、目录 Picker 选项、文件 Picker 隐藏选项、顶栏动作排序手柄、底栏排序手柄。共享入口为 `OxygenClassicMenus.createPopup()` / `OxygenToolbarStyle.preparePopup()`；菜单 item 行通过 AppCompat `ListMenuItemView` 与 `TiebaMenuItemView`。

## 几何与状态

- LTR `-20dp` / RTL `+20dp` 水平 offset、背景圆角/padding 和 `TiebaMenuItemView` 280dp 测量上限仍无截图证据需要修改，予以保留。此前 `16dp` 垂直 offset 与 `overlapAnchor=true` 使菜单从 Overflow 锚点上方/顶栏中展开；用户提供的 Gallery 与 OxygenOS Classic 对比截图确认了这一位置差异。
- 后续修正：Overflow 样式改为 `overlapAnchor=false`、垂直 offset `12dp`。第一次坐标测试测得 Toolbar Overflow 锚点下沿距顶栏底部 12dp；对应偏移使菜单上沿对齐顶栏底部。扩展的 `ClassicOverflowPositionTest` 要求 Toolbar 与 anchored Popup 在 LTR/RTL 下不覆盖顶栏，且不与顶栏分离超过 16dp；`ClassicToolbarLayoutTest` 同步锁定新的非重叠契约。复跑 Actions 已通过 935 项单测，其中新增垂直边界与旧 overlap 契约测试均通过；最终 Pixel 8 Pro 视觉位置仍待用户复验。
- 现有 `ClassicOverflowPositionTest` 使用 PopupWindow bounds，在 360dp、LTR/RTL、Toolbar Overflow/anchored Popup、3/20 行菜单下核对锚点与屏幕边界。没有 320/412/600dp、设备 Insets、高字体或多层菜单证据。
- 收藏双态文案去除旧 checkbox 特判，避免继续预留其 trailing checkbox 与 margin。菜单行其余测量代码不变。
- AppCompat 行背景保留既有 RippleDrawable/state 处理。本阶段没有重建 Popup 或添加开闭动画；既有完整 Ripple 点击、按住滑出取消仍需设备录屏检查。

## 结论

P1-C 的水平定位、行高/Ripple 未做全局猜测性改动。垂直展开已根据用户实机截图作定点修正，待 Actions 与设备复验。设备复核步骤：在 320/360/412/600dp、LTR/RTL、英文/中文、常规/放大字体下逐页打开 15 个 XML 对应宿主和五个动态锚点；记录锚点/Popup bounds、系统栏 Insets、菜单项数、最大行宽、长列表滚动；执行轻点、按住、滑出、键盘焦点切换与主题变化。只对可复现 bounds/裁切缺陷作小范围修正。

## 动画

未添加 MaterialFade/SharedAxis 或 PopupWindow 动画。AndroidX View Transition 不拥有 AppCompat PopupWindow；此项留待独立 Motion 阶段审查。
