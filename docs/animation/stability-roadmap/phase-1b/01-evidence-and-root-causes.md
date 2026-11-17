# 阶段 1B 原始证据与根因

开始前通过 GitHub 查询 `perf/home-gallery-sorting` 的实际 HEAD。远端与干净工作树均为 `00316500783a79d28d593dcce6fda02e5a5c1d08`。审计使用《Fossify Gallery 全应用动画与媒体加载专项审计》及 [阶段 0 + 1A 合同](../phase-0-1a/)。

| 输入 | SHA-256 |
|---|---|
| Gallery-Diagnostics-20261117-112838.txt | `313859671589b7d1f77a80764c7dcb34b7c878da0dac403e50a19070cdfb2ed5` |
| adf4608aa835337881981f19a4bebd7e.mp4 | `b1f2de34b13e7a681d8def2ef54cae326d1fcd0cf70ba8a8974e100bc31e6fe8` |
| 本机阶段 1A ARM64 APK | `4192c26f7b1c97c187a1ce36fdc8bbd92c329cf00699441f13bfaf888b07c5be` |

日志自报 Pixel 8 Pro、Android 16 / API 36、1.14.1 (32)、Release、诊断开启。6262 行，其中 6250 条事件。录屏为 432×960、约 30 fps、190.900 秒。本机阶段 1A APK 经 apksigner 验证，签名证书 SHA-256 为 `2b620c1eee0635ec0d3d2e5f8e2eb74f19d888344667b19260a477ab18a0c18b`。缺少被测手机已安装 APK 的字节或签名导出，因此该本机文件的身份独立于录屏的安装实例。

## ANIM-001 已证实

按同一诊断 session 内的全局 request ID 联结创建、准入、开始、取消和 worker 终态。32 个请求均有一条开始和一条终态。21 COMPLETE，11 CANCELLED，11 个取消均为 `activity_pause`。23 个旧版 checkpoint 检查均报 `checkpoint_missing`。

该 scope 的来源诊断包含 `permissionMode=ALL_FILES` 和 `method=direct`，读取非 .nomedia 目录。11:26:16.865，Viewer session 51 从 request 49、scope `5558939338c9876b`、Adapter version 5 打开。11:26:16.931，request 49 终态 CANCELLED，枚举状态 COMPLETE，已交付六个来源快照，未交付 FINAL。之后同一 scope 的 request 53、56、59、62、65 因同一原因取消，request 68 才交付 3,080 项 FINAL。11:27 的 request 71、74、77、80 又出现同 scope 的 pause 取消。相同 scope 下多次启动已证实。日志无法证明每次间隔内目录字节都相同，因此精确的无变化重复扫描数记为 NOT MEASURED。

## ANIM-010 部分证实

同一源 request 68、Adapter 50、diff generation 6，从 Adapter version 5 的 1,454 项更新至 version 6 的 3,080 项。

| 时间 | 事件 |
|---|---|
| 11:26:34.873 | 来源发布 FINAL |
| 11:26:35.679 | 首次 Diff 完成，789 ms |
| 11:26:35.764 | `layout_or_scroll` 暂缓 |
| 11:26:42.983 | 同一 Diff 重新计算 |
| 11:26:43.661 | 第二次 Diff 完成，674 ms |
| 11:26:43.682 | Adapter commit，提交链 8,801 ms |
| 11:26:44.964 | 该请求提交版首次采样的 `visible_drawable_ready` |

旧代码在不安全时段丢弃已算出的 Diff，只把输入放回待处理队列。日志和受控测试均复现。录屏在约 84、92、100–112 秒反复出现灰色格，仍可见文件名渐变条与视频时长标记，间隔中局部缩略图恢复。连续标记呈现正常网格行间距。观察支持图层占位和文件名叠层的解释。尚未排除部分帧父级裁切、冷缓存、请求调度及绘制负载。导出有 274 条可见 drawable ready，clear 样本为 0。采样在可见、已附着且限频时才记录，无法据此排除未采样复绑、清图和旧请求。

## ANIM-002、ANIM-003 与配置回位

ANIM-002 已证实于源码。成功删除路径永久进入 Activity 集合，后续权威扫描也会过滤同路径。现有录屏没有冻结一次可关联的还原事务。

ANIM-003 已证实于源码。旧普通入口在异步 handoff 前取得准入，自定义配置入口直接排队，两个同 scope 回调均可启动。录屏未证明发生叠页。

配置回位竞争部分证实。scope `c00d5b73f486c16a` 的 request 3 发布 160 CACHE_PREVIEW、160 INDEXED、273 FILESYSTEM、4,981 COMPLETE。11:25:11.602 Viewer session 5 从 Adapter 4/version 3 打开。11:25:13.146 同一 Adapter 提交 version 4，11:25:13.159 有 `photo_scroll_restore_applied`，11:25:13.163 有 `viewer_grid_order_authoritative_fallback`。session 5 在 11:25:14.992 请求关闭。该会话导出有 30 次 `grid_target_return_frame` 与 30 次 `grid_return_closed(reason=target_drawn)`，所有帧的 `presented` 均为 `NOT_OBSERVABLE`。ANIM-005 仍需跨窗口像素证据。
