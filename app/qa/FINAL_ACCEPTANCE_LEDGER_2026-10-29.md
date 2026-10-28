# Fossify Gallery — 阶段回归与最终验收台账（审计中的 Release Candidate）

日期：2026-10-29；仓库：Pew2018/Gallery；唯一允许修改分支：`feature/oxygenos-gallery-nav-vault-performance`。

## 重要说明和证据等级

本表是依据**本轮用户提供的完整需求**、仓库当前代码、版本目录、单元测试和 OxygenOS v1.0 设计指南归纳的**逐项台账**；先前四份 Prompt 的逐字原件与全部用户设备截图/录屏目前未能取回，所以**不能据此宣称已完整穷尽所有历史细项**。设计指南基于 29 张 AEmulator OnePlus 5 OxygenOS 4.5.15 / Android 7.1.1 图，56dp、20sp 等属于截图拟合建议；不等于已逐像素测得 Gallery App。此审计没有 Pixel 8 Pro 可直接操作的实机、没有 Gallery 运行截图、没有冷启动 benchmark。表内“静态检查符合要求；待实机”**不等于已通过实机测试**，未对所有方法完整调用链执行动态调试。

本轮已提交的代码修复：`7df2aed`（禁止扫描自动取消收藏/清理未确认缺失媒体）、`48f510b`（空扫描与目录清理）、`31b5f6c`（外置存储不可访问时保留目录记录），以及本台账所在提交的**目录路径大小写和边界判断修复**。最终 Actions、运行 ID、Artifact 和 HEAD 必须以构建完成后实时 GitHub 结果更新，不能预写为通过。

## 逐项需求台账（67 项）

