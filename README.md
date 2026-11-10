<div align="center">

<img src="KOiL-logo-vector.svg" width="112" alt="KOiL" />

# KOiL

**加油记录 · 油价调价**

[![Build](https://github.com/ZSPOP/KOiL/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/ZSPOP/KOiL/actions/workflows/android-build.yml)
![Android](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/github/license/ZSPOP/KOiL)

</div>

KOiL 是一个本地优先的 Android 应用：记录每次加油的金额、油量、单价与里程，自动算油耗和每公里成本；同时维护全国 31 个省级行政区、各油号的历史调价库，跟踪成品油调价窗口。

数据默认只存在本机。需要多设备时，可以接自己的 WebDAV（坚果云等），不依赖第三方云服务。

## 截图

> 实机截图整理中。补图后放在 `docs/screenshots/`，建议覆盖总览、明细、设置三页。

## 功能

### 记账

- 新增、编辑、补录；按日期与里程校验，减少重复录入
- 选好省份与油号后自动带出当日单价，也可以手动填写
- 自动派生油耗、每公里成本，提供环比与趋势统计
- 总览按月汇总，明细页查看单价与用量走势

### 油价与调价窗口

- 31 个省级行政区 × 各油号的历史调价库，支持一键更新与增量灌库
- 调价窗口日历与提醒
- 原油估算以 EIA 布伦特现货为主源，失败自动降级腾讯期货

### 数据出口

- **CSV**：加油记录、油价历史导出与导入
- **JSON**：完整备份与还原，带备份版本号，可跨版本恢复
- **WebDAV 同步**：自动备份、恢复与冲突合并，支持墓碑删除、条件请求与 412 重试

### 体验

- 深色 / 浅色主题
- 简体中文 / 繁體中文 / English
- 崩溃日志本地留存（最多 10 条，可导出）
- 应用内检查更新

## 下载与安装

到 [Releases](https://github.com/ZSPOP/KOiL/releases) 下载最新的 `KOiL-V*-vc*-release.apk`。

- 系统要求：Android 13（API 33）及以上
- 安装包约 6 MB
- 同签名版本可直接覆盖升级；系统不允许降级安装

> 仓库刚建立，首个 Release 尚未发布；发布后该链接即可使用。应用内「设置 → 关于 → 检查更新」会比对最新版本。

## 首次使用

1. **选省份与油号**：添加第一笔记录时选择省份与油号；首次启动会询问是否开启自动定位省份，也可以稍后在设置里改默认省份
2. **记第一笔**：填日期、金额、油量与里程，单价会按省份、油号和日期自动带出
3. **可选配置**：在设置里更新油价库、开启调价提醒；需要多设备再填 WebDAV 账号

## 权限、网络与数据

| 项目 | 用途 | 说明 |
|---|---|---|
| 粗略定位 | 推荐所在省份 | 只申请 `ACCESS_COARSE_LOCATION`，可拒绝，拒绝后手动选省 |
| 通知 | 调价窗口提醒 | 可关闭 |
| 网络 · 油价源 | 获取公开行情 | EIA 布伦特现货，失败降级腾讯期货 |
| 网络 · GitHub | 检查更新 | 读取 Release 元数据 |
| 网络 · WebDAV | 可选云同步 | 只连接你自己填写的服务器 |

- 记录、设置与崩溃日志默认只存本机；没有统计、广告或推送 SDK
- 卸载即删除本地数据；云上的备份由你的 WebDAV 账号管理
- 定位只用于推荐省份，不记录轨迹

### 数据免责

油价、调价窗口与原油估算仅供参考，不构成加油建议。实际价格以加油站挂牌价和发改委公告为准。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.3.21 |
| UI | Jetpack Compose 1.12.0 + Miuix 0.9.4 |
| 架构 | 单模块 MVVM + Repository，Koin 4.2.2 |
| 本地存储 | Room 2.7.1 + DataStore |
| 网络 | Ktor 3.6.1 |
| 构建 | AGP 9.1.0 / Gradle 9.8.2 / JDK 21 |
| 系统 | minSdk 33（Android 13）/ targetSdk 37 |

## 从源码构建

需要 JDK 21、Android SDK Platform 37、Build-Tools 37.0.0，并在 `local.properties` 写好 SDK 路径：

```properties
sdk.dir=/path/to/android-sdk
```

```bash
export JAVA_HOME=/path/to/jdk21
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

debug 包名是 `com.llpop.koil.debug`，可以和正式版共存。提交前建议跑：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

发版、版本号与签名配置见 [docs/KOiL发版与版本号.md](docs/KOiL发版与版本号.md)。

## 项目结构

```
app/src/main/java/com/llpop/koil
├── data/       Room、DataStore、油价源、WebDAV、更新检查
├── domain/     记录派生与业务规则
├── ui/         Compose 页面、组件与主题
├── di/         Koin 依赖注入
└── util/       通用工具
```

Room schema 导出在 `app/schemas/`，当前基线为 `1`；修改表结构必须升版本并补 Migration。

## FAQ

**油价和加油站挂牌价不一致？**
应用里的价格来自历史调价库与原油估算，用于趋势参考；实际成交价以加油站为准。

**换机后提示 WebDAV 密码不可用？**
应用密码在本机加密保存，换机后密钥不随备份迁移，重新输入一次即可。

**同步会不会覆盖新记录？**
按记录的日期与里程唯一键做合并，删除通过墓碑同步；冲突与重试口径见云同步设计文档。

**为什么只申请粗略定位？**
推荐省份不需要精确位置，粗精度足够，也更省电。

**本地出的包为什么装不上，或者挡住正式版？**
本地包的 `versionCode` 低于已装版本会被系统拒绝；跳号过高则会挡住后续 CI 正式版。规则见发版文档。

## 文档

- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [API_SOURCES.md](API_SOURCES.md) — 油价数据来源与接口说明
- [docs/KOiL油价数据库机制.md](docs/KOiL油价数据库机制.md) — 油价库同步、窗口日历与降级行为
- [docs/KOiL云同步设计.md](docs/KOiL云同步设计.md) — WebDAV 备份/恢复与冲突合并口径
- [docs/KOiL发版与版本号.md](docs/KOiL发版与版本号.md) — 发版流程、版本号与签名

## 参与贡献

有 bug、建议或想加功能，请 [提交 Issue](https://github.com/ZSPOP/KOiL/issues/new)。提交 PR 前先跑一遍单测与 Lint，说明改动动机即可。

## License

[MIT](LICENSE) © 2026 ZSPOP
