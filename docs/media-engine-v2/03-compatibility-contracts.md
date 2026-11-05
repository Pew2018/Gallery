# 03 — 重构不可破坏的用户侧与数据兼容契约

历史审计基线：c5cc5b6852291491a999934d12f26314455959c6；现行源码参考：`main` `49e799eceab00414ef074cbf71b7f0a35133b669`（2026-11-05）。本页为当前的**兼容性约束**，不是功能已通过真机验证的证明。实施按 [18](18-current-media-pipeline-roadmap.md)。

## A. 用户侧既有功能（默认保持）

| 编号 | 保留契约 | 当前代码入口 / 注意事项 |
|---|---|---|
| C01 | OxygenOS 7.1.1 Classic 工具栏、图标、颜色、Ripple、列表密度和弹窗外观不因引擎替换而改变 | OxygenToolbarStyle / OxygenThemeEngine / OxygenClassic* / Activity layout |
| C02 | 顶栏 HOME、FOLDER 两套快捷操作、可选动作及顺序维持原来配置 | Config.kt L25-L27；ToolbarQuickActions.kt；Menu XML；不要把配置存储迁移到索引 DB |
| C03 | 底栏 ALL / FAVORITES / PHOTOS 三入口、排序与重启恢复、点击/涟漪维持 | GalleryBottomBar.kt L170-L173；GalleryNavigation / NavigationOrder |
| C04 | 相册列表/全部媒体/普通文件夹/虚拟收藏/照片配置都继续支持原排序、分组、文件名、搜索、筛选、列数与多选 | MainActivity / MediaActivity / Config / Adapter |
| C05 | 自定义照片配置的 ID、名称、来源、当前选中项、同目录与虚拟收藏来源支持与旧数据一致 | PhotoProfileRepository KEY=photo_profiles_v1；PhotoProfiles / PhotoProfileLoadContext |
| C06 | 收藏媒体、收藏文件夹及隐藏/排除规则保留其数据和用户语义 | FavoritesDao / FavoriteFoldersDao / FavoriteAccessPolicy |
| C07 | 受保护目录、收藏密码/指纹、照片配置授权、会话吊销、隐藏项目禁止缓存越权 | FolderUnlockSessionManager / FavoriteAccessSessionManager / PhotoProfileAccessSessionManager / PhotoAccessValidationBatch |
| C08 | 下拉刷新、相册数量和封面、滚动定位、快速拖动条、缩略图点击后全屏、前后翻页保持可用；刷新时尽可能稳定已有内容 | MediaActivity / MainActivity / Adapters / OxygenFastScroller / Viewer |
| C09 | 导入导出设置、收藏媒体、收藏目录、自定义照片配置及完整备份结构/兼容行为不变 | helpers/backup 下 codecs、GalleryBackupSnapshot.kt L10-L74 |
| C10 | 旧版升级不能丢用户数据，不执行破坏性迁移、不要求卸载/清除数据才能正常浏览 | GalleryDatabase.kt @Database version=11 与 migrations |
| C11 | 低版本 Android 8（minSdk 26）、Android 11+ 分区存储、SAF 持久化树以及 All Files Access 的读取规则维持 | FolderFileAccess.kt L24-L78；MediaFetcher |
| C12 | 多媒体类型、视频时长、原格式、Portrait、编辑、删除/恢复、幻灯片、文件选择器外部 Intent 行为维持 | GetMediaAsynctask / MediaAdapter / ViewPagerActivity / Editor / pickers |
| C13 | 正在切换底栏、页面退出、Activity 销毁、权限变化或改变配置时，过期任务绝不发布旧授权数据 | 各 generation/checkCurrent/SessionManager |
| C14 | 主屏幕和文件夹外的数量、进入文件夹后的真实媒体列表、配置来源与全屏列表的媒体身份一致 | DirectoryMetadataRepository / MediaFetcher / PhotoProfileMediaLoader / ViewPagerActivity |

