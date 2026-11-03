# 08 — 最小精简重构验证记录

日期：2026-11-04（Asia/Taipei）  
仓库：Pew2018/Gallery  
唯一目标分支：`fix/photo-viewer-five-item-boundary`  
起始 HEAD：`27b488a88de9ed105c3ed6fdbe3931249dabbdb9`  
提交 SHA、Actions Run ID/URL、测试统计、APK artifact：完成提交并读取 GitHub Actions 后填写；未获取到的值必须标记未获取。

## 1. 验证范围

本轮代码变化是照片配置公开来源文件系统批次的一次冗余排序精简，测试扩展现有 `PhotoProfileMediaLoadingTest`，并检查 `.nomedia` 直接文件读取的渐进阶段和最终顺序。

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
| 提交 SHA | 待记录 |
| Actions Run ID | 待记录 |
| Run URL | 待记录 |
| 测试 job 状态 | 待记录 |
| 测试用例总数 / 失败 / 错误 / 跳过 | 待从 JUnit summary 或报告中读取 |
| ARM64 Release job 状态 | 待记录 |
| APK Artifact 名称 | 预期 `gallery-foss-arm64-v8a-release`，待实际确认 |
| APK 下载入口 | 待实际确认 artifact 存在后填写 |
| APK 对应 SHA / ABI split | 待实际确认 |

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
| A02 刷新生命周期 | 部分静态审计，整体待验证 | 有 epoch/request、terminal、取消与 watchdog；高频刷新/切页/队列拒绝的本次测试结果待 Actions。 |
| A03 显示稳定 | 未验证 | 没有 Adapter observer、真机滚动锚点、动画录屏或 Perfetto。此次不改 UI 提交频率。 |
| A04 排序与身份 | 有限测试待 Actions | 新增配置 `.nomedia` 多阶段顺序断言；跨目录去重、收藏变化、随机 order 与 Viewer 完整一致仍待完整测试/设备。 |
| A05 全屏浏览 | 未验证 | 未运行测试前不得记通过；普通/收藏/配置各方向 20 张和快速换向需 Actions 与真机。 |
| A06 授权失效 | 未验证 | 保留现有校验路径；撤权即时停止展示/预取需要权限矩阵及设备测。 |
| A07 配置/备份兼容 | 未验证 | 本次未改格式/数据层；FOSS 全量测试及旧安装/导入导出抽测待实际结果。 |
| A08 性能对照 | 代码级差异确认，性能未量化 | 每个公开 profile 文件系统批次减少一次临时排序；无时长、帧数、Adapter 事件的前后采样。 |

## 6. Android 设备矩阵

本环境未连接 Android 真机或模拟器。下列项目均为待真机验收：Android 8、Android 11、Android 14、Android 16；All Files Access、受限媒体权限、持久 SAF；普通/`.nomedia` 文件夹数量同步；文件增删刷新；快速滚动时位置与缩略图；普通/收藏/照片配置 Viewer 双向连续滑动及鉴权撤销。

## 7. 最终结果记录

在 Actions 完成后补录：状态、完整 SHA、测试统计、artifact 真实性和下载链接、失败修复（如有）、最终分支 HEAD、工作区是否干净、未完成验收项。没有采样的数据继续标记未知，不按预期结果填写。
