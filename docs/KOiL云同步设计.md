# KOiL 云同步设计（坚果云 WebDAV · 私有数据备份/恢复）

> 设计基线：v1.5.36（2026-10-30），本文档为功能设计稿，未实现。
> 目标：为加油记录与用户设置提供「作者自用级」云端备份/恢复，不引入付费依赖、不引入第三方 SDK。

---

## 1. 背景与范围

### 1.1 现状
- 加油记录（`koil_record`）与用户设置（DataStore `KOiL_settings`）全部本地存储，仅 CSV / JSON 手动导入导出。
- 已有完整备份/恢复能力：`SettingsViewModel.exportBackup()`（§7.10，JSON 全量备份）与 `importBackup()`（解析恢复），格式 version 1。

### 1.2 目标
- **自动**把私有数据备份到用户自己的坚果云 WebDAV 文件夹；换机/重装后可一键恢复。
- 手动入口 + 状态可见（设置页新增「云同步」区块）。

### 1.3 明确不做（讨论结论）
| 不做 | 原因 |
|---|---|
| **油价库上传** | 31省油价是外部 API 派生的公共数据，可重灌；上传无价值且引入复杂合并语义 |
| 窗口日历等派生状态上传 | 同左，可重建；顶多用于「新设备提速」的加分项，非必须 |
| 双向增量合并（多设备并发编辑） | 作者自用单设备为主；增量合并需删除墓碑/版本向量，复杂度高；用「镜像覆盖 + 受控恢复」规避 |
| 第三方 SDK / 自建服务器 | 坚果云 WebDAV 是纯 HTTP 协议（Ktor 已有），零 SDK |

---

## 2. 总体方案

```
                    ┌─────────────────────────────┐
  本地 Room/DataStore │                             │   坚果云 WebDAV
  koil_record         │  CloudSyncManager           │   dav.jianguoyun.com/dav/KOiL/
  KOiL_settings       │   · 变更订阅 → 全量快照 PUT  │──►  koil_backup.json（固定名）
                    │   · 首次/手动 → GET + 恢复合并 │◄──  （云端永远是最新镜像）
                    └─────────────────────────────┘
```

**核心思路：复用现有备份格式，新增「传输层 + 调度层 + UI」三块。**
云端只存**一个固定名文件** `koil_backup.json`（全量快照，幂等覆盖）。同步语义 = 「本地权威 → 云端镜像」单向镜像 + 「受控恢复」两条拉取路径。

---

## 3. 数据格式（复用 §7.10 备份 JSON，向后兼容扩展）

### 3.1 记录字段（与 exportBackup 完全一致，派生字段保留可再算）

```json
{
  "date": "2026-10-20", "mileage": 89050.5, "oilAmount": 42.35,
  "payableAmount": 365.0, "actualAmount": 360.0,
  "oilPrice": 8.63, "oilGrade": "92", "province": "广东", "source": "entry_page",
  "distance": 512.0, "oilDays": 7, "oilConsumption": 8.27, "costPerKm": 0.70
}
```

- 存储语义：**以 `(reoilDate, mileage)` 唯一索引为合并键**（与 CSV 导入、补录校验同一把尺子），`id` 不跨设备传递。
- 派生字段（distance/oilDays/consumption/costPerKm）随快照保留（与现有备份一致），恢复后不依赖它们，本地派生计算会自动重算。

### 3.2 设置字段（在现有 11 项基础上扩展 2 项）

| 现有字段（exportBackup 已含） | 云同步新增 |
|---|---|
| themeMode / language / defaultProvince / defaultGrade / tankCapacity / keepLastGrade / abnormalThreshold / deviationThreshold / priceAlert / locationEnabled / priceDbAutoUpdate | **eiaEnabled / eiaApiKey**（用户私有注册 Key，应随备份迁移） |

- 兼容：`importBackup` 用 `optString/optDouble` 解析，**未知字段自动忽略、缺失字段保持本地默认** → 新增字段对旧备份文件天然向后兼容。
- 同步**不包含**：派生状态键（窗口日历、lastChecked、通知去重戳等）——可重建，避免新设备被旧派生状态污染。

### 3.3 快照信封（version 2 扩展，向后兼容）

```json
{
  "version": 2,
  "exportTime": "2026-10-30 10:00:00",
  "recordCount": 128,
  "records": [...],
  "settings": {...},
  "meta": { "appVersion": "1.5.36", "deviceName": "" }
}
```

- version 1（现有手动备份）→ 2：仅增加 `meta` 块，records/settings 结构不变；`importBackup` 不感知 meta，**新旧互读**。
- 手动备份（Download/KOiL/KOiL_完整备份_*.json）与云端快照格式**统一为同一种**，`importBackup` 双向可恢复。

---

## 4. WebDAV 客户端设计（新增 `data/sync/WebDavClient.kt`）

