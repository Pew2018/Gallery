# Phase 2 · 04 — 回归与构建

## 当前状态

**代码与CI完成，真机验收待完成。** 完整自动化137 suites / 977 tests，0 failures / errors / skipped；ARM64 Release、包名、ABI、签名、APK摘要验证全部成功。

基线：8620d441e3d5456e6513b188cf2dd46791283363。最终应用源码/测试提交：`ebe8fa34c600215910f29112e2078fd824dd2954`。后续提交仅更新本阶段交付文档，app树保持与成功构建一致。

## 已实际执行

- git clone指定分支、ls-remote确认最新HEAD；git diff --check通过。
- 本地 `./gradlew :app:dependencyInsight --configuration fossReleaseRuntimeClasspath --dependency com.google.android.material:material`：wrapper分发包下载失败，Network is unreachable。
- 本地 `./gradlew :app:testFossReleaseUnitTest`：同一wrapper下载阻碍；未到编译/单测。
- 本地 `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`：同一wrapper阻碍；未产出APK。
- 环境只有Java17、没有Android SDK/已缓存Gradle。未修改Gradle/工作流去规避验证。

## 自动化资产

- 扩展ClassicSettingsControlsTest：分类宽320/360/412/600、font1/1.3/1.5、深浅/RTL、无省略/裁剪、13sp；既有Switch触摸/拖动/取消/Ripple/色彩合同继续运行。
- 新SettingsPhaseTwoTest（8个测试方法，API28/35）：八分类独立/分组完整/重绑定焦点与Ripple、动态缩放子项/重进/状态恢复、平台限制行、主页/分类scroll恢复、实际偏好一次变更、普通/双行/Section测量矩阵、宽屏连续性、主题重绑定保留Ripple实例/pressed状态。
- SettingsPageMotionTest新增方向/RTL/alpha floor和最终态；保留0x/过期回调/销毁测试。
- 完整FOSS Release suite保留OxygenThemeColors/Presets、Toolbar、Dialog、Picker、备份和媒体管线非回归。

## 交付验证门槛

采用既有 `.github/workflows/arm64-foss-release.yml`，需要同一最终应用提交的完整unit test与assemble成功；工作流验证包名org.fossify.gallery、apksigner、ABI（如无native lib会明确报告）、单一arm64输出、APK SHA256，artifact保留14天。

签名优先repo release secrets，否则每次run生成temporary CI key，不保证覆盖安装兼容。最终签名模式、Artifact ID、APK摘要、数量/结果必须从实际run日志/产物读取。

代码/CI/真机是三个独立状态。没有真实WindowManager、GPU、TalkBack或系统授权prompt自动化证据，不能用Robolectric替代05。

## 首轮验证与纠正

