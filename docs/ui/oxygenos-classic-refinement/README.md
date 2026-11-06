# OxygenOS 7.1.1 Classic — Gallery UI 精修项目总纲

> 状态：**Phase 2 设置精修源码/测试已准备；完整自动化与 Actions 验证待执行，真机视觉待验收**。首次审计：2026-11-06。
> 源码审计基线：`refactor/media-pipeline-stability` @ `b7175e8fa0411d8ff600366e316777efb28bb8f3`。
> 该 SHA 仅为审计证据锚点；**实际开发前重新读取远程分支 HEAD，禁止重置到此提交**。
> 本轮任务 **仅新增 `docs/` 文档，不修改应用代码、资源、构建配置、测试或其他目录**。
> **2026-11-06 第二轮定位**：重新审计较新的 `27008fc1b5226187c3c8b99c0a12911aa5163632`，增加 05–09 文件；这个 HEAD 同样只是当时的证据锚点。发现 `PhotoProfileEditActivity` 的 FAB 在 IME 可见时被显式设为 `GONE`，详见 07。**仍未修复任何 UI。**

## 1. 目标与边界

**2026-11-06 新增 UI 精修硬性设计原则：** 保留 OxygenOS 7.1.1 Classic 控件的形态/宽高/留白/小圆角/传统菜单；**尽可能复用 Google 开源的 Material Design 2 / Views 时期 Motion 动画 API**，但仅接入动画，不使用 Material 3/Material You/Expressive 控件样式。所有交互强调色（Ripple、选中图标/文字、Switch/Slider、焦点下划线、进度、Edge Glow、涉及上色的动画）必须由 `OxygenThemeEngine.snapshot()` 解析**用户当前主题预设/自定义强调色**，不可写死旧 ROM 蓝色；详情 10、11 两份专题文档。旧截图的蓝色只作参考样本。

将 Gallery 当前“部分像 OxygenOS 7.1.1”的界面提升为**规格一致、交互完整、可重复验证**的经典 Android 7.1.1 / OxygenOS 4.5.15 风格，保留现有 Gallery 功能和用户主题自定义，而不是重建旧 ROM、切换到 M3 Expressive 或全盘替换 AppCompat。

优先目标：

1. 所有三点菜单的**锚点、宽高、上下留白、列表滚动、文字起点、交互反馈**一致；**收藏文件夹菜单改为无 CheckBox 的双态操作文案**（显示/不显示已排除项目、显示/不显示隐藏项目），不再修复这两项的勾选框。
2. 设置**主页分类行**与**内部设置页**分别按对应参考布局校准；统一分组标题、文字基线、开关、分割线。
3. 底部“相册 / 照片 / 收藏”导航的图标、标签、间隔、Ripple、选中态及切换交互精修，**不恢复曾造成闪烁的整窗动画**。
4. 搜索输入、焦点、软键盘协同动画及列表上下边缘反馈，优先采用克制、可打断的原生过渡。
5. 独立研究与逐步实现预测性返回（含是否增加“应用内动画”开关），避免一次重写全部返回路径。
6. 补充 Toolbar、Dialog、Picker、快速滚动条、夜间主题、字体缩放、横屏及无障碍检查。

**不在本 UI 项目内解决：** 虚拟文件夹、.nomedia/MediaStore 扫描、索引/排序/搜索的数据正确性、Viewer 邻近目录发布、删除恢复、鉴权或数据库架构问题。UI 可交互不等于数据问题修复；不得借 UI 精修改动这些状态机。

## 2. 与并发开发的隔离约束

当前另有 Agent 在同一 `refactor/media-pipeline-stability` 分支修复虚拟文件夹。此文档编制期间及后续 UI 实施预约阶段：

- **本次仅向 `docs/ui/oxygenos-classic-refinement/` 增加 Markdown。** 不触碰 `app/`、`gradle/`、`buildSrc/`、`main`，不创建分支/PR，不合并/强推。
- 不能把本文件审计时 HEAD 当作新的执行起点；实施前对照别的 Agent 最新提交，尤其是相关页面/资源是否改变。
- 在虚拟文件夹修复完成并验收之前，不启动跨页面 UI 代码改动。对共享 `MainActivity`、`MediaActivity`、`SettingsActivity`、`GalleryBottomBar` 的修改必须串行整合。
- 每一代码阶段以一个经过验证的最新 HEAD 为基线、集中改动后统一验证与提交；如有其他 Agent 并发推送，先更新基线及验证，再非强制推送。
- **没有真机录屏/截图与编译结果时，不宣称视觉、动画或功能“完全修复”。**

## 3. 证据优先级与术语

