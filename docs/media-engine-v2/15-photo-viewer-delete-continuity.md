# 全屏删除后的照片浏览连续性

> **历史缺陷复现及修复记录（非当前任务）**：原文的目标分支、执行规则、完成结果和下一步仅适用于当时提交。当前媒体索引/加载/排序/稳定性工程使用 [18 — 现行路线图](18-current-media-pipeline-roadmap.md)、[05 — 实施细则](05-minimal-scan-sort-display-refactor.md)。保留旧数据、旧修复和测试结果作为证据，不把历史 Actions 通过视作新阶段性能/真机验收。

日期：2026-11-04（Asia/Taipei）  
目标仓库：`Pew2018/Gallery`  
唯一修改分支：`fix/photo-viewer-five-item-boundary`

## 本轮基线与上一轮修复

- 开始时重新读取的远程分支 HEAD：`dfe98a83a55e350194bd8fc23da89c45311fee77`。
- 最近提交包含 `1ef92e718`（渐进加载期间批量删除稳定性修复）和 `540a410c8`（补回完整 Activity 源码）；上述提交均保留。
- `docs/media-engine-v2/14-progressive-loading-batch-delete-crash.md` 记录的稳定路径仍在：删除以 path 为身份、只按已确认的删除结果更新，并启动新扫描以拒绝旧发布。
- 没有用户设备的 Logcat 或录屏逐帧时间戳；下文区分源码已证实的控制流和对黑屏现象的工程解释。

## 核实的删除调用链与退出路径

成功删除路径为：

`checkDeleteConfirmation()` → `deleteConfirmed()` → Android 11+/SAF 管理授权回调 → `tryDeleteFileDirItem()` 或回收站移动完成 → `hideDeletedMedium()` → 照片配置恢复 → `refreshUI()` → 既有 `MyPagerAdapter.updateMedia()`。

删除确认后会固定原路径和 Viewer 列表/位置快照；异步系统授权期间即使当前页变化，也不会改为删除新的当前页。删除失败不进入移除回调，继续保留原列表。

本轮前源码中确认的退出误判：

| 位置 | 触发条件 | 数据是否完整 | 处理 |
|---|---|---|---|
| `commitPhotoCatalogMutation()` | 删除后传入的 `items.isEmpty()` | 可能只是 Viewer 当前的一个照片配置邻近窗口 | 删除改走恢复流程；空窗口先重新扫描 |
| `hideDeletedMedium()` | 通用媒体发布结果为空 | 普通目录可按原语义处理；照片配置结果可能是渐进窗口 | 照片配置不再走通用空列表结束路径 |
| 照片配置初次 `publish()` / `continuousItems` | 结果为空 | 由 `complete` 标记区分 | 仅 COMPLETE 空结果结束，局部空结果等待后续阶段 |
| `gotMedia()` → `isDirEmpty()` | 发布空列表 | 可能是局部阶段 | 照片配置仅完整目录的空结果结束 |
| 权限、会话撤销和外部 Intent 分支 | 授权无效、权限丢失、外部调用结束 | 不是目录为空推断 | 保持原有安全退出行为 |

删除 C 后旧 `ViewerPositionPolicy` 通过 `previousPosition.coerceIn()` 选择新列表的同一整数位置，常见结果是 D。通用扫描更新继续使用该身份保持策略；删除专用导航由 `ViewerDeletionNavigationPolicy` 处理：用户在系统对话框期间已经切到且仍有效的当前照片优先，其次上一张实际浏览历史，再按删除前排序选择前一张、后一张。大型/部分窗口缺少足够邻项时，使用点击网格保存的路径顺序和后台完整目录恢复，不从旧整数下标猜完整目录位置。

## 黑屏/闪烁的代码时序

照片目录失效会令 `photoCatalogUpdating = true` 并清除当前 revision 的页面授权。Adapter 对未授权路径会使用延迟页面容器；如果空的或过渡的媒体列表先提交，Fragment 可能先被移除/替换为空容器，再等待授权结果。源码能证明这个空页面窗口存在于“先发布、后验证”的顺序中；缺少真机帧捕获，不能断言它是用户录屏中每一帧黑闪的唯一来源。

