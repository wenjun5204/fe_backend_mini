# 前后端一致性校验报告 v1.5 —— Stage 6 产出

> 校验对象:`api/openapi.yaml` v1.5.0(契约 SSOT)↔ backend(Spring Boot)↔ miniprogram(原生小程序)。
> 分支:feature/v1.5-bagua-jieyou。

## 1. 契约生成门(G1)与漂移检测

| 检查 | 命令 | 结果 |
|---|---|---|
| G1 生成门 | `cli.py verify --stack java` | ✅ ok=true, idempotent=true, routes_ok, models_ok |
| 跨栈一致性 | `cli.py check`(react/vue/node/java) | ✅ 四栈全部 routes_ok=true, models_ok=true |
| 漂移-java | `cli.py drift --gen-dir gen/java` | ✅ routes_drift=false, models_drift=false |
| 漂移-react | `cli.py drift --gen-dir gen/react` | ✅ routes_drift=false, models_drift=false |

## 2. 小程序 ↔ 契约 路径/字段比对

- 小程序实际请求 24 个路径(含动态 id 拼接)与契约 21 条 path 逐一对照:**零偏差**。
  - 前端使用的全部路径均存在于契约(初扫两处差异为正则对连字符/模板串的截断假象,已逐行核实)。
- v1.5 新增调用全部就位:GET /api/bagua/status、POST /api/bagua/celebrate、GET /api/jieyou/quote、POST /api/notify/subscribe、GET /api/notify/status、POST /api/fortune/task-done(可选体 {taskItem})。
- FortuneCard 响应消费:gua(罗盘卦位高亮+卡面 8 套渐变映射)、cardTheme(卡面主题名)均已接线。

## 3. 后端 ↔ 契约

- 5 条新路由在 ApiController 落地,请求/响应 DTO 与契约 schema 一致(5A 报告零字段偏差)。
- 错误码复用 v1.0 五码体系;契约未定义分支仅 1 处:未圆满调 celebrate 返回 40900(防绕过前端直接落里程碑的保护,语义合理)。

## 4. 合规词表全量扫描

- 扫描范围:miniprogram 全部页面文案、backend 全部用户可见 message、JieyouQuoteLib 120 条。
- 词表:conventions.md 基础表 + v1.5 追加 14 词。
- 结果:**零命中**。
  - 修复记录:解忧册免责声明「本册只宽心,不问吉凶」含违禁词「吉凶」,已全链路(wxml/PRD/需求文档/设计文档/原型)统一改为「本册只宽心,只陪您拿主意」。
  - 后端另有三层机器断言(启动期/测试期/独立 grep),120 条文案零命中。

## 5. 实测验证汇总

- 后端:`./mvnw test` 12 套件 59 用例全绿;本地冒烟走通建家→抽卡(全家同卦 gua=XUN)→task-done 三态→八卦 8 卦口径→celebrate 保护→解忧册幂等→订阅额度账本。
- 小程序:11 个 js 语法全过、10 个 json 解析全过、6 页面四件套齐全、tabBar 3 项合法。
- WXML 结构:4 个改写/新增页面(card/jieyou/bagua/home)的 wx:for 与条件指令同节点冲突清零、标签配对平衡;微信开发者工具编译报错 `Bad attr 'wx:else'`(card.wxml)已修复(block 包裹分离条件与循环指令)。

## 6. 结论

**一致性通过**,可进入提交与测试用例终稿阶段。

遗留联调项(不阻断):
1. NOTIFY_TEMPLATE_ID 为占位,需替换微信后台真实模板 ID(占位期按 accepted=false 静默登记,功能不受影响)。
2. task_item 校验依赖前端透传 yiItems 原文,前后端文案池已对齐(FortuneTextLib 27 条)。
3. 真机需确认订阅弹窗与分享面板排队展示顺序。
