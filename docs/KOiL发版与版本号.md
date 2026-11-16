# KOiL 发版与版本号

面向维护者。README 只保留构建入口，这里记录发版操作、版本号约束与签名配置。

## 发版流程

1. **推进版本名与发布日期**：在仓库根目录跑

   ```bash
   python3 scripts/bump_version.py                 # 先看预览：当前 → 下一个 + tag 名
   python3 scripts/bump_version.py --apply         # 真正落盘
   python3 scripts/bump_version.py --apply --date 2026-12-01   # 打 tag 会跨天时显式指定
   ```

   脚本按二十进制规则算出下一个 `versionName`，并一并改好三处：`versionName`、
   `KOIL_RELEASE_DATE`（关于页「发布日期」显示的就是它，必须是**打 tag 当天**，否则被 CI
   的发布日期闸门拦下）、`CHANGELOG.md` 的段标题。`versionCode` 不要手改。
   **不要手算进位**：手写成 `1.1.20 → 1.1.21` 就违反了本口径，而 CI 要等打 tag 才拦得住。
   `--apply` 只插 CHANGELOG 标题不写内容，条目仍要人补。
2. **合 main**：功能分支合并到 `main` 并推送
3. **打标签**：`git tag -a V<versionName> -m "KOiL V<versionName>"` → `git push origin V<versionName>`
   （如本次是 `1.2.0`，就写 `V1.2.0`）
4. CI 自动构建、校验签名、生成 `KOiL-V*-vc*-release.apk`，挂到同名 Release

标签推送即发版，没有二次确认。推之前务必确认 `versionName` 已递增。

## 版本号规则

| 项 | 规则 |
|---|---|
| `versionName` | 纯数字、不带 `V`（写 `V1.2.0` 会让 CI 断言失败）；按**二十进制**推进，三段各自取值 `0..20`、满 `20` 进位：`1.1.19 → 1.1.20`、`1.1.20 → 1.2.0`、`1.2.20 → 1.3.0`、`1.20.20 → 2.0.0`；用 `scripts/bump_version.py` 推进 |
| `versionCode` | 只增不减：Build 用 `run_number`；Release 用 `max(已发布最大 -vcN, 最近 Build run_number) + 1`；不要手改 |
| 标签 | 大写 `V`（`V1.0.0`）；去掉 V 必须等于 `versionName` |
| 附件名 | `KOiL-<tag>-vc<versionCode>-release.apk` |

`versionCode` 的取值规则：只增不减，正式版必须高于任何已分发安装包。

```
KOIL_CI_RUN_NUMBER  →  KOIL_CI_VERSION_CODE  →  1
```

CI 里由 `run_number` 注入；本地不带环境变量时回落到基准值 `1`。

## 本地出测试包

本地包如果沿用基准值 `1`，会低于线上版本，系统拒绝安装。出本地包时显式指定当前 CI 最大 run 号：

```bash
KOIL_CI_VERSION_CODE=<当前最大 run_number> ./gradlew :app:assembleRelease
```

- 本地包的号**不得高于**下一次 CI 构建的号；也不要手工跳到一个很大的数，否则会挡住后续所有正式版
- 同一轮迭代可以反复用同一个号：同签名、同 versionCode 允许覆盖安装
- `versionName` 保持和已发布版本一致，否则应用内更新提示会失真

### 本地包与 CI 的号段关系

| 环节 | versionCode |
|---|---|
| 已产出的最大 CI 运行（run N） | N |
| 本地出测试包（可反复出多个） | ≤ N |
| 发版第 1 步：合 `main` 触发 CI | N + 1（仅 artifact，不发布） |
| 发版第 2 步：推 `V*` 标签 | N + 2（发布 Release） |

`run_number` 按 CI 运行次数递增，任何 push 都会消耗一个号，所以正式版比本地包高 2 只是发版路径上的典型差值，不是固定规律。安全前提只有一条：本地包号不高于下一次 CI 的号。

发过一版之后要重算：手机上换成正式版后，本地包不能再用低于已装版本的号（Android 禁止降级安装）。

## 签名

签名材料不入库，只放在 GitHub Actions Secrets：

| Secret | 内容 |
|---|---|
| `SIGNING_KEYSTORE_B64` | `koil-release.keystore` 的 base64 |
| `SIGNING_PROPERTIES` | `keystore.properties` 全文 |

工作流在构建前解码写入 `keystore/`。两个 Secret 缺一即构建失败，不会静默回落 debug 签名。上传与发布前还会断言正式证书 SHA-256 指纹：

```
cc962f1e2524ba8273c9d668436e9926d8a056e49b816e680c730518d46ffe4b
```

## CI 发布前校验

任一不符直接 fail，不会发出错包：

- 两个签名 Secret 齐全且可恢复
- 产物为正式证书签名，指纹匹配
- `versionCode` 严格高于已发布最大值
- 标签格式为 `V数字.数字.数字`
- 标签去掉 V 后等于 `versionName`
- 版本不低于 `1.0.0`，且严格高于已有最高标签
- 标签提交是 `main` 的祖先
- 同名 Release 不存在
- `KOIL_RELEASE_DATE` 等于标签提交日期或其次日（拦住「发版忘了改发布日期」）

## 注意

- **同名标签会覆盖 Release 附件**：CI 检测到同名 Release 已存在会直接失败；不要在不改代码的情况下重复推同一个 tag
- `keystore/` 等价于私钥。分发源码、打包或上传网盘前必须删除，`.gitignore` 只在 git 操作时生效
- CI 计数被重置（换平台、重建流水线）会让 `run_number` 归零，CI 的「versionCode 不得回退」断言会拦下这类事故
- **发布日期闸门按「标签提交日期」比对**：tag 打在 merge commit 上时，取的是**合并日**。所以版本号 bump 在昨天、
  今天才合并打 tag 的话，`KOIL_RELEASE_DATE` 要写**合并日**（今天），写成 bump 那天会被拦 —— 这是刻意的，
  不是闸门坏了；闸门允许「当天或次日」正是为了兜住 23:59 提交 / 00:10 打 tag 这种跨天操作
