/**
 * 照片上传链路(设计 v2.2 §2.3):chooseMedia 单张 → 压缩循环 → base64 → POST /api/photos
 *
 * 服务端硬闸:解码后 ≤512000 字节(40000)、每日每人 ≤20 张(40300,服务端文案
 * 「今天传了 20 张照片，明天再来」,由 request.js 统一 toast);前端压缩目标留 20KB 余量。
 *
 * 场景枚举 PhotoScene:MOMENT(家人瞬间,本批)/ CAPSULE(时光胶囊,下批接线)。
 */
const { request, BASE_URL, USE_CLOUD } = require('./request')

/** 云托管公网回图域名(部署后填,与 request.js 的 SERVICE_NAME 同处维护;设计 §2.3 部署 checklist) */
const CLOUD_PHOTO_BASE = ''

/** 压缩起点与步长:quality 80 起,每轮 -20,下探到 20 仍超则中止换图 */
const QUALITY_START = 80
const QUALITY_STEP = 20
const QUALITY_MIN = 20

/** 相对路径 photoUrl(契约恒 /api/photos/{photoId})→ <image> 可用的绝对地址 */
function photoUrl(idOrPath) {
  const path = String(idOrPath || '')
  const rel = path.indexOf('/') === 0 ? path : '/api/photos/' + path
  return (USE_CLOUD && CLOUD_PHOTO_BASE ? CLOUD_PHOTO_BASE : BASE_URL) + rel
}

/** 单文件字节数(Promise 形态) */
function fileSizeOf(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().getFileInfo({
      filePath,
      success: (res) => resolve(res.size),
      fail: () => reject(new Error('read size failed')),
    })
  })
}

/** 压缩一轮(小图 compressImage 可能失败,失败时回退原图走后续大小校验) */
function compressOnce(src, quality) {
  return new Promise((resolve) => {
    wx.compressImage({
      src,
      quality,
      success: (res) => resolve(res.tempFilePath),
      fail: () => resolve(src),
    })
  })
}

/**
 * 压缩循环:从 quality 80 起,>目标大小则每轮 -20 递降到 20;
 * 仍超 → toast 中止(返回 null,调用方按「用户取消」同等静默处理)
 */
async function compressToFit(src, maxBytes) {
  let path = src
  let size = await fileSizeOf(path).catch(() => 0)
  let quality = QUALITY_START
  while (size > maxBytes && quality > QUALITY_MIN) {
    path = await compressOnce(path, quality)
    size = await fileSizeOf(path).catch(() => 0)
    if (size <= maxBytes) {
      break
    }
    quality -= QUALITY_STEP
  }
  return size <= maxBytes ? path : null
}

/** 读 base64(Promise 形态) */
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
 * @param {number} maxSizeKB 压缩目标 KB(默认 500;内部再留 20KB 余量对齐服务端 512000 硬闸)
 * @param {string} scene PhotoScene,默认 'MOMENT'(胶囊接线时传 'CAPSULE')
 * @returns {Promise<{photoId:string,url:string}|null>} photoId 供后续绑定(发瞬间/封胶囊)
 */
async function chooseAndUploadPhoto(maxSizeKB, scene) {
  const limitKB = maxSizeKB || 500
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
  // 2. 压缩循环(480KB = 500KB - 20KB 余量);选完再出 loading,不挡相册选择器
  wx.showLoading({ title: '上传照片', mask: true })
  const compressed = await compressToFit(chosen.tempFilePath, (limitKB - 20) * 1024)
  if (!compressed) {
    wx.hideLoading()
    wx.showToast({ title: '这张照片太大，换一张试试', icon: 'none', duration: 2200 })
    return null
  }
  // 3. base64 → POST /api/photos(普通 JSON 请求,双通道零改动;40300 由网络层 toast)
  try {
    const imageBase64 = await readBase64(compressed)
    const res = await request('/api/photos', {
      method: 'POST',
      data: { imageBase64, scene: photoScene },
    })
    return { photoId: res.photoId, url: photoUrl(res.photoUrl || res.photoId) }
  } finally {
    wx.hideLoading()
  }
}

module.exports = { chooseAndUploadPhoto, photoUrl }
