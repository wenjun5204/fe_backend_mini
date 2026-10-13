# 交付报告 · 念家时-v2.0-日子管家

> Stage 10 产物 · fe-be-contract-harness 全流程 · 2026-10-10
> change_id: 念家时-v2.0-日子管家 · 契约 v1.5.0 → v1.6.0 · 分支 feature/nianjiashi-days-mvp · PR #3

## 1. 交付物清单

| 类别 | 产物 | 位置 |
|---|---|---|
| 产品文档 | PRD 定稿 | docs/PRD-念家时.md |
| 交互原型 | 念家时 v2.1（全人群+社交裂变） | docs/prototype/nianjiashi-v2.1.html |
| 需求 | 结构化 Spec（已批准，5 决策 D1~D5） | docs/requirements/requirements-spec-v2.0-days.json + requirements-v2.0-days.md |
| 契约 | v1.6.0 增量：tag=days 7 端点 / 11 schema，既有条目零改动 | api/openapi.yaml |
| 设计 | 后端/前端技术方案 | docs/design/v2.0-days-design.md |
| 后端 | day_record/day_reminder 实体、DayService 7 方法、CalendarConvertCore 上移、ErrorDtos | backend/src/main/java/com/example/backend/ |
| 前端 | 新目录 miniprogram-nianjiashi/：三 Tab、日子 Tab 4 页、7 接口封装 | miniprogram-nianjiashi/ |
| 测试 | 集成 22 条（全量 86/86 绿）+ 人工提测 45 条（P0×15） | DaysApiIntegrationTest + docs/qa/testcases-v2.0-days.md |
| 质量报告 | 一致性 PASS（1 项存量豁免） | docs/qa/consistency-report-v2.0-days.md |

## 2. 流水线阶段记录（12 阶段）

| Stage | 结果 | 备注 |
|---|---|---|
| 0 初始化 | ✅ | execution_plan 落盘 |
| 1 需求分析 | ✅ checkpoint approve（用户批准 D1~D5） | spec 全字段门控过 |
| 2 契约设计 | ✅ checkpoint approve | 四栈校验全过；ErrorResponse.code 后在 Stage 6 修正为 string |
| 3 技术方案 | ✅ checkpoint approve | CalendarConvertCore 上移方案 |
| 4 分支管理 | ✅ | feature/nianjiashi-days-mvp（基座 feature/ai-group-assistant-mvp） |
| 5A 后端实现 | ✅ | 86/86 测试绿，commit eb61967 |
| 5B 前端实现 | ✅ | 34 文件 +2682 行，commit 6c4c055 |
| 6 一致性校验 | ✅ checkpoint approve | 3 项漂移修复（f18f1a4 + 484d9cc）；Pendant 存量豁免 |
| 7 提交与 PR | ✅ checkpoint approve | PR #3（base=ai-group-assistant-mvp） |
| 8 测试用例 | ✅ checkpoint approve | 45 条提测用例，commit 2f2c395 |
| 9 提测与部署 | ⏭️ skipped（用户批准） | 真机自验后再走云托管发布 |
| 10 交付报告 | ✅ 本文档 | — |

## 3. 阶段中修复的三个真实缺陷

1. **契约序列化漂移**（live 校验抓出）：契约 `ErrorResponse.code: integer` vs 运行时字符串 `"40000"`——契约改 string 对齐运行时（改后端会动摇 v1.5 既有错误响应语义）。
2. **ErrorResponse 未导出命名模型**：错误处理器返回匿名 Map → 新增 `ErrorDtos.ErrorResponse` DTO + days 7 端点补 `@ApiResponses`（200/400/401/404 与契约逐端点对应）。
3. **农历文本「8月月3日」重复字**（存量瑕疵）：`lunarText` 去重复拼接，生日簿与日子共用核心一并修复，BirthdayServiceTest 守护通过。

## 4. 已知边界与遗留

- **Pendant 模型命名漂移**（v1.5 存量）：契约命名枚举 vs 运行时裸 String；wire 格式正确；pendant 随旧前端归档，不在本次范围。
- **周年称谓延后**（决策 D1）：DayRecord 无年份字段，40 周年=红宝石婚等称谓待 V1.2 加年份；Change 2 模板若需年份需增补字段。
- **提醒真实下发**（决策 D4）：本 change 只交付配置存取 + reminder-due 到期计算；subscribeMessage.send 属 Stage 9 部署联调，频控口径 v2.0 为 ≤3 条/人/天。
- **农历闰月**（决策 D5）：不支持（对齐原型口径）。
- **8080 残留进程教训**：会话间 dev server 孤儿进程曾污染 live 校验结果——校验前先 `lsof -ti :8080 | xargs kill`。

## 5. 后续排期建议

1. **真机自验**（用户）：微信开发者工具导入 miniprogram-nianjiashi/（AppID 沿用），按 docs/qa/testcases-v2.0-days.md P0 15 条过主路径。
2. **部署**：自验通过后云托管发布（需用户控制台），部署后 `contract cli live` 复检 + /api/ping 探活，回填 Stage 9。
3. **Change 2（模板馆引擎）**：感恩信 AI 代笔、相伴天数卡、海报生成、访客 3 心解锁、我的卡片存档——契约增量 → 同流水线再走一轮。
4. **收尾 change**：解忧册删除（前后端+契约）+ 旧 miniprogram 归档 + 小程序改名「念家时·家人生日提醒」提审。

## 6. 验收状态

| 验收标准（需求 Spec） | 状态 |
|---|---|
| FR-1 记一个日子（三类/零键盘/公农历/可见性/提醒） | ✅ 后端集成 + 前端页面全过 |
| FR-2 列表与详情（倒计时/双视图/详情） | ✅ |
| FR-3 提醒配置存取 + 到期计算 | ✅（真实下发属部署联调） |
| FR-12 新用户空状态 | ✅ |
| 测试门：≥60% 覆盖 + 契约一致性 | ✅ 集成 22 条 + live 校验 PASS |
| 部署 | ⏭️ 待真机自验后执行 |
