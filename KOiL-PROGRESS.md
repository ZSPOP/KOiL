# KOiL 工程进度记录

> 用途：跨会话/跨轮次状态存档，每次迭代交付后更新。
> 版本提交见 git log；变更明细见 CHANGELOG.md。

## 当前版本：v1.5.33（versionCode 233）— 2026-10-29 已交付

- 定位：KOiL 品牌 logo 转矢量（Unbounded 字体彻底移除）+ 同步来源移油价库区块最底（EIA 后）+ 省份箭头换设置页同款线条风
- commit：`a765cb5`（基于 v1.5.32，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.33-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1533.zip`
- 变更明细：见 CHANGELOG v1.5.33
- 待用户验收：① 设置页底部 KOiL 与之前字体渲染形状一致（58.52×20dp 矢量，颜色跟随主题）；② 油价库同步来源在 EIA 下方最底、分隔线同款；③ 首页省份按钮箭头为设置页同款线条箭头
- 说明：字体文件（unbounded_variable.ttf/unbounded.xml）已删除，工程无任何字体引用；logo 矢量源 KOiL-logo-vector.svg + 预览对比图在工程根

### v1.5.33 变更速览
- 🎨 KOiL logo → `res/drawable/koil_logo.xml`（fontTools 提字形轮廓，Bold wght700 形状），设置页 Image+tint 显示，字体文件/引用代码全删
- ⚙️ 同步来源从按钮行下移至油价库区块最底（EIA API Key 之后），同款 0.5dp 分隔线
- 🔽 省份箭头 ArrowDropDown(16dp) → Miuix DropdownArrowEndAction（设置页同款线条风）

## 历史版本：v1.5.32（versionCode 232）— 2026-10-29 已交付

- 定位：异常弹窗三修（叠加/负号/标题+背景）+ 油价库两行居中 + 同步来源移最底 + 全局点击空白清焦
- commit：`df042e7`（基于 v1.5.31，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.32-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1532.zip`
- 变更明细：见 CHANGELOG v1.5.32
- 待用户验收：① 异常弹窗多条记录纵向排列、有标题、偏低显示正数偏差；② 油价库调整日/下个窗口两行与立即更新按钮水平居中、同步来源在区块最底；③ 点文本框后点页面空白 → 光标消失取消选中
- 说明：异常弹窗叠加根因=滚动容器是 Box（多子项重叠），已改 Column 排列；弹窗背景改全不透明

### v1.5.32 变更速览
- 📊 异常弹窗：Column 排列修复叠加 + 偏差取绝对值 + 自绘标题 + 背景不透明
- ⚙️ 同步来源移区块最底，左列两行与按钮垂直居中
- ⌨️ KOiLScreenScaffold / KOiLGlassDialog 统一点击空白 clearFocus

## 历史版本：v1.5.31（versionCode 231）— 2026-10-29 已交付

