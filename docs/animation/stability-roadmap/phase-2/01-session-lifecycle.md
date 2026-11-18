# 阶段 2 转场 Session

## 回调关系

改动前，Grid 的 launch 序号、Viewer 的 entry gate、Viewer 的返回标志及 GridReturn 各自判定回调有效性。两端各自依靠类内字段决定 Holder、代理、输入和发布的清理时机。

改动后，Grid 为一次 launch 发出 Session 票据。Viewer 一次性领取同一个协调器，二者仍各自执行所属窗口的 UI 操作。票据通过 Intent 传递，进程内交接表持有弱引用。重建的 Viewer 使用新 Session 和普通关闭路径。返回 ImageView 映射仍由 Android 平台共享元素负责。入场和返回分别安装捕获各自操作的回调，旧平台回调无法映射新操作的代理。Grid 在接受合法的 reenter 后保持既有目标验证和绘制确认。

Session 身份由随机 ID、运行时代际及操作 ID 组成。诊断请求 ID 单独保留，可通过日志的 runtimeSessionId 与 runtimeOperationId 关联。正常返回由 Grid 在收到 Activity result、平台转场终止及目标回位绘制确认后结算。Viewer 的平台终止仅停止其动画更新，保留代理到平台完成关闭。独立 Viewer 由自身结算。

## 状态转换

| 当前状态 | 接受的后继 |
|---|---|
| PREPARING | ENTRY_CAPTURED、ENTRY_FALLBACK、VIEWING（普通入场）、CANCELLED |
| ENTRY_CAPTURED | ENTERING、ENTRY_FALLBACK、CANCELLED |
| ENTERING | VIEWING、ENTRY_FALLBACK、CANCELLED |
| ENTRY_FALLBACK | VIEWING、CANCELLED |
| VIEWING | INTERACTIVE_RETURN、RETURN_PREPARING、CANCELLED |
| INTERACTIVE_RETURN | VIEWING（回弹完成）、RETURN_PREPARING（移交现有操作）、CANCELLED |
| RETURN_PREPARING | RETURNING、FALLBACK、CANCELLED |
| RETURNING | COMPLETE、FALLBACK、CANCELLED |
| COMPLETE / FALLBACK / CANCELLED | CLEANED（两端租约都已释放） |
| CLEANED | 无后继 |

原 entry gate 由 Session 持有，继续区分 Android 的延迟释放、映射、capture 和实际动画开始。回弹使用当前 return 操作及 settle 代际，动画中断或完成后，旧的回弹回调失效。真正退出后 Session 只记录一次终态。终态后唯一可接收的结果是 Android 尚未交付、身份匹配的首次 Activity result，用于网格位置及确认删除。该结果无法恢复共享元素或重新取得资源。

## 资源与清理

| 资源 | 获取及释放 |
|---|---|
| Grid source Holder 与名称 | 验证源绑定和像素后注册。平台退场结束或名称超时清除名称。Grid stop、reenter 或恢复后的 result 释放 Holder |
| Grid target Holder、名称及 Adapter hold | 返回目标通过路径、Adapter 版本、Holder/布局、缩略图键、drawable、可见区域和目录作用域核对后取得。映射时重新核对并登记名称。回位确认、目标失效、取消、超时、结果异常或 Grid 销毁时统一释放 |
| Viewer 代理、drawable、renderer | Fragment 及 MediaSharedSurface 执行交接。先移除代理、名称、drawable 和硬件层，再恢复实际 renderer 的 alpha。拖拽提交沿用当前代理及变换 |
| Viewer chrome、scrim、系统栏与输入 | 入场在 renderer 交接或淡入完成时恢复，返回和拖拽由同一操作登记并在回弹、退出退化或窗口销毁时释放 |
| Handler、Animator、绘制与 frame commit 监听 | 在所属入场或返回操作登记，释放时取消。已进入消息队列的回调再核对操作、状态或所属监听。Grid 释放延期的平台入场，避免目标失效后窗口等待 |

Grid 目标失效或 lease 超时沿用普通返回。Viewer 找不到 renderer、关闭期间平台没有共享动画或平台超时则释放自身 UI 租约并走淡出。快速退出尚在入场的 Viewer 会作废 entry gate 和已排队监听。Grid 销毁会解除其资源并撤回交接票据。尚在浏览的 Viewer 可以独立完成关闭。

诊断仅记录运行时 ID、代际、操作、枚举资源及受控原因。沿用默认关闭、有效期和轮换限制。draw 和 frame commit 仍只表示 View 绘制与 renderer 提交确认。
