# Pixel 8 Pro 第二轮验收与主页入口移除

## 验收结论：未通过

证据为用户提供的 Gallery-Diagnostics-20261119-232136.txt 和 ce654d23f27c224183bac91975551059.mp4。日志记录 Release 1.14.1 / 32、Pixel 8 Pro、API 36，覆盖 23:19:22.794–23:21:36.906；录屏 119.966 秒、432 × 960、30 fps。按用户安装流程对应上一轮修复提交 7ef31a109，但日志未内嵌 SHA，版本号不足以独立证明安装提交。

### 已改善且本轮证据支持的路径

- 33 次 entry_requested、33 次首 renderer 信号、33 次 cleanup。32 次 shared_map_accepted，无 shared_map_rejected，无 entry_timeout，无 entry_fallback_started。
- 31 次共享完成，完成时均 matchesSelected=true、drawable=true，首 renderer 信号均早于完成；另一次共享会话在入场中主动关闭并记录 entry_cancelled，一次启动时 thumbnail_not_ready 改走 window_fade。不能将这两次算作共享完成失败或遗漏清理。
- ALL_MEDIA 共 10 次（9 张静态照片、1 个视频），全部共享完成，进入耗时 303–476 ms，没有重现上一轮约 1.2 秒首屏超时。
- 半可见上排 request 11、13 的来源裁口高度只有 241 / 670 px，request 15 为 570 / 670 px，均确认来源并共享完成。未重现 static_source_capture_unconfirmed。
- 29 次原生回程都记录 target_drawn、viewerResult=true、firstGridFrameObserved=true。该结果证明会话闭合，不能据此证明回程像素连续。

### 阻止通过的可见问题

录屏约 01:58.3–01:58.6，长截图下拖提交后仍保持细长整图，随后在返回右上角缩略图时突然切成放大的局部裁口。约 01:53–01:55 的重复返回也出现同类交接。帧图以本地临时证据保留，不把用户照片提交到仓库。

对应最后一次回程 request 89：23:21:29.980 请求，23:21:30.029 动画开始，23:21:30.161 结束，23:21:30.245 Grid 闭合。目标为 [673,320][1343,990]，约 670 × 670，代理 drawable 为 299 × 2992。结束回调记录代理矩阵横纵轴均为 3.0501673，因此等比并不等于端点内容匹配；该 drawable 对 670 px 方形裁口的单纯中心裁切比例约为 2.241。回调先后可能影响这个瞬时矩阵，不能仅凭日志认定唯一根因，但录屏已直接证明末段交接不连续。

下一轮应检查 MediaReturnCrop 与平台回程 ChangeBounds / ChangeTransform 的实际进度、目标捕获和最终 snapshot → Grid drawable 的交接。测试必须检查实际末帧的图像内容与首个 Grid 帧一致，不能只断言两个轴等比或会话闭合。本次主页入口移除未改动这些动画代码，未声称该问题已修复。

### 尚未覆盖或不能下结论的项目

- 31 条静态端点记录全部 crop=true、radius=0；本轮没有证明关闭缩略图裁切或开启圆角时通过。
- 没有发生等待 renderer 的入场降级，故“等待真实首图后再完成”未在这轮真机中触发；现有单元回归仍保留。
- EXIF 方向覆盖、60/120 Hz 帧时间不能从该压缩录屏独立确认。
- ALL_MEDIA 的 entry_requested 正确标记来源，而后续 entry_completed 写成 FOLDER，是诊断来源标记不一致，不能用后者误算 ALL_MEDIA 样本数。

## 按用户要求移除主页“显示所有文件夹的内容”

- menu_main 的 show_all 默认隐藏，刷新也保持隐藏，删除点击分支，不再占据固定顶栏按钮位置或溢出菜单入口。
- 自定义顶栏对话框删除“切换浏览视图”固定行；“切换列表／网格视图”是独立功能，继续保留。
- 正常 MainActivity 与启动页清理旧的 all-files 记忆；启动和底栏相册目的地统一为文件夹列表，旧设置或备份无法重新把正常主页送回已移除模式。
- 保留 ALL_MEDIA 底层集合，供全局搜索、Viewer 和第三方选图等原有路径使用。照片页、收藏页、文件夹排序／筛选／列表网格设置和第三方 chooser 的加载分支继续保留。

此功能变更与本轮动画验收结论相互独立，不以隐藏 ALL_MEDIA 主页入口作为旧动画问题通过的依据。

## 本次代码变更验证

完整 JVM 回归 1267 tests，0 failures，0 errors，0 skipped。覆盖启动／底栏旧模式迁移、主页和收藏页菜单刷新、自定义顶栏主页与文件夹页、搜索生命周期及既有 Viewer 回归。安装包构建和签名另在提交后核对。
