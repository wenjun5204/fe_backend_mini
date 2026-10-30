# 交付报告 · 念家时-v2.2-家人空间

> Stage 10 产物 · fe-be-contract-harness 全流程 · 2026-10-13
> change_id: 念家时-v2.2-家人空间 · 契约 v1.7.0 → v1.8.0 · 分支 feature/nianjiashi-family-mvp · PR #5

## 1. 交付物清单

| 类别 | 产物 | 位置 |
|---|---|---|
| 需求 | 结构化 Spec（已批准，含用户裁定：福卡不限当天） | docs/requirements/requirements-spec-v2.2-family.json + .md |
| 契约 | v1.8.0：festival 4 + photos 2 + moments 8 端点，CardKind 扩 BIRTHDAY_CARD，DayRecord +year/周年称谓，胶囊 +photoId | api/openapi.yaml |
| 设计 | BLOB 映射实证（@Lob byte[] → H2 blob/MySQL longblob）、4 Tab 前端裁定 | docs/design/v2.2-family-design.md |
| 后端 | 5 新表 + day_record 加列、AnniversaryTitleLib 25 词条、BLOB 照片管道 | backend/src/main/java/com/example/backend/ |
| 前端 | 第 4 个「家人」Tab、瞬间时间线、生日福卡生成、照片上传、预约 UI | miniprogram-nianjiashi/ |
| 测试 | 集成 47 条（全量 173/173 绿）+ 提测 50 条（P0×24，3 缺口已修） | FamilyApiIntegrationTest + docs/qa/testcases-v2.2-family.md |
| 质量报告 | 一致性 PASS（2 项 wire 等价豁免） | docs/qa/consistency-report-v2.2-family.md |

## 2. 流水线阶段记录（12 阶段）

| Stage | 结果 | 备注 |
|---|---|---|
| 0-4 | ✅ | spec 批准（福卡不限当天裁定）；契约勘误两处 |
| 5A 后端 | ✅ | commit 964e83e |
| 5B 前端 | ✅ | commit 6f697cc |
| 6 一致性 | ✅ | 173/173 测试绿；PhotoUploadRequest 枚举 @Schema 内联导出修复（c45732d） |
| 7 提交 PR | ✅ | PR #5（base=feature/nianjiashi-cards-mvp） |
| 8 提测用例 | ✅ | 50 条 + **3 缺口当场修复**（ef45908） |
| 9 部署 | ⏭️ skipped | 真机自验后随 v1.6/1.7/1.8 一次性部署 |
| 10 交付报告 | ✅ 本文档 | — |

## 3. 本轮抓到并修复的真实缺陷

1. **本地 H2 ENUM 列陷阱**（冒烟发现）：v1.7 建表时 Hibernate 7 把 `made_card.template_kind` 生成为 H2 原生 ENUM 三值列，v1.8 加 BIRTHDAY_CARD 后 `ddl-auto=update` 不会扩枚举 → 插入 50000。**修复：本地开发库原地 `ALTER COLUMN SET DATA TYPE VARCHAR(64)`（数据全保留，用户批准）**。
   - ⚠️ **部署知识**：Hibernate 7 对 H2/MySQL 均生成原生 ENUM 列。生产 MySQL 首次部署建表即带全值（安全）；但**未来任何枚举扩值**（CardKind/PhotoScene/各 Status）都必须配 `ALTER TABLE ... MODIFY/ALTER COLUMN` 迁移，否则 ddl-auto=update 不会改列类型，将复现本坑。
2. **访客页无 BIRTHDAY_CARD 渲染分支**（Stage 8 用例组发现）：访客打开福卡会回落 LETTER 星河形态。已补：霞橘渐变 + 🎂 + 关系副题 + ≤3 张收藏照片横排；解锁后 blessingText 引号揭示。
3. **day-edit 无年份补录入口**（同上发现）：周年称谓功能 UI 不可达。已补 year 选择器（仅纪念日/在一起显示；生日不收——隐私红线）。**顺带堵了一个潜在 bug**：后端 PUT 对 year 全量覆盖（不传即清空），原 prefill 不回填 year，编辑任何已有年份的日子都会把年份悄悄抹掉——现编辑模式回填既有年份。
4. **契约描述残留**（勘误）：CardPayload.dayId 仍写「当天 daysUntil=0 否则 40900」与已裁定的「随时可生成」矛盾，已清理。
5. **PhotoUploadRequest 枚举模型未导出**（Stage 6 live 发现）：String 宽容字段挂 @Schema(implementation) 后 springdoc 内联导出枚举值，wire 等价豁免（保持 String 字段的 40000 宽容校验，规避 Change 1 修过的未知枚举 50000 问题）。

## 4. 全链路冒烟（本地 8080 实测通过）

```
建家 → 照片上传(嗅探 mime) → 发瞬间 → 收藏进日子 → 非当天生成福卡(用户裁定回归 ✓)
→ 分享 scene → 访客体面内容(kicker=生日快乐/relation/照片≤3) → 3 心解锁祝福语 ✓
→ 照片回显(二进制 70B) → 周年称谓(1986→结婚 40 周年·红宝石婚) → 节日预约(HARD_FOR_DAD)
```

## 5. 已知边界与挂账

- **节日横幅落点**：需求文「首页自显」vs 实现「模板馆顶部横滑」——按实现验收，挂账下轮 PRD 勘误
- **deviceId 校验比契约描述更严**：实现为 `[0-9a-fA-F-]{32,64}`（纯十六进制+连字符），契约描述「UUID,32-64 字符」语义一致，前端 device.js 生成的 UUID 满足
- **照片回显公网域名**：云托管部署后需将回图域名加进小程序合法域名白名单（部署 checklist）
- **500KB base64 ≈ 680KB 请求体 vs callContainer 网关上限**：部署联调实测确认，超限回退 multipart（不改契约）
- **真实 subscribeMessage.send**：三 change 均未做，随部署联调统一接

## 6. 后续排期建议

1. **真机自验**：按 docs/qa/testcases-v2.2-family.md P0×24 过主路径（重点 C 组福卡全链路 + D 组周年称谓 + A 组瞬间）
2. **一次性部署**：v1.6+1.7+1.8 三增量合并发布 → ping/模板/日子/卡片全端点 live 复检 → 回填三个 change 的 Stage 9
3. **改名提审**：「念家时·排班纪念日」+ 类目调整 + 隐私指引更新（运营动作）
4. **Change 4 候选**：真实推送联调、老照片修复拉新钩子、家人关系图谱

## 7. 验收状态

| 验收标准（需求 Spec） | 状态 |
|---|---|
| FR-5 节点日历接口闭环（预约/到期清单） | ✅ |
| FR-13 家人瞬间（发布/时间线/真好/收藏/仅本人可删） | ✅ |
| FR-14 生日福卡（随时生成/素材池/访客解锁） | ✅（用户裁定回归） |
| FR-15 周年称谓（25 词条/称谓边界） | ✅（冒烟验证 40 周年） |
| FR-16 胶囊照片上传 | ✅ |
| 测试门：全量 173/173 + 契约一致性 | ✅ |
| 部署 | ⏭️ 待真机自验 |
