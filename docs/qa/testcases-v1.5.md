# 测试用例 v1.5 —— fe-be-contract-harness Stage 8 产出

> 从 `api/openapi.yaml` v1.5.0 契约派生。按 QA 约定:每端点覆盖正常/边界/错误码;验收覆盖接口响应、DB 数据、日志、副作用四维度;前端验收含完整交互路径。

## 1. GET /api/bagua/status

| # | 场景 | 前置 | 预期 |
|---|---|---|---|
| B1 | 正常·新家族(零进度) | 新建家族,无任何事件 | 200;trigrams=8 项,litCount=0,complete=false,celebrated=false;每卦 current=0(震 current=memberCount-1=0) |
| B2 | 正常·部分点亮 | 造数:SHARE_CARD×30 | 兑 lit=true current=30;其余未亮;litCount=1 |
| B3 | 边界·进度只涨不跌 | 兑已亮后当日再分享(被 40900 拒) | status 中兑仍 lit=true,current 不下降 |
| B4 | 正常·圆满 | 8 卦条件全部造数满足 | complete=true;首次查询 celebrated=false |
| B5 | 幂等·庆祝标记 | B4 后调 POST /api/bagua/celebrate 两次 | 两次均 200 {celebrated:true};family_milestone 仅 1 行;此后 status 返回 celebrated=true |
| B6 | 错误·未登录 | 无 token | 40100 |
| B7 | 错误·未加入家族 | 登录但未加入 | 40400(家族不存在)或按契约 notJoined 语义 |
| B8 | 只读性 | 尝试任何写 | 契约无写路径(除 celebrate);客户端伪造写进度无接口可调 |

DB 维度:status 不写任何表(celebrate 仅写 family_milestone)。

## 2. GET /api/jieyou/quote

| # | 场景 | 前置 | 预期 |
|---|---|---|---|
| J1 | 正常·当日首翻 | 当日无记录 | 200;quoteId/type/text/pageNo 齐全;text ≤16 字;type ∈ 三枚举;jieyou_daily_record 落 1 行 |
| J2 | 幂等·同日重复 | J1 后同用户再请求 | 返回与 J1 完全相同的 quoteId;记录表仍 1 行 |
| J3 | 边界·跨日 | 次日再请求 | 新记录落库;quoteId 可同可不同(纯随机) |
| J4 | 边界·不同用户同日 | 用户 B 同日请求 | 各自独立随机,互不影响 |
| J5 | 错误·未登录 | 无 token | 40100 |
| J6 | 合规·文案库 | 全量 120 条扫描 | 违禁词表(含 v1.5 追加 14 词)零命中;每条 ≤16 字;三型各 40 条 |

## 3. POST /api/notify/subscribe

| # | 场景 | 前置 | 预期 |
|---|---|---|---|
| N1 | 正常·接受授权 | body {templateId, accepted:true} | 200 {registered:true, remainingQuotaToday≥1};notify_grant 落 1 行 |
| N2 | 正常·拒绝也登记 | accepted:false | 200 {registered:true};grant 落库 accepted=false;remainingQuotaToday 不增加 |
| N3 | 边界·额度上限 | 同日多次 accepted=true 后 | remainingQuotaToday 不超过 4-今日已发 |
| N4 | 错误·缺字段 | 缺 templateId | 40000 |
| N5 | 错误·未登录 | 无 token | 40100 |
| N6 | 语义·无副作用 | 拒绝授权后抽卡/分享 | 所有功能正常(静默降级) |

## 4. GET /api/notify/status

| # | 场景 | 预期 |
|---|---|---|
| S1 | 正常·有额度 | subscribed=true;dailyLimit=4;remainingQuotaToday 与账本一致 |
| S2 | 正常·无额度 | subscribed=false;remainingQuotaToday=0 |
| S3 | 错误·未登录 | 40100 |

## 5. POST /api/fortune/draw 与 /api/fortune/today(卦位扩展)

| # | 场景 | 预期 |
|---|---|---|
| F1 | 正常·抽卡带卦位 | 响应 FortuneCard 含 gua(8 枚举之一)与 cardTheme(与 gua 映射一致) |
| F2 | 一致·全家同卦 | 同家族用户 A/B 同日分别 draw | gua 相同;cardTheme 相同 |
| F3 | 一致·同日幂等 | 同用户同日重复 draw | 40900(今日已抽);today 仍返回同 gua |
| F4 | 无关性·卦位不影响概率 | 抽样 ≥300 次不同家族/日期 | 平安/如意/鸿福分布 ≈ 70/25/5,与 gua 无相关性(卡方检验 p>0.05) |
| F5 | today 未抽状态 | 含 gua/cardTheme(当日已定卦) |

## 6. POST /api/fortune/task-done(taskItem 扩展)

| # | 场景 | 预期 |
|---|---|---|
| T1 | 正常·带合法 taskItem | body {taskItem:"早睡"} 且属于当日 yiItems | 200 BlessResult;task_item 落库"早睡" |
| T2 | 错误·taskItem 不属于当日卡 | {taskItem:"飞天"} | 40000 |
| T3 | 兼容·缺省 body | 空 body | 行为与 v1.0 一致(+15,task_item 为 NULL) |
| T4 | 边界·重复完成 | 同日再调 | 40900 |
| T5 | 推导·离坎巽计数 | 多用户多日造数后查 bagua/status | 早睡/喝水按(用户,日期)去重计数;打电话按次计数 |

## 7. 小程序前端验收(交互路径走查)

| # | 路径 | 验收点 |
|---|---|---|
| U1 | 三 Tab 导航 | TabBar 恰 3 项:福签/解忧册(中部)/家;切换无白屏;rank 页深链仍可达 |
| U2 | 转罗盘 | 连续点击持续加速(振感反馈);停止后指针卦位高亮;翻卡卡面随 cardTheme 变化;「接福气」大按钮一键直抽 |
| U3 | 首页入口条 | 集福阵 chip 显示「已点亮 N/8 卦」并跳转阵图页;生日簿 chip 显示倒计时并跳生日页 |
| U4 | 解忧册 | 长按蓄力→松手翻页→内页文案/类型/页码/免责小字→「定」印章动效;再进同日显示同一条 |
| U5 | 册子的记忆 | 翻第 4 条时最早一条被裁剪(仅留 3);「办成了」后金色印章+「成了 N 件」;**存储仅本地**(抓包无相关上报) |
| U6 | 订阅授权 | 抽卡成功弹层 checkbox 默认勾选;拒绝后一切功能正常;接受后 notify/subscribe 收到 accepted:true |
| U7 | 家页合并 | 设置入口在右上角;提醒组展示「您每天最多收到 4 条提醒」;我家/家族榜子 Tab 切换数据正确;个人卡片信息齐全;集福阵/生日簿入口仍在 |
| U8 | 适老化走查 | 主按钮 ≥88rpx;正文 ≥34rpx;一屏一主按钮;全程零键盘输入路径 |
| U9 | 合规扫描 | 全部 wxml/js 文案违禁词零命中 |

## 8. 前后端一致性(Stage 6 机检项)

- 契约 G1 verify + 四栈 check 通过
- 小程序实际请求路径/字段 ↔ openapi.yaml paths/schemas 逐项比对零偏差
- 生成产物 drift 检测通过
