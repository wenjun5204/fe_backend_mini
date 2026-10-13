/**
 * 模板馆 Tab(GET /api/templates 驱动,零硬编码模板文案):
 * 顶部节日卡横滑由 GET /api/festivals/due 驱动(0-7 天到期清单,服务端算 daysUntil;
 * 拉取失败回落模板 festivalDate 本地计算)→ 分类过滤 chips → 两列模板网格
 * → live 项点击弹半屏预览弹层(迷你成品卡按 kind 渐变渲染 + 特性标签)
 * 「🔔 上线提醒我」真实预约链路(v1.8.0):POST /api/festivals/reservations,
 * 再点 DELETE 取消(按钮态已预约/未预约);已预约态由 GET /api/festivals/reservations
 * 恢复;BIRTHDAY_CARD(live 无 makerPage)主按钮引导至日子 Tab(福卡唯一入口=日子详情)。
 * 模板缓存 storage njs_templates 10 分钟,列表秒开;预约/到期清单每次 onShow 实时拉。
 */
const { ensureLogin } = require('../../utils/request')
const { listTemplates } = require('../../utils/api-cards')
const fam = require('../../utils/api-family')
const { CATEGORY_TABS, BG_CLASS } = require('../../utils/cards-data')

const CACHE_KEY = 'njs_templates'
const CACHE_TTL = 10 * 60 * 1000
/** 模板收藏(「先收藏」仍为前端本地,设计 §2.4;上线预约已切真实 API) */
const FAV_KEY = 'njs_tpl_fav'

/** 距节日天数(负数=已过;仅 due 拉取失败时的本地回落算法) */
function daysTo(dateStr) {
  if (!dateStr) return -1
  const d = new Date(dateStr + 'T12:00:00')
  const t = new Date()
  t.setHours(12, 0, 0, 0)
  return Math.round((d - t) / 86400000)
}

/** TemplateInfo → 展示视图(字段全来自接口,不在前端写死模板名/文案) */
function tplView(t) {
  const m = t.previewMeta || {}
  return {
    key: t.key,
    name: t.name,
    category: t.category,
    live: !!t.live,
    makerPage: t.makerPage || '',
    festivalDate: t.festivalDate || '',
    festivalLabel: t.festivalLabel || '',
    festivalName: stripEmoji(t.festivalLabel || ''),
    countText: '',
    bgClass: BG_CLASS[m.backgroundKey] || 'bg-night',
    emoji: m.emoji || '💌',
    sub: m.sub || '',
    usedLabel: m.usedLabel || '',
    kicker: m.kicker || '',
    sampleText: m.sampleText || '',
    feats: m.feats || [],
    stats: [m.usedLabel, m.durationLabel, m.freeLabel].filter(Boolean).join('  ·  '),
    hint: m.hint || '',
    /** 网格角标(预留字段;最终值按预约态在 applyItems/setReserved 里算) */
    badge: '',
  }
}

function stripEmoji(text) {
  const pure = String(text).replace(/[^一-龥A-Za-z0-9·]/g, '').trim()
  return pure || text
}

