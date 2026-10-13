# 需求文档 · 念家时 v2.1 模板馆引擎（引擎二）

> change_id：`念家时-v2.1-模板馆引擎`
> 上游输入：`docs/PRD-念家时.md`（v2.0 定稿）§4.2 / §4.3 + `docs/prototype/nianjiashi-v2.1.html`（交互 SSOT）+ Change 1（念家时-v2.0-日子管家）既有资产
> 结构化 spec：`docs/requirements/requirements-spec-v2.1-cards.json`（契约草案与实体定义的 SSOT）
> 状态：待强制评审（pending_mandatory_review）

---

## 0. 范围总览

本 change 交付「仪式感内容工厂」闭环：**做卡片 → 海报（带码）→ 扫码 → 访客 3 心解锁 → CTA 转化**，外加胶囊支线 **封存 → 开箱日召回 → 开箱 → 分享开箱**。

**包含（FR 明细）：**

| FR | 名称 | 要点 |
|----|------|------|
| FR-4 | 模板预览 | 服务端模板配置（清单/分类/上下架/预约状态）+ 前端预览弹层（迷你成品/特性/社会证明/场景提示） |
| FR-5 | 节点日历 | **仅前端展示层**：模板馆顶部节日卡横滑由模板配置数据驱动、本地倒计时；服务端预约推送调度排除（V1.1） |
| FR-6 | 感恩信（AI 代笔） | 选素材（关系/故事多选/语气）→ 后端生成（AI 或降级文案库）→ 逐句改稿 → 定稿存档 |
| FR-7 | 相伴天数卡 | 基于 DayRecord kind=TOGETHER 或独立输入起始日，出卡片数据（含样式选择） |
| FR-8 | 时光胶囊 | 内容+可选照片+开箱日 → 封存（开箱前不可读）→ 开箱日读信 → 分享开箱（V1.1 内容提前到本 change） |
| FR-9 | 海报生成 | 前端 canvas 渲染；后端为每张卡片签发唯一 scene 并下发带参二维码链接 |
| FR-10 | 访客视图 | 免登录公开端点组；体面内容全量 + 打码钩子；3 心解锁（device 维度防刷）；解锁后转化 CTA 数据 |
| FR-11 | 我的卡片 | 生成即存档（当日去重前端做）；列表/再次分享数据源/删除 |

**排除：** 解忧册删除（独立收尾 change）、家人空间（V1.2）、节点日历服务端推送调度（V1.1）、模板 UGC（V2）、改名提审（运营动作）、生日福卡/硬话/结婚纪念日卡的生成流程（模板馆内仅展示为可预约，不提供制作端点）。

---

## 1. 已拍板决策（不再作为开放问题）

| # | 决策 |
|---|------|
| D1 | **AI**：OpenAI 兼容网关，后端读 `OPENAI_BASE_URL` / `OPENAI_API_KEY` / `HARNESS_LLM_MODEL` 风格环境变量；**无 Key 或调用失败时降级本地文案库**（每素材双写法组合），感恩信功能永不因 AI 不可用而报错不可用 |
| D2 | **海报**：前端 canvas 渲染（含二维码本地绘制），后端只下发 scene 码与访客页路径（带参二维码链接），不做服务端出图 |
| D3 | **访客防刷**：device 维度计数——前端生成并持久化 deviceId（本地随机 UUID），同一设备对同一卡片不重复计数 |
| D4 | **范围含时光胶囊**（V1.1 提前）：封存 → 开箱日召回 → 开箱 → 分享开箱全链路 |
| D5 | **胶囊存储形态**：TimeCapsule 独立实体（非 MadeCard 子类型），内容**明文存 + 读接口门控**（详见 §4.4 裁定理由） |
| D6 | **AI 降级文案库**：每故事素材 ≥2 写法 × 每语气 ≥2 收尾 × 开头 ≥3 写法，服务端组装为每段 ≥2 备选的 variants 返回 |
| D7 | **合规**：AI 返回内容逐段过违禁词表（沿用 DayTextLib.FORBIDDEN_WORDS 口径），命中即丢弃该段并用降级文案库写法补位，永不返回违禁内容 |

---

## 2. 数据实体（概要，完整字段见 JSON spec）

