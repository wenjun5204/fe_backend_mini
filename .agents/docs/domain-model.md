# 领域模型

## 核心实体

### Family（家族）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| name | String(10) | 家族名，默认生成如「王家庄」，可改 |
| inviteCode | String | 邀请码，分享卡片携带，进家凭证 |
| ownerUserId | Long | 户主（创建者） |
| totalBless | Long | 家族福池累计值（只增不减） |
| plateLevel | Enum | 门牌等级：NONE/BRONZE(勤俭之家)/SILVER(和睦之家)/GOLD(满门福气)/JADE(福泽满堂) |
| memberLimit | Integer | 上限 8 人 |

规则：MVP 每人限加入 1 个家族；无踢人，仅户主可解散。

### FamilyMember（家族成员）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| familyId | Long | 所属家族 |
| userId | Long | 关联用户 |
| role | Enum | 户主 / 成员（家庭称谓标签存 remark：儿子/孙子等，用户自选，不做强校验） |
| personalBless | Long | 个人福值累计 |
| joinedAt | DateTime | 入家时间 |

### User（用户）

微信登录，MVP 仅存 openid、昵称、头像；昵称头像来自微信授权。

### DailyFortuneCard（每日福签，按家族生成）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| familyId | Long | 所属家族 |
| date | Date | 日期（一个家族一天一条 → 全家同签） |
| level | Enum | 平安(70%) / 如意(25%) / 鸿福(5%)，纯稀有度玩法 |
| yiItems | String(JSON) | 「今日宜」条目 2-3 项，从生活化任务库抽取（散步/多喝水/早睡/给家人打电话/泡脚） |
| blessText | String | 祝福语，从文案库抽取 |

规则：等级不影响福值倍率；「宜」条目永不出现「忌」类内容。

### BlessEvent（福值事件，唯一福值变更路径）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| userId / familyId | Long | 归属 |
| type | Enum | DRAW_CARD(+5, 1次/天) / SHARE_CARD(+10) / TASK_DONE(+15) / TIAN_FU(+8, 每人每天对每位家人1次) / INVITE_JOIN(+30) / FAMILY_STREAK_7D(+50) |
| amount | Integer | 正整数，只增不减 |
| createdAt | DateTime | 审计用 |

### FortuneDrawRecord（抽签记录）

用户 × 日期 × 家族，唯一约束；记录当日是否已抽、是否已分享、任务是否已完成（各一次性）。

## 关键状态流转

```
用户当日状态: 未抽签 → 已抽签 → (已分享? 已完成任务?)
                └─ 催福提醒只能发给「未抽签」成员
门牌: NONE → 500 BRONZE → 2000 SILVER → 5000 GOLD → 20000 JADE（只升不降）
```

## 频控规则（service 层强制）

- 抽签：1 次/人/天
- 催福：3 次/人/对人/天
- 添福：1 次/人/对人/天
- 订阅消息：≤ 4 条/人/天（聚合推送）

## 排行榜口径

- 家内榜：本周（周一 0 点起）成员 BlessEvent 汇总
- 好友家族榜：好友关系内家族 totalBless 对比（微信好友关系，MVP 可用双方互关家族模拟）
