# 架构

## 总体架构

```
微信小程序 (miniprogram/, 原生框架)
        │  wx.cloud.callContainer(生产) / wx.request(本地联调)
        │  生产链路自动注入 X-WX-OPENID(平台验签,不可伪造)
        ▼
微信云托管容器 (backend/, Spring Boot + Java 21, 监听 80)
        │  JPA (cloud profile → 云托管 MySQL / 默认 → H2)
        ▼
云托管 MySQL(生产,MYSQL_ADDRESS 注入) / H2(本地与测试)
```

- 前端为原生微信小程序（非框架），3 个 Tab：福签页 / 我的家 / 家族榜
- 后端单服务，接口前缀 `/api`；本地端口 8080（`server.port=${PORT:8080}`），云托管容器 80
- 网络层双通道：`utils/request.js` 的 `USE_CLOUD` 开关（true=callContainer / false=本地 wx.request）
- `frontend/`（React + Vite）仅作为契约验证的演示端，不承载产品功能
- 登录身份：X-WX-OPENID(优先,生产) / body code(回退,本地联调) → 建用户 → Bearer token(即 userId)

## 后端分层

```
com.example.backend
├── controller/    # REST 接口，出入参用 DTO，与实体分离
├── service/       # 业务逻辑：福签抽取、福值结算、频控
├── repository/    # Spring Data JPA
├── entity/        # 数据库实体
└── config/        # CORS、Jackson、调度任务（每日福签生成/重置）
```

## 核心流程

1. **每日福签**：定时任务每日 0 点为每个家族生成当日福签（全家同签，等级按概率 70/25/5）；用户抽签 = 读取当日已生成的签
2. **福值结算**：行为产生福值事件 → 个人福值 + 家族福池同时累加；只加不减
3. **门牌解锁**：家族福池累计达标（500/2000/5000/20000）触发解锁事件，全员通知
4. **分享链路**：小程序卡片分享 → 新用户带家族邀请码进入 → 直接落地「我的家」页

## 部署与运行

- 本地后端：`cd backend && ./mvnw spring-boot:run`（默认 profile → H2 文件库）
- 测试：`src/test/resources` 独立配置 → H2 内存库 + create-drop，不污染本地数据
- 生产部署：微信云托管，代码仓库方式，构建目录 `backend/`，Dockerfile `backend/Dockerfile`，
  ENTRYPOINT 自带 `--spring.profiles.active=cloud`（MySQL 环境变量三件套缺失时 fail-fast）
- 部署后验证：`curl <服务域名>/api/ping` → `{"status":"ok"}`；
  运行时契约校验：harness `contract cli live <契约> --url <域名>/v3/api-docs`（本地联调用）
- ⚠️ 服务不得开启公网访问路径（X-WX-OPENID 信任前提）；springdoc 已在生产 profile 关闭
- 小程序：微信开发者工具导入 `miniprogram/`；切云端前确认 `request.js` 顶部环境 ID/服务名常量
- 契约：OpenAPI 契约由 fe-be-contract-harness 管理，代码生成与一致性校验以契约为准
