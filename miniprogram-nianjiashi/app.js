const { ensureLogin } = require('./utils/request')

/** 全局默认转发文案(标题随卡片 kind 变化见 utils/share.js SHARE_TITLES) */
const DEFAULT_SHARE = {
  title: '念家时 · 把重要的日子，记在心上',
  pagePath: 'pages/days/days',
}

App({
  onLaunch() {
    // 云托管通道初始化(wx.cloud 需小程序 appid 已绑定云托管环境;本地直连模式无此对象,静默跳过)
    if (typeof wx.cloud !== 'undefined' && wx.cloud && wx.cloud.init) {
      try {
        wx.cloud.init({ traceUser: true })
      } catch (e) {
        // 环境未就绪时不阻塞启动,网络层会走本地通道兜底
      }
    }
    // 登录态尽早建立(X-WX-OPENID / dev-openid → POST /api/auth/login → njs_token)
    // 失败静默重试一次;页面 onShow 的 ensureLogin 再兜底(有 token 时零开销)
    ensureLogin().catch(() => {
      setTimeout(() => {
        ensureLogin().catch(() => {})
      }, 3000)
    })
  },

  /**
   * app 级分享配置(小程序不支持 App 级 onShareAppMessage,转发口径收敛在此,
   * 页面 onShareAppMessage 统一读本配置,无配置时回落默认首页 path):
   * - 默认:标题=品牌语,path=首页(各页 onShareAppMessage 自带回落,当前页即当前页)
   * - 有海报:poster 组件 sharecfg 事件 → 页面 onShareCfg → ensureShareCfg 存 app 级,
   *   转发卡片 path=pagePath+scene 直达访客落地页
   * - visitor 访客页例外:恒返回不带 scene 的纯首页路径,防访客转发扩散他人 scene
   */
  shareCfg: null,

  /** 更新 app 级分享配置(poster sharecfg 事件落地;缺省回落默认值) */
  ensureShareCfg(cfg) {
    cfg = cfg || {}
    this.shareCfg = {
      title: cfg.title || DEFAULT_SHARE.title,
      pagePath: cfg.pagePath || DEFAULT_SHARE.pagePath,
      imageUrl: cfg.imageUrl || '',
    }
    return this.shareCfg
  },

  /** 读 app 级分享配置(未初始化时给默认,避免各页重复写兜底) */
  getShareCfg() {
    return this.shareCfg || this.ensureShareCfg()
  },
})
