# Phase 2.3 — Viewer 闪退、照片配置白闪及预测性返回可靠性

## 1. 开发基线

| 项 | 值 |
|---|---|
| 任务审计基线 | `110ab3f457c139eb098b74422f06916927ffd881` |
| 实际初始远程 HEAD | `110ab3f457c139eb098b74422f06916927ffd881`（与基线一致，无并发提交）|
| 最终提交 | 见下方「构建与交付」|
| 分支 | `perf/home-gallery-sorting`（唯一修改与提交分支）|
| 用户 APK 源码 | `110ab3f457c1`（基线前缀）|
| Glide 实际版本 | 5.0.7（`gradle/libs.versions.toml` 的 `glideCompiler` + 缓存中解析的 `com.github.bumptech.glide:glide:5.0.7`）|

开始工作前已重新获取远程 HEAD 并与基线比对；`git log 110ab3f45..origin/perf/home-gallery-sorting` 为空。未重置历史、未覆盖其他 Agent 修改、未新建分支或 PR。

原始诊断文件 `Gallery-Diagnostics-20261125-020507.txt` 与 `1000041412.mp4` **未随任务提供到执行环境**；本记录中的日志时间、事件序列与录屏位置均直接采用任务描述中的关键事件，未能独立复核原始文件。

## 2. 用户真机问题

| 编号 | 问题 | 优先级 | 本次结论 |
|---|---|---|---|
| BUG-01 | Viewer 切换照片后频繁闪退 | P0 | 根因确认并修复 |
| BUG-02 | 配置管理及返回时白闪 | P1 | 确认显示状态清除与重载之间的空窗，实施事务化修复 |
| BUG-03 | 已显示缩略图再次变占位块 | P1 | 未获得可复现调用链证据，未做推测性改动 |
| BUG-04 | 偶发无法唤起 Viewer 退出动画 | P1 | 完成源码审计和诊断增强，仍需真机捕获异常事件 |
| BUG-05 | 偶发无法唤起退出 App 系统动画 | P1 | 完成源码审计和诊断增强，仍需真机捕获异常事件 |

## 3. 关键证据

- 五次崩溃均紧随 `grid_target_prefetch_started`（时间差 6–127 ms），随后出现进程会话变更和 `previous_exit reason=4`。
- 最后一次完整异常：`java.lang.IllegalStateException`，主线程，调用链包含
  `Glide.clear(...)` ← `MediaActivity$beginViewerReturnPrefetch$target$1.onResourceReady(...)`
  ← `MediaActivity.beginViewerReturnPrefetch(...)`。其余四次只具备高度一致的前置事件，不声称拥有完整堆栈。
- 配置切换空窗：第一次 `display_state_cleared` 到新配置提交约 207 ms；第二次约 151 ms。这些是业务事件间隔，不是物理屏幕白闪时长。
- 录屏关键位置：约 26.25/27.00/27.15/27.60/30.45/30.60/31.35–32 秒。

## 4. 已确认根因

### BUG-01

`MediaActivity.beginViewerReturnPrefetch()` 在 `CustomTarget.onResourceReady()` 内同步调用
`Glide.with(applicationContext).clear(this)`。Glide 5.0.7 的 `SingleRequest` 在资源回调期间把
`isCallingCallbacks` 置为 `true`，而 `clear()`/`begin()`/`cancel()` 都会调用
`assertNotCallingCallbacks()`，因此抛出 `IllegalStateException`。异常消息明确要求把 `clear()` 投递到主线程队列，而不是在回调内同步执行。

项目中的真实 Glide 集成测试先证明了同步 `clear()` 会被真实依赖拒绝，再证明修复后的延迟 `clear()` 在同一真实依赖上可以正常完成。

### BUG-02

