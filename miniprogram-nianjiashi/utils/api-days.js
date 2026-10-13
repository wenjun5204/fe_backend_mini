/**
 * 契约 API 封装:days tag 7 端点(出入参对齐 api/openapi.yaml 与
 * .harness/gen/react/src/types/api.ts 的 Day 系类型锚点)
 * + 复用既有 POST /api/calendar/convert(录入页实时换算预览,只算不存)
 */
const { request } = require('./request')

// ===== days 契约 7 端点 =====

/** GET /api/days → DayListResponse{items}(服务端已排序:非 TOGETHER 按 daysUntil 升序,TOGETHER 压底) */
const listDays = () => request('/api/days')

/**
 * POST /api/days body=DayUpsertRequest{relation,emoji,kind,calendarType,month,day,visibility,reminders}
 * → DayRecord(含 nextDate/daysUntil/togetherDays 计算字段)
 */
const createDay = (data) => request('/api/days', { method: 'POST', data })

/** GET /api/days/{id} → DayRecord(reminders 为当前用户视角) */
const getDay = (id) => request('/api/days/' + id)

/**
 * PUT /api/days/{id} body=DayUpsertRequest(全量覆盖;reminders 不经此端点,契约 PUT 忽略该字段)
 * → DayRecord
 */
const updateDay = (id, data) => request('/api/days/' + id, { method: 'PUT', data })

/** DELETE /api/days/{id} → DeleteDayResponse{deleted:true} */
const deleteDay = (id) => request('/api/days/' + id, { method: 'DELETE' })

/** PUT /api/days/{id}/reminder body={reminders:[]} 幂等覆盖,空数组=关闭 → DayReminder{dayId,reminders} */
const updateDayReminder = (id, reminders) =>
  request('/api/days/' + id + '/reminder', { method: 'PUT', data: { reminders } })

/** GET /api/days/reminder-due?date=yyyy-MM-dd → DayReminderDueResponse(服务端调度/联调用,小程序端不调) */
const getReminderDue = (date) =>
  request('/api/days/reminder-due' + (date ? '?date=' + date : ''))

// ===== 复用既有日历换算(只计算不保存) =====

/** POST /api/calendar/convert body={calendarType,month,day} → {solarDate,lunarText,...} */
const convertCalendar = (data) => request('/api/calendar/convert', { method: 'POST', data })

module.exports = {
  listDays,
  createDay,
  getDay,
  updateDay,
  deleteDay,
  updateDayReminder,
  getReminderDue,
  convertCalendar,
}
