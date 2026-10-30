# 需求文档 v2.0 —— 念家时·日子管家（FR-1 记日子 / FR-2 列表详情 / FR-3 提醒 / FR-12 空状态）

> 来源：`docs/PRD-念家时.md` v2.0（2026-10-10 定稿）＋ 交互原型 `docs/prototype/nianjiashi-v2.1.html`（交互事实依据）
> Change ID：`念家时-v2.0-日子管家`
> 本文为 fe-be-contract-harness Stage 1 产出，作为 Stage 2 契约设计的输入。
> PRD 已定稿，无未决澄清问题；本文所有「决策」均为 Stage 1 基于定稿 PRD 与原型交互做出的结构化裁定，Stage 2 可复核但不可推翻产品语义。

## 1. 需求范围

念家时 v2.0 的**引擎一（家庭纪念日管家）**完整落地：新建原生小程序 `miniprogram-nianjiashi`（AppID 沿用现有，三 Tab 骨架），后端原地演进新增 `day_record` 体系，复用既有登录链路（X-WX-OPENID）与家族模型。

| # | 功能 | PRD 出处 | 端 | 是否涉及新接口 |
|---|---|---|---|---|
| FR-1 | 记一个日子（三类日子/零键盘/公农历换算/可见性/提醒配置） | §4.1 | 全端 | 是（/api/days 创建） |
| FR-2 | 日子列表与详情（倒计时排序/双视图/详情页） | §4.1 | 全端 | 是（/api/days CRUD） |
| FR-3 | 提醒推送（配置存取 + 到期计算，频控 ≤3 条/人/天） | §4.1 | 全端 | 是（提醒配置 + 到期计算端点） |
| FR-12 | 新用户空状态 | §4.4 | 小程序 | 否（列表为空的派生态） |

前端本 change 只实现「日子」Tab 全部页面；「模板馆」「我的」两 Tab 仅占位页。

### 1.1 明确排除（exclusions）

| 排除项 | 去向 |
|---|---|
| 模板馆全部功能（FR-4 预览/FR-5 节点日历/FR-6 感恩信/FR-7 相伴天数卡/FR-8 时光胶囊） | Change 2 |
| 海报与裂变（FR-9 海报生成/FR-10 访客视图/FR-11 我的卡片） | Change 2 |
| 解忧册模块删除（JieyouService/端点/契约条目/前端页面） | 独立收尾 change（先改契约再删代码） |
| 小程序改名提审（「念家时·排班纪念日」）、类目调整、隐私指引更新 | 运营动作，不占开发 change |
| 节日借势横幅的数据驱动（FR-5 节点日历同源数据） | Change 2；本 change 横幅区域仅保留结构位 |
| 周年称谓（FR-2 子项「40 周年=红宝石婚」） | V1.2 周年称谓库（依赖起始年份数据，见 §7 决策 D1） |

## 2. 用户故事与功能需求

### FR-1 记一个日子

**用户故事**：作为离家在外的子女，我想零打字地记下妈妈的生日，并让全家都能看到、提前被提醒，这样我就永不错过重要日子。

功能明细：

- **三类日子**：`kind = BIRTHDAY（生日）/ ANNIVERSARY（纪念日）/ TOGETHER（在一起，起始日后每天自动 +1）`。
- **输入零键盘**（合规与适老红线，一票否决项）：
  - 关系用标签点选：妈妈/爸爸/老伴/儿子/女儿/孙子/孙女/亲家/我自己 + 自定义（自定义为标签输入兜底，原型 `RELATIONS` 集合）；
  - 月日用下拉选择（公历按月天数动态天数 28–31，农历固定 30 项）；
  - `kind = TOGETHER` 时整个日期区隐藏（原型交互：无需选日期）。
- **公农历换算**：公历/农历分段任选一侧，实时换算另一侧 + 显示「X 天后」；农历支持下次落点换算（**复用既有 `/api/calendar/convert`**，沿用生日簿算法）。本 change 不暴露闰月选择（原型无此交互）。
- **可见性**：`visibility = FAMILY（家人可见，默认）/ PRIVATE（只我可见）`。未加入家族的用户无「家人可见」语义，强制 PRIVATE。
- **提醒**：默认关闭；开启任一提前量（7 天 / 3 天 / 当天，多选）前弹授权说明（发送内容、频控 ≤3 条/天、仅发给自己），确认后走 `wx.requestSubscribeMessage` + 既有 `/api/notify/subscribe` 登记；拒绝则提醒保持关闭，静默降级不影响任何功能。
- 每条日子带 `emoji`（关系预置映射，如 妈妈=👩），创建成功后直达详情页。

