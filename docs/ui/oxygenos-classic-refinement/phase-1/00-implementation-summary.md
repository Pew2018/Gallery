# Phase 1 — 实施摘要

## 基线与范围

- 仓库：`Pew2018/Gallery`
- 分支：`refactor/media-pipeline-stability`
- 实施前刷新到的远程 HEAD：`72eaf3a8d8bee5d1c894a20f37561c98398ef92a`
- 本阶段只处理照片配置编辑页 FAB/IME、收藏菜单双态操作文案，以及有测试/源码证据支持的菜单审计项。
- 不触碰媒体扫描、Viewer 分页/顺序、目录鉴权、删除恢复、Room 或 `main`；不引入新菜单实现或全局窗口动画。

## 实施概览

| 工单 | 代码结果 | 证据状态 |
|---|---|---|
| P1-A | 去掉 IME 可见时隐藏 FAB 的分支；用 Commons edge-to-edge `moveBottomSystem` 处理系统导航区；用 WindowInsetsCompat/WindowInsetsAnimationCompat 处理键盘相对导航区的位移 | 源码与回归用例；根窗口 bounds、IME 距离与旋转仍待设备 |
| P1-B | 原两个 MenuItem ID/order/handler 保留；XML 不再 checkable；标题根据保存的配置值映射为四种本地化的下一步操作；删除这两个 ID 的 Checkbox 专属绑定 | 源码与回归用例；鉴权成功/取消的完整 MainActivity 运行流待设备 |
| P1-C | 对 15 个菜单 XML、动态 Popup、共享 AppCompat 链、LTR/RTL offsets、行高/宽度上限与 Ripple 测试进行差异审计；无足够坐标证据处不做猜测性几何修改 | 静态差异审计；既有 Robolectric Popup bounds 测试覆盖 320/360dp LTR/RTL 和 3/20 条菜单；CI 结果见 04 |

## 修改文件

应用实现：

- `PhotoProfileEditActivity.kt`
- `MainActivity.kt`
- `TiebaMenuItemView.kt`
- `FavoriteMenuActionTitles.kt`（新增）
- `menu_main.xml`
- 英文与简体中文 `strings.xml`

回归与项目文档：

- `PhotoProfileEditUiTest.kt`
- `UiFollowupTest.kt`
- 本 `phase-1/` 记录与 UI refinement `README.md`

## 强调色与 Motion

FAB 的背景和对比前景继续由 `OxygenThemeEngine.snapshot()` 的 `rawAccent` 策略生成，本次没有写入固定强调色。删除菜单 Checkbox 专属绑定后，其余 AppCompat Checkbox/Radio 仍使用现存菜单 Theme/controls 样式。未接入 Material Motion；键盘位置随动只使用 AndroidX WindowInsetsAnimationCompat 属性位移，不改变菜单或窗口转场。

## 交付状态

自动化测试、Actions 与 APK 状态请看 [04-regression-and-build.md](04-regression-and-build.md)。没有真机截图、输入法录屏或 Popup 坐标，故不把菜单视觉与 FAB 键盘边距标记为真机完成。
