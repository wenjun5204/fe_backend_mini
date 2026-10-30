# 一致性校验报告 · 念家时 v2.3 值班日历（Stage 6）

> change_id：`念家时-v2.3-值班日历` | 契约 v1.8.0 → v1.9.0（duty tag 7 端点 + 15 新模型）
> 校验时间：2026-10-30 | 本地后端 `mvnw spring-boot:run`（H2 文件库）+ 契约四栈 check + 运行时 live 校验 + 前端字段交叉核对

## 校验矩阵

| # | 层 | 手段 | 结果 |
|---|---|---|---|
| 1 | 契约结构 | `contract cli check api/openapi.yaml` | ✅ ok=true，68 路由（61 既有 + 7 duty），六栈 routes/models 全绿 |
| 2 | 后端路由 | `contract cli live --url http://127.0.0.1:8080/v3/api-docs` | ✅ duty 7 路由全部在运行时导出；枚举值内联导出（见豁免说明） |
| 3 | 后端测试 | `mvnw test` | ✅ 199 条全绿（duty 24 条新增：序列断言/覆盖语义/截断/幂等/用户隔离/40100 全量） |
| 4 | 前端封装 | 字段级交叉核对（api-duty.js ↔ 契约出入参） | ✅ 7 端点路径/方法/请求体字段/响应消费字段全对齐 |
| 5 | 前端页面 | Node stub wx 全链路冒烟（云托管通道转发本地真实后端） | ✅ 首屏 3 月并发/单日编辑/批量排班覆盖确认/提醒设置/清除/翻月缓存全通过 |
| 6 | 合规词扫描 | 违禁词表 grep（duty 页面 + 封装） | ✅ 零命中（值班文案无迷信词/医疗暗示） |

## 枚举 wire 等价（沿 v2.2 豁免范式）

- **DutyShiftKind / DutyRuleType / DutyRemindTiming 命名模型**：DTO 字段为 String 承接（宽容设计：非法值服务层校验回 40000，规避 Jackson 未知枚举直抛 500），字段挂 `@Schema(implementation = Xxx.class)` 后运行时 api-docs 在属性上**内联导出完整枚举值**（early/mid/night/rest、daily/on1off1/on2off2/emnr/weekday、advance_1d/same_day_08/advance_2h），消费方信息与契约等价。
- **既有两项豁免沿用**：Pendant（v1.5 存量漂移，随旧前端归档）、PhotoMimeType（wire 等价，v2.2 已豁免）。

`live` 校验因此报 `ok:false`，除上述三项 wire 等价豁免外全部通过（与 v2.2 报告同口径）。

## 本阶段发现并已修复的问题

1. **前端 `updateRemindConfig` 命名错配**：duty.js 误调 `dutyApi.updateRemindConfig`（封装导出名是 `updateDutyRemindConfig`），提醒保存会静默失败。**Stage 5B 冒烟抓出并修复**（2b2d14d），复验全链路通过。
2. **月历 42 格矩阵边界**：周一首日偏移 `(getDay()+6)%7` 与非本月补位格 `inMonth=false` 置灰不可点，冒烟验证 42 格结构正确。

## 结论

**一致性 PASS**（3 项 wire 等价豁免，其中 2 项为既有存量）。值班日历契约（7 新端点 + 15 新模型）在四栈校验、字段交叉核对、运行时 live 校验、页面全链路冒烟四个层面与后端实现、前端封装三方对齐。
