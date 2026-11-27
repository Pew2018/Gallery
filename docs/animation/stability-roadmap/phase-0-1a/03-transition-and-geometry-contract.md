# TransitionSession、图层所有权与 MediaGeometry

## 会话

一次经来源接受的 Viewer launch 创建一个诊断会话。进入 ID 与返回 ID 独立，返回保留 Viewer 会话 ID，入口有网格证据时回链来源请求。旧 UI `launchId` 仅在来源 Activity 实例中有效。无有效共享映射的窗口也可以有 Viewer 会话。关闭开始前被取消的交互拖拽仍属于同一会话。

| 当前阶段 | 正常后继 | 像素和清理 |
|---|---|---|
| PREPARING | ENTRY_CAPTURED / FALLBACK / CANCELLED | 网格拥有原缩略图，来源验证绑定键和有效 drawable 后可持有 source lease |
| ENTRY_CAPTURED | ENTERING / FALLBACK / CANCELLED | 平台 callback 映射现有 proxy，MediaSharedSurface 负责应用拥有的 proxy 矩阵及属性 |
| ENTERING | VIEWING / FALLBACK / CANCELLED | PhotoTransitionCompletionGate 跟踪 entry gate，交接要核对当前媒体与 renderer |
| VIEWING | RETURN_PREPARING / INTERACTIVE_RETURN / FALLBACK | Fragment 的实际 renderer 拥有可见内容，Viewer 管理 chrome、scrim 和输入 |
| INTERACTIVE_RETURN | VIEWING / RETURN_PREPARING / CANCELLED | 取消的拖拽恢复稳定的 Viewer 层；提交时保留交互起点供平台回程使用 |
| RETURN_PREPARING | RETURNING / FALLBACK / CANCELLED | PhotoViewerReturnTarget 验证媒体、已提交版本、Holder、加载键与可见几何；Grid 持有 target lease 并保留发布门 |
| RETURNING | COMPLETE / FALLBACK / CANCELLED | 平台返回观察与网格恢复独立；释放前按自身的目标 ready 与 draw/commit 验证 |
| CANCELLED / COMPLETE | CLEANED | 恢复稳定层，释放临时 proxy、source/target lease、待处理监听及输入锁，清理可重入 |
| FALLBACK | VIEWING / COMPLETE / CLEANED | 进入降级可落到稳定 Viewer；关闭降级走现有结束/网格清理 |
| CLEANED | 无 | 终态后回调全部作废 |

一致的键为 `(viewerSessionId, operationRequestId, generation)`。同阶段重复、已完成或已清理后的提交、不同身份/代际回调都不得推进阶段；同一平台事件可能跨多个现存观察点，不据此推断所有权已经转移。来源 Holder 在停止或重新进入时释放，目标 Holder 在现行超时、目标失效或回程清理时释放。共享回调和 UI 资源所有权由现有实现维持。缺权限、键变更、目标不可用、动画禁用或现有超时出现时，经原有 fade 或清理路径降级。

drawable ready 表示加载键与 drawable 对应。renderer ready 需注明是 photo renderer、视频封面或视频真实帧。OnDraw 是 View 树工作，frame commit 是硬件 renderer 给出的提交确认，合成器呈现为 NOT_OBSERVABLE。转场 end、Viewer destroy、网格关闭分别记录。

## MediaGeometry

- 明确源像素尺寸与方向校正后尺寸。完整 EXIF 方向矩阵包含平移、旋转及镜像，先约定以原始像素边界为坐标，若解码已应用 EXIF，后续矩阵使用校正空间。视频补充旋转与像素宽高比。
- 映射链为媒体像素 → 方向校正 → drawable 或缓冲坐标 → renderer 局部 → 窗口 → 屏幕。包含 padding、imageMatrix、父级变换、窗口屏幕原点、当前 viewport 和状态/导航栏 Insets。区分 Fragment 局部、窗口局部和全局屏幕坐标。
- 可见内容裁口由投影后的真实内容、ImageView 内容区域、各裁切父级和可见 viewport 取交集。CENTER_CROP 可能填充布局框并裁切内容；FIT_CENTER 可能留边。网格格子布局框、drawable 内容框、实际可见区域各自保留。需要反算时使用同一版本逆矩阵，镜像不能丢失。
- 当前 `MediaTransitionGeometry.imageBounds` 是 ImageView 局部的 drawable 映射及父级位置。`MediaSharedSurface` 以 proxy frame、scale type 和 imageMatrix 应用既有动画。`PhotoFragment` 的平面 renderer 与 Tile 使用不同变换。`VideoFragment` 对封面、TextureView 与解码画面各有边界，`textureCrop` 只表示相对现有 viewport 的补偿。现有日志的 `getGlobalVisibleRect` 与屏幕坐标须按字段分别解释。
- 证据版本绑定媒体身份、内容签名、加载键、Adapter 版本、viewport/insets 快照和转场代际。尺寸零、矩阵奇异/非有限、未知坐标空间、离屏、版本变更或未验证授权均不能提供可靠端点。此阶段验证既有纯几何计算与合约判定，几何接线留到阶段 3。
