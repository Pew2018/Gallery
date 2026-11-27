# 阶段 3 · 静态照片进入全屏实施方案

审计基线为 `perf/home-gallery-sorting` 本地提交 `495838dd762b44ee6f362e9f0e021cc99bb98e56`，与本地 `phase-2-complete` 标签一致，审计前工作树干净。本方案以《动画系统最终整改路线图》为准。

阶段 3 负责 ANIM-006 的**普通静态照片入场**。共享转场应从实际可见的网格像素位置开始，落到 Viewer 当前 Glide renderer 的真实画面，并完成同几何接手。阶段 3 的实现入口是现有 Grid → Viewer 平台共享元素会话。回程和下拖作为回归检查，相关修复留给 4A。视频、GIF、特殊格式和 Tile 接手沿用现行行为。

## 当前代码审计

| 调用点 | 源码确认 | 实施影响 |
|---|---|---|
| [MediaAdapter.kt](../../../../app/src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt#L1467) → [Context.loadImage](../../../../app/src/main/kotlin/org/fossify/gallery/extensions/Context.kt#L478) | Adapter 的请求键含裁切、圆角和内容签名，完成时写入 loaded key。ImageView 使用 CENTER_CROP 或 FIT_CENTER。Glide 可先执行 CenterCrop/FitCenter。启用圆角时 transform 可被替换为 CenterCrop + RoundedCorners，即使 View 为 FIT_CENTER。 | 画面已烘焙的裁口与 View 的 scaleType 是两层信息。不能从设置开关或最终 bitmap 比例反推完整原图。 |
| [MediaActivity.kt](../../../../app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt#L4852) | 启动和平台映射重验 Holder、绑定、loaded key 和可见性，平台映射的是原缩略图 ImageView。可见矩形用于验证和诊断，没有作为原图像素裁口交给入场代理。 | 保留现有 source lease。补充同一次入场的实际内容投影、裁口和有效性快照。 |
| [ViewPagerActivity.kt](../../../../app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt#L5114) → [PhotoFragment.kt](../../../../app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt#L155) | Glide 交付后通过 post 核对 generation、drawable 和当前页，再通知 Activity 在 pre-draw 释放 postponed entry。映射时额外检查 fit 等条件。 | READY 只证明已绑定。建立端点要读取当前真实布局和矩阵，仍复用现有释放和超时规则。 |
| [PhotoFragment.sharedElementViewFor](../../../../app/src/main/kotlin/org/fossify/gallery/fragments/PhotoFragment.kt#L182) → [MediaSharedSurface.kt](../../../../app/src/main/kotlin/org/fossify/gallery/helpers/MediaSharedSurface.kt#L21) | 当前由 Glide 实际 drawable 的 imageMatrix 计算父级局部矩形，以整数边界布置固定 CENTER_CROP 的代理。现有 `imageBounds` 未表达完整祖先变换、实际可见裁口或跨窗口 Insets。 | 目标应取真实 renderer 的像素映射，再从屏幕空间换算进代理父级。仅匹配 Rect 不能证明内容位置、方向或裁口一致。 |
| [OxygenClassicActivityMotion.kt](../../../../app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicActivityMotion.kt#L34) → [ViewPagerActivity.kt](../../../../app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt#L5212) | 现用 ChangeBounds、ChangeTransform、ChangeImageTransform。设备 SDK 的 Android 37 框架源码显示跨 Activity 传送 View 屏幕外框、可选 snapshot、ImageView scaleType，以及 MATRIX 模式矩阵。在调用 `onSharedElementStart` 前会把源状态应用到目标 View。Gallery 此时让代理接管。终点当前只核对页和 renderer 挂载，再通过租约清理还原。 | 不能用框架套到 Viewer drawable 上的源 scaleType 代替网格像素映射。结束前应再验同一 Glide drawable 和矩阵。Android 29/36 行为由设备测试确认。 |

已有 [MediaSharedSurfaceTest](../../../../app/src/test/kotlin/org/fossify/gallery/helpers/MediaSharedSurfaceTest.kt#L111) 验证构造出的两种 View scaleType 与代理框。它没有覆盖真实 Glide 烘焙裁切、跨 Activity 平台捕获和首个 Glide 接手帧。本次为源码审计，尚无同一 APK 的设备录屏证明具体照片的跳动发生在哪一帧。

## 实施顺序

### 3.1 先锁定逐帧复现

在阶段 2 基线 APK 上录制固定样本，画面包含不对称方向标记、边缘刻度和中心点。先录普通文件夹的横图和长图，分别打开和关闭缩略图裁切，再加入默认圆角设置和半可见首行。同步记录 source capture、Viewer map、平台 start/end capture、最后代理绘制与第一 Glide 绘制。逐组标明是起点对不上、动画中裁口变化，还是 Glide 接手时突变。每组登记 APK SHA、设备/API、配置及录屏时间点。记录至少一组当前可通过的对照。

### 3.2 实现可复用的静态照片端点合同

在 `MediaTransitionGeometry` 附近实现只读端点数据与纯计算。保持浮点直到布局或裁切 API 边界。

- 媒体坐标以**方向校正后的完整原图归一化坐标**为双方公共空间。分别记录原始尺寸、方向、已应用方向后的尺寸以及 drawable 像素覆盖的归一化区域。测试 EXIF 1–8，含镜像、转置和反转置。解码器已应用 EXIF 的资源只记录结果，在投影中仅应用一次方向。用现有解码/元数据路径和实际成功的 transform 确认来源。禁止在主线程同步读取 EXIF。证据没就绪时走现有入场降级。
- 端点分别记录布局框、完整原图投影、实际可见裁口。投影链包含 drawable bounds、实际 imageMatrix、padding、祖先变换、window 到 screen 的转换。裁口按 drawable 实际覆盖、View 的裁切规则、有效祖先裁切与可见窗口相交。Insets 保存在布局快照中，用来识别变化并计算可见窗口，避免对已经布局并变换到屏幕的矩形再扣一次。圆角是独立遮罩，不能拿外接矩形替代。
- 网格在成功请求的 loaded key 上保留轻量几何描述，记录实际后端和有效变换。Glide CenterCrop 以真正使用过的输入尺寸、输出尺寸和舍入推导裁切映射。圆角路径同样记录它实际先执行的 CenterCrop。FIT_CENTER 记录完整图覆盖和可能存在的留边。对已裁 bitmap，只在裁口内使用其逆投影。先核对使用的 Glide 版本实现和像素夹具再确认计算，尺寸或来源无法核验的后端使用降级。无需改变缩略图缓存键、请求优先级或重解码。
- 通过现有 `MediaTransitionHandoff` 和 Session entry token 传递不可变的源快照。快照包含媒体内容版本、Adapter committedVersion、绑定与加载版本、布局及 Insets 快照。Holder/drawable 仍由 Grid 本地租约重验和持有，跨窗口数据只含值。启动时建立快照，平台映射/捕获前复查同一来源和几何。由 Viewer 校验平台应用的入场外框与同次源快照一致；数据缺失或过期时使用现有 fade。相同 entry token 以外的读取和回调一律失效。

### 3.3 接入平台共享元素的照片入场

保留 ActivityOptions、窗口、现有代理和全套 Session 生命周期。只为已有共享资格且获得几何证据的静态照片启用新分支。进程重建、非目标媒体和无法证明映射的输出分别走现有可用路径，阶段 3 不为特殊格式增加共享资格。

1. `PhotoFragment` 在已有 pre-draw 链确认当前平面 Glide drawable 已绑定，读取 fit 状态和真正的目标矩阵、裁口以及 Glide request generation。Tile 就绪也沿用现有转场期间禁止接手规则。目标矩阵和最后代理帧来自同一份端点数据。
2. 在 `MediaSharedSurface` 增加专供静态入场的显式矩阵配置，复用现有代理。代理仍使用同一 Glide drawable 内容，从已有资源复制 drawable 状态，无额外全尺寸 bitmap。将源、目标的原图投影反算到这张 drawable 的坐标，而不是把缩略图缩放比例直接套到它上面。
3. 在 Viewer 的平台 enter transition 中，仅对带有效静态端点的代理接入专用图像变换，替代该入场的默认 ChangeImageTransform。ChangeBounds、ChangeTransform 和 scrim 保持原进度与时长。专用变换在 start/end 分别捕获已换算到当时代理局部的内容矩阵和裁口，每帧用同一进度计算统一比例、位移及裁口，再按当前代理原点换算；避免先插值屏幕矩形再对图像做一次 CENTER_CROP。对半可见来源保留真实裁口；圆角使用已验证的遮罩映射。原始 scaleType 可能被平台临时覆盖，动画使用显式端点计算。设备先检查开始捕获、端点恢复、取消和旋转路径；如果任一系统组合无法可靠受控，针对该组合使用现有 fade。
4. 入口保持一张可见媒体层。在映射阶段验证端点，start capture 再确认 source 与 renderer generation 后才接管。终点在同一 UI 提交中核对当前页、实际 drawable、有效矩阵和裁口，恢复 Glide 原有 alpha/transitionAlpha 并移除代理。比较原图标记投影和可见边界，不只比较 View 框。若映射后遇到 Insets/旋转、drawable 替换、失效或取消，通过现有 Session fallback/cleanup 恢复稳定 renderer；过期回调不能揭开新页面。通过录屏检查首个接手帧，draw/frame commit 分别记录为观察与提交。

### 3.4 补充诊断和回归

在既有诊断开关下按 entry token 记录选中分支、源/目标实际矩阵与内容裁口、裁切路径、端点差值和降级原因。保留开始、首尾捕获、接手及清理几条事件。无需逐帧生产日志，逐帧证据由测试采集。诊断字段使用匿名媒体 ID，不写原路径、授权凭据或像素内容。

## 验收矩阵

| 运行层 | 覆盖与通过条件 |
|---|---|
| JVM / Robolectric | EXIF 1–8 坐标点、已旋转 drawable 不二次应用、CENTER_CROP 预裁、FIT_CENTER 留边、关裁切但有圆角、padding、嵌套坐标和 Insets、半可见、整数布局舍入；验证无效矩阵、零尺寸、来源过期、旧 generation、取消和重复清理。 |
| 真实 Android 转场 | API 29 与 API 36/37。方图、横图、竖图、长图 × crop on/off，覆盖常见圆角设置。普通完整可见样本须实际走共享，测试不能通过降级绕过；增加半可见首行、滚动方向、状态栏/导航栏差异、旋转或 Insets 改变、等待中快速退出和动画关闭。失效用例断言明确降级并归还租约、输入锁和监听。 |
| Pixel 8 Pro 视觉验收 | 同一测试 APK、同素材和同设置。对比网格末帧与代理首帧，以及代理末帧与首个 Glide 稳定帧。自动抓取端点边界建议每边差 ≤1 个物理像素，定位标记允许 ≤2 个物理像素以覆盖采样误差。回看全段确认无黑/白闪、镜像颠倒、二次放大、错位或接手跳变。记录冷/热入场各一次，并记录任何降级。录屏缺失的实屏场景标记待验。 |
| 阶段 2 回归 | 复用普通返回、切到 B 返回、下拖取消、视频入场和返回、收藏和自定义入口以及 Session 清理用例。检查转场期间平面 Glide 与 Tile 沿用现行接手时序。执行全量 JVM、相关 instrumentation 和 ARM64 Release 构建，比较基线与修复版的首图时间及降级原因。 |

交付包含针对性代码、回归测试、可追溯测试 APK 及验收记录。按设备标出已验证的共享与降级组合。阶段 3 通过后冻结公共坐标、裁口、EXIF、端点有效性及单次图层交接合同，供阶段 4 和阶段 6 复用。

## 代码施工清单

以下是拟新增的 Kotlin 接口和接线规则。Android UI 读取及 Session 操作在主线程执行。纯几何可独立测试。先落端点与测试，再接网格和 Viewer，最后打开专用 enter transition。

### 1. `helpers/StaticPhotoEntryGeometry.kt` · 新增纯几何与值对象

矩阵用不可变值保存，Android `Matrix` 和 `RectF` 只用于读 View、绘制及调用平台 API。每个阶段的计算结果需要可以独立断言。

```kotlin
internal data class StaticPhotoContentId(val path: String, val signature: String)

internal data class StaticPhotoEndpoint(
    val layoutOnScreen: FloatRect,
    val orientedPhotoToScreen: AffineMatrix,
    val visibleOnScreen: FloatRect,
    val roundMaskOnScreen: RoundedRect?,
    val window: WindowGeometry,
)

internal data class StaticPhotoGridCapture(
    val content: StaticPhotoContentId,
    val adapterVersion: Long,
    val drawable: ThumbnailEvidence,
    val drawableToScreen: AffineMatrix,
    val layoutOnScreen: FloatRect,
    val visibleOnScreen: FloatRect,
    val window: WindowGeometry,
)

internal data class StaticPhotoEntryPlan(
    val token: MediaTransitionSessionCoordinator.Token,
    val start: StaticPhotoEndpoint,
    val end: StaticPhotoEndpoint,
    val proxyDrawableToOrientedPhoto: AffineMatrix,
) {
    fun frame(progress: Float, proxyToScreen: AffineMatrix): StaticPhotoFrame?
}
```

- `readEndpoint(view, decodedPixelsToOrientedPhoto, mask)` 取 `drawable.bounds`，先将 drawable 坐标换到解码像素坐标，再乘 `imageMatrix` 与 padding 平移，最后乘 View 到屏幕矩阵。用 `transformMatrixToGlobal` 的受测实现取得祖先变换，并以同一 View 的 `getLocationOnScreen` 核验坐标原点。把显式 clip、适用的 `clipToPadding` / `clipChildren` 和窗口可见范围各自映射到屏幕再相交。保留 `layoutOnScreen` 给平台 state 校验。空裁口、非有限值、不可逆和无法表达的倾斜、透视或遮罩返回具体失败原因。
- `AffineMatrix` 的乘法顺序固定为左乘映射。使用四个方向角点加中心点比较投影。EXIF 映射先测原始像素边界到方向校正后边界。解码已应用 EXIF 时，尤其是 2、4、5、7，后续只使用解码后的坐标。
- `frame(p, proxyToScreen)` 插值方向校正后的等比像素尺度、平移、实际裁口及圆角。用方向校正后的原图宽高把归一化坐标还原到像素单位，再判断尺度是否等比。计算 `drawableToProxy = inverse(proxyToScreen) * orientedPhotoToScreen(p) * proxyDrawableToOrientedPhoto`。裁口和圆角同样映射回代理局部。单像素舍入差允许在端点比较时处理，避免逐帧舍入。若源 drawable 的采样比例在两个方向上有舍入差，用标记点验证等比投影误差在门限内，超限则降级。

### 2. `Context.kt`、`MediaAdapter.kt` · 绑定真实变换来源

1. 为 `Context.loadImage` 与 `loadImageBase` 的 Grid 调用增加可选的内部成功观察入口，透传到 Glide 的 `onResourceReady`。如果要保持现有公开参数类型，使用 internal overload。观察值包含本次选择的解码路径、Glide 实际 Target 宽高、输出 bitmap 像素尺寸、实际 transform 链和圆角半径。变换选择和记录由同一分支产出。对现有标准 Glide 请求使用 `DrawableImageViewTarget` 子类，覆写 `getSize/removeCallback`，成对转发并记录第一次交给请求的宽高。保留 ViewTarget 自己的 `getRequest/setRequest` 实现和 Glide 的 View 请求管理。接入前核对显式 Target 的 builder 不会遗漏原 `into(ImageView)` 自动应用的 options。必须核对 `into` 启动、请求取消、重绑以及内存与磁盘命中仍与基线一致。专用解码及 Picasso 回退只记录 `Unknown`，由现有显示流程完成。
2. 在现有 `ThumbnailLoad` 缓存 pending 成功描述。`onLoaded` 中只有 request token 相同且 `view.drawable === resource` 时，才把描述和 loaded key 一起提交。几何描述放在现有 `ThumbnailLoad` 上，保留 `getStaticPhotoThumbnailEvidence(image)` 查询入口。每次重绑、回收或取消时随旧 load 失效，无持有 Activity 或 bitmap 的新缓存。
3. 新增 `resolveThumbnailProjection(header, evidence)`。FIT 路径直接使用已校正的完整原图范围。预裁路径使用当前项目 Glide 5.0.7 的 Downsampler 尺寸计算，输入是 Reader 确认的格式、原始/方向校正尺寸、对应 SDK、请求实际尺寸和实际 downsample 策略。仅实现可从头信息确定尺寸及取整的标准 JPEG/PNG 分支，先用同版本 Glide 真实输出 fixture 验证。其他已共享的静态格式须按实际解码路径另行验证，同格式的动态变种保留其当前资格。验收表须按格式明确记录覆盖和降级。尺寸读取放入 `PhotoFragment.loadImage` 已有后台路径，使用 bounds-only 读取加现有 EXIF；反推函数无需重新解码位图。
4. 预裁矩阵按 Glide `TransformationUtils.centerCrop` 计算 `s = max(outW / inW, outH / inH)`、居中偏移，以及 Java `(int)(offset + 0.5f)` 的向零截断。圆角使用 **CenterCrop → RoundedCorners** 的真实顺序。在 Grid endpoint 合成时先求这张 bitmap 像素对完整原图的逆投影，再应用 Grid 的 `imageMatrix`。内存命中、RESOURCE 磁盘命中与冷请求走同一个重建函数，不依赖 transform 本次是否执行。分支未经夹具验证、请求尺寸未知、输出尺寸不一致或 Drawable 无法对应时返回 `thumbnail_projection_unknown`。现有特殊媒体共享路径保留。阶段 3 不让不确定的普通 JPEG/PNG 以旧几何冒充通过验收。

### 3. `MediaActivity.kt`、`MediaTransitionSessionCoordinator.kt` · 提交同一入场的源证据

- 在 `launchPhotoViewerWithSharedElement` 通过现行 Holder/loaded key 验证后，调用 `readStaticPhotoGridSnapshot(adapter, holder, thumbnail, path)`，保存原 drawable 布局、drawable 到屏幕、祖先裁口及上一步的轻量来源描述。这里尚未读取原图头，照片坐标的最终投影在 Viewer 收到 header 后算出。对普通静态候选写入 tiny entry mode 标记 `STATIC_PHOTO`，其他现有媒体路径使用 `LEGACY`。进程重建时，mode 与 Session claim 无法相互验证即走 fade。
- 在确立 `GridViewerSource` 租约后、`startActivityForResult` 之前调用 `session.publishStaticPhotoSource(session.entry, snapshot)`。Session 存的 snapshot 只含值和已有内容标识；view、Holder、drawable 留在 Grid。Grid 在 exit callback 的 `onMapSharedElements` 重验 adapter version、Holder、loaded key 和实际端点。再在 `onCaptureSharedElementSnapshot` 调用 `super` 前同步重核一次，有变化则撤销该 entry 的证据；Viewer start callback 必须看到已确认的同 token 快照。保留系统 snapshot 行为与 Grid 原来的 source lease。
- 补充 `readStaticPhotoSource(token)`、`confirmStaticPhotoSource(token)` 和 `invalidateStaticPhotoSource(token)`。更新仅限当前 entry；确认后不可覆盖端点，只可撤销。同一端点检查允许平台重复回调。清理挂到现有 entry 完成、撤销、Session 取消或 `clean()`，无独立全局 store。Grid 正常 detach 与 Activity 销毁分开处理，避免在 Viewer 动画进行期间把已经确认且不可变的快照无条件作废。

### 4. `PhotoFragment.kt`、`MediaSharedSurface.kt` · 只新增入场专用代理配置

- `beginImageRequest()` 清空 `staticEntryImage`。后台读取的 `StaticPhotoHeader` 与 Glide 成功回调分别按 `imageRequestGeneration` 核验后，绑定同一个已交付 drawable 信息。只认当前普通静态、Glide fit、未手动旋转、未启用 Tile 或其他 renderer 的页面。Picasso 回退仍可照常显示，几何来源不确定时不接专用代理。
- 增加 `prepareStaticPhotoEntry(source, token): StaticPhotoEntryMapping?`。从当前 `binding.gesturesView` 重读实际投影，建立 `StaticPhotoEntryPlan`。在 renderer 仍保留可见性的情况下把映射交给 `MediaSharedSurface.prepareStaticEntry(...)`。返回 mapping 持有 token、页面、实际 drawable 引用、generation、代理和结果端点。现有 `sharedElementViewFor` 继续服务回程及既有媒体路径。
- 专用 proxy 继承 `ImageView`，复用原有 attachment、布局、可访问性和 `takeOver/clear`。其 `onDraw` 对当前 `StaticPhotoFrame` 先 clip 可见矩形及圆角，再 concat matrix 并 draw 同一个 Glide 资源的独立 drawable 实例。实际 drawable 无法独立持有可变 bounds 时走 fade。静态入场专用绘制读取自己的矩阵，不受框架临时设置 ImageView scaleType 的影响，也不依赖 API 29 的 `animateTransform`。静态 proxy 派生值和监听随原有 `PROXY` 租约清除，释放后不能继续写入。

### 5. `OxygenClassicActivityMotion.kt`、`ViewPagerActivity.kt` · 只替换照片入场的图像变换

| 现有调用点 | 代码行为 |
|---|---|
| `onCreate` 创建 enter transition | 增加 `staticPhotoEntry` 可选参数。该模式用 `StaticPhotoEntryTransform : Transition` 代替默认 `ChangeImageTransform`，其余 ChangeBounds、ChangeTransform、scrim、时长及插值保持现状。Grid exit/reenter 与 Viewer return 不传该参数。 |
| `mapEntrySharedElement` | 若为 `STATIC_PHOTO`，从 Session 读源快照并调用 `prepareStaticPhotoEntry`。成功才调用现有 `entry.map()` 和登记租约；读取失败按已有 `rejectSharedEntry` 路径处理。重复 map 仅返回相同 token/page 的代理。 |
| `captureEntrySharedElement` | 核对确认后的源快照及当前平台应用的源外框。先给 proxy 应用 `frame(0)`，再调用 `takeOverSharedEntry`。捕获失败按现有 reject/fallback，不隐藏真实 renderer。 |
| `StaticPhotoEntryTransform.captureStartValues/captureEndValues` | 仅识别绑定 token 的静态 proxy。Start 捕获包含被平台改写后的源布局，End 捕获包含恢复后的真实代理布局。两个值均保留对同一计划的引用及 capture 类型；End 按真实目标端点校验。 |
| `StaticPhotoEntryTransform.createAnimator` | 只在两份 capture 和当前 token 匹配时创建 animator。用与 ChangeBounds 相同的插值进度计算 frame；绘制前利用 proxy 当前布局求局部矩阵，避免假定两个子 animator 的更新次序。帧计算失败只上报一次，由 Activity 调用 `startEntryFallback`。取消时移除 animator 的更新监听并撤销绘制状态。 |
| `onSharedEntryTerminal` | 仅在非取消且仍处于 SHARED 时，把专用代理固定到目标帧，调用 `PhotoFragment.validateStaticEntryHandoff(mapping)` 重读真实 drawable、generation、窗口、内容矩阵和裁口。通过后才设置 `photoSharedElementMapped = true` 并走 `finishMediaEntryTransition`。失败传 `handoff_geometry_changed` 等具体原因，释放代理并交由现有 fade。 |
| `startEntryFallback` / `cancelMediaEntryCallbacks` | 通过现有 lease 顺序恢复 alpha/transitionAlpha 和输入，移除专用画帧状态与监听，再清 Activity 持有的 mapping。fallback 后旧平台 terminal、下一页的旧 Glide 回调和过期 animator 均不得改写新 proxy。 |

端点匹配以同一屏幕坐标下的四边、四角和中心点为准。测试须证明共享结束及 fallback 都只清理一次，成功状态仍允许现有共享回程。对专用 transition 的起止捕获、动画时钟与代理真实绘制顺序，先在 Android 29 和 36/37 跑真实平台用例，再启用完整分支。

### 6. 代码提交与验证顺序

1. **几何和基线测试。** 新增 `StaticPhotoEntryGeometryTest`，覆盖方向、真实裁口、半可见、圆角遮罩、嵌套 Insets 及路径插值。用奇数采样尺寸、极长图和同图八种 EXIF 做夹具。用真实 Glide 输出验证采样尺寸及四点投影，覆盖冷请求、内存缓存与 RESOURCE 磁盘缓存。
2. **证据链。** 验证普通 Grid 点击入口、Adapter committedVersion 更替、Holder 同路径重绑、资源替换以及 capture 前布局变化。扩展已有 Session 测试，覆盖证据确认后的读取、撤销、重复和跨代回调。`STATIC_PHOTO` 元数据缺失时必须被测试识别为降级。
3. **转场与接手。** 新增 `StaticPhotoEntryTransitionTest`（androidTest），用图像标记验证起点和终点，而非仅断言 `onTransitionEnd`。断言一份 entry token 下只发生一次接手或降级与清理。四种图形 × crop on/off 的普通样本以及默认圆角必须通过共享断言。另测目标 `Drawable` 替换、取消、旋转与 Insets 几何变化。
4. **回归和交付。** JDK 21 下执行 `:app:testFossDebugUnitTest`、`:app:assembleFossDebug :app:assembleFossDebugAndroidTest`、相关设备 instrumentation，以及 `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`。阶段 2 用例沿用现有测试。最后用同设备、同图和同设置完成录屏对比，并记录构建 SHA 与共享/降级明细。
