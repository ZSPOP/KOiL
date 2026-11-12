#!/usr/bin/env bash
# 把发版 APK 镜像到 Gitee Release，并清理超出保留数的旧版本。
#
# 用法：gitee-mirror.sh <apk 文件路径> [tag]
#   tag 省略时取 $GITHUB_REF_NAME；附件名取 APK 的文件名（自带 -vcN）。
#
# 调用方（两处共用这一份，避免漂移）：
#   - android-release.yml：push V* 标签时自动镜像
#   - gitee-mirror.yml：workflow_dispatch 补传历史版本
#
# 失败语义按调用方区分（见下方 fail 函数）：
#   - android-release.yml（push 标签，自动发版）：只 ::warning + exit 0。
#     GitHub Release 已经发布了，镜像挂了不能连累正式发版。
#   - gitee-mirror.yml（workflow_dispatch，补传）：exit 1，让 job 变红。
#     补传链上没有需要保护的已发布产物，绿着留一个没有 APK 的空 Release
#     比红更糟 —— 静默缺失只能靠人工翻日志发现。
# 因此本脚本刻意不开 set -e，失败一律走 fail 显式出口。
#
# 已知限制（改之前先看完）：
#   ① attach_files 的删除接口实测返回 405，删不了单个附件。清理只能整条 Release 删，
#      而删 Release 不连带删 tag，所以真实删除是两次调用。
#   ② target_commitish 只能写 "master"：Gitee 默认分支，与 GitHub 的 SHA 不同源，
#      写 SHA 会 404。
#   ③ 下载走 302 跳转，校验必须 -L，否则只看得到 302。
#   ④ 用 GITHUB_TOKEN 建的 GitHub Release 不会再触发 release 事件（GitHub 防递归机制），
#      所以补传不能靠事件触发，只能手动 dispatch。

set -uo pipefail

# 唯一的失败出口。$1 是给人的原因，写清楚「哪一步、什么状态」。
# $2 可选：覆盖默认的 ::error 文案。默认那句说的是「镜像未完成」，但清理阶段的失败
# （比如孤儿 tag 删不掉）发生时上传与校验都已经过了，照抄会把排查方向带偏 ——
# 补传链的失败面不止「没传上去」一种。
fail() {
  echo "::warning::$1"
  if [ "${GITHUB_EVENT_NAME:-}" = "workflow_dispatch" ]; then
    echo "::error::${2:-Gitee 补传失败（${TAG:-<空>}）：镜像未完成，Gitee 上可能留下没有 APK 的 Release}"
    exit 1
  fi
  echo "::warning::Gitee 镜像中止（不影响发版流程）"
  exit 0
}

# 删 Gitee tag 只能走 git 协议：API 没有单 tag 接口 ——
# GET /repos/{o}/{r}/tags/{name} 与 DELETE 同一路径都返回 HTML 404（实测），
# 只有列表 GET /tags 和创建 POST /tags。原来用 DELETE API 的写法从未成功过，
# 每次都 404 但只是 warning，日志看着像收尾正常，实际 tag 一个没删。
#
# token 走 http.extraHeader 而不是拼进 URL：拼 URL 时 git 出错会把带 token 的
# remote 地址原样打进日志，而 secrets 一旦进日志就无法撤回。
del_tag() {
  # 前提：cwd 必须是 git 仓库（两条 workflow 都 checkout 了，满足）。
  # 单独抽出来在本机跑会报 "not a git repository" —— 这是预期行为，不是脚本坏了。
  local tag="$1" log=/tmp/gitee-deltag.log
  # 单引号：凭据由 git 起 sh 时代展开，token 含 @ : / 等字符也不会破坏命令。
  # 第一个空的 credential.helper= 是重置：否则全局或 checkout 留下的
  # gh auth git-credential 会先被调用、把 GitHub 凭据交给 Gitee，认证必然失败。
  local helper='!f() { echo "username=$GITEE_OWNER"; echo "password=$GITEE_TOKEN"; }; f'
  # timeout 120：全脚本唯一没有上界的网络操作。curl 三套参数各自有 --max-time，
  # 只有 git push 是裸的 —— 卡在认证握手 / DNS / TCP 黑洞时会一直挂到 job 被超时杀掉，
  # 日志只剩 cancelled，看不出是谁的锅。tag 只有几十字节，120s 足够。
  # 超时返回 124，落到下面的失败分支（return 1），下游的幂等与 continue 逻辑不变。
  if GIT_TERMINAL_PROMPT=0 timeout 120 git -c credential.helper= -c credential.helper="$helper" \
       push "https://gitee.com/${GITEE_OWNER}/${GITEE_REPO}.git" \
       --delete "refs/tags/${tag}" >"$log" 2>&1; then
    echo "已删 tag ${tag}（git push --delete）"
    return 0
  fi
  # 幂等：Release 删成功而 tag 删失败后重跑，会撞上「tag 已不存在」。
  # 这不是失败，按成功处理，否则每轮清理都会刷一条假 warning。
  if grep -q 'remote ref does not exist' "$log" 2>/dev/null; then
    echo "tag ${tag} 已不存在（幂等跳过）"
    return 0
  fi
  # 只对 token 值做兜底替换，再保留 remote: / fatal: 两类行 ——
  # 原来整片 grep -v '^remote:' 把最该看的
  # 「remote: ZSPOP: Incorrect username or password (access token)」也滤掉了。
  # URL 里本来就没有凭据，remote 行也不含 token。
  if [ -n "${GITEE_TOKEN:-}" ]; then
    # 不加 .bak：备份里是替换前的原文，等于把 token 留在了同目录，脱敏就没意义了。
    sed -i "s|${GITEE_TOKEN}|***|g" "$log" 2>/dev/null || true
    rm -f "$log.bak"
  fi
  echo "::warning::删 tag ${tag} 失败：$(grep -E '^(remote:|fatal:)' "$log" | tail -3 | tr '\n' ' ' | head -c 300)"
  return 1
}

