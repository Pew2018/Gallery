# 阶段 1B 测试和性能

开始版本使用 JDK 21 运行全量原有 JVM 套件，1,218 项通过。四个接线检查在旧源码上通过独立 JUnit 执行，四项均触发断言。它们补充行为验证的入口覆盖。1,454→3,080 项另用真实 RecyclerView、受控 executor、虚拟帧和稳定视口在修改 Adapter 前失败一次，失败点为安全布局后仍未提交。

先后执行生命周期、快滚、删除和 Viewer 定向回归，每轮重跑已启用的阶段检查及相关旧用例。最后执行全量 JVM、AndroidTest Kotlin、FOSS Debug、Debug lint、ARM64 FOSS Release 和 API 29 AVD。

| 验收编号 | 覆盖和限制 |
|---|---|
| B01–B04 | 活动的同任务接线、50 次受控 detach/rejoin 及撤权后的取消。其他 Activity 与 Viewer 扩展共享、真实 50 次手机操作和挂起 I/O 的释放仍待覆盖。 |
| B05–B09 | 目录检查点对授权、MediaStore、mtime、修订、配置差异失效。现有阶段优先级、排序和未索引目录回归。复验 API 36 权限切换及真实 .nomedia 原地新增待原设备。 |
| B10–B16 | 受控大列表布局、Diff 一次提交、同绑定 drawable、几何、锚点与最新待处理版本；沿用缩略图复用和搜索相关回归。高速滑块下真实解码和 A→B→A 迟到回调的设备呈现仍待采样。 |
| B17–B20 | 删除代际、重复/批量结果、缓存和部分发布屏障、同路径恢复/替换、空路径及最后一张删除的导航和网格同步。真机回收站往返和外部文件管理器重复操作待覆盖。 |
| B21–B26 | 两入口接线、双点门票、逆序/取消、待清理返回保护和 4,981 项时的当前媒体定位。真实并发回调注入与所列 20 次快速取消、10 次扫描中切页待覆盖。 |
| B27–B28 | 阶段 1A 诊断、来源、搜索、收藏、Classic UI、共享元素 JVM 回归。设备套件保留失败详情。 |

## 本地构建和静态验证

| 检查 | 结果 |
|---|---|
| FOSS Debug JVM | 173 suites，1,230/1,230 通过，0 失败，0 跳过，含 12 个新增用例 |
| Debug 和 AndroidTest Kotlin / APK | 通过 |
| ARM64 FOSS Release | 本地未签名构建通过。包名 org.fossify.gallery，ARM64，1.14.1 (32)，minSdk 26，targetSdk 36 |
| Debug lint | 14 errors、802 warnings、2 hints。按严重度、规则、文件和消息多重集与阶段 1A 对比，新增 0，移除 0 |

完整 Gradle 命令保留 lint 的非零返回；JVM、Debug、AndroidTest 和 Release 任务各自完成。

## API 29 Pixel_4 模拟器

最终 Debug APK 上同一 AVD 连续定向执行配置回位 20 次，20/20 通过。每次使用同样的 41 个排序文件名和 20 次固定方向切页，测试目录用 UUID 隔离。定向复测后再运行全套，12/16 通过。配置回位、5,000 项未索引启动、收藏回位和视频预览通过。初轮同一用例中的视频事件注入曾失败，最终全套通过。

与阶段 1A 最终全套的 11/16 对比，仍复现同四项失败。配置回位在阶段 1A 全套中的失败此次未复现。

| 已知失败 | 最终全套表现 |
|---|---|
| searchFastScrollerThumbDragMovesHorizontalResultsOnDevice | 搜索横向快滚注入后 offset 为 0 |
| viewerPreservesLandscapePortraitSquareAndPanoramaRatiosOnDevice | 首绘前未找到 Viewer 状态层 |
| viewerStatusSurfaceUsesTheSelectedLightAndDarkPaletteOnDevice | 首绘前未找到深色 Viewer 状态层 |
| downwardDragReversesRestoresAndDismissesFromTheCurrentPhotoFrame | 模拟缺失返回路径后仍有挂起路径 |

## Pixel 8 Pro 修复前的单次采样

| 指标 | 修复前采样 | 修复后同机 |
|---|---|---|
| 扫描 worker | 32 启动，21 COMPLETE，11 CANCELLED。3,080 项 scope 为 11 启动，10 取消 | NOT MEASURED |
| 不变目录完整重复扫描次数 | NOT MEASURED | NOT MEASURED |
| COMPLETE worker p50/p95/p99 | 146/2,905/4,212 ms，n=21 | NOT MEASURED |
| 首次可见 Preview | 配置相册的阶段发布 1,944 ms，采样的 ready 2,003 ms；像素呈现 NOT MEASURED | NOT MEASURED |
| Adapter 提交 p50/p95/p99 | 8/53/8,801 ms，n=47，nearest rank | NOT MEASURED |
| Diff 计算 p50/p95/p99 | 6/674/789 ms，n=30，含取消或失败计算样本 | NOT MEASURED |
| 3,080 项链 | Diff 两次，789 ms 与 674 ms；8,801 ms 提交 | 受控测试只算一次，在安全布局后的虚拟 32 ms 内提交。真实耗时 NOT MEASURED |
| 缩略图恢复 | 新版本提交到首个可见 ready 样本 1,282 ms；视口全部恢复 NOT MEASURED | NOT MEASURED |
| 网格白块/细条 | 录屏在约 84–112 秒的多个观察点可见空格和文件名叠层。完整次数 NOT MEASURED | NOT MEASURED |
| 返回目标 | 30 次 return closed，均为 target_drawn。实际像素成功率 NOT MEASURED | NOT MEASURED |
| 内存 | NOT MEASURED | NOT MEASURED |

这些是一次诊断开启的观察样本。JVM 的虚拟帧与真机耗时分别记录。
