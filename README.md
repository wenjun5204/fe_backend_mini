# fe_backend_mini

前后端一体的示例项目：React + Vite 前端，Spring Boot (Maven) 后端。

## 目录结构

```
fe_backend_mini/
├── frontend/   # React + Vite
└── backend/    # Spring Boot + Maven (自带 mvnw，无需安装 Maven)
```

## 启动方式

### 1. 启动后端（端口 8080）

```bash
cd backend
./mvnw spring-boot:run
```

> Windows 下使用 `mvnw.cmd spring-boot:run`

### 2. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

打开 http://localhost:5173 ，页面会通过 Vite 代理请求 `http://localhost:8080/api/hello` 并显示后端返回的数据。

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | React 19、Vite |
| 后端 | Spring Boot、Java 21、Maven |

## 微信小程序客户端

`miniprogram/` 目录是一个原生微信小程序，同样对接本仓库的后端。

```bash
# 启动后端（端口 8080）
cd backend && ./mvnw spring-boot:run
```

用[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)导入 `miniprogram/` 目录即可预览；完整的上线步骤见 [miniprogram/README.md](miniprogram/README.md)。
