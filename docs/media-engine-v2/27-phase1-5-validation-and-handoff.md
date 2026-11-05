# 27 — Phase 1.5 验证与交付

唯一分支 `refactor/media-pipeline-stability`，起始远程HEAD `7670a10a3b857df490ea2120f6fa1272f48a8879`。原始两份Pixel日志的完整逐行解析见25，函数修改和测试边界见26。

## 应用源码、CI与APK

受测应用源码 SHA：`026a943a445619407134394bf434b753c65695e2`。

最终运行：[Actions 41975453251](https://github.com/Pew2018/Gallery/actions/runs/41975453251)。完整 `:app:testFossReleaseUnitTest` 已通过：886 tests、0 failures、0 errors、0 skipped。已实际下载本次backup-ui-test-reports（Artifact ID13391203197），独立汇总全部JUnit XML，与CI相符。ARM64 FOSS Release构建、包名/ABI/签名校验、Artifact实际下载与哈希比对均已通过，Run最终completed/success。

历史运行：41973562513在`startFavoriteViewer`新增交接校验引用不存在的folderSessionId而编译失败；838d014…修正发送端/接收端使用favorite session。41973905285实际完成完整884 tests、0 failures/errors/skipped；随后新增确定性tie回归与所有权清理触发新运行，旧APK阶段被并发策略取消。41974456185也被新提交取消。中间成功或取消不能替代最终源码的CI成绩。

最终所有权补修：重新认证进入startFolderViewer时取消旧scan gate/watchdog，清理旧候选扩展/complete准备标志；failure同时释放候选扩展标志。不会让已退休attempt阻止新Viewer producer。

本地Gradle：2026-11-05尝试 `./gradlew --version`，下载Gradle9.5.1时Network is unreachable；没有本地完整单测通过声明。静态diff检查通过不替代CI。

保留已有签名规则：优先仓库Release secrets，否则每次生成临时CI证书。不保证临时签名APK能覆盖已装不同签名版本；构建后报告实际模式/证书。

## Pixel 8 Pro / Android 16 真机矩阵

全部 `PENDING_DEVICE`。当前没有设备控制/执行环境，不能以JVM或Robolectric结果替代。

| 场景 | 验收/记录 |
|---|---|
| 普通文件夹>10000项，从中间进入 | 首屏可交互，双向至少20项；到临时边界等待时长；有界候选扩展和全目录完成分别记录 |
| 进入立即快速滑动 | 不永久局限5/15项；等待结束或扫描失败可边界重试；UNKNOWN不能冒充END |
| 自定义配置>5000项且网格正在Diff | capture/consume gridVersion一致；当前匿名path不变；后台新成员只在明确COMPLETE升级规则加入 |
| 日期/名称/大小/修改时间/路径排序 | COMPLETE邻居符合全局排序；升级在idle，实际当前path保持 |
| 随机 | 已显示顺序稳定，新增合法媒体追加；分段扩展不错误反向 |
| .nomedia、未索引文件、多来源 | 最终合法文件不丢失，新增媒体仍被文件系统补扫发现 |
| 混合公开/受保护、撤权 | 未授权内容不能靠旧Store/网格路径进入；公开来源仍可使用 |
| 扫描中删除/移动/重命名/恢复 | 旧revision不能复活原path；当前项优先真实历史/邻居恢复，不按旧整数跳图 |
| 连续滚动与刷新/后台恢复 | Diff/stable ID/延期发布继续有效，无新明显闪烁、错图、持续重排 |

## 同设备性能复测表（不填造数据）

固定设备、数据集、排序、授权与诊断开关，前后各重复至少3次；每次保留request、scope、businessRequest、handoff ID、gridVersion和单调时间。样本足够再算p50/p95。

| 指标 | 修复前证据 | 修复后 |
|---|---|---|
| 初始5张→首次扩展 | A：12858ms，vw-b507…，单次日志 | PENDING_DEVICE |
| 首张图片实际渲染 | A Performance=205.846ms；B=208.024ms，无直接request字段 | PENDING_DEVICE，不能等同可交互时间 |
| 临时边界→可继续浏览 | A首boundary到15张=11608ms，仅日志窗口事件 | PENDING_DEVICE |
| 重复全量扫描次数 | A原普通scan取消后Viewer新scan；源码能解释；不能由request0数出所有后台scan | PENDING_DEVICE |
| metadata次数/累计 | B两次各5285；6720/5685ms；不同ml请求 | PENDING_DEVICE |
| 阶段/Adapter/Diff | B全文件40submit/10commit/14drop；不是单次请求成绩 | PENDING_DEVICE |
| 最大单次Diff | B request11=1483ms；request12=68ms | PENDING_DEVICE |
| 当前path/邻居 | 旧日志有页ID但无完整grid版本；新摘要补足 | PENDING_DEVICE |

## 阶段准入

本阶段自动化正确性/CI交付后，仍须用户真机验证A和B的P0。没有足够同条件设备结果，不进入原阶段2的Room查询/索引、全局排序或缓存架构重构。记录日志开销未测；B3真实drawable错位未证实；文件外部覆盖且mtime/size不变等既有限制保留。

超时边界补修：Viewer普通目录不是固定总45秒截断。排队和“45秒无新增候选进度”才超时；每次当前attempt候选数量实际增加后续期，同count重复事件不能续期。允许合法大目录扫描继续完成，不借超时永久截断完整结果。仍无真机后测，不能承诺实际全量完成时间。

观测口径：普通Viewer task调用方传入VIEWER reason，但原MediaPipelineDiagnostics白名单仅接受INITIAL/REFRESH/RETURN/CONFIG_SWITCH/EXTERNAL_CHANGE，最终为UNKNOWN；sourceType仍为FOLDER。应结合唯一ml请求、businessRequest load代际、scope及Viewer的handoff capture/consume ID关联，不把UNKNOWN误读成另一业务请求。未为此扩展阶段0枚举。

## 最终完整JUnit XML核验

来源：Run41975453251的`backup-ui-test-reports`，ZIP SHA-256 `0b70c4c9f09181f11bd37cbc02288385dbf8b6a9274fac583e7e92a5c56da4b9`。以下均0 failures / 0 errors；完整套件886项，0 skipped。

| Suite | Tests |
|---|---:|
| Phase15GridEvidenceTest |6|
| Phase15ViewerRegressionTest |17|
| FolderRefreshActivityTest |11|
| GalleryRefreshRegressionTest |9|
| PhotoProfileMediaLoadingTest |30|
| RefreshReliabilityTest |10|
| ViewerDeletionNavigationPolicyTest |14|
| ViewerGridOrderPolicyTest |11|

这里只列关键套件，不将其小计冒充全部886项。新增23项均未使用Thread.sleep。885项的上一运行41974922404也实际完成单测后被新提交取消APK阶段；最终判断以本次886项和同SHA的APK为准。

## 最终APK、签名与下载核验（2026-11-05）

- 受CI验证的应用源码：`026a943a445619407134394bf434b753c65695e2`；[Run41975453251](https://github.com/Pew2018/Gallery/actions/runs/41975453251) 最终 **completed / success**。后续文档收尾提交只更新docs，不改变本APK应用源码。
- 完整单测：886 tests / 0 failures / 0 errors / 0 skipped；`assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`成功。
- 包名 `org.fossify.gallery`，versionName `1.14.1`，versionCode `32`，compileSdk36。CI aapt检查和本地下载ZIP的原生库清单均为仅`arm64-v8a`。
- CI `apksigner verify --verbose --print-certs` 成功：v2=true；证书 `CN=Gallery Temporary CI Test Signature, O=Local Testing`；证书SHA-256 `0a687554fcf948b647f2d8d7f865329194a334414564c13e2673c0408e77189b`。模式 `temporary-CI-not-upgrade-compatible`，不能保证覆盖其他签名已装版本，不应为测试要求清除应用数据。
- 实际存在且已下载的Artifact：[`gallery-foss-arm64-v8a-release`，ID13391393642](https://github.com/Pew2018/Gallery/actions/runs/41975453251/artifacts/13391393642)。ZIP SHA-256 `cee5f5c5bed7aa6134056ba524ff07bc90ac54261d5777f925031bd5e40e6c5b`，与GitHub artifact digest一致。
- ZIP内唯一APK `gallery-foss-arm64-v8a-release.apk`：18369179 bytes；SHA-256 `2add41160860baa3e7215e7c25c8eb9ed449339a46ac351b183804cc44c86f7e`，与CI记录逐字一致；下载后再次检查native ABI只有arm64-v8a。
- 可直接取得的测试APK：[gallery-phase1-5-foss-arm64-v8a-release.apk](https://chatgpt.com/library?file_id=libfile_6a34aa68e86c8191af8faaaf398e8e72&account_id=personal)。
- 真实性边界：apksigner、aapt在CI执行；本地下载以相同SHA证明是同一份已校验APK，并独立确认ZIP内容和ABI。不声称本地有Android SDK或进行了设备安装。

**结论：代码、886项自动化回归、FOSS ARM64构建与实际APK交付已完成；上方所有Pixel真机验收及同条件性能前后对照仍为PENDING_DEVICE。B3位图错绑未证实，未修改图片解码。原阶段2尚未启动。**
