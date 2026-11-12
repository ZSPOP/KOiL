#!/usr/bin/env bash
# gitee-mirror.sh 的离线演练。CI 里 PR Check 只跑 Gradle，这个脚本一行都不会被执行，
# 而这条链的全部价值都在【失败路径】上 —— 恰恰是正常发版永远跑不到的部分。
# V1.1.9 发出去也只能验证「正常路径 0 条 warning」，重试逻辑本身仍然是零覆盖。
#
# 用法：bash .github/scripts/test-gitee-mirror.sh
# 退出码 0 = 全部通过；非 0 = 有用例失败（会打印失败场景名）。
#
# 原理：造一个假的 curl 放 PATH 最前面，按 URL 形态返回固定响应；用 git 真身（只有
# del_tag 会用到，且 stub 环境下大概率失败，落到 continue 分支，不影响计数断言）。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIRROR="${SCRIPT_DIR}/gitee-mirror.sh"

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# ── 假 curl ────────────────────────────────────────────────────────────────
# 支持 -o / -w / -X DELETE；对 /releases?per_page=100 可按 $WORK/failcount 模拟
# 「收到即断」（curl exit 56，body 0 字节）。所有 DELETE 记到 $WORK/deletes.log。
cat > "$WORK/bin/curl" <<'STUB'
#!/usr/bin/env bash
args=("$@"); url=""; out=""; fmt=""; method="GET"
for ((i=0;i<${#args[@]};i++)); do
  case "${args[$i]}" in
    -o) out="${args[$((i+1))]}";;
    -w) fmt="${args[$((i+1))]}";;
    -X) method="${args[$((i+1))]}";;
    http*) url="${args[$i]}";;
  esac
done
[ "$method" = "DELETE" ] && echo "$url" >> "$STUB_WORK/deletes.log"

if [[ "$url" == *"releases?per_page"* && -f "$STUB_WORK/failcount" ]]; then
  n=$(cat "$STUB_WORK/failcount" 2>/dev/null || echo 0)
  if [ "$n" -gt 0 ]; then
    echo $((n-1)) > "$STUB_WORK/failcount"
    echo "curl: (56) Recv failure: Connection reset by peer" >&2
    exit 56
  fi
fi

code=200; body=""
if   [[ "$url" == *"/releases/tags/"* ]]; then code=404; body='{"message":"Not Found"}'
elif [[ "$url" == *"releases?per_page"* ]]; then
  code=200
  # 附件名必须是真实形态：清理只对「assets 里有 KOiL- 开头的文件」的条目计数
  # （见 CAND 的 jq 筛选），用 a.apk 这类假名会让 ELIGIBLE=0、永远走「无需清理」，
  # 删除段根本执行不到。
  body='[{"id":1,"tag_name":"V1.1.3","assets":[{"name":"KOiL-V1.1.3-vc90-release.apk"}]},{"id":2,"tag_name":"V1.1.4","assets":[{"name":"KOiL-V1.1.4-vc98-release.apk"}]},{"id":3,"tag_name":"V1.1.5","assets":[{"name":"KOiL-V1.1.5-vc105-release.apk"}]},{"id":4,"tag_name":"V1.1.6","assets":[{"name":"KOiL-V1.1.6-vc125-release.apk"}]},{"id":5,"tag_name":"V1.1.7","assets":[{"name":"KOiL-V1.1.7-vc130-release.apk"}]},{"id":6,"tag_name":"V1.1.8","assets":[{"name":"KOiL-V1.1.8-vc141-release.apk"}]}]'
elif [[ "$url" == *"/attach_files"* ]]; then code=201; body='{"browser_download_url":"https://gitee.example/dl.apk"}'
elif [[ "$url" == *"/releases" && "$method" == "POST" ]]; then code=201; body='{"id":99001,"tag_name":"V1.1.9"}'
elif [[ "$url" == *"/releases/99001"* ]]; then code=200; body='{"assets":[]}'
else code=200; body='x'; fi

