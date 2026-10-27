# KOiL 工程进度记录

> 用途：跨会话/跨轮次状态存档，每次迭代交付后更新。
> 版本提交见 git log；变更明细见 CHANGELOG.md。

## 当前版本：v1.5.12（versionCode 212）— 2026-10-27 已交付

- commit：`8569999`（基于 v1.5.11 `488d739`）
- APK：https://aka.doubaocdn.com/s/K6vvC7ld9Q （KOiL-v1.5.12-release.apk）
- 源码 zip（**完整工程源码**）：https://aka.doubaocdn.com/s/DYHRhQ58Wg （KOiL-src-v1512.zip）
- 签名：release 证书 SHA-256 恒定 `cc962f1e...`，编译通过、verifier 通过
- ⚠️ 2026-10-27 修正：旧 zip 只含 .git+文档不是工程源码；新 zip 含完整工程（见下「源码包打包规范」）

### v1.5.12 变更（补录弹窗 UI 对齐首页录入卡，仅補錄卡片）
1. 里程区间提示移至日期行与本次里程行之间
2. 应付金额只读自动计算（= 加油量 × 参考单价，对齐首页 updatePayable）
3. 里程/油量/实付字段红色错误态（对齐首页 FormField：#FF3B30 边框/图标/标签）
   - 里程：里程链冲突或同日重复 → 红
   - 油量：超油箱容量 → 红
   - 实付：偏差超阈值或实付>应付 → 红
4. 字段图标对齐首页：里程=Speed / 油量=WaterDrop / 应付=Money / 实付=Money
- 编辑弹窗字段同步适配新组件签名（仅补图标+键盘类型，行为不变）

### 待用户验收（v1.5.12）
- 里程范围提示位置（日期行下、里程行上）
- 应付金额自动计算 + 只读
- 字段红色错误态是否与首页一致
- 字段图标

## 上一版本：v1.5.11（versionCode 211）— 2026-10-27 已交付

- commit：`488d739`（基于 v1.5.10 `b7d0098`）
- APK：https://aka.doubaocdn.com/s/3vov4412D9 （KOiL-v1.5.11-release.apk）
- 源码 zip：https://aka.doubaocdn.com/s/3uycL13hz6 （KOiL-src-v1511.zip，五件套 verifier matched 5/5）

### v1.5.11 变更（仅補錄卡片，首页录入不动）
用户逐条确认的补录页校验规则：
1. 日期 → 可编辑 · 未来禁选 · 早于第一条（或同日里程更小）拦截
2. 同日同里程重复 → 拦截
3. 同日相似记录 → 无（移除 ±2L/±10元 提醒）
4. 油量下限 → >0（移除 <1L 拦截）
5. 油量上限 → 无（油箱容量兜底，移除 >200L）
6. 金额上限 → 无（移除 >1500）
7. 实付 vs 应付 → 实付>应付 一律拦截
8. 优惠幅度 → B 兜底：偏差% > 设置页阈值（可配）
9. 价格偏差 → A：实付单价 vs 当日参考价，超阈值（可配）
10. 油箱容量 → 加油量 > 油箱容量 拦截
11. 保存条件 → isReady 全部校验通过才可保存
12. 里程范围提示 → 选日期后下方提示该区间可输入里程（prev+1 ~ next-1，无界侧省略，仅提示不阻断）

### 待用户验收
- 装包验证：里程范围提示是否出现、保存按钮置灰是否跟随新规则

## 源码包打包规范（2026-10-27 修正，务必遵守）
源码 zip 必须是**可直接打开构建的完整 Android 工程**，不是只有 .git+文档。
- 打包内容：`git ls-files`（排除 `.web-ref/`）+ 手动补 `keystore/koil-release.keystore` 与 `keystore.properties`（git 忽略但构建必需）+ 整个 `.git/`
- 必须包含：settings.gradle.kts / build.gradle.kts / gradlew / gradlew.bat / gradle/wrapper/gradle-wrapper.jar / app/（src + build.gradle.kts）/ keystore
- 排除：app/build/、.gradle/、.kotlin/、.tmp-tool-results/、.web-ref/、*.apk、*.zip、local.properties
- 校验：verifier archive zip-check-entries 断言上述关键工程文件 + 解压回读确认 app/src 完整

## 常驻待办（对外发布前必做）
- 清空 `KOiL-price-db-forecast` 残留旧值 + 标记 `KOiL-price-db-forecast-194-cleared`
  （apizero 时代最后一次 forecast；当前仅作者自用，故 194 未实施）

## 历史版本速览
- v1.5.10：补录卡逻辑复刻 Web（日期边界/金额ratio/里程链/重复校验+保存置灰）
- v1.5.9：历史按月分批加载 + 删除确认弹窗
- v1.5.8：移除卡片内长方形（涟漪 + 柔光色块根因）
- v1.5.7：长按选中态去框线
- v1.5.6：长按选中态柔和化
- v1.5.5：全屏遮罩收起
- v1.5.4：长按交互 + 補錄按钮
- v1.5.3：总览页修复 + 排版
- v1.5.2：查看颜色修正
- v1.5.1：查看颜色 + 数据源开关
- v1.5.0：八项修复交付
- v1.4.x：历史版本（详见 CHANGELOG）
