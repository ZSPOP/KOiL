# KOiL 加油记录应用

## 应用概览

一款简洁的加油记录管理工具，用户录入每次加油的里程表读数、加油量、应付/实付金额，系统自动计算行驶里程、百公里油耗、每公里费用等派生数据，并提供历史记录列表、累计汇总统计和趋势分析图表。品牌名 **KOiL**。

## 设计规范（HyperOS 4 + iOS 26 Liquid Glass）

### 视觉风格
- iOS 18 融合设计语言，中性灰阶系统 · Liquid Glass 玻璃质感
- iOS 系统蓝主色（#0071E3）+ 中性近黑/近白背景
- 玻璃卡片：半透磨砂 + 柔焦光晕 + 柔和大阴影 + 顶部高光描边（深色）
- 大圆角（28px 卡片）、温润曲线、轻盈悬浮
- 三档主题：跟随系统（默认）/ 深色 / 浅色

### 核心色（HyperOS 品牌蓝）
| 角色 | 值（浅色） | 值（深色） |
|------|-----------|-----------|
| primary | hsl(210 100% 45%) [#0071E3] | hsl(210 100% 55%) [#0A84FF] |
| background | hsl(40 22% 98%) 近白微暖 | hsl(240 8% 5%) 中性近黑 |
| surface-1 | hsl(0 0% 100%) | hsl(240 6% 10%) |
| surface-2 | hsl(240 5% 96%) | hsl(240 5% 14%) |
| foreground | hsl(30 6% 13%) 近黑 87% | hsl(0 0% 92%) 白 92% |
| muted-foreground | hsl(30 3% 56%) 灰 56% | hsl(0 0% 56%) 白 56% |
| border | rgba(0,0,0,0.08) 黑 8% | rgba(255,255,255,0.09) 白 9% |
| warn | hsl(38 80% 62%) | hsl(38 75% 58%) |
| danger | hsl(0 65% 63%) | hsl(0 60% 60%) |
| success | hsl(150 45% 55%) | hsl(150 45% 52%) |

### 玻璃材质
- `.glass`：`backdrop-filter: saturate(180%) blur(30px)` + 1px 玻璃边框 + 柔阴影
- `.glass-soft`：更薄更透的玻璃变体（blur 22px）
- `.glass-strong`：更厚更实的玻璃变体（blur 30px）
- 深色玻璃：去 saturate、纯 blur、顶部白色 7% 高光内描边（Liquid Glass）
- 背景柔光光晕：蓝系柔焦 radial-gradient（克制透明度，深色底部光晕移除）

### 字体
- Display/正文：'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif
- Mono：'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace
- 全局 Google Fonts Inter (400;500;600;700)

### 圆角
- 卡片/面板：28px（`rounded-[28px]`）
- 按钮/Pill：999px
- Chip/内联 code：6px

### 动效
- 标准曲线：`cubic-bezier(0.2, 0.8, 0.2, 1)`
- 悬浮上抬：`hover-lift`（translateY -2px + 柔阴影）
- 主题切换过渡：背景/文字/边框/阴影 300-400ms 平滑过渡

### 主题切换
- 三档：跟随系统（默认）/ 浅色 / 深色
- 持久化：localStorage（key: `koil-theme-mode`）
- 系统模式监听 `prefers-color-scheme` 实时响应
- 通过 `data-theme` 属性挂在 `<html>` 上，CSS 变量切换

## 功能模块
1. 录入加油：日期 + 里程 + 加油量 + 应付 + 实付
2. 汇总统计：9 个指标卡片 + CSV 导出
3. 历史记录：数据表格 + 删除操作
4. 趋势分析：油耗趋势 / 费用趋势 / 月度费用占比（ECharts）

## 数据模型

### 加油记录 (koil_record)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | uuid | 主键 |
| reoilDate | date | 加油日期 |
| mileage | numeric | 本次里程表读数（km） |
| oilAmount | numeric | 加油量（L） |
| payableAmount | numeric | 应付金额（元） |
| actualAmount | numeric | 实付金额（元） |

### 派生字段（前端/后端计算，不存库）
- prevMileage / distance / oilDays / oilDateRange
- unitPricePayable / unitPriceActual
- oilConsumption（百公里油耗）
- costPerKm（每公里费用）

### 短间隔油耗规则
- 行驶间隔 distance < 100km 的记录：油耗（oilConsumption）与每公里成本（costPerKm）不计算（返回 null），显示 "—"
- 短间隔记录标记 `shortDistanceSkipped=true`，不参与异常判定（isAbnormalOilConsumption 始终为 false）
- 平均油耗 avgOilConsumption 只基于 distance ≥ 100km 的有效段计算
- 阈值常量：`MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM = 100`（后端 service 中）

### 汇总统计（后端计算）
- totalCount / totalDistance / totalOilAmount / totalPayable / totalActual
- avgOilConsumption / avgCostPerKm / avgUnitPriceActual / avgDistancePerReoil

### 合并预检接口
- `POST /api/koil-records/precheck`：一次请求返回 duplicate + mileageChain + boundary + prevRecord
- 入参：reoilDate、mileage、oilAmount、actualAmount、source、excludeId、includeBoundary、includePrevRecord
- 录入页、补录弹窗、编辑弹窗三入口均改为单次 precheck 请求，替代原 2-4 个并发预检请求
- 旧接口保留兼容（duplicate-check / mileage-conflict-check / mileage-boundary / prev-record）

## 命名约定

### 保留的业务词（油的语义，不算旧名）
- `oil`：OilGrade、oilPrice、oilAmount、oilConsumption、oilDays、oilDateRange
- 中文「加油」：面向用户的自然语言描述

### 已淘汰的旧品牌/技术标识符（全部改为 koil / KOiL）
- 表名/路由/类型/函数/变量/key/事件/CSS 类/data 属性/文件名统一使用 **koil** 前缀
