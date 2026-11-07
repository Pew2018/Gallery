# Photo Profile Toolbar Stability — 2026-11-07

## 真机现象

Pixel 8 Pro / Android 16 在自定义照片配置浏览首次进入、下拉刷新和渐进加载时，顶栏快捷图标会出现迟到、短暂消失或挪位。主页与普通文件夹没有同样的内容驱动切换。

## 修复前的调用链与根因

`MediaActivity.setupOptionsMenu()` 建立菜单后调用 `refreshMenuItems()` / `updateMenuColors()`。照片配置加载再经过 `getPhotoProfileMedia()`、阶段结果 `applyPhotoGridResult()` 和 `publishMedia()`，媒体集合变化时刷新菜单。

旧的 `updateMenuColors()` 用 `PhotoPlaybackPolicy.toolbarScope(photoHasContent)` 决定照片配置的快捷动作作用域：空列表走 HOME，有内容走 FOLDER。HOME 与 FOLDER 使用不同的用户配置，故首次发布会重建动作计划；刷新中列表暂时清空时可能再次回到 HOME。旧代码还把“当前列表存在可播放媒体”作为幻灯片快捷动作的显示条件，空列表会移除该槽位并推动后续图标。`ToolbarQuickActions.apply()` 每次更新都会重设可见性、图标和标题；`OxygenToolbarStyle.tintMenu()` 也会遍历并重新赋值所有菜单图标。

## 状态边界

快捷动作布局由页面身份、用户保存配置、可用宽度和固定操作占位决定。扫描阶段、列表空暂态和渐进发布不参与布局决策。幻灯片槽位在照片配置页依照用户配置保留，只有当前媒体可播放且不处于锁定状态时才启用；溢出菜单中的幻灯片命令仍可按实际媒体隐藏。权限撤销仍沿用原有访问会话失效和媒体清除流程，锁定状态下幻灯片操作禁用。

## 修改文件与行为

- `MediaActivity.updateMenuColors()`：All Files 根页面仍使用 HOME，照片配置及普通文件夹使用 FOLDER。照片媒体状态不再参与 scope 判定。
- `MediaActivity.refreshMenuItems()`：仅在可见性/启用状态确实变化时写入菜单；配置页溢出幻灯片仍按可播放媒体显示，快捷槽位则保持稳定。
- `ToolbarQuickActions.apply()`：按实际 action 与 MenuItem 实例变化才重设图标和标题；可见性、启用状态均作差异更新。保存的 MenuItem 为弱引用，菜单重建后依据新实例执行完整初始化。实际宽度未测量时使用 `screenWidthDp`，测量后按布局宽度重新计算；现有尺寸变化监听负责窗口尺寸变化。
- `OxygenToolbarStyle.tintMenu()`：按 MenuItem、Drawable 与前景色缓存 Tint，只在图标实例或主题前景变化时重新着色；缓存只保留弱引用。
- `PhotoPlaybackPolicy.kt`：删除媒体内容决定 Toolbar Scope 的旧策略，增加纯函数判断幻灯片可执行状态。
- XML 菜单保持不变：管理配置固定按钮和五个快捷槽位已存在，不需更改 ID 或布局尺寸。

## 宽度与固定按钮

管理配置图标仍为固定按钮，计入 `fixedActionCount`，不进入用户可配置的五个槽位。保留 48dp 操作触控区和现有 56dp Toolbar / 标题布局。现有宽度测试覆盖 320、360、393 和 480dp。首次未测量宽度采用窗口配置宽度，布局测量回调会在实际宽度变化时重新计算；需要真机确认 Pixel 8 Pro 首次显示是否存在窗口 inset 导致的槽位边界变化。

## 回归测试

- `PhotoProfileEnhancementsTest.photoProfileToolbarUsesFolderPlanAcrossEveryPublicationStage`：覆盖 INITIAL、PARTIAL、COMPLETE、刷新空暂态和最终空列表；动作计划保持 FOLDER 计划，幻灯片启用状态独立变化。
- `ToolbarQuickActionsTest.photoProfileLayoutPlanDoesNotDependOnMediaAvailability`：覆盖四种宽度下稳定计划。
- `ToolbarQuickActionsTest.unchangedSlotBindingIsSkippedButReinflatedItemsAreInitialized`：覆盖相同槽位跳过绑定、动作变化和重新 inflate 后重新初始化。
- `ToolbarQuickActionsTest.slideshowAvailabilityChangesEnabledStateWithoutChangingThePlan`：覆盖空媒体、可播放媒体和需要解锁状态。
- 原有 `fixedPhotoProfileActionIsReservedBeforeOptionalActionsAtTargetWidths` 继续验证固定管理按钮占位和 48dp 槽位预算。

本地测试命令：`./gradlew :app:testFossReleaseUnitTest --tests org.fossify.gallery.helpers.ToolbarQuickActionsTest --tests org.fossify.gallery.helpers.PhotoProfileEnhancementsTest --stacktrace`。当前执行环境没有 Gradle 9.5.1 缓存，Wrapper 下载被网络策略阻断，因此本地测试尚未运行；需由目标分支 GitHub Actions 提供构建与单测结果。

旧媒体请求仍由既有 `mediaLoadRequests.isCurrent(request)`、请求上下文与显示键校验拦截；本次没有改动请求代次和权限会话机制。

## 真机验收步骤

1. 冷启动后进入自定义配置浏览，观察首帧图标数量、顺序及管理配置按钮。
2. 分别从主页和收藏页进入配置浏览。
3. 连续下拉刷新三次，确认按钮没有消失、补入或换位。
4. 在大量照片配置中观察渐进加载期间的按钮位置；在 `.nomedia` 来源配置复测。
5. 测试无媒体配置：快捷布局不变，幻灯片按钮禁用；加载可播放照片后启用。
6. 返回其他底栏页再进入配置浏览。
7. 打开搜索、输入、关闭，确认经典搜索动画与点击目标正常。
8. 修改快捷操作配置后重新进入，确认新顺序应用；确认管理配置按钮可用。
9. 改变主题强调色，确认图标 Tint 正确，且下拉刷新不会重绑图标。
10. 测试配置锁定、解锁和撤销媒体访问，确认无未授权操作或受保护媒体信息泄露。

## 长期约束与待验收

自定义配置浏览的 Toolbar Scope 和按钮布局不得由媒体扫描进度、当前列表是否为空或阶段性发布结果决定。以后调整加载、刷新和性能流程时必须维持此约束。

代码层回归测试只能证明覆盖的决策规则和绑定策略；无闪烁、首次布局视觉稳定、搜索动画和实际色彩仍需上述真机验收确认。
