/**
 * 时光胶囊封存:类型二选(SELF/PARTNER)+ 开箱规则 chips(ONE_YEAR_LATER 服务端算日期,
 * 其余 GAOKAO_SCORE_DAY/WEDDING_ANNIVERSARY/BABY_18 需选未来日期——契约 CapsuleOpenRule)
 * + 封面提示语 chips + 内容文本域(20-200 字,契约后端上限 500,前端收紧)
 * + 可选照片(utils/photo.chooseAndUploadPhoto('CAPSULE')真实上传 → photoId,
 * v1.8.0 契约 CapsuleCreateRequest +photoId;上传失败/用户取消 → 纯文字封存继续,不强阻断)
 *
 * 「✨ 封存并生成邀请函」:POST /api/capsules(带 photoId)→ 成功后提示订阅(
 * wx.requestSubscribeMessage 静默失败不阻断)→ POST /api/cards(kind=CAPSULE,
 * payload 仅 capsuleId,coverHint/openDate/type 服务端以胶囊实体为权威回填)
 * → POST share → poster 海报舞台
 */
const api = require('../../utils/api-cards')
const {
  CAPSULE_TYPES,
  CAPSULE_OPEN_RULES,
  COVER_HINTS,
  daysUntilOf,
  fmtDot,
  pad2,
  markMadeToday,
} = require('../../utils/cards-data')
const { chooseAndUploadPhoto } = require('../../utils/photo')
const { posterPageShare } = require('../../utils/share')

/** 内容长度:前端收紧 20-200 字(契约后端 1-500,产品口径 20-200) */
const CONTENT_MIN = 20
const CONTENT_MAX = 200

/** 订阅消息模板 ID 占位(运营在公众平台申请后替换;拒绝/失败均静默降级) */
const SUBSCRIBE_TMPL_ID = 'PLACEHOLDER_CAPSULE_TEMPLATE_ID'

Page(
  Object.assign(
    {
      data: {
        types: CAPSULE_TYPES,
        type: 'SELF',
        rules: CAPSULE_OPEN_RULES,
        openRule: 'ONE_YEAR_LATER',
        needDate: false,
        openDate: '',
        openDateDot: '',
        dateStart: '',
        coverHints: COVER_HINTS,
        coverHint: COVER_HINTS[0],
        contentText: '',
        contentLen: 0,
        photoPath: '',
        photoId: '',
        previewKicker: '',
        previewPending: false,
        previewDays: 0,
        submitting: false,
      },

      onLoad() {
        // 非「一年后」规则须选未来日期(picker 下限=明天)
        const tomorrow = new Date(Date.now() + 86400000)
        this.setData({
          dateStart:
            tomorrow.getFullYear() +
            '-' +
            pad2(tomorrow.getMonth() + 1) +
            '-' +
            pad2(tomorrow.getDate()),
        })
        this.refreshPreview()
      },

      /** 类型二选(契约 CapsuleType: SELF/PARTNER) */
      pickType(e) {
        this.setData({ type: e.currentTarget.dataset.key })
      },

      /** 开箱规则 chips(契约 CapsuleOpenRule;ONE_YEAR_LATER 服务端算 openDate=封存日+365) */
      pickRule(e) {
        this.setData({
          openRule: e.currentTarget.dataset.key,
          needDate: e.currentTarget.dataset.need,
        })
        this.refreshPreview()
      },

      /** 开箱日(仅非一年后规则显示;须为未来日期,服务端 parseFutureDate 校验) */
      onDate(e) {
        this.setData({ openDate: e.detail.value })
        this.refreshPreview()
      },

      /** 封面提示语 chips(开箱人扫码第一眼;契约 coverHint 1-40 字) */
      pickHint(e) {
        this.setData({ coverHint: e.currentTarget.dataset.hint })
      },

      /** 内容输入(20-200 字) */
      onContent(e) {
        const text = e.detail.value || ''
        this.setData({ contentText: text, contentLen: text.length })
      },

      /**
       * 可选照片:真实上传链路(chooseAndUploadPhoto('CAPSULE') → photoId 随胶囊封存)
       * 用户取消/上传失败(40300 每日 20 张/超限)→ 保持原状,纯文字封存继续,不强阻断
       */
      choosePhoto() {
        chooseAndUploadPhoto(200, 'CAPSULE').then((res) => {
          if (!res) {
            return
          }
          this.setData({ photoPath: res.url, photoId: res.photoId })
        })
      },

      removePhoto() {
        this.setData({ photoPath: '', photoId: '' })
      },

      /** 深色预览:开箱倒计时(一年后=今天+365;其余按所选日期) */
      refreshPreview() {
        const oneYear = this.data.openRule === 'ONE_YEAR_LATER'
        const target = oneYear
          ? this.formatDate(new Date(Date.now() + 365 * 86400000))
          : this.data.openDate
        if (!target) {
          this.setData({ previewKicker: 'TIME CAPSULE', previewPending: true, previewDays: 0 })
          return
        }
        this.setData({
          previewKicker: 'SEALED UNTIL ' + fmtDot(target),
          previewPending: false,
          previewDays: daysUntilOf(target),
        })
      },

      formatDate(d) {
        return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
      },

      /** 「✨ 封存并生成邀请函」:封存 → 订阅提示 → CAPSULE 邀请函卡 → 海报舞台 */
      seal() {
        if (this.data.submitting) return
        const content = (this.data.contentText || '').trim()
        if (content.length < CONTENT_MIN) {
          wx.showToast({ title: '再写几句吧，至少 ' + CONTENT_MIN + ' 个字', icon: 'none' })
          return
        }
        if (content.length > CONTENT_MAX) {
          wx.showToast({ title: '最多 ' + CONTENT_MAX + ' 个字，再精炼一下', icon: 'none' })
          return
        }
        const rule = this.data.rules.filter((r) => r.key === this.data.openRule)[0]
        if (rule && rule.needDate && !this.data.openDate) {
          wx.showToast({ title: '先选开箱的日子', icon: 'none' })
          return
        }
        this.setData({ submitting: true })
        const body = {
          type: this.data.type,
          openRule: this.data.openRule,
          coverHint: this.data.coverHint,
          contentText: content,
        }
        if (this.data.photoId) {
          body.photoId = this.data.photoId
        }
        if (rule && rule.needDate) {
          body.openDate = this.data.openDate
        }
        api
          .sealCapsule(body)
          .then((capsule) => {
            // 封存成功即提示订阅开箱日提醒(静默失败不阻断)
            this.trySubscribe()
            return api.createCard({
              templateKind: 'CAPSULE',
              payload: { capsuleId: capsule.id },
            })
          })
          .then((card) => Promise.all([card, api.shareCard(card.id)]))
          .then(([card, share]) => {
            markMadeToday('CAPSULE')
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

      /** 订阅消息授权(模板 ID 占位;拒绝/失败均静默降级,不影响封存) */
      trySubscribe() {
        try {
          wx.requestSubscribeMessage({
            tmplIds: [SUBSCRIBE_TMPL_ID],
            success: () => {},
            fail: () => {},
          })
        } catch (e) {
          // 静默降级
        }
      },
    },
    posterPageShare()
  )
)
