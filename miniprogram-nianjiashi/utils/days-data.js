/**
 * 日子 Tab 前端常量与展示视图组装
 * 农历落点换算全部由后端完成(DayRecord 自带 nextDate/nextDateLunarText/daysUntil/togetherDays),
 * 前端只做展示拼装,不做农历算法。
 */

/** 关系标签(与后端 DayService 预置 emoji 映射同表,双端各持一份) */
const RELATIONS = ['妈妈', '爸爸', '老伴', '儿子', '女儿', '孙子', '孙女', '亲家', '我自己']

const EMOJI = {
  妈妈: '👩',
  爸爸: '👨',
  老伴: '👴',
  儿子: '🧑',
  女儿: '👧',
  孙子: '👦',
  孙女: '👧',
  亲家: '🤝',
  我自己: '😊',
}

const KIND_LABEL = { BIRTHDAY: '生日', ANNIVERSARY: '纪念日', TOGETHER: '在一起' }
const CAL_LABEL = { SOLAR: '公历', LUNAR: '农历' }
const VIS_LABEL = { FAMILY: '家人可见', PRIVATE: '只我可见' }

/** 祝福语库(按 kind 分组;详情页点选复制用) */
const WISHES = {
  BIRTHDAY: ['生日快乐,天天都有好心情。', '身体健康,吃得香、睡得甜。', '平平安安,一家人常相见。'],
  ANNIVERSARY: ['风风雨雨这么多年,还像当年一样好。', '一家人和和美美,日子越过越甜。', '平安顺遂,来年更胜今年。'],
  TOGETHER: ['在一起的第 N 天,依然觉得很好。', '下一个 N 天,继续赖着你。'],
}

function pad2(n) {
  return (n < 10 ? '0' : '') + n
}

/** '2026-10-18' → '2026年10月18日' */
function fmtDate(dateStr) {
  if (!dateStr) return ''
  const p = dateStr.split('-')
  if (p.length !== 3) return dateStr
  return Number(p[0]) + '年' + Number(p[1]) + '月' + Number(p[2]) + '日'
}

/** 距今天数(只用于录入页换算预览;列表/详情用服务端算好的 daysUntil) */
function daysUntilOf(dateStr) {
  if (!dateStr) return 0
  const d = new Date(dateStr + 'T12:00:00')
  const t = new Date()
  t.setHours(12, 0, 0, 0)
  return Math.max(0, Math.round((d - t) / 86400000))
}

/**
 * DayRecord → 列表/详情通用展示视图
 * (字段名对齐 TS 锚点:id/userId/relation/emoji/kind/calendarType/month/day/visibility/
 *  reminders/createdAt/nextDate/nextDateLunarText/daysUntil/togetherDays)
 */
function viewOf(r) {
  const isTogether = r.kind === 'TOGETHER'
  const isToday = !isTogether && r.daysUntil === 0
  let dateText = ''
  if (isTogether) {
    const y = (r.createdAt || '').substring(0, 4)
    dateText = '从' + y + '年' + r.month + '月' + r.day + '日起 · 每天自动 +1'
  } else if (r.calendarType === 'LUNAR') {
    dateText = (r.nextDateLunarText || '') + ' · 公历' + fmtDate(r.nextDate)
  } else {
    dateText = r.month + '月' + r.day + '日 · ' + (r.nextDateLunarText || '')
  }
  return {
    id: r.id,
    userId: r.userId,
    kind: r.kind,
    isTogether,
    isBirthday: r.kind === 'BIRTHDAY',
    isToday,
    daysUntil: r.daysUntil,
    togetherDays: r.togetherDays,
    emoji: r.emoji || '😊',
    name: isTogether ? '在一起的第 ' + r.togetherDays + ' 天' : r.relation + '的' + KIND_LABEL[r.kind],
    tagText: isTogether ? '相伴中' : KIND_LABEL[r.kind],
    calText: CAL_LABEL[r.calendarType] || '',
    visText: VIS_LABEL[r.visibility] || '',
    dateText,
    gradClass: isTogether ? 'grad-hot' : r.kind === 'ANNIVERSARY' ? 'grad-teal' : 'grad-violet',
    tagClass: isTogether ? 'tag-hot' : r.kind === 'ANNIVERSARY' ? 'tag-teal' : 'tag-violet',
  }
}

module.exports = {
  RELATIONS,
  EMOJI,
  KIND_LABEL,
  CAL_LABEL,
  VIS_LABEL,
  WISHES,
  pad2,
  fmtDate,
  daysUntilOf,
  viewOf,
}
