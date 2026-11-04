# Gallery Media Engine V2 — 代码调研资料索引

> 状态：**阶段一至三均已通过完整 FOSS Release 单测及 ARM64 APK Actions；第三阶段为 796 项测试，真机体验尚未验证**
> 日期：2026-11-04  
> 目标仓库：Pew2018/Gallery  
> 唯一目标分支：fix/photo-viewer-five-item-boundary  
> 第三阶段起始 HEAD：`c9e2c693ddec0c9d430c7607d67c9f26345bd3c3`；验证源码 SHA：`a8c1f45480179b030ccb248ba24ebc2098c98109`
> 对照 main 基线：44b31846fcb8b817d27ff5fef05829601260b3f5  
> 分支关系：本轮开始时 fix 相对 main 的差异见 Git；起始 HEAD 与 task marker 一致。
> 阶段一 SHA `5a9d437…` / Run #461 786 项；阶段二 SHA `0b709396…` / Run #463 789 项；阶段三 SHA `a8c1f454…` / Run #464 796 项。各自均生成 ARM64 APK。无 schema、配置格式或发布密钥改动。

## 文件导航

1. [01-current-dataflow.md](01-current-dataflow.md)：页面入口、媒体扫描、索引与缓存、目录元数据、刷新任务、适配器提交和全屏交接的现状数据流。
2. [02-evidence-and-risk-register.md](02-evidence-and-risk-register.md)：带固定 SHA 和代码行范围的证据编号、当前已有的防护、结构性风险、未证实问题。
3. [03-compatibility-contracts.md](03-compatibility-contracts.md)：不得损坏的顶栏、底栏、照片配置、收藏、保护授权、导入导出和旧版升级行为。
4. [04-validation-and-migration-readiness.md](04-validation-and-migration-readiness.md)：原始全面重构调研的测试清单及候选迁移方案（**归档为参考，不代表当前执行范围**）。
5. **[05-minimal-scan-sort-display-refactor.md](05-minimal-scan-sort-display-refactor.md)：当前生效的小规模重构目标、范围、非目标、验收条件和执行阶段。**
6. **[06-progress-and-change-log.md](06-progress-and-change-log.md)：实时任务状态和每轮目标/证据/修改/测试/遗留事项。**
7. [07-minimal-refactor-implementation.md](07-minimal-refactor-implementation.md)：当前分支审计、证据分类、实施取舍与实际代码修改。
8. [08-minimal-refactor-verification.md](08-minimal-refactor-verification.md)：自动化验证、Actions、APK、性能基线与未完成验收项。
9. **[09-refresh-publication-stability.md](09-refresh-publication-stability.md)：第二阶段扫描生命周期、分类目录进度和照片配置列表发布的审计、修复与验证记录。**
10. **[10-display-scroll-stability.md](10-display-scroll-stability.md)：第三阶段显示内容 Diff、Adapter 提交、目录相等性和滚动锚点的审计与验证。****

11. [11-favorites-viewer-sorting-fixes.md](11-favorites-viewer-sorting-fixes.md)：四项集中修复的代码证据、测试与交付边界。

## 审计口径与重要限制

- **F = 源码事实**：已在目标 fix 分支对应文件和行范围看到代码。仅证明代码存在，不代表真机行为一定正确。
- **I = 工程推断**：由多个事实推导出的耦合/竞态/性能风险；必须通过复现、日志或专项测试确认，不能直接定性为“已证实根因”。
- **U = 未验证**：缺少真机操作、Trace、权限矩阵、性能统计、基准测试或编译结果。
- 本期资料依据 GitHub 远程源码与分支比较，不是本地完整编译、设备端 Profiling、ADB 日志或运行时调用图；文件行号固定于上述 commit，后续新提交会漂移。
- 用户反馈的索引不结束、刷新列表闪烁、普通目录和 .nomedia 目录新增文件、全屏滑动边界等属于**用户报告症状**，不能仅凭这些描述确定当前 fix HEAD 一定仍复现。应重新执行场景矩阵。
- 本文档只为下一步方案设计提供资料，不授权立即删除旧实现、改写数据结构、改数据库、合并到 main。
- 尤其注意：当前 fix 分支新增渐进式全屏目录、分段媒体目录和滑动边界处理（相对 main 共 16 个提交），这些是必须评估的在研成果，不允许根据旧 main 的结论覆盖掉。

## 当前已确认的架构特征（摘录）

- **F**：MainActivity 通过 DirectoryScanEpoch / GalleryScanTerminalGate / CachedDirectories / GalleryAlbumScanExecutor 调度主页扫描；分类页走独立 CategorizedFolderScanner。
- **F**：MediaActivity 独立管理媒体读取请求、缓存预览、AsyncTask、照片配置分阶段发布和目录元数据提交。
- **F**：MediaFetcher 同时包含 MediaStore 查询、实际目录枚举、.nomedia 处理、分批候选、排序/分组等职责；FolderFileAccess 可按不同访问能力从直接文件系统或持久化 SAF 树枚举。
- **F**：DirectoryAdapter 和 MediaAdapter 各自实现稳定 ID、后台 DiffUtil、滚动时延期提交、滚动锚点；它们并未完全共用一个提交状态机。
- **F**：ViewPagerActivity 有单独的邻近窗口、发布门控、权限检查、照片配置目录扩展、完整目录与边界协调流程。
- **F**：旧有 GalleryDatabase v11、收藏关系、PhotoProfileRepository 配置及备份快照系统可作为外部兼容边界保留。
- **I**：当前可能最值得收敛的是“谁拥有媒体目录版本、谁能发布状态、局部扫描如何影响相册数量、谁能宣称 END/COMPLETE”，而不是从头重写 UI。
- **U**：目前尚无当前 SHA 的真实扫描时长、列表跳动次数、丢帧率、设备内存峰值、媒体规模分布和全屏首帧/边界响应基准。

## 当前交付边界

**历史调研（01–04）只负责收集证据，不构成重构指令。** 当前实施口径统一以 05 为准；阶段一排序和阶段二扫描/渐进发布修改分别见 07、09，持续进度以 06 为准。代码级去重不等于用户报告的真机闪烁、索引持续或权限矩阵问题已全部解决；仍需设备验证。未经测量不报告性能百分比。
