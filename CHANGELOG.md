# KOiL CHANGELOG

## v1.5.37（versionCode 237）— 2026-10-30（基于 v1.5.36，云同步 + 种子机制移除）

> 新增坚果云 WebDAV 私有数据备份/恢复（设计文档 `docs/KOiL云同步设计.md`）；发布前清理内置种子机制。
> 不涉及 DB schema（覆盖升级安全）。构建零新 warning（仅存量）。

### 🟢 新增：云同步（坚果云 WebDAV）
- 新增 `data/sync/`：`WebDavClient`（Ktor PUT/GET/MKCOL + Basic Auth，超时 15s）、`CloudSyncManager`（编排）、`BackupCodec`（备份 JSON 编解码，复用 §7.10 手动备份格式，version 1→2 向后兼容）、`CryptoHelper`（Android Keystore AES-GCM 加密应用密码）
- 同步语义：本地权威 → 云端镜像（全量快照幂等覆盖 `koil_backup.json`）；恢复仅两条受控路径——①新设备空库自动恢复 ②设置页手动「从云端恢复」（预览 valid/重复/无效 + 确认后不删本地）
- 触发：记录变更 5s 防抖 / 设置变更 3s 防抖自动推送（30s 频率闸 + Mutex 防并发）；启动空库自动恢复、上次失败补推；失败指数退避（15s/60s 共 3 次）
- 同步内容：加油记录全量 + 13 项用户设置（含 EIA Key）；**油价库不上传**（可重灌，见设计文档）
- 设置页新增「云同步」区块：开关、WebDAV 地址/账号/应用密码（不回显、可清除）、立即备份/从云端恢复、状态行（未配置/已备份/失败待重试）、三语 27 个新 key
- AppSettings 新增 7 键（KOiL-sync-*）；AppModule/KOiLApplication 接入；不依赖第三方 SDK

### 🟠 发布前清理：内置种子记录机制移除
- 删除 `assets/seed_records.json` + `SeedInitializer`/`SeedModels`（16 条测试记录不再灌入任何新装用户）
- KOiLApplication 移除 seedIfEmpty；一次性物理删除旧键 `KOiL-record-seeded`（标记 `-cleared`）
- 已装用户：已灌入测试记录需在 App 内手动删除（删光后不复活）

## v1.5.36（versionCode 236）— 2026-10-29（基于 v1.5.35，深度压测 8 项修复）

> 用户确认执行压测报告 P1~P8 全部修复。不涉及 DB schema（覆盖升级安全）。

### 🔴 P1 调价窗口"±14 天硬编码"→ 权威日历优先（2 处）
- 首页 `AddViewModel`：接口 next 存档过期时，"下个窗口"改取权威日历 `priceDbAdjustDates` 第一个 ≥今天 的窗口（含节假日推算）；版本号"最近窗口"改取日历最后一个 <今天 的窗口，替代 next−14 推算
- 设置页 `SettingsViewModel`：W1"调整日"/下个窗口同理，权威日历为空才回退 ±14 天（极端兜底）
- 效果：春节等长假期（10 工作日漂 20+ 天）下，首页下个窗口、设置页调整日、V 版本号恒落真窗口

### 🔴 P2 通知权限"拒绝记忆"
- 新增 DataStore 键 `KOiL-notification-permission-requested`；MainActivity 仅首次安装请求一次权限，允许/拒绝都落标记，拒绝后不再每次启动重复无效请求（Android 13+ 拒绝后系统静默不弹框）；设置页开关跳系统设置链路不变

### 🟡 P3 CSV 导入日期单行容错
- `normalizeDate` 对非法数字日期（如 abc-01-01）runCatching 返回原串，由调用方正则判 format_error；不再抛 NumberFormatException 拖垮整文件解析

### 🟡 P4 总览趋势点抽样
- `computeTrend` 超过 60 点均匀抽样，记录量增长时趋势图不再全量传递

### 🟡 P5 崩溃日志同名清理
- `CrashReporter` 写公共目录前按 DISPLAY_NAME 清理历史 `KOiL-crash.txt`（系统同名 insert 会生成 (1)(2)… 堆积），只保留最新一份

### 🟡 P6 "沿用上次加油"里程置空
- 沿用旧里程必触发"里程递减"拦截（mileage <= last），改为不预填里程，由用户输入本次新里程

### 🟡 P7 录入失败提示固定文案
- `addRecord` 失败不再透出数据库内部异常文本；新增三语 `koil_precheck_add_failed_generic`（添加失败，请重试），原带参 key 删除（死键清理）

### 🟡 P8 增量入库仅限"生效日"
- `verifyProvince` 写入闸由 `eff > today` 收窄为 `eff != today` 跳过——非窗口日价格抖动（数据源四舍五入）不再写"上一窗口生效日"假变化点；窗口日 24 点生效边界不变

### 附：零 warning 构建
- 清理 9 处 Kotlin warning（discount 恒真条件、多余 !!、optString 多余 ?.、AddViewModel 补 FlowPreview/ExperimentalCoroutinesApi OptIn）

# KOiL CHANGELOG

## v1.5.35（versionCode 235）— 2026-10-29（基于 v1.5.34，省份按钮间距 + logo 放大 + 同步来源行间距）

> 用户报 3 处一次执行。不涉及 DB schema（覆盖升级安全）。

### 🔽 省份按钮文字→箭头间距
- 用户截图对比：设置页 Dropdown 行尾箭头靠 weight(1f) 撑到行尾有空隙，省份紧凑按钮内箭头贴文字 → 加 **8dp** 固定间距（与 Miuix 行尾箭头标准内距观感一致）

### 🎨 设置页底部 logo 放大 10%
- 58.52×20dp → **64.4×22dp**（XML 与 Image modifier 同步）

### ⚙️ 同步来源行间距
- 文字行到卡片圆角底部 6dp → **1dp**

# KOiL CHANGELOG

## v1.5.34（versionCode 234）— 2026-10-29（基于 v1.5.33，logo 矢量渲染缺失修复 + 省份字体间距对齐 + 同步来源行间距）

> 用户报 v1.5.33 的 3 处问题一次执行。**logo 缺失为严重缺陷**：VectorDrawable 转换脚本对 SVG「M 隐式 LineTo」解析错误导致四个字母缺笔画。不涉及 DB schema（覆盖升级安全）。

### 🎨 KOiL logo 矢量渲染缺失修复（关键）
- 根因：fontTools SVGPathPen 会把「M 后连续坐标」输出为隐式 LineTo（如 `M160.65 559.95 265.95 581.05`），转换器按 M 只读 2 坐标、其余坐标点被丢弃 → K/O/i/L 全部缺笔画；且 M 后错误插入 L 产生畸形命令
- 修复：转换器重写（M 首组坐标直接输出、后续隐式坐标补 L）；viewport 高度 1000→1020（容纳 O 的 descender 防底部裁剪），viewportWidth 同步 2984.5
- 验证：修复后 K O i L 全部完整（K 直干、O 镂空孔、i 圆点均在），与字体渲染一致
- 同步更新工程根 KOiL-logo-vector.svg（y-down 坐标系最终形状）

### 🔽 省份字体与箭头间距对齐设置页
- 文字 12sp Medium → 14sp Bold（对齐油号选择行尾）；删除手动 Spacer(2dp)，箭头直接跟随（DropdownArrowEndAction 自带间距，与设置页 Dropdown 行尾排布一致）

### ⚙️ 同步来源行间距
- 「同步来源」文字行到油价库卡片圆角底部间隔 12dp → 6dp

# KOiL CHANGELOG

## v1.5.33（versionCode 233）— 2026-10-29（基于 v1.5.32，KOiL 品牌 logo 转矢量 + 同步来源移区块最底 + 省份箭头换设置页同款）

> 用户报 3 项改动一次执行。**关键变更：Unbounded 字体彻底移除**——设置页底部 KOiL 品牌字从字体渲染转为矢量 drawable（形状为 Bold wght700 字形轮廓），字体文件与引用代码全部删除。不涉及 DB schema（覆盖升级安全）。

### 🎨 KOiL 品牌 logo 转矢量（不再依赖字体）
1. 用 fontTools 将 Unbounded 可变字体实例化为 wght=700（与之前 Bold 渲染一致），提取 K/O/i/L 四字形轮廓，按字形宽度横排组合
2. 生成 `res/drawable/koil_logo.xml`（VectorDrawable，视口 2926×1000，宽 58.52dp 高 20dp，黑色底供主题 tint）——预览图/对比图已随确认流程交付
3. 设置页底部：`Text("KOiL", Unbounded 字体)` → `Image(koil_logo)` + ColorFilter.tint（颜色与之前一致 onBackgroundVariant 60%）
4. **删除 `res/font/unbounded.xml` + `unbounded_variable.ttf`**，清理 Font/FontFamily/FontVariation import；全局确认无其它引用

### ⚙️ 油价库区块：同步来源移到最底
- 用户明确"放在数据库卡的最下面，也就是 EIA 下面"：同步来源从「立即更新」按钮行下方移出，放到 **EIA API Key 项之后、区块闭合前**（区块最后一行）
- 分隔线与区块内其它分隔线完全同款：0.5dp / `onBackgroundVariant` alpha 0.12 / 通栏宽度；字号 12sp 保持

### 🔽 首页省份箭头换设置页同款
- 用户两次反馈实心小三角不显眼、不像设置页外观 → 替换为 Miuix `DropdownArrowEndAction`（设置页 Dropdown / 油号选择行尾同款线条风格箭头，颜色=按钮文字色）
- 清理 `Icons.Filled.ArrowDropDown` import（不再使用）

## v1.5.32（versionCode 232）— 2026-10-29（基于 v1.5.31，异常弹窗三修 + 油价库布局 + 全局点击失焦）

> 用户报 v1.5.31 的 4 处问题一并修复。不涉及 DB schema（覆盖升级安全）。

### 📊 异常明细弹窗三修
1. **记录叠加根因**：KOiLGlassDialog 滚动容器内部是 **Box**，Box 内多个直接子项全部叠在同一位置（默认顶对齐）——之前 forEach 输出的多条记录行全叠在弹窗左上角
   - 修复：content 内包一层 **Column** 垂直排列（行 + 分隔线依次下排）
2. **偏差负号**：偏低记录显示「偏低 -24.8%」语义错误（状态词已含方向）→ 取绝对值「偏低 24.8%」（不全局改，环比 delta 的负号仍需保留）
3. **补标题 + 背景不透明**：KOiLGlassDialog 的 title 参数此前未渲染（标题从未显示），现在在内容内自绘标题；弹窗背景 alpha 0.94 → **完全不透明**，确保盖住背后卡片

### ⚙️ 油价库区块布局
- 「同步来源」从左侧 Column 移出，放到区块最底（「立即更新」按钮行之后）——之前放左列会把两行文字顶上去，导致「调整日 / 下个窗口｜最近更新」与右边按钮**不垂直居中**
- 左列恢复两行 → 与按钮天然水平居中；同步来源保留 0.5dp 间隔线 + 12sp 样式

### ⌨️ 全局「点击空白取消选中」
- 录入卡/总览/设置等文本框点击后光标闪烁，点其它地方不取消选中
- 修复：KOiLScreenScaffold 内容根 + KOiLGlassDialog 统一加「点击空白 clearFocus」（子输入框消费点击不误触；设置页原有局部处理保留不冲突）
- 覆盖：首页录入卡、总览页、设置页、补录/编辑弹窗

---

## v1.5.31（versionCode 231）— 2026-10-29（基于 v1.5.30，改名 + 异常明细弹窗 + 数字显示规范化等 6 项）

> 用户连续报 5 个小需求 + 1 个全局优化一并执行。不涉及 DB schema（覆盖升级安全）。

### 🏷 改名 + 导航图标（三语同步）
| 位置 | 改前 | 改后 |
|---|---|---|
| 首页顶栏 | 本次加油 / Refueling | **KOiL**（品牌名，三语统一） |
| 总览页顶栏 | 資料總覽 / Overview | **數據總覽 / Data Overview**（简体原已是"数据总览"） |
| 导航第 1 个 | 加油 / Log | **首頁 / Home**，图标 笔 → **房子** |
| 导航第 2 个 | 總覽 / Overview | **數據 / Data**，图标 柱状图 → **数据库桶** |

### 📊 异常卡点击 → 明细弹窗
- 有异常（N>0）时异常卡可点击；无异常不可点
- 点击弹出柔光玻璃弹窗「油耗异常明细（N 条）」，滚动列表逐条展示：日期、油号、油耗值（L/100km）、**偏高/偏低**状态 + 偏差幅度（相对均值 %）
- 三语新增 3 key：`koil_ov_abnormal_detail_title / _high / _low`

### ✍️ 录入卡应付金额「待计算」占位
- 对齐补录卡 v1.5.14 做法：参考价未就绪/无法计算时应付字段显示「待计算」占位，算好后即时填充

### 🔽 省份下拉三角
- 首页油价卡省份按钮：文本字符 `▾`(10sp) → **矢量箭头 16dp**（与设置页 Dropdown 同风格）

### ➖ 同步来源行
- 从区块底部**移回「下个窗口｜最近更新」行正下方**，两行间加 0.5dp 细分隔线，字号 **11sp→12sp**（行高 14→15）与下个窗口一致

### 🔢 数字显示规范化（全局统一）
- 新增 `NumberFormat`（domain/logic）：金额固定 2 位；度量（油价/油耗/油量/单价）最多 2 位、尾零裁剪、整数不带小数点；百分比 1 位、尾零裁剪
- 覆盖：首页油价卡、油号选择、总览六指标、记录卡明细、环比 delta、图表双轴、补录/编辑卡、油价库状态、油价历史明细页、CSV 导出（保持可导入回环）
- 金额类（应付/实付/优惠/每公里成本/总额）固定 2 位保持不变；总额由取整改为 2 位

---

## v1.5.30（versionCode 230）— 2026-10-29（基于 v1.5.29，通知设置跳转加固 + 提示行收紧）

> 用户反馈 v1.5.29 通知开关仍"没反应、没跳转"、红色字仍偏远。修复根因。不涉及 DB schema（覆盖升级安全）。

