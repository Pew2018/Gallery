# 缩略图调度与诊断

`MediaAdapterStabilityTest.distantVisibleHoldersStartWithHighPriorityWithoutAListUpdate` 在已提交的 3,080 项真实 RecyclerView 上固定 Adapter version，跳到位置 2,600。旧代码测得正常非零 View 高度、正确绑定路径、空 Diff 队列及 Glide `LOW`。原优先级依赖 LayoutManager 上一次布局报告的范围，远端位置在绑定时还未成为可见项。Glide 在创建请求时固定优先级，快滚稳定后也没有使这类请求进入可见档位的接线。受控失败条件是落点 Holder 的实际 Glide 优先级低于 HIGH。同一用例在修复后通过。

`MediaAdapter` 继续立即提交原本判为 HIGH 的请求。其他新绑定只保存一个当前请求，随 Holder 附着于 RecyclerView 以 HIGH 提交。已附着 Holder 的新键也以 HIGH 提交。相同键保留现有请求和 Drawable。换键和回收先作废 token，再取消 Picasso 并清 Glide 及 Drawable。通过真实 Glide 监听器注入 A→B→A 旧回调，确认返回已处理以阻止 Glide 把旧资源设给 View。快滚停止时无需清空、重复发起或遍历整个列表。图片尺寸配置、裁切、变换、签名和磁盘缓存策略沿用已有实现。

`Context.loadImageBase` 和 SVG 在资源回调前核对当前 token，PNG fallback 的成功也更新当前绑定状态。采样日志用 Adapter ID、version、Holder ID 和请求代际关联匿名媒体，关联当前尺寸、可见矩形、滚动状态和优先级。创建、提交 Glide/Picasso、资源就绪、观察到绑定和可见 pre-draw 各自记录。Glide 的 cache data source 可见，Picasso fallback 只报告 UNKNOWN。请求取消、失败及收到的过期回调按原因记录。REQUEST_STARTED 表示提交加载库的时点，实际解码线程开始时间 NOT OBSERVABLE。FRAME_OBSERVED 是 pre-draw 候选，呈现为 NOT OBSERVABLE。

每个 Adapter 每秒最多选择四个完整请求样本，并保留旧 drawable 事件的独立限频。沿用 GalleryDiagnosticLogger 默认关闭、24 小时过期、后台写入、隐私处理及轮转。未观察到的取消、内部 cache/解码队列活动与未采样布局不能从样本数反推。真机上的磁盘慢读、解码竞争、全视口白块时长和其他测量问题仍开放。扩大预取或清缓存均会改变资源占用，当前改动限于已复现的请求优先级。
