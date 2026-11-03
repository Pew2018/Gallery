# 04 — 下一阶段的证据采集、回归矩阵与迁移前置条件

状态：准备方案；**未执行构建、真机性能测量或业务重构**。基线：c5cc5b6852291491a999934d12f26314455959c6。

## 1. 已有测试资产（源码目录存在，不等于本轮已运行）

- 刷新与文件夹：DirectoryScanEpochTest、DirectoryMetadataRepositoryTest、GalleryRefreshRegressionTest、GalleryRefreshRecoveryTest、RefreshReliabilityTest、RefreshCompletionTest、FolderRefreshActivityTest、NoMediaCategoryRefreshTest、FolderMediaReadTest、FolderMediaScanTest。
- 列表稳定性：MediaAdapterStabilityTest、DirectoryAdapterRefreshTest、ViewerGridOrderPolicyTest、ViewerMediaUpdatePolicyTest。
- 照片配置：PhotoProfileMediaLoadingTest、PhotoProfileSourceScanPolicyTest、PhotoSessionViewerCatalogStoreTest、PhotoViewerCatalogCoordinatorTest、PhotoProfileReturnProbeTest。
- 全屏：PhotoViewerContinuousSwipeTest、PhotoViewerProgressivePagerTest、PhotoViewerPageAccessStateTest、ViewerSwipePublicationGateTest、MyPagerAdapterLifecycleTest、ViewerNeighborWindowTest。
- 安全/回退：FavoriteAccessPolicyTest、FolderUnlockSessionManagerTest、ViewerAccessCoordinatorTest、PhotoAccessValidationBatch 相关调用与安全回归。
- 备份：GalleryBackupZipCodecTest、PhotoProfileBackupCodecTest、OxygenSettingsBackupCodecTest、FavoriteBackupRestoreTest、BackupPerformanceTest 等。

测试文件目录：app/src/test/kotlin/org/fossify/gallery/。后续首先检查上述测试的断言是否覆盖**真实生命周期/竞态**，不是只验证纯函数。

## 2. 数据采集表：每组都要附设备、APK SHA 与权限

| 代号 | 操作场景 | 需记录的观测值 | 通过条件（逻辑） |
|---|---|---|---|
| T01 | 普通目录新复制 1 / 100 张后主页下拉 | index 查询数、目录数量、耗时、完结状态 | 数量一致、索引终止、不得遗漏 |
| T02 | .nomedia 目录新增、删除媒体后主页/目录/配置刷新 | 磁盘真实数、三个视图数、完整性与失败原因 | 具当前授权时结果一致 |
| T03 | 首次打开大量图库（5k/20k/50k/100k） | 首个稳定列表耗时、CPU、内存峰值、总读取数 | 不卡死、可中止、有可恢复失败 |
| T04 | 同一目录连续快速下拉 10 次、切底栏和返回 | 请求版本、取消数、排队数、终态数、loading 指示 | 旧请求不发布、最终请求有终态 |
| T05 | 长列表高速 fling 期间复制/删除/修改元数据 | topVisiblePath、offsetPx、Adapter 操作、缩略图重绑 | 未删除锚点不跳，未变化条目无大规模动画 |
| T06 | 搜索、排序、分组、修改缩略图列数、随机排序 | 排序快照、选中状态、滚动与查看器列表顺序 | 用户明确排序才改变顺序 |
| T07 | 收藏目录及媒体动态增删 | 关系表、数量、全屏返回状态、排序 | 不丢收藏关系、不泄漏非收藏内容 |
| T08 | 照片配置多来源（含 .nomedia + 收藏）、切配置 | 去重数、阶段数量、完整目录、数据更新 | 同一文件仅显示一次，旧 scope 不提交 |
| T09 | 全屏从任意位置连续前/后 20 张，边界等待中换向 | currentPath、catalogRevision、window、before/after、等待时长 | 五张预览不构成真实边界，无重复/跳片 |
| T10 | 浏览时撤销受保护目录/配置/收藏授权 | 访问事件、页面内容、Bitmap 请求、内存快照 | 立即停止不再有授权的展示与预加载 |
| T11 | SAF 授权变化、无 all-files、外置存储离线 | READ 结果状态、已保留缓存、error UI | 不把未读文件当成删除；正确提示 |
| T12 | 旧安装升级 / 全量导出导入 / 配置独立备份 | Config 键值、Favorites DB 关系、配置 ID/source | 用户数据逐条一致，格式仍可恢复 |
| T13 | 横屏/后台切前台/进程重建/页面返回 | pending publication、adapter state、currentPath | 不重复加载/回退空页/旧回调覆盖 |
| T14 | 快速拖动滚动条与缩略图加载取消 | scroll offsets、thumb 位置、load bind count | 滚动条同步、不卡顿、不出现大片闪动 |

