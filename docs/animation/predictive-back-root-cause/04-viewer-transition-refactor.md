# 04 · Viewer 系统返回与下拖关闭的分离整改

## 1. 目标结构

| 交互 | 所有者 | 视觉 |
|---|---|---|
| 系统边缘返回（API≥34，稳定浏览页） | **平台** | 整个 Activity 窗口随系统手势缩放/位移，露出真实父级网格 |
| 图片下拖关闭 | Gallery | 图片表面共享元素缩放回网格（保留原行为） |
| 应用内部步骤（入场未完、interactive dismiss 在途、返回已提交） | Gallery | 应用自有的收尾动画，先到稳定帧再释放窗口 |
| API≤33 返回 | Gallery | 无系统跨 Activity 预览，沿用原应用动画 |

两条路径共用：当前媒体身份、返回位置、删除结果、会话状态与清理、安全检查；**不共用视觉驱动**。

## 2. 修改的文件与关键函数

### 新增 `helpers/ViewerBackOwnership.kt`
- `hasInAppStep(state, dragActive)`：只有稳定 `VIEWING` 且无拖拽才算“无 in-app step”。
- `platformOwnsEdgeBack(sdk, state, dragActive)`：API≥34 且无 in-app step ⇒ 平台拥有。
- `appOwnsEdgeBack()`：严格取反，保证“任一时刻恰好一个 owner”（由测试穷举验证）。

### `helpers/ViewerBackGesture.kt`
- 从 `OnBackPressedCallback(true)` 改为 `OnBackPressedCallback(false)`：默认关闭，不再无条件吞掉手势。启用状态由 `ViewPagerActivity.syncViewerBackOwnership()` 按所有权策略驱动。
- 方向从 `backEvent.touchX` 猜测改为官方 `backEvent.swipeEdge` 转发；修正了错误注释（原来把抑制系统动画归因于“default priority”，实际原因是 `enabled=true`）。
- 保留 `handleOnBackStarted/Progressed/Cancelled/Pressed` 的单一职责：只喂给现有 Session，不产生第二条返回路径；取消不清 result，提交只一次。

### `activities/ViewPagerActivity.kt`
- `beginSystemBackGesture(touchX)` → `beginAppOwnedBackGesture(swipeEdge)`：明确它只在“应用拥有返回”时被回调触发；方向用 `swipeEdge != EDGE_RIGHT`。
- 新增 `viewerBackGesture` 字段 + `syncViewerBackOwnership()`，在以下状态转换后调用：`onCreate` 注册后、`beginGesture()` 成功/失败、`cancelGesture()` 恢复、dismiss settle 动画结束、`entryFinished()` 进入 VIEWING。
- `finishAfterTransition()`：新增 `platformOwnsCurrentReturn()` 判定。平台拥有时走新的 `commitPlatformReturn()`；否则保留原 `requestViewerClose("system_back")`。
- 新增 `commitPlatformReturn()`：
  - `beginReturn()` 创建唯一 operation；以和原路径完全相同的 extras 写 `setResult`（RETURN_PATH、DELETED_PATHS、REQUEST_ID、VIEWER_SESSION_ID、LAUNCH、MediaTransitionHandoff token）；
  - 直接推进 `targetReady()` + `terminate(COMPLETE, "platform_cross_activity_return")`，因为没有应用动画来驱动 `RETURN_PREPARING → RETURNING`；
  - **不安装** `window.sharedElementReturnTransition`，**不调用** `super.finishAfterTransition()`，只 `super.finish()`，从机制上消除“平台动画 + 应用缩放”的二次动画；
  - 通过诊断记录 `viewer_platform_return` 事件供真机核对。
- 下拖关闭路径未改：`mediaDismissGestureBegin/Progress/Settled` 与 `beginInteractiveDismiss()` 继续提供图片回程；它们在 `INTERACTIVE_RETURN` 期间把所有权交回应用。

## 3. 调用路径（整改后）

平台拥有（API≥34 稳定页）：
```
边缘手势 → 启用回调=false ⇒ dispatcher 不消费 → 平台跨 Activity 动画
  → 手势完成 → framework finishAfterTransition()
  → platformOwnsCurrentReturn()=true → commitPlatformReturn()
      → setResult(结果/删除/token) → targetReady → terminate(COMPLETE) → super.finish()
  → 父 Activity onActivityResult/onActivityReenter → 恢复同一媒体位置、应用删除
  → 全程没有 window.sharedElementReturnTransition，没有第二段缩放
```
手势取消：平台恢复窗口，应用的 Session 未 beginReturn、未提交 result，页面保持可交互（V03）。

应用拥有（下拖，任意 API）：
```
touch 下拖 → mediaDismissProgress → beginInteractiveDismiss + renderInteractiveDismissProgress
  → 释放 → mediaDismissSettled(true) → finish() → requestViewerClose("finish")
  → 安装 mediaTransition 共享元素回程 → super.finishAfterTransition()
```
该路径未受本轮改动影响。

## 4. 必须保留且已检查的契约

- 媒体身份/返回位置：`EXTRA_RETURN_PATH` 与原路径同样的 `getCurrentPath()`；父端 `restoreViewerReturnPositionWithoutTransition` 在 `!viewerReturnMapped` 时恢复。
- 删除同步：`successfullyDeletedViewerPaths` 同样写入 result；父端 `applyViewerDeletedPaths` 不变。
- 保护目录/收藏鉴权：仅在 commit 时通过 result/正常 finish 触发；取消路径无副作用。
- 入场未完返回：`finishDuringMediaEntry()` 仍在 `requestViewerClose()` 中优先执行；`platformOwnsCurrentReturn()` 在 `entryActive`（非 VIEWING）时返回 false，因此不会绕过淡出收尾。
- 共享元素入场：`configureMediaTransitionSession()` 的 enter/share callback 未改动。
- 连续进入/返回：Session `beginReturn` 唯一 token、`terminate` 一次性、`clean()` 保持原有 onDestroy 清理。

## 5. 未验证项（PENDING_DEVICE_VALIDATION）

- 平台跨 Activity 动画在 translucent + `windowContentTransitions` 的 Viewer 上是否真正播放（H-TRANSLUCENT）；若系统因透明/场景转场回退到回调，需要真机证据后决定是否调整 Viewer 窗口，本轮**不改主题**。
- 左右边缘的真实视觉方向、圆角、父网格可见性。
- `PhotoViewerInteractiveGestureTest`（androidTest）通过 dispatcher 模拟 back 并假定稳定页有启用回调；该假设按新所有权模型已改变，需在真机上把这些用例改为“在 in-app step 内触发”或改为断言平台路径。**本轮未擅自修改仪器测试用例，也未声称其通过。**
