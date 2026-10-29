# KOiL 油价数据库 · API 数据源记录

> 最后更新：2026-10-26 ｜ 维护者：Doubao Agent（每次改网络层/换源必须同步更新本文件）
> 用户要求：不把数据源展示在 App 设置页；以本文件 + 代码注释为唯一权威记录，防止会话切换后遗忘。

## 一、当前在用的数据源（4 个，勿随意增删）

| # | 角色 | 域名 | 接口 | 请求 | 提供字段 | 免key |
|---|---|---|---|---|---|---|
| 1 | **主源（价格曲线）** | youjia.abapi.cn（阿巴匹） | `/api/chart-data.php?province=<省名URL编码>&range=year` | GET | 历史价格曲线：`dates[]` + `p92[]/p95[]/p98[]/p0[]`（元/升），最新价；**`range=year` 实测返回近一年约 220~222 个点**（缺省不带该参数仅近 30 天）；⚠️ **曲线日期滞后真实调价日约 3 天**（9-24 生效在 9-27 才变价） | ✅ |
| 2 | **锚点窗口日（灌库归并基准）** | v2.xxapi.cn（优信 API）→ api.ruseo.cn（如梭 API）同源互备 | `/api/oilPrice`（xxapi）`/api/oilprice`（ruseo） | GET | 全国最新价快照（region/date/gasoline_92 等）；取 `date`（最近窗口生效日）− 1 = 真实窗口日（含搁浅窗口） | ✅ |
| 3 | **节假日源（推算窗口日用，v1.4.113 新增）** | cdn.jsdelivr.net（npm 包 `chinese-days`） | `/npm/chinese-days/dist/years/{年}.json` | GET | **当年节假日 + 调休补班日**：`{"holidays":{日期:信息}, "workdays":{日期:信息}, "inLieuDays":{...}}`；holidays=放假日，workdays=调休补班日（周末上班算工作日）；jsDelivr CDN + npm 分发、GitHub Action 自动抓取国务院通知 → 稳定、长期维护、免 key | ✅ |
| 4 | **原油价格（预测幅度本地估算，v1.4.193 新增）** | api.eia.gov（美国能源信息署官方） | `v2/petroleum/pri/spt/data/?frequency=daily&facets[series][]=RBRTE...&api_key=<注册Key>` | GET | 布伦特原油日度结算价（RBRTE）历史（实测 total 10003 条）；近 10 交易日均值 vs 前 10 → 变化率 → 元/吨 | ⚠️ 需免费注册 Key（设置页可改，默认内置 `GkLpNb...`） |
| 5 | **原油降级源（EIA 失败时，v1.4.193 新增）** | qt.gtimg.cn（腾讯财经） | `/q=hf_OIL`（需 Referer `https://gu.qq.com/` + UA） | GET | 布伦特实时现价/昨收 → 当日涨跌幅 → 元/吨（粗估） | ✅ |

> ⚠️ **apizero（极数本源 v1.apizero.cn）已于 v1.4.194 彻底移除**：2026-10-23 起匿名接口收费限流（4030 未携带 Key），用户拒绝付费。原「双源核验次源 / 预测专接口 / forecast 预测幅度」三项功能均已由本地兜底替代（见下文依赖表）。

