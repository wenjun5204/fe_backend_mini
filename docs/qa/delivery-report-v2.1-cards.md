# 交付报告 · 念家时-v2.1-模板馆引擎

> Stage 10 产物 · fe-be-contract-harness 全流程 · 2026-10-11
> change_id: 念家时-v2.1-模板馆引擎 · 契约 v1.6.0 → v1.7.0 · 分支 feature/nianjiashi-cards-mvp · PR #4

## 1. 交付物清单

| 类别 | 产物 | 位置 |
|---|---|---|
| 需求 | 结构化 Spec（已批准，5 决策） | docs/requirements/requirements-spec-v2.1-cards.json + requirements-v2.1-cards.md |
| 契约 | v1.7.0 增量：14 端点（cards 7/capsules 4/visitor 3 免鉴权）/ 34 schema | api/openapi.yaml |
| 设计 | 后端 4 表 / AIService 降级策略 / 前端 7 页 / poster canvas / scene 码协议 | docs/design/v2.1-cards-design.md |
| 后端 | 4 表 + 7 服务（AIService/CardService/CapsuleService/VisitorService/LetterTextLib/TemplateCatalog 等） | backend/src/main/java/com/example/backend/ |
| 前端 | 模板馆 Tab 激活 + 7 新页 + poster 组件 + 访客页（13 页全量） | miniprogram-nianjiashi/ |
| 测试 | 集成 40 条（全量 126/126）+ 人工提测 42 条（P0×12） | CardsApiIntegrationTest 等 + docs/qa/testcases-v2.1-cards.md |
| 质量报告 | 一致性 PASS + 端到端裂变冒烟九步全通 | docs/qa/consistency-report-v2.1-cards.md |

## 2. 流水线阶段记录（12 阶段）

| Stage | 结果 | 备注 |
|---|---|---|
| 0 初始化 | ✅ | execution_plan（范围含时光胶囊提前） |
| 1 需求分析 | ✅ checkpoint approve | AI 网关+降级/胶囊独立实体明文存/42900 等已拍板 |
| 2 契约设计 | ✅ checkpoint approve | 14 端点 34 schema，四栈全过 |
| 3 技术方案 | ✅ checkpoint approve | pagePath maxLength 32→40 勘误提前兑现 |
| 4 分支管理 | ✅ | feature/nianjiashi-cards-mvp（基座 feature/nianjiashi-days-mvp） |
| 5A 后端实现 | ✅ | 126/126，commit 4693e69 |
| 5B 前端实现 | ✅ | 13 页 + poster canvas，commit ce16b55（三批次交付） |
| 6 一致性校验 | ✅ checkpoint approve | live 校验 + **九步端到端冒烟**，commit a58f491 |
| 7 提交与 PR | ✅ checkpoint approve | PR #4 |
| 8 测试用例 | ✅ checkpoint approve | 42 条，commit 3108ebf |
| 9 提测与部署 | ⏭️ skipped（用户批准，沿用 Change 1 决策） | 需 OPENAI_API_KEY 云托管环境变量配置 |
| 10 交付报告 | ✅ 本文档 | — |

## 3. 阶段中修复的两个真实缺陷

1. **LetterTone/StoryKey 模型未导出**（5A 引入的枚举 String 化与 Change 1 枚举模式不一致）：DTO 三字段加 `@Schema(implementation=...)` 元数据引用，wire 与校验语义零变化，springdoc 恢复导出。
2. **api-cards.js 注释语法炸弹**（5A→5B 批次间）：块注释中 `*/` 提前闭合导致整个卡片请求模块 node --check 失败，批次 2 最小修复。

另：5B 因子代理连接中断分三批次交付（utils→poster/模板馆/感恩信→天数卡/胶囊→访客/我的卡片/收尾），跨批次一致性经终检覆盖。

## 4. 已知边界与遗留

- **Pendant 模型存量豁免**（沿用 Change 1 报告）。
- **胶囊照片**：契约有 photoUrl 字段但无上传端点，UI 标注「仅本机留作纪念」；开箱揭示走照片位占位。上传服务列 Change 3 候选。
- **VisitorCardView 无分享者昵称字段**：访客页「来自朋友的分享」降级文案；如需真实昵称需契约增补（涉隐私口径，列后续评审）。
- **AI 真实链路**：本地冒烟验证的是 FALLBACK 全功能可用；OPENAI_API_KEY 配置后的 AI 路径需部署后联调（三层防线中第②层「AI 输出过滤」已有单测覆盖）。
- **二维码两态**：小程序未发布前 QR 内容为 pagePath 文本（提示截图/扫码识别引导）；发布后需在微信公众平台配置「扫普通二维码进小程序」规则并切 QR_LINK_MODE。

## 5. 后续排期建议

1. **真机自验**：按 docs/qa/testcases-v2.1-cards.md P0 12 条走裂变主链路（做卡→分享→扫码→3 心→转化）。
2. **部署**：Change 1+2 一起发云托管；配置 OPENAI_API_KEY（可选——不配则全站 FALLBACK 依然可用）；部署后 live 复检回填两个 change 的 Stage 9。
3. **Change 3 候选**：节点日历预约推送调度、胶囊照片上传、家人空间（V1.2）、解忧册删除收尾 + 旧前端归档 + 改名提审。

## 6. 验收状态

| 验收标准 | 状态 |
|---|---|
| FR-4 模板预览（服务端配置驱动） | ✅ 冒烟 6 模板下发 |
| FR-6 感恩信 AI 代笔 + 逐句改稿 | ✅ FALLBACK 4 段×2 备选冒烟通过 |
| FR-7 相伴天数卡 | ✅ |
| FR-8 时光胶囊（封存/门控/开箱） | ✅ SEALED→40900 门控冒烟 |
| FR-9 海报 scene 码 | ✅ 幂等同码冒烟 |
| FR-10 访客 3 心解锁（免登录/防刷/门控） | ✅ 九步冒烟全通 |
| FR-11 我的卡片 | ✅ |
| AI 降级永不不可用 | ✅ 无 Key 全链路冒烟 |
| 部署 | ⏭️ 待真机自验后执行 |
