# KOiL 油价数据库 · API 数据源记录

> 最后更新：2026-11-10 ｜ 维护者：Doubao Agent（每次改网络层/换源必须同步更新本文件）
> 约定：不把数据源展示在 App 设置页；以本文件 + 代码注释为唯一权威记录。

## 一、当前在用的数据源（6 个：4 个价格/日历源 + 1 个原油降级源 + 1 个汇率源，勿随意增删）

| # | 角色 | 域名 | 接口 | 请求 | 提供字段 | 免key |
|---|---|---|---|---|---|---|
| 1 | **主源（价格曲线）** | youjia.abapi.cn（阿巴匹） | `/api/chart-data.php?province=<省名URL编码>&range=year` | GET | 历史价格曲线：`dates[]` + `p92[]/p95[]/p98[]/p0[]`（元/升），最新价；**`range=year` 实测返回近一年约 220~222 个点**（缺省不带该参数仅近 30 天）；⚠️ **曲线日期滞后真实调价日约 3 天**（9-24 生效在 9-27 才变价） | ✅ |
| 2 | **锚点窗口日（灌库归并基准）** | v2.xxapi.cn（优信 API）→ api.ruseo.cn（如梭 API）同源互备 | `/api/oilPrice`（xxapi）`/api/oilprice`（ruseo） | GET | 全国最新价快照（region/date/gasoline_92 等）；取 `date`（最近窗口生效日）− 1 = 真实窗口日（含搁浅窗口） | ✅ |
| 3 | **节假日源** | cdn.jsdelivr.net（npm 包 `chinese-days`） | `/npm/chinese-days/dist/years/{年}.json` | GET | **当年节假日 + 调休补班日**：`{"holidays":{日期:信息}, "workdays":{日期:信息}, "inLieuDays":{...}}`；holidays=放假日，workdays=调休补班日（周末上班算工作日）；jsDelivr CDN + npm 分发、GitHub Action 自动抓取国务院通知 → 稳定、长期维护、免 key | ✅ |
| 4 | **原油价格** | api.eia.gov（美国能源信息署官方）+ www.eia.gov 的 WPSR 价格表（同一官方数据、另一条发布链路） | ①`v2/petroleum/pri/spt/data/?frequency=daily&facets[series][]=RBRTE...&api_key=<注册Key>`（历史）②`petroleum/prices/data/12_series/D/PET_PRI_SPT_S1_D.json`（最近 6 个交易日，免 Key） | GET | 布伦特原油日度结算价（RBRTE）历史；窗口内均价 vs 基准均价 × 7.33 桶/吨 × 运行时汇率（见 #6）→ 元/吨。**① 与 ② 是同系列、同单位，但 ② 比 ① 快约 3 天**，按日期合并取较新值 | ⚠️ ① 需免费注册 Key（设置页可改；默认内置免费 Key，见 `AppSettings.DEFAULT_EIA_API_KEY`，公开仓库可见，额度耗尽自动降级腾讯）；② 免 Key，解析失败静默退回 ① |
| 5 | **原油降级源** | qt.gtimg.cn（腾讯财经） | `/q=hf_OIL`（需 Referer `https://gu.qq.com/` + UA） | GET | 布伦特实时现价/昨收 → 当日涨跌幅 → 元/吨（粗估） | ✅ |
| 6 | **USD/CNY 汇率** | data-api.ecb.europa.eu（欧洲央行官方 SDMX）→ www.ecb.europa.eu（官方 XML，备）→ latest.currency-api.pages.dev（第三路） | ①`/service/data/EXR/D.USD+CNY.EUR.SP00.A?startPeriod=&endPeriod=&format=csvdata` ②`/stats/eurofxref/eurofxref-daily.xml` ③`/v1/currencies/usd.json` | GET | USD/CNY 月内工作日均值：① 同时返回 `USD/EUR` 与 `CNY/EUR` 两个系列，**逐日相除**得 USD/CNY 再求月均（不能先算月均再相除）；② 只给**最新一个交易日**的 29 个货币，救不了历史月与「当月末点」；③ 聚合源，只取 `usd.cny` 且只认响应体里的 `date`（jsDelivr 通道会落后一天，固定走 Pages 通道） | ✅ 全部免 Key |

> **已排除的汇率源**：Frankfurter（`api.frankfurter.dev`）—— v1 响应头带 `deprecation: @1779103800`（2026-05-18 起标记弃用，至今未关停），其继任版本 v2 **不是 ECB 定盘口径**：ECB 未定盘的日子它会给出当日值、ECB 无数据的周六它也编出值（实测 `v2/rates?base=USD&quotes=CNY` 对 2026-11-10 返回 6.6666、对 2026-11-07 返回 6.6642）。用它会让 `dataThrough` 变成不存在的日期、线上与回测口径不可比，**禁止使用**（不要在后续版本「好心升级」到 v2）。另：CFETS 人民币中间价（`chinamoney.com.cn`）有 openresty WAF，GET / POST / 带 cookie 全部 403，无法自动化接入。


