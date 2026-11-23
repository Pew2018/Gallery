# 01 · 普通页面进入动画：OPEN 分支审计与 OxygenOS Classic 精修

> 审计基线：开始工作前重新 `git fetch` 确认的 `perf/home-gallery-sorting` 远程 HEAD
> `f0867983c7306da023dfd3037d8c74dfd2a869d2`（与本任务给出的审计基线一致，分支上没有
> 其他 Agent 的新提交）。
> 平台依据：AOSP `android16-release`（`platform/frameworks/base`，本轮实取）与
> `android13-release`（用于旧版本对照）。
> 本文只把能由源码 / 资源 / 自动化测试证明的结论写成「已确认」；需要真机合成的项目
> 一律标注为「待真机验收」。

## 0. 一句话结论

**两个 Android 版本区间里，`activityOpenEnterAnimation` 才是 Activity 打开动画的唯一驱动；
`android:windowEnterAnimation` 从来不是。** 而我们的页面样式一直没有声明
`activityOpenEnterAnimation`，所以在 Android 14+ 上普通页面打开时**没有任何动画**：
`ANIM_FROM_STYLE` 一旦生效，平台只从我们的样式里取属性，不会回落到框架默认动画，
取不到就直接结束转场（硬切）。本轮把该属性补到两套页面样式上，并把资源本身改成
180ms / 8dp / 0.96 透明度 / 无缩放。

## 1. 结论先行

| 项 | 结论 | 等级 |
|---|---|---|
| Android 14/15/16 普通页面 OPEN 由什么驱动 | 平台 Shell：`Transition` 把窗口样式以 `ANIM_FROM_STYLE` 交给 Shell，Shell 读样式里的 `activityOpenEnterAnimation` | 已确认（AOSP + 资源） |
| `oxygen_classic_page_open_enter.xml`（170ms）在 14+ 是否参与 | **否**。`OxygenClassicPageBackWindow` 原本只有 CLOSE 两项，`activityOpenEnterAnimation` 解析为 0 | 已确认（AOSP + 资源） |
| 14+ 实际看到的 OPEN | 无动画（无 animator 附着 ⇒ 转场立即结束），即硬切 | 源码推导，待真机复核 |
| Android 13 及以下 OPEN 由什么驱动 | `AppTransition.loadAnimation`：`TRANSIT_OLD_ACTIVITY_OPEN` → `activityOpenEnterAnimation` | 已确认（AOSP 13） |
| `android:windowEnterAnimation` 的作用 | 只作用于窗口级 `TRANSIT_ENTER/EXIT`（`WindowStateAnimator`），与 Activity 打开无关 | 已确认（AOSP 13） |
| Android 14+ CLOSE / 预测性返回 | 未改动；450ms 提交时钟、96dp 协同位移、恒等 CLOSE-enter 原样保留 | 已确认（未修改 + 既有测试） |
| Viewer 120ms 共享元素 | 未改动 | 已确认（未修改 + 既有测试） |
| 14+ OPEN 修复方式 | 在 `OxygenClassicPageBackWindow` 增加 `activityOpenEnterAnimation`，指向与旧版同一资源 | 已实现 |
| RTL 方向 | 用 `values-ldrtl` 把 8dp 镜像成 -8dp（窗口动画在 surface 上求值，无法从 view 的 layoutDirection 解析） | 已实现 + 已测 |

## 2. Android 14+：为什么「只声明 CLOSE」会让 OPEN 变成硬切

### 2.1 平台只从「我们的样式」里取 OPEN

`services/core/java/com/android/server/wm/Transition.java`

- `:2956` 对 activity→activity 转场调用
  `calculateAnimationOptionsForActivityTransition(type, sortedTargets)`。
- `:3144-3155`：只要最顶层 activity 的主窗口 `windowAnimations != 0`，就构造
  `AnimationOptions(ANIM_FROM_STYLE)` 并把 `lp.windowAnimations` 塞进去
  （`makeAnimOptionsFromLayoutParameters` / `addOptionsFromLayoutParameters`，见
  `core/java/android/window/TransitionInfo.java:1177-1188`）。
- 我们把 `windowAnimations` 设为 `OxygenClassicPageBackWindow`（非 0），条件恒成立。

`libs/WindowManager/Shell/src/com/android/wm/shell/transition/TransitionAnimationHelper.java`

