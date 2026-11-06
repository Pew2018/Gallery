# Phase 0 · 03 — Viewer、导航层级与转场风险

> 源码证据锚点 024b4731ac6508cab45f7d81eea73294a78d0d04；对照现存 06/08 代码地图和当前 Kotlin 文件。

## 导航路径和拥有者

Gallery不是一个单 NavHost：MainActivity负责相册/收藏文件夹根页；物理目录、.nomedia、全部文件、收藏媒体、回收站和照片配置媒体进入不同 mode/path 的 MediaActivity；照片配置管理/编辑为独立 Activity；设置八类页在一个 SettingsActivity；连续 Viewer 为 ViewPagerActivity；外部单媒体和视频播放器另有入口。GalleryBottomBar三个根入口跨 Activity启动根页面。

| 场景 | 入口/数据边界 | 动画所有者与红线 |
|---|---|---|
| Main → 普通或 .nomedia 目录 | MainActivity.itemClicked(path)，先检查保护祖先/鉴权，再开启 MediaActivity | 系统 Activity window；不可动画提前展示目录内容 |
| Main → 固定收藏媒体 | MediaActivity FAVORITES + 捕获 catalogue/access session | 受保护媒体范围与 scroll restorer不变 |
| Main → 回收站 | MediaActivity RECYCLE_BIN | 返回动画不能触发恢复/清空 |
| 照片配置网格 → Viewer | openInViewPager + profile ID/revision/access token | 到 startActivity 前验证配置和授权 |
| 普通文件夹网格 → Viewer | openOrdinaryViewer 准备当前顺序、neighbor handoff | 不拷贝目录或加载移入主线程 |
| Viewer左右切换 | ProgressivePhotoViewPager.dispatchTouchEvent + ViewPager callbacks/MyPagerAdapter | 自然跟手滑动；非幻灯片、非系统返回，不替换算法 |
| Viewer顶栏/底栏显隐 | ViewerChromeMotion可中断alpha/property animator | 动画仅限chrome，不能动媒体内容发布 |
| Viewer向下滑退出 | ViewPagerFragment handleEvent→finish/slide_down | 独立手势，与系统水平 Back 分开 |
| 文件夹返回主页 | Commons SimpleActivity back callback + MediaActivity.onBackPressedCompat | 搜索优先；完成返回时才锁/撤session |
| Main目录分组返回 | MainActivity.onBackPressedCompat | 取消预测返回不可 pop目录路径 |
| 设置分类返回 | SettingsActivity settingsBackCallback | 单ScrollView内局部更换，同一容器 |
| 外部 Picker / Viewer | Result/Intent回外部调用方 | 不强制跳 Gallery 首页 |
| 底栏根切换 | GalleryBottomBar.open, 0ms window + first draw gate | 保留抗首帧闪烁约束 |

## Viewer边界不是运动动画

ProgressivePhotoViewPager在 provisional/unknown boundary 上识别向外滑并请求更多数据，需考虑RTL和子View水平滚动。ViewPagerActivity页面选择/滚动回调、MyPagerAdapter与Neighbor/GridOrder/Handoff stores共同维护当前媒体、邻页和发布时机。普通手指左右滑动是 ViewPager 拖动；定时 slideshow 有自己的 animatePagerTransition；向下退出是另一种关闭手势；系统 Back dispatcher又是另一层。

不要为了视觉转场改目录顺序、邻页窗口、授权session、扫描发布、在 settle期间 full rebind；取消Back不能结束页面/释放权限。受保护媒体优先于共享元素缩略图动画。

## MaterialContainerTransform候选范围

目前静态源码不能证明网格缩略图与Viewer图像view在同一时刻有可用、稳定、授权后的匹配 bounds/drawable。推荐 Phase 1 继续系统默认Activity转场。

若独立试验，只限非保护、单张、稳定普通来源；确认双方容器矩形与同一路径图像，传入真实表面和主题遮罩色；支持动画0、取消和lifecycle；失败回退系统转场。不要用旧缓存/错位缩略图做过渡，不删FavoritesReturnCover，不扩展到收藏、回收站或配置session。

高风险级别：P0密码/生物识别/受保护内容/Picker取消；P1网格↔Viewer、scroll恢复、底栏root首帧；P2 Settings局部切页、普通Dialog、chrome。手势竞争、源路径录屏、真实共享元素bounds与window帧时长均待设备验证。

