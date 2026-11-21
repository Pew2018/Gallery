# 阶段 4B：系统与预测性返回

## 起点与状态

2026-11-21，从 4A 交付 HEAD 7a9a57222 继续。沿用 perf/home-gallery-sorting，未新建分支或工作树，未改写历史，未强推。

本文件覆盖 4B 首轮交付与随后针对性复核。当前结论：**代码完成、自动化通过、待用户真机视觉验收**。

## 能力核查（含首轮错误结论的修正）

| 事实 | 依据 |
| --- | --- |
| compileSdk / targetSdk 36，minSdk 26 | gradle/libs.versions.toml |
| androidx.activity 编译期 1.8.0、运行时 1.12.4 | `:app:dependencies` 实测；`handleOnBackStarted/Progressed/Cancelled` 自 1.8.0 提供 |
| **Android 13/14/15 必须在清单里显式 opt-in** | AOSP `ParsingPackageUtils`（14/15）中 application 级该属性默认为 `false`，且不在 targetSdk gated 分组内；`ApplicationInfo.isOnBackInvokedCallbackEnabled()` 仅在显式置位时为真；`ActivityInfo.isOnBackInvokedCallbackEnabled()` 仅在活动自身显式置位时为真（`PRIVATE_FLAG_ENABLE_ON_BACK_INVOKED_CALLBACK`） |
| Android 16+（targetSdk 36）默认启用 | 官方文档；本机 API 37 实测未设标志时仍能收到进度（见下） |
| 无需修改依赖 | activity 1.8.0/1.12.4 已含 `BackEventCompat`、`dispatchOnBack*` 与动画回调 |

首轮交付曾写“无需修改 Manifest”，该结论只对新系统成立，**对 Android 13/14/15 是错的**：不设该属性时平台忽略 `OnBackInvokedCallback`，只走旧 `onBackPressed`，预测性进度永远收不到。本轮按官方“活动级 opt-in”机制只给 Viewer 补了一行清单属性，未采用应用级开关，避免影响主页与文件夹页的既有转场。

平台与应用分工：

- **平台**：识别边缘手势、判断能否返回、在 API 34+ 派发 started / progressed / cancelled，最终派发 invoked；应用未 opt-in 时完全不派发。
- **应用**：`ViewerBackGesture`（默认优先级 `OnBackPressedCallback`）接收上述回调，进度驱动**既有**下拖包络，取消恢复原状态，提交走 4A 已验证的共享元素回程。
- **API 29/33**：没有进度；只收到最终返回，行为与普通返回一致。
- **API 34+**：派发进度；默认优先级回调使平台不再对同一窗口播放跨 Activity 系统动画。

## 实际修改

1. `helpers/ViewerBackGesture.kt`：包装 started / progressed / cancelled / invoked，全部委托既有能力，不新建协调器或返回实现。
2. `ViewPagerActivity` 注册该回调，并把 `mediaDismissProgress` 中“取得共享表面 + 网格预览 + 遮罩 + 租约”的逻辑抽成 `beginInteractiveDismiss()`，供下拖与系统返回共用；下拖行为未变。
3. `beginSystemBackGesture` / `renderSystemBackProgress` / `cancelSystemBackGesture` / `commitSystemBack`：开始、进度、取消、提交分别映射到既有交互返回、`mediaDismissSettled(false)` 与 `mediaDismissSettled(true)`。
4. **AndroidManifest**：仅给 `ViewPagerActivity` 增加 `android:enableOnBackInvokedCallback="true"`。这是本轮唯一清单改动，未改依赖、未动其他 Activity、未改返回优先级。
5. **复核修补（本轮）**：取消回弹未结束时再次返回会跳变。平台的新手势进度从 0 重新开始，原实现直接用该值覆盖接管帧。现在 `beginSystemBackGesture` 记录接管时的实时进度 `systemBackResumeProgress`，`renderSystemBackProgress` 按“接管帧 + 手势进度”映射（与下拖控制器 `baseProgress + delta` 的既有约定一致），并在取消/提交后清零。未新增动画、未延长等待、未改动 Session 或清理机制。

## 证据：真实系统边缘手势

用临时审计用例（已从仓库移除，只保留结论）在同一台设备上开启手势导航后注入真实边缘滑动，逐帧读取 Session 状态与进度：

| 设备 | 清单标志 | 结果 |
| --- | --- | --- |
| Android 15（API 35，自建 AVD） | 无（对照） | 28 个采样点全部 `VIEWING`、进度 0，手势完全没有进入应用 |
| Android 15（API 35） | `enableOnBackInvokedCallback="true"` | 第 4 个采样点进入 `INTERACTIVE_RETURN`（0.0023），逐步升到 0.404；回撤到边缘抬起后回到 `VIEWING` |
| Android 16（API 37，审查用 AVD） | 有 | 同样进入 `INTERACTIVE_RETURN`，进度升到 0.4079，取消后 `VIEWING` |

