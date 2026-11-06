# Phase 2 · 01 — 设置差异与几何

## 修改前证据与决策

| 当前代码证据 | 目标/决定 | 确定性、风险与验证 |
|---|---|---|
| CategoryRow init：16sp/14sp、72dp min、16dp padding、56dp slot、36dp ImageView 内缩6dp | 标题保留16，副标题13；最小72，允许换行增高 | 14sp 是源码确认；13sp 为12/13候选中的可读性折中，非真机主观验收。矩阵测量文字 bounds |
| SwitchRow 8+48+8 至少64dp | 上下4+48+4=56dp min | 算术可证；保留48dp触摸区/轨道/24dp thumb，不改变手势 |
| Section minHeight=0，12sp，top16/bottom8；主题页top20/bottom4 | 两页共用48dp min/12sp medium/top16/bottom8 | 样式差异确认；大字体使用wrap_content增高 |
| SecondaryText 14sp，marginTop3 | 13sp，3dp间隔保留 | 不改变主标题16sp与72dp双行最小高度 |
| setupSettingsHub 每次 onResume removeAllViews | 首次建行，随后 bindColors，更新已有 Divider | 焦点/Drawable实例销毁由代码可证；回归检查 View、焦点和Ripple身份 |
| Container 在600/840分别跳到87%/75% | `min(w,max(522dp,0.75*w))` | 599→600旧宽599→522、839→840旧宽729.93→630；新公式连续且保留600/840参考宽；布局测量扫320..900 |

## 最终应用规格

| 组件 | 实现值 | 依据 |
|---|---|---|
| Toolbar | 56dp /20sp，保持 | 现存经典合同 |
| 分类 | min72dp、左右16dp、上下8dp；leading56dp，36dp容器内6dp留白=24dp图标；文字起点72dp | 现存代码/指南拟合 |
| 分类标题/副标题 | 16sp /13sp、间距3dp、wrap_content | 应用候选，13sp优先可读性；12sp未经过真机A/B，不宣称优于12sp |
| 普通/开关行 | min56dp，左右16dp；普通上下8dp，开关上下4dp+48dp控件触摸高度 | 保持/修正最小高度叠加 |
| 双行 | min72dp，左右16dp，上下8dp，标题16sp、副标题13sp | 内容自然增高 |
| 分组 | min48dp、12sp medium、左右16dp、top16/bottom8 | 指南48–56dp拟合范围内应用候选 |
| Divider | 1dp、page与primaryText混合10%；首页七条，无尾线；内页保留Section组织、不增加逐行线 | 语义色，避免过度装饰 |
| Switch | 48dp宽度盒、28dp optical高度；36×16dp细轨，24dp圆thumb；min48dp触摸高，switchPadding24dp | 已有控件实现，未改形态 |

## 八个分类覆盖

| 分类 | 分组 | 本次处理/复核 |
|---|---|---|
| 外观 | 主题、缩略图 | 共享普通/开关/双行/Section；主题和图标页回调保留 |
| 相册 | 浏览 | 横向滚动保留；原隐藏的pull-to-refresh仍隐藏 |
| 照片 | 显示、屏幕、手势 | HDR、刘海和缩放子项依原系统/配置条件；长Switch文字增高 |
| 控件 | 信息、操作栏 | 扩展信息/底栏子项仍依配置；管理Dialog未改 |
| 视频 | 播放、播放器 | 原隐藏on-video-tap仍隐藏；业务不变 |
| 文件 | 来源、操作、回收站 | Q/R权限条件与回收站子项原样保留 |
| 安全 | 应用、媒体 | 鉴权入口/判断未变，不触碰密码布局 |
| 高级 | 语言、性能、备份 | Tiramisu语言条件、诊断、导入导出/恢复回调不变 |

自动化只证明 View 测量/属性/可见性，不证明真实字体栅格、GPU、WindowManager或无障碍焦点全部正确。无 Gallery 本阶段前后真机对照，不能声称像素级一致。
