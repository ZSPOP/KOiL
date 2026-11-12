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
# 全程 best-effort：任何一步失败只 ::warning 并 exit 0，不阻断调用方。
# 因此本脚本刻意不开 set -e —— 镜像挂了不该让正式发版变红。
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

APKF_PATH="${1:-}"
TAG="${2:-${GITHUB_REF_NAME:-}}"
APKF="$(basename "${APKF_PATH:-}")"

GITEE_MIRROR_KEEP="${GITEE_MIRROR_KEEP:-5}"
GITEE_MIRROR_DRY_RUN="${GITEE_MIRROR_DRY_RUN:-true}"

API="https://gitee.com/api/v5/repos/${GITEE_OWNER:-}/${GITEE_REPO:-}"
DOWN_URL="https://gitee.com/${GITEE_OWNER:-}/${GITEE_REPO:-}/releases/download/${TAG}/${APKF}"

# 守卫①：ref 必须是 V 标签。dispatch 时 ref 是必选项，选到分支会拿 "main" 当 tag，
# 在 Gitee 建出一条叫 main 的 Release。
if [[ ! "$TAG" =~ ^V[0-9]+ ]]; then
  echo "::warning::Gitee 镜像跳过：ref 不是 V 标签（${TAG:-<空>}）"
  exit 0
fi
if [ -z "${GITEE_TOKEN:-}" ] || [ -z "${GITEE_OWNER:-}" ] || [ -z "${GITEE_REPO:-}" ]; then
  echo "::warning::Gitee 镜像跳过：GITEE_TOKEN / GITEE_OWNER / GITEE_REPO 未全部配置"
  exit 0
fi
if [ ! -f "${APKF_PATH}" ]; then
  echo "::warning::Gitee 镜像跳过：找不到产物 ${APKF_PATH:-<空>}"
  exit 0
fi

# 令牌只走 header，绝不拼进 URL —— URL 会原样出现在 Actions 日志里。
AUTH=(-H "Authorization: token ${GITEE_TOKEN}")

# ── ①建 Release。幂等：同名 tag 已有 Release 就复用，不重建。
#    重建的唯一手段是整条删除（限制①），会让重跑变成破坏性操作。
REL=$(curl -sS --retry 2 "${AUTH[@]}" "${API}/releases/tags/${TAG}" 2>/dev/null || true)
REL_ID=$(printf '%s' "$REL" | jq -r 'if type=="object" then (.id // empty) else empty end' 2>/dev/null || true)
if [ -n "$REL_ID" ]; then
  echo "复用已存在的 Gitee Release：${TAG}（id=${REL_ID}）"
else
  REL=$(curl -sS --retry 2 -X POST "${AUTH[@]}" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg t "$TAG" '{tag_name:$t, name:$t, target_commitish:"master", prerelease:false,
          body:("Gitee 镜像，正式签名包，源码与完整发布说明见 GitHub Release " + $t)}')" \
    "${API}/releases" 2>/dev/null || true)
  REL_ID=$(printf '%s' "$REL" | jq -r 'if type=="object" then (.id // empty) else empty end' 2>/dev/null || true)
  if [ -z "$REL_ID" ]; then
    echo "::warning::Gitee 建 Release 失败：$(printf '%s' "$REL" | head -c 300)"
    echo "::warning::Gitee 镜像中止（不影响 GitHub Release 已发布）"
    exit 0
  fi
  echo "已建 Gitee Release：${TAG}（id=${REL_ID}）"
fi

# ── ②上传附件。multipart + 字段名 file；Gitee 没有 GitHub 那种预签名上传流程。
#    已有同名附件就跳过，保证重复 dispatch 幂等。
EXIST=$(curl -sS --retry 2 "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
  | jq -r '(.assets // [])[].name' 2>/dev/null | grep -x -- "$APKF" || true)
UP_URL=""
if [ -n "$EXIST" ]; then
  echo "附件已存在，跳过上传（幂等重跑）"
