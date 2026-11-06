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
