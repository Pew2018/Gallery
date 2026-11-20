# 阶段 3.5：顶栏与主页进度条

日期：2026-11-20。实施前 HEAD/origin 均为 3ee851b0954f0e398a99208dd79bba319c17cb22，工作区干净；仅使用 perf/home-gallery-sorting，无新分支、工作树、历史改写或强推。

## 修改

- Commons 7.1.3 的 AppTheme.Base 继承 Material3，actionModeStyle 为 AppCompat.ActionMode → Widget.Material3.ActionMode，内容高度通过 height → actionBarSize 解析，默认 64dp。此前 OxygenToolbarStyle 的普通行自定为 56dp。
- 普通 MySearchMenu 改读同一 ActionMode 样式高度；共同取该高度与 20sp 文本加 24dp 纵向空间的较大值。选择态保持原默认高度，只在字体需要更高空间时一同适配。Insets 仍由原系统栏机制处理，不叠加状态栏高度。
- 选择期间仅普通栏内容容器 INVISIBLE，普通栏表面继续绘制，保持测量和网格位置并避免 CAB 淡出时露出页面底色；CAB 真正退出后在预绘制恢复，快速反向切换会撤销旧恢复监听。退出/分离均清理监听。Commons 仍负责选择、数量、全选和操作回调。
- MainActivity 删除 directoryHeaderProgress 的创建、挂载、样式、显示及销毁引用；保留 setDirectoryLoading 的 watchdog、超时、取消、错误及重试。主页转场 frameState 使用实际加载状态，不再通过进度条判断。MediaActivity、Viewer、对话框和下拉刷新提示未改。
- Viewer/编辑器顶栏、Session、来源资格、几何计算、Glide 交接、缩放曲线及扫描链路未改。

主要文件：OxygenToolbarStyle.kt、OxygenSelectionMode.kt、MainActivity.kt；增加 JVM 与 Android 原生 SelectionToolbarHeightTest。

## 首轮交付验证（历史记录）

使用 JDK 21、项目 Wrapper 与既有依赖；未升级 Commons。

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,270 tests，0 failures/errors/skipped；首轮交付版本 |
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


## 后续修补：刷新圆圈与顶栏交接（2026-11-20）

执行方案为工作目录中的《Fossify Gallery 阶段3.5后续修补执行方案》。起点 HEAD/origin 为 8cd08828448a75c89106d63ed9f184247602d410，工作区干净；仅使用原 perf/home-gallery-sorting。保持已有提交历史，无新增分支、工作树、强推或无关修改。

### 真机证据与修补前复现

34 秒录屏 f437b0a32527ed7b42d109bd3806cc5d.mp4 与 Gallery-Diagnostics-20261120-013052.txt 显示刷新圆圈回缩后重现及选择栏交接空窗；用户另确认自定义照片配置同类问题。三次文件夹刷新都 COMPLETE，最终提交约 147/159/155 ms、11 项，不能把 UI 跳动解释为扫描卡死。原日志没有源码 SHA，版本号不能单独绑定安装包。

在原生产代码的 API 29 包上加入真实手势与逐帧回归后，分别捕获：

- 顶栏：普通内容已不可见时，CAB alpha 从 0 逐渐升至 1；退出时同类断档。记录有 normal=false/CAB=0、0.0079、0.0314 等实际帧，不只检查动画终态。
- 普通文件夹：onRefresh 替换了刚刚完成下拉定位的圆圈动画，启动新的缩放动画。回调瞬间 scale 仍可能是 1，因此测试还检查实际动画是否被重启。
- 自定义照片配置：初次完整结果结束了业务加载但圆圈仍处于 refreshing，后续真实下拉不能触发回调。JVM 同时复现当前 final 不能停止圆圈；预览和旧终态不得停止新圆圈的断言保留。

原始失败与修补后结果均保留在交付验证记录中，不以删除断言、延长等待或重复运行直至通过掩盖问题。

### 实际修改

