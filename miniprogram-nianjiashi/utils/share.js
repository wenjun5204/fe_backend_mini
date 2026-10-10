/**
 * 海报转发混入(病毒主链路:微信好友转发卡片 path=pagePath+scene,直达访客页)
 * 页面用法:
 *   const { posterPageShare } = require('../../utils/share')
 *   Page(Object.assign({ ...页面配置 }, posterPageShare))
 * 海报组件触发 sharecfg 事件({pagePath,title,imageUrl})后,本页转发即带 scene 直达访客落地页;
 * 未展示过海报时回落到 Tab 首页默认分享。
 */
const { KIND_LABEL } = require('./cards-data')

/** 分享标题按卡片 kind(与原型海报 foot 口径一致的家人向文案) */
const SHARE_TITLES = {
  LETTER: '这封信写给你，点开看完好吗',
  DAYS_CARD: '我们在的第 N 天，想给你看看',
  CAPSULE: '给你埋了一个时光胶囊，到日子开箱',
}

function posterPageShare() {
  return {
    /** 海报组件 sharecfg 事件落地:存 path/标题/海报图,转发卡片立即可用
     * (不预置 data.shareCfg——setData 动态建键即可,避免与页面自身 data 合并覆盖) */
    onShareCfg(e) {
      const cfg = e.detail || {}
      this.setData({
        shareCfg: {
          pagePath: cfg.pagePath || '',
          title: cfg.title || '念家时 · 把重要的日子，记在心上',
          imageUrl: cfg.imageUrl || '',
        },
      })
    },

    onShareAppMessage() {
      const cfg = this.data.shareCfg
      if (cfg && cfg.pagePath) {
        return { title: cfg.title, path: cfg.pagePath, imageUrl: cfg.imageUrl || undefined }
      }
      return {
        title: '念家时 · 把重要的日子，记在心上',
        path: '/pages/days/days',
      }
    },
  }
}

module.exports = { posterPageShare, SHARE_TITLES, KIND_LABEL }
