/**
 * 「我的」Tab:基本资料卡(登录用户名)+ 三个数据格(卡片数/待开胶囊/收到的心)
 * + 列表入口:我的卡片(→mycards)/我的时光胶囊(无参列表选择:1 个直接进,
 * 多个弹 actionSheet,0 个引导去封存)/提醒设置/隐私设置/关于。
 * 数据源:GET /api/cards(total/heartsReceived)+ GET /api/capsules(dueCount)。
 */
const { ensureLogin, getUser } = require('../../utils/request')
const cardsApi = require('../../utils/api-cards')

Page({
  data: {
    nickname: '',
    initial: '家',
    cardCount: 0,
    capsuleDue: 0,
    heartsReceived: 0,
  },

  onShow() {
    const user = getUser()
    const nickname = user && user.nickname ? user.nickname : ''
    this.setData({
      nickname: nickname || '家里人',
      initial: nickname ? nickname.substring(0, 1) : '家',
    })
    ensureLogin()
      .catch(() => {})
      .then(() => cardsApi.listMyCards())
      .then((res) => {
        const items = (res && res.items) || []
        this.setData({
          cardCount: (res && res.total) || items.length,
          heartsReceived: (res && res.heartsReceived) || 0,
        })
      })
      .catch(() => {})
    cardsApi
      .listMyCapsules()
      .then((res) => {
        this.setData({ capsuleDue: (res && res.dueCount) || 0 })
      })
      .catch(() => {})
  },

  /** 我的卡片 → 卡片墙 */
  goMyCards() {
    wx.navigateTo({ url: '/pages/mycards/mycards' })
  },

  /** 我的时光胶囊:0 个引导去封存;1 个直接进开箱页;多个弹选择(微信上限 6 项) */
  goCapsules() {
    cardsApi
      .listMyCapsules()
      .then((res) => {
        const items = (res && res.items) || []
        if (!items.length) {
          wx.showToast({ title: '还没有胶囊，先去埋一个', icon: 'none' })
          setTimeout(() => {
            wx.navigateTo({ url: '/pages/capsule-edit/capsule-edit' })
          }, 600)
          return
        }
        if (items.length === 1) {
          wx.navigateTo({ url: '/pages/capsule-open/capsule-open?capsuleId=' + items[0].id })
          return
        }
        const labels = items.slice(0, 6).map(capsuleLabel)
        wx.showActionSheet({
          itemList: labels,
          success: (r) => {
            const picked = items[r.tapIndex]
            if (picked) {
              wx.navigateTo({ url: '/pages/capsule-open/capsule-open?capsuleId=' + picked.id })
            }
          },
        })
      })
      .catch(() => {})
  },

  toastReminder() {
    wx.showToast({ title: '每人每天最多 3 条，可单独关闭', icon: 'none' })
  },

  toastPrivacy() {
    wx.showToast({ title: '隐私：卡片只对家人可见，海报不露手机号', icon: 'none' })
  },

  toastAbout() {
    wx.showToast({ title: '念家时 · 把重要的日子，记在心上', icon: 'none' })
  },
})

/** actionSheet 单行:封面提示 + 开箱状态(超长截断) */
function capsuleLabel(c) {
  const hint = String(c.coverHint || '时光胶囊').replace(/^"|"$/g, '')
  let state = '已开箱'
  if (c.status !== 'OPENED') {
    state = c.dueNow ? '今天可开' : (c.daysUntilOpen || 0) + ' 天后开'
  }
  const text = hint + ' · ' + state
  return text.length > 16 ? text.slice(0, 15) + '…' : text
}
