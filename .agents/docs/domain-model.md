# 领域模型

> 对齐契约 v1.9.0 与 27 个实体（2026-10-30）。双引擎：日子管家 × 值班日历。
> v1「福签/福值」体系仍在库中（旧前端归档），新迭代不得扩展该体系。

## 一、日子引擎（v2.0+，主引擎之一）

### DayRecord（日子记录）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| userId | Long | 归属（可见性 SELF/FAMILY） |
| name | String | 日子名（如「妈妈的生日」） |
| kind | Enum | BIRTHDAY / ANNIVERSARY / TOGETHER |
| calendarType | Enum | SOLAR / LUNAR（农历生日经 `/api/calendar/convert` 算公历落点） |
| month / day | Integer | 月日 |
| year | Integer | 可空；TOGETHER 起始年、ANNIVERSARY 周年计算基数 |
| relationText | String | 自由关系文本（「妈妈」「我们俩」） |
| emoji | String | 展示图标 |
| visibility | Enum | SELF 仅自己 / FAMILY 家人可见 |

规则：BIRTHDAY 不输出年龄（隐私）；ANNIVERSARY 输出周年称谓（1 纸婚…40 红宝石…60 钻石，
静态库按 year 差计算）；TOGETHER 输出在一起天数。

### DayReminder（按人提醒配置）

DayRecord × 提醒天数偏移（默认提前 3 天）；到期清单 `/api/days/reminder-due`。

## 二、值班引擎（v2.3，主引擎之二）

### DutyShift（排班记录，用户 × 日期一条）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| userId | Long | 归属（仅本人可见） |
| dutyDate | LocalDate | 业务日期；uk(user_id, duty_date) |
| shift | String | early 早 / mid 中 / night 夜 / rest 休（服务层校验值域） |
| source | String | manual 或 rule:<ruleType>（存档用） |

### DutyRule（周期规则，用户级单条生效）

ruleType：daily[早] / on1off1[早,休] / on2off2[早,早,休,休] / emnr[早,中,夜,休] / weekday(周五→早)。
uk(user_id)；重新应用整行覆盖；序列规则填充范围内每一天（含休班），weekday 只填命中日；
跨度 >365 天截断；删除单日/范围清除均不触碰规则本体（重新应用回填）。

### DutyRemind（提醒配置，用户级一条）

timing：advance_1d(提前1天12:00,默认) / same_day_08(当天08:00) / advance_2h(提前2小时)；
enabled 意愿开关。真实 subscribeMessage.send 不做——授权额度走 NotifyGrant 账本（部署联调演进项）。

**下次值班口径**：从明天起 60 天内最近非「休」班次日（今天不参与倒计时）。

## 三、家族与家人（v1 基座 + v2.2 家人空间）

### Family / FamilyMember

每人限 1 家族，上限 8 人；inviteCode 进家凭证；户主/成员两角色。
退出/转让/解散未实现（破坏性操作，入口隐藏）。
福池 totalBless 与门牌等级为 v1 遗产字段，随旧前端归档不再演进。

### FamilyMoment（家人瞬间）+ MomentLike / MomentCollect

照片+一句话时间线；真好（每人对每条限 1 次，uk）；收藏进日子（仅 BIRTHDAY 日子，
形成生日福卡素材池，全家人收藏共享可见）；删除仅本人（隐私红线），照片联动失效。

### PhotoAsset（照片管道）

photoId（16-20 位 base62 高熵串=capability URL）/scene(MOMENT|CAPSULE)/BLOB 存储
（解码后 ≤500KB，每日 ≤20 张）/status(ORPHAN→ACTIVE→DELETED，>48h GC)。

## 四、表达层（模板馆引擎 v2.1）

### MadeCard（定稿卡片）

templateKind：LETTER 感恩信 / DAYS_CARD 相伴天数卡 / CAPSULE 时光胶囊 / BIRTHDAY_CARD 生日福卡；
status ACTIVE/SOFT_DELETED；shareScene 场景码幂等签发；访客 3 心解锁链路。
生日福卡**无当天门控**（随时可生成，用户裁定）。

### TimeCapsule（时光胶囊）

封存（可附 photoId）→ 到期开箱（幂等，40900 未到期）。

### LetterGenerateQuota（感恩信 AI 额度）

每日每人 ≤5 次（AI 与降级路径统一计数）；AI 关闭时全走 FALLBACK 文案库。

## 五、节日与通知

### FestivalReservation（节日/上线预约）

uk(user_id, template_key) 幂等登记；0-7 天到期清单（模板馆横滑数据源）；
真实推送下发排除（同 NotifyService 口径）。

### NotifyGrant / NotifySend（订阅消息额度账本）

remainingQuotaToday = 累计接受授权数 − 当日已发送数，下限 0；每日上限 4 条。
**只记账不发消息**——真实发送是部署联调阶段演进项（duty 提醒同口径）。

### VisitorUnlock（访客解锁）

免鉴权组 `/api/visitor/*`；3 心解锁隐藏内容；点亮心软频控 42900（单设备单卡 >3 次/分钟）。

## 六、v1 遗产（已停止演进，仅维护）

Family 福池/门牌、DailyFortuneCard 每日福签（全家同签，平安70/如意25/鸿福5）、
BlessEvent 福值事件（唯一福值变更路径，只增不减）、FortuneDrawRecord 抽签记录、
Bagua 八卦进度（只读推导）、Jieyou 解忧册（宽心话纯随机）、挂件/奖状。
**红线延续**：福值永不惩罚用户；迷信词禁用表继续对全部用户可见文案生效。

## 频控规则（service 层强制）

- 抽签：1 次/人/天（v1）；催福：3 次/人/对人/天（v1）
- 瞬间发布：10 条/人/天；照片上传：20 张/人/天；感恩信生成：5 次/人/天
- 订阅消息：≤4 条/人/天（聚合推送）；访客点亮心：单设备单卡 >3 次/分钟 → 42900
