# 27 — Phase 1.5 验证与交付

唯一分支 `refactor/media-pipeline-stability`，起始远程HEAD `7670a10a3b857df490ea2120f6fa1272f48a8879`。原始两份Pixel日志的完整逐行解析见25，函数修改和测试边界见26。

## 应用源码、CI与APK

PENDING_CI：集中提交后由现有 `arm64-foss-release.yml` push触发；完整 `:app:testFossReleaseUnitTest`，ARM64 FOSS Release、包名/ABI/签名、Artifact实际下载校验完成后在本页补写真实SHA、Run、XML数量和摘要。没有验证前不得视作交付完成。

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
