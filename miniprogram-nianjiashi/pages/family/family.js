/**
 * 家人 Tab(第 4 个 Tab,设计 v2.2 §2.1/§2.4)
 * 家人横幅卡 + 成员列表 + 家人瞬间时间线(真好/收藏进日子/本人删)+ 弹层发瞬间。
 * 昵称解析:时间线只下发 userId,经 GET /api/family 成员列表组装 userId→昵称 map(契约口径)。
 */
const { ensureLogin, getUser } = require('../../utils/request')
const { listDays } = require('../../utils/api-days')
const fam = require('../../utils/api-family')
const { chooseAndUploadPhoto, photoUrl } = require('../../utils/photo')

/** 成员/瞬间头像底色轮换(视觉令牌 --grad-* 体系) */
const GRADS = ['grad-hot', 'grad-teal', 'grad-violet']
const ROLE_LABEL = { OWNER: '户主', MEMBER: '家人' }

/** '2026-10-12T09:30' → '今天 09:30' / '10月12日' / '2025年10月12日' */
function fmtTime(iso) {
  if (!iso || iso.indexOf('T') < 0) return ''
  const parts = iso.split('T')
  const dp = parts[0].split('-')
  if (dp.length !== 3) return iso
  const y = Number(dp[0])
  const mo = Number(dp[1])
  const da = Number(dp[2])
  const now = new Date()
  if (y === now.getFullYear() && mo === now.getMonth() + 1 && da === now.getDate()) {
    return '今天 ' + parts[1].substring(0, 5)
  }
  if (y === now.getFullYear()) {
    return mo + '月' + da + '日'
  }
  return y + '年' + mo + '月' + da + '日'
}

/** MemberStatus → 成员视图(头像:头像 URL 优先,缺省用昵称首字) */
function memberViewOf(m, index) {
  const nickname = m.nickname || '家人'
  return {
    userId: m.userId,
    nickname,
    roleText: ROLE_LABEL[m.role] || '家人',
    avatarUrl: m.avatarUrl && m.avatarUrl.indexOf('http') === 0 ? m.avatarUrl : '',
    avatarText: nickname.substring(0, 1),
    gradClass: GRADS[index % GRADS.length],
  }
}

/** FamilyMomentView → 时间线视图(photoUrl 相对地址转绝对;昵称/头像底色经成员 map 解析) */
function momentViewOf(m, nameMap, gradMap, myUserId) {
  const author = nameMap[m.userId] || '家人'
  return {
    id: m.id,
    mine: m.userId === myUserId,
    author,
    avatarText: author.substring(0, 1),
    gradClass: gradMap[m.userId] || 'grad-hot',
    photo: photoUrl(m.photoUrl || m.photoId),
    text: m.text || '',
    time: fmtTime(m.createdAt),
    likeCount: m.likeCount || 0,
    likedByMe: !!m.likedByMe,
    collectedCount: m.collectedCount || 0,
    collected: (m.collectedByMeDayIds || []).length > 0,
    collectedByMeDayIds: m.collectedByMeDayIds || [],
  }
}

/** 成员 userId → {昵称, 头像底色} 两张 map(时间线昵称解析契约口径) */
function memberMaps(members) {
  const nameMap = {}
  const gradMap = {}
  ;(members || []).forEach((m) => {
    nameMap[m.userId] = m.nickname
    gradMap[m.userId] = m.gradClass
  })
  return { nameMap, gradMap }
}