配置切换被三个入口重复处理：`PhotoProfileManageActivity.useProfile()` → `SharedPreferences` 监听 → `clearPhotoDisplayState()` → 子页面 `finish()` → `onActivityResult()`/`onResume()` → 再次加载。
监听回调在子页面仍覆盖网格时同步清掉 Adapter（`discardInaccessibleGrid()` 中 `binding.mediaGrid.adapter = null`），而新配置结果要等子页面返回后才提交，造成纯色空窗。`getPhotoProfileMedia()` 在 SCOPE_CHANGED 时也会在开始加载前清空 Adapter。

这不是「背景颜色错误」，也不是同配置返回的问题；同配置返回已有 `PhotoChildReturnPolicy` / `PhotoGridRetentionPolicy` 覆盖。

## 5. 未确认风险与证据缺口

- BUG-03：没有事件证实「相同媒体再次绑定 → 旧 Drawable 被清除 → placeholder → 重复请求」链条，故未修改 Adapter/DiffUtil/Glide key 逻辑。
- BUG-04：无完整异常日志；源码审计发现 `beginSystemBackGesture()` 和 `requestViewerClose()` 存在多个静默 return 分支，但不能据此断言某一个就是真机根因。本次仅增加可观测事件与确定性状态机测试，不宣称已修复。
- BUG-05：`BackStepPolicy`、Manifest 的 `android:enableOnBackInvokedCallback="true"`、根页面不启用 `OnBackPressedCallback` 均符合 Android 16 官方要求；未找到可证实的直接缺陷，不试错修改。Android 16 原生动画仍需真机验收。
- 原始日志和录像缺失，所有真机结论均基于任务提供的关键事件。

## 6. 修复前后调用链

### BUG-01

修复前：

```text
grid_target_prefetch_started
  → CustomTarget.onResourceReady
      → ViewerReturnPixelReserve.remember
      → viewerReturnPrefetch.onFinished
      → Glide.with(app).clear(this)        # 同步，位于 isCallingCallbacks=true 区间
      → IllegalStateException → 进程死亡
```

修复后：

```text
grid_target_prefetch_started
  → CustomTarget.onResourceReady
      → ViewerPrefetchLease.ready
          → 资源字段采集 + 像素防御性复制（仍在 Glide 资源有效期内）
          → 单一终态 READY + onResolved（协调器/诊断）
          → scheduler.post(release)         # 专用 Handler，不参与动画 handler 的 mass-clear
  → 回调返回，Glide 释放 isCallingCallbacks
  → release Runnable 执行 → 真实 Glide.clear(target) → onLoadCleared
```

所有清理路径（成功、失败、页面变化、被替代、Viewer 退出、手势取消、Activity 销毁、session 结束、过期回调、延迟释放时已有新请求）统一收敛到 lease 的单一终态；旧 lease 只释放自身 target。请求身份由 generation + viewerSessionId + path/key + Target 实例共同判定，可区分 A→B→A 中两次相同 path/key 的请求。

150 ms 稳定窗口、当前媒体跟随、快速翻页取消、像素储备容量/预算、返回时消费储备、未准备完成时的降级均保留。`ViewerReturnPrefetchCoordinator` 与 `ViewerReturnPixelReserve` 未被禁用或重构。

全项目同类缺陷审计：检查了 `EditActivity`、`ViewPagerActivity`、`PhotoFragment`、`VideoFragment`、`MediaAdapter`、`Context.kt`、`MediaActivity` 中其余 `RequestListener`/`CustomTarget`/`clear()` 路径，未发现其他在回调内同步调用 Glide `clear()`/`into()` 的情况；未做无关 Glide 重构。

### BUG-02

修复前：

```text
useProfile → SharedPreferences 监听
  → mediaLoadRequests.invalidate + stopFetching + clearPhotoDisplayState
  → discardInaccessibleGrid  → adapter = null
  → [150–207 ms 空白]
  → 子页面 finish → onActivityResult → reloadPhotoProfile → 新配置首屏
```

修复后：

