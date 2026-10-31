/**
 * 值班 Tab(第 5 个 Tab,设计 v2.3 §2.2)
 * 今日状态卡(今日班次+下次值班倒计时+提醒入口)+ 班次图例 + 月历(周一首日/翻月/单日编辑弹层)
 * + 本月统计条 + 主按钮批量排班(5 预设规则/覆盖二次确认/365 天截断提示)。
 * 月历按月懒加载:首屏当月 ±1 月共 3 次并发,翻月命中缓存直接渲染,失败局部 loading+重试。
 */
const { ensureLogin } = require('../../utils/request')
const dutyApi = require('../../utils/api-duty')

/** 班次元数据:单字标签+PRD 配色+时段文案(图例/弹层共用) */
const SHIFT_META = {
  early: { label: '早', name: '早班', range: '08:00-17:00', color: '#ff4d6d', tagClass: 'tag-early' },
  mid: { label: '中', name: '中班', range: '12:00-24:00', color: '#e8943a', tagClass: 'tag-mid' },
  night: { label: '夜', name: '夜班', range: '20:00-次日08:00', color: '#3d4a5c', tagClass: 'tag-night' },
  rest: { label: '休', name: '休班', range: '好好休息', color: '#c8cdd3', tagClass: 'tag-rest' },
}

/** 5 预设规则 chip(批量排班弹层;说明文案对齐 PRD §5.4) */
/**
 * 5 预设规则(单行选项):name 主文案 + seq 循环序列可视化预览(色块点)。
 * weekday 无循环序列,seq 为空走 desc 文案。
 */
const RULES = [
  {
    type: 'daily', name: '每天早班', seq: ['early'],
    desc: '天天都是早班',
  },
  {
    type: 'on1off1', name: '做一休一', seq: ['early', 'rest'],
    desc: '上一天休一天',
  },
  {
    type: 'on2off2', name: '做二休二', seq: ['early', 'early', 'rest', 'rest'],
    desc: '上两天休两天',
  },
  {
    type: 'emnr', name: '早中夜休轮着来', seq: ['early', 'mid', 'night', 'rest'],
    desc: '四班三运转',
  },
  {
    type: 'weekday', name: '每周五值班', seq: [],
    desc: '每周固定一天早班',
  },
]

/** 提醒时机选项(默认 advance_1d) */
const TIMINGS = [
  { value: 'advance_1d', name: '提前 1 天', desc: '中午 12:00 提醒您' },
  { value: 'same_day_08', name: '当天 8 点', desc: '上班早上提醒您' },
  { value: 'advance_2h', name: '提前 2 小时', desc: '临上班前提醒您' },
]

/** 排班时长快捷 chip:点选后按开始日期自动算截止(整月=下月月末) */
const SPAN_CHIPS = [
  { days: 7, label: '排一周' },
  { days: 14, label: '排两周' },
  { days: 30, label: '排一个月' },
  { days: 90, label: '排三个月' },
]

const WEEK_HEADS = ['一', '二', '三', '四', '五', '六', '日']

/** Date → '2026-11-05' */
function fmtDate(d) {
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const day = d.getDate()
  return y + '-' + (m < 10 ? '0' + m : m) + '-' + (day < 10 ? '0' + day : day)
}

/** Date → '2026-11' */
function fmtMonth(d) {
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  return y + '-' + (m < 10 ? '0' + m : m)
}

/** '2026-11' → 标题「2026年11月」 */
function monthTitle(month) {
  const parts = month.split('-')
  return parts[0] + '年' + Number(parts[1]) + '月'
}

/**
 * 月历矩阵(周一首日):42 格,每格 {date,inMonth,day,shift?,tagClass,isToday,isFuture}
 * 前后月补位格 inMonth=false(置灰不可点);班次从缓存 map 取。
 */
function buildGrid(month, shiftMap) {
  const parts = month.split('-').map(Number)
  const first = new Date(parts[0], parts[1] - 1, 1)
  // 周一=0 的偏移(getDay(): 日=0..六=6 → 周一为首时 offset=(day+6)%7)
  const offset = (first.getDay() + 6) % 7
  const gridStart = new Date(first)
  gridStart.setDate(first.getDate() - offset)
  const today = fmtDate(new Date())
  const cells = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    const date = fmtDate(d)
    const inMonth = fmtMonth(d) === month
    const shift = inMonth ? shiftMap[date] : ''
    const meta = shift ? SHIFT_META[shift] : null
    cells.push({
      date,
      inMonth,
      day: d.getDate(),
      shift: shift || '',
      tagLabel: meta ? meta.label : '',
      tagClass: meta ? meta.tagClass : '',
      isToday: date === today,
    })
  }
  return cells
}