阶段 | ID | 原始需求 | 预期结果 | 当前实现 | 代码位置 | 检查方式 | 发现的问题 | 修复方式 | 修复提交 | 验收状态 | 剩余风险
--- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | ---
1 | FUNC-001 | 收藏首次进入 | 冷启动直达收藏可见正确目录 | 独立目录查询+异步协调 | MainActivity.loadFavoriteFoldersWithOverrides | 静态/实机 | 首次完整权限场景未实测 | 保留独立加载器 | — | 代码有对应实现；待实机验证 | 首次加载、权限变化
1 | FUNC-002 | 收藏显示隐藏开关 | 设置后重启仍保留 | SharedPreferences.apply | Config.favoriteShowHidden | 静态/实机 | 持久化代码存在 | 未改 | — | 静态检查符合要求；待实机 | Activity 重建
1 | FUNC-003 | 收藏显示排除开关 | 设置后重启仍保留 | SharedPreferences.apply | Config.favoriteShowExcluded | 静态/实机 | 持久化代码存在 | 未改 | — | 静态检查符合要求；待实机 | 授权取消
1 | FUNC-004 | 隐藏目录与点号目录 | 仅在允许时显示 | isDotHidden + hasNoMediaAncestor | FavoriteFolderRules | JVM/实机 | MediaStore 不索引 .nomedia | 独立收藏路径读取 | — | 代码有对应实现；待实机验证 | 存储供应商差异
1 | FUNC-005 | 排除目录及后代 | 祖先匹配且不混淆前缀 | 按路径分隔符判断 | FavoriteFolderRules.isExcluded | JVM/实机 | 目录分类筛选旧代码未规范路径 | 统一为 isExcluded | 本轮最终提交 | 修复已提交；待最新 CI 和实机 | SAF 路径
1 | FUNC-006 | 不同大小写目录 | Photo 与 photo 不能合并 | 以区分大小写的 normalizedPath 去重 | MainActivity.loadCategorizedFolders | JVM/实机 | 曾使用 lowercase 合并 | 移除 lowercase 去重 | 本轮最终提交 | 修复已提交；待最新 CI 和实机 | 实际文件系统差异
1 | FUNC-007 | 收藏请求防并发 | 过期请求不能覆盖新状态 | 代次协调器 | FavoriteFolderLoadCoordinator | JVM/实机 | 已见代次逻辑 | 未改 | — | 代码有对应实现；待实机验证 | 界面生命周期
1 | FUNC-008 | 收藏密码/生物识别 | 成功后授权；取消不放行 | 隐藏与排除独立临时授权 | MainActivity.promptProtectedFavoriteFilters | 静态/实机 | 缺少 Android 16 实测 | 保留授权闸门 | — | 尚待完整审计及实机验证 | 外部认证 onStop
1 | FUNC-009 | 未授权缩略图 | 受保护目录无预览泄露 | 锁定 Holder 清理 Glide 目标 | DirectoryAdapter.setupView | 静态/实机 | 已见清理逻辑，需多路径核查 | 未改 | — | 代码有对应实现；待实机验证 | 缓存/最近任务快照
1 | FUNC-010 | 收藏不因扫描缺失清空 | 扫描不完整不得取消收藏 | 禁止扫描自动取消收藏 | MediaActivity.startAsyncTask | JVM/CI/实机 | 确认原代码存在误删收藏路径 | 删除自动取消收藏分支 | 7df2aed | 修复已提交；待最新 CI 和实机 | 跨进程文件变化
1 | FUNC-011 | 目录缓存不因无权限清空 | 断开存储时保留记录 | 验证父目录可访问且文件确实不存在 | MainActivity/Context.removeInvalidDBDirectories | JVM/CI/实机 | 确认原代码仅凭空扫描删目录 | 保护数据库路径 | 48f510b,31b5f6c | 修复已提交；待最新 CI 和实机 | 动态权限与 SAF
1 | SECURITY-001 | 独立隐私空间入口 | 独立分类与打开路径 | 导航只有 ALL/FAVORITE_FOLDERS | GalleryBottomBar/NavigationOrder | 静态 | 无独立隐私空间 | 尚无安全完整的实现 | — | 尚未实现 | P0 功能缺失
1 | SECURITY-002 | 隐私空间独立管理 | 独立目录集合和数据库/配置 | 未见 Vault 独立模型 | GalleryDatabase/Config | 静态 | 无独立集合 | 尚未实现 | — | 尚未实现 | 收藏不可替代
1 | SECURITY-003 | 隐私空间解锁与内容隔离 | 无授权不进入普通搜索/媒体缓存 | 现存锁定文件夹不等于独立 Vault | MediaFetcher/getCachedMedia/DirectoryAdapter | 静态/实机 | 未提供专属 Vault 授权链 | 尚未实现 | — | 尚未实现 | P0 隐私需求
1 | SECURITY-004 | 不加密/不移动原文件 | 只做 App 内访问隔离 | 当前没有独立 Vault 操作 | Vault 业务缺席 | 静态 | 业务尚未实现 | 不虚构功能 | — | 尚未实现 | 其他 App 仍可访问
2 | INTERACTION-001 | 搜索工具栏返回 | 收起 IME 后正确退出 | SearchExitGate + OxygenSearchNavigation | SearchActivity.exitSearch | 静态/实机 | 无设备录屏证据 | 未改 | — | 代码有对应实现；待实机验证 | Android 16 IME 时序
2 | INTERACTION-002 | 搜索系统返回 | IME 优先由系统返回处理 | 未劫持原生系统 IME 返回 | SearchActivity/SimpleActivity | 静态/实机 | 预测性返回未测 | 未改 | — | 尚待完整审计及实机验证 | 手势差异
2 | INTERACTION-003 | 搜索延迟焦点 | 退出后不会重新弹出键盘 | post 焦点 Runnable 可取消 | SearchActivity.initialFocusRequest | 静态/实机 | 代码有取消闸门 | 未改 | — | 代码有对应实现；待实机验证 | 极快连续退出
2 | INTERACTION-004 | 搜索结果并发 | 旧查询不能覆盖新查询 | 查询文本状态验证 | SearchActivity.textChanged | 静态/实机 | 未测大图库搜索 | 未改 | — | 尚待完整审计及实机验证 | 任务竞争
2 | INTERACTION-005 | 多选数量显示 | 显示选中数量而非 3/334 | SelectionCountFormatter | OxygenSelectionMode | JVM/实机 | Commons 标题变种可能未覆盖 | 未改 | — | 代码有对应实现；待实机验证 | 选中态
2 | INTERACTION-006 | 多选图标颜色 | 返回/删除/分享统一可读 | ActionMode 主题前景计算与 tint | OxygenSelectionMode.style | 静态/实机 | 仅有代码；缺少截图 | 未改 | — | 尚待完整审计及实机验证 | 多主题/深色
2 | INTERACTION-007 | 多选状态恢复 | 弹窗返回后正确保存或清理选择 | 仍委托 Commons | MediaAdapter/DirectoryAdapter | 静态/实机 | 需要交互压力测试 | 未改 | — | 尚待完整审计及实机验证 | Adapter 更新
2 | INTERACTION-008 | 删除两个勾选项默认关 | 跳过回收站不得默认开启 | 两个 Checkbox 初始 false | DeleteWithRememberDialog | 静态/实机 | 需实际点击与取消测试 | 未改 | — | 代码有对应实现；待实机验证 | Commons 弹窗重建
2 | INTERACTION-009 | 会话删除偏好 | 重启后不会持久保留危险勾选 | DeleteSessionState 内存态 | Config/DeleteSessionState | JVM/实机 | 测试覆盖 reset，Activity 顺序未测 | 未改 | — | 代码有对应实现；待实机验证 | 进程重建
2 | INTERACTION-010 | 移动/复制/重命名 | 确认一次仅执行一次 | 现有业务委托 Commons | MediaAdapter/DirectoryAdapter/dialogs | 静态/实机 | 未对完整事务回归 | 未改 | — | 尚待完整审计及实机验证 | 文件操作安全
2 | INTERACTION-011 | Popup/排序/筛选 Dialog | 取消后不改数据 | 保留原 Builder 业务回调 | ChangeSortingDialog/FilterMediaDialog | 静态/实机 | 全套操作未实测 | 未改 | — | 尚待完整审计及实机验证 | 按钮及复选框
3 | UI-001 | 所有图片 Toolbar | 56dp 且与设计色协调 | OxygenToolbarStyle | MediaActivity | 静态/截图 | 缺少实际截图 | 未改 | — | 尚待完整审计及实机验证 | Insets/字号
3 | UI-002 | 收藏 Toolbar | 56dp/20sp/24dp 图标 | OxygenToolbarStyle | MainActivity | 静态/截图 | 多状态未核验 | 未改 | — | 尚待完整审计及实机验证 | 返回与滚动
3 | UI-003 | 设置 Toolbar | #212121 与 #1E1E1E 层级 | 设置中使用 OxygenThemeEngine | SettingsActivity | 静态/截图 | 代码使用同源 token | 未改 | — | 代码有对应实现；待实机验证 | Android 16 Edge-to-edge
3 | UI-004 | 主题页 Toolbar | 返回键及文字对比可读 | 使用统一引擎并渲染 | OxygenThemeActivity.render | 静态/截图 | 缺少截屏与触摸区域实测 | 未改 | — | 代码有对应实现；待实机验证 | 大字号
3 | UI-005 | 搜索 Toolbar | 展开/关闭无高度跳变 | 共享 OxygenToolbarStyle | SearchActivity | 静态/截图 | 无操作截图 | 未改 | — | 尚待完整审计及实机验证 | IME 转场
3 | UI-006 | 编辑器与查看器 Toolbar | 保持经典控件/系统栏 | 部分继承主题扩展 | EditActivity/ViewPagerActivity/VideoPlayerActivity | 静态/截图 | 未逐页像素核验 | 未改 | — | 尚待完整审计及实机验证 | 横屏
3 | UI-007 | 状态栏/导航栏 | 系统图标可读且无叠层 | OxygenSystemBars/insets | SimpleActivity/OxygenSystemBars | 静态/实机 | 系统版本差异 | 未改 | — | 尚待完整审计及实机验证 | Android 16
3 | UI-008 | 经典 Switch 开关态 | 20dp Thumb/36dp 细轨道 | SwitchCompat 自定义状态 Drawable | OxygenClassicControls.applySwitches | 静态/截图 | 状态代码存在；视觉未测 | 未改 | — | 代码有对应实现；待实机验证 | Commons 重新 tint
3 | UI-009 | 经典 Switch 禁用态 | 灰度/交互正确 | StateListDrawable disabled 状态 | OxygenClassicControls | 静态/截图 | 未逐个控件验收 | 未改 | — | 尚待完整审计及实机验证 | 深浅色
3 | UI-010 | Checkbox/RadioButton/Slider | MD1 控件形态及对比 | 统一 tint、SeekBar 样式 | OxygenClassicControls/OxygenClassicDialog | 静态/截图 | 基础着色存在，像素未确认 | 未改 | — | 尚待完整审计及实机验证 | Dialog 复用
3 | UI-011 | 设置行与分组 | 56/72dp、16/12sp 边距 | SettingsActivity + XML | activity_settings.xml | 静态/截图 | 未逐项测量完整列表 | 未改 | — | 尚待完整审计及实机验证 | 字体缩放
3 | UI-012 | 经典 Dialog/PopupMenu | 3dp 小圆角与经典按钮 | OxygenClassicDialog/PopupStyle | OxygenClassicDialog/OxygenClassicCustomizationBridge | 静态/截图 | 部分 Commons 弹窗需逐一验证 | 未改 | — | 尚待完整审计及实机验证 | 宽度/禁用态
3 | UI-013 | 底部导航图标/标签 | 24dp/12sp，无 M3 药丸 | 两项自定义平面底栏 | GalleryBottomBar | 静态/实机 | 无滑动/Insets 截图 | 未改 | — | 代码有对应实现；待实机验证 | 导航手势
3 | UI-014 | 底栏顺序持久化 | 两项重排重启有效 | NavigationOrder + Config | BottomNavigationOrderDialog/Config | JVM/实机 | JVM 测顺序，重启未测 | 未改 | — | 代码有对应实现；待实机验证 | 隐私空间以后新增时迁移
3 | UI-015 | 网格图片/视频标记 | 无突兀粉色/图标一致 | 适配器自定义样式 | MediaAdapter/DirectoryAdapter | 静态/截图 | 未逐个 ViewHolder 对照 | 未改 | — | 尚待完整审计及实机验证 | 不同尺寸
3 | UI-016 | 23 套主题预设 | 经典+原有 Material 预设完整 | 11 经典+12 Material | OxygenThemePresets | JVM/实机 | 常量测试覆盖，色块未截图 | 未改 | — | 代码有对应实现；待实机验证 | 用户原始截图
3 | UI-017 | 自动前景对比度 | 自动文本至少 4.5:1 | WCAG sRGB 计算 | OxygenThemeEngine.autoForeground | JVM/实机 | JVM 单色样例已覆盖 | 未改 | — | 代码有对应实现；待实机验证 | 非纯色覆盖层
3 | UI-018 | 自定义主题持久化 | 启停经典主题保留 Fossify 颜色 | 启用时备份、关闭时恢复 | Config.oxygenThemeActivated | 静态/实机 | 升级后恢复需验证 | 未改 | — | 代码有对应实现；待实机验证 | 偏好迁移
4 | MEDIA-001 | 网格未加载占位 | 不能无故出现纯黑块 | 按背景明度使用灰色 | OxygenThumbnailPlaceholder | 静态/截图 | 无截图或性能证据 | 未改 | — | 代码有对应实现；待实机验证 | 透明 PNG
4 | MEDIA-002 | RecyclerView 复用 | 不闪现旧媒体缩略图 | Glide.clear + Picasso.cancel | MediaAdapter/DirectoryAdapter | 静态/实机 | 请求并发压力未测 | 未改 | — | 代码有对应实现；待实机验证 | 快速滚动
4 | MEDIA-003 | 图片失败回退 | 失败显示明确提示 | Glide fallback | DirectoryAdapter/Context.loadImageBase | 静态/实机 | 不同格式失败路径未覆盖 | 未改 | — | 尚待完整审计及实机验证 | 缓存异常
4 | MEDIA-004 | GIF/WebP/SVG/AVIF/JXL | 格式支持不可退化 | 依赖仍在版本目录 | gradle/libs.versions.toml | 静态/实机 | 库存在不等于逐格式能打开 | 未替换解码库 | — | 代码有对应实现；待实机验证 | 真机解码
4 | MEDIA-005 | RAW/HEIC/PNG/EXIF | 方向、缩放、透明正确 | 原实现仍在 | PhotoFragment/ExifUtils | 静态/实机 | 缺少格式样本测试 | 未改 | — | 尚待完整审计及实机验证 | 设备编解码器
4 | MEDIA-006 | 大图缩放翻页 | 不丢位置/黑屏/旧帧 | 原查看器实现 | ViewPagerActivity/PhotoFragment | 静态/实机 | 尚未完成交互回归 | 未改 | — | 尚待完整审计及实机验证 | 内存压力
4 | MEDIA-007 | 视频第一帧预览 | 首帧渲染前维持预览 | ExoPlayer onRenderedFirstFrame | VideoFragment | 静态/实机 | 前置阶段最新代码有处理 | 未改 | — | 代码有对应实现；待实机验证 | 不同视频格式
4 | MEDIA-008 | 视频释放音频资源 | 后台/退出不残留音轨 | Media3 生命周期处理 | VideoFragment/VideoPlayerActivity | 静态/实机 | 需要连播和旋转压力测试 | 未改 | — | 尚待完整审计及实机验证 | 播放焦点
4 | MEDIA-009 | 视频播放/静音/变速 | 播放控件状态一致 | 现有交互控件 | VideoFragment | 静态/实机 | 未在设备验证 | 未改 | — | 尚待完整审计及实机验证 | 系统音频
4 | PERF-001 | 减少重复 MediaStore 查询 | 启动扫描不逐文件夹全量查 | 共享 getAndroid11FolderMedia | GetMediaAsynctask/MediaFetcher | 静态/性能 | 没有量化数据 | 未改 | — | 代码有对应实现；待实机验证 | 大量库/SD卡
4 | PERF-002 | 冷启动/首批媒体时间 | 有实际耗时测量 | 未接入设备测量 | MainActivity/MediaActivity | 实机 benchmark | 无测量条件 | 不伪造数值 | — | 尚待完整审计及实机验证 | 性能未实测
4 | PERF-003 | 滚动掉帧/峰值内存 | 报告实际 P95/峰值 | 未运行测量工具 | RecyclerView/Glide | 实机 benchmark | 没有样本与设备测量 | 不伪造数值 | — | 尚待完整审计及实机验证 | 内存峰值未知
4 | PERF-004 | 权限变化不删除媒体库 | 授权状态不同于文件删除 | 严格实体存在性条件 | MediaCachePrunePolicy/MediaActivity/Context | JVM/CI/实机 | 此前存在不完整扫描即删缓存 | 多路径同步修复 | 7df2aed,48f510b,31b5f6c | 修复已提交；待最新 CI 和实机 | 实机动态权限
4 | PERF-005 | 目录数据库同步 | 目录空扫描不视为删除 | 可读父目录检查 | MainActivity.checkInvalidDirectories | JVM/CI/实机 | 此前无条件移除空扫描目录 | 增加安全判断 | 48f510b | 修复已提交；待最新 CI 和实机 | SAF/外置
4 | SECURITY-005 | 回收站/删除安全 | 用户确认后才执行文件操作 | 保留原有 deleteFiles 流程 | MainActivity/MediaActivity/DeleteWithRememberDialog | 静态/实机 | 尚未执行受控文件操作 | 未改 | — | 尚待完整审计及实机验证 | 不可使用真实用户照片测试
4 | COMPAT-001 | Android 8–16 支持 | minSdk26、targetSdk36 | 已读取版本目录 | gradle/libs.versions.toml | 静态 | 需验证行为不止 API 标志 | 未改 | — | 代码有对应实现；待实机验证 | Android 8 兼容
4 | COMPAT-002 | Android 13/14 媒体权限 | READ_MEDIA 与选择访问 | Manifest 已声明相应权限 | AndroidManifest.xml | 静态/实机 | 权限流程尚未实测 | 未改 | — | 尚待完整审计及实机验证 | 选取部分照片
4 | COMPAT-003 | Pixel 8 Pro Android 16 | 导航、IME、Inset、预测返回 | 目标 SDK36 + 适配 helper | SearchActivity/OxygenSystemBars | 实机 | 没有设备执行证据 | 未改 | — | 尚待完整审计及实机验证 | 最高优先级验收
4 | COMPAT-004 | 横竖屏/进程恢复 | 恢复状态且无受保护缓存泄漏 | 部分生命周期清理 | MainActivity/MediaActivity/VideoFragment | 实机 | 内存压力场景尚未执行 | 未改 | — | 尚待完整审计及实机验证 | 授权恢复
4 | BUILD-001 | 仅 arm64 FOSS Release | 单一 arm64 APK，非 Play/debug | 工作流有任务与 ABI 检查 | arm64-foss-release.yml | Actions | 最后提交尚需 CI 结果 | 未改变构建策略 | — | 尚待完整审计及实机验证 | GitHub runners
4 | BUILD-002 | 包名与版本 | org.fossify.gallery, versionName1.14.1 | Gradle APP_ID/VERSION_NAME/ABI_CODE | gradle.properties/app/build.gradle.kts | 静态/Actions | 需核对最终 APK metadata | 未改 | — | 代码有对应实现；待实机验证 | arm64 versionCode32
4 | BUILD-003 | 签名与覆盖安装 | 与已安装 APK 签名一致 | 仓库密钥或临时 CI Key | arm64-foss-release.yml | Actions/设备 | CI fallback 是不兼容更新的临时密钥 | 明确披露；不卸载绕过 | — | 尚待完整审计及实机验证 | 已有数据保护
4 | BUILD-004 | 自动化覆盖与最新 APK | 最终 SHA 有相应成功 CI 与 Artifact | Push 自动触发 workflow | GitHub Actions | Actions | 尚待最终 run 结束 | 持续检查 | — | 尚待完整审计及实机验证 | 不可使用旧 APK

