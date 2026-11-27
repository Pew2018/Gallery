# 阶段 5C：选择态颜色与选中动画

日期：2026-11-27（香港时间）。起点 HEAD 与 origin/perf/home-gallery-sorting 均为 ab914a3ae4588be8dce10e97d6e856fa1c1e49dd（`fix(gallery): drop home directory progress strip for phase 3.5`），工作区在该提交上干净。只在 `perf/home-gallery-sorting` 提交，没有新分支，没有回到 main。阶段 3.5 的顶栏高度、选择栏内容高度和主页进度条删除保持不变。4A、4B、5A、文件夹动画和设置动画未重做。

源码身份：实现与阶段说明在 `36dc097eb008af3182409eb20339ee0bde52684e`，父提交 `ab914a3ae4588be8dce10e97d6e856fa1c1e49dd`。本行在随后的文档提交中写入，代码差异仍在 36dc097eb。

## 改了什么

选择态不再把顶栏和状态栏直接涂成 `OxygenThemeEngine.toolbarSurface` / `toolbarForeground`。进入选择时，当前有效主题的顶栏色与前景色按进度混到选择灰和白色；退出时从当前进度混回当时读到的主题。强调色只读不写，预设 id 与自定义强调色配置都不改。

- 顶栏表面目标 `#757575`，文字和操作图标目标 `#FFFFFF`。
- 配套状态栏目标 `#616161`。API 35 以下写 `window.statusBarColor`，同时写既有状态栏 underlay；API 35 及以上只更新 underlay。状态栏图标保持浅色（白）。导航栏不参与这次过渡。
- Viewer 已拥有窗口时，`OxygenSystemBars.apply` 仍回放 Viewer 记录，不套选择灰。
- 反向操作取消当前动画，从当前进度走向新目标。选择数量或 `onPrepareActionMode` 再次进入同一目标时只重绘当前帧，不重启动画。`ANIMATOR_DURATION_SCALE` 为 0 时直接落到终态。
- 顶栏高度仍用阶段 3.5 的 `selectionContentHeight`。颜色刷新不重新测量。

文件和文件夹选中时，只缩放图片内容（文件夹锁遮罩可见时一并缩放），勾选标记从 0.5 倍淡入到 1 倍。取消时反向。格子、名称和计数不缩放。列表只缩放缩略图，不缩放整行。勾选着色仍是用户强调色。

- 同一项、选择状态变化、当前在屏上、且系统动画开启时才播放。全选沿用 Commons 的选择与 `notifyItemChanged`；不在屏上的项直接落到终态。
- 同一选择状态的再次绑定不替换正在播放的动画。滚动回收时清掉缩放、勾选和该项标记，下一项不会继承。
- 图片请求键和已有 drawable 不因选择绑定而更换。
- 点击打开图片前，若该缩略图还在还原，立即完成显示复位（缩放回到 1、勾选隐藏），再继续原有点击。Viewer 回程、预测性返回、预览位置和来源几何公式未改。
- 没有新增 RecyclerView payload 框架。选择业务仍在 Commons。

## 参数

时长留给阶段 7，这次不按手感改。

| 参数 | 值 |
| --- | --- |
| 顶栏过渡 | `OxygenClassicMotion.SELECTION_CHROME_MS = 180` |
| 内容与勾选 | `OxygenClassicMotion.SELECTION_CONTENT_MS = 180` |
| 缓动 | 既有 `PathInterpolator(0.4, 0, 0.2, 1)` |
| 内容缩放 | `OxygenSelectionMotion.CONTENT_SCALE = 0.85`，轴心为视图中心 |
| 勾选起点 | `CHECK_START_SCALE = 0.5`，透明度随进度 0→1 |
| 顶栏 / 状态栏 / 前景 | `#757575` / `#616161` / `#FFFFFF` |
| 关闭动画 | `Settings.Global.ANIMATOR_DURATION_SCALE == 0` 时进度直接到 0 或 1 |

## 测试

