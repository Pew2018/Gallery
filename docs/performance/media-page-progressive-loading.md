# 媒体页渐进加载（阶段 A）

## 从打开页面到首张缩略图

普通文件夹和「所有图片」在布局就绪后同时启动有界 Room 预览和正式扫描。Room 只取一个视口所需的可读样本，候选查询有上限，样本按当前排序显示。对「所有图片」的 Room 预览逐父目录应用可见性规则，也检查祖先保护、排除目录、点文件和 .nomedia。完整集合及其排序由正式扫描确认。

Android 的 MediaStore 读取和直接文件访问独立。MediaStore 可消费时，从游标先交付一个经过权限、文件身份与过滤校验的样本。全部索引行继续用于文件夹发现和元数据匹配。获得所有文件访问权限时，直接文件枚举仍确认每个纳入目录的实际内容，能发现 MediaStore 缺失的项目。MediaStore 失败后，普通文件夹继续读取磁盘，「所有图片」使用已知目录、公共媒体目录和配置的目录。发现范围不完整时保留 PARTIAL。

直接扫描发布第一个已验证的文件，随后按网格容量补首屏。搜集更多项目时，预览至少间隔 300 ms 且候选数量大致翻倍。由于文件系统枚举和索引样本的来源顺序可能不同，预览只补入新路径，并保留已显示的位置。完整扫描应用最新排序和分组。随机排序保留已经显示的前缀，并打乱剩余项。

## 完成状态与交互

- 缓存、索引和磁盘预览带同一请求 ID。旧请求、迟到的低优先级来源和重复路径不能覆盖新结果。
- 缩略图首批提交后隐藏中央加载。滑动中的 Adapter 更新会等到手势结束。保留相同前缀的快照使用插入通知，大变动沿用现有 Diff 或更新策略和滚动锚点。缩略图请求继续按原请求键复用。
- 搜索针对最新有效快照重新过滤，正式扫描继续运行。FastScroller 等待完整结果落入 Adapter。Viewer 接收已提交的可见邻居，临时边界可以等待正式扫描。
- 文件夹统计、Room 持久化和可选视频时长继续异步处理。成功的完整枚举才允许按现有规则修剪缓存。失败保留已验证的画面，权限状态失效时清除相关结果。

文件夹枚举继续使用现有的直接列表或 SAF 子项游标。仅有磁盘文件的目录，第一批在枚举得到文件名之后、全量元数据读取之前发布。Room 查询按修改日期取得有界样本，按大小、文件名、拍摄日期等排序的全局排名在正式扫描后确认。

## 诊断与复测

在应用内启用现有诊断后，按请求 ID 关联下列事件。常规运行仍默认关闭诊断。

- `media_page_open`、`media_cache_query_start`、`media_cache_query_end`、`media_index_preview_ready`、`media_first_candidate_ready` 与 `media_scan_partial`。
- `media_first_preview_submit`、`media_first_adapter_commit`、`media_first_thumbnail_rendered`、`media_scan_complete` 与 `media_final_commit`。缩略图记录点为真实 Drawable 进入可见 ImageView 后的预绘制。
- `MediaPipeline` 汇总扫描排队、MediaStore 查询、目录枚举、元数据读取和排序。Adapter 继续记录 Diff 等待、耗时、丢弃和提交策略。用 `scan_restart_requested` 与终态事件检查重复扫描与取消。

本地回归使用 `:app:testFossDebugUnitTest`。ARM64 Release 使用 `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`。设备端 `MediaPageStartupTest` 用相同的 5,000 张合成 JPEG 验证成功提交、可见缩略图和最终条数，并输出阶段耗时。

Pixel 8 Pro 的冷启动、热启动和有无缓存对比仍需在真机上测量。文件夹进出动画、Viewer 图片比例和全屏转场属于后续阶段。

### 交付版本的模拟器抽样

API 37 ARM64 模拟器、FOSS Debug、所有文件访问权限、单个含 5,000 张 96×64 JPEG 的 .nomedia 文件夹。同一进程先打开空缓存，再打开已填充 Room 的文件夹。以下为交付源码的一轮记录，计时起点为该请求的 `media_page_open`。

| 指标 | 空缓存 | 有 Room 缓存 |
| --- | ---: | ---: |
| 首批可用数据 | 743 ms（文件扫描） | 281 ms（Room） |
| 首次 Adapter 提交 | 997 ms | 511 ms |
| 扫描结果就绪 | 3,878 ms | 5,036 ms |
| 最终 Adapter 提交 | 3,955 ms | 5,097 ms |
| 5,000 次文件元数据读取累计 | 2,170 ms | 3,582 ms |
| 全量排序 | 17 ms | 4 ms |

两次打开分别记录到五次和七次增量追加，均使用插入通知，提交记录为 1–20 ms。每次打开只有一次完整扫描，工作排队分别为 1 ms 和 4 ms。测试观察到两个场景均显示实际缩略图，首张绘制的独立计时事件未取得。本地 1,127 个 JVM 测试和 1 个设备测试通过。没有同环境的旧版对照、所有图片大库绘制计时、过期队列等待统计或 Pixel 8 Pro 真机数据。
