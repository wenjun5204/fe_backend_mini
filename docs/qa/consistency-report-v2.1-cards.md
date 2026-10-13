# 前后端一致性报告 · 念家时-v2.1-模板馆引擎

> Stage 6 产物 · 契约 v1.7.0 · 2026-10-10
> change_id: 念家时-v2.1-模板馆引擎 · 分支 feature/nianjiashi-cards-mvp

## 校验矩阵

| # | 校验项 | 工具 | 结果 |
|---|---|---|---|
| 1 | 契约四栈一致性（47 路由 / 89 模型） | `contract cli check` | ✅ react/vue/node/java 全过 |
| 2 | 生成区漂移（java / react 骨架 vs 契约） | `contract cli drift` | ✅ 双栈零漂移 |
| 3 | 字段级交叉核对（13 个 Cards 系契约 schema vs 后端 DTO） | 脚本比对 | ✅ 契约字段在 Java 侧零缺失 |
| 4 | 后端全量测试 | `mvnw test` | ✅ 126/126（新增 40：Cards 19/Capsules 6/Visitor 7/AiLetter 单测 8） |
| 5 | 运行时序列化漂移 | `contract cli live` | ✅ 47/47 契约路由全在（live 48 含豁免探活 /api/ping）；89 模型全导出（Pendant 存量豁免） |
| 6 | **端到端裂变链路冒烟**（真实 HTTP 九步） | curl 脚本 | ✅ 见下 |
| 7 | 前端语法/合规 | node --check 23 文件全过；违禁词表零命中；13 页 × 4 件套路径自查 | ✅ |

## 端到端冒烟记录（本地 8080 真实调用）

```
1) 感恩信生成（无 OPENAI_KEY）→ source=FALLBACK、4 段、每段备选=2   ✅ 降级链路可用
2) 定稿存档 + 分享码签发 → scene 幂等（两次同码）、pagePath 28+8   ✅
3) 访客公开视图（免登录）→ 打码钩子「还有一段 TA 没发出来的话」    ✅
4) 3 心点亮（deviceId 维度）→ unlocked=true, hearts=3             ✅
5) 解锁取内容 → hiddenContent/ctaCopy 正常下发                     ✅
6) 未解锁设备取内容 → 40000                                        ✅ 门控
7) 他人读我的卡片 → 40400（不泄露存在性）                          ✅
8) 胶囊封存 SEALED → 未到期开箱 → 40900                            ✅ 时间门控
9) 卡片软删 → 该 scene 访客端 → 40400                              ✅ 链路联动失效
```

## 本阶段发现并已修复的问题

1. **LetterTone / StoryKey 模型未导出**（5A 引入）：5A 为规避 Jackson 枚举反序列化 50000 将入参字段改为 String 手工解析（与 Change 1 的 DayKind「真枚举+宽容 @JsonCreator」模式不一致），导致 springdoc 不导出这两个命名模型、live 校验漂移。**修复：DTO 三字段加 `@Schema(implementation=...)` 元数据引用**（LetterGenerateRequest.stories/tone + LetterSegment.storyKey）——纯元数据，wire 格式与 40000 校验语义不变，126/126 复测全绿。
2. **契约勘误（Stage 3 风险项兑现）**：ShareSceneResponse.pagePath maxLength 32 与实际 36 字符矛盾 → 改 40（spec 同步）。

## 显式豁免（存量漂移）

- **`Pendant`**：v1.5 存量（契约命名枚举 vs 运行时裸 String，wire 正确），随旧前端归档废弃，沿用 Change 1 一致性报告豁免口径。live 校验因此报 ok:false，除此项外全部通过。

## 冒烟中的契约口径确认（非缺陷）

- deviceId 校验为 UUID 字符集（32-64 位 hex/横线），与契约「前端生成并持久化的 UUID」一致；前端 device.js 生成 32 位 hex。
- LETTER 定稿 payload 必填五件套（recipient/tone/paragraphs/signText/styleKey），signText 必须等于服务端固定落款（防前端伪造），契约 description 已写明。
- 胶囊内容字段名为 contentText（非 content），契约与实现一致。

## 结论

**一致性 PASS**（1 项存量豁免）。14 端点在四栈校验、字段交叉核对、运行时 live 校验、真实 HTTP 端到端冒烟四个层面与后端实现、前端封装对齐；AI 降级链路（无 Key 环境全功能可用）经冒烟验证。
