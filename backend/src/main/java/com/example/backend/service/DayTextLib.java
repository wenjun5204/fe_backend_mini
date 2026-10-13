package com.example.backend.service;

import com.example.backend.dto.DayDtos.DayKind;
import java.util.List;
import java.util.Map;

/**
 * 日子提醒文案库:title/actionText 按 kind 预置,纯静态无随机。
 * ⚠️ 合规红线:全量文案类加载期过违禁词表断言(违禁词出现即启动失败),
 * 词表与 .agents/docs/conventions.md + JieyouQuoteLib 同步。
 */
public final class DayTextLib {

    private DayTextLib() {
    }

    public static final int MAX_ACTION_TEXT_LENGTH = 24;

    /** 违禁词表(与 conventions.md + v1.5 追加词同步):文案中出现即 Bug */
    public static final List<String> FORBIDDEN_WORDS = List.of(
            "能成", "不行", "会有", "注定", "天意", "预示", "破财", "有灾",
            "运势", "吉凶", "求签", "算命", "占卜", "八字", "塔罗", "改运", "挡灾",
            "化解", "开光", "大师", "卦象", "卦运", "宜忌", "生门", "吉位", "凶位",
            "命中注定", "犯太岁", "本命", "凶", "忌", "招财进宝",
            "降血压", "降血糖", "治疗", "理疗", "排毒", "药用", "偏方");

    /** 展示标题,如「妈妈的生日」 */
    public static String title(DayKind kind, String relation) {
        return switch (kind) {
            case BIRTHDAY -> relation + "的生日";
            case ANNIVERSARY -> relation + "的纪念日";
            case TOGETHER -> "和" + relation + "在一起";
        };
    }

    /** 行动建议文案(≤24 字,按 kind 预置;TOGETHER 只在当天产出) */
    public static String actionText(DayKind kind, int remindOffset) {
        Map<Integer, String> byKind = ACTION_TEXT.get(kind);
        String text = byKind.get(remindOffset);
        return text != null ? text : byKind.get(0);
    }

    private static final Map<DayKind, Map<Integer, String>> ACTION_TEXT = Map.of(
            DayKind.BIRTHDAY, Map.of(
                    7, "还有一周,把祝福先想好",
                    3, "还有三天,挑张卡片写句话",
                    0, "打个电话说声生日快乐"),
            DayKind.ANNIVERSARY, Map.of(
                    7, "重要的日子快到了,提前想想",
                    3, "纪念的日子就在后天,准备一下",
                    0, "今天是个值得纪念的日子"),
            DayKind.TOGETHER, Map.of(
                    0, "今天也在彼此身边,说声谢谢"));

    static {
        // 启动期合规断言:行动文案 ≤24 字且过违禁词表,标题过违禁词表;违禁即阻断启动
        for (Map.Entry<DayKind, Map<Integer, String>> entry : ACTION_TEXT.entrySet()) {
            for (String text : entry.getValue().values()) {
                if (text.length() > MAX_ACTION_TEXT_LENGTH) {
                    throw new IllegalStateException("日子行动文案超过 " + MAX_ACTION_TEXT_LENGTH + " 字: " + text);
                }
                assertNoForbiddenWord(entry.getKey() + " 行动文案「" + text + "」", text);
            }
        }
        for (DayKind kind : DayKind.values()) {
            for (String relation : List.of("妈妈", "老伴", "我自己", "亲家公")) {
                assertNoForbiddenWord(kind + " 标题「" + relation + "」", title(kind, relation));
            }
        }
    }

    private static void assertNoForbiddenWord(String label, String text) {
        for (String word : FORBIDDEN_WORDS) {
            if (text.contains(word)) {
                throw new IllegalStateException("日子文案含违禁词「" + word + "」: " + label);
            }
        }
    }
}