APKF_PATH="${1:-}"
TAG="${2:-${GITHUB_REF_NAME:-}}"
APKF="$(basename "${APKF_PATH:-}")"

GITEE_MIRROR_KEEP="${GITEE_MIRROR_KEEP:-5}"
GITEE_MIRROR_DRY_RUN="${GITEE_MIRROR_DRY_RUN:-true}"

# 前置校验：KEEP 非法必须在任何网络请求之前拦住。否则要等上传跑完（实测最长 456s）
# 才在清理段报错，等于白传一次整个 APK。
if ! [[ "$GITEE_MIRROR_KEEP" =~ ^[0-9]+$ ]]; then
  fail "GITEE_MIRROR_KEEP 必须是非负整数，实际是 ${GITEE_MIRROR_KEEP}"
fi

API="https://gitee.com/api/v5/repos/${GITEE_OWNER:-}/${GITEE_REPO:-}"
DOWN_URL="https://gitee.com/${GITEE_OWNER:-}/${GITEE_REPO:-}/releases/download/${TAG}/${APKF}"

# 守卫①：ref 必须是 V 标签。dispatch 时 ref 是必选项，选到分支会拿 "main" 当 tag，
# 在 Gitee 建出一条叫 main 的 Release。
if [[ ! "$TAG" =~ ^V[0-9]+ ]]; then
  fail "Gitee 镜像跳过：ref 不是 V 标签（${TAG:-<空>}）"
fi
if [ -z "${GITEE_TOKEN:-}" ] || [ -z "${GITEE_OWNER:-}" ] || [ -z "${GITEE_REPO:-}" ]; then
  fail "Gitee 镜像跳过：GITEE_TOKEN / GITEE_OWNER / GITEE_REPO 未全部配置"
fi
if [ ! -f "${APKF_PATH}" ]; then
  fail "Gitee 镜像跳过：找不到产物 ${APKF_PATH:-<空>}"
fi

