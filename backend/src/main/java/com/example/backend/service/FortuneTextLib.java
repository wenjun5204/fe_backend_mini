package com.example.backend.service;

import com.example.backend.dto.FortuneDtos;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 福签文案库与等级抽取。
 * ⚠️ 合规红线:所有文案只祝福不预测,无迷信词/医疗词,「今日宜」永不出现「忌」。
 * 词表见 .agents/docs/conventions.md,新增文案前必须过禁用词扫描。
 */
public final class FortuneTextLib {

    private FortuneTextLib() {
    }

    /** 祝福语池(全部正面祝福,无预测语义) */
    public static final List<String> BLESS_TEXTS = List.of(
            "愿您和家人出入平安，顺顺当当",
            "心宽体健，事事如意",
            "福气盈门，全家安康",
            "平平安安，笑口常开",
            "一家人和和美美，就是最大的福气",
            "添衣添饭添福气，顺心顺意顺平安"
    );

    /** 今日宜事项池(生活化正面建议,永不出现忌与医疗词) */
    public static final List<String> YI_ITEMS = List.of(
            "宜散步二十分钟",
            "宜多喝两杯水",
            "宜早点休息",
            "宜给家人打个电话",
            "宜用热水泡泡脚",
            "宜好好吃顿早餐"
    );

    /** 抽签吉祥话 */
    public static final List<String> PRAISE_TEXTS = List.of(
            "坚持第{N}天，越活越年轻！",
            "今天您比昨天更精神！",
            "接住福气，全家安康！"
    );

    /** 福签等级:平安 70% / 如意 25% / 鸿福 5%,纯稀有度玩法,不影响福值 */
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

    /** 随机祝福语 */
    public static String randomBlessText() {
        return BLESS_TEXTS.get(ThreadLocalRandom.current().nextInt(BLESS_TEXTS.size()));
    }

    /** 随机抽 2-3 项今日宜 */
    public static List<String> randomYiItems() {
        int count = 2 + ThreadLocalRandom.current().nextInt(2);
        return ThreadLocalRandom.current()
                .ints(0, YI_ITEMS.size())
                .distinct()
                .limit(count)
                .mapToObj(YI_ITEMS::get)
                .toList();
    }

    /** 抽签吉祥话,{N} 替换为连续天数 */
    public static String praiseText(int streakDays) {
        String text = PRAISE_TEXTS.get(ThreadLocalRandom.current().nextInt(PRAISE_TEXTS.size()));
        return text.replace("{N}", String.valueOf(streakDays));
    }
}
