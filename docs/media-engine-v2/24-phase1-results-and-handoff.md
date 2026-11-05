# 24 — Phase 1 结果、Actions/ARM64 交接与设备验证矩阵

日期：2026-11-05。仓库：`Pew2018/Gallery`；唯一工作分支：`refactor/media-pipeline-stability`。
审计起始远程 HEAD：`5cc20adf6875ca220df8db4ff3bb24e42f82de22`。

## 1. 本阶段代码与 CI 的可追踪边界

本阶段源码初次集中提交，随后针对明确的测试编译错误、零值诊断契约及超时终态进行必要补修。**最终受 CI 验证的应用源码 SHA 是 `5b75cb2a5c7a96021f35013292a5479436f87f36`**；本页另经纯文档提交补写实际 Actions/Artifact 记录，因此文档提交 SHA 可能更新，而不表示应用源码又发生变化。所有构建数据来自下述真实成功运行，不预先填造。

- 审计基线：`5cc20adf6875ca220df8db4ff3bb24e42f82de22`。
- 阶段 1 最终受测源码 SHA：`5b75cb2a5c7a96021f35013292a5479436f87f36`。文档收尾提交 SHA 请参阅本文件 git history。
- Actions 完整单测：**已成功：Run 41961735980（见第 6 节）**（参照与该提交对应的 `Arm64 FOSS Release APK (non-publishing)` run）。
- ARM64 APK：**成功**，Artifact `gallery-foss-arm64-v8a-release`（ID `13385912998`），已实际下载核验；详见第 6 节。
- APK 校验结果：CI 已验证包名 `org.fossify.gallery`、原生 ABI `arm64-v8a`、v2 临时测试签名；实际证书和版本见第 6 节。
- 设备：**PENDING_DEVICE**，未获得第 0 阶段真实设备 B01–B20 数据，故无同设备性能基线、jank/首帧画面证据。

**证据链的阅读顺序：** [22 — 审计与根因](22-phase1-lifecycle-audit.md) → [23 — 实施/测试](23-phase1-implementation-and-tests.md) → 当前提交 Git tree → 本提交对应的 GitHub Actions Workflow Run 和 Artifact。不能用原阶段 0 的成功 Run 证明阶段 1 已通过。

## 2. 本次实施范围与改动原则

1. 诊断：恢复 `-1` 未测哨兵；区分计数与持续时间；合法累加与无效负数隔离；取消/过期/执行拒绝确保尽可能有正确的一次性终态。
2. HOME/CATEGORIZED：在已有 `DirectoryScanEpoch`／version／terminal gate 旁维护只负责诊断收尾的引用；页面退场、旧 UI callback 过期不把旧数据重新发布。
3. FOLDER/FAVORITES：执行器拒绝时记录 FAILED business terminal 与诊断，再沿用原 UI 失败/重试路径；避免后台读取 UI 所拥有的可变旧媒体集合；完整性剪枝规则保持原样。
4. 保留：`FolderFileAccess`、`MediaFetcher`、`.nomedia` 直接读取、`DirectoryMetadataRepository`、受保护会话、PhotoProfile 源/阶段控制、Viewer 路径身份、adapter stable ID/Diff、数据库 Schema、排序、线程池参数及 OxygenOS Classic 样式。
5. 增补回归：扩展诊断负值/终态测试、请求代际/超时回调顺序、受限线程池拒绝/重试、部分来源状态断言；不删除历史测试。

**未宣称修复的部分：** 所有设备条件下的无穷 loading、所有连续刷新竞态、真实 SAF 撤权、后台恢复、删除/移动与数据库事务交错、照片配置切换和全屏 catalog 最终一致性。对没有稳定复现的新根因不进行大面积重构。

## 3. 真机验证矩阵（当前状态均为 PENDING_DEVICE）

| ID | 环境／场景 | 最低真实验收要求 | 状态 |
|---|---|---|---|
| D01 | Android 8、普通文件夹和 100/101/201 张 `.nomedia` | 新增 1 与 100，首页 count/cover、内部列表和 Viewer 一致；正式终态 COMPLETE 或可信 PARTIAL | PENDING_DEVICE |
| D02 | Android 11/14/16；All Files、Scoped Storage、SAF | 授权有效时新增/删除/移动可见；撤权只提示 ACCESS_REQUIRED/FAILED，不清可信收藏关系 | PENDING_DEVICE |
| D03 | 同一 HOME scope 连续 10 次下拉、切分类、Activity 返回 | 仅当前请求发布，旧响应不恢复 loading 或卡片，诊断终态可追踪 | PENDING_DEVICE |
| D04 | 目录初始 100 项，删除 1、移动 10、恢复回收站 | 原路径不复活；当前列表/计数/全屏最终一致，扫描取消不覆盖新操作 | PENDING_DEVICE |
| D05 | 单/多/受保护混合 PhotoProfile，含 `.nomedia` 和收藏 | 公开媒体保留、私有来源未授权不显示，切配置后无旧图和旧 Viewer catalog | PENDING_DEVICE |
| D06 | 视频时长异步补全、后台/旋转/退出、低内存 | 失效后不修改旧 adapter，失败可安全重试，不产生永远 loading | PENDING_DEVICE |
| D07 | 5k/30k/100k，冷启动/热启动/刷新、全屏前后各 20 张 | 同 APK/授权/数据/设备 10 次 p50/p95，帧时间与 AdapterObserver 交叉验证 | PENDING_DEVICE |
| D08 | 现有不同签名 APK 覆盖安装 | 比对 `apksigner --print-certs` 证书，不一致不得试图覆盖升级而造成数据风险 | PENDING_DEVICE |