[ -n "$out" ] && printf '%s' "$body" > "$out"
if [ -n "$fmt" ]; then
  # 必须按格式串逐个替换，不能一律返回「code time」：脚本 :403 用的是 -w '%{http_code}'，
  # 真 curl 只输出 200。返回 "200 0.100" 会让 case "$RC" 的 200|204 匹配不到，
  # 于是删除成功被同一份代码打成「返回 HTTP 200 0.100」的假 warning —— 而「删除成功」
  # 恰恰是这套演练要证明的东西。
  s=${fmt//%\{http_code\}/$code}
  s=${s//%\{size_download\}/1000}
  s=${s//%\{time_total\}/0.100}
  printf '%s' "$s"
else
  printf '%s' "$body"
fi
STUB
chmod +x "$WORK/bin/curl"

# 假 git：删除段要跑通，得先让 del_tag 成功，否则会 continue、删不到 Release。
# 只关心 ls-remote（孤儿交叉校验）与 push --delete（del_tag），一律成功。
cat > "$WORK/bin/git" <<'GITSTUB'
#!/usr/bin/env bash
exit 0
GITSTUB
chmod +x "$WORK/bin/git"

# 假 APK：1000 字节，只用来跑 stat/size 那条路径。
head -c 1000 /dev/urandom > "$WORK/KOiL-V1.1.9-vc150-release.apk"

FAILED=0
ok()   { echo "  ✅ $1"; }
bad()  { echo "  ❌ $1"; FAILED=1; }

# run_mirror <event> <dry_run>  —— 输出写进 $WORK/last.log
run_mirror() {
  local event="$1" dry="$2" keep="${3:-5}"
  PATH="$WORK/bin:$PATH" \
  STUB_WORK="$WORK" \
  GITEE_OWNER=ZSPOP GITEE_REPO=KOiL GITEE_TOKEN=FAKE \
  GITHUB_EVENT_NAME="$event" \
  GITEE_MIRROR_KEEP="$keep" GITEE_MIRROR_DRY_RUN="$dry" \
  bash "$MIRROR" "$WORK/KOiL-V1.1.9-vc150-release.apk" V1.1.9 > "$WORK/last.log" 2>&1
}

echo "S1 前 2 次连接重置、第 3 次成功（dispatch, dry_run=true）"
echo 2 > "$WORK/failcount"
run_mirror workflow_dispatch true; RC=$?
W=$(grep -c '次失败' "$WORK/last.log" || true)
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
[ "$W" -eq 2 ] && ok "2 条重试 warning" || bad "重试 warning 应为 2，实际 $W"
grep -q '次失败（curl 退出码 56，返回 0 字节）' "$WORK/last.log" \
  && ok "warning 带得出退出码与字节数" || bad "warning 没带退出码/字节数"
grep -q '保留版本号最高的 5 个' "$WORK/last.log" \
  && ok "读到列表后继续走清理" || bad "没走到清理阶段"

echo "S2 3 次全失败（dispatch, dry_run=false）"
echo 9 > "$WORK/failcount"
rm -f "$WORK/deletes.log"
run_mirror workflow_dispatch false; RC=$?
W=$(grep -c '次失败' "$WORK/last.log" || true)
[ "$RC" -eq 1 ] && ok "exit 1" || bad "exit 应为 1，实际 $RC"
[ "$W" -eq 3 ] && ok "3 条重试 warning" || bad "重试 warning 应为 3，实际 $W"
grep -q '::error::Gitee 补传失败' "$WORK/last.log" \
  && ok "打 ::error:: 且文案指向清理未执行" || bad "缺 ::error::"
D=$(cat "$WORK/deletes.log" 2>/dev/null | wc -l)
[ "$D" -eq 0 ] && ok "全程未发出任何 DELETE（读不到列表就不动手）" || bad "发了 $D 个 DELETE"

echo "S3 3 次全失败（自动链 push）"
echo 9 > "$WORK/failcount"
run_mirror push false; RC=$?
[ "$RC" -eq 0 ] && ok "exit 0，不连累发版" || bad "exit 应为 0，实际 $RC"
grep -q 'Gitee 镜像中止' "$WORK/last.log" && ok "打「镜像中止」" || bad "缺「镜像中止」"

echo "S4 正常路径"
rm -f "$WORK/failcount"
run_mirror workflow_dispatch true; RC=$?
W=$(grep -c '次失败' "$WORK/last.log" || true)
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
[ "$W" -eq 0 ] && ok "0 条重试 warning（不刷噪声）" || bad "正常路径打了 $W 条 warning"

echo "S6 删除段跑通（keep=1, dry_run=false）：200 必须被识别成成功"
rm -f "$WORK/failcount" "$WORK/deletes.log"
run_mirror workflow_dispatch false 1; RC=$?
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
grep -q '已删 tag V1.1.3（git push --delete）' "$WORK/last.log" \
  && ok "删 tag 成功" || bad "没删到 tag"
grep -q '已删 Release id=1（HTTP 200）' "$WORK/last.log" \
  && ok "删 Release 文案正确（200 命中 case 分支）" || bad "200 没被识别成成功"
if grep -q '返回 HTTP 200 0.100' "$WORK/last.log"; then
  bad "假 warning：-w 没按真 curl 输出"
else
  ok "无假 warning"
fi
D=$(cat "$WORK/deletes.log" 2>/dev/null | wc -l)
[ "$D" -ge 1 ] && ok "确实发出了 DELETE（$D 次）" || bad "没发 DELETE"

echo "S5 非幂等请求不带 --retry-all-errors"
grep -q 'GET_CURL=("${CURL\[@\]}" --retry-all-errors)' "$MIRROR" \
  && ok "GET_CURL 存在" || bad "缺 GET_CURL"
# 建 Release 那行必须用 CURL（不含 retry-all-errors），否则 curl 会重发这个 POST：
# 首建成功但响应丢失 → 重发撞 400 已存在 → 没有 .id → fail → 落一个空 Release。
# 上传那行用 UP_CURL，本来就排除在外。
POST_BAD=$(grep -- '-X POST' "$MIRROR" | grep -v 'UP_CURL' | grep -c 'GET_CURL' || true)
[ "$POST_BAD" -eq 0 ] && ok "非幂等 POST 都没带 retry-all-errors" \
  || bad "有 $POST_BAD 处非幂等 POST 带上了 retry-all-errors"
grep -q 'REL=$(curl -sS "${CURL\[@\]}" -X POST' "$MIRROR" \
  && ok "建 Release 显式用 CURL" || bad "建 Release 没显式用 CURL"
grep -q 'LIST_CURL=("${CURL\[@\]}" --max-time 30' "$MIRROR" \
  && ok "列表用 LIST_CURL（30s，不被 retry-max-time 90 卡住）" || bad "缺 LIST_CURL"

echo
if [ "$FAILED" -eq 0 ]; then echo "全部通过"; else echo "有用例失败"; fi
exit "$FAILED"