Page({
  data: {
    loading: true,
    loadFailed: false,
    notJoined: false,
    // 家人横幅卡:家族名 + 成员数 + 记的日子数
    familyName: '',
    memberCount: 0,
    dayCount: 0,
    members: [],
    inviteCode: '',
    // 瞬间时间线(cursor 分页)
    moments: [],
    nextCursor: 0,
    noMore: false,
    loadingMore: false,
    // 收藏目标:BIRTHDAY 日子(来自 GET /api/days,收藏 picker 数据源)
    birthdayDays: [],
    // 发瞬间弹层
    composer: {
      show: false,
      photo: '',
      photoId: '',
      text: '',
      publishing: false,
    },
  },

  onShow() {
    // Tab 级页面每次到达重拉:他人新瞬间到达即刷新(设计 §2.1)
    this.load()
  },

  /** 转发邀请卡片:未满员时带 inviteCode 直达进家确认(share.js 的 posterShare 未用于本页) */
  onShareAppMessage() {
    const d = this.data
    if (!d.notJoined && d.inviteCode && d.memberCount < 8) {
      return {
        title: '「' + d.familyName + '」喊你进家，一起记重要的日子',
        path: '/pages/family/family?inviteCode=' + d.inviteCode,
      }
    }
    return {
      title: '念家时 · 把重要的日子，记在心上',
      path: '/pages/days/days',
    }
  },

  /** 落地参数识别:家人转发的邀请卡带 inviteCode 进来 → 未入家则先拉家名再弹确认 */
  onLoad(options) {
    const code = (options && options.inviteCode) || ''
    if (code) {
      this._pendingInviteCode = code
    }
  },

  /** load() 完成后的落地参数消费:登录态就绪 + 未加入家族时才有意义 */
  maybeAcceptInvite() {
    const code = this._pendingInviteCode
    if (!code || this.data.notJoined !== true) return
    this._pendingInviteCode = ''
    fam
      .getInviteInfo(code)
      .then((info) => {
        const name = (info && info.familyName) || '家人的家'
        wx.showModal({
          title: '加入「' + name + '」',
          content: '家人邀请你进这个家，进家后可以分享家人瞬间',
          confirmText: '进家',
          success: (res) => {
            if (!res.confirm) return
            fam
              .joinFamily(code)
              .then(() => {
                wx.showToast({ title: '进家了', icon: 'success', duration: 1800 })
                this.load()
              })
              .catch(() => {})
          },
        })
      })
      .catch(() => {
        // 邀请码无效(40400)/已满员(40900)由网络层统一 toast;手动输入兜底仍在
      })
  },

  /** 邀请码一键复制 */
  copyInvite() {
    const code = this.data.inviteCode
    if (!code) return
    wx.setClipboardData({
      data: code,
      success: () => wx.showToast({ title: '邀请码已复制', icon: 'none', duration: 1600 }),
    })
  },

  onPullDownRefresh() {
    this.load()
  },

  onReachBottom() {
    this.loadMore()
  },

  load() {
    this.setData({ loading: true, loadFailed: false })
    ensureLogin()
      .catch(() => {})
      .then(() => fam.getMyFamily())
      .then((family) => {
        if (!family || family.notJoined) {
          this.setData({ notJoined: true, loading: false })
          wx.stopPullDownRefresh()
          this.maybeAcceptInvite()
          return
        }
        const members = (family.members || []).map(memberViewOf)
        this.setData({
          notJoined: false,
          familyName: family.familyName || '我的家',
          memberCount: members.length,
          members,
          inviteCode: family.inviteCode || '',
        })
        // 时间线 + 日子(横幅记的日子数 + 收藏 picker 的 BIRTHDAY 日子)并行
        return Promise.all([fam.listMoments(), listDays().catch(() => ({ items: [] }))]).then(
          ([momentRes, dayRes]) => {
            const maps = memberMaps(this.data.members)
            const user = getUser()
            const myUserId = (user && user.userId) || -1
            const items = (momentRes && momentRes.items) || []
            this.setData({
              moments: items.map((m) => momentViewOf(m, maps.nameMap, maps.gradMap, myUserId)),
              nextCursor: (momentRes && momentRes.nextCursor) || 0,
              noMore: !momentRes || !momentRes.nextCursor,
              dayCount: (dayRes && dayRes.items ? dayRes.items.length : 0),
              birthdayDays: this.birthdayDaysOf(dayRes),
              loading: false,
            })
            wx.stopPullDownRefresh()
          }
        )
      })
      .catch(() => {
        // 请求失败 ≠ 没有家:不能误显示建家引导,给重试入口(网络层已 toast)
        this.setData({ loadFailed: true, loading: false })
        wx.stopPullDownRefresh()
      })
  },

  /** GET /api/days → kind=BIRTHDAY 的日子选项(收藏 picker 数据源) */
  birthdayDaysOf(dayRes) {
    const items = (dayRes && dayRes.items) || []
    return items
      .filter((r) => r.kind === 'BIRTHDAY')
      .map((r) => ({
        id: r.id,
        name: r.relation + '的生日',
        emoji: r.emoji || '🎂',
      }))
  },

  loadMore() {
    const d = this.data
    if (d.loadingMore || d.loading || !d.nextCursor) {
      return
    }
    this.setData({ loadingMore: true })
    fam
      .listMoments(d.nextCursor)
      .then((res) => {
        const maps = memberMaps(this.data.members)
        const user = getUser()
        const myUserId = (user && user.userId) || -1
        const items = (res && res.items) || []
        this.setData({
          moments: this.data.moments.concat(
            items.map((m) => momentViewOf(m, maps.nameMap, maps.gradMap, myUserId))
          ),
          nextCursor: (res && res.nextCursor) || 0,
          noMore: !res || !res.nextCursor,
          loadingMore: false,
        })
      })
      .catch(() => {
        this.setData({ loadingMore: false })
      })
  },

  // ===== 真好(点赞) =====

  onLike(e) {
    const id = e.currentTarget.dataset.id
    const idx = this.data.moments.findIndex((m) => m.id === id)
    if (idx < 0 || this.data.moments[idx].likeBusy) {
      return
    }
    const m = this.data.moments[idx]
    // 乐观更新 + 失败回滚(同 day-detail 提醒开关模式)
    this.setData({
      ['moments[' + idx + '].likedByMe']: !m.likedByMe,
      ['moments[' + idx + '].likeCount']: m.likeCount + (m.likedByMe ? -1 : 1),
      ['moments[' + idx + '].likeBusy']: true,
    })
    const call = m.likedByMe ? fam.unlikeMoment(id) : fam.likeMoment(id)
    call
      .then((res) => {
        this.setData({
          ['moments[' + idx + '].likeCount']: res.likeCount,
          ['moments[' + idx + '].likedByMe']: res.likedByMe,
          ['moments[' + idx + '].likeBusy']: false,
        })
      })
      .catch(() => {
        this.setData({
          ['moments[' + idx + '].likedByMe']: m.likedByMe,
          ['moments[' + idx + '].likeCount']: m.likeCount,
          ['moments[' + idx + '].likeBusy']: false,
        })
      })
  },

  // ===== 收藏进日子 =====

  onCollect(e) {
    const id = e.currentTarget.dataset.id
    const moment = this.data.moments.find((m) => m.id === id)
    if (!moment) {
      return
    }
    const days = this.data.birthdayDays
    if (!days.length) {
      wx.showToast({ title: '先去日子页记一个家人的生日，就能收藏了', icon: 'none', duration: 2600 })
      return
    }
    // actionSheet 最多 6 项(小程序上限),已收藏的日子打 ✓ 再点即取消
    const list = days.slice(0, 6)
    wx.showActionSheet({
      itemList: list.map((d) => {
        const collected = moment.collectedByMeDayIds.indexOf(d.id) >= 0
        return (collected ? '✓ ' : d.emoji + ' ') + d.name
      }),
      success: (res) => {
        const day = list[res.tapIndex]
        if (!day) {
          return
        }
        if (moment.collectedByMeDayIds.indexOf(day.id) >= 0) {
          this.doUncollect(moment, day)
        } else {
          this.doCollect(moment, day)
        }
      },
    })
  },

  doCollect(moment, day) {
    const idx = this.data.moments.findIndex((m) => m.id === moment.id)
    fam
      .collectMoment(moment.id, day.id)
      .then((res) => {
        const collectedByMeDayIds = moment.collectedByMeDayIds.concat([day.id])
        this.setData({
          ['moments[' + idx + '].collectedCount']: res.collectedCount,
          ['moments[' + idx + '].collected']: true,
          ['moments[' + idx + '].collectedByMeDayIds']: collectedByMeDayIds,
        })
        wx.showToast({ title: '收藏进「' + day.name + '」了', icon: 'none', duration: 2200 })
      })
      .catch(() => {})
  },

  doUncollect(moment, day) {
    const idx = this.data.moments.findIndex((m) => m.id === moment.id)
    fam
      .uncollectMoment(moment.id, day.id)
      .then((res) => {
        this.setData({
          ['moments[' + idx + '].collectedCount']: res.collectedCount,
          ['moments[' + idx + '].collected']: false,
          ['moments[' + idx + '].collectedByMeDayIds']: moment.collectedByMeDayIds.filter(
            (id) => id !== day.id
          ),
        })
      })
      .catch(() => {})
  },

  // ===== 删除(仅本人,二次确认) =====

  onDelete(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删掉这条瞬间？',
      content: '删掉后家人也看不见了',
      confirmText: '删掉',
      confirmColor: '#ff4d6d',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        fam
          .deleteMoment(id)
          .then(() => {
            this.setData({ moments: this.data.moments.filter((m) => m.id !== id) })
          })
          .catch(() => {})
      },
    })
  },

  // ===== 照片预览 =====

  onPreview(e) {
    const url = e.currentTarget.dataset.src
    if (url) {
      wx.previewImage({ urls: [url] })
    }
  },

  // ===== 发瞬间弹层 =====

  openComposer() {
    this.setData({ 'composer.show': true })
  },

  closeComposer() {
    if (this.data.composer.publishing) {
      return
    }
    this.setData({ 'composer.show': false })
  },

  pickPhoto() {
    if (this.data.composer.publishing) {
      return
    }
    // loading 由 photo.js 在选完照片后统一管理;用户取消 resolve(null) 静默
    chooseAndUploadPhoto()
      .then((res) => {
        if (!res) {
          return
        }
        this.setData({ 'composer.photo': res.url, 'composer.photoId': res.photoId })
      })
      .catch(() => {})
  },

  onTextInput(e) {
    this.setData({ 'composer.text': e.detail.value })
  },

  publish() {
    const c = this.data.composer
    if (c.publishing) {
      return
    }
    const text = (c.text || '').trim()
    if (!c.photoId) {
      wx.showToast({ title: '先选一张照片', icon: 'none', duration: 2200 })
      return
    }
    if (text.length < 1) {
      wx.showToast({ title: '再说一句话就发了', icon: 'none', duration: 2200 })
      return
    }
    this.setData({ 'composer.publishing': true })
    fam
      .postMoment({ photoId: c.photoId, text })
      .then(() => {
        this.setData({
          'composer.show': false,
          'composer.publishing': false,
          'composer.photo': '',
          'composer.photoId': '',
          'composer.text': '',
        })
        wx.showToast({ title: '发布好了', icon: 'success', duration: 1800 })
        // 回到时间线首页刷新(自家新瞬间即时可见)
        this.refreshMoments()
      })
      .catch(() => {
        // 40300 每日限 10 条 / 40000 违禁词等,网络层已 toast,弹层保留草稿
        this.setData({ 'composer.publishing': false })
      })
  },

  /** 仅重拉时间线首页(发完瞬间/下拉续页场景,横幅与成员不动) */
  refreshMoments() {
    const maps = memberMaps(this.data.members)
    const user = getUser()
    const myUserId = (user && user.userId) || -1
    fam
      .listMoments()
      .then((res) => {
        const items = (res && res.items) || []
        this.setData({
          moments: items.map((m) => momentViewOf(m, maps.nameMap, maps.gradMap, myUserId)),
          nextCursor: (res && res.nextCursor) || 0,
          noMore: !res || !res.nextCursor,
        })
      })
      .catch(() => {})
  },

  // ===== 未加入家族:建家 / 进家引导 =====

  goCreateFamily() {
    wx.showModal({
      title: '创建一个家',
      editable: true,
      placeholderText: '给家起个名字',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        const name = (res.content || '').trim()
        fam
          .createFamily(name || '我的家')
          .then(() => {
            wx.showToast({ title: '家建好了', icon: 'success', duration: 1800 })
            this.load()
          })
          .catch(() => {})
      },
    })
  },

  goJoinFamily() {
    wx.showModal({
      title: '加入家人的家',
      editable: true,
      placeholderText: '输入邀请码',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        const code = (res.content || '').trim()
        if (!code) {
          wx.showToast({ title: '邀请码不能为空', icon: 'none', duration: 2200 })
          return
        }
        fam
          .joinFamily(code)
          .then(() => {
            wx.showToast({ title: '进家了', icon: 'success', duration: 1800 })
            this.load()
          })
          .catch(() => {})
      },
    })
  },

  retry() {
    this.load()
  },
})
