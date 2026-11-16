# ViewerSourceScope 与媒体身份

一次交接描述由 `sourceType`、实际查询范围的 `scopeKey`、授权代际、内容修订、排序/过滤签名、提交的 Adapter 版本、媒体身份和请求代际组成。位置仅参与在某个已提交版本中定位。这里的 scope 描述是只读证据；现有入口继续通过各自的授权和发布门。

| 来源 | Scope 及现有授权/版本依据 |
|---|---|
| 普通文件夹 | 物理目录与过滤、分组和排序。FolderUnlockSessionManager 复验受保护祖先。FolderReturnCheckpoint 仅覆盖符合条件的公开单目录，比较配置、各 MediaStore 卷版本/代际、目录 mtime、FolderContentRevision 和新鲜度。 |
| .nomedia | 路径相同也可能有不同索引资格。使用受权限约束的物理目录扫描及专用变更证据；MediaStore 代际单独不足以证明新鲜度。 |
| 全部媒体 | 全部媒体查询、设备读权限、隐藏/排除规则及可访问卷集合。 |
| 收藏 | 关系快照、FavoriteAccessSessionManager 当前授权和收藏关系修订号。去掉受保护内容后才能交付。 |
| 回收站 | 回收站有效成员及 RecycleBinMembershipRevision。 |
| 内联搜索 | 当前已提交来源、搜索代际及过滤签名。查询文字留在应用内。 |
| 全局搜索 | SearchActivity 独立查询与访问规则，单独解析范围，现有路径不能直接当作网格交接凭证。 |
| 自定义照片配置 | PhotoProfileScope、PhotoProfileLoadContext、PhotoContentRevision；配置、规则、源及当前授权共同决定有效目录。 |
| 第三方 Picker / 外部 URI | Intent 媒体类型、调用方授权范围和当前 URI grant。ViewPagerActivity 的外部 URI 路径可仅有单项，授权仍须复验。 |

- `ViewerMediumIdentity` 已用路径定位符及媒体类型；`ViewerContentSignature` 携带 size、modified、provider ID 和显式失效版本。Provider ID 应在 provider/volume namespace 内解释。路径相同且已知签名冲突或失效版本改变时，旧像素及旧交接失效。零值表示缺测，旧实现允许补全元数据，零值不能建立跨请求复用证明。
- 内容修订描述成员或文件内容；排序/过滤签名描述查询及展现，Adapter 版本描述提交结果。只有适用的 scope、授权、查询签名、新鲜度及请求黏合键都成立时才考虑复用。排序差异可在后续阶段定义显式重投影路径。
- 活动的业务 request generation 可重新计数。诊断必须携带进程命名空间中的全局记录 ID；旧请求可以记录真实终态，不能将旧事件算入新请求。返回要取 Viewer 当前账号授权下的当前媒体、目标 Adapter 版本及同一加载键。
- 授权撤销应通过现有访问门阻止缓存、已完成目录、handoff 或旧回调曝光。缺少可验证的授权代际时，诊断标 UNKNOWN，复用资格仍需由现有访问实现证明。日志允许枚举、计数、版本及加盐指纹。
