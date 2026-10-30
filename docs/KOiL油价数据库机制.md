# KOiL 油价数据库机制说明

> 记录 KOiL 油价数据库的完整逻辑机制，防止后续迭代遗忘。
> 版本基线：**v1.5.36（2026-10-30）**（本文档 v1.5.22 误删后从 git 历史恢复并全面更新至当前口径）。
> 任何机制改动请同步更新本文档、`CHANGELOG.md` 与 `API_SOURCES.md`（数据源侧）。
> 数据源权威清单/实测样例见工程根 `API_SOURCES.md`；本文档与 `OilPriceDbSyncer.kt` / `AppDatabase.kt` 顶部注释互为索引。

---

## 1. 数据模型

表 `koil_prices`，主键 `(province, grade, effectiveDate)`：

| 字段 | 说明 |
|---|---|
| province | 省份（31 个省/直辖市，`OilPriceDbSyncer.PROVINCES`） |
| grade | 油号：92号汽油 / 95号汽油 / 98号汽油 / 0号柴油 |
| effectiveDate | **生效日 = 权威窗口日 + 1 天**（窗口「24 时」生效 → 次日 0 点实际生效） |
| price | 元/L；**搁浅窗口 = 维持价（上一窗口价）** |
| source | 来源 URL（abapi / 窗口搁浅·維持(abapi) / 官方核验） |
| isVerified | 官方已核验标记 |
| isStranded | 0=调价窗口，1=搁浅窗口（窗口日到了但价格未变） |

> 表名 `koil_prices`（v1.4.159 由 fuel_prices 改名）；`isStranded` 列 v1.4.181 新增。Room 库当前 version=1（v1.5.0 重置，见 §11）。

**防未来生效日纪律（v1.5.21 B8）**：库里**永不出现 `effectiveDate > 今天` 的记录**——灌库写入闸跳过未来生效日；启动时一次性清理历史遗留未来记录（标记 `KOiL-price-db-window-day-cleared`）。窗口日当天数据源若提前放次日新价，一律跳过，等次日归并自然写入。

---

## 2. 锚点链（v1.5.36 现行 4 级 + 本地推算）

锚点 = 一个已知窗口日。**①②③④ 只提供单个锚点日期，⑤ 负责推算当年全年窗口日历**。

| 级 | 来源 | 取什么 | 备注 |
|---|---|---|---|
| ① | **xxapi**（v2.xxapi.cn/api/oilPrice） | 最近窗口生效日 − 1 天 | **优先**（同源互备主） |
| ② | **ruseo**（api.ruseo.cn/api/oilprice） | 最近窗口生效日 − 1 天 | 与 ① 同源互备（回退） |
| ③ | **abapi 全年曲线**（abapiAnchor） | 最近调价日（**生效日 − 3 天**） | 实测 18/18 与发改委公告吻合 |
| ④ | **发改委公告**（ndrc.gov.cn xwfb 列表，v1.5.23 D4） | 公告标题年份+月日 = 权威窗口日（含搁浅） | 网页抓取，公告格式变化即抓取为空 → 自动跳过，不影响主链 |
| ⑤ | **本地推算**（calculateAdjustDates） | 从锚点推算当年全年窗口日列表 | 纯本地算法，断网可用 |

> **v1.4.194 apizero 已收费移除**：原锚点链第③级（apizero 预测专接口）删除，abapi 升为③；原「双源当日价核验」退化为 abapi 单源（见 §9）。
> **v1.5.27 多源一致性可观测**：锚点命中来源（xxapi/ruseo/abapi/ndrc/无）落盘 `KOiL-price-db-last-anchor-source`；每次同步成功写组合快照 `KOiL-price-db-last-sources`（"锚点:xxapi · 今日价:abapi"），设置页油价库区域展示「同步来源」。

**配套缓存（不算级，但关键）**：
- 内存缓存（`cachedAdjustDates`）+ 持久化缓存（`settings.priceDbAdjustDates`，逗号分隔）：App 重启后复用上次已推算的窗口日历；
- **缓存命中即使 ①-④ 全失败也不空降级**（杜绝假生效日）；缓存命中时照常执行假日期清理；
- **跨年失效（v1.4.191）**：内存/持久化缓存首日年份 ≠ 今年 → 清缓存重算（跨年自动滚动到新年日历，当年口径从零重计，无需清库）；
- **v1.4.186 一次性迁移**：标记 `KOiL-price-db-calendar-186-migrated`，升级后强制重算一次（旧推算日历可能漏窗口）；
- **锚点缺失时**：`getAdjustDates()` 返回空列表 → 调用方降级（seedProvince 用 abapi 原日期）；补灌链在日历就绪前不灌（防假日期纪律，启动任务 30s 轮询重试直至就绪）。