### 🔧 通知设置跳转加固（治"点开关没反应也没跳转"）
- **根因**：v1.5.29 用 `runCatching` 包裹 startActivity——异常被**静默吞掉**。若 `ACTION_APP_NOTIFICATION_SETTINGS` 在 ROM 上无处理者（ActivityNotFoundException）、或 context 非 Activity 缺 `NEW_TASK` 标志，跳转直接失败且无任何反馈 = "没反应"
- **修复**（新增 `openNotificationSettings()` 辅助函数，开关与提示行共用）：
  1. Intent 加 `FLAG_ACTIVITY_NEW_TASK`——兼容非 Activity context
  2. `resolveActivity` 预检——该 action 无处理者时不再盲跳
  3. fallback：`ACTION_APPLICATION_DETAILS_SETTINGS`（应用信息页，所有 ROM 必有；通知开关就在 App 详情里）
  4. 不再静默吞异常——总有一个可感知的跳转结果

### 🖌 提示行收紧
- 通知 + 定位两处红色提示行：`padding vertical 2dp` 移除 + `offset(y = -3.dp)` 上移，消除 SettingSwitchRow 副标题底内边距造成的悬空感

---

## v1.5.29（versionCode 229）— 2026-10-29（基于 v1.5.28，通知开关跳系统设置 + 4 项小修）

> 用户报 3 个问题（通知开关拒绝后点开没反应 / 提示行间距 / 同步来源位置）+ 待办（异常油耗 30→20）一并执行。不涉及 DB schema（覆盖升级安全）。

### 🔧 通知开关点开"没反应"修复（HyperOS 拒绝后不再弹运行时框）
- **根因**：Android 13 通知权限设计偏向"拒绝后去设置里开"，小米 HyperOS 直接实现为拒绝后 `requestPermissions` **静默返回拒绝、不再弹框**——launcher 悄悄失败，开关没动静（定位权限是传统危险权限，拒绝后系统仍会弹框，故定位正常）
- **修复**（SettingsScreen）：
  1. 通知开关点开且未授权 → **直接跳系统通知设置页**（ACTION_APP_NOTIFICATION_SETTINGS），绕开系统不弹框的坑，所有机型统一可走
  2. 记录"用户想开"意图（pendingOpenPush）；从系统设置授权返回 → ON_RESUME 自动补开推送开关
  3. 红色提示行保留（点它同样跳系统设置）

### 🖌 三处 UI 小修
- 红色提示行（通知 + 定位）`vertical 4dp → 2dp`，贴近副标题，不再悬空
- 「同步来源」从窗口链条 Column 第 3 行**移到区块底部**（"立即更新"按钮行之后当脚注）——来源属排查元信息，不干扰主信息流

### ⚙️ 设置项
- 异常油耗默认阈值 **30% → 20%**（AppSettings `DEFAULT_ABNORMAL_THRESHOLD`；已手动改过的用户自定义值不受影响）

---

## v1.5.28（versionCode 228）— 2026-10-29（基于 v1.5.27，通知弹窗升级 + 权限联动）

> 用户选定：弹窗方案 A（通道升 HIGH 强制横幅）+ 通知/定位开关权限联动（拒绝→开关自动关、点开→再请求）。不涉及 DB schema（覆盖升级安全）。

### 🔔 通知弹窗升级（方案 A：横幅 + 完整正文）
- **根因**：通知通道为 DEFAULT 重要级 → 系统不强制弹横幅（HyperOS 常只进通知栏一行小字）；正文单行截断
- **修复**（OilPriceNotifier）：
  1. 新通道 `oil_price_adjust_high`（**IMPORTANCE_HIGH** → heads-up 横幅弹窗；旧通道 `oil_price_adjust` 删除清理——同 ID 重建在部分机型保留旧重要性，故换新 ID）
  2. 通知加 `BigTextStyle` → 正文完整显示、横幅更高、可展开
  3. `PRIORITY_HIGH` 配合触发横幅
- ⚠️ 提示：换新通道后，若之前未在系统设置里关过通知，新通道默认开启即弹横幅

### 🔐 通知 / 定位开关权限联动（你的设计：拒绝 → 开关自动关，点开 → 再请求）
- **通知推送开关**（SettingsScreen + MainActivity）：
  1. 首次安装启动照常弹权限框；**拒绝 → 推送开关持久化置关**（MainActivity `onRequestPermissionsResult` 联动写 DataStore）
  2. 设置页开关**未授权恒显示关**（不再"开关开着却收不到"的假象）；点开 → 先请求系统权限 → 同意才开、拒绝保持关
  3. 未授权时开关下方显示红色提示行「系统通知权限未开启，推送无法送达，点此开启」→ **点击跳转系统通知设置**一键开启
- **定位开关**（同一套逻辑）：
  1. 未授权恒显示关；点开 → 先请求定位权限 → 同意才开 + 自动定位省份，拒绝保持关
  2. 未授权显示提示行「未获得定位权限，开启定位需同意权限请求」（点击重新请求）
- 返回前台自动刷新权限状态（用户在系统设置里改过后立即同步显示）
- 三语新增：`koil_set_price_push_denied` / `koil_set_location_denied`；KOiLColors 新增 Warning 提示色

---

## v1.5.27（versionCode 227）— 2026-10-29（基于 v1.5.26，窗口日当天提醒 + 多源一致性展示）

> 用户在 4 项建议中选定 #2「窗口日当天 0 点提醒」与 #3「设置页多源一致性」。不涉及 DB schema（覆盖升级安全）。

### 🔔 窗口日当天"今晚 24 点生效"提醒（新增，通知 ID 1003 独立）
- **需求**：窗口日当天（如 11-12）提醒"今晚 24 时窗口到期、新价生效"，比现有"前一日提醒"更贴近加油决策
- **实现**：
  1. `OilPriceDbSyncer.notifyPriceChanges` 新增第 3 段：今天 == 窗口日历中的窗口日 且 未提醒过该窗口日 → 发通知
     （新 DataStore 键 `KOiL-price-last-window-day-reminded` 去重；独立 ID 1003 不覆盖调价生效 1001 / 前一日提醒 1002）
  2. 新 `OilPriceWindowDayWorker` + `scheduleWindowDayReminder()`：一次性 WorkRequest 精准调度窗口日当天 **00:05**；
     窗口日当天已过 00:05（当天启动）→ delay=0 立即执行（幂等，已提醒即跳过）
  3. 三保险：启动链（同步成功后重新排期 + notify 兜底）· 8h 周期 Worker（每次重新排期，持续运行跨窗口日也覆盖）· 一次性 Worker（00:05 精准）
- 三语文案新增：`koil_noti_title_window_today` / `koil_noti_msg_window_today`

### 🔍 设置页多源一致性展示（油价库区域新增"同步来源"行）
- **需求**：把每次同步的来源链透明化——你能直观看到数据来自哪个源、兜底链是否健康，排查问题更快
- **实现**：
  1. 锚点链 `fetchAnchorWindowDate` 改为返回 `AnchorWindow(date, source)`——来源跟随实际命中节点
     （xxapi → ruseo → abapi 曲线 → 发改委 ndrc 第 4 级兜底），落盘 `KOiL-price-db-last-anchor-source`
  2. 每次同步成功写组合快照 `KOiL-price-db-last-sources`（如 "锚点:xxapi · 今日价:abapi"）
  3. 设置页"下个窗口｜最近更新"行下方新增小字「同步来源：…」（三语 key `koil_pdb_sync_sources`）
- 同步更新 `API_SOURCES.md`（数据源纪律）

---

## v1.5.26（versionCode 226）— 2026-10-29（基于 v1.5.25，总览居中 + 油价库补灌自动重试）

> 用户认可方案的两项：总览六指标居中 + 油价库"打开不自动灌"根因修复。不涉及 DB schema（覆盖升级安全）。

### 🖌 总览页六指标 3×2 居中 + 列间距（OverviewScreen）
- **根因**：StatCell 内容全部 start 对齐 → 三列内容挤在各自格子左侧，第三列右侧留白约 20% 屏宽 → 整体视觉偏左
- **修复**：StatCell 外层 Column 改 `horizontalAlignment = CenterHorizontally`（icon+label 行、数值+单位行、涨跌行全部居中）；
  两行网格列间改为显式 `Spacer(12.dp)` 均匀间距；行间距 14dp、外部 padding 16dp 不变

### 🔄 油价库全国补灌自动重试（KOiLApplication + OilPriceDbSyncer）
- **根因**：全国 31 省补灌（seedRemainingProvinces）前置条件"窗口日历就绪"（getAdjustDates 非空，防假生效日纪律）；
  重装后日历缓存为空，启动瞬间锚点链（xxapi→ruseo→abapi→发改委）若失败 → 日历空 → 补灌**静默跳过且无重试**；
  失败后 checkAndUpdate 的 catch 还写入 8h 节流时间戳 → 自动检测也被锁死 → 只有点「立即更新」(force) 能绕过
  （与你观察"打开几分钟不动、点立即更新马上灌"完全一致；截图「下个窗口：10-29」来自聚合接口文本，不依赖日历成功）
- **修复**：
  1. 新增 `fillRemainingProvincesIfCalendarReady()`（日历就绪才灌，返回是否已执行）——把"日历未就绪"变成可观测状态
  2. 启动兜底改为重试循环：首轮 3s + 后续每 30s 轮询，最多 6 次（约 3 分钟自愈），就绪即自动灌全国
  3. `checkAndUpdate` 失败**不再写 lastCheckAt**——瞬时失败不再锁死 8 小时，下次启动/周期 Worker 可立即重试；
     成功才写节流时间戳
- 保留纪律：日历未就绪仍不灌（不放开空降级，避免重蹈假生效日 bug）

---

## v1.5.25（versionCode 225）— 2026-10-29（基于 v1.5.24，修复吸顶闪退）

> 用户实测 v1.5.24 总览页月份标题吸顶时闪退。不涉及 DB schema（覆盖升级安全）。

### 🐛 修复：月份标题吸顶闪退（OverviewScreen）
- **根因**：吸顶背景用 `textureBlur(backdrop)` 毛玻璃——月份标题位于 backdrop **录制子树内部**（与顶栏不同，
  顶栏是 Scaffold 兄弟节点、在录制子树外），textureBlur 采样 backdrop 层时会包含 header 自身 → **自采样递归** → GPU 渲染崩溃闪退
- **修复**：移除 header 上的 textureBlur（及 blur 相关 import），吸顶背景改为**半透明 surface**（`surface.copy(alpha=0.92f)`）——
  观感接近顶栏毛玻璃、无递归风险；贴顶 offset、吸顶检测、未吸顶无色块等逻辑全部保留不变

---

## v1.5.24（versionCode 224）— 2026-10-29（基于 v1.5.23，UI 三处 + 设置页缺口实时刷新）

> 用户点名的 3 处 UI 改动 + 已批准的「设置页油价库缺口实时刷新」改进。不涉及 DB schema（覆盖升级安全）。

### 🖌 UI1 首页调价窗口提醒（AddScreen）
- 「建议提前加油」徽章：从第二行徽章区移到「下一个调价窗口日」那一行**最右侧**（右对齐、垂直居中，窗口日文字超长省略）
- 预计文案：(估算/单日参考) 从尾部前置到「预计」后 → `预计(估算)上调170元/吨`（三语资源 koil_add_forecast_up/down 加 %1$s 占位）
- 标点：`(截至10-27)` 后加逗号 → `(截至10-27)，折合上调约0.13元/升`（新增分隔符资源 koil_add_forecast_sep，三语）

### 🖌 UI2 资料总览页月份标题吸顶（OverviewScreen）
- **根因**：月份标题吸顶位置 = LazyColumn 视口顶部（屏幕最顶），被悬浮毛玻璃顶栏（TopAppBar）遮住 → 看起来"没贴顶"
- **修复**：layoutInfo offset 检测标题进入顶栏区域 → 绘制 offset 下移顶栏高度，**贴顶栏底部连续无跳变**；
  吸顶时背景换**顶栏同款毛玻璃**（同一 LayerBackdrop + textureBlur，参数与 BlurredBar 完全一致；blur 不可用回退 surface 纯色）；
  **未吸顶时无任何背景色块**（移除原恒有 .background）

### 🖌 UI3 资料总览页月份标题字号（OverviewScreen）
- 月份文字 14sp → **16sp**；「N笔」文字 11sp → **16sp（与月份一样大）**，并移到**行最右侧**（月份靠左、右对齐）

### 🖌 UI4 资料总览页六指标卡（OverviewScreen）
- 网格 **2列×3行 → 3列×2行**（平均油耗/每公里成本/总里程 一行，总加油量/总实付/总应付 一行）
- 删除网格下方「实付较应付节省/超支 ¥N」对比条（代码段 + 三语死键 koil_ov_vs_actual_saved/over 一并清理）

### 🔄 设置页油价库缺口实时刷新（SettingsViewModel + SettingsScreen）
- **根因**：loadPriceDbStatus 只在进入设置页时加载一次，后台补全国完成后缺口停留旧快照（用户误以为"不会自动灌完"）
- **修复**：init 监听 `priceDao.observeCount().distinctUntilChanged()` → 库行数变化自动重算缺口（只读不写，无循环）；
  缺口 > 0 时显示「后台补灌中，完成后自动刷新」提示（新增三语 key koil_pdb_seeding），补完缺口归零文案自动消失

---
## v1.5.23（versionCode 223）— 2026-10-29（基于 v1.5.22，深度检测清单批量执行）

> 执行「全功能深度检测」发现项 + 估值基准修复（E5/E6）：中危 3 修 + 低危 8 修 + 死键清理 2 项 + 发改委锚点接入。
> 不涉及 DB schema 变更（Room 无 Migration，覆盖升级安全）。

### 📈 E5 估值基准修复（核心 bug：10-29 窗口「预计下调」实为上调）
- **根因**：估算基准用「本窗口均值 vs 上窗口均值」环比；10-15 窗口**搁浅**（价格未变、官方基准不重置），
  其上窗口均值被 10-02 地缘暴涨（布伦特 135.51）拉高 → 环比错误显示下调（实测 -1.75%）
- **修复**：基准锚定「库内最近一条**非搁浅调价**生效日」——基准均值 = 该日往前 10 个 EIA 交易日；
  变化率 = (本窗口均值 − 基准均值) ÷ 基准均值。10-15 搁浅不再重置基准（对齐官方「搁浅不重置」规则）；
  库内无任何非搁浅调价时回退环比。修复后方向与机构预测一致（10-29 预计上调）
