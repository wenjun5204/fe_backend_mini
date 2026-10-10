/**
 * 模板馆 Tab(GET /api/templates 驱动,零硬编码模板文案):
 * 顶部节日卡横滑(带 festivalDate 的项,本地倒计时)→ 分类过滤 chips → 两列模板网格
 * → live 项点击弹半屏预览弹层(迷你成品卡按 kind 渐变渲染 + 特性标签 + 开始制作/预约提醒)
 * 缓存 storage njs_templates 10 分钟,列表秒开(设计 §2.4)
 */
const { ensureLogin } = require('../../utils/request')
const { listTemplates } = require('../../utils/api-cards')
const { CATEGORY_TABS, BG_CLASS } = require('../../utils/cards-data')

const CACHE_KEY = 'njs_templates'
const CACHE_TTL = 10 * 60 * 1000
/** 模板收藏(预约态「预约提醒」仅前端本地,设计 §2.4) */
const FAV_KEY = 'njs_tpl_fav'

/** 距节日天数(负数=已过,横滑不展示已过节点) */
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
    /** 网格角标:节日名或「预约」 */
    badge: t.festivalLabel || (!t.live ? '预约' : ''),
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
    const cached = this.readCache()
    if (cached) {
      this.applyItems(cached)
      return
    }
    ensureLogin()
      .catch(() => {})
      .then(() => listTemplates())
      .then((res) => {
        const items = (res && res.items) || []
        this.writeCache(items)
        this.applyItems(items)
        wx.stopPullDownRefresh()
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

  applyItems(items) {
    const views = items.map(tplView)
    // 节日横滑:带 festivalDate 且未过期的项,按临近排序
    const rail = views
      .filter((v) => v.festivalDate && daysTo(v.festivalDate) >= 0)
      .sort((a, b) => daysTo(a.festivalDate) - daysTo(b.festivalDate))
      .map((v) => {
        const n = daysTo(v.festivalDate)
        v.countText = n === 0 ? '就是今天' : n + ' 天后'
        return v
      })
    this.setData({
      rail,
      allItems: views,
      grid: this.filterGrid(views, this.data.activeTab),
      loading: false,
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
    const pv = Object.assign({}, view, {
      btnText: view.live ? '✨ 开始制作' : '🔔 预约上线提醒',
      btnLater: '先收藏',
    })
    this.setData({ sheetOpen: true, pv })
  },

  closeSheet() {
    this.setData({ sheetOpen: false })
  },

  /** 主按钮:live 且有制作页 → 跳编辑页;live 无制作页 → 首页提醒提示;预约态 → 本地收藏 */
  pvGo() {
    const pv = this.data.pv
    if (!pv) return
    if (pv.live && pv.makerPage) {
      this.closeSheet()
      wx.navigateTo({
        url: '/' + pv.makerPage,
        fail: () => {
          wx.showToast({ title: '制作页马上就来，先逛逛别的', icon: 'none' })
        },
      })
      return
    }
    if (pv.live) {
      wx.showToast({ title: '生日当天会在首页提醒你', icon: 'none' })
      return
    }
    this.markFav(pv)
    wx.showToast({ title: '已预约 · 上线时提醒你', icon: 'none' })
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