Page({
  data: {
    rail: [],
    tabs: CATEGORY_TABS,
    activeTab: '',
    grid: [],
    allItems: [],
    loading: true,
    /** templateKey → true(已预约上线提醒;GET /api/festivals/reservations 恢复) */
    reservedMap: {},
    // 预览弹层
    sheetOpen: false,
    pv: null,
  },

  onShow() {
    this.load()
  },

  onPullDownRefresh() {
    try {
      wx.removeStorageSync(CACHE_KEY)
    } catch (e) {
      // 清缓存失败也照常重拉
    }
    this.load()
  },

  load() {
    ensureLogin()
      .catch(() => {})
      .then(() => {
        const cached = this.readCache()
        const tplPromise = cached
          ? Promise.resolve(cached)
          : listTemplates().then((res) => {
              const items = (res && res.items) || []
              this.writeCache(items)
              return items
            })
        // 模板走 10 分钟缓存;到期清单与我的预约每次实时(便宜且天天变)
        return Promise.all([
          tplPromise,
          fam.listDueFestivals().catch(() => null),
          fam.listMyReservations().catch(() => null),
        ]).then(([items, due, mine]) => {
          this.applyItems(items, due, mine)
          wx.stopPullDownRefresh()
        })
      })
      .catch(() => {
        wx.stopPullDownRefresh()
        this.setData({ loading: false })
      })
  },

  readCache() {
    try {
      const box = wx.getStorageSync(CACHE_KEY)
      if (box && box.items && Date.now() - box.t < CACHE_TTL) {
        return box.items
      }
    } catch (e) {
      // 读缓存失败走网络
    }
    return null
  },

  writeCache(items) {
    try {
      wx.setStorageSync(CACHE_KEY, { t: Date.now(), items })
    } catch (e) {
      // 写缓存失败不阻断
    }
  },

  /** 网格角标:节日名 > 已预约 > 预约 */
  badgeOf(v, reserved) {
    return v.festivalLabel || (!v.live ? (reserved ? '已预约' : '预约') : '')
  },

  /** 预览弹层主按钮文案(按 live/makerPage/预约态) */
  btnTextOf(v, reserved) {
    if (v.live) {
      return v.makerPage ? '✨ 开始制作' : '🎂 去做生日福卡'
    }
    return reserved ? '🔔 已预约 · 再点一下取消' : '🔔 上线提醒我'
  },

  /** 预约态 → 视图(角标/按钮文案随预约态翻转) */
  withReserved(views, reservedMap) {
    return views.map((v) => {
      const reserved = !!reservedMap[v.key]
      return Object.assign({}, v, {
        badge: this.badgeOf(v, reserved),
        btnText: this.btnTextOf(v, reserved),
      })
    })
  },

  applyItems(items, due, mine) {
    const reservedMap = {}
    ;((mine && mine.items) || []).forEach((r) => {
      reservedMap[r.templateKey] = true
    })
    const views = this.withReserved(items.map(tplView), reservedMap)
    this.setData({
      reservedMap,
      rail: this.railOf(views, due),
      allItems: views,
      grid: this.filterGrid(views, this.data.activeTab),
      loading: false,
    })
  },

  /**
   * 节日横滑:GET /api/festivals/due(0-7 天,服务端 daysUntil)为准;
   * 拉取失败(due=null)回落模板 festivalDate 本地计算,保持横滑可用。
   */
  railOf(views, due) {
    const byKey = {}
    views.forEach((v) => {
      byKey[v.key] = v
    })
    if (due) {
      return (due.items || [])
        .filter((d) => byKey[d.templateKey])
        .sort((a, b) => a.daysUntil - b.daysUntil)
        .map((d) => {
          const v = byKey[d.templateKey]
          return {
            key: d.templateKey,
            bgClass: v.bgClass,
            emoji: d.emoji || v.emoji,
            festivalName: stripEmoji(d.festivalLabel || ''),
            name: v.name,
            usedLabel: v.usedLabel,
            countText: d.daysUntil === 0 ? '就是今天' : d.daysUntil + ' 天后',
          }
        })
    }
    return views
      .filter((v) => v.festivalDate && daysTo(v.festivalDate) >= 0)
      .sort((a, b) => daysTo(a.festivalDate) - daysTo(b.festivalDate))
      .map((v) => {
        const n = daysTo(v.festivalDate)
        return Object.assign({}, v, { countText: n === 0 ? '就是今天' : n + ' 天后' })
      })
  },

  filterGrid(views, cat) {
    return cat ? views.filter((v) => v.category === cat) : views
  },

  switchTab(e) {
    const key = e.currentTarget.dataset.key || ''
    this.setData({
      activeTab: key,
      grid: this.filterGrid(this.data.allItems, key),
    })
  },

  openPreview(e) {
    const key = e.currentTarget.dataset.key
    const view = this.data.allItems.find((v) => v.key === key)
    if (!view) return
    this.setData({ sheetOpen: true, pv: view })
  },

  closeSheet() {
    this.setData({ sheetOpen: false })
  },

  /**
   * 主按钮分流:
   * - live 有制作页 → 跳编辑页;
   * - live 无制作页(BIRTHDAY_CARD)→ 日子 Tab 引导(福卡唯一入口=日子详情,裁定修订);
   * - live=false → 真实预约/取消(POST / DELETE /api/festivals/reservations,幂等)
   */
  pvGo() {
    const pv = this.data.pv
    if (!pv) return
    if (pv.live) {
      if (pv.makerPage) {
        this.closeSheet()
        wx.navigateTo({
          url: '/' + pv.makerPage,
          fail: () => {
            wx.showToast({ title: '制作页马上就来，先逛逛别的', icon: 'none' })
          },
        })
        return
      }
      this.closeSheet()
      wx.switchTab({ url: '/pages/days/days' })
      wx.showToast({ title: '选一个生日日子，随时能做福卡', icon: 'none', duration: 2400 })
      return
    }
    if (this.data.reservedMap[pv.key]) {
      this.cancelReservation(pv)
    } else {
      this.reserve(pv)
    }
  },

  /** 🔔 上线提醒我:POST /api/festivals/reservations(templateKey,幂等) */
  reserve(pv) {
    if (this._busy) return
    this._busy = true
    fam
      .reserveFestival(pv.key)
      .then(() => {
        this._busy = false
        this.setReserved(pv.key, true)
        wx.showToast({ title: '已预约 · 上线时提醒你', icon: 'none' })
      })
      .catch(() => {
        // 40000/40100 文案由网络层统一 toast
        this._busy = false
      })
  },

  /** 再点取消:DELETE /api/festivals/reservations/{templateKey}(幂等) */
  cancelReservation(pv) {
    if (this._busy) return
    this._busy = true
    fam
      .cancelReservation(pv.key)
      .then(() => {
        this._busy = false
        this.setReserved(pv.key, false)
        wx.showToast({ title: '已取消预约，想约再点一下', icon: 'none' })
      })
      .catch(() => {
        this._busy = false
      })
  },

  /** 预约态翻转:reservedMap + 网格角标 + 弹层按钮文案同步更新 */
  setReserved(key, on) {
    const reservedMap = Object.assign({}, this.data.reservedMap)
    if (on) {
      reservedMap[key] = true
    } else {
      delete reservedMap[key]
    }
    const patch = (views) => this.withReserved(views, reservedMap)
    const pv =
      this.data.pv && this.data.pv.key === key ? patch([this.data.pv])[0] : this.data.pv
    this.setData({
      reservedMap,
      allItems: patch(this.data.allItems),
      grid: patch(this.data.grid),
      pv,
    })
  },

  /** 次按钮:先收藏(live 与预约态都可收藏) */
  pvLater() {
    const pv = this.data.pv
    if (!pv) return
    this.markFav(pv)
    this.closeSheet()
    wx.showToast({ title: '已收藏，在「我的」里能找到', icon: 'none' })
  },

  markFav(pv) {
    try {
      const favs = wx.getStorageSync(FAV_KEY) || {}
      favs[pv.key] = { name: pv.name, t: Date.now() }
      wx.setStorageSync(FAV_KEY, favs)
    } catch (e) {
      // 收藏失败不阻断
    }
  },
})
