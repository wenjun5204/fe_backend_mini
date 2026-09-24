package com.example.backend.service;

import com.example.backend.dto.FortuneDtos;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 福签文案库与等级抽取。
 * ⚠️ 合规红线:所有文案只祝福不预测,无迷信词/医疗词,「今日宜」永不出现「忌」。
 * 词表见 .agents/docs/conventions.md,新增文案前必须过禁用词扫描(FortuneTextLibTest)。
 *
 * <p>内容结构:
 * <ul>
 *   <li>常规池:BLESS_TEXTS(30 条)/ YI_ITEMS(24 条),全年通用</li>
 *   <li>节日限定池:FESTIVALS,命中日期区间时优先使用节日专属文案(全家同签,一天一签,
 *       判定发生在 ensureCard 生成那一刻)</li>
 * </ul>
 *
 * <p>📅 节日日期维护规则:春节/元宵/端午/中秋/重阳按农历浮动,每年 12 月排期时
 * 对照万年历核对/更新下一年的公历区间(公历固定节日无需更新)。
 */
public final class FortuneTextLib {

    private FortuneTextLib() {
    }

    /** 祝福语池(全部正面祝福,无预测语义),30 条 */
    public static final List<String> BLESS_TEXTS = List.of(
            "愿您和家人出入平安，顺顺当当",
            "心宽体健，事事如意",
            "福气盈门，全家安康",
            "平平安安，笑口常开",
            "一家人和和美美，就是最大的福气",
            "添衣添饭添福气，顺心顺意顺平安",
            "愿您今天心情好，吃饭香，睡觉甜",
            "家人惦记您，福气跟着您",
            "愿您的每一天都有小欢喜",
            "常回家看看，福气自然来",
            "愿您腿脚有力，笑口常开",
            "饭要一口口吃，福要一天天攒",
            "愿您心平气和，百事顺遂",
            "今天的太阳很好，愿您心情更好",
            "愿您被家人惦念，被福气围绕",
            "家里有您，就是最大的福气",
            "愿您手暖脚暖，心里更暖",
            "好好吃饭，好好睡觉，就是好日子",
            "愿您天天有笑声，日日有福气",
            "家和万事兴，愿您全家顺顺当当",
            "愿您想起家人时，嘴角上扬",
            "平淡日子里的平安，就是真福气",
            "愿您胃口好，精神足，福气旺",
            "有人等您吃饭，就是好福气",
            "愿您走稳每一步，过好每一天",
            "福气就在您和家人相视一笑里",
            "愿您的付出都被家人看见",
            "一杯热茶，一份牵挂，一身福气",
            "愿您老有所乐，福有所依",
            "今天也要记得对自己好一点"
    );

    /** 今日宜事项池(生活化正面建议,永不出现忌与医疗词),27 条 */
    public static final List<String> YI_ITEMS = List.of(
            "宜散步二十分钟",
            "宜多喝两杯水",
            "宜早点休息",
            "宜给家人打个电话",
            "宜用热水泡泡脚",
            "宜好好吃顿早餐",
            "宜晒十分钟太阳",
            "宜和老伴说句贴心话",
            "宜给孙辈讲个老故事",
            "宜午睡二十分钟",
            "宜下楼和邻居聊聊天",
            "宜吃点新鲜水果",
            "宜活动活动手腕脚腕",
            "宜看一集喜欢的电视剧",
            "宜开窗通风十分钟",
            "宜慢慢走，别着急",
            "宜给阳台的花浇浇水",
            "宜听一段喜欢的戏曲",
            "宜和家人一起做顿饭",
            "宜伸个懒腰，深呼吸三次",
            "宜早点吃晚饭",
            "宜给远方的朋友发条消息",
            "宜把今天的开心事讲给家人听",
            "宜睡前少看手机，多聊聊天",
            // v1.5 追加:含早睡/喝水/打电话关键字的宜事项,八卦集福阵离/坎/巽进度口径依赖其落库
            "宜晚上十点前早睡，养足精神",
            "宜勤喝水，小口慢饮",
            "宜给家人打电话说说近况"
    );

    /** 抽签吉祥话,{N} 替换为连续天数 */
    public static final List<String> PRAISE_TEXTS = List.of(
            "坚持第{N}天，越活越年轻！",
            "今天您比昨天更精神！",
            "接住福气，全家安康！",
            "连续{N}天接福，您真有毅力！",
            "福气天天见，家人岁岁安！",
            "好习惯{N}天了，给自己点个赞！"
    );

