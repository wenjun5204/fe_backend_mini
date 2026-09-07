/**
 * 契约 API 封装:13 个接口与 api/openapi.yaml 一一对应
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
const completeFortuneTask = () => request('/api/fortune/task-done', { method: 'POST' })

// 互动
const tianfu = (targetUserId) => request('/api/interact/tianfu', { method: 'POST', data: { targetUserId } })
const cuifu = (targetUserId) => request('/api/interact/cuifu', { method: 'POST', data: { targetUserId } })

// 排行榜
const getFamilyRank = () => request('/api/rank/family')
const getFriendFamilyRank = () => request('/api/rank/friends')

module.exports = {
  login, getMyFamily, createFamily, joinFamily, getInviteInfo,
  getTodayFortune, drawFortune, shareFortune, completeFortuneTask,
  tianfu, cuifu, getFamilyRank, getFriendFamilyRank,
}
