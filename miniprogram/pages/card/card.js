const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

const LEVEL_NAME = { PINGAN: '平安', RUYI: '如意', HONGFU: '鸿福' }
const LEVEL_TAG = { PINGAN: '平安签 · 全家共享', RUYI: '如意签 · 全家共享', HONGFU: '鸿福签 · 全家共享' }

Page({
  data: {
    // page state: loading | notJoined | ready
    pageState: 'loading',
    defaultFamilyName: '',
    familyName: '',
    inviteCode: '',
    // draw state
    phase: 'idle', // idle | shaking | drawn
    card: null,
    levelName: '',
    levelTag: '',
    praiseText: '',
    drawn: false,
    shared: false,
    taskDone: false,
    personalBless: 0,
  },

  onLoad(options) {
    this._inviteCode = (options && options.inviteCode) || ''
    this.init()
  },

  onShow() {
    if (this._inited) {
      this.refreshToday()
    }
  },

  async init() {
    try {
      await ensureLogin()
      // 分享卡片进入:带邀请码直接进家(新用户第一秒就在场景里)
      if (this._inviteCode) {
        try {
          await api.joinFamily(this._inviteCode)
          wx.showToast({ title: '进家成功,全家欢迎您!', icon: 'none' })
        } catch (e) {
          // 已在家族中/家族满员等:继续走正常流程
        }
        this._inviteCode = ''
      }
      await this.refreshFamily()
      this._inited = true
      if (this.data.pageState === 'ready') {
        await this.refreshToday()
      }
    } catch (e) {
      this.setData({ pageState: 'ready' })
    }
  },

  async refreshFamily() {
    try {
      const family = await api.getMyFamily()
      if (family.notJoined) {
        this.setData({ pageState: 'notJoined' })
      } else {
        this.setData({
          pageState: 'ready',
          familyName: family.familyName,
          inviteCode: family.inviteCode,
        })
      }
    } catch (e) {
      this.setData({ pageState: 'notJoined' })
    }
  },

  async refreshToday() {
    try {
      const today = await api.getTodayFortune()
      this.applyToday(today)
    } catch (e) {
      // 未加入家族等场景静默
    }
  },

  applyToday(today) {
    const card = today.card || {}
    this.setData({
      phase: today.drawn ? 'drawn' : 'idle',
      card,
      levelName: LEVEL_NAME[card.level] || '平安',
      levelTag: LEVEL_TAG[card.level] || LEVEL_TAG.PINGAN,
      drawn: today.drawn,
      shared: today.shared,
      taskDone: today.taskDone,
      personalBless: today.personalBless || 0,
      praiseText: '',
      // 已接签时直接展示福签卡片(「再看一眼」即重新弹出)
      showCard: !!today.drawn && !!card,
    })
  },

  /** 一键建家(零键盘输入:用默认名) */
  async onCreateFamily() {
    const name = this.data.defaultFamilyName || '福友家'
    try {
      const family = await api.createFamily(name)
      wx.showToast({ title: '建家成功,快邀请家人吧!', icon: 'none' })
      await this.refreshFamily()
      await this.refreshToday()
      this.shareToFamily()
    } catch (e) {
      // 错误 toast 已由网络层统一处理
    }
  },

  /** 摇一摇/点按钮抽福签(动画 ≤2.5s) */
  async onDraw() {
    if (this.data.phase !== 'idle') {
      return
    }
    this.setData({ phase: 'shaking' })
    const drawPromise = api.drawFortune()
    // 签筒晃动 1.2s 后翻卡
    setTimeout(async () => {
      try {
        const result = await drawPromise
        this.setData({
          phase: 'drawn',
          card: result.card,
          levelName: LEVEL_NAME[result.card.level] || '平安',
          levelTag: LEVEL_TAG[result.card.level] || LEVEL_TAG.PINGAN,
          praiseText: result.praiseText || '',
          drawn: true,
          shared: false,
          taskDone: false,
          personalBless: result.personalBless,
          showCard: true,
        })
        if (wx.vibrateShort) {
          wx.vibrateShort({ type: 'medium' })
        }
      } catch (e) {
        this.setData({ phase: 'idle' })
      }
    }, 1200)
  },

  /** 事已完成(+15) */
  async onTaskDone() {
    try {
      const result = await api.completeFortuneTask()
      this.setData({
        taskDone: true,
        personalBless: result.personalBless,
        showCard: false,
      })
      wx.showToast({ title: '事已完成,福值 +' + result.amount, icon: 'none' })
    } catch (e) {
      // 已完成/未抽签等提示由网络层处理
    }
  },

  /** 转给家人(软引导奖励 +10):点击即打开微信分享,同时发放奖励 */
  onShareTap() {
    // 分享通过 button open-type=share 触发,奖励在此发放(MVP 口径)
    api.shareFortune().then((result) => {
      this.setData({
        shared: true,
        personalBless: result.personalBless,
        showCard: false,
      })
      wx.showToast({ title: '福气已带给全家,+' + result.amount, icon: 'none' })
    }).catch(() => {})
  },

  closeCard() {
    this.setData({ showCard: false })
  },

  /** 建家后立刻拉起分享(邀请卡) */
  shareToFamily() {
    // 由用户主动点击分享按钮触发,这里仅提示
    wx.showToast({ title: '点右下角「转发」邀请家人', icon: 'none' })
  },

  /** 分享卡片:家人群/好友 */
  onShareAppMessage() {
    return {
      title: (this.data.familyName || '您') + '邀请您：一起接福气，全家福值榜见！',
      path: '/pages/card/card?inviteCode=' + (this.data.inviteCode || ''),
      imageUrl: '',
    }
  },
})
