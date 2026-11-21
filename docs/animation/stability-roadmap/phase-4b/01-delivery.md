# 阶段 4B：系统与预测性返回

## 起点

2026-11-21，4A 代码与测试提交 c02bb933fb7bdbbba29787b2639c2ee0d0472e83，交付身份记录随后单独提交。沿用 perf/home-gallery-sorting，未新建分支或工作树，未改写历史，未强推。4A 的产物与说明保持独立，本文件只覆盖 4B。

当前结论：**代码完成、自动化通过、待用户真机视觉验收**。

## 能力核查（有边界，未做全面兼容研究）

| 事实 | 依据 |
| --- | --- |
| compileSdk / targetSdk 36，minSdk 26 | gradle/libs.versions.toml |
| androidx.activity 编译期 1.8.0、运行时解析到 1.12.4 | `:app:dependencies` 实测；`OnBackPressedCallback` 的 `handleOnBackStarted/Progressed/Cancelled` 自 1.8.0 提供 |
| 无需修改 Manifest | 官方文档：应用 targetSdk 到 36 时预测性返回默认启用，无需 `android:enableOnBackInvokedCallback`；合并后的 Manifest 未设置该标志，`<application>` 也未 opt out |
| 无需修改依赖 | 现有 activity 版本已包含 `BackEventCompat`、`OnBackPressedDispatcher.dispatchOnBack*` 与动画回调 |

平台与应用的分工：

- **平台**：识别边缘返回手势、判断能否返回、在 API 34+ 通过 `BackEventCompat` 派发 started / progressed / cancelled，最终派发 invoked。
- **应用**：本阶段由 `ViewerBackGesture`（`OnBackPressedCallback`，默认优先级）接收上述回调，把进度映射到**既有**下拖包络，取消时恢复原状态，提交时走 4A 已验证的共享元素回程。
- **API 29**：没有任何预测性进度，只收到最终返回，行为与 4A 的普通返回完全一致。
- **API 33**：预测性返回已启用，但不派发进度；同样只走最终返回。
- **API 34+**：派发进度；应用在默认优先级消费该手势，平台因此不再对同一窗口播放跨 Activity 系统动画，避免两个动画同时驱动同一图层。
- **API 35/36**：系统动画默认开启，同样被上述默认优先级回调取代，视觉仍由本应用负责。

## 实际修改

1. 新增 `helpers/ViewerBackGesture.kt`：`OnBackPressedCallback` 包装 started / progressed / cancelled / invoked 四个入口，全部委托给 `ViewPagerActivity` 的既有能力，不新建协调器、不新建返回实现。
2. `ViewPagerActivity` 在 `onCreate` 注册该回调（生命周期感知），并在 `mediaDismissProgress` 中把“取得共享表面 + 网格预览 + 遮罩 + 输入/资源租约”的原逻辑抽成 `beginInteractiveDismiss()`，供下拖与系统返回共用；下拖行为未变。
3. 新增 `beginSystemBackGesture` / `renderSystemBackProgress` / `cancelSystemBackGesture` / `commitSystemBack`：
   - 开始：若已有 settle 回弹动画占用表面，则在当前进度接管，不重复建立包络；否则从 `VIEWING` 进入既有交互返回。
   - 进度：`BackEventCompat.progress`（0–1）直接驱动既有 `renderInteractiveDismissProgress`；系统手势没有纵向位移，斜率置 0，不引入固定像素偏移或新几何。
   - 取消：复用 `mediaDismissSettled(false)` 的回弹动画与 `cancelGesture` → 释放租约 → 恢复画面、顶栏、遮罩与输入。
   - 提交：复用 `mediaDismissSettled(true)` 交出实时帧，随后仍由 `requestViewerClose` → 4A 的回程核心、目标绑定与版本校验完成。
4. 舞台关闭动画、`finishAfterTransition` 优先级、Session 终结与 `platform_finished_without_motion` 退化规则均未改动。

未做：没有改依赖或 Manifest，没有新增选择态动画、文件夹页面动画、视频或特殊媒体共享资格，没有把返回统一降级到某个固定版本。

## 返回优先级

- 对话框仍是独立窗口，先于 Activity 收到返回，行为未变。
- Viewer 内不存在搜索、IME、选择态或“返回退出放大”这类既有分支；`onBackPressed()` 覆写保留给回调未生效的路径，最终仍进入 `requestViewerClose`。
- 3 键导航、硬件键、无障碍返回与手势返回都走同一个 dispatcher；按键路径本轮用真实 `KEYCODE_BACK` 分发验证。

## 验证结果

