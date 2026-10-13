/**
 * 本地偏好设置(v1.5「家」页设置弹层开关):只存本机,不上报
 * - 动画:关闭后翻签/翻书等装饰动效缩短为静态结果(低端机适老)
 * - 声音:关闭后转盘触感振动等提示静音
 * - 隐身榜外:开启后本家族不出现在好友家族榜(本地占位,服务端能力上线前仅记忆开关状态)
 */
const KEYS = {
  anim: 'qjf_anim_enabled',
  sound: 'qjf_sound_enabled',
  hideInRank: 'qjf_hide_in_rank',
}

function get(key, fallback) {
  try {
    const v = wx.getStorageSync(key)
    if (v === '' || v === null || v === undefined) {
      return fallback
    }
    return v
  } catch (e) {
    return fallback
  }
}

function set(key, value) {
  try {
    wx.setStorageSync(key, value)
  } catch (e) {
    // 静默
  }
}

module.exports = {
  getAnimEnabled: () => get(KEYS.anim, true),
  setAnimEnabled: (v) => set(KEYS.anim, !!v),
  getSound: () => get(KEYS.sound, true),
  setSound: (v) => set(KEYS.sound, !!v),
  getHideInRank: () => get(KEYS.hideInRank, false),
  setHideInRank: (v) => set(KEYS.hideInRank, !!v),
}