- 定位：改名（KOiL/数据总览/首页/数据+图标）+ 异常卡点击明细弹窗 + 应付待计算占位 + 省份箭头 16dp + 同步来源行间隔线 12sp + 数字显示规范化
- commit：`fe83019`（基于 v1.5.30，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.31-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1531.zip`
- 变更明细：见 CHANGELOG v1.5.31
- 待用户验收：① 首页顶栏=KOiL、导航=首页/数据/设置 + 新图标；总览页=数据总览；② 有异常时点异常卡弹明细；③ 录入卡应付算不出时显示待计算；④ 省份下拉箭头变大；⑤ 设置页同步来源移到下个窗口行下方+间隔线+12sp；⑥ 各处数字：金额 2 位、油价/油耗整数不带小数点、百分比 1 位
- 说明：数字规范化覆盖 CSV 导出（7.00→7 不影响导入回环）；繁体导航=首頁/數據/設定

### v1.5.31 变更速览
- 🏷 三语改名 + 导航图标（Home/Storage）
- 📊 异常卡点击弹窗（滚动列表：日期/油号/油耗/偏高偏低/偏差%）
- ✍️ 录入卡应付「待计算」占位（FormField 加 placeholder，补录卡同款）
- 🔽 省份下拉三角 10sp 字符 → 16dp 矢量箭头
- ➖ 同步来源行移回窗口行下 + 0.5dp 间隔线 + 12sp
- 🔢 NumberFormat 工具：金额 2 位 / 度量剪尾零 / 百分比 1 位，全局统一

## 历史版本：v1.5.30（versionCode 230）— 2026-10-29 已交付

- 定位：通知设置跳转加固（NEW_TASK + resolveActivity 预检 + 应用详情 fallback，不再静默吞异常）+ 提示行 offset 收紧
- commit：`7ecfb01`（基于 v1.5.29，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.30-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1530.zip`
- 变更明细：见 CHANGELOG v1.5.30
- 待用户验收：① 拒绝过通知权限后，点推送开关/提示行 → 应跳转到系统通知设置（HyperOS 若拒绝该 action 则落应用信息页）；
  开启后返回 App 开关自动打开；② 红色提示行紧贴副标题下方
- 说明：v1.5.29 跳转失败的根因是 runCatching 吞异常（ACTION_APP_NOTIFICATION_SETTINGS 无处理者或缺 NEW_TASK 都会静默失败），本轮已三重加固

### v1.5.30 变更速览
- 🔧 openNotificationSettings()：NEW_TASK + resolveActivity 预检 + ACTION_APPLICATION_DETAILS_SETTINGS fallback（开关/提示行共用）
- 🖌 提示行：vertical padding 移除 + offset(-3.dp) 上移贴近副标题（通知/定位两处）

## 历史版本：v1.5.29（versionCode 229）— 2026-10-29 已交付

- 定位：通知开关跳系统设置（治 HyperOS 拒绝后点开无反应）+ 提示行间距 + 同步来源移底部 + 异常油耗默认 30→20
- commit：`8aa5a63`（基于 v1.5.28，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.29-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1529.zip`
- 变更明细：见 CHANGELOG v1.5.29
- 待用户验收：① 拒绝过通知权限后，设置页点推送开关 → 直接跳到系统通知设置；开启后返回 App 开关自动打开；
  ② 红色提示行贴近副标题；③ 同步来源一行在油价库区块底部；④ 新装（或未改过设置）用户异常油耗默认 20%
- 说明：定位权限联动（v1.5.28 已做）行为正常未改动

### v1.5.29 变更速览
- 🔧 通知开关：未授权点开 → 跳系统通知设置（不再赌系统弹框）；pendingOpenPush 意图标记 + ON_RESUME 自动补开
- 🖌 提示行 vertical 4→2dp；同步来源从窗口链条第 3 行移到区块底部脚注
- ⚙️ DEFAULT_ABNORMAL_THRESHOLD 30.0 → 20.0

## 历史版本：v1.5.28（versionCode 228）— 2026-10-29 已交付

- 定位：通知弹窗升级（方案A：HIGH通道+BigTextStyle）+ 通知/定位开关权限联动（拒绝→自动关、点开→再请求）
- commit：`1eb63c3`（基于 v1.5.27，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.28-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1528.zip`
- 变更明细：见 CHANGELOG v1.5.28
- 待用户验收：① 通知弹横幅+完整正文（换新通道 HIGH，首次触发时看是否弹出 heads-up 横幅）；
  ② 设置页：拒绝过权限时开关显示关 + 红色提示行；点开再次弹权限框，同意才开
- 说明：① 换新通道后旧通知历史不保留（可接受）；② 若之前在系统设置关过通知，点提示行跳系统设置开启

### v1.5.28 变更速览
- 🔔 通知弹窗：新通道 oil_price_adjust_high（IMPORTANCE_HIGH）+ BigTextStyle 完整正文 + PRIORITY_HIGH；旧通道删除清理
- 🔐 权限联动：通知开关（MainActivity 启动被拒写关 + 设置页未授权恒关 + 点开先请求 + 提示行跳系统设置）；
  定位开关（同逻辑，同意后自动定位）；返回前台刷新权限状态；KOiLColors.Warning 提示色；三语 2 新 key