- `:125-141`：`type == TRANSIT_OPEN` 且 change 是 activity（非 task）时，
  `animAttr = WindowAnimation_activityOpenEnterAnimation`。
- `:167-183`：`overrideType == ANIM_FROM_STYLE && !isTask` ⇒
  `transitionAnimation.loadAnimationAttr(options.getPackageName(), options.getAnimations(), animAttr, …)`
  ——从**我们的样式**里取该属性；只有走 `else` 分支时才会 `loadDefaultAnimationAttr`
  （框架默认）。也就是说：一旦声明了窗口样式，平台**不再**回落到默认 OPEN 动画。

`core/java/com/android/internal/policy/TransitionAnimation.java:346-370`
`loadAnimationAttr`：样式里没有该属性 ⇒ `resId` 无效 ⇒ 返回 `null`。

`DefaultTransitionHandler.java:487-488` 只在 `a != null` 时附着动画；
`:353-361` 在动画列表为空时直接 `finishCallback.onTransitionFinished`。

⇒ `OxygenClassicPageBackWindow` 只声明 CLOSE 两项时：OPEN 动画为 `null`，
转场没有任何 animator，页面的第一帧就是最终帧。**这就是「所有普通页面都硬切、
只有 Viewer 有共享元素动画」的根因。**

### 2.2 为什么旧版没暴露这个问题

`OxygenClassicPageWindow`（仅 Android 13 及以下安装）声明了
`windowEnterAnimation`，但 Activity 打开根本不用它（见 §3），同样没有
`activityOpenEnterAnimation`。因此旧版也是「无 OPEN 动画」，只是这个分支在本项目
的真机（Pixel 8 Pro / Android 16）上从不执行，长期没有被观察到。

## 3. Android 13 及以下：OPEN 的真正驱动属性

`services/core/java/com/android/server/wm/AppTransition.java`（android13-release）

- `:767-772`：`TRANSIT_OLD_ACTIVITY_OPEN` / `TRANSLUCENT_ACTIVITY_OPEN` ⇒
  `animAttr = activityOpenEnterAnimation / activityOpenExitAnimation`。
- `:812`：`a = animAttr != 0 ? loadAnimationAttr(lp, animAttr, transit) : null`。

`WindowStateAnimator.java`（android13-release）

- `:627-648`：`WindowManagerPolicy.TRANSIT_ENTER/EXIT/SHOW/HIDE` 才映射到
  `windowEnterAnimation / windowExitAnimation / windowShowAnimation / windowHideAnimation`。
  Activity 打开不会走这四个 transit。

⇒ 旧版结论：`windowEnterAnimation` 不是 OPEN 驱动；缺 `activityOpenEnterAnimation`
时同样没有 OPEN 动画。本轮在 `OxygenClassicPageWindow` 里补上该属性，旧版才第一次
真正拥有页面进入动画（源码推导，本机无 Android 13 设备，标注「待真机验收」）。

## 4. 实际改动