```text
childPageCovered → PhotoProfileHandoffCoordinator.CONFIGURATION_COVERED
useProfile → SharedPreferences 监听
  → onScopeChanged：仅当「子页面覆盖 + 访问未变 + 真正切换配置」时
      → deferDisplayClear = true，startTargetLoad = true
      → 立即为目标 scope 启动加载，旧 Adapter 保持挂载
      → getPhotoProfileMedia 中跳过 discardInaccessibleGrid
  → 目标首屏 publishMediaSnapshot 一次性替换内容 → onPreviewCommitted / onGridCommitted
  → Activity result / onResume 对同一 target 只做 merge，不再启动第二个 worker
```

权限、密码、来源绑定、筛选条件变化等仍然立即走原清理路径；`clearPhotoDisplayState()` 未被删除或弱化，`FLAG_SECURE`、受保护目录、媒体身份校验保持不变。

## 7. 修改文件与关键方法

| 文件 | 关键改动 |
|---|---|
| `app/src/main/kotlin/.../helpers/ViewerPrefetchLease.kt`（新增）| 单次请求的身份、单一终态、像素复制与延迟释放 |
| `app/src/main/kotlin/.../activities/MediaActivity.kt` | `beginViewerReturnPrefetch` / `cancelViewerReturnPrefetch` / `endViewerReturnPrefetch` 改为 lease 驱动；新增专用 release handler、generation/session；配置 handoff 接入监听、`getPhotoProfileMedia`、`applyPhotoGridResult`、`reloadPhotoProfile`、`onResume`；诊断字段拆分 |
| `app/src/main/kotlin/.../helpers/PhotoProfileHandoffCoordinator.kt`（新增）| 配置切换事务状态与去重决策 |
| `app/src/main/kotlin/.../activities/ViewPagerActivity.kt` | Viewer back 接受/拒绝/取消/提交/合并诊断；写回 `EXTRA_VIEWER_SESSION_ID` |
| `app/src/main/kotlin/.../helpers/AppBackOwnershipDiagnostics.kt`（新增）| 仅记录应用可观测的返回所有权状态 |
| `app/src/main/kotlin/.../activities/MainActivity.kt` | `syncAppBackStep` 增加诊断事件（不改行为）|
| `app/src/main/kotlin/.../helpers/GalleryDiagnosticLogger.kt` | 崩溃记录轮转保留最近 4 次、记录版本与前一会话；事件字段上限 16→24 |
| `.github/workflows/arm64-foss-release.yml` | 上传与 APK 同源的 `mapping.txt`、source SHA 与 mapping SHA-256 |

## 8. 新增测试与覆盖范围

| 文件 | 覆盖 | 真实生产路径 | 仍不能覆盖 |
|---|---|---|---|
| `ViewerPrefetchLeaseTest.kt` | C03–C11：身份竞争、会话结束/销毁取消、失败清理、旧 target 延迟释放、像素复制有效、100 次翻页有界、handler mass-clear 不丢失释放 | 生产使用的 `ViewerPrefetchLease` 本体 | 真实 Glide 回调时序（由集成测试覆盖）|
| `ViewerPrefetchGlideIntegrationTest.kt` | C01/C02/C12：真实 Glide 5.0.7 内存命中同步回调、异步解码回调、真实依赖拒绝同步 clear、lease 延迟 clear 成功 | 真实 Glide `RequestManager`/`SingleRequest`/`CustomTarget`；仅注入调度与 clearer 记录 | Android 16 上的实际动画与窗口合成 |
| `PhotoProfileHandoffCoordinatorTest.kt` | P01–P18 中可由纯决策逻辑验证的分支：同配置保留、多层子页面、A→B→A、缓存命中/未命中、改名、来源变化、权限撤销、重复通知、迟到扫描、空配置、阈值不匹配 | 生产 `PhotoProfileHandoffCoordinator` 与策略对象 | Adapter Diff 与实际帧提交 |
| `Phase23BackOwnershipTest.kt` | C 状态机终态/取消重试/旧 generation 隔离/租约清理；D 根页面返回所有权与诊断契约 | 生产 `MediaTransitionSessionCoordinator`、`BackStepPolicy`、诊断字段函数 | Android 16 Shell 预测性动画；BUG-04/05 真机根因 |

