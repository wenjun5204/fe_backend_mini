/**
 * 统一的 wx.request 封装
 *
 * ⚠️ 开发阶段：
 *  - BASE_URL 指向本机后端 http://127.0.0.1:8080
 *  - 需要在微信开发者工具右上角「详情 → 本地设置」勾选
 *    「不校验合法域名、web-view(业务域名)、TLS 版本以及 HTTPS 证书」
 *    （项目配置里 urlCheck 已设为 false，通常默认即可）
 *
 * ⚠️ 上线阶段：
 *  - 把 BASE_URL 换成你自己的 HTTPS 域名（需 ICP 备案），例如 https://api.example.com
 *  - 到小程序后台「开发管理 → 开发设置 → 服务器域名」把该域名加进 request 合法域名
 */
const BASE_URL = 'http://127.0.0.1:8080'

function request(path, options = {}) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + path,
      method: options.method || 'GET',
      data: options.data,
      header: { 'content-type': 'application/json', ...(options.header || {}) },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${JSON.stringify(res.data)}`))
        }
      },
      fail: (err) => reject(err),
    })
  })
}

module.exports = { request, BASE_URL }