## 四阶段数量与状态（保守口径）

- 阶段1：15 项；修复已提交 4；明确尚未实现 4；其余均为代码初审/待深审或实测（不能算验收通过）。
- 阶段2：11 项；修复已提交 0；明确尚未实现 0；其余均为代码初审/待深审或实测（不能算验收通过）。
- 阶段3：18 项；修复已提交 0；明确尚未实现 0；其余均为代码初审/待深审或实测（不能算验收通过）。
- 阶段4：23 项；修复已提交 2；明确尚未实现 0；其余均为代码初审/待深审或实测（不能算验收通过）。

这里的“修复已提交”仅表示代码已提交；**最终 CI 成功**与**真实设备行为确认**分别另计，不得合并成“已通过”。独立隐私空间有 4 项尚未实现，因此不满足“全部需求通过”标准，不能宣称最终验收完成。

## 发现的 P0/P1 与处理

- **P0 / 数据完整性**：旧 MediaActivity 会把扫描结果缺失的收藏路径从收藏表中直接取消；旧 MainActivity 和 Context 有依据空媒体结果或临时不可访问路径删除目录与媒体缓存的风险。已通过以上 3 次提交实施保守保护策略并新增纯 JVM 测试；仍需最终 CI 与安卓实机确认。
- **P0 / 功能缺失**：独立隐私空间目前不是可交付功能。应有独立的文件夹清单、身份验证、进入后的媒体浏览，以及普通搜索、媒体缓存、缩略图与分享 Intent 隔离；不应将收藏或 Commons 现有锁定文件夹冒充隐私空间。**尚未实现**，本次仍未达完全验收条件。
- **P1 / 路径分类**：文件夹分类视图曾用 `lowercase()` 去重，且路径后代判断未经规范化；会误合并大小写不同的目录。已用 `FavoriteFolderRules.normalizedPath/isExcluded/isDotHidden` 统一，新增大小写及前缀测试；待 CI 与实机。
- **P1/P2 / UI 细节**：Toolbar、Switch、Dialog、状态栏、媒体网格已找到对应样式实现，但**缺少逐页运行截图**，不按“视觉通过”结案。