## 历史版本：v1.5.27（versionCode 227）— 2026-10-29 已交付

- 定位：窗口日当天"今晚24点生效"提醒（建议#2）+ 设置页多源一致性展示（建议#3）
- commit：`02a0ce5`（基于 v1.5.26，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.27-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1527.zip`
- 变更明细：见 CHANGELOG v1.5.27
- 待用户验收：① 窗口日当天（11-12）0:05 收到"今晚24时窗口到期"通知；当天启动 App 也会即时提醒（幂等不重复）；
  ② 设置页油价库区域新增「同步来源：锚点:xxapi · 今日价:abapi」一行（数据可信度透明化）
- 说明：通知需系统通知权限（原有）；API_SOURCES.md 已同步

### v1.5.27 变更速览
- 🔔 窗口日当天提醒：notifyPriceChanges 第 3 段 + 新 OilPriceWindowDayWorker（00:05 一次性调度）
  + 启动链/8h 周期 Worker 兜底；独立通知 ID 1003；DataStore 键 KOiL-price-last-window-day-reminded
- 🔍 多源一致性：锚点链返回命中来源（xxapi/ruseo/abapi/ndrc）落盘 + 同步来源组合快照
  KOiL-price-db-last-sources；设置页新增「同步来源」展示行（三语）

## 历史版本：v1.5.26（versionCode 226）— 2026-10-29 已交付

- 定位：总览页六指标居中+列间距；油价库补灌自动重试（含"失败不再锁 8h"）
- commit：`080ffe5`（基于 v1.5.25，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.25-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1525.zip`
- 变更明细：见 CHANGELOG v1.5.25
- 待用户验收：总览页滚动加油历史 → 月份标题贴顶栏下**不再闪退**；吸顶背景半透明 surface（观感接近顶栏毛玻璃）；
  其余 v1.5.24 改动（调价提醒/月份字号/六指标 3×2/缺口自动刷新）继续验收

### v1.5.25 变更速览
- 🐛 吸顶闪退修复：header 在 backdrop 录制子树内，textureBlur 采样含自身 → 递归崩溃；
  改半透明 surface 背景（alpha 0.92），移除 blur imports；贴顶/检测/无色块逻辑不变

## 历史版本：v1.5.24（versionCode 224）— 2026-10-29 已交付

- 定位：UI 三处（调价窗口提醒徽章/文案、月份标题吸顶+字号、六指标 3×2）+ 设置页油价库缺口实时刷新
- commit：`6cca520`（基于 v1.5.23，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.24-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1524.zip`
- 变更明细：见 CHANGELOG v1.5.24
- 待用户验收：① 首页调价提醒：「建议提前加油」在窗口日行右侧、`预计(估算)上调170元/吨(截至10-27)，折合…`；
  ② 总览页月份标题贴顶栏下（毛玻璃同顶栏、未贴顶无色块）；③ 月份文字 16sp、笔数同字号右对齐；
  ④ 六指标 3×2、无「实付较应付」条；⑤ 设置页缺口自动刷新（补灌完成自动归零 + 「后台补灌中」提示）

### v1.5.24 变更速览
- 🖌 UI1 调价提醒：建议徽章移窗口日行右侧 / (估算)前置「预计」后 / 截至后加逗号（新增 koil_add_forecast_sep）
- 🖌 UI2 月份吸顶：layoutInfo 检测贴顶栏区域 → offset 贴顶栏底部（无跳变）+ 顶栏同款毛玻璃（同 LayerBackdrop/textureBlur 参数）；未吸顶无背景色块
- 🖌 UI3 月份标题：14sp→16sp；「N筆」11sp→16sp 右对齐
- 🖌 UI4 六指标：2列×3行 → 3列×2行；删除实付较应付对比条（死键 koil_ov_vs_actual_saved/over 三语清理）
- 🔄 设置页缺口实时刷新：observeCount 监听自动重算（根因=进页面一次性快照不随后台补灌刷新）；新增「后台补灌中」提示（koil_pdb_seeding 三语）

## 历史版本：v1.5.23（versionCode 223）— 2026-10-29 已交付

- 定位：深度检测清单批量执行 + 估值基准修复 —— E5 基准锚定（搁浅不重置）+ E6 数据截至标注 + 中危三修（D1/A1/D2）+ 低危八修（A2/A3/A4/B1/C1/C3/D3/E4）+ 死键清理接线（D7/B4）+ 发改委锚点接入（D4）
- commit：`0ee9240`（基于 v1.5.22，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.23-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1523.zip`
- 变更明细：见 CHANGELOG v1.5.23
- 待用户验收：① 首页预计涨跌方向（10-29 窗口应为**上调**，与机构预测一致）且标注「数据截至 10-27」；
  ② 明细页当年累计涨跌含年初调价；③ 删光记录后重启种子不再复活；④ 总览范围选择重启后保持；
  ⑤ 搁浅窗口次日版本号不再滚动成检查日；⑥ 设置页版本号=真实窗口日

