/**
 * 照片上传链路(v1.9.2 通道改造,设计 v2.2 §2.3):chooseMedia 单张 → 官方 compressImage
 * 压缩(quality 递降 + compressedWidth 降采样,目标入库 ≤200KB)→ 双通道上传:
 * - local/selfdeploy:wx.uploadFile multipart 主形态(二进制直传,无 base64 ×4/3 放大)
 * - cloud(体验版/正式版):callContainer 无文件直传能力,走 base64 JSON 兼容形态(200KB→payload ≈270KB)
 *
 * 服务端硬闸:≤512000 字节(40000)、每日每人 ≤20 张(40300,服务端文案由网络层统一 toast)。
 * 前端作主压缩 ≤200KB(减轻 MySQL blob 体积;服务端不二次压缩)。
 *
 * 场景枚举 PhotoScene:MOMENT(家人瞬间)/ CAPSULE(时光胶囊)。
 */
const { request, ensureLogin, getToken, setToken, BASE_URL, USE_CLOUD, CHANNEL } = require('./request')

/** 云托管公网回图域名(部署后填,与 request.js 的 SERVICE_NAME 同处维护;设计 §2.3 部署 checklist) */
const CLOUD_PHOTO_BASE = ''

/** 入库压缩目标(契约 v1.9.2:前端作主压缩 ≤200KB;服务端 512000 硬闸远在上层,无需再留余量) */
const TARGET_KB = 200

/** 压缩质量:80 起,每轮 -20,下探到 20 */
const QUALITY_START = 80
const QUALITY_STEP = 20
const QUALITY_MIN = 20

/** 降采样宽度阶梯(quality 只对 jpg 有效,png/webp 压不动时靠官方 compressedWidth 缩分辨率) */
const SAMPLE_WIDTHS = [0, 1080, 720, 480]

/** 相对路径 photoUrl(契约恒 /api/photos/{photoId})→ <image> 可用的绝对地址
 *  selfdeploy:/api/photos/* 免鉴权,自部署域名直接回图(天然可用);
 *  local:本地直连;cloud:CLOUD_PHOTO_BASE 公网回图域名(部署后填)。 */
function photoUrl(idOrPath) {
  const path = String(idOrPath || '')
  const rel = path.indexOf('/') === 0 ? path : '/api/photos/' + path
  if (CHANNEL === 'selfdeploy' || CHANNEL === 'local') {
    return BASE_URL + rel
  }
  return (USE_CLOUD && CLOUD_PHOTO_BASE ? CLOUD_PHOTO_BASE : BASE_URL) + rel
}

/** 单文件字节数(Promise 形态;失败 -1,调用方按「读不出大小」中止,防原图盲传超服务端闸) */
function fileSizeOf(filePath) {
  return new Promise((resolve) => {
    wx.getFileSystemManager().getFileInfo({
      filePath,
      success: (res) => resolve(res.size),
      fail: () => resolve(-1),
    })
  })
}

/** 压缩一轮(官方 wx.compressImage;小图可能失败,失败时回退原图走后续大小校验)
 *  compressedWidth=0 表示不降采样(仅降质量);>0 时官方参数缩分辨率(jpg/png 均生效) */
function compressOnce(src, quality, compressedWidth) {
  return new Promise((resolve) => {
    const options = {
      src,
      quality,
      success: (res) => resolve(res.tempFilePath),
      fail: () => resolve(src),
    }
    if (compressedWidth > 0) {
      options.compressedWidth = compressedWidth
    }
    wx.compressImage(options)
  })
}

/**
 * 压缩循环:先原尺寸降质量(80→20),仍超则逐级降采样(1080→720→480)重降质量;
 * 全部阶梯走完仍超 → 返回 null(调用方 toast 中止,与「用户取消」同等静默处理)。
 * 大小读不出(-1)也中止:宁可换图,不可原图直传赌服务端闸。
 */
async function compressToFit(src, maxBytes) {
  let path = src
  for (let i = 0; i < SAMPLE_WIDTHS.length; i++) {
    const width = SAMPLE_WIDTHS[i]
    let quality = QUALITY_START
    while (quality >= QUALITY_MIN) {
      const size = await fileSizeOf(path)
      if (size === -1) {
        return null
      }
      if (size <= maxBytes) {
        return path
      }
      path = await compressOnce(path, quality, width)
      quality -= QUALITY_STEP
    }
  }
  return null
}

