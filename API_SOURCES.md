# KOiL 油价数据库 · API 数据源记录

> 最后更新：2026-10-24 ｜ 维护者：Doubao Agent（每次改网络层/换源必须同步更新本文件）
> 用户要求：不把数据源展示在 App 设置页；以本文件 + 代码注释为唯一权威记录，防止会话切换后遗忘。

## 一、当前在用的数据源（3 个，勿随意增删）

| # | 角色 | 域名 | 接口 | 请求 | 提供字段 | 免key |
|---|---|---|---|---|---|---|
| 1 | **主源（价格曲线）** | youjia.abapi.cn（阿巴匹） | `/api/chart-data.php?province=<省名URL编码>&range=year` | GET | 历史价格曲线：`dates[]` + `p92[]/p95[]/p98[]/p0[]`（元/升），最新价；**`range=year` 实测返回近一年约 220~222 个点**（缺省不带该参数仅近 30 天）；⚠️ **曲线日期滞后真实调价日约 3 天**（9-24 生效在 9-27 才变价） | ✅ |
| 2 | **次源（窗口+预测）** | v1.apizero.cn（极数本源 ApiZero） | `/api/oil-price` | POST `{"province":"广东"}` | 今日价（92/95/98/0号）、`update_date`、`next_adjustment`（"下次油价10月29日24时调整"）、`forecast` | ✅ |
| 3 | **预测专接口（窗口日精确化）** | v1.apizero.cn（同次源） | `/api/oil-price-forecast` | POST `{"province":"广东"}` | `next_adjust_date`（yyyy-MM-dd 精确窗口日）、`days_remaining`、`prediction.direction / confidence`（备用）；**预测幅度不用它**（口径偏激 1215 vs 聚合 270） | ✅ |
| 4 | **权威窗口日（灌库归并基准，v1.4.113 改推算法）** | 由「次源锚点 + 节假日」推算（见 #3 与 #5） | — | **当年全年调价窗口日（含历史+未来）**：锚点=次源 `next_adjust_date`（实测 10-29 准确），节假日=chinese-days；按《石油价格管理办法》每 10 个工作日一个窗口、遇节假日顺延、调休补班算工作日，从锚点向前倒推回溯当年首调、向后顺推至年末；**实测 25/25 命中发改委窗口日**（1-6 至 12-24）；推算失败降级用 abapi 原日期 | ✅ |
| 5 | **节假日源（推算窗口日用，v1.4.113 新增）** | cdn.jsdelivr.net（npm 包 `chinese-days`） | `/npm/chinese-days/dist/years/{年}.json` | GET | **当年节假日 + 调休补班日**：`{"holidays":{日期:信息}, "workdays":{日期:信息}, "inLieuDays":{...}}`；holidays=放假日，workdays=调休补班日（周末上班算工作日）；jsDelivr CDN + npm 分发、GitHub Action 自动抓取国务院通知 → 稳定、长期维护、免 key | ✅ |

### 实测返回样例（2026-10-22，广东）
```json
// 次源 v1.apizero.cn POST /api/oil-price  {"province":"广东"}
{
  "code": 0, "msg": "cached",
  "data": {
    "province": "广东", "update_date": "2026-10-22",
    "prices": [
      {"name": "92号汽油", "type": "gasoline_92", "price": 8.63, "unit": "元/升"},
      {"name": "95号汽油", "type": "gasoline_95", "price": 9.35, "unit": "元/升"},
      {"name": "98号汽油", "type": "gasoline_98", "price": 11.35, "unit": "元/升"},
      {"name": "0号柴油",  "type": "diesel_0",    "price": 8.31, "unit": "元/升"}
    ],
    "next_adjustment": "下次油价10月29日24时调整",
    "forecast": "预计上调油价270元/吨"
  },
  "tips": "极数本源 · https://apizero.cn"
}

// 预测专接口 v1.apizero.cn POST /api/oil-price-forecast  {"province":"广东"}（QPS 限 1 次/秒）
{
  "code": 0, "msg": "成功",
  "data": {
    "crude_oil": {"wti": 99.627, "brent": 111.773, "wti_change": 2.477, "brent_change": 2.953, "source": "sina_finance"},
    "next_adjust_date": "2026-10-29",          // 精确窗口日（与聚合接口一致 10-29）
    "days_remaining": 7,
    "prediction": {
      "direction": "上涨", "direction_emoji": "📈",
      "estimated_change_per_ton": 1215,        // ⚠️ 口径偏激，App 不用此值
      "estimated_change_per_liter": 0.9,
      "confidence": "低",
      "analysis": "……"
    }
  },
  "tips": "极数本源 · https://apizero.cn"
}
```

## 二、字段依赖关系（谁提供什么 → 存到哪）