---

## 3. 窗口日历推算规则（⑤）

`calculateAdjustDates(anchor, holidaySet, workdaySet)`：

- 锚点按**「10 个工作日 ≈ 14 自然日 + 节假日顺延」**：
  - **向后顺推**：从锚点（若在本年）每 +10 工作日至当年末；
  - **向前倒推**：从锚点每 −10 工作日回溯至当年首调。
- 工作日判定：调休补班日（workdays）算工作日；周末非补班日不算；放假日（holidays）不算（chinese-days 数据：放假日 + 调休补班日）。
- 节假日数据来自 **chinese-days**（jsDelivr → unpkg → npmmirror 三 CDN 回退），拉取「去年/今年/明年」三年；某年缺失则按纯工作日推。
- 结果升序、含历史与未来窗口，写入 `settings.priceDbAdjustDates`（逗号分隔持久化）。
- 日期规范化 `OilPriceDateUtil.normalize`（v1.5.23 E4：matchEntire 完整匹配）：支持 `yyyy-MM-dd` / `yyyy/MM/dd` / `yyyy年M月d日` / `M月d日` / `M-d`；无年份按当前年推断，月份早于当前月视为下一年；无法识别返回 null（v1.5.36 P3：非法输入 runCatching 返回原串由调用方判 format_error，不抛异常拖垮解析）。

---

## 4. 灌库流程（seedProvince）

拉单省当年曲线（abapi range=year，实测返回约 220~222 点，覆盖近一年，含上一年）并写入：

1. **幂等**：按 `省|油号|生效日` 键跳过已存在（重灌/补灌不重复插）；
2. **变化点插入**：只存「价格有变化」的点（调价日）；生效日 = `adjustToWindow(abapi日期) + 1`（abapi 滞后日期 → 归并到 ≤ 它的最近权威窗口日，再加 1 天）；
3. **写入闸（v1.5.21 B8）**：`eff > today` 一律跳过（库里永不出现未来生效日）；
4. **同价假记录清理（v1.4.182）**：灌库前删除「isStranded=false 且价格与上一条相同」的历史遗留假点（真实调价必然变价；早期版本「窗口归档」遗留的假记录会挡住搁浅幂等）；
5. **补搁浅（v1.4.181）**：对每个**已过窗口**（窗口日 **< 今天**，v1.5.21 B1——窗口日 24 点前不算已过，避免白天就出现"次日生效"的搁浅记录），若「窗口日+1」无生效记录且无本次新增变化点 → 插入搁浅记录（`isStranded=true`）：
   - **维持价** = abapi 曲线中 **≤ 窗口日** 的最后一个价格点（不设当年下界）；
   - **首窗口搁浅**（如 1-06）：abapi 当年曲线该日前无点 → 回退到**上一年最后一个调价点（上年末价）**；
   - **v1.4.189 回滚修复**：只补「库里无记录」的已过窗口；已有的记录（调价或搁浅）一律不删不动（v1.4.188 曾误删已有调价记录导致全搁浅）。
   - **v1.4.189+ 强制补灌**：标记 `KOiL-price-db-reseed-189`，升级后首次立即更新强制全量补灌一次（恢复被 v1.4.188 误替换成搁浅的调价记录）。

**触发**：`seedRemainingProvinces`（立即更新/定时更新尾部 + 启动兜底）：
- 条件：各省窗口日覆盖数 < **已过窗口数**（threshold = `adjustDates.count { it < today }`，v1.5.21 B2 严格 <），或存在**搁浅缺口**（`hasStrandedGap`：已过窗口日+1 无生效记录 / 同价假记录占位），或强制补灌标记；
- 每省独立拉取，单省失败只留缺口不阻塞其他省；`seedLock` CAS 防并发；4 路并行 + 组间 200ms 缓解 abapi 限速；
- **日历未就绪不灌**：`fillRemainingProvincesIfCalendarReady` 返回是否已执行，启动任务 30s 轮询重试（保留防假日期纪律）。

