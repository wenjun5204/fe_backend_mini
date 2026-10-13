/**
 * 解忧册「册子的记忆」本地存储(v1.5,PRD §3.10.4):
 * - 只存本机 storage,绝不上报服务端、绝不请求推送(隐私红线)
 * - 只留最近 3 条,第 4 条产生时最旧的自动沉底消失
 * - 「办成了」仅本地标记;累计计数「成了 N 件」仅本人可见
 */
const MEMORIES_KEY = 'jieyou_memories'
const DONE_COUNT_KEY = 'jieyou_done_count'
const MAX_KEEP = 3

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

/** 当日日期键(yyyy-MM-dd,用于同日同条去重) */
function todayKey() {
  const d = new Date()
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}

/** 当日展示文本(M月D日) */
function todayText() {
  const d = new Date()
  return (d.getMonth() + 1) + '月' + d.getDate() + '日'
}

function getMemories() {
  try {
    const list = wx.getStorageSync(MEMORIES_KEY)
    return Array.isArray(list) ? list : []
  } catch (e) {
    return []
  }
}

function saveMemories(list) {
  const trimmed = list.slice(0, MAX_KEEP)
  try {
    wx.setStorageSync(MEMORIES_KEY, trimmed)
  } catch (e) {
    // 存储失败静默:不影响页面其它功能
  }
  return trimmed
}

/**
 * 收进册子的记忆:同日同条去重;只保留最近 3 条
 * @param quote {quoteId, text, typeLabel}
 */
function addMemory(quote) {
  const list = getMemories()
  const dateKey = todayKey()
  if (list.some((r) => r.dateKey === dateKey && r.quoteId === quote.quoteId)) {
    return list
  }
  list.unshift({
    quoteId: quote.quoteId,
    text: quote.text,
    typeLabel: quote.typeLabel,
    dateKey: dateKey,
    dateText: todayText(),
    done: false,
  })
  return saveMemories(list)
}

/** 「办成了」:仅本地标记,返回更新后的列表 */
function markDone(quoteId, dateKey) {
  const list = getMemories()
  let changed = false
  list.forEach((r) => {
    if (String(r.quoteId) === String(quoteId) && r.dateKey === dateKey && !r.done) {
      r.done = true
      changed = true
    }
  })
  if (changed) {
    saveMemories(list)
    try {
      wx.setStorageSync(DONE_COUNT_KEY, getDoneCount() + 1)
    } catch (e) {
      // 静默
    }
  }
  return list
}

/** 累计「成了 N 件」计数(本地累计,不随 3 条裁剪重置) */
function getDoneCount() {
  try {
    const n = parseInt(wx.getStorageSync(DONE_COUNT_KEY), 10)
    return isNaN(n) || n < 0 ? 0 : n
  } catch (e) {
    return 0
  }
}

module.exports = { getMemories, addMemory, markDone, getDoneCount }
