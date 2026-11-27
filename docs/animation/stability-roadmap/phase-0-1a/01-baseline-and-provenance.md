# 阶段 0 · 源码、产物与采集基线

## 构建溯源

| 字段 | 已核验值 |
|---|---|
| repository | [Pew2018/Gallery](https://github.com/Pew2018/Gallery) |
| branch | `perf/home-gallery-sorting` |
| sourceHeadSha | `172f0af1f44d3c84b35c1ace32908b443a042a81`，`git ls-remote --heads` 与干净的本地工作区一致 |
| main / merge-base | `b308a2132b8d119de86c874a001450e7d85e1d56`，目标分支独有 114 个提交，main 独有 0；远端仅这两个分支 |
| applicationCodeSha | `ff65fa333c11f19525611abfa402637655cfca72`，HEAD 和构建 SHA 的后续差异均为 R2.3 文档 |
| workflowName | Arm64 FOSS Release APK (non-publishing)，目标分支的 `app/**` push 触发，也支持手动调度 |
| runId / build SHA | [#742 / 43890831210](https://github.com/Pew2018/Gallery/actions/runs/43890831210)，`f7645074ddfbf1eb6a8ca61e7d892f93f3f57795`，查询时最新成功 |
| artifactId | [14237840252](https://github.com/Pew2018/Gallery/actions/runs/43890831210/artifacts/14237840252)，已下载，ZIP digest 与 APK digest 分开 |
| apkSha256 | `230983f53aad5b97d2ba2b4bd612d393e40a19aa32cf5afeecf2ed8c7c7d94a2` |
| signingCertificateSha256 | `27a015ae3ed5e910abdd77637981b44b9f589223fdc0f796ec23190d3a864d8c`，临时 CI 签名，独立执行 apksigner 验证 |
| packageName / versionName / versionCode | `org.fossify.gallery` / `1.14.1` / `32` |
| ABI / minSdk / targetSdk | `arm64-v8a` / 26 / 36，aapt 实读 |
| verificationStatus | VERIFIED（下载产物）；UNVERIFIED（原录屏设备实际安装包，无设备与哈希） |

复验时对 run artifact 使用 `gh run download`，对解压的 APK 执行 `shasum -a 256`、`apksigner verify --print-certs`、`aapt dump badging`。设备存在时用 `adb shell pm path org.fossify.gallery` 找到 base 与可能的 splits，拉取后逐个计算哈希和证书。版本号只提供元数据。

## 原始材料

| 材料 | SHA-256 或缺口 |
|---|---|
| 全应用动画与媒体加载专项审计 | `4b3bd65569805f739c8915c31b3260f4643272843c9102e0313874deaadbf3de`，已读 |
| Google Photos 历史 `-compressed.mp4` | `6d52d78c6703e6f492e4e7fbc22b9c353edc9eb3202e47aa8cef2c9f1644cdf0`，720×1480，7205 帧，60.041667 s |
| Google Photos 派生关键帧联系表 | `ae8434e4e6ac7437076f79861db4577c23d9134a6ef9a9cb0a5420f2f9bec6b4` |
| OxygenOS 联系表 1/2/3 | `a59785a854dc84b5e2f57970f884714c25529b4c8579c68b8495e01167bed01f` / `968acba693b878d22eda80272484ef1a7d8ce56b53b4c41fa2bf25bec64caec2` / `a9766eece7d400a3677e09ccb15fb365abc899611a9eb2d677782a641d40bebd`，原始截图集未确认 |
| 指定原始材料 | 未找到 `-compressed(4).mp4`、`4392a31633b0d8505f14121a01fbdad8.mp4`、`Gallery-Diagnostics-20261116-230430.txt`、`审计动画与加载优化.txt`、`OxygenOS_4.5.15_Android_7.1.1_UI_Design_Guide_v1.0.md` |

OxygenOS 联系表已检查。第 2 表的设备信息页可读到 OxygenOS 4.5.15 / Android 7.1.1，第 3 表含 Gallery 的静态 Photos/Collections 界面，可作为既有 Classic UI 参考。

使用 ffprobe 读流信息并从历史参考原片重新抽取零基帧。120 fps 是录屏采样。以下每行的 Gallery 原始视频、对应日志及同步点均缺失，源码栏是待按场景检验的入口。

| 场景 | 历史参考样本 / 本轮复核 | 当前 Gallery 源码及未证实风险 |
|---|---|---|
| A01 主页进文件夹 | 168、174，分组过渡到网格 | MainActivity → MediaActivity，首屏发布与窗口动画独立 |
| A02 文件夹返回 | 1477、1486，网格回分组 | Classic close 与目录恢复 |
| A03 缩略图展开 | 348、365，方图从选中格变大 | MediaActivity source lease → Viewer proxy，端点对齐待测 |
| A04 收回缩略图 | 475、551 为回程中代理，目标格一度空白 | Viewer return → GridReturn，首个网格帧可能仍遮盖目标 |
| A05 切换媒体再返回 | 连续切换与落回同一目标待核验 | Viewer 当前路径重新定位，离屏及变更场景待测 |
| A06 不同比例 | 1010 横图、1230 长竖图、1800 竖图 | Grid scale type 与 renderer 各自变换，镜像及裁切待测 |
| A07 视频 | 未确认内容类型、播放与首帧 | VideoFragment 的封面与播放器交接需独立证据 |
| A08 下拖与回弹 | 475→551 可见缩小和变亮，取消缺触点 | 交互拖拽与平台返回交接，速度及取消序列待测 |
| A09 遮罩和系统栏 | 365、475 可见背景及栏变化 | Scrim、Chrome、系统栏各自有状态，合成关系待测 |
| A10 快开快关及滚动 | 3930 出现网格占位 | Adapter 复绑、请求失败、drawable 清空需区分；Gallery 历史空位的因果未证实 |

## 统计口径

同一 Android 进程内使用单调时钟。每轮分别记录冷进程、热 Activity、已填充 MediaStore/Room、Glide 冷缓存/磁盘命中/内存命中、后台全扫描并发情况。这些维度可同时存在。每种条件记录硬件、系统、动画倍率、数据集、容器与授权；缓存来源未知时写 UNKNOWN。

| 指标 | 起点 → 终点或计数规则 | 实测 |
|---|---|---|
| 请求数 | 按真实创建的请求代际计数；调用和等待授权单列 | 待采集 |
| 启动作业数 / 重复 | 按真实 worker 进入计数；同授权 scope、revision、查询签名重复启动另计 | 待采集 |
| 完成/取消/失败 / 耗时 | 每个已启动 worker 只计一次终态；启动到 worker 退出；排队与交付等待单列 | 待采集 |
| 首次有效缓存预览 | 请求创建到通过授权和代际门的首个非空缓存发布；提交另记 | 待采集 |
| 首张 drawable / 网格首有效帧 | 请求创建到匹配加载键的 drawable ready / 匹配当前可见 viewport 的 draw，commit 单列 | 待采集 |
| Adapter submit / commit | 同 adapter 实例与提交代际配对，无变化快照单列 | 待采集 |
| Viewer 首可用图像 | 有效 launch 到当前媒体对应 drawable / renderer readiness，分别记录 | 待采集 |
| 共享转场 | 同一次进入或返回的开始到终态，取消与降级单列 | 待采集 |
| 返回目标 ready / draw / commit | 返回请求创建到同一目标键、版本和 lease 的各时点 | 待采集 |
| Viewer 关闭完成 | 返回请求到 Viewer cleanup 生命周期观察，网格恢复独立 | 待采集 |
| 完整扫描 | 请求创建到完整 worker 结果；最终 UI 发布另记，部分结果不计完整 | 待采集 |
| 屏幕最终呈现 | 需与 SurfaceFlinger presented frame 或同等可验证信号匹配 | NOT_OBSERVABLE |

## 原有事件

- `scan_restart_requested` 在 `getMedia` 创建代际前输出旧 `mediaRefreshRequest`，只计调用。 `scan_cancelled` 可来自 stop 请求和取消回调。 `media_scan_summary` 是按 pipeline trace 聚合的交付摘要，含部分、取消、拒绝等结果。
- `shared_map_accepted` 为共享映射通过，`shared_start_captured` 为应用采取平台共享起点，`shared_animation_started` / `shared_transition_ended` 是转场回调，`entry_completed` 为进入 gate 完成。
- `viewer_requested` 是旧返回请求事件，`viewer_map` / `grid_map` 是两端映射；`viewer_animator_started` / `viewer_transition_end` 是平台动画观察。 `grid_capture_start/end` 读取平台回调所带的快照，无法推断另外有一份应用主动绘制的可见快照。
- `grid_first_return_frame` 可以在目标缩略图仍被隐藏时产生。 `grid_target_return_frame` 经过键、位置、drawable 与恢复状态检查。 `ack=frame_commit` 来自硬件 renderer 的提交回调，`ack=after_draw` 来自 OnDraw 后的任务；二者都缺少合成器呈现证据。 `grid_return_closed` 为网格清理，原因可为正常退出或降级。
- 代码确认暂停会停止普通扫描，返回有条件走 checkpoint 或重新加载。旧日志无法量化重复启动，Gallery 空位、裁切跳变和重复可见层仍需同步实测。

阶段 1A 仅在现有分支与回调采样。保持原查询、权限、扫描调度、发布顺序、图层及动画参数。

## 补采的模拟器启动样本

阶段 0 源码单独重建，API 29 Pixel_4 上用 MediaPageStartupTest 的 5,000 个合成图片样本补采。以下为单次采样的毫秒值。时间原点为 `media_page_open`，只有 worker 栏为运行时长。冷/热指该用例控制的目录元数据状态，Glide 层命中情况 UNKNOWN。最终呈现待真机验证。

| 场景 | 首预览提交 | 首 Adapter 提交 | 首 drawable ready | 可见候选 pre-draw | worker 时长 | 完整扫描交付 | 最终 Adapter 提交 |
|---|---:|---:|---:|---:|---:|---:|---:|
| .nomedia，空 Room | 282 | 308 | 982 | 1000 | 2055 | 2118 | 3861 |
| .nomedia，有 Room 缓存 | 369 | 391 | 569 | 621 | 1661 | 1686 | 1703 |
| 全部媒体，20 个样本预先入索引 | 184 | 204 | 596 | 616 | 1759 | 1769 | 1783 |
