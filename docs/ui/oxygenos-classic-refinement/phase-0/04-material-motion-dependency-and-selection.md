# Phase 0 · 04 — Material Motion 依赖证据与选型

> 源码锚点 024b4731ac6508cab45f7d81eea73294a78d0d04。必须区分 catalog 声明与 resolved dependency graph；本轮没有后者。

## 1. Gradle 与 SDK 信息

| 项 | 仓库声明 | 状态 |
|---|---|---|
| Material Components | libs.versions.toml 没有 material alias；app通过 Commons 7.1.3及 MaterialToolbar/MaterialAlertDialogBuilder、Material3 attributes使用Material | 源码/目录声明；具体artifact版本未确认 |
| Commons | org.fossify:commons 7.1.3，app build有version guard | 声明确认，解析树未运行 |
| androidx.transition | 无 catalog alias/直接 implementation | 可能传递依赖，尚无证据 |
| compile/min/target | SDK 36 / 26 / 36 | catalog行40–43 |
| AGP/Kotlin/Wrapper | AGP 9.2.1 / Kotlin 2.3.10 / Gradle 9.5.1 | catalog、wrapper |
| Java | 17 | catalog与ARM64 workflow |
| Release shrink | minifyEnabled=true、shrinkResources=true | app/build.gradle.kts:101–106 |
| Commons UI | Common Activity、Dialog/Picker/Security组件 | tag/source需在checkout时继续核对 |

com.google.android.material.transition.* 基于 AndroidX Transition；com.google.android.material.transition.platform.* 是 Framework Transition变体。不能把二者版本/API边界混为一谈。

## 2. API classpath 可用性

| API | 类名 | 当前结论 |
|---|---|---|
| MaterialSharedAxis | com.google.android.material.transition.MaterialSharedAxis | classpath 未probe，未确认 |
| MaterialFade | com.google.android.material.transition.MaterialFade | 未确认 |
| MaterialFadeThrough | com.google.android.material.transition.MaterialFadeThrough | 未确认 |
| MaterialContainerTransform | com.google.android.material.transition.MaterialContainerTransform | 未确认 |
| Platform ContainerTransform | com.google.android.material.transition.platform.MaterialContainerTransform | MDC platform类未确认 |
| AndroidX TransitionManager/Fade | androidx.transition.* | 无直接声明，transitive未确认 |
| Commons 7.1.3兼容 | instrumentation/compile与传递依赖关系 | 实际编译/resolution未验证 |

当前项目可以确认复用的是 OxygenClassicMotion时长/easing、ViewPropertyAnimator、RippleDrawable与原生ViewPager；它们不证明任何 Material Motion class 已在 compile classpath。

## 3. 环境验证和工作流

工作区没有仓库clone/wrapper、gradle executable或Android SDK。只可读到wrapper 9.5.1和JDK17；没有运行dependency tree、dependencyInsight、class existence probe、单测、R8或APK build，所以不报告resolved MDC/AndroidX版本。

.arm64-foss-release.yml支持workflow_dispatch，但push只匹配 app/**、buildSrc/**、gradle/**、gradle.properties 与workflow；docs-only commit不会自动运行。该workflow运行 :app:testFossReleaseUnitTest 与 :app:assembleFossRelease arm64-v8a。当前只改文档，不手动触发构建；源码变化时可在适当阶段触发。

下一个有Gradle checkout的Agent运行：
./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath
./gradlew :app:dependencyInsight --configuration fossReleaseRuntimeClasspath --dependency com.google.android.material:material
./gradlew :app:dependencyInsight --configuration fossReleaseRuntimeClasspath --dependency androidx.transition
./gradlew :app:dependencies --configuration fossReleaseCompileClasspath

并以 resolved compile classpath验证API类，再跑FOSS Release单测、R8与release构建。

## 4. 技术决策

- 可直接复用现有 OxygenClassicMotion/ViewPropertyAnimator、系统Window动画、RippleDrawable、ViewPager拖动。
- SharedAxis、Fade、FadeThrough、ContainerTransform及platform counterpart均需先完成依赖解析/class probe；没有理由盲目加版本或升级Commons。
- MaterialFade(View)不控制整个AlertDialog Window、PopupWindow、输入法Window、跨Activity root或ViewPager左右滑动。
- Settings当前同ScrollView重绘，不满足现成两个独立outgoing/incoming views；先不做SharedAxis。
- 依赖验证后首个试点可选稳定的局部空态或说明View MaterialFade；普通AlertDialog Window需以公开window style评估，安全/Picker不优先。
- 动画只管时间和运动；经典几何、Window权属、用户自定义ThemeToken及真实surface继续由 Gallery 管理。
- 真机所需结论：IME跟随、系统Back竞争、animator scale 0/2、R8结果、实际帧时间与视觉效果。