- **TemplateConfig**（`template_config`）：模板配置，key/name/category(FESTIVAL|FAMILY|COUPLE|FUN)/live/sort/festivalDate(可空，节点倒计时数据源)/社会证明与预览元数据。上下架由数据配置驱动（无管理端点，SQL/种子维护）。
- **MadeCard**（`made_card`）：卡片存档。id/userId/templateKind(LETTER|DAYS_CARD|CAPSULE)/payload(JSON，按 kind 异构：定稿内容+样式)/sourceDayId(可空，DAYS_CARD 可关联 DayRecord)/shareScene(唯一，首次分享时签发)/status/createdAt；计算字段 totalHearts(收到的心)。
- **TimeCapsule**（`time_capsule`）：独立实体。id/userId/type(SELF|PARTNER)/openRule/openDate/coverHint/contentText/photoUrl(可空)/status(SEALED|OPENED)/sealedAt/openedAt。开箱前读接口不返回 contentText/photoUrl。
- **VisitorUnlock**（`visitor_unlock`）：deviceId+cardId 唯一，hearts(0-3)/unlockedAt(可空)。

## 3. 端点草案（14 个，visitor 组免鉴权）

1. `GET /api/templates` — 模板清单（分类筛选；含 festivalDate 供前端节点日历倒计时）
2. `POST /api/templates/letter/generate` — 感恩信生成（AI/降级，返回每段 ≥2 备选 variants；每日每人 ≤5 次，40300）
3. `POST /api/cards` — 卡片定稿存档（LETTER 定稿 / DAYS_CARD / CAPSULE 邀请函）
4. `GET /api/cards` — 我的卡片列表（双列卡片墙 + 统计：做过的卡片数 / 收到的心）
5. `GET /api/cards/{id}` — 卡片详情（再次分享数据源）
6. `DELETE /api/cards/{id}` — 删除卡片
7. `POST /api/cards/{id}/share` — 签发 shareScene + 访客页路径（幂等，首呼签发后固定）
8. `POST /api/capsules` — 封存胶囊
9. `GET /api/capsules` — 我的胶囊列表（待开红点）
10. `GET /api/capsules/{id}` — 胶囊详情（开箱前仅返回封面提示语/开箱日/倒计时，**不含内容**）
11. `POST /api/capsules/{id}/open` — 开箱（未到期 40900；幂等）
12. `GET /api/public/cards/{scene}` — 【免鉴权】访客卡片公开数据（体面内容 + 打码钩子 + 本设备心进度）
13. `POST /api/public/cards/{scene}/hearts` — 【免鉴权】点亮心（device 维度；软频控 42900）
14. `GET /api/public/cards/{scene}/content` — 【免鉴权】解锁后取隐藏内容（未解锁 40900）

复用：`/api/auth/login`（登录链路）、`/api/days*`（togetherDays 天数卡数据基础）、`/api/notify/subscribe`（胶囊开箱日提醒的订阅登记，语义复用，模板 ID 占位，本 spec 不展开）。

错误码：沿用 40000/40100/40300/40400/40900 + **新增 42900**（访客点心软频控）。40300 新增语义=AI 生成每日 >5 次/人；40900 新增语义=胶囊未到期开箱、访客未解锁取内容。

## 4. FR 明细与验收标准

### 4.1 FR-4 模板预览

- 模板馆数据由 `GET /api/templates` 驱动：分类 Tab（全部/节日/家人/情侣/整活）前端过滤 category；未上线模板（live=false）主按钮「🔔 上线提醒我」（预约登记**仅前端本地收藏**，服务端预约推送属 V1.1）；已上线模板主按钮「✨ 开始制作」。
- 每个模板点击先进预览弹层：迷你成品（真实样式渲染）+ 特性标签 + 社会证明（人数/时长/免费）+ 场景化提示，全部字段来自 TemplateConfig。

**验收：**
- `GET /api/templates` 返回 ≥6 条模板（letter/days/capsule/birthday/father/wedding 对齐原型 TPL_DATA），live=false 模板前端不出现「开始制作」可进入路径（birthday 除外——live 但无制作页，按钮提示「生日当天首页提醒你」）。
- 预览弹层所有展示字段（kicker/emoji/样本文案/sub/feats/stats/hint）可由接口数据渲染，无硬编码模板名。

### 4.2 FR-6 感恩信（AI 代笔）

