/**
 * 统一网络层:自动携带 token,统一处理业务错误码(契约错误码 40000/40100/40300/40400/40900)
 * 错误 toast 文案已过合规词表(.agents/docs/conventions.md)
 *
 * 双通道:
 * - USE_CLOUD=false:wx.request 直连本地后端(开发者工具需勾选「不校验合法域名」)
 * - USE_CLOUD=true(当前):wx.cloud.callContainer 走微信云托管(无需配置合法域名,
 *   平台自动注入 X-WX-OPENID,后端据此识别用户身份)
 * 云托管环境 ID:控制台「环境信息」页获取
 */
const USE_CLOUD = true
const CLOUD_ENV = 'prod-d5g1qs6gjdabafaf6'
const SERVICE_NAME = 'reganmini'

const BASE_URL = 'http://127.0.0.1:8080'
const TOKEN_KEY = 'qjf_token'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token)
}

/**
 * 登录:
 * - 云托管通道:callContainer 自动注入 X-WX-OPENID(不可伪造),body 无需 code;
 * - 本地通道:MVP 用设备稳定标识作为 code(后端视为 openid)。
 */
function ensureLogin() {
  if (getToken()) {
    return Promise.resolve(getToken())
  }
  const options = { method: 'POST', data: {} }
  if (!USE_CLOUD) {
    const OPENID_KEY = 'qjf_openid'
    let openid = wx.getStorageSync(OPENID_KEY)
    if (!openid) {
      openid = 'dev-' + Math.random().toString(36).slice(2, 10)
      wx.setStorageSync(OPENID_KEY, openid)
    }
    options.data = { code: openid }
  }
  return request('/api/auth/login', options)
    .then((data) => {
      setToken(data.token)
      return data.token
    })
}

function request(path, options = {}) {
  options = options || {}
  if (USE_CLOUD) {
    return requestViaCloud(path, options)
  }
  return requestViaHttp(path, options)
}

/** 云托管通道 */
function requestViaCloud(path, options) {
  return new Promise((resolve, reject) => {
    const header = { 'content-type': 'application/json' }
    const token = options.token || getToken()
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }
    wx.cloud.callContainer({
      config: { env: CLOUD_ENV },
      name: SERVICE_NAME,
      path: path,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => handleResponse(res, resolve, reject),
      fail: () => {
        toastError('网络不太好,请您稍后再试')
        reject(new Error('network error'))
      },
    })
  })
}

/** 本地直连通道 */
function requestViaHttp(path, options) {
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
      success: (res) => handleResponse(res, resolve, reject),
      fail: () => {
        toastError('网络不太好,请您稍后再试')
        reject(new Error('network error'))
      },
    })
  })
}

/** 两个通道的响应结构一致(wx.request 与 callContainer 均返回 statusCode/data) */
function handleResponse(res, resolve, reject) {
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
}

function toastError(message) {
  wx.showToast({ title: message, icon: 'none', duration: 2200 })
}

module.exports = { request, ensureLogin, getToken, setToken, BASE_URL, USE_CLOUD }
