# 测试与性能对照

## 历史真机与可控重现

| 指标 | 1A Pixel 8 Pro | 1B Pixel 8 Pro（任务说明） | 1B-R |
|---|---|---|---|
| 3,080 项来源 worker | 同 scope 11 次启动，10 次取消 | 2 次实际启动，其中第二次为显式刷新 | Pixel NOT MEASURED。API 37 的 50 次完成往返仍使用同一来源请求 |
| Viewer 独立读取 | NOT MEASURED | 5 次 miss。约 10,308 次是说明中的估数 | Pixel NOT MEASURED。API 37 的 50 个稳定打开没有启动 Viewer worker |
| 1,454→3,080 Adapter 真实提交链 | 8,801 ms，789 ms / 674 ms 两次 Diff | 未复现真实内容变化。3,080 项 same_content / NO_OP 约 12 ms，来源约 3,026 ms | Pixel NOT MEASURED。JVM SIMULATED，一次 Diff，一次提交，安全布局恢复后 32 ms 虚拟窗口内提交 |
| 持续空白 / 尺寸 | 录屏历史记录约 84–112 s 多次占位 | 任务说明视频约 8–13 s，62 个可见 drawable 样本，部分 670×670 | Pixel NOT MEASURED。API 29 合成素材测试见下 |
| 内存和 Bitmap 峰值 | NOT MEASURED | NOT MEASURED | NOT MEASURED |
| 首张实际屏幕呈现 / 回程单帧 | NOT OBSERVABLE | NOT OBSERVABLE | NOT OBSERVABLE |

## API 37 普通文件夹，3,080 张合成 JPEG

使用 ARM64 模拟器、所有文件访问权限、FOSS Debug。1B 使用隔离源码归档中的生产代码及定向基线用例。1B-R 定向用例进行 50 次稳定往返、从第一张连续向后 50 项、首尾边界及逐轮回位。第 51 次先走同一快照，随后在 Viewer 打开的合成目录中写入一张、删除一张，观察到一次新的权威任务和成员变化后的完整目录。

| 指标 | 1B 前 5 次 | 1B-R 稳定 50 次 |
|---|---:|---:|
| Grid request | 1，往返不重新发起 | 1，往返不重新发起 |
| Grid 独立枚举 | 1 | 来源后续无重扫，初始枚举计数 NOT MEASURED |
| Viewer 独立 worker | 5 次实际 COMPLETE，0 取消 | 0 |
| Viewer 独立目录枚举 | 5 | 0 |
| Viewer 媒体元数据读取 | 15,400 次，覆盖同一批 3,080 个文件 | 0 次 scanner 元数据读取，仍执行逐路径授权和存在性校验 |
| 重复读取 | 仅在 Viewer 间重复计 12,320 次。相对含 Grid 的首轮读取，Viewer 重复 15,400 次 | 0 |
| Viewer 完整目录 | 0 命中，5 miss | 50 命中，0 miss |
| 目标路径回位 | 5/5 | 50/50 |

对照数据按真实 worker summary 和专用 scan fallback 事件归因。1B 的 VIEWER 曾被序列化为 UNKNOWN。1B-R 再次命中的累计第 51 次也没有扫描，真正变更后才启动一次，完整回退耗时约 3,537 ms。权限校验期间的文件系统探测没有算入 scanner metadataReadCount。

| 性能事件（毫秒，p50 / p95 / p99） | 1B | 1B-R |
|---|---|---|
| 首个选中 pager 交付 | 90 / 127 / 127，n=5 | 77 / 194 / 962，n=51 |
| 邻近 pager 就绪 | 495 / 570 / 570，n=2 | 650 / 1,009 / 2,037，n=51 |
| 完整目录准备 | 3,475 / 3,617 / 3,617，n=5 | 2,052 / 3,880 / 4,645，n=51 |
| 真实用户滑动等待及首帧 | NOT MEASURED | NOT MEASURED。连续 50 项与首尾行为已断言 |

每列用 nearest rank。前后样本量、主机并行负载和记录配置不同。该组足以核对 I/O 次数。尾部时间需要在真机复测。

## API 29 稳定列表快滚对照