- 输入全点选：recipient（妈妈/岳母/婆婆/奶奶/自定义文本）→ stories 多选（5 个固定素材键：「她总给我留饭」「送我上学那些年」「她爱唠叨但都是好意」「她一个人把我带大」「我离家后她学会视频」）→ tone（tear 戳泪点/fun 幽默/calm 平淡）。
- 后端生成 5 段式：开头 + 每个故事一段 + 语气收尾 + 落款。AI 模式：LLM 产出后服务端组织成 variants（每段 ≥2 备选）；降级模式：从文案库组合（每故事 ≥2 写法、每语气 ≥2 收尾、开头 ≥3 写法）。响应标明 source: AI|FALLBACK。
- 逐句改稿：前端在每段 variants 内循环点换；导航栏 🔄 整体重写 = 再次调 generate（计入频控）。**任何情况下信件功能不返回错误不可用**——AI 异常静默降级，用户无感。
- 定稿：`POST /api/cards`（templateKind=LETTER，payload 存定稿段落+recipient+tone+样式）。

**验收：**
- 无 OPENAI_API_KEY 环境下 generate 仍返回完整 5 段信（source=FALLBACK）。
- 每段 variants ≥2；点换在同段 variants 内循环不重复相邻。
- AI 输出含违禁词的段被丢弃并以下降级写法补位，接口永不返回违禁内容。
- 同一用户当日第 6 次调 generate 返回 40300。
- 定稿后海报正文引用最终改定内容；我的卡片出现该卡。

### 4.3 FR-7 相伴天数卡

- 输入：relation（恋人/结婚/闺蜜/好友）→ 起始日（两种来源：选择关联的 DayRecord kind=TOGETHER（sourceDayId + togetherDays 服务端计算）；或独立输入起始日期）→ 样式（SUNSET 霞橘渐变 / NIGHT 深夜星河 / MINT 薄荷清晨）。
- 天数 = 今天 − 起始日（下限 1），实时预览前端本地计算，每天自动 +1，「永不过期」。
- 生成即存档 `POST /api/cards`（templateKind=DAYS_CARD）。

**验收：**
- 选关联 TOGETHER 日子时 payload.sourceDayId 指向该 DayRecord，天数与 `GET /api/days` 的 togetherDays 一致；无关联时独立起始日同样出正确天数。
- 三种样式均可渲染并正确存档（styleKey 回显一致）。

### 4.4 FR-8 时光胶囊

- 输入：type（SELF 给未来的自己 / PARTNER 给未来的 TA）→ 开箱日预设（ONE_YEAR_LATER 一年后 / GAOKAO_SCORE_DAY 高考出分日 / WEDDING_ANNIVERSARY 结婚纪念日 / BABY_18 宝宝 18 岁；openRule 保留枚举用于展示，**openDate 为权威判定字段**，ONE_YEAR_LATER 服务端按封存日+365 天计算，其余预设由用户选定具体日期）→ 封面提示语 coverHint（预置点选：「一年不见，你还挺想我吧」「立个 flag 在这儿了」等）→ contentText（内容）+ photoUrl（可选照片标记/图片链接）。
- **实体裁定：TimeCapsule 为独立实体，不做成 MadeCard 的子类型。** 理由：①胶囊有独立生命周期状态机（SEALED→OPENED）与开箱日语义（到期判定/召回/提醒），MadeCard 的 status 语义承载不了「时间门控」；②开箱前内容不可读是跨接口的读取门控（本人详情、访客视图都要遮挡），需要实体级字段级权限而非 payload 黑盒；③胶囊同时要产出「邀请函」卡片进我的卡片墙（templateKind=CAPSULE 的 MadeCard 引用 capsuleId），两者是一对一引用关系而非包含关系。
- **存储形态裁定：明文存 + 读接口门控。** 理由：威胁模型里没有「数据库管理员/拖库」对抗需求（个人主体小程序、内容本身无金融/医疗敏感数据），加密存储引入密钥管理成本且开箱日仍需服务端解密下发、门控逻辑一寸不少；接口门控已满足「开箱前不可读」的产品承诺（本人端 GET 详情不返回内容字段、访客端只发 coverHint）。加密存储列为后续安全增强，不阻塞本 change。
- 封存后生成邀请函海报（MadeCard templateKind=CAPSULE + share scene，访客可见体面内容=coverHint+开箱倒计时，隐藏内容=信件本体）。开箱日当天首页召回横幅由 `GET /api/capsules` 返回的 dueNow/openedToday 数据驱动；开箱页摇晃动画→信件+照片揭示→双 CTA（我也埋一个 / 分享开箱）。订阅消息开箱日提醒复用既有 `/api/notify/subscribe` 语义（templateId 占位，一句引用不展开）。

