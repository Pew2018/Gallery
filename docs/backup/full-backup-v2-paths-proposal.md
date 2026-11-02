# 完整备份 ZIP v2：无损路径元数据提案（未实施）

本文依据本分支现有源码核对。当前生产导出/读取仍为 ZIP v1，六文件结构、版本、校验规则与独立备份格式不变。本文件不是已实施格式规范；v2 应另行开发、审查和测试。

## 1. 当前源码与编码边界

| 用户配置/数据 | 当前实现位置 | 表示方式与已确认限制 |
|---|---|---|
| `INCLUDED_FOLDERS`、`EXCLUDED_FOLDERS`、`PINNED_FOLDERS` | `LegacySettingsSnapshot.collectLegacySettings()`；`LegacySettingsCodec` 的 `Kind.SET` | 内部为 StringSet，导出 `TextUtils.join(",", ...)`；导入 `raw.split(',')`。目录名含逗号时不可逆拆分；集合顺序本来不承诺保留 |
| `CUSTOM_FOLDERS_ORDER` | `Config.customFoldersOrder`；`DirectoryAdapter` 重新排序保存；`Context` 排序读取；`OxygenSettingsBackupCodec` | 内部已是 `|||` 连接的字符串，读取 `split("|||")`，JSON只包装该字符串，未消除分隔符歧义。导出前就可能已丢失边界；v2不可声称自动恢复 |
| `DEFAULT_FOLDER` | `Config.defaultFolder`；`OxygenSettingsBackupCodec.strings/validScope` | 单一原始字符串，无逗号拼接。v1校验目前只接受合法绝对文件路径、空值和已列出的虚拟范围；不能声称已支持任意SAF URI |
| `SORT_FOLDER_PREFIX` | Commons `BaseConfig.getFolderSorting`；`OxygenSettingsBackupCodec.dynamicPrefixes` | 路径写在偏好键后缀，读取用 `lowercase(Locale.getDefault())`。逗号不是分隔符，但大小写和土耳其语等语言环境可能造成折叠；原大小写可能已无法从键恢复 |
| `GROUP_FOLDER_PREFIX`、`VIEW_TYPE_PREFIX` | `Config.save/getFolderGrouping`、`save/getFolderViewType` | 同样按当前语言环境小写化路径后拼到键；具有同样的键冲突/大小写信息损失 |
| 相册封面 `ALBUM_COVERS` | `LegacySettingsCodec.Kind.COVERS/validateCovers`；Config继承的相册封面偏好 | JSON数组中的 `path` 与 `tmb`，本身不是逗号列表；必须保留原字符串和结构，单独校验，不能错误再次split |
| 收藏媒体 | `FavoriteBackupCodec` | 一行一路径的原版TXT，支持受校验的文件路径或content URI；保留现格式，不在v2借机改写。换行/control字符不是合法记录 |
| 收藏文件夹 | `FavoriteBackupCodec.FolderRecord` | JSON v1：`path`、`addedAt`；路径按 `FavoriteFolderRules.normalizedPath()` 判断重复。路径字符串可保留逗号，但原有归一化语义仍要单独说明 |
| 照片浏览配置 | `PhotoProfileBackupCodec` | JSON v1结构化 `sources`，当前FOLDER来源只接受合法绝对路径；FAVORITES为独立类型，无路径。不借v2偷偷扩展现有JSON v1 |
| 其他路径偏好 | `Config` 的 `tempFolderPath`、`lastExportedFavoritesFolder`、继承的上次设置导出目录、`everShownFolders`、视频进度路径键、Commons的保护路径键 | 多数是设备缓存/交互历史/安全配置，当前完整备份白名单不包含。v2默认继续排除，不能遍历全部偏好后自动打包 |

物理路径与虚拟范围必须分型，不能把 `FAVORITES`、`SHOW_ALL`、`RECYCLE_BIN` 或照片配置虚拟入口当作磁盘目录。

### 既有损失的限制

