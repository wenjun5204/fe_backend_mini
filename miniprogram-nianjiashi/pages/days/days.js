const { ensureLogin, getUser } = require('../../utils/request')
const { listDays } = require('../../utils/api-days')
const { viewOf } = require('../../utils/days-data')
const { getMyFamily } = require('../../utils/api-family')

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
    // 家人们入口(家人页降级二级页):成员首字头像排+摘要
    familyJoined: false,
    familyAvatars: [],
    familySummary: '',
  },

  onShow() {
    // 每次进入重拉:记下第一个日子返回后空状态即时消失(设计 §2.3)
    this.load()
    this.loadFamilyEntry()
  },

  /** 家人们横幅:成员首字头像(最多 5 个)+人数摘要;未加入显示邀请引导 */
  loadFamilyEntry() {
    getMyFamily()
      .then((f) => {
        if (!f || f.notJoined) {
          this.setData({ familyJoined: false, familyAvatars: [], familySummary: '' })
          return
        }
        const members = f.members || []
        this.setData({
          familyJoined: true,
          familyAvatars: members.slice(0, 5).map((m) => (m.nickname || '家').substring(0, 1)),
          familySummary: members.length + ' 位家人 · 互相看得见重要的日子',
        })
      })
      .catch(() => {}) // 家人接口失败不阻塞首页
  },

  goFamily() {
    wx.navigateTo({ url: '/pages/family/family' })
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
    wx.navigateTo({ url: '/pages/tpl/tpl' })
  },

  goAllDays() {
    wx.navigateTo({ url: '/pages/days-list/days-list' })
  },

  openDetail(e) {
    wx.navigateTo({ url: '/pages/day-detail/day-detail?id=' + e.currentTarget.dataset.id })
  },
})
