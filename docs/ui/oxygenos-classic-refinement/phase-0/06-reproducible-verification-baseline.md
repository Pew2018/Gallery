# Phase 0 · 06 — 可复现验证与测量基线

> 本轮完成远程 GitHub 静态审查、附件设计材料读取和环境核查。没有本地 checkout、Android设备或模拟器。

## 实际步骤与结果

| 步骤 | 状态 | 证据 |
|---|---|---|
| 读取目标branch ref与commit | 成功 | HEAD 024b4731ac6508cab45f7d81eea73294a78d0d04 |
| 递归读取Git tree | 成功 | 15个 menu XML、26个 Gallery dialog类、核心Kotlin/布局/主题/测试/workflow |
| compare 38ad807… 到当前 | 成功 | ahead 3, behind 0，含MediaActivity业务改动和现存UI文档更新 |
| 读取12份既有UI文档 | 完成 | README及01–11；保留01–11原文 |
| 读取3份历史Dialog/Motion文档 | 完成 | classic-dialog-repair-review、classic-dialog-review、classic-motion-device-checks |
| 用户视觉基线 | 部分完成 | 设计指南及氧OS参考sheet可读；无当前APK设备截图/测量 |
| 单测/Gradle dependency resolution | 未执行 | 无clone/wrapper/gradle executable |
| FOSS Release workflow | 仅静态检查 | 支持workflow_dispatch，push filter排除docs |
| 真机或模拟器 | 未执行 | 无adb、SDK、emulator、测试APK |

环境命令结果：git status --short --branch exit 128，提示当前目录非Git仓库；java -version 成功，OpenJDK 17.0.20；command -v gradle 与 command -v adb 均未找到；ANDROID_HOME/ANDROID_SDK_ROOT 为空。由于缺少工程环境，Gradle任务未能发起，不编造其exit code。

## 当前没有的证据

无设备型号/API/density/window size、无导航/字号模式、无自定义theme runtime、无坐标/anchor/popup bounds、无录屏/应用截图、无渲染帧率/动画时间、无FOSS APK commit。源码中的dp/sp常量与配置偏移不是运行测量；UI guide拟合也不是当前Gallery视图量尺。

## 后续设备测量表

| 项 | 值 |
|---|---|
| APK commit / artifact / SHA256 | 待填写 |
| 设备型号 / Android API / build | 待填写 |
| density / font scale / window dp | 待填写 |
| 方向 / 三键或手势 / 语言 / RTL | 待填写 |
| preset或custom ARGB / dark mode | 待填写 |
| 页面、操作与复现步骤 | 待填写 |
| target View ID / bounds px与dp | 待填写 |
| anchor rect / popup rect / insets / IME top | 待填写 |
| 录屏、截图来源和时间 | 待填写 |
| 状态：源码/自动化/真机/拟合/待测 | 待填写 |

目标覆盖矩阵但尚未运行：窗口320/360/412/600dp；竖横屏；中文/英文/RTL；fontScale 1.0/1.3/1.5；animator 0/1/2；手势与三键；light/dark、三种预设和custom浅/深/低对比色；长菜单/子菜单；无媒体、少量、大量；普通/.nomedia/favorite/recycle/profile来源。

最终证据严格分层：源码确认、自动化验证、真机实测、设计拟合、待验证。本轮只满足源码确认和文档建立；Phase 0设备基线仍需补做。