- **配套**：EIA 为 T+1 发布，窗口日当天数据未走完——UI 标注「数据截至 MM-dd」（新三语 key `koil_add_forecast_as_of`）

### 🐛 中危三修（深度检测 D1/A1/D2）
- **D1** 油价明细页「当年累计涨跌」漏算年初第一条调价：diff 改用**全量记录**上一条（此前年过滤后 i==0 时
  prev=null，当年上调/下调次数与累计金额少计年初调价）
- **A1** 种子记录「复活」：灌入条件从「表为空」改为一次性标记 `KOiL-record-seeded`——用户删光记录后
  重启不再重灌 16 条种子（此前 count==0 即重灌，删除形同虚设）
- **D2** 设置页版本号滚动失真：搁浅结论只在「窗口日次日」落账——非窗口日重复检查不再把
  lastWindowChecked 滚成检查日（此前 V20261031 之类的检查日版本号，现保持 V20261029）

### 🐛 低危八修（A2/A3/A4/B1/C1/C3/D3/E4）
- **A2** Geocoder 定位：Thread.sleep(200) → CountDownLatch 等待至多 2s（此前超时即静默降级 IP，偶发定位失败）
- **A3** IP 归属地：加 30 分钟节流（缓解 ip-api 免费版 45 次/分钟限流；明文 http 为免费版限制，注释说明）
- **A4** 定位缓存：丢弃 30 分钟前的旧缓存（避免数月前的旧位置误报省份）
- **B1** 全工程 `String.format`/字符串字面量 format 统一 `Locale.ROOT`（39 处、9 文件，消 DefaultLocale 类 lint 警告）
- **C1** CSV 行解析支持双引号包裹字段与 `""` 转义（KOiL 自导出无引号，回环行为等价；外部含逗号字段不再错位）
- **C3** 手写 JSON 序列化：Double NaN/Infinity 防御性写 null（防非法 JSON）
- **D3** 窗口提醒通知独立 ID（1002）——不再与调价生效通知（1001）互相覆盖
- **E4** 调价日期正则 `find` → `matchEntire`（完整匹配，不再宽容部分匹配脏文本）

### 🧹 死键清理与接线（D7/B4）
- **D7** 删除 `KOiL-last-grade` 死键（「沿用上次油号」实际取上次记录油号实现，lastGrade 键从未读写）
- **B4** 总览页范围选择持久化：setRange 写 `KOiL-summary-range`、启动恢复上次选择（此前重启回「全部」）

### 🏛️ D4 发改委权威窗口锚点接入（第 4 级兜底）
- `fetchNdrcAdjustDates()` 从死代码接入锚点链**第 4 级**（主链 xxapi→ruseo→abapi 不变）：
  实测发改委官网列表页正则命中 4 条公告（8-28/9-11/9-24/10-15，与推算日历完全吻合）；
  它是网页抓取，公告格式一旦变化即抓取为空 → 自动跳过，不影响主链锚点（风险可控）
- 最近窗口搁浅时主链"快照 date−1"可能错锚，第 4 级发改委兜底修正

### 维持不修（有意的设计，交付说明记录）
- **C2** 备份恢复维持「逐条独立提交、部分成功保留」（改为整批事务会变成全有或全无，改变现有行为语义）
- 明细页「当年无记录时最新价显示 --」、pending 兼容面板、EIA Key 明文（自用 App）——维持现状

## v1.5.22（versionCode 222）— 2026-10-29（基于 v1.5.21，正确性小修批量）

> 批量执行待办 #3~#12：翻译补漏 + 通知逻辑四修（A/B/C/D）+ 估值链路五修（#8~#12）。
> 不涉及 DB schema 变更（Room 无 Migration，覆盖升级安全）。

### 🌐 #3 三语翻译补漏（消除 Lint MissingTranslation Error）
- `koil_pdb_adjust_stranded_short` 补 en（`Window %1$s (stranded)`）/ zh-rTW（`調整日 %1$s（擱淺）`）

### 🔔 #4~#7 通知逻辑四修
- **#4（Bug A）搁浅不再误发"调价生效"通知**：`notifyPriceChanges` 推送条件加"价格实际变化"
  （`prev==null || prev.price != newest.price`）；搁浅窗口（维持价）只锚定 `lastNotified` 不推送，
  后续真调价仍正常推（防旧锚点干扰）
- **#5（B）手动「立即更新」后也触发通知**：`notifyPriceChanges` 从 Worker 抽到
  `OilPriceDbSyncer`（公共方法），`SettingsViewModel.checkPriceDbNow` 成功后同入口调用
- **#6（C）通知点击跳转 App**：`OilPriceNotifier` 加 PendingIntent（MainActivity，FLAG_IMMUTABLE）
- **#7（D）同步失败窗口提醒不丢**：`OilPriceSyncWorker.doWork` 把通知移出 `success` 判断
  （网络挂也跑通知，逻辑幂等；retry 策略不变）

### 📈 #8~#12 估值链路五修（预计涨跌正确性）
- **#8 腾讯降级按来源标注**：`forecastSource` 写入 UI 状态，腾讯来源显示「单日参考」（新三语 key
  `koil_add_forecast_single_day`），EIA 显示「估算」——不再用"单日波动"冒充"窗口幅度"
- **#9 EIA 窗口日历对齐切分**：`calcFromEiaSeries(points, adjustDates)`——本窗口 = (上一窗口日, 今天]，
  上窗口 = 再往前一窗口（与"10 工作日均值环比"口径一致）；窗口日历未就绪回退原 10+10 硬切
- **#10 建议逻辑去历史反推**：`AddScreen` suggestion 删 avgTrend fallback——估算失败（EIA+腾讯均不可用）
  时不驱动"早加/等"（历史涨跌不预示下一轮方向，因果不成立）
- **#11 估值周期刷新**：`AddViewModel` 每 8h 重算一次估值（进程存活期间；首刷由灌库触发）——
  原油跨日变动后首页不停留旧估值
- **#12 柴油密度修正**：折合元/升按当前油号折算——92/95/98 汽油 0.76→1316 升/吨、0# 柴油 0.83→1205 升/吨
  （柴油每升幅度不再偏 9%）

## v1.5.21（versionCode 221）— 2026-10-29（基于 v1.5.20，数据正确性专项）

> 两项数据正确性修复：#1 彻底删除 apizero 时代 forecast 残留；#2 窗口日 24 点生效边界（修复"29 号 0 点生效"假象）。
> 不涉及 DB schema 变更（Room 无 Migration，覆盖升级安全）。

### 🗑️ #1 KOiL-price-db-forecast 彻底删除（常驻待办结清）
- **背景**：apizero（v1.4.194 已移除）时代最后一次 forecast 文本仍残留在 DataStore 键 `KOiL-price-db-forecast`，
  代码中保留整套读取死代码（"接口 forecast 优先"分支恒不命中）。本版物理删除键 + 清全部代码引用。
- **代码删除**：
  1. `AppSettings`：删 `KEY_PRICE_DB_FORECAST` / `priceDbForecast` Flow / `setPriceDbForecast()`
  2. `OilPriceDbSyncer`：删 forecast 写入（checkAndUpdate）与 `OilPriceSyncResult.forecast` / `nextAdjustDate` 透传；
     `performPriceCheck` 不再返回 nextAdjustDate（窗口日仅剩聚合接口文本解析 → 本地窗口日历兜底）
  3. `OilPriceModels`：删 `OilPriceResponse.forecast` / `OilPriceSyncResult.forecast` 死字段
  4. `AddViewModel`：删 `priceDbForecast` 常驻监听、`parseForecast()` 文本解析（第二段恒走 EIA/腾讯估算）；
     `AddUiState.forecastEstimated` 字段删除——幅度恒为估算，UI 标注「估算」恒显示
- **数据清理**：启动一次性 `remove("KOiL-price-db-forecast")` + 标记 `KOiL-price-db-forecast-cleared`（幂等）
- **行为**：首页预测幅度照常显示（EIA/腾讯估算 + 「估算」标注），不再有任何"接口 forecast"路径
- **文档**：API_SOURCES.md 依赖表预测幅度行同步（键已删除、恒走估算）

### 🕐 #2 窗口日 24 点生效边界修复（B1~B8）

> **Bug 现象**：今天是窗口日（如 10-29）时，App 白天就把当天当"已发生/已搁浅"处理——数据库卡显示
> "V20261029 擱淺"、调价次数多算 1（20→21）、库里出现"次日生效"（10-30）的搁浅记录，等于"以窗口日 0 点生效"。
> **正确口径**：新价 29 号 24 点（=30 号 0 点）才生效，29 号全天显示旧价，24 点前不产生任何 10-30 记录。

- **B1 补搁浅**（`OilPriceDbSyncer.seedProvince`）：`adjustDates.filter{it <= today}` → `< today`
  ——窗口日当天不再提前补"次日生效"的搁浅记录（今天白天库里不再出现 10-30 生效的维持价记录）
- **B2 补灌阈值/缺口**（`seedRemainingProvinces` L388、`hasStrandedGap` L423）：`<= today` → `< today`
  ——期望覆盖数与缺口检测不再把今天窗口计入（各省不误报"未拉取完整"）
- **B3 核验搁浅误判**（`performPriceCheck`）：今天 ∈ 窗口日历时，白天价格未变**不下"搁浅"结论**、
  不写 `lastWindowChecked=今天`（消息改为"今天是调价窗口日，价格待 24 点后生效"）
  ——数据库卡不再当天显示"V今日 擱淺"
- **B4 调价次数/缺口口径**（`SettingsViewModel` L201/L210）：`<= todayStr` → `< todayStr`
  ——"調價 N 次"今天窗口不计入（修复 20→21 的多算 1）
- **B5 verifyProvince 降级**：窗口日历未就绪时不再用 `today` 当生效日（宁缺毋滥，交给 seedProvince 幂等补灌）
- **B8 写入硬闸门**（`verifyProvince` + `seedProvince` 调价点）：生效日 > 今天 一律跳过
  ——数据源若窗口日白天提前放次日新价也写不进库，等次日归并自然写入；库里永不出现"未来生效"记录
- **B6 存量清理**：启动一次性 `DELETE FROM koil_prices WHERE effectiveDate > 今天` + 标记
  `KOiL-price-db-window-day-cleared`（清掉已污染的 10-30 假搁浅；今晚若真调价，10-30 变化点由正常路径补入）
- **B7 展示层兜底**：DAO 新增 `observeNewestUpTo(省,油号,今天)`（`effectiveDate <= :date`）；
  首页四宫格（`observeViewProvincePrices`）、当前单价（`observeCurrentPrice`）、
  调价通知（`OilPriceSyncWorker` 改 `getLatestPrice(≤今天)`）统一取"截至今天已生效"价
  ——即使有漏网"未来生效"记录也不显示、不推送
- **验证**：实测 10-29 abapi 仍返回旧价 8.63（数据源未提前放价）；代码级对账：窗口日历 10-15→10-29 顺推确认，
  种子数据无 10-29/10-30 干扰；编译 + apksigner 校验通过

## v1.5.20（versionCode 220）— 2026-10-27（基于 v1.5.16，性能与大数据量专项）

> 深度压力测试 18 项问题中的剩余 8 项（中危性能为主）一次性修复，合并原计划 v1.5.17/v1.5.18 直接出本版，不涉及 DB schema 变更与新功能。

### 🔴 导入导出正确性（#3 #4）
1. **#4 导出油耗状态不再写死"正常"**：新建 `domain/logic/DerivedCalculator.kt`（共享 `RecordWithDerived` / `ConsumptionStatus` / `compute()`），由 `OverviewViewModel` 与 `SettingsViewModel.exportCsv` 共用；CSV 状态列按真实派生结果输出（正常/偏高/偏低/短距/–），油耗、每公里成本、单价、加油间隔均改用同一派生口径，消除"页面偏高、导出却正常"的不一致
2. **#3 导出里程不再丢小数**：本次里程/上次里程/行驶里程由 `%.0f` 改为整数显示整数、非整数保留原值（`fmtMileage()`），油量/金额仍保留 2 位
3. **#3 导入检测文件内自身重复/里程链冲突**：原实现仅对比库内记录，CSV 文件内重复行、同文件里程倒挂均漏检；现以「库内 + 已接受文件行」合并视图判定，逐行即时入树
4. **#3 复合键浮点健壮性**：去重键由 `"日期|里程"` 字符串拼接改为 `TreeSet` 按 `(日期, 里程)` 精确比较（避免小数里程字符串/精度问题）

### 🟡 性能：导入与重算（#6 #7）
5. **#7 导入预检 O(n²) → O(n log n)**：`previewCsv` 前后记录查找由 `lastOrNull/firstOrNull` 全表扫描改为 `TreeSet.lower/higher` 二分；三遍处理（解析→排序判定→按原顺序归类），大文件不再随记录数平方级变慢
6. **#6 重算/导入移出主线程**：`observeAll` 加 120ms `debounce`（合并批量导入/快速增删的连续写入），排序与派生计算切 `Dispatchers.Default`；CSV 文件读取切 IO、解析判定切 Default；阈值变化重算同样切后台
7. **#6 确认导入改单事务批量**：原逐条 `insert` + `catch` 吞异常（N 次事务且静默漏写）改为 DAO 新增 `insertAllIgnore(IGNORE)` 单事务批量插入，按返回 rowId 统计实际写入数，唯一索引冲突（预览后数据被改）安全跳过

### 🟡 性能：历史列表 LazyColumn 重构（#8 #9 #10 #11）
8. **#8 历史列表惰性渲染**：总览页整体由「`Column + verticalScroll` + 按月手动分页 `take(visibleMonths)`」改为 `LazyColumn`，月份标题用 `stickyHeader` 吸顶，记录卡只组合可见项；移除手动分页逻辑与"滚动加载更多"提示（及三语死字符串 koil_ov_scroll_more）
9. **#9 分组/前驱映射 remember 缓存**：`groupBy` 月分组与 `buildPrevMap` 由每次重组都重算改为 `remember(records)`，滚动/弹窗等无关重组不再重复构建
10. **#10 记录卡稳定 key 与回调**：`items(key = 记录 id)`，长按回调 `remember` 稳定（不再每卡每次新建 4 个 lambda）；顺手删除 `RecordCard` 中从未被使用的 `onDelete/onEdit` 死参数，未变化卡片滚出/滚入可正确跳过重组
11. **#11 滚动监听改 snapshotFlow**：返回顶部按钮显隐由 `LaunchedEffect(scrollState.value)`（每像素变化触发）改为 `snapshotFlow { firstVisibleItemIndex/Offset }.distinctUntilChanged()`，仅在显隐翻转时更新状态

