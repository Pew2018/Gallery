# Phase 1 — 收藏文件夹双态操作菜单

## 行为映射

标题表示下一次点击的动作，并依据已经持久化的 `config` 值计算：

| 当前值 | `favorite_always_show_excluded` | `favorite_always_show_hidden` |
|---|---|---|
| `false` | 显示已排除项目 | 显示隐藏项目 |
| `true` | 不显示已排除项目 | 不显示隐藏项目 |

两项完全独立，覆盖 FF、FT、TF、TT 组合。

## 修改与兼容性

- 保留两个 MenuItem ID、顺序 40/41、收藏页可见性及 `MainActivity.setupOptionsMenu()` handler。
- 菜单 XML 去掉 `checkable=true`，`refreshMenuItems()` 设置资源标题，不再设置 `isChecked`。
- 新增 `FavoriteMenuActionTitles` 将两个布尔值转换为四个 string resource ID；资源只增加默认英文和 `values-zh-rCN` 简体中文，其余语言回退英语。
- 从 `TiebaMenuItemView.initialize()` 删除仅为这两个 ID 注入 Checkbox、颜色和 margin 的逻辑。类仍负责原菜单行 `onMeasure()`；其他 checkable 菜单继续使用 AppCompat Checkbox/Radio。
- handler 中保存配置、清理目录视图、重载收藏数据和触发受保护来源鉴权的顺序未改。认证取消时配置仍按旧逻辑保留；菜单标题从配置读取，不按授权结果推断。

## 自动化覆盖

`UiFollowupTest` 断言膨胀的真实菜单 XML 中原 ID/order 存在且非 checkable；Inflated rows 无 Checkbox；人工构造的其他 checkable 菜单行仍保留 Checkbox；四种布尔输入映射到四条英文/中文资源。

窄屏长文案、RTL 字符裁切、Activity 重进/进程重启持久化及 MainActivity 保护来源认证成功/取消仍需 Actions 和设备/流程验收。现有收藏刷新 handler 未改变。
