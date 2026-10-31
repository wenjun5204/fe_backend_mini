/**
 * 值班表只读落地页(PRD 5.7 P1,契约 v1.9.1):
 * 被分享者打开——顶部「来自 XX 的值班表」+ 只读月历 + 「我也记值班」转化按钮。
 * 免鉴权(scene 高熵不可枚举),无登录态也能看;scene 无效给体面兜底文案。
 * 页面只读:月历可翻月,不可点格子、无编辑弹层。
 */
const dutyApi = require('../../utils/api-duty')

/** 周一为首的星期表头(与 duty 页一致) */
const WEEK_HEADS = ['一', '二', '三', '四', '五', '六', '日']

/** 班次标签:格子内横向两字标签(与 duty 页 tagLabel 同源) */
const SHIFT_TAGS = {
  early: { label: '早', cls: 'early' },
  mid: { label: '中', cls: 'mid' },
  night: { label: '夜', cls: 'night' },
  rest: { label: '休', cls: 'rest' },
}

function fmtMonth(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return y + '-' + m
}

/** 月历矩阵(周一首日):42 格,每格 {date,inMonth,day,tagLabel,tagClass,isToday} */
function buildGrid(month, shiftMap) {
  const parts = month.split('-').map(Number)
  const first = new Date(parts[0], parts[1] - 1, 1)
  // 周一为首:周日(0)归到上一格区,偏移 = (day+6)%7
  const offset = (first.getDay() + 6) % 7
  const start = new Date(parts[0], parts[1] - 1, 1 - offset)
  const today = new Date()
  const todayKey =
    today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-' + String(today.getDate()).padStart(2, '0')

  const cells = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const date = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
    const inMonth = fmtMonth(d) === month
    const shift = inMonth ? shiftMap[date] : ''
    const tag = shift ? SHIFT_TAGS[shift] : null
    cells.push({
      date,
      inMonth,
      day: d.getDate(),
      tagLabel: tag ? tag.label : '',
      tagClass: tag ? tag.cls : '',
      isToday: date === todayKey,
    })
  }
  return cells
}

function shiftMonth(month, delta) {
  const parts = month.split('-').map(Number)
  return fmtMonth(new Date(parts[0], parts[1] - 1 + delta, 1))
}

function monthTitle(month) {
  const parts = month.split('-')
  return parts[0] + ' 年 ' + Number(parts[1]) + ' 月'
}

Page({
  data: {
    scene: '',
    ownerName: '',
    month: '',
    monthTitle: '',
    weekHeads: WEEK_HEADS,
    cells: [],
    stats: { early: 0, mid: 0, night: 0, rest: 0 },
    loading: true,
    invalid: false,
  },

  /** 只读月历本地缓存:{monthKey: {map, stats}}(无编辑,缓存不过期) */
  monthCache: {},

  onLoad(options) {
    const scene = (options && options.scene) || ''
    if (!/^[0-9a-zA-Z]{8,12}$/.test(scene)) {
      this.setData({ loading: false, invalid: true })
      return
    }
    const month = fmtMonth(new Date())
    this.setData({ scene, month, monthTitle: monthTitle(month) })
    this.loadMonth(month).finally(() => this.setData({ loading: false }))
  },

  /** 拉某月只读数据(命中缓存不重复拉);失败落 invalid 兜底(请求层已 toast,对齐 visitor 页先例) */
  loadMonth(month) {
    if (this.monthCache[month]) {
      this.applyMonth(month, this.monthCache[month])
      return Promise.resolve()
    }
    return dutyApi
      .getDutySharedCalendar(this.data.scene, month)
      .then((r) => {
        const map = {}
        ;(r.shifts || []).forEach((s) => {
          map[s.date] = s.shift
        })
        const cached = { map, stats: r.stats || { early: 0, mid: 0, night: 0, rest: 0 } }
        this.monthCache[month] = cached
        this.applyMonth(month, cached)
        this.setData({ ownerName: r.ownerName || '家人' })
      })
      .catch(() => {
        // scene 无效/已被注销:体面兜底视图(唯一入口只剩转化按钮)
        this.setData({ invalid: true })
      })
  },

  applyMonth(month, cached) {
    this.setData({
      month,
      monthTitle: monthTitle(month),
      cells: buildGrid(month, cached.map),
      stats: cached.stats,
    })
  },

  prevMonth() {
    this.loadMonth(shiftMonth(this.data.month, -1))
  },

  nextMonth() {
    this.loadMonth(shiftMonth(this.data.month, 1))
  },

  /** 转化按钮:进值班 Tab(未登录/新用户会被 ensureLogin 引导) */
  goDuty() {
    wx.switchTab({ url: '/pages/duty/duty' })
  },

  /** 二次转发:家人转家人,带同 scene 继续只读(不落首页,保住"看排班"语义) */
  onShareAppMessage() {
    return {
      title: this.data.ownerName ? this.data.ownerName + '的值班表' : '一份值班表',
      path: '/pages/duty-view/duty-view?scene=' + this.data.scene,
    }
  },
})
