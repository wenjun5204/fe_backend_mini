# 念家时(日子管家)· 前端

微信小程序原生目录(fe-be-contract-harness Stage 5B 产出,Change 1:日子 Tab 全量页面)。

## AppID 说明

- **AppID 沿用现有小程序 `wx5e93c2b8cd8ef2bc`,走改名流程**(改名提审属运营动作,不在代码仓内完成)。
- 旧前端 `../miniprogram/`(别点这个签)继续运行,本目录不迁移其任何样式代码。

## 与旧目录的隔离(同 AppID 共用 wx storage)

本地存储 key 一律 `njs_` 前缀:`njs_token` / `njs_user` / `njs_openid`。
新旧小程序共用同一 AppID 即共用同一 storage,沿用旧 `qjf_*` key 会互相覆盖登录态。

## 目录结构

```
miniprogram-nianjiashi/
├── app.js / app.json / app.wxss     # 三 Tab:日子 / 模板馆 / 我的;视觉令牌见 app.wxss
├── utils/
│   ├── request.js                   # 双通道网络层(云托管 / 本地直连),照抄旧实现逻辑、独立常量
│   ├── api-days.js                  # days 契约 7 端点 + 复用 POST /api/calendar/convert
│   └── days-data.js                 # 关系标签 / 表情映射 / 祝福语库 / 展示视图组装
└── pages/
    ├── days/days                    # 日子首页:问候语+最近日子卡+列表+空状态引导+下拉刷新
    ├── day-edit/day-edit            # 记一个日子(创建/编辑)
    ├── days-list/days-list          # 全部日子:时间线 ⇄ 日历双视图+当日事件弹层
    ├── day-detail/day-detail        # 详情:倒计时/祝福语复制/提醒开关/删除
    ├── tpl/tpl                      # 模板馆占位页(Change 2 上线)
    └── me/me                        # 我的占位页(基本资料)
```

## 接口契约

以 `../../api/openapi.yaml` tag=days 的 7 个端点为唯一事实源;
字段名对齐 `.harness/gen/react/src/types/api.ts` 中 Day 系类型锚点。
农历落点换算全部由后端完成(DayRecord 自带 nextDate/daysUntil/togetherDays 计算字段),
录入页的实时换算预览复用既有 `POST /api/calendar/convert`。

## 后端通道切换

`utils/request.js` 顶部常量:`USE_CLOUD`(true=微信云托管 callContainer / false=本地 wx.request 直连)、
`CLOUD_ENV`、`SERVICE_NAME`(与旧目录指向同一后端服务)。
