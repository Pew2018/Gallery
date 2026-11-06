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

Workflow run、测试总数/失败数、最终状态、签名/ABI 检查和 Artifact 名称将在推送后的结果可读时补充。没有 Action 成功记录前，不应标记自动化或 APK 构建已通过。

## 尚无设备证据

当前没有连接 ADB 设备或模拟器。尚未完成 FAB 实际 IME 间距/动画/可点、主题切换与旋转；未采集各宿主 Popup 坐标和长菜单/真实 Ripple 录屏。因此真机状态均为待验收。
