# 自部署服务器部署指南(双形态之一)

> 适用于「念家时」后端从微信云托管迁移/并行到自购服务器(轻量云 + 域名)。
> 云托管部署不受影响(见文末「云托管回归」);两形态可长期并存,小程序端按发版切换通道。

## 架构差异速览

| | 云托管(现网) | 自部署(本文) |
|---|---|---|
| 鉴权 | 平台注入 X-WX-OPENID(不可伪造) | wx.login code → code2Session |
| token | 明文 userId(legacy,secret 空) | HMAC 签名(secret 必填) |
| 前端通道 | wx.cloud.callContainer | wx.request + HTTPS 域名 |
| 安全前提 | 服务不得开公网路径 | 天然公网,信任模型已重设计 |

## 0. 前置 checklist(部署门禁)

- [ ] **域名 ICP 备案**(最大外部阻塞,1-3 周):小程序 wx.request 生产强制 HTTPS+已备案域名;
      备案期间可用「体验版 + 手机勾选调试模式 + http://IP:8080」先行验证全链路
- [ ] HTTPS 证书:域名解析到服务器后 `certbot --nginx -d api.你的域名.com`(Let's Encrypt 免费)
- [ ] 微信小程序后台:
  - [ ] 「开发管理→开发设置」取 AppID/AppSecret
  - [ ] 「开发管理→服务器域名→request 合法域名」加入 `https://api.你的域名.com`
  - [ ] 「用户隐私保护指引」:采集方式由「云托管静默注入 openid」改为「wx.login 换取 openid」(同步更新)
- [ ] 服务器:Docker + Docker Compose 已装;安全组只放 80/443(8080 不对公网直接暴露)

## 1. 服务器部署

```bash
# 服务器上(需 git clone 本仓库)
cd fe_backend_mini
cp .env.example .env
vi .env   # 填 MYSQL_PASSWORD/MYSQL_ROOT_PASSWORD/WX_APP_ID/WX_APP_SECRET/AUTH_TOKEN_SECRET
# AUTH_TOKEN_SECRET 生成:openssl rand -hex 32

docker compose up -d --build
docker compose logs backend | grep -i started   # 等待启动完成
```

AuthModeGuard 会在启动时校验三要素(WX_APP_ID/WX_APP_SECRET/AUTH_TOKEN_SECRET),缺一即启动失败——这是刻意设计,公网部署严禁弱配置上线。

## 2. 部署后验证清单

```bash
# ① 探活
curl https://api.你的域名.com/api/ping
# 期望 {"status":"ok"}

# ② 防伪造负面用例(核心安全断言)
curl -s -X POST https://api.你的域名.com/api/auth/login \
  -H 'Content-Type: application/json' -H 'X-WX-OPENID: evil-attacker' -d '{}'
# 期望 {"code":"40000",...} —— 伪造头被彻底忽略(trust=false)

# ③ code 登录链路(需要真实小程序 code:开发者工具 Network 里拷一个,5 分钟内有效)
curl -s -X POST https://api.你的域名.com/api/auth/login \
  -H 'Content-Type: application/json' -d '{"code":"081AbCdEf..."}'
# 期望 {"token":"1.{userId}.{hmac}","userId":...,...} —— token 是 HMAC 形态而非纯数字

# ④ 明文 token 遍历冒充被拒
curl -s https://api.你的域名.com/api/days -H 'Authorization: Bearer 2'
# 期望 {"code":"40100",...}
```

## 3. 小程序端切换

`miniprogram-nianjiashi/utils/request.js` 顶部:

```js
const API_MODE = 'selfdeploy'                       // 从 'auto' 切换
const SELFDEPLOY_BASE_URL = 'https://api.你的域名.com'  // 备案+证书就绪后填
```

上传体验版 → 真机全链路冒烟(登录/日子/值班/家人/传照片/图片回显)→ 提审发布。

回滚 = 改回 `API_MODE = 'auto'` 再发一版,通道自动回落云托管(数据不迁移则看不到自部署上产生的数据,注意双形态数据是两套独立库)。

## 4. 运维要点

- **AUTH_TOKEN_SECRET 轮换** = 全员 token 失效,前端 401 自动重登兜底(用户无感,仅 wx.login 一次)
- **WX_APP_SECRET 泄露**立即在 MP 后台重置并同步更新 .env → `docker compose up -d backend`
- 数据备份:`docker compose exec mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD quanjiafu > backup.sql`(建议 crontab 每日)
- 日志:`docker compose logs -f backend`;code2Session 失败会以 errcode 概要记 WARN(appid/secret 配错常见表现 40013/40125)

## 5. 可选:数据迁移(云托管 MySQL → 自部署)

两库按 openid 唯一索引天然归并,同 openid 用户自动是同一人:

```bash
# 云托管控制台导出 SQL → 服务器导入(仅首次切换历史数据)
docker exec -i <mysql容器> mysql -u root -p$MYSQL_ROOT_PASSWORD quanjiafu < cloud-dump.sql
```

迁移后所有旧 token 失效(密钥不同),用户下次打开小程序自动重登,无感。

## 6. 云托管回归(改造后必须执行)

后端镜像统一,云托管重新构建部署(不加任何新环境变量):

```bash
curl <云托管域名>/api/ping                      # {"status":"ok"}
# 小程序(API_MODE='auto')体验版冒烟:登录/日子/值班/分享全链路
# 行为与改造前完全一致(trust 头 + legacy 明文 token,存量配置零迁移)
```
