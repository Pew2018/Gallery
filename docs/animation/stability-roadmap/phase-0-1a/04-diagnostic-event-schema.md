# 诊断事件与关联规则

每行沿用 `session`、单调 `elapsedRealtimeMs`、模块和级别。诊断会话用完整的进程 UUID。新事件的 ID 来自同一进程递增序列，聚合键为 `(session, id)`。旧日志的 Activity 局部 `request`、`launchId` 与历史 `identityHashCode` 单列。时间相近不能替代显式关联。

## 扫描、缓存与发布

| 事件 | 触发点和字段 |
|---|---|
| `scan_restart_requested` | 调用 `getMedia` 的意图。旧请求标识移至 `previousGeneration` 和可用时的 `previousRequestId` |
| `media_request_not_created` | 收藏权限过期或正在等待收藏授权，先退出调用。此时无请求 ID |
| `media_request_created` | `mediaLoadRequests.begin()` 后记录全局 `request`、局部 `requestGeneration`、`originTrigger`、`sourceType`、`scopeId`、`scopeRevision`、可用时的 `previousRequestId`。当前代码没有统一授权代际，字段值为 UNKNOWN |
| `media_request_admitted` | AsyncTask `onPreExecute`，既有准入后尝试排队；随后仍可能被拒 |
| `media_worker_started` | 真正进入 `doInBackground`，记录 `workerId` 和 `sinceRequestMs`。当前每个接线请求对应一个 AsyncTask，workerId 与请求 ID 同值 |
| `media_worker_source` | 复用 worker 已经判断出的 `.nomedia` 路径，保留原 scopeId，增加 `resolvedSourceType` |
| `media_request_cancel_requested` | 观察原有 stop 或 watchdog，首次原因和 worker 是否启动 |
| `media_request_skipped` | 未启动即被替换、生命周期退出、缺配置、拒绝或取消，带 `admitted` 和 `terminalReason` |
| `media_worker_terminal` | 实际离开 worker，并发停止只产生一条终态，带 `workerMs`、`terminalReason`、`scanState` 和 `terminal` |
| `media_content_published` | 通过当前请求、权限、排序与发布门后，记录 `stage`、`snapshotVersion`、`itemCount`、`sinceRequestMs`、`cacheStatus`。此事件表示来源快照已接纳，Adapter 提交独立 |
| `folder_checkpoint_evaluated` | 返回时的已计算 checkpoint 分支，HIT 表示通过 checkpoint 条件，随后仍走现有权限门。MISS 原因包括未取得 checkpoint、受保护、目录/排序不适用、显示不完整或失效 |
| `media_first_thumbnail_resource_ready` / `media_first_thumbnail_rendered` | 沿用现有首图 signal，附 `traceRequestId`；resource ready 是 READY，后者实际是可见候选 PRE_DRAW，呈现情况为 NOT_OBSERVABLE |
| `scan_started` / `worker_started`、`scan_cancelled`、`scan_completed`、`media_scan_summary` | 保留旧事件。summary 聚合排队、worker 和交付延迟，结果可以晚于新 worker 终态。旧 stop / cancelled 可重复，不能累加为作业数 |

Worker COMPLETE 对应完整扫描；CANCELLED 包括已启动 worker 的超时停止；FAILED 保留 FAILED、ACCESS_REQUIRED、PARTIAL 等原始扫描结果。PARTIAL Worker 结果代表工作退出但目录不完整；有效部分内容仍可交付。PREVIEW、PARTIAL、FINAL 用于内容发布。计数只依据完整关联的 started / terminal 对；掉落、轮转或开启时已在运行的事件链标 UNKNOWN。worker 退出后仍可遇到交付、授权或发布门拒绝。

本表的新 worker 链覆盖 MediaActivity 发起的普通与照片配置 AsyncTask。Viewer 自发补全、MainActivity 扫描及照片配置共享生产者的内部订阅继续使用各自现有日志。新链未提供通用订阅者数量或共享作业关联。Scope ID 按来源类型与 locator 加盐生成，型号和 revision 相同只提供关联线索，授权一致性仍按 [来源合同](02-source-identity-contract.md) 验证。

## 网格

