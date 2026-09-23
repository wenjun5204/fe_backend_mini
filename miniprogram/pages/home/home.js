const { ensureLogin, getToken, getUser } = require('../../utils/request')
const api = require('../../utils/api')
const prefs = require('../../utils/prefs')

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
    loadFailed: false,
    familyName: '',
    inviteCode: '',
    totalBless: 0,
    plate: null,
    drawnCount: 0,
    memberCount: 0,
    members: [],
    upcomingBirthday: null,
    // v1.5:个人卡片(数据复用现有接口)
    me: null, // {nickname, avatarText, avatarUrl, streakDays, personalBless}
    // v1.5:「我家 | 家族榜」分段子 Tab
    subTab: 'home', // home | rank
    inRank: null,
    // v1.5:集福阵入口卡
    baguaLitText: '进去看看',
    // v1.5:设置弹层
    showSettings: false,
    notifyStatus: null, // {subscribed, remainingQuotaToday, dailyLimit}
    hideInRank: false,
    animEnabled: true,
    soundEnabled: true,
  },

  async onLoad() {
    await ensureLogin()
    this.refresh()
  },

  onShow() {
    // 首次进入时 onShow 会先于 ensureLogin 完成:登录态未就绪不刷新,由 onLoad 登录后触发
    if (getToken()) {
      this.refresh()
    }
  },

  async onRetry() {
    await ensureLogin().catch(() => {})
    this.refresh()
  },

  async refresh() {
    this.setData({ loadFailed: false })
    try {
      const family = await api.getMyFamily()
      if (family.notJoined) {
        this.setData({ notJoined: true })
        return
      }
      this.setData({ notJoined: false })
      const [upcomingResult, baguaResult] = await Promise.all([
        api.getUpcomingBirthday().catch(() => ({ upcoming: null })),
        api.getBaguaStatus().catch(() => null), // 集福阵入口卡数据,失败静默
      ])
      const members = family.members || []
      const plate = PLATES[family.plateLevel] || PLATES.NONE
      this.setData({
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
        upcomingBirthday: upcomingResult.upcoming,
        baguaLitText: baguaResult && baguaResult.litCount !== undefined && baguaResult.litCount !== null
          ? '已点亮 ' + baguaResult.litCount + '/8 卦'
          : '进去看看',
        me: this.buildMe(members),
      })
    } catch (e) {
      // 请求失败 ≠ 没有家:不能误显示「一键建家」,保留当前内容并给出重试入口;
      // 具体错误提示由网络层统一 toast
      this.setData({ loadFailed: true })
    }
  },

  /** 个人卡片:头像/昵称来自登录态,连续天数/个人福值来自家族成员列表中定位自己 */
  buildMe(members) {
    const user = getUser()
    let member = null
    if (user && user.userId) {
      member = (members || []).find((m) => m.userId === user.userId) || null
    }
    const nickname = (user && user.nickname) || (member && member.nickname) || '您'
    return {
      nickname,
      avatarUrl: (user && user.avatarUrl) || '',
      avatarText: nickname.slice(0, 1),
      streakDays: member ? member.streakDays : 0,
      personalBless: member ? member.personalBless : 0,
    }
  },

  /* ============ 子 Tab:我家 | 家族榜 ============ */

  onSwitchSub(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ subTab: tab })
    if (tab === 'rank' && !this.data.inRank) {
      this.loadInRank()
    }
  },

  /** 家族榜:复用 /api/rank/family(样式参考 pages/rank) */
  async loadInRank() {
    try {
      const resp = await api.getFamilyRank()
      this.setData({ inRank: resp })
    } catch (e) {
      // 未加入家族等提示由网络层处理
    }
  },

  /** 查看完整双榜(原家族榜独立页,深链兼容) */
  onGoFullRank() {
    wx.navigateTo({ url: '/pages/rank/rank' })
  },

  /* ============ 设置弹层 ============ */

  onOpenSettings() {
    this.setData({
      showSettings: true,
      hideInRank: prefs.getHideInRank(),
      animEnabled: prefs.getAnimEnabled(),
      soundEnabled: prefs.getSound(),
    })
    // 提醒分组:展示剩余额度(GET /api/notify/status),失败静默
    api.getNotifyStatus().then((status) => {
      this.setData({ notifyStatus: status })
    }).catch(() => {
      this.setData({ notifyStatus: null })
    })
  },

  onCloseSettings() {
    this.setData({ showSettings: false })
  },

  onHideInRankChange(e) {
    prefs.setHideInRank(e.detail.value)
    this.setData({ hideInRank: e.detail.value })
  },

  onAnimChange(e) {
    prefs.setAnimEnabled(e.detail.value)
    this.setData({ animEnabled: e.detail.value })
  },

  onSoundChange(e) {
    prefs.setSound(e.detail.value)
    this.setData({ soundEnabled: e.detail.value })
  },

  /** 生日记录可见范围:跳生日簿逐条管理 */
  onBirthdayVisibility() {
    wx.navigateTo({ url: '/pages/birthday/birthday' })
  },

  /** 家庭关系分组占位(退出/转让/解散:服务端能力上线前仅占位样式) */
  onFamilyRelation() {
    wx.showToast({ title: '这个功能正在准备中', icon: 'none' })
  },

  /** 挂件/奖状入口(占位) */
  onPendant() {
    wx.showToast({ title: '挂件正在准备中', icon: 'none' })
  },

  onAward() {
    wx.showToast({ title: '奖状正在准备中', icon: 'none' })
  },

  /* ============ 原有功能 ============ */

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

  /** 打开家庭生日簿 */
  onBirthdayBook() {
    wx.navigateTo({ url: '/pages/birthday/birthday' })
  },

  /** 打开八卦集福阵 */
  onGoBagua() {
    wx.navigateTo({ url: '/pages/bagua/bagua' })
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
