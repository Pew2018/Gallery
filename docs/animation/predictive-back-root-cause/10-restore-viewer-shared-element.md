# 10 · Viewer 全屏进入/退出恢复为 7ee6610 共享元素

> 基线：用户验收通过的 CI run
> <https://github.com/Pew2018/Gallery/actions/runs/45012109741>，
> 对应提交 `7ee661082464dd0f015f0b92f15ac8a87572abd3`。
>
> 本文是 07/08/09 的后续结论：07/08/09 记录的 app-owned 入场、返回锚点、
> 返回探针与系统跨 Activity 返回属于中间方案，已在本次改动中撤销，不再代表
> 当前代码。

## 1. 恢复目标

Viewer 进入和退出重新由平台 Activity Scene Transition 的共享元素驱动：

- 网格缩略图通过 `ActivityOptions.makeSceneTransitionAnimation()` 启动 Viewer。
- Viewer 使用 `window.sharedElementEnterTransition` 从缩略图几何展开。
- 关闭时使用 `window.sharedElementReturnTransition` + `super.finishAfterTransition()` 回收到缩略图。
- 网格使用 `sharedElementExitTransition` / `sharedElementReenterTransition` 接收。
- Viewer 的返回回调始终启用，预测性返回仍由应用现有的交互式 dismiss + 共享元素回程处理。

## 2. 改回的关键契约

| 位置 | 恢复后的行为 |
|---|---|
| `GalleryViewerTransitionTheme` | `windowIsTranslucent=true`、`windowContentTransitions=true`、共享元素 enter/return=`@android:transition/move` |
| `MediaActivity.launchPhotoViewerWithSharedElement()` | 所有 API 都走 `makeSceneTransitionAnimation`，不再设置 `EXTRA_APP_OWNED_ENTRY` |
| `ViewPagerActivity.configureMediaTransitionSession()` | 无条件 `postponeEnterTransition()` + `sharedElementEnterTransition` + `setEnterSharedElementCallback` |
| `ViewPagerActivity.releasePhotoSharedElement()` | 无条件 `startPostponedEnterTransition()` |
| `ViewPagerActivity.finishAfterTransition()` | 直接 `requestViewerClose("system_back")` |
| `ViewPagerActivity.requestViewerClose()` | 恢复 `sharedElementReturnTransition` + `super.finishAfterTransition()` |
| `ViewerBackGesture` | 恢复 `OnBackPressedCallback(true)`，不再按 API/状态启停 |
| `OxygenClassicActivityMotion.configureShared()` | Viewer 窗口动画恢复为 `0`，不安装普通页的 custom CLOSE 样式 |
| `MediaActivity.photoReturnTarget()` / `showInteractiveGridTarget()` | 恢复为基线实现，不再使用 app-owned 返回锚点/探针 |

## 3. 删除的中间方案代码

- `helpers/GridPhotoEntryPreview.kt`
- `helpers/ViewerBackOwnership.kt`
- `helpers/ViewerReturnTargetProbe.kt`
- `MediaTransitionSessionCoordinator` 的 entry preview/backdrop 状态
- `PhotoViewerTransition.EXTRA_APP_OWNED_ENTRY` / `APP_RETURN_MS`
- `MeasuredPhotoReturnView.photoHeader` 与 `PhotoFragment.entryReturnHeader/entryReturnDrawable`
- 对应的 app-owned Viewer 测试

## 4. 保留的普通页预测性返回修复

以下**没有回退**：

- `OxygenClassicPageBackWindow`
- `oxygen_classic_page_back_enter/exit.xml`
- `OxygenClassicActivityMotion.install()` 的普通页 custom CLOSE 分支
- `AppTheme.windowBackground` / `OxygenClassicTaskBackground`
- 配置管理页覆盖期间的 Photos 网格保留
- `PredictiveBackSupport`

## 5. 取舍

恢复共享元素后，Viewer 再次满足以下任一条件：

- `windowIsTranslucent=true`
- 启动时使用 `ActivityOptions.makeSceneTransitionAnimation()`（`mHasSceneTransition`）

因此 Android 14+ 的 `BackNavigationController` 会把 Viewer 的边缘返回判为
`TYPE_CALLBACK`，系统不会做跨 Activity 整窗动画。Viewer 的返回动画回到
`7ee6610` 的应用共享元素回程，这是恢复进入/退出动画的必然结果；普通页面的
预测性返回不受影响。

## 6. 验证

已执行：

```bash
./gradlew :app:compileFossDebugKotlin
./gradlew :app:assembleFossDebug
./gradlew :app:testFossDebugUnitTest \
  --tests 'org.fossify.gallery.helpers.OxygenClassicActivityMotionTest' \
  --tests 'org.fossify.gallery.helpers.MediaAdapterStabilityTest' \
  --tests 'org.fossify.gallery.helpers.GalleryRootTransitionTest' \
  --tests 'org.fossify.gallery.helpers.PredictiveBackCloseAnimationTest' \
  --tests 'org.fossify.gallery.helpers.PhotoGridRetentionPolicyTest' \
  --tests 'org.fossify.gallery.helpers.PhotoViewerInitializationTest' \
  --tests 'org.fossify.gallery.helpers.PhotoViewerReturnTargetTest' \
  --tests 'org.fossify.gallery.helpers.SystemBackTransitionTest'
```

结果：编译、组装、以上测试全部通过。Robolectric 使用 JDK 21 与本地
`ROBOLECTRIC_DEPENDENCY_DIR`。

仍需设备逐帧验收：

1. 网格点击 → 缩略图展开到全屏。
2. 左/右边缘返回 → 连续回收到缩略图。
3. 边缘返回取消 → 回到全屏。
4. 工具栏返回、下拉返回 → 同一共享元素回程。
5. 视频/不支持静态几何的媒体降级。
6. 深色/浅色主题。
7. 普通设置页/配置页预测性返回保持现有 custom CLOSE 行为。
