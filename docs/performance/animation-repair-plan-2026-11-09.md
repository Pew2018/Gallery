# perf 分支动画修复方案：录屏问题与代码证据

基线：本地 `perf/home-gallery-sorting`，HEAD `bb8a12307c811553964c2fdeab090cdda9254c95`。检查开始时工作区干净。此方案编写时仅新增文档；后续实施与验证见 [执行记录](validation-animation-2026-11-09/README.md)。下列现有文件行号对应此提交；新增类、接口与参数均为拟议设计。

录屏：`fa83ab6cc1574b1aea0a8509fe30ee2d.mp4`，约 29.37 秒、30 fps。时间点为近似播放时间。

## 1. 结论与证据边界

主要问题并不只是时长不统一，而是转场的控制权、共享画面的范围、就绪条件和返回目标没有形成完整协议。

可以从当前代码直接确认：

1. 共享元素采用 ChangeBounds，但没有完整控制媒体矩阵、裁切和截图背景。
2. 同时保留通用窗口动画；查看器最终 finish 还会应用通用退出动画。
3. 视频封面加载完成不报告 imageRendered，解码首帧才报告；封面与 TextureView 会在转场期间切换。
4. 返回路径固定为最初打开的文件，横滑到其他文件后不重映射。
5. 相册目标只要求封面可找到，未要求首屏可见网格已具备稳定画面。
6. 图片在 100ms 后可能启用另一套渲染视图，早于 280ms 转场结束。
7. 横滑开始落选时立即更新标题，视觉画面尚未稳定。

白闪、黑块、尺寸跳变与以上实现高度吻合，但单凭静态代码不能把每一次白闪都唯一归因于某个窗口属性。需要真机记录窗口、共享元素映射、就绪与首帧事件来闭环。

对上一轮观察作一项收紧：人群视频 P1060476.MP4 在录屏中能看到放大；台阶合影打开后显示 00000PORTRAIT…，当前 MyPagerAdapter.kt:67 根据 medium.isVideo() 选择 VideoFragment，否则选择 PhotoFragment。不能只凭列表时长标记就确认它实际作为视频打开。用户指出的“有一个视频未展示放大动画”必须作为独立必验项，但具体条目尚不能从录屏和静态代码唯一锁定，不应误报已经找到了唯一根因。

## 2. 逐项证据与修改位置

### A. 白闪、整页淡化、两套动画叠加

录屏：1.7–2.2、3.0–3.5、5.9–6.3、7.9–8.4、18.2–18.6、23.9–24.3、26.2–26.8 秒。

代码证据：

- helpers/OxygenClassicActivityMotion.kt:8–12：除根页面外统一安装 OxygenClassicPageWindow，没有共享元素模式判断。
- activities/BaseViewerActivity.kt:28–35：onCreate 安装上述窗口动画；finish 调用 super 后无条件 applyClose。
- activities/ViewPagerActivity.kt:785–799：共享返回完成后再次进入 finish，最终会经过 BaseViewerActivity.finish，存在追加通用关闭动画的路径。
- activities/MediaActivity.kt:719–727：已经 postponeEnterTransition，随后仍 install 通用窗口动画。
- res/values/oxygenos_classic.xml:36–43 与 res/anim/oxygen_classic_page_*.xml：窗口层带 alpha 与横向位移，170/140ms；共享元素分别 280/300ms。
- MainActivity.kt:2248、MediaActivity.kt:899/3324、ViewPagerActivity.kt:554：注释称只运行共享元素，但实际只设置了 sharedElement transitions；没有形成排除窗口动画及显式处理普通 enter/exit/reenter/return transitions 的统一配置。
- styles.xml:24：查看器主题继承 TranslucentTheme。SimpleActivity.kt:47–69 已经在 super 前后设黑色窗口背景，因此不能把问题简单归为“忘记设置黑底”。主题与系统共享元素背景淡化过程仍需实机核对。

修改方案：