else
  UP=$(curl -sS --retry 2 -X POST "${AUTH[@]}" \
    -F "file=@${APKF_PATH}" "${API}/releases/${REL_ID}/attach_files" 2>/dev/null || true)
  UP_URL=$(printf '%s' "$UP" | jq -r '.browser_download_url // empty' 2>/dev/null || true)
  # 守卫②：上传响应里没有 browser_download_url 不代表上传失败 —— 可能只是字段名/结构
  # 与预期不同。此时回读 Release 确认附件是否真的落地，别把成功误判成失败。
  if [ -z "$UP_URL" ]; then
    if curl -sS --retry 2 "${AUTH[@]}" "${API}/releases/${REL_ID}" 2>/dev/null \
         | jq -e --arg n "$APKF" '(.assets // []) | map(.name) | index($n) != null' >/dev/null 2>&1; then
      echo "上传响应未含 browser_download_url，但回读确认附件已存在：${APKF}"
    else
      echo "::warning::Gitee 上传附件失败：$(printf '%s' "$UP" | head -c 300)"
      echo "::warning::Gitee 镜像中止（不影响 GitHub Release 已发布）"
      exit 0
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
PROBE=$(curl -sSL -o /dev/null -w '%{http_code} %{size_download}' --retry 2 "$CHECK_URL" 2>/dev/null || echo "000 0")
CODE=${PROBE%% *}
SIZE=${PROBE##* }
if [ "$CODE" != "200" ] || [ "$SIZE" != "$LOCAL_SIZE" ]; then
  if [ "$CHECK_URL" != "$DOWN_URL" ]; then
    echo "::warning::用上传响应地址校验失败（HTTP ${CODE} / ${SIZE} 字节），回退自拼 URL 再试"
    PROBE=$(curl -sSL -o /dev/null -w '%{http_code} %{size_download}' --retry 2 "$DOWN_URL" 2>/dev/null || echo "000 0")
    CODE=${PROBE%% *}
    SIZE=${PROBE##* }
  fi
fi
if [ "$CODE" != "200" ] || [ "$SIZE" != "$LOCAL_SIZE" ]; then
  echo "::warning::Gitee 镜像校验失败：HTTP ${CODE}，远端 ${SIZE} 字节 ≠ 本地 ${LOCAL_SIZE} 字节（${CHECK_URL}）"
  echo "::warning::校验不通过，跳过清理（不确定远端状态时不动删除）"
  exit 0
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
#    排序用 created_at 而不是 tag 名：要的是「最近发布」，与语义版本无关
#    （tag 名排序在 V1.9 / V1.10 这类跨版本号上会错位）。
ALL=$(curl -sS --retry 2 "${AUTH[@]}" "${API}/releases?per_page=100" 2>/dev/null || true)
if ! printf '%s' "$ALL" | jq -e 'type=="array"' >/dev/null 2>&1; then
  echo "::warning::Gitee Release 列表读取失败，跳过清理：$(printf '%s' "$ALL" | head -c 200)"
  exit 0
fi
TOTAL=$(printf '%s' "$ALL" | jq 'length')
# 单独打印「镜像版本数」：清理只对满足 a/b 两道口径的条目计数，
# 直接拿 TOTAL 去对照保留数会让人误判 dry-run 清单（比如 8 条里只有 6 条可计）。
ELIGIBLE=$(printf '%s' "$ALL" | jq -r '
  map(select(.tag_name | test("^V[0-9]")))
  | map(select(((.assets // []) | map(.name // "") | map(select(test("^KOiL-"))) | length) > 0))
  | length')
echo "Gitee 现有 ${TOTAL} 个 Release，其中镜像版本 ${ELIGIBLE} 个，保留最近 ${GITEE_MIRROR_KEEP} 个（dry-run=${GITEE_MIRROR_DRY_RUN}）"
TO_DEL=$(printf '%s' "$ALL" | jq -r \
  --argjson keep "$GITEE_MIRROR_KEEP" --arg cur "$TAG" '
  sort_by(.created_at) | reverse
  | map(select(.tag_name | test("^V[0-9]")))
  | map(select(((.assets // []) | map(.name // "") | map(select(test("^KOiL-"))) | length) > 0))
  | .[$keep:]
  | map(select(.tag_name != $cur))
  | .[] | "\(.id)\t\(.tag_name)"')
if [ -z "$TO_DEL" ]; then
  echo "无需清理"
  exit 0
fi
while IFS=$'\t' read -r rid rtag; do
  [ -z "${rid:-}" ] && continue
  if [ "$GITEE_MIRROR_DRY_RUN" = "true" ]; then
    echo "[dry-run] 将删除 Release id=${rid} / tag=${rtag}（Release 与 tag 各一次调用，不是同一次）"
    continue
  fi
  RC=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "${AUTH[@]}" "${API}/releases/${rid}" 2>/dev/null || echo 000)
  case "$RC" in
    200|204) echo "已删 Release id=${rid}（HTTP ${RC}）" ;;
    *) echo "::warning::删 Release id=${rid} 返回 HTTP ${RC}（继续，不阻断后续项）" ;;
  esac
  TC=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE "${AUTH[@]}" "${API}/tags/${rtag}" 2>/dev/null || echo 000)
  case "$TC" in
    200|204) echo "已删 tag ${rtag}（HTTP ${TC}）" ;;
    *) echo "::warning::删 tag ${rtag} 返回 HTTP ${TC}（继续，不阻断）" ;;
  esac
done <<< "$TO_DEL"
