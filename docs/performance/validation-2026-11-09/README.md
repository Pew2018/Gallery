# 验证证据索引

- `full-summary.json`：最终交付代码完整测试总数、失败/错误/跳过数、环境、分支和命令。
- `full-final.log`：最终完整 FOSS Release 单测日志。
- `targeted-summary.json` / `targeted-final.log`：主流程针对性测试 61 项、0 失败。
- `catalog-targeted.log`：目录集变更、适配器、搜索、收藏与日期专项 36 项通过。
- `favorite-termination-targeted.log`：收藏范围变化时终止加载的专项。
- `date-bubble-targeted.log`：最终日期气泡极值回归；后续已重新执行完整套件。
- `Home*Test.json` 等：最终套件中用例名称、准确计数及测量 stdout。
- `baseline-comparison.txt` / `baseline-comparison-run.log`：起始 HEAD 的同数据集对话框测量及成功运行日志。
- `optimized-comparison.txt`：最终代码对应主机测量，保留原始精度。
- `first-full-run.log`：首次完整运行 1081 项、15 失败的历史记录；不以该结果冒充最终通过。
- `baseline-existing-failures.log`：基线也存在的异步/主机路径测试失败证据。
- `changed-files.txt`：本次改动清单，排除了开始时已有的未跟踪 `buildSrc/build/`。

## 复现命令

项目目录内，JDK/SDK 使用本机现有缓存：

```sh
ANDROID_HOME=/Users/patrick/Library/Android/sdk \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testFossReleaseUnitTest --offline
```

主要针对性测试（在完整套件之前运行）：

```sh
./gradlew :app:testFossReleaseUnitTest --offline \
  --tests '*HomeDirectoryStatsRegressionTest' \
  --tests '*DirectoryMetadataRepositoryTest' \
  --tests '*HomeSortingWorkRegressionTest' \
  --tests '*HomeSortingComparisonBenchmarkTest' \
  --tests '*AlbumSearchLifecycleTest' \
  --tests '*DirectoryStatsMigrationTest' \
  --tests '*FolderMediaReadTest' \
  --tests '*FolderRefreshActivityTest' \
  --tests '*FavoriteFolderProgressiveActivityTest'
```

基线是在 `/tmp/gallery-perf-baseline-6246f6b15` 解包 `git archive 6246f6b156184035c19046225b8d6e2c50efac12` 后，只复制同一个 `HomeSortingComparisonBenchmarkTest.kt` 进去运行。没有在原工作区 checkout 旧版本。

## 计数口径

Comparison 的 `counters` / `delta` 列依次为：

1. fixture provider 的查询次数；此数据集索引行数为 0。
2. `MediaFetcher.readFolder` 中目录枚举次数。
3. 文件元数据读取次数。
4. `MediaFetcher.sortMedia` 调用次数。
5. 上述排序输入媒体总数（不含旧 `sortValue.sortedBy` 的额外排序）。
6. Room SELECT 语句数。
7. Room INSERT/UPDATE/DELETE 语句数（不等于实际受影响行数）。

Work 测试的六列依次为目录枚举、文件元数据、provider 查询、目录缓存读取调用、Room SELECT、Room 修改语句。它额外断言整个 scanner 诊断快照在热缓存/快速切换期间不变。

Work 测试先故意阻塞日期查询，验证选择变化不退役有效扫描；`firstUsableMs/fullStatsMs` 在释放阻塞后计时。其 scanner 字典内的 query duration 包含人工阻塞，不能当成设备查询耗时。Comparison 的 `fullStatsMs` 包含约 100 ms 排空异步回调的测试窗口。单相册更改的提交包含模拟 Looper 推进，仍只表示主机观测。

所有耗时都是主机单次观测，不是统计置信区间，不推导真机百分比。真实设备、外部存储/授权边界和完整分阶段设备性能状态均在主工程记录明确标为未验证。
