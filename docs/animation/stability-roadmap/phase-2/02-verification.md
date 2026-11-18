# 阶段 2 验收记录

基线为 `b96d6e423275b94b51d2d609d97707b499f79a69`，开工时与本地干净工作区、远端 `perf/home-gallery-sorting` 一致。

- 以 JDK 21 和现有 Wrapper 在基线源码的独立快照重新构建及执行。基线 JVM 为 1,235 通过、0 失败、0 跳过。基线 lint 为 14 errors、802 warnings、2 hints。
- 新增协调器单测覆盖阶段合法性、两次操作身份、重复及乱序结果、终态幂等、取消和过期回调、资源释放、交互回弹和移交。Grid 增加 result 先于 reenter 的绑定回归。Viewer 测试同时确认旧 entry callback 无法映射新 return 代理。
- 新增模拟器验收循环，检查 50 次独立 Session、同图及换图返回、唯一终态、目标身份、名称和 Holder 释放、输入解锁。
- 精确基线 API 29 全套注册 19 项，启动 17 项。11 通过、1 跳过、5 失败，末项手势注入线程异常终止进程。失败为搜索横向快滚、照片画幅用例的状态栏首帧、深浅状态栏首帧、下拖检查点、文件夹转场手势注入。
- 精确基线 API 37 全套的目录复用用例等待完整 Viewer catalog 超时。缓存启动用例随后持续停滞，已结束该轮进程。阶段 1B-R 还记录过 API 37 并行筛选视口超时，以及隔离复测通过。
- 本地测试使用模拟器的 View、绑定位图和平台回调确认。真机白帧、完整合成器交接和视觉流畅性均为 **NOT VERIFIED**。

[原分支 ARM64 Release 工作流](https://github.com/Pew2018/Gallery/actions/workflows/arm64-foss-release.yml?query=branch%3Aperf%2Fhome-gallery-sorting)
