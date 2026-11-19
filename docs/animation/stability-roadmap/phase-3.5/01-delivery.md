# 阶段 3.5：顶栏与主页进度条

日期：2026-11-20。实施前 HEAD/origin 均为 3ee851b0954f0e398a99208dd79bba319c17cb22，工作区干净；仅使用 perf/home-gallery-sorting，无新分支、工作树、历史改写或强推。

## 修改

- Commons 7.1.3 的 AppTheme.Base 继承 Material3，actionModeStyle 为 AppCompat.ActionMode → Widget.Material3.ActionMode，内容高度通过 height → actionBarSize 解析，默认 64dp。此前 OxygenToolbarStyle 的普通行自定为 56dp。
- 普通 MySearchMenu 改读同一 ActionMode 样式高度；共同取该高度与 20sp 文本加 24dp 纵向空间的较大值。选择态保持原默认高度，只在字体需要更高空间时一同适配。Insets 仍由原系统栏机制处理，不叠加状态栏高度。
- 选择期间仅普通栏内容容器 INVISIBLE，普通栏表面继续绘制，保持测量和网格位置并避免 CAB 淡出时露出页面底色；CAB 真正退出后在预绘制恢复，快速反向切换会撤销旧恢复监听。退出/分离均清理监听。Commons 仍负责选择、数量、全选和操作回调。
- MainActivity 删除 directoryHeaderProgress 的创建、挂载、样式、显示及销毁引用；保留 setDirectoryLoading 的 watchdog、超时、取消、错误及重试。主页转场 frameState 使用实际加载状态，不再通过进度条判断。MediaActivity、Viewer、对话框和下拉刷新提示未改。
- Viewer/编辑器顶栏、Session、来源资格、几何计算、Glide 交接、缩放曲线及扫描链路未改。

主要文件：OxygenToolbarStyle.kt、OxygenSelectionMode.kt、MainActivity.kt；增加 JVM 与 Android 原生 SelectionToolbarHeightTest。

## 验证

使用 JDK 21、项目 Wrapper 与既有依赖；未升级 Commons。

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,270 tests，0 failures/errors/skipped；最终生产代码与 JVM 用例此后未改 |
| API 29 原生顶栏 | 默认字体、2 倍字体、横屏均通过；真实 CAB/普通行等高、底边与网格稳定、快速反向选择及回调交接通过 |
| API 29 静态几何 | 普通文件夹、ALL_MEDIA、半可见首排各 20 组合全部通过，覆盖方形、横图、竖图、约 1:30 长图、EXIF 转置与裁切/圆角开关 |
| API 29 完整下拖 | 取消/提交、切换媒体后返回、放大状态检查等完整既有用例单独复核通过 |
| Debug / AndroidTest / ARM64 FOSS Release | 均构建成功；本地 Release 未签名，安装交付使用同提交 CI 签名包 |
| Lint FossDebug | 非零：15 errors、806 warnings、2 hints；原提交生产代码同目录、同 JDK/任务重新构建对照，按级别/规则/文件/消息多重集比较，新增 0、移除 0 |
| Detekt | 非零：原提交与最终版均 3,463 条、4,031 weighted issues；无新增规则或问题位置，原有 OxygenToolbarStyle TooManyFunctions 指标从 13 变为 15，其他问题完全一致，未更新/扩大 baseline |

初轮与合并原生检查保留失败记录：ALL_MEDIA 出现夹具不可见/共享路线或 Glide 降级断言失败；一次完整下拖在双击放大断言失败，空闲条件单独复核通过。原提交 ALL_MEDIA 对照通过后继续排查，发现既有夹具只滚动一次，后续渐进发布会改变真实位置。仅修补 Android 测试：按当前路径/位置重新驱动真实 RecyclerView，等布局与 Adapter 更新完成后捕获当前缩略图；保持原 15 秒预算、全部 20 个组合、共享路线和 Glide 断言。修补后的 ALL_MEDIA 全部通过；未修改生产扫描、位置保留、资格校验或几何算法。最终背景保留修补后，合并运行另有两组矩阵遇到目标 renderer 晚于既有就绪预算到达，诊断确认来源几何有效、应用按原策略降级；无编译负载定向复核两组均通过，未调整超时或断言。最终普通文件夹矩阵、完整下拖及三种顶栏配置也通过。

API 29 使用软件渲染模拟器。未连接真机；Pixel 8 Pro/API 36、本轮现代 API 原生测试及真机视觉效果未验证。文件夹/照片/收藏/列表的真实操作与视觉组合仍按下方一次合并验收检查。

最终执行入口：

```text
:app:testFossDebugUnitTest
:app:lintFossDebug
:app:detekt
:app:assembleFossDebug :app:assembleFossDebugAndroidTest
:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a
```

Detekt 依赖所在 Maven Central 端点返回 403，本机通过临时 Gradle init 脚本使用 Google 托管的 Maven Central 镜像运行该检查，未修改项目仓库/依赖。原提交的三个文件从 git show 导出后，与其余原有文件一起同配置检查，不创建工作树。

## APK 与源码身份

本地 ARM64 FOSS Release 未配置发布签名。可安装交付包沿用仓库 Arm64 FOSS Release APK (non-publishing) 工作流，以本次源码提交触发构建，保留原有签名、ABI、包名和 SHA-256 校验。具体提交 SHA、运行链接、下载位置和 APK 哈希在交付回复中列出；版本名/版本号不能独立证明源码身份。若 CI 使用临时密钥，以其声明为准，不声称可覆盖任意旧签名安装。

## 一次合并真机验收

1. 安装交付 ARM64 Release。主页文件夹与文件夹内文件分别长按、多选、全选、取消最后一项，抽查照片、收藏和列表；确认两态底边、网格位置稳定，数量及操作正常、无双标题。
2. 默认/大字体，横竖屏与快速进入—退出—再进入选择态；核对按钮和标题不裁切。
3. 主页加载、排序、下拉刷新和一次可触发的错误/重试，确认无线性进度条及占位；文件页和操作对话框的加载反馈保留。
4. 首行与半可见缩略图，方图、横图、竖图、长图入场；普通返回、下拖取消及 A→B 返回 B。检查二次缩放、错位及清理；历史长图回程末段问题留给 4A，不计作已修复。

没有 Pixel 8 Pro 本次 APK 的真机视觉证据，现代 API 的本轮原生结果也不以历史记录代替。阶段结论：**代码完成，待真机验收**；不自动标记阶段 3.5 通过。
