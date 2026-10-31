# TODO — 值班分享与待办改动

> 2026-10-31 整理。来源：值班排班 PRD 5.7（P1）+ 当前工作区未提交改动。
> PRD 依据：`docs/prototype/PRD-值班排班.md`（拆分信号第 3 条依赖本功能）。

## 一、待提交的已改改动（工作区暂存，未 commit）

- [ ] `miniprogram-nianjiashi/pages/duty/duty.wxml` — 批量排班弹层按钮文案「按这个规则排班」→「这么排班」（口语化减字）
- [ ] `miniprogram-nianjiashi/utils/request.js` — `USE_CLOUD` false → true（切回云托管通道，提交前确认是否要保留）

## 二、值班分享 · 第一步（纯前端，不碰契约）— ✅ 2026-10-31 已完成

PRD 5.7 P1 的轻量落地，先打通转发链路、收 `duty_share` 埋点数据。

- [x] `pages/duty/duty.js` 新增 `onShareAppMessage`：标题「我{N}月的值班表」/ path `/pages/days/days`（只读落地页属第二步）
- [x] `duty.json` 无 disableShare 类配置，无需改动；定义 `onShareAppMessage` 后右上角菜单转发自动出现
- [x] 状态卡分享：右侧加弱文字链「分享值班表」按钮（`open-type="share"`——share 面板只能由该类按钮唤起，长按无法触发，故按 PRD 意图改为常驻轻入口）
- [x] 分享缩略图：未传 `imageUrl`，微信默认取当前页截图降级（canvas 月历截图排第二步）
- [ ] 埋点 `duty_share`：**暂搁置**——小程序尚无埋点体系（无 track/report 工具），需先定采集方案，随 P1 埋点整体一起做
- [x] 顺带修：`app.wxss` `.chip` 补 `width:auto; margin:0`——微信原生 button 默认 184px 固定宽导致「关系/谁可以看到」等 chips 一行一个；day-edit、days-card-edit、birthday-card-edit、capsule-edit、letter-edit 六处共用，一处修复全部生效
- [x] day-edit 页版式精修（页面级 wxss，不影响其他页）：「这位家人是您的…」改 4 列等宽网格（10 项 3 行排齐）；「谁可以看到」两项等宽并排
- [x] day-edit「月和日」两个 picker 合并为单个 `mode="multiSelector"` 双列联动选择器：滚动月列实时联动日列天数（2 月/大小月/农历 30），确认后两列一起收；显示为「11 月 · 8 日」单条

## 三、值班分享 · 第二步（涉及契约，走 Stage 2 checkpoint）

被分享者打开只读月历视图——PRD 5.7 完整形态。

- [ ] 先改 `api/openapi.yaml`（契约升版）：新增"按 scene 查询某用户某月排班"只读接口
  - scene 复用 visitor 的 8-12 位随机码模式，防枚举
  - 出参只含月份 + 日期×班次映射 + 分享者昵称，不含 openid 等敏感字段
- [ ] 后端：controller → service → repository 新增只读查询，注意 `@Transactional(readOnly=true)` 内禁止兜底 save（MySQL 严格拒绝，见 AGENTS.md 踩坑）
- [ ] 前端：新只读落地页，顶部「来自 XX 的值班表」+「我也记值班」转化按钮（冷启动拉新核心链路）
- [ ] 分享缩略图升级：canvas 绘制月历截图
- [ ] 合规核对：隐私协议新增"值班数据本人与被分享者可见"说明（PRD 第 9 节）
- [ ] 契约校验：harness 的 `contract cli live`

## 四、P1 其余项（PRD 已规划，随排期做）

- [ ] 工时估算：按班次时段累计 + 夜班连续 >3 天休息提示（PRD 5.4 155-160 行）
- [ ] 自定义循环序列 / "每周几"多选（PRD 5.4 P1）
- [ ] 开放问题 2 需评审决策：只读视图是否升级为"家人互看值班"家庭空间能力
