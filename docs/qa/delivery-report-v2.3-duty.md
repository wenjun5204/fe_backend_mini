# 交付报告 · 念家时 v2.3 值班日历（Change 4）

> change_id：`念家时-v2.3-值班日历` | 契约 v1.8.0 → v1.9.0 | harness 状态文件：`.harness-run/state-njs-duty.json`
> 交付时间：2026-10-30 | PR：https://github.com/wenjun5204/fe_backend_mini/pull/9（分支 `feature/nianjiashi-duty-mvp`）

## 一、交付内容

**「值班」第 5 Tab——个人视角值班记录与提醒工具（PRD §5.1–5.6 P0 全量）**

| 模块 | 内容 |
|---|---|
| TabBar | 4→5 项：日子/家人/**值班**/模板馆/我的；值班页主包 `pages/duty/duty`（微信 tab 页禁分包，主包 516K 体积无忧） |
| 今日状态卡 | 今日班次（早班/休班😴/无排班）+ 下次值班 60 天倒计时（从明天起、休班跳过、今天不参与）+ 🔔提醒入口 |
| 月历视图 | 周一首日 42 格、班次色块单字标签（早玫红#ff4d6d/中橙#e8943a/夜藏蓝#3d4a5c/休灰#c8cdd3）、今日描边、按月懒加载+缓存+局部重试 |
| 单日编辑弹层 | 四班次点选即保存（upsert）、「清除这一天」（幂等、不动规则本体）、批量排班引导文案 |
| 批量排班弹层 | 日期 picker（默认下月整月）+ 5 预设规则（每天早班/做一休一/做二休二/早中夜休/每周五）、范围内已有排班覆盖二次确认、>365 天截断 Toast |
| 本月统计条 | 早/中/夜/休次数 |
| 值班提醒 | 时机三选一（advance_1d 默认/same_day_08/advance_2h）+ 开关；保存后订阅授权请求+既有 /api/notify/subscribe 登记；夜班+当天8点弱引导提示 |
| 后端 | `duty_shift`/`duty_rule`/`duty_remind` 三表（uk 物理保证）+ DutyService + ApiController 7 路由 + 24 条集成测试 |

## 二、12 阶段终态

| Stage | 名称 | 状态 | 关键产出/证据 |
|---|---|---|---|
| 0 | 初始化 | completed | execution_plan（含 6 项排除：真实推送/分享 P1/导入 P2/自定义班次/埋点/分包作废） |
| 1 | 需求分析 | completed ✅approve | requirements-v2.3-duty.md + spec JSON（两口径用户拍板：提醒不真实发送/本期无埋点） |
| 2 | 契约设计 | completed ✅approve | v1.9.0 duty tag 7 端点 15 模型；`contract cli check` 六栈全绿 |
| 3 | 技术方案 | completed ✅approve | v2.3-duty-design.md（3 表/事务矩阵/序列常量表/页面结构与三弹层） |
| 4 | 分支管理 | completed | feature/nianjiashi-duty-mvp（基座 main bf478bf） |
| 5A | 后端实现 | completed | e2d28b2；`mvnw test` **199 条全绿**（duty 24 新增） |
| 5B | 前端实现 | completed | 2b2d14d；Node stub wx 全链路冒烟通过（抓出并修复 1 个命名错配 bug） |
| 6 | 一致性校验 | completed ✅approve | 六层校验 PASS（3 项 wire 等价豁免，2 项 v2.2 既有）；cd87017 |
| 7 | 提交与 PR | completed ✅approve | PR #9，3 commits 已推送 |
| 8 | 测试用例 | completed ✅approve | testcases-v2.3-duty.md（六模块 30 条 + 4 条验收口径回归）+ test_report 全绿 |
| 9 | 提测与部署 | skipped（用户既定模式） | 真机自验后经云托管控制台部署；部署后验证口径=提测用例模块 G（ping/live/MySQL uk） |
| 10 | 交付报告 | 本文档 | — |

## 三、验证汇总

- **后端**：`mvnw test` 199/199 全绿（DutyApiIntegrationTest 24 条：四序列断言/weekday 只填周五/覆盖不论来源/365 天截断/清除幂等+回填/60 天窗口扫描/用户隔离/40100 七端点全量/参数错误矩阵）
- **契约**：`contract cli check` ok=true（68 路由，六栈 routes/models 全绿）；`contract cli live` 对本地运行时——duty 7 路由全在、三个枚举经 `@Schema(implementation)` 内联导出，仅剩 Pendant/PhotoMimeType 两项 v2.2 既有豁免
- **前端**：`node --check` 语法通过；Node stub wx（云托管通道转发本地真实后端）页面全链路冒烟 20 项断言通过——首屏 3 月并发、单日编辑、批量排班覆盖确认、提醒设置+订阅授权请求、清除、翻月缓存
- **合规**：duty 页面/封装违禁词 grep 零命中；文案口语化用「您」；无医疗暗示
- **端到端 curl 冒烟**：登录→设班→今日卡→规则应用（做一休一 12 月 31 天，早16/休15）→提醒配置→月历→清除，全部符合契约

## 四、口径与裁定落档

1. 提醒**不真实发送**（用户拍板）：只存时机配置 + 订阅授权登记（NotifyService 额度账本），真实 subscribeMessage.send 属部署联调阶段演进项
2. **本期无埋点**（用户拍板）：duty_* 4 事件不实现，留待拆分信号判断时补
3. 值班 Tab 页放**主包**（技术必然：微信 tab 页禁分包，PRD 分包路径作废）
4. 第 5 Tab 拍板新增（用户指令），值班插第 3 位
5. 下次值班=从明天起 60 天内最近非休班次日；序列规则含休班写入；weekday 只填周五；删单日不动规则本体（重新应用回填）

## 五、待办（部署联调阶段）

- [ ] 云托管部署（控制台）：`duty_shift`/`duty_rule`/`duty_remind` 由 ddl-auto 自动建表；`USE_CLOUD=true` 通道复验
- [ ] 值班提醒订阅消息模板申请（当前前端模板 ID 占位 `PLACEHOLDER_DUTY_TEMPLATE_ID`，与日子/胶囊模板同批处理）
- [ ] 真机走查提测用例模块 T（320pt 小屏 5 Tab 宽度）与 M（翻月手感）
- [ ] PR #9 评审合并

## 六、拆分信号观测（PRD §1.3，上线 4 周后）

值班功能周活占日活 30%+ / 值班用户次日留存 +5pp / 值班页分享新增占比 >20% / 「团队排班/换班」反馈集中出现——满足 ≥2 条则启动独立小程序拆分评估（本期埋点缺位，信号观测需先补埋点基建）。
