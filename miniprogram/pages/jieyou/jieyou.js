const { ensureLogin, getToken } = require('../../utils/request')
const api = require('../../utils/api')
const storage = require('../../utils/jieyou-storage')
const prefs = require('../../utils/prefs')

// 宽心话三型(契约 JieyouQuoteType):促成(推一把)/缓行(等一等)/放下(松一松)
const TYPE_LABEL = { CU_CHENG: '促成', HUAN_XING: '缓行', FANG_XIA: '放下' }
const TYPE_CLASS = { CU_CHENG: 'bt-cucheng', HUAN_XING: 'bt-huanxing', FANG_XIA: 'bt-fangxia' }

// 长按蓄力时长:按住 ≥600ms 再松手才起翻
const PRESS_MS = 600

Page({
  data: {
    quote: null, // {quoteId, text, typeLabel, typeClass, pageNo}
    pressing: false,
    opened: false,
    stamped: false,
    saved: false,
    pressHint: '按住封面，默念您的事',
    // 册子的记忆(仅本地,绝不上报)
    memories: [],
    doneCount: 0,
  },

  onLoad() {
    this._quote = null
    this._pressStartAt = 0
    this.setData({
      memories: storage.getMemories(),
      doneCount: storage.getDoneCount(),
    })
    ensureLogin().catch(() => {}).then(() => this.loadQuote())
  },

  onShow() {
    // Tab 页常驻:回到本页时同步一次本地记忆(仅本机数据,无请求、无推送)
    this.setData({
      memories: storage.getMemories(),
      doneCount: storage.getDoneCount(),
    })
    if (getToken() && !this._quote) {
      this.loadQuote()
    }
  },

  onHide() {
    if (this._pressTipTimer) {
      clearTimeout(this._pressTipTimer)
      this._pressTipTimer = null
    }
  },

  /** 进入页面拉当日宽心话(服务端幂等:同日同条,纯随机,不采集问题内容) */
  async loadQuote() {
    try {
      const q = await api.getJieyouQuote()
      this._quote = {
        quoteId: q.quoteId,
        text: q.text,
        typeLabel: TYPE_LABEL[q.type] || '宽心',
        typeClass: TYPE_CLASS[q.type] || 'bt-cucheng',
        pageNo: q.pageNo,
      }
    } catch (e) {
      this._quote = null
    }
  },

  /* ============ 长按蓄力 → 松手翻页 ============ */

  onCoverTouchStart() {
    if (this.data.opened) {
      return
    }
    this._pressStartAt = Date.now()
    this.setData({ pressing: true, pressHint: '想着呢……想好了就松手' })
    if (this._pressTipTimer) {
      clearTimeout(this._pressTipTimer)
    }
    this._pressTipTimer = setTimeout(() => {
      wx.showToast({ title: '想好了就松手', icon: 'none' })
    }, 2600)
  },

  onCoverTouchEnd() {
    if (this._pressTipTimer) {
      clearTimeout(this._pressTipTimer)
      this._pressTipTimer = null
    }
    if (!this.data.pressing) {
      return
    }
    const held = Date.now() - (this._pressStartAt || 0)
    this.setData({ pressing: false })
    if (this.data.opened) {
      return
    }
    if (held >= PRESS_MS) {
      this.openBook()
    } else {
      this.setData({ pressHint: '再按一会儿，想好了再松手' })
    }
  },

  async openBook() {
    if (!this._quote) {
      // 网络失败时再试一次
      await this.loadQuote()
    }
    if (!this._quote) {
      wx.showToast({ title: '网络不太好，请您稍后再翻', icon: 'none' })
      return
    }
    if (prefs.getSound() && wx.vibrateShort) {
      try {
        wx.vibrateShort({ type: 'light' })
      } catch (e) {
        // 静默
      }
    }
    this.setData({
      opened: true,
      quote: this._quote,
      pressHint: '今天翻过了，明天再来',
    })
  },

  /* ============ 「定」印章 / 收进册子的记忆 ============ */

  /** 就这么定了:盖「定」字印章(rotate+scale 入场) */
  onDecide() {
    if (!this.data.quote || this.data.stamped) {
      return
    }
    this.setData({ stamped: true })
    if (prefs.getSound() && wx.vibrateShort) {
      try {
        wx.vibrateShort({ type: 'medium' })
      } catch (e) {
        // 静默
      }
    }
  },

  /** 收进册子的记忆:只存本机,同日同条去重,只留最近 3 条 */
  onSaveMemory() {
    if (!this.data.quote || this.data.saved) {
      return
    }
    const list = storage.addMemory(this.data.quote)
    this.setData({ saved: true, memories: list })
    wx.showToast({ title: '已收进册子的记忆', icon: 'none' })
  },

  /** 「办成了」:仅本地标记 + 本地累计计数,绝不上报 */
  onMarkDone(e) {
    const quoteId = e.currentTarget.dataset.quoteid
    const dateKey = e.currentTarget.dataset.datekey
    const list = storage.markDone(quoteId, dateKey)
    this.setData({ memories: list, doneCount: storage.getDoneCount() })
  },

  /** 分享给家人:「我翻到了这句，你怎么看？」 */
  onShareAppMessage() {
    return {
      title: '我翻到了这句，你怎么看？',
      path: '/pages/card/card',
    }
  },
})