说明：集成测试使用真实 Glide 依赖，未使用允许任意重入的 Glide 替身。为在 host JVM 上初始化 Glide 注册表，仅对 `WebpBitmapFactory` 与 `JxlCoder` 的 native 静态初始化加了 Robolectric shadow；请求、回调与 `clear()` 均为真实实现。

## 9. 测试结果

`:app:testFossDebugUnitTest`（Temurin JDK 21，Robolectric 4.16.1，真实 Glide 5.0.7）：

| 项 | 数量 |
|---|---:|
| 总测试数 | 1653 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 本次新增 | 40（`ViewerPrefetchLeaseTest` 11、`ViewerPrefetchGlideIntegrationTest` 4、`PhotoProfileHandoffCoordinatorTest` 17、`Phase23BackOwnershipTest` 8）|
| 既有测试 | 1613，全部保留并通过 |

此前以基线代码运行 `ViewerReturnPrefetch*`/`ViewerReturnPixelReserveTest` 子集同样通过。所有新增测试均实际执行；没有删除失败测试、降低断言或用替身替换生产逻辑。

## 10. 待真机验收

- V1 Viewer 连续翻页无崩溃、无错误返回目标。
- V2 配置切换无整屏白闪、已显示缩略图不回退占位、权限变化正确清理。
- V3 Viewer 退出动画在取消/重试/快速翻页/渲染器切换下可靠。
- V4 Android 16 原生退出 App 动画与真实父页面预览可靠。

BUG-04、BUG-05：**已完成针对性代码审计及诊断增强，仍需真机捕获异常事件。**

## 11–12. 构建与交付

| 项 | 值 |
|---|---|
| 提交 SHA | `347b0707abd330aa574a5a5242e43453c0faf887` |
| Commit 链接 | https://github.com/Pew2018/Gallery/commit/347b0707abd330aa574a5a5242e43453c0faf887 |
| Actions run ID | `45542902932` |
| Actions 链接 | https://github.com/Pew2018/Gallery/actions/runs/45542902932 |
| 构建结论 | success（`arm64-release` 全部步骤通过）|
| APK 产物 | `gallery-foss-arm64-v8a-release`（包内文件名 `gallery-foss-arm64-v8a-release.apk`）|
| APK 产物链接 | https://github.com/Pew2018/Gallery/actions/runs/45542902932/artifacts/14990236936 |
| APK SHA-256 | `0ab93f11cc70f4daf8de558fd5e5e48ca6ed47a36a22a808882b07086e7e7ef6` |
| mapping 产物 | `gallery-foss-arm64-v8a-release-mapping`（含 `mapping.txt`、`source-sha.txt`、`mapping-sha256.txt`）|
| mapping 产物链接 | https://github.com/Pew2018/Gallery/actions/runs/45542902932/artifacts/14990321653 |
| mapping SHA-256 | `4a8b8dd7cb61390ae610d499e1fe6d3a1299c816740903bcad9a87301bfa7ea6` |
| APK 与 mapping 对应源码 SHA | 两者均为 `347b0707abd330aa574a5a5242e43453c0faf887`（日志中分别输出 `Verified source commit` 与 `Mapping source commit`）|
| 签名模式 | `temporary-CI-not-upgrade-compatible`（CI 临时签名；覆盖安装前需卸载旧签名版本）|
| ABI 校验 | 仅 `arm64-v8a`；包名 `org.fossify.gallery`；`apksigner verify` 通过 |

该 run 由上述提交 push 自动触发，未手动干预；构建成功后才补充本节，因此形成一个独立的文档提交（不改动代码）。