| 文件 | 原实现 | 问题 | 改动 | 保留 |
|---|---|---|---|---|
| `res/values/oxygenos_classic.xml` | `oxygen_classic_page_enter_duration_ms = 170` | 与目标 180ms 不一致 | 改为 180 | 关闭动画 token（140ms）、返回位移（10dp）不动 |
| 同上 · `OxygenClassicPageWindow` | 只有 `windowEnterAnimation` / `windowExitAnimation` | Activity 打开不读 `windowEnter`，旧版无 OPEN 动画 | 增加 `activityOpenEnterAnimation`，指向同一资源 | 旧的两个 item 原样保留（窗口级 transit 仍在用） |
| 同上 · `OxygenClassicPageBackWindow` | 只有 CLOSE 两项 | 14+ OPEN 解析为 0 ⇒ 硬切 | 增加 `activityOpenEnterAnimation`，指向同一资源；注释改为写明 `ANIM_FROM_STYLE` 语义 | CLOSE 两项、450ms 时钟、96dp 位移全部不动 |
| `res/anim/oxygen_classic_page_open_enter.xml` | 170ms / 0.94 透明度 / 0.985 缩放 / 8dp | 整页缩放不符合 Classic；起始透明度过低 | 180ms / 0.96 → 1.0 / 仅透明度 + 8dp 位移 / `fast_out_slow_in` | 位移方向与插值不变 |
| `res/values-ldrtl/oxygen_classic_page_motion.xml`（新增） | 无 | RTL 下 8dp 仍从右侧进入，方向相反 | `oxygen_classic_page_enter_offset = -8dp` | — |
| `helpers/OxygenClassicMotion.kt` | 只有设置页 token、`easing()` | 页面 / 嵌套文件夹常量散落 | 新增 `PAGE_ENTRY_*`、`NESTED_FOLDER_*` 与 `horizontalDirection()`（RTL 唯一实现） | 所有既有 token 数值不变 |
| `helpers/GalleryEntryMotion.kt` | 180ms / 18dp / 0.84 / 无 RTL 处理 | 位移与透明度明显大于页面进入；RTL 方向错误 | 170ms / 8dp / 0.96；方向走 `horizontalDirection()` | 取消、重入、结束清理逻辑不变 |
| `helpers/ClassicSettingsPageTransition.kt` | `direction()` 内联 RTL 判断；`animateTo` 固定用 `DURATION_MS` | RTL 逻辑重复；手势收束与嵌套文件夹参数无法对齐 | `direction()` 委托给 `OxygenClassicMotion.horizontalDirection`；新增 `settleDurationMs`（默认仍为 180ms，设置页行为不变） | 截图交接、Snapshot 生命周期、`run()` 一次性播放入口不变 |
| `activities/MainActivity.kt` | `distanceDp = 18`、`incomingStartAlpha = 0.84f` | 返回手势与点击进入参数不一致 | 改用 `NESTED_FOLDER_*` 三个 token | 取消回滚、`nestedFolderBack*` 记账、`syncAppBackStep()` 不变 |

## 5. 参数对照

| 属性 | 普通页面进入（改前） | 普通页面进入（改后） | 嵌套文件夹（改前） | 嵌套文件夹（改后） |
|---|---|---|---|---|
| 时长 | 170ms（且 14+ 实际未播放） | 180ms | 180ms（手势收束 180ms） | 170ms（点击与手势同一时钟） |
| 水平位移 | 8dp | 8dp（RTL -8dp） | 18dp | 8dp（RTL 镜像） |
| 起始透明度 | 0.94 | 0.96 | 0.84 | 0.96 |
| 结束透明度 | 1.0 | 1.0 | 1.0 | 1.0 |
| 缩放 | 0.985 → 1.0 | 无 | 无 | 无 |
| 插值 | `fast_out_slow_in` | `fast_out_slow_in` | `OxygenClassicMotion.easing()` | 同 |
| 应用对象 | Activity 窗口 surface | Activity 窗口 surface | 网格容器（`directoriesGrid`） | 同 |
| RTL | 不镜像 | 镜像 | 错误方向 | 镜像 |

未改动的所有者（分属不同动画，禁止合并）：预测性返回提交时钟 450ms、
Viewer 共享元素 120ms、设置页截图交接 180ms / 8dp、弹窗 170ms / 6dp、
三点菜单 140ms / 3dp、底栏 0ms 窗口切换 + 150ms 选中态。

## 6. 明确保留（本轮不改）

- **预测性返回**：`oxygen_classic_page_back_enter/exit` 一字未改；`activityCloseEnter/ExitAnimation`
  仍指向它们，`BackNavigationController.isCustomizeExitAnimation` 仍会选中 Custom 分支
  （`BackNavigationController.java:696-720`）。Shell 的 back 加载器
  （`CustomCrossActivityBackAnimation.kt:230-280`）只读 CLOSE 两个属性，新增 OPEN 不可能影响它。
- **Viewer**：`PhotoViewerTransition.DURATION_MS = 120ms`、`configureShared()` 的
  `setWindowAnimations(0)` 与场景转场均未触碰。
- **底栏**：`GalleryBottomBar` 的零窗口动画与首帧交接未改（`disableRootWindowMotion` 仍是
  `setWindowAnimations(0)` + 一次性 `ActivityOptions`）。
- **弹窗 / 三点菜单**：资源已在目标区间（170ms / 6dp、140ms / 3dp、110ms / 2dp），未改。
- **设置页截图交接**：`ClassicSettingsActivityTransition` 只在 Android 14 以下启用；14+ 由平台
  承担（本轮修复后平台确实有动画了，而旧代码下这里是硬切）。
- **照片配置编辑页的 `LayoutTransition`**：来源行增删由它独占，与窗口动画不共享属性；
  未改。

