/**
 * 访客落地页(免登录!整页零 ensureLogin——visitor 组 3 端点 noAuth,无 token 也能开)
 *
 * 双入口 onLoad:
 * - options.scene:分享 path(pages/visitor/visitor?scene=xx)带参直达
 * - options.q:扫普通二维码进入,decodeURIComponent 后从 URL 里取 scene 参数
 *   (utils/cards-data QR_LINK_MODE='url' 发布态,QR 内容 https://{QR_HOST}/p/{scene})
 * 无 scene / 格式不合法 → 空态引导去模板馆
 *
 * GET /api/visitor/cards/{scene} 展示公开部分(VisitorCardView,按 kind 组装渐变封面);
 * 隐藏内容区打码(渐隐 + 「🔒 点亮 3 颗心解锁」);点心 POST .../hearts(deviceId 取自
 * utils/device.js,逐颗点亮弹跳 + 「还差 N 颗 ❤️」提示;42900 点太快由网络层 toast);
 * hearts≥3 后 GET .../content 揭示(pop 去打码),深色转化 CTA(reLaunch 首页)。
 *
 * onShareAppMessage 恒返回不带 scene 的纯首页路径——防访客转发扩散他人 scene。
 */
const api = require('../../utils/api-cards')
const { getDeviceId } = require('../../utils/device')
const { fmtDot } = require('../../utils/cards-data')

/** scene 合法形态(与后端 SCENE_PATTERN 一致,前端先挡一层省一次网络) */
const SCENE_RE = /^[0-9a-zA-Z]{8,12}$/

/** 扫普通二维码:URL → scene 参数 */
function sceneFromQ(q) {
  let url = ''
  try {
    url = decodeURIComponent(q || '')
  } catch (e) {
    url = q || ''
  }
  const hit = url.match(/[?&]scene=([0-9a-zA-Z]{8,12})/)
  return hit ? hit[1] : ''
}

/** VisitorCardView → 渐变封面视图(公开部分;渐变 class 与模板馆同表) */
function posterViewOf(view) {
  const p = view.publicPayload || {}
  if (view.templateKind === 'DAYS_CARD') {
    return {
      bg: p.styleKey === 'NIGHT' ? 'bg-night' : p.styleKey === 'MINT' ? 'bg-mint' : 'bg-sunset',
      kicker: 'DAYS TOGETHER',
      emoji: '',
      big: String(p.daysTogether || 1),
      unit: ' 天',
      title: '',
      sub: '“从 ' + fmtDot(p.startDate) + ' 到今天”',
      sub2: (p.relation || '朋友') + ' 想让你看看这个数字',
    }
  }
  if (view.templateKind === 'CAPSULE') {
    return {
      bg: 'bg-mint',
      kicker: 'TIME CAPSULE',
      emoji: '⏳',
      big: String(p.daysUntilOpen || 0),
      unit: ' 天后开箱',
      title: '',
      sub: p.coverHint || '',
      sub2: '开箱日 ' + fmtDot(p.openDate),
    }
  }
  // LETTER(深夜星河):标题+第一段即公开钩子,全文留作解锁
  return {
    bg: 'bg-night',
    kicker: 'A LETTER TO MOM',
    emoji: '💌',
    big: '',
    unit: '',
    title: '写给' + (p.recipient || '妈妈') + '的信',
    sub: (p.paragraphs && p.paragraphs[0]) || '',
    sub2: p.signText || '',
  }
}