### v1.5.23 变更速览
- 📈 E5：估算基准改为「最近非搁浅调价生效日往前 10 个 EIA 交易日」（10-15 搁浅不重置基准）；
  E6：首页预测标注「截至 MM-dd」（EIA T+1 数据末段缺失透明化）
- 🐛 D1 明细页跨年涨跌漏算 / A1 种子一次性标记防复活 / D2 版本号限定窗口日次日落账
- 🐛 A2 定位 CountDownLatch 2s / A3 IP 节流 30min / A4 缓存时效 30min / B1 Locale.ROOT 39 处
  / C1 CSV 引号解析 / C3 JSON NaN 防御 / D3 通知独立 ID / E4 日期正则锚定
- 🧹 D7 删 lastGrade 死键 / B4 总览范围持久化
- 🏛️ D4 发改委公告锚点（第 4 级兜底，主链 xxapi→ruseo→abapi 不变；格式变化自动跳过）

## 历史版本：v1.5.22（versionCode 222）— 2026-10-29 已交付

- 定位：正确性小修批量 —— #3 三语翻译补漏 + 通知逻辑四修（搁浅误通知/立即更新通知/点击跳转/失败不丢）+ 估值链路五修（腾讯标注/窗口对齐/建议去历史反推/周期刷新/柴油密度）
- commit：`cdd7791`（基于 v1.5.21，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.22-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1522.zip`
- 变更明细：见 CHANGELOG v1.5.22
- 待用户验收：① 搁浅窗口不再收到"调价至同价"通知；② 手动立即更新后有通知；③ 点通知可进 App；
  ④ 首页预计涨跌标注「估算」（EIA）/「单日参考」（腾讯）；⑤ 选 0# 柴油时折合元/升更准

### v1.5.22 变更速览
- 🌐 #3：`koil_pdb_adjust_stranded_short` 补 en/zh-rTW 翻译（消 MissingTranslation Error）
- 🔔 #4~#7：通知抽公共方法（syncer.notifyPriceChanges，Worker 与立即更新共用）；搁浅只锚定不推送；
  通知加 PendingIntent 跳转；doWork 通知独立于同步结果
- 📈 #8~#12：EIA 窗口日历对齐切分（本窗口 vs 上窗口）；腾讯降级 UI 标「单日参考」；
  建议文案删 avgTrend 反推；估值 8h 周期刷新；折合元/升按油号密度（汽油 1316 / 柴油 1205）

## 历史版本：v1.5.21（versionCode 221）— 2026-10-29 已交付

- 定位：数据正确性专项 —— ① 结清常驻待办（KOiL-price-db-forecast 彻底删除）；② 窗口日 24 点生效边界（B1~B8）
- commit：`c5544e7`（基于 v1.5.20 `2a6b593`，不涉及 DB schema，覆盖升级安全）
- APK：`KOiL-v1.5.21-release.apk`（release 正式签名，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1521.zip`
- 变更明细：见 CHANGELOG v1.5.21
- 待用户验收：① 数据库卡不再当天显示"V今日 擱淺"；② 调价次数恢复 20（今天窗口不计入）；③ 记录页无 10-30 假搁浅；④ 首页/录入页/通知 24 点前均显示旧价