### 4.1 能力与实现
| 能力 | 实现 |
|---|---|
| PUT 上传 | `PUT /dav/KOiL/koil_backup.json`，Body = JSON 文本 |
| GET 拉取 | `GET` 同 URL，404 = 云端无备份 |
| MKCOL 建目录 | 首次上传前 `MKCOL /dav/KOiL/`（409 已存在可忽略） |
| 认证 | **Basic Auth**（用户名 + 应用密码，App 密码单独申请） |
| 传输 | 复用 `OilPriceNetwork` 同款 Ktor HttpClient（超时 8s 可放宽到 15s，ContentNegotiation 不需要） |

### 4.2 端点约定
- 默认端点 `https://dav.jianguoyun.com/dav/KOiL/`，可在设置页修改（兼容自建 Nextcloud 等 WebDAV）。
- 固定文件名 `koil_backup.json`（避免时间戳文件名导致无法定位最新）。

### 4.3 安全
- **应用密码**：坚果云「应用密码」机制——不暴露主密码；泄露可单独撤销。
- **存储**：应用密码经 **Android Keystore 加密**后存 DataStore（新键 `KOiL-sync-app-password` 只存密文）；Keystore 不可用时回退明文并 UI 标注「未加密存储」（作者自用兜底，文档留档）。
- 传输 HTTPS + Basic；数据明文存用户自有网盘（应用密码授权范围内），隐私等级与自建相当。

---

## 5. 同步语义（核心决策）

### 5.1 推送：本地权威 → 云端镜像（全量覆盖）
- 触发（见 §6）后，生成全量快照 **PUT 覆盖** 云端文件。
- 云端永远是「最近一次本机状态」的镜像。**单设备场景下无并发覆盖问题**；即使多设备，最坏情况是后推的覆盖先推的（可接受，作者自用）。

### 5.2 拉取恢复：仅两条受控路径（避免"删除复活"）
| 路径 | 触发 | 合并策略 |
|---|---|---|
| **A. 首次自动恢复** | 开启同步 且 本地记录数 == 0 | 直接 `insertAllIgnore` 全量入库 + 应用 settings |
| **B. 手动「从云端恢复」** | 设置页按钮 | 本地记录数==0 → 同 A；本地有数据 → **预览式合并**（复用 CSV 导入四分组：valid/duplicate/mileage_conflict/error），用户确认后 `insertAllIgnore`（IGNORE 命中唯一索引防重，**不删本地**） |

- **不做**「每次启动自动合并拉取」：本地删掉的记录会被云端旧快照复活（备份不含删除墓碑）。这是刻意的取舍，文档明确。
- 合并键冲突（同日同里程但数值不同）：归入 mileage_conflict 组，人工确认，不自动覆盖——沿用 CSV 导入的判定逻辑（`MileageKey` TreeSet 二分，v1.5.20）。

### 5.3 设置应用规则
- 恢复时 settings 整体应用（themeMode 等），**恢复后本地为新权威**，下次变更即推。
- 只应用上述 13 项，派生状态键不恢复。

---

## 6. 调度与触发（新增 `data/sync/CloudSyncManager.kt`）

### 6.1 变更自动推送
```
recordDao.observeAll()
  .debounce(5s)            // 连续录入/编辑合并为一次推送
  .distinctUntilChanged()
  → 同步开启 && 上次推送 >30s → 生成快照 PUT（协程 IO，失败静默记状态）
```
- 订阅点在 `CloudSyncManager`（App 级单例，Koin），**不改 AddViewModel/Overview 任何录入逻辑**；设置项变更（setDefaultProvince 等）在 `AppSettings` setter 里统一调 `schedulePush()`（轻量，防抖复用）。

### 6.2 启动链路（KOiLApplication，对齐现有启动链）
1. 同步开启？→ 读云端快照 `meta.exportTime` vs 本地 `KOiL-sync-last-push-at`：
   - 本地无记录 → **自动恢复**（路径 A）；
   - 否则仅更新状态行（不自动合并）。
2. 网络失败静默（App 正常使用不受影响），状态记 `KOiL-sync-last-status`。

### 6.3 手动入口（设置页）
- 「立即备份」→ 强制 PUT（不受 30s 防抖限制）。
- 「从云端恢复」→ 路径 B。

### 6.4 失败重试
- 单次失败：指数退避重试（15s → 1m → 5m，上限 3 次）后放弃，记状态；
- 下次启动 / 下次变更自动重试；不引入额外周期 Worker（低优先级，避免空转流量）。

---

## 7. UI 设计（设置页「云同步」区块）

