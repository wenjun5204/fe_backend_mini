package com.example.backend.service;

import com.example.backend.dto.CardDtos.CardKind;
import java.util.Map;

/**
 * 访客侧用户可见文案(打码钩子/剩余提示/隐藏一句话模板/胶囊悬念文案/转化 CTA),纯静态。
 * ⚠️ 合规红线:全量文案类加载期过 DayTextLib.FORBIDDEN_WORDS 断言(命中即启动失败)。
 */
public final class CardTextLib {

    private CardTextLib() {
    }

    /** 打码钩子提示(访客落地页渐隐遮罩文案),按 kind 取 */
    private static final Map<CardKind, String> LOCKED_TEASERS = Map.of(
            CardKind.LETTER, "还有一段 TA 没发出来的话",
            CardKind.DAYS_CARD, "数字背后还藏着一句 TA 没说出口的话",
            CardKind.CAPSULE, "胶囊里还有一句话和一张照片在等你");

    /** DAYS_CARD 隐藏一句话模板(%d=相伴天数,服务端填充) */
    public static final String HIDDEN_LINE_TEMPLATE = "下一个 %d 天，打算继续赖着你";

    /** CAPSULE 开箱日前的预置悬念文案(真实 contentText 永不进 SEALED 期访客响应) */
    public static final String CAPSULE_SUSPENSE =
            "这里面有一句话，和一张你等着看的照片。到了开箱日，回来开启。";

    /** 深色转化 CTA(解锁内容页常驻) */
    public static final String CTA_TITLE = "用「念家时」打开你们的照片";
    public static final String CTA_SUB = "免费记日子 · 做卡片 · 埋时光胶囊";
    public static final String CTA_BUTTON = "打开念家时";

    public static String lockedTeaser(CardKind kind) {
        return LOCKED_TEASERS.get(kind);
    }

    /** 剩余提示:已解锁 / 还差 N 颗 ❤️ */
    public static String remainingHint(int hearts) {
        if (hearts >= 3) {
            return "已解锁";
        }
        return "还差 " + (3 - hearts) + " 颗 ❤️";
    }

    /** 隐藏一句话(服务端按 daysTogether 实时填充) */
    public static String hiddenLine(int daysTogether) {
        return String.format(HIDDEN_LINE_TEMPLATE, daysTogether);
    }

    static {
        // 启动期合规断言:全量文案过违禁词表;违禁即阻断启动
        for (CardKind kind : CardKind.values()) {
            String teaser = LOCKED_TEASERS.get(kind);
            if (teaser == null) {
                throw new IllegalStateException("访客打码钩子缺少 kind=" + kind + " 的文案");
            }
            assertClean(kind + " 打码钩子", teaser);
        }
        for (int hearts = 0; hearts <= 3; hearts++) {
            assertClean("剩余提示", remainingHint(hearts));
        }
        assertClean("隐藏一句话模板", hiddenLine(2480));
        assertClean("胶囊悬念文案", CAPSULE_SUSPENSE);
        assertClean("CTA 标题", CTA_TITLE);
        assertClean("CTA 副标题", CTA_SUB);
        assertClean("CTA 按钮", CTA_BUTTON);
    }

    private static void assertClean(String label, String text) {
        for (String word : DayTextLib.FORBIDDEN_WORDS) {
            if (text.contains(word)) {
                throw new IllegalStateException("访客文案含违禁词「" + word + "」: " + label);
            }
        }
    }
}
