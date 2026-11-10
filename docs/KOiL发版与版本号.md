# KOiL 发版与版本号

面向维护者。README 只保留构建入口，这里记录发版操作、版本号约束与签名配置。

## 发版流程

1. **改版本名**：`app/build.gradle.kts` 里的 `versionName` 手动递增（当前基线 `1.0.0`），`versionCode` 不要手改
2. **合 main**：功能分支合并到 `main` 并推送
3. **打标签**：`git tag -a V1.0.0 -m "KOiL V1.0.0"` → `git push origin V1.0.0`
4. CI 自动构建、校验签名、生成 `KOiL-V*-vc*-release.apk`，挂到同名 Release

标签推送即发版，没有二次确认。推之前务必确认 `versionName` 已递增。

## 版本号规则

| 项 | 规则 |
|---|---|
| `versionName` | 手动递增，纯数字（`1.0.0`）；写 `V1.0.0` 会导致 CI 断言失败 |
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

## 注意

- **同名标签会覆盖 Release 附件**：CI 检测到同名 Release 已存在会直接失败；不要在不改代码的情况下重复推同一个 tag
- `keystore/` 等价于私钥。分发源码、打包或上传网盘前必须删除，`.gitignore` 只在 git 操作时生效
- CI 计数被重置（换平台、重建流水线）会让 `run_number` 归零，CI 的「versionCode 不得回退」断言会拦下这类事故
