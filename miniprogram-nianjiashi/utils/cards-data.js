/**
 * 卡片引擎前端常量与展示视图组装
 * 与后端 TemplateCatalog/LetterTextLib 的可选项双端各持一份
 * (契约枚举为准:TemplateKey/TemplateCategory/StoryKey/LetterTone/CapsuleType/CapsuleOpenRule/styleKey)。
 */

/**
 * 二维码内容两态(设计裁定④):
 * - 'path'(当前,未发布态):QR 内容=后端下发的 pagePath 字符串,海报服务保存图片/面对面场景,
 *   病毒主链路走微信好友转发卡片(onShareAppMessage path=pagePath);
 * - 'url'(已发布态,运营切换):公众平台「扫普通二维码进入小程序」配置 https://{QR_HOST}/p/ 前缀
 *   → pages/visitor/visitor,QR 内容切为 https://{QR_HOST}/p/{scene},options.q 带参进入。
 */
const QR_LINK_MODE = 'path'
const QR_HOST = ''

/** 感恩信 · 写给谁(点选为主,「自定义」走弹窗输入兜底;契约 recipient 1-12 字) */
const RECIPIENT_CHIPS = ['妈妈', '奶奶', '外婆', '婆婆']

/** 5 个固定故事素材(与契约 StoryKey 枚举逐一对应,不可增删改字) */
const STORIES = [
  '她总给我留饭',
  '送我上学那些年',
  '她爱唠叨但都是好意',
  '她一个人把我带大',
  '我离家后她学会视频',
]

/** 语气三选(契约 LetterTone) */
const TONES = [
  { key: 'TEAR', emoji: '🥹', name: '戳泪点', desc: '看到会哭' },
  { key: 'FUN', emoji: '😄', name: '幽默', desc: '笑着笑着哭了' },
  { key: 'CALM', emoji: '🕊️', name: '平淡', desc: '克制的深情' },
]

/** 天数卡 · 关系(契约 relation 1-12 字,含 emoji) */
const DAY_RELATION_CHIPS = ['恋人 ❤️', '结婚 💍', '闺蜜 🌸', '好友 🤝']

/** 天数卡样式三选(契约 styleKey;霞橘/深夜星河/薄荷清晨,对齐原型) */
const CARD_STYLES = [
  { key: 'SUNSET', name: '霞橘渐变 🌅', gradClass: 'bg-sunset' },
  { key: 'NIGHT', name: '深夜星河 🌌', gradClass: 'bg-night' },
  { key: 'MINT', name: '薄荷清晨 🌿', gradClass: 'bg-mint' },
]

/** 胶囊类型(契约 CapsuleType) */
const CAPSULE_TYPES = [
  { key: 'SELF', name: '给未来的自己', desc: '一年后自己开' },
  { key: 'PARTNER', name: '给未来的 TA', desc: 'TA 扫码开箱' },
]

/** 开箱规则(契约 CapsuleOpenRule;ONE_YEAR_LATER 服务端算,其余前端选未来日期) */
const CAPSULE_OPEN_RULES = [
  { key: 'ONE_YEAR_LATER', name: '一年后', needDate: false },
  { key: 'GAOKAO_SCORE_DAY', name: '高考出分日', needDate: true },
  { key: 'WEDDING_ANNIVERSARY', name: '结婚纪念日', needDate: true },
  { key: 'BABY_18', name: '宝宝 18 岁', needDate: true },
]

/** 封面提示语预置(开箱人第一眼;契约 coverHint 1-40 字,可自定义) */
const COVER_HINTS = ['"一年不见，你还挺想我吧"', '"立个 flag 在这儿了"', '"到日子了，回来开箱"']

const KIND_LABEL = {
  LETTER: '给妈妈的感恩信',
  DAYS_CARD: '相伴天数卡',
  CAPSULE: '时光胶囊邀请函',
  BIRTHDAY_CARD: '生日福卡',
}

