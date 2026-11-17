# 普通文件夹扫描与回位

`MediaActivity.detachFolderViewerScan` 在普通、公开、物理文件夹实际打开 Viewer 后保留本 Activity 现有的请求和 AsyncTask，只保留一份待交付 UI 发布。继续使用原有 45 秒 watchdog、扫描 executor、权限校验、源阶段和请求 ID。前景返回时先重新核对访问能力、目录身份、目录 mtime、MediaStore 各卷版本/代际、配置签名和 `FolderContentRevision`。直接文件系统扫描另保留一个 FileObserver，帮助识别 MediaStore 尚未观察到的本地变化。既已接受的 COMPLETE 与 Adapter 是否提交分别处理。

同一 Activity 在同一请求下复用进行中作业或已交付快照。权限撤销、源变化、内容变化、配置变化、显式刷新、超时和销毁使复用失效。失效回到正常权限门及加载路径。受限授权、专用 .nomedia、受保护源、SAF 限制目录、OTG、全图、收藏、回收站和自定义配置继续走原路径。随机排序继续使用旧路径。扫描按请求捕获配置，排序变化沿原重新排序或重新加载入口处理。

全文件权限的直接读取是日志中 3,080 项目录的已证路径。仅依赖 MediaStore 的先前回位检查排除该授权模式，这次复用增加了本地变化观察，同时保留原有 MediaStore 和目录检查。跨进程、跨独立 Activity 和 Viewer 自己的边界扩展仍使用独立请求，没有进程级共享扫描目录。FileObserver 事件交付和实际权限切换需要原设备覆盖。
