/**
 * 统一网络层:自动携带 token,统一处理业务错误码(契约错误码 40000/40100/40300/40400/40900)
 *
 * 三通道(编译期常量 API_MODE 选择,不做运行时下发——同一 release 版全量唯一):
 * - local(开发者工具/开发版 develop,或 API_MODE='local'):wx.request 直连本地后端
 *   http://127.0.0.1:8080(工具端需勾选「不校验合法域名」);登录走 dev code(后端 auth.dev-code-enabled)
 * - cloud(体验版/正式版,或 API_MODE='cloud'):wx.cloud.callContainer 走微信云托管
 *   (无需配置合法域名,平台自动注入 X-WX-OPENID,后端据此识别用户身份)
 * - selfdeploy(API_MODE='selfdeploy',发版连自部署服务器时切换):
 *   wx.request 打 SELFDEPLOY_BASE_URL(需 HTTPS+已备案域名,配进小程序 request 合法域名);
 *   登录走 wx.login → code → 后端 code2Session(标准链路)
 * API_MODE='auto'(默认)按 envVersion 自动:develop→local,trial/release→cloud。
 *
 * 401 自动重登:HTTP 401 或业务码 40100 → 清 token → 重新登录 → 重放原请求一次
 * (token 失效场景兜底:secret 轮换/登录态过期;_retried 防死循环,登录请求自身跳过重试)。
 *
 * ⚠ 新旧小程序共用同一 AppID 即共用同一 wx storage:
 *   本目录 token/user key 一律 njs_ 前缀,与旧目录的 qjf_* 互相隔离。
 */

// ===== 通道配置(发版前按部署形态检查这 3 行) =====
/** 'auto'(按 envVersion 自动) | 'local' | 'cloud' | 'selfdeploy' */
const API_MODE = 'auto'
/** 自部署服务器域名(备案+HTTPS 证书就绪后填入;体验版调试模式可临时用 http://IP:8080) */
const SELFDEPLOY_BASE_URL = 'https://api.example.com'
const CLOUD_ENV = 'prod-d5g1qs6gjdabafaf6'
const SERVICE_NAME = 'reganmini'

// ===== 通道判定 =====
/** 运行版本:develop 开发版 / trial 体验版 / release 正式版;取不到(异常兜底)按 release 处理 */
const ENV_VERSION = (() => {
  try {
    const info = wx.getAccountInfoSync()
    return (info && info.miniProgram && info.miniProgram.envVersion) || 'release'
  } catch (e) {
    return 'release'
  }
})()
const CHANNEL =
  API_MODE !== 'auto' ? API_MODE : ENV_VERSION === 'develop' ? 'local' : 'cloud'
/** 兼容既有消费方(photo.js 等):cloud 通道为真 */
const USE_CLOUD = CHANNEL === 'cloud'
const BASE_URL = CHANNEL === 'selfdeploy' ? SELFDEPLOY_BASE_URL : 'http://127.0.0.1:8080'

const TOKEN_KEY = 'njs_token'
const USER_KEY = 'njs_user'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  if (token) {
    wx.setStorageSync(TOKEN_KEY, token)
  } else {
    wx.removeStorageSync(TOKEN_KEY)
  }
}

function clearToken() {
  try {
    wx.removeStorageSync(TOKEN_KEY)
  } catch (e) {
    // 清理失败不阻塞
  }
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

/** wx.login 取 code(仅 selfdeploy 通道;code 5 分钟有效单次使用,不缓存) */
function wxLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => (res.code ? resolve(res.code) : reject(new Error('no code'))),
      fail: () => reject(new Error('wx.login failed')),
    })
  })
}

/**
 * 登录(三通道):
 * - cloud:callContainer 自动注入 X-WX-OPENID(不可伪造),body 无需 code;
 * - local:固定测试身份作 code(后端 auth.dev-code-enabled 视为 openid)——
 *   固定 dev-nianjiashi-local 让本地预览数据始终挂在同一身份下(清缓存不变新用户);
 * - selfdeploy:wx.login 取 code → 后端 code2Session 换 openid。
 */
function ensureLogin() {
  if (getToken()) {
    return Promise.resolve(getToken())
  }
  if (CHANNEL === 'selfdeploy') {
    return wxLoginCode()
      .then((code) => request('/api/auth/login', { method: 'POST', data: { code }, skipAuthRetry: true }))
      .then(saveLogin)
  }
  const options = { method: 'POST', data: {} }
  if (CHANNEL === 'local') {
    const OPENID_KEY = 'njs_openid'
    let openid = wx.getStorageSync(OPENID_KEY)
    if (!openid) {
      openid = 'dev-nianjiashi-local'
      wx.setStorageSync(OPENID_KEY, openid)
    }
    options.data = { code: openid }
  }
  return request('/api/auth/login', options).then(saveLogin)
}

function saveLogin(data) {
  setToken(data.token)
  setUser({
    userId: data.userId,
    nickname: data.nickname || '',
    avatarUrl: data.avatarUrl || '',
  })
  return data.token
}

function request(path, options = {}) {
  options = options || {}
  return doRequest(path, options).catch((err) => {
    // 401 自动重登一次:token 失效(secret 轮换/登录态过期)兜底;登录请求自身与免鉴权请求跳过
    if (err && err.code === '40100' && !options._retried && !options.skipAuthRetry && !options.noAuth) {
      clearToken()
      return ensureLogin().then(() =>
        doRequest(path, Object.assign({}, options, { _retried: true }))
      )
    }
    throw err
  })
}

function doRequest(path, options) {
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

/** wx.request 通道(local 本地直连 / selfdeploy 自部署域名;options.noAuth=true 时不带 Authorization) */
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
      // 40100 标记给 request() 做"清 token 重登重放";其余原样 toast
      const err = new Error(body.message)
      err.code = body.code
      if (body.code === '40100') {
        reject(err)
        return
      }
      toastError(body.message || '服务开小差了,请您稍后再试')
      reject(err)
      return
    }
    resolve(body)
  } else if (res.statusCode === 401) {
    const err = new Error('未登录或身份失效')
    err.code = '40100'
    reject(err)
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

module.exports = {
  request,
  ensureLogin,
  getToken,
  setToken,
  getUser,
  setUser,
  BASE_URL,
  USE_CLOUD,
  CHANNEL,
}