1. **用户新增的 Gallery 真机截图**：现状证据，包含收藏页、相册页弹出菜单、设置首页和“全屏查看”内部页面。
2. **用户新增的 OxygenOS 7.1.1 参考截图**：目标外观证据，包含 OnePlus 旧版设置、电话菜单、联系人输入及底部越界蓝色弧形等。参考界面来源多样，不能把旧版“系统设置大标题”套到 Gallery 紧凑的应用设置首页。
3. **用户提供的 `OxygenOS_4.5.15_Android_7.1.1_UI_Design_Guide_v1.0.md`**：基于 29 张截图的拟合规格；当前未作为仓库文件提交。其 [实测]、[视觉观察]、[拟合]、[未知] 标记必须保留。
4. **实际 GitHub 分支代码**：用于判定现状与候选根因；只读代码不能证明最终设备渲染。
5. **Android 官方公开文档**：用于 API 能力/系统行为，见预测性返回评估。

尺寸的 `dp/sp` 只在有证据时视为“当前实现值”；历史 ROM 的未知资源不可伪装成官方精确数据。目标值统一标注为“候选”或“拟合”，待真机对照验证。

## 4. 文档索引

| 文档 | 用途 |
|---|---|
| [01-visual-and-code-gap.md](01-visual-and-code-gap.md) | 截图、设计基线、当前 Kotlin/XML/测试证据、问题与优先级 |
| [02-phased-implementation.md](02-phased-implementation.md) | 依赖关系、阶段 0–4、代码级修改边界、逐项 DoD |
| [03-verification-matrix.md](03-verification-matrix.md) | 自动化和真机矩阵、量化观察项、非回归门槛 |
| [04-predictive-back-feasibility.md](04-predictive-back-feasibility.md) | 预测性返回的系统约束、路径清单、试点方案、开关边界及工程量 |
| [05-menu-settings-popup-code-atlas.md](05-menu-settings-popup-code-atlas.md) | 15 个菜单 XML、动态三点菜单、设置手柄和弹出/关闭动线 |
| [06-navigation-hierarchy-and-viewer-back-map.md](06-navigation-hierarchy-and-viewer-back-map.md) | 普通文件夹/虚拟收藏/回收站/照片配置的页面层级与 Viewer 返回路径 |
| [07-input-ime-empty-profile-fab.md](07-input-ime-empty-profile-fab.md) | 输入框、Commons 搜索逻辑、自定义配置空草稿加号被 IME 隐藏的源码定位及修法 |
| [08-animations-dialog-viewer-transition-atlas.md](08-animations-dialog-viewer-transition-atlas.md) | 所有 Gallery Dialog、Commons 弹窗、Popup、全屏翻页和返回动画入口 |
| [09-ui-work-orders-and-safety-rails.md](09-ui-work-orders-and-safety-rails.md) | 可领取的实施工单、文件热区、依赖前置及不能破坏的行为 |
| [10-accent-semantic-and-runtime-invariants.md](10-accent-semantic-and-runtime-invariants.md) | **用户自定义强调色不可写死**；语义 Token、读色位置、渐变/动画上色、各种主题回归 |
| [11-legacy-material-motion-selection.md](11-legacy-material-motion-selection.md) | Google 旧 Material Design Views Motion 选型、真实 Kotlin 入口、Window 与 View 区别、候选时长和回退 |

关联历史材料：[经典弹窗修复审计](../classic-dialog-repair-review.md)、[历史弹窗全覆盖记录](../../classic-dialog-review.md)、[经典动画真机验收](../../testing/classic-motion-device-checks.md)、[媒体管线文档](../../media-engine-v2/README.md)。

## 5. 任务看板（规划状态，不表示完成）

**2026-11-06 设计决策更新：** 收藏文件夹三点菜单保留两条操作、原有 MenuItem ID 与 `config.favoriteShowExcluded` / `config.favoriteShowHidden` 设置，仅将带勾选框的状态项改为**普通操作菜单项**：关闭时显示“显示已排除项目”/“显示隐藏项目”，开启时显示“不显示已排除项目”/“不显示隐藏项目”；文案是**下一次点击会执行的操作**。两项菜单不再 `checkable`，不用 `isChecked` 表示状态；设置仍持久化且原有鉴权/刷新逻辑不变。用户所写“不现实隐藏项目”按笔误规范为“不显示隐藏项目”。本决策覆盖本文及 01/02/03/05/08/09 中旧的“修复 Checkbox”要求，不影响 Gallery 其他 Checkbox。