## 3. 应有的诊断字段与采集格式

建议在真正重构前先由**现有工具**采集或增加仅供诊断的最小埋点（本轮不修改代码），每条事件至少关联：

- appVersion / APK commit SHA / Android SDK / 授权模式；
- requestId / scopeKey（匿名散列）/ stage / sourceType / datasetRevision / authRevision；
- scan start/end/cancel、cache hit/miss、MediaStore query 数、disk enumerate 数、partial/complete 及失败类型；
- directory count old/new、Adapter submit/commit/drop/rebase 数、stable-anchor old/new 与 offsetPx；
- Viewer selectedPath（仅设备本地敏感调试，上传报告须脱敏）、catalog count、boundary UNKNOWN/HAS_MORE/END、expand size、延时；
- CPU time、wall time、heap size、临时磁盘空间、UI jank frames。

GalleryPerfTrace.kt 已有 path-free 计数/耗时埋点；其他 FolderRefreshTrace 调用也存在于 MediaFetcher/MainActivity。不要在发布版日志输出真实用户路径、照片名、凭据、受保护内容。

**最小原始结果格式（后续填写，不要编造数字）：**

~~~csv
apk_sha,android_sdk,permission_mode,case_id,media_count,folder_count,request_id,stage,wall_ms,ui_jank_frames,adapter_commits,anchor_shift_px,scan_state,notes
~~~

统计口径：以多轮（建议每组合计至少 10 次冷/热启动与操作）报告 p50/p95；区分初次加载、增量刷新、全量重建、Viewer 首帧、边界扩展。阈值需基于现有应用基线和设备能力定，不在未经测量时给出“已达到 xx ms”。

## 4. 暂定迁移阶段和验收门

| 阶段 | 交付物 | 进入下一阶段的门槛 |
|---|---|---|
| S0 本轮 | 现状数据流、源码证据、兼容契约、用例矩阵写入 docs | 文档存在、分支/固定 SHA 可追溯；不改业务代码 |
| S1 观测基线 | 真机 trace + 现有单元测试和 CI 构建记录 + 数据量分布 | 对主要用户报告症状建立可复现/不可复现结论 |
| S2 状态/数据契约 | 新 MediaScope、ScanOutcome、目录 revision、Immutable MediaSnapshot、授权能力接口设计 | 单一版本/完整性/取消/权限不变量具自动化测试 |
| S3 新读取层 | MediaStore + disk + SAF .nomedia 统一只读查询，对旧引擎进行 shadow compare | 普通目录/分类/配置数量与顺序对照，受限权限安全 |
| S4 发布调度层 | 单请求所有者、coalescing、epoch、唯一 terminal、不可过期提交；兼容 Activity/Adapter | 高频刷新测试中无挂起/过期/无限指示 |
| S5 页面迁移 | 主页 -> 普通目录 -> 收藏 -> 照片配置逐个受控替换 | 每个场景与旧实现对照通过才扩大 |
| S6 Viewer 接入 | 使用当前列表同源 revision / path index 与权限验证，继承 fix 渐进加载 | 连续左右翻页、目录升级、切换方向/授权吊销测试通过 |
| S7 稳定与清理 | 设备矩阵 + 数据迁移/备份回归 + 性能对比，最后删除无调用旧路径 | 证据可审计，发布失败可切回旧引擎 |

**回滚建议**：先在 fix 分支内部以实现开关切换“旧引擎 / V2”，不变更用户数据库/备份格式；直到每个场景稳定后再移除旧路径。开关方案是建议，尚未实现。

## 5. 工作量与风险

上一轮粗估为**43–78 人日**，属于架构级工程规划而不是实测数据；当前 fix 分支新增加的部分/分段 Viewer 降低重复实现必要性，但也增加兼容测试面。要在 S1 采集后重新评估，不能把估算当交付承诺。

## 6. 本轮完成 / 未完成边界

已完成：以当前 fix SHA 采集主要源码流、与 main 的分支差异、用户功能边界、已有测试资产、证据和待测问题清单；文档放置于 docs/media-engine-v2。

未完成：实际运行 CI、连接真机、执行性能采样、修复任何 Kotlin 问题、删除旧代码或数据库迁移。以上须另行阶段实施。