---

## v1.5.16（versionCode 216）— 2026-10-27（基于 v1.5.15，稳定性 hotfix）

> 深度压力测试 18 项问题中的低风险稳定项，按风险递增分批，本版为第一批（不涉及 DB schema/大改）。

### 🔴 数据正确性
1. **#2 录入应付金额 0 元修复（含存量数据）**：
   - 新数据：`AddViewModel` 无参考价且未手填应付时，应付由「0.0」改为「按实付入账」（对齐补录卡），避免总览"优惠金额/每公里成本"虚高
   - 存量：DAO 新增 `fixZeroPayable()`（`UPDATE koil_record SET payableAmount = actualAmount WHERE payableAmount = 0 AND actualAmount > 0`），`KOiLApplication` 启动后按一次性 DataStore 标记 `KOiL-record-payable-zero-fixed` 回填一次（不走 Room Migration，与现有一次性标记风格一致，不清库）
2. **#16 编辑应付算不出回退实付**：`saveEdit` 应付为空时由「保留旧应付」改为回退本次实付（对齐补录卡），双重兜底
3. **#15 CSV 导入里程规则统一严格递增**：补录/编辑/导入三处统一——里程 `<` 改 `<=`、`>` 改 `>=`，等于相邻记录也判冲突（修导入/录入阈值不一致）

### 🔴 保存健壮性（#1 #5）
4. **补录/编辑保存防重入**：新增 `isSavingBackfill` / `isSavingEdit` 状态（对齐首页 isAdding），保存期间按钮置灰，快速双击不再并发 insert 撞唯一约束（SQLiteConstraintException）
5. **保存失败不再静默**：补录/编辑 `catch(_:Exception){}` 改为捕获后置 `saveError=true`，弹窗显示红色「保存失败，请重试」条（新增三语 koil_ov_save_failed）；用户重新输入即清除；打开/取消弹窗重置保存态

### 🟡 输入与交互（#13 #17 #18）
6. **#13 输入框过滤多个小数点**：新建 `ui/component/FieldFilters.kt` 的 `sanitizeDecimalInput()`（仅保留数字 + 第一个小数点），录入卡 FormField / 补录卡 / 编辑卡 EditField 统一替换原 `filter{ 数字或点 }`（原过滤允许 "1.2.3" 致 toDouble=null）
7. **#17 补录日期回退限制去掉**：KOiLDatePicker 月份切换原最多回退 120 个月（10 年）→ 改为可无限往前（选①，"早于第一条记录"由业务校验拦截）；未来月份仍不可达
8. **#18 启动定位不再反复写 DataStore**：MainViewModel 定位到的省份与当前默认省一致时不写，避免每次启动无谓写入

### 🟡 清理与崩溃（#12 #14）
9. **#12 彻底移除「加油提醒里程」死功能**：删除 AppSettings 的 KEY_REMIND_THRESHOLD / DEFAULT_REMIND_THRESHOLD / remindThreshold Flow / setRemindThreshold；SettingsViewModel 字段/combine（17→16 flow，重排 args 索引）/setter/导入导出映射；三语 strings 删除 koil_set_remind_mileage(_sum)（保留「提醒与校验」section 标题，异常/偏差/油箱三项仍在）。旧备份含该字段时导入忽略（兼容）
10. **#14 合并重复的全局崩溃捕获**：删除 CrashHandler.kt（写 files/crash-*.txt），统一由 CrashReporter（写 files/crash.txt + Download/KOiL-crash.txt）在 KOiLApplication.onCreate 最早安装（覆盖启动期崩溃）；MainActivity 移除重复安装

---

## v1.5.15（versionCode 215）— 2026-10-27（基于 v1.5.14）

### 🔽 日期可选择时补上下拉箭头图标（用户指出：油号框箭头对着的小图标没进版）
- **KOiLDateField**：`onDateSelected != null`（日期可选择：补录卡）时，日期值右侧显示 **DropdownArrowEndAction** 下拉箭头（对齐油号/省份字段，表示可点击选择）；只读（编辑卡/首页录入卡 `onDateSelected=null`）不显示
- 视觉与油号框（GradePriceSelect）/省份框（ProvinceSelectField）保持一致

## v1.5.14（versionCode 214）— 2026-10-27（基于 v1.5.13）

### ✏️ 编辑/补录卡文案区分 + 应付"待计算"占位（用户验收反馈）
1. **校验通过文案区分**：编辑卡 → 「校驗通過，可以**儲存**」（新增 koil_ov_edit_ready，zh/en/zh-rTW 三语）；补录卡保持「校驗通過，可以**補錄**」（BackfillPrecheckHint 新增 readyText 参数）
2. **应付金额未计算时显示「待計算」**：
   - miuix TextField 无 placeholder 参数 → EditField 用 Box 叠加层实现占位（value 为空时显示，14sp 半透明，与输入左对齐）
   - ViewModel：油量或参考价缺失时 updateEditPayable/updateBackfillPayable **清空**应付（而非保留旧值）→ 显示"待计算"
   - precheck 完成后（参考价就绪）立即刷新应付自动算，避免打开弹窗停留"待计算"
   - 编辑卡打开瞬间应付不再显示旧记录值，先"待计算"，参考价到位后按 油量×参考价 自动算出
3. 应付字段图标保持 Money（¥）不变（用户截图确认的图标）

## v1.5.13（versionCode 213）— 2026-10-27（基于 v1.5.12）

### 🔵 补录卡保存按钮变蓝 + 编辑卡 1:1 复刻补录卡（用户确认最终口径）
1. **补录卡保存按钮**：全部校验通过 → 蓝色（KOiL 主色 #0071E3）可保存；否则灰色置灰（disabledColor=surfaceVariant 60%），对齐首页录入卡 addRecord 按钮
2. **编辑卡 1:1 复刻补录卡**（字段结构/图标/红色错误态/里程区间提示/校验提示条/应付只读自动算/保存置灰+变蓝）：
   - **日期只读**：KOiLDateField `onDateSelected=null`，日历不可再改（唯一差异）
   - **里程区间提示**：打开编辑弹窗即显示该日期（只读）的里程区间（prev+1 ~ next-1，无界侧省略），里程仍可修改
   - **全量校验对齐补录卡**：早于第一条（或同日里程更小）、同日同里程重复、双向里程链（prev 需大于/next 需小于）、价格偏差 A+B（阈值=设置页）、实付>应付、油箱容量——**校验排除当前编辑记录自身 id**（DAO 新增 `getEarliestExcluding` / `getSameDayRecordsExcluding`，避免与自己判重/误判 beforeFirst）
   - **红色错误态**：里程（链冲突/同日重复）、油量（超油箱）、实付（偏差超阈/实付>应付）→ #FF3B30 边框/图标/标签
   - **应付只读自动算**：油量×参考单价即时刷新（对齐补录卡 updateBackfillPayable）
   - **校验提示条**：橙色拦截/绿色通过（复用 BackfillPrecheckHint）
   - **保存按钮**：editReady 全通过 → 蓝色可保存；否则置灰

## v1.5.12（versionCode 212）— 2026-10-27（基于 v1.5.11）

### 🎨 补录弹窗 UI 对齐首页录入卡（用户 4 条需求，仅補錄卡片）
1. **里程区间提示移位**：选日期后的「本区间可输入里程」提示从弹窗底部移到**日期行与本次里程行之间**（紧跟日期下方、里程输入上方）
2. **应付金额只读自动计算**：对齐首页 updatePayable——应付 = 加油量 × 参考单价（按日期/省份/油号），输入油量即时刷新；应付字段不再可编辑
3. **字段级红色错误提醒**：对齐首页 FormField——
   - 里程：里程链冲突（需大于/需小于）或同日重复 → 红色边框/图标/标签
   - 加油量：超过油箱容量 → 红色
   - 实付金额：价格偏差超阈值或实付>应付 → 红色
   - 输入框底色/标签/图标/错误色与首页录入卡一致（#FF3B30）
4. **字段图标对齐首页**：里程=Speed、加油量=WaterDrop、应付=Money、实付=Money
- 编辑弹窗字段同步适配新组件签名（仅补图标+键盘类型，行为不变）

## v1.5.11（versionCode 211）— 2026-10-27（基于 v1.5.10）

### 🔁 补录卡校验规则按用户确认清单调整（首页录入不动）
用户逐条确认 11+1 条规则，补录页校验链改为（仅補錄卡片，首页保持原状）：
- **保留**：日期可编辑（未来禁选）· 早于第一条（或同日里程更小）拦截；同日同里程重复拦截；双向里程链（prev 需大于 / next 需小于）
- **移除 Web 复刻拦截**：同日相似记录提醒（±2L/±10元）→ 无；油量下限 <1L → 改为 >0；油量上限 >200L → 无（油箱容量兜底）；实付金额 >1500 → 无
- **搬入首页口径**：价格偏差 A（参考价偏差）+ B（无参考价回退实付vs应付，阈值=设置页可配）；实付 > 应付 一律拦截；加油量 > 油箱容量 拦截
- **保存条件**：isReady 全部校验通过才可保存（任一拦截 → 按钮置灰）
- **新增里程范围提示**：选择日期后，下方提示该区间可输入里程范围（prev+1 ~ next-1，无界侧省略），仅信息提示不阻断
- 三语文案：新增 koil_ov_bf_mileage_range/_min/_max；清理废弃 key（similar/oil_too_small/oil_too_large/actual_too_large/actual_over_payable/discount_too_large）

## v1.5.10（versionCode 210）— 2026-10-27（基于 v1.5.9）

### 🔁 补录卡逻辑全面复刻 Web 版（用户提供 Web 工程源码，直接复刻）
补录（首页 + 補錄）校验链对齐 Web `koil-records.service.ts` create/update 全集：
- **日期边界**：补录日期早于第一条记录（或同日但里程更小）→ 拦截「補錄不能早於你的第一筆記錄」
- **金额/油量上限**（复刻 `validateOilAmountAndAmount`）：
  - 加油量 <1L → 過小拦截；>200L → 超上限拦截
  - 实付金额 >1500 元 → 拦截
  - **实付/应付比值校验**：应付基准优先用用户输入，否则参考价×油量；
    ratio >1.01 → 「實付金額高於應付金額」；ratio <0.75 → 「優惠超過25%」拦截
- **里程链校验**（复刻 `checkMileageChain`）：与前一日期记录冲突（里程需大於）/
  与后一日期记录冲突（里程需小於），提示携带对端日期与里程
- **重复校验**（复刻 `checkDuplicate`）：同日同里程 → 拦截「該日期和里程的加油記錄已存在」；
  同日存在油量±2L 且金额±10 元相似记录 → 橙色提醒；同日已有多条 → 计数提示
- **保存按钮联动**：任一校验不过 → 保存按钮置灰不可点（预检全通过才允许提交）
- 移除旧的口径（偏差百分比 checkDeviation 拦截），统一走 Web 比值口径

### 🆕 历史记录按月分批加载 + 删除确认弹窗（用户需求）
- **按月分批加载**：默认只展示最近 1 个月记录，滚动到底**逐月续载**（不再一次性全铺开）；
  月份总数提示「捲動載入更多記錄」仅在还有未加载月份时显示；切换范围筛选时重置为最近 1 个月
- **删除确认弹窗**：长按 → 点红色删除 → 弹「確定要刪除這筆加油紀錄嗎？」确认框
  （取消 / 紅色刪除按钮），确认后才真正删除，防误触

## v1.5.8（versionCode 208）— 2026-10-27（基于 v1.5.7）

### 🐛 彻底移除卡片内"长方形"（根因：按压涟漪 + 柔光色块）
- **根因一**：卡片内容区 `combinedClickable` 自带按压涟漪，且内容 Column 无圆角裁剪，
  点击/长按瞬间在卡片内出现**直角长方形涟漪**——已用 `indication = null` 彻底禁用涟漪
- **根因二**：v1.5.7 的柔光色块层（主色 8%）在卡片内仍显示为长方形色块——已整体删除
- 现在长按选中：**卡片内零色块、零框线、零涟漪**，只有全屏遮罩 + 中央圆按钮

## v1.5.7（versionCode 207）— 2026-10-27（基于 v1.5.6）

### 🎨 长按选中态去框线（用户验收反馈：圆角卡片内出现长方形色块难看）
- **彻底移除卡片描边框**（v1.5.6 的 1dp 描边 = 圆角卡片内再套一个长方形，突兀）
- 长按选中仅保留**与卡片同圆角的柔和主色光晕**（8%），200ms 淡入淡出
- 无任何独立长方形/框线，数据区（含加油量/实付金额）随整卡柔和浮出

## v1.5.6（versionCode 206）— 2026-10-27（基于 v1.5.5）

### 🎨 长按选中态柔和化（用户验收反馈迭代）
- 初版为 2dp 主色硬描边（突兀），改为**柔和选中态**：
  - 1dp 主色 40% 透明细描边 + 卡片内主色 8% 柔光晕
  - 200ms 淡入淡出过渡，弹出收起不突兀
  - 数据区（含加油量/实付金额等）随卡片整体浮出，操作对象一目了然

## v1.5.5（versionCode 205）— 2026-10-26（基于 v1.5.4）

### 🐛 长按操作层升级为全屏遮罩（用户验收反馈）
- 之前遮罩只盖卡片本身，点击卡片外区域无法收起
- 改为**全屏遮罩**：长按弹出圆按钮后，点击屏幕任意位置（含卡片外/空白/其它卡片）均收起；
  圆按钮通过卡片 `onGloballyPositioned` 上报全局坐标定位，仍悬浮在长按卡片中央
- 编辑/删除执行后自动收起并清空定位状态

## v1.5.4（versionCode 204）— 2026-10-26（基于 v1.5.3）

