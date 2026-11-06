# Phase 2 · 04 — 回归与构建

## 当前状态

源码、测试、文档已准备；完整自动化与ARM64构建待Actions执行。**未标记Phase 2代码DoD完成。**

基线：8620d441e3d5456e6513b188cf2dd46791283363。最终应用源码提交与Run URL将在取得实际结果后补齐；不提前填写不存在的SHA/摘要。

## 已实际执行

- git clone指定分支、ls-remote确认最新HEAD；git diff --check通过。
- 本地 `./gradlew :app:dependencyInsight --configuration fossReleaseRuntimeClasspath --dependency com.google.android.material:material`：wrapper分发包下载失败，Network is unreachable。
- 本地 `./gradlew :app:testFossReleaseUnitTest`：同一wrapper下载阻碍；未到编译/单测。
- 本地 `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`：同一wrapper阻碍；未产出APK。
- 环境只有Java17、没有Android SDK/已缓存Gradle。未修改Gradle/工作流去规避验证。

## 自动化资产

- 扩展ClassicSettingsControlsTest：分类宽320/360/412/600、font1/1.3/1.5、深浅/RTL、无省略/裁剪、13sp；既有Switch触摸/拖动/取消/Ripple/色彩合同继续运行。
- 新SettingsPhaseTwoTest（8个测试方法，API28/35）：八分类独立/分组完整/重绑定焦点与Ripple、动态缩放子项/重进/状态恢复、平台限制行、主页/分类scroll恢复、实际偏好一次变更、普通/双行/Section测量矩阵、宽屏连续性、自定义色Ripple原位更新。
- SettingsPageMotionTest新增方向/RTL/alpha floor和最终态；保留0x/过期回调/销毁测试。
- 完整FOSS Release suite保留OxygenThemeColors/Presets、Toolbar、Dialog、Picker、备份和媒体管线非回归。

## 交付验证门槛

采用既有 `.github/workflows/arm64-foss-release.yml`，需要同一最终应用提交的完整unit test与assemble成功；工作流验证包名org.fossify.gallery、apksigner、ABI（如无native lib会明确报告）、单一arm64输出、APK SHA256，artifact保留14天。

签名优先repo release secrets，否则每次run生成temporary CI key，不保证覆盖安装兼容。最终签名模式、Artifact ID、APK摘要、数量/结果必须从实际run日志/产物读取。

代码/CI/真机是三个独立状态。没有真实WindowManager、GPU、TalkBack或系统授权prompt自动化证据，不能用Robolectric替代05。

## 首轮验证与纠正

应用源码186cf1d9在[Run42186070437](https://github.com/Pew2018/Gallery/actions/runs/42186070437)编译成功；新增测试的`RippleDrawable.color`无公开getter，导致test Kotlin编译失败，未执行单测/assemble。纠正为公开可验证的Ripple身份和pressed状态断言，不通过反射私有framework字段制造颜色getter；实际GPU着色保持真机待验收。并补充360dp同宽12/13sp Native候选渲染，不影响应用副标题13sp。
