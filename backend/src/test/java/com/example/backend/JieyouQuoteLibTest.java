package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.dto.JieyouDtos.JieyouQuoteType;
import com.example.backend.service.JieyouQuoteLib;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 合规门测试:解忧册宽心话 120 条(促成/缓行/放下 ×40),
 * 每条 ≤16 字、指向行动、全量过违禁词表(conventions.md + v1.5 追加词)。
 */
class JieyouQuoteLibTest {

    @Test
    void 全量宽心话通过合规自检() {
        JieyouQuoteLib.validate();
    }

    @Test
    void 三型各四十条且页码连续() {
        Map<JieyouQuoteType, Integer> counts = new EnumMap<>(JieyouQuoteType.class);
        for (int id = 1; id <= JieyouQuoteLib.QUOTE_COUNT; id++) {
            JieyouQuoteLib.Quote quote = JieyouQuoteLib.byId(id);
            assertEquals(id, quote.id());
            counts.merge(quote.type(), 1, Integer::sum);
        }
        assertEquals(40, counts.get(JieyouQuoteType.CU_CHENG));
        assertEquals(40, counts.get(JieyouQuoteType.HUAN_XING));
        assertEquals(40, counts.get(JieyouQuoteType.FANG_XIA));
    }

    @Test
    void 每条不超过十六字且非空() {
        for (int id = 1; id <= JieyouQuoteLib.QUOTE_COUNT; id++) {
            String text = JieyouQuoteLib.byId(id).text();
            assertNotNull(text);
            assertTrue(!text.isBlank(), "宽心话为空: 第" + id + "页");
            assertTrue(text.length() <= JieyouQuoteLib.MAX_TEXT_LENGTH,
                    "宽心话超长: 第" + id + "页 " + text);
        }
    }

    @Test
    void 全量宽心话无违禁词() {
        for (int id = 1; id <= JieyouQuoteLib.QUOTE_COUNT; id++) {
            String text = JieyouQuoteLib.byId(id).text();
            for (String word : JieyouQuoteLib.FORBIDDEN_WORDS) {
                assertTrue(!text.contains(word), "宽心话包含违禁词「" + word + "」: 第" + id + "页 " + text);
            }
        }
    }

    @Test
    void 随机抽取始终返回合法页码() {
        for (int i = 0; i < 200; i++) {
            JieyouQuoteLib.Quote quote = JieyouQuoteLib.randomQuote();
            assertTrue(quote.id() >= 1 && quote.id() <= JieyouQuoteLib.QUOTE_COUNT);
            assertNotNull(quote.type());
        }
    }
}
