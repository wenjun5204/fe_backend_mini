/**
 * 生日福卡生成页(v1.8.0,设计 v2.2 §2.2)
 * 唯一入口=日子详情(kind=BIRTHDAY)「🎂 做生日福卡」,无模板馆入口、无时间门控:
 * 生日当天由提醒自然触达,非当天也能做(按钮常驻)。
 *
 * 链路:onLoad(dayId) → GET /api/days/{id}(relation/落点展示)
 * + GET /api/days/{id}/moments(素材池=家人收藏进该日子的瞬间,九宫格点选 1-3 张)
 * → 祝福语点选(family-data.BLESSING_CHIPS)或自由输入(1-40 字,服务端过违禁词表)
 * → 「🎂 生成生日福卡」POST /api/cards(templateKind=BIRTHDAY_CARD,
 *   payload={dayId, blessingText, momentIds, styleKey:'WARM'};relation 服务端以
 *   DayRecord 覆盖,前端不传)→ POST share → poster 舞台(BIRTHDAY_CARD 形态)。
 * 素材池为空:空态卡引导去家人 Tab(没有收藏瞬间做不了福卡,契约 momentIds 1-3 必填)。
 */
const api = require('../../utils/api-cards')
const fam = require('../../utils/api-family')
const { getDay } = require('../../utils/api-days')
const { ensureLogin } = require('../../utils/request')
const { viewOf } = require('../../utils/days-data')
const { BLESSING_CHIPS } = require('../../utils/family-data')
const { photoUrl } = require('../../utils/photo')
const { markMadeToday } = require('../../utils/cards-data')
const { posterPageShare } = require('../../utils/share')

/** 契约 blessingText 1-40 字(服务端同限,前端同口径提示) */
const BLESS_MAX = 40
/** 契约 momentIds 1-3 张(去重,服务端同限) */
const PHOTO_MAX = 3

Page(
  Object.assign(
    {
      data: {
        dayId: 0,
        loading: true,
        day: null, // {name, relation, dateText, emoji}
        pool: [], // 素材池九宫格
        chips: BLESSING_CHIPS,
        blessing: '',
        blessLen: 0,
        selected: [], // momentIds(1-3)
        previewPhoto: '',
        submitting: false,
      },

      onLoad(options) {
        const dayId = Number((options && options.dayId) || options.id)
        if (!dayId) {
          wx.showToast({ title: '日子参数缺失', icon: 'none' })
          setTimeout(() => wx.navigateBack(), 600)
          return
        }
        this.setData({ dayId })
        this.load()
      },

      load() {
        ensureLogin()
          .catch(() => {})
          .then(() =>
            Promise.all([getDay(this.data.dayId), fam.listDayMoments(this.data.dayId)])
          )
          .then(([record, poolRes]) => {
            const v = viewOf(record)
            this.setData({
              loading: false,
              day: {
                name: v.name,
                relation: record.relation || '',
                dateText: v.dateText,
                emoji: v.emoji,
              },
              pool: ((poolRes && poolRes.items) || []).map((m) => ({
                id: m.id,
                photo: photoUrl(m.photoUrl || m.photoId),
                on: false,
              })),
            })
          })
          .catch(() => {
            // 40000/40400 文案由网络层统一 toast;返回上一页
            this.setData({ loading: false })
            setTimeout(() => this.backHome(), 1200)
          })
      },

      /* 祝福语点选(填入输入框,仍可改) */
      pickBlessing(e) {
        const text = e.currentTarget.dataset.text || ''
        this.setData({ blessing: text, blessLen: text.length })
      },

      /* 祝福语自由输入(1-40 字) */
      onBlessInput(e) {
        const text = e.detail.value || ''
        this.setData({ blessing: text, blessLen: text.length })
      },

      /* 素材九宫格点选(1-3 张;再点取消) */
      togglePhoto(e) {
        const id = e.currentTarget.dataset.id
        const sel = this.data.selected.slice()
        const idx = sel.indexOf(id)
        if (idx >= 0) {
          sel.splice(idx, 1)
        } else {
          if (sel.length >= PHOTO_MAX) {
            wx.showToast({ title: '最多选 ' + PHOTO_MAX + ' 张照片', icon: 'none' })
            return
          }
          sel.push(id)
        }
        const first = this.data.pool.filter((m) => sel.indexOf(m.id) >= 0)[0]
        this.setData({
          selected: sel,
          previewPhoto: (first && first.photo) || '',
          pool: this.data.pool.map((m) => Object.assign({}, m, { on: sel.indexOf(m.id) >= 0 })),
        })
      },

      /* 「🎂 生成生日福卡」:定稿 → 取码 → 海报舞台 */
      make() {
        if (this.data.submitting) return
        const blessing = (this.data.blessing || '').trim()
        if (!blessing.length || blessing.length > BLESS_MAX) {
          wx.showToast({
            title: '祝福语要在 1 到 ' + BLESS_MAX + ' 字之间',
            icon: 'none',
          })
          return
        }
        if (!this.data.selected.length) {
          wx.showToast({ title: '先从收藏里选 1-3 张照片', icon: 'none', duration: 2200 })
          return
        }
        this.setData({ submitting: true })
        const payload = {
          dayId: this.data.dayId,
          blessingText: blessing,
          momentIds: this.data.selected,
          styleKey: 'WARM',
        }
        api
          .createCard({ templateKind: 'BIRTHDAY_CARD', payload })
          .then((card) => {
            const full = card && card.payload ? card : { templateKind: 'BIRTHDAY_CARD', payload }
            return Promise.all([full, api.shareCard(card.id)])
          })
          .then(([card, share]) => {
            markMadeToday('BIRTHDAY_CARD')
            this.setData({ submitting: false })
            const poster = this.selectComponent('#poster')
            if (poster) {
              poster.draw(card, share)
            }
          })
          .catch(() => {
            // 40000(素材池校验/违禁词)文案由网络层统一 toast
            this.setData({ submitting: false })
          })
      },

      /* 空态引导:去家人 Tab 发/收藏瞬间 */
      goFamily() {
        wx.switchTab({ url: '/pages/family/family' })
      },

      backHome() {
        const pages = getCurrentPages()
        if (pages.length > 1) {
          wx.navigateBack()
        } else {
          wx.switchTab({ url: '/pages/days/days' })
        }
      },
    },
    posterPageShare()
  )
)
