const { getMyFamily, getBaguaStatus, wearPendant } = require('../../utils/api')
const { getUser } = require('../../utils/request')

/** 挂件目录:解锁条件全部可从既有数据推导(达标解锁制,不消耗福值) */
const CATALOG = [
  { key: 'BAMBOO', emoji: '🎋', name: '竹叶', cond: '新朋友见面礼' },
  { key: 'FLAME', emoji: '🔥', name: '火焰', cond: '连续接福 7 天' },
  { key: 'LANTERN', emoji: '🏮', name: '灯笼', cond: '攒下 200 福值' },
  { key: 'KOI', emoji: '🐟', name: '锦鲤', cond: '攒下 1000 福值' },
  { key: 'BAGUA', emoji: '✨', name: '集福', cond: '集福阵点亮过半' },
  { key: 'FUBAO', emoji: '🧧', name: '福包', cond: '集福圆满' }
]

Page({
  data: {
    items: [],
    wearing: '',
    loading: true
  },

  onLoad() {
    this.refresh()
  },

  refresh() {
    Promise.all([getMyFamily(), getBaguaStatus().catch(() => null)])
      .then(([family, bagua]) => {
        const me = (family.members || []).find(m => {
          const u = getUser()
          return u && m.userId === u.userId
        }) || (family.members || [])[0] || {}
        const lit = bagua ? bagua.litCount : 0
        // 客户端预推导解锁态(展示用;佩戴时服务端仍会校验)
        const unlocked = new Set(['BAMBOO'])
        if ((me.streakDays || 0) >= 7) unlocked.add('FLAME')
        if ((me.personalBless || 0) >= 200) unlocked.add('LANTERN')
        if ((me.personalBless || 0) >= 1000) unlocked.add('KOI')
        if (lit >= 4) unlocked.add('BAGUA')
        if (lit >= 8) unlocked.add('FUBAO')
        this.setData({
          loading: false,
          wearing: me.pendant || '',
          items: CATALOG.map(p => ({
            ...p,
            unlocked: unlocked.has(p.key),
            current: this.progressText(p, me, lit)
          }))
        })
      })
      .catch(() => this.setData({ loading: false }))
  },

  /** 进度小字:未解锁时告诉用户还差多少 */
  progressText(p, me, lit) {
    switch (p.key) {
      case 'FLAME': return `已连续 ${me.streakDays || 0} 天`
      case 'LANTERN': return `已攒 ${me.personalBless || 0} 福值`
      case 'KOI': return `已攒 ${me.personalBless || 0} 福值`
      case 'BAGUA': return `已点亮 ${lit} 方`
      case 'FUBAO': return `已点亮 ${lit} 方`
      default: return '已解锁'
    }
  },

  onPick(e) {
    const key = e.currentTarget.dataset.key
    const item = this.data.items.find(i => i.key === key)
    if (!item || !item.unlocked) {
      wx.showToast({ title: '再陪家人攒一攒,就能点亮它', icon: 'none' })
      return
    }
    if (key === this.data.wearing) return
    wearPendant(key)
      .then(() => {
        this.setData({ wearing: key })
        wx.showToast({ title: '已戴上,家人都看得见', icon: 'none' })
      })
      .catch(() => {})
  },

  onBack() {
    wx.navigateBack()
  }
})
