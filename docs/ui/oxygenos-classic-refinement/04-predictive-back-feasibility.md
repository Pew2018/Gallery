# 04 · 预测性返回可行性、开关边界与工程量评估

> 阶段：**技术预研，不在本轮实现**。Android 官方文档于 2026-11-06 核对。
> 审计代码锚点：`refactor/media-pipeline-stability` @ `b7175e8fa0411d8ff600366e316777efb28bb8f3`；实施时必须重新审计最新 HEAD。

## 1. 为什么要独立评估

用户观察“部分页面有预测性返回，其他页面没有”。这可能由**跨 Activity 系统动画**、**屏内导航回调拦截**、**IME 消费返回**、**对话框窗口**、**Gallery Viewer 横向手势**等多个因素造成。不能把“页面返回没有动画”统一归为某个 Android Manifest 属性。

Gallery 当前 `gradle/libs.versions.toml`：`minSdk=26`、`compileSdk=36`、`targetSdk=36`；`commons=7.1.3`。`SettingsActivity` 有 `OnBackPressedCallback`（只在分类子页启用），`MainActivity` 和 `MediaActivity` 有 `onBackPressedCompat()`；`OxygenThemeActivity` 另有返回逻辑。检查的 `app/src/main/AndroidManifest.xml` 没有显式 `enableOnBackInvokedCallback` 声明。**仍需检查 Commons 基类、合并后的 Manifest 和 AndroidX 依赖树，不能仅凭本项目代码断言所有页面的系统行为。**

## 2. 平台事实与限制

依据 Android Developers：

- Android 13（API 33）引入预测性返回；Android 14 以后对自定义进度动画有更完整 API，AndroidX Activity 提供向后兼容的 `OnBackPressedCallback` 及 progress callback。
- **target 36 + 运行在 Android 16+**：系统跨 Activity / 返回桌面 / 跨任务的预测性返回动画默认启用；旧式 `onBackPressed()`、拦截 `KEYCODE_BACK` 的依赖路径必须迁移或验证。
- `android:enableOnBackInvokedCallback` 是 Manifest 属性，可按 `application` 或 `activity` 配置。它**不是普通 SharedPreferences 设置，无法直接通过一个用户开关在运行时随意切换 manifest 属性**。
- 具有启用的自定义默认优先级 Back 回调的场景可能由应用消费该次返回，这时系统级预测性动画不会自动播放；应用必须负责相应页面的进度和取消/完成处理。相反，未消费的 Activity 结束或返回桌面应尽量交给系统绘制。
- 对 IME、Dialog、系统权限界面及外部 Intent，用户看到的返回效果可能属于系统/外部组件；Gallery 的“自定义应用内动画”开关不应承诺控制这些外部界面。

