# Pixel 8 Pro 阶段 3 真机证据复核

本次复核用户提供的 Gallery-Diagnostics-20261119-131136.txt 与 a72b0393bf05af4dc1e23112533a6e8c.mp4。日志为 Release 1.14.1 / 32、Pixel 8 Pro、Android 16 / API 36。日志本身没有内嵌 Git SHA，不能仅凭版本号独立确认安装提交。当前代码核对基线为 15aa2ed57。录屏为 432 × 960、30 fps、167.266 秒，帧图仅用于定位可见表现，精确跳幅应使用原分辨率录屏补测。

## 已确认问题

| 优先级 | 证据与表现 | 源码判断与整改位置 |
|---|---|---|
| P1 回程末段尺寸和裁口突变 | 录屏约 00:02.45–00:03.15，长截图下拖后的回程末段，细长图像的尺寸、露出的内容和最终网格裁口不能连续交接。其他回程也需逐项复验。 | 阶段 3 只实现静态入场专用几何，返回继续走 PhotoFragment.sharedElementViewFor → MediaSharedSurface.prepare 的 CENTER_CROP 代理。入场公共坐标与真实裁口尚未进入回程。需要在既有返回目标租约内复用端点和裁口合同，同时验证手势释放时实时起点、平台回程末帧与 Grid 首帧。双层泄漏目前是待检假设，不能仅凭与网格缩略图重叠判定。 |
| P1 全部媒体入口首图等待超时 | request 45、48、51、54 均为 ALL_MEDIA。约 00:54–01:08 段的对应操作需结合原片进一步精确对时。四次都在约 1.2 秒到达 entry_timeout，matchesSelected=false、drawable=false，随后 fallback。 | publishAdjacent 在转场期间一律挂起相邻窗口。首次 publication 的发布条件、临时 selected Medium、activePagerAdapter 与真实首图应联合复现，检查已有 adapter 但无有效当前 Fragment 时是否仍挂起必要首屏。现有证据强烈提示发布与入场互相等待，尚不能只凭日志断言唯一根因。必须增加 ALL_MEDIA 真实入口测试。 |
| P1 半可见首排丢失共享入场 | request 71、73、77、81 被 static_source_capture_unconfirmed 拒绝。request 71 源 visibleBounds 只有 y=319–392，端点已生成，Glide 也已 ready，但源 confirmed 捕获没有成立。约录屏 02:21–02:43 的半可见首排操作需逐次核对。 | 目前仅在 MediaActivity 的 onCaptureSharedElementSnapshot 回调中 confirmStaticPhotoSource，再要求 Viewer start callback 确认通过。平台未建立有效 snapshot 时会失去确认，尤其是部分离屏来源。应核查真实框架映射/捕获与 snapshot 是否可用的关系，将像素端点有效性与 snapshot 生成能力分别校验，继续保留 Holder、drawable、loadedKey、版本和可见裁口的强验证。 |
| P2 降级完成与真实首图脱节 | request 45 在 13:09:41.984 entry_completed，13:09:42.052 才 viewer_first_renderer_signal。request 48、51、54 分别在完成后约 60、183、150 ms 才收到首 renderer 信号。录屏可见 Loading media 与黑底等待。 | startEntryFallback 无 renderer 时直接 finishMediaEntryTransition，日志 COMPLETE 并不能证明首图已显示。需要定义窗口退出共享等待、稳定 renderer 可绘制两个时点，保持输入和加载提示生命周期一致，并增加明确的 ready/draw 检查。 |
| P2 验收缺口 | 此次有 29 次 entry_requested 和 29 次 entry_completed，其中 4 次超时、8 次 shared_map_rejected。此前普通文件夹与合成几何测试通过仍遗漏这些真机路径。 | 补入全部媒体、半可见首排、真实长截图、转场末帧到 Grid 首帧和无 renderer 降级用例。单纯断言 shared map、Session 清理或最终四边相等，不能替代真实像素交接。 |

## 修复顺序与验收

1. 先复现 ALL_MEDIA 首图发布路径，证明 selected renderer 能在等待动画期间独立建立，检查连续重开同图。维持现有超时预算。
2. 用真实部分离屏 ImageView 复现 snapshot 缺失路径，解除对 snapshot 回调的错误确认依赖。目标仍须通过身份、加载键和端点验证。
3. 将回程起止像素与裁口对齐，单独验收下拖提交、取消和切到 B 后返回。对长截图使用边缘内容标记，逐帧确认末段不会重新改变露出的内容。
4. 验证无 renderer 时的降级、加载提示、输入锁和 ready/draw 日志。再次采集同 APK、同设备的录屏和日志。

本次产出为证据复核记录。应用代码未修改，修复尚未实施，阶段 3 的真机视觉验收未通过。
