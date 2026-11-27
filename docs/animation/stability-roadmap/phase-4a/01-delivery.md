# 阶段 4A：普通返回与下拖

## 起点与状态

2026-11-21，用户确认阶段 3.5 本轮真机验收通过，并要求按原路线进入第四阶段。起点为 perf/home-gallery-sorting 的 88aa09179828dc1494df63aa345c097fdeb22ab7，开始检查时本地与跟踪分支一致，工作区仅有阶段 3.5 验收记录与阶段 4A 交接文档两处未提交内容；两者均保留、阅读并按归属处理。沿用原分支、构建、签名与分发方式，未新建分支或工作树，未改写历史，未强推。

当前结论：**代码完成、自动化通过、待用户真机视觉验收**。本文件为 4A 唯一说明。

## 复核结论：回程核心无需改写

按交接要求先梳理真实调用链，再决定是否修补：

- 工具栏返回：`mediumViewerToolbar` 导航按钮 → `requestViewerClose("toolbar")`。
- 传统 Back：`onBackPressed()` / 平台 `finishAfterTransition()` → `requestViewerClose("system_back")`。
- 下拖提交：`mediaDismissSettled(true)` → `finish()` → `requestViewerClose`，交接手指下的实时帧。

三者进入同一个 `requestViewerClose`，共用 `MediaTransitionSessionCoordinator` 的 `beginReturn`、目标就绪、`MeasuredPhotoReturnView` 实测反向投影、文件/适配器版本校验与 `returnLeases` 终结清理。拖动取消走 `cancelGesture` → `releaseAll`，共用同一套遮罩、工具栏与输入恢复。

复核未发现需要修改的生产代码缺陷，因此本阶段**没有为了让“修复”成立而更改正确的回程路径**：

- 历史 ANIM-005（长截图末段裁口）在 API 29 的方/横/竖/长图 × FIT/CROP × 圆角开关矩阵中未复现，20 个组合的自动末帧像素对照全部通过；未改动裁口、矩阵、缩放曲线或释放时机。
- 上轮记录的 `platform_finished_without_motion` 仍按既有规则处理：仅当平台未派发 reentry 时进入既有 fade 安全退化。本阶段未复现新的连续快速往返故障，也未把该退化当作新缺陷。
- 未使用固定坐标补偿、放宽共享资格、延长等待、清空网格或关闭位置保留。

## 实际补充：覆盖缺口

复用的既有覆盖：完整下拖（反转/回弹/提交）、切图后返回、50 次连续往返、静态照片 60 组合末帧对照、视频半可见首排裁切、A→B 返回 B。

本阶段实际缺少的是“入口同源”和“取消后的连续性”，补两处：

1. 新增 `toolbarSystemBackAndDismissGesturesShareTheReturnCore`：同一媒体上依次验证工具栏返回、平台 Back、下拖取消后重拖提交、拖动中退出。每个入口都断言 Session 只产生一次终态（COMPLETE/FALLBACK）、状态到 CLEANED、网格重新获得焦点、缩略图无遗留 transitionName、Holder 可回收、输入锁已释放；取消用例在按 CANCEL 前额外断言 Session 已处于 INTERACTIVE_RETURN 且进度大于 0，并在取消后断言代理清空、进度归零、图像缩放/位移复原、遮罩隐藏。
2. 扩展 `fiftyViewerReturnsCleanTheirSessionAndRestoreTheSelectedThumbnail`：50 轮混合往返按周期交替使用 finish、平台 Back、工具栏返回，并在每第 4 轮插入一次被中断的下拖后关闭，仍断言每轮独立 Session、单一终态、CLEANED 与无残留。

两个用例都复用真实 Activity、真实 RecyclerView、真实触摸事件与既有反射观测点，没有新增生产代码分支或测试专用捷径。

## 验证结果

使用 JDK 21、项目 Wrapper 与既有依赖；API 29 为软件渲染模拟器。