官方资料：
1. [Android 16 行为变更：预测性返回迁移](https://developer.android.com/about/versions/16/behavior-changes-16)
2. [Add support for the predictive back gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
3. [Add support for built-in and custom predictive back animations](https://developer.android.com/about/versions/14/features/predictive-back)

**特别提示**：是否能够展示系统级跨 Activity/返回桌面预览，不仅取决于应用 UI 回调，还取决于设备 API、导航模式、窗口主题、系统策略及生命周期。桌面上的最终效果不能由 Gallery 保证。

## 3. 返回事件清单（必须在 Phase 4 前跑通）

| 场景 | 目前已知入口 | 期望“完成返回”的含义 | 特殊风险 |
|---|---|---|---|
| 设置首页退出 Gallery | `SettingsActivity` 当前分类为空 | Activity 结束，返回前一屏 | 自定义 callback 不应无条件吞系统返回 |
| 设置分类返回首页 | `SettingsActivity.navigateSettingsBack()` | 屏内分类置空、滚动复位到合适位置 | 取消手势不能提前修改 `activeSettingsCategory` |
| Gallery 搜索打开 | `MainActivity.onBackPressedCompat()`、`MediaActivity.onBackPressedCompat()` | 优先关闭搜索与 IME，而非结束 Activity | 输入法先消费返回时不能重复关搜索 |
| 主页目录层级 | `MainActivity.onBackPressedCompat()` | 返回父层目录或退出根屏 | 取消手势不能先 `removeAt` 修改路径栈 |
| 普通文件夹/收藏 | `MediaActivity.onBackPressedCompat()` | 返回前一 Activity，必要时做原有锁定/撤销 | 鉴权会话终止必须只在完成返回后执行 |
| 全屏 Viewer | `ViewPagerActivity` + Fragment/ViewPager | 退出浏览，保持上层网格与滚动锚点 | Back 手势与左右滑动、缩放/下滑关闭冲突；删除恢复和保护源 |
| 主题/图标配置 | `OxygenThemeActivity` | 返回上一级主题页面或 Settings | 当前自建页面层级与动画需要单独接入 |
| 密码/生物识别/确认 Dialog | AppCompat / Commons hooks / 各 Dialog | 取消当前弹窗/授权，不改变主屏过早状态 | 单次取消、指纹状态和嵌套弹窗 |
| 文件选择器/媒体选择器 | Commons / Gallery Picker | 若有未完成选择先还原；否则关闭 | SAF/权限 Intent 可属于系统 |
| 第三方 Intent 入口 | `MainActivity` 等第三方 Intent 分支 | 返回调用方而非 Gallery 默认根页面 | 外部 Activity 返回目标必须正确 |

**优先级模型（建议）**：IME 可消费返回 → 对话框/Sheet → 内联搜索 → 屏内层级 → Activity back stack → 系统桌面。真实顺序必须根据 AndroidX Callback、窗口和输入法的调度结果确定，而不是直接写死一个全局 OnBack 回调。

## 4. 分阶段 Spike 与验收

### Spike A — 只审计，不新增功能

- 用 Gradle `dependencies` 和 `dependencyInsight` 核查实际 `androidx.activity`、`androidx.core`、AppCompat 版本，以及 Commons `SimpleActivity` 返回逻辑。
- 检查合并 Manifest、`OnBackInvokedDispatcher`/AndroidX 注册栈与 `onBackPressedCompat()` 的调用链。
- 分别在 Android 12（3 键/手势）、Android 13、Android 14/15、Android 16+ 的支持设备上记录“开始/进度/取消/完成”的真实表现。
- 标注触发 root window/system back 的页面与目前有自定义拦截的页面；分类注册的 `settingsBackCallback.isEnabled` 时点可能影响预览。

### Spike B — Settings 内部分类（最小且无业务副作用）

- 只在 `SettingsActivity` 当前分类非空时注册、启用支持 progress 的 Back callback；手势开始时缓存当前分类与滚动状态。
- 进度回调只修改展示属性（如轻位移和透明度），不得调用 `renderSettingsPage()`、改配置或结束 Activity。
- 取消时平滑恢复 alpha=1/translationX=0，保留分类与滚动位置；完成时**只调用一次** `navigateSettingsBack()` 或适当的无二次动画提交路径。
- 与原 `animateSettingsPageChange()` 关系明确：避免手势完成后重复播放相同动画，尤其是快速反向。
- 低于支持进度 API 的系统上退化为当前正常返回。

### Spike C — Search、普通目录与 Viewer

- Search 屏内返回仍优先于退出 Activity，IME 可见时不得提前关页面。
- 目录/收藏可预览“上一界面”但手势进度中不可变更媒体顺序、目录栈、鉴权会话。
- Viewer 进入复杂手势场景前先验证：侧边开始的系统 Back 与 ViewPager 水平滑动、缩放、旋转、下滑关闭是否冲突；未授权或删除中可随时中止预览。
- 系统处理普通 Activity 返回时，不要再叠加一个手工的整个窗口缩小/平移动画，以免双重动画。

## 5. 关于“可关闭开关”的产品合同

**建议名称：**「应用内预测性返回动画」

**建议说明：**「在支持的 Android 版本中，返回手势可预览应用内的上一界面。系统返回桌面等动画由 Android 决定。」

- 设置为**开**：仅对经适配的应用内页面启用手势进度展示；普通 Activity 的系统级动画仍由系统决定。
- 设置为**关**：关闭 Gallery 自己的预览绘制/自定义转场，**保留正确的普通返回行为**，不破坏 IME/权限/对话框返回语义。
- 是否实际提供开关，需要先完成 Spike B。若代码需要改 Manifest/重建 Activity 才能达到“完全关闭系统动画”，**不应以普通运行时 Switch 假装实现**；建议保留系统标准行为，只控制应用内部动画，或暂不提供开关。
- 默认值建议：在已验证的 Android 13+ 设备上“开”，否则用当前正常返回；无进度能力时开关只影响 UI 描述，不可承诺系统动画。
- 文案、默认值及设置归类可在 Spike 验证后调整；任何新偏好如需进入备份/恢复、设置导出，另开兼容性任务，不得在这一轮偷改数据模型。

## 6. 相对工程量评估（非承诺工期）

| 级别 | 范围 | 工作量 | 风险 |
|---|---|---|---|
| S | 查 manifest、依赖、Commons 回调；无需自定义进度，只让兼容页面使用系统默认 Activity 返回 | 小 | 中，依赖实际基类 |
| M | Settings + 搜索/目录等少数应用内返回、动画取消/完成、跨版本回退 | 中 | 中 |
| L | Viewer/Picker/Dialog/视频/鉴权 + 全页面状态栈 + 完整开关与备份/恢复 | 大 | 高 |

主要成本不在“动画本身”，而在**不同页面的返回语义、取消不产生副作用、跨版本行为与真实手势回归**。在未获取实际 dependencies 和运行时数据前，不写虚假的固定人日估算。

## 7. 必须通过的回归

1. 右/左边缘拖动到 20%、50%、90% 然后取消：页面状态、滚动、媒体数据、鉴权完全不变；alpha/translation 恢复。
2. 完成手势：导航/返回只执行一次，不多弹一个 Dialog，不重复鉴权/关闭 Viewer。
3. IME 显示时：第一次系统返回按输入法策略关键盘，不错误地跳出图片网格或 Settings；后续返回正确。
4. 系统三键、触摸 Toolbar 返回、硬件 Back、无障碍 Back 和手势 Back 语义一致。
5. Android 12 或更早不崩溃；Android 16+ 不依赖已不再分发的旧回调路径。
6. 活动销毁/屏幕旋转/动画 scale 0 时无半透明残留和失去响应。
7. 返回根页面时不出现敏感媒体一帧泄露、旧目录顺序变更或扫描任务错误完成。

**Go/No-Go：** 若无法稳定处理 Viewer 鉴权或 IME 的手势取消，Phase 4 可只上线 Settings Spike 已验证子集；不要为了“全页面统一”强行交付未安全验证的复杂路径。
