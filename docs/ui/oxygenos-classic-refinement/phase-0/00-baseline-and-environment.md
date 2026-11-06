# Phase 0 · 00 — 代码基线、并发风险与环境

> 目标分支：refactor/media-pipeline-stability  
> 实际读取 HEAD：024b4731ac6508cab45f7d81eea73294a78d0d04  
> commit 日期：2026-11-06 02:39:09 UTC；标题：docs(ui): specify user-theme accent tokens and classic Material Motion adoption。  
> 所有源码行号均以该 SHA 为锚；后续提交后需重新定位。

## 当前基线与相对上轮变化

远程分支 API 确认 HEAD 仍为本任务历史参考 SHA；没有重置。比较上轮强调色文档基线 38ad8072435822749cfd3e0642eb0985aec0e00a 与当前 HEAD：ahead_by=3、behind_by=0。差异包括：

| 变化 | 文件 | 风险判断 |
|---|---|---|
| 媒体管线同步提交 | MediaActivity.kt：10 additions / 3 deletions | 活跃业务文件，本轮只读；页面相关 UI 实施需再核对 |
| 同步测试/媒体文档 | FolderRefreshActivityTest.kt、docs/media-engine-v2/43-... | 不覆盖此并发工作 |
| UI 决策文档迭代 | 本目录 01–11 与 README | 以当前文档为准；本轮不改写 01–11 |

工作区 /workspace/scratch/bad75babc5bb 不含 .git，无法检查本地 index 或 diff；GitHub 分支读取和文件比较可用。提交时以重新核实的远程 HEAD 为父提交，并以 expected-SHA 更新 ref；并发移动则拒绝更新并重新审查。此环境不是本地 Android 工程 checkout。

## 并发风险表

| 热区 | 当前证据 | 安全边界 |
|---|---|---|
| MainActivity.kt 收藏菜单 | 当前行 817–831 刷新菜单，902–920 handler；Phase 1 文案会触及 | 保留 MenuItem ID、保存配置/鉴权/刷新次序 |
| MediaActivity.kt 浏览和 Viewer | 最近 3 个提交有 10+/3- 改动 | 本轮不碰；日后 UI 修改前重新核查 |
| SettingsActivity.kt | 8 类设置页共用内容容器 | 动画限制在展示层，不重构业务页 |
| PhotoProfileEditActivity.kt | IME listener 115–122 明确隐藏 FAB | 高置信度小修候选；不动草稿/来源/保存验证 |
| GalleryBottomBar / Viewer / 安全对话框 | 首帧 gate、访问会话与手势逻辑活跃 | 不把运动方案扩成状态机重写 |

分支远程元数据显示 protected=false；这不授权覆盖并发提交，也不改变非强推要求。

## 环境和视觉材料

- 附件 OxygenOS_4.5.15_Android_7.1.1_UI_Design_Guide_v1.0.md 可读。它描述 29 张 864×1536 PNG 样本，360×640dp 与 2.4px/dp 为截图拟合值，不是 density 读取。
- 已查看 OxygenOS sheet 参考图及两张附件 PNG；参考画面含电话、短信、联系人等多个 App，并非 Gallery 当前 APK 运行截图，不能作为 Gallery 真机验收。
- OpenJDK 17.0.20 可用；命令行找不到 gradle、adb；ANDROID_HOME 与 ANDROID_SDK_ROOT 为空。
- 当前目录没有 .git 和本地 gradlew，无法从此 shell 执行项目 Gradle、测试、安装 APK、dump bounds 或 GPU 帧数据。
- 仓库 wrapper 声明 Gradle 9.5.1；ARM64 FOSS workflow 使用 Temurin 17、运行 release 单测与 arm64 release 构建。

## 执行状态

| 步骤 | 状态 |
|---|---|
| 读取远程分支/ref、tree、commit | 完成；HEAD 与任务参考一致 |
| git status --short --branch | 已尝试，exit 128：fatal: not a git repository |
| java -version | 成功；OpenJDK 17.0.20 |
| Gradle dependencies / tests / assemble | 未执行：无 checkout、wrapper 和 gradle executable |
| adb devices / Android UI测量 | 未执行：无 adb / SDK / 模拟器 |
| FOSS Release workflow | 静态检查完成；有 workflow_dispatch，push paths 不含 docs |
| 自动化 / 真机基线 | 未完成；不得报告通过 |

本次是远程源码静态审计，不是本地测试。设备补测需记录设备型号/API/density/window、导航模式、font scale、主题、APK commit、View bounds 与录屏。

