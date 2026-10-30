/**
 * 契约 API 封装:duty 值班日历 v1.9.0 新端点
 * (出入参对齐 api/openapi.yaml v1.9.0 的 Duty* 类型锚点,字段名以此为准)
 *
 * 7 端点归属(tag=duty):
 * - 月历/状态卡 2:getDutyCalendar(month)/getDutyToday()
 * - 单日编辑 2:setDutyShift(date,shift)/clearDutyShift(date)
 * - 周期规则 2:getDutyRule()/applyDutyRule({ruleType,startDate,endDate})
 * - 提醒配置 1:updateDutyRemindConfig({timing,enabled})
 * 订阅授权登记复用既有 request 直调 POST /api/notify/subscribe(见提醒弹层)。
 */
const { request } = require('./request')

/**
 * GET /api/duty/calendar?month=yyyy-MM(缺省当月) → DutyCalendarResponse{month,shifts,stats}
 * (shifts 仅含有排班日期,按 date 升序;stats={early,mid,night,rest})
 */
function getDutyCalendar(month) {
  return request('/api/duty/calendar' + (month ? '?month=' + month : ''))
}

/** GET /api/duty/today → DutyTodayResponse{date,todayShift?,nextDuty?,remind} */
function getDutyToday() {
  return request('/api/duty/today')
}

/**
 * POST /api/duty/shifts body={date,shift} → DutyShiftView{date,shift,source:'manual'}(upsert,点选即保存)
 * shift:early|mid|night|rest
 */
function setDutyShift(date, shift) {
  return request('/api/duty/shifts', { method: 'POST', data: { date, shift } })
}

/** DELETE /api/duty/shifts/{date} → DutyShiftClearResponse{date,cleared:true}(幂等;不触碰规则本体) */
function clearDutyShift(date) {
  return request('/api/duty/shifts/' + encodeURIComponent(date), { method: 'DELETE' })
}

/** GET /api/duty/rule → DutyRuleView{ruleType?,startDate?,endDate?,active,appliedDays}(无规则空壳) */
function getDutyRule() {
  return request('/api/duty/rule')
}

/**
 * POST /api/duty/rules/apply body={ruleType,startDate,endDate} → DutyRuleApplyResponse
 * (ruleType:daily|on1off1|on2off2|emnr|weekday;跨度>365 天服务端截断,truncated=true)
 */
function applyDutyRule(ruleType, startDate, endDate) {
  return request('/api/duty/rules/apply', {
    method: 'POST',
    data: { ruleType, startDate, endDate },
  })
}

/** PUT /api/duty/remind-config body={timing,enabled} → DutyRemindConfig(回显) */
function updateDutyRemindConfig(timing, enabled) {
  return request('/api/duty/remind-config', { method: 'PUT', data: { timing, enabled } })
}

module.exports = {
  getDutyCalendar,
  getDutyToday,
  setDutyShift,
  clearDutyShift,
  getDutyRule,
  applyDutyRule,
  updateDutyRemindConfig,
}