### FR-2 日子列表与详情

**用户故事**：作为记了多个日子的用户，我想按「最近要到的日子」浏览全部日子并点开看详情，这样我知道接下来该为哪个日子做准备。

功能明细：

- **首页列表**（`renderDays`）：生日/纪念日按距下次落点 `daysUntil` 升序排列；`TOGETHER` 固定列表底部，显示「第 N 天」（每天 +1）。最近一条（首个非 TOGETHER）以 hero 卡放大展示。
- **详情页**（`openDetail`）：
  - 倒计时大数字；`daysUntil = 0` 显示「就是今天 🎉」；TOGETHER 显示「N 天，每天 +1」；
  - 类型/公农历/提醒个数标签行；
  - 祝福语点选即复制（`wx.setClipboardData`，祝福语文案库内置前端常量，按 kind 分组，无需接口）；
  - 「做一张卡片」直达模板馆（本 change 跳转占位页，Change 2 接管）；
  - 提醒开关（改当前用户的提醒配置）；
  - 删除需二次确认（`wx.showModal`），删除提示「提醒也会停止」。
- **全部日子页**（`renderAllDays`）：时间线（按下次落点月份分组）⇄ 日历双视图切换；
  - 时间线视图同首页排序规则 + 按月分组头（「N 个日子」）；TOGETHER 归入「每天 · 相伴中」分组；
  - 日历视图：月切换（`calMove`）；**仅标记用户自己记的日子**（生日/纪念日各一色点，不标公共节假日——空间记忆是「我们家的日子」）；点有日子的日期弹出当日事件清单（`openDaySheet`），可逐条进详情。
- **可见性规则**：列表返回 = 本人创建的全部 + 同家族他人 `visibility=FAMILY` 的记录；`PRIVATE` 记录对家人不可见且接口不泄露存在性。
- **编辑/删除权限**：创建人可操作自己的记录；`FAMILY` 可见记录户主亦可编辑/删除（沿用生日簿权限模型）；他人越权操作返回 40400（等价不存在）。

### FR-3 提醒推送（本 change 交付边界：配置存取 + 到期计算）

**用户故事**：作为容易忘记日子的用户，我提前选好提醒时点，到期当天收到带行动建议的提醒。

功能明细：

- 配置模型：**per-user**（`dayId + userId` 维度），沿用既有生日簿提醒模型；`reminders ⊆ {7, 3, 0}`，多选、去重、≤3 项，空数组=关闭；每个家人可在同一条 FAMILY 记录上设自己的提醒。
- 到期计算（服务端）：给定日期，对每条可见日子计算 `daysUntil`，命中用户配置的提前量即产生一条应提醒项（含关系/类型/行动建议文案，如「今天是妈妈的生日 → 做一张专属福卡」——「做卡片」类文案本 change 仅文案占位，跳转目标 Change 2 上线）。
- 频控：**≤3 条/人/天**（v2.0 口径，区别于 v1.5 通知的 4 条），到期计算结果即计入频控口径。
- **本 change 不实现真实 `subscribeMessage.send` 下发**——下发与微信侧联调属 Stage 9 部署联调；授权登记复用既有 `/api/notify/subscribe`（模板 ID 换「日子提醒」模板，开发期占位），拒绝登记静默降级。

### FR-12 新用户空状态

**用户故事**：作为刚打开小程序的新用户，我没有任何日子时看到明确引导，理解「记日子不用打字」，一步开始。

功能明细：

- 无任何可见日子时（`state.records.length === 0`，服务端列表为空）：首页保留节日横幅区域（本 change 无节日数据源，展示静态默认卡或隐藏，以 Stage 2 视觉稿为准），日子区替换为引导卡：
  - 主 CTA「＋ 记一个日子」；
  - 次选「去模板馆逛逛」（跳转占位 Tab）；
  - 「不用打字」顾虑消除文案。
- 有任意一条日子后空状态永不出现；从空状态记下第一个日子后列表即时刷新。