---

## 5. 一次性灌库 + 持续增量模型（v1.4.183）

- **首次联网灌库**：`initPriceDb`（库为空时灌默认省一整年曲线；不再内置油价快照）；其余省由 `seedRemainingProvinces` 后台补全；
- 之后**永不重灌**（幂等键保证）；
- 新窗口日到了 → **增量加记录**：调价（调价检测 verifyProvince 插入）或搁浅（补搁浅插入）；
- **跨年不清库**：`purge` 只清「当年 1-1~12-31 内的假日期」，**往年日期一律保留**，历史持续累加。

---

## 6. 假日期清理（purge）

`purgeIllegalEffectiveDates(dates)`：

- 白名单 = 当年窗口日历每个窗口日 + 1 天；
- **只删**：生效日在「当年 1-1 ~ 12-31」**且**不在白名单的记录（如降级灌入的 09-27）；
- **不碰往年**（v1.4.183 起）。
- 调用点：`getAdjustDates` 推算成功时、**缓存命中时**（此前只在推算分支执行 → 缓存命中会跳过 → 降级假日期残留，v1.4.114 修复）、设置页打开/刷新状态时（`purgeIllegalEffectiveDatesIfReady`）。

---

## 7. 统计口径

| 展示项 | 口径 |
|---|---|
| **調價 N**（设置页数据库卡） | 窗口日历中**今年已发生窗口日数（含搁浅）** = `adjustDates.count { it < today }`（v1.5.21 B4：今天窗口日不计入） |
| **記錄 N** | 库内全量记录（变化点 + 搁浅窗口，`COUNT(*)`） |
| **上調/下調次数**（记录页汇总） | 库内 `isStranded=false` 记录中 diff>0.001 / diff<-0.001 的条数（首条有前值时计入，如 1-06 入库后 01-21 计上调） |
| **擱淺次数**（记录页汇总） | `records.count { it.isStranded }`（v1.4.181 起直读库，不再差集） |
| **淨漲跌 / 累計上調 / 下調金額** | 仅统计非搁浅调价的 diff 累加 |
| **缺口提示** | 各省覆盖窗口数 < threshold 或存在搁浅缺口 → 红条警示（提示补灌） |

> 口径修正史：v1.4.180 曾用「窗口 − 上調 − 下調」差集算搁浅（首条基準價导致多 1，20−17=3 vs 真实 2）→ v1.4.181 改为直读 `isStranded`；v1.5.21 B4 调价次数改严格 `< today`。

---

## 8. 记录页展示口径（OilPriceDetailScreen / ViewModel）

- 数据源：`observeByProvinceGrade` 查库（升序），**直接包含搁浅记录**（无展示层补全）；
- **日期显示**：调价记录 = 生效日（如 10-16）；**搁浅记录 = 窗口日（生效日 − 1 天，如 10-15）**（与数据库卡「調整日」一致）；
- **搁浅行**：灰色日期/价格/标签 + 灰色「擱淺」chip + 尾部「維持 09-25 價格」；
- **首窗口搁浅**（keptFrom=null）：尾部显示「維持 上年價格」；
- **走势图**：价格序列含搁浅（同价 → 水平线），台阶正确；超过 60 点均匀抽样（v1.5.36 P4，记录量增长时不全量传递）；
- **省份切换**：横向滑动 chips + 左右箭头（v1.4.180，可点可滑，边界变灰）。

---

## 9. 调价检测 / 更新机制

`checkAndUpdate(force)`（8 小时节流 + 自动更新开关 + 防并发 CAS）：

1. **采样核验**：默认省 abapi 单源（`fetchBothSources` 保留 Pair 签名，secondary 恒失败——apizero 移除后调用方零改动）；
2. **只信任「数据日期 == 今天」的源参与今日核验**：主源滞后 → 跳过核验（窗口存档仍生效，不产生假调价记录）；
3. **搁浅判定**：采样价与库内最新价差 < `STRANDED_EPSILON`(0.005)：
   - **窗口日当天不下搁浅结论**（v1.5.21 B3）：今天若是窗口日，白天价格未变只返回"价格待 24 点后生效"，不写 `lastWindowChecked=今天`；
   - **搁浅结论只在「窗口日次日」落账**（v1.5.23 D2）：非窗口日重复检查不滚动版本号（此前会把 V 滚成检查日）；
   - 其余情况返回"非窗口日，正常维持"。
