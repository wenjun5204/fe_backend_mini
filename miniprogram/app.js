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
    // 登录态在页面级 ensureLogin 按需建立(分享进入场景优先处理邀请码)
  },
})
