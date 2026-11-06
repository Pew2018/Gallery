# Phase 2 · 02 — 分组、交互与主题

SettingsActivity.settingsCategories 的八页、settingsSectionRows 的十八组和 isSettingsRowAvailable 条件保留。renderSettingsPage 仍从 live config 计算可用行，只有当前分类的组/行可见，组内无可用行时不显示标题。未引入新的权限条件。

ClassicSettingsSwitch 保持 parent row 为唯一状态/持久化所有者：performClick guard 阻止 CompoundButton 原生重复toggle；拖动、RTL、垂直滚动取消、ACTION_CANCEL、禁用、失焦与detach机制原样保留。不更换SwitchCompat/MaterialSwitch，不增加checked listener。

新增 ClassicSettingsPresentation.rowRipple 只处理 presentation：已有RippleDrawable只setColor；首次无Ripple时创建有矩形mask的bounded Ripple。分类、内页可点击holder、主题页复用。主题更新不重建已有Ripple，pressed状态交给系统；GPU波纹曲线仍需设备检查。

分组/主副文字、Divider、Radio和Switch继续取 OxygenThemeEngine.snapshot 与 OxygenThemeColors 的现有 semantic token。尤其Switch onThumb保留用户rawAccent，不能强制套用文字的readableAccent。没有新增固定蓝色交互值，也没有修改预设数量、custom hex解析/保存或默认恢复。

OxygenThemeActivity Section改用同一个XML style，Divider去除额外8dp margin；action/radio/color row保持56dp min与16sp文字；Radio整行补focusable，重着色保留Ripple；主题页销毁取消content animator并归位。不修改chooseColor、备份、Picker、密码、安全或主题配置业务。

验证覆盖见04：现有ClassicSettingsControlsTest包括单次行回调、拖动、RTL、禁用、取消、Raster色彩和轨道；新增设置页实际偏好变更计数/动态可见性与重进恢复。系统权限弹窗、拖动与真实滚动拦截、TalkBack和硬件Ripple仍待设备。
