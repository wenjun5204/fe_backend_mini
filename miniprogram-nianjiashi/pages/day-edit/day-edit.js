const { ensureLogin } = require('../../utils/request')
const { createDay, updateDay, getDay, convertCalendar } = require('../../utils/api-days')
const { RELATIONS, EMOJI, KIND_LABEL, daysUntilOf, fmtDate } = require('../../utils/days-data')

/** 公历某月天数(取闰年算 2 月,与服务端「2 月 29 合法」口径一致) */
function solarDaysInMonth(month) {
  return new Date(2028, month, 0).getDate()
}

Page({
  data: {
    isEdit: false,
    dayId: null,
    // 关系
    relations: RELATIONS,
    relation: '妈妈',
    isCustom: false,
    customRelation: '',
    showCustom: false,
    // 类型
    kinds: [
      { key: 'BIRTHDAY', label: '生日' },
      { key: 'ANNIVERSARY', label: '纪念日' },
      { key: 'TOGETHER', label: '在一起 💕' },
    ],
    kind: 'BIRTHDAY',
    // 日历与月日
    cals: [
      { key: 'SOLAR', label: '公历' },
      { key: 'LUNAR', label: '农历' },
    ],
    calendarType: 'SOLAR',
    monthRange: [],
    dayRange: [],
    monthIndex: 0,
    dayIndex: 0,
    // 换算预览
    conv: { kicker: '下一次这个日子', main: '正在算…', sub: '' },
    convLoading: false,
    // 起始/纪念年份(仅纪念日与在一起显示;生日不收——隐私红线,后端仅存档不输出年龄)
    yearRange: [],
    yearIndex: 0,
    year: null,
    // 可见性
    visibility: 'FAMILY',
    // 提醒(默认关)
    reminderOptions: [
      { value: 7, label: '提前 7 天' },
      { value: 3, label: '提前 3 天' },
      { value: 0, label: '当天提醒' },
    ],
    reminderOn: { 7: false, 3: false, 0: false },
    saving: false,
  },

  onLoad(options) {
    const now = new Date()
    const monthRange = []
    for (let i = 1; i <= 12; i++) monthRange.push(i + ' 月')
    const yearRange = ['不记得 / 先不填']
    for (let y = now.getFullYear(); y >= 1900; y--) yearRange.push(y + ' 年')
    this.setData({ monthRange, monthIndex: now.getMonth(), yearRange })
    this.fillDays(now.getDate() - 1)

    if (options && options.id) {
      // 编辑模式:回填已有日子(提醒配置在详情页管理,此处不动)
      this.setData({ isEdit: true, dayId: Number(options.id) })
      wx.setNavigationBarTitle({ title: '编辑这个日子' })
      this.prefill(Number(options.id))
    } else {
      this.updateConv()
    }
  },

  prefill(id) {
    ensureLogin()
      .catch(() => {})
      .then(() => getDay(id))
      .then((r) => {
        const known = RELATIONS.indexOf(r.relation)
        this.setData({
          relation: r.relation,
          isCustom: known < 0,
          customRelation: known < 0 ? r.relation : '',
          showCustom: known < 0,
          kind: r.kind,
          calendarType: r.kind === 'TOGETHER' ? 'SOLAR' : r.calendarType,
          visibility: r.visibility,
          monthIndex: (r.month || 1) - 1,
          year: r.year || null,
          yearIndex: r.year ? (new Date().getFullYear() - r.year + 1) : 0,
        })
        this.fillDays((r.day || 1) - 1)
      })
      .catch(() => {})
  },

  fillDays(dayIndex) {
    const cal = this.data.calendarType
    const month = this.data.monthIndex + 1
    const count = cal === 'LUNAR' ? 30 : solarDaysInMonth(month)
    const dayRange = []
    for (let i = 1; i <= count; i++) dayRange.push(i + ' 日')
    const clamped = Math.min(dayIndex || 0, count - 1)
    this.setData({ dayRange, dayIndex: clamped < 0 ? 0 : clamped })
    this.updateConv()
  },

  /* ===== 关系 ===== */
  pickRelation(e) {
    this.setData({ relation: e.currentTarget.dataset.rel, isCustom: false, showCustom: false, customRelation: '' })
  },

  pickCustom() {
    this.setData({ isCustom: true, showCustom: true, relation: '' })
  },

  onCustomInput(e) {
    this.setData({ customRelation: e.detail.value })
  },

  /* ===== 类型 / 日历 / 月日 ===== */
  pickKind(e) {
    const kind = e.currentTarget.dataset.kind
    this.setData({ kind })
    if (kind !== 'TOGETHER') this.updateConv()
  },

  pickCal(e) {
    this.setData({ calendarType: e.currentTarget.dataset.cal })
    this.fillDays(this.data.dayIndex)
  },

  onMonthChange(e) {
    this.setData({ monthIndex: Number(e.detail.value) })
    this.fillDays(this.data.dayIndex)
  },

  onDayChange(e) {
    this.setData({ dayIndex: Number(e.detail.value) })
    this.updateConv()
  },

  /** 实时换算卡:复用既有 POST /api/calendar/convert(只算不存) */
  updateConv() {
    if (this.data.kind === 'TOGETHER') return
    const cal = this.data.calendarType
    const month = this.data.monthIndex + 1
    const day = this.data.dayIndex + 1
    this.setData({ convLoading: true, conv: { kicker: '下一次这个日子', main: '正在算…', sub: '' } })
    convertCalendar({ calendarType: cal, month, day })
      .then((res) => {
        if (!res || !res.solarDate) {
          this.setData({ convLoading: false, conv: { kicker: '下一次这个日子', main: '换算不到', sub: '请您换个日期' } })
          return
        }
        const until = daysUntilOf(res.solarDate)
        if (cal === 'SOLAR') {
          this.setData({
            convLoading: false,
            conv: {
              kicker: '下一次这个日子 · 农历',
              main: res.lunarText || '',
              sub: '对应公历 ' + fmtDate(res.solarDate) + ' · ' + until + ' 天后',
            },
          })
        } else {
          this.setData({
            convLoading: false,
            conv: {
              kicker: '下一次这个日子 · 公历',
              main: fmtDate(res.solarDate),
              sub: until + ' 天后',
            },
          })
        }
      })
      .catch(() => {
        // 错误提示由网络层统一 toast(农历三十等个别日期可能无落点)
        this.setData({ convLoading: false, conv: { kicker: '下一次这个日子', main: '换算不到', sub: '请您换个日期' } })
      })
  },

  /* ===== 提醒 ===== */
  toggleReminder(e) {
    const value = Number(e.currentTarget.dataset.value)
    const on = {}
    Object.keys(this.data.reminderOn).forEach((k) => {
      on[k] = this.data.reminderOn[k]
    })
    if (on[value]) {
      on[value] = false
      this.setData({ reminderOn: on })
      return
    }
    // 从全关到开启:先说明内容与频率(合规:订阅前说明)
    const noneOn = !on[7] && !on[3] && !on[0]
    if (noneOn) {
      wx.showModal({
        title: '开启日子提醒',
        content: '到了您选的时间，我们发一条消息提醒您。每人每天最多 3 条，家人看不到，也不会打扰您。',
        confirmText: '同意并开启',
        cancelText: '暂不开启',
        success: (res) => {
          if (res.confirm) {
            on[value] = true
            this.setData({ reminderOn: on })
            this.trySubscribe()
          } else {
            wx.showToast({ title: '提醒保持关闭', icon: 'none' })
          }
        },
      })
    } else {
      on[value] = true
      this.setData({ reminderOn: on })
    }
  },

  /** 订阅消息授权(模板 ID 占位;拒绝/失败均静默降级,不影响保存) */
  trySubscribe() {
    try {
      wx.requestSubscribeMessage({
        tmplIds: ['PLACEHOLDER_DAYS_TEMPLATE_ID'],
        success: () => {},
        fail: () => {},
      })
    } catch (e) {
      // 静默降级
    }
  },

  /* ===== 可见性 ===== */
  pickVis(e) {
    this.setData({ visibility: e.currentTarget.dataset.vis })
  },

  /* ===== 年份(纪念日/在一起:周年称谓与相伴天数依据;生日不展示,隐私红线) ===== */
  onYearChange(e) {
    const idx = Number(e.detail.value)
    this.setData({ yearIndex: idx, year: idx > 0 ? new Date().getFullYear() - idx + 1 : null })
  },

  /* ===== 保存 ===== */
  save() {
    if (this.data.saving) return
    const relation = (this.data.isCustom ? this.data.customRelation : this.data.relation).trim()
    if (!relation) {
      wx.showToast({ title: '先把称呼填好', icon: 'none' })
      return
    }
    if (relation.length > 12) {
      wx.showToast({ title: '称呼最多 12 个字', icon: 'none' })
      return
    }
    const kind = this.data.kind
    const body = {
      relation,
      emoji: EMOJI[relation] || '😊',
      kind,
      calendarType: kind === 'TOGETHER' ? 'SOLAR' : this.data.calendarType,
      visibility: this.data.visibility,
    }
    if (kind === 'ANNIVERSARY' || kind === 'TOGETHER') {
      // 选了年份才有(纪念日→周年称谓;在一起→补录起始年)
      if (this.data.yearIndex > 0) body.year = new Date().getFullYear() - this.data.yearIndex + 1
    } else if (this.data.isEdit && this.data.year) {
      // BIRTHDAY 编辑:后端 year 全量覆盖不传即清空——回填既有存档年,防误清(隐私:生日年份仅存档)
      body.year = this.data.year
    }
    if (kind === 'TOGETHER') {
      if (!this.data.isEdit) {
        // 创建时月日可省,服务端取当天为起始日
      } else {
        body.month = this.data.monthIndex + 1
        body.day = this.data.dayIndex + 1
      }
    } else {
      body.month = this.data.monthIndex + 1
      body.day = this.data.dayIndex + 1
      if (!this.data.isEdit) {
        const reminders = []
        Object.keys(this.data.reminderOn).forEach((k) => {
          if (this.data.reminderOn[k]) reminders.push(Number(k))
        })
        body.reminders = reminders
      }
    }
    this.setData({ saving: true })
    const done = (res) => {
      this.setData({ saving: false })
      wx.showToast({ title: '日子记好了 ✨', icon: 'none' })
      setTimeout(() => {
        if (this.data.isEdit) {
          wx.navigateBack()
        } else {
          wx.redirectTo({ url: '/pages/day-detail/day-detail?id=' + res.id })
        }
      }, 600)
    }
    const fail = () => this.setData({ saving: false })
    if (this.data.isEdit) {
      updateDay(this.data.dayId, body).then(done).catch(fail)
    } else {
      createDay(body).then(done).catch(fail)
    }
  },
})