1. 扩展 OxygenClassicActivityMotion，增加 `Mode { ROOT, PAGE, SHARED_MEDIA, SHARED_ALBUM }`，通过统一配置入口决定动画控制权。
2. 共享模式清除通用 windowAnimations，明确设置普通 enter/exit/reenter/return transitions 与 overlap 策略；共享内容仅由媒体转场控制器处理。配置必须覆盖源 Activity 和目标 Activity，且在捕获开始前完成。
3. BaseViewerActivity.finish 不再无条件 applyClose。只有 PAGE 模式走原有关闭动画；共享完成、共享取消和降级各有一次性结束出口，避免 finishAfterTransition 递归时追加动画。
4. MainActivity、MediaActivity、ViewPagerActivity 全部通过相同配置方法建共享转场，删除各处复制的 ChangeBounds 构造和只靠注释的隐式约定。
5. GalleryViewerTransitionTheme 明确媒体窗口底色、预览和背景淡化策略；GalleryPageTransitionTheme 明确主题页底色。先保留现有主题语义，真机验证后才决定是否取消 translucent；不能直接改父主题来赌白闪消失。
6. 背景、系统栏、工具栏由一个转场进度/生命周期驱动，禁止整棵媒体 root 做 alpha 交叉淡化。目标窗口接管前保留源画面，目标首帧有效后再交接。

验收：共享过程中不出现通用横移动画；没有中间白屏或整页灰白重影；关闭只发生一次。

### B. 黑块缩回、照片比例和裁切跳变

录屏：6.8–7.4、8.8–9.3、11.6–12.1、14.0–14.4 秒。

代码证据：

- PhotoFragment.kt:145–158 返回 gesturesView、subsamplingView 或 gifView 作为共享元素。
- pager_photo_item.xml:9–13、29–34：前两者是 match_parent 全屏视图。
- PhotoFragment.kt:187–195 给这些渲染视图设黑底；823–825 又在深度缩放视图就绪后设黑底。
- 因此共享元素的快照可能包含全屏黑色留边，而不仅是照片像素。
- 网格 MediaAdapter.kt:1157 按配置用 CENTER_CROP / FIT_CENTER；共享转场只用 ChangeBounds，没有适配网格裁切与 GestureImageView/深度缩放坐标的过渡。
- VideoFragment.kt:168–177 会在全屏 preview 与实际尺寸 TextureView 之间选择共享源，二者边界及内容映射不同。

修改方案：

1. 新增 `MediaTransitionSurface` 与 `MediaTransitionGeometry`：提供 path、内容版本、媒体方向、drawable/受控快照、屏幕坐标下内容矩形、裁切矩形、图片矩阵和圆角。
2. 新增 `MediaSharedElementController`，使用不带背景的专用 ImageView 代理承载共享内容。黑色画布仍留在父容器，不参与共享快照。
3. `PhotoFragment.sharedElementViewFor` 改为提供稳定代理，不直接暴露 match_parent 的手势视图或 subsamplingView。
4. 代理捕获图片真实显示区域：用渲染器实际矩阵/可见区域计算，叠加父级偏移和 insets 后转换到窗口坐标；不能用屏幕宽高猜测。
5. 为代理实现受控几何转场，统一插值 bounds、图片 matrix、clip 和圆角。两端使用同一图像坐标系；仅 ChangeBounds 或给任意手势控件加 ChangeImageTransform 都不足以解决此问题。
6. 网格图可能已经被加载器裁成小图，不能假设设 FIT_CENTER 就能恢复完整照片。转场优先使用已授权、按需解码的屏幕级完整预览；首尾与已有缩略图做受控像素交接。预览未就绪时保留原图并降级，不能阻塞主线程解码或强行拉伸裁切图。
7. 转场中冻结代理 drawable、matrix 和 clip；图片加载器只能发布到实际渲染层，转场结束后再接管。取消/后台/失败都必须恢复实际视图并释放代理。

验收：缩小的只有图片内容；没有随行黑色大矩形；视频退出不先放大到局部再缩小；横图、竖图、EXIF 旋转图均连续。

### C. 视频缺少放大、封面/首帧切换、返回尺寸突变

代码证据：