Page({
  data: {
    // 空态(noscene=无参数/gone=卡片不存在或已删)
    empty: false,
    emptyEmoji: '🖼️',
    emptyTitle: '',
    emptySub: '',
    // 卡片态
    scene: '',
    teaser: '',
    pv: null,
    hearts: [
      { on: false },
      { on: false },
      { on: false },
    ],
    popIdx: 0,
    lockSub: '点亮 3 颗心即可查看 · 无需注册',
    heartPending: false,
    // 解锁态
    unlocked: false,
    hiddenParas: [],
    hiddenSign: '',
    hiddenLine: '',
    hiddenText: '',
    hiddenPhoto: '',
    cta: null,
  },

  onLoad(options) {
    options = options || {}
    let scene = options.scene || ''
    if (!scene && options.q) {
      scene = sceneFromQ(options.q)
    }
    if (!SCENE_RE.test(scene)) {
      this.showEmpty(
        '🖼️',
        '这里没有卡片',
        '从朋友的分享链接或海报二维码进来，才能看到 TA 给你的卡片'
      )
      return
    }
    this.setData({ scene })
    this.load()
  },

  load() {
    const deviceId = getDeviceId()
    this._deviceId = deviceId
    api
      .getVisitorCard(this.data.scene, deviceId)
      .then((view) => {
        const teaser = view.lockedTeaser || ''
        this.setData({
          empty: false,
          teaser,
          pv: posterViewOf(view),
        })
        if (view.unlocked) {
          // 同设备二次进入:直接进解锁态
          this.applyHearts(view.hearts)
          this.fetchContent()
        } else {
          this.applyHearts(view.hearts)
        }
      })
      .catch(() => {
        // 40400 卡片不存在/已删(网络层已 toast),落空态可去模板馆逛
        this.showEmpty('🫧', '卡片走丢了', '它可能已被删除，先去模板馆逛逛别的吧')
      })
  },

  showEmpty(emoji, title, sub) {
    this.setData({
      empty: true,
      emptyEmoji: emoji,
      emptyTitle: title,
      emptySub: sub,
    })
  },

  /** 服务端 hearts 数 → 3 颗心的点亮视图 */
  applyHearts(count) {
    const n = Math.max(0, Math.min(3, count || 0))
    this.setData({
      hearts: [1, 2, 3].map((i) => ({ on: i <= n })),
    })
  },

  /** 点心:POST hearts → 逐颗点亮弹跳 + 剩余提示;满 3 颗取隐藏内容 */
  tapHeart() {
    if (this.data.unlocked) {
      wx.showToast({ title: '已经解锁啦，往下滑看看', icon: 'none' })
      return
    }
    if (this.data.heartPending || !this.data.scene) return
    this.setData({ heartPending: true })
    api
      .lightHeart(this.data.scene, this._deviceId)
      .then((state) => {
        const n = state.hearts || 0
        this.setData({
          hearts: [1, 2, 3].map((i) => ({ on: i <= n })),
          popIdx: n,
          lockSub: state.unlocked ? '已解锁 · 无需注册，看完就好' : '还差 ' + (3 - n) + ' 颗心就能看',
        })
        setTimeout(() => {
          this.setData({ popIdx: 0 })
        }, 320)
        if (state.unlocked) {
          wx.showToast({ title: '已解锁 ✨', icon: 'none' })
          this.fetchContent()
        } else if (state.remainingHint) {
          wx.showToast({ title: state.remainingHint, icon: 'none' })
        }
      })
      .catch(() => {
        // 42900 点太快(网络层已 toast「点太快啦，歇一下再点」),静默恢复可点
      })
      .then(() => {
        this.setData({ heartPending: false })
      })
  },

  /** 满心后取隐藏内容(UnlockedContentResponse → 揭示视图 + 转化 CTA) */
  fetchContent() {
    api
      .getVisitorContent(this.data.scene, this._deviceId)
      .then((res) => {
        const h = (res && res.hiddenContent) || {}
        this.setData({
          unlocked: true,
          lockSub: '已解锁 · 无需注册，看完就好',
          hiddenParas: h.paragraphs || [],
          hiddenSign: h.signText || '',
          hiddenLine: h.hiddenLine || '',
          hiddenText: h.contentText || '',
          hiddenPhoto: h.photoUrl || '',
          cta: (res && res.ctaCopy) || null,
        })
      })
      .catch(() => {
        // 40900 等异常:网络层已 toast,下次 onShow 重进可重试
      })
  },

  /** 深色转化 CTA:「打开念家时」→ 回首页(reLaunch 后首页自行拉起登录) */
  openApp() {
    wx.reLaunch({ url: '/pages/days/days' })
  },

  /** 空态 → 模板馆 */
  goTpl() {
    wx.switchTab({ url: '/pages/tpl/tpl' })
  },

  /**
   * 访客页转发:恒纯首页路径,不带他人 scene——
   * 防止访客二次转发扩散别人卡片的解锁链路(卡片只该到收卡人手里)
   */
  onShareAppMessage() {
    return {
      title: '念家时 · 把重要的日子，记在心上',
      path: '/pages/days/days',
    }
  },
})
