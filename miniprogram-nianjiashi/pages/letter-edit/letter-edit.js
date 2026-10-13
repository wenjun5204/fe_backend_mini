/**
 * 感恩信第一步:选素材(写给谁/5 个固定故事多选/语气三卡)→「✨ AI 帮我写」
 * 点选为主零键盘,「自定义」走弹窗输入兜底(契约 recipient 1-12 字);
 * 选择结果存 storage njs_letter_req,由 letter-preview 发起 POST /api/templates/letter/generate
 * (stories 值域 = 契约 StoryKey 枚举,与 utils/cards-data.js STORIES 同源)
 */
const { RECIPIENT_CHIPS, STORIES, TONES } = require('../../utils/cards-data')

const DRAFT_KEY = 'njs_letter_req'
const CUSTOM_KEY = '__custom'

Page({
  data: {
    recipients: RECIPIENT_CHIPS.map((name) => ({ name })),
    customChip: { name: '自定义 +' },
    recipient: '妈妈',
    isCustom: false,
    stories: STORIES.map((text) => ({ text, on: false })),
    tones: TONES,
    tone: 'TEAR',
  },

  onLoad() {
    // 默认勾选前两个故事(对齐原型 storyChips 初始态)
    const stories = this.data.stories.map((s, i) => ({ ...s, on: i < 2 }))
    this.setData({ stories })
  },

  /** 写给谁(单选;自定义弹窗输入) */
  pickRecipient(e) {
    const name = e.currentTarget.dataset.name
    if (name === CUSTOM_KEY) {
      this.askCustom()
      return
    }
    this.setData({ recipient: name, isCustom: false })
  },

  askCustom() {
    wx.showModal({
      title: '怎么称呼',
      editable: true,
      placeholderText: '比如：外婆、婶婶，1-12 个字',
      success: (res) => {
        if (!res.confirm) return
        const value = (res.content || '').trim()
        if (!value) {
          wx.showToast({ title: '称呼不能为空', icon: 'none' })
          return
        }
        if (value.length > 12) {
          wx.showToast({ title: '称呼最多 12 个字', icon: 'none' })
          return
        }
        this.setData({ recipient: value, isCustom: true })
      },
    })
  },

  /** 故事多选(契约 stories 1-5 个,至少点 1 个) */
  toggleStory(e) {
    const idx = e.currentTarget.dataset.idx
    const key = 'stories[' + idx + '].on'
    const next = !this.data.stories[idx].on
    this.setData({ [key]: next })
  },

  /** 语气三选(单选) */
  pickTone(e) {
    this.setData({ tone: e.currentTarget.dataset.key })
  },

  /** 「✨ AI 帮我写」:存选择,进 letter-preview(生成动画与接口调用在那边) */
  generate() {
    const stories = this.data.stories.filter((s) => s.on).map((s) => s.text)
    if (!this.data.recipient) {
      wx.showToast({ title: '先选一下写给谁', icon: 'none' })
      return
    }
    if (!stories.length) {
      wx.showToast({ title: '先点一个你们的故事', icon: 'none' })
      return
    }
    const req = { recipient: this.data.recipient, stories, tone: this.data.tone }
    try {
      wx.setStorageSync(DRAFT_KEY, req)
    } catch (e) {
      // 存储失败不阻断,下方跳页后预览页用默认参数兜底
    }
    wx.navigateTo({ url: '/pages/letter-preview/letter-preview' })
  },
})
