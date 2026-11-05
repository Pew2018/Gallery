# Gallery 项目文档入口

> 2026-11-05 整理。文档中的历史分支/版本不等于当前可操作目标，实施前核对远程最新 HEAD。

**当前媒体索引、加载、排序、稳定性项目**：先阅读 [media-engine-v2/18-current-media-pipeline-roadmap.md](media-engine-v2/18-current-media-pipeline-roadmap.md)，再阅读 [实施细则](media-engine-v2/05-minimal-scan-sort-display-refactor.md)、[验证标准](media-engine-v2/04-validation-and-migration-readiness.md) 和 [兼容性契约](media-engine-v2/03-compatibility-contracts.md)。从 `main` `49e799eceab00414ef074cbf71b7f0a35133b669` 创建 `refactor/media-pipeline-stability`，本轮只有文档修订。

[media-engine-v2/README.md](media-engine-v2/README.md) 将 01–17 归为历史证据/修复日志；其它 `backup/`、`testing/`、`emulator-testing/`、`ui/` 是各自功能历史，不能被新媒体路线图的任务需求覆盖。已停用的模拟器 harness 不可当成当前 CI 测试项。

未来任何性能改善、权限回归、旧版数据兼容或 P0 修复都要以指定 SHA 的实际测试与设备数据佐证。
