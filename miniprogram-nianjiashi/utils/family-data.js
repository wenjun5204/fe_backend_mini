/**
 * 家人空间前端常量(设计 v2.2 §2.2:BLESSING_CHIPS 福卡祝福语点选等)
 * 文案须过后端 DayTextLib.FORBIDDEN_WORDS 词表(提交侧强校验,前端预置同样守表)。
 */

/** 生日福卡预置祝福语(点选;契约 blessingText 1-40 字,亦支持自由输入) */
const BLESSING_CHIPS = [
  '生日快乐，健健康康，天天都有好心情',
  '又长一岁，福气满满，一家人都陪着你',
  '岁岁平安，年年有今日，岁岁有今朝',
]

module.exports = { BLESSING_CHIPS }