### v1.5.21 变更速览
- 🗑️ #1 forecast 彻底删除：DataStore 键 `KOiL-price-db-forecast` 物理删除（一次性清理 + 标记 `KOiL-price-db-forecast-cleared`）；
  删 AppSettings/Syncer/Models/AddViewModel 全部 forecast 引用与 parseForecast 分支；预测幅度恒走 EIA/腾讯估算（UI「估算」标注恒显）
- 🕐 #2 窗口日 24 点生效边界（B1~B8）：补搁浅/补灌阈值/缺口检测 `<=today`→`<today`（B1/B2）；
  核验搁浅窗口日保护（B3）；调价次数口径同改（B4）；verifyProvince 降级不写 today（B5）；写入闸拦 `effectiveDate>today`（B8）；
  启动一次性删未来生效日记录（B6，标记 `KOiL-price-db-window-day-cleared`）；四宫格/当前价/调价通知改 ≤今天 查询（B7）

## 历史版本：v1.5.20（versionCode 220）— 2026-10-29 已交付

- commit：`2a6b593`（基于 v1.5.16 `65eb51f`，合并原计划 v1.5.17/v1.5.18，不涉及 DB schema 与新功能）
- APK：`KOiL-v1.5.20-release.apk`（62MB，release 正式签名；apksigner 校验通过，证书 SHA-256 恒定 `cc962f1e...`）
- 源码 zip（完整工程源码 + .git 历史）：`KOiL-src-v1520.zip`（31MB，含 settings/build/gradlew/wrapper/app/src/keystore/schemas）
- 构建环境：Gradle 9.3.1 + AGP 9.1.0 + JDK 21（Temurin，~/jdk/jdk-21.0.13+9）+ SDK /home/user/android-sdk；构建耗时约 4 分钟
- 变更明细：见 CHANGELOG v1.5.20 —— 导入导出正确性（#3 #4）、导入/重算性能（#6 #7）、历史列表 LazyColumn 重构（#8 #9 #10 #11）
- 待用户验收：CSV 导入/导出正确性（状态列真实派生、里程小数保留、文件内重复/里程倒挂拦截）、大数据量下历史列表滚动流畅度、返回顶部按钮

### v1.5.20 变更速览（详见 CHANGELOG）
- #4 导出油耗状态不再写死"正常"：新建 domain/logic/DerivedCalculator.kt，总览与导出共用派生口径（正常/偏高/偏低/短距/–）
- #3 导出里程不丢小数（fmtMileage：整数显整数、非整数保留）；导入检测文件内自身重复/里程链冲突；去重键改 TreeSet (日期,里程) 精确比较
- #7 导入预检 O(n²)→O(n log n)（TreeSet.lower/higher 二分）；#6 observeAll debounce 120ms + 排序/派生计算切 Default、CSV 读写切 IO、确认导入改 insertAllIgnore 单事务批量
- #8 历史列表 Column+verticalScroll → LazyColumn + stickyHeader 月份吸顶；#9 groupBy/buildPrevMap remember 缓存；#10 items(key=记录id)+稳定回调（顺手删 RecordCard 死参数）；#11 返回顶部显隐改 snapshotFlow distinctUntilChanged

## 历史版本：v1.5.16（versionCode 216）— 2026-10-27 已交付

