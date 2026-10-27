# 工作区记忆

## E2E 测试数据清理机制（fuel_record 表）

- **重要：清理必须由主 agent 执行，E2E 子 agent 不具备数据库访问权限**，不能依赖子 agent "自行清理"
- 适用场景：对 fuel_record 表做任何写入类 E2E 测试 / 接口验证后
- 正确清理步骤（主 agent 操作）：
  1. 测试前先记录基线最新 `_created_at`：`SELECT max(_created_at) AS baseline FROM fuel_record`
  2. E2E 测试完成后，主 agent 立即执行清理：`DELETE FROM fuel_record WHERE _created_at > '基线时间'`
  3. 回读验证：`SELECT count(*) FROM fuel_record` 确认恢复到基线数量
- 例外：若测试计划明确要求保留某条数据用于后续验证，需在测试前声明并向用户说明，不得默认留存
- 失效根因（2026-10-17）：上一轮图标开关验收 E2E 测试结束后，主 agent 忘记执行清理步骤；E2E 子 agent "声称已清理"是假阳性，它没有 DB 操作能力
