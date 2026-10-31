/**
 * 统一网络层:自动携带 token,统一处理业务错误码(契约错误码 40000/40100/40300/40400/40900)
 * 实现逻辑照抄旧 miniprogram/utils/request.js,常量独立维护。
 *
 * 双通道:
 * - USE_CLOUD=false(当前):wx.request 直连本地后端(开发者工具需勾选「不校验合法域名」)
 * - USE_CLOUD=true:wx.cloud.callContainer 走微信云托管(无需配置合法域名,
 *   平台自动注入 X-WX-OPENID,后端据此识别用户身份)
 * 云托管环境 ID:控制台「环境信息」页获取
 * 部署念家时后端到云托管后再切回 true;期间本地预览一律 false。
 *
 * ⚠ 新旧小程序共用同一 AppID 即共用同一 wx storage:
 *   本目录 token/user key 一律 njs_ 前缀,与旧目录的 qjf_* 互相隔离。
 */
const USE_CLOUD = true
const CLOUD_ENV = 'prod-d5g1qs6gjdabafaf6'
const SERVICE_NAME = 'reganmini'

const BASE_URL = 'http://127.0.0.1:8080'
const TOKEN_KEY = 'njs_token'
const USER_KEY = 'njs_user'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token)
}

/** 当前用户信息(「我的」页资料卡/全部日子页定位自己的记录;仅登录成功时写入) */
function getUser() {
  try {
    return wx.getStorageSync(USER_KEY) || null
  } catch (e) {
    return null
  }
}

function setUser(user) {
  try {
    wx.setStorageSync(USER_KEY, user)
  } catch (e) {
    // 存储失败不阻塞登录
  }
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
    // 本地通道固定测试身份:开发者工具「清除缓存」会清掉 wx storage,
    // 随机 dev 身份会变成新用户导致预览数据"消失"(日子还在库里,归属旧身份)。
    // 固定 dev-nianjiashi-local 让本地预览数据始终挂在同一身份下。
    const OPENID_KEY = 'njs_openid'
    let openid = wx.getStorageSync(OPENID_KEY)
    if (!openid) {
      openid = 'dev-nianjiashi-local'
      wx.setStorageSync(OPENID_KEY, openid)
    }
    options.data = { code: openid }
  }
  return request('/api/auth/login', options)
    .then((data) => {
      setToken(data.token)
      setUser({
        userId: data.userId,
        nickname: data.nickname || '',
        avatarUrl: data.avatarUrl || '',
      })
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

/** 云托管通道(options.noAuth=true 时不带 Authorization,visitor 免鉴权组专用) */
function requestViaCloud(path, options) {
  return new Promise((resolve, reject) => {
    const header = { 'content-type': 'application/json' }
    const token = options.noAuth ? '' : options.token || getToken()
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }
    wx.cloud.callContainer({
      config: { env: CLOUD_ENV },
      service: SERVICE_NAME,
      path: path,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => handleResponse(res, path, resolve, reject),
      fail: () => {
        toastError('网络不太好,请您稍后再试')
        reject(new Error('network error'))
      },
    })
  })
}

/** 本地直连通道(options.noAuth=true 时不带 Authorization) */
function requestViaHttp(path, options) {
  return new Promise((resolve, reject) => {
    const header = { 'content-type': 'application/json' }
    const token = options.noAuth ? '' : options.token || getToken()
    if (token) {
      header['Authorization'] = 'Bearer ' + token
    }
    wx.request({
      url: BASE_URL + path,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res) => handleResponse(res, path, resolve, reject),
      fail: () => {
        toastError('网络不太好,请您稍后再试')
        reject(new Error('network error'))
      },
    })
  })
}

/** 两个通道的响应结构一致(wx.request 与 callContainer 均返回 statusCode/data) */
function handleResponse(res, path, resolve, reject) {
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
    // 排查云托管 404/502 时,响应体通常包含网关或后端的明确错误信息
    console.error('[request] HTTP ' + res.statusCode, {
      path,
      service: USE_CLOUD ? SERVICE_NAME : undefined,
      body: res.data,
    })
    toastError('服务开小差了,请您稍后再试')
    reject(new Error('HTTP ' + res.statusCode))
  }
}

function toastError(message) {
  wx.showToast({ title: message, icon: 'none', duration: 2200 })
}

module.exports = { request, ensureLogin, getToken, setToken, getUser, setUser, BASE_URL, USE_CLOUD }
