package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.FortuneDtos;
import com.example.backend.service.FortuneTextLib;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 合规门测试:福签文案库永不出现禁用词(词表见 .agents/docs/conventions.md) */
class FortuneTextLibTest {

    /** 迷信/医疗禁用词表(与 conventions.md 保持同步) */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "运势", "吉凶", "求签", "算命", "占卜", "八字", "塔罗", "改运", "挡灾", "化解", "开光", "大师",
            "命中注定", "犯太岁", "本命", "凶", "忌", "破财", "降血压", "降血糖", "治疗", "理疗", "排毒", "药用", "偏方");

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
    void 随机宜事项数量在2到3之间且不重复() {
        for (int i = 0; i < 30; i++) {
            List<String> items = FortuneTextLib.randomYiItems();
            assertTrue(items.size() >= 2 && items.size() <= 3, "宜事项数量应为 2-3: " + items);
            Set<String> unique = new HashSet<>(items);
            assertEquals(items.size(), unique.size(), "宜事项不应重复: " + items);
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

    private void assertNoForbidden(List<String> texts) {
        for (String text : texts) {
            for (String word : FORBIDDEN_WORDS) {
                assertFalse(text.contains(word), "文案包含禁用词: " + word + " -> " + text);
            }
        }
    }
}