- VideoFragment.kt:318 直接 Glide.into(videoPreview)，没有加载成功回调通知 Activity 可以开始转场。
- VideoFragment.kt:596–602 只有 onRenderedFirstFrame 调用 listener.imageRendered。
- ViewPagerActivity.kt:4201–4219 依赖 imageRendered 放行，否则 1200ms 超时放行。
- VideoPreviewFrameHandoff.kt:15–16 在解码首帧立即将封面 GONE，没有判断共享动画是否正在进行。
- VideoFragment.sharedElementViewFor 不检查封面 drawable、对应请求版本或 TextureView 是否已有有效帧，也不拒绝缩放中的视频画面。
- pager_video_item.xml:43–48 的 preview 是全屏 ImageView；VideoFragment.kt:1024–1051 的 TextureView 尺寸来自 getRealMetrics；初始 mVideoSize 为 (1,1)，随后异步读取分辨率和 onVideoSizeChanged 又更新尺寸。
- MediaActivity.kt:3310–3317 源 thumbnail 仅检查 drawable 非空，没有确认其为当前文件已经加载成功的内容，可能把占位图当可共享媒体。
- MediaActivity.kt:3449 附近，照片档案来源 openInViewPager 直接 startActivity，没有复用普通文件夹的共享启动方法。这是确定存在的动画覆盖缺口，但不能证明录屏缺失项恰好走此分支。

修改方案：

1. VideoFragment 预览 Glide 请求增加身份校验的成功/失败回调；成功并完成布局/预绘制后上报 `PreviewReady(path, requestGeneration, geometry)`。视频放大不依赖播放开始或解码首帧。
2. 分离 `previewReady`、`decodedFrameReady`、`transitionRunning`、`closing` 四个状态；扩展 VideoPreviewFrameHandoff，以 `decodedFrameReady && !transitionRunning && !closing` 作为封面让出的条件。
3. 共享期间始终使用同一个无黑底封面代理，不在封面与 TextureView 之间换目标。播放后的返回优先截取当前有效帧作为受控代理；失败时选择确定性降级，禁止把未出帧 TextureView 强行作为共享元素。
4. 返回先捕获当前画面与几何，冻结布局/手势状态，再创建代理；不得为方便返回而先把实际视频 zoom 重置为 1。缩放状态无法安全映射时，当前画面平稳退出。
5. 尺寸计算改为 videoSurfaceFrame 实际可用区域，主线程消费有效视频尺寸；宽高未知时用预览长宽比，禁止 (1,1) 的临时尺寸变成可见画面。onVideoSizeChanged 在转场期间暂存，结束后应用。
6. 普通目录、收藏与照片档案来源统一经过同一个内部共享启动入口，但保留各自已有授权检查、目录交接与会话逻辑。外部系统播放器不强制应用此协议。
7. 日志记录 sourceType、实际 fragment 类型、封面就绪、解码首帧、映射成功与降级原因，确认用户指出的缺失视频走了哪条路径。日志不记录明文私密路径。

验收：两个视频条目及各内部来源均检查；暂停状态、未播放、自动播放、封面慢、解码慢都能有明确的放大或明确的失败降级，不因未播放而等待超时后硬切。

### D. 返回网格黑一下、横滑后返回没有缩回当前位置

代码证据：

- ViewPagerActivity.kt:583–586 只在 getCurrentPath() 等于 photoSharedElementPath 时映射返回元素。
- MediaActivity.kt:3321、3338 固定 pendingViewerReturnPath 为初次点击的 path；没有接收当前页返回路径的 onActivityReenter。
- 因此横滑到另一张后不存在正确共享返回协议，这能解释返回方式不一致。
- 源返回映射检查 holder 和 drawable，但没有等目标文件的正确缩略图完成加载/预绘制，也没有目标离屏处理。
- MediaAdapter.kt:1149–1181 在 requestKey 改变时清空图像再加载；onViewRecycled:271–281 也会清空。它们是必要的复用保护，不应为了消除黑闪直接删除。
- 当前“纯黑格”也可能是平台共享快照交接或视图隐藏恢复造成，静态代码尚不能证明一定是 Adapter 重载。

修改方案：

