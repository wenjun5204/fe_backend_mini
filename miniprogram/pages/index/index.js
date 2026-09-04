const { request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    message: '',
    serverTime: '',
    error: '',
  },

  fetchHello() {
    this.setData({ loading: true, error: '', message: '', serverTime: '' })
    request('/api/hello')
      .then((data) => {
        this.setData({ message: data.message, serverTime: data.time, loading: false })
      })
      .catch((err) => {
        this.setData({
          error: `后端连接失败：${err.errMsg || err.message}`,
          loading: false,
        })
      })
  },
})