这组 A/B 只改清单、代码相同，说明修补在受影响的系统上确实生效；也说明仅调用 `dispatchOnBackStarted/Progressed` 的用例不能替代系统接线验证。

## 验证结果

使用 JDK 21 与项目 Wrapper。

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,279 tests，0 failures / 0 errors / 0 skipped（`--rerun-tasks`） |
| Lint FossDebug | 非零：15 errors、806 warnings、2 hints，与 4A 起点逐级一致；改动文件内仅原有的 `GestureBackNavigation` 存量错误，无新增规则 |
| Detekt | 非零：3,461 条 / 4,029 weighted，与起点一致；`ViewerBackGesture`、`ViewPagerActivity` 无新增条目 |
| ARM64 FOSS Release | 构建成功（本地未签名，安装包用同提交 CI 产物） |
| API 35 预测性进度/取消/提交 | 通过（11.6 s） |
| API 35 接管连续性 | 连续 5 轮（每轮先跑另一预测性用例再跑本用例）全部通过，10–15 s；接管帧 0.068→0.078、0.533→0.543，下拖回弹 0.690→0.700，均为“接管帧 + 0.01” |
| API 29 返回入口 | 通过（13.1 s） |
| API 29 真实按键返回 | 通过（4.9 s） |
| API 29 50 次混合往返 | 通过（67.2 s） |
| API 29 完整下拖 | 冷启动首个用例曾 2 次在**入场**阶段降级（`mapped=false, fadeFallback=true`）失败；同一镜像随后连续 3 次通过（48–50 s）。失败点在入场共享元素，不在本次改动的返回路径，未据此修改生产代码 |

测试侧同时修正两处自身缺陷（非产品问题）：读取已终止 Session 的 `outcome` 时为空导致格式化 NPE；提交后立即断言 `outcome` 而未等待网格完成 `CLEANED`。修正后连续 5 轮稳定。

## 源码与安装包身份（复核修补版）

| 项 | 值 |
| --- | --- |
| 分支 | perf/home-gallery-sorting |
| 复核修补提交 | b037264c2e18f334545fcb0503e56677d5222d3e |
| 远程同步 | 本地与 origin 一致（推送后确认） |
| CI | https://github.com/Pew2018/Gallery/actions/runs/44919317153（success，headSha 与源码提交一致） |
| APK | /Users/patrick/Downloads/gallery-4b-review/gallery-foss-arm64-v8a-release/gallery-foss-arm64-v8a-release.apk |
| SHA-256 | 1c654eb077bf943bb7c801ac11858c04c31cfbbaff905aa7c847d5a90027d64b |
| 包名 / 版本 | org.fossify.gallery，versionCode 32，versionName 1.14.1 |
| ABI | arm64-v8a |
| 签名 | CN=Gallery Temporary CI Test Signature, O=Local Testing（cert SHA-256 eb2c3cd8f67120d0c7c56e0b5b2fd0880ce857191948b7ca0a7985b7add15b47） |

该包取代首轮 4B 交付（提交 5eb7ac841，APK SHA-256 09ca6a69…）：首轮未在清单里 opt-in，Android 13/14/15 收不到预测性手势，且存在取消回弹接管跳变。工作流在缺少仓库密钥时每轮生成新的临时密钥，因此 4A、首轮 4B、本轮 4B 三个包互不兼容，也未必能覆盖 3.5 的旧安装；签名冲突时由用户决定处理方式，本轮未卸载应用或清除数据。

## 未验证项

- Pixel 8 Pro 真机上的实际手感：进度为等比缩放 + 遮罩/顶栏淡出，是否匹配系统手势需真机判断。
- 真机横屏、大字体与长图组合；本轮只有 API 29/35/37 模拟器。
- Android 15 上系统关闭动画与本应用回程的真机衔接（模拟器只验证到状态与进度，不是视觉结论）。

## 交付与验收步骤

1. 安装本轮 ARM64 Release（临时 CI 签名不能保证覆盖旧安装；签名冲突时请自行决定处理方式，本阶段不卸载应用、不清数据）。
2. Android 14/15 设备上从边缘慢速内滑：画面应随进度缩小并露出网格，顶栏/遮罩同步淡出；这是本轮修补的关键点。
3. 中途松手：画面、顶栏、遮罩、输入恢复到滑动前；**在回弹未结束时立刻再滑一次**，画面不得跳回接近全屏。
4. 滑过阈值提交：沿用 4A 回程落到同一缩略图；A→B 返回 B。
5. 依次抽查工具栏返回、3 键/硬件/无障碍返回，确认与手势提交走同一条回程且只结束一次。
6. API 29 设备确认普通返回仍可靠。

## 阶段结论

代码完成、自动化通过。用户确认前不标记 4B 或整个第四阶段验收通过。