1. 增加一次性 `MediaReturnTarget`：sessionId、当前已授权 path、内容版本、是否可共享。ViewPagerActivity 关闭前 setResult；MediaActivity.onActivityReenter 消费。普通查看器启动改为可接收结果的方式，并保留现有请求码语义。
2. 返回始终以当前已确认页为目标；entryPath 只负责进入，returnPath 单独维护。撤销授权时立即清理，不能映射到旧路径。
3. 源网格 postpone 返回，按 path 找当前 adapter position。离屏时无动画滚到目标附近，等待正确 holder、加载成功 token、drawable、布局及一次预绘制，再放行。
4. 使用本次最新 adapter 获取数据，避免在 callback 内永久捕获旧 adapter 实例；路径验证、requestGeneration、版本与布局身份同时匹配。
5. 为参与转场的 holder 建立有界 lease，必要时临时 setIsRecyclable(false)，结束/取消后成对恢复；不长时间冻结整个目录扫描。
6. 只将影响当前转场目标绑定的网格发布延后；复用已有滚动/发布机制，不丢弃数据更新。转场结束后合并最新版本，沿用滚动锚点。
7. 记录“placeholder、真实 drawable、共享快照、hidden view”的交接时间，查明黑格来源。无论来源，目标真实缩略图至少完成预绘制后才移除代理，最终恢复 visibility、alpha 和 transitionName。
8. 删除、过滤、授权失效、超时和目标找不到时走统一退出降级，清除共享映射，禁止缩回错误文件。

验收：直接返回、横滑一张/多张后返回、目标离屏、扫描更新、删除当前项后返回都不黑闪、不串图、不跳回最初文件。

### E. 相册进入白屏、封面飘浮、目标首屏补图

代码证据：

- MainActivity.albumTransitionOptions:2246–2247 传递目录封面的媒体路径；MediaActivity.resolveAlbumCoverView:931–945 在目标可见网格中查找同一路径。
- 目录封面不保证属于目标排序首屏，可能被过滤、隐藏，或根本不是可共享的普通媒体。
- setupAdapter:1864–1875 封面加载完成或 resolve 返回非空即 release；并不等待其余可见格子具备稳定画面。
- resolve 仅要求 requestKey 非空和 drawable 非空，不代表是当前请求真实加载成功的 drawable。
- configureAlbumPageTransition:898–922 只配置进入映射，没有明确的当前封面返回状态协议；MainActivity 回到目录时重新找封面，封面更新后的像素一致性未处理。

修改方案：

1. 相册转场独立于照片大图，保留单独入口，但复用同一个时序与清理控制器。
2. 增加 `AlbumTransitionTarget`：目录身份、封面身份/版本、目标是否可见；在目标首屏布局后判断能否真实匹配，不能只依赖 coverPath 字符串。
3. 封面不在首屏时不强行滚动目标相册到封面，也不保留孤立封面等 1200ms；改用完整页面的短转场，保留用户排序和滚动语义。
4. 加载状态拆为真实媒体就绪和主题占位。优先加载首屏、共享封面优先级最高。首屏其余项允许明确的稳定占位，但不能先清空整页再突然补图；就绪阈值只覆盖可见首屏，不等全相册扫描。
5. 首屏真实图快速到齐时一次提交稳定布局；慢加载时保留有设计的占位并结束受控降级，避免用全局延时掩盖问题。
6. 返回以目录身份定位首页卡片，若封面已更新，使用一致的代理交接；没有安全映射则页面退出，禁止旧封面飞到新封面。
7. 标题栏、返回按钮与底栏从同一进度建立/恢复；不将两个完整网格长时间交叉淡化。

验收：2019、Trip、WeiXin、Weixin 均无全白过场；单图相册、首屏无封面、大相册慢载均行为稳定。

### F. 深度缩放渲染器中途替换、标题和控件不同步

代码证据：

- PhotoFragment.kt:105、780–785 在 100ms 后安排 addZoomableView，而进入转场为 280ms。
- addZoomableView:790、815 提前设置 mIsSubsamplingVisible 并显示视图，异步 onReady 再设黑底；共享进入和返回可能选到不同视图。
- reportImageRendered:133–141 通过 post + drawable 非空报告，并未保证目标内容已在预绘制阶段稳定。
- ViewPagerActivity.onPageSelected:4492 立即 updateActionbarTitle；onPageScrollStateChanged 已有 IDLE 和菜单延后处理，标题可纳入同一机制。
- ViewerChromeMotion 控制显隐但不感知媒体共享转场生命周期。
- ViewPagerFragment.handleEvent:187–189 下滑关闭后额外 overridePendingTransition(slide_down)，又是一条独立退出路径；ACTION_CANCEL 也进入同一关闭判断。

