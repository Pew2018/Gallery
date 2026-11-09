# 性能优化工程

## 当前范围

当前代码基线为 `perf/home-gallery-sorting`，HEAD `5165a9dbe304ae547b67020a78ed6f784e3cecd3`。本轮优化聚焦主页相册目录：准备排序键、执行排序和搜索筛选不应占用 UI 线程；媒体扫描、PhotoProfile、Viewer、数据库结构和持久索引不在本轮范围。

## 工程记录

- [主页目录排序后台化](home-directory-sorting.md)：目标、实现边界、兼容性、测试/CI/设备验收状态和后续测量计划。

## 状态摘要

| 工作 | 状态 | 依据/限制 |
|---|---|---|
| 后台排序实现 | 已实现 | 提交 `5532aae`；`HomeDirectorySorter` 和 `MainActivity` 调度调整 |
| 排序单测 | 已添加，结果未核实 | `HomeDirectorySorterTest.kt` 存在；此审计未运行测试 |
| ARM64 Release 工作流 | 已配置，运行结果未核实 | 提交 `5165a9d`；配置不等同成功运行或 APK 验收 |
| 真机性能及交互回归 | 待完成 | 尚无同设备前后对照数据 |

**目前不能声称性能提升已测得或本轮验收完成。**
