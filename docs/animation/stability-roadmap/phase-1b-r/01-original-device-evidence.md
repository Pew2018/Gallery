# 1B-R 原始证据与溯源

开发分支为 `perf/home-gallery-sorting`。开始前独立查询 GitHub refs，远端与干净工作树均为 `ae2289f91258e5d6405a44de1761728eb42bc289`。GitHub API 确认[阶段 1B Release run 44018373846](https://github.com/Pew2018/Gallery/actions/runs/44018373846)为该提交，结论 success。

| 输入 | SHA-256 | 本轮状态 |
|---|---|---|
| Gallery-Diagnostics-20261117-112838.txt | `313859671589b7d1f77a80764c7dcb34b7c878da0dac403e50a19070cdfb2ed5` | 摘自[阶段 1B 证据档案](../phase-1b/01-evidence-and-root-causes.md)，原始文件未取得 |
| adf4608aa835337881981f19a4bebd7e.mp4 | `b1f2de34b13e7a681d8def2ef54cae326d1fcd0cf70ba8a8974e100bc31e6fe8` | 摘自同一档案，原始文件未取得 |
| Gallery-Diagnostics-20261117-231739.txt | NOT MEASURED | 原始文件未取得 |
| 68b29a8ff1569b57a14016d876a3f005.mp4 | NOT MEASURED | 原始文件未取得 |

已检查共享目录、Codex 附件目录、Downloads、Desktop、Documents 和临时目录。收到的附件为文字任务说明。设备历史与本轮 ADB 设备分别记录。原 Pixel 8 Pro 未连接。历史设备自报 Android 16 / API 36，Gallery 1.14.1 (32)，Release，诊断开启。实际手机安装包字节和签名未取得。

| 历史窗口 | 资料记载 | 核验状态 |
|---|---|---|
| 1A，11:26:34.873–11:26:44.964 | FINAL 发布后两次 Diff。8,801 ms 提交链。后来采到首个 visible drawable ready | 阶段 1B 脱敏档案 |
| 1A，视频约 84–112 s | 间断灰色格和名称层 | 阶段 1B 脱敏档案 |
| 1B，23:16:46–23:17:36，视频约 8–13 s | 任务说明记录稳定 3,080 项列表中的快滚占位。62 条 drawable 样本，采样尺寸 670×670 | 未重建事件与帧对齐 |
| 1B，五次公开目录往返 | 任务说明记录五次 `same_scan_request`，后续第二个来源 worker 是显式刷新。Viewer 独立路径有 miss 和部分发布 | 原始摘要待复核。约 10,308 次元数据读取仅为任务说明估数，去重数量 NOT MEASURED |

本轮合成 JPEG、应用诊断和录屏均未提交。新测试只生成随机名测试目录与红、蓝像素。

本机历史 APK 的 SHA-256 已独立核对。1A 为 `4192c26f7b1c97c187a1ce36fdc8bbd92c329cf00699441f13bfaf888b07c5be`，1B 为 `a1091392176d9569ab17dc3e5a95899f3e1ff7d428879c2dcebb2325feac329a`。这些哈希对应工作区文件。
