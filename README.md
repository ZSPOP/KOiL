# KOiL

记录加油、跟踪油价调整窗口的 Android 应用。玻璃拟态风格界面，数据全部存本机，可选 WebDAV 云同步。

- **最低版本**：Android 13（API 33）
- **包名**：`com.llpop.koil`
- **技术栈**：Kotlin + Jetpack Compose + Miuix + Room + DataStore

## 下载

到 [Releases](../../releases) 页下载最新 `KOiL-V*-release.apk` 直接安装。应用内「设置 → 关于 → 检查更新」会自动比对最新版本。

## 功能

- 加油记录：录入/编辑/补录、按油号与省份自动匹配单价、派生字段（油耗、每公里成本）
- 油价数据：全国 31 省 × 各油号历史调价库，支持「立即更新」与调价窗口提醒
- 原油估算：EIA 布伦特现货为主源，失败自动降级腾讯期货
- 数据出口：CSV 导出/导入（记录、油价历史）、JSON 完整备份/还原
- 云同步：WebDAV（坚果云等）自动备份与恢复
- 深色/浅色主题，简体中文 / 繁體中文 / English 三语

<!-- 截图区：实机截图就位后取消注释（放 docs/screenshots/）
## 截图

| 总览 | 油价调价 | 设置 |
|---|---|---|
| ![总览](docs/screenshots/overview.png) | ![油价调价](docs/screenshots/prices.png) | ![设置](docs/screenshots/settings.png) |
-->

## 本地构建

需要 JDK 21 + Android SDK 37。

```bash
export JAVA_HOME=/path/to/jdk21
./gradlew :app:assembleRelease
```

签名材料（`keystore/koil-release.keystore`、`keystore.properties`）**不入库**，需自行放置于仓库根目录；缺失时自动回落 debug 签名。

### 本地出包时的 versionCode

不带环境变量时 `versionCode` 会回落到 `app/build.gradle.kts` 里的固定基准值（252），**比线上版本低，系统会拒绝安装**。本地出测试包务必显式指定：

```bash
KOIL_CI_VERSION_CODE=<当前 CI 最大 run_number + 251> ./gradlew :app:assembleRelease
```

- 取「已产出的最大 CI 值」而不是「下一个值」：对已装版本是严格升级，对下次 CI 构建也是严格升级，两头都不依赖「同 versionCode 覆盖安装」这个边缘行为
- 同一轮迭代里本地反复出包可以一直用同一个号（同签名、同 versionCode 允许覆盖安装），出多少个包都不用改号
- **`versionName` 保持不动**：应用内检查更新只比 `versionName`，本地包先改成未发布的号会导致正式版发布后仍显示「已是最新版」

#### 为什么本地包不会挡住正式版

一次发版会消耗**两次** CI 运行（推 `main` 一次、打 tag 一次），所以正式版的 `versionCode` 永远比本地包高 2。以 V1.6.3 发布后的状态为例：

| 环节 | versionCode |
|---|---|
| 已完成的最大 CI 运行（run 22） | 273 |
| 本地出测试包（可反复出任意多个） | 273 |
| 发版第 1 步：合 `main` → CI run 23 | 274（仅 artifact，不发布） |
| 发版第 2 步：打 `V1.6.4` → CI run 24 | **275（Release）** |

手机上装 273 → 收到 1.6.4 提示（比 `versionName`）→ 下载 275 安装（275 > 273，覆盖成功）。

**发过一版之后要重算**：手机上换成正式版（275）后，本地包就不能再用旧的 273 了（低于已装版本会被拒装）。此时先看一眼 Actions 最新 run 号（run 24），本地取 275（与已装同号，可覆盖）或 276。

唯一会踩的坑是**本地跳号**——手工填一个 CI 短期内到不了的数（如 280），此后所有正式版都会被它挡住。

## 发版流程

1. **改版本号**：`app/build.gradle.kts` 的 `versionName` 手动递增（如 `1.6.4`），`versionCode` **不要手改**
2. **合 main**：功能分支合并到 `main` 并推送
3. **打标签**：`git tag -a V1.6.4 -m "..."` → `git push origin V1.6.4`
4. CI 自动构建 → 生成 `KOiL-V1.6.4-release.apk` → 挂到同名 Release → 应用内检查更新即可看到

### versionCode 规则

```
versionCode = github.run_number + 251
```

- CI 每次构建自增，全局单调，无需人工维护
- **本地构建不要预设超过 CI 的号**，否则手机上装了本地包会挡住后续正式版（Android 禁止降级安装）
- 想测新功能优先走 CI：Actions → Build → Run workflow → 选分支，产物在该次运行的 Artifacts 里

### 签名

仓库公开，签名材料只存在于 GitHub Actions Secrets：

| Secret | 内容 |
|---|---|
| `SIGNING_KEYSTORE_B64` | `koil-release.keystore` 的 base64 |
| `SIGNING_PROPERTIES` | `keystore.properties` 全文 |

工作流在构建前解码写入 `keystore/`，两个 Secret 缺失则回落 debug 签名并告警。

### 注意

- **同名标签会覆盖 Release 附件**：不要在不改代码的情况下重复推同一个 tag，否则用户会被提示「有新版本」但内容未变
- 标签推送即发版，没有二次确认，务必确认 `versionName` 已递增

## 文档

- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [API_SOURCES.md](API_SOURCES.md) — 油价数据来源与接口说明
- [KOiL-PROGRESS.md](KOiL-PROGRESS.md) — 开发进度与设计决策

## 反馈

[提交 Issue](../../issues/new)