## 自动化测试边界

原有 `FavoriteFolderRulesTest`、`FavoriteFolderLoadCoordinatorTest`、`FavoriteMediaSizeReconcilerTest`、`NavigationOrderTest`、`OxygenThemePresetsTest`、`SecondPhaseInteractionTest` 已抽查断言与相关实现。本轮新增 `MediaCachePrunePolicyTest`，并扩展目录大小写/后代规则测试。测试为 JVM 单元测试，不能证明 Android 16 的 IME、返回动画、Glide、播放器和生物识别行为。

## 构建和覆盖安装警告

`gradle.properties` 声明 `APP_ID=org.fossify.gallery`、`VERSION_NAME=1.14.1`、基础 `VERSION_CODE=30`；Arm64 ABI 分拆版本码预计为 32（必须由最终 APK 实测确认）。`gradle/libs.versions.toml` 声明 compileSdk/targetSdk 36、minSdk 26。GitHub workflow 仅调用 `:app:testFossReleaseUnitTest` 与 `:app:assembleFossRelease`，用 aapt/apksigner/ZIP ABI 校验。签名优先尝试仓库 secrets，缺少时创建**一次性临时 CI 签名**；该签名一般不能覆盖安装现有不同签名的应用，严禁为了测试而删除已有应用数据。

