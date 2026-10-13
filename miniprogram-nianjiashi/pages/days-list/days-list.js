const { ensureLogin, getUser } = require('../../utils/request')
const { listDays } = require('../../utils/api-days')
const { viewOf, pad2 } = require('../../utils/days-data')

Page({
  data: {
    view: 'list',
    groups: [],
    // 日历视图
    calTitle: '',
    weekLabels: ['日', '一', '二', '三', '四', '五', '六'],
    cells: [],
    calSummary: '',
    // 当日事件弹层
    sheetShow: false,
    sheetTitle: '',
    sheetRows: [],
  },

  onLoad() {
    const now = new Date()
    this.calY = now.getFullYear()
    this.calM = now.getMonth()
    this.records = []
    this.myUserId = null
  },

  onShow() {
    this.load()
  },

  load() {
    ensureLogin()
      .catch(() => {})
      .then(() => listDays())
      .then((res) => {
        const items = (res && res.items) || []
        const user = getUser()
        this.myUserId = user ? user.userId : null
        this.records = items
        this.buildTimeline(items)
        this.buildCalendar()
      })
      .catch(() => {})
  },

  /* ===== 时间线视图(按落点月分组;TOGETHER 归「每天 · 相伴中」组) ===== */
  buildTimeline(items) {
    const views = items.map(viewOf)
    const nonTog = views.filter((v) => !v.isTogether && v.id)
    const byKey = {}
    const order = []
    nonTog.forEach((v) => {
      const raw = items.find((r) => r.id === v.id)
      if (!raw || !raw.nextDate) return
      const p = raw.nextDate.split('-')
      const key = p[0] + '-' + p[1]
      const name = Number(p[0]) + '年' + Number(p[1]) + '月'
      if (!byKey[key]) {
        byKey[key] = { key, name, rows: [] }
        order.push(key)
      }
      byKey[key].rows.push(v)
    })
    order.sort()
    const groups = order.map((k) => ({
      key: k,
      name: byKey[k].name,
      count: byKey[k].rows.length + ' 个日子',
      rows: byKey[k].rows,
    }))
    const together = views.filter((v) => v.isTogether)
    if (together.length) {
      groups.push({ key: 'everyday', name: '每天', count: '相伴中', rows: together })
    }
    this.setData({ groups })
  },

  /* ===== 日历视图(月网格,本人可见的日子都上日历;自己=实心点,家人创建=空心点) ===== */
  buildCalendar() {
    const y = this.calY
    const m = this.calM
    const monthStr = y + '-' + pad2(m + 1)
    const today = new Date()
    const byDate = {}
    this.records.forEach((r) => {
      if (r.kind === 'TOGETHER' || !r.nextDate) return
      // 口径与时间线一致:列表里本人可见的日子都上日历(自己创建=实心点,家人的=空心点)
      const own = this.myUserId != null && r.userId === this.myUserId
      if (r.nextDate.substring(0, 7) === monthStr) {
        ;(byDate[r.nextDate] = byDate[r.nextDate] || []).push({ record: r, own })
      }
    })
    const startWeek = new Date(y, m, 1).getDay()
    const daysInMonth = new Date(y, m + 1, 0).getDate()
    const cells = []
    let idx = 0
    for (let i = 0; i < startWeek; i++) {
      cells.push({ idx: idx++, blank: true, num: '', cls: 'other', dots: [], dateStr: '', has: false })
    }
    let eventDays = 0
    let todayHas = false
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = monthStr + '-' + pad2(d)
      const evs = byDate[dateStr]
      const isToday = today.getFullYear() === y && today.getMonth() === m && today.getDate() === d
      const dots = evs
        ? evs.map((e) => (e.record.kind === 'ANNIVERSARY' ? (e.own ? 'a' : 'a-hollow') : e.own ? 'b' : 'b-hollow'))
        : []
      let cls = ''
      if (isToday) cls = 'today'
      if (evs) {
        cls = isToday ? 'today has' : 'has'
        eventDays++
        if (isToday) todayHas = true
      }
      cells.push({ idx: idx++, blank: false, num: d, cls, dots, dateStr, has: !!evs })
    }
    const summary = eventDays
      ? '本月有 ' + eventDays + ' 天值得记住' + (todayHas ? ' · 今天有日子 🎉' : '')
      : '这个月暂无日子 · 可以前后翻翻，或去记一个'
    this.setData({
      calTitle: m + 1 + '月 ' + y,
      cells,
      calSummary: summary,
    })
  },

  calMove(e) {
    const delta = Number(e.currentTarget.dataset.delta)
    this.calM += delta
    if (this.calM < 0) {
      this.calM = 11
      this.calY--
    }
    if (this.calM > 11) {
      this.calM = 0
      this.calY++
    }
    this.buildCalendar()
  },

  switchView(e) {
    this.setData({ view: e.currentTarget.dataset.view })
  },

  /* 点有日子的日期 → 弹当日清单 */
  tapCell(e) {
    const cell = this.data.cells[Number(e.currentTarget.dataset.idx)]
    if (!cell || !cell.has) return
    const rows = []
    this.records.forEach((r) => {
      // 与日历标记口径一致:本人可见的日子都列出
      if (r.kind !== 'TOGETHER' && r.nextDate === cell.dateStr) {
        rows.push(viewOf(r))
      }
    })
    const p = cell.dateStr.split('-')
    this.setData({
      sheetShow: true,
      sheetTitle: Number(p[1]) + '月' + Number(p[2]) + '日',
      sheetRows: rows,
    })
  },

  closeSheet() {
    this.setData({ sheetShow: false })
  },

  openDetail(e) {
    this.setData({ sheetShow: false })
    wx.navigateTo({ url: '/pages/day-detail/day-detail?id=' + e.currentTarget.dataset.id })
  },
})