### 🧹 记录卡交互重构 + 補錄按钮（讨论确认后统一交付）
- **长按操作（替代左滑 + ⋮）**：长按记录卡 → 卡片中央浮现两个圆形按钮
  （✏️ 编辑·蓝 / 🗑️ 删除·红，淡入缩放动画），卡片内容变暗；
  点击圆按钮执行后自动收起；点击遮罩空白收起；长按另一张卡自动切换
- **点击卡片不再直接进入编辑**（原 clickable 进编辑移除；按钮显示时点击=收起）
- **移除左滑操作区**（SwipeActionRecordCard 删除——此界面垂直滚动+系统返回手势
  会吞水平拖动，左滑不稳定，弃用）
- **移除 ⋮ 菜单**（三入口收敛为长按单入口，卡片顶行更干净）
- **補錄改胶囊按钮**：「+ 補錄」纯文字 → 主色浅底 + 加号图标 + 「補錄」，
  点击区域加大（文案去 "+"，图标已表达）
- 清理无用 import（Dropdown/手势/MoreVert 等 10 行）

## v1.5.3（versionCode 203）— 2026-10-26（基于 v1.5.2）

### 🧹 总览页修复 + A/C 排版重构（讨论攒齐后统一交付）
- **顶部模块 A+C 排版**：
  - 主次分层：平均油耗 / 每公里成本 升级为主卡（大数字 24sp 粗体 + 較上期箭头），
    總里程/總加油量/總實付/總應付 为次级格（19sp）
  - 實付 vs 應付 对比条：`實付較應付節省 ¥459`（应付>实付=节省绿色；反之为超支橙色）
- **加载提示条件显示**：「捲動載入更多記錄」仅当还有更多记录时显示（17<20 全显示时不再误导）
- **移除冗余"加载更多"按钮**：滚动到底自动加载已覆盖分页，双入口收敛为单入口
- **平均油價单位修正**：`¥7.22/元/L` → `¥7.22/L`（¥ 与 元 重复）
- **⋮ 更多按钮实现**：点击弹出菜单（編輯/刪除，与左滑操作一致）——此前只更新状态无 UI 弹出
- 趋势图 9 个日期标签为"每 2 点显示 1 个"防拥挤采样（17 点全部绘制），非缺陷

## v1.5.2（versionCode 202）— 2026-10-26（基于 v1.5.1）

### 🐛 查看颜色二次修正（v1.5.1 J 项未到位）
- 根因：设置页右侧文案（匯出/匯入/備份等 SettingRow value）实际用的是
  `MiuixTheme.colorScheme.onSurfaceVariantActions`（Miuix 交互动作文案扩展色），
  不是 onBackground——v1.5.0 primary（蓝）/ v1.5.1 onBackground（正文白）均与右侧不同色
- 修正：数据库卡「查看」文字 + 箭头 Icon tint 改为 `onSurfaceVariantActions`，
  与右侧文案**完全同色**（深浅色自适应）

## v1.5.1（versionCode 201）— 2026-10-26（基于 v1.5.0）

### 🐛 两项验收反馈（设置页）
- **J 查看颜色**：数据库卡「查看」文字+箭头由主题 primary（深色亮蓝）改为
  `MiuixTheme.colorScheme.onBackground`——与设置页右侧文案（匯出/匯入/備份等）同色，非蓝色
- **K API Key 数据源开关**：去掉 v1.5.0 的「编辑」开关，改为**数据源开关**：
  - 开关**开** = 用 EIA 估算，Key 输入框**完全隐藏**（防误改）
  - 开关**关** = 直接用腾讯，**显示输入框**供修改 Key（改完再打开即启用新 Key）
  - 开关状态**持久化**（`KOiL-eia-enabled`），默认开
  - CrudeOilEstimator 按开关路由：关→直接腾讯（不请求 EIA）；开→EIA 失败才降级腾讯

## v1.5.0（versionCode 200）— 2026-10-26（基于 v1.4.194）

### 🧹 八项修复（讨论攒齐后统一交付）
- **B 窗口日顺延改循环**：AddViewModel 顺延从"只加 14 天一次"改为 `while` 循环顺延
  → 长期未开 App（>14 天）且存档过期很远时不再显示过去窗口（覆盖春节长周期）
- **C verifyProvince 死代码清理**：apizero 移除后 matches（双源一致=搁浅）/bothPresent
  （不一致入 pending）分支永不可达 → 删除，收敛为 abapi 单源有价即自动入库；
  搁浅窗口仍由 seedRemainingProvinces 补搁浅处理（功能不变）
- **D 数据库归 V1**：AppDatabase version 3→1，删除 v2/v3 历史迁移与 schemas/2.json、
  3.json——1.5.0 为干净正式起点。⚠️ **已装旧版必须卸载重装**（Room 禁止版本回退，
  覆盖升级会崩溃）；koil_record 加油记录随卸载清空（作者已确认可接受）
- **E 上次加油卡片实时检测**：KOiLRecordDao 新增 `observeLatest(): Flow`，
  AddViewModel 常驻订阅 → 记录在任意入口（本页/记录页/明细页）增删改，首页
  「上次加油」卡片实时刷新
- **F 合并预测+折合徽章**：首页窗口板块徽章①②合并为单个——
  `预计上调270元/吨(估算) 折合上调约0.21元/升`（估算标注移入括号紧跟数字、
  折合带方向，跟随预测正负；三语新增 up/down 折合 string）
- **G 油箱容量副标题**：`超量录入校验` → `油箱容量 50L · 超量录入校验`（数值实时同步）
- **H 数据库卡三处**：版本号标题 20sp Bold→17sp Medium（对齐下方开关行）；
  「查看」颜色写死 KOiLColors.Primary→MiuixTheme.colorScheme.primary（深浅色自适应、
  与设置页右侧 Switch/数值同步）；标题行下加 0.5dp 隔断横线
- **I EIA Key 防误改**：副标题精简为 `原油估算源（可修改，留空降级腾讯）`（一行）；
  标题行右侧加「编辑」开关——默认关 → 输入框只读灰色；打开才可编辑（实时保存，
  关开关后 Key 保持修改后的值）

## v1.4.194（versionCode 199）— 2026-10-26（基于 v1.4.193）

### 🗑️ 彻底移除 apizero（已收费）+ 冷启动窗口板块秒开
- **背景**：apizero（极数本源）2026-10-23 起匿名接口收费限流（4030 需付费 Key），用户
  拒绝付费 → 全部功能已有独立兜底，本版**彻底摘除**该依赖。
- **apizero 使用点清理（4 处）**：
  1. **双源核验次源**（fetchFromApiZero）→ `fetchBothSources` 改为 abapi 单源
     （保留 Pair 签名，secondary 恒失败；verifyProvince 自动走「单源→有价源入库」，
     不再产生 pending）
  2. **预测专接口**（fetchForecast + ForecastResponse/ForecastData）→ 全删
  3. **窗口日历锚点③**（getAdjustDates）→ 锚点链 5 级缩为 4 级：
     ①xxapi → ②ruseo → ③abapi 曲线 → ④本地推算（均已有）
  4. **同步链路**每轮净减 2 个 apizero 请求
- **预测幅度**：恒走 EIA 原油估算（v1.4.193 已落地），UI 标注「估算」；调价窗口日恒走
  本地推算窗口日历（v1.4.192 已落地）——**两者与 apizero 零关系**。
- **冷启动窗口板块延时修复**：`loadPriceAdjustInfo` 拆两段——
  ① 窗口日/版本号（纯本地）**立即渲染**，不再等网络；
  ② EIA 估算（含网络请求）**独立异步**，完成后补更新预测幅度，不阻塞首屏。
- **文档**：API_SOURCES.md 数据源表重构（apizero 移入付费段并标注已移除）、
  OilPriceNetwork 头注释更新（单源 + 兜底链）。
- **验证**：compileDebugKotlin 通过；覆盖升级/全新安装行为不变（窗口日历、EIA Key、
  估算标注逻辑未动）。

## v1.4.193（versionCode 198）— 2026-10-26（基于 v1.4.192）

### 🔮 预测涨跌幅度本地估算（apizero 收费/限流后不失效）
- **背景**：apizero 已收费限流（4030「匿名试用次数已用完 used=63/daily=5」），App 匿名
  调用被永久拒绝 → 预测幅度（forecast）真机永远拿不到。此前唯一提供预测幅度的免费源
  已不可用（通用搜索无替代 API）。
- **方案**：**原油价格本地估算**——成品油调价 = 前 10 个工作日国际原油均价 vs 上期
  10 日均价的变化率 × 经验系数（1% ≈ 56.5 元/吨，新闻实证 5.4% → 305 元/吨）：
  - **主源：EIA 官方 API**（布伦特现货 RBRTE 日度，免费注册 Key **内置为默认值**，
    含 10000+ 行完整历史——10 日均值比较与用户打开 App 频率无关，断档不存在；
    配额 1000 次/日；Key 失效/留空 → 降级）
  - **降级：腾讯财经实时**（hf_OIL 布伦特期货，公开免 Key）——现价 vs 昨收当日变化率
    （方向可靠、幅度粗）
  - 显示标注「估算」；两源皆失败 → 维持「待检测」不显示假值
- **实现**：
  1. `OilPriceNetwork`：新增 `fetchEiaBrentDaily`（EIA v2 API 解析）+ `fetchTencentBrent`
     （qt.gtimg.cn 解析：index0 现价 / index7 昨收）
  2. 新增 `CrudeOilEstimator`（data/oil/）：EIA 10+10 日均值 → 变化率 → ×56.5；
     腾讯当日涨跌幅降级
  3. `AppSettings`：新增 `eiaApiKey`（默认内置用户注册 Key）
  4. `AddViewModel`：forecast（apizero）不可用 → 调 estimator，`forecastEstimated` 标记；
     `AddScreen` badge 前缀「估算」（三语）
  5. 设置页「油价数据库」区新增 **EIA API Key** 输入框（可改/留空禁用 EIA，仅腾讯降级）
- **性能**：仅 apizero forecast 缺失时才发起 EIA 请求（正常时零开销）；首屏仍 Room
  本地流秒开，估算全部异步后台，失败不阻塞不重试。
- **边界**：估算为近似值（官方三地均值挂靠 + 调控措施会偏离），仅作方向参考；
  apizero 若恢复/未来提供 Key 仍优先用官方 forecast。

## v1.4.192（versionCode 197）— 2026-10-26（基于 v1.4.191）

### 🔮 预测窗口日降级兜底（apizero 不可用时不失效）
- **背景**：预测功能（下个调价窗口日 + 预测涨跌幅度）实际单点依赖 apizero——实测
  xxapi/ruseo 不提供 next_adjustment / forecast 字段，`fetchBothSources` = abapi + apizero，
  下个窗口日 100% 来自 apizero（forecast 专接口 → 回退 oil-price next_adjustment）。
  apizero 挂 → 预测窗口日为空（显示待检测）。
- **修复**：下个窗口日回退链补上**本地推算窗口日历**（`getAdjustDates` 下个未来窗口，
  与数据库卡「下个窗口」同一来源——该推算 v1.4.184 已实现，本次仅复用兜底）：
  1. `performPriceCheck`：`nextAdjustDate = apizero forecast → 聚合文本 → 本地窗口日历`
  2. `AddViewModel.loadPriceAdjustInfo`：接口 next_adjustment 存档为空时 → 读本地窗口日历
     下个未来窗口（两者皆无才不显示）。
- **边界**：预测**涨跌幅度**仍仅 apizero 提供（无替代数据源）——apizero 挂时幅度显示
  「待检测」，窗口日正常。数据库/记录页/窗口日历不受 apizero 影响（①②④⑤兜底）。

## v1.4.191（versionCode 196）— 2026-10-26（基于 v1.4.190）

### 🗓️ 跨年维度支持（2027 自动滚动，当年口径从零重计，往年可回看）
- **背景**：当前窗口日历只生成"今年"全年、记录页统计为全库累计——跨年到 2027 后
  缓存日历不会自动滚动（卡在 2026）、记录页 2026+2027 混排、统计混算，无法区分年份。
- **修复**：
  1. **窗口日历跨年自动滚动**（OilPriceDbSyncer.getAdjustDates）：内存缓存 + 持久化缓存
     命中时检测「缓存年份 ≠ 今年」→ 清缓存 → 锚点 5 级链重新推算新年全年日历。
     跨年后调价次数（数据库卡）自动从零重计、新窗口正常增量入库，**无需清库重装**。
  2. **记录页年份维度**（OilPriceDetail）：默认显示**当年**记录与统计（列表、調價/擱淺/
     涨跌全部按所选年份算）；顶部新增年份切换 chip（如「2026 年 / 2027 年」），
     可切回看往年；2027 首条涨跌无前值（当年从零开始），2027 首窗口搁浅维持价
     接 2026 年底价（v1.4.182 已具备跨年衔接）。
  3. **数据库卡口径**：調價 N = 当年已过窗口数（跨年后自动清零重计）；記錄 N = 全库累计
     （2480 + 逐年增量，不按当年）。
- **全新安装 1-01 只灌当年**：seedProvince 已有 `pt.date < yearStart` 过滤（当年 1-1），
  abapi 返回 13 个月曲线中去年部分被丢弃 → 1 月 1 日新装只灌新年，不灌去年。

## v1.4.190（versionCode 195）— 2026-10-26（基于 v1.4.189）

### 🔧 覆盖升级恢复补全（v1.4.189 升级不触发补灌的修复）
- **问题**：从 v1.4.188 覆盖升级到 v1.4.189 后记录页仍全搁浅——v1.4.188 破坏的库
  20 条全是搁浅记录：`covered` 已达标（20≥20）不触发补灌，`hasStrandedGap` 检测不到
  「搁浅占位」不算缺口 → `seedProvince`（变化点覆盖搁浅恢复逻辑）压根不跑。
- **修复**：新增 `priceDbReseed189` 一次性标记——**189+ 升级后首次立即更新强制全量补灌
  所有省**（`forceReseed`），让「变化点覆盖搁浅」恢复被 188 误替换的调价记录；
  真实搁浅窗口（1-06、10-15，abapi 无变化）不受影响，一次后恢复常规触发条件。
- **效果**：被 188 破坏的库，覆盖升级到 v1.4.190 后点「立即更新」即自动恢复为
  18 调价 + 2 搁浅。