- 已导出的 v1 逗号列表无法可靠区分“一个带逗号的路径”和“两个路径”。禁止按当前设备是否存在文件猜测后宣称无损。
- `CUSTOM_FOLDERS_ORDER` 已在应用内部使用分隔字符串；未来v2导出前须先建立可靠的结构化本地存储。仅把 `split("|||")` 的结果包装成数组，不能解决此前的歧义。
- 小写化动态键不能恢复原始大小写。未来迁移只能使用明确的一对一证据；若多个真实路径映射到同一旧键，列为歧义，保留待审原键，不自动分配。
- 原偏好路径可包含中文、空格、Unicode及逗号；不做Unicode规范化、大小写折叠或猜测存储根映射。

## 2. 建议 ZIP v2 结构

七个固定文件：

1. `manifest.json`：原format保持标识，顶层version明确为2；清单列出六个payload的长度和SHA-256。
2. `settings.txt`：继续保留原版TXT表示，用于独立提取/旧版工具人工使用；不是v2路径集合的权威来源。
3. `favorites.txt`：原样格式。
4. `favorite_folders.json`：原JSON v1。
5. `photo_profiles.json`：原JSON v1。
6. `oxygen_settings.json`：保留现有非路径设置；路径相关旧字段作为兼容投影，v2恢复禁止作为权威值写入。
7. `folder_paths.json`：新增、必需、权威路径数据；结构自身version为1，与ZIPversion独立。

旧应用的完整ZIP读取器会因ZIPversion/文件数不匹配而拒绝v2，这属于显式不支持。不能宣称旧应用能直接完整恢复v2；应继续提供明确的“导出v1兼容备份”选项或提取独立旧文件。

## 3. 建议类型定义

下面是待实施的字段类型合同。所有对象 `additionalProperties=false`，所有字符串禁止NUL和控制字符。

```typescript
type PhysicalLocation =
  | { kind: "file"; path: string }
  | { kind: "document"; uri: string }; // URI本身不是授权，未来各消费端需明确支持

type Scope =
  | PhysicalLocation
  | { kind: "virtual"; id: "all" | "favorites" | "recycle_bin" | "photo_profiles" };

type FolderPathsV1 = {
  format: "fossify-gallery-folder-paths";
  version: 1;
  includedFolders: PhysicalLocation[]; // 集合语义，字符串精确保留
  excludedFolders: PhysicalLocation[];
  pinnedFolders: Scope[];
  customFoldersOrder: Scope[];        // 数组顺序有意义
  defaultFolder: Scope | null;
  folderPreferences: {
    scope: Scope;
    sort?: number;                   // 与现有掩码/数值范围校验相同
    group?: number;
    viewType?: 1 | 2 | 3;
  }[];
  unresolvedLegacyScopes: {
    key: string;
    value: number | string;
    reason: "case_fold_collision" | "delimiter_ambiguity";
  }[];                               // 保留审计信息，不自动写回偏好
};
```

`unresolvedLegacyScopes` 不是完整偏好转储，只允许已审计的排序/分组/视图键和自定义顺序原值；禁止放入保护凭据、绑定或授权数据。是否允许带有歧义项导出v2，必须向用户明确说明“部分旧配置尚未无损迁移”；否则应拒绝宣称无损的导出。

数组重复策略：集合数组按 `kind + 原字符串` 精确去重；有序数组重复项拒绝，不默默移动次序。未来若需要目录同一性归一化，应另设运行时匹配层，绝不能改写备份中的原始字符串。

## 4. 恢复优先级与失败规则

### ZIP v1

永远保留现有六文件读取、原分隔解析与校验。提示旧表示可能有损；不改变TXT分隔符、不从路径存在性猜测修复。

### ZIP v2

1. 先验证ZIP结构、manifest version、所有必需payload、CRC、原始长度、SHA-256、UTF-8及JSON结构。
2. `folder_paths.json` 的校验与解析必须整体成功。
3. 生成统一的只读恢复计划，路径集合/默认目录/自定义顺序/各目录偏好只来自结构化文件；先从旧TXT及旧扩展设置的待写入值中排除这些键。
4. 不能将TXT与结构化路径的集合拼接或按最后写入者覆盖；结构化路径权威，兼容投影不参与恢复。
5. 缺少、损坏或不受支持的结构化文件：拒绝整份v2恢复，在任何用户数据写入前退出。禁止静默降级到可能有损的TXT。可另行明确选择提取旧版独立文件，作为新的导入操作，而不是本次v2的暗中回退。
6. 文件访问、真实保护目录、虚拟收藏、SAF持久授权和媒体权限仍分别检查；路径元数据可保留离线引用，不能因为结构化数据有效就认为可访问。
7. 跨设备根目录只保留原值并报告未匹配。任何根目录映射必须由用户明确确认，按精确前缀边界逐项预览，不能字符串全局替换。

