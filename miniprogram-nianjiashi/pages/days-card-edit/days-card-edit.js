/**
 * 相伴天数卡:关系 chips + 在一起的日子(picker 年月日,默认今天-1 天)
 * + 样式三选(霞橘/深夜星河/薄荷清晨 → 契约 styleKey)+ 深色实时预览卡(本地天数计算)
 * 「✨ 生成天数卡片」→ POST /api/cards(kind=DAYS_CARD,payload{relation,startDate,styleKey})
 * → POST share → poster 组件全屏海报舞台(页面混入 utils/share.js posterPageShare)
 * 注:DAYS_CARD 卡 payload 仅 relation/startDate/styleKey 三字段,其余契约拒收(40000)
 */
const api = require('../../utils/api-cards')
const {
  DAY_RELATION_CHIPS,
  CARD_STYLES,
  daysTogetherOf,
  fmtDot,
  pad2,
  todayStr,
  markMadeToday,
} = require('../../utils/cards-data')
const { posterPageShare } = require('../../utils/share')

Page(
  Object.assign(
    {
      data: {
        relations: DAY_RELATION_CHIPS,
        relation: DAY_RELATION_CHIPS[0],
        startDate: '',
        startDateDot: '',
        dateEnd: '',
        styles: CARD_STYLES,
        styleKey: 'SUNSET',
        days: 1,
        submitting: false,
      },

      onLoad() {
        // 起始日默认今天-1 天(首屏即「第 1 天」);picker 上限=今天,不允许选未来
        const yest = new Date(Date.now() - 86400000)
        const startDate =
          yest.getFullYear() + '-' + pad2(yest.getMonth() + 1) + '-' + pad2(yest.getDate())
        this.setData({ startDate, dateEnd: todayStr() })
        this.refreshPreview()
      },

      /** 关系单选(契约 relation 1-12 字,含 emoji) */
      pickRelation(e) {
        this.setData({ relation: e.currentTarget.dataset.name })
      },

      /** 在一起的日子(picker 年月日) */
      onDate(e) {
        this.setData({ startDate: e.detail.value })
        this.refreshPreview()
      },

      /** 样式三选(契约 styleKey: SUNSET/NIGHT/MINT) */
      pickStyle(e) {
        this.setData({ styleKey: e.currentTarget.dataset.key })
      },

      /** 实时预览:起始日→今天差值,本地计算(与 utils/cards-data daysTogetherOf 同公式) */
      refreshPreview() {
        const startDate = this.data.startDate
        this.setData({
          days: daysTogetherOf(startDate),
          startDateDot: fmtDot(startDate),
        })
      },

      /** 「✨ 生成天数卡片」:定稿 → 取 scene → 海报舞台 */
      makePoster() {
        if (this.data.submitting) return
        if (!this.data.relation) {
          wx.showToast({ title: '先选一下你们的关系', icon: 'none' })
          return
        }
        if (!this.data.startDate) {
          wx.showToast({ title: '先选在一起的日子', icon: 'none' })
          return
        }
        this.setData({ submitting: true })
        const payload = {
          relation: this.data.relation,
          startDate: this.data.startDate,
          styleKey: this.data.styleKey,
        }
        api
          .createCard({ templateKind: 'DAYS_CARD', payload })
          .then((card) => {
            const full = card && card.payload ? card : { templateKind: 'DAYS_CARD', payload }
            return Promise.all([full, api.shareCard(card.id)])
          })
          .then(([card, share]) => {
            markMadeToday('DAYS_CARD')
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