## v1.4.189（versionCode 194）— 2026-10-26（基于 v1.4.188）——紧急修复

### 🚨 修复 v1.4.188 引入的「全部记录变搁浅」灾难
- **根因**：v1.4.188 补搁浅逻辑误删**库里已有的正常调价记录**——`hasChange` 只检查
  「本次新增的变化点」，库里已有的调价记录（幂等跳过未入 entities）不算"有调价"
  → 每个已过窗口都被判"无调价"→ 删除已有调价记录 + 替换成搁浅（记录页 20 条全变「擱淺」）。
- **修复**：
  1. **补搁浅回滚**：只补「库里无记录」的已过窗口（`!hasChange && 键不在库`），
     **已有的记录一律不删不动**。
  2. **变化点覆盖搁浅**（恢复被 188 误替换的记录）：变化点插入时，若库里该生效日是
     搁浅记录（isStranded=true）但 abapi 该窗口有变化 → **删除搁浅 + 插回调价记录**。
- **效果**：覆盖安装 v1.4.189 后点「立即更新」→ 被误删的调价记录自动恢复
  （abapi 有变化的窗口从搁浅还原为調價），真实搁浅窗口（1-06、10-15）保留。

## v1.4.188（versionCode 193）— 2026-10-26（基于 v1.4.187）

### 🔧 数据库卡「调整日」口径修正（方案C：跟随 App 默认省 + 92号汽油）
- **根因**：不同省份/油号价格各不相同，数据库卡判定/显示原用「任意省任一油号」记录
  （getByDate LIMIT 1）→ 显示的是其他省的价格（如 10-16 显示 8.57，实为某省价格，
  广东 92# 是 8.63）；且 10-16 库里残留 verifyProvince 抢插的假调价记录（isStranded=false）
  把 10-15 搁浅顶成「調價」。
- **修复（方案C）**：
  1. **口径 = 默认省（settings.defaultProvince，App 默认广东）+ 92号汽油**：
     - 调价：`調整日10-15 · 調價至 8.63 元/升`（默认省 92# 生效价）；
     - 搁浅：`調整日10-15（擱淺，維持 09-25 價格¥8.63）`（默认省 92# 上一条调价记录）。
     - DAO `getLatestNonStranded` 改为 `(province, grade, date)` 精确查询。
  2. **搁浅判定补 isStranded**：库内该生效日是搁浅记录（isStranded=true）→ 走搁浅分支，不再误判调价。
  3. **补搁浅清假记录**：seedProvince 补搁浅时，库内生效日若存在「非搁浅」假调价记录
     （verify 抢插/降级遗留）→ 删除后补搁浅（恢复 10-16 = 維持 09-25 ¥8.63）。

**效果**：设置页 10-15 正确显示 `調整日10-15（擱淺，維持 09-25 價格¥8.63）`；
覆盖安装后点「立即更新」触发补灌即自动清 8.57 假记录并补回搁浅。

## v1.4.188（versionCode 193）— 2026-10-26（基于 v1.4.187）

### 🔧 数据库卡「调整日」口径修正（不同省份/油号价格不同）
- **根因**：数据库卡判定/显示原用「任意省任一油号」记录（getByDate LIMIT 1）→ 显示的是其他省的
  价格（如 10-16 显示 8.57，实为某省价格，广东 92# 是 8.63）；且 10-16 库里残留 verifyProvince
  抢插的假调价记录（isStranded=false）把 10-15 搁浅顶成「調價」。
- **修复**：
  1. **固定口径**：判定与价格一律用 **默认省「广东」+ 92号汽油**：
     - 调价：`調整日10-15 · 調價至 8.63 元/升`（广东 92# 生效价）；
     - 搁浅：`調整日10-15（擱淺，維持 09-25 價格¥8.63）`（广东 92# 上一条调价记录）。
     - DAO `getLatestNonStranded` 改为 `(province, grade, date)` 精确查询。
  2. **搁浅判定补 isStranded**：库内该生效日是搁浅记录（isStranded=true）→ 走搁浅分支，不再误判调价。
  3. **补搁浅清假记录**：seedProvince 补搁浅时，库内生效日若存在「非搁浅」假调价记录
     （verify 抢插/降级遗留）→ 删除后补搁浅（恢复 10-16 = 維持 09-25 ¥8.63）。

**效果**：设置页 10-15 正确显示 `調整日10-15（擱淺，維持 09-25 價格¥8.63）`；
覆盖安装后点「立即更新」触发补灌即自动清 8.57 假记录并补回搁浅。

## v1.4.187（versionCode 192）— 2026-10-26（基于 v1.4.186）

### 🖥 数据库卡展示修正
- **搁浅维持信息修正**：`調整日10-15（擱淺，價格維持10-16）` 原把「生效日」当「維持日」显示
  → 改为显示**上一条调价记录**：`調整日10-15（擱淺，維持 09-25 價格¥8.63）`（新增
  `getLatestNonStranded` DAO 查询 + ViewModel 取维持日期/价格；无上一条时显示 `（擱淺）`）。
- **调价判定修正**：数据库卡判定「最近窗口是否调价」原查「窗口日」，但库内记录生效日 = 窗口日+1
  → 改为查「窗口日+1」，**调价窗口不再被误判为搁浅**。
- **行2 布局**：`下個窗口` 与 `最近更新` 紧凑同行、竖线分隔（去掉推到最右的 Spacer）。
- **日期格式**：最近更新 `10/26 10:40` → `10-26 10:40`（统一用 `-`）。

## v1.4.186（versionCode 191）— 2026-10-26（基于 v1.4.185）

### 🔧 修复「10-15 搁浅丢失 + 10-16 假记录」（重新安装后出现）
**根因链**：全新安装时若锚点链临时全失败（apizero 挂 + xxapi/ruseo 失败）→ 落 abapi 锚点（09-24）
推算日历**漏 10-15 窗口** → 补搁浅只补 1-06、漏 10-15；随后点立即更新 → `verifyProvince`（双源核验）
把窗口日当天「双源一致价」**抢插为调价记录**（isStranded=false，如 10-16 ¥8.63「維持不變」）；
补灌触发条件 `covered < threshold` 因假记录凑满条数 → 不再补 → 10-15 搁浅永久缺失。

**修复（3 处）**：
1. **verifyProvince 同价不插**：双源一致/单源 价格与库内最近调价价相同 = 搁浅窗口 → **不插调价记录**
   （防 isStranded=false 同价假记录产生；10-15 由补灌补搁浅）。
2. **补灌触发加「搁浅缺口检测」**：新增 `hasStrandedGap`——已过窗口日+1 无记录、
   或存在同价假记录占位 → 即使 covered ≥ threshold 也触发 seedProvince（清理假记录 + 补搁浅）。
3. **升级一次性清旧日历缓存**：新增 `priceDbCalendar186Migrated` 标记，升级后强制清
   `priceDbAdjustDates` 重算（错日历不再长期复用）。

**效果**：升级后首次立即更新 → 清错日历 → 重拉锚点（xxapi/ruseo 正常 → 日历含 10-15）→
检测到搁浅缺口 → seedProvince 清 10-16 假记录 + 补 10-15 搁浅 → 擱淺次数恢复 2。
无数据源/无逻辑回归，仅写入路径与触发条件修正。

## v1.4.185（versionCode 190）— 2026-10-26（基于 v1.4.184）

### 📝 建库代码内嵌机制摘要 KDoc
- `AppDatabase.kt` 类前新增 KDoc：数据模型（主键/生效日=窗口日+1/isStranded）、
  一次性灌库+增量模型、迁移史（v1→v2→v3）、完整机制指向 docs 文档。
- `OilPriceDbSyncer.kt` 类注释扩展：锚点 5 级链（v1.4.184 顺序）、灌库流程
  （幂等/变化点/补搁浅/首窗口上年末价/同价假记录清理）、purge 只清当年、统计口径要点。
- 目的：打开建库/灌库代码即见核心机制；完整细节仍以 docs/KOiL油价数据库机制.md 为准。
- 无逻辑改动。

## v1.4.184（versionCode 189）— 2026-10-26（基于 v1.4.183）

### 🔀 锚点 5 级链顺序调整 + 数据库机制文档
- **顺序调整**（apizero 近期不稳定，昨晚出现获取不到）：
  `① apizero → ② ruseo → ③ xxapi → ④ abapi → ⑤ 推算` 改为
  **`① xxapi → ② ruseo → ③ apizero → ④ abapi → ⑤ 本地推算`**；
  同源互备（ruseo/xxapi）内部也改为 **xxapi 优先**（fetchAnchorWindowDate 顺序交换）。
- **新增文档**：`docs/KOiL油价数据库机制.md` —— 完整记录数据模型、锚点链、
  窗口推算、灌库/搁浅/清理/统计口径/迁移/已知边界，防后续遗忘；随源码包交付。

## v1.4.183（versionCode 188）— 2026-10-26（基于 v1.4.182）

### 🗄 一次性灌库 + 持续增量模型（跨年保留历史）
- **背景**：灌库（31省×4油号×窗口全量）本是一次性起点，之后新窗口日（调价/搁浅）
  应持续增量累加；但「清理假日期」（purge）跨年后用当年窗口日历做白名单，
  会把往年记录（如 2026）当成"假日期"全清 → 破坏累加。
- **修复**：purge 只清「当年窗口日历范围内的假日期」（生效日在当年 1-1~12-31
  且非窗口日+1，如降级灌入的 09-27）；**往年日期一律保留**。
- **效果**：2026 起点灌库后，2027 年不重灌、不清库，历史记录持续累加；
  记录页/统计基于库内当年+历史数据。当年内行为不变（假日期照清）。

## v1.4.182（versionCode 187）— 2026-10-26（修复 v1.4.181 搁浅未真正入库）

### 🔧 修复：搁浅记录没灌进库（用户实测擱淺次數 0）
- **根因 1（1-06 首窗口搁浅没补）**：补搁浅的维持价查询限定「当年 1-1 起」，
  但 abapi 2026 曲线 1-06 前无数据点（实测年内首点 = 01-23）→ 维持价取不到 → 整条跳过。
  **修复**：维持价查询去掉当年下界，回退到上年最后一个调价点（2025-12-24 ¥6.73 = 上年末价），
  1-06 搁浅正常入库（生效日 1-07，顯示「維持 上年價格」）。
- **根因 2（10-15 搁浅没补）**：库里遗留一条历史假记录 10-16（isStranded=false、¥8.63
  与 09-25 同价 = 早期「窗口归档」的假变化点）→ 补搁浅幂等检查「10-16 已在库」→ 跳过。
  **修复**：seedProvince 开头清理「同价假记录」（isStranded=false 且价格与上一条相同——
  真实调价必然变价，同价且非搁浅只能是假点）→ 删除 → 幂等放行 → 补真搁浅（10-16, isStranded=true）。
- **效果**：点「立即更新」后，1-06 + 10-15 两个搁浅窗口全部入库（各 4 油号），
  记录页显示灰色「擱淺」chip ×2，擱淺次數 = 2。清理幂等，重复补灌不重复删。

## v1.4.181（versionCode 186）— 2026-10-26（基于 v1.4.180）

### 🗄 搁浅入库（一步到位，替代展示层补全）
- **背景**：此前库只存价格变化点 → 搁浅（窗口到了没调价）只能靠展示层反推，
  导致：搁浅次数差集口径出错（20−17=3，真实仅 2）、首窗口搁浅（1-06）维持价拿不到
  显示 ¥0.00、缺口统计绕判。**根因：搁浅是真实窗口事件，本应入库。**
- **数据模型**：KOiLPriceEntity 新增 `isStranded`（0=调价 / 1=搁浅窗口）；
  数据库 version 2→3（Migration：ALTER TABLE koil_prices ADD COLUMN isStranded，不清库）。
- **灌库改造**（seedProvince）：
  - **幂等**：按 省|油号|生效日 跳过已存在记录（重灌/重复触发不重复插）；
  - **补搁浅**：对每个已过窗口，无生效记录 → 插入搁浅记录（维持价 = 窗口前最后价格，
    isStranded=true）；**首窗口搁浅（1-06）维持价 = abapi 首点（上年末价）**——不再有 0.0。
  - 补灌覆盖阈值改为「已过窗口数（含搁浅）」，存量库升级后点「立即更新」自动幂等补齐搁浅。
- **展示层**：记录页搁浅条目**直接读库**（删除展示层补全逻辑）；搁浅次数 = count(isStranded)
  **直接准确**（不再差集）；搁浅条目日期显示**窗口日**（10-15，与数据库卡「調整日」一致）；
  调价记录仍显示生效日。走势图不受影响（搁浅同价 = 水平线）。
- **口径变化（已确认）**：记录数从「变化点」变为「窗口全量（含搁浅）」，数字会增大。

## v1.4.181（versionCode 186）— 2026-10-26（基于 v1.4.180）

### 🔧 修正「擱淺次數」口径（多算 1 的问题）
- **问题**：旧口径 `擱淺 = 已过窗口数 − 上調 − 下調` 会把首条「基準價」（无方向、不计入
  上調/下調）所在窗口误算成搁浅 → 恒多 1（如 20 窗口 − 17 = 3，但真实搁浅仅 2 次：
  1-06 与 10-15）。
- **修复**：改用与时间线灰色「擱淺」条目**同一判定**——「窗口日+1 在库内无生效记录 = 搁浅」；
  汇总卡数字与灰色条目数量完全一致（2 = 2）。
- 各省数据完整时该口径 = 真实搁浅；若某省缺拉记录（数据缺口）会略偏高，锚点修复后
  补灌完整基本不受影响。

## v1.4.180（versionCode 185）— 2026-10-26（基于 v1.4.179）

### 📋 调价记录页补全「搁浅」条目（展示层，不灌库）
- **背景**：数据库卡「調價 N 次」的口径 = 窗口日历中今年**已发生窗口日数（含搁浅）**，
  但记录页时间线只列价格变化点 → 两者对不上（如 10-15 搁浅在记录页缺失）。
- **改动**：时间线补全已过窗口的**搁浅条目**——灰色行 `10-15 擱淺 · 維持 09-25 價格`
  （日期/价格/标签全灰，维持价 = 该搁浅窗口前最近一次生效价）——**与「調價 N」完全对应**。