/** 前一月/后一月的 month key */
function shiftMonth(month, delta) {
  const parts = month.split('-').map(Number)
  const d = new Date(parts[0], parts[1] - 1 + delta, 1)
  return fmtMonth(d)
}

/** 下月 1 号/月末(批量排班默认值) */
function nextMonthBounds() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  const end = new Date(now.getFullYear(), now.getMonth() + 2, 0)
  return { start: fmtDate(start), end: fmtDate(end) }
}

Page({
  data: {
    loading: true,
    // —— 今日状态卡 ——
    todayText: '',
    todaySub: '',
    nextDutyText: '',
    remindOn: false,
    // —— 月历 ——
    month: '',
    monthTitle: '',
    weekHeads: WEEK_HEADS,
    cells: [],
    stats: { early: 0, mid: 0, night: 0, rest: 0 },
    monthLoading: false,
    // —— 单日编辑弹层 ——
    daySheet: null, // {date, label, shift, options:[{key,name,range,tagClass,active}]}
    // —— 批量排班弹层 ——
    batchSheet: null, // {startDate,endDate,rules,activeRule}
    // —— 提醒弹层 ——
    remindSheet: null, // {timings,timing,enabled}
    rules: RULES,
    timings: TIMINGS,
    spanChips: SPAN_CHIPS,
    shiftLabel: { early: '早', mid: '中', night: '夜', rest: '休' },
  },

  /** 月历缓存:{monthKey: {map:{date:shift}, stats}} */
  monthCache: {},

  onLoad() {
    // 登录失败(后端未起/token 失效)也要收口 loading:refreshAll 内部各自 catch,
    // 兜底文案呈现,绝不让全屏遮罩把页面点击永久挡死
    ensureLogin()
      .catch(() => null)
      .then(() => this.refreshAll())
  },

  onShow() {
    // 从其他页回来时轻刷新状态卡(月历缓存继续用,落库操作在页面内自会失效缓存)
    if (!this.data.loading) {
      this.loadToday()
    }
  },

  /**
   * 转发分享(PRD 5.7 P1 第一步):标题带当前月,缩略图用默认页面截图。
   * 被分享者先落首页;只读月历落地页属第二步,需先走契约 Stage 2。
   */
  onShareAppMessage() {
    const m = this.data.month ? Number(this.data.month.split('-')[1]) : new Date().getMonth() + 1
    return {
      title: '我' + m + '月的值班表',
      path: '/pages/days/days',
    }
  },

  /** 首屏:状态卡 + 当月/前月/后月 3 并发;任一失败不卡死 loading(遮罩会挡住整页点击) */
  refreshAll() {
    const now = new Date()
    const month = fmtMonth(now)
    this.setData({ loading: true, month, monthTitle: monthTitle(month) })
    Promise.all([
      this.loadToday().catch(() => null),
      this.loadMonth(month).catch(() => null),
      this.loadMonth(shiftMonth(month, -1)).catch(() => null),
      this.loadMonth(shiftMonth(month, 1)).catch(() => null),
    ]).then(() => this.setData({ loading: false }))
  },

  /** 状态卡:今日班次 + 下次值班 + 提醒状态;失败给兜底文案(具体报错 toast 由 request 层统一弹) */
  loadToday() {
    return dutyApi.getDutyToday().then((t) => {
      const meta = t.todayShift ? SHIFT_META[t.todayShift] : null
      const todayText = meta ? '今日' + meta.name : '今日无排班'
      const todaySub = meta
        ? (t.todayShift === 'rest' ? '😴 ' : '') + meta.range
        : '点下面的日历,先记一天班试试'
      let nextDutyText = '近期暂无值班安排'
      if (t.nextDuty) {
        const nextMeta = SHIFT_META[t.nextDuty.shift] || {}
        const parts = t.nextDuty.date.split('-')
        nextDutyText =
          '距下次值班还有 ' + t.nextDuty.daysUntil + ' 天 · ' +
          Number(parts[1]) + '月' + Number(parts[2]) + '日 ' + nextMeta.name
      }
      this.setData({
        todayText,
        todaySub,
        nextDutyText,
        remindOn: !!(t.remind && t.remind.enabled),
      })
    }).catch(() => {
      // 兜底:状态卡拿不到数据时也给可用的界面,不把整页 loading 卡死
      this.setData({
        todayText: '今日班次没读到',
        todaySub: '稍后再进值班页试试',
        nextDutyText: '',
      })
    })
  },

  /** 拉单月月历(命中缓存直接渲染;失败局部 loading+重试) */
  loadMonth(month) {
    const cached = this.monthCache[month]
    if (cached) {
      if (month === this.data.month) this.renderMonth(month)
      return Promise.resolve()
    }
    if (month === this.data.month) this.setData({ monthLoading: true })
    return dutyApi.getDutyCalendar(month)
      .then((cal) => {
        const map = {}
        ;(cal.shifts || []).forEach((s) => { map[s.date] = s.shift })
        this.monthCache[month] = { map, stats: cal.stats || { early: 0, mid: 0, night: 0, rest: 0 } }
        if (month === this.data.month) this.renderMonth(month)
      })
      .catch(() => {
        // 翻月加载失败:局部 loading 标记,页面保留重试入口,不阻塞全局
        if (month === this.data.month) this.setData({ monthLoading: true })
      })
  },

  /** 渲染当前 data.month 的格子与统计 */
  renderMonth(month) {
    const cached = this.monthCache[month] || { map: {}, stats: { early: 0, mid: 0, night: 0, rest: 0 } }
    const stats = cached.stats
    this.setData({
      cells: buildGrid(month, cached.map),
      stats,
      hasAnyShift: stats.early + stats.mid + stats.night + stats.rest > 0,
      monthLoading: false,
    })
  },

  /** ‹ 翻上月 */
  prevMonth() {
    this.gotoMonth(shiftMonth(this.data.month, -1))
  },

  /** › 翻下月 */
  nextMonth() {
    this.gotoMonth(shiftMonth(this.data.month, 1))
  },

  /** 翻月(缓存优先,未命中拉取;失败保持当前页并局部 loading) */
  gotoMonth(month) {
    this.setData({ month, monthTitle: monthTitle(month) })
    if (this.monthCache[month]) {
      this.renderMonth(month)
      return
    }
    this.setData({ cells: [], monthLoading: true })
    this.loadMonth(month)
  },

  /** 月历加载失败的重试按钮 */
  retryMonth() {
    const month = this.data.month
    delete this.monthCache[month]
    this.setData({ monthLoading: true })
    this.loadMonth(month)
  },

  // ================= 单日编辑弹层 =================

  /** 点日期格:非本月补位格不可点 */
  onCellTap(e) {
    const cell = e.currentTarget.dataset.cell
    if (!cell || !cell.inMonth) return
    const options = Object.keys(SHIFT_META).map((key) => ({
      key,
      name: SHIFT_META[key].name,
      range: SHIFT_META[key].range,
      tagClass: SHIFT_META[key].tagClass,
      active: cell.shift === key,
    }))
    const parts = cell.date.split('-')
    this.setData({
      daySheet: {
        date: cell.date,
        label: Number(parts[1]) + '月' + Number(parts[2]) + '日',
        hasShift: !!cell.shift,
        options,
      },
    })
  },

  closeDaySheet() {
    this.setData({ daySheet: null })
  },

  /** 四班次点选即保存 */
  onShiftPick(e) {
    const key = e.currentTarget.dataset.key
    const sheet = this.data.daySheet
    if (!sheet || !key) return
    dutyApi.setDutyShift(sheet.date, key).then(() => {
      const meta = SHIFT_META[key]
      wx.showToast({ title: '已设 ' + meta.name, icon: 'none' })
      this.closeDaySheet()
      this.afterShiftChanged(sheet.date)
    })
  },

  /** 「清除这一天」(幂等;重新应用规则会回填) */
  onClearDay() {
    const sheet = this.data.daySheet
    if (!sheet) return
    dutyApi.clearDutyShift(sheet.date).then(() => {
      wx.showToast({ title: '已清除', icon: 'none' })
      this.closeDaySheet()
      this.afterShiftChanged(sheet.date)
    })
  },

  /** 落库后:失效受影响月份缓存+重拉该月与状态卡 */
  afterShiftChanged(date) {
    const month = date.substring(0, 7)
    delete this.monthCache[month]
    this.loadMonth(month)
    this.loadToday()
  },

  // ================= 批量排班弹层 =================

  openBatchSheet() {
    const bounds = nextMonthBounds()
    this.setData({
      batchSheet: {
        startDate: bounds.start,
        endDate: bounds.end,
        pickedRule: 'on1off1',
        pickedSpan: 0,
      },
    })
  },

  closeBatchSheet() {
    this.setData({ batchSheet: null })
  },

  onStartDateChange(e) {
    // 手改开始日期:截止日跟着平移(保持原天数跨度),并取消快捷 chip 选中
    const sheet = this.data.batchSheet
    if (!sheet) return
    const days = dateSpanDays(sheet.startDate, sheet.endDate)
    const start = e.detail.value
    const end = days > 0 ? addDays(start, days) : sheet.endDate
    this.setData({
      'batchSheet.startDate': start,
      'batchSheet.endDate': end,
      'batchSheet.pickedSpan': 0,
    })
  },

  onEndDateChange(e) {
    this.setData({ 'batchSheet.endDate': e.detail.value, 'batchSheet.pickedSpan': 0 })
  },

  /** 快捷时长 chip:按开始日期自动算截止(如 排一周=开始+6 天) */
  onSpanPick(e) {
    const sheet = this.data.batchSheet
    const days = Number(e.currentTarget.dataset.days)
    if (!sheet || !days) return
    this.setData({
      'batchSheet.pickedSpan': days,
      'batchSheet.endDate': addDays(sheet.startDate, days - 1),
    })
  },

  onRulePick(e) {
    this.setData({ 'batchSheet.pickedRule': e.currentTarget.dataset.type })
  },

  /** 应用:范围内已有排班先二次确认(覆盖提示),再提交 */
  onApplyRule() {
    const sheet = this.data.batchSheet
    if (!sheet) return
    if (sheet.endDate < sheet.startDate) {
      wx.showToast({ title: '截止日期不能早于开始日期', icon: 'none' })
      return
    }
    this.confirmOverwriteIfNeeded(sheet).then(() => this.doApplyRule(sheet))
  },

  /** 范围内已有排班时弹覆盖确认(PRD §5.4 覆盖提示;取消则不提交) */
  confirmOverwriteIfNeeded(sheet) {
    const cached = this.monthCache
    let hasExisting = false
    let cursor = sheet.startDate
    while (cursor <= sheet.endDate) {
      const map = (cached[cursor.substring(0, 7)] || {}).map || {}
      if (map[cursor]) {
        hasExisting = true
        break
      }
      cursor = nextDate(cursor)
    }
    if (!hasExisting) return Promise.resolve()
    return new Promise((resolve) => {
      wx.showModal({
        title: '覆盖已有排班?',
        content: '这个日期范围内已经记过班,应用新规则会把这些天的班全部替换掉。',
        confirmText: '覆盖',
        cancelText: '再想想',
        success: (res) => {
          if (res.confirm) resolve()
        },
      })
    })
  },

  doApplyRule(sheet) {
    dutyApi.applyDutyRule(sheet.pickedRule, sheet.startDate, sheet.endDate).then((r) => {
      const ruleName = (RULES.find((x) => x.type === r.ruleType) || {}).name || ''
      wx.showToast({
        title: r.truncated
          ? '跨度太长,已按一年上限排 ' + r.appliedDays + ' 天'
          : '已按「' + ruleName + '」排 ' + r.appliedDays + ' 天 ✓',
        icon: 'none',
        duration: 2500,
      })
      this.closeBatchSheet()
      // 失效应用范围跨到的所有月份缓存,重拉当前月+状态卡
      this.invalidateMonthRange(sheet.startDate, r.endDate)
      this.loadMonth(this.data.month)
      this.loadToday()
    })
  },

  /** 清空这段日期的排班(批量撤销):二次确认后调范围清除;规则本体不动,重新应用会回填 */
  onClearRange() {
    const sheet = this.data.batchSheet
    if (!sheet) return
    if (sheet.endDate < sheet.startDate) {
      wx.showToast({ title: '截止日期不能早于开始日期', icon: 'none' })
      return
    }
    wx.showModal({
      title: '清空这段排班?',
      content: '从 ' + sheet.startDate + ' 到 ' + sheet.endDate + ' 已记的班会全部清掉。',
      confirmText: '清空',
      confirmColor: '#c0564a',
      cancelText: '再想想',
      success: (res) => {
        if (!res.confirm) return
        dutyApi.clearDutyShiftRange(sheet.startDate, sheet.endDate).then((r) => {
          wx.showToast({ title: '已清空 ' + r.clearedDays + ' 天', icon: 'none' })
          this.closeBatchSheet()
          this.invalidateMonthRange(sheet.startDate, r.to || sheet.endDate)
          this.loadMonth(this.data.month)
          this.loadToday()
        })
      },
    })
  },

  /** 失效 startDate..endDate 跨到的月份缓存 */
  invalidateMonthRange(startDate, endDate) {
    let month = startDate.substring(0, 7)
    const last = (endDate || startDate).substring(0, 7)
    while (month <= last) {
      delete this.monthCache[month]
      month = shiftMonth(month, 1)
    }
  },

  // ================= 提醒设置弹层 =================

  openRemindSheet() {
    this.setData({
      remindSheet: {
        timing: 'advance_1d',
        enabled: true,
        nightHint: false,
      },
    })
  },

  closeRemindSheet() {
    this.setData({ remindSheet: null })
  },

  onTimingPick(e) {
    const value = e.currentTarget.dataset.value
    this.setData({
      'remindSheet.timing': value,
      // 夜班建议:选了「当天 8 点」且今日/刚设班次含夜班时提示改「提前 2 小时」
      'remindSheet.nightHint': value === 'same_day_08' && this.hasNightShift(),
    })
  },

  onRemindToggle(e) {
    this.setData({ 'remindSheet.enabled': e.detail.value })
  },

  /** 今日或月内已见夜班(弱判断:今日卡文案含「夜班」或月统计 night>0) */
  hasNightShift() {
    return this.data.todayText.indexOf('夜班') >= 0 || this.data.stats.night > 0
  },

  /** 保存时机配置 → 请求订阅授权(拒绝静默)→ 登记授权 → 弱引导状态更新 */
  onRemindSave() {
    const sheet = this.data.remindSheet
    if (!sheet) return
    dutyApi.updateDutyRemindConfig(sheet.timing, sheet.enabled).then(() => {
      if (sheet.enabled) {
        this.trySubscribe()
      }
      wx.showToast({ title: sheet.enabled ? '提醒已开启' : '已关闭提醒', icon: 'none' })
      this.closeRemindSheet()
      this.setData({ remindOn: sheet.enabled })
    })
  },

  /** 订阅消息授权(模板 ID 占位;拒绝/失败均静默降级,不重复弹) */
  trySubscribe() {
    try {
      wx.requestSubscribeMessage({
        tmplIds: ['PLACEHOLDER_DUTY_TEMPLATE_ID'],
        success: () => {},
        fail: () => {},
      })
    } catch (e) {
      // 静默降级
    }
  },
})

/** '2026-11-30' → '2026-12-01' */
function nextDate(date) {
  const parts = date.split('-').map(Number)
  const d = new Date(parts[0], parts[1] - 1, parts[2] + 1)
  return fmtDate(d)
}

/** '2026-11-01' + N 天(含当天 N 天跨度即 +N-1) */
function addDays(date, n) {
  const parts = date.split('-').map(Number)
  const d = new Date(parts[0], parts[1] - 1, parts[2] + n)
  return fmtDate(d)
}

/** '2026-11-01'~'2026-11-10' 的天数跨度(含首尾,共 10 天) */
function dateSpanDays(start, end) {
  const a = start.split('-').map(Number)
  const b = end.split('-').map(Number)
  const days = Math.round((new Date(b[0], b[1] - 1, b[2]) - new Date(a[0], a[1] - 1, a[2])) / 86400000) + 1
  return days > 0 ? days : 0
}
