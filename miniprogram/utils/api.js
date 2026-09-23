/**
 * 契约 API 封装:18 个接口与 api/openapi.yaml(v1.5.0)一一对应
 */
const { request } = require('./request')

// 鉴权/家族
const login = (code) => request('/api/auth/login', { method: 'POST', data: { code }, token: '' })
const getMyFamily = () => request('/api/family')
const createFamily = (name) => request('/api/family', { method: 'POST', data: { name } })
const joinFamily = (inviteCode) => request('/api/family/join', { method: 'POST', data: { inviteCode } })
const getInviteInfo = (inviteCode) => request('/api/invite/info?inviteCode=' + inviteCode, { token: '' })

// 福签
const getTodayFortune = () => request('/api/fortune/today')
const drawFortune = () => request('/api/fortune/draw', { method: 'POST' })
const shareFortune = () => request('/api/fortune/share', { method: 'POST' })
// taskItem:完成的今日宜事项文本(v1.5 可选体,八卦阵任务型进度依赖;缺省兼容旧行为)
const completeFortuneTask = (taskItem) =>
  request('/api/fortune/task-done', { method: 'POST', data: taskItem ? { taskItem } : {} })

// 互动
const tianfu = (targetUserId) => request('/api/interact/tianfu', { method: 'POST', data: { targetUserId } })
const cuifu = (targetUserId) => request('/api/interact/cuifu', { method: 'POST', data: { targetUserId } })

// 排行榜
const getFamilyRank = () => request('/api/rank/family')
const getFriendFamilyRank = () => request('/api/rank/friends')

// 家庭生日簿
const convertCalendar = (data) => request('/api/calendar/convert', { method: 'POST', data })
const getBirthdays = () => request('/api/birthdays')
const getUpcomingBirthday = () => request('/api/birthdays/upcoming')
const createBirthday = (data) => request('/api/birthdays', { method: 'POST', data })
const updateBirthday = (id, data) => request('/api/birthdays/' + id, { method: 'PUT', data })
const deleteBirthday = (id) => request('/api/birthdays/' + id, { method: 'DELETE' })
const updateBirthdayReminder = (id, reminderDays) => request('/api/birthdays/' + id + '/reminder', { method: 'PUT', data: { reminderDays } })

// 八卦集福阵(v1.5,只读进度 + 圆满庆祝一次性标记)
const getBaguaStatus = () => request('/api/bagua/status')
const celebrateBagua = () => request('/api/bagua/celebrate', { method: 'POST' })

// 解忧册(v1.5,服务端幂等:同日同条)
const getJieyouQuote = () => request('/api/jieyou/quote')

// 订阅消息(v1.5,授权登记;拒绝也登记,静默降级)
const subscribeNotify = (templateId, accepted) =>
  request('/api/notify/subscribe', { method: 'POST', data: { templateId, accepted } })
const getNotifyStatus = () => request('/api/notify/status')

module.exports = {
  login, getMyFamily, createFamily, joinFamily, getInviteInfo,
  getTodayFortune, drawFortune, shareFortune, completeFortuneTask,
  tianfu, cuifu, getFamilyRank, getFriendFamilyRank,
  convertCalendar, getBirthdays, getUpcomingBirthday, createBirthday, updateBirthday,
  deleteBirthday, updateBirthdayReminder,
  getBaguaStatus, celebrateBagua, getJieyouQuote, subscribeNotify, getNotifyStatus,
}
