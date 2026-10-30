# 需求文档 · 念家时 v2.3 值班日历（个人值班记录与提醒 MVP）

> change_id：`念家时-v2.3-值班日历`
> 上游输入：`docs/prototype/PRD-值班排班.md`（v1.0 MVP 评审稿，2026-10-30）§4–§7 P0 全量 + 交互原型 `docs/prototype/duty-calendar.html`
> 结构化 spec：`docs/requirements/requirements-spec-v2.3-duty.json`（契约草案与实体定义的 SSOT）
> 状态：已评审（用户已拍板两项口径，见 §1 D1/D2）

---

## 0. 范围总览

本 change 交付「值班」Tab（第 5 个 Tab）：**个人视角的值班记录与提醒工具**——今日状态卡（今日班次 + 下次值班倒计时）、月历班次视图与单日编辑、批量排班周期规则（5 预设，核心卖点）、本月统计条、值班提醒配置。竞品做「给班组长的排班工具」，我们反向轻量切入「我的值班日历」，天然融入念家时的「日子」心智。

**包含（P0 全量，对应 PRD §5.1–5.6）：**

| FR | 名称 | 要点 |
|----|------|------|
| FR-D1 | 班次体系 | 固定四班次：早班(08:00–17:00)/中班(12:00–24:00)/夜班(20:00–次日08:00)/休；日历内单字标签 + 配色（早=玫红 #ff4d6d / 中=橙 #e8943a / 夜=藏蓝 #3d4a5c / 休=灰 #c8cdd3）；MVP 不支持自定义班次 |
| FR-D2 | 今日状态卡 | 今日日期星期 + 今日班次（今日早班/今日休班 😴/今日无排班）；下次值班倒计时（向后 60 天内最近非「休」排班日，显示距下次值班还有 N 天 + 日期 + 班次；无排班显示「近期暂无值班安排」）；右上角「🔔 提醒」入口 |
| FR-D3 | 月历视图 | 周一为首日；‹ › 翻月；日期格 = 数字 + 班次色块标签；今日描边高亮；点击日期 → 单日编辑弹层（四班次点选即保存 + 「清除这一天」+ 引导文案指向批量排班）；非本月日期置灰不可点；按月懒加载（当月 ±1 月） |
| FR-D4 | 批量排班 / 周期规则 | 5 预设规则单选：每天早班[早]/做一休一[早,休]/做二休二[早,早,休,休]/早→中→夜→休[早,中,夜,休]/每周五值班[周五→早]；开始/截止日期 picker（默认下月 1 号–月末）；应用逻辑=序列循环填充或按星期匹配；覆盖前二次确认；结果 Toast「已按『做一休一』排 N 天 ✓」；跨度 >1 年截断至 365 天并提示 |
| FR-D5 | 本月统计条 | 当前视图月份早班/中班/夜班/休班次数 |
| FR-D6 | 值班提醒 | 提醒时机三选一：提前 1 天 12:00（默认）/ 当天 08:00 / 提前 2 小时；保存后请求订阅授权（复用既有 /api/notify/subscribe 登记链路）；被拒后不重复弹，状态卡「提醒未开启」弱引导；夜班 + 当天 08:00 组合提示改「提前 2 小时」 |

**已拍板（不再作为开放问题）：**
- 提醒落地口径：**沿用现状**——只存提醒时机配置 + 订阅授权登记（NotifyService 额度账本），不做真实 subscribeMessage.send（部署联调阶段演进项）（用户拍板 2026-10-30）
- 埋点：**本期不做**（小程序无埋点体系，duty_* 4 事件留待拆分信号判断时补）（用户拍板 2026-10-30）

**排除：** 真实推送下发（部署联调）、分享只读视图与分享卡（PRD §5.7 P1）、OCR/文本粘贴导入（§5.8 P2）、自定义班次（P2）、自定义循环序列与「每周几」多选（P1）、工时估算与夜班连续天数警示（P1）、家人互看值班（PRD 开放问题 2，V2 评估）、团队排班/换班审批/薪资工时核算（PRD §11 明确不做）、埋点（本期拍板不做）。

---

## 1. 已拍板决策与本 change 裁定