## 3. 前端范围（新建 `miniprogram-nianjiashi/`）

- 原生微信小程序；`project.config.json` 指向现有 AppID（沿用，不重新注册）；**新视觉体系从零搭建，不从旧 `miniprogram/` 迁移任何样式代码**；旧目录保留作参考（废弃迁移属运营收尾，不在本 change）。
- 三 Tab 骨架：**日子（首页）/ 模板馆（占位）/ 我的（占位）**；本 change 仅实现「日子」Tab 全部页面：
  - `pages/days/days` — 首页（hero 卡 + 日子列表 + 空状态 + 记日子 FAB）
  - `pages/add/add` — 记一个日子（关系标签/类型分段/公农历分段/月日下拉/实时换算卡/可见性/提醒授权）
  - `pages/alldays/alldays` — 全部日子（时间线 ⇄ 日历双视图 + 当日事件弹层）
  - `pages/detail/detail` — 日子详情
  - `pages/templates/templates` — 模板馆占位页
  - `pages/mine/mine` — 我的占位页
- 双通道网络层沿用 `utils/request.js` 模式（云托管/本地切换）；视觉规范 v2（暖白底 #fbfaf7、霞橘渐变 #ff7a59→#ff4d6d、动画 ≤2.5s 可跳过）。

## 4. 后端范围（Spring Boot 原地演进，不新建服务）

- **新增表**：`day_record`（日子主体：类型/日历/可见性/月日）、`day_reminder`（per-user 提醒配置，dayId+userId 唯一）。
- **复用**：登录链路（X-WX-OPENID→AuthService）、家族/成员模型（每人限 1 家族，可见性按家族推导）、公农历换算（`/api/calendar/convert`）、订阅授权登记（`/api/notify/subscribe`、`/api/notify/status`）。
- **与既有 BirthdayRecord 的关系**：`DayRecord` 是 `BirthdayRecord` 的**超集**（kind 含 BIRTHDAY，并新增 ANNIVERSARY/TOGETHER；可见性、公农历、per-user 提醒模型一致，但不绑定家族成员/亲友主体，改为自由关系文本）。**MVP 阶段两表并存**：新前端走 `/api/days*` 新链路，旧 `/api/birthdays*` 接口与旧前端不动（家庭内老数据割裂为已接受的产品决策，见风险 R3）。

## 5. 契约需求（Stage 2 输入）

### 5.1 新增端点草案

| 端点 | 方法 | 请求核心字段 | 响应核心字段 | 错误码 |
|---|---|---|---|---|
| `/api/days` | GET | — | `DayListResponse{items:[DayRecordView]}`（服务端排好序：非 TOGETHER 按 daysUntil 升序、TOGETHER 固定底部） | 40100 |
| `/api/days` | POST | `DayUpsertRequest{relation, emoji?, kind, calendarType, month?, day?, visibility, reminders?}` | `DayRecordView`（含计算字段） | 40000/40100 |
| `/api/days/{id}` | GET | — | `DayRecordView` | 40100/40400 |
| `/api/days/{id}` | PUT | 同 POST body（reminders 不经此端点） | `DayRecordView` | 40000/40100/40400 |
| `/api/days/{id}` | DELETE | — | `{deleted:true}` | 40100/40400 |
| `/api/days/{id}/reminder` | PUT | `{reminders:[7\|3\|0]}`（子集/去重/≤3，空=关闭） | `{dayId, reminders}` | 40000/40100/40400 |
| `/api/days/reminder-due` | GET | query `date`(yyyy-MM-dd，缺省=当天) | `{items:[{dayId,userId,remindOffset,title,actionText}]}`（服务端调度/Stage 9 联调用，小程序端不调用） | 40100 |

### 5.2 复用端点（契约不改）

`POST /api/auth/login`（登录）、`POST /api/calendar/convert`（公农历互算/下次落点，请求用 month/day+calendarType 形态）、`POST /api/notify/subscribe` + `GET /api/notify/status`（提醒授权登记/额度查询，模板 ID 换「日子提醒」模板，日上限配置为 3）。

### 5.3 数据实体 DayRecord

