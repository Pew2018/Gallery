# 回归覆盖

| 编号 | 检查路径 |
|---|---|
| R01–R03、R05–R08 | 真实 RecyclerView 跳转、同键不清图。设备合成图用路径、同一 token、非零尺寸及红/蓝像素验收，3 s 内必须覆盖全部可见缩略图。冷用全新路径，热为立即反向回到同盘区域，Glide 实际来源由采样日志提供。 |
| R04 | 实际 Glide listener 的 A→B→A 旧回调注入与拒绝。 |
| R09 | 沿用 `MediaPageStartupTest` 的首缩略图发生在 scanner COMPLETE 之前检查。 |
| R10 | 沿用 `MediaAdapterStabilityTest` 的独立 1,454→3,080，要求真实 RecyclerView 受控 Diff 只计算一次、提交一次、锚点和载入身份稳定。 |
| R11–R17 | 目录复制与代际反复测试、缺少完整证明拒绝、访问与修订变更拒绝。现代模拟器的普通公开目录重复进入、首尾和 50 项导航、回位、源 request、外部添加和删除见性能报告。 |
| R18–R19 | `ViewerDeletionLedgerTest` 与旧 Adapter 接线测试检查复活屏障、批量/最后一项和新请求同路径可再次出现。设备回收站与文件管理器替换待复测。 |
| R20 | `NoMediaCategoryRefreshTest`、`RefreshRepairTest` 及 PhotoProfile JVM 回归。真机 .nomedia、新增和计数需复核。 |
| R21–R22 | `ViewerLaunchGateTest`、回位 Adapter 测试及配置相册设备回位用例。用户实机乱序授权、连续双击和更换配置仍待复测。 |

排序和搜索沿用现有媒体更新策略与独立测试。对端到端数据变更或授权无法执行的场景，禁止以合同测试结果替代真机结论。
