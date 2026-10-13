# 前后端一致性报告 · 念家时-v2.2-家人空间

> Stage 6 产物 · 契约 v1.8.0 · 2026-10-13

## 校验矩阵

| # | 校验项 | 工具 | 结果 |
|---|---|---|---|
| 1 | 契约四栈一致性（61 路由 / 109 模型） | `contract cli check` | ✅ react/vue/node/java 全过 |
| 2 | 生成区漂移（java / react 骨架 vs 契约） | `contract cli drift` | ✅ 双栈零漂移 |
| 3 | 字段级三方交叉核对（契约 schema vs 后端 DTO vs 前端 api-family.js） | 脚本比对 | ✅ Festival/Photo/Moment 系 11 schema 字段双向齐全；前端 14 端点封装全覆盖（postMoment/listDueFestivals/listMyReservations/listDayMoments 等） |
| 4 | 后端全量测试 | `mvnw test` | ✅ 173/173（既有 126 + 家人空间 47 条集成） |
| 5 | 运行时序列化漂移 | `contract cli live`（本地 8080 /v3/api-docs） | ✅ 61/61 契约路由全在（live 62 含豁免探活 /api/ping）；v1.8 新模型全部导出（2 项 wire 等价豁免，见下） |
| 6 | 生日福卡无时间门控回归 | 集成测试第 25 组 | ✅ 提前 1 天/当天后生成均 200（用户裁定落地） |

## 本阶段发现并已修复的问题

1. **PhotoUploadRequest 枚举模型未导出**：scene/mimeType 为 String 字段（宽容设计：非法值服务端校验回 40000，规避 Jackson 未知枚举直抛 50000 的坑），springdoc 默认导不出枚举形状。**修复：字段挂 `@Schema(implementation = PhotoScene/PhotoMimeType.class)`**——修复后运行时 api-docs 在属性上**内联导出完整枚举值**（`enum:[MOMENT,CAPSULE]`、`enum:[JPEG,PNG,WEBP]`），消费方信息与契约等价。

## 显式豁免（wire 等价，不修复的理由）

- **PhotoScene / PhotoMimeType 命名模型**：运行时已内联导出枚举值（信息完整），仅缺命名模型注册。若强行注册（字段改类型化枚举）会重新引入 Change 1 修掉的「未知枚举值 → 50000 绕过 40000」问题，得不偿失。
- **Pendant 模型**：v1.5 存量漂移（前两轮已豁免，pendant 随旧前端归档）。

`live` 校验因此报 `ok:false`，除上述两项 wire 等价豁免外全部通过。

## 结论

**一致性 PASS**（2 项 wire 等价豁免）。家人空间契约（14 新端点 + 4 组既有扩展）在四栈校验、字段交叉核对、运行时 live 校验三个层面与后端实现、前端封装三方对齐。