同一台 ARM64 Pixel 4 AVD，前后均使用 FOSS Debug、相同测试源码与新的随机目录。两轮先后运行，主机有后台 JVM 工作。每种规模跳到五个相距足够远的新窗口，再反向返回五次。定时从请求跳转到窗口中所有 ImageView 绑定正确的媒体 token，并通过两种合成图颜色和实际尺寸检查。表内每格五样本，毫秒，nearest rank p50 / p95 / p99。

| 媒体数 | 1B 冷 | 1B 热 | 1B-R 冷 | 1B-R 热 |
|---:|---:|---:|---:|---:|
| 1,500 | 168 / 305 / 305 | 99 / 124 / 124 | 169 / 640 / 640 | 121 / 288 / 288 |
| 3,080 | 239 / 280 / 280 | 96 / 161 / 161 | 218 / 431 / 431 | 128 / 164 / 164 |
| 5,000 | 147 / 207 / 207 | 83 / 120 / 120 | 148 / 167 / 167 | 108 / 122 / 122 |
| 10,023 | 204 / 255 / 255 | 131 / 151 / 151 | 142 / 186 / 186 | 88 / 105 / 105 |

两版各 40 个视口均在 3 秒内达到阈值，观察到的错图和超时均为 0。3,080 项冷恢复中位数下降，尾部及热恢复中位数仍慢于这轮 1B 对照。首批运行也两版通过，修复版当时并发资源占用较高，未用来判断趋势。测试用跳转和反向回跳，Android 手指拖动、惯性滑动和 Pixel 8 Pro 大容量图像解码仍需真机验证。冷热由测试的路径历史控制，实际缓存层命中率、排队、取消原因总量、同键重复加载总量、单缩略图 p50/p95/p99 和持续白块面积均为 NOT MEASURED。真实屏幕合成为 NOT OBSERVABLE。

## 复测命令

JDK 21，使用已有 FOSS 变体。命令中的测试设备序列号按本机选择。

```sh
./gradlew --offline :app:testFossDebugUnitTest
./gradlew --offline :app:assembleFossDebug :app:assembleFossDebugAndroidTest
./gradlew --offline :app:lintFossDebug
./gradlew --offline :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a
adb -s emulator-5554 shell am instrument -w -r org.fossify.gallery.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class org.fossify.gallery.activities.ThumbnailFastScrollTest org.fossify.gallery.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 shell am instrument -w -e class org.fossify.gallery.activities.FolderCatalogReuseTest org.fossify.gallery.debug.test/androidx.test.runner.AndroidJUnitRunner
```

最终 JVM 测试为 173 suites、1,233/1,233 通过，0 失败，0 跳过。Debug 和 AndroidTest APK 构建成功。Lint 对比按规则、级别、仓库相对文件、消息及出现次数核验，新增 0、移除 0。两版均有 14 errors、802 warnings 和 2 hints，现有错误仍使 lint 命令返回非零。

本地 ARM64 FOSS Release 构建成功。Manifest 为 `org.fossify.gallery`、1.14.1 (32)、minSdk 26、targetSdk 36。仅打包 ARM64。本地构建未使用签名密钥，带签名的 Release 交付由同一分支的 Actions 完成。

### API 29 全套

18 项中 11 通过、6 失败、1 因 API 版本跳过。跳过的完整目录用例已在 API 37 单独通过。API 29 全套中的稳定列表快滚、配置相册当前媒体回位、真实文件排序与收藏回位通过。

| 既有失败 | 本轮结果 |
|---|---|
| 横向搜索 FastScroller | 中段拖动后 offset=0 |
| Viewer 图片比例 | 首绘前找不到 Viewer 状态层 |
| Viewer 深浅状态层 | 深色 fixture 首绘前找不到状态层 |
| 下拖恢复与退出 | 删除目标后回位路径仍挂起。第一次较高负载运行另有手势检查点超时 |

另两项失败是全部媒体启动中 INDEX_PREVIEW 的首提交时序，以及视频比例测试中源 Grid 未显示视频 fixture。第一次全套还在后续手势用例中被触摸注入线程异常中断。保留这些结果，并对两项额外失败分别做基线与修复对照。

对照复测中，阶段 1B 原始生产代码也触发相同的视频空网格断言。MediaPageStartupTest 在两版的定向运行均通过。全套中的启动时序失败仍保留为开放风险。
