# 阶段 5C 后续：选中态精修

日期：2026-11-27（香港时间）。只在 `perf/home-gallery-sorting` 上完成。起点 HEAD `d34d284daf0b50dcf0f995fd512bc10205c8dc64`，工作区干净。没有新分支，没有回到 main。顶栏高度保持阶段 3.5（3.5）。Viewer 回程、预测性返回、预览位置、文件夹动画、视频与大图未改。

## 派生色规则

选择顶栏不再使用固定 `#757575`。

- 种子：当前用户强调色 `rawAccent`（只读，不写回配置）。
- 顶栏：`OxygenThemeColors.selectionToolbar` — HSL 饱和 ×0.55，明度 ×0.70 后夹到 \[0.22, 0.42\]，再向黑搜索直到白字对比 ≥ 4.5。
- 状态栏：`selectionStatus(toolbar)` — 明度再减 0.08（下限 0.12），同样保证白图标对比 ≥ 4.5。两截一起随选择进度过渡。
- 前景：白字与白色操作图标。
- 退出：进度回到 0，恢复进入前的主题顶栏/状态栏；强调色与预设 id 不变。
- 反向可中断；`onPrepareActionMode` 同目标不重播；`ANIMATOR_DURATION_SCALE == 0` 直接终态。

## 动画参数

| 参数 | 值 |
| --- | --- |
| 顶栏过渡 | `SELECTION_CHROME_MS = 180`，既有 `PathInterpolator(0.4, 0, 0.2, 1)` |
| 缩略图 | `CONTENT_SCALE = 0.92`，`SELECTION_CONTENT_MS = 160`，缓出 `PathInterpolator(0, 0, 0.2, 1)` |
| 滑动中单格 | `SELECTION_SWIPE_MS = 110` |
| 勾选 | 后半段（`t ≥ 0.5`）淡入；`CHECK_START_SCALE = 0.86` → 1；锚在缩小后图片的顶末角（translation 跟随 inset） |
| 列表 | 不缩放整行；只缩略图 + 勾选 |
| 全选 | 仅当前可见项播放；屏外直接终态；滚回不补播 |

## 滑动取消规则

`OxygenSelectionDrag` 替换 Commons 橡皮筋 `selectItemRange`：

- 同一次长按拖动：滑入未选项 → 选中；滑入已选项 → 取消。
- 手指停在同一格不二次翻转；离开再进入才允许反向。
- 每个手指进入的格子结算一次；松手后状态保持。
- 滑动过程中的 `notify` 绑定走短时长。

## 本地构建

JDK 21，Gradle 9.5.1，`ANDROID_HOME` 指向本机 SDK 36。

| 检查 | 结果 |
| --- | --- |
| `OxygenSelectionPhase5cTest` | 通过（含派生色、勾选后半段、滑动 toggle、短时长） |
| `OxygenThemeColorsTest`（含 selectionChrome 用例） | 通过 |
| `SelectionToolbarHeightTest` | 通过 |
| `OxygenSystemBarsTest` | 通过 |
| `:app:testFossDebugUnitTest` | 通过 |
| `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` | 通过 |

## 验收分层

| 项 | 代码 | JVM | 真机 |
| --- | --- | --- | --- |
| 强调色派生顶栏/状态栏 | 完成 | 通过 | 未验证 |
| 缩略图 92% + 勾选后半段 | 完成 | 通过 | 未验证 |
| 终态满格/勾选复位、不重载图 | 完成 | 通过 | 未验证 |
| 长按滑动连续取消 | 完成 | 通过（listener 级） | 未验证 |
| 顶栏高度 3.5 | 完成 | 通过 | 未验证 |

未通过：无（上述 JVM 项）。真机观感未验证。

源码提交 SHA 写在本文件随后的文档提交中（见下方“提交 SHA”行，于 push 后填入）。