- 配对 `adapter_update_submitted`、`adapter_update_committed` 或 `adapter_update_skipped` 使用 `session + adapterId + diffGeneration`。取提交时和提交后 `adapterVersion`、`elapsedMs`、更新策略、 `snapshotVersion`、`snapshotStage`、列表大小和可用上游请求。初次挂载记录 `adapter_initial_snapshot`。历史 `adapter_submit`、`adapter_commit`、`adapter_snapshot_fast_commit` 等保留自身既有键。
- NO_OP 快照可以提交新 Adapter 版本，`visibleListChanged=false`。APPEND、REORDER、REPLACE、DIFF 各用现有分支标记。作废于提交前的调用无 diffGeneration；替换、撤销和过期 diff 保留旧提交键。
- `visible_drawable_cleared` 观察新绑定键导致的 clear，`visible_drawable_ready` 观察与当前请求键匹配的 drawable。采样仅限已附着、可见且有裁口的 ImageView，每个 Adapter 每秒窗口最多 8 条。两个事件可能分别被限流。匿名 `mediaId`、`drawableKeyId`、临时 `holderId`、Adapter 当前版本帮助定位；Holder ID 是进程内视图标识，长期匹配使用媒体和加载键。

## Viewer 与返回

1. `entry_requested` 记录 Viewer 会话 ID、可用的网格 scope / sourceRequestId、Adapter 版本和匿名媒体。进程 ID 随内部 Intent 传递，跨进程恢复时旧 entry ID 作废。 `viewer_launch_accepted`、`viewer_selection_resolved` 为 Activity 创建及定位观察。其他入口可产生各自的 Viewer 会话 ID；来源网格键缺失时链从 Viewer 开始。
2. `grid_source_lease_acquired` 和 `grid_source_released` 为来源租约。入口沿用 `shared_map_accepted`、`shared_start_captured`、`shared_animation_started`、`shared_transition_ended` 和 `entry_completed`。 `viewer_page_bound` 表示 pager 选中位置观察。 `viewer_first_renderer_signal` 取当前 Fragment 现有回调，按实际可取得的 renderer / drawable 标注。
3. `media_viewer_publication` 带 Viewer 会话和 PREVIEW / PARTIAL / FINAL；FINAL 来自现有完整目录标记。 `viewer_video_first_frame` 是既有播放器回调的 Performance 信号，目前无单个 Viewer 的显式关联。视频封面 ready 与 TextureView / Tile 交接不能据此推断；真机合成呈现仍不可观测。
4. `viewer_requested` 以独立 return ID 记录 Viewer 会话，Grid Reenter 接收同一返回键。 `viewer_return_fallback` 和 `viewer_closed_during_entry` 为既有降级边界。 `viewer_map`、`grid_map` 为平台映射，`grid_released` 的 target_ready 表示返回目标已通过检查并取得 lease。
5. `grid_capture_start/end` 观察平台回调快照。 `grid_first_return_frame` 可早于目标恢复；`grid_target_return_frame` 为目标匹配后的观察。 `ack=after_draw` 仅支持 DRAW，`ack=frame_commit` 支持 FRAME_COMMIT，二者的 `presented=NOT_OBSERVABLE`。部分源图层字段随 `grid_first_return_layers` 记录。
6. `viewer_animator_started`、`viewer_transition_end` 分别是现有动画观察。 `viewer_cleanup_completed` 位于 Viewer 自身清理末尾，含 return ID（如有）。 `grid_return_closed` 记录现有目标 lease 和发布阻挡解除的清理原因。两个窗口可各自先结束。

## 开销和隐私

日志保持用户开启、24 小时到期、5 文件轮换、每个约 1 MiB、总计约 5 MiB，另有既存最多约 16 KiB 的崩溃记录。正常事件写入使用单后台线程与 1,024 项有界队列。关闭时新扫描 trace 不构造，Viewer payload 不求值，Adapter 样本不取时钟、指纹或裁口。开启后本轮在调用点收集少量字段，哈希和格式化仍有成本；主线程活动采样按预算限流。现有一回程一次的监听继续使用，未增加逐帧回调。事件拒绝或日志轮换可产生观测缺口。

新字段只采集结构化枚举、计数、版本、时间、几何、临时 ID 和盐化匿名标识。业务 scope 的原值、搜索文本、原始私有路径、授权 token、URI 和图片像素不写入日志。未增加与目录大小成正比的诊断状态。
