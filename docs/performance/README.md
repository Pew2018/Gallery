# 性能优化工程

当前本地分支：`perf/home-gallery-sorting`。本轮起始 HEAD：`6246f6b156184035c19046225b8d6e2c50efac12`。

- [主页扫描、排序无关统计与缓存升级](home-gallery-stats.md)：当前实现、完整数据流、Room v11→v12、并发/安全、回归、主机对照及真机待验收项。
- [Photos 首屏加载 Phase 1](root-navigation-photo-publication-phase1.md) / [Phase 1.1](root-navigation-photo-publication-phase1.1.md)：冷启动首屏、有限缓存预览、请求关联。
- [Photos 发布稳定性 Phase 1.2](root-navigation-photo-publication-phase1.2.md)：刷新基线保留（不再 160→120）、配置切换重复请求、中间快照准入。
- [验证记录与原始测量](validation-2026-11-09/)：最终测试统计及日志、同数据集基线/修改后观测。
- [早期目录排序后台化记录](home-directory-sorting.md)：历史阶段；其中“不改扫描/schema”的范围已被本轮取代。

完整缓存下的排序切换已通过真实 MainActivity/对话框/适配器主机测试确认：不增加媒体扫描、元数据读取或数据库读写。实际设备与各种授权/存储模式的性能及交互仍标为未验证，不能从主机测试推导真机提升百分比。
