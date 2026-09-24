const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')
const prefs = require('../../utils/prefs')

const LEVEL_NAME = { PINGAN: '平安', RUYI: '如意', HONGFU: '鸿福' }
const LEVEL_TAG = { PINGAN: '平安卡 · 全家共享', RUYI: '如意卡 · 全家共享', HONGFU: '鸿福卡 · 全家共享' }

// 八方:符号/名/卡面主题装饰(方位只决定卡面主题,不影响等级与福值)
const GUAS = [
  { key: 'QIAN', char: '🕊️', name: '云鹤', theme: '云鹤', emoji: '🕊️' },
  { key: 'DUI', char: '🪷', name: '荷塘', theme: '荷塘', emoji: '🪷' },
  { key: 'LI', char: '🏮', name: '灯笼', theme: '灯笼', emoji: '🏮' },
  { key: 'ZHEN', char: '🌱', name: '春雷', theme: '春雷', emoji: '🌱' },
  { key: 'XUN', char: '🎐', name: '风铃', theme: '风铃', emoji: '🎐' },
  { key: 'KAN', char: '🐟', name: '锦鲤', theme: '锦鲤', emoji: '🐟' },
  { key: 'GEN', char: '⛰️', name: '山景', theme: '山景', emoji: '⛰️' },
  { key: 'KUN', char: '🌾', name: '花开', theme: '花开', emoji: '🌾' },
]

// 转盘物理参数(与已验收原型 docs/prototype/bagua-interactive.html 完全一致)
const FRICTION = 0.975   // 摩擦:角速度每 16.7ms 衰减一次
const STOP_EPS = 0.02    // 停转阈值(deg/ms)
const OMEGA_MAX = 2.8    // 角速度上限(deg/ms)
const TAP_IMPULSE = 0.5  // 每次点击施加的角速度冲量(deg/ms)
const FRAME_MS = 16      // rAF 替代:setInterval 帧间隔
const BIG_BTN_IMPULSE = 1.6 // 「接福气」大按钮保底冲量(约 2-4 秒自然停转)

// 订阅消息模板 ID:开发期占位,联调时替换为微信后台申请的真实模板 ID
const NOTIFY_TEMPLATE_ID = 'PLACEHOLDER-TMPL-REPLACE-ME'

const FLOAT_CHARS = ['福', '旺', '顺', '安', '乐', '和', '康', '暖']

// 转盘 8 扇区定位(自正上方顺时针)
const SECTORS = GUAS.map((g, i) => ({
  key: g.key,
  char: g.char,
  name: g.name,
  style: 'transform: rotate(' + i * 45 + 'deg) translateY(-206rpx) rotate(' + -i * 45 + 'deg)',
}))

function guaInfo(key) {
  return GUAS.find((g) => g.key === key) || GUAS[0]
}

function themeNameOf(card) {
  const info = guaInfo(card && card.gua)
  const theme = (card && card.cardTheme) || info.theme
  return theme.indexOf('卡') >= 0 ? theme : theme + '卡'
}