# 令牌只走 header，绝不拼进 URL —— URL 会原样出现在 Actions 日志里。
AUTH=(-H "Authorization: token ${GITEE_TOKEN}")
# 所有请求都必须带超时。缺这个会踩过：GitHub runner 在海外，上传到 Gitee 可能长时间
# 无响应，而 curl 默认无限等 —— 实测有一次 Mirror step 挂住 9 分钟仍未返回，
# 只能手动 cancel，否则会一直占着 job（默认超时 360 分钟）。
# --retry-all-errors 不能省：--retry 只重试白名单（5xx / 429 / 部分超时），
# 「连接被重置 / 空响应」（curl exit 52/56）不在里面 —— 实测对「收到即断」的服务
# 连发，--retry 2 只建立 1 条连接，一次都没重试。V1.1.8 的清理跳过就是这么来的
# （日志 `Gitee Release 列表读取失败，跳过清理：` 冒号后为空，那唯一的退出码被
#  2>/dev/null 吞掉了）。
# 需要 curl >= 7.71，GitHub runner 是 8.x；本机老 curl 会报 unknown option，属预期。
CURL=(--connect-timeout 10 --max-time 180 --retry 3 --retry-delay 5 --retry-max-time 90)
# retry-all-errors 只能给幂等请求。它会连 POST 一起重发 —— 建 Release 是非幂等的，
# 重发撞 400「已存在」→ 响应里没有 .id → fail → 落一个没有 APK 的空 Release 且
# 自动发版路径仍是绿的。那正是脚本头注释点名要避免的状态。
GET_CURL=("${CURL[@]}" --retry-all-errors)
# 列表单独一套：--retry-max-time 90 挡不住 --max-time 180。规则是「重试窗口超过
# retry-max-time 就不再重试」，而单次尝试本身就可能吃掉 180 秒 —— 一进去就已超窗，
# curl 一次都不重试，恰好最该重试的超时场景不重试；再加上应用层 3 轮，最坏
# 3×180 = 540 秒只为读几十 KB。一个 JSON 列表 30 秒足够，压到约 100 秒。
LIST_CURL=("${CURL[@]}" --max-time 30 --retry-all-errors)
# 上传单独一套参数：不开 --retry（POST 重试会重复上传整个文件）。
# 两条放弃线，缺一条都会变成「静默慢等」：
#   ①好消息线：30 秒内平均低于 4KB/s 就断 —— 这条路走不通，别占着 job。
#   ②硬上限：1200 秒。实测（run 43187462919，2026-11-12）6.6MB 用了 455.9 秒，
#     约 14.5KB/s —— 这是 GitHub runner（海外）→ Gitee（国内）的常态上行速度。
#     600 秒只留 32% 余量，速度掉到 11KB/s 就会超时；放到 1200 秒（对应 ≥5.5KB/s）
#     才容得下波动。代价是发版链多花约 8 分钟，已确认接受。
UP_CURL=(--connect-timeout 10 --max-time 1200 --speed-limit 4096 --speed-time 30)
# 下载校验单独一套：海外 runner 从 Gitee 拉 6.6MB，180 秒对应 ≥37KB/s，
# 慢链路会被误判成「镜像失败」并导致反复手查。放宽到 600 秒，
# 但保留限速放弃（60 秒内低于 2KB/s 才是真的死链）。
CHECK_CURL=(--connect-timeout 10 --max-time 600 --speed-limit 2048 --speed-time 60)

# ── ①建 Release。幂等：同名 tag 已有 Release 就复用，不重建。
#    重建的唯一手段是整条删除（限制①），会让重跑变成破坏性操作。
REL=$(curl -sS "${GET_CURL[@]}" "${AUTH[@]}" "${API}/releases/tags/${TAG}" 2>/dev/null || true)
REL_ID=$(printf '%s' "$REL" | jq -r 'if type=="object" then (.id // empty) else empty end' 2>/dev/null || true)
if [ -n "$REL_ID" ]; then
  echo "复用已存在的 Gitee Release：${TAG}（id=${REL_ID}）"
else
  # 建 Release 是非幂等的：这里刻意用 CURL（不带 --retry-all-errors）。
  REL=$(curl -sS "${CURL[@]}" -X POST "${AUTH[@]}" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg t "$TAG" '{tag_name:$t, name:$t, target_commitish:"master", prerelease:false,
          body:("Gitee 镜像，正式签名包，源码与完整发布说明见 GitHub Release " + $t)}')" \
    "${API}/releases" 2>/dev/null || true)
  REL_ID=$(printf '%s' "$REL" | jq -r 'if type=="object" then (.id // empty) else empty end' 2>/dev/null || true)
  if [ -z "$REL_ID" ]; then
    fail "Gitee 建 Release 失败：$(printf '%s' "$REL" | head -c 300)"
  fi
  echo "已建 Gitee Release：${TAG}（id=${REL_ID}）"
fi

# ── ②上传附件。multipart + 字段名 file；Gitee 没有 GitHub 那种预签名上传流程。
#    已有同名附件就跳过，保证重复 dispatch 幂等。
EXIST=$(curl -sS "${GET_CURL[@]}" "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
  | jq -r '(.assets // [])[].name' 2>/dev/null | grep -x -- "$APKF" || true)
