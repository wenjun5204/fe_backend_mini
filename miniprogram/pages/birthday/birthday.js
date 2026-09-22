const { ensureLogin } = require('../../utils/request')
const api = require('../../utils/api')

const MONTHS = Array.from({ length: 12 }, (_, index) => index + 1)
const REMINDER_OPTIONS = [7, 3, 0]

function getDayCount(calendarType, month) {
  // 生日不记录年份；公历按闰年上限保留 2 月 29 日，实际日期由服务端统一换算。
  return calendarType === 'LUNAR' ? 30 : new Date(2028, month, 0).getDate()
}

function buildDays(calendarType, month) {
  return Array.from({ length: getDayCount(calendarType, month) }, (_, index) => index + 1)
}

function getDaysText(daysUntil) {
  if (daysUntil === 0) return '就是今天'
  if (daysUntil === 1) return '明天生日'
  return daysUntil + ' 天后'
}

function getReminderText(reminderDays) {
  if (!reminderDays.length) return '暂不提醒'
  return reminderDays
    .slice()
    .sort((a, b) => b - a)
    .map((day) => day === 0 ? '当天' : '提前 ' + day + ' 天')
    .join('、')
}

Page({
  data: {
    loading: true,
    saving: false,
    editing: false,
    records: [],
    calendarType: 'SOLAR',
    visibility: 'FAMILY',
    monthIndex: 0,
    dayIndex: 0,
    name: '',
    isLeapMonth: false,
    reminderDays: [],
    reminderText: '暂不提醒',
    converted: null,
    conversionLoading: false,
    conversionError: false,
    conversionTitle: '下一次生日对应的农历日期',
    editingId: null,
    months: MONTHS,
    days: buildDays('SOLAR', 1),
    reminderOptions: REMINDER_OPTIONS,
  },

  async onLoad() {
    await ensureLogin()
    this.refresh()
  },

  async onPullDownRefresh() {
    await this.refresh()
    wx.stopPullDownRefresh()
  },

  async refresh() {
    this.setData({ loading: true })
    try {
      const result = await api.getBirthdays()
      const records = (result.items || []).map((record) => ({
        ...record,
        daysText: getDaysText(record.daysUntil),
        visibilityText: record.visibility === 'PRIVATE' ? '仅您可见' : '家人可见',
        reminderText: getReminderText(record.reminderDays || []),
      }))
      this.setData({ records, loading: false })
    } catch (e) {
      this.setData({ loading: false })
    }
  },

  setDate(monthIndex, dayIndex, callback) {
    const month = monthIndex + 1
    const days = buildDays(this.data.calendarType, month)
    this.setData({
      monthIndex,
      dayIndex: Math.min(dayIndex, days.length - 1),
      days,
    }, callback)
  },

  onAdd() {
    const today = new Date()
    const monthIndex = today.getMonth()
    const days = buildDays('SOLAR', monthIndex + 1)
    this.setData({
      editing: true,
      editingId: null,
      name: '',
      calendarType: 'SOLAR',
      visibility: 'FAMILY',
      monthIndex,
      dayIndex: Math.min(today.getDate() - 1, days.length - 1),
      days,
      isLeapMonth: false,
      reminderDays: [],
      reminderText: '暂不提醒',
      converted: null,
      conversionError: false,
      conversionTitle: '下一次生日对应的农历日期',
    }, () => this.convertSelectedDate())
  },

  onEdit(e) {
    const record = e.currentTarget.dataset.record
    const monthIndex = record.month - 1
    const days = buildDays(record.calendarType, record.month)
    const reminderDays = record.reminderDays || []
    this.setData({
      editing: true,
      editingId: record.id,
      name: record.displayName,
      calendarType: record.calendarType,
      visibility: record.visibility,
      monthIndex,
      dayIndex: Math.min(record.day - 1, days.length - 1),
      days,
      isLeapMonth: record.isLeapMonth,
      reminderDays,
      reminderText: getReminderText(reminderDays),
      converted: record.nextBirthdayDate ? {
        solarDate: record.nextBirthdayDate,
        lunarText: record.nextBirthdayLunarText,
      } : null,
      conversionError: false,
      conversionTitle: record.calendarType === 'LUNAR' ? '下一次生日对应的公历日期' : '下一次生日对应的农历日期',
    })
  },

  onCloseForm() {
    this.setData({ editing: false, saving: false })
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value })
  },

  onCalendarType(e) {
    const calendarType = e.currentTarget.dataset.type
    const days = buildDays(calendarType, this.data.monthIndex + 1)
    this.setData({
      calendarType,
      days,
      dayIndex: Math.min(this.data.dayIndex, days.length - 1),
      isLeapMonth: false,
      converted: null,
      conversionError: false,
      conversionTitle: calendarType === 'LUNAR' ? '下一次生日对应的公历日期' : '下一次生日对应的农历日期',
    }, () => this.convertSelectedDate())
  },

  onVisibility(e) {
    this.setData({ visibility: e.currentTarget.dataset.visibility })
  },

  onMonthChange(e) {
    this.setDate(Number(e.detail.value), this.data.dayIndex, () => this.convertSelectedDate())
  },

  onDayChange(e) {
    this.setData({ dayIndex: Number(e.detail.value) }, () => this.convertSelectedDate())
  },

  onLeapMonth() {
    this.setData({ isLeapMonth: !this.data.isLeapMonth }, () => this.convertSelectedDate())
  },

  onReminder(e) {
    const value = Number(e.currentTarget.dataset.day)
    const exists = this.data.reminderDays.includes(value)
    const reminderDays = exists
      ? this.data.reminderDays.filter((item) => item !== value)
      : [...this.data.reminderDays, value]
    this.setData({ reminderDays, reminderText: getReminderText(reminderDays) })
    wx.vibrateShort({ type: 'light', fail: () => {} })
  },

  async convertSelectedDate() {
    const requestId = (this.convertRequestId || 0) + 1
    this.convertRequestId = requestId
    this.setData({ conversionLoading: true, conversionError: false })
    try {
      const converted = await api.convertCalendar({
        calendarType: this.data.calendarType,
        month: this.data.monthIndex + 1,
        day: this.data.dayIndex + 1,
        isLeapMonth: this.data.isLeapMonth,
      })
      if (requestId === this.convertRequestId) {
        this.setData({ converted, conversionLoading: false, conversionError: false })
      }
    } catch (e) {
      if (requestId === this.convertRequestId) {
        this.setData({ converted: null, conversionLoading: false, conversionError: true })
      }
    }
  },

  async onSave() {
    const displayName = this.data.name.trim()
    if (!displayName) {
      wx.showToast({ title: '请先写下家人的称呼', icon: 'none' })
      return
    }
    if (this.data.saving) return

    const body = {
      subjectType: 'CONTACT',
      displayName,
      calendarType: this.data.calendarType,
      month: this.data.monthIndex + 1,
      day: this.data.dayIndex + 1,
      isLeapMonth: this.data.isLeapMonth,
      visibility: this.data.visibility,
    }
    this.setData({ saving: true })
    try {
      const record = this.data.editingId
        ? await api.updateBirthday(this.data.editingId, body)
        : await api.createBirthday(body)
      await api.updateBirthdayReminder(record.id, this.data.reminderDays)
      wx.vibrateShort({ type: 'medium', fail: () => {} })
      wx.showToast({ title: '生日已经记好了', icon: 'success' })
      this.setData({ editing: false, saving: false })
      this.refresh()
    } catch (e) {
      this.setData({ saving: false })
      // 网络层会给出清楚、口语化的提示。
    }
  },

  onDelete(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name
    wx.showModal({
      title: '删除生日记录',
      content: '删除“' + name + '”的生日后，相关提醒也会停止。',
      confirmText: '删除',
      confirmColor: '#b01f24',
      cancelText: '保留记录',
      success: async (result) => {
        if (!result.confirm) return
        try {
          await api.deleteBirthday(id)
          wx.showToast({ title: '已删除，提醒已停止', icon: 'none' })
          this.refresh()
        } catch (err) {
          // 网络层已提示。
        }
      },
    })
  },
})