Page({
  data: {
    // page state: loading | notJoined | ready | loadFailed
    // loadFailed:网络失败 ≠ 没有家,绝不能误显示「一键建家」(会建出重复的家)
    pageState: 'loading',
    // 生日事件横幅:临近生日 ≤7 天或当天展示
    birthdayBanner: null,
    birthdayChipText: '记一位家人的生日',
    defaultFamilyName: '',
    familyName: '',
    inviteCode: '',
    // 转盘
    sectors: SECTORS,
    rotation: 0,
    spinning: false,
    winnerIdx: -1,
    tapCount: 0,
    floats: [],
    // 集福阵入口条
    baguaLitText: '',
    // draw state: idle | shaking | drawn
    phase: 'idle',
    card: null,
    levelName: '',
    levelTag: '',
    guaName: '',
    themeName: '',
    themeClass: '',
    themeEmoji: '🏮',
    praiseText: '',
    drawn: false,
    shared: false,
    taskDone: false,
    personalBless: 0,
    showCard: false,
    // 抽卡成功弹层订阅勾选(默认勾选;拒绝/失败一律静默,不影响任何功能)
    notifyChecked: true,
    animEnabled: true,
  },

  onLoad(options) {
    this._inviteCode = (options && options.inviteCode) || ''
    this._rotation = 0
    this._omega = 0
    this._looping = false
    this._timer = null
    this._dragging = false
    this._vel = 0
    this._drawing = false
    this._funToastShown = false
    this._notifyHandled = true
    this._floatSeq = 0
    this.init()
  },

  onShow() {
    this.setData({ animEnabled: prefs.getAnimEnabled() })
    if (!this._inited) {
      return
    }
    this.refreshToday()
    // 从生日簿/集福阵页回来:强制刷新入口条;普通 tab 切换走 30s 节流
    const forceBanner = !!this._birthdayVisitAt
    this._birthdayVisitAt = null
    this.refreshBirthdayBanner(forceBanner)
    this.refreshEntries()
  },

  onHide() {
    this.stopLoop()
  },

  onUnload() {
    this.stopLoop()
  },

  onReady() {
    this.ensureZoneCenter()
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
        // 首屏请求并发,不串行等待
        await Promise.all([this.refreshToday(), this.refreshBirthdayBanner(true), this.refreshEntries()])
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

  /** 拉取临近生日事件(当天/≤7天),用于首页横幅与入口条;30s 节流,force 可绕过 */
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
          birthdayChipText: upcoming.daysUntil === 0
            ? upcoming.displayName + '今天生日'
            : upcoming.displayName + '生日还有 ' + upcoming.daysUntil + ' 天',
        })
      } else {
        this.setData({ birthdayBanner: null, birthdayChipText: '记一位家人的生日' })
      }
    } catch (e) {
      // 无生日记录/未加入家族等场景静默,不影响主流程
    }
  },

  /** 集福阵入口条数据(已点亮 N/8 方);失败静默,入口仍可点 */
  refreshEntries() {
    api.getBaguaStatus().then((status) => {
      if (status && status.litCount !== undefined && status.litCount !== null) {
        this.setData({ baguaLitText: '已点亮 ' + status.litCount + '/8 方' })
      }
    }).catch(() => {})
  },

  /** 点击横幅/入口条:直达生日簿 */
  onBirthdayBanner() {
    this._birthdayVisitAt = Date.now()
    wx.navigateTo({ url: '/pages/birthday/birthday' })
  },

  /** 完成态弱入口:去生日簿新增(自动打开表单) */
  onAddBirthday() {
    this._birthdayVisitAt = Date.now()
    wx.navigateTo({ url: '/pages/birthday/birthday?add=1' })
  },

  /** 集福阵入口 */
  onGoBagua() {
    wx.navigateTo({ url: '/pages/bagua/bagua' })
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
    const info = guaInfo(card.gua)
    this.setData({
      phase: today.drawn ? 'drawn' : 'idle',
      card,
      levelName: LEVEL_NAME[card.level] || '平安',
      levelTag: LEVEL_TAG[card.level] || LEVEL_TAG.PINGAN,
      guaName: info.name,
      themeName: themeNameOf(card),
      themeClass: 'th-' + info.key,
      themeEmoji: info.emoji,
      drawn: today.drawn,
      shared: today.shared,
      taskDone: today.taskDone,
      personalBless: today.personalBless || 0,
      praiseText: '',
      // 已接卡时不自动弹层:弹层是打断性 UI,改为点「再看一眼福卡」主动查看;
      // 翻卡成功的开奖时刻仍由停转/大按钮触发主动弹出
      showCard: false,
    })
  },

  /* ===================== 转盘物理(原型移植) ===================== */

  /** 查询转盘区域中心(拖拽角度计算用;每次按下时刷新以兼容页面滚动) */
  ensureZoneCenter() {
    return new Promise((resolve) => {
      wx.createSelectorQuery().in(this)
        .select('.dial-zone')
        .boundingClientRect((rect) => {
          if (rect) {
            this._zoneCenter = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
          }
          resolve(this._zoneCenter || null)
        })
        .exec()
    })
  },

  angleOf(x, y, center) {
    return Math.atan2(y - center.y, x - center.x) * 180 / Math.PI
  },

  clampOmega(v) {
    return Math.max(-OMEGA_MAX, Math.min(OMEGA_MAX, v))
  },

  renderRotation() {
    // 只 setData 一个数字,降低 60fps 下的通信开销
    this.setData({ rotation: Math.round(this._rotation * 10) / 10 })
  },

  startLoop() {
    if (this._looping) {
      return
    }
    this._looping = true
    if (!this.data.spinning) {
      this.setData({ spinning: true })
    }
    this._lastT = Date.now()
    this._timer = setInterval(() => this.physicsTick(), FRAME_MS)
  },

  physicsTick() {
    const now = Date.now()
    const dt = Math.min(48, now - this._lastT)
    this._lastT = now
    this._rotation += this._omega * dt
    this._omega *= Math.pow(FRICTION, dt / 16.7)
    this.renderRotation()
    if (Math.abs(this._omega) <= STOP_EPS) {
      this.stopLoop()
      this.finishSpin()
    }
  },

  stopLoop() {
    if (this._timer) {
      clearInterval(this._timer)
      this._timer = null
    }
    this._looping = false
    this._omega = 0
    if (this.data.spinning) {
      this.setData({ spinning: false })
    }
  },

  /** 点转盘中央「福」字:顺着当前转向加一股劲(连点越转越快) */
  onHubTap() {
    const dir = !this._omega ? 1 : (this._omega > 0 ? 1 : -1)
    this._omega = this.clampOmega((this._omega || 0) + dir * TAP_IMPULSE)
    this.startLoop()
    this.setData({ tapCount: this.data.tapCount + 1 })
    if (prefs.getSound() && wx.vibrateShort) {
      try {
        wx.vibrateShort({ type: 'light' })
      } catch (e) {
        // 触感失败静默
      }
    }
    this.spawnFloat()
  },

  /** 浮字彩蛋(接福气/转着玩都出) */
  spawnFloat() {
    const id = ++this._floatSeq
    const item = {
      id,
      char: FLOAT_CHARS[Math.floor(Math.random() * FLOAT_CHARS.length)],
      left: 30 + Math.floor(Math.random() * 40),
    }
    const floats = this.data.floats.concat([item])
    this.setData({ floats })
    setTimeout(() => {
      this.setData({ floats: this.data.floats.filter((f) => f.id !== id) })
    }, 1000)
  },

  /** 手指拨转盘:跟点击一样只加劲,不限次数 */
  onDialTouchStart(e) {
    if (this._looping) {
      return
    }
    this._dragging = true
    this._lastMoveT = Date.now()
    this._vel = 0
    this._lastAng = null
    const t = e.touches && e.touches[0]
    this._startTouch = t ? { x: t.clientX, y: t.clientY } : null
    this.ensureZoneCenter().then((center) => {
      if (!center || !this._startTouch || !this._dragging || this._lastAng !== null) {
        return
      }
      this._lastAng = this.angleOf(this._startTouch.x, this._startTouch.y, center)
    })
  },

  onDialTouchMove(e) {
    if (!this._dragging || this._looping || !this._zoneCenter) {
      return
    }
    const t = e.touches && e.touches[0]
    if (!t) {
      return
    }
    const a = this.angleOf(t.clientX, t.clientY, this._zoneCenter)
    const now = Date.now()
    if (this._lastAng === null) {
      this._lastAng = a
      this._lastMoveT = now
      return
    }
    let d = a - this._lastAng
    if (d > 180) {
      d -= 360
    }
    if (d < -180) {
      d += 360
    }
    this._rotation += d
    const dt = Math.max(1, now - this._lastMoveT)
    this._vel = this._vel * 0.6 + (d / dt) * 0.4
    this._lastAng = a
    this._lastMoveT = now
    this.renderRotation()
  },

  onDialTouchEnd() {
    if (!this._dragging) {
      return
    }
    this._dragging = false
    if (Math.abs(this._vel || 0) > 0.25) {
      this._omega = this.clampOmega((this._omega || 0) + this._vel * 2.2)
      this.startLoop()
    }
  },

  /** 自然停转:指针所指方位高亮;今日未接则翻出今日福卡 */
  finishSpin() {
    // 第 i 扇区圆心位于盘面角度 i*45(自正上方顺时针);盘转 rotation 后屏幕角度 = i*45 + rotation,取最接近正上方者
    const norm = ((-this._rotation % 360) + 360) % 360
    const idx = Math.round(norm / 45) % 8
    this.setData({ winnerIdx: idx })
    setTimeout(() => {
      if (this.data.winnerIdx === idx) {
        this.setData({ winnerIdx: -1 })
      }
    }, 900)
    if (this.data.phase === 'idle') {
      setTimeout(() => this.drawNow(), 900)
    } else if (!this._funToastShown) {
      this._funToastShown = true
      wx.showToast({ title: '今天的福已接过,再转就是纯解闷啦', icon: 'none' })
    }
  },

  /** 「接福气」大按钮兜底(适老化:不会拨也一定能接到):保底一股劲,停转后翻卡 */
  onBigDraw() {
    if (this.data.phase !== 'idle' || this._drawing) {
      return
    }
    const base = this._omega || 0
    this._omega = this.clampOmega(base >= 0 ? Math.max(base, BIG_BTN_IMPULSE) : Math.min(base, -BIG_BTN_IMPULSE))
    this.startLoop()
    // 异常兜底:若 4.2s 后物理循环已停但尚未触发抽卡,直接抽
    setTimeout(() => {
      if (this.data.phase === 'idle' && !this._drawing && !this._looping && this._inited) {
        this.drawNow()
      }
    }, 4200)
  },

  /* ===================== 抽卡 ===================== */

  /** 停转后调 POST /api/fortune/draw(服务端下发结果,客户端只负责表现) */
  async drawNow() {
    if (this._drawing || this.data.phase !== 'idle') {
      return
    }
    this._drawing = true
    this.setData({ phase: 'shaking' })
    const startedAt = Date.now()
    try {
      const result = await api.drawFortune()
      // 至少留 0.9s 的停驻高亮时刻,再翻卡
      const wait = Math.max(0, 900 - (Date.now() - startedAt))
      setTimeout(() => {
        this._drawing = false
        this.applyDrawResult(result)
      }, wait)
    } catch (e) {
      this._drawing = false
      this.setData({ phase: 'idle' })
    }
  },

  applyDrawResult(result) {
    const card = result.card || {}
    const info = guaInfo(card.gua)
    this.setData({
      phase: 'drawn',
      card,
      levelName: LEVEL_NAME[card.level] || '平安',
      levelTag: LEVEL_TAG[card.level] || LEVEL_TAG.PINGAN,
      guaName: info.name,
      themeName: themeNameOf(card),
      themeClass: 'th-' + info.key,
      themeEmoji: info.emoji,
      praiseText: result.praiseText || '',
      drawn: true,
      shared: false,
      taskDone: false,
      personalBless: result.personalBless,
      showCard: true,
      notifyChecked: true,
    })
    // 本次抽卡的订阅授权尚未处理
    this._notifyHandled = false
    if (prefs.getSound() && wx.vibrateShort) {
      try {
        wx.vibrateShort({ type: 'medium' })
      } catch (e) {
        // 静默
      }
    }
  },

  /** 再看一眼:主动弹出今日福卡(数据已在页面内,不重新请求) */
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
      await api.createFamily(name)
      wx.showToast({ title: '建家成功,快邀请家人吧!', icon: 'none' })
      await this.refreshFamily()
      await this.refreshToday()
      this.shareToFamily()
    } catch (e) {
      // 错误 toast 已由网络层统一处理
    }
  },

  /** 完成今日宜事项(+15);带事项文本,集福阵任务型进度依赖此字段 */
  async onTaskDone(e) {
    if (this.data.taskDone) {
      return
    }
    const taskItem = (e && e.currentTarget && e.currentTarget.dataset.item) || ''
    try {
      const result = await api.completeFortuneTask(taskItem)
      this.setData({
        taskDone: true,
        personalBless: result.personalBless,
        showCard: false,
      })
      wx.showToast({ title: '事已完成,福值 +' + result.amount, icon: 'none' })
    } catch (e2) {
      // 已完成/未抽卡等提示由网络层处理
    }
  },

  /** 转给家人(软引导奖励 +10):点击即打开微信分享,同时发放奖励 */
  onShareTap() {
    // 正向时刻请求订阅授权(拒绝/失败一律静默,不影响任何功能)
    this.handleNotifySubscribe()
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
    this.handleNotifySubscribe()
    this.setData({ showCard: false })
  },

  /** 抽卡成功弹层勾选「明天提醒我来接福」 */
  onToggleNotify() {
    this.setData({ notifyChecked: !this.data.notifyChecked })
  },

  /**
   * 订阅授权(仅抽卡成功的正向时刻触发一次):
   * wx.requestSubscribeMessage → POST /api/notify/subscribe {templateId, accepted};
   * 用户拒绝/调用失败一律静默降级,绝不弹错、绝不阻塞功能。
   */
  handleNotifySubscribe() {
    if (this._notifyHandled) {
      return
    }
    this._notifyHandled = true
    if (!this.data.notifyChecked) {
      return
    }
    try {
      wx.requestSubscribeMessage({
        tmplIds: [NOTIFY_TEMPLATE_ID],
        success: (res) => {
          let accepted = false
          try {
            accepted = !!(res && res[NOTIFY_TEMPLATE_ID] === 'accept')
          } catch (e) {
            accepted = false
          }
          api.subscribeNotify(NOTIFY_TEMPLATE_ID, accepted).catch(() => {})
        },
        fail: () => {
          // 模板未配置等调用失败:登记一次拒绝,同样静默
          api.subscribeNotify(NOTIFY_TEMPLATE_ID, false).catch(() => {})
        },
      })
    } catch (e) {
      // 静默降级
    }
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
      ? '「' + festival + '限定」福卡送给您!来自' + (this.data.familyName || '您的家人')
      : '别点这个福…点开就有福!来自' + (this.data.familyName || '您的家人')
    return {
      title,
      path: '/pages/card/card?inviteCode=' + (this.data.inviteCode || ''),
      imageUrl: '',
    }
  },
})
