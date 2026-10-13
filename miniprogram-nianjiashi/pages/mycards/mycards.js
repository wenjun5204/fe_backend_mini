/**
 * 我的卡片页(GET /api/cards → MadeCardListResponse{items,total,heartsReceived})
 * 双列卡片墙:每张按 kind 渲染迷你成品卡(霞橘/深夜星河/青金渐变 + kicker + 标题 + 制作时间);
 * 「再次分享」→ POST /api/cards/{id}/share(幂等同码)→ poster 海报舞台;
 * 「删除」→ 二次确认 → DELETE /api/cards/{id}(软删,好友侧链路同步失效)→ 刷新;
 * 空态引导去模板馆。
 *
 * 分享配置走 app 级:poster 组件 sharecfg 事件 → getApp().ensureShareCfg(存 app 级
 * shareCfg)同时落页面 data;转发卡片 path=pagePath+scene 直达访客页。
 */
const api = require('../../utils/api-cards')
const { ensureLogin } = require('../../utils/request')
const {
  KIND_LABEL,
  daysTogetherOf,
  daysUntilOf,
  fmtDot,
} = require('../../utils/cards-data')

/** MadeCard → 迷你成品卡视图(渐变/kicker/大字,同海报三套样式) */
function cardView(c) {
  const p = c.payload || {}
  const kind = c.templateKind
  const madeDot = fmtDot(String(c.createdAt || '').split('T')[0])
  let bg = 'bg-night'
  let kicker = ''
  let emoji = ''
  let big = ''
  let unit = ''
  if (kind === 'DAYS_CARD') {
    bg = p.styleKey === 'NIGHT' ? 'bg-night' : p.styleKey === 'MINT' ? 'bg-mint' : 'bg-sunset'
    kicker = 'DAYS TOGETHER'
    big = String(daysTogetherOf(p.startDate))
    unit = ' 天'
  } else if (kind === 'CAPSULE') {
    bg = 'bg-mint'
    kicker = 'TIME CAPSULE'
    emoji = '⏳'
    big = String(daysUntilOf(p.openDate))
    unit = ' 天后'
  } else if (kind === 'BIRTHDAY_CARD') {
    // 福卡:暖橘渐变 +「生日快乐」kicker + 关系副题(v1.8.0;海报形态见 components/poster)
    bg = p.styleKey === 'MINT' ? 'bg-mint' : 'bg-warm'
    kicker = 'HAPPY BIRTHDAY 🎂'
    emoji = '🎂'
  } else {
    bg = 'bg-night'
    kicker = 'A LETTER TO MOM'
    emoji = '💌'
  }
  return {
    id: c.id,
    kind,
    raw: c,
    bg,
    kicker,
    emoji,
    big,
    unit,
    name: KIND_LABEL[kind] || '我的卡片',
    when: '制作于 ' + madeDot,
    hearts: c.totalHearts || 0,
    sharing: false,
    deleting: false,
  }
}

Page({
  data: {
    loading: true,
    cards: [],
    total: 0,
    heartsReceived: 0,
  },

  onShow() {
    this.load()
  },

  load() {
    ensureLogin()
      .catch(() => {})
      .then(() => api.listMyCards())
      .then((res) => {
        const items = (res && res.items) || []
        this.setData({
          loading: false,
          cards: items.map(cardView),
          total: (res && res.total) || items.length,
          heartsReceived: (res && res.heartsReceived) || 0,
        })
      })
      .catch(() => {
        this.setData({ loading: false, cards: [] })
      })
  },

  /** 再次分享:幂等取码 → 海报舞台 */
  tapShare(e) {
    const id = e.currentTarget.dataset.id
    const view = this.data.cards.find((c) => c.id === id)
    if (!view || view.sharing) return
    this.markCard(id, { sharing: true })
    api
      .shareCard(id)
      .then((share) => {
        this.markCard(id, { sharing: false })
        const poster = this.selectComponent('#poster')
        if (poster) {
          poster.draw(view.raw, share)
        }
      })
      .catch(() => {
        this.markCard(id, { sharing: false })
      })
  },

  /** 删除:二次确认 → 软删 → 刷新(好友手里的链接同步打不开) */
  tapDelete(e) {
    const id = e.currentTarget.dataset.id
    const view = this.data.cards.find((c) => c.id === id)
    if (!view || view.deleting) return
    wx.showModal({
      title: '删除这张卡片？',
      content: '删掉后，朋友手里的分享链接也会一起失效，想清楚再删哦',
      confirmText: '删掉',
      confirmColor: '#c0564a',
      cancelText: '先留着',
      success: (r) => {
        if (!r.confirm) return
        this.markCard(id, { deleting: true })
        api
          .deleteMyCard(id)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'none' })
            this.load()
          })
          .catch(() => {
            this.markCard(id, { deleting: false })
          })
      },
    })
  },

  /** 局部更新单张卡的操作态(不打断整墙渲染) */
  markCard(id, patch) {
    this.setData({
      cards: this.data.cards.map((c) => (c.id === id ? Object.assign({}, c, patch) : c)),
    })
  },

  /** 空态 → 模板馆 */
  goTpl() {
    wx.switchTab({ url: '/pages/tpl/tpl' })
  },

  /** poster 组件 sharecfg 事件:app 级 ensureShareCfg + 页面 data 双落,转发立即可用 */
  onShareCfg(e) {
    const cfg = getApp().ensureShareCfg(e.detail || {})
    this.setData({ shareCfg: cfg })
  },

  /** 转发:有海报配置带 scene 直达访客页,否则默认首页 */
  onShareAppMessage() {
    const cfg = this.data.shareCfg
    if (cfg && cfg.pagePath) {
      return { title: cfg.title, path: cfg.pagePath, imageUrl: cfg.imageUrl || undefined }
    }
    return {
      title: '念家时 · 把重要的日子，记在心上',
      path: '/pages/days/days',
    }
  },
})
