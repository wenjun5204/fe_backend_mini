const { ensureLogin } = require('../../utils/request')
const { getDay, deleteDay, updateDayReminder } = require('../../utils/api-days')
const { viewOf, WISHES, KIND_LABEL } = require('../../utils/days-data')

Page({
  data: {
    id: null,
    r: null,
    wishes: [],
    reminderOptions: [
      { value: 7, label: '提前 7 天' },
      { value: 3, label: '提前 3 天' },
      { value: 0, label: '当天提醒' },
    ],
    reminderOn: { 7: false, 3: false, 0: false },
    remText: '未设提醒',
  },

  onLoad(options) {
    this.setData({ id: Number(options.id) })
    this.load()
  },

  load() {
    ensureLogin()
      .catch(() => {})
      .then(() => getDay(this.data.id))
      .then((record) => {
        const v = viewOf(record)
        const on = { 7: false, 3: false, 0: false }
        const rems = record.reminders || []
        rems.forEach((x) => {
          on[x] = true
        })
        this.setData({
          r: v,
          wishes: WISHES[record.kind] || WISHES.BIRTHDAY,
          reminderOn: on,
          remText: rems.length ? '提醒 ' + rems.length + ' 个' : '未设提醒',
        })
      })
      .catch(() => {
        // 40400(不存在/不可见)错误提示由网络层统一 toast;返回上一页
        setTimeout(() => this.backHome(), 1200)
      })
  },

  /* 祝福语点选复制 */
  copyWish(e) {
    const text = e.currentTarget.dataset.text
    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: '已复制，到时候粘贴发送', icon: 'none' })
      },
    })
  },

  /* 做卡片:按 kind 分流(设计 v2.2 §2.2)——BIRTHDAY→生日福卡(无时间门控,随时可做)、
   * TOGETHER→相伴天数卡、其余→模板馆引导 */
  makeCard() {
    const r = this.data.r
    if (!r) return
    if (r.isBirthday) {
      wx.navigateTo({ url: '/pages/birthday-card-edit/birthday-card-edit?dayId=' + r.id })
      return
    }
    if (r.isTogether) {
      wx.navigateTo({ url: '/pages/days-card-edit/days-card-edit' })
      return
    }
    wx.showToast({ title: '这类日子的卡片在模板馆，先去逛逛', icon: 'none', duration: 2200 })
    setTimeout(() => wx.navigateTo({ url: '/pages/tpl/tpl' }), 900)
  },

  /* 编辑 */
  editDay() {
    wx.navigateTo({ url: '/pages/day-edit/day-edit?id=' + this.data.id })
  },

  /* 提醒开关(PUT /api/days/{id}/reminder,幂等覆盖,空数组=关闭) */
  toggleReminder(e) {
    const value = Number(e.currentTarget.dataset.value)
    const on = {}
    Object.keys(this.data.reminderOn).forEach((k) => {
      on[k] = this.data.reminderOn[k]
    })
    if (on[value]) {
      on[value] = false
      this.pushReminder(on)
      return
    }
    const noneOn = !on[7] && !on[3] && !on[0]
    if (noneOn) {
      // 从全关到开启:先说明内容与频率(合规:订阅前说明)
      wx.showModal({
        title: '开启日子提醒',
        content: '到了您选的时间，我们发一条消息提醒您。每人每天最多 3 条，家人看不到，也不会打扰您。',
        confirmText: '同意并开启',
        cancelText: '暂不开启',
        success: (res) => {
          if (res.confirm) {
            on[value] = true
            this.pushReminder(on)
            this.trySubscribe()
          } else {
            wx.showToast({ title: '提醒保持关闭', icon: 'none' })
          }
        },
      })
    } else {
      on[value] = true
      this.pushReminder(on)
    }
  },

  pushReminder(on) {
    const reminders = []
    Object.keys(on).forEach((k) => {
      if (on[k]) reminders.push(Number(k))
    })
    const prev = this.data.reminderOn
    this.syncFromMap(on)
    updateDayReminder(this.data.id, reminders)
      .then((res) => {
        const echo = (res && res.reminders) || []
        const echoOn = { 7: false, 3: false, 0: false }
        echo.forEach((x) => {
          echoOn[x] = true
        })
        this.syncFromMap(echoOn)
        wx.showToast({ title: reminders.length ? '提醒已更新' : '提醒已关闭', icon: 'none' })
      })
      .catch(() => {
        // 失败回滚到服务端回显前的状态
        this.syncFromMap(prev)
      })
  },

  syncFromMap(on) {
    const count = Object.keys(on).filter((k) => on[k]).length
    this.setData({
      reminderOn: on,
      remText: count ? '提醒 ' + count + ' 个' : '未设提醒',
    })
  },

  /** 订阅消息授权(模板 ID 占位;拒绝/失败均静默降级) */
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

  /* 删除(二次确认,提示提醒也会停止) */
  removeDay() {
    const r = this.data.r
    if (!r) return
    wx.showModal({
      title: '删除这个日子',
      content: '确定删除「' + (r.isTogether ? KIND_LABEL.TOGETHER : r.name) + '」吗？提醒也会一并停止。',
      confirmText: '删除',
      confirmColor: '#c0564a',
      cancelText: '再想想',
      success: (res) => {
        if (!res.confirm) return
        deleteDay(this.data.id)
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'none' })
            setTimeout(() => this.backHome(), 600)
          })
          .catch(() => {})
      },
    })
  },

  backHome() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      wx.navigateBack()
    } else {
      wx.switchTab({ url: '/pages/days/days' })
    }
  },
})
