/**
 * 时光胶囊开箱页(深色 #12121f,对齐原型 .page-capsule-open)
 * 从「我的」页或列表进入(参数 capsuleId);GET /api/capsules/{id}:
 * - SEALED:摇晃胶囊(弹性动画)+「到日子了/还没到」状态(openDate 与今天比较,
 *   以服务端 dueNow/daysUntilOpen 为准),未到时按钮禁用+倒计时文案
 * - OPENED:内容已对本人常开,直接展示揭示态(不播动画)
 *
 * 「开启胶囊」→ 摇晃动画(0.9s)→ POST /api/capsules/{id}/open → 爆开消散(0.5s)
 * → 信件+照片位揭示(pop 0.5s)→ 双 CTA;总预算 ≤2.5s
 * 未到期 40900:网络层已 toast「还没到开箱的日子」,页面恢复可重试态兜底
 *
 * 双 CTA:⏳ 我也埋一个 → capsule-edit;📤 分享开箱 → 找该胶囊的 CAPSULE 邀请函卡
 * (POST /api/cards 对 OPENED 胶囊拒收,故复用封存时生成的邀请函;没有则 toast 提示)
 * → POST share → poster 海报舞台(页面混入 utils/share.js posterPageShare)
 */
const api = require('../../utils/api-cards')
const { fmtDot } = require('../../utils/cards-data')
const { posterPageShare } = require('../../utils/share')
const { getUser } = require('../../utils/request')

/** 摇晃时长(2 次 coshake 0.45s),到点后再发起开箱请求 */
const SHAKE_MS = 900
/** 爆开消散时长,结束后揭示信件 */
const BURST_MS = 500

Page(
  Object.assign(
    {
      data: {
        capsuleId: 0,
        loading: true,
        failed: false,
        // 封存态展示
        sealedDot: '',
        sealedAgo: 0,
        openDot: '',
        dueNow: false,
        daysUntilOpen: 0,
        // 开箱动画态
        opening: false,
        shakeOn: false,
        burstOn: false,
        // 揭示态
        opened: false,
        contentText: '',
        photoUrl: '',
        fromLine: '',
        sharing: false,
      },

      onLoad(options) {
        const id = parseInt(options && options.capsuleId, 10)
        if (!id) {
          wx.showToast({ title: '胶囊参数缺失', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 600)
          return
        }
        this.setData({ capsuleId: id })
        this.load()
      },

      load() {
        this.setData({ loading: true, failed: false })
        api
          .getMyCapsule(this.data.capsuleId)
          .then((c) => {
            this.applyCapsule(c)
            this.setData({ loading: false })
          })
          .catch(() => {
            this.setData({ loading: false, failed: true })
          })
      },

      /** 组装视图:SEALED 展示摇晃态;OPENED 内容已常开,直接进揭示态 */
      applyCapsule(c) {
        const sealedDate = String(c.sealedAt || '').split('T')[0]
        const sealedDot = fmtDot(sealedDate)
        if (c.status === 'OPENED') {
          this.reveal({
            contentText: c.contentText || '',
            photoUrl: c.photoUrl || '',
            sealedDot,
          })
          return
        }
        this.setData({
          sealedDot,
          sealedAgo: this.daysAgoOf(sealedDate),
          openDot: fmtDot(c.openDate),
          dueNow: !!c.dueNow,
          daysUntilOpen: c.daysUntilOpen || 0,
        })
      },

      /** 封存日距今天数(非负) */
      daysAgoOf(dateStr) {
        if (!dateStr) return 0
        const s = new Date(dateStr + 'T12:00:00')
        const t = new Date()
        t.setHours(12, 0, 0, 0)
        return Math.max(0, Math.round((t - s) / 86400000))
      },

      /** 「开启胶囊」:摇晃 → POST open → 爆开 → 揭示 */
      openBox() {
        if (this.data.opening || this.data.opened) return
        if (!this.data.dueNow) return
        this.setData({ opening: true, shakeOn: true })
        setTimeout(() => {
          api
            .openCapsule(this.data.capsuleId)
            .then((res) => {
              this.setData({ shakeOn: false, burstOn: true })
              setTimeout(() => {
                this.reveal({
                  contentText: (res && res.contentText) || '',
                  photoUrl: (res && res.photoUrl) || '',
                  sealedDot: this.data.sealedDot,
                })
              }, BURST_MS)
            })
            .catch(() => {
              // 40900 未到期等:网络层已 toast 文案,恢复可重试态
              this.setData({ opening: false, shakeOn: false })
            })
        }, SHAKE_MS)
      },

      /** 揭示态:信件 + 照片位(pop 动画)+ 双 CTA */
      reveal(info) {
        const user = getUser()
        const who = (user && user.nickname) || '你'
        this.setData({
          opening: false,
          burstOn: false,
          opened: true,
          contentText: info.contentText,
          photoUrl: info.photoUrl,
          fromLine: '来自 ' + info.sealedDot + ' 的 ' + who,
        })
      },

      /** ⏳ 我也埋一个 */
      goSeal() {
        wx.navigateTo({ url: '/pages/capsule-edit/capsule-edit' })
      },

      /**
       * 📤 分享开箱:复用封存时生成的 CAPSULE 邀请函卡(OPENED 胶囊不可再建卡,
       * 后端 applyCapsule 仅收 SEALED)→ POST share → poster 海报舞台
       */
      shareOpen() {
        if (this.data.sharing) return
        this.setData({ sharing: true })
        api
          .listMyCards()
          .then((res) => {
            const card = ((res && res.items) || []).filter(
              (it) => it.capsuleId === this.data.capsuleId
            )[0]
            if (!card) {
              this.setData({ sharing: false })
              wx.showToast({ title: '这枚胶囊还没有邀请函海报', icon: 'none' })
              return null
            }
            return api.shareCard(card.id).then((share) => {
              this.setData({ sharing: false })
              const poster = this.selectComponent('#poster')
              if (poster) {
                poster.draw(card, share)
              }
            })
          })
          .catch(() => {
            this.setData({ sharing: false })
          })
      },
    },
    posterPageShare()
  )
)
