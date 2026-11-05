# 05 — 媒体索引、扫描、排序与显示的现行实施细则

> **2026-11-05 已更正执行范围**。文件名为兼容旧链接保留，但旧“唯一 fix 分支、8–16 人日、仅精简不考虑索引优化”的口径已经废止。现行纲领：[18](18-current-media-pipeline-roadmap.md)。工作分支 `refactor/media-pipeline-stability`；`main` 审计 SHA `49e799eceab00414ef074cbf71b7f0a35133b669`。

## 目标、顺序与原则

先**正确性/稳定性**、再**减少重复工作**，最后**增量缓存与 UI/Viewer 一致性**。不得因编译成功宣称全部问题已解决；不预设新媒体数据库、第二刷新框架、新线程池或解码器替换。

| 阶段 | 定点实施方法 | 验收 |
|---|---|---|
| 0 诊断 | 复用 `GalleryPerfTrace`/`GalleryDiagnosticLogger`，给每请求增加扫描、缓存、SQL、排序、发布和 UI 时长/计数摘要 | 同设备/权限/媒体库，10+ 次 p50/p95、准确性样本 |
| 1 生命周期 | 审计 `MainActivity.getDirectories`/`MediaActivity.getMedia`/`GetMediaAsynctask`/`CategorizedFolderScanner`，复用现有 epoch、terminal、取消代际 | COMPLETE/PARTIAL/FAILED/CANCELLED/TIMED_OUT/ACCESS_REQUIRED；旧结果不提交 |
| 2 热点 | `MediumDao` 用 EXPLAIN 选组合索引；`MediaFetcher.sortMedia` 预计算比较键并稳定 ties；`PhotoProfileMediaLoader` 累计排序改安全有序合并；减少不变 Room 写入 | 内容及顺序不变或被明确验证的语义变更；查询/CPU/写入改善可测 |
| 3 增量 | 基于 `FolderReturnCheckpoint`/`PhotoProfileCatalogCheckpoint` 以及现有缓存实行按 scope 复用、文件变更定点失效；不确定时安全全扫 | 普通/.nomedia/SAF 外部变化无漏项、误删、越权 |
| 4 UI/Viewer | 保留 `MediaAdapter`、`DirectoryAdapter` DiffUtil、路径身份、分段目录/权限校验，减少上游无意义 stage/rebind | 点击图、左右滑动、删除恢复、锚点一致 |
| 5 完整回归 | Android 8/11/14/16、100/5k/30k/100k 规模及备份升级 | 完整 FOSS Release Tests、ARM64、设备矩阵、无新增 P0 |

## 实施边界

- **扫描**：MediaStore + 文件系统/SAF + `.nomedia` 必保；扫描失败或失权不得解释成空目录或文件删除；不能为了速度跳过真正的媒体授权。
- **排序**：保留名称/路径/大小/拍摄时间/修改时间/自然排序、随机与分组的可见语义；先建立 ties、大小写、数字名、升降序、跨来源去重测试，再改 comparator 或批次 merge。
- **数据**：`Medium` 目前有 full_path 唯一索引，部分 DAO 高流量筛选缺组合索引；先测量再增加显式 Room migration，不改变用户收藏关系、PhotoProfile ID/source、备份格式或签名密钥。
- **显示**：缓存预览允许先显示可信数据，但不能当 COMPLETE；已有 Grid/Viewer 向前向后窗口、真实 END、删除导航、缩略图签名及 access revision 必保；失权清 UI 是安全例外。
- **工程**：每阶段先更新分支 HEAD、查看最新提交和 01–18，基于代码证据定点修改、单独提交、补测试和 CI/真机证据。不直接写 main、不创建 PR、不强推、不用固定旧 SHA 覆盖他人代码。

## 状态

**新阶段 0–5 全部待实施。** 原小规模阶段的实施及 Action 结果作为历史留在 06–17 中，不转化为新阶段已完成。当前 `main` 的 [Actions 41939577702](https://github.com/Pew2018/Gallery/actions/runs/41939577702) 为 success（完整单测和 ARM64），但没有本路线的真机基线。