## 7. 验证

### 7.1 已运行的自动化

| 项 | 命令 | 结果 |
|---|---|---|
| 全量 JVM 单元测试 | `./gradlew :app:testFossDebugUnitTest`（JDK 21） | 188 个测试类 / 1360 个用例，0 失败 0 错误 |
| 新增用例 | `PageEntryMotionTest`（9 个） | 通过 |
| 既有动画用例 | `OxygenClassicActivityMotionTest`（7 个，含 `sdk=36`） | 通过 |
| Release 资源与编译 | `./gradlew :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`（JDK 21） | BUILD SUCCESSFUL；APK 内确认 `oxygen_classic_page_enter_offset` 有 `()` 8dp 与 `(ldrtl)` -8dp 两套配置、`oxygen_classic_page_enter_duration_ms = 180` |

> 注：Robolectric 的 `sdk=36` 沙箱要求 JDK 21（`DefaultSdkProvider` 会直接抛
> `Android SDK 36 requires Java 21`）；本机默认 JDK 17 下该用例必然失败，与本次改动无关。

新增用例覆盖：

- A02/A09：两套页面样式都把 `activityOpenEnterAnimation` 指到同一个资源，且 CLOSE 两项未被替换。
- A01/A05：进入资源只有透明度 + 位移两层，无 `ScaleAnimation`，180ms 与 token 一致。
- A12：LTR 起始 +8dp、RTL 起始 -8dp（`values-ldrtl`），且 `horizontalDirection()` 同步镜像。
- A05/A13：`GalleryEntryMotion` 只写网格的 `alpha/translationX`，结束/取消后不残留属性；
  动画缩放为 0 时直接落到终态（A11）。
- A06：`beginNestedFolderBackStep()` 与 `GalleryEntryMotion` 使用同一组 token（源码级断言，
  并禁止 18dp / 0.84 回流）。
- A03/A14：既有 `PredictiveBackCloseAnimationTest`、`ViewerMotionTest` 等全部保持通过。

### 7.2 静态源码可证明的约束

- OPEN 只有一个所有者：`activityOpenEnterAnimation`（两套样式指向同一文件），页面自身不再写
  `alpha/translationX`。
- CLOSE / 预测性返回的所有权未变：仍是 `activityCloseEnter/ExitAnimation` + 450ms。
- 动画缩放为 0 时，`GalleryEntryMotion` 与 `ClassicSettingsPageTransition` 都不启动自定义动画；
  窗口动画由系统 `Settings.Global.ANIMATOR_DURATION_SCALE` 统一归零。

### 7.3 无模拟器条件下无法验证

- 平台是否真的把 180ms / 8dp / 0.96 的 surface 动画播出（Robolectric 无法合成 Shell 通道）。
- 首帧是否出现白/黑闪帧、`0.96` 透明度下旧页是否透出可察觉的重影。
- 手势中途取消后，网格与窗口属性是否在真机帧级别干净恢复。
- Android 13 及以下的窗口动画（本机无该版本设备）。

## 8. 真机验收清单（Pixel 8 Pro / Android 16）

1. 相册 → 文件夹：应为 8dp 轻推 + 轻微淡入（约 180ms），无整页缩放、无白闪。
2. 文件夹 → 手势返回：父页应保持全屏预览，提交后关闭页继续右移 96dp 并快速淡出（450ms），
   与改动前一致。
3. 工具栏返回：同一套 CLOSE 资源，不应出现硬切或二次淡入。
4. 设置 → 任一分类 / 主题：应与文件夹进入同一节奏；14+ 不再硬切。
5. 设置 → 自定义照片配置 → 编辑：进入与返回同一节奏；编辑页内来源行增删仍是局部动画。
6. 嵌套文件夹：点击进入与右滑返回的位移/淡入应明显收敛；返回手势拖动时网格跟手，
   松手或取消都不留偏移。
7. 底栏切换：仍然瞬时切换，仅 150ms 选中态渐变；无整页滑动。
8. 弹窗与三点菜单：位置、点击区域、Ripple 不变。
9. 关闭系统「动画时长」后重复 1–8：所有页面立即到位，无残留淡入。
10. 系统语言切到阿拉伯语后重复 1、6：进入方向镜像（从左侧推入）。
11. Gallery 内打开图片 → 返回：120ms 共享元素动画应完全不变。