4. **变化检测 → 增量入库**：仅默认省核验（`verifyProvince`）：
   - 生效日 = 归并窗口日 + 1；窗口日历未就绪 → 跳过本次增量（v1.5.21 B5，交给 seedProvince 幂等补灌）；
   - **v1.5.36 P8 写入闸**：只在 `eff == today`（真正生效日）才入库——非窗口日价格抖动（数据源四舍五入）不再写"上一窗口生效日"假变化点；真实调价由次日核验/灌库自然写入；
   - 单源有价即自动入库（真实调价必然变价；同价非搁浅假点由 purge 清理）。
5. **窗口滚动存档**：接口 `nextAdjustment` 文本 → 本地推算窗口日历下个未来窗口兜底；旧 next 已过 → 升级为 prev；过期预告直接作为 prev 存档（避免首次同步即永久缺 prev）。
6. **失败不锁 8h**（v1.5.26）：失败不写 `lastCheckAt`——原实现失败也打节流时间戳，一次瞬时失败会把自动检测锁死 8 小时；失败后保持旧时间戳，下次启动/周期 Worker 立即重试。
7. **定时更新**：约 8 小时一次（OilPriceSyncWorker）；通知开关控制推送（未授权静默）。
8. **启动兜底**：全国补灌（30s 轮询等日历就绪）+ 假日期清理。

### 调价通知（v1.5.22 抽公共：Worker 与设置页「立即更新」共用，独立于同步成功与否）

`notifyPriceChanges(context)`（受 `pricePush` 开关控制）：

| 通知 | 触发 | ID |
|---|---|---|
| **调价生效** | 默认省+默认油号最新生效日变化，**且价格实际变化**（prev==null 或 prev.price != newest.price——搁浅维持价不再误发"调价至同价"）；首次启用静默锚定不打扰 | 默认（1001） |
| **窗口日前一天提醒** | 下一个未过期窗口 == 明天（幂等去重 `KOiL-price-last-window-reminded`） | 1002（v1.5.23 D3 独立，不覆盖调价通知） |
| **窗口日当天提醒**（v1.5.27） | 今天 == 窗口日 → "今晚 24 时生效"（幂等去重 `KOiL-price-last-window-day-reminded`）；触发点 = 一次性 Worker（窗口日 00:05 精准调度 `scheduleWindowDayReminder`，REPLACE 策略）+ 启动/8h 周期兜底 | 1003（独立） |

---

## 10. 预测幅度（本地估算，v1.5.23 基准锚定）

`CrudeOilEstimator.estimate()`（apizero forecast 收费移除后，幅度恒走本地估算，UI 恒标注「估算」）：

- **主源 EIA**（官方布伦特现货 RBRTE，免费 Key 可配，`eiaEnabled` 开关 + `eiaApiKey`，T+1 数据）：
  - **基准日** = 库内最近一条**非搁浅调价**的生效日（搁浅轮不重置基准——官方规则，基准仍锚上次真调价）；
  - **基准均值** = EIA 中基准日及其往前 9 个交易日的均价（共 10 日，模拟官方「调价落地参考油价」）；
  - **本窗口均值** = (上一窗口日, 今天] 的 EIA 交易日均价；
  - **变化率** = (本窗口均值 − 基准均值) ÷ 基准均值 × 100；**系数 1% ≈ 56.5 元/吨**（新闻实证：5.4% → 约 305 元/吨）；
  - 基准缺失（库内无任何非搁浅调价）→ 回退「本窗口 vs 上窗口」环比（仅方向参考）；窗口日历未就绪 → 回退「最近 10 vs 前 10」硬切。
- **降级/直选腾讯**（qt.gtimg.cn 布伦特期货实时，免 Key）：单日涨跌幅 → 元/吨（粗估，UI 标「单日参考」，不冒充窗口幅度）；
- **任何源失败 → 返回 null**（UI 显示「待检测」，不显示假值）；
- **数据截至标注**：EIA 路径返回 `dataDate`（已用到的最后交易日），UI 标注「截至 MM-dd」（EIA T+1 末段缺失透明化，v1.5.23 E6）。

---

## 11. 数据库迁移历史