- **纯展示层实现**：搁浅条目由 ViewModel 按窗口日历补全（窗口已过 + 该省该油号无生效记录
  = 搁浅），**不灌库**——库内记录数、缺口统计、调价次数口径均不受影响；涨跌统计
  （上調/下調/累計金額/淨漲跌）只统计价格变化点，搁浅不参与。

### ↔️ 记录页省份切换加左右箭头
- **背景**：省份靠横向滑动 chips 切换，无任何可见提示。
- **改动**：省份 chips 行首尾加 **‹ › 箭头图标**——提示「可滑动选择」+ 提供**点击切换**
  （切到相邻省份，与点击 chip 等价）；到首/尾省份时对应箭头变灰（禁用）。
- ViewModel 新增 `selectPrevProvince()` / `selectNextProvince()`。

## v1.4.179（versionCode 184）— 2026-10-26（基于 v1.4.178）

### 🔧 修复「查看」点不动（油价记录页进不去）
- **问题**：数据库卡右上角「查看」无点击响应——v1.4.173 基线里查看按钮只有文字+箭头图标、
  没有 clickable 绑定；v1.4.174 修过但 178 基于 173 基线重建时未合入。
- **修复**：查看按钮改为可点击 Row（clip + clickable → onOpenPriceDetail()，与 174 同款），
  点击进入油价历史明细页。

### 🔤 KOiL 品牌字 Bold 真正生效（原生可变字体轴）
- **问题**：177/178 用 Compose `FontVariation.Settings(FontVariation.weight(700))` 设置可变字体
  wght=700，APK 内字体文件（res/en.ttf = 568KB 可变字体）打包正确但**渲染仍未变粗**——
  Compose FontVariation 在资源字体（res/font）上有兼容问题。
- **修复**：改用 **Android 原生 `Typeface.Builder(resources, R.font.unbounded_variable)
  .setFontVariationSettings("'wght' 700")`**（API 26+ 官方可变字体方式，不依赖 Compose
  FontVariation 层）；API < 26 回退 sans-serif Bold。字体文件仍是 Unbounded-Variable.ttf（wght 200-900）。

## v1.4.178（versionCode 183）— 2026-10-26（基于 v1.4.173 基线）

### 🎯 油价数据库「窗口日/版本号」锚点 5 级回退链（修复待檢測/无法灌入）
- **问题**：apizero 预测接口在你的网络不可达 → 窗口日历推算无锚点 → 版本号停在待檢測、
  全国补灌（seedRemainingProvinces）因 adjustDates 为空而无法触发 → 省份一直 1/31。
- **修复**：getAdjustDates 锚点改为 **5 级回退链**：
  ① apizero 预测专接口 next_adjust_date → ② ruseo 快照 date−1 → ③ xxapi 快照 date−1
  （ruseo/xxapi 同源互备，date = 最近窗口生效日，10-16 → 窗口 10-15，含搁浅窗口）
  → ④ 主源 abapi 全年曲线最近调价日 −3 天（实测 18/18 与发改委公告吻合，如 09-27 生效 → 09-24 窗口）。
  任一可达 → 锚点成立 → 10 工作日推算全年窗口日历 → 版本号 V20261015 / 調整日 / 下個窗口 10-29 全部恢复。
- **新增**：OilPriceNetwork 接入 ruseo（api.ruseo.cn）与 xxapi（v2.xxapi.cn）——同源镜像（价格一致、
  date 一致），fetchAnchorWindowDate 取 date−1 为窗口日；两个源都是最新一期全国 31 省快照（无历史曲线），
  **不参与历史灌库**（避免非调价日 10-16 被计入调价次数），仅作锚点。
- **兜底**：apizero 与 ruseo/xxapi 全挂时 → abapi 曲线锚点（变化日−3）；checkAndUpdate 的 nextRaw
  追加「推算日历下个未来窗口」兜底（此前只在接口给值时写入，接口挂则待檢測）。

### ⚡ 全国补灌提速（4 路并发）
- **seedRemainingProvinces**：31 省逐省串行（每省 1 请求 + 200ms）改为 **4 路并发 + 组间 200ms**，
  补灌耗时约降至 1/4；锚点恢复后 adjustDates 就绪，各省自动补全年曲线。

### 🗓 节假日数据多 CDN 回退
- fetchChinaHolidays：jsDelivr（实测部分网络 502）→ **unpkg** → 国内 npmmirror，三源任一成功即用，
  避免窗口推算因节假日数据缺失而整链失败。

### ✅ 验证
- 锚点链沙箱实测：abapi 锚点（09-24）→ 10 工作日推算 → 全年 25 个窗口，前 20 个与发改委公告
  **逐一吻合**；ruseo/xxapi date−1 = 10-15（最近窗口）→ 顺推下个 10-29，与 apizero 预测一致。
- 节假日：unpkg 实测返回 33 个放假日 + 6 个调休补班日。

# KOiL 版本更新说明（CHANGELOG）

> 本文件随源码包分发，记录每个版本相对上一个版本的变更，便于跨环境接手开发时快速了解改动。

## v1.4.177（versionCode 182）— 2026-10-26（基于 v1.4.173 基线）

### 🔤 底部「KOiL」字重真正生效 Bold
- **修正**：此前（v1.4.174~176）只加了 `FontWeight.Bold`，但字体只有单个可变字体文件
  `unbounded_regular.ttf`，Compose 不会把 Bold 自动映射到 wght 轴 → **字从未变粗**（用户实测确认）。
- **正确实现**：从字体官方仓库 **w3f/unbounded**（github.com/w3f/unbounded）下载**可变字体**
  `Variable/Unbounded-Variable.ttf`（fvar 表 wght 200-900）→ `res/font/unbounded_variable.ttf`（唯一字体文件），
  用 Compose `FontVariation.Settings(FontVariation.weight(700))` 显式设置 wght=700（API 26+ 可变字体，
  Compose 1.11.1 支持）→ 底部 KOiL 渲染为真正的 Bold；同时移除旧的 regular/bold 单字重文件。
- **本版基线 = v1.4.173**：油价卡片四项改造、窗口两行布局、导出提醒 Toast、底部品牌区（Unbounded 20sp）
  全部保留；**不含** v1.4.174~176 的窗口推算/ndrc 抓取改动（按用户要求退回）。
- 数据源：apizero 不可达时窗口日显示待檢測（173 原样行为）。

---


### 🏷️ 油价数据库卡片改造（用户要求）
- **标题行**：「V20261015」→「數據庫版本(V20261015)」（待检测态 →「數據庫版本(待檢測)」，三语同步）；右侧原「查看調價記錄」入口上移到标题行，文案精简为「查看」+ 箭头小图标，与版本号同一行、垂直居中。
- **窗口信息布局**：調整日信息单独一行（含擱淺/調價至状态）；「下個窗口：xx」「最近更新：xx」合并到同一行（左窗口右更新）。

### 🔔 导出/备份结果提醒改为 Toast
- 汇出/备份/更新/采纳等操作结果提示由常驻卡片改为 **Toast 短提醒（自动消失）**，不再停留在页面上。

### 🔤 底部品牌区改造
- 页脚「KOiL v1.4.x · com.llpop.koil」一行 → **上方「KOiL」（Unbounded 字体 20sp）+ 下方版本号「V1.4.173」（V 大写）**；新增 Unbounded 可变字体资源（res/font/unbounded_regular.ttf）。

---


### ✍️ 设置页字号与颜色统一（用户要求，按截图基准 = Miuix 渲染）
- **统一基准**：Miuix 0.9.4 `BasicComponent` 默认渲染 = 标题 `headline1`（17sp Medium onBackground）、副标题/右侧值 `body2`（14sp onSurfaceVariantSummary / onSurfaceVariantActions）。下拉组件标题字号由库内固定、调用处无法覆盖，因此 KOiL 自研设置行**迁就 Miuix 默认**实现整页统一（6 处 Miuix 下拉保留原样，交互不变）。
- **所有设置行左侧标题统一 17sp Medium**：SettingRow（汇出/汇入/备份/恢复等）、StepperInputRow（油箱容量等）、SettingSwitchRow（定位/油价提醒/油价推送/记住油号等）、自动更新内联行、待确认更新标题，全部 15sp→17sp。
- **所有设置行副标题（描述文字）统一 14sp**：11sp→14sp，颜色统一为 `onSurfaceVariantSummary`（原 onBackgroundVariant）。
- **右侧当前值统一 14sp `onSurfaceVariantActions`**：SettingRow 右侧值由主题色 Primary 改为与 Miuix 渲染一致的灰蓝，整页右侧值颜色一致。
- 数据/标签类小字（待确认列表项、footer 版本号、导入明细、统计项 label）保持 11sp 不动。

## v1.4.171（versionCode 176）— 2026-10-25

### 🎨 调价时间线定稿（三列紧凑 + 第4列幅度条，用户指定）
- **排版回到 v1.4.169 三列紧凑**：日期(左 56dp) · 價格(左 70dp) · 漲跌幅 chip(左)——三列靠左紧凑排列。
- **右侧新增第 4 列幅度条**：chip 右侧一条横条，**长度正比于调价幅度**（1 元为满条，最短 6% 保证可见），**涨红跌绿**（与 chip 同色）——既利用右侧空白，又让调价力度可视化（¥0.06 短条 / ¥0.92 近满条）。
- 基準價行无幅度条（占位列保持对齐）。

---
## v1.4.170（versionCode 175）— 2026-10-25

### 📦 打包体系修复（重要）
- **源码 zip 逐版翻倍 bug**：v1.4.160 起打包命令漏排除 `*.zip`，导致每个新源码包把之前所有旧源码包嵌套打进（169 达 4.4GB）。已删除工程目录全部旧包，打包命令增加 `-x "*.zip"`；v1.4.170 源码包恢复 **1.4MB**（源码 + .git 全历史 + keystore，git 内可回退任意版本）。云端旧 src 链接（v1.4.160~169）均含嵌套旧包，请以 v1.4.170 包为准。

### 🎨 调价时间线三列铺满 + 信息层级强化（用户定稿）
- **涨跌幅移到行末右对齐贴边**：整行铺满（日期左 → 价格中 → 涨跌幅右），消除右侧大片空白；表头同步（價格 72dp / 涨跌幅弹性右对齐）。
- **价格 16sp Bold** 成为每行视觉中心（基準價行保持常规字重）；**最新行价格用主题色**突出当前价；**基準價行整行淡化**（日期/价格 55% 透明度，作为参照基准弱化）。
- **涨跌幅 chip 加幅度条**：箭头后追加横条，长度正比于调价幅度（全局基准 25dp，最小 3dp）——¥0.06 短条 / ¥0.92 长条，调价力度一眼可辨。

---
## v1.4.169（versionCode 174）— 2026-10-25

### 🎨 调价时间线改为三列紧凑排版（用户定稿）
- 时间线每行改为 **三列紧凑**：`日期(左) · 价格(左) · 涨跌幅(左)`——日期灰字固定 56dp 宽、价格 14sp 半粗固定 70dp 宽、涨跌幅为箭头 chip（↑上調 淡红底 / ↓下調 淡绿底 / 基準價 灰 chip），列间距仅 8dp，不再用弹性权重撑开（紧凑、不松散）。
- 表头同步三列左对齐：`日期 | 价格 | 涨跌幅`（新增三语文案 koil_pdb_col_change）。
- 说明：v1.4.168 的两段式（左组+右价格）方案经用户验收后否决，此为定稿方案。

---
## v1.4.168（versionCode 173）— 2026-10-25

### 🎨 明细页排版重构（时间线方案一 + 区块统一）
- **时间线改为两段式排版**（方案一）：原"日期｜動作（居中浮动）｜價格"三列悬浮结构改为 **左组（日期 + 動作 chip）+ 右价格**——日期灰小字、動作带淡红/淡绿圆角底 chip 紧跟其后（左对齐有锚点），价格右对齐 15sp 加粗（每行焦点）；扫读路径最短，不再来回跳。
- **動作 chip 加方向箭头**：上調 ↑（淡红底）、下調 ↓（淡绿底）、基準價/持平为灰色 chip 无箭头；涨跌一眼可辨。
- **表头同步两段式**：日期 調整（左组）｜ 價格（右对齐），与数据行列对齐。
- **走势图套圆角卡片**：与汇总卡视觉统一（区块整块化）；卡片内 8dp 留白。
- **走势图 Y 轴刻度加深**：刻度/日期标签透明度 40% → 60%、字号 9dp → 10dp，更清晰。

---

## v1.4.167（versionCode 172）— 2026-10-25

### 🐛 明细页细节修复
- **返回箭头去除方形选中色卡**：点击返回箭头不再出现正方形高亮背景（clickable 指定 `indication=null`，仅保留点击响应）。
- **汇总卡两数值字号统一**：最新价格金额从 22sp 调回 16sp，与"今年淨漲跌"金额一致（避免一边大一边小、视觉不对称）。

---

## v1.4.166（versionCode 171）— 2026-10-25

### 🐛 修复 + 明细页体验增强
- **修复 v1.4.165 闪退**：明细页原改为 LazyColumn 整页懒加载滚动，其懒加载 item 层与 Miuix 纹理卡片（textureBlur 汇总卡）存在兼容性问题 → 进入页面即崩溃。改为 **Column + verticalScroll 整体滚动容器**（时间线仅 20 条左右、一次性渲染，无懒加载层），既保留"整页滚动 + 顶栏随滚动折叠"的效果，又彻底规避崩溃。
- **调价时间线增加表头**：日期 | 调整 | 价格，三列与数据行列对齐，用户一眼看懂每列含义（三语新增 koil_pdb_col_date/col_action/col_price）。
- **汇总卡最新价格突出**：第一格数字放大（16sp → 22sp 加粗），一眼看到核心数据。
- **走势图增强**：曲线下新增**面积渐变填充**（主色 20% → 2% 淡出）；高度 150dp → 170dp 更有呼吸感。
- （Y 轴价格刻度 + 淡网格线此前已实现，本次保留。）

---

## v1.4.165（versionCode 170）— 2026-10-25

