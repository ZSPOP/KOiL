#!/usr/bin/env bash
# gitee-mirror.sh 的离线演练。PR Check（android-build.yml 的 pr-check job）每个 PR
# 都跑它，因为它覆盖的全是【失败路径】—— 恰恰是正常发版永远跑不到的部分：列表读失败、
# tag 删不掉只在故障时出现，keep=5 要到第 6 个版本才真删。
#
# 用法：bash .github/scripts/test-gitee-mirror.sh
# 退出码 0 = 全部通过；非 0 = 有用例失败（会打印失败场景名）。
#
# 原理：造假 curl + 假 git 放 PATH 最前面。假 curl 按 URL 形态返回固定响应（-w 的
# 输出按格式串逐个替换，与真 curl 对齐）；假 git 只让 del_tag 成功，保证删除段真的
# 跑到「先 tag 后 Release」，而不是 continue 掉。它把参数写进 git.log，S6 靠它断言
# 「git 真的收到了 --delete refs/tags/V1.1.3」，而不是只看脚本自己打的日志。

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

sz=1000
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
elif [ "$method" = "DELETE" ]; then
  # Gitee 删 tag 会级联删 Release，所以「tag 删成功之后的 DELETE」实测是 404。
  # 这条分支是全脚本注释最长、风险最高的地方（见 gitee-mirror.sh 的 404 分支），
  # 默认给 200 就走不到，用开关文件单独验。
  code=$(cat "$STUB_WORK/delcode" 2>/dev/null || echo 200); body=''
else code=200; body='x'; fi

# 下载校验的瞬时失败：V1.1.11 那次 runner 侧拿到 000/0（实测 Gitee 直链 200/6725974）。
# 用开关文件模拟「第一次探测就断」，脚本应重试并最终通过。
# 用计数而不是一次性开关：正常实现下第一次 000、第二次就成功；把重试去掉之后
# 首轮只有 1 次机会 → 回退到自拼 URL 再撞一次（计数正好用掉）→ 必然失败，
# 这条用例才能区分「靠重试救回来」和「靠回退救回来」。
if [ -f "$STUB_WORK/dl_fail_left" ] && [ "$out" = "/dev/null" ]; then
  n=$(cat "$STUB_WORK/dl_fail_left" 2>/dev/null || echo 0)
  if [ "$n" -gt 0 ]; then
    echo $((n-1)) > "$STUB_WORK/dl_fail_left"
    code=000; sz=0
  fi
fi

[ -n "$out" ] && printf '%s' "$body" > "$out"
if [ -n "$fmt" ]; then
  # 必须按格式串逐个替换，不能一律返回「code time」：脚本 :403 用的是 -w '%{http_code}'，
  # 真 curl 只输出 200。返回 "200 0.100" 会让 case "$RC" 的 200|204 匹配不到，
  # 于是删除成功被同一份代码打成「返回 HTTP 200 0.100」的假 warning —— 而「删除成功」
  # 恰恰是这套演练要证明的东西。
  s=${fmt//%\{http_code\}/$code}
  s=${s//%\{size_download\}/$sz}
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
echo "$*" >> "$STUB_WORK/git.log"
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
# 观测点必须是 ELIGIBLE 的结果，不能是「保留版本号最高的 5 个」——后者在
# ELIGIBLE=0 时照样打印，上一版就是靠它绿着过去的（stub 附件名叫 a.apk）。
grep -q '其中镜像版本 6 个' "$WORK/last.log" \
  && ok "读到列表且 6 条都计入可清理" || bad "ELIGIBLE 不是 6（列表没读到或筛选口径坏了）"

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
# git.log 也要清：否则以后若有人在 S6 前加一个会触发 del_tag 的用例，
# 「git 确实收到 --delete V1.1.3」会被那个用例的残留顶上。
rm -f "$WORK/failcount" "$WORK/deletes.log" "$WORK/git.log"
run_mirror workflow_dispatch false 1; RC=$?
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
grep -q '已删 tag V1.1.3（git push --delete）' "$WORK/last.log" \
  && ok "删 tag 成功" || bad "没删到 tag"
# 光看日志不算：要确认 git 真收到了 --delete refs/tags/V1.1.3 这个动作
grep -q -- '--delete refs/tags/V1.1.3' "$WORK/git.log" 2>/dev/null \
  && ok "git 确实收到 --delete refs/tags/V1.1.3" || bad "git 没收到删 tag 指令"
grep -q '已删 Release id=1（HTTP 200）' "$WORK/last.log" \
  && ok "删 Release 文案正确（200 命中 case 分支）" || bad "200 没被识别成成功"
if grep -q '返回 HTTP 200 0.100' "$WORK/last.log"; then
  bad "假 warning：-w 没按真 curl 输出"
else
  ok "无假 warning"
fi
D=$(cat "$WORK/deletes.log" 2>/dev/null | wc -l)
[ "$D" -ge 1 ] && ok "确实发出了 DELETE（$D 次）" || bad "没发 DELETE"

echo "S7 404 分支：tag 删成功、Release 被 Gitee 级联带走"
rm -f "$WORK/failcount" "$WORK/deletes.log"; echo 404 > "$WORK/delcode"
run_mirror workflow_dispatch false 1; RC=$?
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
grep -q '已不存在（随 tag 一并删除），视为成功' "$WORK/last.log" \
  && ok "404 被映射成成功" || bad "404 没走「视为成功」分支"
if grep -q '::warning::删 Release' "$WORK/last.log"; then
  bad "404 被当成了失败（打了 warning）"
else
  ok "404 没被当失败"
fi
rm -f "$WORK/delcode"

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

echo "S8 校验首次探测拿到 000/0（网络抖动）应重试，不能误报镜像失败"
echo 2 > "$WORK/dl_fail_left"
run_mirror workflow_dispatch false; RC=$?
[ "$RC" -eq 0 ] && ok "exit 0" || bad "exit 应为 0，实际 $RC"
grep -q 'Gitee 镜像校验通过' "$WORK/last.log" && ok "最终判定校验通过" || bad "没走通校验"
# 关键：必须先确认「第一次真的拿到 000」，否则这条用例只是又跑了一遍正常路径。
grep -q '镜像校验第 1 次未通过（HTTP 000 / 0 字节）' "$WORK/last.log" \
  && ok "第一次探测确实拿到 000/0 并进入重试" || bad "第一次探测没触发 000/0，用例没覆盖到重试"
if grep -q 'Gitee 镜像校验失败' "$WORK/last.log"; then
  bad "网络抖动被误报成镜像失败"
else
  ok "未误报镜像失败"
fi

echo
if [ "$FAILED" -eq 0 ]; then echo "全部通过"; else echo "有用例失败"; fi
exit "$FAILED"