### 实测返回样例（2026-10-22，广东；apizero 已移除，仅留档）
```json
// （已移除）次源 v1.apizero.cn POST /api/oil-price  {"province":"广东"} —— 2026-10-23 起收费限流
// 2026-10-26 实测匿名请求返回：{"code":4030,"msg":"未携带 API Key，匿名试用次数已用完。请在请求里带上自己的 Key","data":{"limit_daily":5,"used":63,"need_key":1}}
// 预测专接口 /api/oil-price-forecast 同源收费，App 不再调用。

// 主源 youjia.abapi.cn GET /api/chart-data.php?province=广东&range=year
// 返回 { "dates": [...], "p92": [...], "p95": [...], "p98": [...], "p0": [...] }

// 锚点源 xxapi GET https://v2.xxapi.cn/api/oilPrice → { "code":0, "data":[{ "regionName":"广东", "date":"2026-10-16", "n92":8.63, ... }] }
// 锚点源 ruseo GET https://api.ruseo.cn/api/oilprice → { "code":0, "data":{ "list":[{ "region":"广东", "date":"2026-10-16", "gasoline_92":8.63, ... }] } }

// EIA 原油 GET https://api.eia.gov/v2/petroleum/pri/spt/data/?frequency=daily&data[0]=value&facets[series][]=RBRTE&sort[0][column]=period&sort[0][direction]=desc&length=30&api_key=<Key>
// 返回 { "response": { "total": 10003, "data": [{ "period": "2026-10-20", "value": 115.22, ... }] } }

// 腾讯降级 GET https://qt.gtimg.cn/q=hf_OIL（Header Referer: https://gu.qq.com/）
// 返回 v_hf_OIL="110.45,-1.12,...,111.70,...,2026-10-26,布伦特原油"（index0=现价、index7=昨收）
```

## 二、字段依赖关系（谁提供什么 → 存到哪）

| App 能力 | 数据来源 | 本地存储（DataStore 键） |
|---|---|---|
| 今日油价（四油号） | **abapi 单源**（v1.4.194 起 apizero 移除，双源核验退化为单源自动入库；updateDate==today 才参与） | Room `KOiL_record` |
| 价格历史/涨跌趋势（↑3.9%） | 主源曲线 + 库内窗口对比 | Room（种子+同步滚动存档） |
| **调价窗口日（下一调整日）** | **本地推算窗口日历** `KOiL-price-db-adjust-dates`（xxapi/ruseo 锚点 → abapi 曲线 → calculateAdjustDates 推算；v1.4.192 起数据库卡/首页同一来源） | `KOiL-price-db-next-adjustment`（兼容旧存档） |
| **预测幅度（预计上调270元/吨）** | **EIA 原油估算**（近 10 vs 前 10 交易日均值变化率 × 56.5 元/吨 → 折合元/升）；EIA 失败降级腾讯当日涨跌幅；**v1.5.21 起接口 forecast 存档彻底移除**（键已物理删除），幅度恒走估算，UI 恒标注「估算」 | ~~`KOiL-price-db-forecast`~~（v1.5.21 已删除） |
| 最近已过窗口 W1 / 版本号 Vxxx | prev 存档 → next−14 推算 → lastWindowChecked | `KOiL-price-db-prev-next-adjustment` / `lastWindowChecked` |
| 31省×4油号×N窗口（**生效日精准化，v1.4.113**） | **历史灌库**=主源 abapi 曲线归并「推算窗口日 +1」；**当天入库（调价日）**=默认省 abapi 单源核验：有价即自动入库（apizero 移除后无双源比对/pending）；**生效日=权威窗口日+1**（窗口日 24 时生效 → 次日实际生效交易日）；推算/抓取失败降级用 abapi 原日期 | Room `KOiL_price` |

> **v1.4.113 机制变更（重要）**：① 窗口日由"发改委翻页抓取"改为"次源锚点 + chinese-days 节假日推算"（含历史+未来窗口，实测 25/25 命中）；② 生效日 = 权威窗口日 + 1（24 时生效 → 次日实际生效，偏差预检在调价当天白天加油不会误用当晚新价）；③ **移除旧"每次检测 31 省双源当日价核验"**——主源 abapi 调价日滞后 + 双源当日价天然不一致导致假 pending 堆积；改为**仅默认省调价日双源核验**（不一致才提示采纳），其余省由历史灌库覆盖；④ 新机制首次跑通一次性清空旧遗留假 pending（标记 `KOiL-price-db-legacy-pending-cleared`）。

