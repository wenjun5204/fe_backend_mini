const { ensureLogin, getUser } = require('../../utils/request')
const { listDays } = require('../../utils/api-days')

Page({
  data: {
    nickname: '',
    initial: '家',
    dayCount: 0,
  },

  onShow() {
    const user = getUser()
    const nickname = user && user.nickname ? user.nickname : ''
    this.setData({
      nickname: nickname || '家里人',
      initial: nickname ? nickname.substring(0, 1) : '家',
    })
    // 「记着的日子」= 自己创建的日子条数
    ensureLogin()
      .catch(() => {})
      .then(() => listDays())
      .then((res) => {
        const items = (res && res.items) || []
        const u = getUser()
        const mine = u ? items.filter((r) => r.userId === u.userId) : items
        this.setData({ dayCount: mine.length })
      })
      .catch(() => {})
  },

  goDays() {
    wx.switchTab({ url: '/pages/days/days' })
  },

  toastReminder() {
    wx.showToast({ title: '每人每天最多 3 条，可单独关闭', icon: 'none' })
  },

  toastPrivacy() {
    wx.showToast({ title: '隐私：日子只对家人可见，海报不露手机号', icon: 'none' })
  },

  toastFeedback() {
    wx.showToast({ title: '意见已记下，谢谢您', icon: 'none' })
  },

  toastAbout() {
    wx.showToast({ title: '念家时 · 把重要的日子，记在心上', icon: 'none' })
  },
})
