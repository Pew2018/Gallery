# 30 — Phase 2A 结果与交接

日期：2026-11-05。仓库 `Pew2018/Gallery`；唯一修改分支 `refactor/media-pipeline-stability`。本轮开始前重新读取远程 HEAD `500f327a534832b83e872babc26ef32729e07867`；未重置到旧提交。CI验证的应用源码 SHA：`dcd41024e06451eb7a46d28db18a84157cd5102b`，该提交完整Git tree与本地已审查源码 tree `ddf1392305e69a5b578e87e97c01b9576619d592` 一致。最终文档更新只改docs。

## 1. GitHub Actions 与测试

- 最终验证运行：[Actions Run 41983357685](https://github.com/Pew2018/Gallery/actions/runs/41983357685)，最终 `completed / success`，head SHA为`dcd41024e06451eb7a46d28db18a84157cd5102b`。
- 完整 `:app:testFossReleaseUnitTest`：**889 tests，0 failures，0 errors，0 skipped**。工作流独立汇总JUnit XML显示 `{tests: 889, failures: 0, errors: 0, skipped: 0}`。相对Phase 1.5完整886项基线，新增的本阶段3项策略测试通过，原套件全部继续通过。
- `:app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a` 成功。CI `apksigner verify --verbose --print-certs`、aapt包名检查及ABI检查均成功。
- 前一个提交`772183e9e7795af4788086793a2695dd77c10377`在传输时出现UTF-8内容编码错误，Run `41982920235`失败于完整测试步骤，未打包APK。未强推覆盖；通过后续修复提交按Git blob SHA逐文件比对本地源文件，并在`dcd4102…`重新运行完整CI。最终结论只依据后一Run。

## 2. ARM64 Release APK

- 包名：`org.fossify.gallery`
- 版本：`1.14.1` / versionCode `32`
- ABI：`arm64-v8a` only
- Artifact：[gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/41983357685/artifacts/13395436120)，ID `13395436120`，实际存在；Artifact ZIP SHA-256：`4e6ce3534f940c1b3c894f0d3f0b167d78eb1be46b7cf43bdb0a8e0bbd7aa548`。
- APK SHA-256：`d121463944c60e2bd79d7e810b345fa11d9e9004db65a0d95867141fa075e70f`。
- 签名：CI `apksigner`通过，V2签名；证书DN `CN=Gallery Temporary CI Test Signature, O=Local Testing`；证书SHA-256 `c1218152a84321aff6b7df5ad351bdce6dd3f04f2bc76da596d73f487db45b58`。模式为`temporary-CI-not-upgrade-compatible`，不能保证覆盖其他证书签署的已安装版本。
- 测试报告Artifact：[backup-ui-test-reports，ID 13395265990](https://github.com/Pew2018/Gallery/actions/runs/41983357685/artifacts/13395265990)。

## 3. 本阶段实际完成

1. 对已经正式COMPLETE且授权/请求仍有效的普通物理文件夹排序，复用当前`Medium`集合；在后台调用原排序器，并通过原Adapter/Diff更新。满足条件时不清Adapter、不重新查询缓存、不启动全量媒体/文件系统扫描。
2. 其他状态仍走原扫描及权限路径：PhotoProfile、收藏、主页、回收站、分组行、PARTIAL、加载中、旧request或无效访问都不复用快照。
3. 没有跳过实体存在/regular/readability验证，没有删除`.nomedia`磁盘补扫；没有改变Viewer状态机、Phase 1.5保护gate、Room Schema或OxygenOS Classic样式。
4. 没有优化比较器预计算：旧日志排序99ms/81ms是修复前样本，远小于metadata旧样本数秒；没有本阶段设备数据证明其当前显著。

## 4. 性能数据与真实性边界

- 旧日志数据（Phase 1.5修复前Pixel 8 Pro样本）：5,285项metadata累计6,720ms，另一次5,685ms；目录枚举808ms，MediaStore约173ms，排序99ms。它们用于定位热点，不是本轮代码的前后测量。
- 本地Gradle Wrapper无法下载Gradle 9.5.1（`Network is unreachable`）；测试/构建由Actions执行。没有Pixel 8 Pro、Perfetto、UI录屏或同设备数据集对照。
- 本地代码审计确认本地排序分支避免整个`getMedia()`重新加载调用链，但本轮没有真实设备统计其实际减少的磁盘调用数、排序点击耗时、Adapter/Diff帧时间或首显时间。
- Room逐语句调用数/累计耗时/行数及实际SQLite `EXPLAIN QUERY PLAN`未采集：当前环境没有设备数据库。未基于模型字段猜测执行计划，不新增Schema/Migration。
- 所以100项、5000项、10000+、约5000项PhotoProfile、`.nomedia`、多文件夹、收藏、未索引和SAF/ALL_FILES场景的真实性能与端到端正确性矩阵均为`PENDING_DEVICE`。

## 5. Phase 1.5 与后续准入

Phase 1.5阶段之前用户报告的Viewer连续浏览问题仍未做修复后Pixel复测；本阶段没有扩展Viewer范围。因此不能声称“全屏问题彻底解决”。自动化回归继续通过，但不是触控和设备时序验收。

进入下一轮前建议先完成Phase 1.5的Pixel设备矩阵与本阶段同设备排序对照，至少记录100/5000/10000+普通目录、`.nomedia`与SAF/ALL_FILES、排序前后request/终态/metadata/sort/Adapter/Diff/滚动path锚点；重复样本后再报告p50/p95。Room索引或缓存调整须先提交实际SQL、行数、设备query计时和`EXPLAIN QUERY PLAN`，之后再单独评估Migration或失效策略。
