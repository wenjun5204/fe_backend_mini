package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.FortuneDtos;
import com.example.backend.service.FortuneTextLib;
import com.example.backend.service.FortuneTextLib.FestivalPool;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 合规门测试:福签文案库(常规池+节日池)永不出现禁用词(词表见 .agents/docs/conventions.md) */
class FortuneTextLibTest {

    /** 迷信/医疗禁用词表(与 conventions.md 保持同步) */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "运势", "吉凶", "求签", "算命", "占卜", "八字", "塔罗", "改运", "挡灾", "化解", "开光", "大师",
            "命中注定", "犯太岁", "本命", "凶", "忌", "破财", "降血压", "降血糖", "治疗", "理疗", "排毒", "药用", "偏方");

    /** 日常样本日(不在任何节日区间内) */
    private static final LocalDate NORMAL_DAY = LocalDate.of(2026, 9, 14);

    @Test
    void 祝福语库无禁用词() {
        assertNoForbidden(FortuneTextLib.BLESS_TEXTS);
    }

    @Test
    void 今日宜事项库无禁用词() {
        assertNoForbidden(FortuneTextLib.YI_ITEMS);
    }

    @Test
    void 吉祥话库无禁用词() {
        for (int i = 0; i < 20; i++) {
            String praise = FortuneTextLib.praiseText(3);
            for (String word : FORBIDDEN_WORDS) {
                assertFalse(praise.contains(word), "吉祥话包含禁用词: " + word + " -> " + praise);
            }
        }
    }

    @Test
    void 所有节日限定池无禁用词() {
        for (FestivalPool festival : FortuneTextLib.FESTIVALS) {
            assertNoForbidden(festival.blessTexts());
            assertNoForbidden(festival.yiItems());
        }
    }

    @Test
    void 节日判定命中与未命中() {
        assertEquals("新春", nameOf(LocalDate.of(2026, 2, 17)));
        assertEquals("元宵", nameOf(LocalDate.of(2026, 3, 3)));
        assertEquals("端午", nameOf(LocalDate.of(2026, 6, 19)));
        assertEquals("中秋", nameOf(LocalDate.of(2026, 9, 25)));
        assertEquals("重阳", nameOf(LocalDate.of(2026, 10, 18)));
        assertEquals("冬至", nameOf(LocalDate.of(2026, 12, 22)));
        assertEquals("国庆", nameOf(LocalDate.of(2026, 10, 1)));
        assertEquals("元旦", nameOf(LocalDate.of(2027, 1, 1)));
        assertNull(FortuneTextLib.festivalOf(NORMAL_DAY), "日常日不应命中节日");
    }

    @Test
    void 节日日抽取只来自节日限定池() {
        LocalDate midAutumn = LocalDate.of(2026, 9, 25);
        FestivalPool festival = FortuneTextLib.festivalOf(midAutumn);
        assertNotNull(festival);
        for (int i = 0; i < 20; i++) {
            String bless = FortuneTextLib.randomBlessText(midAutumn);
            assertTrue(festival.blessTexts().contains(bless), "中秋祝福语应来自限定池: " + bless);
        }
        for (int i = 0; i < 20; i++) {
            for (String item : FortuneTextLib.randomYiItems(midAutumn)) {
                assertTrue(festival.yiItems().contains(item), "中秋宜事项应来自限定池: " + item);
            }
        }
    }

    @Test
    void 日常抽取只来自常规池() {
        for (int i = 0; i < 20; i++) {
            String bless = FortuneTextLib.randomBlessText(NORMAL_DAY);
            assertTrue(FortuneTextLib.BLESS_TEXTS.contains(bless), "日常祝福语应来自常规池: " + bless);
        }
    }

    @Test
    void 随机宜事项数量在2到3之间且不重复() {
        for (int i = 0; i < 30; i++) {
            List<String> items = FortuneTextLib.randomYiItems(NORMAL_DAY);
            assertTrue(items.size() >= 2 && items.size() <= 3, "宜事项数量应为 2-3: " + items);
            Set<String> unique = new HashSet<>(items);
            assertEquals(items.size(), unique.size(), "宜事项不应重复: " + items);
        }
    }

    @Test
    void 生成文案长度不超过数据库列宽() {
        // daily_fortune_card: blessText ≤ 50, yiItems(逗号拼接) ≤ 200
        for (LocalDate date : List.of(NORMAL_DAY, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 2, 17))) {
            for (int i = 0; i < 20; i++) {
                assertTrue(FortuneTextLib.randomBlessText(date).length() <= 50, "祝福语超长");
                String joined = String.join(",", FortuneTextLib.randomYiItems(date));
                assertTrue(joined.length() <= 200, "宜事项拼接超长: " + joined);
            }
        }
    }

    @Test
    void 等级抽取始终返回合法枚举() {
        for (int i = 0; i < 100; i++) {
            FortuneDtos.FortuneLevel level = FortuneTextLib.drawLevel();
            assertTrue(level == FortuneDtos.FortuneLevel.PINGAN
                    || level == FortuneDtos.FortuneLevel.RUYI
                    || level == FortuneDtos.FortuneLevel.HONGFU);
        }
    }

    private String nameOf(LocalDate date) {
        FestivalPool festival = FortuneTextLib.festivalOf(date);
        return festival != null ? festival.name() : null;
    }

    private void assertNoForbidden(List<String> texts) {
        for (String text : texts) {
            for (String word : FORBIDDEN_WORDS) {
                assertFalse(text.contains(word), "文案包含禁用词: " + word + " -> " + text);
            }
        }
    }
}
