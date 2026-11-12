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
fail() {
  echo "::warning::$1"
  if [ "${GITHUB_EVENT_NAME:-}" = "workflow_dispatch" ]; then
    echo "::error::Gitee 补传失败（${TAG:-<空>}）：镜像未完成，Gitee 上可能留下没有 APK 的 Release"
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
  local tag="$1" log=/tmp/gitee-deltag.log
  # 单引号：凭据由 git 起 sh 时代展开，不在本脚本里提前拼进字符串，
  # token 里含特殊字符（@ : / 等）也不会破坏命令。
  # 第一个空的 credential.helper= 是重置：否则全局或 checkout 留下的
  # gh auth git-credential 会先被调用、把 GitHub 凭据交给 Gitee，认证必然失败。
  local helper='!f() { echo "username=$GITEE_OWNER"; echo "password=$GITEE_TOKEN"; }; f'
  if GIT_TERMINAL_PROMPT=0 git -c credential.helper= -c credential.helper="$helper" \
       push "https://gitee.com/${GITEE_OWNER}/${GITEE_REPO}.git" \
       --delete "refs/tags/${tag}" >"$log" 2>&1; then
    echo "已删 tag ${tag}（git push --delete）"
  else
    echo "::warning::删 tag ${tag} 失败：$(grep -vE '^remote:' "$log" | tail -3 | tr '\n' ' ' | head -c 300)"
  fi
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
CURL=(--connect-timeout 10 --max-time 180 --retry 2)
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
REL=$(curl -sS "${CURL[@]}" "${AUTH[@]}" "${API}/releases/tags/${TAG}" 2>/dev/null || true)
REL_ID=$(printf '%s' "$REL" | jq -r 'if type=="object" then (.id // empty) else empty end' 2>/dev/null || true)
if [ -n "$REL_ID" ]; then
  echo "复用已存在的 Gitee Release：${TAG}（id=${REL_ID}）"
else
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
EXIST=$(curl -sS "${CURL[@]}" "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
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
    if curl -sS "${CURL[@]}" "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
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
PROBE=$(curl -sSL -o /dev/null -w '%{http_code} %{size_download}' "${CHECK_CURL[@]}" "$CHECK_URL" 2>/dev/null || true)
PROBE=${PROBE:-000 0}
CODE=${PROBE%% *}
SIZE=${PROBE##* }
if [ "$CODE" != "200" ] || [ "$SIZE" != "$LOCAL_SIZE" ]; then
  if [ "$CHECK_URL" != "$DOWN_URL" ]; then
    echo "::warning::用上传响应地址校验失败（HTTP ${CODE} / ${SIZE} 字节），回退自拼 URL 再试"
    PROBE=$(curl -sSL -o /dev/null -w '%{http_code} %{size_download}' "${CHECK_CURL[@]}" "$DOWN_URL" 2>/dev/null || true)
    PROBE=${PROBE:-000 0}
    CODE=${PROBE%% *}
    SIZE=${PROBE##* }
  fi
fi
if [ "$CODE" != "200" ] || [ "$SIZE" != "$LOCAL_SIZE" ]; then
  fail "Gitee 镜像校验失败：HTTP ${CODE}，远端 ${SIZE} 字节 ≠ 本地 ${LOCAL_SIZE} 字节（${CHECK_URL}）；未通过校验故不执行清理"
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
ALL=$(curl -sS "${CURL[@]}" "${AUTH[@]}" "${API}/releases?per_page=100" 2>/dev/null || true)
if ! printf '%s' "$ALL" | jq -e 'type=="array"' >/dev/null 2>&1; then
  echo "::warning::Gitee Release 列表读取失败，跳过清理：$(printf '%s' "$ALL" | head -c 200)"
  exit 0
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
# LC_ALL=C：sort -V 的版本比较不受 locale 影响。-k2,2 只比 tag 字段，
# 否则 id 的数字会先参与比较（"123\tV1.1.10" 与 "45\tV1.1.9"）。
SORTED=$(printf '%s\n' "$CAND" | LC_ALL=C sort -V -t$'\t' -k2,2)
DEL_N=$(( ELIGIBLE - GITEE_MIRROR_KEEP ))
if [ "$DEL_N" -le 0 ]; then
  echo "无需清理"
  exit 0
fi
TO_DEL=$(printf '%s\n' "$SORTED" | head -n "$DEL_N" | awk -F'\t' -v cur="$TAG" '$2 != cur')
if [ -z "$TO_DEL" ]; then
  echo "无需清理（待删项只有当前 TAG）"
  exit 0
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
    echo "[dry-run] 将删除 Release id=${rid} / tag=${rtag}（Release 走 API、tag 走 git push --delete，两次独立调用）"
    continue
  fi
  # 不写 `|| echo 000`：与 UP_META 同一个坑 —— curl 失败时 -w 仍会输出 000，
  # 再追加一行会让 RC 变两行，`case` 匹配不到、还会被当成另一个错误码。
  RC=$(curl -sS -o /dev/null -w '%{http_code}' "${CURL[@]}" -X DELETE "${AUTH[@]}" "${API}/releases/${rid}" 2>/dev/null || true)
  RC=${RC:-000}
  case "$RC" in
    200|204) echo "已删 Release id=${rid}（HTTP ${RC}）" ;;
    *) echo "::warning::删 Release id=${rid} 返回 HTTP ${RC}（继续，不阻断后续项）" ;;
  esac
  del_tag "$rtag"
done <<< "$TO_DEL"
