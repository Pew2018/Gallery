# 阶段 2 验收记录

基线为 `b96d6e423275b94b51d2d609d97707b499f79a69`，开工时与本地干净工作区、远端 `perf/home-gallery-sorting` 一致。

- 以 JDK 21 和现有 Wrapper 在基线源码的独立快照重新构建及执行。基线 JVM 为 1,235 通过、0 失败、0 跳过。基线 lint 为 14 errors、802 warnings、2 hints。
- 新增协调器单测覆盖阶段合法性、两次操作身份、重复及乱序结果、终态幂等、取消和过期回调、资源释放、交互回弹和移交。Grid 增加 result 先于 reenter 的绑定回归。Viewer 测试同时确认旧 entry callback 无法映射新 return 代理。
- 新增模拟器验收循环，检查 50 次独立 Session、同图及换图返回、唯一终态、目标身份、名称和 Holder 释放、输入解锁。
- 精确基线 API 29 全套注册 19 项，启动 17 项。11 通过、1 跳过、5 失败，末项手势注入线程异常终止进程。失败为搜索横向快滚、照片画幅用例的状态栏首帧、深浅状态栏首帧、下拖检查点、文件夹转场手势注入。
- 精确基线 API 37 全套的目录复用用例等待完整 Viewer catalog 超时。缓存启动用例随后持续停滞，已结束该轮进程。阶段 1B-R 还记录过 API 37 并行筛选视口超时，以及隔离复测通过。
- API 29 早期候选全套同样启动 17 项，8 通过、1 跳过、8 失败，文件夹手势注入终止进程。除基线的五项外，观察到缓存启动行数、普通横向快滚、视频共享入场颜色样本失败。
- API 29 同机定向对照复现了基线及候选 `2217f49e3` 的普通横向快滚失败，两次偏移均为 189，期望为 3045。候选版的另五项通过，包括缓存启动、视频共享元素、照片下拖、配置相册换图回位和部分扫描。早先的缓存及视频颜色失败在这轮定向复测未复现。
- API 37 单项重试在 `c5471c0b7` 为 49 COMPLETE、1 FALLBACK，在 `2217f49e3` 为 46 COMPLETE、4 FALLBACK。两轮均完成 50 次往返及清理断言。首次隔离启动出现模拟器进程启动超时，系统在测试开始前结束进程。
- 本地测试使用模拟器的 View、绑定位图和平台回调确认。真机白帧、完整合成器交接和视觉流畅性均为 **NOT VERIFIED**。

[原分支 ARM64 Release 工作流](https://github.com/Pew2018/Gallery/actions/workflows/arm64-foss-release.yml?query=branch%3Aperf%2Fhome-gallery-sorting)

## 最终源码 c7872b3a9

- JDK 21 下全套 JVM 为 **1,247 通过、0 失败、0 跳过**。两个 Debug APK 和 `assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` 成功。本地 Release APK 仅含 `arm64-v8a`，未配置本地签名。
- Lint 仍以基线的 14 errors 退出。报告同时含 802 warnings、2 hints。按文件、严重度、问题消息去除行号差异后，新增 0、移除 0。
- 并行模拟器测试下，API 29 完成 50 次清理，24 次 COMPLETE、26 次 FALLBACK。API 37 完成 50 次清理，15 次 COMPLETE、35 次 FALLBACK。每轮均检查换图及同图返回、目标加载键、Holder 可回收、名称消除和 Viewer 输入解锁。本地重型构建结束后的 API 37 隔离复测再次通过，41 次 COMPLETE、9 次 FALLBACK，用时 54.962 秒。
- API 29 的视频用例在上述混跑记录到 12 个样本中的 5 个样本缺少源色。隔离重跑通过该颜色断言，随后触摸注入返回 false 并使测试失败。同机精确基线的三次隔离复测均在同一注入断言失败，均通过颜色断言。混跑掉色原因仍待验证。模拟器并行时的降级比例不作为真机性能结论。

## CI 与 APK

[ARM64 FOSS Release Actions](https://github.com/Pew2018/Gallery/actions/runs/44230631501) 成功。下载的 [APK artifact](https://github.com/Pew2018/Gallery/actions/runs/44230631501/artifacts/14395277424) 仅含 `arm64-v8a`。工作流源提交与 APK 内嵌 Git revision 均为 `c7872b3a90f85c6c2ddfb0640348f8b4af060f95`。APK SHA-256 为 `b3cb80aa6d172a20133703bb1e9523be5904c9d1348bd4383060a97ca92c7f87`，与工作流记录一致。APK v2 签名独立验证通过，证书为临时 CI 测试证书，不能覆盖安装不同证书的发行版。

阶段 3 准入仍关闭。API 29 混跑中的视频颜色取样异常待定位，真机实屏白帧与合成器证据为 **NOT VERIFIED**。