## 4. 第 0 阶段数据与第 2 阶段准入

Phase 0 的历史独立成功证据：[Run 41951185632](https://github.com/Pew2018/Gallery/actions/runs/41951185632)，`eeddba4b68779a0809c24ede5daaff613aed9426`，856 tests、ARM64 Artifact `gallery-foss-arm64-v8a-release` ID `13380139965`。**这不是阶段 1 成绩。**

尚未收到基于统一真实设备的 B01–B20 采集 TXT、帧/图片首绘完成时刻、SQL `EXPLAIN QUERY PLAN`、设备 IO/GC/内存分布；因此目前仅有以下**源码候选热点**，没有有说服力的具体优化幅度：
- `MainActivity.gotDirectories` 逐目录读取、元数据提交与渐进卡片发布；
- `MediaFetcher` 共享 MediaStore 游标、文件系统/SAF 读取与 `sortMedia`；
- `PhotoProfileMediaLoader` 多源缓存/索引/补扫/累计排序；
- `MediaAdapter` 和 Viewer 的 Diff/分段目录与图片加载交接。

进入阶段 2 的条件：先以**已通过的阶段 1 CI 和无新增已确认 P0**作为代码门槛，再采集至少对应热点的真实同设备观察、可复现 query/IO/排序/Room 数据与阶段 1 权限/完整性回归；涉及 Room Index 必须追加明确 migration 及 `EXPLAIN` 证明。自动化正确性不替代 `PENDING_DEVICE` 矩阵。

## 5. 安全与发布说明

本次 CI 确认使用 `temporary-CI-not-upgrade-compatible` 临时测试证书，构建成功的 APK 不能保证覆盖安装不同签名的现有 Gallery；不应为测试要求用户清除应用数据。实际成功 Run 和下载核验见第 6 节。

## 6. 最终 GitHub Actions 与 APK 交付核验（2026-11-05）

**CI 验证的最终应用源码 SHA：** `5b75cb2a5c7a96021f35013292a5479436f87f36`。本节所列真实构建结果覆盖并更新上文最初建档时的 PENDING_CI 占位信息；以下文档收尾提交仅修改 docs，不能在文档自身填写其最终 SHA，也不改变已经过 CI 验证的应用源代码。

- GitHub Actions：[**Run 41961735980**](https://github.com/Pew2018/Gallery/actions/runs/41961735980)，触发提交 `5b75cb2a5c7a96021f35013292a5479436f87f36`，最终 `completed / success`。
- 完整命令 `./gradlew :app:testFossReleaseUnitTest --stacktrace --info`：CI 实测 **863 tests、0 failures、0 errors、0 skipped**。与阶段 0 的 856 tests 分开记录。
- 命令 `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --stacktrace`：成功；校验包名 `org.fossify.gallery`、版本名 `1.14.1`、versionCode `32`、原生库 **仅 arm64-v8a**。
- `apksigner verify --verbose --print-certs`：APK Signature Scheme **v2 = true**；使用测试证书 `CN=Gallery Temporary CI Test Signature, O=Local Testing`，证书 SHA-256 `8769216c723427b2b6754867d0e1dd7c757a706b1a696769a2d2ae4e6f5a28b4`；模式 `temporary-CI-not-upgrade-compatible`，不保证能覆盖安装不同签名的已装应用。
- **实际存在且已通过 GitHub 下载核验的 ARM64 Artifact**：`gallery-foss-arm64-v8a-release`，Artifact ID `13385912998`，ZIP SHA-256 `f39709503637709a1fcae8dddbaae73f318d4f43cbc23240f3a91b16a46894e3`。内部 APK `gallery-foss-arm64-v8a-release.apk` SHA-256 `a9ca687234ee8d40528130b27cc22a0630c02d830da2e254b81002c18ef8e0c2`，与 CI SHA-256 日志相同。
- 失败与补修证据：首次 [Run 41960357994](https://github.com/Pew2018/Gallery/actions/runs/41960357994) 于 `DirectoryScanEpochTest.kt:47` 新测试 `invoke` 的 Kotlin 编译报错，随后修正。中间新推送使旧 Run 被并发策略取消。最终成功 Run 的 863 项测试不能被写成“所有新用例都有修复前运行失败的红绿实验”；对未进行前后失败实测的行为，仅有静态根因、可控竞态断言和修复后通过证据。

**剩余状态：** D01–D08（Android 8/11/14/16 的普通及 `.nomedia` 增删、SAF 授权变更、删除/移动/回收站、混合解锁 PhotoProfile、Viewer 和大媒体库帧性能）均为 `PENDING_DEVICE`；第 0 阶段 B01–B20 真实设备采样尚未取得，不能依据 CI 断言“所有 P0 已在真机消失”或开始阶段 2 的 SQL/排序性能优化。
