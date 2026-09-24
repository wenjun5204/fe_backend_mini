const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

// 八方固定顺序(与契约 Gua 枚举一致)(与契约 BaguaStatusResponse.trigrams 一致)
const GUAS = [
  { key: 'QIAN', char: '🕊️', name: '云鹤' },
  { key: 'DUI', char: '🪷', name: '荷塘' },
  { key: 'LI', char: '🏮', name: '灯笼' },
  { key: 'ZHEN', char: '🌱', name: '春雷' },
  { key: 'XUN', char: '🎐', name: '风铃' },
  { key: 'KAN', char: '🐟', name: '锦鲤' },
  { key: 'GEN', char: '⛰️', name: '山景' },
  { key: 'KUN', char: '🌾', name: '花开' },
]

// 阵图节点圆环定位(自正上方顺时针)
const NODE_OFFSET = 236 // rpx,节点圆心到阵图中心的距离

function nodeStyle(i) {
  return 'transform: rotate(' + i * 45 + 'deg) translateY(-' + NODE_OFFSET + 'rpx) rotate(' + -i * 45 + 'deg)'
}

Page({
  data: {
    loadFailed: false,
    familyName: '',
    litCount: 0,
    nodes: [],
    detail: null, // 福位详情弹层
    // 圆满庆祝
    showGrand: false,
    grandNodes: [],
    sparks: [],
    grandDate: '',
  },

  async onLoad() {
    await ensureLogin().catch(() => {})
    this.refresh()
  },

  onRetry() {
    this.setData({ loadFailed: false })
    ensureLogin().catch(() => {}).then(() => this.refresh())
  },

  /** 拉取家族集福阵进度(GET /api/bagua/status,服务端推导,只读) */
  async refresh() {
    this.setData({ loadFailed: false })
    try {
      const results = await Promise.all([
        api.getBaguaStatus(),
        api.getMyFamily().catch(() => null), // 仅取家族名展示在中央位,失败静默
      ])
      const status = results[0]
      const family = results[1]
      const byKey = {}
      ;(status.trigrams || []).forEach((t) => {
        byKey[t.gua] = t
      })
      const nodes = GUAS.map((g, i) => {
        const t = byKey[g.key] || {}
        // 服务端 hint 展示净化:统一展示用词,前端兜底
        const hint = (t.hint || '').replace(/\u7b7e/g, '\u5361')
        return {
          key: g.key,
          char: g.char,
          name: g.name,
          lit: !!t.lit,
          current: t.current || 0,
          target: t.target || 0,
          hint,
          style: nodeStyle(i),
        }
      })
      this.setData({
        nodes,
        litCount: status.litCount || 0,
        familyName: (family && family.familyName) || '全家',
      })
      // 8 方全亮且未庆祝过:播放圆满庆祝动效,结束后调 POST /api/bagua/celebrate
      if (status.complete && !status.celebrated) {
        this.showGrand()
      }
    } catch (e) {
      // 未加入家族/网络失败:给重试入口
      this.setData({ loadFailed: true })
    }
  },

  /** 点节点:弹进度详情(current/target 进度条 + hint) */
  onNodeTap(e) {
    const index = e.currentTarget.dataset.index
    const node = this.data.nodes[index]
    if (!node) {
      return
    }
    const pct = node.target > 0 ? Math.min(100, Math.round(node.current / node.target * 100)) : 0
    this.setData({
      detail: {
        char: node.char,
        name: node.name,
        lit: node.lit,
        current: node.current,
        target: node.target,
        hint: node.hint,
        pct,
        remain: Math.max(0, node.target - node.current),
      },
    })
  },

  closeDetail() {
    this.setData({ detail: null })
  },

  /** 圆满庆祝:旋转光环 + 粒子(从原型移植) */
  showGrand() {
    if (this.data.showGrand) {
      return
    }
    const sparks = []
    for (let i = 0; i < 18; i++) {
      sparks.push({
        id: i,
        left: (8 + Math.random() * 84).toFixed(1),
        top: (30 + Math.random() * 60).toFixed(1),
        delay: (Math.random() * 2.4).toFixed(2),
      })
    }
    const grandNodes = GUAS.map((g, i) => ({
      key: g.key,
      char: g.char,
      style: 'transform: rotate(' + i * 45 + 'deg) translateY(-' + NODE_OFFSET + 'rpx) rotate(' + -i * 45 + 'deg)',
    }))
    const d = new Date()
    this.setData({
      showGrand: true,
      sparks,
      grandNodes,
      grandDate: d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 达成',
    })
  },

  /** 庆祝动效结束后:标记圆满(POST /api/bagua/celebrate,幂等;失败静默,下次进入再补) */
  async closeGrand() {
    if (!this.data.showGrand) {
      return
    }
    this.setData({ showGrand: false })
    try {
      await api.celebrateBagua()
    } catch (e) {
      // 静默:服务端幂等,下次进入会再提示
    }
  },

  /** 分享集福阵给家人 */
  onShareAppMessage() {
    return {
      title: (this.data.familyName || '我们家') + '的集福阵已点亮 ' + this.data.litCount + '/8 方，一起点亮吧！',
      path: '/pages/card/card',
    }
  },
})
