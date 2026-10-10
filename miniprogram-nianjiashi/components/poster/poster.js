/**
 * 海报舞台组件:canvas 2d 绘制 750×1200 成品图 + 底部分享面板
 * 三套样式对齐原型 POSTERS:LETTER 深夜星河 / DAYS_CARD 霞橘(按 styleKey) / CAPSULE 青金
 *
 * 页面用法:
 *   <poster id="poster" bindsharecfg="onShareCfg" />
 *   const poster = this.selectComponent('#poster')
 *   poster.draw(card, sceneInfo)
 *     card      = { templateKind: 'LETTER'|'DAYS_CARD'|'CAPSULE', payload: {...} } (POST /api/cards 的回显)
 *     sceneInfo = { scene, pagePath } (POST /api/cards/{id}/share 的回显;缺失时 QR 用兜底内容)
 *   poster.saveToAlbum()  // 保存图片(按钮与外部均可调)
 * 绘制完成后触发 sharecfg 事件({pagePath,title,imageUrl})——页面混入 utils/share.js
 * 的 posterPageShare() 后,微信好友转发卡片即带 scene 直达访客落地页。
 */
const {
  POSTER_GRADS,
  daysTogetherOf,
  daysUntilOf,
  fmtDot,
  QR_LINK_MODE,
  QR_HOST,
} = require('../../utils/cards-data')
const { SHARE_TITLES } = require('../../utils/share')
const qrcode = require('../../utils/qrcode')

/** 设计稿宽高(canvas 逻辑坐标系,wxss 侧 600rpx×960rpx 等比展示) */
const DW = 750
const DH = 1200

/** 按 kind 把卡片定稿组装为绘制视图(渐变/kicker/标题/大字/正文行/品牌 foot) */
function viewOf(card) {
  const kind = card.templateKind
  const p = card.payload || {}
  if (kind === 'DAYS_CARD') {
    return {
      grad: POSTER_GRADS[p.styleKey] || POSTER_GRADS.SUNSET,
      kicker: 'DAYS TOGETHER',
      title: '在一起的第 N 天',
      emoji: '',
      bigNum: String(daysTogetherOf(p.startDate)),
      bigUnit: ' 天',
      body: ['“从 ' + fmtDot(p.startDate) + ' 到今天”'],
      sign: '',
      foot: '做一张你们的',
    }
  }
  if (kind === 'CAPSULE') {
    return {
      grad: POSTER_GRADS.MINT,
      kicker: 'TIME CAPSULE',
      title: '一年后见',
      emoji: '🔒',
      bigNum: String(daysUntilOf(p.openDate)),
      bigUnit: ' 天后开启',
      body: p.coverHint ? [p.coverHint] : [],
      sign: '',
      foot: '亲手开启',
    }
  }
  // LETTER(深夜星河):信文居中,取前 3 段加引号,落款右下
  const paras = p.paragraphs || []
  const shown = paras.slice(0, 3)
  const lines = shown.map((t, i) => {
    let line = t
    if (i === 0) line = '“' + line
    if (i === shown.length - 1) line = line + '”' + (paras.length > 3 ? '…' : '')
    return line
  })
  return {
    grad: POSTER_GRADS.NIGHT,
    kicker: '✉ A LETTER TO MOM',
    title: '写给' + (p.recipient || '妈妈') + '的信',
    emoji: '💌',
    bigNum: '',
    bigUnit: '',
    body: lines,
    sign: p.signText || '',
    foot: '看完这封信的结尾',
  }
}

/** 正文折行:每 22 字换行(设计 §2.3) */
function wrapText(text, max) {
  const lines = []
  let rest = String(text || '')
  while (rest.length > max) {
    lines.push(rest.slice(0, max))
    rest = rest.slice(max)
  }
  if (rest) lines.push(rest)
  return lines
}

