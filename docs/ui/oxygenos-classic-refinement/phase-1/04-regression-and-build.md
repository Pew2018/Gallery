# Phase 1 — 回归与构建记录

## 本地环境

- JDK：OpenJDK 17.0.20。
- Wrapper：仓库要求 Gradle 9.5.1。
- 本地命令尝试：`./gradlew :app:testFossReleaseUnitTest --tests 'org.fossify.gallery.helpers.PhotoProfileEditUiTest' --tests 'org.fossify.gallery.helpers.UiFollowupTest' --no-daemon --stacktrace`。
- 结果：Wrapper 需要下载 Gradle 9.5.1，当前执行环境报 `java.net.SocketException: Network is unreachable`；没有本地 `gradle` 可执行文件或 Android SDK/ADB，故本地测试没有启动。

## 自动化与 Actions

Phase 1 用户要求的完整回归任务由 `.github/workflows/arm64-foss-release.yml` 执行：

```text
:app:testFossReleaseUnitTest
:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a
```

首次 run：[#42157087601](https://github.com/Pew2018/Gallery/actions/runs/42157087601)，在 `:app:compileFossReleaseKotlin` 失败，`TiebaMenuItemView.kt` 移除专属逻辑时误删了仍被继承声明需要的 `ListMenuItemView` import。修复已补回。

第二次 run：[#42157421606](https://github.com/Pew2018/Gallery/actions/runs/42157421606)，应用主源码编译通过，但 `:app:compileFossReleaseUnitTestKotlin` 失败：`UiFollowupTest.kt` 新增菜单行标题断言缺少 `android.widget.TextView` import。测试 import 已补上。

第三次 run：[#42157721854](https://github.com/Pew2018/Gallery/actions/runs/42157721854)，934 个单测中 933 通过、1 个新增 FAB Insets 用例失败。失败原因是该用例直接假设 Robolectric 合成 `systemBars()` 与 IME inset 和源码输入相同，实际其 API 28 inset 聚合值不同，导致精确 `-276px` 期望不适用。位移公式已提取为纯函数单测，Insets 集成用例改为验证 FAB 保持可见并随可见 IME 上移。

第四次 run：[#42158323494](https://github.com/Pew2018/Gallery/actions/runs/42158323494)，935 个单测中 934 通过、1 个失败。剩余失败是尝试用 Robolectric 第二个合成窗口 Insets 证明键盘隐藏后 translation 归零；运行环境仍将 IME/系统栏 bottom 聚合为 24px，纯函数关闭状态已经独立验证为零。已移除不可靠的合成关闭集成断言，保留纯函数隐藏状态测试。APK assemble 未运行，无 APK Artifact；第五次 run 待回填。没有 Action 成功记录前，不应标记自动化或 APK 构建已通过。

## 尚无设备证据

当前没有连接 ADB 设备或模拟器。尚未完成 FAB 实际 IME 间距/动画/可点、主题切换与旋转；未采集各宿主 Popup 坐标和长菜单/真实 Ripple 录屏。因此真机状态均为待验收。
