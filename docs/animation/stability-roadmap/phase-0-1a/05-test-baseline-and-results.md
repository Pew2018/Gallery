# 验证记录

## 阶段 0 冻结

输入为 `172f0af1f44d3c84b35c1ace32908b443a042a81`。使用 JDK 21、Gradle wrapper 9.5.1、SDK 36 与本地依赖缓存。

```sh
./gradlew --offline :app:testFossDebugUnitTest :app:compileFossDebugAndroidTestKotlin \
  :app:lintFossDebug :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --continue
```

| 校验 | 阶段 0 |
|---|---|
| 全量 FOSS Debug JVM | PASS，168 suites，1,198/1,198，通过 1,198，失败 0，跳过 0 |
| AndroidTest Kotlin 编译 | PASS |
| ARM64 FOSS Release assemble | PASS，本地 unsigned |
| lintFossDebug | FAIL，14 errors、802 warnings、2 hints；基线过滤 18 errors、413 warnings。合并命令因此退出失败 |
| R2.3 lintFossRelease | HISTORICAL，14 errors、834 warnings，和本轮 Debug 变体分列 |
| 原录屏设备与现场复验 | BLOCKED，未连接原设备；没有可核对的原始视频与日志 |

JVM 套件包含当前的 MediaSharedSurface、PhotoViewerInitialization、PhotoViewerReturnTarget、MediaTransitionGestureController、VideoViewerFrameLayer、ViewerDeletionNavigationPolicy、MediaAdapterStability、ViewerSwipePublicationGate、FolderMediaScan 及 Classic UI 测试。PhotoViewerInteractiveGesture 和 MediaPageStartup 位于 androidTest，另用 API 29 Pixel_4 模拟器执行。

阶段 0 的 JVM XML、lint XML 和构建日志已在本地另存，后续编译不会覆盖。源码和原有事件基线已冻结，真机性能证据状态见 [01](01-baseline-and-provenance.md)。

### 阶段 0 模拟器补充验证

```sh
./gradlew --offline :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.fossify.gallery.activities.PhotoViewerInteractiveGestureTest,org.fossify.gallery.activities.MediaPageStartupTest
```

只读 API 29 Pixel_4 AVD 执行 16 项，11 通过、5 失败、0 跳过。MediaPageStartupTest 1/1；PhotoViewerInteractiveGestureTest 10/15。基线失败如下。

- 横向搜索快滚拖拽无 offset。
- 照片比例用例无法在首绘前找到 Viewer 状态层。
- 浅深配色用例无法在首绘前找到深色状态层。
- 竖屏、宽屏视频封面比例断言失败。
- 下拖反转、恢复和退出用例失败。

测试不代表原录屏设备或系统合成器的帧时序。

## 阶段 1A

| 校验 | 结果 |
|---|---|
| 全量 FOSS Debug JVM | PASS，170 suites，1,218 通过，0 失败，0 跳过 |
| 本轮新增 | PASS，PhaseOneDiagnosticsTest 16/16，ViewerSourceContractTest 4/4，覆盖 D01–D20 |
| 原有关键回归 | PASS，1,198/1,198 JVM。运行原测试与断言 |
| AndroidTest Kotlin 编译 | PASS |
| lintFossDebug | FAIL，14 errors、802 warnings、2 hints。按严重度、规则 ID、文件及消息逐条多重集比较，增减均为 0 |
| 实机 APK、合成器帧和视觉验收 | BLOCKED，原设备及同源证据缺失 |

中间回归检查发现，发布参数改变了现有反射入口，且旧版无进程标记的 Viewer Intent 失去了旧诊断。最终保留原入口签名及旧事件，新增跨窗口关联需要匹配的进程标记。几何测试采用与现有几何套件一致的 Robolectric 空 Application 配置，避免初始化无关图片加载器。这些修正均在最终全量结果中验证。

本地 ARM64 FOSS Release assemble PASS。输出为 `gallery-32-foss-arm64-v8a-release-unsigned.apk`，包名 `org.fossify.gallery`，版本 1.14.1 (32)，ABI split 为 arm64-v8a。CI 签名产物按同提交的远程运行和 APK 字节单独核验。

最终全套模拟器测试为 11/16 通过、5 失败、0 跳过。MediaPageStartupTest 通过。原先失败的 video preview 比例用例通过；搜索快滚、两个 Viewer 状态层和下拖用例继续失败。另有自定义照片配置切页后的离屏缩略图回位超时，初次基线全套通过。基线和最终 APK 分别在同一模拟器按相同顺序执行启动与配置相册回位，均为 2/2 通过。回位超时在最终全套出现，在同机定向复验未复现，原因待实机复核。

MediaPageStartupTest 实际导出的三次启动，Activity 本地代际均为 1，新增请求键在同进程内分别为 1、3、5。每个请求各有一次创建、准入、worker 开始和 COMPLETE；部分发布早于终态。全部媒体的 INDEX_PREVIEW 早于 FINAL。首 drawable 标为 READY，可见候选标为 PRE_DRAW，均记录 `presented=NOT_OBSERVABLE`。

本轮全套模拟器样本的首 drawable / pre-draw / worker / 最后 Adapter 提交（毫秒）分别为：空 Room 3064 / 3192 / 7138 / 13338，有 Room 2138 / 2292 / 4789 / 8418，索引预览 765 / 790 / 1934 / 4022。阶段 0 补采和本组采样用不同测试顺序与宿主负载；两组是采证样本，性能验收需要重复、同条件测量。