## Pixel 8 Pro / Android 16 手动验收（全部使用测试文件）

| 顺序 | 操作 | 预期结果 |
| --- | --- | --- |
| 1. 冷启动/首次加载 | 保留 App 数据，强制停止后打开，进入所有图片与收藏 | 媒体首屏可显示，无崩溃；收藏无需重复切换设置 |
| 2. 收藏、隐藏、排除 | 准备点号/.nomedia/排除/正常及同名前缀的受控文件夹，开启两个收藏开关，强制停止重进 | 目录仅在权限和授权允许时正确显示；不会取消收藏记录 |
| 3. 隐私空间 | 检查导航与设置是否有独立隐私空间入口 | 当前版本尚未实现：应记录未通过，切勿将锁定文件夹视为通过 |
| 4. 搜索与 IME | 搜索为空/有内容，键盘显示时分别用工具栏返回及系统返回手势 | 工具栏退出后键盘不残留；系统返回先关闭 IME |
| 5. 多选和删除确认 | 使用受控副本进入多选/全选/取消，打开删除对话框并取消 | 数量准确，图标清晰；两个危险选项默认关闭且取消不操作 |
| 6. 经典控件 | 逐页检查设置/主题/弹窗 Switch 三态、Radio、Checkbox、Slider | 细轨道圆形 20dp Thumb，经典 MD1 样式，深浅主题对比足够 |
| 7. 主题设置 | 切换预设/自定义背景/自动与手动文本，再切回 Fossify 并重启 | 配色即刻更新，重启与恢复保持用户旧设置 |
| 8. 缩略图 | 快速滑动包含透明 PNG、黑照片、视频、GIF 的目录 | 灰色未加载占位；图片不串位、黑照片不被强制变灰 |
| 9. 图片查看器 | 查看 JPEG/PNG/GIF/WebP/HEIC/RAW/AVIF/JXL，翻页缩放旋转 | 无旧图残留、无无故黑屏、EXIF 正确；不支持则准确记录格式 |
| 10. 视频查看器 | 连续播放暂停/静音/变速/循环/横屏两段不同视频 | 首帧不黑、进度正常、退出不残留音频 |
| 11. 后台恢复 | 播放或加载时切后台并恢复、旋转屏幕 | 没有崩溃、重复播放器、旧列表覆盖、权限越界 |
| 12. 权限变化 | 在受控目录中撤回/恢复照片权限和外置盘，重新扫描 | 不会误删收藏、数据库记录及用户真实文件 |

## 尚未取得的证据与最终阻塞

- 完整历史四阶段 Prompt 和原始截图/录屏逐项对照证据。
- Pixel 8 Pro Android 16 上密码/生物识别、IME、预测返回、系统栏、播放器及文件访问的实际测试记录。
- 所有 UI 页面、控件多态截图的逐像素比对。
- 不同容量图库的冷启动首帧、首批媒体、滚动掉帧、内存峰值实测数据。
- 独立隐私空间实现与完整隔离测试（明确未完成）。
- 最终 HEAD 对应的成功 Actions、签名详情及最新 APK Artifact 证据（在工作流完成后核对）。

最终判断：**未达到全部需求验收通过**。若对应最终 Commit 的 arm64 FOSS Release CI 成功，可将构建产物标记为 **Release Candidate / 待实机验收**，仍不得声称隐私空间通过、Android 16 实机通过或正式稳定版。