| 检查 | 结果 |
| --- | --- |
| 完整 JVM | 176 suites，1,279 tests，0 failures / 0 errors / 0 skipped（`--rerun-tasks` 全量执行） |
| API 29 新增入口用例 | 通过（13.3 s）：工具栏、Back、取消后重拖提交、拖动中退出 |
| API 29 50 次混合往返 | 通过（90.0 s）：finish / Back / 工具栏 / 中断下拖交替 |
| API 29 完整下拖 | 通过（50.6 s）：反转、回弹、提交，末帧从当前照片帧交接 |
| API 29 半可见首排矩阵 | 通过（39.4 s）：含长图、EXIF、FIT/CROP 与圆角，自动末帧像素对照通过 |
| API 29 静态照片矩阵 | 通过（31.4 s）：方/横/竖/长图，20 组合末帧对照 |
| Lint FossDebug | 非零：15 errors、806 warnings、2 hints；与阶段起点记录逐级一致，androidTest 源码内 0 条；存量错误仍使命令非零 |
| Detekt | 非零：3,461 条，与阶段起点记录一致，androidTest 源码内 0 条；未修改 baseline，存量问题仍使命令非零 |
| Debug / AndroidTest / ARM64 FOSS Release | 构建成功；交付安装包使用同一源码提交的现有 CI 签名产物 |

API 29 为模拟器自动检查，**不是** Pixel 8 Pro 真机视觉证据。未做录屏逐帧核对，因此“长截图末段的视觉连续性”仍按待真机验收记录，自动末帧像素对照与 target_drawn/cleanup 日志都不能独立证明视觉连续。

## 源码与安装包身份

| 项 | 值 |
| --- | --- |
| 分支 | perf/home-gallery-sorting |
| 4A 源码提交 | c02bb933fb7bdbbba29787b2639c2ee0d0472e83 |
| 远程同步 | 本地与 origin 一致（提交后推送） |
| CI | https://github.com/Pew2018/Gallery/actions/runs/44908043794（success，headSha 与源码提交一致） |
| APK | /Users/patrick/Downloads/gallery-4a/gallery-foss-arm64-v8a-release/gallery-foss-arm64-v8a-release.apk |
| SHA-256 | 2dfd84658c683e874683752e51db48ede86c35d77cf843de5a52f0a078b4cdeb |
| 包名 / 版本 | org.fossify.gallery，versionCode 32，versionName 1.14.1 |
| ABI | arm64-v8a |
| 签名 | CN=Gallery Temporary CI Test Signature, O=Local Testing（cert SHA-256 fa004b5d1386bc2e91a7347604f3ff39c06aeffa18a837515b65c7a42688dd26） |

该签名来自工作流在缺少仓库密钥时生成的临时密钥，**不能保证覆盖上一轮安装**；跨轮安装若报签名冲突，需要用户自行决定处理方式，本阶段未执行卸载或清除数据。

## 未验证项

- Pixel 8 Pro 真机上的实际视觉效果：工具栏返回、Back、下拖取消/提交的裁口、比例、位置与残留。
- 真机录屏逐帧核对长图末段；本阶段只保留自动末帧对照。
- 系统与预测性返回属于 4B，本阶段未接线、未验证。

## 交付与验收步骤

真机只需聚焦 4A 差额，不重复整套 3.5 验收：

1. 安装本轮 ARM64 Release，打开一张长截图，工具栏返回；再次打开后使用系统 Back 返回，观察末段是否直接落到同一缩略图、无跳裁口与二次缩放。
2. 下拖到一半松手回弹，确认画面、顶栏、遮罩与可交互状态恢复，再重新下拖提交；随后在拖动过程中按返回。
3. A 打开后返回 A，切到 B 后返回 B；抽查首行半可见项与首屏外目标。
4. 每次返回后确认网格可立即再次点击、无残留缩小图层或输入失效。

## 阶段结论

代码完成、自动化通过。用户确认前不标记 4A 验收通过；4B 系统与预测性返回单独收口。