修改方案：

1. 对 PhotoFragment 增加 transitionRunning 状态；共享过程中可后台准备深度缩放资源，但只在转场结束且该 path 仍有效时切换实际渲染器。
2. 就绪事件改在身份、真实内容、布局、矩阵及预绘制都有效时发出；回调携带 generation，阻止邻页和过期异步任务放行当前动画。
3. 视觉标题更新延后到 ViewPager IDLE；访问校验、按钮权限等安全状态仍立即更新，不能为视觉同步拖延授权处理。
4. ChromeMotion 接入转场开始/结束/取消；进入时控件按统一进度出现，退出时统一隐藏。保持现有中断代数保护，避免旧动画结束回调覆盖新状态。
5. 下滑、系统返回、工具栏返回全部请求同一个 close(reason) 入口。去掉手势分支单独追加 slide_down；ACTION_CANCEL 仅取消手势，不触发关闭。

验收：进入过程中不换渲染器；横滑标题不提前跳；视频进度栏、播放按钮和上下工具栏不存在二次闪现；取消手势不关闭页面。

## 3. 统一生命周期与降级协议

拟新增 `GalleryMediaTransitionSession`，负责一次转场，不保存长寿命 Activity/View 引用。

状态：IDLE → WAITING_SOURCE/TARGET → ENTERING → SHOWN → PREPARING_RETURN → RETURNING → FINISHED；任意阶段可进入 CANCELLED/FALLBACK，结束清理只能执行一次。

关键方法建议：

```kotlin
beginEnter(request: MediaTransitionRequest)
onSurfaceReady(surface: MediaTransitionSurface, generation: Long)
onDecodedFrameReady(identity: MediaIdentity)
prepareReturn(target: MediaReturnTarget)
onTargetPreDraw(target: MediaTransitionSurface)
cancel(reason: CancelReason)
finishOnce()
```

- PhotoViewerTransition/AlbumPageTransition 保留兼容 extra 名称，时长与状态交给统一协调器；正式内部命名逐步从 photo 扩成 media，避免一半调用漏掉视频。
- 不依赖固定延迟启动；1200ms 仅作为等待上限，超时必须产生明确降级而非照常播放无目标的共享动画。
- 首轮保持媒体 280ms、相册 300ms，先消除叠加和错位；之后再依据同一设备录屏决定是否统一总时长。控件 170ms 可嵌入同一时间轴，无需所有元素强行同速。
- 遵从系统关闭动画设置；直接应用最终状态，不保留隐藏代理/等待回调。
- 所有出口清理：超时 Runnable、预绘制监听、共享回调、临时 drawable/bitmap、holder lease、transitionName、visibility/alpha、待发布尺寸与网格更新。
- 后台、旋转、快速连点、Activity 销毁、授权撤销各有 generation/session 检查。
- 图片代理按实际显示尺寸采样，避免全分辨率 Bitmap 和无界缓存；不在主线程执行文件读取、视频帧解码或目录查询。

## 4. 实施拆分与依赖顺序

| 批次 | 具体文件/变更 | 完成条件 |
| --- | --- | --- |
| 1 | 新增 GalleryMediaTransitionSession、身份/就绪模型、诊断事件；修改 OxygenClassicActivityMotion、BaseViewerActivity、styles.xml | 模式控制权唯一，finish 不追加第二套动画，能定位所有降级 |
| 2 | 新增 MediaTransitionGeometry/MediaTransitionSurface/MediaSharedElementController；修改 PhotoFragment、pager_photo_item.xml、ViewPagerActivity | 普通照片进入/返回无黑块，裁切连续，渲染器不会中途替换 |
| 3 | 修改 VideoFragment、VideoPreviewFrameHandoff、pager_video_item.xml | 封面就绪即可放大；首帧不抢占代理；视频返回无突然局部放大 |
| 4 | 修改 MediaActivity 共享启动/返回协议、MediaAdapter 加载身份与临时 lease | 横滑后缩回当前文件；源目标不黑闪，离屏/删除可降级 |
| 5 | 修改 MainActivity.albumTransitionOptions、MediaActivity 相册就绪/映射、DirectoryAdapter 加载身份 | 目录封面首屏匹配可靠；相册白闪、飘浮封面、补图突变消除 |
| 6 | 修改 ViewPagerActivity 标题提交、ViewerMotion、ViewPagerFragment 手势关闭 | 标题和控件时序一致；所有返回入口一致 |
| 7 | 构建、单元回归、真机录屏和性能核对 | 覆盖下述矩阵，不只以测试通过宣称视觉问题解决 |