```json
// 2026-10-26 实测匿名请求返回：{"code":4030,"msg":"未携带 API Key，匿名试用次数已用完。请在请求里带上自己的 Key","data":{"limit_daily":5,"used":63,"need_key":1}}
// 预测专接口 /api/oil-price-forecast 同源收费，不接入。

// 主源 youjia.abapi.cn GET /api/chart-data.php?province=广东&range=year
// 返回 { "dates": [...], "p92": [...], "p95": [...], "p98": [...], "p0": [...] }

// 锚点源 xxapi GET https://v2.xxapi.cn/api/oilPrice → { "code":0, "data":[{ "regionName":"广东", "date":"2026-10-16", "n92":8.63, ... }] }
// 锚点源 ruseo GET https://api.ruseo.cn/api/oilprice → { "code":0, "data":{ "list":[{ "region":"广东", "date":"2026-10-16", "gasoline_92":8.63, ... }] } }

// EIA 原油 GET https://api.eia.gov/v2/petroleum/pri/spt/data/?frequency=daily&data[0]=value&facets[series][]=RBRTE&sort[0][column]=period&sort[0][direction]=desc&length=30&api_key=<Key>
// 返回 { "response": { "total": 10003, "data": [{ "period": "2026-10-20", "value": 115.22, ... }] } }

// EIA 增量 GET https://www.eia.gov/petroleum/prices/data/12_series/D/PET_PRI_SPT_S1_D.json（无需 Key）
// 返回 { "release_date": "11/9/2026", "next_release_date": "11/10/2026",
//        "thead": ["11/06/26", ...], "tbody": [{ "section": "Crude Oil", "data": { "RBRTE": ["indent_0", "Brent - Europe", "1987 - 2026", "97.83", ...] } }] }
// 与上一条同 series（RBRTE）、同单位（USD/bbl），但 v2 比它慢约 3 天；只取最近 6 个交易日按日期覆盖
// `release_date` 是「源端发布这批数据的日期」（M/d/yyyy，月日不补零），不是「数据截至日」——
// 实测 11-09 发布、数据只到 11-06。App 把它透到 UI：「截至 11-06 · 源端 11-09 发布」

// 腾讯降级 GET https://qt.gtimg.cn/q=hf_OIL（Header Referer: https://gu.qq.com/）
// 返回 v_hf_OIL="110.45,-1.12,...,111.70,...,2026-10-26,布伦特原油"（index0=现价、index7=昨收）
```

## 二、字段依赖关系（谁提供什么 → 存到哪）

| App 能力 | 数据来源 | 本地存储（DataStore 键） |
|---|---|---|
| 今日油价（四油号） | **abapi 单源** | Room `KOiL_record` |
| 价格历史/涨跌趋势（↑3.9%） | 主源曲线 + 库内窗口对比 | Room（种子+同步滚动存档） |
| **预测幅度（按机制测算：预计上调约 270 元/吨）** | **EIA 原油估算**（窗口内均价 vs 基准均价 × 7.33 桶/吨 × 月度汇率 → 折合元/升）；EIA 失败降级腾讯当日 ΔP；UI 按来源标注「按机制测算」/「单日参考」，并恒定标注「实际可能因调控与测算不同，以发改委公告为准」 | 无（每次现算，不落盘） |
| 最近已过窗口 W1 / 版本号 Vxxx | prev 存档 → next−14 推算 → lastWindowChecked | `KOiL-price-db-prev-next-adjustment` / `lastWindowChecked` |

> **窗口日推算（设计要点）**
> ① 权威窗口日 = 次源锚点（xxapi → ruseo 同源互备）+ chinese-days 节假日推算，从锚点向前回溯当年首调、向后顺推到年末，含历史与未来窗口（实测 25/25 命中），不依赖发改委翻页；
> ② 生效日 = 权威窗口日 + 1（24 时生效 → 次日实际生效），偏差预检在调价当天白天不会误用当晚新价；
> ③ 今日价核验只做默认省：主源 abapi 调价日滞后，且双源当日价天然不一致，全省双源核验会堆出假 pending；其余省由历史灌库覆盖，不一致时只提示采纳；
> ④ 推算成功的窗口日持久化到 DataStore（`KOiL-price-db-adjust-dates`，逗号分隔），重启后复用——窗口日为空时绝不空降级，否则会把"原日期 + 1"当假生效日写入，污染库内 `MAX(effectiveDate)`；降级路径直接用 abapi 原日期/当日值，不加 1；
> ⑤ 库内孤立假生效日由 `purgeIllegalEffectiveDates` 兜底：只删「当年范围内、不属于任何窗口日 + 1、且与上一条同省同油号同价」的孤立记录，往年一律保留（`AUTO_PURGE_ENABLED` 可关）。


> **多源一致性可观测**：① 锚点链命中来源落盘 `KOiL-price-db-last-anchor-source`（xxapi/ruseo/abapi/ndrc/无——跟随实际命中节点，发改委 D4 第 4 级兜底格式变化自动跳过）；② 每次同步成功写组合快照 `KOiL-price-db-last-sources`（"锚点:xxapi · 今日价:abapi"），设置页油价库区域展示「同步来源」一行，数据可信度透明化、兜底链健康可排查；③ 新增窗口日当天提醒（WorkManager 一次性调度窗口日 00:05 + 启动/8h 周期兜底，通知 ID 1003 独立，DataStore `KOiL-price-last-window-day-reminded` 去重）。

**⚠️ 现状可靠性**：调价窗口日 = 本地推算窗口日历（xxapi/ruseo 锚点 + abapi 曲线双重兜底，持久化不空降级）；预测幅度 = EIA 官方原油（免费 Key 内置）+ 腾讯降级；价格 = abapi 单源（历史曲线灌库 + 今日价核验）。**无付费依赖，无单点风险**。

## 三、已调研未接入的候选源（勿重复搜索）

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
