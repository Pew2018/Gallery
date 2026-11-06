# Phase 0 · 05 — 动态强调色与语义 Token 审计

> 源码基线 024b4731ac6508cab45f7d81eea73294a78d0d04。颜色字面量来自关键 helper 静态扫描；没有运行时截图取色或全仓验证。

## 当前真值源

- OxygenThemeEngine.accent(config) 按 oxygenThemePresetId读取有效 preset，否则取 oxygenCustomAccent；snapshot(context, config)统一解析Classic或Commons token（约27–42）。
- OxygenThemeTokens区分rawAccent、interactiveAccent、page/dialog/toolbar surface及primary/secondary/disabled text。readableAccent基于实际表面调可读性；switchPalette对raw accent有专门规则。
- OxygenClassicControls处理经典Switch/Radio/Checkbox/SeekBar。OxygenClassicDialog与ClassicCommonsHooks在Window展示和布局时取主题色。OxygenToolbarStyle处理toolbar色。GalleryBottomBar.selectTab/recolor/updateRipple取snapshot并更新现有RippleDrawable。OxygenClassicPageInput为cursor/focus underline取当前theme token。
- PhotoProfileEditActivity.applyPageColors给FAB设置tokens.rawAccent，并按其对比度选择黑/白icon前景。未证明每一自定义色在设备可读性均合格。
- 当前运动主要控制alpha/translation；Material Motion默认颜色不可覆盖上述语义来源。

## 核心 helper 固定颜色扫描结果

| 字面量示例 | 位置/作用 | 审计分类 |
|---|---|---|
| #212121、#1E1E1E | classic toolbar/status neutral | 有意经典中性色，不是accent缺陷 |
| #FAFAFA、#303030、白色 | theme/dialog surfaces | light/dark表面 |
| #E6E6E6、#D6D6D6、#5A5A5A、#E0E0E0、#484848 | off switch/progress track与灰阶 | 控件中性色 |
| Ripple alpha 32/28 | 从当前accent或toolbar foreground计算 | 动态派生，不是固定RGB |
| 控件/PageInput/bottom bar | 本次关键文件扫描未发现Color.BLUE或固定#2196F3运行时交互色 | 只限检查文件，不代表全仓无硬编码 |

静态中性色、禁用灰、危险红、视频沉浸黑和Toolbar白色前景不可不分语义地删改。不同参考App各自蓝、青绿、紫、黑、红不是Gallery统一Accent。

## 控件对应token合同

| 使用处 | 来源 |
|---|---|
| 页面/底栏selected图标与文本 | interactiveAccent |
| 普通菜单文字 | 当前Popup surface的primary/secondary text；收藏双态文案不是checkbox状态 |
| Ripple | 用户主题有效强调色派生alpha与控件state |
| 开启Switch thumb | switchPalette(tokens).onThumb，遵守rawAccent特例 |
| SeekBar/scan/loading progress | progressPalette或现有interactive token并按surface验证 |
| Input cursor/underline | OxygenClassicPageInput的state list |
| FAB | 当前源码用rawAccent，先保留，按极浅/暗/透明色测icon对比 |
| Dialog按钮/Tab indicator | readableAccent(rawAccent, dialogBackground) |
| EdgeGlow若实现 | 当前表面上可读的用户accent，不能固定旧蓝 |
| Motion container/scrim | 真实起始/结束surface与主题遮罩；不用MDC默认颜色 |

主题变化要取消旧Animator并用新snapshot归一；不能永久缓存seed。已打开Popup/Window是否动态重着色尚待实机。

## 风险与未覆盖项

1. 极浅、极暗、低对比、透明ARGB seed在每种surface上的实机对比未测。
2. 仅扫描关键主题helper，未完成所有Kotlin、res/color、drawable、progress/fast-scroller的全仓RGB审计；这是后续Phase1开工前检查项。
3. 主题切换和动画中途取消的旧色残留未测。
4. 当前没有证据证明存在固定蓝色accent故障；也没有足够证据宣称全颜色方案已验收。

