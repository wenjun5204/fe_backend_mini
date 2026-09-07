const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

const PLATES = {
  NONE: { name: '还没门牌', sub: '攒满 500 福值解锁' },
  BRONZE: { name: '勤俭之家', sub: '青铜门牌 · 已解锁' },
  SILVER: { name: '和睦之家', sub: '白银门牌 · 已解锁' },
  GOLD: { name: '满门福气', sub: '金字门牌 · 已解锁' },
  JADE: { name: '福泽满堂', sub: '玉匾 · 已解锁' },
}

Page({
  data: {
    notJoined: false,
    familyName: '',
    inviteCode: '',
    totalBless: 0,
    plate: null,
    drawnCount: 0,
    memberCount: 0,
    members: [],
  },

  async onLoad() {
    await ensureLogin()
  },

  onShow() {
    this.refresh()
  },

  async refresh() {
    try {
      const family = await api.getMyFamily()
      if (family.notJoined) {
        this.setData({ notJoined: true })
        return
      }
      const members = family.members || []
      const plate = PLATES[family.plateLevel] || PLATES.NONE
      this.setData({
        notJoined: false,
        familyName: family.familyName,
        inviteCode: family.inviteCode,
        totalBless: family.totalBless,
        memberCount: members.length,
        drawnCount: members.filter((m) => m.drawnToday).length,
        plate: {
          name: plate.name,
          sub: family.plateLevel === 'NONE' ? '攒满 500 福值解锁「勤俭之家」' : plate.sub,
          next: family.nextPlateBless > 0 ? '还差 ' + family.nextPlateBless + ' 福值升级门牌' : '最高门牌已达成',
        },
        members,
      })
    } catch (e) {
      this.setData({ notJoined: true })
    }
  },

  /** 添福(每人每天对每位家人 1 次) */
  async onTianfu(e) {
    const userId = e.currentTarget.dataset.userid
    try {
      const result = await api.tianfu(userId)
      wx.showToast({ title: '添福成功 +' + result.amount, icon: 'none' })
      this.refresh()
    } catch (err) {
      // 频控提示由网络层统一 toast
    }
  },

  /** 催福(对同一人每天 3 次) */
  async onCuifu(e) {
    const userId = e.currentTarget.dataset.userid
    try {
      const result = await api.cuifu(userId)
      wx.showToast({
        title: result.remainCount > 0
          ? '已提醒,今天还能催 ' + result.remainCount + ' 次'
          : '已提醒,今天不能再催啦',
        icon: 'none',
      })
    } catch (err) {
      // 频控/已接福提示由网络层统一 toast
    }
  },

  /** 邀请家人 */
  onInvite() {
    wx.showToast({ title: '点右上角「···」转发给家人', icon: 'none' })
  },

  onShareAppMessage() {
    return {
      title: (this.data.familyName || '您') + '邀请您：一起接福气，全家福值榜见！',
      path: '/pages/card/card?inviteCode=' + (this.data.inviteCode || ''),
    }
  },
})
