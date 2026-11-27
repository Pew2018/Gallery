# 06 · Pixel 8 Pro 真机验收与采集步骤

> 设备：Google Pixel 8 Pro，Android 16，手势导航。
> 状态：**PENDING_DEVICE_VALIDATION**。本轮无设备直连、无 Android 16 模拟器，以下任何项都不得预先标注 PASS。

## 1. 安装与诊断准备

1. 安装 CI 产出的 ARM64 FOSS Release APK（产物信息见交付记录）。
2. 开启 Gallery 内置诊断（设置 → 高级 → 诊断日志；24 小时后自动关闭）。
3. 每次采集前记录时间戳，便于把 logcat / dumpsys / 录屏对齐：
   ```
   adb shell date
   adb logcat -c
   ```

## 2. 验收矩阵（逐项、逐阶段观察）

每个手势必须分五段观察：**开始 → 中间 → 取消 → 提交 → 最终稳定画面**。

| 测试 | 操作 | 预期 | 判定 |
|---|---|---|---|
| T01 | 设置主页 → 外观与界面，左侧返回 | 完整上一页面，无异常黑色区域 | PENDING |
| T02 | 同路径右侧返回 | 正确的系统右侧返回动画 | PENDING |
| T03 | 返回到一半取消 | 页面恢复，无闪烁 | PENDING |
| T04 | 分类 → 主题设置 → 返回 | 显示正确分类页面 | PENDING |
| T05 | 相册主页 → 普通文件夹 → 返回 | 完整显示主页 | PENDING |
| T06 | 普通文件夹 → Viewer → 左侧返回 | 系统原生跨 Activity 动画（整个窗口缩放，真实网格在后） | PENDING |
| T07 | 同上右侧返回 | 系统原生右侧动画 | PENDING |
| T08 | Viewer 返回到一半取消 | 照片完整恢复、可继续浏览 | PENDING |
| T09 | Viewer 手指下拖关闭 | 保留图片回到网格效果 | PENDING |
| T10 | Viewer 工具栏返回 | 正确返回、不重复动画 | PENDING |
| T11 | 收藏文件夹中的 Viewer 返回 | 无黑屏和媒体错位 | PENDING |
| T12 | `.nomedia` 文件夹中的 Viewer 返回 | 内容及位置正确 | PENDING |
| T13 | 快速连续进入与退出 | 无闪烁、崩溃或不可点击 | PENDING |
| T14 | 系统深色 + Gallery 浅色 | 颜色与窗口正常 | PENDING |
| T15 | Gallery 深色 | 无异常背景或系统栏颜色 | PENDING |

## 3. 黑色区域归因采集（关键）

对 T01 路径，在手势**开始/中间/提交**三刻分别抓取：

```
adb shell dumpsys window windows > win_<phase>.txt
adb shell dumpsys activity activities > act_<phase>.txt
adb shell dumpsys SurfaceFlinger --list > sf_<phase>.txt
adb shell am broadcast -a android.intent.action.RUN -e cmd '...' # 如适用
```

- 用 `uiautomator dump` 或录屏确认父 Activity 是否仍在栈中且已绘制。
- Perfetto / Winscope 录制一次 `View` + `WindowManager` + `SurfaceFlinger` trace，检查 Shell transition 类型（back-to-activity）与参与者。
- Gallery 诊断导出：设置内导出 diagnostics，检索模块 `PredictiveBack` 的 `window_state_fields` 与 `back_callbacks`，对照 `windowBackgroundArgb`、`decorBackgroundArgb`、`translucent`、`hasEnabledCallbacks`。

判定规则：
- 若 `act` 中父 Activity 已不存在 ⇒ 分支 A；
- 若父 Activity 存在但未成为预览目标 ⇒ 分支 B（重点看 translucent / 场景转场回退）；
- 若 Decor/content bounds 只覆盖部分屏幕 ⇒ 分支 C；
- 若子窗口 translucent/背景色异常 ⇒ 分支 D；
- 若 SurfaceFlinger 显示系统背景或任务快照图层 ⇒ 分支 E；
- 若状态栏 underlay 区域不一致 ⇒ 分支 F。

见 `03-black-background-evidence.md`。没有这些证据前，黑色区域修复项保持未完成。

## 4. Viewer 平台返回核对

在 T06/T07 中，Gallery 诊断应出现：
- `Viewer` / `back_ownership`，`appOwns=false`（稳定页）；
- `ViewerReturnDiagnostics` / `viewer_platform_return`，`reason=no_app_visual`。

若出现 `requestViewerClose` 的共享元素回程日志或 repeated transition，则说明二次动画未消除，需回到 04 检查所有权判定。

若完全没有系统动画（硬切），记录为 H-TRANSLUCENT 的设备证据：检查 WindowManager 是否因 `windowIsTranslucent=true` / 场景转场回退到 callback，**不要**直接把 Viewer 改成不透明后宣称完成；需据证据决定窗口方案并保留入场共享元素。

## 5. 结果

所有条目初始为 `PENDING_DEVICE_VALIDATION`。完成后在此文件逐项改为 PASS/FAIL 并附截图与日志文件名；未验证项保持 PENDING。