修改后，现有已显示 Fragment 保持附着；复用 Photos 的既有 staged loader 重新读取当前 scope。恢复目标和邻近窗口在工作线程经 `PhotoViewerSnapshotStore.validateNeighborWindow()` 验证；当前 revision、Profile/session、恢复 generation 和 Viewer 生命周期再次确认后，先重置并填入对应授权，再用现有 Adapter 一次更新数据及按媒体 path 定位。局部空/中间列表不会先交给 Adapter；不会将 Adapter 清空重建。扫描中的短暂等待复用既有 Viewer 加载提示和窗口背景。未绕过受保护目录、收藏或 SAF 授权。

`PhotoContentRevision.changed()` 对永久删除由恢复入口负责一次。回收站移动已经在 `movePathsInRecycleBin()` 的操作边界增加 revision，Viewer 回调识别该路径已更新，避免重复增加。随后启动扫描不再再次变更照片 revision、重置同一 Catalog 或失效刚显示照片的解码签名。

## 实际实现

- `ViewPagerActivity.deleteConfirmed()`：删除前固定目标 path、旧排序/局部列表、位置和浏览历史；删除及 SAF 回调仍绑定固定 path。
- `recoverAfterPhotoDeletion()`：统一处理 Photos 配置删除；标记已删除 path、失效旧 publication、进入恢复态，复用已有 staged loader，不先发布局部删除列表，保持 Activity 与当前 Fragment。
- `recoverPhotoDeletionFromLoadResult()`：从新阶段或完整结果选择替代媒体，验证邻近授权后更新已有 Adapter；如果用户在授权期间换页，先验证并保留新当前页。generation/revision 过期结果拒绝；完整、已授权且空的结果才结束。
- `refreshViewPager()`/`gotMedia()`：允许照片配置局部空阶段保留 Viewer；扫描参数避免对仍显示的已授权页做额外内容失效。
- `onPageSelected()`：仅在 Viewer 会话内记录实际上一页路径。
- `ViewerDeletionNavigationPolicy.kt`：独立的删除导航顺序及完整空目录退出判定；不改变一般排序或异步列表的锚点策略。
- `MyPagerAdapter` 未改代码；恢复复用现有 path/type Fragment 身份与 Adapter 更新流程。

## 回归覆盖

- `ViewerDeletionNavigationPolicyTest`：A/B/C/D 删除中间、首尾照片；有效浏览历史及历史失效；单项渐进窗口+完整网格顺序；局部空未知与完整空；删除失败保留原路径；临时授权期间用户换页。
- `PhotoViewerProgressivePagerTest`：当前渐进窗口仅含 C 时删除，空窗口不推断结束且旧 Fragment 保留；完整目录恢复后用同一 Adapter 选中 B。
- `MyPagerAdapterLifecycleTest`：验证 C 删除后策略选中 B、旧 C Fragment 被移除、新 Fragment 的媒体身份是 B；未授权延迟页只在访问窗口更新后恢复；一般位置策略旧断言保持。
- `PhotoProfileEmulatorTest.deletingOnlyLoadedPhotoKeepsViewerOpenAndRecoversPreviousGridNeighbor`：真实 Android 文件删除和 Photos 配置/全屏 Activity；把全屏发布窗口控制为仅当前一张，调用成功删除后的生产回调，验证 Viewer 未结束、Adapter 未重建、C 不复活、最终显示 B 且图片 Drawable 存在，之后继续滑到 D。
- 上述模拟器场景绕过系统删除确认弹窗，直接模拟成功文件删除后的回调；系统 MediaStore/SAF 对话框没有被自动化。它不等于真机复现或逐帧黑屏验证。

## 验证与交付状态

- 后续调整（2026-11-04）：用户要求移除 Actions 中的模拟器测试代码。其 workflow、instrumentation 用例及夹具脚本已从活动构建路径移除，原文归档在 `docs/emulator-testing/ARCHIVED-harness-source.md`。下文的模拟器测试条目描述的是归档前的测试设计，不表示当前分支仍会运行它。

- 初次本地定向 Gradle 测试尝试未启动：Wrapper 下载 Gradle 9.5.1 时返回 `Network is unreachable`。这是环境阻断，不是测试通过。
- `git diff --check`：通过。
- 本页原记录中的 GitHub Actions、完整单测/模拟器矩阵和 APK 状态属于删除模拟器 harness 前的执行任务；删除提交之后不再触发模拟器矩阵。常规单测与 ARM64 APK workflow 保持独立。
- 真机剩余验证：在用户实际设备上，使用 Photos 配置、渐进扫描中删除当前图，观察是否停留 B、能继续到 D、无黑屏帧及受保护目录/SAF 删除行为。