| 版本 | 变更 | 迁移方式 |
|---|---|---|
| v1（旧） | — | — |
| v2（v1.4.159） | fuel_prices → koil_prices | `ALTER TABLE ... RENAME TO`（保留数据） |
| v3（v1.4.181） | 加 `isStranded` 列 | `ALTER TABLE ... ADD COLUMN` |
| **V1（v1.5.0 重置）** | App 未对外发布（作者自用）：**删除 v2/v3 历史迁移**与 schemas/2.json、3.json，全新安装直接按当前 entity 建表 | 库 version=1；⚠️ 已装旧版（version=3）覆盖升级会因 Room 禁止版本回退崩溃——**必须卸载重装** |

- WAL 模式（大批量写入不阻塞查询）；
- **禁止 fallbackToDestructiveMigration**（清库式升级会丢用户数据）；
- **覆盖升级安全**：v1.5.21~v1.5.36 均不涉及 DB schema，升级无需重装；Room schema 导出到 `app/schemas/<version>/`，未来迁移以此为基准。

---

## 12. 通知权限记忆（v1.5.36 P2）

- 新增 DataStore 键 `KOiL-notification-permission-requested`；
- MainActivity **仅首次安装请求一次**通知权限，允许/拒绝都落标记；拒绝后不再每次启动重复无效请求（Android 13+ 拒绝后系统静默不弹框）；
- 设置页推送开关：未授权点开 → 跳系统通知设置（NEW_TASK + resolveActivity 预检 + 应用详情 fallback，v1.5.30 加固）；返回 App 开关自动刷新。

---

## 13. 已知边界与依赖

| 项 | 说明 |
|---|---|
| **数据源非官方** | abapi/ruseo/xxapi 均为第三方，价格与日期可能有 1-3 天滞后或细微偏差——**库逻辑已闭环，剩余不确定性来自数据源**（今日价只信任 updateDate==today，滞后即跳过核验） |
| 锚点极端退化 | ①-④ 全失败且无缓存 → 空降级（seedProvince 用 abapi 原日期）；⑤ 本地推算断网可用，锚点缺失概率极低；启动补灌 30s 轮询等待日历就绪 |
| abapi 只给当年数据 | 首窗口搁浅维持价取不到 → 该条跳过（保持现状，不给错价） |
| 各省缺窗口 | abapi 偶发缺某省某窗口 → 缺口红条提示补灌 |
| 全新安装断网 | 空库，联网后启动/立即更新自动补灌 |
| 跨年分年回看 | 库内保留历史（v1.4.183 起），记录页尚未分年维度（2027 前可选做） |
| EIA 配额 | 免费 Key 约 1000 次/日；失败自动降级腾讯 |

---

## 附录：核心 DataStore 键（`KOiL_settings`）

| 键 | 类型 | 用途 |
|---|---|---|
| `KOiL-price-db-adjust-dates` | String | 权威窗口日历（逗号分隔，持久化缓存） |
| `KOiL-price-db-next-adjustment` / `-prev-next-adjustment` | String | 下个/最近已过窗口（兼容旧存档） |
| `KOiL-price-db-last-window-checked` | String | 最近核验窗口（版本号 V 依据） |
| `KOiL-price-db-last-check-at` | Long | 上次检测时间戳（8h 节流；失败不写） |
| `KOiL-price-db-last-update-date` / `-init-at` / `-cache` | — | 数据库卡状态展示 |
| `KOiL-price-db-last-anchor-source` / `-last-sources` | String | 多源一致性展示（v1.5.27） |
| `KOiL-price-db-auto-update` | Boolean | 自动更新开关 |
| `KOiL-price-db-legacy-pending-cleared` / `-calendar-186-migrated` / `-reseed-189` / `-adjust-dates-leaned` / `-forecast-cleared` / `-window-day-cleared` | Boolean | 一次性迁移/清理标记（防重复执行） |
| `KOiL-price-last-notified-date` / `-last-window-reminded` / `-last-window-day-reminded` | String | 通知幂等去重 |
| `KOiL-eia-api-key` / `-eia-enabled` | — | EIA 原油估算配置 |
| `KOiL-notification-permission-requested` | Boolean | 通知权限已请求过（v1.5.36 P2） |
| `KOiL-record-seeded` | Boolean | 种子一次性标记（v1.5.23：删光不复活） |

> 已删除键：`KOiL-price-db-forecast`（v1.5.21 物理删除，残留旧值已一次性清理）。设置侧非油价键（主题/语言/导航等）不在本表。