### 7.1 布局（对齐现有油价库区块风格）
```
┌─ 云同步（koil_sync_section）────────────────┐
│ [开关] 云同步         同步状态行（上次/未配置）│
│ WebDAV 地址（默认 dav.jianguoyun.com/dav/KOiL/）│
│ 账号 / 应用密码（密码框）                     │
│ [立即备份] [从云端恢复]（记录数>0 时恢复需确认弹窗）│
│ 帮助行：如何申请坚果云应用密码（链接文案）      │
└────────────────────────────────────────────┘
```
- 未配置（URL/账号/密码任一为空）→ 开关置灰 + 红色提示行（对齐通知/定位权限的提示样式）。
- 状态行三态：`未配置` / `已备份 HH:mm` / `同步失败，将于下次重试`。

### 7.2 三语 key（`koil_` 前缀规范）
新增约 12 个 key：`koil_sync_section`、`koil_sync_switch`、`koil_sync_url`、`koil_sync_username`、`koil_sync_app_password`、`koil_sync_help_app_password`、`koil_sync_backup_now`、`koil_sync_restore`、`koil_sync_status_*`（未配置/已备份/失败/恢复中）、`koil_sync_restore_confirm_title/msg`、`koil_sync_restore_done/failed` 等，三语（zh-CN/en/zh-rTW）同步补齐。

---

## 8. 实施步骤（代码改动点）

### 8.1 新增文件
| 文件 | 职责 |
|---|---|
| `data/sync/WebDavClient.kt` | Ktor WebDAV 传输（PUT/GET/MKCOL + Basic Auth + Keystore 解密取密码） |
| `data/sync/CloudSyncManager.kt` | 编排：变更订阅/防抖、快照生成、推送、恢复合并、状态维护 |
| `data/sync/BackupCodec.kt` | **从 SettingsViewModel 抽取**备份 JSON 生成/解析（buildJsonString + 字段映射 → 独立对象，两个入口共用） |
| `ui/screen/settings/SyncViewModel.kt` | 设置页云同步区状态 + 动作（或并入 SettingsViewModel，实现时定） |

### 8.2 修改文件
| 文件 | 改动 |
|---|---|
| `di/AppModule.kt` | 注册 `singleOf(::WebDavClient)`、`singleOf(::CloudSyncManager)`、`viewModelOf(::SyncViewModel)` |
| `KOiLApplication.kt` | 启动链加「云同步首拉/状态刷新」（对齐 §6.2） |
| `data/settings/AppSettings.kt` | 新增键：`KOiL-sync-enabled` / `-url` / `-username` / `-app-password`（密文）/ `-last-push-at` / `-last-status`；setter 触发 schedulePush 钩子 |
| `ui/screen/settings/SettingsScreen.kt` | 新增云同步区块（沿用区块/分隔线/下拉组件样式） |
| `strings.xml` + `values-en` + `values-zh-rTW` | 三语 key |
| `SettingsViewModel.kt` | `exportBackup`/`importBackup` 改用 `BackupCodec`（行为不变，防重复实现）；恢复逻辑增加「合并预览」入口复用 |

### 8.3 明确不改
- 不改 Room schema（无新表，覆盖升级安全，DB version 保持 1）。
- 不改 KOiLRecordDao 表结构（合并复用 `insertAllIgnore` + 唯一索引）。
- 不改油价库任何逻辑。

---

## 9. 边界与风险

| 项 | 说明 |
|---|---|
| 数据量 | 全量快照 <1MB；坚果云免费 1GB/月上传额度利用率 <0.1%，无压力 |
| 多设备并发编辑 | 不支持（后推覆盖先推）；文档明示为设计取舍，未来如需可升级版本向量合并 |
| 删除同步 | 不自动同步删除（无墓碑）；依赖「受控恢复」人工路径，避免复活 |
| 首次配置成本 | 需用户手动申请坚果云应用密码（一次性，帮助行给指引） |
| WebDAV 不可用 | 静默降级：App 一切功能不受影响，仅状态行提示；下次自动重试 |
| 密码安全 | Keystore 加密为主；不可用时明文 + UI 标注 |
| 恢复的校验 | 复用 CSV 四分组判定（日期格式/里程链/重复），坏数据进 error 组不破坏本地 |
| 测试要点 | ① 新装空库 → 开同步 → 自动恢复；② 本地删记录 → 手动恢复不复活；③ 断网/错密码 → 状态正确、重试生效；④ 旧版 version1 手动备份文件可被云端恢复读入 |

---

## 10. 验收标准（Done 定义）

1. 设置页可配置 WebDAV（URL/账号/应用密码），三语完整。
2. 开启同步后，录入/编辑/删除任一记录 → 5s 内自动推送云端（可抓包或查状态行验证）。
3. 新设备（本地空库）开启同步 → 自动拉取恢复：记录数、设置项与云端一致。
4. 手动「从云端恢复」：本地有数据时走预览合并，无删除复活、无重复插入。
5. 断网/密码错误：App 正常使用，状态行提示失败，恢复网络后自动重试成功。
6. 油价库不随同步（新设备联网自动重建，行为与现状一致）。
7. 零 warning 构建；不涉及 DB schema。
