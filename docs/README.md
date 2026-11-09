# Gallery 文档中心

本目录按“当前工程、历史交付、专题参考”组织。历史报告保留原始证据，但其中的分支名、阶段状态和下一步只代表当时，不作为当前任务指令。

## 当前工程

- [性能优化工程](performance/README.md)：主页目录排序后台化的设计、实现、验证状态与后续性能验收。
- [媒体管线历史路线图](media-engine-v2/18-current-media-pipeline-roadmap.md)：此前媒体索引/加载/排序工程的阶段计划。该文档的基线与分支状态已过期；只用于理解历史阶段，当前 `perf/home-gallery-sorting` 状态以性能优化工程为准。

## 专题资料

- [媒体管线与诊断](media-engine-v2/README.md)、[诊断说明](diagnostics/app-diagnostics.md)
- [界面精修](ui/oxygenos-classic-refinement/README.md)
- [备份与恢复](backup/)
- [回归与设备测试](testing/)、[模拟器测试记录](emulator-testing/)
- [其它界面/媒体专题](classic-dialog-review.md)、[媒体资源](media-assets.md)、[工具栏稳定性](Photo_Profile_Toolbar_Stability_2026-11-07.md)

## 历史归档约定

`media-engine-v2/` 中 01–17 是旧阶段资料；22–43 是各阶段审计、实现和交付记录。它们作为历史归档保留，不表示当前分支已经完成相同工作。各专题目录中的 `phase-*`、审计、修复和设备验收文档同样按固定提交追溯。阶段仅在对应记录提供实际测试/CI/设备证据时才标为完成。

## 状态口径

- **已实现**：代码已提交；不代表测试通过。
- **自动化已验证**：记录了对应提交的实际测试结果。
- **设备待验**：真机数据或交互验收仍缺失。
- **已归档**：交付记录保留供追溯，不是当前待办。
