# 全家福·接福气 MVP 技术方案（Stage 3 产出）

## 1. 仓库识别（confirmed_repos）

| 仓库 | 角色 | 技术栈 | 说明 |
|---|---|---|---|
| `fe_backend_mini`（本仓库） | 唯一业务仓库 | 小程序(原生) + Spring Boot(Java 21) + H2 | 单仓库多模块，无跨仓库依赖 |

- 后端：`backend/`，Maven 自带 mvnw，接口前缀 `/api`，端口 8080
- 产品前端：`miniprogram/`（原生微信小程序，3 Tab：福签/我的家/家族榜）
- 契约演示端：`frontend/`（React + Vite），仅用于跨栈一致性校验，不承载产品功能
- 契约 SSOT：`api/openapi.yaml`，由 fe-be-contract-harness 管理生成与漂移检测

## 2. 后端模块设计（backend/）

```
com.example.backend
├── controller/ApiController        # 13 个契约路由,出入参 DTO
├── service/
│   ├── AuthService                 # 登录:MVP code 即 openid,签发 token(=userId)
│   ├── FamilyService               # 建家/进家/我的家;成员上限8;每人限1家
│   ├── FortuneService              # 福签:当日生成(全家同签)/抽签/分享/任务
│   ├── BlessService                # 福值:所有变更走 BlessEvent,只增不减;门牌解锁
│   ├── InteractService             # 添福(1次/人/对人/天)/催福(3次/人/对人/天)
│   └── RankService                 # 家内榜(本周)/好友家族榜(互关家族模拟)
├── repository/                     # Spring Data JPA
├── entity/                         # 6 实体(见 .agents/docs/domain-model.md)
└── config/                         # CORS(小程序无跨域问题,保留Vite演示端)/每日任务
```

### 关键实现决策

1. **每日福签生成**：`@Scheduled(cron="0 0 0 * * *")` 为每个家族生成当日福签；`drawFortune` 时若当日无签则即时补生成（兜底，避免定时任务失败阻塞）。等级概率 70/25/5，「今日宜」从 6 项生活化任务库抽 2-3 项，永不出现「忌」。
2. **福值变更唯一路径**：`BlessService.grant(userId, familyId, type)` → 写 `BlessEvent` → 同步累加 `FamilyMember.personalBless` 与 `Family.totalBless` → 检查门牌升级。事务内完成，失败回滚。
3. **频控**：`FortuneDrawRecord`（抽/分享/任务）按 user+date 唯一约束防重；添福/催福按 user+target+date 计数。超限返回 40300。
4. **连续天数 streakDays**：抽签时对比上次抽签日期，连续则 +1，断签清零（福值不清零，只熄火焰标识）。
5. **错误结构**：`{code, message}`，HTTP 状态 200 + 业务码（小程序端统一处理简单）；message 全部过合规词表。
6. **鉴权**：MVP 用 `Authorization: Bearer {token}`，token 即 userId（演示态），内测前替换为 openid→session。`/api/invite/info` 免鉴权。

### 数据库（H2 文件模式）

6 张表：`user` / `family` / `family_member` / `daily_fortune_card` / `bless_event` / `fortune_draw_record`。唯一约束：`user.openid`、`family.invite_code`、`(family_id,date)` on card、`(user_id,date)` on draw_record。

## 3. 小程序设计（miniprogram/）

```
pages/
├── card/      # Tab1 福签:签筒摇卡(含点按钮等效)→黄历福签卡→分享/完成任务
├── home/      # Tab2 我的家:福值池+门牌+成员墙(添福/催福)+邀请
├── rank/      # Tab3 家族榜:家内榜/好友家族榜切换+追赶文案
utils/
├── request.js # 统一网络层,自动带 token,错误 toast 过合规词表
└── format.js  # 日期/福值格式化
```

- 分享进入：`onLoad` 解析 `inviteCode` 参数 → 未登录先 login → `joinFamily` → 跳转 Tab2
- 视觉规范按 `.agents/docs/conventions.md`：中国红 #b01f24 / 描金 #ffd76e / 米黄 #fff8ec / 福签大字楷体
- 动画 ≤ 2.5s 可跳过；摇一摇与按钮等效

## 4. 契约对接与生成区约定

- harness 生成的骨架（java/react）落 `.harness/gen/`，仅作一致性参照，**不直接拷贝进源码**；业务实现按契约手写在 `backend/src`，DTO 字段与契约模型一一对应
- 漂移检测：`python3 lib/contract/cli.py drift api/openapi.yaml --stack java --gen-dir .harness/gen/java` 在 Stage 6 执行
- 改接口流程：改 `api/openapi.yaml` → re-run check/verify → 回到 Stage 2 checkpoint 重签

## 5. 测试策略（Stage 8 展开）

- 后端单测：BlessService(只增不减/门牌升级)、InteractService(频控)、FortuneService(同签/等级概率/宜事项无禁忌词)
- 合规扫描：测试用例中包含对 blessText/yiItems 输出的禁用词断言（词表来自 conventions.md）
- 前端：小程序手工验证清单（3 Tab 主流程 + 分享链路）

## 6. 明确不做（MVP 边界，来自需求 Spec）

支付、老照片修复、自定义福卡、私信、同城榜、微信步数同步、真实订阅消息推送（接口预留 sendNotification 内部方法，模板配置后启用）。
