const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

const LEVEL_NAME = { PINGAN: '平安', RUYI: '如意', HONGFU: '鸿福' }
const LEVEL_TAG = { PINGAN: '平安签 · 全家共享', RUYI: '如意签 · 全家共享', HONGFU: '鸿福签 · 全家共享' }

Page({
  data: {
    // page state: loading | notJoined | ready | loadFailed
    // loadFailed:网络失败 ≠ 没有家,绝不能误显示「一键建家」(会建出重复的家)
    pageState: 'loading',
    // 生日事件横幅:临近生日 ≤7 天或当天展示
    birthdayBanner: null,
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
    if (!this._inited) {
      return
    }
    this.refreshToday()
    // 从生日簿增删改回来:强制刷新横幅;普通 tab 切换走 30s 节流
    const forceBanner = !!this._birthdayVisitAt
    this._birthdayVisitAt = null
    this.refreshBirthdayBanner(forceBanner)
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
        // 首屏两路请求并发,不串行等待
        await Promise.all([this.refreshToday(), this.refreshBirthdayBanner(true)])
      }
    } catch (e) {
      // 登录失败等:给重试入口,而不是伪装成「可建家」
      this.setData({ pageState: 'loadFailed' })
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
      // 请求失败 ≠ 未加入家族:误判会诱导重复建家,改为给出重试入口
      this.setData({ pageState: 'loadFailed' })
    }
  },

  /** 加载失败重试:回到 loading 态重新走 init */
  onRetryLoad() {
    this.setData({ pageState: 'loading' })
    this.init()
  },

  /** 拉取临近生日事件(当天/≤7天),用于首页横幅;30s 节流,force 可绕过 */
  async refreshBirthdayBanner(force) {
    const now = Date.now()
    if (!force && this._bannerLastAt && now - this._bannerLastAt < 30 * 1000) {
      return
    }
    this._bannerLastAt = now
    try {
      const result = await api.getUpcomingBirthday()
      const upcoming = result && result.upcoming
      if (upcoming && upcoming.displayName) {
        // 接口字段是 displayName/daysUntil,映射为横幅展示字段
        this.setData({
          birthdayBanner: {
            name: upcoming.displayName,
            today: upcoming.daysUntil === 0,
            daysUntil: upcoming.daysUntil,
          },
        })
      } else {
        this.setData({ birthdayBanner: null })
      }
    } catch (e) {
      // 无生日记录/未加入家族等场景静默,不影响主流程
    }
  },

  /** 点击横幅:直达生日簿 */
  onBirthdayBanner() {
    this._birthdayVisitAt = Date.now()
    wx.navigateTo({ url: '/pages/birthday/birthday' })
  },

  /** 完成态弱入口:去生日簿新增(自动打开表单) */
  onAddBirthday() {
    this._birthdayVisitAt = Date.now()
    wx.navigateTo({ url: '/pages/birthday/birthday?add=1' })
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
      // 已接签时不自动弹层:弹层是打断性 UI,改为点「再看一眼福签」主动查看;
      // 抽签成功的开奖时刻仍由 onDraw 主动弹出
      showCard: false,
    })
  },

  /** 再看一眼:主动弹出今日福签(数据已在页面内,不重新请求) */
  onReviewCard() {
    if (this.data.card) {
      this.setData({ showCard: true })
    } else {
      this.refreshToday()
    }
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

  /** 分享卡片:家人群/好友;节日日使用限定文案提升传播点 */
  onShareAppMessage() {
    const festival = (this.data.card && this.data.card.festival) || ''
    const title = festival
      ? '「' + festival + '限定」福签送给您!来自' + (this.data.familyName || '您的家人')
      : '别点这个签…点开就有福!来自' + (this.data.familyName || '您的家人')
    return {
      title,
      path: '/pages/card/card?inviteCode=' + (this.data.inviteCode || ''),
      imageUrl: '',
    }
  },
})