/** 读 base64(Promise 形态,仅 cloud 兼容形态用) */
function readBase64(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath,
      encoding: 'base64',
      success: (res) => resolve(res.data),
      fail: () => reject(new Error('read file failed')),
    })
  })
}

/**
 * 选一张照片并上传(照片为必选;用户取消 chooseMedia 属正常流,resolve(null))
 * @param {number} maxSizeKB 入库压缩目标 KB(默认 200;服务端硬闸 512000 字节兜底)
 * @param {string} scene PhotoScene,默认 'MOMENT'(胶囊接线时传 'CAPSULE')
 * @returns {Promise<{photoId:string,url:string}|null>} photoId 供后续绑定(发瞬间/封胶囊)
 */
async function chooseAndUploadPhoto(maxSizeKB, scene) {
  const targetKB = maxSizeKB || TARGET_KB
  const photoScene = scene || 'MOMENT'
  // 1. 单张选取(隐私最小化:不批量读相册)
  const chosen = await new Promise((resolve) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: (res) => resolve(res.tempFiles && res.tempFiles[0]),
      fail: () => resolve(null), // 用户取消
    })
  })
  if (!chosen) {
    return null
  }
  // 2. 压缩循环;选完再出 loading,不挡相册选择器
  wx.showLoading({ title: '上传照片', mask: true })
  const compressed = await compressToFit(chosen.tempFilePath, targetKB * 1024)
  if (!compressed) {
    wx.hideLoading()
    wx.showToast({ title: '这张照片压不到合适大小，换一张试试', icon: 'none', duration: 2200 })
    return null
  }
  // 3. 上传:cloud 走 base64 JSON 兼容形态(callContainer 无文件直传);其余走 wx.uploadFile multipart
  try {
    const uploaded = await uploadCompressed(compressed, photoScene)
    return { photoId: uploaded.photoId, url: photoUrl(uploaded.photoUrl || uploaded.photoId) }
  } finally {
    wx.hideLoading()
  }
}

/** 压缩件上传:按通道分流(40300/40000 等业务错误由网络层统一 toast) */
function uploadCompressed(filePath, photoScene) {
  if (USE_CLOUD) {
    return readBase64(filePath).then((imageBase64) =>
      request('/api/photos', { method: 'POST', data: { imageBase64, scene: photoScene } }))
  }
  return uploadViaHttp(filePath, photoScene)
}

/** multipart 主形态(wx.uploadFile;local 直连本地后端 / selfdeploy 自部署域名)
 *  响应处理镜像 request.js:业务码 toast、401/40100 清 token 重登重放一次(_retried 防死循环) */
function uploadViaHttp(filePath, photoScene, retried) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    wx.uploadFile({
      url: BASE_URL + '/api/photos',
      filePath,
      name: 'image',
      formData: { scene: photoScene },
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => resolve(handleUploadResponse(res, filePath, photoScene, retried)),
      fail: () => {
        wx.showToast({ title: '网络不太好,请您稍后再试', icon: 'none', duration: 2200 })
        reject(new Error('network error'))
      },
    })
  })
}

/** uploadFile 响应体是字符串 JSON,手动 parse + 业务码分发(对齐 request.js handleResponse) */
function handleUploadResponse(res, filePath, photoScene, retried) {
  let body = null
  try {
    body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
  } catch (e) {
    console.error('[photo] 响应不是合法 JSON', { statusCode: res.statusCode, data: res.data })
    wx.showToast({ title: '服务开小差了,请您稍后再试', icon: 'none', duration: 2200 })
    throw new Error('bad response')
  }
  if (res.statusCode >= 200 && res.statusCode < 300) {
    if (body && body.code && body.code !== '0' && parseInt(body.code, 10) >= 40000) {
      if (body.code === '40100' && !retried) {
        setToken('')
        return ensureLogin().then(() => uploadViaHttp(filePath, photoScene, true))
      }
      wx.showToast({ title: body.message || '服务开小差了,请您稍后再试', icon: 'none', duration: 2200 })
      throw new Error(body.code)
    }
    return body
  }
  console.error('[photo] HTTP ' + res.statusCode, { data: res.data })
  if (res.statusCode === 401 && !retried) {
    setToken('')
    return ensureLogin().then(() => uploadViaHttp(filePath, photoScene, true))
  }
  wx.showToast({ title: '服务开小差了,请您稍后再试', icon: 'none', duration: 2200 })
  throw new Error('HTTP ' + res.statusCode)
}

module.exports = { chooseAndUploadPhoto, photoUrl }