| # | 决策 | 来源 |
|---|------|------|
| D1 | **提醒不真实发送**：duty 提醒只存时机配置（timing 三选一）+ 复用既有 POST /api/notify/subscribe 登记授权；真实 subscribeMessage.send 与每日 4 条频控下发属部署联调阶段 | 用户拍板 |
| D2 | **本期无埋点**：duty_page_view/duty_day_edit/duty_rule_apply/duty_remind_enable 4 事件不实现（小程序无埋点基建，单独搭上报链路性价比低） | 用户拍板 |
| D3 | **值班 Tab 页放主包 `pages/duty/duty`**：PRD §4.1 写分包 `subpackages/duty/pages/calendar/calendar`，但微信 tabBar 页面必须在主包（框架硬限制）；主包当前 516K，加一页体积无忧，不建分包 | 本 change 裁定（技术必然） |
| D4 | **Tab 第 5 项拍板新增**（PRD 开放问题 1）：TabBar = 日子/家人/**值班**/模板馆/我的，值班插在家人与模板馆之间；现有 Tab 无 icon（纯文字），第 5 项同款纯文字，无视觉拥挤风险（5 项 × 2 字均在宽度安全区） | 用户指令（「新增加一个 tab」）+ 本 change 裁定 |
| D5 | **班次存储 = 用户 × 日期一条**（PRD §6 概念模型照抄）：`duty_shift` 表 uk(user_id, duty_date)；shift 枚举 early/mid/night/rest；source 记 `rule:<ruleType>` 或 `manual`（统计与撤销语义用，MVP 仅存档） | 本 change 裁定 |
| D6 | **周期规则单条生效**：`duty_rule` 表 uk(user_id)，重新应用即整行覆盖（ruleType/startDate/endDate/active）；删除单日班次仅删 duty_shift 行，不触碰规则本体，重新应用规则会回填（PRD §7） | 本 change 裁定 |
| D7 | **规则应用覆盖语义**：序列类规则（daily/on1off1/on2off2/emnr）填充范围内每一天；weekday 类只填充命中星期五的日期；范围内已有排班（无论来源）直接覆盖；跨度 >365 天截断到 365 天并在响应 truncated=true 提示前端 Toast | 本 change 裁定（PRD §5.4 + §7） |
| D8 | **下次值班扫描口径**：从「明天」起向后扫 60 天内最近一个非「休」班次日（今天已在状态卡展示，不参与倒计时）；daysUntil=距今天天数；60 天内无非休排班返回 nextDuty=null，前端显示「近期暂无值班安排」 | 本 change 裁定（PRD §5.2「向后扫描」具体化） |
| D9 | **提醒时机枚举**：`advance_1d`（提前 1 天 12:00，默认）/`same_day_08`（当天 08:00）/`advance_2h`（提前 2 小时）；存 `duty_remind` 表 uk(user_id)；enabled 与授权状态解耦（enabled=用户意愿开关，授权额度走 NotifyService 账本） | 本 change 裁定 |
| D10 | **月历懒加载**：GET /api/duty/calendar?month=YYYY-MM 单月查询；前端首屏拉当月 ±1 月共 3 次；翻月按需拉取并本地缓存，加载失败局部 loading + 重试（PRD §6/§7） | 本 change 裁定 |

---

## 2. 数据实体（概要，完整字段见 JSON spec）

- **DutyShift**（`duty_shift`）：排班记录。id/userId/dutyDate(LocalDate)/shift(early|mid|night|rest)/source(`manual` 或 `rule:<ruleType>`)/updatedAt；uk(user_id, duty_date) 是「用户 × 日期一条」的物理保证。
- **DutyRule**（`duty_rule`）：周期规则。id/userId/ruleType(daily|on1off1|on2off2|emnr|weekday)/startDate/endDate/active/updatedAt；uk(user_id) 单条生效，重新应用整行覆盖。
- **DutyRemind**（`duty_remind`）：提醒配置。id/userId/timing(advance_1d|same_day_08|advance_2h)/enabled/updatedAt；uk(user_id)。

## 3. 页面变更

| 页面 | Tab | scope |
|------|-----|-------|
| `pages/duty/duty` | **值班（新增第 5 Tab）** | 新增：①今日状态卡（今日班次+下次值班倒计时+🔔提醒入口）②班次图例 ③月历视图（翻月+单日编辑弹层）④本月统计条 ⑤主按钮「批量排班」 |
| `app.json` | — | 增量：tabBar 4→5 项（值班插第 3 位）；pages 增 pages/duty/duty |
| `utils/api-duty.js` | — | 新增：duty tag 7 端点封装（对齐契约 v1.9.0） |

## 4. 非功能

- 分包加载目标作废（D3：主包页）；月历渲染无长列表（≤42 格）；基础库 ≥2.30 一致性走查
- 隐私：值班数据仅本人可见（无家人可见性，V2 评估）；无新增采集能力，无需更新微信隐私保护指引
- 合规：值班文案零迷信词风险；班次时段仅生活化表述；无医疗暗示

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 5 Tab 在小屏（iPhone SE 320pt）文字拥挤 | 5 项均为 2 字纯文字 tab，无 icon；Stage 5B 走查 320pt 宽度安全区 |
| 规则应用批量写放大（365 天 = 365 行 upsert） | 单用户单事务批量 saveAll；H2/MySQL 均在秒级；MVP 接受 |
| 订阅授权被拒后的弱引导循环 | 授权拒绝仅登记不重弹（既有 NotifyService 口径），状态卡文案引导进提醒弹层重新开启 |
