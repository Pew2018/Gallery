# 07 · 预测性返回机制修复、系统源码依据与可复现证据

> 审计基线：`30824b332900f30326a337f0e6457b6cd567c980`（`perf/home-gallery-sorting`）。
> 对照仓库：`Pew2018/MaterialNG` `579609354b460af049a97cbb819aa7cb87a73283`。
> 本文只把**实际观察到的运行结果**写成结论；没有设备直连的项目一律标注为未验证。

## 1. 结论先行

| 页面 | 本轮确认的根因 | 修复 |
|---|---|---|
| 普通页（设置分类、主题页、文件夹、包含/排除/隐藏文件夹、配置管理…） | 关闭窗口的 `windowAnimations` 为 `0`，系统走 `DefaultCrossActivityBackAnimation`：父页被额外左移并缩放到 0.9，父页外侧露出 `BackAnimationBackground` 颜色层。用户看到的“黑色/白色大面积背景”就是这层随 `TaskDescription.colorBackground` 变色的颜色层，不是父页布局。 | 关闭窗口声明可加载的 `activityCloseExitAnimation`，系统改走 `CustomCrossActivityBackAnimation`：父页基础矩形保持全屏，仅叠加近恒等的 CLOSE-enter 动画。 |
| Viewer | `GalleryViewerTransitionTheme.windowIsTranslucent=true` **且**用 `ActivityOptions.makeSceneTransitionAnimation()` 启动。前者让 `hasTranslucentActivity()` 成立，后者把 `ActivityRecord.mHasSceneTransition` 永久置为 true，`BackNavigationController` 直接选择 `TYPE_CALLBACK`，系统整窗动画没有资格。 | 主题改为不透明；Android 14+ 不再使用场景转场启动；图片入场改为应用自绘（复用 `StaticPhotoEntryView` 与会话几何）；系统边缘返回交还平台。 |

`MaterialNG` 的普通设置页源码仍是“无 `windowAnimations`、无 `overrideActivityTransition`”的普通 AppCompat 页面。**不能**据此断言它走 Custom；本轮在 AOSP 源码中确认的是两条系统分支的差别，并只在 Gallery 上用真机等价模拟器验证了分支选择。

## 2. 系统源码依据（android16-release）

- `services/core/java/com/android/server/wm/BackNavigationController.java`
  - 焦点窗口有应用级回调（`!callbackInfo.isSystemCallback()`）→ `TYPE_CALLBACK`；框架默认回调以 `PRIORITY_SYSTEM` 注册，所以“无启用回调”仍可进入系统动画。
  - `hasTranslucentActivity(currentActivity, prevActivities)` → `TYPE_CALLBACK`；判据是 `ActivityRecord.occludesParent()`，由主题的 `windowIsTranslucent`/`windowIsFloating` 决定。
  - `currentActivity.mHasSceneTransition` → `TYPE_CALLBACK`；该字段在 `ActivityRecord` 构造时由启动 `ActivityOptions` 的 `ANIM_SCENE_TRANSITION` 一次性写入。
  - 只有 `isCustomizeExitAnimation(window)` 或 `Activity#overrideActivityTransition` 才会写入 `BackNavigationInfo.CustomAnimationInfo`。
- `libs/WindowManager/Shell/src/com/android/wm/shell/back/ShellBackAnimationRegistry.java`
  - `CustomCrossActivityBackAnimation.prepareNextAnimation(customAnimationInfo)` 成功 → 用 Custom；否则退回 Default。
- `CustomCrossActivityBackAnimation.kt`
  - `preparePreCommitEnteringRectMovement()`：`startEnteringRect = targetEnteringRect = startClosingRect`（全屏基准）。
  - `preparePreCommitClosingRectMovement()`：关闭页缩放到 0.9 并向滑动侧偏移。
  - `getPreCommitEnteringBaseTransformation()`：父页只叠加 `activityCloseEnterAnimation` 在 0…0.2 进度上的变换。
  - `CustomAnimationLoader.loadAll()`：CLOSE 动画加载失败即返回 null → 回到 Default。
- `DefaultCrossActivityBackAnimation.kt`
  - 父页从屏幕外 96dp 开始并随手势缩放到 0.9，因此四周露出 `BackAnimationBackground`。
- `BackAnimationBackground.java`
  - 颜色层 `z=-1`，颜色来自 `enteringTarget.taskInfo.taskDescription.backgroundColor`。Gallery 的 `SimpleActivity.onApplyThemeResource()` 把 `android:colorBackground` 注入 TaskDescription，所以“黑→白”只改变这层颜色。

## 3. 复现与运行时证据（Android 15 / API 35 AOSP arm64 模拟器）

设备：`sdk_gphone64_arm64`，Android 15，手势导航，动画缩放 1.0，Gallery FOSS debug（同一工作区构建）。
证据采集：`adb shell dumpsys SurfaceFlinger --list` 在手势保持期间读取图层；`screenrecord` 逐帧比对；`dumpsys activity activities` 确认 Activity 栈。

### 3.1 普通页（文件夹）修复前

图层出现：

```
animation-leash of predict_back  ...MediaActivity
animation-leash of predict_back  ...MainActivity
back-animation-background
Cross-Activity back animation scrim
```

