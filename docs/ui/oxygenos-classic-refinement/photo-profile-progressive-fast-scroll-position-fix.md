# 自定义照片渐进加载期间快速滚动位置修复

## 用户复现

打开包含大量图片的自定义照片配置，在渐进加载仍显示进度时拖动右侧快速滚动条到列表靠下位置。阶段结果或 `COMPLETE` 到达后，旧位置恢复逻辑可能把列表拉回更早保存的位置。

## 审计基线与根因

- 目标分支：`refactor/media-pipeline-stability`
- 开始时远程 HEAD：`85631540c8665a8380a0202548da8c27d73618b3`
- 快速滚动库：Commons 7.1.3 使用的 `com.github.tibbi:RecyclerView-FastScroller` revision `5a95285b1f13f3e2c55c63f6260c2d74a65e896a`

根因由当前应用代码及上述精确依赖源码共同确认：库将拖动处理安装在 `thumbIV` 的触摸监听器中。`ACTION_DOWN` 调用公开的 `HandleStateListener.onEngaged()`；移动时通过 `RecyclerView.scrollBy()` 调整列表位置；释放或取消时调用 `onReleased()`。这条路径没有要求 RecyclerView 进入 `SCROLL_STATE_DRAGGING`，所以列表保持 `IDLE` 是有效且可复现的状态。

照片页原本只依据 RecyclerView 的 `scrollState` 延迟发布阶段性数据；因此快速滚动时 `submitPhotoGridResult()` 会立即应用结果。`FavoritesScrollRestorer.remember()` 在已有 `pending` 锚点时不会替换它，而旧锚点仅在 RecyclerView 的 `DRAGGING` 通知中被清除。结果发布随后更新 Adapter，并在 `setupAdapter()` 提交后调用 `photoScroll.restore()`，旧锚点仍可触发 `scrollToPositionWithOffset()`，覆盖用户刚选择的位置。

这解释了进度结束与跳回位置看起来同时发生的情形：两者都由完成阶段的结果发布驱动；实际改变滚动位置的是恢复回调中的 `scrollToPositionWithOffset()`，不是进度条本身。

## 修复与时序

MediaActivity 为照片页快速滚动条注册第三方库公开的 `HandleStateListener`，不替换库的触摸监听器，也不改变列表滚动计算。

1. `onEngaged()` 立即使旧的照片位置锚点及任何延迟恢复回调失效，并将发布门闩标记为移动中。
2. 用户拖动期间，非授权边界结果只保留最新一项；`RecyclerView.scrollState == IDLE` 不再导致结果提前发布。
3. `onReleased()` 在列表空闲时先根据现有 Adapter 的可见媒体路径捕获最终位置，再提交最新待处理结果。
4. Adapter 更新后的恢复仍使用路径身份和原屏幕偏移；若媒体排序变化，则定位同一路径的新位置。锚点缺失时，现有恢复器按媒体序号选择邻近有效媒体。
5. 权限解锁/撤销结果保持原有立即处理路径，不等待拖动结束；Activity 销毁时清理待发布回调。

快速滚动库明确只在 thumb 上安装触摸处理，未给空白 track 安装独立触摸监听。本修复使用它公开的 thumb 手势回调，不添加另一套主 Gallery 滚动算法。库提供的 `ACTION_CANCEL` 会进入 `onReleased()`。

## 文件和回归覆盖

- `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/FavoritesScrollRestorerTest.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/PhotoViewerInitializationTest.kt`

新增确定性覆盖：

- RecyclerView 保持 `IDLE` 时模拟快速滚动到列表下方，确认旧顶部锚点被废弃。
- 将当前路径从原位置移动到最终列表第 420 位，确认恢复按路径而非旧 Adapter position 定位。
- 通过 MediaActivity 实际照片结果发布入口，确认 RecyclerView 保持 `IDLE` 时快速滚动仍阻止阶段结果提交，并保留最新阶段结果。

已有 `FavoritesScrollRestorerTest` 继续覆盖 Viewer/刷新锚点、重排、目标缺失后的相邻媒体回退、旋转、空数据和延迟回调取消；`ViewerSwipePublicationGateTest` 覆盖最新结果合并和授权失效清理。

## 验证、构建和真机验收

- `git diff --check`：通过。
- 本地 Gradle 测试：执行环境缺少 Gradle 9.5.1 分发包，wrapper 下载失败（网络不可达）；已由 Actions 完整 FOSS Release 单元测试验证通过。
- GitHub Actions：[#592 — `c6328b54796a51959217622860d28f75c757dfb2`](https://github.com/Pew2018/Gallery/actions/runs/42254061959)，完整 FOSS Release 单元测试通过；ARM64-only Release APK 构建、签名、包名及 ABI 校验均通过。
- 产物：`gallery-foss-arm64-v8a-release`（10,480,344 bytes），其中 APK 文件为 `gallery-foss-arm64-v8a-release.apk`。Artifacts 有效期至 2026-11-20。
- Pixel 8 Pro 待验：在自定义配置仍显示进度时拖动快速滚动条，分别测试正常松开、ACTION_CANCEL、配置切换和最终图片重排；确认当前媒体路径及屏幕偏移保持、进度结束且没有旧位置回弹。

## 范围与限制

本次只改变自定义照片页的快速滚动状态、阶段结果发布和照片位置锚点协作。普通文件夹、收藏、回收站、Picker、搜索、排序、Viewer 数据加载和媒体扫描规则不变。快速滚动库没有空白 track 点击处理，因此本修复不为该库增加 track 跳转行为。
