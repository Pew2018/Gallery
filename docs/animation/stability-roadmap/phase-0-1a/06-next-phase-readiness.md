# 后续阶段准入

| 风险 | 已定位模块 | 所需验证 |
|---|---|---|
| ANIM-001 退出暂停导致普通扫描重来 | MediaActivity `onPause`、`onResume`、`startAsyncTask`，GetMediaAsynctask 与 FolderReturnCheckpoint | 同 scope/revision 重复作业计数，稳定/变化目录、授权撤销、`.nomedia` 新文件、排序过滤切换、partial 与宿主离开 |
| ANIM-002 路径 tombstone 过滤同名新文件 | MediaActivity 的 Viewer result 与 `publishMedia`，MediaUpdatePolicy | 同一 Activity 删除、恢复或重建，再与旧晚到快照交错；验证删除最后一张返回和重复结果 |
| ANIM-003 照片配置入口并发 | MediaActivity 普通路径的 handoff generation 和 profile 异步入口，共用 launch 和来源 lease | 快速双点击、过期资格、重入、真实窗口数量及租约释放，模拟在途授权乱序 |
| ANIM-005/006 回程与端点 | GridReturn、PhotoTransitionCompletionGate、MediaSharedSurface 和 renderer | 核对 source/target 版本、同图 draw/commit、系统呈现、几何或裁切误差 |
| ANIM-007/008/009/010 | Video/Tile renderer、下拖、滚动和可见 Holder | 分离封面、真实首帧、接手、滚动空位、系统栏与手势的真机证据 |

**阶段 1B** 可开始基于来源合同设计和编写确定性测试。进入扫描复用行为改动前，需要同步原设备证据或重新实测；需补同一授权范围内的请求时间线、复用资格、访问撤销和终态全路径。当前 worker 诊断留给后续显式 producer/subscriber 关联字段。保护、缓存、过滤、排序、删除与恢复是高风险边界。

**阶段 2** 可基于会话、lease、观测级别和几何定义编写审查用例。合并完整协调器前，需要补齐进/退、取消、快速重入及跨窗口的同源视觉和合成时序。先锁定已有共享回调、输入锁、网格发布阻挡和照片/视频 renderer 接管，中途继续保留降级回归。仪器测试未通过项见验证记录。

几何修正随后在阶段 3 按端点合同验证。拖拽曲线、文件夹进出、RecyclerView 动画、Tile/Video 生命周期及调参仍按阶段 4–7 独立实施。阶段 1A 代码范围限诊断、纯合同及测试。
