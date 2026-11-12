#!/usr/bin/env bash
# Gradle 构建包装：仅当失败属于「依赖解析 / 网络抖动」类时自动重试一次。
#
# 背景（Build #73）：runner 上 Maven Central 偶发连接抖动，junit/robolectric 等稳定版本
# 也会「Could not find」——依赖声明本身没问题，重跑即过。之前的行为是整条 CI 红、
# 等人工发现 + 手动 rerun；本脚本把这一步自动化，同时守住两条边界：
#   1. 编译/测试/lint 等真实失败不重试——重试结果注定一样，白烧约 3 分钟还稀释了红的意义；
#   2. 只重试一次——连续两次都解析失败说明不是抖动，必须停下来让人看。
set -uo pipefail

LOG=$(mktemp)
"$@" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}
[ "$RC" -eq 0 ] && exit 0

# 只匹配「环境类」失败特征；模式保持克制，宁可漏掉一些抖动（走人工 rerun）
# 也不要把真实代码错误误判成环境问题后盲目重试。
if ! grep -qE \
  'Could not (resolve all files|GET|HEAD|find|download|resolve [a-zA-Z])|Read timed out|Connection reset|connection (timed out|was reset)|UnknownHost|Software caused connection abort|Premature EOF|response (429|5[0-9]{2})' \
  "$LOG"; then
  echo "::error::Gradle 构建失败（非依赖解析/网络类错误，不重试）"
  exit "$RC"
fi

echo "::warning::检测到依赖解析/网络类失败（见上方日志），自动重试一次…"
"$@" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}
if [ "$RC" -eq 0 ]; then
  echo "::notice::重试成功，确认是 runner 侧依赖解析抖动"
  exit 0
fi
echo "::error::Gradle 构建重试一次后仍失败（两次均为依赖解析/网络类错误，非偶发）"
exit "$RC"