| App 能力 | 数据来源 | 本地存储（DataStore 键） |
|---|---|---|
| 今日油价（四油号） | 双源核验（主源优先，次源对照；updateDate==today 才参与） | Room `KOiL_record` |
| 价格历史/涨跌趋势（↑3.9%） | 主源曲线 + 库内窗口对比 | Room（种子+同步滚动存档） |
| **调价窗口日（下一调整日）** | **仅次源** `next_adjustment` | `KOiL-price-db-next-adjustment` |
| **预测幅度（预计上调270元/吨）** | **仅次源** `forecast` | `KOiL-price-db-forecast` |
| 最近已过窗口 W1 / 版本号 Vxxx | prev 存档 → next−14 推算 → lastWindowChecked | `KOiL-price-db-prev-next-adjustment` / `lastWindowChecked` |
| 31省×4油号×N窗口（**生效日精准化，v1.4.113**） | **历史灌库**=主源 abapi 曲线归并「节假日推算窗口日 +1」；**当天入库（调价日）**=默认省双源核验：一致→自动入库 / 不一致→入 pending 等待采纳（一直挂着）/ 单源失效→用另一源；**生效日=权威窗口日+1**（窗口日 24 时生效 → 次日实际生效交易日）；推算/抓取失败降级用 abapi 原日期 | Room `KOiL_price` |

> **v1.4.113 机制变更（重要）**：① 窗口日由"发改委翻页抓取"改为"次源锚点 + chinese-days 节假日推算"（含历史+未来窗口，实测 25/25 命中）；② 生效日 = 权威窗口日 + 1（24 时生效 → 次日实际生效，偏差预检在调价当天白天加油不会误用当晚新价）；③ **移除旧"每次检测 31 省双源当日价核验"**——主源 abapi 调价日滞后 + 双源当日价天然不一致导致假 pending 堆积；改为**仅默认省调价日双源核验**（不一致才提示采纳），其余省由历史灌库覆盖；④ 新机制首次跑通一次性清空旧遗留假 pending（标记 `KOiL-price-db-legacy-pending-cleared`）。

> **v1.4.114 修复（重要，承接 1.4.113 缺陷）**：① **窗口日持久化**——此前 `cachedAdjustDates` 仅内存 `@Volatile` 缓存，App 重启后失效，且次源 `next_adjust_date` 临时拿不到即返回空列表 → 降级把"原日期+1"（如 10-24→10-25）当假生效日写入，污染库内 `MAX(effectiveDate)`（设置页曾误显"價格維持10-25"）。现推算成功的窗口日落盘 DataStore（`KOiL-price-db-adjust-dates`，逗号分隔），重启后复用，**次源临时失败也绝不空降级**；② **降级不再生成假生效日**——窗口日为空时 `seedProvince`/`verifyProvince` 用 abapi 原日期/当日值（不加 1），杜绝"原日期+1"假记录；③ **一次性清理库内已产生的假生效日**——删除 effectiveDate 不属于任何"窗口日+1"的孤立记录（标记 `KOiL-price-db-adjust-dates-leaned`）。

**⚠️ 单点风险**：调价窗口日、预测、版本号 100% 依赖次源 apizero。它挂/改格式 → 首页窗口、设置页版本号全部失效。
（2026-10-22 已修复一次：apizero 实际返回 `type:"gasoline_92"` 等新格式，旧映射只认 `"92#"` → 次源解析 0 条 → 双源核验退化为单源。`APIZERO_TYPE_MAP` 现同时兼容新旧两种格式。）

## 三、已调研未接入的候选源（2026-10-22 调研，勿重复搜索）

### A. 免费免 key（可立即加）
| 源 | 能力 | 适合作 |
|---|---|---|
| 极数本源**预测专接口** `oil-price-forecast`（apizero.cn/aidocs/oil-price-forecast） | 结构化：`next_adjust_date`（精确窗口日）、`days_remaining`、`prediction.direction / estimated_change_per_ton / estimated_change_per_liter / confidence` | 替换中文文本正则解析 + next−14 推算 |
| 东方财富数据中心调价历史表（data.eastmoney.com/cjsj/oil_default.html，AKShare `energy_oil_hist` 同源） | 历年调价日期+汽柴油涨跌 | 滚动窗口存档官方核对 |

### B. 免费需注册 key
| 源 | 免费额度 | 适合作 |
|---|---|---|
| 聚合数据 juhe.cn `apis.juhe.cn/gnyj/query` | 50 次/天 | 第三价格源，双源不一致时裁决 |
| 极速数据 jisuapi 今日油价 | 注册送额度 | 按省查油价参考 |

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
2. 改接口格式：同步更新 `APIZERO_TYPE_MAP` / 日期解析（OilPriceDateUtil）
3. 预测/窗口解析：优先结构化接口字段，避免中文文本正则
4. 本文件与 `OilPriceNetwork.kt` 顶部注释互为索引，改动时必须两处同步
