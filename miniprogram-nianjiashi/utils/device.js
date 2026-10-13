/**
 * 访客设备标识(需求 D3:device 维度即"清缓存可再点 3 心"的既定语义,不做对抗)。
 * key=njs_device_id(新旧小程序共用 storage 的 njs_ 前缀隔离约定);
 * 32 位 hex(4 段 Math.random 拼接),契约 deviceId 校验 32-64 字符。
 */
const KEY = 'njs_device_id'

function makeHex32() {
  let id = ''
  for (let i = 0; i < 4; i++) {
    id += Math.floor(Math.random() * 0x100000000 + 0x100000000)
      .toString(16)
      .slice(1)
  }
  return id
}

/** 取持久化 deviceId,无则生成并写入 */
function getDeviceId() {
  let id = ''
  try {
    id = wx.getStorageSync(KEY) || ''
  } catch (e) {
    id = ''
  }
  if (!id || id.length < 32) {
    id = makeHex32()
    try {
      wx.setStorageSync(KEY, id)
    } catch (e) {
      // 存储失败时用内存值兜底(本次会话可用)
    }
  }
  return id
}

module.exports = { getDeviceId }