- commit：`65eb51f`（基于 v1.5.15 `4882e1d`）
- 定位：深度压力测试 18 项问题的第一批稳定性 hotfix（低风险、可独立回退）
- 变更：#2 应付 0 元修复(含存量回填)、#1/#5 保存防重入+失败红条、#16 编辑应付回退实付、#15 CSV 里程严格递增、#13 输入框多小数点过滤、#17 补录日期无限回退、#18 定位省不重复写、#12 移除加油提醒里程死功能、#14 合并双崩溃捕获

## 当前版本：v1.5.15（versionCode 215）— 2026-10-27 已交付

- commit：`877a3dc`（基于 v1.5.14 `80d7104`）
- APK：https://aka.doubaocdn.com/s/Ei0ihYGa1Y （KOiL-v1.5.15-release.apk）
- 源码 zip（完整工程源码）：https://aka.doubaocdn.com/s/iHJXuxGAgX （KOiL-src-v1515.zip，verifier 关键工程文件 11/11）
- 签名：release 证书 SHA-256 恒定 `cc962f1e...`，编译通过、verifier 通过

### v1.5.15 变更（日期可选择补下拉箭头）
- KOiLDateField：onDateSelected != null（补录卡日期可选）时右侧显示 DropdownArrowEndAction 下拉箭头（对齐油号/省份字段）；只读（编辑卡/首页）不显示

## 当前版本：v1.5.14（versionCode 214）— 2026-10-27 已交付

- commit：`88bf228`（基于 v1.5.13 `255dc22`）
- APK：https://aka.doubaocdn.com/s/UnpDYU6p9B （KOiL-v1.5.14-release.apk）
- 源码 zip（完整工程源码）：https://aka.doubaocdn.com/s/BEJzArLU0c （KOiL-src-v1514.zip，verifier 关键工程文件 10/10）
- 签名：release 证书 SHA-256 恒定 `cc962f1e...`，编译通过、verifier 通过

### v1.5.14 变更（文案区分 + 应付待计算占位）
1. 编辑卡校验通过 →「校驗通過，可以儲存」；补录卡保持「可以補錄」（BackfillPrecheckHint 加 readyText）
2. 应付未计算（油量/参考价缺失）→ 显示「待計算」：EditField Box 叠加占位 + ViewModel 清空逻辑；precheck 后参考价就绪自动算出
3. 编辑卡打开瞬间应付先"待计算"，不再显示旧记录值

## 当前版本：v1.5.13（versionCode 213）— 2026-10-27 已交付

- commit：`84cc5fd`（基于 v1.5.12 `062ae1b`）
- APK：https://aka.doubaocdn.com/s/kuOQbUtog3 （KOiL-v1.5.13-release.apk）
- 源码 zip（完整工程源码）：https://aka.doubaocdn.com/s/zhTMMtrh1d （KOiL-src-v1513.zip，verifier 关键工程文件 10/10）
- 签名：release 证书 SHA-256 恒定 `cc962f1e...`，编译通过、verifier 通过

### v1.5.13 变更（补录保存按钮变蓝 + 编辑卡 1:1 复刻补录卡）
1. 补录卡保存按钮：全部校验通过 → 蓝色（KOiL 主色）可保存；否则灰色置灰（对齐首页 addRecord）
2. 编辑卡 1:1 复刻补录卡，唯一差异日期只读（KOiLDateField onDateSelected=null）：
   - 打开即显示该日期里程区间提示（prev+1 ~ next-1），里程可修改
   - 全量校验：早于第一条/同日同里程重复/双向里程链/偏差A+B/实付>应付/油箱容量，**排除编辑记录自身 id**（DAO 新增 getEarliestExcluding / getSameDayRecordsExcluding）
   - 红色错误态（里程/油量/实付）+ 校验提示条 + 应付只读自动算（油量×参考价）
   - 保存按钮 editReady 全通过 → 蓝色；否则置灰

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
- ~~清空 `KOiL-price-db-forecast` 残留旧值~~ → **v1.5.21 已结清**：键物理删除（一次性清理 + 标记 `KOiL-price-db-forecast-cleared`）
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