UP_URL=""
if [ -n "$EXIST" ]; then
  echo "附件已存在，跳过上传（幂等重跑）"
else
  # 上传是整条链路唯一的长耗时操作，必须打出耗时与状态码：否则日志里只有「已建 Release」
  # 和下一行输出，看不出它是慢、是超时、还是被限速断开。
  echo "开始上传 ${APKF}（$(stat -c%s "${APKF_PATH}") 字节）"
  UP_META=$(curl -sS "${UP_CURL[@]}" -X POST "${AUTH[@]}" \
    -F "file=@${APKF_PATH}" -o /tmp/gitee-up.json -w '%{http_code} %{time_total}' \
    "${API}/releases/${REL_ID}/attach_files" 2>/dev/null || true)
  # 不写 `|| echo "000 0"`：curl 失败时 -w 仍然输出（形如 "000 0.000000"），
  # 再追加一行会让 UP_META 变两行、`${UP_META##* }` 取到后面那个 0。
  # 只有 curl 完全无输出时才兜底。
  UP_META=${UP_META:-000 0}
  UP=$(cat /tmp/gitee-up.json 2>/dev/null || true)
  UP_URL=$(printf '%s' "$UP" | jq -r '.browser_download_url // empty' 2>/dev/null || true)
  # 吞吐量直接算进日志：调阈值时不用再人工拿「字节 ÷ 耗时」手算。
  # 实测基线 14.5 / 35.2 / 14.8 KB/s（三个样本，2.4 倍波动）。
  UP_SECS=${UP_META##* }
  UP_BYTES=$(stat -c%s "${APKF_PATH}")
  UP_KBPS=$(awk -v b="$UP_BYTES" -v s="$UP_SECS" 'BEGIN{ if (s+0 > 0) printf "%.1f", b/1024/s; else printf "n/a" }' 2>/dev/null || echo n/a)
  echo "上传返回 HTTP ${UP_META%% *}，耗时 ${UP_SECS}s（${UP_KBPS} KB/s）"
  # 守卫②：上传响应里没有 browser_download_url 不代表上传失败 —— 可能只是字段名/结构
  # 与预期不同。此时回读 Release 确认附件是否真的落地，别把成功误判成失败。
  if [ -z "$UP_URL" ]; then
    if curl -sS "${GET_CURL[@]}" "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
         | jq -e --arg n "$APKF" '(.assets // []) | map(.name) | index($n) != null' >/dev/null 2>&1; then
      echo "上传响应未含 browser_download_url，但回读确认附件已存在：${APKF}"
    else
      fail "Gitee 上传附件失败：$(printf '%s' "$UP" | head -c 300)"
    fi
  else
    echo "已上传附件：${UP_URL}"
  fi
fi

# ── ③匿名下载校验。只信「上传返回成功」证明不了别人能下到正确文件：
#    优先用 Gitee 自己返回的下载地址（必然有效），自拼 URL 只作为 fallback ——
#    "附件 URL = /releases/download/{tag}/{file}" 这个规律没有在本地验证过。
CHECK_URL="${UP_URL:-$DOWN_URL}"
LOCAL_SIZE=$(stat -c%s "$APKF_PATH")

probe_size() {
  local url="$1" out
  out=$(curl -sSL -o /dev/null -w '%{http_code} %{size_download}' "${CHECK_CURL[@]}" "$url" 2>/dev/null || true)
  printf '%s\n' "${out:-000 0}"
}

# 校验必须重试。上传耗时一百多秒之后立刻拉 CDN，偶发 HTTP 000（连接建立失败）
# 不代表镜像坏了：V1.1.11 那次 runner 侧 000/0 字节，而实测 Gitee 直链是
# 200 / 6725974 字节，与本地一致。一次失败就报警 + 跳过清理，等于把网络抖动
# 记成「镜像没上去」，之后每次看见 warning 都会以为是真问题。
# 三次机会，退避 5s / 10s。成功返回 0。
check_url() {
  local url="$1" attempt
  for attempt in 1 2 3; do
    PROBE=$(probe_size "$url")
    CODE=${PROBE%% *}
    SIZE=${PROBE##* }
    if [ "$CODE" = "200" ] && [ "$SIZE" = "$LOCAL_SIZE" ]; then
      return 0
    fi
    echo "::warning::镜像校验第 ${attempt} 次未通过（HTTP ${CODE} / ${SIZE} 字节），$( [ "$attempt" -lt 3 ] && echo '稍后重试' || echo '已用尽重试' )"
    if [ "$attempt" -lt 3 ]; then
      sleep $((attempt * 5))
    fi
  done
  return 1
}

if ! check_url "$CHECK_URL"; then
  if [ "$CHECK_URL" != "$DOWN_URL" ]; then
    echo "::warning::用上传响应地址校验失败（HTTP ${CODE} / ${SIZE} 字节），回退自拼 URL 再试"
    if ! check_url "$DOWN_URL"; then
      fail "Gitee 镜像校验失败：HTTP ${CODE}，远端 ${SIZE} 字节 ≠ 本地 ${LOCAL_SIZE} 字节（${DOWN_URL}）；未通过校验故不执行清理"
    fi
  else
    fail "Gitee 镜像校验失败：HTTP ${CODE}，远端 ${SIZE} 字节 ≠ 本地 ${LOCAL_SIZE} 字节（${CHECK_URL}）；未通过校验故不执行清理"
  fi
fi
echo "Gitee 镜像校验通过：HTTP ${CODE} / ${SIZE} 字节 / ${CHECK_URL}"

# ── ④清理。Gitee 免费仓 1GB（代码+附件合并计算），单文件上限 100MB；
#    每版约 6.6MB，约 150 版才占满，短期不紧迫 —— 但手动清理必然会被忘记。
#
#    候选口径（三道，缺一道就可能静默删掉不该删的东西）：
#      a. tag 名匹配 ^V[0-9]   —— 排除手动建的、别的用途的 Release
#      b. 含 KOiL-* 附件        —— 排除没有镜像包的 Release
#      c. 排除当前 TAG          —— 补传旧版本时，该 Release 的 created_at 是旧的，
#                                  可能排在 keep 之外；不排除就会「刚上传成功、接着被删」
#
#    排序用版本号（sort -V），不用 created_at：清理的语义是「保留最新的几个版本」。
#    created_at 在补传场景下会失真 —— 补传的旧版本是刚创建的，会被当成「最近发布」
#    保护起来，而版本号更新的反而先被删（实测顺序：V1.1.5 先于 V1.1.3 被删）。
#    sort -V 做版本排序，能正确处理 V1.9 / V1.10 跨位数，不必退回纯字符串排序。
# 读列表最多试 3 次。--retry-all-errors 管不到「HTTP 200 + body 为空或被截断」，
# 而限流场景常见的正是这种形态，所以重试与判据都放在应用层：判据是能否 jq 出数组。
#
# 不能用 `|| true` 取 RC：那样拿到的是 true 的 0，退出码这个唯一能定性的信息就没了；
# 也不能 2>/dev/null —— V1.1.8 那次日志只剩「跳过清理：」后面一片空白，就是因为
# stderr 被吞，看不出是 reset(56) 还是超时(28)。
ALL=""; RC=0; BYTES=0
for _try in 1 2 3; do
  ERR=$(mktemp)
  ALL=$(curl -sS "${LIST_CURL[@]}" "${AUTH[@]}" "${API}/releases?per_page=100" 2>"$ERR"); RC=$?
  BYTES=$(printf '%s' "$ALL" | wc -c)
  # 必须先看字节数再交给 jq。实测 jq 1.6 对【空输入】时 `-e 'type=="array"'` 返回 0
  # （无输出也算通过），1.7 才正确返回 4；空 body 在 1.6 上会静默穿过守卫。
  #
  # 但这不是 V1.1.8 那次的原因：runner 是 jq 1.7，那次 warning 能打出来恰恰证明
  # $ALL 真是 0 字节、守卫工作正常（1.6 反而会一声不响地过，然后在 `jq 'length'`
  # 拿到空串）。这层防御针对的是旧镜像和本地开发环境。
  if [ "$BYTES" -gt 0 ] && printf '%s' "$ALL" | jq -e 'type=="array"' >/dev/null 2>&1; then
    rm -f "$ERR"
    break
  fi
  echo "::warning::读 Gitee Release 列表第 ${_try}/3 次失败（curl 退出码 ${RC}，返回 ${BYTES} 字节）：$(head -c 200 "$ERR" 2>/dev/null | tr '\n' ' ')"
  rm -f "$ERR"
  if [ "$_try" -lt 3 ]; then sleep 5; fi
done
if [ "$BYTES" -eq 0 ] || ! printf '%s' "$ALL" | jq -e 'type=="array"' >/dev/null 2>&1; then
  # （字节数判据同上：jq 1.6 会让空输入穿过，所以不能只靠 jq）
  # 走 fail 而不是硬编码 exit 0：fail 按调用方分流 —— 自动发版只 warning + exit 0
  # （Release 已发布，不能连累发版链），补传链 exit 1 让 job 变红。原来的 exit 0
  # 让补传读不到列表也绿着过去，正是 #38 定的「补传链不能绿着留残缺」在别处复发。
  fail "Gitee Release 列表三次读取均失败，本次未执行清理（上传与校验已完成，镜像可用）" \
       "Gitee 补传失败（${TAG}）：清理阶段未执行 —— 列表读取三次都失败。APK 与 Release 已保留，重跑一次即可按当时列表重算（清理是幂等的）"
fi
TOTAL=$(printf '%s' "$ALL" | jq 'length')
# 单独打印「镜像版本数」：清理只对满足 a/b 两道口径的条目计数，
# 直接拿 TOTAL 去对照保留数会让人误判 dry-run 清单（比如 8 条里只有 6 条可计）。
CAND=$(printf '%s' "$ALL" | jq -r '
  map(select(.tag_name | test("^V[0-9]")))
  | map(select(((.assets // []) | map(.name // "") | map(select(test("^KOiL-"))) | length) > 0))
  | .[] | "\(.id)\t\(.tag_name)"')
ELIGIBLE=$(printf '%s\n' "$CAND" | grep -c . || true)
echo "Gitee 现有 ${TOTAL} 个 Release，其中镜像版本 ${ELIGIBLE} 个，保留版本号最高的 ${GITEE_MIRROR_KEEP} 个（dry-run=${GITEE_MIRROR_DRY_RUN}）"
# 孤儿 tag 点名清扫。
#
# 位置有两个约束：必须在拿到 ALL（/releases 列表）之后 —— 要自证「这个 tag 确实
# 没有 Release」；又必须在 DEL_N 的「无需清理 -> exit 0」之前 —— 否则这一步永远跑不到。
#
# 为什么要点名：候选口径是从 /releases 构造的，没有 Release 的 tag 永远扫不到它。
# 删除顺序对调后不会再新增孤儿，但历史遗留（V1.1.3 / V1.1.4）得点名清一次。
#
# 三道守卫都不是可选的。点错一个名字删掉当前正在补传的 TAG，会造出「有 Release
# 无 tag」的反向残缺 —— 比孤儿更难查，因为 Release 列表看着还是对的。
# 孤儿清扫的失败标记。见下方 else 分支：这里的删除没有自愈路径，失败必须让整条链红。
ORPHAN_FAIL=0

if [ -n "${GITEE_MIRROR_EXTRA_TAGS:-}" ]; then
  REL_TAGS=$(printf '%s' "$ALL" | jq -r '.[].tag_name' 2>/dev/null || true)
  # 不能写 `for _t in ${X//,/ }`：未加引号会做单词分裂 + pathname expansion，
  # tag 名里带 * 或 ? 时会在下面的 ^V[0-9] 守卫之前就被展开成文件名，守卫形同虚设。
  IFS=',' read -ra _tags <<< "$GITEE_MIRROR_EXTRA_TAGS"
  for _t in "${_tags[@]}"; do
    _t="${_t//[[:space:]]/}"
    [ -z "$_t" ] && continue
    if ! [[ "$_t" =~ ^V[0-9] ]]; then
      echo "::warning::跳过非法 tag 名 ${_t}（必须匹配 ^V[0-9]，避免误删 main 之类）"
      continue
    fi
    if [ "$_t" = "$TAG" ]; then
      echo "::warning::跳过当前 TAG ${_t}（它正是本次补传的版本）"
      continue
    fi
    if printf '%s\n' "$REL_TAGS" | grep -qxF "$_t"; then
      echo "::warning::${_t} 仍有 Release，不是孤儿，跳过（请改用 keep 清理）"
      continue
    fi
    if [ "$GITEE_MIRROR_DRY_RUN" = "true" ]; then
      echo "[dry-run] 将删除孤儿 tag ${_t}（/releases 里已无此 Release）"
    else
      # 必须接住返回值。孤儿 tag 不在 /releases 里，清理段的候选口径永远扫不到它 ——
      # 这里失败就没有第二次机会（重跑同一个 dispatch 才会重试），而裸调 del_tag
      # 会把 return 1 丢掉，接着 DEL_N 算出「无需清理」exit 0，变成「专门来删孤儿、
      # 删失败了还绿」。与 #38 定的「补传链不能绿着留残缺」是同一条原则。
      if ! del_tag "$_t"; then
        echo "::error::孤儿 tag ${_t} 删除失败且无自愈路径（/releases 里本就没有它），需手工 git push --delete refs/tags/${_t}"
        ORPHAN_FAIL=1
      fi
    fi
  done
fi
# LC_ALL=C：sort -V 的版本比较不受 locale 影响。-k2,2 只比 tag 字段，
# 否则 id 的数字会先参与比较（"123\tV1.1.10" 与 "45\tV1.1.9"）。
SORTED=$(printf '%s\n' "$CAND" | LC_ALL=C sort -V -t$'\t' -k2,2)
DEL_N=$(( ELIGIBLE - GITEE_MIRROR_KEEP ))
if [ "$DEL_N" -le 0 ]; then
  # 孤儿删除失败时这条「无需清理」会把失败吞掉：ELIGIBLE <= KEEP 是常态（Gitee 2~3 条、
  # keep=5），日志只剩一行好看的 exit 0。必须在退出口之前先缴失败。
  if [ "$ORPHAN_FAIL" = "1" ]; then
    fail "孤儿 tag 清扫失败（见上方 ::error），Release 清理也无对象可删" \
      "Gitee 清理失败（${TAG}）：镜像已完成（上传与校验均通过），但孤儿 tag 清理失败，需手工 git push --delete"
  fi
  echo "无需清理"
  exit 0
fi
TO_DEL=$(printf '%s\n' "$SORTED" | head -n "$DEL_N" | awk -F'\t' -v cur="$TAG" '$2 != cur')
if [ -z "$TO_DEL" ]; then
  if [ "$ORPHAN_FAIL" = "1" ]; then
    fail "孤儿 tag 清扫失败（见上方 ::error），待删项只有当前 TAG" \
      "Gitee 清理失败（${TAG}）：镜像已完成（上传与校验均通过），但孤儿 tag 清理失败，需手工 git push --delete"
  fi
  echo "无需清理（待删项只有当前 TAG）"
  exit 0
fi
# dry-run 且真的有待删项 —— 这是唯一需要人拍板的时刻。workflow 注释里写「约第 6 条时
# 改成 false」只能靠人记，而那天大家看到的只是一行「无需清理」。让它自己进 annotations：
# 平时不吵，一旦出现就挂在 run 列表上，翻一眼就能看到。
if [ "$GITEE_MIRROR_DRY_RUN" = "true" ]; then
  DEL_PREVIEW=$(printf '%s\n' "$TO_DEL" | grep -c . || true)
  # 文案要说「你这一次」该怎么做。自动链默认已是 dry_run=false，所以能走到这里的
  # 基本只有手动 dispatch（gitee-mirror.yml 的 inputs.dry_run 默认 true）——
  # 对着一个 workflow 里明明写着 false 的人说「把 GITEE_MIRROR_DRY_RUN 改成 false」，
  # 他只会不知道去哪改。
  echo "::warning::dry-run：本次已列出 ${DEL_PREVIEW} 条待删项（清单见上方 [dry-run] 行），未执行删除。确认清单无误后重新 dispatch 并把 dry_run 设为 false"
fi

# 真实删除前的显式声明。dry_run 原本要改两个文件的 env 才生效，现在开了 dispatch
# 输入后点一下下拉框就能真删，配 keep=0 可以一次清空所有镜像 Release。
# 原来那层摩擦是安全阀，撤掉后至少要保证日志里躲不过这一行 —— 否则扫日志时
# 真实删除和演练的观感几乎一样。
if [ "$GITEE_MIRROR_DRY_RUN" != "true" ]; then
  DEL_COUNT=$(printf '%s\n' "$TO_DEL" | grep -c . || true)
  # keep=0 时「保留 0 个」是字面实话但会让人以为删干净了 —— 实际当前 TAG 被排除在
  # 删除清单外，会留下一条。真删场景下这行是唯一确认信息，必须说准。
  if [ "$GITEE_MIRROR_KEEP" = "0" ]; then
    echo "::warning::dry-run=false，本次将真实删除 ${DEL_COUNT} 个 Gitee Release（仅保留当前 ${TAG}）"
  else
    echo "::warning::dry-run=false，本次将真实删除 ${DEL_COUNT} 个 Gitee Release（保留版本号最高的 ${GITEE_MIRROR_KEEP} 个）"
  fi
fi
while IFS=$'\t' read -r rid rtag; do
  [ -z "${rid:-}" ] && continue
  if [ "$GITEE_MIRROR_DRY_RUN" = "true" ]; then
    echo "[dry-run] 将删除 tag=${rtag} / Release id=${rid}（tag 走 git push --delete，Release 走 API；先 tag 后 Release）"
    continue
  fi
  # 顺序：先删 tag、后删 Release。
  #
  # 反过来（先 Release 后 tag）是孤儿 tag 的成因：tag 删失败时 Release 已经没了，
  # 留下的「有 tag 无 Release」对本脚本的候选口径（从 /releases 构造）永久不可见 ——
  # 下一轮、下十轮都不会再被扫到，故障无法自愈。V1.1.3 / V1.1.4 就是这么攒下的。
  #
  # 对调后 tag 删失败 → Release 还在 → 下一轮仍是候选，自动重试。
  # 代价是 Release 成功而 tag 失败时会多刷一轮 del_tag，已按幂等处理（见 del_tag）。
  if ! del_tag "$rtag"; then
    # 接住返回值是整个对调的意义所在。不接的话 del_tag 的 return 1 只是装饰性的
    # （本脚本没开 set -e），下一行照样删 Release，孤儿 tag 一模一样地被重新造出来。
    echo "::warning::删 tag ${rtag} 失败，保留 Release id=${rid} 供下一轮重试"
    continue
  fi
  # 不写 `|| echo 000`：与 UP_META 同一个坑 —— curl 失败时 -w 仍会输出 000，
  # 再追加一行会让 RC 变两行，`case` 匹配不到、还会被当成另一个错误码。
  # DELETE 放 GET_CURL 是安全的：404 已映射成「随 tag 一并删除，视为成功」，
  # 重复删的观感和结果一致；这样「非幂等不重试」就只有建 Release 一个例外，好记。
  RC=$(curl -sS -o /dev/null -w '%{http_code}' "${GET_CURL[@]}" -X DELETE "${AUTH[@]}" "${API}/releases/${rid}" 2>/dev/null || true)
  RC=${RC:-000}
  case "$RC" in
    200|204) echo "已删 Release id=${rid}（HTTP ${RC}）" ;;
    # 404 不是失败。id 是刚从 /releases 读出来的，返回 404 只可能是它已经不在了 ——
    # 实测 Gitee 删 tag 会级联删掉挂在上面的 Release（反方向不成立：删 Release 不删 tag）。
    # 上面 del_tag 成功时，这里必然 404。判成失败会让日志看着像删 Release 没成功，
    # 而实际目标早就达成了，只能靠人肉对照 id 才敢确认。
    #
    # 本分支成立的前提是：上方 del_tag 失败会 continue —— 能走到这里只可能是 tag 已删、
    # Release 被级联带走。若改回「先 Release 后 tag」或去掉那个 continue（见 #39），
    # 404 就会重新变成「真失败被静默吞掉」。
    404) echo "Release id=${rid} 已不存在（随 tag 一并删除），视为成功" ;;
    *) echo "::warning::删 Release id=${rid} 返回 HTTP ${RC}（继续，不阻断后续项）" ;;
  esac
done <<< "$TO_DEL"

# 有 Release 真删的路径也要缴一次：否则孤儿失败会被后面的删除日志淹掉。
#
# 必须写成 if + 显式 exit 0。`[ ... ] && fail` 在 ORPHAN_FAIL=0 时短路返回 1，
# 而这已经是脚本最后一句 —— 进程退出码就是 1，于是「清理成功、没有任何孤儿失败」
# 的主路径会无条件红。前面两个出口用同样写法是安全的，只因为后面紧跟 exit 0；
# 那个前提没写在任何地方，这里索性统一成 if。
if [ "$ORPHAN_FAIL" = "1" ]; then
  fail "孤儿 tag 清扫失败（见上方 ::error），Release 清理已按计划完成" \
      "Gitee 清理失败（${TAG}）：镜像已完成（上传与校验均通过），但孤儿 tag 清理失败，需手工 git push --delete"
fi
exit 0
