# fe_backend_mini

**念家时·家人生日提醒**——家庭纪念日管家 × 仪式感社交小程序。微信小程序（原生）+ Spring Boot 后端，契约驱动开发（fe-be-contract-harness），部署于微信云托管。

> 当前产品：**念家时**（2026-10 由「别点这个签」转型，PRD 见 `docs/PRD-念家时.md`）。
> 双引擎：日子管家（生日/纪念日/在一起，公农历换算，提前提醒）× 模板馆（感恩信 AI 代笔 / 相伴天数卡 / 时光胶囊 / 生日福卡，海报裂变，访客 3 心免注册解锁）。

## 目录结构

```
fe_backend_mini/
├── miniprogram-nianjiashi/  # 念家时小程序（当前产品前端，四 Tab）
├── miniprogram/             # 旧「别点这个签」前端（已停止迭代，保留作参考）
├── backend/                # Spring Boot + Java 21 + Maven（自带 mvnw）
├── api/openapi.yaml         # 接口契约（唯一事实源，v1.8.0，61 路由）
├── docs/                    # PRD、交互原型、需求 Spec、设计、测试用例、交付报告
├── frontend/                # React + Vite（早期演示用，与当前产品无关）
└── .agents/docs/            # Agent 知识库（架构/规范/领域模型）
```

## 启动方式

```bash
# 后端（端口 8080，本地 H2）
cd backend && ./mvnw spring-boot:run

# 前端：微信开发者工具导入 miniprogram-nianjiashi/
#   详情 → 本地设置 → 勾选「不校验合法域名」
#   utils/request.js 的 USE_CLOUD=false 走本地；部署后切 true 走云托管
```

## 开发流程（契约驱动）

接口以 `api/openapi.yaml` 为唯一事实源，由 [fe-be-contract-harness](../fe-be-contract-harness) 12 阶段流水线驱动：需求 Spec → 契约 → 双端实现 → 一致性校验 → PR。改接口先改契约，四栈校验 + 运行时 live 校验双门控。

```bash
# 契约校验
python3 ../fe-be-contract-harness/lib/contract/cli.py check api/openapi.yaml
# 运行时校验（后端启动后）
python3 ../fe-be-contract-harness/lib/contract/cli.py live api/openapi.yaml --url http://127.0.0.1:8080/v3/api-docs
# 后端测试（当前 173 条）
cd backend && ./mvnw test
```

## 测试与文档

- 提测用例：`docs/qa/testcases-v2.*.md`（三 change 共 137 条，P0 全覆盖）
- 交付报告：`docs/qa/delivery-report-v2.*.md`（12 阶段 × 3 change 全终态）
- 交互原型：`docs/prototype/nianjiashi-v2.1.html`

## 部署（微信云托管）

`backend/Dockerfile` 多阶段构建，`cloud` profile 连云托管 MySQL；部署验证 `/api/ping`。详细链路与踩坑（MySQL 严格模式拒绝只读事务写库、H2/MySQL 原生 ENUM 列扩值需 ALTER 迁移等）见 `.agents/docs/architecture.md`。