使用 JDK 21 与项目 Wrapper。

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,279 tests，0 failures / 0 errors / 0 skipped（`--rerun-tasks`） |
| Lint FossDebug | 非零：15 errors、806 warnings、2 hints，与 4A 起点逐级一致；未新增 `ViewerBackGesture` 相关条目，未扩大 baseline |
| Detekt | 非零：3,461 条 / 4,029 weighted，与 4A 起点一致；`ViewerBackGesture` 无任何条目，未修改 baseline |
| ARM64 FOSS Release | 构建成功（本地未签名，安装包用同提交 CI 产物） |
| API 29 返回入口 | 通过（10.2 s）：工具栏 / Back / 取消后重拖提交 / 拖动中退出 |
| API 29 50 次混合往返 | 通过（72.8 s）：finish / Back / 工具栏 / 中断下拖交替，每轮独立 Session 与单一终态 |
| API 29 完整下拖 | 通过（50.5 s） |
| API 29 真实按键返回 | 通过（3.3 s）：`KEYCODE_BACK` 经窗口分发关闭，单一终态、CLEANED、无残留 |
| API 37 预测性返回进度 | 通过（5.6 / 5.9 / 6.2 s，三次）：started 进入 `INTERACTIVE_RETURN` 并建立 `MeasuredPhotoReturnView` 表面；progress 0.45 映射到共享包络；cancelled 恢复 `VIEWING`、代理清空、进度归零、遮罩隐藏；再次 started + progressed + invoked 提交后单一终态、CLEANED、无 transitionName、输入锁释放 |
| API 37 真实按键返回 | 通过（4.7 s） |

API 37 使用本轮新建的临时 AVD（系统镜像 android-37.0，arm64，软件渲染），测试结束保留在 `/Users/patrick/.android/avd/Gallery_API37.avd`。API 29 沿用原 AVD。

API 37 上曾出现一次 4A 用例在读取已关闭 Viewer 的 Session 字段时失败，随后同条件 6 次运行（含 3 次连续）均通过；未改动生产代码，只在测试中补了“Session 缺失”的明确断言信息，未把该次失败归为产品缺陷，也不声称已定位根因。

## 源码与安装包身份

| 项 | 值 |
| --- | --- |
| 分支 | perf/home-gallery-sorting |
| 4B 源码提交 | 5eb7ac841e272b2143ed8d54cb90415990d5d730 |
| 远程同步 | 本地与 origin 一致（推送后确认） |
| CI | https://github.com/Pew2018/Gallery/actions/runs/44909463538（success，headSha 与源码提交一致） |
| APK | /Users/patrick/Downloads/gallery-4b/gallery-foss-arm64-v8a-release/gallery-foss-arm64-v8a-release.apk |
| SHA-256 | 09ca6a6995f1f9eeb6602e1fa8227e5314a62fa80fa1d71487c9f463643e7405 |
| 包名 / 版本 | org.fossify.gallery，versionCode 32，versionName 1.14.1 |
| ABI | arm64-v8a |
| 签名 | CN=Gallery Temporary CI Test Signature, O=Local Testing（cert SHA-256 46886cdc75e92710d522663eb50e10a1d00b5ea75f91aa2866e2330fa5150379） |

工作流在缺少仓库密钥时每次运行都会生成新的临时密钥，因此：

- 本包**不能**保证覆盖 3.5 或 4A 的旧安装；本轮 4A 包证书为 fa004b5d…，4B 为 46886cdc…，两者互不兼容。
- 若安装时报签名冲突，需要用户自行决定处理方式；本阶段未卸载应用、未清除数据，也未改用其他签名体系。
- 4A 与 4B 的 APK 分别保存在 `/Users/patrick/Downloads/gallery-4a` 与 `/Users/patrick/Downloads/gallery-4b`，便于独立审查；后续使用以 4B 包为准。

## 未验证项

- Pixel 8 Pro 真机上的真实边缘手势观感：进度映射为等比缩放与遮罩/顶栏淡出，是否与系统手势手感匹配需要真机判断。
- Android 15/16 设备上系统关闭动画与本应用回程的实际衔接（本轮只有 API 29 与 API 37 模拟器）。
- 真实手势下的横屏、大字体与长图组合，本阶段未逐一在 37 上跑矩阵。

模拟器结果不能替代真机视觉验收；本阶段的“取消恢复”“提交沿用回程”“按键路径”为自动化结论。

## 交付与验收步骤

1. 安装本轮 ARM64 Release（临时 CI 签名不能保证覆盖上一轮安装；若签名冲突，请自行决定处理方式，本阶段不会卸载或清应用数据）。
2. 在支持预测性返回的系统上，从屏幕边缘慢速内滑：画面应随进度缩小并露出网格，顶栏/遮罩同步淡出。
3. 中途松手：画面、顶栏、遮罩、输入应恢复到滑动手势之前，随后立即再滑一次应正常，不受上次回调影响。
4. 滑过阈值提交：应沿用 4A 的回程落到同一缩略图，A→B 返回 B。
5. 依次抽查工具栏返回、3 键/硬件返回与无障碍返回，确认与手势提交走同一条回程且只结束一次。
6. 在 API 29 设备上确认普通返回仍可靠，无手势预览。

## 阶段结论

代码完成、自动化通过。用户确认前不标记 4B 或整个第四阶段验收通过。
