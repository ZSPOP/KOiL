#!/usr/bin/env bash
# Gradle 构建包装：仅当失败属于「依赖解析 / 网络抖动」类时自动重试一次。
#
# 背景（Build #73）：runner 上 Maven Central 偶发连接抖动，junit/robolectric 等稳定版本
# 也会「Could not find」——依赖声明本身没问题，重跑即过。之前的行为是整条 CI 红、
# 等人工发现 + 手动 rerun；本脚本把这一步自动化，同时守住两条边界：
#   1. 编译/测试/lint 等真实失败不重试——重试结果注定一样，白烧约 3 分钟还稀释了红的意义；
#   2. 只重试一次——连续两次都解析失败说明不是抖动，必须停下来让人看。
#
# 判定特征（PR #24 评审后收紧）：
#   - 「Could not find」只认依赖坐标形态（group:artifact:），不再裸匹配——
#     否则 Gradle 脚本写错的「Could not find method foo()」这类配置期真错误也会被误重试；
#   - 超时/重置等网络特征必须与「Could not resolve / Could not GET」同现才生效——
#     单测断言失败的异常栈里天然带「Read timed out」（回退链超时测试），裸匹配会误判。
# 注意：真实抖动下的恢复效果要等 CI 真遇到才能验证，本 PR 的绿灯不构成证据。
set -uo pipefail

LOG=$(mktemp)
: > "$LOG"
attempt() {
  "$@" 2>&1 | tee -a "$LOG"
  return ${PIPESTATUS[0]}
}

if attempt "$@"; then
  exit 0
fi

# 主特征：依赖解析家族的直证
is_resolution_failure=0
if grep -qE \
  'Could not resolve all (files|dependencies)|Could not (GET|HEAD|download)|Could not find [^[:space:]]+:[^[:space:]]+:' \
  "$LOG"; then
  is_resolution_failure=1
fi
# 辅特征：网络层错误必须与解析动作同现，单独出现不算（可能来自测试断言的异常栈）
if [ "$is_resolution_failure" -eq 0 ] \
  && grep -qE 'Could not (resolve|GET)' "$LOG" \
  && grep -qE 'Read timed out|Connection reset|connection (timed out|was reset)|UnknownHost|Software caused connection abort|Premature EOF|response (429|5[0-9]{2})' \
     "$LOG"; then
  is_resolution_failure=1
fi

if [ "$is_resolution_failure" -eq 0 ]; then
  echo "::error::Gradle 构建失败（非依赖解析/网络类错误，不重试）"
  exit 1
fi

echo "::warning::检测到依赖解析/网络类失败（见上方日志），自动重试一次…"
if attempt "$@"; then
  echo "::notice::重试成功，确认是 runner 侧依赖解析抖动"
  exit 0
fi
echo "::error::Gradle 构建重试一次后仍失败（两次均为依赖解析/网络类错误，非偶发）"
exit 1
