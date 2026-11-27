# 阶段 5C MD 精修

日期：2026-11-27（香港时间）。只在 `perf/home-gallery-sorting` 上完成。起点 HEAD `c0a2ace2ea4e0718ee3eb85a26caa36dc943a090`，工作区干净。没有新分支，没有回到 main。顶栏高度保持阶段 3.5。对照 `selection-md-polish.md`；用户已同意缩略图与交互方向，选择态顶栏保持 Grey 600 / 白字，不再改色。

## 顶栏颜色

- 目标：Grey 600 `#757575` / Grey 700 `#616161` / 白字白图标。
- 已删掉强调色压暗派生（`OxygenThemeColors.selectionToolbar` / `selectionStatus`）。
- 退出恢复进入前主题；数量变化不重播；`ANIMATOR_DURATION_SCALE == 0` 直接终态。

## 缩略图动画

| 参数 | 值 |
| --- | --- |
| 缩放 | `CONTENT_SCALE = 0.91` |
| 时长 | `SELECTION_CONTENT_MS = 200`，轻微 decelerate |
| 勾选 | 与缩放同期（`CHECK_FADE_START = 0`）；网格左上 |
| 未选项 | 选择态内淡空心圆 |
| 露底 | 浅灰 `#E8E8E8`，无 stroke |
| 列表 | 不缩放整行；勾选仍在行末 |
| 全选 | 仅可见项播放；滚回直接终态 |

## 滑动矩形选中 / 取消

`OxygenSelectionDrag` 按锚点与当前格的矩形结算。模式在 ACTION_DOWN 快照上固定。缩回矩形时恢复手势前状态。

## 未改

Viewer、预测性返回、预览位置、文件夹页动画、视频播放与大图。

## 本地构建

JDK 21，Gradle 9.5.1，`ANDROID_HOME` → SDK 36。

| 检查 | 结果 |
| --- | --- |
| `OxygenSelectionPhase5cTest` | 通过（灰顶栏、同期勾选、矩形拖选、空心圆、200ms） |
| `OxygenThemeColorsTest` | 通过（已去掉强调色派生用例） |
| `SelectionToolbarHeightTest` | 通过 |
| `OxygenSystemBarsTest` | 通过 |
| `:app:testFossDebugUnitTest` | 通过，218 suites / 1666 tests，0 failures |
| `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` | 通过 |

## 验收分层

| 项 | 代码 | JVM | 真机 |
| --- | --- | --- | --- |
| Grey 600 顶栏 / Grey 700 状态栏 | 完成 | 通过 | 未验证 |
| 缩略图 0.91 / 200ms / 左上勾选 / 空心圆 | 完成 | 通过 | 未验证 |
| 矩形范围选中与取消 | 完成 | 通过（几何 + listener） | 未验证 |
| 顶栏高度 3.5 | 完成 | 通过 | 未验证 |

未通过：无（上述 JVM 项）。真机观感未验证。
