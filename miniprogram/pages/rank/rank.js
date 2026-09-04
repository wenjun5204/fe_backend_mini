const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

const PLATE_NAME = { NONE: '', BRONZE: '勤俭之家', SILVER: '和睦之家', GOLD: '满门福气', JADE: '福泽满堂' }

Page({
  data: {
    tab: 'in', // in=家内榜 friend=好友家族榜
    inRank: null,
    friendRank: null,
    chaseText: '',
    friendItems: [],
  },

  async onLoad() {
    await ensureLogin()
  },

  onShow() {
    this.refresh()
  },

  async refresh() {
    this.loadInRank()
    this.loadFriendRank()
  },

  async loadInRank() {
    try {
      const resp = await api.getFamilyRank()
      this.setData({ inRank: resp })
    } catch (e) {
      // 未加入家族等提示由网络层处理
    }
  },

  async loadFriendRank() {
    try {
      const resp = await api.getFriendFamilyRank()
      const items = (resp.items || []).map((item) => ({
        ...item,
        plateName: PLATE_NAME[item.plateLevel] || '',
      }))
      this.setData({ friendItems: items, chaseText: resp.chaseText || '' })
    } catch (e) {
      // 同上
    }
  },

  onSwitchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab })
  },
})