/** 模板分类 Tab(契约 TemplateCategory) */
const CATEGORY_TABS = [
  { key: '', name: '全部' },
  { key: 'FESTIVAL', name: '节日' },
  { key: 'FAMILY', name: '家人' },
  { key: 'COUPLE', name: '情侣' },
  { key: 'FUN', name: '整活' },
]

/** previewMeta.backgroundKey → 渐变 class(TemplateCatalog 六键) */
const BG_CLASS = {
  NIGHT: 'bg-night',
  SUNSET: 'bg-sunset',
  MINT: 'bg-mint',
  VIOLET: 'bg-violet',
  DAD: 'bg-dad',
  WEDDING: 'bg-wedding',
}

/** canvas 海报渐变起止色(与 app.wxss 令牌同表;750×1200 画布;WARM=福卡暖橘,v1.8.0) */
const POSTER_GRADS = {
  SUNSET: ['#ff7a59', '#ff4d6d'],
  NIGHT: ['#23233a', '#12121f'],
  MINT: ['#2bc4b4', '#0f9d8e'],
  WARM: ['#ff9a56', '#ff5e3a'],
}

// ===== 小工具 =====

function pad2(n) {
  return (n < 10 ? '0' : '') + n
}

/** '2026-10-18' → '2026.10.18'(海报/卡片内联日期) */
function fmtDot(dateStr) {
  if (!dateStr) return ''
  const p = String(dateStr).split('-')
  return p.length === 3 ? p[0] + '.' + p[1] + '.' + p[2] : String(dateStr)
}

/** 今天 yyyy-MM-dd(本地时区) */
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
}

/** 起始日至今的天数(服务端同公式 max(1, between);预览用,定稿以服务端为准) */
function daysTogetherOf(startDate) {
  if (!startDate) return 1
  const s = new Date(startDate + 'T12:00:00')
  const t = new Date()
  t.setHours(12, 0, 0, 0)
  return Math.max(1, Math.floor((t - s) / 86400000))
}

/** 距开箱日天数(非负) */
function daysUntilOf(dateStr) {
  if (!dateStr) return 0
  const d = new Date(dateStr + 'T12:00:00')
  const t = new Date()
  t.setHours(12, 0, 0, 0)
  return Math.max(0, Math.round((d - t) / 86400000))
}

/** 节日倒计时(festivalDate → 'X 天后';已过返回 '') */
function festivalCountdown(dateStr) {
  if (!dateStr) return ''
  const n = daysUntilOf(dateStr)
  return n === 0 ? '就是今天' : n + ' 天后'
}

// ===== 当日同 kind 去重(本地 storage,服务端不去重为既定行为;对齐原型 pushMadeCard) =====

const MADE_TODAY_KEY = 'njs_made_today'

/** 记录今日已做某 kind 卡片 */
function markMadeToday(kind) {
  try {
    const map = wx.getStorageSync(MADE_TODAY_KEY) || {}
    map[kind] = todayStr()
    wx.setStorageSync(MADE_TODAY_KEY, map)
  } catch (e) {
    // 存储失败不阻断
  }
}

/** 今日是否已做过某 kind */
function isMadeToday(kind) {
  try {
    const map = wx.getStorageSync(MADE_TODAY_KEY) || {}
    return map[kind] === todayStr()
  } catch (e) {
    return false
  }
}

module.exports = {
  QR_LINK_MODE,
  QR_HOST,
  RECIPIENT_CHIPS,
  STORIES,
  TONES,
  DAY_RELATION_CHIPS,
  CARD_STYLES,
  CAPSULE_TYPES,
  CAPSULE_OPEN_RULES,
  COVER_HINTS,
  KIND_LABEL,
  CATEGORY_TABS,
  BG_CLASS,
  POSTER_GRADS,
  pad2,
  fmtDot,
  todayStr,
  daysTogetherOf,
  daysUntilOf,
  festivalCountdown,
  markMadeToday,
  isMadeToday,
}