与现有恢复语义相同，MERGE的设置覆盖范围须逐字段定义；REPLACE仅在已审计路径设置范围替换，安全可见性策略继续保留。业务关系仍使用现有Room事务、偏好补偿和恢复检查点。v2准备写入的路径键须先加入审计过的检查点白名单，不能扩大到所有pref。

## 5. 验证与大小上限（建议值，需实现前确认）

- manifest：仍最大64KiB；每payload最大8MiB；v2总解压预算最大48MiB+64KiB，ZIP最大56MiB。与v1的40MiB+64KiB/48MiB限额分开，不扩大v1。
- 正好7个固定非目录条目；拒绝重复名称、缺失/未知文件、目录、绝对路径、路径穿越、ZIP64、尾随数据和中央目录/本地条目不一致；保留当前校验强度。
- 每类集合最多100,000条；目录偏好最多10,000条；单路径/URI最多4,096个Unicode码点且需同时满足8MiB文件限额；动态偏好键另按原实现长度限制。
- `file`只接受绝对路径，禁止 `.`、`..` 段；保留大小写、空格、原Unicode与合法分隔字符。末尾斜杠在存储字符串中保留，匹配时另算身份；同名不同目录绝不按basename合并。
- `document`只接受合法 `content` URI，保留原转义表示；备份不含授权flag/token。实际恢复按目标设备的persisted read grant及provider元数据判断。不支持SAF的旧消费端必须跳过并报告，不能把URI转成 `File(uri)`。
- 目录偏好值沿用现有排序、分组掩码和视图类型范围；不接受任意偏好键或任意类型。
- 未知JSON字段、重复JSON键、不支持的format/version、数值溢出、类型错误或过大输入在写入前拒绝。

## 6. 安全排除与授权

绝不导出或装入结构化路径文件：PIN/图案Hash、生物识别证据、fallback凭据、保护开关/凭据键、来源绑定、解锁Session、一次性恢复授权、SAF持久授权、系统permission状态、设备token、临时缓存目录、历史播放器进度和恢复Journal。

路径可能透露用户目录命名，应只保留用户明确纳入备份的数据；不要扫描媒体内容或缩略图来补齐缺失路径大小写。输出文件本身仍需由用户妥善保管。

## 7. 后续实现前测试清单

- 带逗号、字面 `|||`、中文、空格、emoji、组合/分解Unicode、土耳其语大小写、大小写不同路径、末尾斜杠、同名不同目录。
- 无损本地集合 -> v2 -> 恢复 -> 再导出结构相等；明确无法恢复的旧歧义输入不得伪造无损结论。
- 外置SD卡离线、跨设备根变更、SAF已授权/无授权/撤销、第三方opaque URI、未知provider、独立真实文件夹锁。
- v1全部旧测试继续通过；旧版本显式拒绝v2；独立TXT/JSON v1保持格式不变。
- v2结构化路径与旧投影冲突时只用结构化值；缺失/错误结构化文件拒绝整份恢复；不做隐式降级。
- JSON重复键、重名ZIP项、压缩炸弹、每文件/总限额、CRC/SHA错误、UTF-8错误、数值范围、路径穿越、错误version。
- 路径设置事务与补偿、进程中断、检查点协调、权限/保护状态变化、不可写安全键、统计来自最终提交。

## 本阶段结论

仅交付本提案；当前ZIP v1仍保持六文件结构，当前独立TXT/JSON格式及其历史编码限制保持不变。下一阶段必须先解决本地有损存储和小写偏好键的证据问题，再实施能够真实承诺无损的v2导出。