`DayRecord { id, userId(创建人), relation(1-12 字，如「妈妈」), emoji(1 个表情，缺省由 relation 映射), kind(BIRTHDAY\|ANNIVERSARY\|TOGETHER), calendarType(SOLAR\|LUNAR；TOGETHER 固定 SOLAR), month(1-12), day(1-31，按日历口径校验), visibility(FAMILY\|PRIVATE，默认 FAMILY), reminders[](当前用户视角的提醒提前量 ⊆{7,3,0}，底层为 day_reminder 表), createdAt }`。

响应视图 `DayRecordView` 附加服务端计算只读字段：`nextDate`(yyyy-MM-dd，TOGETHER 为 null)、`nextDateLunarText`、`daysUntil`、`togetherDays`(仅 TOGETHER，每天 +1)。

### 5.4 错误码（全量复用既有五码，无新增）

40000（month/day 越界、kind/calendarType/reminders 非法、TOGETHER 传 LUNAR）/ 40100（未登录）/ 40300（提醒发送频控 >3 条/人/天，仅 Stage 9 下发链路）/ 40400（记录不存在、PRIVATE 对家人不可见、越权操作等价不存在）/ 40900（本 change 无新增场景，保留枚举）。

## 6. 验收标准

**FR-1**：① 三类日子各建 1 条全部成功，响应含正确 nextDate/daysUntil；② 录入全程零键盘（无任何 text input 组件）；③ 公历侧选 10-18 实时显示农历文本+落点+「X 天后」，农历侧选八月初三实时显示公历落点+「X 天后」；④ 默认 FAMILY 可切 PRIVATE，无家族用户建 FAMILY 请求被服务端降级/拒绝且文案可解释；⑤ 提醒默认关闭，开启 7 天前出现授权说明弹层，拒绝后配置不变；⑥ reminders 传 4 项/重复值/非 {7,3,0} 值均 40000；⑦ TOGETHER 建成后 togetherDays=1，次日 +1。

**FR-2**：① 列表顺序=非 TOGETHER 按 daysUntil 升序、TOGETHER 固定底部；② daysUntil=0 的详情页显示「就是今天 🎉」；③ PRIVATE 记录不出现在家人账号列表/详情（40400）；④ 非创建人非户主编辑/删除 FAMILY 记录返回 40400；⑤ 删除有确认弹层，删除后本人提醒配置一并清除；⑥ 时间线按落点月份分组；日历仅标用户自己的日子（含跨月切换、当日事件弹层）；⑦ 祝福语点选复制成功（剪贴板内容一致）。

**FR-3**：① PUT reminder 幂等覆盖，响应回显当前配置；② reminder-due 对「当天+提前 7/3 命中」的日子各返回一条应提醒项，含行动建议文案；③ 同一用户当日应提醒项 >3 时按规则截断且不越频控；④ 本 change 无任何真实下发调用（`subscribeMessage.send` 不出现于交付代码）；⑤ 授权拒绝登记不影响日子功能（静默降级）。

**FR-12**：① 空账号首页展示引导卡（主 CTA/次选/「不用打字」文案齐备）；② 记下第一个日子后空状态消失、列表即时刷新；③ 有日子的账号永不出现空状态。

**通用**：新端点正常/边界/错误码用例齐备；合规词表扫描全量通过；提醒默认关闭写入默认值逻辑而非前端约定。

## 7. 关键决策（Stage 1 裁定）

- **D1 无年份设计**：`DayRecord` 不含年份（沿用「月日循环」心智与原型数据结构）。推论：`TOGETHER` 起始日=记录当天（createdAt 年+month/day，原型隐藏日期区）；周年称谓（40 周年=红宝石婚）无法计算，延至 V1.2 周年称谓库；Change 2 的相伴天数卡/感恩信需要「含年份的起始日」时再增补字段或独立实体。
- **D2 新旧并存**：`/api/days*` 全新链路，`/api/birthdays*` 与旧前端不动；不做数据迁移。
- **D3 提醒 per-user**：沿用生日簿提醒模型（dayId+userId），FAMILY 记录上每个家人各自配置、各自收提醒。
- **D4 FR-3 交付边界**：配置存取 + 到期计算（含 `/api/days/reminder-due` 调度端点）属本 change；真实订阅消息下发属 Stage 9 部署联调。
- **D5 农历口径**：不暴露闰月选择，落点换算按农历月/日数值匹配（对齐原型 `findLunar` 与既有生日簿算法）；如 Stage 2 评审认定需 isLeapMonth，属契约增量。
