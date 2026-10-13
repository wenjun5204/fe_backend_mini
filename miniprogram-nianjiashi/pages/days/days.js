const { ensureLogin, getUser } = require('../../utils/request')
const { listDays } = require('../../utils/api-days')
const { viewOf } = require('../../utils/days-data')

function greetingOf() {
  const h = new Date().getHours()
  if (h >= 5 && h < 11) return '早上好'
  if (h >= 11 && h < 13) return '中午好'
  if (h >= 13 && h < 18) return '下午好'
  return '晚上好'
}

Page({
  data: {
    greeting: '',
    nickname: '您',
    hero: null,
    rows: [],
    isEmpty: true,
    loading: true,
  },

  onShow() {
    // 每次进入重拉:记下第一个日子返回后空状态即时消失(设计 §2.3)
    this.load()
  },

  onPullDownRefresh() {
    this.load()
  },

  load() {
    const user = getUser()
    this.setData({
      greeting: greetingOf(),
      nickname: user && user.nickname ? user.nickname : '您',
    })
    ensureLogin()
      .catch(() => {})
      .then(() => listDays())
      .then((res) => {
        const items = (res && res.items) || []
        const views = items.map(viewOf)
        const hero = views.find((v) => !v.isTogether) || null
        this.setData({ rows: views, hero, isEmpty: items.length === 0, loading: false })
        wx.stopPullDownRefresh()
      })
      .catch(() => {
        wx.stopPullDownRefresh()
        this.setData({ loading: false })
      })
  },

  goAdd() {
    wx.navigateTo({ url: '/pages/day-edit/day-edit' })
  },

  goTpl() {
    wx.switchTab({ url: '/pages/tpl/tpl' })
  },

  goAllDays() {
    wx.navigateTo({ url: '/pages/days-list/days-list' })
  },

  openDetail(e) {
    wx.navigateTo({ url: '/pages/day-detail/day-detail?id=' + e.currentTarget.dataset.id })
  },
})
