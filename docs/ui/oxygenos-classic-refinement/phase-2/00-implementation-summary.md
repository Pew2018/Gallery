# Phase 2 — OxygenOS Classic 设置精修

## 基线、范围与证据

- 执行基线：`8620d441e3d5456e6513b188cf2dd46791283363`，从指定远程分支重新 clone 并 ls-remote 确认；工作区初始无改动。
- 分支仅 `refactor/media-pipeline-stability`；未操作 main、分支/PR 创建、Room、媒体/删除/Viewer 管线。
- 阅读 README、01/02/03/05/08/09/10/11，Phase 0 的 01/04/05/06，Phase 1 的 00，以及附件设计指南和参考截图。
- 历史文档不是当前真值：最新代码已有 Phase 1 菜单、IME 和删除 Checkbox 修复；本阶段保留。Commons 7.1.3、现有行 ID、权限条件、所有设置保存/业务回调保留。
- 参考图是旧版 ROM 的静态观察；指南 dp/sp 是截图拟合。本次数值是应用实现选择，**不是厂商官方资源实测**。没有本阶段真机截图或录屏。

## 实施结果（验证状态见 04）

| 工单 | 实施 |
|---|---|
| P2-A | 八分类共享 72dp 最小行高/16sp 标题/13sp 副标题，56dp leading slot 和实际 24dp 图标保留；原位重着色，七条 Divider，无尾线 |
| P2-B | 普通 56dp、双行 72dp 最小高度保留；Switch 行从至少 64dp 调为 56dp；分组 48dp 最小高度；不改变条件可见性规则 |
| P2-C | 保留 ClassicSettingsSwitch 全部触摸/点击/拖动/取消/权限行所有权；整行 Ripple 依据当前主题更新颜色且保留实例 |
| P2-D | 保留 ViewPropertyAnimator；最低 alpha 0.12→0.72、8dp、80+100ms；支持 RTL、epoch 取消、销毁归位、0x；保存分类及主页滚动状态 |
| P2-E | 主题页使用同一 Section 样式、Ripple 重着色 helper 和 Motion token，移除 Divider 上额外 8dp；Radio 行补键盘焦点，销毁取消动画 |
| P2-F | Theme snapshot 语义颜色保留；宽屏公式连续，600dp 内容 522dp、840dp 内容 630dp；手机全宽 |

主要文件：SettingsActivity、OxygenThemeActivity、TiebaSettingsCategoryRow、TiebaSettingsContainer、OxygenClassicMotion、ClassicSettingsPresentation、oxygenos_classic.xml；activity_settings.xml 的 View ID 和层次未改，ClassicSettingsSwitch/OxygenClassicControls/OxygenThemeEngine 逻辑未改。

最终源码提交、CI、APK 和自动化结果在 [04](04-regression-and-build.md) 记录；[05](05-device-acceptance-checklist.md) 是独立的待真机验收清单。未以源码实施代替代码完成 DoD。

## 最终交付状态

执行基线8620d441；最终应用代码/测试HEAD为`ebe8fa34c600215910f29112e2078fd824dd2954`，Run42188141776成功。977测试全部通过、ARM64 APK签名/包名/ABI/摘要验证通过。P2-A至P2-F的代码与自动化工作完成；六项的最终视觉/真实系统交互均待设备验收。后续记录提交仅更新docs，不改变已构建app树。详细证据、修正历史与签名限制见04。