> **v1.4.114 修复（重要，承接 1.4.113 缺陷）**：① **窗口日持久化**——此前 `cachedAdjustDates` 仅内存 `@Volatile` 缓存，App 重启后失效，且次源 `next_adjust_date` 临时拿不到即返回空列表 → 降级把"原日期+1"（如 10-24→10-25）当假生效日写入，污染库内 `MAX(effectiveDate)`（设置页曾误显"價格維持10-25"）。现推算成功的窗口日落盘 DataStore（`KOiL-price-db-adjust-dates`，逗号分隔），重启后复用，**次源临时失败也绝不空降级**；② **降级不再生成假生效日**——窗口日为空时 `seedProvince`/`verifyProvince` 用 abapi 原日期/当日值（不加 1），杜绝"原日期+1"假记录；③ **一次性清理库内已产生的假生效日**——删除 effectiveDate 不属于任何"窗口日+1"的孤立记录（标记 `KOiL-price-db-adjust-dates-leaned`）。

> **v1.4.194 移除 apizero（重要，承接 1.4.193）**：① 双源核验退化为 **abapi 单源**（verifyProvince 走「单源缺失→用有价源自动入库」分支；不再产生 pending）；② 窗口日历锚点链缩为 4 级：xxapi → ruseo → abapi 曲线 → 本地推算（删预测专接口）；③ 预测幅度恒走 **EIA 原油估算**（UI 标注「估算」）；④ `fetchBothSources` 保留 Pair 签名（secondary 恒失败），调用方零改动；⑤ 同步链路每轮净减 2 个 apizero 请求。

**⚠️ 现状可靠性**：调价窗口日 = 本地推算窗口日历（xxapi/ruseo 锚点 + abapi 曲线双重兜底，持久化不空降级）；预测幅度 = EIA 官方原油（免费 Key 内置）+ 腾讯降级；价格 = abapi 单源（历史曲线灌库 + 今日价核验）。**无付费依赖，无单点风险**。

## 三、已调研未接入的候选源（2026-10-22 调研，勿重复搜索）

### A. 免费免 key（可立即加）
| 源 | 能力 | 适合作 |
|---|---|---|
| 东方财富数据中心调价历史表（data.eastmoney.com/cjsj/oil_default.html，AKShare `energy_oil_hist` 同源） | 历年调价日期+汽柴油涨跌 | 滚动窗口存档官方核对 |

### B. 免费需注册 key
| 源 | 免费额度 | 适合作 |
|---|---|---|
| 聚合数据 juhe.cn `apis.juhe.cn/gnyj/query` | 50 次/天 | 第三价格源，双源不一致时裁决 |
| 极速数据 jisuapi 今日油价 | 注册送额度 | 按省查油价参考 |
| EIA opendata（已接入 #4） | 免费无限期（www.eia.gov/opendata/register.php 填名/邮箱/Private citizen 注册） | 原油/成品油官方历史 |

### C. 付费
| 源 | 说明 |
|---|---|
| 极数本源 apizero（oil-price / oil-price-forecast） | **v1.4.194 已移除**：2026-10-23 起匿名限流收费（4030，需 Key 付费档） |
| 万维易源 ShowAPI（138-46 油价 / 138-49 行情含 `next_change_time`） | 调价窗口核对 |
| 起零数据 istero（免费 20 次/天，付费 2000 次/天） | 31 省核验需付费档 |

### D. 官方权威（非 API，HTML 可爬）
| 源 | 说明 |
|---|---|
| 国家发改委 ndrc.gov.cn / 商务部 oilsyggs.mofcom.gov.cn | 每次调价官方通知+各省最高零售价附表，适合调价日低频核对 |

## 四、维护纪律
1. 换源/加源：先在本文档登记（域名、接口、字段、实测样例），再改 `data/oil/OilPriceNetwork.kt`
2. 改接口格式：同步更新 `YOUJIA_GRADE_MAP` / 日期解析（OilPriceDateUtil）
3. 预测/窗口解析：优先本地推算（窗口日历）+ EIA 估算（预测幅度），避免中文文本正则
4. 本文件与 `OilPriceNetwork.kt` 顶部注释互为索引，改动时必须两处同步