## B. 媒体访问与一致性的不变量（拟作为 V2 验收契约）

1. **同一 scope+version 仅一个正式结果**：缓存预览和局部阶段只能标记为 provisional，不能覆盖更晚的 COMPLETE 或新 scope。
2. **失效请求不提交**：取消、超时、权限撤销、切换配置、搜索/过滤条件改变之后，旧请求不得更新 Room、Adapter 或全屏数据。
3. **不完整 ≠ 空**：PARTIAL/FAILED/ACCESS_REQUIRED/被取消不允许将未读目录或媒体解释为被删除；不得据此删除收藏关系。
4. **真实目录与 .nomedia**：当前具备访问能力且用户明确选中的文件夹，可以按权限与隐藏策略读取其直接媒体，不能只依赖 MediaStore。
5. **目录卡片与媒体数量同源**：目录卡片的计数、缩略图与列表应来自同一成功读取的事实集，或明确展示上次可信快照/待同步状态。
6. **排序一致**：列表、全屏和配置中完全相同的 scope+sort 应共享确定性的序列；随机排序在一次会话/刷新中不能无故全体打乱。
7. **滚动时稳定**：原有媒体并未删除或重排时，背景刷新不得抹掉可见锚点及像素偏移；首次展示与明确用户改排序除外。
8. **Viewer 边界不伪造**：局部五张/窗口缓存不能被解释为真实目录结束；只有已验证完整的目录/有据可查的边界才能宣称 END。
9. **仅授予最低访问能力**：公共缓存与临时索引不携带可重用的保护目录授权；每次实际展示前依照当前有效会话校验。
10. **数据与缓存分离**：任何 V2 缓存升级失败可重建，不得让旧配置、收藏记录、备份和 protected settings 随缓存一起被清除。

## C. 备份与数据库强约束

**当前事实**：GalleryDatabase version=11，包含 Directory / Medium / Widget / DateTaken / Favorite / FavoriteFolder；PhotoProfileRepository 使用 SharedPreferences 键 photo_profiles_v1；GalleryBackupCollector 读取设置、收藏媒体与目录、照片配置并形成快照。

如阶段 2–3 真实数据证明需要新增索引，建议只将其作为**可丢弃、可重建**的元数据层；不应悄悄替换业务收藏或照片配置的权威存储，不应改变现有压缩包 manifest/Codec 含义。若必须变更 schema，只允许显式、可验证、保留用户数据的迁移；升级前复制并对照 DB 数据。

## D. 当前 main 的浏览能力必须保留

旧文中“当前 fix 相对 main 新增”属于当时的历史分支对照；在 `main` `49e799eceab00414ef074cbf71b7f0a35133b669` 已可查见 `PhotoSessionViewerCatalogStore`、`SegmentedPhotoCatalog`、`FolderViewerSnapshotStore`、`ViewerGridOrderStore`、`PhotoProfileSourceScanPolicy` 及全屏渐进/鉴权实现。新计划以当前 main 为起点，不得再把这些当作尚未合并的可丢弃功能。

重构必须测试并保留：当前网格路径与全屏次序交接、UNKNOWN/HAS_MORE/END 真实边界、分段媒体目录、扫描中删除后的可信恢复、受保护与公共来源隔离、缓存不能代替授权。这是兼容责任，不代表实机已完全稳定。

## E. 禁止的捷径

- 不可直接删掉访问校验、回滚数据库、调用 destructiveMigration、依赖“清应用数据即可解决”。
- 不可把 ViewPager 的 5-item 邻近窗口、PhotoSessionViewerCatalogStore 的 20k 内存窗口视为永久媒体数量上限。
- 不可为了“刷新稳定”彻底禁用刷新或忽略外部新文件。
- 不可为了“更快”绕过 .nomedia 直接读取、取消安全校验、或始终全库重扫。
- 不可修改顶栏、底栏、主题和备份格式以掩盖引擎问题。
