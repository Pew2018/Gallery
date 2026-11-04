# 08 — 最小精简重构验证记录

> 本文件先保留阶段一验证记录；第二阶段最终验证结果见文末，二者的测试数和提交 SHA 分别列出，不混算。

日期：2026-11-04（Asia/Taipei）  
仓库：Pew2018/Gallery  
唯一目标分支：`fix/photo-viewer-five-item-boundary`  
起始 HEAD：`27b488a88de9ed105c3ed6fdbe3931249dabbdb9`  
最终源代码 SHA：`5a9d4377038d6d27b3175f2569293ec205005d3f`。Actions Run `41684331467` 已成功，测试 786 项，失败/错误/跳过均为 0。

## 1. 验证范围

本轮代码变化是照片配置 cache preview 与公开文件系统候选批次的兩处冗余排序精简；扩展现有 `PhotoProfileMediaLoadingTest` 检查 cache preview 顺序及 `.nomedia` 渐进阶段/最终顺序。

以下结果均不能代替真机行为：构建成功不能证明 Android 8/11/14/16 文件权限矩阵、刷新动画/滚动锚点或全屏手势已通过。无设备 Trace 时，性能栏不填写时长和百分比。

## 2. 本地测试/构建

| 命令 | 结果 | 说明 |
|---|---|---|
| `./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.PhotoProfileMediaLoadingTest --stacktrace` | **未执行测试；wrapper 启动失败** | Wrapper 需要下载 Gradle 9.5.1，但运行环境对 `services.gradle.org` 网络不可达。失败发生于 Gradle 启动前，不是 Kotlin/测试失败。完整错误：`java.net.SocketException: Network is unreachable`。 |
| 完整 `./gradlew :app:testFossReleaseUnitTest --stacktrace --info` | 未在本地执行 | 本地 Gradle distribution 不可用；由指定分支 Actions 执行。 |
| `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --stacktrace` | 未在本地执行 | 由指定分支 Actions 执行；不能用本地状态推断 APK。 |

仓库已确认工作流 `.github/workflows/arm64-foss-release.yml` 对目标 fix 分支 push 和 `app/**` 变更有触发条件，且工作流执行完整 FOSS Release 单测与 ARM64 FOSS Release assemble。代码提交后实际 Run 状态如下。

## 3. GitHub Actions 与 APK