JDK 21，Gradle 9.5.1，`fossDebug`，Robolectric 4.16.1。没有真机，没有模拟器界面。

| 检查 | 结果 |
| --- | --- |
| `OxygenSelectionPhase5cTest` | 通过，9 项，0 失败 |
| `SelectionToolbarHeightTest` | 通过，4 项，0 失败。竖屏、横屏、大字体仍共用阶段 3.5 高度；主页无进度条 |
| `OxygenSystemBarsTest` | 通过，19 项，0 失败。Viewer 与页面栏的既有端点未改 |
| 完整 `:app:testFossDebugUnitTest` | 通过。218 suites，1,663 tests，0 failures / errors / skipped。2026-11-27 10:58 香港时间，Gradle `--offline`，3 分 5 秒 |
| 真机与仪器化界面 | 未验证 |
| Detekt | 任务仍按既有 baseline 非零失败，与阶段 3.5 相同，未扩大 baseline。本阶段对象函数数压到阈值内；不把 detekt 记为通过 |

`OxygenSelectionPhase5cTest` 覆盖：

- 顶栏与容器混到 `#757575`，标题为白，状态栏 `#616161`，导航栏不变，搜索栏高度与 `contentHeight` 不变，强调色和预设 id 不变。
- 进度 0.4 时把标题改成 `4 / 9` 并再次 prepare，动画对象和进度保持。退出从该进度回到主题顶栏和主题状态栏。
- 动画关闭时进入和退出都直接到终态。
- API 36 选择态状态栏颜色在 underlay 上，为 `#616161`。
- Viewer 拥有窗口时，进入选择不改 Viewer 状态栏色。
- 网格：同一项播放；根布局和名称缩放保持 1；终态内容 0.85、勾选可见且透明度与缩放为 1；请求标记和 drawable 不变；同一选择不更换动画。
- 列表只缩放缩略图。
- 不在屏上时直接到终态且没有动画。回收后缩放复位、勾选为 GONE。换项时直接到终态。
- 取消过程中 `settleForSource` 后 `scaleX` / `scaleY` 与 `localToScreen` 的 X 缩放为 1，不等动画结束。
- 动画关闭时内容直接到终态。在屏判断要求已布局且可见。

## 验收项

| 项 | 代码 | JVM | 真机 |
| --- | --- | --- | --- |
| 单选、多选、取消最后一项 | 完成 | 通过（绑定、反向、终态） | 未验证 |
| 全选只动画当前可见项 | 完成 | 通过（不在屏上直接终态） | 未验证 |
| 清空 | 完成 | 通过（退出颜色与取消终态） | 未验证 |
| 快速连点 / 动画中断 | 完成 | 通过（颜色反向、同一选择不重启、点击前立即复位） | 未验证 |
| 滚动复用不错位 | 完成 | 通过（回收清标记与缩放） | 未验证 |
| 选择不重载图片 | 完成 | 通过（请求标记与 drawable 不变） | 未验证 |
| 顶栏保持 3.5 高度 | 完成 | 通过 | 未验证 |
| 系统关闭动画直接终态 | 完成 | 通过 | 未验证 |

未通过：上述 JVM 项没有失败。真机视觉没有未通过记录，因为没有运行。

## 剩余问题

- 没有真机或模拟器画面，内缩、勾选和顶栏变色的实际观感未看。阶段 7 才调时长。
- 文件列表名称仍 `layout_toStartOf` 勾选。勾选从 GONE 变为 VISIBLE 时名称可用宽度按原布局变化；动画期间勾选保持 VISIBLE，整行不缩放。
- 全选的“当前可见”用单元格 `isShown` 与 `getLocalVisibleRect`。Commons 仍对范围发无 payload 的 `notifyItemChanged`，屏外项会绑定并直接落终态，不播放。
- Detekt 全量与 baseline 的逐条新增比较没有作为通过证据。任务本身仍会因既有 baseline 非零而失败。

## 结论

代码完成，JVM 定向测试通过，真机未验证。阶段 5C 不记为已验收。
