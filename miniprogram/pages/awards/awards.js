const { getMyFamily, getBaguaStatus } = require('../../utils/api')
const { getUser } = require('../../utils/request')

/** 奖状墙:全部从既有数据推导(身份/火焰/福值/门牌/卦数),达标即亮,无服务端存储 */
const PLATE_NAMES = { BRONZE: '勤俭之家', SILVER: '和睦之家', GOLD: '满门福气', JADE: '福泽满堂' }

Page({
  data: {
    items: [],
    earnedCount: 0,
    loading: true
  },

  onLoad() {
    Promise.all([getMyFamily(), getBaguaStatus().catch(() => null)])
      .then(([family, bagua]) => {
        const me = (family.members || []).find(m => {
          const u = getUser()
          return u && m.userId === u.userId
        }) || (family.members || [])[0] || {}
        const streak = me.streakDays || 0
        const bless = me.personalBless || 0
        const isOwner = me.role === 'OWNER'
        const plate = family.plateLevel || 'NONE'
        const plateOrder = ['NONE', 'BRONZE', 'SILVER', 'GOLD', 'JADE']
        const plateIdx = plateOrder.indexOf(plate)
        const lit = bagua ? bagua.litCount : 0

        const def = [
          { emoji: '🏡', name: '建家元老', desc: '张罗起一个家', earned: isOwner, progress: isOwner ? '' : '户主专属' },
          { emoji: '🔥', name: '七日接福', desc: '连续接福 7 天', earned: streak >= 7, progress: `已连续 ${streak} 天` },
          { emoji: '🌋', name: '卅日接福', desc: '连续接福 30 天', earned: streak >= 30, progress: `已连续 ${streak} 天` },
          { emoji: '🧧', name: '百福临门', desc: '攒下 100 福值', earned: bless >= 100, progress: `已攒 ${bless}` },
          { emoji: '🏮', name: '五百福满', desc: '攒下 500 福值', earned: bless >= 500, progress: `已攒 ${bless}` },
          { emoji: '🐲', name: '两千大福', desc: '攒下 2000 福值', earned: bless >= 2000, progress: `已攒 ${bless}` },
          { emoji: '🥉', name: '勤俭之家', desc: '家门牌到青铜', earned: plateIdx >= 1, progress: PLATE_NAMES[plate] || '尚未挂门牌' },
          { emoji: '🥈', name: '和睦之家', desc: '家门牌到白银', earned: plateIdx >= 2, progress: PLATE_NAMES[plate] || '尚未挂门牌' },
          { emoji: '🥇', name: '满门福气', desc: '家门牌到黄金', earned: plateIdx >= 3, progress: PLATE_NAMES[plate] || '尚未挂门牌' },
          { emoji: '☯️', name: '集福过半', desc: '点亮 4 卦', earned: lit >= 4, progress: `已点亮 ${lit} 卦` },
          { emoji: '🎆', name: '八卦圆满', desc: '点亮全部 8 卦', earned: lit >= 8, progress: `已点亮 ${lit} 卦` }
        ]
        this.setData({
          loading: false,
          items: def,
          earnedCount: def.filter(d => d.earned).length
        })
      })
      .catch(() => this.setData({ loading: false }))
  },

  onBack() {
    wx.navigateBack()
  }
})