每批以可独立回退的提交推进。先做媒体正常进入/退出这一条纵向闭环，再覆盖视频及相册，避免同时替换所有页面后难以定位回归。

## 5. 测试与验收清单

### 自动化测试

新增有行为意义的测试，不以“源码包含某字符串”代替验证：

- GalleryMediaTransitionSessionTest：加载成功/失败、超时、晚到回调、取消、快速重新进入、重复 finish、动画关闭；清理各执行一次。
- MediaTransitionGeometryTest：横竖图、centerCrop→fitCenter、EXIF 方向、insets、圆角与非零原点；检查起终矩阵和裁切范围，不拉伸图像。
- MediaReturnTargetTest：滑到新文件、当前文件删除/过滤、目标离屏、Adapter 更新、授权撤销；绝不能映射旧文件。
- 扩展 VideoPreviewFrameHandoffTest（按现有测试位置落地）：首帧早于动画结束仍保留代理/封面；结束后才交接；过期 player 回调无效；返回期间不切换表面。
- 扩展 VideoViewerFrameLayerTest：不只检查层级，还检查 preview drawable 就绪与共享稳定性。
- 扩展 OxygenClassicActivityMotionTest：PAGE 与 SHARED 两模式互斥、共享结束不追加关闭动画。
- 集成测试：普通目录/收藏/照片档案三种来源都经过共享启动协议；标题在 IDLE 提交，但权限立即刷新。
- 保留并回归现有 MyPagerAdapterLifecycleTest、ViewerPageTransformerTest、ViewerSwipePublicationGateTest、MediaAdapterStabilityTest、DirectoryAdapterRefreshTest、GalleryRootTransitionTest、ViewerMotionTest 及渐进加载相关测试。

计划命令：`./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug`。执行时先按本地 Android/JDK/依赖条件确认任务可用；本次写方案未执行，不声称已经通过。

### 真机视觉验收（必须）

按原录屏顺序重录，并额外覆盖：

1. 冷/热缓存打开相册与媒体；2019、Trip、WeiXin、Weixin。
2. 用户指出的缺失动画视频：先用匿名身份日志确认对应条目，再单独验证未播放/暂停/播放中进入退出。
3. 横图、竖图、EXIF 旋转图、长图、GIF、深度缩放图；正常状态与缩放/旋转状态退出。
4. 横滑一张、多张后返回；目标离屏、排序刷新、删当前项、隐藏文件、授权撤销。
5. 系统返回、边缘返回、工具栏返回、下滑关闭、取消下滑、连点、动画中切后台、横竖屏切换。
6. 系统动画关闭；录屏设备同版本优先，再补 API 29 与 34/35 代表设备，关注系统返回行为。

通过标准：

- 逐帧无非设计的纯白/灰白整屏、黑色缩略图空档、随图片缩小的黑色大矩形。
- 同一时刻只有一张受控共享媒体，实际源/目标交接没有可见空档或双影。
- 无先放大后缩小、宽高比跳变、错误文件归位；控件/标题无二次出现。
- 不等待全目录扫描；无主线程解码；代理内存有上限；对比基线的打开延迟和慢帧情况，不为消除闪屏引入明显卡顿。
- 每个降级有可说明原因；正常已加载视频不以“超时后硬切”冒充修复。

## 6. 明确不采取的捷径

- 不只调整 280/300ms 时长或加长超时。
- 不靠延迟 100/300ms 猜媒体已就绪。
- 不清空所有背景来隐藏黑块，媒体画布仍需稳定黑底。
- 不删 Adapter 的清图/身份校验而引入复用串图。
- 不为视觉同步延迟权限撤销，不把私密文件截图保留在全局缓存。
- 不将静态代码推断写成已真机证实的结论。
