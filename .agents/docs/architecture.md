# 架构

## 总体架构

```
微信小程序 (miniprogram/, 原生框架)
        │  HTTPS / JSON
        ▼
Spring Boot 后端 (backend/, Java 21 + Maven)
        │  JPA
        ▼
H2 数据库（MVP，文件模式）→ 预留 MySQL
```

- 前端为原生微信小程序（非框架），3 个 Tab：福签页 / 我的家 / 家族榜
- 后端单服务，接口前缀 `/api`，端口 8080
- `frontend/`（React + Vite）仅作为契约验证的演示端，不承载产品功能

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

- 后端：`cd backend && ./mvnw spring-boot:run`
- 小程序：微信开发者工具导入 `miniprogram/`
- 契约：OpenAPI 契约由 fe-be-contract-harness 管理，代码生成与一致性校验以契约为准
