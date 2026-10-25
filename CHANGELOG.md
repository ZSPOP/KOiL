# KOiL 版本更新说明（CHANGELOG）

> 本文件随源码包分发，记录每个版本相对上一个版本的变更，便于跨环境接手开发时快速了解改动。

---

## v1.4.149（versionCode 154）— 2026-10-25

### 🐛 修复
- **省份归一化 bug**：`LocationHelper.normalizeProvince` 后缀移除顺序错误，导致「广西壮族自治区 / 新疆维吾尔自治区 / 宁夏回族自治区」被错误归一化为「广西壮族 / 新疆维吾尔 / 宁夏回族」，与省份列表不匹配、查价与入库异常。现按后缀长度从长到短匹配修复。
- **定位提示硬编码中文**：定位成功提示「已切换到X省」原先在英文/繁体环境下仍显示简体中文，现改为 string resource（`add_locate_switched`），支持 简中 / 繁中 / 英文。

### ✨ 界面与数据展示
- **总览页记录卡新增省份徽标**：多省加油时可按胶囊标签一眼区分记录来源省份。
- **环比 delta 颜色生效**：油耗 / 成本 / 里程的环比变化（↑橙 / ↓绿）此前颜色被渲染层忽略，现按指标好坏着色展示。
- **总览页空态引导**：无加油记录时显示「暂无加油记录 + 前往录入页」的引导提示。
- **设置页 footer 显示版本号**：改为 `KOiL v1.4.149 · com.llpop.koil`。

### 🔐 签名与工程
- **新增正式签名 keystore**：`keystore/koil-release.keystore`（alias=koil，store/key 密码相同），release 构建自动读取 `keystore.properties` 使用正式签名；`keystore.properties` 与 `keystore/` 已加入 .gitignore 不入库。
- **启用 Git 版本管理**：本版本为 Git 首个存档点（commit `3db8f49`），后续每版迭代均会 commit 存档，支持随时回退。

### 🧹 代码质量
- 清理编译警告：`@OptIn(FlowPreview)` 标注、重复非空判断（`OverviewViewModel`）、JSON 备份恢复的类型推断问题（`SettingsViewModel`）。

---

## 历史版本（摘要，供接手参考）

- **v1.4.148**：油价数据同步模块（多数据源核验 + 调价窗口推算）、日期选择器、CSV/JSON 导入导出、多语言支持（简中/繁中/英文）、AnchoredPopup 组件等。
- **v1.4.27 及更早**：加油记录录入/总览/设置三页、油耗统计与趋势图表、异常检测、主题切换、悬浮导航等基础功能。