### 🎨 明细页规范对齐（二级页：去底栏 + 顶栏与一级页一致）
- **二级页隐藏底部导航**（对齐 Mishka NavDisplay 二级路由页规范 + Material 惯例）：调价记录明细页打开时底部导航条（加油/總覽/設定）整体隐藏，内容区占满全屏；明细页底部留白改回系统导航条 inset（不再需要为底栏预留 84/120dp 防遮挡）。
- **顶栏效果与一级页一致**：明细页原为"顶部固定区（省份/油号/汇总卡/走势图）+ 底部时间线 LazyColumn"的混合布局，顶部区块不随滚动、顶栏折叠观感与一级页不同；现改为**整页统一滚动容器**（LazyColumn：省份条/油号/汇总卡/走势图/时间线全部为列表项），滚动行为与设置页完全一致——下滑整页滚动、顶栏大标题随滚动折叠。

---

## v1.4.164（versionCode 169）— 2026-10-25

### 🏗️ 架构调整（对齐 Mishka，根治横向手势冲突）
- **背景**：参照 Mishka 源码发现，其所有二级页（如外观设置页）都是**独立路由页**（`NavDisplay`+`entry<Route>`），**不在主界面 HorizontalPager 内**，所以其"介面縮放"滑动条不受 Pager 手势拦截器影响、可正常左右滑动；而 KOiL 的调价记录明细页此前在 Pager 内的设置页里（状态切换渲染），被 Miuix `pagerGestureOverride(CrossAxisInterceptor)` 跨轴拦截器（挂 Pager 父级）吞掉全部横向手势。
- **修复**：调价记录明细页**提升为 MainScreen 级全屏页**（`showOilPriceDetail` 状态，脱离 HorizontalPager 渲染）——省份横滑条、走势图拖动等横向手势完整归页面内组件；同时**恢复 Pager 跨轴拦截器**（主界面架构回到与 Mishka 一致）。
- 设置页"調價記錄"入口改为回调 `onOpenPriceDetail`，由 MainScreen 承载明细页。

---

## v1.4.163（versionCode 168）— 2026-10-25

### 🐛 修复（重要）
- **省份横滑/走势图拖动仍失效的真正根因**：MainScreen 的 HorizontalPager 挂了 Miuix `pagerGestureOverride(CrossAxisInterceptor)` 跨轴手势拦截器——它挂在 Pager（页面内容**父级**）上，父级优先判定横向手势并消费，页面内所有横向组件（省份横滑条、走势图拖动）永远抢不到；而 `userScrollEnabled=false` 使 Pager 自身又不翻页 → **横向手势被整体吞掉，页面毫无反应**（此即多版修复无效的根因，与遮罩/LazyRow/手势实现无关）。
- **修复**：移除 Pager 跨轴拦截器及其配套嵌套滚动连接（`pagerGestureOverride` / `PagerInterceptionMode` / `PagerGestureNestedScrollConnection`）。切 tab 仍走底部导航（springAnimateToPage），无需横滑翻页；横向手势完全归还给页面内组件。

---

## v1.4.162（versionCode 167）— 2026-10-25

### ✨ 优化
- **顶栏返回箭头**：放大到 24dp、颜色改为与标题一致的 `onBackground`（对齐参考截图"外觀與主題"页）。

---

## v1.4.161（versionCode 166）— 2026-10-25

### 🐛 修复
- **省份仍不能滑动**：彻底移除 LazyRow 及一切绘制遮罩/兄弟覆盖，改用最朴素的基础 `horizontalScroll` 横向滚动容器，排除所有可能干扰手势的因素，保证滑动必定生效；进入页面估算滚动到当前省份。
- **走势图手势失效（点击也看不到点）**：手写手势循环，**点击显示日期+价格 + 横向拖动实时跟随**双支持；纵向拖动不消费、让给页面正常滚动。

### ✨ 优化
- **顶栏返回箭头**：换为完整左箭头 `←`（MiuixIcons.Back，Mishka 同款 Miuix 生态），对齐参考截图"外觀與主題"页箭头。
- **时间线去掉 +/- 号**：上调/下调直接显示金额"上調 ¥0.35 / 下調 ¥0.20"（三语同步），与汇总卡净涨跌风格一致。

---

## v1.4.160（versionCode 165）— 2026-10-25

### 🐛 修复
- **汇总次数对不上窗口数（恒少 1）**：根因是首条「基準價」记录无前一条可比较、不计上调/下调，但其窗口有记录也不计搁浅 → 上調+下調+擱淺 恒为窗口数−1。改为**搁浅次数 = 今年已发生窗口数 − 上调次数 − 下调次数**（差集口径），总数恒等于窗口数（20）。
- **省份列表仍滑不动**：提示层改为绘制在 LazyRow **自身 drawWithContent** 上（只画不拦截触摸），滑动/点击手势始终落在 chips 上。

### ✨ 优化
- **顶栏返回箭头**：改为 Miuix 风格细箭头（ChevronBackward），与参考截图一致。
- **汇总卡五宫格重排**：上面一行「最新价格（前）+ 今年净涨跌（后）」，下面一行「上调/下调/搁浅次数」；净涨跌显示"上涨 ¥X.XX / 下调 ¥X.XX"（不带 +/- 号）。
- **走势图滑动查看**：由"点击某点"改为**手指在曲线上滑动跟随**——拖动时实时显示该点日期+价格，松手保留最后查看的点。
- **时间线排版重设计**：日期（左灰）· 动作+幅度（中）· 价格（右大号加粗），行间细分隔线，更规整。

---

## v1.4.159（versionCode 164）— 2026-10-25

### ♻️ 命名统一（遵循「标识符优先以 KOiL 命名」规范）
- **资源 key 全部加 `koil_` 前缀**：三语 strings.xml 共 272 个 key（`add_`/`ov_`/`stat_`/`set_`/`pdb_` 等）统一改为 `koil_add_*` / `koil_ov_*` ……，代码中全部 `R.string.xxx` 引用同步更新（仅编译期标识符，不影响运行）。
- **Fuel 标识符改 KOiL**：`FuelPriceDao` → `KOiLPriceDao`、`FuelPriceEntity` → `KOiLPriceEntity`、`fuelPriceDao` → `koilPriceDao`（文件重命名 + 全工程引用同步）。
- **数据库表名** `fuel_prices` → `koil_prices`：Room version 1→2，新增迁移 `ALTER TABLE fuel_prices RENAME TO koil_prices`——已安装用户升级**数据保留**，全新安装直接建新表。
- **保留不动**：用户可见的英文界面文案（Refueling / fuel prices / Total fuel 等），非标识符，改反而错误。

---

## 📌 跨环境交接约定（接手方请照做）

为了让工程在多个开发环境之间往返搬运时**变更信息不丢失**，请每个环境的开发方遵守：

1. **每完成一个版本迭代，在本文件顶部追加一段新版本记录**（版本号 + versionCode + 日期 + 「修复 / 新增 / 优化」清单），保留旧版本段落不删；
2. **用 Git 提交存档**：`git add . && git commit -m "vX.Y.Z 版本说明"`（工程根目录已初始化 Git，commit 后版本历史随包带走）；
3. **打包分发时**保留以下内容，不要剔除：`.git/`（版本历史）、`keystore/` 与 `keystore.properties`（签名）、`CHANGELOG.md`（变更说明）、`local.properties` 可剔除（每台机器 SDK 路径不同，会自动重新生成）；
4. 接手方打开包后，先读本文件即可知道上一版改了什么；如需代码级差异，用 `git log` / `git diff` 查看。

---

## v1.4.158（versionCode 163）— 2026-10-25

### 🐛 修复
- **汇总卡 5 格叠加错乱**：卡片容器内部为 Box 布局，两行统计被叠在一起；改为 Column 两行（次数行 + 净涨跌/最新价行）正常排列。
- **省份滑动失效**：右侧渐隐遮罩层拦截了触摸；改为 Canvas 纯绘制（不参与触摸），LazyRow 恢复正常左右滑动。
- **时间线底部仍被底栏遮挡**：底部内边距下限提高到 88dp，任何布局下最后一条都不再被盖住。
- **时间线排版右侧大片空白**：单行改为 SpaceBetween 铺满整行（日期左 / 价格中 / 涨跌右）。

### ✨ 优化
- **次数口径对齐窗口日历**：上調+下調+擱淺 恒等于「今年已发生窗口数」——搁浅判定改为按当前省+油号（窗口日+1 该省该油号无记录即搁浅），与涨跌次数同源。
- **走势图点击提示**：点击曲线任一数据点，显示该点日期 + 价格气泡（竖线定位 + 圆点放大）。
- **明细页顶栏动态化**：对齐首页/设置页——初始大标题左对齐，下滑滚动时折叠为居中标题；返回箭头固定在顶栏（与其他页一致）。

---

## v1.4.157（versionCode 162）— 2026-10-25

### ✨ 优化
- **調價記錄页汇总卡重构**（按用户口径）：上调次数 / 下调次数 / 搁浅次数（权威窗口日历已发生但库内无生效记录）+ **今年净涨跌**（=累计上调金额−累计下调金额）+ 最新价格。
- **省份选择恢复横向滑动 chips**，并加**可滑动提示**：右侧渐隐遮罩 + 箭头，进入页面自动滚动到当前省份。
- **调价时间线改为最新在上**（倒序），查最近调价无需滚到底部。
- **走势图增加中间日期刻度**（原来只标首尾两个日期，现按点数等距标 4 个）。

---

## v1.4.156（versionCode 161）— 2026-10-25

### ✨ 优化
- **調價記錄页省份选择**：原横向滑动 chips（31 省需左右滑动、不明显）改为**下拉选择器**——显示当前省 + 下拉箭头，点开弹出完整 31 省列表，一目了然。
- **调价时间线底部内边距**：列表底部让出底栏高度，最后几条调价记录不再被底部按钮遮挡。

---

## v1.4.155（versionCode 160）— 2026-10-25

### 🐛 修复
- **缺口提示仍误报 31 省**：上一版阈值改用"今年已发生窗口数"，但仍**包含搁浅窗口**——搁浅窗口价格不变、不写记录，每省实际日期数比窗口数少几个 → 全部标红。改为阈值 = **已调价窗口数**（窗口日+1 在库内有记录才算调价过，排除搁浅），并容忍 2 个窗口的拉取缺口。正常省份不再误报，真正缺数据的省仍会提示。

---

## v1.4.154（versionCode 159）— 2026-10-25

### 🐛 修复
- **調價記錄明细页空数据**：明细页按油号查询用了代码键（"92"），而库内 grade 以完整标签存储（"92号汽油"，与录入页/总览页一致）→ 查询不命中显示"暫無調價記錄"。改为明细页使用标签键查询，31 省 × 4 油号时间线/走势/涨跌统计全部正常。
- **"31省數據未拉取完整"误报**：缺口判定阈值误用全年窗口日数（含未来窗口），所有省都会因"未来窗口尚无记录"而标红。改为阈值 = 今年已发生窗口数，仅真正缺窗口的省才提示。
- **推送通知油号键**：同步 Worker 通知改为与库内一致的标签键，确保调价推送取到真实价格。

---

## v1.4.153（versionCode 158）— 2026-10-25

### ✨ 新增
- **調價記錄明细页**：設定 → 油價數據庫 → 「查看調價記錄」。31 省 × 4 油号任意切换，顶部汇总**今年累计上调/下调（元/L）**与最新价格，价格走势折线图，下方历次调价时间线（上調 +0.15 红 / 下調 -0.15 绿 / 維持不變 / 基準價）。
- **調價推送通知**：设置页新增「調價推送通知」开关（默认开，API 33+ 启动时申请通知权限）。每次自动/手动同步后：① 新生效日有调价 → 系统通知「油價已調整：广东 92号汽油 ¥7.55 → ¥7.70」；② 窗口日前一天 → 通知「明天是調價窗口日」。幂等去重（按生效日/窗口日），不重复骚扰。
- **缺口省份提示**：设置页油价数据库卡片在补灌未完成时红色提示「N 省數據未拉取完整」，同步完成后自动消失。

### 🐛 修复
- 无（本版为功能增强）。

---

## v1.4.152（versionCode 157）— 2026-10-25

### 🐛 修复
- **各省油价数据补灌不触发（数据不全）**：假生效日清理后各省仅剩零星真记录（如记录 198 条，应约 2400 条），但补灌逻辑只按"省份是否已在库"判定，31 省名义上"已覆盖" → 缺口永不补全。现改为：
  - 新增按省统计窗口日覆盖数，**覆盖窗口日 < 应有量（当年权威窗口日数）即重灌该省**（`REPLACE` 幂等）；
  - 窗口日历未就绪时跳过补灌（杜绝降级假日期回流）；
  - 单省拉取失败只留缺口、不阻塞其他省，下次同步自动重试；
  - 补灌后 31 省 × 4 油号 × 调价窗口历史完整（约 2400 条量级），各省 `價格維持` 与录入参考价可靠。

---

## v1.4.151（versionCode 156）— 2026-10-25

### 🐛 修复
- **假生效日清理在"窗口日历缓存命中"时不执行**：上一版把清理挂在"重新推算窗口日"分支，但窗口日历已有缓存时 `getAdjustDates` 直接命中返回、跳过清理，导致旧版降级灌入的假生效日（如 09-27，真实调价日为 09-24，生效应为 09-25）一直残留，设置页显示"價格維持 09-27"对不上。现改为：
  - 清理逻辑抽为独立幂等函数，**缓存命中时也执行**；
  - 新增公开入口 `purgeIllegalEffectiveDatesIfReady()`，**打开设置页即按已缓存窗口日历清理**，再刷新统计；
  - 清理后库内最新生效日恢复为真实调价生效日（09-25），"價格維持"、`調價` 次数与 `記錄` 行数一并准确。

---

## v1.4.150（versionCode 155）— 2026-10-25

### 🐛 修复
- **调价次数统计口径错误**：设置页「历史油价数据库」的「调价」数字此前使用 `COUNT(DISTINCT effectiveDate)`，会被降级灌库产生的**假生效日**污染（实测显示 42，而 2026 年至今真实调价窗口约 20 个）。现改为优先统计「权威调价窗口日历」中今年已发生的窗口日数（含搁浅窗口），窗口日历为空时才回退旧口径。
- **假生效日清理机制改进**：油价库的孤立假日期（不属于任何"窗口日+1"的记录）清理从"仅首次"改为**每次窗口日历就绪后幂等校验**，确保再次降级灌库后也会被自动清除，`数据` 行数与 `调价` 次数恢复准确。

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