保持手势时：关闭页 0.9 缩放并右移；父页也被缩小、上移，屏幕四周（顶部/底部/右侧）显示颜色层。`colorBackground` 改动只会改变这些区域的颜色——与用户“只是黑变白”的反馈一致。

### 3.2 普通页修复后

关闭窗口声明 `OxygenClassicPageBackWindow`（含 `activityCloseEnterAnimation`/`activityCloseExitAnimation`）后，同一手势：

- 父页（设置分类的上层页/相册主页）**全屏**保留，绿色/蓝色工具栏从屏幕顶端贯穿；
- 关闭页 0.9 缩放、向滑动侧偏移并保持圆角裁切；
- 颜色层不再在可视区域出现，只有父页被 0.2 的遮罩自然压暗。

像素证据：修复前 `x=10, y=20` 是颜色层（`#C8C8C8`，即页面白被遮罩压暗），修复后同点是父页工具栏（`#145C20`，绿色工具栏压暗）。

### 3.3 Viewer 修复前

保持手势期间图层列表**没有** `predict_back` leash，也没有 `Cross-Activity back animation scrim`；`dumpsys activity` 中 Viewer 仍是栈顶。截图显示图片、工具栏完全不动——系统没有接管，应用回调在稳定页也未启用，返回是一次无动画结束。

`logcat`（系统 `CoreBackPreview`）在旧版本会给出 `backType=TYPE_CALLBACK`。

### 3.4 Viewer 修复后

同一手势的图层：

```
animation-leash of predict_back  ...ViewPagerActivity
animation-leash of predict_back  ...MediaActivity
back-animation-background
Cross-Activity back animation scrim
```

保持手势时：整个 Viewer 窗口（图片、工具栏、黑色画布、底栏）一起缩放/位移/裁切，左侧露出**真实的媒体网格**（用户目标的“背后显示真实媒体网格”）。提交后回到网格，并带回返回位置与删除集合。

## 4. 修改点与行为变化

| 文件 | 变化 | 改变的行为 |
|---|---|---|
| `values/oxygenos_classic.xml` | 新增 `OxygenClassicPageBackWindow`（只声明 CLOSE enter/exit） | 让 WindowManager 选择 Custom 跨 Activity 分支；不改打开动画 |
| `anim/oxygen_classic_page_back_enter.xml` / `_exit.xml` | 新增 | 父页近恒等入场；关闭页提交后 140ms 收束 |
| `OxygenClassicActivityMotion.install()` | 34+ 普通页由 `setWindowAnimations(0)` 改为上面的样式 | 父页不再被系统额外缩放/偏移，颜色层不再大面积可见 |
| `SimpleActivity.onCreate()` | 统一为普通页安装窗口契约（主页、Viewer、编辑器除外） | 主题页、搜索、包含/排除/隐藏文件夹、配置管理等不再各自遗漏 |
| `styles.xml` `GalleryViewerTransitionTheme` | `windowIsTranslucent=false`，移除 `windowContentTransitions` 与共享元素转场 | Viewer 通过 `occludesParent()`；不再永久 `mHasSceneTransition` |
| `MediaActivity` 启动 Viewer | 34+ 不再 `makeSceneTransitionAnimation`；保留 <34 旧路径 | Viewer 生命周期内不再被系统判为场景转场 |
| `PhotoViewerTransition.EXTRA_APP_OWNED_ENTRY` | 新增 | 显式标记应用自绘入场 |
| `ViewPagerActivity` | 新增应用自绘 `StaticPhotoEntryView` 入场；系统边缘返回不再安装应用回程；工具栏/下拖改为结果交接 + 窗口淡出 | 边缘返回完全由系统整窗动画驱动；图片位置/删除结果协议不变 |
| `MediaTransitionSessionCoordinator` | 增加缩略图 bitmap 的进程内交接 | 不透明窗口在解码期间原地显示网格缩略图，避免黑屏 |
| `ViewerBackOwnership` / `PredictiveBackSupport` / 诊断注释 | 改为与系统源码一致的分支说明 | 不再宣称“窗口动画样式必然禁用预测性返回” |

## 5. 仍然未验证 / 已知限制

- **没有 Pixel 8 Pro（Android 16）直连**：本轮全部视觉证据来自 API 35 AOSP 模拟器。AOSP `android15-release` 与 `android16-release` 的
  `CrossActivityBackAnimation` / `Default…` / `Custom…` 核心几何代码逐行一致（仅 Handler/ProtoLog 适配差异），但仍需真机复核。
- 左右两侧手势、取消、连续快速返回、深浅色/强制模式组合：模拟器上抽查了左边缘提交/取消与橙色/深色主题，未做完整矩阵。
- 应用自绘入场依赖网格传入的 `StaticPhotoSource` 与缩略图 bitmap；不可用时回退到既有 fade 路径。
- 不透明 Viewer 的下拖关闭背景为黑色（原先半透明窗口可透出网格）。这是让系统整窗返回具备资格的必要代价，已在下拖提交时用窗口淡出收束。
- Viewer 入场结束帧在软件渲染模拟器上偶发约 100–200ms 的收束空档（子采样图淡入），真机帧率下未复核。
