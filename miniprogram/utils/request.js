/**
 * 统一网络层:自动携带 token,统一处理业务错误码(契约错误码 40000/40100/40300/40400/40900)
 * 错误 toast 文案已过合规词表(.agents/docs/conventions.md)
 *
 * ⚠️ 开发阶段 BASE_URL 指向本机后端,开发者工具需勾选「不校验合法域名」
 * ⚠️ 上线阶段换成 HTTPS 备案域名并配置合法域名
 */
const BASE_URL = 'http://127.0.0.1:8080'
const TOKEN_KEY = 'qjf_token'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token)
}

/**
 * 登录:MVP 用设备稳定标识作为 code(后端视为 openid)。
 * 正式上线替换为 wx.login code + 后端 code2session。
 */
function ensureLogin() {
  if (getToken()) {
    return Promise.resolve(getToken())
  }
  const OPENID_KEY = 'qjf_openid'
  let openid = wx.getStorageSync(OPENID_KEY)
  if (!openid) {
    openid = 'dev-' + Math.random().toString(36).slice(2, 10)
    wx.setStorageSync(OPENID_KEY, openid)
  }
  return request('/api/auth/login', { method: 'POST', data: { code: openid } })
    .then((data) => {
      setToken(data.token)
      return data.token
    })
}

function request(path, options = {}) {
  options = options || {}
  return new Promise((resolve, reject) => {
    const header = { 'content-type': 'application/json' }
    const token = options.token || getToken()
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }
    wx.request({
      url: BASE_URL + path,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const body = res.data
          // 统一错误结构 {code, message}
          if (body && body.code && body.code !== '0' && parseInt(body.code, 10) >= 40000) {
            toastError(body.message || '服务开小差了,请您稍后再试')
            reject(new Error(body.message))
            return
          }
          resolve(body)
        } else {
          toastError('服务开小差了,请您稍后再试')
          reject(new Error('HTTP ' + res.statusCode))
        }
      },
      fail: () => {
        toastError('网络不太好,请您稍后再试')
        reject(new Error('network error'))
      },
    })
  })
}

function toastError(message) {
  wx.showToast({ title: message, icon: 'none', duration: 2200 })
}

module.exports = { request, ensureLogin, getToken, setToken, BASE_URL }