    // ==================== 节日限定池 ====================

    /** 公历日期区间(闭区间) */
    public record DateRange(LocalDate start, LocalDate end) {
        boolean contains(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }

        public static DateRange of(LocalDate start, LocalDate end) {
            return new DateRange(start, end);
        }

        public static DateRange day(LocalDate date) {
            return new DateRange(date, date);
        }
    }

    /**
     * 节日限定池:命中区间时,祝福语与今日宜全部从节日池抽取(纯限定,更有仪式感)。
     * 列表顺序即优先级(区间几乎不重叠,若重叠取先命中者)。
     */
    public record FestivalPool(String name, List<DateRange> ranges,
                               List<String> blessTexts, List<String> yiItems) {
    }

    /** 节日限定池清单(农历节日日期每年 12 月核对更新,见类注释维护规则) */
    public static final List<FestivalPool> FESTIVALS = List.of(
            new FestivalPool("新春", List.of(
                            DateRange.of(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 24)),
                            DateRange.of(LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 13))),
                    List.of(
                            "新春福气到，全家乐淘淘",
                            "愿新的一年，全家平安顺遂",
                            "开门见福，全年顺心",
                            "愿您新春添福，岁岁安康",
                            "新的一年，愿您笑口常开",
                            "愿全家新春团聚，和和美美",
                            "新年纳福，事事顺心",
                            "爆竹声声辞旧岁，福气满满迎新春"
                    ),
                    List.of(
                            "宜和家人一起贴福字",
                            "宜给长辈拜个年",
                            "宜全家一起吃顿团圆饭",
                            "宜和家人拍张全家福",
                            "宜给孙辈包个红包",
                            "宜对家人说句新年祝福"
                    )),
            new FestivalPool("元宵", List.of(
                            DateRange.day(LocalDate.of(2026, 3, 3)),
                            DateRange.day(LocalDate.of(2027, 2, 20))),
                    List.of(
                            "汤圆甜甜，全家团圆",
                            "灯明月圆，福气盈门",
                            "愿您日子像汤圆，圆圆满满",
                            "正月十五闹花灯，愿您天天好心情",
                            "一碗汤圆一份甜，全家安康一整年",
                            "愿这盏灯，照亮全家一年的顺遂"
                    ),
                    List.of(
                            "宜煮一碗汤圆和家人分享",
                            "宜带孙辈去看花灯",
                            "宜猜一条灯谜乐一乐",
                            "宜和家人拍张笑脸照",
                            "宜给家人盛一碗热汤圆"
                    )),
            new FestivalPool("端午", List.of(
                            DateRange.day(LocalDate.of(2026, 6, 19)),
                            DateRange.day(LocalDate.of(2027, 6, 9))),
                    List.of(
                            "粽叶飘香，愿您安康",
                            "五月粽香，全家平安",
                            "愿您像粽子一样，里外都充实",
                            "端午安康，福气常伴",
                            "粽香传情，愿全家顺遂",
                            "粽叶青青，日子甜甜"
                    ),
                    List.of(
                            "宜和家人一起包粽子",
                            "宜吃个粽子应应景",
                            "宜出门看看龙舟",
                            "宜给孙辈讲讲端午的故事",
                            "宜煮一锅粽子分给邻居尝尝"
                    )),
            new FestivalPool("中秋", List.of(
                            DateRange.day(LocalDate.of(2026, 9, 25)),
                            DateRange.day(LocalDate.of(2027, 9, 15))),
                    List.of(
                            "月圆人圆，全家团圆",
                            "愿这轮明月，照您全家安康",
                            "花好月圆，福气满门",
                            "愿您和家人共赏明月，共享安康",
                            "月到中秋分外明，愿您全家事事顺心",
                            "一块月饼一份甜，全家安康就是福",
                            "千里共明月，福气传万家"
                    ),
                    List.of(
                            "宜和家人吃块月饼",
                            "宜给远方的家人打个电话",
                            "宜和家人一起赏月",
                            "宜陪着老伴散散步",
                            "宜和家人围坐聊聊天",
                            "宜拍一张中秋全家福"
                    )),
            new FestivalPool("重阳", List.of(
                            DateRange.day(LocalDate.of(2026, 10, 18)),
                            DateRange.day(LocalDate.of(2027, 10, 9))),
                    List.of(
                            "重阳登高，福气高高",
                            "愿您身体硬朗，越活越精神",
                            "秋高气爽，愿您心宽体健",
                            "家有一老，如有一宝",
                            "愿您福如东海，笑口常开",
                            "菊花开得好，您的福气少不了",
                            "愿全家长辈都健康长寿"
                    ),
                    List.of(
                            "宜和家人登高望远",
                            "宜喝一杯菊花茶",
                            "宜让子女陪您说说话",
                            "宜出门晒晒太阳",
                            "宜给老朋友打个电话",
                            "宜吃一块重阳糕"
                    )),
            new FestivalPool("冬至", List.of(
                            DateRange.of(LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 23)),
                            DateRange.of(LocalDate.of(2027, 12, 21), LocalDate.of(2027, 12, 23))),
                    List.of(
                            "冬至大如年，愿您阖家温暖",
                            "一碗热汤，暖身更暖心",
                            "愿这个冬天，全家都被暖意包围",
                            "数九寒天，愿您身暖心也暖",
                            "冬至到，福气到",
                            "愿您冬天不冷，心里有暖"
                    ),
                    List.of(
                            "宜吃一碗热腾腾的饺子",
                            "宜喝一碗热汤暖暖身",
                            "宜早点钻进暖被窝",
                            "宜用热水泡个脚",
                            "宜提醒家人添衣保暖"
                    )),
            new FestivalPool("国庆", List.of(
                            DateRange.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)),
                            DateRange.of(LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 5))),
                    List.of(
                            "举国同庆，全家同福",
                            "国泰民安，家和人安",
                            "愿您假期玩得开心，歇得舒心",
                            "盛世华诞，福气盈门",
                            "假期里，愿您天天好心情"
                    ),
                    List.of(
                            "宜和家人出门走走看看",
                            "宜看一场升旗仪式",
                            "宜和家人来一次短途游",
                            "宜拍一张全家福",
                            "宜给远方亲人打个电话"
                    )),
            new FestivalPool("元旦", List.of(
                            DateRange.day(LocalDate.of(2027, 1, 1)),
                            DateRange.day(LocalDate.of(2028, 1, 1))),
                    List.of(
                            "新年第一天，福气开新篇",
                            "愿新的一年，全家平安喜乐",
                            "元旦纳福，万事顺意",
                            "新的一年，愿您笑口常开",
                            "愿家人相伴，岁岁平安"
                    ),
                    List.of(
                            "宜和家人一起吃顿好的",
                            "宜许一个新年小愿望",
                            "宜给家人一个拥抱",
                            "宜翻开新日历，圈出家人生日",
                            "宜和家人视频聊聊天"
                    ))
    );

    /** 命中节日则返回对应限定池,否则 null */
    public static FestivalPool festivalOf(LocalDate date) {
        for (FestivalPool festival : FESTIVALS) {
            for (DateRange range : festival.ranges()) {
                if (range.contains(date)) {
                    return festival;
                }
            }
        }
        return null;
    }

    // ==================== 抽取入口(节日优先) ====================

    /** 福签等级:平安 70% / 如意 25% / 鸿福 5%,纯稀有度玩法,不影响福值(节日不影响概率) */
    public static FortuneDtos.FortuneLevel drawLevel() {
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 5) {
            return FortuneDtos.FortuneLevel.HONGFU;
        }
        if (r < 30) {
            return FortuneDtos.FortuneLevel.RUYI;
        }
        return FortuneDtos.FortuneLevel.PINGAN;
    }

    /** 随机祝福语:节日日从限定池抽,日常从常规池抽 */
    public static String randomBlessText(LocalDate date) {
        FestivalPool festival = festivalOf(date);
        List<String> pool = festival != null ? festival.blessTexts() : BLESS_TEXTS;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** 随机抽 2-3 项今日宜:节日日从限定池抽,日常从常规池抽,不重复 */
    public static List<String> randomYiItems(LocalDate date) {
        FestivalPool festival = festivalOf(date);
        List<String> pool = festival != null ? festival.yiItems() : YI_ITEMS;
        int count = Math.min(2 + ThreadLocalRandom.current().nextInt(2), pool.size());
        return ThreadLocalRandom.current()
                .ints(0, pool.size())
                .distinct()
                .limit(count)
                .mapToObj(pool::get)
                .toList();
    }

    /** 抽签吉祥话,{N} 替换为连续天数 */
    public static String praiseText(int streakDays) {
        String text = PRAISE_TEXTS.get(ThreadLocalRandom.current().nextInt(PRAISE_TEXTS.size()));
        return text.replace("{N}", String.valueOf(streakDays));
    }
}