Component({
  data: {
    visible: false,
    drawing: false,
    footText: '',
  },

  lifetimes: {
    detached() {
      this._canvas = null
    },
  },

  methods: {
    noop() {},

    close() {
      this.setData({ visible: false })
    },

    /** 打开舞台并绘制(对外主入口) */
    draw(card, sceneInfo) {
      const view = viewOf(card || {})
      this._card = card || {}
      this._scene = sceneInfo || {}
      this.setData({ visible: true, drawing: true, footText: view.foot })
      wx.nextTick(() => {
        this.render(view)
      })
    },

    /** canvas 2d 实际绘制:设计坐标 750×1200 缩放到物理像素 */
    render(view) {
      const query = this.createSelectorQuery()
      query.select('#posterCanvas').fields({ node: true, size: true }).exec((res) => {
        const hit = res && res[0]
        if (!hit || !hit.node) {
          this.setData({ drawing: false })
          return
        }
        const canvas = hit.node
        const ctx = canvas.getContext('2d')
        const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
        const dpr = (info && info.pixelRatio) || 2
        canvas.width = hit.width * dpr
        canvas.height = hit.height * dpr
        // 设计坐标 → 物理像素(600rpx:750 与 960rpx:1200 同比例);setTransform 同时重置上次绘制的变换
        ctx.setTransform(canvas.width / DW, 0, 0, canvas.height / DH, 0, 0)
        // 清画布(同一组件多次绘制)
        ctx.clearRect(0, 0, DW, DH)
        this._canvas = canvas
        this.paint(ctx, view)
        this.setData({ drawing: false })
        this.emitShareCfg()
      })
    },

    paint(ctx, view) {
      // ===== 背景渐变(三套样式) =====
      const grad = ctx.createLinearGradient(0, 0, DW, DH)
      grad.addColorStop(0, view.grad[0])
      grad.addColorStop(1, view.grad[1])
      ctx.fillStyle = grad
      ctx.fillRect(0, 0, DW, DH)

      ctx.textAlign = 'center'
      ctx.textBaseline = 'alphabetic'

      // ===== 顶部 kicker / 标题 =====
      ctx.fillStyle = 'rgba(255,255,255,0.75)'
      ctx.font = 'bold 26px sans-serif'
      ctx.fillText(view.kicker, DW / 2, 128)

      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 54px sans-serif'
      ctx.fillText(view.title, DW / 2, 198)

      // ===== 中部内容(emoji / 天数大字) =====
      if (view.emoji) {
        ctx.font = '100px sans-serif'
        ctx.fillText(view.emoji, DW / 2, 328)
      }
      if (view.bigNum) {
        ctx.fillStyle = '#ffffff'
        ctx.font = 'bold 170px Georgia, serif'
        ctx.fillText(view.bigNum, DW / 2, view.emoji ? 470 : 430)
        ctx.font = 'bold 44px sans-serif'
        ctx.fillText(view.bigUnit, DW / 2, view.emoji ? 540 : 500)
      }

      // ===== 信文/正文(每行 22 字折行,居中) =====
      ctx.fillStyle = 'rgba(255,255,255,0.92)'
      ctx.font = '30px sans-serif'
      const bodyLines = []
      ;(view.body || []).forEach((t) => {
        wrapText(t, 22).forEach((l) => bodyLines.push(l))
      })
      let y = view.bigNum ? 640 : view.emoji ? 440 : 300
      bodyLines.forEach((line) => {
        ctx.fillText(line, DW / 2, y)
        y += 56
      })

      // ===== 落款(LETTER,右下) =====
      if (view.sign) {
        ctx.textAlign = 'right'
        ctx.fillStyle = 'rgba(255,255,255,0.85)'
        ctx.font = '28px sans-serif'
        ctx.fillText(view.sign, DW - 60, y + 28)
        ctx.textAlign = 'center'
      }

      // ===== 底部品牌位 + 二维码 =====
      ctx.strokeStyle = 'rgba(255,255,255,0.25)'
      ctx.lineWidth = 2
      ctx.beginPath()
      ctx.moveTo(110, 985)
      ctx.lineTo(DW - 110, 985)
      ctx.stroke()

      ctx.textAlign = 'left'
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 38px sans-serif'
      ctx.fillText('念家时', 60, 1065)
      ctx.fillStyle = 'rgba(255,255,255,0.65)'
      ctx.font = '24px sans-serif'
      ctx.fillText('把重要的日子，记在心上', 60, 1110)

      this.paintQr(ctx)
    },

    /** 二维码区:内容两态见 utils/cards-data.js QR_LINK_MODE */
    paintQr(ctx) {
      const scene = this._scene || {}
      let text = ''
      if (QR_LINK_MODE === 'url' && QR_HOST && scene.scene) {
        text = 'https://' + QR_HOST + '/p/' + scene.scene
      } else if (scene.pagePath) {
        text = scene.pagePath
      } else {
        text = 'pages/days/days'
      }

      const boxX = 490
      const boxY = 980
      const boxSize = 200
      ctx.fillStyle = '#ffffff'
      this.roundRect(ctx, boxX, boxY, boxSize, boxSize, 16)
      ctx.fill()

      try {
        qrcode.stringToBytes = qrcode.stringToBytesFuncs['UTF-8']
        const qr = qrcode(0, 'M')
        qr.addData(text, 'Byte')
        qr.make()
        const n = qr.getModuleCount()
        const margin = 16
        const cell = (boxSize - margin * 2) / n
        ctx.fillStyle = '#1c1c28'
        for (let r = 0; r < n; r++) {
          for (let c = 0; c < n; c++) {
            if (qr.isDark(r, c)) {
              ctx.fillRect(boxX + margin + c * cell, boxY + margin + r * cell, cell + 0.5, cell + 0.5)
            }
          }
        }
      } catch (e) {
        // 矩阵生成异常不阻断海报(白框保留,主链路走好友转发)
      }
    },

    roundRect(ctx, x, y, w, h, r) {
      ctx.beginPath()
      ctx.moveTo(x + r, y)
      ctx.arcTo(x + w, y, x + w, y + h, r)
      ctx.arcTo(x + w, y + h, x, y + h, r)
      ctx.arcTo(x, y + h, x, y, r)
      ctx.arcTo(x, y, x + w, y, r)
      ctx.closePath()
    },

    /** 导出临时图并广播分享配置(pagePath/title/imageUrl) */
    emitShareCfg() {
      const scene = this._scene || {}
      const kind = this._card && this._card.templateKind
      const payload = {
        pagePath: scene.pagePath || '',
        title: SHARE_TITLES[kind] || '念家时 · 把重要的日子，记在心上',
        imageUrl: '',
      }
      const canvas = this._canvas
      if (!canvas) {
        this.triggerEvent('sharecfg', payload)
        return
      }
      wx.canvasToTempFilePath({
        canvas,
        success: (res) => {
          this._tempPath = res.tempFilePath || ''
          this.triggerEvent('sharecfg', Object.assign({}, payload, { imageUrl: this._tempPath }))
        },
        fail: () => {
          this.triggerEvent('sharecfg', payload)
        },
      })
    },

    /** 保存成品图到相册(质量门要求:canvasToTempFilePath + saveImageToPhotosAlbum) */
    saveToAlbum() {
      const canvas = this._canvas
      if (!canvas) {
        wx.showToast({ title: '海报还没画好，稍等一下', icon: 'none' })
        return
      }
      wx.canvasToTempFilePath({
        canvas,
        success: (res) => {
          wx.saveImageToPhotosAlbum({
            filePath: res.tempFilePath,
            success: () => {
              wx.showToast({ title: '已保存到相册', icon: 'success' })
            },
            fail: (err) => {
              const msg = (err && err.errMsg) || ''
              if (msg.indexOf('auth') >= 0 || msg.indexOf('deny') >= 0) {
                wx.showModal({
                  title: '需要相册权限',
                  content: '在设置里允许保存到相册，海报才能存下来',
                  confirmText: '去设置',
                  success: (r) => {
                    if (r.confirm) {
                      wx.openSetting()
                    }
                  },
                })
              } else {
                wx.showToast({ title: '保存没成功，再试一次', icon: 'none' })
              }
            },
          })
        },
        fail: () => {
          wx.showToast({ title: '导出海报没成功，再试一次', icon: 'none' })
        },
      })
    },

    /** 分享面板按钮 */
    saveAlbum() {
      this.saveToAlbum()
    },

    /** 面对面:出示本机海报,对面扫码即达(访客页免登录) */
    faceToFace() {
      wx.showToast({ title: '把这张海报给对面，扫码就能看', icon: 'none', duration: 2400 })
    },
  },
})