应用源码186cf1d9在[Run42186070437](https://github.com/Pew2018/Gallery/actions/runs/42186070437)编译成功；新增测试的`RippleDrawable.color`无公开getter，导致test Kotlin编译失败，未执行单测/assemble。纠正为公开可验证的Ripple身份和pressed状态断言，不通过反射私有framework字段制造颜色getter；实际GPU着色保持真机待验收。并补充360dp同宽12/13sp Native候选渲染，不影响应用副标题13sp。

## 第二轮验证与纠正

[Run42186564876](https://github.com/Pew2018/Gallery/actions/runs/42186564876)实际执行977 tests、8失败：5个API35 Activity用例因Robolectric外部目录为空，在Environment真实实现取`getExternalDirs()[0]`时失败；fixture新增ShadowEnvironment.addExternalDir并设置受控AppOps输入（默认拒绝、权限用例再切允许），没有修改生产权限代码。2个TiebaStyleMigration旧600–840区间87%宽度断言改为连续公式525dp@700，仍保留重复测量/来回resize并加入600/840参考点。1个API35排版断言含行尾空格，改用Layout.getLineMax检查可见文字宽度，保留省略/高度/文字bounds断言并增加失败诊断。未删除失败测试、未降低生产文字字号来迁就测试。

第三轮Run42187545854在测试编译发现AppOps setMode及MANAGE_EXTERNAL_STORAGE常量不在SDK公开stub中；改用Robolectric4.16.1公开ShadowAppOpsManager.setMode(String,uid,package,mode)及AOSP标准操作名。生产权限实现继续不改，fixture通过Environment→AppOps调用链验证显隐（服务由Robolectric模拟，不是系统权限弹窗）。

## 最终完整单测（已实际下载XML复核）

源码与测试提交：`ebe8fa34c600215910f29112e2078fd824dd2954`。
[Run42188141776](https://github.com/Pew2018/Gallery/actions/runs/42188141776)，JUnit artifact `backup-ui-test-reports` ID13488946637。
137个suite、977个tests，失败0、错误0、跳过0。相对基线960增加17个运行用例。

| Suite | 执行数量 | 结果 |
|---|---:|---|
| ClassicSettingsControlsTest | 35 | 全通过 |
| SettingsPhaseTwoTest | 16（8方法×API28/35） | 全通过 |
| SettingsPageMotionTest | 3 | 全通过 |
| OxygenThemeColorsTest | 13 | 全通过 |
| OxygenThemePresetsTest | 7 | 全通过 |
| ClassicToolbarLayoutTest | 6 | 全通过 |
| TiebaStyleMigrationTest | 10 | 全通过 |

八分类布局/点击、18组完整性、API/模拟权限显隐、实际设置写入一次、跨页/重建配置保留、scroll恢复、focus/Ripple身份、56/72/48dp min、320/360/412/600dp×1/1.3/1.5字体×RTL、320..900dp连续宽度、深浅/自定义token、动画0x/中断/销毁/终态均有对应自动化断言。系统权限prompt、GPU Ripple、真实帧/WindowManager、TalkBack、Android16手势仍不在这些断言覆盖内。

最终预览artifact `photo-source-ui-previews` ID13488399635，12/13sp成对类别图共48张（API28/35）。已查看API35默认方向浅色及RTL深色1.5字体最终图，无文字裁剪；不是设备视觉验收。

## 最终构建与APK（成功）

- 最终应用代码/测试HEAD：`ebe8fa34c600215910f29112e2078fd824dd2954`；[commit](https://github.com/Pew2018/Gallery/commit/ebe8fa34c600215910f29112e2078fd824dd2954)。
- [Actions Run42188141776](https://github.com/Pew2018/Gallery/actions/runs/42188141776)，job127793306473：所有步骤success。
- `./gradlew :app:testFossReleaseUnitTest`与`./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`均由既有workflow实际执行成功。
- 最终run实际解析仍为Commons7.1.3 / Material1.14.0 / AndroidX Transition1.5.0；未新增/升级依赖。
- APK artifact：[gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/42188141776/artifacts/13488614449)，ID13488614449；内含`gallery-foss-arm64-v8a-release.apk`。
- APK大小18,405,015字节；SHA-256：`bd36ecd079ffdcd6ed6ec3296c38970eeda71fbfb04c23080832008d403088f6`。
- 工作流apksigner验证成功，APK Signature Scheme v2；包名`org.fossify.gallery`、versionName1.14.1、versionCode32；native libraries仅`arm64-v8a`。
- 已额外下载最终artifact ZIP、解压APK、计算SHA256并检查ZIP内lib目录，与CI摘要及arm64结果一致。没有把artifact ZIP的digest误写成APK摘要。
- 签名模式：`temporary-CI-not-upgrade-compatible`；临时RSA3072测试密钥，每次run变化，不能保证覆盖安装旧版。签名证书SHA256：`cbdf0925eaf51ef4a869767115f8a317b63b3a3d4fc999e6e11c01c97cf0c41b`。
- Artifact保留14天；服务器expires_at=`2026-11-20T08:39:42Z`，即台北时间2026-11-20 16:39:42（平台提前删除或仓库政策变化除外）。
- 本地Gradle受网络/SDK阻碍，成功单测/assemble证据来自上述CI；本地只复核JUnit XML、候选图、APK摘要和native ABI，不冒称本地apksigner运行。

最终边界复核：`setupSettingItems()`及之后所有设置/密码/权限/备份handler与基线逐字一致；ClassicSettingsSwitch、OxygenClassicControls、OxygenThemeEngine和activity_settings.xml逐字未改。源码/测试/文档变更独立于媒体、Room、扫描、排序、删除、Viewer和底栏根页面0ms策略。`git diff --check`及XML解析通过。

完整设备验收仍见05；自动化覆盖的是受控View/模拟系统条件，不能替代真实权限prompt、TalkBack、GPU Ripple、预测返回或60/120Hz帧观察。后续若用户反馈视觉差距，只调整presentation候选，不扩大到数据/安全业务。
