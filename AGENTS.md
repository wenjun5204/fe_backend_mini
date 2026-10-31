# AGENTS.md — 念家时 项目 Agent 规范

> 本文件是所有 AI Agent / 开发者在本仓库工作的**第一入口**。
> 项目 = 「念家时·排班纪念日」（原名别点这个签/全家福·接福气，面向大众用户的家庭互动产品）：微信小程序 + Spring Boot 后端，部署于微信云托管。
> **迭代方向（2026-10 起）**：双引擎 = 日子管家（纪念日/生日，`docs/PRD-念家时.md`）× 值班日历（个人排班与提醒，`docs/prototype/PRD-值班排班.md`）；后续主要迭代「纪念日 + 排班」功能，模板馆/家人空间为辅助能力。
> 相关文档：`docs/PRD-念家时.md`、`docs/prototype/PRD-值班排班.md`、`.agents/docs/`（架构/规范/领域模型）。

## ⚠️ 合规红线（最高优先级，任何代码/文案/接口设计不得违背）

本产品面向大众用户（不区分年龄段），微信审核 + 伦理边界是**一票否决项**：

1. **禁迷信词**：所有用户可见文案禁止出现「运势 / 吉凶 / 求签 / 算命 / 占卜 / 八字 / 塔罗 / 改运 / 挡灾 / 化解 / 开光 / 大师 / 卦象 / 卦运 / 宜忌 / 生门 / 吉位 / 凶位 / 能成 / 不行 / 会有 / 注定 / 天意 / 预示 / 破财 / 有灾」（v1.5 全量清单，含结果断言句式）。统一使用「福卡 / 福签 / 祝福 / 平安 / 福气 / 今日宜 / 点亮 / 集福 / 宽心 / 接福」。
2. **只宜不忌**：黄历式福签只输出正面行为建议（宜散步、宜多喝水），**永远不出现「忌」和凶吉预测**。
3. **不预测**：福签等级（平安/如意/鸿福）只是稀有度玩法，不得与任何"命理"挂钩，不得暗示能影响现实运势。
4. **软引导分享**：转发必须是**奖励制**（+10 福值），不得出现「不转发福签不生效 / 不转发家人会有事」的强制或恐吓逻辑。这是微信「诱导分享」红线的分界。
5. **无医疗暗示**：微任务只用生活化表述（喝水/散步/泡脚/早睡），禁止「降血压 / 降血糖 / 治疗 / 理疗 / 排毒」等词。
6. **通知频控**：订阅消息每人每日 ≤ 4 条；「催福」对同一人每日 ≤ 3 次。
7. **道德底线**：可以放大"惦记家人"的情感，**绝不制造恐惧**。文案负面清单见 `.agents/docs/conventions.md`。
8. **隐私**：福值/接福状态仅家族内可见；好友榜默认仅微信好友可见；提供「隐身榜外」开关。

## 项目结构与职责

```
fe_backend_mini/
├── miniprogram-nianjiashi/  # 念家时小程序（产品主前端,三 Tab:日子/值班/我的;家人/模板馆为二级页）
├── miniprogram/             # 旧「别点这个签」前端（已停止迭代,仅作参考）
├── backend/                 # Spring Boot + Java 21 + Maven（自带 mvnw）
├── frontend/                # React + Vite（早期演示用,与当前产品无关）
├── api/openapi.yaml         # 接口契约（唯一事实源,v1.9.0）
├── docs/                    # PRD、交互原型、需求 Spec、设计、测试用例、交付报告
└── .agents/docs/            # Agent 知识库（architecture / conventions / domain-model）
```

- 产品主前端是 `miniprogram-nianjiashi/`，不是 `miniprogram/` 或 `frontend/`
- 后端接口统一前缀 `/api`，本地端口 8080；云托管容器监听 80（`server.port=${PORT:8080}`）
- 数据库：本地开发/测试用 H2（测试为内存库）；生产走微信云托管 MySQL（`cloud` profile，环境变量 `MYSQL_ADDRESS`/`MYSQL_USERNAME`/`MYSQL_PASSWORD`，由 `CloudEnvPostProcessor` 拆分注入，缺失时 fail-fast）

## 部署与鉴权（双形态：微信云托管 + 自部署服务器）

- `backend/Dockerfile`：多阶段构建，`ENV PORT=80`，ENTRYPOINT 激活 `--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-cloud}`（云托管构建默认 cloud，自部署 compose 指定 selfdeploy）
- **信任边界由 profile 决定**（安全默认值内嵌在 profile 文件，不靠运维记忆）：
  - `cloud`（云托管）：`auth.trust-wx-header=true`——信任平台注入的 `X-WX-OPENID`；**前提仍是服务不得开启公网访问路径**；token 默认 legacy 明文 userId（`AUTH_TOKEN_SECRET` 不配即零影响）
  - `selfdeploy`（自部署公网）：`trust=false`/`dev-code=false` 写死在 profile 文件——`X-WX-OPENID` 可被任意客户端伪造，身份只能来自 `wx.login` code → code2Session（`WeChatCode2SessionClient`）；token 强制 HMAC 签名（`TokenService`，明文 userId 在公网可被 `Bearer 1..N` 遍历冒充）；三要素（`WX_APP_ID`/`WX_APP_SECRET`/`AUTH_TOKEN_SECRET`）缺失由 `AuthModeGuard` 启动即死
  - 默认/测试 profile：`trust=false` + `dev-code=true`（code 即 openid，本地/集成测试依赖此回退——test resources 会整体遮蔽主配置，两处都要写）