| 编号 | 项目 | 优先级 | 实施阶段 | 状态 |
|---|---|---|---|---|
| UI-01 | 收藏菜单移除两个 CheckBox，改为随当前设置切换的操作文案 | P1（设计决策） | 1 | 已实现；四种状态/中英文与无 CheckBox 自动化通过；真机视觉待验收 |
| UI-02 | 三点菜单锚点、宽度、高度、留白 | P1 | 1 | 已按对比图修正下沿展开；188dp 最小宽度及 320/360/412/600dp LTR/RTL bounds 自动化通过；待用户真机复验 |
| UI-03 | 设置主页与内部设置行规格和分栏 | P1 | 2 | Phase 2 已实施；完整自动化/CI 待执行，真机待验收；见 phase-2/04 |
| UI-04 | 底栏选中态、形态与 Ripple 时序 | P1 | 3 | 待视觉测试 |
| UI-05 | 搜索输入/焦点/IME 动画 | P1 | 3 | 待验证 |
| UI-06 | 列表顶部底部经典越界反馈 | P2 | 3 | 待原型 |
| UI-07 | 预测性返回与可选动画开关 | P2，独立风险项 | 4 | 仅可行性规划 |
| UI-08 | Toolbar/Dialog/Picker/深浅主题等扫尾 | P2 | 1–4 | 待覆盖审计 |
| UI-09 | 新建/编辑照片配置时 IME 隐藏添加来源 FAB | P1（源码明确） | 1 | 强制 GONE 已移除；复用 edge-to-edge 底栏 Insets 并跟随 IME 动画；自动化通过，真机距离/窗口几何待验收 |
| UI-10 | Popup/Dialog/Viewer/文件夹进退全套动画归属 | P2 | 先做规格审计再分级实现 | 已建代码地图，未改 UI |
| UI-11 | 全部强调色与动画着色适配用户自定义颜色 | 跨阶段硬性约束 | 0–4 | 已建立 Token/测试合同，未改 UI |
| UI-12 | Google 旧版 Material Motion 只用于动画、不改变经典控件形态 | 跨阶段技术选型 | 0–4 | 已建立场景矩阵，待依赖检查与试点 |

优先级说明：P0 表示明确影响当前界面状态辨识或操作；不代表优先于正在修复的虚拟文件夹功能 P0。

## Phase 1 implementation

本阶段交付记录见 [phase-1/00-implementation-summary.md](phase-1/00-implementation-summary.md)。P1-A/P1-B 源码改动和回归用例已提交；P1-C 按用户对比图对全局 Overflow/Popup 菜单实施下沿展开与面板最小宽度校准，保留 280dp 上限、48dp 可触控行高、经典配色与 Ripple。完整测试与 ARM64 APK 状态以 phase-1/04 记录为准；代码已修改、自动化已验证、真机视觉待验收。

## 6. 下一阶段的首要动作

1. 读取远程分支最新 HEAD 与本目录全部 12 份 Markdown 文档，核对全部列出的代码位置是否仍有效。
2. 与当前虚拟文件夹 Agent 的实际修改文件核对冲突，确认功能修复已验收。
3. **先补齐 Phase 0 的真机截图/尺寸/行为基线；** 禁止靠“看上去差不多”批量硬编码宽高。
4. 严格按阶段推进，每阶段有代码审计、确定性测试、CI 构建、真机回归与结果记录。
5. 每次完成一个阶段，更新本文状态和相关证据，不能直接将“计划”改为“已验证”。

## Phase 0 audit package

**2026-11-06 — Static audit and patch plan completed; device/dependency baseline pending.** This phase did not modify Gallery app code.

| Document | Coverage |
|---|---|
| [00-baseline-and-environment.md](phase-0/00-baseline-and-environment.md) | Remote HEAD, parallel-change risk, tools, and verification blockers |
| [01-menu-dialog-settings-inventory.md](phase-0/01-menu-dialog-settings-inventory.md) | Menus/popups, favorite action text, settings, dialog ownership |
| [02-bottom-bar-search-ime-and-edge-effect.md](phase-0/02-bottom-bar-search-ime-and-edge-effect.md) | Bottom bar, search/IME, FAB, overscroll |
| [03-viewer-navigation-and-transition-risks.md](phase-0/03-viewer-navigation-and-transition-risks.md) | Viewer paths, back ownership, transition risk |
| [04-material-motion-dependency-and-selection.md](phase-0/04-material-motion-dependency-and-selection.md) | Declared vs resolved dependencies, API evidence, selection |
| [05-accent-color-contract-audit.md](phase-0/05-accent-color-contract-audit.md) | User theme tokens, neutral literals, remaining risks |
| [06-reproducible-verification-baseline.md](phase-0/06-reproducible-verification-baseline.md) | Commands/results, evidence limits, device template |
| [07-phase-1-readiness-and-patch-plan.md](phase-0/07-phase-1-readiness-and-patch-plan.md) | Prioritized minimum fixes and safety rails |

Audit source SHA: 024b4731ac6508cab45f7d81eea73294a78d0d04. Source findings are separated from executed tests and device measurements. No APK was built and no UI was declared fixed.

## Phase 2 implementation

[实施摘要](phase-2/00-implementation-summary.md)记录设置主页、八分类、Section、控件主题、导航与宽屏的最小presentation修改。[回归与构建](phase-2/04-regression-and-build.md)区分已执行/待执行验证，[真机清单](phase-2/05-device-acceptance-checklist.md)仍待设备。UI-08仅设置/主题页Section与Divider一致性、UI-11仅设置Ripple/主题色合同获得本阶段代码覆盖，不宣称跨阶段全部完成。
