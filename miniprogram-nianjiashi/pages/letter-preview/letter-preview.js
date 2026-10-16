/**
 * 感恩信第二步:AI 生成 → 信纸预览改稿 → 定稿出海报
 * - 进入即 POST /api/templates/letter/generate(letter-edit 存的 njs_letter_req),
 *   AI 加载动画 ≤2.5s(旋转光球+文案轮换,超时转「还在写,可返回」态)
 * - 每段右侧「换」:variants 内循环 (cur+1)%len,不相邻重复
 * - 整体重写 = 同参重调 generate(计入服务端每日 5 次频控,40300 由网络层 toast)
 * - 定稿 POST /api/cards(kind=LETTER,payload 含定稿段落)→ POST share 取 scene
 *   → poster 组件全屏舞台(好友转发 path 带 scene/保存图片/取消)
 * 页面混入 utils/share.js 的 posterPageShare():海报组件 sharecfg 事件落地转发配置
 */
const api = require('../../utils/api-cards')
const { markMadeToday, STORIES } = require('../../utils/cards-data')
const { posterPageShare } = require('../../utils/share')

const DRAFT_KEY = 'njs_letter_req'
/** 动画最少展示 900ms(接口降级路径 <500ms 也要有「正在写」的心智) */
const MIN_LOADING = 900
/** 2.5s 预算:超时后动画转「还在写,可返回」态(设计 §4 风险对策) */
const SLOW_AT = 2500
const LOADING_TEXTS = ['正在替你写信…', '把你们的故事，写成想说的话…', '斟酌一下语气…']

Page(
  Object.assign(
    {
      data: {
        aiLoading: true,
        aiText: LOADING_TEXTS[0],
        aiSlow: false,
        req: { recipient: '妈妈', stories: [], tone: 'TEAR' },
        paragraphs: [],
        signText: '',
        failed: false,
        submitting: false,
      },

      onLoad() {
        let req = null
        try {
          req = wx.getStorageSync(DRAFT_KEY)
        } catch (e) {
          req = null
        }
        if (!req || !req.recipient) {
          req = { recipient: '妈妈', stories: [STORIES[0]], tone: 'TEAR' }
        }
        this.setData({ req })
        this.generate()
      },

      onUnload() {
        this.stopLoading()
      },

      // ===== AI 加载动画 =====

      startLoading() {
        this.stopLoading()
        this._textIdx = 0
        this.setData({ aiLoading: true, aiSlow: false, aiText: LOADING_TEXTS[0] })
        this._textTimer = setInterval(() => {
          this._textIdx = (this._textIdx + 1) % LOADING_TEXTS.length
          this.setData({ aiText: LOADING_TEXTS[this._textIdx] })
        }, 900)
        this._slowTimer = setTimeout(() => {
          if (this.data.aiLoading) {
            this.setData({ aiSlow: true })
          }
        }, SLOW_AT)
      },

      stopLoading() {
        if (this._textTimer) {
          clearInterval(this._textTimer)
          this._textTimer = null
        }
        if (this._slowTimer) {
          clearTimeout(this._slowTimer)
          this._slowTimer = null
        }
        this.setData({ aiLoading: false })
      },

      loadingBack() {
        this.stopLoading()
        wx.navigateBack()
      },

      // ===== 生成与改稿 =====

      generate() {
        this.setData({ failed: false })
        this.startLoading()
        const startedAt = Date.now()
        const minWait = new Promise((resolve) => {
          setTimeout(resolve, MIN_LOADING)
        })
        api
          .generateLetter(this.data.req)
          .then((res) => {
            Promise.all([Promise.resolve(res), minWait]).then(([r]) => {
              this.applyLetter(r)
            })
          })
          .catch(() => {
            // 40300 频控/网络错误:网络层已 toast,此处回到可重试态
            setTimeout(() => {
              this.stopLoading()
              this.setData({ failed: true })
            }, Math.max(0, MIN_LOADING - (Date.now() - startedAt)))
          })
      },

      applyLetter(res) {
        const paragraphs = ((res && res.paragraphs) || []).map((seg, idx) => {
          const variants = seg.variants && seg.variants.length ? seg.variants : [seg.selected || '']
          let cur = variants.indexOf(seg.selected)
          if (cur < 0) cur = 0
          return { id: idx, variants, cur, text: variants[cur] }
        })
        this.stopLoading()
        if (!paragraphs.length) {
          this.setData({ failed: true })
          return
        }
        this.setData({ paragraphs, signText: (res && res.signText) || '', failed: false })
      },

      /** 每段「换」:variants 内循环 */
      swapPara(e) {
        const idx = e.currentTarget.dataset.idx
        const para = this.data.paragraphs[idx]
        if (!para || para.variants.length < 2) return
        const next = (para.cur + 1) % para.variants.length
        this.setData({
          ['paragraphs[' + idx + '].cur']: next,
          ['paragraphs[' + idx + '].text']: para.variants[next],
        })
      },

      /** 整体重写(同参重调,计入每日 5 次频控) */
      regen() {
        if (this.data.aiLoading) return
        this.generate()
      },

      // ===== 定稿出海报 =====

      makePoster() {
        if (this.data.submitting || this.data.aiLoading) return
        if (!this.data.paragraphs.length) return
        this.setData({ submitting: true })
        const req = this.data.req
        const payload = {
          recipient: req.recipient,
          tone: req.tone,
          paragraphs: this.data.paragraphs.map((p) => p.text),
          signText: this.data.signText,
          styleKey: 'NIGHT',
        }
        api
          .createCard({ templateKind: 'LETTER', payload })
          .then((card) => {
            const full = card && card.payload ? card : { templateKind: 'LETTER', payload }
            return Promise.all([full, api.shareCard(card.id)])
          })
          .then(([card, share]) => {
            markMadeToday('LETTER')
            this.setData({ submitting: false })
            const poster = this.selectComponent('#poster')
            if (poster) {
              poster.draw(card, share)
            }
          })
          .catch(() => {
            this.setData({ submitting: false })
          })
      },
    },
    posterPageShare()
  )
)