- MediaActivity.onRefresh 不再先设置 isRefreshing=false。控件已由真实下拉完成定位并开始旋转，普通文件夹和照片配置的既有请求入口继续承接该反馈，不重复启动动画。
- 自定义照片配置通过现有请求/来源校验并发布非缓存完整结果时，补齐 isRefreshing=false。预览、旧请求、失败、配置不存在、权限和页面取消仍走原有守卫与收尾；watchdog、超时、扫描、排序、索引、发布和重试不改。
- OxygenSelectionMode 只标记受管选择栏的展示交接；SimpleActivity 在 AppCompat 已完成 CAB 初始化/退出设置的原有生命周期回调中完成该交接。只取消该 CAB 的透明度淡入/淡出，保持完整前景同步切换；原业务回调、数量、全选及操作不替换。取消发生在动画未启动时也完成 CAB 视图清理。
- OxygenToolbarStyle 保留实色表面、当前统一高度、字体规则和布局占位，只切换内容容器。删除等待 isShown 消失的逐帧恢复监听，不引入固定时长或轮询。未经过本项目 wrapper 的 ActionMode 保留原 AppCompat 透明度动画，有原生隔离断言。
- Viewer/编辑器、Session、共享资格、照片几何、EXIF/Insets、Glide 交接及缩放曲线均未改。主页已删除的线性条不恢复，其他加载反馈保留。

主要文件：MediaActivity.kt、SimpleActivity.kt、OxygenSelectionMode.kt、OxygenToolbarStyle.kt。复用并扩充 FolderRefreshActivityTest、JVM/原生 SelectionToolbarHeightTest 和 PhotoViewerInteractiveGestureTest。

### 后续修补验证

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,272 tests，0 failures/errors/skipped。生产代码与 JVM 用例此后未改 |
| Debug、AndroidTest、ARM64 FOSS Release | 构建成功；本地 Release 未签名，最终安装包使用同提交现有 CI 签名产物 |
| Lint | 与修改前同目录、同 JDK 21、同任务的基线逐项完全一致：15 errors、806 warnings、2 hints；新增 0、移除 0；原存量错误仍使命令非零 |
| Detekt | 原版/修补版均 3,463 条、4,031 weighted issues。无新增规则或问题位置；原有 onCreate 长度 145→144、publishMediaSnapshot 长度 241→242、SimpleActivity 函数数 18→20。未修改 baseline，原存量使命令非零 |
| API 29 最终合并原生检查 | 7/7 通过：逐帧选择交接及无关模式隔离、普通文件夹与自定义照片配置真实下拉、三组静态照片矩阵（60 组合）、完整下拖取消/提交与切图返回 |
| API 29 配置检查 | 相册实际 fontScale=2.0 的大字体通过；实际 orientation=2/fontScale=1.0 的横屏通过。逐帧记录同时校验顶栏底边与网格坐标 |
| API 37 隔离设备 | 默认逐帧交接、两条真实下拉与普通静态矩阵 4/4 通过；实际 orientation=2/fontScale=1.0 的横屏和 orientation=1/fontScale=2.0 的大字体均通过 |

原生检查在编译/R8/静态分析结束后顺序运行。API 29 默认交接采样 543 帧、实际横屏 967 帧；API 37 实际横屏 121 帧、实际大字体 484 帧，失败数均为 0；两条路径均 callbacks=1、scale=1→1、restarted=false、stopped=true。帧数来自测试采样，不用于宣称帧率或性能指标。原生数据是模拟器自动检查，不作为 Pixel 8 Pro 真机视觉验收。

配置测试保留一轮 API 37 横屏旧实例/空引用失败，以及配置未实际应用的准备失败。仅写入旋转设置会受启动器/设置主页方向限制，测试改为通过平台支持的旋转接口、可旋转的显示设置页，先确认系统实际配置，再断言相册自身 orientation/fontScale；两个系统最终均通过，未改应用逻辑、测试预算或通过断言来绕过失败。

### 封面候选项与剩余范围

录屏中 Pictures (11) 返回主页后封面变化，但缺少有效自定义封面设置、前后 tmb 媒体身份和目录排序记录。代码核对确认有效自定义封面优先，否则沿用目录分组/排序后的媒体；本次后续补丁未触及封面生成或提交。仍无充分证据认定错绑或归因于本次补丁，不实施封面/扫描修复，也不将其标记已解决。

后续修补只交付一个最终签名 ARM64 包。真机一次合并验收包含两条浏览路径的下拉刷新、选择/取消/全选、快速反向交接、默认/大字体与横竖屏，并抽查首行/半可见缩略图、代表性图像、普通返回/下拖取消和 A→B 返回 B。不安排大目录压力测试或 50 次往返。

最终提交、CI、APK SHA-256、签名证书及本地/远程同步身份随交付回复和安装包身份文件提供。无同一修补版 APK 的 Pixel 8 Pro 真机视觉证据前，结论为**代码完成，待真机验收**；不自动标记阶段 3.5 通过。
