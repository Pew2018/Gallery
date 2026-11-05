# 41 — 虚拟收藏与回收站排序/搜索源码审计

## 审计基线

- 审计时间：2026-11-05 UTC。
- 重新读取的远程分支：refactor/media-pipeline-stability。
- 审计 HEAD：b7175e8fa0411d8ff600366e316777efb28bb8f3；这是本轮执行时重新获取的远程值，与任务描述中的历史参考值相同。它不是要求回退到的目标。
- 工作树初始干净。只检查并开发上述分支；没有切换或修改 Archive、main，没有 Schema/Migration 变更。
- 上一阶段记录 37–40 已阅读。它们针对普通物理文件夹及 Adapter 提交恢复；不构成虚拟来源已经获得快照排序的证据。

## 排序路径和来源分支

MediaActivity.showSortingDialog() 保存用户配置并产生 mediaSortOperations generation，然后依次尝试：

1. resortCompletedPhysicalFolder()：必须是正式 COMPLETE 的普通物理目录，要求 read capability、当前 completed request、未在刷新、无分组标题等。
2. previewSortDuringFolderScan()：必须是正在读的普通物理目录，要求当前已显示快照及 capability 有效。
3. 失败路径仅在 !mIsGettingMedia 时调用 getMedia(reason = sort_snapshot_unavailable)。已有扫描时保留列表、等待扫描发布，不会在此处取消扫描。

两个物理目录入口均明确排除 FAVORITES、RECYCLE_BIN；未满足前两条时，诊断把它们归类为 special_source。因此已加载虚拟来源在闲置时会走完整 getMedia()，扫描中则只能等待。Adapter 虽然不会因该回退被直接置空，但完整读取及最终发布仍是排序反馈的依赖。

getMedia() 产生新的 mediaLoadRequests 请求、失效排序 generation、取消旧 task，再走缓存/扫描。startAsyncTask() 创建 GetMediaAsynctask；扫描读完后 gotMedia() 对收藏额外通过 FavoriteAccessPolicy.authorizedItems() 做授权过滤，物理目录则走自己的发布验证；publishMedia() 更新完整 mMedia 与来源版本，再调用 setupAdapter()。后者保留现有 MediaAdapter，按 MediaUpdateReason 通过 updateMedia() 决定 reorder、Diff 或 replace。

GetMediaAsynctask 扫描开始时读取排序、分组和所需字段。修改前只有 ordinaryFolder 专用的最终排序配置复读及缺字段重载；虚拟来源未有等价处理。MediaFetcher.getFilesFrom() 为收藏按当前查询的收藏路径生成列表；回收站的 getMediaInFolder() 从 context.getUpdatedDeletedMedia() 单次取得 Room 删除记录，再按文件路径读取媒体元数据。完整结果之后统一 groupMedia()。

收藏扫描接受 onFavoritePreview 参数，但 MediaActivity.startAsyncTask() 没有传入该回调；本 Activity 当前不消费渐进收藏候选。不能据此把收藏 UI 记为渐进发布。可见缓存也不等于正式 COMPLETE。

## 收藏访问和成员

- 收藏关系来自 Context.getFavoritePaths()，底层为 favorites Room 关系 DAO 的有效路径查询。
- 进入收藏前通过 FavoriteAccessSessionManager 建立进程内会话；会话保存所有捕获路径和受保护祖先的证据。扫描用 validatePaths() 在 worker 上检查捕获集合，完成发布前再次读取关系并 retainFavorites()；Activity 的 gotMedia() 再应用 FavoriteAccessPolicy。
- Room favorites 表观察者会核验会话并撤销已取消收藏路径；会话失效监听会盖住并清除收藏媒体。访问会话是授权依据，单个物理目录 grant 不足以证明整个混合收藏集合。
- 关系集合没有可由排序入口直接读取的 revision/generation。上一阶段的 mediaSearchGeneration、displayedSourceVersion 属于 UI 发布版本，不是 favorites Room 关系版本。
- 上一阶段有 FavoriteMediaLoadRequests 和收藏扫描关系快照；它们用于异步装载及发布，但排序入口此前没有用它们批准虚拟集合复用。

## 回收站成员及删除恢复

- Context.getUpdatedDeletedMedia() 单次调用 MediumDao.getDeletedMedia()，把 deleted_ts != 0 的 Room 记录映射到回收站文件路径。
- MediaFetcher.getMediaInFolder(RECYCLE_BIN) 从该 Room 结果建立候选，再执行配置要求的 size/existence 检查并构造 Medium。回收站 Medium 是该次正式扫描的来源快照。
- restoreRecycleBinPaths() 成功复制后将记录的 deleted_ts 更新为 0；emptyTheRecycleBin() 删除文件并清 Room 删除项；MediaActivity.deleteFilteredFiles() 永久删除后删 Room 删除项。选择恢复的 Adapter 回调刷新列表，全量恢复/清空会结束 Activity，永久删除当前 Activity 后会刷新。
- 现有 PhotoContentRevision 供照片配置/内容缓存使用，不是回收站成员集合的可靠 generation；原代码没有专用的回收站 membership revision。
- Room 行本身不能证明文件依然存在；文件缓存也不能证明该行仍是回收站成员。正式扫描会按既有媒体读取优先级检查文件；重用必须同时核验当前 Room 成员关系和扫描阶段，不能把 cache 标成完整结果。

## 搜索路径

收藏和回收站都走共享的 searchQueryChanged()：在后台从完整 mMedia 构造过滤、分组列表，然后用 search generation、文本、displayedSourceVersion、当前排序、Activity lifecycle 和 displayedMediaRequest 门控，再通过 Adapter USER_SEARCH 提交。搜索不把当前过滤列表写回完整来源；清空时基于完整 mMedia 重建。

收藏额外检查 access session 并按会话过滤结果。回收站在旧代码中没有成员访问检查；旧 Room/显示快照能否代表仍在回收站，只依赖后续刷新或 Adapter 操作回调，缺少可同步查询的来源版本。上一轮修复的“显示快照可在 refresh request 改变时继续搜索”、搜索 generation、最新待提交快照以及 Diff 过期保护已共享覆盖虚拟来源，但不能单独解决虚拟成员变化。

## 确定事实与设备边界

源码确定：虚拟来源明确被排除在两个已有排序快照入口之外；排序期间若无刷新会回退 getMedia()；刷新期间不会因该分支取消现有 task；收藏渐进回调未接入 Activity；回收站来自 Room 删除记录；搜索基于共享的完整 mMedia 与 Adapter 管线。

日志/设备已证明：仅上一阶段文档 37–40 中记录的普通目录/.nomedia Adapter 和扫描现象。它们没有证明收藏或回收站真机排序延迟、成员复活或授权异常。

风险而未证实：虚拟排序若在正式成员或授权状态变化后提交，可能短暂展示过期行；扫描开始时排序值可能覆盖更新配置；回收站搜索在恢复/删除发布前没有成员版本保护。没有本轮虚拟来源设备日志，不能把这些风险表述为已发生的真机故障。

选择在 Activity 现有排序入口增加来源准入、成员校验和现有 Adapter 提交，避免重写 Adapter/Diff/扫描器。每次扫描和逐文件校验仍由当前读授权、文件完整性与 metadata 策略决定。
