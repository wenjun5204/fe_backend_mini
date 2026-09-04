# 微信小程序客户端

原生微信小程序，对接本仓库的 Spring Boot 后端（`/api/hello` 联调示例）。

## 目录

```
miniprogram/
├── project.config.json   # 开发者工具项目配置（AppID 在这里填）
├── app.json / app.js / app.wxss
├── utils/request.js      # wx.request 封装，BASE_URL 在此配置
└── pages/index/          # 示例页：请求后端接口
```

## 第一步：本地预览（今天就能做）

1. 安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)（稳定版）
2. 打开开发者工具 →「导入项目」→ 选择本 `miniprogram/` 目录
3. AppID 先使用测试号：点「测试号」即可（或在 `project.config.json` 的 `appid` 填 `touristappid`）
4. 启动后端：`cd backend && ./mvnw spring-boot:run`
5. 模拟器里点「请求后端」按钮，能显示后端返回即联调成功
   - 若请求失败，确认右上角「详情 → 本地设置」已勾选「不校验合法域名…」

## 第二步：注册正式 AppID（只有你能做）

1. 访问 https://mp.weixin.qq.com 注册小程序（个人主体免费、需实名）
2. 登录后「开发管理 → 开发设置」里复制 AppID
3. 替换 `project.config.json` 中的 `"appid": "touristappid"`

## 第三步：上线发布（需要正式环境）

| 事项 | 说明 | 谁来做 |
|---|---|---|
| 后端部署到公网 | HTTPS + ICP 备案域名，例如 `https://api.yourdomain.com` | 你（我也可以帮写部署脚本/Dockerfile） |
| 合法域名 | 小程序后台「开发管理 → 开发设置 → 服务器域名」添加 request 域名 | 你 |
| 改 BASE_URL | `utils/request.js` 里改成正式 HTTPS 域名 | 我（后端上线后可代改） |
| 上传代码 | 开发者工具点「上传」，或配置 miniprogram-ci 用命令行上传 | 你扫码 / 我可配脚本 |
| 提交审核发布 | 小程序后台「版本管理」提交审核 | 你 |

> 小程序正式环境**不允许**请求 `http://` 或非备案 IP，所以联调用本机，
> 上线必须换 HTTPS 域名。