- 小程序网络层三通道：`miniprogram-nianjiashi/utils/request.js` 顶部 `API_MODE`（`'auto'`=develop→local/trial+release→cloud；`'selfdeploy'`=发版连自部署域名）；401 自动清 token 重登重放一次；图片回显 `photo.js` 按 `CHANNEL` 取基址
- 部署后验证：`curl <服务域名>/api/ping` 返回 `{"status":"ok"}`；自部署另有防伪造负面用例清单（`docs/deploy/selfdeploy-deployment.md` §2）；契约校验用 harness 的 `contract cli live`（`/api/ping` 已默认豁免）
- **踩坑教训**：MySQL 严格拒绝只读事务内写库（H2 不拒绝，本地测试发现不了）——`@Transactional(readOnly=true)` 的方法内不得调用任何兜底 save；兑底异常处理器必须打全量堆栈日志，禁止吞异常

## 开发规范摘要

- **契约驱动**：接口以 OpenAPI 契约为唯一事实源（SSOT），由 fe-be-contract-harness 驱动生成与校验；改接口先改契约，再走 Stage 2 checkpoint
- **简洁 UI 硬规范**（面向大众用户，保留大字号可读性）：正文 ≥ 17pt、核心信息 ≥ 22pt、主按钮高 ≥ 88rpx、一屏一个主按钮、任何功能 ≤ 3 次点击、零键盘输入优先、口语化文案用「您」、禁英文术语
- **视觉规范（v2 暖白体系，实际生效于 `app.wxss` 视觉令牌）**：暖白底 `#fbfaf7`、墨字 `#1c1c28`、主色霞橘渐变 `#ff7a59→#ff4d6d`、辅色雾紫 `#6c5ce7`/青金 `#12b2a3`；动画 ≤ 2.5 秒且可跳过
- **视觉风格关键词：清新、大气、克制**——做减法不做加法：细线（≤2rpx）代替重边框、浅底色块代替描边填充、分隔线列表代替卡片堆叠、一页只允许一个视觉焦点（渐变主按钮或深色 hero 卡）；选中态用细彩线+浅色底（如玫红 `4rpx` 下划线/`6%` 玫红底），禁大面积高饱和填充；间距宁松勿紧（区块 ≥24rpx、列表行 ≥26rpx 内边距）；禁止 emoji 滥用（功能性图标位每屏 ≤3 处）
- **按钮组一律左右（横排）布局**：所有类 radio/segmented 的按钮组（选项 chips、视图切换、提醒时机等）必须横排——容器挂 `seg`/`pick-chips` 等 flex 布局类，子项用 `flex:1` 或 grid 等宽分列，**严禁竖排一行一个**。根因提醒：微信原生 `button` 默认 184px 固定宽 + auto margin，容器不是 flex 时按钮必然叠成一列（`.chip`/`seg-btn` 已在 `app.wxss` 重置，但容器类忘了挂就会复发）；新增任何按钮组时先确认容器有 `display:flex`
- **日历组件风格全局统一**（基准：`pages/duty/duty.wxss` 月历）：周一首、月标题「YYYY 年 M 月」、裸文字翻月箭头、白卡容器、今日格玫红细线、有内容格浅色底，禁墨色/渐变整体填充格子；格子内业务内容可不同（值班=班次标签，日子=事件圆点），骨架与交互态必须同源
- **展示型长文本/列表行的可点击区域用 `view + bindtap`，不塞进原生 `button`**：button 适合短标签式选项与操作；长文本进 button 会被 184px 默认宽 + `::after` 描边拖丑（历史踩坑：祝福语逐行 button）。先定排版形态再选承载元素；分享（`open-type="share"`）/表单提交/原生按钮态仍用 button
- **可跳转入口必须视觉可辨**：section 头可点击项挂 `section-more link`（玫红胶囊），纯提示语保持灰字弱化；禁止可点击元素做成灰字
- **福值规则**：福值只涨不跌，**永不惩罚用户**（扣分 = 用户流失）
- 后端分层：controller → service → repository，DTO 与实体分离，接口出入参以契约为准
- 提交规范：`feat/fix/docs/refactor` 前缀 + 中文描述
- 隐私合规：实际仅采集 openid（静默注入）与业务数据存储，无位置/相册/头像等高敏接口；接入新采集能力前必须同步更新微信后台「用户隐私保护指引」

## 详细知识

- 架构：`.agents/docs/architecture.md`
- 编码与文案规范（含合规词表）：`.agents/docs/conventions.md`
- 领域模型（日子/值班/家族/卡片）：`.agents/docs/domain-model.md`
