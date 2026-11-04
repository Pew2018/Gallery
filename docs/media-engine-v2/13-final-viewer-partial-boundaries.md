# 全屏浏览部分目录边界收敛

日期：2026-11-04（Asia/Shanghai）。目标分支：`fix/photo-viewer-five-item-boundary`。

## 审计结论

开始时远程 fix 与 `feature/photo-session-seamless-viewer` 均指向 `507797d5795b012f7ed4f22ba10374c1c5844ae1`。最近提交已为普通文件夹和非分段 Photos 部分列表增加 `contiguousPartial()`，并记录在 R05 开发文档中。

| 风险 | 证据与结论 |
|---|---|
| 收藏部分扫描压缩缺口 | **确认缺陷。** `publishValidatedFavorites()` 对预览执行授权后，仅用 `reconcile()` 按网格顺序压紧剩余项。若缓存网格为 A–F、预览缺 C，B 与 D 可在非完整阶段成为邻居。修复为对部分结果复用 `contiguousPartial()`；继续使用既有收藏授权和完整扫描路径。 |
| 普通文件夹/Photos 的部分锚点与当前页 | **确认边界缺陷。** `contiguousPartial()` 在无网格顺序、锚点未记录或预览缺锚点时退回普通 reconcile；folder/photo 调用方会以完整结果提交。异步准备期间用户滑动后，`refreshUI()` 虽按路径定位，但当前路径已缺席时保留整数位置会显示另一媒体。修复为这些情况返回“不可安全发布”的空结果；调用方仅在当前路径仍包含于部分列表时提交。COMPLETE 继续按已有合法替代逻辑处理，权限撤销仍优先结束或撤权。 |
| Photos 搜索、排序与分段方向 | **未发现需改缺陷。** 网格与全屏使用相同的“前缀优先、同组稳定保序”语义（`ViewerSearchPolicy` / `searchQueryChanged`）；分段扩展已有权威 ordinal 和方向检查，冲突时使用目录顺序。继续保留现有分段和回退。 |
| 异步发布、滑动、Fragment 身份 | **已有保护。** publication/load/stage generations、目录 revision、`ViewerSwipePublicationGate`、idle 交接和 Path/type Fragment 身份均在当前调用链；未发现本轮可证明的额外漏洞。新增真实 Pager/Adapter 回归，覆盖扫描锚点陈旧且当前页已前进时不发布缺少当前 path 的结果。 |
| 顺序交接并发 | **已有安全降级。** `ViewerGridOrderStore.take()` 先匹配一次性 ID 再消费，旧启动无法消费后来写入的 ID 不同记录。超过 50k、过期、丢失继续回权威顺序；凭据不参与授权。本轮不增加缓存容量或持久化。 |
| 同路径内容与缓存 | **已有正常更新保护，存在环境可观察性边界。** `Medium.getSignature()` 含 path/mtime/size/显式失效版本；编辑/替换等应用内动作走失效 registry，刷新入口 `refreshViewPager()` 也失效当前路径；Adapter 按签名更新 Fragment。外部程序若同时改变字节且保持全部可观察元数据不变，应用未获事件时不能即时检测；不引入全库哈希。 |

## 实施内容

- `ViewerGridOrderPolicy.contiguousPartial()`：对部分列表不再以普通排序猜测缺失锚点或未知顺序；没有已提交网格顺序、当前锚点或可验证连续项时拒绝该阶段发布。
- `ViewPagerActivity.completePhotoViewerLoad()`、收藏预览与 `publishFolderCandidates()`：沿用已有后台校验、扫描和 revision/generation；提交前再次核对实际当前路径。部分阶段无法证明当前页仍在结果中时保留当前已授权 Pager，而不是按旧整数索引改页。完整结果仍由原路径处理。
- `ViewerGridOrderPolicyTest`：加入无顺序、锚点缺失和旧锚点导致当前已滑到媒体缺失的拒绝用例。
- `PhotoViewerProgressivePagerTest`：实际 FragmentManager、Pager、Adapter 和 Fragment arguments 覆盖当前页身份；缺少 live path 的部分列表不能被交给 adapter。

未修改搜索排序函数、目录存储、分段算法、全量缓存、Fragment 管理、授权模型、内容缓存键或 UI 动画，因为审计未发现需扩大的代码依据。

## 验证

- `git diff --check`：通过。
- 本地定向 Gradle 测试：未启动。Wrapper 下载 Gradle 9.5.1 时网络返回 `Network is unreachable`。
- GitHub Actions [Run 41777792302](https://github.com/Pew2018/Gallery/actions/runs/41777792302)，源码 SHA `edba4896048d50d1fa224d9599cf657673d72c17`：成功。完整 FOSS Release 单测 823 项，0 failures、0 errors、0 skipped；ARM64 Release 构建和 APK 校验、上传均成功。
- APK：artifact [gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/41777792302/artifacts/13297533124)，ID `13297533124`；APK SHA-256 `1937708ac74b727c78ff677e7f428f4a661e463d9a0bba05cb75b3197cb9cbb6`。包名 `org.fossify.gallery`，versionName `1.14.1` / versionCode `32`，APK Signature Scheme v2 验证通过，native ABI 仅 `arm64-v8a`。工作流使用临时 CI 签名，不证明可覆盖现有安装。
- 真机目录权限、收藏关系变动、搜索、快速滑动、文件增删移动和真实图像/视频解码仍需设备确认；Robolectric 不代表设备验收。
