# 前后端一致性报告 · 念家时-v2.0-日子管家

> Stage 6 产物 · 契约 v1.6.0 · 2026-10-10

## 校验矩阵

| # | 校验项 | 工具 | 结果 |
|---|---|---|---|
| 1 | 契约四栈一致性（33 路由 / 55 模型） | `contract cli check` | ✅ react/vue/node/java 全过 |
| 2 | 生成区漂移（java / react 骨架 vs 契约） | `contract cli drift` | ✅ 双栈零漂移 |
| 3 | 字段级三方交叉核对（契约 schema vs 后端 DTO vs 前端 api-days.js） | 脚本比对 | ✅ Day 系 7 schema 字段双向齐全，前端 7 接口函数全覆盖 |
| 4 | 后端全量测试 | `mvnw test` | ✅ 86/86（含 DaysApiIntegrationTest 22 条） |
| 5 | 运行时序列化漂移 | `contract cli live`（本地 8080 /v3/api-docs） | ✅ 33/33 契约路由全在（live 34 含豁免探活 /api/ping）；Day 系 7 模型 + ErrorResponse 全部导出 |
| 6 | 前端语法/合规 | `node --check` 10 文件全过；合规词表 24 词零命中 | ✅ |

## 本阶段发现并已修复的问题

1. **契约 `ErrorResponse.code` 类型漂移**（Stage 2 引入）：契约写成 `integer`，运行时自 v1.5 起序列化为字符串 `"40000"`（`ApiException.code` 为 String，新旧前端均用 `parseInt` 兼容）。**修复：契约改为 `type: string`**，对齐运行时既有行为——改后端为整数会动摇全量既有错误响应语义。
2. **ErrorResponse 未导出为命名模型**（本 change 引入）：后端错误处理器返回匿名 Map，springdoc 导不出契约中的 ErrorResponse 模型。**修复：新增 `ErrorDtos.ErrorResponse` DTO**（code/message，与运行时序列化一致），处理器改返回该类型；并在 7 个 days 端点补齐 `@ApiResponses` 显式声明 200/400/401/404——与契约逐端点错误码一一对应（顺带修复了 springdoc 对 days 端点 200 模型引用的丢失）。
3. **农历文本渲染瑕疵**（存量）：`lunarText` 月名已带「月」字再拼接产生「农历8月月3日」。**修复：去掉重复拼接**（f18f1a4），生日簿与日子共用 CalendarConvertCore 一并修复，BirthdayServiceTest 守护通过。
4. **8080 端口孤儿进程**：上次会话残留的旧版 dev server 占用端口导致首轮 live 校验打到旧进程（22 路由）。已清理并用新代码重启复检。

## 显式豁免（存量漂移，不在本 change 修复）

- **`Pendant` 模型**：契约 v1.5 将其声明为命名枚举（BAMBOO/FLAME/…），运行时为裸 String 字段——wire 格式正确、仅命名模型未导出。pendant 属旧产品（v1.4）功能，随旧 `miniprogram/` 归档而废弃，本 change 不触碰其序列化语义。`live` 校验因此报 `ok:false`，除此项外全部通过。

## 结论

**一致性 PASS**（1 项显式豁免）。days 契约（7 端点 / 11 模型）在四栈校验、字段交叉核对、运行时 live 校验三个层面与后端实现、前端封装三方对齐。
