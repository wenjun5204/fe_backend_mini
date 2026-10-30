# 架构

> 产品 = 念家时·排班纪念日（双引擎：日子管家 × 值班日历）；契约 v1.9.0（68 路由）；
> 本文档对齐 2026-10-30 代码事实（miniprogram-nianjiashi 三 Tab + backend 27 实体）。

## 总体架构

```
微信小程序 (miniprogram-nianjiashi/, 原生框架)
        │  wx.cloud.callContainer(生产) / wx.request(本地联调)
        │  生产链路自动注入 X-WX-OPENID(平台验签,不可伪造)
        ▼
微信云托管容器 (backend/, Spring Boot + Java 21, 监听 80)
        │  JPA (cloud profile → 云托管 MySQL / 默认 → H2)
        ▼
云托管 MySQL(生产,MYSQL_ADDRESS 注入) / H2(本地与测试)
```

- 前端为原生微信小程序（非框架），**3 个 Tab：日子（首页，含家人们入口）/ 值班（排班日历）/ 我的**；家人、模板馆为二级页（navigateTo）
- 页面全景（15 页）：days（日子首页）/ days-list / day-edit / day-detail（日子四件套）、duty（值班日历+三弹层）、family（家人空间）/ moment 相关、tpl（模板馆）、letter-edit / letter-preview（感恩信）、days-card-edit / birthday-card-edit（卡片制作）、capsule-edit / capsule-open（时光胶囊）、mycards（我的卡片墙）、visitor（访客落地，免鉴权）
- 旧前端 `miniprogram/`（别点这个签）已停止迭代，仅作参考；`frontend/`（React）为早期演示端
- 后端单服务，接口前缀 `/api`；本地端口 8080（`server.port=${PORT:8080}`），云托管容器 80
- 网络层双通道：`utils/request.js` 的 `USE_CLOUD` 开关（true=callContainer / false=本地 wx.request）；存储 key 一律 `njs_` 前缀与旧前端隔离
- 登录身份：X-WX-OPENID(优先,生产) / body code(回退,本地联调) → 建用户 → Bearer token(即 userId)

## 后端分层

```
com.example.backend
├── controller/    # ApiController(单文件聚合全部 REST 路由,出入参 DTO 与实体分离)
├── service/       # 业务逻辑(27 个):日子/值班/家族/卡片/胶囊/照片/瞬间/节日/访客/通知账本 等
├── repository/    # Spring Data JPA
├── entity/        # 27 个数据库实体
└── config/        # AuthInterceptor(登录态)/CloudEnvPostProcessor(云环境变量)/WebConfig
```

## 核心流程（双引擎）

**日子引擎（v2.0+）**

1. **日子记录**：DayRecord（BIRTHDAY/ANNIVERSARY/TOGETHER 三类，公农历换算 `/api/calendar/convert`）+ DayReminder（按人提醒配置，提前天数）
2. **到期计算**：服务端统一算 daysUntil（农历生日按公历落点对齐），`/api/days/reminder-due` 供提醒调度
3. **周年称谓**（v2.2）：ANNIVERSARY 按 year 字段输出称谓（1 纸婚…60 钻石婚，静态库）

**值班引擎（v2.3）**

4. **排班记录**：DutyShift（用户×日期一条，uk 物理保证）；单日编辑 upsert / 批量排班覆盖写入 / 范围清除批量撤销
5. **周期规则**：DutyRule 单条生效（daily/on1off1/on2off2/emnr 序列循环 / weekday 只填周五）；重新应用整行覆盖，跨度 >365 天截断
6. **下次值班**：从明天起 60 天窗口扫最近非「休」班次日
7. **值班提醒**：DutyRemind 存时机配置（advance_1d/same_day_08/advance_2h）；真实推送不做（NotifyService 额度账本口径，部署联调演进项）

**表达层（模板馆引擎 v2.1 + 家人空间 v2.2）**

8. **卡片制作**：MadeCard（LETTER/DAYS_CARD/CAPSULE/BIRTHDAY_CARD 四类）+ 感恩信 AI 逐句生成（额度制）
9. **时光胶囊**：封存（可附照片）→ 到期开箱（幂等）
10. **照片管道**：base64 JSON 上传（≤500KB，BLOB 存储）→ 高熵 photoId capability URL 免鉴权回图
11. **家人瞬间**：照片+一句话时间线，真好（限 1 次）/收藏进日子（生日福卡素材池）
12. **节日预约**：FestivalReservation 幂等登记 + 0-7 天到期清单（模板馆横滑数据源）
13. **访客链路**：分享卡扫码落地 → 3 心解锁隐藏内容（免鉴权组 `/api/visitor/*`）

## 部署与运行

- 本地后端：`cd backend && ./mvnw spring-boot:run`（默认 profile → H2 文件库）
- 测试：`src/test/resources` 独立配置 → H2 内存库 + create-drop；当前 204 条全绿
- 生产部署：微信云托管，代码仓库方式，构建目录 `backend/`，Dockerfile `backend/Dockerfile`，
  ENTRYPOINT 自带 `--spring.profiles.active=cloud`（MySQL 环境变量三件套缺失时 fail-fast）
- 部署后验证：`curl <服务域名>/api/ping` → `{"status":"ok"}`；
  运行时契约校验：harness `contract cli live <契约> --url <域名>/v3/api-docs`（本地联调用；
  Pendant/PhotoMimeType 两项 wire 等价豁免见各 consistency-report）
- ⚠️ 服务不得开启公网访问路径（X-WX-OPENID 信任前提）；springdoc 已在生产 profile 关闭
- 小程序：微信开发者工具导入 `miniprogram-nianjiashi/`；切云端前确认 `utils/request.js` 顶部环境 ID/服务名常量
- 契约：OpenAPI 契约（`api/openapi.yaml`，v1.9.0）由 fe-be-contract-harness 管理（12 阶段流水线，
  状态文件 `.harness-run/state-njs-*.json`），改接口先改契约再走 Stage 2 checkpoint

## 已知踩坑（不可违背的教训）

1. MySQL 严格模式拒绝只读事务内写库（H2 不拒绝）——`@Transactional(readOnly=true)` 方法内零写库
2. H2/MySQL 原生 ENUM 列扩值需 ALTER 迁移——枚举一律存字符串 + service 层校验值域
3. 兜底异常处理器必须打全量堆栈，禁止吞异常（前端只见 50000，真相在日志）
4. wx.request 直连本地需勾选「不校验合法域名」；本地通道固定测试身份防清缓存后「数据消失」
5. 全局 app.wxss 与页面 wxss 类名合并生效——页面级组件类名须加页面前缀（duty- 冲突事故 2026-10-30）