| 项目 | 实际值 |
|---|---|
| 最终源码提交 SHA | `5a9d4377038d6d27b3175f2569293ec205005d3f` |
| Actions Run ID | `41684331467`（Run #461） |
| Run URL | https://github.com/Pew2018/Gallery/actions/runs/41684331467 |
| 测试 job 状态 | 成功；完整 FOSS Release 单元测试步骤与 JUnit 汇总验证均成功 |
| 测试用例总数 / 失败 / 错误 / 跳过 | 786 / 0 / 0 / 0（来自工作流日志的 `Verified JUnit totals`） |
| ARM64 Release job 状态 | 成功；APK 校验步骤成功 |
| APK Artifact 名称 | `gallery-foss-arm64-v8a-release`（实际存在） |
| APK 下载入口 | [GitHub Actions Run 461](https://github.com/Pew2018/Gallery/actions/runs/41684331467) 的 Artifacts；artifact ID `13253618722`，[下载 ZIP](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13253618722/zip) |
| APK 对应 SHA / ABI split | `5a9d4377038d6d27b3175f2569293ec205005d3f`；工作流 ARM64-only assemble/校验步骤成功 |

若构建失败，应追加失败 step/log 的定位与修复提交；本表只填最终有证据的 Run。仍在执行中的 run 必须标为进行中，不得表述为成功。

## 4. 性能对照

| 指标 | 修改前 | 修改后 | 结论 |
|---|---|---|---|
| 公开 profile cache preview 的排序调用 | 源码事实：cache 全集排序、preview 有序截取/校验后再排序子集 | 源码修改：保留 preview 次序，跳过重复子集排序 | 每次 cache preview 少一次子集排序；没有测到耗时 |
| 公开 profile 每个 filesystem batch 的排序调用 | 源码事实：校验后排序 batch，随后再排序累计候选 | 源码修改：跳过 batch 临时排序，只排序累计发布快照 | 每个公开 profile batch 少一次临时排序；没有测到节省的耗时 |
| 排序耗时 / 全程时长 p50/p95 | 无同设备基线 | 未设备采样 | 不作数值结论 |
| Adapter commit/rebind/移动次数 | 未采集 | 未采集 | 不宣称动画/流畅度改善 |
| 刷新位移 / UI jank | 未采集 | 未采集 | 待真机录屏或 Perfetto |

测试中 cache preview 样例为 3 个文件；`.nomedia` 数据量为 260 个文件，用来触发多个候选批次并验证顺序；这些都是回归样例，不是性能基准。

## 5. A01–A08 验收台账

| ID | 当前结论 | 证据/未完成项 |
|---|---|---|
| A01 扫描正确 | 待设备验收 | 现有代码保留 `.nomedia` 磁盘读取和 COMPLETE 元数据提交；本次测试覆盖配置来源，未验证主页卡片数量设备同步。 |
| A02 刷新生命周期 | 自动化测试通过，设备竞态待测 | 完整 FOSS 单测 786/0/0/0；保留 request/terminal/cancel/watchdog；具体真机高频序列未测。 |
| A03 显示稳定 | 未验证 | 没有 Adapter observer、真机滚动锚点、动画录屏或 Perfetto。此次不改 UI 提交频率。 |
| A04 排序与身份 | 自动化回归通过，设备行为待验 | 新增 cache preview 与 `.nomedia` 多阶段顺序断言；完整套件 786/0/0/0。随机跨刷新和设备 Viewer 同序仍待实际场景测。 |
| A05 全屏浏览 | 既有自动化套件通过；真机未验 | Actions 的 786 项测试全部通过；普通/收藏/配置各方向 20 张、快速换向仍待真机。 |
| A06 授权失效 | 既有自动化套件通过；真机未验 | Actions 的 786 项测试全部通过；Android 权限撤销后的展示/预取需设备矩阵。 |
| A07 配置/备份兼容 | 自动化套件通过；实际升级/恢复未测 | FOSS 全量测试 786/0/0/0；本次未改数据格式，但旧安装升级及真实导入导出需设备/文件抽测。 |
| A08 性能对照 | 代码级差异确认，性能未量化 | 每次 cache preview 少一次子集排序，每个公开 profile filesystem batch 少一次临时排序；无耗时、帧数、Adapter 事件前后采样。 |

## 6. Android 设备矩阵

本环境未连接 Android 真机或模拟器。下列项目均为待真机验收：Android 8、Android 11、Android 14、Android 16；All Files Access、受限媒体权限、持久 SAF；普通/`.nomedia` 文件夹数量同步；文件增删刷新；快速滚动时位置与缩略图；普通/收藏/照片配置 Viewer 双向连续滑动及鉴权撤销。

## 7. 最终结果记录

最终结果：Run #461 完成且成功；测试 786 项、失败 0、错误 0、跳过 0；ARM64 Release APK artifact 存在并完成工作流校验；无构建修复提交。验证时分支 HEAD 为 `5a9d4377038d6d27b3175f2569293ec205005d3f`，本地跟踪工作区干净；后续仅验证记录的文档补录如生成新 SHA，以该提交记录为准。真机与性能指标仍为未测。

## 8. 第二阶段：扫描生命周期与渐进发布验证

日期：2026-11-04（UTC）。本阶段开始时重新核对的远程 fix HEAD 为 `a80f191987f0c2de978cf43ed289598092d529aa`；完成验证的代码 SHA 为 `0b70939655c738a2c8b6ed2c5de869cb735638c8`。

### Actions 与 APK

| 项目 | 实际结果 |
|---|---|
| Run | [#463 / 41751856059](https://github.com/Pew2018/Gallery/actions/runs/41751856059)，成功，提交 SHA `0b70939655c738a2c8b6ed2c5de869cb735638c8` |
| 完整单测 | `./gradlew :app:testFossReleaseUnitTest --stacktrace --info` 成功；789 tests、0 failures、0 errors、0 skipped |
| ARM64 assemble | `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a --stacktrace` 成功；APK 包名、签名和 ABI 校验成功 |
| APK Artifact | `gallery-foss-arm64-v8a-release`，artifact ID `13286112363`，[下载 ZIP](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13286112363/zip)；10,391,493 bytes |
| APK SHA-256 | `61e537cbe9276def67b0d574ebad3274f3a41d22f2bc7a848c93d778280579af`；构建日志打印源码 SHA `0b709396…`，native ABI 仅 `arm64-v8a` |
| 测试报告 Artifact | `backup-ui-test-reports`，ID `13286061937`，[下载 ZIP](https://api.github.com/repos/Pew2018/Gallery/actions/artifacts/13286061937/zip) |

### 首次 CI 失败与修正

Run #462 (`41750986403`, SHA `b84c11eb3f4fd773f0e4e7bce0c3db4541178ea9`) 在 `:app:compileFossReleaseKotlin` 阶段失败，测试未启动、未产生 APK。日志中的 `MainActivity.kt` 顶层语法错误由首次代码上传时大型源码文件被错误编码/截断造成，并非应用逻辑或测试断言失败。恢复完整 UTF-8 源码后，先比较本地 `git hash-object` 和 GitHub blob SHA（均为 `c722d612dd93ad6eb6778ed7af9f01a0d11ebe5c`），再以快进提交 `0b709396…` 修正并触发 Run #463；本阶段的最终成功结果以 #463 为准。该传输事故和失败 run 保留在记录中，不删除历史提交。

本地定向 Gradle 测试因 wrapper 下载 Gradle 9.5.1 时 `java.net.SocketException: Network is unreachable` 未能启动；上述完整 Actions 测试提供了本阶段有效自动化验证。

### 第二阶段验收更新

| ID | 第二阶段结论 | 证据与未完成项 |
|---|---|---|
| A01 扫描正确 | 自动化回归通过；设备数量同步待验 | 分类缓存失败后继续扫描的 `.nomedia` Activity 测试通过；主页卡片计数与真实设备媒体列表一致仍需真机验收。 |
| A02 刷新生命周期 | 代码审计和完整单测通过；竞态设备验收待测 | 789/0/0/0；保留 epoch、terminal gate、generation、取消、拒绝与 Watchdog 原机制；极端页面切换时序无真机 trace。 |
| A03 显示稳定 | 重复可见发布的代码路径已收敛；实际视觉效果待测 | 分类同内容快照和照片配置同可见 stage 不再进入 Activity/Viewer 发布入口；没有录屏、Adapter rebind 统计、Frame Timeline 或锚点像素数据，不宣称扑克牌动画已彻底修复。 |
| A04 排序与身份 | 自动化回归通过；设备网格/Viewer 同序待验 | Stage 2 只比较当前 Adapter 内容契约、scope 与权限摘要，未更改排序、去重、随机 rank 或 Viewer 交接；其 Stage 1 两处排序精简另由 Run #461 验证。 |
| A08 性能对照 | 仅确认代码级无效发布减少，未量化运行时性能 | 未测扫描次数、查询/文件枚举次数、Adapter commit/rebind、滚动位移或耗时；不报告性能百分比。 |

Android 8、11、14、16 真机/模拟器、SAF/分区存储权限撤销、快速滚动视觉稳定性、真实 `.nomedia` 新增文件计数和全屏手势仍待设备验收。


## 9. 第三阶段：显示与锚点稳定性验证

开始 SHA：`c9e2c693ddec0c9d430c7607d67c9f26345bd3c3`，分支 `fix/photo-viewer-five-item-boundary`。实施和测试范围见 [10-display-scroll-stability.md](10-display-scroll-stability.md)。

### 本地验证

| 命令 | 结果 |
|---|---|
| `./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.MediaAdapterStabilityTest --tests org.fossify.gallery.helpers.DirectoryAdapterRefreshTest --tests org.fossify.gallery.helpers.FavoritesScrollRestorerTest --tests org.fossify.gallery.helpers.GalleryRefreshRegressionTest --stacktrace` | Wrapper 下载 Gradle 9.5.1 时返回 `java.net.SocketException: Network is unreachable`；Gradle 未启动，测试未执行。 |
| `git diff --check` | 通过。 |
| UTF-8 与大文件结构核查 | MediaActivity、两个 Adapter 及修改 helper 均可按 UTF-8 读取；行数与括号/大括号配对正常，未发现大块删除。提交前 blob SHA 核验待记录。 |

### 新增可观测测试

- MediaAdapter `ManualExecutor` 队列用于断言技术字段变化不进入 Diff；AdapterDataObserver 记录零行操作；另验证真实修改/顺序变化仍进入 Diff。
- DirectoryAdapter 检查 id 变化零 Diff/通知、封面或数量变化仍进入 Diff。
- FavoritesScrollRestorer 验证滚动恢复被用户拖动打断，以及滚动中刷新不捕获/执行旧 anchor。
- 完整 FOSS Release 测试汇总、ARM64 Release 编译、签名/包名/ABI 检查、Artifact 与源 SHA：提交后从 Actions 回填。

真机/模拟器、录屏、Perfetto、实际 Adapter 绑定计数和像素锚点偏移均未采集。Actions 成功只能证明编译与自动化用例，不等于设备视觉验收。