**验收：**
- 封存成功返回 SEALED 状态 + daysUntilOpen；`GET /api/capsules/{id}` 开箱前响应**不含** contentText/photoUrl 字段（非空值遮挡，是不下发）。
- 未到期 `POST /open` 返回 40900；到期 open 成功置 OPENED 并返回内容；重复 open 幂等返回首次结果。
- 开箱日当天 `GET /api/capsules` 该胶囊 dueNow=true，首页召回横幅出现；开箱后横幅消失。
- 邀请函海报进我的卡片；访客扫码在开箱前只能看到 coverHint + 倒计时。

### 4.5 FR-9 海报生成

- 后端职责仅两件：卡片存档 + `POST /api/cards/{id}/share` 签发唯一 shareScene（8-12 位随机串，卡片维度幂等）并下发访客页路径 `pages/visitor/visitor?scene=...`（scene 协议，≤32 字符满足小程序码限制）。
- 前端 canvas 绘制海报：正文（引用卡片定稿内容）+ 固定品牌位（念家时 + slogan）+ 二维码（本地按访客页路径生成）；三套类样式（信纸夜空/霞橘/薄荷）；分享面板（微信好友/朋友圈/面对面/保存图片）。

**验收：**
- share 幂等：同卡片两次调用返回同一 scene。
- 海报 canvas 输出含品牌位与可扫的二维码，扫码直达访客视图且内容与该卡片一致。
- 海报生成 ≤3s（性能预算）。

### 4.6 FR-10 访客视图

- 访客端点组（scene 维度）**免鉴权**：不要求登录 token，不返回 40100；访客身份以 body/query 的 deviceId 标识（前端首次进入生成 UUID 并持久化）。
- `GET /api/public/cards/{scene}`：按卡片可见策略展示**体面内容**（天数卡=天数+起始日；信=前 3 段；胶囊=coverHint+开箱倒计时）+ 打码钩子（「还有一段 TA 没发出来的话」锁定态占位）+ 本设备 hearts 进度。
- `POST /hearts`：deviceId 维度递增（同设备同卡片封顶 3，不重复计数），返回 hearts/unlocked/剩余提示；42900 软频控（单设备单卡片 >3 次/分钟）。
- `GET /content`：仅 unlocked=true 可取隐藏内容（否则 40900）；解锁后前端展示深色转化 CTA（固定文案：「用念家时打开你们的照片 / 免费记日子做卡片埋胶囊」，前端常量）。
- **微信合规红线**：不强制授权、不强制注册。

**验收：**
- 未登录设备全流程可走通：查看→3 心点亮→解锁内容→CTA。
- 同一 deviceId 重复点亮不增加计数（第 4 次起返回当前值）；A 设备解锁不影响 B 设备进度。
- 未解锁调 content 返回 40900；已删除/不存在 scene 返回 40400。
- 心进度提示「还差 N 颗」与服务端计数一致。

### 4.7 FR-11 我的卡片

- 生成即存档（信定稿 / 天数卡生成 / 胶囊封存即邀请函）；**当日同 kind 去重由前端做**（对齐原型 pushMadeCard），服务端不去重。
- `GET /api/cards`：双列卡片墙数据（样式渲染所需的封面元数据从 payload 归并）+ 统计（做过的卡片数、收到的心=所有卡片 totalHearts 之和）；「再次分享」直接重出海报（数据源=卡片详情）；删除需确认。

**验收：**
- 三类卡片各做 1 张后列表 3 条、封面渲染正确（kind+styleKey 驱动）。
- 再次分享出图内容与定稿一致（修改后不受影响）。
- 删除后：列表消失、对应 scene 访客端返回 40400（软删，已分享出去的海报码失效）。
- 当日同 kind 前端去重生效（服务端不去重为既定行为）。

## 5. 非功能与合规

- **合规红线（继承 AGENTS.md / PRD §5，一票否决）**：AI 生成文案须过合规词表断言——静态文案库类加载期断言（沿用 DayTextLib.FORBIDDEN_WORDS 机制，违禁词出现即启动失败）；AI 运行时输出逐段过滤（D7）。分享是奖励制非强制；隐私最小化（AI 信件内容不含敏感个人信息上报、访客免注册）。
- **AI 成本控制**：生成每日每人 ≤5 次（40300）；AI 优先降级兜底，降级路径零外部依赖。
- **性能**：海报生成 ≤3s；AI 写信加载动画 ≤2.5s 且可跳过（降级路径下实际生成 <500ms）。
- **视觉规范 v2**：沿用 PRD §5 色板/字重/动画约束；`prefers-reduced-motion` 全量降级。
